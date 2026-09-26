package ru.sortix.parkourbeat.activity.type;

import lombok.Getter;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.game.MusicMode;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.rating.JumpResult;
import ru.sortix.parkourbeat.levels.settings.SkyType;
import ru.sortix.parkourbeat.player.PlayerSettingsManager;
import ru.sortix.parkourbeat.player.music.MusicTrack;
import ru.sortix.parkourbeat.player.music.MusicTracksManager;
import ru.sortix.parkourbeat.player.music.platform.MusicPlatform;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.text.PbText;
import ru.sortix.parkourbeat.world.TeleportUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Наблюдение за игроком на уровне.
 * <p>
 * Главная сложность здесь - музыка. Раньше наблюдателю просто дёргали
 * {@code startPlayingTrackFull()}, не выдав ему ресурспак уровня: клиент играл то, что
 * лежало в его текущем паке (обычно базовый пак лобби), то есть тишину или чужой трек.
 * Теперь пак трека уровня выдаётся явно, воспроизведение начинается только после
 * подтверждения клиента, и оно жёстко привязано к состоянию забега того, за кем смотрим.
 */
public class SpectateActivity extends UserActivity {

    /**
     * Насколько поздно ещё можно подхватить уже идущий забег.
     * <p>
     * Трек всегда играется с начала: перемотки в ресурспак-музыке нет. Если забег идёт
     * дольше этого времени, включать музыку бессмысленно - она будет рассинхронизирована
     * с тем, что игрок видит. В таком случае ждём следующей попытки.
     */
    private static final long MAX_JOIN_LAG_MILLIS = 2500L;

    private enum PackState {
        /** У уровня нет трека - музыки не будет вообще. */
        ABSENT,
        LOADING,
        READY,
        FAILED
    }

    @Getter
    private @Nullable UUID targetPlayerId = null;

    /**
     * Игра того, за кем наблюдаем. Обновляется каждый тик и читается табло.
     */
    @Getter
    private @Nullable Game targetGame = null;

    private volatile PackState packState = PackState.LOADING;
    private boolean musicPlaying = false;
    private boolean targetRunning = false;
    /** Забег начался, пока пак ещё грузился: включим музыку сразу после подтверждения. */
    private boolean startMusicWhenReady = false;
    private int searchCooldownTicks = 0;
    /**
     * Счётчики попаданий цели на прошлом тике.
     * <p>
     * Раньше показывался прирост очков, но он умножается на комбо и модификаторы - отсюда
     * и брались "+342". Игроку показывают базовую цену прыжка, поэтому и здесь считаем
     * по счётчикам попаданий: какой из них вырос, такой результат и произошёл.
     */
    private int lastPerfect = -1;
    private int lastGood = 0;
    private int lastOk = 0;
    private int lastMiss = 0;

    /** Камеру отцепил сам зритель (Shift) - насильно возвращать её не надо. */
    private boolean cameraReleasedByPlayer = false;
    private float lastKnownProgress = 0f;
    private @Nullable Game lastReportedGame = null;

    /**
     * С какого расстояния считаем, что клиент отстал от цели и мир нужно догрузить.
     * Обычный забег в этот радиус укладывается, а телепорт на старт - уже нет.
     */
    private static final double FOLLOW_DISTANCE_SQUARED = 64 * 64;

    public SpectateActivity(@NonNull ParkourBeat plugin, @NonNull Player player, @NonNull Level level) {
        super(plugin, player, level);
    }

    // ==================== ЗАПУСК ====================

    @Override
    public void startActivity() {
        this.player.setGameMode(GameMode.SPECTATOR);
        this.level.getLightShow().getBaseSky().apply(this.player);
        this.level.getLevelSettings().getParticleController().startSpawnParticles(this.player);

        // Наблюдателю тоже показываем обе трассы дуэли: он смотрит за чужим забегом
        // и заранее не знает, за какую сторону тот бежит.
        if (ru.sortix.parkourbeat.duel.DuelManager.isDuel(this.level)) {
            this.plugin.get(ru.sortix.parkourbeat.duel.DuelManager.class)
                .showBothPaths(this.level, this.player);
        }

        LangOptions.level_spectate_success.sendMsg(player,
            new Placeholders("%level%", ((TextComponent) this.level.getDisplayName()).content()));

        this.requestLevelPack();

        Player target = this.findTarget();
        if (target != null) {
            this.setTargetPlayer(target);
        } else {
            this.player.sendMessage(Lang.text(this.player, "spectate.nobody"));
        }

    }

