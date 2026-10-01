// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/activity/type/PlayActivity.java
package ru.sortix.parkourbeat.activity.type;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.game.movement.GameMoveHandler;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.item.ItemsManager;
import ru.sortix.parkourbeat.item.editor.type.TestGameItem;
import ru.sortix.parkourbeat.levels.DirectionChecker;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.Waypoint;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.physics.CustomPhysicsManager;
import ru.sortix.parkourbeat.rating.JumpResult;
import ru.sortix.parkourbeat.rating.JumpTriggerEvaluator;
import ru.sortix.parkourbeat.rating.Modifier;
import ru.sortix.parkourbeat.rating.ModifierSet;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.world.TeleportUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import ru.sortix.parkourbeat.utils.text.PbText;
public class PlayActivity extends UserActivity {

    @Getter
    private final @NonNull Game game;
    private final boolean isEditorGame;
    private final CustomPhysicsManager physicsManager;

    private static final long JUMP_COOLDOWN_MILLIS = 120L;
    private boolean jumping = false;
    private long lastJumpAt = 0L;

    /**
     * Сервер подтвердил настоящий прыжок. Действует короткое окно, потому что событие
     * прилетает в том же тике, что и движение вверх.
     */
    private long confirmedJumpUntil = 0L;
    /**
     * Кандидат в прыжки: движение вверх есть, но подтверждения ещё нет.
     * Позиция запоминается на момент отрыва — оценивать надо именно её, а не точку
     * через тик, иначе результат уедет на четверть блока вперёд.
     */
    private Location pendingJumpLocation = null;
    private int pendingJumpTicks = 0;
    /**
     * Игрок оттолкнулся от слайма или кровати. Считается В МОМЕНТ ОТРЫВА и запоминается:
     * пока прыжок ждёт подтверждения, игрока уже подбросило, и под ногами у него пусто.
     * Проверять слайм в момент зачёта поздно — именно поэтому отскоки стали ловить промах.
     */
    private boolean pendingJumpSpecialBounce = false;
    private boolean lastJumpSpecialBounce = false;

    /**
     * Жёсткая неприкосновенность после портала: секунду после перехода промах не
     * засчитывается ни при каких условиях и это окно нельзя укоротить.
     */
    private long portalHardGraceUntil = 0L;
    private static final long PORTAL_HARD_GRACE_MILLIS = 1000L;
    private final java.util.Random jumpRandom = new java.util.Random();

    @Getter
    private Location lastPlayerJumpLocation = null;

    private final List<Waypoint> triggerWaypoints = new ArrayList<>();
    private long portalGraceUntil = 0L;
    private long portalGraceMinUntil = 0L;
    private Location portalExit = null;
    /**
     * ПРЫЖКОВЫЕ КОЛЬЦА МЕРЯЮТСЯ ВДОЛЬ ПУТИ, А НЕ ВДОЛЬ ОСИ УРОВНЯ.
     * <p>
     * Раньше у каждого кольца была одна цифра - продольная координата по оси уровня, и
     * по ней же кольца сортировались, по ней выбиралось «кольцо впереди» и по ней же
     * решалось, что кольцо пройдено. На прямом участке это одно и то же. А на боковых
     * прыжках (змейка поперёк оси, игрок смотрит в стену) все кольца одного захода
     * стоят почти на одной продольной координате: порядок между ними становился
     * случайным, указатель перепрыгивал через ещё не взятое кольцо, и следующий прыжок
     * искал своё кольцо уже за указателем - мисс. Дальше ошибка ехала по цепочке:
     * прыжок цеплялся за соседнее кольцо и получал +100/+50 вместо +300.
     * <p>
     * Теперь у кольца длина дуги пути от старта ({@link #triggerArcs}), игрок тоже
     * ведётся по дуге ({@link #playerArc}), а сторона «рано/поздно» берётся по
     * соседним отрезкам самого пути, а не по оси.
     */
    private ru.sortix.parkourbeat.game.movement.PathProgress triggerPath = null;
    private double[] triggerArcs = new double[0];
    /** Предыдущая точка пути перед кольцом (null у первой точки). */
    private org.bukkit.util.Vector[] triggerPrevPoints = new org.bukkit.util.Vector[0];
    /** Следующая точка пути после кольца (null у последней точки). */
    private org.bukkit.util.Vector[] triggerNextPoints = new org.bukkit.util.Vector[0];
    /** Где игрок сейчас на пути (длина дуги). NaN - ещё не определено. */
    private double playerArc = Double.NaN;
    /** Насколько игрок сейчас отошёл от пути. */
    private double playerPathDistance = 0.0D;

    /**
     * Запас по дуге при поиске кольца для прыжка. Дуга между двумя точками всегда не
     * короче прямой между ними, плюс проекция игрока на путь немного гуляет, - отсюда
     * запас сверх окна +50. Кольца соседнего витка змейки по дуге дальше в разы.
     */
    private static final double TRIGGER_ARC_SLACK = 1.5D;

    private int nextTriggerIndex = 0;

    /**
     * Какие прыжки уже оплачены очками в этой попытке.
     * <p>
     * Одного указателя nextTriggerIndex для этого мало. Он умеет ехать назад
     * (откат на чекпоинт, перемотка в практике), и после отката те же самые кольца
     * снова становились "неоплаченными". А главное - на трассах с разворотом и на
     * участках, идущих поперёк оси уровня, кольца сбиваются в кучу по продольной
     * координате, и игрок, прыгающий на месте, вычерпывал их одно за другим, набивая
     * комбо, не двигаясь. Флаг снимается только вместе с очками - при откате.
     */
    private boolean[] triggerScored = new boolean[0];

    /**
     * Точка отрыва прошлого ЗАСЧИТАННОГО прыжка. По ней проверяется, что между двумя
     * оплаченными прыжками игрок действительно куда-то переместился.
     */
    private Location lastScoredJumpLocation = null;

    /**
     * Сколько блоков игрок обязан пролететь между двумя оплаченными прыжками.
     * <p>
     * Даже прыжок с места на месте занимает около полусекунды, а в спринте один прыжок
     * уносит на три-четыре блока, поэтому легальную цепочку прыжков этот порог не задевает
     * никогда. Зато он полностью закрывает набивку очков прыжками в одной точке: именно
     * так работают бесконечные стрейфы с 360-модами, когда игрок крутится на пятачке и
     * снимает очки с одних и тех же колец.
     */
    private static final double MIN_TRAVEL_BETWEEN_SCORED_JUMPS = 1.0D;