    /**
     * Выдаёт наблюдателю ресурспак уровня: без него в клиенте просто нет звуков трека.
     * Текстуры уровня передаются явно - активность в этот момент уже наша, но полагаться
     * на это не стоит, вызов может произойти и до переключения.
     */
    private void requestLevelPack() {
        MusicTrack track = this.level.getLevelSettings().getGameSettings().getMusicTrack();
        if (track == null || !track.isStillAvailable()) {
            this.packState = PackState.ABSENT;
            return;
        }

        UUID texturesLevelId = this.level.getLevelSettings().getGameSettings().isCustomTextures()
            ? this.level.getUniqueId() : null;

        this.packState = PackState.LOADING;
        track.setResourcepackAsync(this.plugin, this.player, texturesLevelId, result -> {
            // Активность могли уже сменить, пока пак ехал.
            if (this.plugin.get(ActivityManager.class).getActivity(this.player) != this) return;

            if (result == null || !result.isOk()) {
                this.packState = PackState.FAILED;
                this.startMusicWhenReady = false;
                this.player.sendMessage(Lang.text(this.player, "spectate.nomusic"));
                return;
            }

            this.packState = PackState.READY;
            if (this.startMusicWhenReady && this.targetRunning) {
                this.startMusicWhenReady = false;
                this.startMusic();
            }
        }, null);
    }

    // ==================== ЦЕЛЬ НАБЛЮДЕНИЯ ====================