    /**
     * Во сколько раз дальше окна +50 может стоять прыжок вбок, чтобы его проход всё ещё
     * считался промахом. Всё, что дальше, на пути игрока просто не лежало.
     */
    private static final double UNREACHABLE_SIDE_FACTOR = 3.0D;

    private PlayActivity(@NonNull Game game, boolean isEditorGame) {
        super(game.getPlugin(), game.getPlayer(), game.getLevel());
        this.game = game;
        this.isEditorGame = isEditorGame;
        this.game.setDisplayTimecode(isEditorGame);
        this.physicsManager = this.plugin.get(CustomPhysicsManager.class);

        if (!isEditorGame && game.getLevel().getLevelSettings().getGameSettings().getMusicTrack() != null) {
            this.sendMusicNotice(game.getPlayer());
        }
    }

    private void sendMusicNotice(@NonNull Player target) {
        ru.sortix.parkourbeat.player.PlayerSettingsManager settings =
            this.plugin.get(ru.sortix.parkourbeat.player.PlayerSettingsManager.class);

        target.sendMessage(LangOptions.level_play_music_notice.getComponent(target));

        if (!settings.shouldShowDetailedMusicNotice(target.getUniqueId())) return;
        settings.increaseMusicNoticeShown(target.getUniqueId());
        settings.save();

        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer legacy =
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand();
        target.sendMessage(PbText.of(Lang.raw(PlayerLang.of(target), "auto.play_activity.send_music_notice.1")));
        target.sendMessage(PbText.of(Lang.raw(PlayerLang.of(target), "auto.play_activity.send_music_notice.2")));
        target.sendMessage(PbText.of(Lang.raw(PlayerLang.of(target), "auto.play_activity.send_music_notice.3")));
        target.sendMessage(PbText.of(Lang.raw(PlayerLang.of(target), "auto.play_activity.send_music_notice.4")));
        target.sendMessage(PbText.of(Lang.raw(PlayerLang.of(target), "auto.play_activity.send_music_notice.5")));
        target.sendMessage(PbText.of("&f"));
        target.sendMessage(PbText.of(Lang.raw(PlayerLang.of(target), "auto.play_activity.send_music_notice.6")));
        target.sendMessage(PbText.of(Lang.raw(PlayerLang.of(target), "auto.play_activity.send_music_notice.7")));
        target.sendMessage(PbText.of(Lang.raw(PlayerLang.of(target), "auto.play_activity.send_music_notice.8")));
    }