    /**
     * Телепорт обязан уехать в следующий тик. Метод вызывается в том числе из startActivity(),
     * а тот - из обработчика PlayerTeleportEvent, где игрок ещё физически не перемещён.
     * Телепорт прямо здесь порождал вложенный PlayerTeleportEvent, который снова выглядел как
     * переход между мирами, и всё уходило в бесконечную рекурсию со StackOverflowError
     * (воспроизводилось обычным /tp на игрока, стоящего на уровне).
     */
    public void setTargetPlayer(@Nullable Player target) {
        if (target != null && !this.canSpectate(target)) {
            this.player.sendMessage(Lang.text(this.player, "spectate.denied"));
            return;
        }

        this.stopMusic();
        this.targetRunning = false;
        this.startMusicWhenReady = false;
        this.targetGame = null;

        this.targetPlayerId = target == null ? null : target.getUniqueId();
        if (target == null) return;

        UUID expectedTargetId = this.targetPlayerId;
        this.player.setSpectatorTarget(null);
        this.player.sendMessage(Lang.text(this.player, "spectate.watching", "%player%", target.getName()));

        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            if (!this.player.isOnline()) return;
            // Пока ждали тик, игрока могли увести с уровня или он сменил цель.
            if (!expectedTargetId.equals(this.targetPlayerId)) return;
            if (this.plugin.get(ActivityManager.class).getActivity(this.player) != this) return;

            Player actualTarget = this.plugin.getServer().getPlayer(expectedTargetId);
            if (actualTarget == null || !actualTarget.isOnline()) return;

            TeleportUtils.teleportAsync(this.plugin, this.player, actualTarget.getLocation())
                .thenAccept(success -> this.attachSpectatorTarget(expectedTargetId));
        });
    }

    private void attachSpectatorTarget(@NonNull UUID expectedTargetId) {
        if (!this.player.isOnline()) return;
        // За время телепорта игрок мог переключить цель или вовсе выйти с уровня.
        if (!expectedTargetId.equals(this.targetPlayerId)) return;
        if (this.plugin.get(ActivityManager.class).getActivity(this.player) != this) return;

        Player target = this.plugin.getServer().getPlayer(expectedTargetId);
        if (target == null || !target.isOnline() || target.getWorld() != this.player.getWorld()) return;

        this.player.setSpectatorTarget(target);
    }

    /**
     * Настройка приватности игрока (/tptoggle). Доступ к самому уровню проверяется раньше
     * и отдельно: сюда попадают только те, кто на уровень уже пущен.
     */
    private boolean canSpectate(@NonNull Player target) {
        if (target == this.player) return false;
        try {
            return this.plugin.get(PlayerSettingsManager.class)
                .canTeleportTo(this.player.getUniqueId(), target.getUniqueId());
        } catch (Exception e) {
            return true;
        }
    }

    @NonNull
    private List<Player> getSpectatableTargets() {
        List<Player> result = new ArrayList<>();
        for (Player candidate : this.level.getWorld().getPlayers()) {
            if (candidate == this.player) continue;
            if (candidate.getGameMode() == GameMode.SPECTATOR) continue;
            if (!this.canSpectate(candidate)) continue;
            result.add(candidate);
        }
        return result;
    }

    @Nullable
    private Player findTarget() {
        if (this.targetPlayerId != null) {
            Player target = this.plugin.getServer().getPlayer(this.targetPlayerId);
            if (target != null && target.isOnline()
                && target.getWorld() == this.level.getWorld()
                && this.canSpectate(target)) {
                return target;
            }
            this.targetPlayerId = null;
        }
        List<Player> candidates = this.getSpectatableTargets();
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * Переключение на следующего игрока по кругу.
     */
    public void switchToNextTarget() {
        List<Player> candidates = this.getSpectatableTargets();
        if (candidates.isEmpty()) {
            this.player.sendMessage(Lang.text(this.player, "spectate.noone_left"));
            return;
        }
        if (candidates.size() == 1 && candidates.get(0).getUniqueId().equals(this.targetPlayerId)) {
            return;
        }

        int currentIndex = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).getUniqueId().equals(this.targetPlayerId)) {
                currentIndex = i;
                break;
            }
        }
        this.setTargetPlayer(candidates.get((currentIndex + 1) % candidates.size()));
    }

    // ==================== МУЗЫКА ====================

    /**
     * Игра того, за кем смотрим: обычный забег или тестовый прогон строителя.
     */
    @Nullable
    private Game resolveGame(@NonNull Player target) {
        UserActivity targetActivity = this.plugin.get(ActivityManager.class).getActivity(target);
        if (targetActivity instanceof PlayActivity playActivity) {
            return playActivity.getGame();
        }
        if (targetActivity instanceof EditActivity editActivity) {
            PlayActivity testing = editActivity.getTestingActivity();
            return testing == null ? null : testing.getGame();
        }
        return null;
    }

    private void onRunStarted(@NonNull Game game) {
        if (game.getMusicMode() == MusicMode.DISABLED) return;

        switch (this.packState) {
            case ABSENT, FAILED -> {
                return;
            }
            case LOADING -> {
                // Пак ещё едет: запомним намерение и включим музыку, как только он приедет.
                this.startMusicWhenReady = true;
                return;
            }
            case READY -> {
            }
        }

        // Подключились к середине чужого забега - синхронно уже не получится.
        if (game.getSongTimeMillis() > MAX_JOIN_LAG_MILLIS) {
            this.player.sendMessage(Lang.text(this.player, "spectate.music_next"));
            return;
        }

        this.startMusic();
    }

    private void onRunEnded() {
        this.startMusicWhenReady = false;
        this.stopMusic();
    }

    private void startMusic() {
        if (this.musicPlaying) return;
        try {
            MusicPlatform platform = this.plugin.get(MusicTracksManager.class).getPlatform();
            platform.disableRepeatMode(this.player);
            platform.startPlayingTrackFull(this.player);
            this.musicPlaying = true;
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Unable to start spectator music for " + this.player.getName(), e);
        }
    }

    private void stopMusic() {
        if (!this.musicPlaying) return;
        this.musicPlaying = false;
        try {
            this.plugin.get(MusicTracksManager.class).getPlatform().stopPlayingTrackFull(this.player);
        } catch (Exception ignored) {
        }
    }

    /**
     * Слышит ли наблюдатель музыку прямо сейчас - нужно табло.
     */
    public boolean isMusicPlaying() {
        return this.musicPlaying;
    }

    /**
     * Короткий статус музыки для табло.
     */
    @NonNull
    public String getMusicStatus() {
        if (this.musicPlaying) return Lang.raw(this.player, "spectate.music.playing");
        return switch (this.packState) {
            case ABSENT -> Lang.raw(this.player, "spectate.music.absent");
            case LOADING -> Lang.raw(this.player, "spectate.music.loading");
            case FAILED -> Lang.raw(this.player, "spectate.music.failed");
            case READY -> Lang.raw(this.player, this.targetRunning
                ? "spectate.music.waiting" : "spectate.music.paused");
        };
    }

    // ==================== ТИК ====================

    @Override
    public void onTick() {
        if (this.targetPlayerId == null) {
            // Кто-то мог начать забег уже после того, как мы пришли: ищем цель не каждый
            // тик, чтобы не перебирать список игроков мира по двадцать раз в секунду.
            if (this.searchCooldownTicks-- > 0) return;
            this.searchCooldownTicks = 20;

            Player found = this.findTarget();
            if (found != null) this.setTargetPlayer(found);
            return;
        }

        Player target = this.plugin.getServer().getPlayer(this.targetPlayerId);
        if (target == null || !target.isOnline() || !this.canSpectate(target)) {
            this.detachTarget();
            return;
        }

        if (target.getWorld() != this.level.getWorld()) {
            // Мир сменился: либо игрок ушёл на другой уровень (идём следом),
            // либо вышел в лобби (тогда смотреть уже нечего).
            if (this.followTargetToAnotherLevel(target)) return;
            this.detachTarget();
            return;
        }

        this.keepCameraOnTarget(target);

        Game game = this.resolveGame(target);
        this.targetGame = game;

        boolean running = game != null
            && game.getCurrentState() == Game.State.RUNNING
            && game.getMusicMode() != MusicMode.DISABLED;

        if (running != this.targetRunning) {
            this.targetRunning = running;
            if (running) {
                this.lastKnownProgress = 0f;
                this.onRunStarted(game);
            } else {
                if (this.lastReportedGame != null) this.reportRunResult(this.lastReportedGame);
                this.onRunEnded();
            }
        }

        if (running) {
            // Прогресс запоминаем на каждом тике: после падения игра его уже обнулит,
            // а нам нужно знать, где именно всё закончилось.
            this.lastKnownProgress = game.getPassedProgress();
            this.lastReportedGame = game;
        }

        this.showEarnedPoints(game, running);
    }

    /**
     * Держит камеру на цели и чинит два неприятных момента.
     * <p>
     * Первый - ванильный: клиент, наблюдающий за сущностью, не перерисовывает мир, если та
     * телепортировалась далеко (например, игрок упал и его вернули на старт). Экран при этом
     * трясётся, а зритель остаётся в точке падения. Лечится телепортом самого зрителя вслед
     * за целью: сервер обязан выдать клиенту новые чанки, ванильный клиент этого сам не просит.
     * <p>
     * Второй - Shift, которым клиент отцепляет камеру. Раз наблюдение здесь ни на что другое
     * не завязано, просто возвращаем камеру обратно.
     */
    private void keepCameraOnTarget(@NonNull Player target) {
        if (this.player.getGameMode() != GameMode.SPECTATOR) return;

        org.bukkit.Location targetLocation = target.getLocation();
        org.bukkit.Location viewerLocation = this.player.getLocation();

        boolean farAway = viewerLocation.getWorld() != targetLocation.getWorld()
            || viewerLocation.distanceSquared(targetLocation) > FOLLOW_DISTANCE_SQUARED;

        if (farAway) {
            // Клиент, сидящий в чужой сущности, не догружает мир вокруг неё: если цель
            // отбросило далеко (упала и её вернули на старт), зритель оставался в точке
            // падения с трясущимся экраном. Полностью выселяем, подтягиваем и вселяем
            // заново - половинчатая пересадка эту тряску не лечила.
            this.player.setSpectatorTarget(null);
            this.cameraReleasedByPlayer = false;

            UUID targetId = target.getUniqueId();
            TeleportUtils.teleportAsync(this.plugin, this.player, targetLocation)
                .thenAccept(success -> this.plugin.getServer().getScheduler().runTaskLater(
                    this.plugin, () -> this.attachSpectatorTarget(targetId), 2L));
            return;
        }

        // Зритель сам вышел из игрока - не тащим его обратно силой.
        if (this.cameraReleasedByPlayer) {
            if (this.player.getSpectatorTarget() != null) this.cameraReleasedByPlayer = false;
            return;
        }

        if (this.player.getSpectatorTarget() != target) {
            this.player.setSpectatorTarget(target);
        }
    }

    /**
     * Показывает начисления цели тем же титулом и теми же цветами, что видит она сама.
     */
    private void showEarnedPoints(@Nullable Game game, boolean running) {
        if (game == null || !running) {
            this.lastPerfect = -1;
            return;
        }

        ru.sortix.parkourbeat.rating.RunTracker tracker = game.getRunTracker();
        int perfect = tracker.getPerfectCount();
        int good = tracker.getGoodCount();
        int ok = tracker.getOkCount();
        int miss = tracker.getMissCount();

        // Первый тик забега (или новая попытка со сброшенными счётчиками):
        // просто запоминаем точку отсчёта, ничего не показываем.
        if (this.lastPerfect < 0
            || perfect < this.lastPerfect || good < this.lastGood
            || ok < this.lastOk || miss < this.lastMiss) {
            this.lastPerfect = perfect;
            this.lastGood = good;
            this.lastOk = ok;
            this.lastMiss = miss;
            return;
        }

        JumpResult result = null;
        if (perfect > this.lastPerfect) result = JumpResult.PERFECT;
        else if (good > this.lastGood) result = JumpResult.GOOD;
        else if (ok > this.lastOk) result = JumpResult.OK;
        else if (miss > this.lastMiss) result = JumpResult.MISS;

        this.lastPerfect = perfect;
        this.lastGood = good;
        this.lastOk = ok;
        this.lastMiss = miss;

        if (result == null) return;

        this.player.showTitle(net.kyori.adventure.title.Title.title(
            net.kyori.adventure.text.Component.empty(),
            PbText.of(result == JumpResult.MISS
                ? result.getColorPrefix() + "MISS" : result.formatPoints()),
            net.kyori.adventure.title.Title.Times.of(
                java.time.Duration.ZERO,
                java.time.Duration.ofMillis(150),
                java.time.Duration.ofMillis(100))));
    }

    /**
     * Итог попытки в чат: прошёл или упал.
     * <p>
     * Игра не отдаёт наружу событие конца забега, поэтому определяем по прогрессу на
     * последнем тике: дошёл почти до конца - победа, иначе падение.
     */
    private void reportRunResult(@NonNull Game game) {
        // Титулом, а не в чат: результат нужен на секунду и не должен забивать переписку.
        Component title;
        Component subtitle;

        if (this.lastKnownProgress >= 0.99f) {
            title = Lang.text(this.player, "spectate.title.completed");
            subtitle = PbText.of(game.getCurrentGrade().getFormatted()
                + " &7- " + String.format(java.util.Locale.ROOT, "%.2f", game.getDisplayAccuracy())
                + " &7- &f" + game.getRunTracker().getScore());
        } else {
            title = Lang.text(this.player, "spectate.title.lost");
            subtitle = Lang.text(this.player, "spectate.subtitle.reached",
                "%progress%", String.format(java.util.Locale.ROOT, "%.0f%%",
                    this.lastKnownProgress * 100f));
        }

        this.player.showTitle(net.kyori.adventure.title.Title.title(title, subtitle,
            net.kyori.adventure.title.Title.Times.of(
                java.time.Duration.ofMillis(200),
                java.time.Duration.ofMillis(1600),
                java.time.Duration.ofMillis(400))));
    }

    /**
     * Цель ушла на другой уровень - идём следом, а не выбрасываем зрителя в лобби.
     */
    private boolean followTargetToAnotherLevel(@NonNull Player target) {
        UserActivity targetActivity = this.plugin.get(ActivityManager.class).getActivity(target);
        if (targetActivity == null) return false;

        Level newLevel = targetActivity.getLevel();
        if (newLevel == this.level) return false;

        if (!newLevel.getLevelSettings().getGameSettings().isAccessibleForPlaying(this.player, true)) {
            this.player.sendMessage(Lang.text(this.player, "spectate.level_denied"));
            return false;
        }

        UUID keepTargetId = target.getUniqueId();
        this.player.sendMessage(Lang.text(this.player, "spectate.level_followed"));

        SpectateActivity next = new SpectateActivity(this.plugin, this.player, newLevel);
        next.targetPlayerId = keepTargetId;
        this.plugin.get(ActivityManager.class).switchActivity(this.player, next, newLevel.getSpawn());
        return true;
    }

    private void detachTarget() {
        this.targetPlayerId = null;
        this.targetGame = null;
        this.targetRunning = false;
        this.onRunEnded();
        try {
            if (this.player.getGameMode() == GameMode.SPECTATOR) {
                this.player.setSpectatorTarget(null);
            }
        } catch (Exception ignored) {
        }
        this.player.sendMessage(Lang.text(this.player, "spectate.left_level"));
        this.searchCooldownTicks = 20;
    }

    // ==================== СОБЫТИЯ ====================

    @Override
    public void on(@NonNull PlayerMoveEvent event) {
    }

    @Override
    public void on(@NonNull PlayerToggleSprintEvent event) {
    }

    /**
     * Shift - ванильный выход из игрока. Раньше автоматика тут же вселяла обратно, и
     * выйти было невозможно; теперь запоминаем, что зритель вышел сам, и не мешаем ему.
     * Вернуться - кликнуть по игроку, как в обычном наблюдателе.
     */
    @Override
    public void on(@NonNull PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        if (this.player.getSpectatorTarget() != null) {
            this.cameraReleasedByPlayer = true;
        }
    }

    @Override
    public int getFallHeight() {
        return this.getFallHeight(false);
    }

    @Override
    public void onPlayerFall() {
        TeleportUtils.teleportAsync(this.getPlugin(), this.player, this.level.getSpawn());
    }

    /**
     * Каждый шаг выполняется независимо. Раньше setSpectatorTarget() падал с
     * "Player must be in spectator mode" (например, если режим игры успел сменить кто-то ещё),
     * и из-за этого до сброса неба и режима игры дело уже не доходило - игрок оставался
     * с белым небом и вечным спамом ошибок.
     */
    @Override
    public void endActivity() {
        this.safely("stopMusic", this::stopMusic);
        this.safely("setSpectatorTarget", () -> {
            if (this.player.getGameMode() == GameMode.SPECTATOR) {
                this.player.setSpectatorTarget(null);
            }
        });
        this.safely("stopSpawnParticles", () ->
            this.level.getLevelSettings().getParticleController().stopSpawnParticlesForPlayer(this.player));
        this.safely("hideDuelPaths", () -> this.plugin
            .get(ru.sortix.parkourbeat.duel.DuelManager.class)
            .hideBothPaths(this.level, this.player));
        this.safely("setGameMode", () -> this.player.setGameMode(GameMode.ADVENTURE));
        this.safely("resetSky", () -> SkyType.reset(this.player));
    }

    private void safely(@NonNull String what, @NonNull Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Unable to finish spectating step \"" + what + "\" of player " + this.player.getName(), t);
        }
    }
}