    @NonNull
    public static CompletableFuture<PlayActivity> createAsync(@NonNull ParkourBeat plugin,
                                                              @NonNull Player player,
                                                              @NonNull UUID levelId,
                                                              boolean isEditorGame
    ) {
        UserActivity activity = plugin.get(ActivityManager.class).getActivity(player);
        if (activity instanceof PlayActivity
            && activity.getLevel().getUniqueId().equals(levelId)
            && ((PlayActivity) activity).isEditorGame == isEditorGame
        ) {
            return CompletableFuture.completedFuture((PlayActivity) activity);
        }

        GameSettings targetSettings = plugin.get(LevelsManager.class).getAvailableLevelSettings(levelId);
        if (targetSettings != null && !targetSettings.isAccessibleForPlaying(player, true)) {
            LangOptions.level_play_noaccess.sendMsg(player);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<PlayActivity> result = new CompletableFuture<>();
        ModifierSet modifiers = isEditorGame
            ? new ModifierSet()
            : plugin.get(ru.sortix.parkourbeat.rating.StatisticsManager.class)
            .getSelectedModifiers(player.getUniqueId());

        Game.createAsync(plugin, player, levelId, true, modifiers).thenAccept(game -> {
            if (game == null) {
                result.complete(null);
                return;
            }

            if (!game.getLevel().isLevelAccessibleForPlaying(player, true, true)) {
                result.complete(null);
                return;
            }

            result.complete(new PlayActivity(game, isEditorGame));
        });
        return result;
    }

    @Override
    public void startActivity() {
        physicsManager.addPlayer(player, level);
        this.game.refreshModifiers();
        this.game.resetLevelGame(LangOptions.level_play_title_preparing.getComponent(player), null, false);
        this.game.resetRunProgress();

        this.player.setGameMode(GameMode.ADVENTURE);

        for (PotionEffect effect : this.player.getActivePotionEffects()) {
            this.player.removePotionEffect(effect.getType());
        }

        this.player.getInventory().clear();
        if (this.isEditorGame) {
            this.plugin.get(ItemsManager.class).putItem(this.player, TestGameItem.class);
            this.player.setFlying(false);
            this.player.setAllowFlight(false);
        } else if (!this.isEditorGame && this.game.hasModifier(Modifier.PRACTICE)) {
            this.setupPracticeHotbar();
        }

        if (!this.isEditorGame && this.game.hasModifier(Modifier.PRACTICE)) {
            this.player.setAllowFlight(true);
            this.player.setFlying(true);
        } else {
            this.player.setFlying(false);
            this.player.setAllowFlight(false);
        }

        if (this.isTwoD()) {
            // Монетки уровня видны игроку сразу, ещё до старта.
            // Монетки светятся и в обычной игре: без подсветки их попросту не видно
            // на фоне уровня.
            ru.sortix.parkourbeat.twod.TwoDCoins.refresh(this.plugin, this.getLevel(), true);
        }

        if (this.isDigger()) {
            // Забег «Копателя» стартует следующим тиком: активность ещё доводит до ума
            // режим игры и инвентарь, а забег их сразу перебивает под себя.
            this.plugin.getServer().getScheduler().runTask(this.plugin, () ->
                this.digger().start(this.player, this.getLevel(), !this.isEditorGame));
        }

        this.plugin.get(ru.sortix.parkourbeat.tutorial.TutorialManager.class)
            .onLevelEnter(this.player, this.getLevel());

        // СТОРОНА ВЫБИРАЕТСЯ ДО СБОРКИ ТРИГГЕРОВ.
        //
        // Прыжковые кольца собираются по пути игрока, а путей на дуэльной карте два.
        // Строитель мог задать сторону заранее (тестовый забег), поэтому assign() её
        // не перебивает, а лишь занимает свободную, если выбора ещё не было.
        if (ru.sortix.parkourbeat.duel.DuelManager.isDuel(this.getLevel())) {
            this.plugin.get(ru.sortix.parkourbeat.duel.DuelManager.class)
                .assign(this.player, this.getLevel());
            // Забег собрался раньше, чем стала известна сторона, - пересобираем
            // проверку точности под нужную трассу.
            this.game.getGameMoveHandler().rebuildAccuracyChecker();
        }

        this.game.onEnterLevel();
        this.buildTriggerDistances();
        this.lastPlayerJumpLocation = null;
        this.pendingJumpLocation = null;
        this.pendingJumpTicks = 0;
        this.pendingJumpSpecialBounce = false;
        this.lastJumpSpecialBounce = false;
        this.confirmedJumpUntil = 0L;
        this.portalHardGraceUntil = 0L;

        this.plugin.getServer().getScheduler().runTask(this.plugin, this.game::applyAutoLook);
    }

    private void setupPracticeHotbar() {
        ItemStack stopItem = ItemUtils.create(Material.DIAMOND, meta -> {
            meta.displayName(net.kyori.adventure.text.Component.text("Закончить практику")
                .color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        });
        this.player.getInventory().setItem(0, stopItem);
    }

    private void buildTriggerDistances() {
        this.triggerWaypoints.clear();

        // На дуэльной карте это путь СВОЕЙ стороны. Чужие кольца в список не попадают
        // вовсе - поэтому прыгнуть на чужой стороне физически не за что: там для этого
        // игрока нет ни одного триггера.
        List<Waypoint> path = ru.sortix.parkourbeat.duel.DuelManager
            .waypointsFor(this.plugin, this.getLevel(), this.player);
        this.triggerPath = new ru.sortix.parkourbeat.game.movement.PathProgress(path);

        // Порядок колец - это порядок точек пути, никакой пересортировки по оси.
        // Список точек и так идёт от старта к финишу (редактор вставляет точку туда,
        // где она меньше всего удлиняет маршрут).
        List<Integer> pathIndexes = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            Waypoint waypoint = path.get(i);
            if (waypoint.getHeight() <= 0) continue;
            if (ru.sortix.parkourbeat.levels.PortalPathFilter
                .isHidden(this.getLevel(), waypoint.getLocation())) continue;
            this.triggerWaypoints.add(waypoint);
            pathIndexes.add(i);
        }

        int count = this.triggerWaypoints.size();
        this.triggerArcs = new double[count];
        this.triggerPrevPoints = new org.bukkit.util.Vector[count];
        this.triggerNextPoints = new org.bukkit.util.Vector[count];
        boolean usable = this.triggerPath.isUsable();
        for (int k = 0; k < count; k++) {
            int i = pathIndexes.get(k);
            this.triggerArcs[k] = usable
                ? this.triggerPath.arcAtPoint(i)
                : this.axisDistance(path.get(i).getLocation());
            this.triggerPrevPoints[k] = i > 0 ? path.get(i - 1).getLocation().toVector() : null;
            this.triggerNextPoints[k] = i + 1 < path.size() ? path.get(i + 1).getLocation().toVector() : null;
        }
        this.triggerScored = new boolean[count];
        this.resetTriggerIndexToPosition(0.0D);
    }

    /** Старое мерило: расстояние от старта по оси уровня. Только для пути из одной точки. */
    private double axisDistance(@NonNull Location location) {
        ru.sortix.parkourbeat.levels.settings.LevelSettings settings = this.getLevel().getLevelSettings();
        return Math.abs(settings.getDirectionChecker().getCoordinate(location) - settings.getStartPosition());
    }

    /**
     * Где на пути находится точка. Ищется рядом с прошлым положением игрока: на змейке
     * соседний виток бывает ближе по прямой, и глобальный поиск перекидывал бы туда.
     */
    @NonNull
    private ru.sortix.parkourbeat.game.movement.PathProgress.Projection projectOnPath(
        @NonNull Location location, double previousArc) {
        if (this.triggerPath != null && this.triggerPath.isUsable()) {
            return this.triggerPath.project(location, previousArc);
        }
        return new ru.sortix.parkourbeat.game.movement.PathProgress.Projection(this.axisDistance(location), 0.0D);
    }

    private void updatePlayerArc(@NonNull Location location) {
        ru.sortix.parkourbeat.game.movement.PathProgress.Projection projection =
            this.projectOnPath(location, this.playerArc);
        this.playerArc = projection.arcLength();
        this.playerPathDistance = projection.distanceToPath();
    }

    /**
     * Множитель сложности уровня, заданный строителем в редакторе.
     * Влияет на ширину окон попадания по прыжкам.
     */
    public double getDifficultyMultiplier() {
        try {
            return this.getLevel().getLevelSettings().getGameSettings().getDifficultyMultiplier();
        } catch (Exception e) {
            return 1.0D;
        }
    }

    /**
     * Перемотать указатель колец на текущее место игрока.
     *
     * @param playerDistance пройденное расстояние по оси уровня. Сейчас важно лишь,
     *                       старт это (0) или нет: само место берётся из позиции игрока
     *                       на пути, потому что по оси на боковых участках его не понять.
     */
    public void resetTriggerIndexToPosition(double playerDistance) {
        double okRadius = JumpTriggerEvaluator.frontOkRadius(this.getDifficultyMultiplier());

        this.pendingJumpLocation = null;
        this.pendingJumpTicks = 0;

        if (playerDistance <= 0.0D) {
            // Старт забега: игрок стоит на спавне, путь начинается с нуля.
            this.playerArc = 0.0D;
            this.playerPathDistance = 0.0D;
        } else {
            // Откат: игрок уже перенесён на точку отката. Ищем его по всему пути -
            // прошлое положение осталось там, где он умер, и окно от него ничего не даст.
            this.playerArc = Double.NaN;
            this.updatePlayerArc(this.player.getLocation());
        }

        this.nextTriggerIndex = 0;
        while (this.nextTriggerIndex < this.triggerArcs.length) {
            if (this.triggerArcs[this.nextTriggerIndex] >= this.playerArc - okRadius) {
                break;
            }
            this.nextTriggerIndex++;
        }

        // Перемотка назад - это откат состояния забега целиком: вместе с указателем
        // возвращаются и очки (снимок чекпоинта), поэтому кольца впереди точки отката
        // снова становятся неоплаченными и их можно честно взять заново. Всё, что
        // осталось позади, оплаченным и остаётся - иначе откат превращался бы в способ
        // получить очки за одни и те же прыжки дважды.
        for (int i = this.nextTriggerIndex; i < this.triggerScored.length; i++) {
            this.triggerScored[i] = false;
        }
        this.lastScoredJumpLocation = null;
    }

    /** Уровень двумерный: обычная логика забега к нему не применяется вообще. */
    /** Карта «Копателя»: забег там ведёт свой менеджер, обычная логика ему только мешает. */
    private boolean isDigger() {
        return ru.sortix.parkourbeat.digger.DiggerManager.isDigger(this.getLevel());
    }

    private ru.sortix.parkourbeat.digger.DiggerManager digger() {
        return this.plugin.get(ru.sortix.parkourbeat.digger.DiggerManager.class);
    }

    private boolean isTwoD() {
        return ru.sortix.parkourbeat.twod.TwoDManager.isTwoD(this.getLevel());
    }

    @NonNull
    private ru.sortix.parkourbeat.twod.TwoDManager twoD() {
        return this.plugin.get(ru.sortix.parkourbeat.twod.TwoDManager.class);
    }

    @Override
    public void on(@NonNull PlayerMoveEvent event) {
        // Идёт отсчёт перед дуэлью: пока не прозвучало "ПОЕХАЛИ", не двигается никто.
        // Полсекунды форы в гонке решают исход, поэтому проверка стоит самой первой.
        if (this.plugin.get(ru.sortix.parkourbeat.duel.DuelManager.class).isFrozen(this.player)) {
            Location frozenTo = event.getTo();
            // Отменяем только СМЕЩЕНИЕ. Отмена поворота головы вернула бы игроку и
            // взгляд тоже - камера дёргалась бы всё время отсчёта.
            if (frozenTo != null && (frozenTo.getX() != event.getFrom().getX()
                || frozenTo.getY() != event.getFrom().getY()
                || frozenTo.getZ() != event.getFrom().getZ())) {
                event.setCancelled(true);
            }
            return;
        }

        // ПАУЗА ПОСЛЕ ОТКАТА НА ЧЕКПОИНТ.
        //
        // Одного замедления мало: в майнкрафте прыгать можно при любом замедлении, а
        // прыжок с Ctrl ещё и толкает вперёд. Все жмут пробел сразу после телепорта и
        // улетают с чекпоинта раньше отсчёта. Поэтому смещение режется здесь, как в
        // отсчёте дуэли: вверх и вбок нельзя, поворачивать голову и падать - можно.
        if (!this.isTwoD() && !this.isDigger() && this.game.isCheckpointPaused()) {
            Location anchor = this.game.getCheckpointPauseAnchor();
            Location to = event.getTo();
            if (anchor != null && to != null
                && (to.getX() != anchor.getX()
                || to.getZ() != anchor.getZ()
                || to.getY() > anchor.getY())) {
                // Не отменяем, а переписываем: отмена вернула бы игрока в from, а там
                // после телепорта вполне может лежать место смерти.
                Location fixed = anchor.clone();
                fixed.setYaw(to.getYaw());
                fixed.setPitch(to.getPitch());
                // Падать вниз разрешаем: чекпоинт может стоять чуть над блоком.
                if (to.getY() < anchor.getY()) fixed.setY(to.getY());
                event.setTo(fixed);
            }
            return;
        }

        // Идёт вступительный облёт туториала: игрок сейчас наблюдатель, его двигает
        // камера, и обычная логика забега тут только мешает.
        if (this.plugin.get(ru.sortix.parkourbeat.tutorial.TutorialManager.class)
            .isCutscene(this.player)) {
            return;
        }

        if (this.isTwoD()) {
            // Игра начинается, когда игрок наступает на блок в начале линии.
            this.twoD().handlePlayMove(this.player, this.getLevel());
            return;
        }

        // В «Копателе» игрока везёт носитель: своих шагов у него нет вообще.
        if (this.isDigger()) return;

        Game.State state = this.game.getCurrentState();
        GameMoveHandler gameMoveHandler = this.game.getGameMoveHandler();

        if (state == Game.State.PREPARING) {
            gameMoveHandler.onPreparingState(event);
        } else if (state == Game.State.READY) {
            gameMoveHandler.onReadyState(this.player);
        } else if (state == Game.State.RUNNING) {
            gameMoveHandler.onRunningState(this.player, event.getFrom(), event.getTo());
            if (event.getTo() != null) this.updatePlayerArc(event.getTo());
            this.detectJump(event);
            this.evaluateTriggers();
        }
    }

    /**
     * Сервер сказал, что игрок именно прыгнул. Если кандидат уже висит — засчитываем его
     * немедленно, не дожидаясь подтверждения по следующему тику.
     */
    @Override
    public void on(@NonNull com.destroystokyo.paper.event.player.PlayerJumpEvent event) {
        if (this.game.getCurrentState() != Game.State.RUNNING) return;
        // Прыжок на паузе не засчитывается и не случается: игрок стоит на месте.
        if (this.game.isCheckpointPaused()) {
            event.setCancelled(true);
            return;
        }
        this.confirmedJumpUntil = System.currentTimeMillis() + 250L;

        if (this.pendingJumpLocation != null) {
            Location candidate = this.pendingJumpLocation;
            this.pendingJumpLocation = null;
            this.pendingJumpTicks = 0;
            this.lastJumpSpecialBounce = this.pendingJumpSpecialBounce || this.detectSpecialBounce();
            this.commitJump(candidate);
        }
    }

    private void detectJump(@NonNull PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        boolean movingUp = to.getY() > from.getY();
        boolean onGround = this.player.isOnGround();

        // ПОДТВЕРЖДЕНИЕ КАНДИДАТА.
        //
        // Раньше прыжок засчитывался сразу по признаку "вверх и не на земле". Беда в том,
        // что автоматический шаг на полублок выглядит ровно так же: игрок поднимается на
        // 0.5 блока, и на один тик клиент присылает onGround=false. Такой фантомный
        // "прыжок" съедал кулдаун, и настоящий прыжок через сотню миллисекунд уже
        // игнорировался — отсюда и незасчитанные прыжки с полублоков.
        //
        // Теперь кандидат живёт один тик. Настоящий прыжок к следующему тику всё ещё в
        // воздухе, шаг на полублок — уже на земле, и кандидат просто выбрасывается,
        // не потратив кулдаун и не подняв ложный промах.
        if (this.pendingJumpLocation != null) {
            Location candidate = this.pendingJumpLocation;
            this.pendingJumpTicks++;

            if (onGround) {
                // Это был шаг на блок, а не прыжок.
                this.pendingJumpLocation = null;
                this.pendingJumpTicks = 0;
                this.jumping = false;
                return;
            }
            if (this.pendingJumpTicks >= 1) {
                this.pendingJumpLocation = null;
                this.pendingJumpTicks = 0;
                this.lastJumpSpecialBounce = this.pendingJumpSpecialBounce
                    || this.detectSpecialBounce();
                this.commitJump(candidate);
            }
            return;
        }

        if (onGround) {
            this.jumping = false;
            return;
        }
        if (this.jumping || !movingUp) return;

        long now = System.currentTimeMillis();
        if (now - this.lastJumpAt < JUMP_COOLDOWN_MILLIS) return;
        this.jumping = true;

        Location candidate = this.player.getLocation().clone();
        boolean specialBounce = this.detectSpecialBounce();

        // Прыжок уже подтверждён сервером в этом же тике — ждать нечего.
        if (now < this.confirmedJumpUntil) {
            this.lastJumpSpecialBounce = specialBounce;
            this.commitJump(candidate);
            return;
        }

        // Иначе ждём один тик и смотрим, остался ли игрок в воздухе.
        this.pendingJumpLocation = candidate;
        this.pendingJumpSpecialBounce = specialBounce;
        this.pendingJumpTicks = 0;
    }

    /**
     * Засчитать прыжок из точки отрыва: найти ближайший триггер и оценить попадание.
     */
    private void commitJump(@NonNull Location playerLoc) {
        this.lastJumpAt = System.currentTimeMillis();
        this.jumping = true;
        this.lastPlayerJumpLocation = playerLoc.clone();

        int bestIdx = -1;
        double minEffectiveDelta = Double.MAX_VALUE;

        double difficulty = this.getDifficultyMultiplier();
        double okRadius = JumpTriggerEvaluator.frontOkRadius(difficulty);
        double maxYDistance = JumpTriggerEvaluator.maxYDistance(difficulty);

        // Место отрыва на пути. Ищем рядом с текущим положением игрока, а не по всему
        // пути: соседний виток змейки по прямой бывает ближе, чем свой.
        double jumpArc = this.projectOnPath(playerLoc, this.playerArc).arcLength();
        double arcWindow = okRadius + TRIGGER_ARC_SLACK;

        for (int i = this.nextTriggerIndex; i < this.triggerWaypoints.size(); i++) {
            double triggerArc = this.triggerArcs[i];
            // Кольца идут по пути по порядку: дальше только ещё более далёкие.
            if (triggerArc > jumpArc + arcWindow) break;
            // Кольцо, за которое уже заплачено, второй раз очков не приносит.
            if (i < this.triggerScored.length && this.triggerScored[i]) continue;
            // Далеко позади по пути - это уже пройденное кольцо, его промах выпишет
            // evaluateTriggers. Сюда же отсекается соседний виток змейки.
            if (triggerArc < jumpArc - arcWindow) continue;

            Location wLoc = this.triggerWaypoints.get(i).getLocation();
            double yDist = Math.abs(playerLoc.getY() - wLoc.getY());
            if (yDist > maxYDistance) continue;

            // Расстояние до кольца по горизонтали. На прямом участке это ровно то же,
            // что было раньше: hypot(продольное, боковое) по осям уровня.
            double effectiveDelta = Math.hypot(playerLoc.getX() - wLoc.getX(), playerLoc.getZ() - wLoc.getZ());
            if (effectiveDelta < minEffectiveDelta) {
                minEffectiveDelta = effectiveDelta;
                bestIdx = i;
            }
        }

        if (bestIdx != -1 && minEffectiveDelta <= okRadius) {
            double evaluationDelta = this.sideOfTrigger(bestIdx, playerLoc) * minEffectiveDelta;
            JumpResult result = JumpTriggerEvaluator.evaluate(evaluationDelta, difficulty);

            if (result != JumpResult.MISS) {
                if (this.hasTravelledSinceLastScoredJump(playerLoc)) {
                    this.game.registerJump(result);
                    this.plugin.get(ru.sortix.parkourbeat.replay.ReplayManager.class).recordJump(this.player, result);
                    if (bestIdx < this.triggerScored.length) this.triggerScored[bestIdx] = true;
                    this.lastScoredJumpLocation = playerLoc.clone();
                    // Указатель НЕ перепрыгивает через кольца перед взятым: раньше
                    // nextTriggerIndex = bestIdx + 1 молча хоронил их, и если кольца
                    // стояли в неверном порядке, своё кольцо следующего прыжка
                    // оказывалось за указателем. Пропущенное кольцо честно станет
                    // промахом, когда игрок уйдёт от него по пути.
                    while (this.nextTriggerIndex < this.triggerScored.length
                        && this.triggerScored[this.nextTriggerIndex]) {
                        this.nextTriggerIndex++;
                    }
                }
                // Прыжок с того же пятачка, что и предыдущий засчитанный, просто не
                // судится: ни очков, ни комбо, ни промаха с уроном. Кольцо остаётся
                // непройденным, и если игрок так его и не перепрыгнет по-человечески,
                // промах ему выпишет обычная проверка пройденных триггеров.
            } else {
                this.handleMissOrSpecialCases();
            }
        } else {
            this.handleMissOrSpecialCases();
        }

        this.fireJumpEffect();
    }

    /**
     * Успел ли игрок отойти от точки, из которой прыгал в прошлый раз за очки.
     * <p>
     * Прыжок из той же точки, что и предыдущий оплаченный, - это не второй прыжок,
     * а второе нажатие пробела на одном и том же месте. Легальную игру порог не трогает:
     * между двумя прыжками игрок всегда пролетает несколько блоков.
     */
    private boolean hasTravelledSinceLastScoredJump(@NonNull Location playerLoc) {
        if (this.lastScoredJumpLocation == null) return true;
        if (this.lastScoredJumpLocation.getWorld() != playerLoc.getWorld()) return true;
        return this.lastScoredJumpLocation.distanceSquared(playerLoc)
            >= MIN_TRAVEL_BETWEEN_SCORED_JUMPS * MIN_TRAVEL_BETWEEN_SCORED_JUMPS;
    }

    /**
     * Прыжок раньше кольца (-1) или позже (+1) - ПО НАПРАВЛЕНИЮ ПУТИ в этом месте.
     * <p>
     * Раньше знак брался по оси уровня. На боковом прыжке путь идёт поперёк оси,
     * продольная разница там - доли блока шума, и знак выпадал случайно: идеальный
     * прыжок с края блока судился по узким задним радиусам и давал +100/+50.
     * <p>
     * Теперь смотрим, к какому из двух отрезков пути у кольца игрок ближе: к входящему
     * (ещё не добежал) или к исходящему (уже пробежал точку).
     */
    private double sideOfTrigger(int triggerIndex, @NonNull Location playerLoc) {
        org.bukkit.util.Vector point = this.triggerWaypoints.get(triggerIndex).getLocation().toVector();
        org.bukkit.util.Vector prev = this.triggerPrevPoints[triggerIndex];
        org.bukkit.util.Vector next = this.triggerNextPoints[triggerIndex];
        double px = playerLoc.getX();
        double pz = playerLoc.getZ();

        if (prev != null && next != null) {
            double distIn = horizontalDistanceToSegment(px, pz, prev, point);
            double distOut = horizontalDistanceToSegment(px, pz, point, next);
            if (Math.abs(distIn - distOut) > 1.0E-4D) {
                return distOut < distIn ? 1.0D : -1.0D;
            }
        }

        // Ничья (игрок ровно на биссектрисе угла) или у кольца только один сосед:
        // решаем по направлению пути через кольцо.
        double dirX = 0.0D;
        double dirZ = 0.0D;
        if (prev != null) {
            double len = Math.hypot(point.getX() - prev.getX(), point.getZ() - prev.getZ());
            if (len > 1.0E-6D) {
                dirX += (point.getX() - prev.getX()) / len;
                dirZ += (point.getZ() - prev.getZ()) / len;
            }
        }
        if (next != null) {
            double len = Math.hypot(next.getX() - point.getX(), next.getZ() - point.getZ());
            if (len > 1.0E-6D) {
                dirX += (next.getX() - point.getX()) / len;
                dirZ += (next.getZ() - point.getZ()) / len;
            }
        }
        if (Math.abs(dirX) < 1.0E-6D && Math.abs(dirZ) < 1.0E-6D) {
            // Путь здесь вертикальный или разворачивается на месте - остаётся ось уровня.
            DirectionChecker checker = this.getLevel().getLevelSettings().getDirectionChecker();
            double playerCoord = checker.getCoordinate(playerLoc);
            double wCoord = checker.getCoordinate(this.triggerWaypoints.get(triggerIndex).getLocation());
            double signedDelta = checker.isNegative() ? wCoord - playerCoord : playerCoord - wCoord;
            return signedDelta > 0 ? 1.0D : -1.0D;
        }
        double dot = (px - point.getX()) * dirX + (pz - point.getZ()) * dirZ;
        return dot > 0 ? 1.0D : -1.0D;
    }

    private static double horizontalDistanceToSegment(double px, double pz,
                                                      @NonNull org.bukkit.util.Vector a,
                                                      @NonNull org.bukkit.util.Vector b) {
        double abX = b.getX() - a.getX();
        double abZ = b.getZ() - a.getZ();
        double lengthSquared = abX * abX + abZ * abZ;
        double t = lengthSquared <= 1.0E-9D
            ? 0.0D
            : ((px - a.getX()) * abX + (pz - a.getZ()) * abZ) / lengthSquared;
        if (t < 0.0D) t = 0.0D;
        else if (t > 1.0D) t = 1.0D;
        double cx = a.getX() + abX * t;
        double cz = a.getZ() + abZ * t;
        return Math.hypot(px - cx, pz - cz);
    }

    private void handleMissOrSpecialCases() {
        org.bukkit.util.BoundingBox pBox = this.player.getBoundingBox();

        org.bukkit.util.BoundingBox headBox = new org.bukkit.util.BoundingBox(
            pBox.getMinX() + 0.05,
            pBox.getMaxY(),
            pBox.getMinZ() + 0.05,
            pBox.getMaxX() - 0.05,
            pBox.getMaxY() + 0.8,
            pBox.getMaxZ() - 0.05
        );

        boolean isHeadHitter = ru.sortix.parkourbeat.world.BoundingBoxUtils.isBoundingBoxOverlapsWithAnyBlock(
            this.player.getWorld(),
            headBox,
            true,
            true
        );

        Location feet = this.player.getLocation();
        Block feetBlock = feet.getBlock();
        Block belowBlock = feet.clone().subtract(0, 0.5, 0).getBlock();

        boolean isSpecialBounce = this.lastJumpSpecialBounce
            || isSpecialBounceBlock(feetBlock.getType())
            || isSpecialBounceBlock(belowBlock.getType());

        // В ВОДЕ ПРОМАХ НЕ ЗАСЧИТЫВАЕТСЯ. Под водой прыжка как такового нет:
        // клиент всплывает и постоянно "двигается вверх", из-за чего детектор прыжков
        // ловил ложные срабатывания и вешал MISS с уроном.
        if (this.isJudgementImmune()) return;

        if (!isHeadHitter && !isSpecialBounce) {
            this.game.registerJump(JumpResult.MISS);
            this.plugin.get(ru.sortix.parkourbeat.replay.ReplayManager.class).recordJump(this.player, JumpResult.MISS);
            if (!this.isEditorGame) {
                this.game.applyDamage(2.0D);
            }
        }
    }

    /**
     * Под ногами слайм или кровать — значит игрока подбросило, а не он прыгнул сам.
     * Смотрим и на блок ног, и на полблока ниже, и на блок ниже: во время отскока
     * игрок уже отрывается от поверхности, и одной точки замера не хватает.
     */
    private boolean detectSpecialBounce() {
        Location feet = this.player.getLocation();
        return isSpecialBounceBlock(feet.getBlock().getType())
            || isSpecialBounceBlock(feet.clone().subtract(0, 0.5, 0).getBlock().getType())
            || isSpecialBounceBlock(feet.clone().subtract(0, 1.0, 0).getBlock().getType());
    }

    private static boolean isSpecialBounceBlock(@NonNull Material material) {
        return material == Material.SLIME_BLOCK || isBed(material);
    }

    private static boolean isBed(@NonNull Material material) {
        return org.bukkit.Tag.BEDS.isTagged(material) || material.name().endsWith("_BED");
    }

    private void evaluateTriggers() {
        if (this.game.getCurrentState() != Game.State.RUNNING) return;
        if (this.nextTriggerIndex >= this.triggerWaypoints.size()) return;
        if (Double.isNaN(this.playerArc)) return;

        boolean grace = this.isJudgementImmune();
        double difficulty = this.getDifficultyMultiplier();

        while (this.nextTriggerIndex < this.triggerWaypoints.size()) {
            // Уже оплаченное кольцо просто проезжаем.
            if (this.nextTriggerIndex < this.triggerScored.length && this.triggerScored[this.nextTriggerIndex]) {
                this.nextTriggerIndex++;
                continue;
            }

            // Насколько игрок ушёл от кольца ВПЕРЁД ПО ПУТИ. На боковых участках ось
            // уровня тут ничего не говорит: игрок бежит поперёк неё.
            double passedBy = this.playerArc - this.triggerArcs[this.nextTriggerIndex];

            if (JumpTriggerEvaluator.isPassedUnjumped(passedBy, difficulty)) {
                // Игрок далеко от пути (ушёл в сторону, вылетел за трассу) - кольцо
                // на его дороге не лежало, промах за него не выписываем.
                boolean wasReachable = this.playerPathDistance <= UNREACHABLE_SIDE_FACTOR
                    * JumpTriggerEvaluator.frontOkRadius(difficulty);

                if (!grace && wasReachable) {
                    this.game.registerJump(JumpResult.MISS);
                    this.plugin.get(ru.sortix.parkourbeat.replay.ReplayManager.class).recordJump(this.player, JumpResult.MISS);
                    if (!this.isEditorGame) {
                        this.game.applyDamage(2.0D);
                    }
                }
                this.nextTriggerIndex++;
            } else {
                break;
            }
        }
    }

    /**
     * Прыжки сейчас не судятся вообще: ни промаха, ни урона, ни сброса комбо.
     * Это либо льгота после телепорта портала, либо вода — в воде игрок физически
     * не может попадать по триггерам, и наказывать его за это нельзя.
     */
    public boolean isJudgementImmune() {
        // НА 360-УРОВНЕ ПРЫЖКИ НЕ СУДЯТСЯ ВООБЩЕ.
        //
        // Прыжковые кольца берутся с пути из частиц, а пути на 360-уровне нет: там
        // просто бегают по трубе. Значит, любой прыжок оказывался «мимо кольца» -
        // промах, минус точность и два сердца за то, что игрок вообще прыгнул.
        if (ru.sortix.parkourbeat.threesixty.ThreeSixtyManager.isThreeSixty(this.getLevel())) return true;
        if (this.isPortalGrace()) return true;
        return Game.isInWaterCached(this.player);
    }

    public boolean isPortalGrace() {
        long now = System.currentTimeMillis();

        // ЖЁСТКАЯ СЕКУНДА. Обрезать её нельзя ничем: игрока только что перенесло,
        // и любой промах в это время — следствие телепорта, а не игры.
        if (now < this.portalHardGraceUntil) return true;

        if (now >= this.portalGraceUntil) return false;

        if (this.portalExit != null && now >= this.portalGraceMinUntil) {
            Location current = this.player.getLocation();
            if (current.getWorld() == this.portalExit.getWorld()
                && current.distanceSquared(this.portalExit) < 9.0D) {
                this.portalGraceUntil = Math.min(this.portalGraceUntil, now + 200L);
                this.portalExit = null;
            }
        }
        return true;
    }

    public void onPortalTeleport() {
        this.onPortalTeleport(null);
    }

    /**
     * Льгота после принудительного телепорта (откат на чекпоинт). Игрока только что
     * перенесло, прыжки в этот момент судить нельзя.
     */
    public void applyJudgementGrace(long millis) {
        // Кандидат в прыжки после телепорта недействителен: он относился к другому месту.
        this.pendingJumpLocation = null;
        this.pendingJumpTicks = 0;
        long now = System.currentTimeMillis();
        this.portalGraceUntil = Math.max(this.portalGraceUntil, now + Math.max(0L, millis));
        this.portalHardGraceUntil = Math.max(this.portalHardGraceUntil, now + Math.max(0L, millis));
        this.portalGraceMinUntil = now;
        this.portalExit = null;
    }

    public void onPortalTeleport(Location exit) {
        this.pendingJumpLocation = null;
        this.pendingJumpTicks = 0;
        long now = System.currentTimeMillis();
        this.portalHardGraceUntil = now + PORTAL_HARD_GRACE_MILLIS;
        int ping = 0;
        try {
            ping = this.plugin.get(ru.sortix.parkourbeat.player.PingManager.class).getPing(this.player);
        } catch (Exception ignored) {
        }

        long window = 450L + Math.max(0, ping) * 3L;
        if (window > 3000L) window = 3000L;

        this.portalGraceUntil = now + window;
        this.portalGraceMinUntil = now + 150L;
        this.portalExit = exit == null ? null : exit.clone();

        try {
            this.game.getGameMoveHandler().applyTeleportGrace(Math.max(1000L, window));
        } catch (Exception ignored) {
        }
        // Игрока перенесло: место на пути ищем заново и целиком, окно от прошлого
        // положения тут бессмысленно. Если выход известен - меряем по нему, телепорт
        // мог ещё не долететь.
        this.playerArc = Double.NaN;
        this.updatePlayerArc(exit != null ? exit : this.player.getLocation());

        if (this.triggerWaypoints.isEmpty()) return;

        while (this.nextTriggerIndex < this.triggerWaypoints.size()) {
            if (this.triggerArcs[this.nextTriggerIndex] > this.playerArc) break;
            this.nextTriggerIndex++;
        }
    }

    private void fireJumpEffect() {
        ru.sortix.parkourbeat.levels.settings.LightShowSettings lightShow = this.getLevel().getLightShow();
        long songTimeMillis = this.game.getSongTimeMillis();

        ru.sortix.parkourbeat.levels.settings.JumpZone active = null;
        for (ru.sortix.parkourbeat.levels.settings.JumpZone zone : lightShow.getJumpZones()) {
            if (zone.contains(songTimeMillis)) {
                active = zone;
                break;
            }
        }
        if (active == null) active = lightShow.getDefaultJumpTrigger();
        if (active == null) return;

        if (active.getEffects().contains(ru.sortix.parkourbeat.levels.settings.JumpEffect.SOUND)) {
            ru.sortix.parkourbeat.world.JumpEffectSender.play(this.plugin, this.player,
                ru.sortix.parkourbeat.levels.settings.JumpEffect.SOUND, active.getSoundKey());
        }

        ru.sortix.parkourbeat.levels.settings.JumpEffect effect = active.nextEffect(this.jumpRandom);
        if (effect == null || effect == ru.sortix.parkourbeat.levels.settings.JumpEffect.SOUND) return;

        if (effect == ru.sortix.parkourbeat.levels.settings.JumpEffect.TIME_PUSH) {
            ru.sortix.parkourbeat.levels.LightShowRunner runner = this.game.getLightShowRunner();
            if (runner != null) runner.addTimePush(300L);
            return;
        }
        ru.sortix.parkourbeat.world.JumpEffectSender.play(this.plugin, this.player, effect, active.getSoundKey());
    }

    public void onPracticeInteract(PlayerInteractEvent event) {
        if (!this.isEditorGame && this.game.hasModifier(Modifier.PRACTICE)
            && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            ItemStack item = event.getItem();
            if (item != null && item.getType() == Material.DIAMOND) {
                event.setCancelled(true);
                this.player.getInventory().setItem(0, null);
                this.game.forceStopLevelGame();
                this.game.setCurrentState(Game.State.READY);
                TeleportUtils.teleportAsync(this.plugin, this.player, this.getLevel().getSpawn());
            }
        }
    }

    @Override
    public void onTick() {
        if (this.isTwoD()) return;
        if (this.isDigger()) return;

        this.recordReplayFrame();
        this.checkFallZones();
        if (this.game.getCurrentState() == Game.State.RUNNING) {
            this.updatePlayerArc(this.player.getLocation());
        }
        this.evaluateTriggers();

        if (!this.isEditorGame && this.game.getCurrentState() == Game.State.RUNNING && this.game.hasModifier(Modifier.PRACTICE)) {
            ItemStack slot0 = this.player.getInventory().getItem(0);
            if (slot0 == null || slot0.getType() != Material.DIAMOND) {
                this.setupPracticeHotbar();
            }
        }
    }

    @Override
    public void on(@NonNull PlayerToggleSprintEvent event) {
        if (this.isTwoD()) return;
        if (this.isDigger()) return;
        if (this.game.getCurrentState() == Game.State.RUNNING) {
            this.game.getGameMoveHandler().onRunningState(event);
        }
    }

    @Override
    public void on(@NonNull PlayerToggleSneakEvent event) {
        // SHIFT на 2D-уровне обрабатывается самим забегом: игрок слезает с камеры.
        if (this.isTwoD()) return;
        // В «Копателе» слезть с носителя нельзя вовсе - это отменяется в его менеджере.
        if (this.isDigger()) return;
        // На 360-уровне SHIFT - обычное движение (спуститься с уступа, пройти под
        // блоком), а не остановка забега. Уровень без урона за отпущенный бег не
        // должен убивать и за приседание: это то же наказание другим способом.
        if (!this.game.getGameMoveHandler().isSprintDamageEnabled()) return;
        if (event.isSneaking() && this.game.getCurrentState() == Game.State.RUNNING) {
            if (!this.isEditorGame && this.game.hasModifier(Modifier.PRACTICE)) {
                return;
            }
            this.game.failLevel(LangOptions.level_play_title_stopped.getComponent(player), null);
        }
    }

    @Override
    public boolean isEditorMode() {
        return this.isEditorGame;
    }

    @Override
    public int getFallHeight() {
        return ru.sortix.parkourbeat.levels.FallZoneRenderer
            .resolveFallHeight(this.level, this.player, this.getFallHeight(false));
    }

    private void recordReplayFrame() {
        if (this.game.getCurrentState() != Game.State.RUNNING) return;
        if (this.isEditorGame) return;
        this.plugin.get(ru.sortix.parkourbeat.replay.ReplayManager.class).recordFrame(this.player);
    }

    private void checkFallZones() {
        if (this.game.getCurrentState() != Game.State.RUNNING) return;
        if (!ru.sortix.parkourbeat.levels.FallZoneRenderer
            .isBelowDeathLine(this.level, this.player, this.getFallHeight(false))) return;

        // Настроенная строителем зона падения работает всегда, это запланированная смерть.
        // А вот высота по умолчанию за пределами пути ни на чём не основана - там не убиваем.
        if (!this.isInsideConfiguredFallZone() && this.isOutsidePathSpan()) return;

        this.onPlayerFall();
    }

    private boolean isInsideConfiguredFallZone() {
        try {
            if (this.level.getLightShow().getFallZones().isEmpty()) return false;
            // Зона падения ставится палочкой по МЕСТУ на уровне, значит и проверять её
            // надо по месту игрока, а не по часам песни (см. FallZoneRenderer).
            int timeMillis = ru.sortix.parkourbeat.levels.LightShowPositions
                .toTimeMillis(this.level, this.player.getLocation());
            return ru.sortix.parkourbeat.levels.FallZoneRenderer
                .findZone(this.level, timeMillis) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onPlayerFall() {
        if (this.isDigger()) {
            // Падать в «Копателе» не с чего: игрок сидит на носителе и не касается земли.
            // Если он всё-таки провалился, возвращаем его на спавн, а не валим забег.
            if (!this.digger().isPlaying(this.player)) {
                TeleportUtils.teleportAsync(this.plugin, this.player, this.getLevel().getSpawn());
            }
            return;
        }
        if (this.isTwoD()) {
            // В 2D падение обрабатывает сам забег, а вне забега игрока просто
            // возвращает на спавн-лобби уровня.
            if (!this.twoD().isPlaying(this.player)) {
                TeleportUtils.teleportAsync(this.plugin, this.player, this.getLevel().getSpawn());
            }
            return;
        }
        this.game.failLevel(LangOptions.level_play_title_fall.getComponent(player), null);
    }

    @Override
    public void endActivity() {
        this.plugin.get(ru.sortix.parkourbeat.tutorial.TutorialManager.class)
            .onLevelLeave(this.player);

        if (this.isTwoD()) {
            this.twoD().stopGame(this.player, false);
        }
        if (this.isDigger()) {
            this.digger().stop(this.player);
        }
        physicsManager.purgePlayer(player);
        this.player.setFlying(false);
        this.player.setAllowFlight(false);
        this.game.forceStopLevelGame();
        this.game.setCurrentState(Game.State.PREPARING);
        this.game.shutdown();

        // Сторона освобождается сразу: иначе следующий игрок не смог бы занять первую,
        // а сам ушедший вернулся бы на уровень с уже занятой стороной.
        this.plugin.get(ru.sortix.parkourbeat.duel.DuelManager.class).release(this.player);
    }
}
