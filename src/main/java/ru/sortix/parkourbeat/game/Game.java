package ru.sortix.parkourbeat.game;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.ActivityPacketsAdapter;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.activity.type.PlayActivity;
import ru.sortix.parkourbeat.game.movement.GameMoveHandler;
import ru.sortix.parkourbeat.game.movement.MovementAccuracyChecker;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.LightShowRunner;
import ru.sortix.parkourbeat.levels.ParticleController;
import ru.sortix.parkourbeat.levels.settings.CompletionParticle;
import ru.sortix.parkourbeat.levels.settings.LevelBossBarColor;
import ru.sortix.parkourbeat.levels.settings.LevelSettings;
import ru.sortix.parkourbeat.player.music.MusicTrack;
import ru.sortix.parkourbeat.player.music.platform.MusicPackDispatcher;
import ru.sortix.parkourbeat.player.music.MusicTracksManager;
import ru.sortix.parkourbeat.player.music.platform.MusicPlatform;
import ru.sortix.parkourbeat.rating.AccuracyGrade;
import ru.sortix.parkourbeat.rating.JumpResult;
import ru.sortix.parkourbeat.rating.Modifier;
import ru.sortix.parkourbeat.rating.ModifierSet;
import ru.sortix.parkourbeat.rating.RunTracker;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.stats.RunResult;
import ru.sortix.parkourbeat.stats.RunSubmission;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.lang.PlayerLang;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.world.AutoLookSettings;
import ru.sortix.parkourbeat.world.LocationUtils;
import ru.sortix.parkourbeat.world.TeleportUtils;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import ru.sortix.parkourbeat.utils.text.PbText;
@Getter
public class Game {
    public static final double BLOCKS_PER_SECOND = 5.6123;

    /**
     * Минимальный прирост прогресса (в процентных пунктах), при котором игроку
     * вообще сообщают о новом рекорде. Умер на 8.51%, потом на 8.52% — рекорд
     * формально есть и в базу он пишется, но дёргать человека титлом ради
     * сотой доли процента незачем.
     */
    private static final double MIN_PROGRESS_RECORD_DELTA = 1.0D;

    /**
     * Общий текст проигрыша. Игроку не сообщается, какой именно модификатор его
     * добил — он и так знает, что включал, а сухое "Провален модификатор SD"
     * читается как ошибка плагина, а не как поражение.
     */

    /**
     * Общий текст проигрыша. Игроку не сообщается, какой именно модификатор его
     * добил — он и так знает, что включал, а сухое "Провален модификатор SD"
     * читается как ошибка плагина, а не как поражение.
     * <p>
     * Раньше это была статическая константа; теперь текст зависит от языка игрока,
     * поэтому собирается на месте.
     */
    @NonNull
    private Component loseTitle() {
        return Lang.text(this.player, "game.title.lost");
    }

    private static final Title.Times FINISH_REASON_TITLE_TIMES = Title.Times.of(Duration.ofMillis(500L), Duration.ofMillis(1500L), Duration.ofMillis(500L));

    private final @NonNull LevelsManager levelsManager;
    private final @NonNull MusicTracksManager musicTracksManager;
    private final @NonNull ActivityPacketsAdapter packetsAdapter;
    private final @NonNull Player player;
    private final @NonNull Level level;
    private final @NonNull GameMoveHandler gameMoveHandler;
    private @NonNull MusicMode musicMode;
    private final @NonNull RunTracker runTracker;

    @Getter
    private @NonNull ModifierSet modifiers;
    private @NonNull AccuracyGrade lastGrade = AccuracyGrade.SS;
    /** Защита от двойной записи одного забега (тик мог успеть вызвать финиш дважды). */
    private boolean runSubmitted = false;
    private long lastBleedAtMillis = 0L;
    @Setter
    private @NonNull State currentState = State.PREPARING;
    @Setter
    @Getter
    private boolean allowEndlessRun = false;
    @Setter
    private boolean displayTimecode = false;
    private BukkitTask gameTask;
    private BossBar bossBar;
    private BossBar technicalBossBar;
    private LightShowRunner lightShowRunner;
    private ru.sortix.parkourbeat.levels.wonder.WonderRunner wonderRunner;
    private ru.sortix.parkourbeat.levels.lamps.LampRunner lampRunner;
    private ru.sortix.parkourbeat.levels.PortalRunner portalRunner;
    private volatile LevelBossBarColor bossBarColorOverride = null;
    private volatile long songStartedAtMillis = 0L;
    private volatile long songStoppedAtMillis = 0L;
    private volatile int lastTrackPieceNumber = 0;

    // ==================== ЧЕКПОИНТЫ ====================
    /** Отсортированные по ходу уровня активные чекпоинты. Пустой список — их нет. */
    private final @NonNull java.util.List<ru.sortix.parkourbeat.levels.settings.Checkpoint> checkpoints
        = new java.util.ArrayList<>();
    /** Отметка каждого чекпоинта на треке, мс от начала песни. */
    private final @NonNull java.util.List<Integer> checkpointOffsets = new java.util.ArrayList<>();
    /** Номер куска нарезки, который сейчас играет (1..N+1). 0 — ничего не играет. */
    private volatile int currentSlice = 0;
    /** Индекс последнего пройденного чекпоинта: -1 — игрок ещё до первого. */
    private volatile int reachedCheckpoint = -1;
    /** Сколько раз игрока откатывало на чекпоинт за этот забег. */
    private volatile int checkpointRespawns = 0;
    /**
     * Во сколько раз урезаются очки забега за все откаты на чекпоинт.
     * 1.0 - откатов не было. См. {@link #applyCheckpointPenalty}.
     */
    private volatile double checkpointScoreFactor = 1.0D;

    /**
     * ПАУЗА ПОСЛЕ ОТКАТА НА ЧЕКПОИНТ (экспериментально).
     * <p>
     * Без неё забег продолжался в тот же миг, что и телепорт: музыка шла с отметки, а
     * первый прыжок часто стоит сразу за чекпоинтом. Игрок не успевал понять, что умер,
     * и терял вторую попытку подряд. С паузой он стоит на месте, видит отсчёт без слов
     * и только потом едет дальше.
     */
    public static final boolean CHECKPOINT_PAUSE_ENABLED = true;
    /** Длительность паузы. Делится на три шага отсчёта. */
    public static final long CHECKPOINT_PAUSE_MILLIS = 1500L;

    /** До этого момента игрок стоит в паузе после отката. 0 - паузы нет. */
    private volatile long checkpointPauseUntil = 0L;
    /**
     * ТОЧКА, В КОТОРОЙ ИГРОК СТОИТ НА ПАУЗЕ.
     * <p>
     * Без неё пауза отменяла move-события, а отмена возвращает игрока в {@code from}
     * события. Сразу после телепорта в очереди ещё лежат пакеты с ПРЕЖНЕЙ позицией, и
     * первый же такой пакет утаскивал игрока обратно на место смерти - он стоял там всю
     * паузу. Теперь смещение не отменяется, а переписывается на эту точку.
     */
    private volatile Location checkpointPauseAnchor = null;
    /** Номер паузы: отложенные шаги старой паузы не должны трогать новую. */
    private int checkpointPauseToken = 0;
    /**
     * Пак с нарезкой не поехал в этой сессии. Чекпоинты выключены до смены уровня,
     * но их список остаётся на месте: пересобирать его между забегами дешевле,
     * чем терять навсегда.
     */
    private volatile boolean checkpointPackFailed = false;
    /** Очки, комбо и попадания на момент взятия последнего чекпоинта. */
    private RunTracker.Snapshot checkpointRunSnapshot = null;
    /** Точность движения на тот же момент. */
    private MovementAccuracyChecker.Snapshot checkpointAccuracySnapshot = null;
    /** Задача, которая заводит следующий кусок нарезки ровно на стыке. */
    private BukkitTask sliceTask;
    /** Трек, которым реально играем: при чекпоинтах это нарезанный плейлист. */
    private @Nullable MusicTrack playbackTrack = null;
    /**
     * До этого момента проигрыш не засчитывается вообще.
     * <p>
     * Без этого откат на чекпоинт зацикливался намертво: телепорт назад сам по себе
     * выглядит как движение против направления уровня, и следующий же PlayerMoveEvent
     * вызывал новый failLevel, тот — новый откат, и так до бесконечности.
     */
    private volatile long respawnGraceUntil = 0L;

    private Game(@NonNull ParkourBeat plugin, @NonNull Player player, @NonNull Level level, @NonNull ModifierSet modifiers) {
        this.levelsManager = plugin.get(LevelsManager.class);
        this.musicTracksManager = plugin.get(MusicTracksManager.class);
        this.packetsAdapter = plugin.get(ActivityManager.class).getPacketsAdapter();
        this.player = player;
        this.level = level;
        this.modifiers = modifiers.copy();
        this.runTracker = new RunTracker(this.modifiers);
        this.gameMoveHandler = new GameMoveHandler(this);
        this.reloadCheckpoints();
        this.musicMode = this.resolveMusicMode();
        this.prepareGame(plugin);
    }

    /**
     * Кеш проверки воды на один тик.
     * <p>
     * {@link #isInWater(Player)} перебирает до 27 блоков вокруг игрока. Раньше он звался
     * пару раз за забег, а теперь ещё и на каждом PlayerMoveEvent (иммунитет к промахам
     * под водой) — это десятки тысяч обращений к чанкам в секунду на полном сервере.
     * В пределах одного тика вода измениться не может, поэтому результат переиспользуется.
     */
    private static final java.util.Map<java.util.UUID, long[]> IN_WATER_CACHE
        = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean isInWaterCached(@NonNull Player player) {
        long tick = Bukkit.getCurrentTick();
        long[] cached = IN_WATER_CACHE.get(player.getUniqueId());
        if (cached != null && cached[0] == tick) return cached[1] != 0L;

        boolean result = isInWater(player);
        IN_WATER_CACHE.put(player.getUniqueId(), new long[]{tick, result ? 1L : 0L});
        return result;
    }

    public static void clearWaterCache(@NonNull Player player) {
        IN_WATER_CACHE.remove(player.getUniqueId());
    }

    public static boolean isInWater(@NonNull Player player) {
        if (player.isInWater() || player.isSwimming()) return true;
        Location loc = player.getLocation();
        if (loc.getWorld() == null) return false;

        for (double dx = -0.3; dx <= 0.3; dx += 0.3) {
            for (double dz = -0.3; dz <= 0.3; dz += 0.3) {
                for (double dy = -0.5; dy <= 1.8; dy += 0.5) {
                    Material type = loc.clone().add(dx, dy, dz).getBlock().getType();
                    if (type == Material.WATER || type == Material.BUBBLE_COLUMN) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @NonNull
    public static CompletableFuture<Game> createAsync(
        @NonNull ParkourBeat plugin,
        @NonNull Player player,
        @NonNull UUID levelId,
        boolean preventWrongSpawn
    ) {
        return createAsync(plugin, player, levelId, preventWrongSpawn, new ModifierSet());
    }

    @NonNull
    public static CompletableFuture<Game> createAsync(
        @NonNull ParkourBeat plugin,
        @NonNull Player player,
        @NonNull UUID levelId,
        boolean preventWrongSpawn,
        @NonNull ModifierSet modifiers
    ) {
        CompletableFuture<Game> result = new CompletableFuture<>();
        LevelsManager levelsManager = plugin.get(LevelsManager.class);
        levelsManager.loadLevel(levelId, null).thenAccept(level -> {
            if (level == null) {
                result.complete(null);
                return;
            }
            try {
                if (!level.isLevelAccessibleForPlaying(player, true, true)) {
                    if (level.getWorld().getPlayers().isEmpty()) {
                        levelsManager.unloadLevelAsync(levelId, false);
                    }
                    result.complete(null);
                    return;
                }

                if (!LocationUtils.isValidSpawnPoint(level.getSpawn(), level.getLevelSettings())) {
                    if (preventWrongSpawn) {
                        LangOptions.level_prepare_spawninvalid_prevent.sendMsg(player);

                        if (level.getWorld().getPlayers().isEmpty()) {
                            levelsManager.unloadLevelAsync(levelId, false);
                        }

                        result.complete(null);
                        return;
                    } else {
                        LangOptions.level_prepare_spawninvalid_notify.sendMsg(player);
                    }
                }

                result.complete(new Game(plugin, player, level, modifiers));
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Unable to prepare game", e);
                result.complete(null);
            }
        });
        return result;
    }

    private void prepareGame(@NonNull ParkourBeat plugin) {
        LevelSettings settings = this.level.getLevelSettings();
        this.level.applyViewDistances();

        ParticleController particleController = settings.getParticleController();

        if (!particleController.isLoaded()) {
            particleController.loadParticleLocations(settings.getWorldSettings().getWaypoints());
        }

        this.player.setGameMode(GameMode.ADVENTURE);


        this.setCurrentState(State.READY);

        // При рабочих чекпоинтах выдаём отдельный плейлист с нарезкой, а не исходный трек:
        // в нём лежат куски part1..partN+1, по одному на промежуток между чекпоинтами.
        MusicTrack musicTrack = this.getPlaybackTrack();
        if (musicTrack == null || !musicTrack.isStillAvailable()) {
            // Пака не будет вообще, а значит некому снять текстуры прошлого уровня:
            // делаем это сами, иначе игрок ходит по этому уровню в чужих текстурах.
            this.dropForeignTextures();
            return;
        }

        final boolean checkpointPack = this.musicMode == MusicMode.CHECKPOINTS;
        if (checkpointPack) {
            plugin.getLogger().info("Выдаём пак нарезки '" + musicTrack.getId()
                + Lang.raw(PlayerLang.of(this.player), "auto.game.prepare_game.1") + this.player.getName());
        }

        musicTrack.isResourcepackCurrentlySet(this.player, currentlySet -> {
            // Совпадения трека мало. Текстуры уровня вмерживаются в архив трека, поэтому
            // два разных уровня на одном треке дают РАЗНЫЕ паки. Раньше проверялся только
            // трек, и переход на уровень с тем же треком не выдавал пак вовсе -
            // текстуры предыдущего уровня оставались на игроке.
            if (Boolean.TRUE.equals(currentlySet) && this.hasCorrectTexturesLoaded()) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!this.player.isOnline()) return;
                // Игра создаётся ДО switchActivity, поэтому текущая активность игрока здесь
                // ещё от прошлого уровня. Свой уровень называем явно.
                musicTrack.setResourcepackAsync(plugin, this.player,
                    this.getNeededTexturesLevelId(), result -> {
                        if (!checkpointPack) return;
                        // РЕЗУЛЬТАТ - ЭТО ENUM, А НЕ BOOLEAN.
                        //
                        // Раньше здесь стояла проверка на Boolean.TRUE, которая не совпадала
                        // никогда. Из-за этого откат на цельный трек срабатывал при КАЖДОЙ
                        // успешной выдаче пака: чекпоинты стирались, музыка переключалась
                        // вторым паком посреди уровня и обрывалась.
                        if (result == null || result.isOk()) return;
                        // SUPERSEDED - пак просто перебит следующим запросом, это не сбой.
                        if (result == MusicPackDispatcher.Result.SUPERSEDED) return;
                        if (result == MusicPackDispatcher.Result.PLAYER_LEFT) return;
                        // ПАК НАРЕЗКИ НЕ ЗАГРУЗИЛСЯ.
                        //
                        // Оставлять игрока совсем без музыки нельзя: это хуже, чем уровень
                        // без чекпоинтов. Откатываемся на цельный трек — уровень играется
                        // как обычно, просто смерть возвращает на старт.
                        plugin.getServer().getScheduler().runTask(plugin,
                            this::fallbackToFullTrack);
                    }, null);
            });
        });
    }

    /**
     * Уровень, чьи текстуры должны быть на клиенте на этом уровне (null - никаких).
     */
    @javax.annotation.Nullable
    private java.util.UUID getNeededTexturesLevelId() {
        return this.level.getLevelSettings().getGameSettings().isCustomTextures()
            ? this.level.getUniqueId()
            : null;
    }

    private boolean hasCorrectTexturesLoaded() {
        try {
            java.util.UUID loaded = this.getPlugin()
                .get(ru.sortix.parkourbeat.player.CustomTexturesManager.class)
                .getLoadedTexturesLevel(this.player);
            return java.util.Objects.equals(loaded, this.getNeededTexturesLevelId());
        } catch (Exception e) {
            return true;
        }
    }

    private void dropForeignTextures() {
        try {
            this.getPlugin().get(ru.sortix.parkourbeat.player.CustomTexturesManager.class)
                .dropForeignTextures(this.player, this.level.getUniqueId());
        } catch (Exception e) {
            this.getPlugin().getLogger().log(java.util.logging.Level.WARNING,
                Lang.raw(PlayerLang.of(this.player), "auto.game.drop_foreign_textures.1") + this.player.getName(), e);
        }
    }

    /**
     * Пак с нарезкой не поехал — играем цельный трек, чтобы уровень не остался немым.
     */
    private void fallbackToFullTrack() {
        if (this.musicMode != MusicMode.CHECKPOINTS) return;

        this.getPlugin().getLogger().warning("Пак нарезки не загрузился, откатываемся"
            + Lang.raw(PlayerLang.of(this.player), "auto.game.fallback_to_full_track.1") + this.player.getName()
            + Lang.raw(PlayerLang.of(this.player), "auto.game.fallback_to_full_track.2") + this.level.getUniqueId() + ")");

        // НЕ РАЗРУШАЕМ СОСТОЯНИЕ, А СТАВИМ ФЛАГ.
        //
        // Раньше здесь очищался список чекпоинтов, и это было навсегда: объект игры
        // переиспользуется между забегами на одном уровне, поэтому после единственного
        // отката чекпоинты и шансы умирали до самого выхода в другой мир.
        this.checkpointPackFailed = true;
        this.musicMode = MusicMode.FULL_TRACK;
        this.playbackTrack = null;
        this.stopSliceTask();
        this.reachedCheckpoint = -1;

        MusicTrack original = this.level.getLevelSettings().getGameSettings().getMusicTrack();
        if (original == null || !this.player.isOnline()) return;

        this.playbackTrack = original;
        original.setResourcepackAsync(this.getPlugin(), this.player,
            this.getNeededTexturesLevelId(), result -> {
            }, null);
    }

    @NonNull
    public ParkourBeat getPlugin() {
        return this.levelsManager.getPlugin();
    }

    public void start() {
        if (this.currentState != State.READY) return;

        this.setCurrentState(State.RUNNING);
        this.getPlugin().get(ru.sortix.parkourbeat.replay.ReplayManager.class)
            .startRecording(this.player);

        if (!this.player.isSprinting() || this.player.isSneaking()) {
            // На уровне без урона за отпущенный бег (по умолчанию - 360) проваливать
            // забег за то, что игрок стартовал не с зажатым Ctrl, тоже нельзя:
            // это то же самое наказание, только мгновенное.
            if (!this.hasModifier(Modifier.PRACTICE) && !isInWater(this.player)
                && this.gameMoveHandler.isSprintDamageEnabled()) {
                this.failLevel(LangOptions.level_play_title_pressrun.getComponent(player), null);
                return;
            }
        }

        this.refreshModifiers();
        this.resetRunProgress();

        // На дуэльной карте игрок видит только свою трассу: чужая ему не нужна и
        // сбивала бы с толку прямо на бегу.
        ru.sortix.parkourbeat.duel.DuelManager
            .particlesFor(this.getPlugin(), this.level, this.player)
            .startSpawnParticles(this.player);

        MusicPlatform musicPlatform = this.musicTracksManager.getPlatform();
        this.packetsAdapter.setWatchingPosition(this.player, true);
        if (this.musicMode == MusicMode.PIECES) {
            musicPlatform.disableRepeatMode(this.player);
            this.musicTracksManager.setTrackPiecesSendingEnabled(this, true);
            this.tryToSendTrackPiece();
        } else if (this.musicMode == MusicMode.FULL_TRACK) {
            musicPlatform.disableRepeatMode(this.player);
            musicPlatform.startPlayingTrackFull(this.player);
        } else if (this.musicMode == MusicMode.CHECKPOINTS) {
            musicPlatform.disableRepeatMode(this.player);
            this.playSliceFrom(1);
        }
        this.getPlugin().get(ru.sortix.parkourbeat.player.PlayersVisibilityManager.class)
            .hideOthersFor(this.player);

        this.songStartedAtMillis = System.currentTimeMillis();
        this.songStoppedAtMillis = 0L;
        this.runSubmitted = false;
        this.reachedCheckpoint = -1;
        this.checkpointRespawns = 0;
        this.checkpointScoreFactor = 1.0D;
        this.checkpointRunSnapshot = null;
        this.checkpointAccuracySnapshot = null;
        this.attemptsVisibleUntil = 0L;

        // Чекпоинты пересобираются на каждый забег: строитель мог их подвинуть,
        // а главное — так шансы гарантированно возвращаются после проигранной попытки.
        // Режим музыки при этом НЕ поднимаем обратно: пак игроку выдаётся один раз, на
        // входе в уровень, и включать куски, которых в выданном паке нет, — это тишина.
        this.reloadCheckpoints();

        if (this.hasModifier(Modifier.HARD_ROCK)) {
            this.player.setHealth(1.0D);
        }

        ru.sortix.parkourbeat.duel.DuelManager
            .particlesFor(this.getPlugin(), this.level, this.player)
            .setHiddenViewer(this.player, this.hasModifier(Modifier.HIDDEN));

        UserActivity act = this.getPlugin().get(ActivityManager.class).getActivity(this.player);
        PlayActivity pa = null;
        if (act instanceof PlayActivity) {
            pa = (PlayActivity) act;
        } else if (act instanceof EditActivity) {
            pa = ((EditActivity) act).getTestingActivity();
        }
        if (pa != null) {
            pa.resetTriggerIndexToPosition(0.0D);
        }

        // У «КОПАТЕЛЯ» СВОЙ ПРОИГРЫВАТЕЛЬ ШОУ, ВТОРОЙ ЗДЕСЬ НЕ НУЖЕН.
        //
        // Обычная игра тоже заводит LightShowRunner, и на digger-карте их получалось
        // два: один считает время трека, другой своё. Оба раз в секунду досылают
        // игроку время неба - и оно металось между показом и опорным значением.
        // Со стороны это выглядит так, будто «что-то блокирует» плагин.
        //
        // Владелец шоу на такой карте - забег: он знает, где сейчас музыка.
        if (!this.level.getLevelSettings().getGameSettings().isDiggerLevel()) {
            this.ensureLightShowRunner().startShow();
        }
        this.createBossBar();
        this.startGameTask();
    }

    /**
     * Перечитать выбранные игроком модификаторы. Вызывается при входе на уровень
     * и на старте забега, чтобы включение/выключение PRACTICE применялось сразу,
     * а не через перезаход.
     */
    public void refreshModifiers() {
        if (this.displayTimecode) return;
        try {
            StatisticsManager statistics = this.getPlugin().get(StatisticsManager.class);
            if (statistics != null) {
                this.modifiers = statistics.getSelectedModifiers(this.player.getUniqueId()).copy();
            }
        } catch (Exception ignored) {
        }
        this.runTracker.setModifiers(this.modifiers.copy());
    }

    /** Полное обнуление состояния забега: очки, комбо, промахи, точность, оценка. */
    public void resetRunProgress() {
        this.runTracker.reset();
        this.runTracker.setModifiers(this.modifiers.copy());
        this.gameMoveHandler.getAccuracyChecker().reset();
        this.lastGrade = AccuracyGrade.SS;
        this.lastBleedAtMillis = 0L;
        this.runSubmitted = false;
        this.reachedCheckpoint = -1;
        this.checkpointRespawns = 0;
        this.checkpointScoreFactor = 1.0D;
        this.endCheckpointPause();
    }

    /** Точка спавна уровня с довёрнутой камерой (если автовыравнивание включено). */
    @NonNull
    public Location getAlignedSpawn() {
        Location spawn = this.level.getSpawn();
        if (!AutoLookSettings.ENABLED) return spawn;
        return LocationUtils.alignToDirection(spawn, this.level.getLevelSettings().getDirectionChecker());
    }

    /**
     * Довернуть камеру игрока по направлению уровня, не сдвигая его с места.
     * Ровно то же выравнивание , что строитель получает при установке спавна
     */
    public void applyAutoLook() {
        if (!AutoLookSettings.ENABLED) return;
        if (!this.player.isOnline()) return;
        Location aligned = LocationUtils.alignToDirection(
            this.player.getLocation(), this.level.getLevelSettings().getDirectionChecker());
        this.player.teleport(aligned);
    }

    // ==================== ЧЕКПОИНТЫ ====================

    /**
     * Перечитать чекпоинты уровня и их отметки на треке.
     * <p>
     * Позиция на уровне и время в песне — это одно и то же, пересчитанное через
     * скорость бега, поэтому отметка чекпоинта берётся прямо из его координаты.
     */
    public void reloadCheckpoints() {
        this.checkpoints.clear();
        this.checkpointOffsets.clear();

        java.util.List<ru.sortix.parkourbeat.levels.settings.Checkpoint> active
            = new java.util.ArrayList<>();
        for (ru.sortix.parkourbeat.levels.settings.Checkpoint checkpoint
            : this.level.getLightShow().getCheckpoints()) {
            if (checkpoint.isEnabled()) active.add(checkpoint);
        }
        if (active.isEmpty()) return;

        active.sort(java.util.Comparator.comparingDouble(checkpoint ->
            ru.sortix.parkourbeat.levels.LightShowPositions
                .getSignedDistance(this.level, checkpoint.getPosition())));

        for (ru.sortix.parkourbeat.levels.settings.Checkpoint checkpoint : active) {
            this.checkpoints.add(checkpoint);
            this.checkpointOffsets.add(ru.sortix.parkourbeat.levels.LightShowPositions
                .toTimeMillis(this.level, checkpoint.getPosition()));
        }
    }

    /**
     * Отметки чекпоинтов на треке в миллисекундах — то, по чему прокси режет ogg.
     */
    @NonNull
    public java.util.List<Integer> getCheckpointOffsets() {
        return java.util.Collections.unmodifiableList(this.checkpointOffsets);
    }

    @NonNull
    public java.util.List<ru.sortix.parkourbeat.levels.settings.Checkpoint> getCheckpoints() {
        return java.util.Collections.unmodifiableList(this.checkpoints);
    }

    public int getCheckpointRespawns() {
        return this.checkpointRespawns;
    }

    /**
     * Сколько всего откатов даёт уровень.
     */
    public int getCheckpointAttempts() {
        try {
            return this.level.getLevelSettings().getGameSettings().getCheckpointAttempts();
        } catch (Exception e) {
            return GameSettings.DEFAULT_CHECKPOINT_ATTEMPTS;
        }
    }

    private static final String[] SUPERSCRIPT_DIGITS =
        {"\u2070", "\u00b9", "\u00b2", "\u00b3", "\u2074", "\u2075", "\u2076", "\u2077", "\u2078", "\u2079"};

    private static String superscript(int value) {
        if (value < 0) value = 0;
        if (value < 10) return SUPERSCRIPT_DIGITS[value];
        StringBuilder result = new StringBuilder();
        for (char c : String.valueOf(value).toCharArray()) {
            result.append(SUPERSCRIPT_DIGITS[c - '0']);
        }
        return result.toString();
    }

    /** Момент последнего показа счётчика попыток. */
    private long lastAttemptsShownAt = 0L;
    /** До этого момента счётчик попыток показывается, дальше актионбар свободен. */
    private volatile long attemptsVisibleUntil = 0L;

    /**
     * Актионбар обновляется чаще, чем гаснет текст: иначе счётчик мигал бы посреди
     * своего же окна показа.
     */
    private static final long ATTEMPTS_ACTIONBAR_PERIOD_MILLIS = 1000L;
    /** Сколько счётчик висит после взятия чекпоинта. */
    public static final long ATTEMPTS_SHOW_AFTER_CHECKPOINT_MILLIS = 5000L;
    /** Сколько счётчик висит после отката. */
    public static final long ATTEMPTS_SHOW_AFTER_FAIL_MILLIS = 3000L;

    /**
     * Показать счётчик попыток на заданное время.
     */
    private void showCheckpointAttempts(long durationMillis, long delayMillis) {
        long now = System.currentTimeMillis();
        this.attemptsVisibleUntil = now + delayMillis + durationMillis;
        // Сдвигаем последний показ назад, чтобы счётчик появился сразу после задержки,
        // а не ждал ещё целый период.
        this.lastAttemptsShownAt = now + delayMillis - ATTEMPTS_ACTIONBAR_PERIOD_MILLIS;
    }

    /**
     * Счётчик попыток в актионбаре. Постоянно не висит: появляется только после взятия
     * чекпоинта и после отката, чтобы не мешать оценкам прыжков всё остальное время.
     * Цвет темнеет по мере расхода попыток, чтобы игрок понимал остаток боковым зрением.
     */
    private void tickCheckpointAttempts() {
        if (!this.hasWorkingCheckpoints()) return;
        if (this.currentState != State.RUNNING) return;

        long now = System.currentTimeMillis();
        if (now >= this.attemptsVisibleUntil) return;
        if (now - this.lastAttemptsShownAt < ATTEMPTS_ACTIONBAR_PERIOD_MILLIS) return;
        this.lastAttemptsShownAt = now;

        int total = this.getCheckpointAttempts();
        int used = Math.min(this.checkpointRespawns, total);
        int left = total - used;

        String color;
        if (left >= 3) color = "&a";
        else if (left == 2) color = "&e";
        else if (left == 1) color = "&6";
        else color = "&c";

        this.player.sendActionBar(PbText.of(
            color + superscript(Math.min(used + 1, total)) + "/" + superscript(total)));
    }

    /**
     * Чекпоинты работают только тогда, когда трек под них реально нарезан.
     * Без нарезки музыка с чекпоинта не пойдёт, а молча ронять игрока в тишину — хуже,
     * чем не включать чекпоинты вовсе.
     */
    /**
     * Уровень В ПРИНЦИПЕ пригоден для чекпоинтов: они расставлены и трек под них нарезан.
     * Про режим музыки здесь ничего не спрашивается — иначе получилась бы петля,
     * ведь сам режим и выбирается по этому признаку.
     */
    private boolean hasCheckpointSlices() {
        if (this.checkpointPackFailed) return false;
        if (this.checkpoints.isEmpty()) return false;
        GameSettings settings = this.level.getLevelSettings().getGameSettings();
        if (settings.isUseTrackPieces()) return false;
        return settings.hasUsableSlices(this.checkpoints.size());
    }

    /**
     * Чекпоинты работают прямо сейчас. Дополнительно к пригодности уровня требуется,
     * чтобы игроку реально был выдан пак нарезки: иначе откат уводил бы его в тишину.
     */
    public boolean hasWorkingCheckpoints() {
        return this.musicMode == MusicMode.CHECKPOINTS && this.hasCheckpointSlices();
    }

    @NonNull
    private MusicMode resolveMusicMode() {
        GameSettings settings = this.level.getLevelSettings().getGameSettings();
        if (settings.getMusicTrack() == null) return MusicMode.DISABLED;
        // Посекундная синхронизация давно не поддерживается и с чекпоинтами не смешивается.
        if (settings.isUseTrackPieces()) return MusicMode.PIECES;
        if (this.hasCheckpointSlices()) return MusicMode.CHECKPOINTS;
        return MusicMode.FULL_TRACK;
    }

    /**
     * Трек, который надо выдать игроку ресурспаком. При рабочих чекпоинтах это отдельный
     * плейлист с нарезкой, а не исходный трек: в нём лежат куски part1..partN+1.
     */
    @Nullable
    public MusicTrack getPlaybackTrack() {
        if (this.playbackTrack != null) return this.playbackTrack;

        GameSettings settings = this.level.getLevelSettings().getGameSettings();
        MusicTrack original = settings.getMusicTrack();
        if (original == null) return null;

        if (this.musicMode != MusicMode.CHECKPOINTS) {
            this.playbackTrack = original;
            return this.playbackTrack;
        }

        String slicedId = settings.getSlicedPlaylistId();
        if (slicedId == null || slicedId.isEmpty()) {
            this.playbackTrack = original;
            return this.playbackTrack;
        }

        MusicPlatform platform = this.musicTracksManager.getPlatform();
        MusicTrack sliced = platform.getTrackById(slicedId);
        if (sliced == null) {
            // Плейлиста может ещё не быть в кеше платформы — он появился только что,
            // сразу после нарезки. Заглушка с тем же id полностью рабочая для выдачи пака.
            sliced = new MusicTrack(platform, slicedId, original.getName(), false, true);
        }
        this.playbackTrack = sliced;
        return this.playbackTrack;
    }

    /**
     * Индекс чекпоинта, на который игрока откатит прямо сейчас. -1 — ни одного не прошёл.
     */
    public int getReachedCheckpoint() {
        return this.reachedCheckpoint;
    }

    /**
     * Идёт откат на чекпоинт: проигрыш и проверки направления временно отключены.
     */
    public boolean isRespawnGrace() {
        return System.currentTimeMillis() < this.respawnGraceUntil;
    }

    /**
     * Пересчитать пройденные чекпоинты по текущему положению игрока.
     * Вызывается на каждом тике игры: отдельный триггер здесь не нужен, чекпоинт — это
     * просто отметка на оси уровня.
     */
    private void updateReachedCheckpoint() {
        if (this.checkpoints.isEmpty()) return;
        if (this.currentState != State.RUNNING) return;
        // ЧЕКПОИНТ НЕ БЕРЁТСЯ, ЕСЛИ ОТКАТИТЬ НА НЕГО ВСЁ РАВНО НЕЛЬЗЯ.
        //
        // Раньше отметка засчитывалась по одной геометрии: звук, «ЧЕКПОИНТ 1» в
        // актионбаре - а откат при этом был выключен (пак нарезки не доехал, нарезка
        // не совпадает с чекпоинтами, у уровня нет трека). Игрок видел, что взял
        // чекпоинт, падал - и проигрывал с нуля.
        if (!this.hasWorkingCheckpoints()) return;

        double passed = this.getPassedDistancePublic(false);
        int reached = this.reachedCheckpoint;

        while (reached + 1 < this.checkpoints.size()) {
            double checkpointDistance = ru.sortix.parkourbeat.levels.LightShowPositions
                .getSignedDistance(this.level, this.checkpoints.get(reached + 1).getPosition());
            if (passed + 0.001D < checkpointDistance) break;
            reached++;
        }

        if (reached == this.reachedCheckpoint) return;
        this.reachedCheckpoint = reached;

        if (!this.displayTimecode) {
            this.player.playSound(this.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.8f);
        }
        // Только актионбар: титл перекрывал бы оценки прыжков.
        // Снимаем состояние забега: при откате игрок вернётся именно к нему.
        this.checkpointRunSnapshot = this.runTracker.snapshot();
        try {
            this.checkpointAccuracySnapshot = this.gameMoveHandler.getAccuracyChecker().snapshot();
        } catch (Exception e) {
            this.checkpointAccuracySnapshot = null;
        }

        this.player.sendActionBar(Lang.text(this.player, "game.checkpoint",
            "%number%", String.valueOf(reached + 1)));
        // Счётчик делит актионбар с этим сообщением, поэтому сперва даём ему повисеть,
        // и только потом на пять секунд показываем оставшиеся попытки.
        this.showCheckpointAttempts(ATTEMPTS_SHOW_AFTER_CHECKPOINT_MILLIS, 1200L);
    }

    /**
     * Проигрыш на уровне с чекпоинтами: игрок не вылетает, а возвращается на последний
     * пройденный чекпоинт, и музыка запускается ровно с этого же места.
     *
     * @return true, если откат выполнен и обычный проигрыш отменяется
     */
    /**
     * Почему откат на чекпоинт сейчас невозможен. null - возможен.
     */
    @Nullable
    private String checkpointRefusal() {
        if (this.currentState != State.RUNNING) return "забег не идёт";
        if (this.checkpoints.isEmpty()) return "на уровне нет чекпоинтов";
        if (this.checkpointPackFailed) return "пак нарезки не загрузился, играется цельный трек";
        GameSettings settings = this.level.getLevelSettings().getGameSettings();
        if (settings.getMusicTrack() == null) return "у уровня нет трека";
        if (settings.isUseTrackPieces()) return "включена посекундная синхронизация";
        if (!settings.hasUsableSlices(this.checkpoints.size())) {
            return "нарезка трека не совпадает с чекпоинтами (перенарежь трек в редакторе)";
        }
        if (this.musicMode != MusicMode.CHECKPOINTS) return "режим музыки " + this.musicMode;
        if (this.reachedCheckpoint < 0) return "ни один чекпоинт ещё не пройден";

        // Попытки кончились — уровень честно проваливается, иначе игрок застрянет здесь
        // навсегда и будет откатываться бесконечно.
        if (this.checkpointRespawns >= this.getCheckpointAttempts()) {
            return "попытки кончились (" + this.checkpointRespawns + "/" + this.getCheckpointAttempts() + ")";
        }

        // Модификаторы, у которых мгновенный проигрыш — это и есть весь смысл.
        // Без этого исключения чекпоинты превращали SUDDEN DEATH и PERFECT в обычный
        // забег с бесконечными попытками, но с повышенным множителем очков.
        if (this.hasModifier(Modifier.PRACTICE)) return "модификатор PRACTICE";
        if (this.hasModifier(Modifier.SUDDEN_DEATH)) return "модификатор SUDDEN_DEATH";
        if (this.hasModifier(Modifier.PERFECT)) return "модификатор PERFECT";
        return null;
    }

    private boolean tryRespawnAtCheckpoint(@Nullable Component reasonFirstLine,
                                           @Nullable Component reasonSecondLine) {
        String refusal = this.checkpointRefusal();
        if (refusal != null) {
            // Молчать тут нельзя: со стороны отказ выглядит ровно как «чекпоинт сломался».
            if (!this.checkpoints.isEmpty() && this.currentState == State.RUNNING) {
                this.getPlugin().getLogger().info("Чекпоинт не сработал у " + this.player.getName()
                    + " на уровне " + this.level.getUniqueId() + ": " + refusal);
            }
            return false;
        }

        int index = this.reachedCheckpoint;
        ru.sortix.parkourbeat.levels.settings.Checkpoint checkpoint = this.checkpoints.get(index);
        this.checkpointRespawns++;
        this.applyCheckpointPenalty(checkpoint);

        // Грейс включается ДО телепорта, а не в колбэке: телепорт асинхронный, и между
        // вызовом и его завершением успевает прилететь ещё несколько move-событий.
        this.respawnGraceUntil = System.currentTimeMillis() + 1500L;

        // Ни титла, ни сабтитла: просто тихо возвращаем на чекпоинт.
        this.player.playEffect(EntityEffect.HURT);
        this.player.playSound(this.player.getLocation(), Sound.ENTITY_WOLF_HURT, 1.0F, 1.0F);

        this.stopSliceTask();
        this.musicTracksManager.getPlatform().stopPlayingSlice(this.player, this.currentSlice);

        Location target = checkpoint.toLocation(this.level.getWorld());
        target = LocationUtils.alignToDirection(target,
            this.level.getLevelSettings().getDirectionChecker());
        target.setPitch(this.player.getLocation().getPitch());

        this.player.setFallDistance(0f);
        this.player.setHealth(20.0D);

        final Location finalTarget = target;
        final int finalIndex = index;
        final long pause = CHECKPOINT_PAUSE_ENABLED ? CHECKPOINT_PAUSE_MILLIS : 0L;
        if (pause > 0L) {
            // Грейс до телепорта тоже растягиваем: пауза начнётся только в колбэке.
            this.respawnGraceUntil = System.currentTimeMillis() + 1500L + pause;
        }
        TeleportUtils.teleportAsync(this.getPlugin(), this.player, finalTarget).thenAccept(success -> {
            if (!this.player.isOnline()) return;
            // Пока летел телепорт, забег мог закончиться штатно или игрок вышел с уровня.
            if (this.currentState != State.RUNNING) return;

            // ВОЗВРАТ СОСТОЯНИЯ ЗАБЕГА.
            //
            // Очки, комбо и счётчики попаданий откатываются к значениям на чекпоинте.
            // Иначе смерть под конец уровня стоила бы игроку всего комбо и части очков,
            // а шансов у него максимум четыре — наказание вышло бы несоразмерным.
            if (this.checkpointRunSnapshot != null) {
                this.runTracker.restore(this.checkpointRunSnapshot);
            }
            if (this.checkpointAccuracySnapshot != null) {
                this.gameMoveHandler.getAccuracyChecker().restore(this.checkpointAccuracySnapshot);
            } else {
                // Слепка нет — хотя бы перематываем указатель сегмента: он умеет только
                // расти, и без этого игрок мерился бы против участка далеко впереди.
                this.gameMoveHandler.getAccuracyChecker().rewindTo(finalTarget);
            }

            UserActivity activity = this.getPlugin().get(ActivityManager.class).getActivity(this.player);
            PlayActivity pa = null;
            if (activity instanceof PlayActivity found) {
                pa = found;
            } else if (activity instanceof EditActivity editor) {
                pa = editor.getTestingActivity();
            }
            if (pa != null) {
                pa.resetTriggerIndexToPosition(this.getPassedDistancePublic(false));
                pa.applyJudgementGrace(800L + pause);
            }
            this.gameMoveHandler.applyTeleportGrace(1000L + pause);
            this.respawnGraceUntil = System.currentTimeMillis() + 400L + pause;

            // Время песни отматывается на отметку чекпоинта: и таймкод, и лайтшоу,
            // и боссбар после отката показывают то же, что при обычном проходе.
            // На паузе время стоит ровно на отметке и трогается вместе с игроком.
            long now = System.currentTimeMillis();
            this.songStartedAtMillis = now - this.checkpointOffsets.get(finalIndex);
            this.songStoppedAtMillis = pause > 0L ? now : 0L;

            if (pause > 0L) {
                // Якорь - фактическая позиция ПОСЛЕ телепорта, а не цель: так пауза
                // держит игрока ровно там, где он стоит, без рывков на полблока.
                this.runCheckpointPause(finalIndex, pause, this.player.getLocation());
                this.showCheckpointAttempts(ATTEMPTS_SHOW_AFTER_FAIL_MILLIS, 0L);
                return;
            }

            // НУМЕРАЦИЯ КУСКОВ.
            //
            // part1 - от старта до 1-го чекпоинта, part2 - от 1-го до 2-го и так далее.
            // Значит с чекпоинта с индексом i (это (i+1)-й по счёту) играть надо part(i+2),
            // а не part(i+1). Из-за этой единицы после смерти включался кусок ПЕРЕД
            // чекпоинтом - то есть музыка уезжала обратно к началу трека.
            this.playSliceAfterRespawn(finalIndex + 2);
            this.showCheckpointAttempts(ATTEMPTS_SHOW_AFTER_FAIL_MILLIS, 0L);
        });
        return true;
    }

    /**
     * Запустить кусок нарезки с указанного номера (1 — от старта до первого чекпоинта)
     * и завести таймер на следующий кусок.
     */
    /**
     * Запустить кусок после отката, дав клиенту время переварить остановку прошлого.
     * <p>
     * AMusic глушит звук отдельным пакетом. Если сразу за ним прислать пакет запуска,
     * клиент нередко обрабатывает их в обратном порядке и глушит только что начатый
     * кусок - получается тишина до самого следующего стыка. Пара тиков паузы это снимает.
     */
    /** Стоит ли игрок сейчас в паузе после отката. */
    public boolean isCheckpointPaused() {
        return this.checkpointPauseUntil != 0L;
    }

    /** Где игрок обязан стоять, пока идёт пауза. null - паузы нет. */
    @Nullable
    public Location getCheckpointPauseAnchor() {
        return this.checkpointPauseAnchor;
    }

    /**
     * ПАУЗА: ИГРОК ЗАМИРАЕТ, ИДЁТ ОТСЧЁТ БЕЗ СЛОВ, ПОТОМ ЗАБЕГ ТРОГАЕТСЯ.
     * <p>
     * Отсчёт - три точки, гаснущие по одной, и стрелка на старте. Никакого текста:
     * одинаково читается на любом языке.
     * <p>
     * Двигаться и прыгать нельзя: смещение режет {@code PlayActivity}. Замедление
     * здесь только ради эффекта - оно сужает поле зрения, и это читается как «замер».
     */
    private void runCheckpointPause(int checkpointIndex, long pauseMillis, @NonNull Location anchor) {
        int token = ++this.checkpointPauseToken;
        this.checkpointPauseAnchor = anchor.clone();
        this.checkpointPauseUntil = System.currentTimeMillis() + pauseMillis;

        int pauseTicks = (int) Math.max(3L, pauseMillis / 50L);
        this.player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

        String[] frames = {"&f\u25CF \u25CF \u25CF", "&f\u25CF \u25CF", "&f\u25CF"};
        int stepTicks = Math.max(1, pauseTicks / frames.length);
        Duration stepStay = Duration.ofMillis(stepTicks * 50L + 100L);

        for (int i = 0; i < frames.length; i++) {
            final String frame = frames[i];
            final float pitch = 1.0f + i * 0.12f;
            this.getPlugin().getServer().getScheduler().runTaskLater(this.getPlugin(), () -> {
                if (!this.isPauseAlive(token)) return;
                this.player.showTitle(Title.title(PbText.of(frame), Component.empty(),
                    Title.Times.of(Duration.ZERO, stepStay, Duration.ZERO)));
                this.player.playSound(this.player.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, pitch);
            }, (long) i * stepTicks);
        }

        this.getPlugin().getServer().getScheduler().runTaskLater(this.getPlugin(), () -> {
            if (!this.isPauseAlive(token)) return;
            this.endCheckpointPause();

            this.player.showTitle(Title.title(PbText.of("&a&l\u25B6"), Component.empty(),
                Title.Times.of(Duration.ZERO, Duration.ofMillis(300), Duration.ofMillis(200))));
            this.player.playSound(this.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);

            // Время трогается ровно вместе с музыкой. Прошлый кусок заглушён ещё до
            // телепорта, так что обходной задержки на пару тиков тут не нужно.
            long now = System.currentTimeMillis();
            this.songStartedAtMillis = now - this.checkpointOffsets.get(checkpointIndex);
            this.songStoppedAtMillis = 0L;
            this.playSliceFrom(checkpointIndex + 2);

            // Отпустил Ctrl, пока стоял, - наказание начнётся, как только кончится грейс.
            this.gameMoveHandler.onCheckpointResume(this.player);
        }, (long) frames.length * stepTicks);
    }

    private boolean isPauseAlive(int token) {
        return token == this.checkpointPauseToken
            && this.checkpointPauseUntil != 0L
            && this.currentState == State.RUNNING
            && this.player.isOnline();
    }

    /** Снять паузу и её эффекты. Безопасно звать когда угодно. */
    private void endCheckpointPause() {
        if (this.checkpointPauseUntil == 0L) return;
        this.checkpointPauseUntil = 0L;
        this.checkpointPauseAnchor = null;
        this.checkpointPauseToken++;
        if (!this.player.isOnline()) return;
        if (PAUSE_SLOW != null) this.player.removePotionEffect(PAUSE_SLOW);
    }

    // Имена эффектов менялись между версиями API (SLOW -> SLOWNESS),
    // поэтому берём их по имени, а не полем: так собирается и работает на обеих.
    @Nullable
    private static final org.bukkit.potion.PotionEffectType PAUSE_SLOW = effectType("SLOW", "SLOWNESS");

    @SuppressWarnings("deprecation")
    @Nullable
    private static org.bukkit.potion.PotionEffectType effectType(@NonNull String... names) {
        for (String name : names) {
            try {
                org.bukkit.potion.PotionEffectType type = org.bukkit.potion.PotionEffectType.getByName(name);
                if (type != null) return type;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private void applyFreezeEffect(@Nullable org.bukkit.potion.PotionEffectType type, int ticks, int amplifier) {
        if (type == null) return;
        // Снимаем прежний: более слабый эффект того же типа иначе не перезаписался бы.
        this.player.removePotionEffect(type);
        this.player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            type, ticks, amplifier, false, false, false));
    }

    private void playSliceAfterRespawn(int sliceNumber) {
        this.getPlugin().getServer().getScheduler().runTaskLater(this.getPlugin(), () -> {
            if (this.currentState != State.RUNNING) return;
            if (!this.player.isOnline()) return;
            this.playSliceFrom(sliceNumber);
        }, 3L);
    }

    private void playSliceFrom(int sliceNumber) {
        java.util.List<Integer> durations = this.level.getLevelSettings()
            .getGameSettings().getSliceDurationsMillis();
        if (sliceNumber < 1) sliceNumber = 1;
        if (sliceNumber > durations.size()) return;
        if (!this.player.isOnline()) return;

        this.currentSlice = sliceNumber;
        this.sliceStartedAtMillis = System.currentTimeMillis();
        this.musicTracksManager.getPlatform().startPlayingSlice(this.player, sliceNumber);
        this.scheduleNextSlice(durations.get(sliceNumber - 1));
    }

    /** Момент запуска текущего куска по системным часам. */
    private volatile long sliceStartedAtMillis = 0L;

    /**
     * Следующий кусок заводится по РЕАЛЬНОЙ длительности предыдущего, которую померил
     * ffmpeg на прокси. Считать по отметкам чекпоинтов нельзя: ogg режется по границам
     * страниц, и куски отличаются от расчётных на десятки миллисекунд — за пару стыков
     * это превратилось бы в слышимый разъезд.
     */
    private void scheduleNextSlice(int currentSliceMillis) {
        this.stopSliceTask();
        if (currentSliceMillis <= 0) return;

        final long targetAtMillis = this.sliceStartedAtMillis + currentSliceMillis;
        this.scheduleSliceCheck(targetAtMillis);
    }

    /**
     * Стык кусков сверяется с СИСТЕМНЫМИ ЧАСАМИ, а не отсчитывается тиками.
     * <p>
     * Тик на просевшем сервере длится больше 50 мс, и отложенная на N тиков задача
     * приходит позже реального конца куска. За несколько стыков это накопилось бы в
     * слышимую паузу и разъезд музыки с уровнем. Поэтому задача просыпается заранее,
     * проверяет часы и при необходимости досыпает.
     */
    private void scheduleSliceCheck(long targetAtMillis) {
        long remaining = targetAtMillis - System.currentTimeMillis();
        if (remaining <= 0L) {
            this.playSliceFrom(this.currentSlice + 1);
            return;
        }

        // Спим не более секунды за раз: так лаг любой длительности будет замечен.
        long sleepMillis = Math.min(remaining, 1000L);
        long delayTicks = Math.max(1L, sleepMillis / 50L);

        this.sliceTask = this.getPlugin().getServer().getScheduler().runTaskLater(
            this.getPlugin(),
            () -> {
                if (this.currentState != State.RUNNING) return;
                if (!this.player.isOnline()) return;
                this.scheduleSliceCheck(targetAtMillis);
            },
            delayTicks);
    }

    private void stopSliceTask() {
        if (this.sliceTask == null) return;
        try {
            this.sliceTask.cancel();
        } catch (Exception ignored) {
        }
        this.sliceTask = null;
    }

    public void tryToSendTrackPiece() {
        double distance = this.getPassedDistance(true);
        int trackSectionNumber = (int) Math.floor(distance / BLOCKS_PER_SECOND) + 1;
        if (trackSectionNumber <= this.lastTrackPieceNumber) return;
        this.lastTrackPieceNumber = trackSectionNumber;
        this.sendTrackPiece(trackSectionNumber);
    }

    private void sendTrackPiece(int trackSectionNumber) {
        this.musicTracksManager.getPlatform().startPlayingTrackPiece(this.player, trackSectionNumber);
    }

    /**
     * Множитель сложности уровня, выставленный строителем в редакторе (по умолчанию 1.0).
     * Это НЕ рейтинговое название сложности, а реальная жёсткость геймплея.
     */
    public double getDifficultyMultiplier() {
        try {
            return this.level.getLevelSettings().getGameSettings().getDifficultyMultiplier();
        } catch (Exception e) {
            return 1.0D;
        }
    }

    /**
     * Во сколько раз больнее бьёт уровень с поднятой сложностью.
     * При сложности 9 урон почти в 6 раз сильнее обычного.
     */
    public double getDamageScale() {
        double extra = Math.max(0.0D, this.getDifficultyMultiplier() - 1.0D);
        return 1.0D + DAMAGE_GROW_PER_DIFFICULTY_LEVEL * extra;
    }

    /**
     * Насколько чаще уровень с поднятой сложностью наносит периодический урон.
     */
    public double getDamageRateScale() {
        double extra = Math.max(0.0D, this.getDifficultyMultiplier() - 1.0D);
        return 1.0D + DAMAGE_RATE_GROW_PER_DIFFICULTY_LEVEL * extra;
    }

    /** Прирост силы урона за каждую единицу сложности сверх 1.0. */
    public static final double DAMAGE_GROW_PER_DIFFICULTY_LEVEL = 0.6D;
    /** Прирост частоты урона за каждую единицу сложности сверх 1.0. */
    public static final double DAMAGE_RATE_GROW_PER_DIFFICULTY_LEVEL = 0.35D;

    public void applyDamage(double amount) {
        if (this.displayTimecode) return;

        amount *= this.getDamageScale();

        double newHealth = Math.max(0.0D, this.player.getHealth() - amount);
        if (newHealth <= 0.0D) {
            this.failLevel(LangOptions.level_play_title_death.getComponent(player), null);
        } else {
            this.player.setHealth(newHealth);
            this.player.playEffect(EntityEffect.HURT);
            this.player.playSound(this.player.getLocation(), Sound.ENTITY_WOLF_HURT, 1.0F, 1.0F);
        }
    }

    public void failLevel(@Nullable Component reasonFirstLine, @Nullable Component reasonSecondLine) {
        // Игрока только что откатило на чекпоинт — он ещё летит в телепорте.
        // Любой проигрыш в этом окне игнорируется целиком.
        if (this.isRespawnGrace()) return;

        // Уровень с чекпоинтами не выкидывает игрока: его откатывает на последний
        // пройденный чекпоинт, и музыка идёт с того же места.
        if (this.tryRespawnAtCheckpoint(reasonFirstLine, reasonSecondLine)) return;

        if (this.currentState == State.RUNNING
            && this.hasModifier(Modifier.PRACTICE)
            && this.getDisplayAccuracy() >= 45.0D) {

            this.player.showTitle(Title.title(
                reasonFirstLine == null ? Component.empty() : reasonFirstLine,
                reasonSecondLine == null ? Component.empty() : reasonSecondLine,
                FINISH_REASON_TITLE_TIMES
            ));
            this.player.playEffect(EntityEffect.HURT);
            this.player.playSound(this.player.getLocation(), Sound.ENTITY_WOLF_HURT, 1.0F, 1.0F);

            Location rewind = this.level.getSpawn();
            UserActivity activity = this.getPlugin().get(ActivityManager.class).getActivity(this.player);
            PlayActivity pa = null;
            if (activity instanceof PlayActivity) {
                pa = (PlayActivity) activity;
            } else if (activity instanceof EditActivity) {
                pa = ((EditActivity) activity).getTestingActivity();
            }
            if (pa != null && pa.getLastPlayerJumpLocation() != null) {
                rewind = pa.getLastPlayerJumpLocation();
            }

            this.player.setFallDistance(0f);
            this.player.setHealth(20.0D);

            final Location finalRewind = rewind;
            final PlayActivity finalPa = pa;
            TeleportUtils.teleportAsync(this.getPlugin(), this.player, finalRewind).thenAccept(success -> {
                double newDist = this.getPassedDistancePublic(false);
                if (finalPa != null) {
                    finalPa.resetTriggerIndexToPosition(newDist);
                }
                this.player.setAllowFlight(true);
                this.player.setFlying(true);
            });
            return;
        }

        double currentProgress = this.getPassedProgress() * 100.0D;
        AccuracyGrade grade = this.getCurrentGrade();

        // Прохождение пишем в историю и пересчитываем рекорд ДО показа титла (п.9 ТЗ):
        // титл зависит от того, рекорд это или нет.
        RunSubmission submission = this.submitRunResult(false, currentProgress);

        // Личный рекорд без финиша фиксируется СТРОГО по процентам (не по точности)
        // это решает RecordComparison, здесь просто читаем отве
        //
        // В базу рекрд уходит в любом случае, но показываем его, только если
        // прирост осмысленный: иначе получается «прогресс 8% -> +0%
        boolean isNewPR = submission != null
            && submission.isPersonalRecord()
            && currentProgress > 1.0D
            && submission.getProgressDelta() >= MIN_PROGRESS_RECORD_DELTA;

        // ДИАГНОСТИКА ЗАБЕГОВ С ОТКАТАМИ: игроки говорят, что рекорд не ставится.
        // Одна строка в консоль на проигрыш - по ней видно, где именно он теряется.
        if (this.checkpointRespawns > 0) {
            RunResult prev = submission == null ? null : submission.getPreviousPersonalRecord();
            this.getPlugin().getLogger().info("[чекпоинты] проигрыш " + this.player.getName()
                + ": откатов=" + this.checkpointRespawns
                + " прогресс=" + String.format(java.util.Locale.ROOT, "%.1f", currentProgress)
                + " записан=" + (submission != null)
                + " рекорд=" + (submission != null && submission.isPersonalRecord())
                + " прошлый=" + (prev == null ? "нет" : String.format(java.util.Locale.ROOT, "%.1f%s",
                prev.getProgressPercent(), prev.isCompleted() ? " (пройден)" : ""))
                + " показан=" + isNewPR);
        }

        this.stopMusic();

        CompletionParticle fallParticle = this.level.getLightShow().getLoseParticle();

        if (isNewPR) {
            String gradeColor = grade.getColorCode();
            this.sendProgressRecordMessage(submission);

            String lang = PlayerLang.of(this.player);
            Component title = Lang.text(lang, "game.title.record_personal");
            Component subtitle = Lang.text(lang, "game.subtitle.progress",
                "%progress%", String.format(java.util.Locale.ROOT, "%s%.0f%%", gradeColor, currentProgress));

            this.player.showTitle(Title.title(title, subtitle, FINISH_REASON_TITLE_TIMES));
            TeleportUtils.teleportAsync(this.getPlugin(), this.player, this.getAlignedSpawn()).whenComplete((success, throwable) -> {
                try {
                    if (fallParticle != null) fallParticle.play(this.player);
                    this.resetLevelGame(null, null, false);
                } catch (Throwable t) {
                    this.getPlugin().getLogger().log(java.util.logging.Level.SEVERE, "Unable to reset game", t);
                }
            });
            return;
        }
        TeleportUtils.teleportAsync(this.getPlugin(), this.player, this.getAlignedSpawn()).whenComplete((success, throwable) -> {
            try {
                if (fallParticle != null) fallParticle.play(this.player);
                String progress = this.bossBar == null ? null : String.format("%.0f", this.bossBar.progress() * 100f);
                this.resetLevelGame(
                    reasonFirstLine,
                    reasonSecondLine != null
                        ? reasonSecondLine
                        : progress == null ? Component.empty() : LangOptions.level_play_progress.getComponent(player, new Placeholders("%value%", progress)),
                    false
                );
            } catch (Throwable t) {
                this.getPlugin().getLogger().log(java.util.logging.Level.SEVERE, "Unable to reset game", t);
            }
        });
    }

    public void completeLevel() {
        try {
            this.getPlugin().get(ru.sortix.parkourbeat.tutorial.TutorialManager.class)
                .onLevelCompleted(this.player);
        } catch (Throwable ignored) {
        }

        double currentAcc = this.getDisplayAccuracy();
        AccuracyGrade grade = this.getCurrentGrade();
        int score = this.getFinalScore();
        int maxCombo = this.runTracker.getMaxCombo();
        int misses = this.runTracker.getMissCount();

        LevelDifficulty diff = this.level.getLevelSettings().getGameSettings().getDifficulty();
        boolean isUnranked = (diff == LevelDifficulty.N_A);
        RunSubmission submission = this.submitRunResult(true, 100.0D);
        boolean isGlobalRecord = submission != null && submission.isGlobalRecord();
        boolean isPersonalRecord = submission != null && submission.isPersonalRecord();

        Component title;
        Component subtitle;

        String gradeColor = grade.getColorCode();

        String lang = PlayerLang.of(this.player);
        String scored = Lang.raw(lang, "game.subtitle.scored",
            "%score%", String.valueOf(score),
            "%accuracy%", String.format(java.util.Locale.ROOT, "%s%.2f%%", gradeColor, currentAcc));

        if (isUnranked) {
            title = Lang.text(lang, "game.title.completed");
            subtitle = Lang.text(lang, "game.subtitle.unranked");
        } else if (isGlobalRecord) {
            title = Lang.text(lang, "game.title.record_global");
            subtitle = PbText.of(scored);
        } else if (isPersonalRecord) {
            title = Lang.text(lang, "game.title.record_personal");
            subtitle = PbText.of(scored);
        } else {
            title = Lang.text(lang, "game.title.completed");
            subtitle = Lang.text(lang, "game.subtitle.grade", "%grade%", grade.getFormatted());
        }

        this.player.showTitle(Title.title(title, subtitle, FINISH_REASON_TITLE_TIMES));
        this.sendSummaryChatMessage(currentAcc, grade, score, maxCombo, misses, isUnranked, submission);

        CompletionParticle winParticle = this.level.getLightShow().getWinParticle();
        boolean shouldSpawnFireworks = isPersonalRecord;

        TeleportUtils.teleportAsync(this.getPlugin(), this.player, this.getAlignedSpawn()).whenComplete((success, throwable) -> {
            try {
                if (shouldSpawnFireworks) {
                    this.spawnFirework(this.level.getSpawn());
                }
                if (winParticle != null) winParticle.play(this.player);
                this.resetLevelGame(null, null, true);
            } catch (Throwable t) {
                this.getPlugin().getLogger().log(java.util.logging.Level.SEVERE, "Unable to reset game", t);
            }
        });
    }

    @Nullable
    private boolean isUnrankedLevel() {
        return this.level.getLevelSettings().getGameSettings().getDifficulty() == LevelDifficulty.N_A;
    }

    /**
     * Забег с откатами на чекпоинт идёт в статистику и в рекорды, но с урезанными очками.
     * <p>
     * Пройти уровень с нуля и пройти его, умерев пять раз у самого финиша, - разные
     * результаты. Раньше такой забег не записывался вовсе, и смерть на 40% просто
     * пропадала. Теперь прогресс сохраняется честно, а разница уходит в очки и PP.
     * <p>
     * Базовая часть штрафа за один откат.
     */
    public static final double CHECKPOINT_PENALTY_BASE = 0.04D;
    /** Сколько добавляется к штрафу, если чекпоинт стоит у самого финиша. */
    public static final double CHECKPOINT_PENALTY_BY_POSITION = 0.12D;
    /** Ниже этой доли очков забег не урезается никакими откатами. */
    public static final double CHECKPOINT_PENALTY_FLOOR = 0.50D;

    /**
     * ШТРАФ ЗА ОДИН ОТКАТ.
     * <p>
     * Откат стоит тем дороже, чем дальше стоит чекпоинт: смерть у финиша без чекпоинта
     * отправила бы игрока переигрывать почти весь уровень, а смерть на первом - лишь
     * небольшой кусок. То есть поздний чекпоинт экономит игроку больше работы, и
     * списывается за него больше.
     * <p>
     * Чекпоинт в начале - около 4%, у финиша - около 16%. Штрафы перемножаются, а не
     * складываются: так пять откатов подряд не уводят очки в ноль, а пол не даёт
     * опуститься ниже половины.
     */
    private void applyCheckpointPenalty(@NonNull ru.sortix.parkourbeat.levels.settings.Checkpoint checkpoint) {
        double position = 0.0D;
        try {
            double total = this.level.getLevelSettings().getTotalLevelDistance();
            if (total > 0.0D) {
                position = ru.sortix.parkourbeat.levels.LightShowPositions
                    .getSignedDistance(this.level, checkpoint.getPosition()) / total;
            }
        } catch (Throwable ignored) {
        }
        if (Double.isNaN(position)) position = 0.0D;
        position = Math.max(0.0D, Math.min(1.0D, position));

        double cost = CHECKPOINT_PENALTY_BASE + CHECKPOINT_PENALTY_BY_POSITION * position;
        this.checkpointScoreFactor = Math.max(CHECKPOINT_PENALTY_FLOOR,
            this.checkpointScoreFactor * (1.0D - cost));
    }

    /** Очки забега с учётом откатов. Именно их видит игрок и именно они идут в базу. */
    public int getFinalScore() {
        return (int) Math.round(this.runTracker.getScore() * this.checkpointScoreFactor);
    }

    private RunSubmission submitRunResult(boolean completed, double progressPercent) {
        if (this.runSubmitted) return null;
        if (this.displayTimecode) return null;
        if (this.modifiers.isActive(Modifier.PRACTICE)) return null;
        // Уровень без сложности не прошёл модерацию. Записывать по нему рейтинг нельзя:
        // иначе любой мог бы сделать уровень на секунду и фармить с него PP и рекорды.
        if (this.isUnrankedLevel()) return null;
        if (!completed && progressPercent < 1.0D && this.runTracker.getTotalJudged() == 0) return null;

        StatisticsManager statistics;
        try {
            statistics = this.getPlugin().get(StatisticsManager.class);
        } catch (Exception e) {
            return null;
        }
        if (statistics == null) return null;

        GameSettings settings = this.level.getLevelSettings().getGameSettings();
        long timeMillis = this.getSongTimeMillis();

        double expectedMillis = (this.level.getLevelSettings().getTotalLevelDistance() / BLOCKS_PER_SECOND) * 1000.0D;
        boolean suspicious = completed && expectedMillis > 0.0D && timeMillis > 0L
            && timeMillis < expectedMillis * 0.8D;

        RunResult run = RunResult.builder()
            .playerId(this.player.getUniqueId())
            .playerName(this.player.getName())
            .levelId(this.level.getUniqueId())
            .levelName(settings.getDisplayNameLegacy(false))
            .difficulty(settings.getDifficulty())
            .progressPercent(Math.max(0.0D, Math.min(100.0D, progressPercent)))
            .completed(completed)
            .accuracy(this.getDisplayAccuracy())
            .grade(this.getCurrentGrade())
            .score(this.getFinalScore())
            .rawScore(this.runTracker.getRawScore())
            .maxCombo(this.runTracker.getMaxCombo())
            .count300(this.runTracker.getPerfectCount())
            .count100(this.runTracker.getGoodCount())
            .count50(this.runTracker.getOkCount())
            .missCount(this.runTracker.getMissCount())
            .modifiers(new java.util.HashSet<>(this.modifiers.getActive()))
            // Штраф за откаты вшивается в множитель: по нему считается PP, и забег с
            // откатами даёт меньше рейтинга ровно в той же пропорции, что и очков.
            .multiplier(this.modifiers.getTotalMultiplier() * this.checkpointScoreFactor)
            .timeMillis(timeMillis)
            .timestamp(System.currentTimeMillis())
            .suspicious(suspicious)
            .build();

        this.runSubmitted = true;
        try {
            return statistics.submitRun(run);
        } catch (Exception e) {
            this.getPlugin().getLogger().log(java.util.logging.Level.SEVERE,
                "Не удалось записать прохождение игрока " + this.player.getName(), e);
            return null;
        }
    }

    private void sendSummaryChatMessage(double accuracy, AccuracyGrade grade, int score, int maxCombo, int misses,
                                        boolean isUnranked, @Nullable RunSubmission submission) {
        String lang = PlayerLang.of(this.player);
        StringBuilder message = new StringBuilder(Lang.raw(lang, "game.summary.header",
            "%accuracy%", String.format(java.util.Locale.ROOT, "%.2f%%", accuracy),
            "%grade%", grade.getFormatted(),
            "%score%", String.valueOf(score),
            "%combo%", String.valueOf(maxCombo),
            "%miss%", String.valueOf(misses)));

        // Полное комбо с откатом не бывает: откат - это та же ошибка, просто прощённая.
        if (misses == 0 && this.checkpointRespawns == 0) {
            message.append(" &7[&b&lFC&7]");
        }

        // Откаты на чекпоинт показываем отдельной строкой: пройти уровень с нуля
        // и пройти его с пятью откатами — это очень разные достижения.
        if (this.checkpointRespawns > 0) {
            message.append("\n").append(Lang.raw(lang, "game.summary.checkpoints",
                "%count%", String.valueOf(this.checkpointRespawns)));
            int penalty = (int) Math.round((1.0D - this.checkpointScoreFactor) * 100.0D);
            if (penalty > 0) message.append(" &8(&c-").append(penalty).append("%&8)");
        }

        if (submission != null) {
            RunResult previous = submission.getPreviousPersonalRecord();
            if (submission.isPersonalRecord() && previous != null && previous.isCompleted()) {
                int delta = submission.getScoreDelta();
                message.append("\n").append(Lang.raw(lang, "game.summary.previous_personal",
                    "%score%", String.valueOf(previous.getScore()),
                    "%delta%", (delta >= 0 ? "&a" : "&c")
                        + String.format(java.util.Locale.ROOT, "%+d", delta)));
            }

            RunResult previousGlobal = submission.getPreviousGlobalRecord();
            if (submission.isGlobalRecord() && previousGlobal != null) {
                message.append("\n").append(Lang.raw(lang, "game.summary.previous_global",
                    "%player%", previousGlobal.getPlayerName(),
                    "%score%", String.valueOf(previousGlobal.getScore())));
            }

            if (!isUnranked && submission.getTopPosition() > 0) {
                message.append("\n").append(Lang.raw(lang, "game.summary.level_place",
                    "%position%", positionColor(submission.getTopPosition())
                        + "#" + submission.getTopPosition(),
                    "%total%", String.valueOf(submission.getTopSize())));

                StatisticsManager statisticsManager = this.getPlugin().get(StatisticsManager.class);
                int globalRank = statisticsManager.getDisplayRank(this.player.getUniqueId());
                if (globalRank > 0) {
                    message.append("\n").append(Lang.raw(lang, "game.summary.server_rank",
                        "%position%", positionColor(globalRank) + "#" + globalRank,
                        "%total%", String.valueOf(statisticsManager.getRankedPlayersCount())));
                }
            }
        }

        if (isUnranked) {
            int levelId = this.level.getLevelSettings().getGameSettings().getUniqueNumber();
            message.append("\n\n").append(Lang.raw(lang, "game.summary.unranked",
                "%id%", String.valueOf(levelId)));
        }

        this.player.sendMessage(PbText.of(message.toString()));
    }

    private void sendProgressRecordMessage(@Nullable RunSubmission submission) {
        if (submission == null) return;
        RunResult previous = submission.getPreviousPersonalRecord();
        if (previous == null || previous.isCompleted()) return;

        double delta = submission.getRun().getProgressPercent() - previous.getProgressPercent();
        this.player.sendMessage(Lang.text(this.player, "game.summary.previous_progress",
            "%previous%", String.format(java.util.Locale.ROOT, "%.0f%%", previous.getProgressPercent()),
            "%delta%", String.format(java.util.Locale.ROOT, "%.0f%%", delta)));
    }

    @NonNull
    private static String positionColor(int position) {
        return ru.sortix.parkourbeat.stats.StatsFormat.positionColor(position);
    }

    private void spawnFirework(@NonNull Location location) {
        World world = location.getWorld();
        if (world == null) return;

        List<org.bukkit.Color> fireworkColors = this.extractColorsFromDisplayName();

        Firework fw = world.spawn(location, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
            .with(FireworkEffect.Type.BALL_LARGE)
            .withColor(fireworkColors)
            .withFade(org.bukkit.Color.WHITE)
            .flicker(true)
            .trail(true)
            .build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }

    /**
     * Извлекает уникальные цвета из названия карты для фейерверка
     */
    @NonNull
    private List<org.bukkit.Color> extractColorsFromDisplayName() {
        List<org.bukkit.Color> colors = new ArrayList<>();
        String legacyName = this.level.getLevelSettings().getGameSettings().getDisplayNameLegacy(false);

        for (int i = 0; i < legacyName.length() - 1; i++) {
            if (legacyName.charAt(i) == '§' || legacyName.charAt(i) == '&') {
                char code = Character.toLowerCase(legacyName.charAt(i + 1));
                org.bukkit.Color color = parseColorChar(code);
                if (color != null && !colors.contains(color)) {
                    colors.add(color);
                }
            }
        }

        if (colors.isEmpty()) {
            colors.add(org.bukkit.Color.YELLOW);
            colors.add(org.bukkit.Color.ORANGE);
        }
        return colors;
    }

    @Nullable
    private org.bukkit.Color parseColorChar(char code) {
        return switch (code) {
            case '0' -> org.bukkit.Color.fromRGB(0, 0, 0);
            case '1' -> org.bukkit.Color.fromRGB(0, 0, 170);
            case '2' -> org.bukkit.Color.fromRGB(0, 170, 0);
            case '3' -> org.bukkit.Color.fromRGB(0, 170, 170);
            case '4' -> org.bukkit.Color.fromRGB(170, 0, 0);
            case '5' -> org.bukkit.Color.fromRGB(170, 0, 170);
            case '6' -> org.bukkit.Color.fromRGB(255, 170, 0);
            case '7' -> org.bukkit.Color.fromRGB(170, 170, 170);
            case '8' -> org.bukkit.Color.fromRGB(85, 85, 85);
            case '9' -> org.bukkit.Color.fromRGB(85, 85, 255);
            case 'a' -> org.bukkit.Color.fromRGB(85, 255, 85);
            case 'b' -> org.bukkit.Color.fromRGB(85, 255, 255);
            case 'c' -> org.bukkit.Color.fromRGB(255, 85, 85);
            case 'd' -> org.bukkit.Color.fromRGB(255, 85, 255);
            case 'e' -> org.bukkit.Color.fromRGB(255, 255, 85);
            case 'f' -> org.bukkit.Color.fromRGB(255, 255, 255);
            default -> null;
        };
    }

    public void resetLevelGame(@Nullable Component reasonFirstLine, @Nullable Component reasonSecondLine, boolean levelComplete) {
        boolean switchState = this.currentState == State.RUNNING;
        this.resetRunningLevelGame(reasonFirstLine, reasonSecondLine, levelComplete);
        this.forceStopLevelGame();
        if (switchState) this.setCurrentState(State.READY);
        this.getPlugin().get(ru.sortix.parkourbeat.replay.ReplayManager.class)
            .cancelRecording(this.player.getUniqueId());
    }

    private void resetRunningLevelGame(@Nullable Component reasonFirstLine, @Nullable Component reasonSecondLine, boolean levelComplete) {
        if (this.currentState != State.RUNNING) return;

        if (reasonFirstLine != null || reasonSecondLine != null) {
            this.player.showTitle(Title.title(
                reasonFirstLine == null ? Component.empty() : reasonFirstLine,
                reasonSecondLine == null ? Component.empty() : reasonSecondLine,
                FINISH_REASON_TITLE_TIMES
            ));
        }

        if (levelComplete) {
            this.player.playSound(this.player.getLocation(), Sound.ENTITY_SILVERFISH_DEATH, 1, 1);
            this.player.playSound(this.player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
        } else {
            this.player.playSound(this.player.getLocation(), Sound.ENTITY_SILVERFISH_DEATH, 1, 1);
        }

        this.gameMoveHandler.getAccuracyChecker().reset();
        this.runTracker.reset();
        this.runSubmitted = false;
        this.runTracker.setModifiers(this.modifiers.copy());
        this.lastGrade = AccuracyGrade.SS;
        this.lastBleedAtMillis = 0L;
        this.reachedCheckpoint = -1;
        this.checkpointRespawns = 0;
        this.checkpointScoreFactor = 1.0D;
        this.endCheckpointPause();
    }
    public void forceStopLevelGame() {
        this.safely("restore visibility", () -> this.getPlugin()
            .get(ru.sortix.parkourbeat.player.PlayersVisibilityManager.class)
            .restoreFor(this.player));

        this.safely("song timer", () -> {
            if (this.songStartedAtMillis != 0L && this.songStoppedAtMillis == 0L) {
                this.songStoppedAtMillis = System.currentTimeMillis();
            }
        });

        this.safely("player state", () -> {
            this.player.setHealth(20);
            this.player.setGameMode(GameMode.ADVENTURE);
            this.player.setFlying(false);
            this.player.setAllowFlight(false);
        });

        this.safely("packets adapter", () -> this.packetsAdapter.setWatchingPosition(this.player, false));
        this.safely("water cache", () -> clearWaterCache(this.player));
        this.safely("checkpoint pause", this::endCheckpointPause);
        this.safely("slice timer", this::stopSliceTask);
        this.safely("stop music", this::stopMusic);

        this.safely("particles", () -> {
            // Гасим оба контроллера: сторона к этому моменту могла уже освободиться,
            // и спрашивать "чей это был путь" поздно.
            ParticleController controller = this.level.getLevelSettings().getParticleController();
            controller.stopSpawnParticlesForPlayer(this.player);
            controller.setHiddenViewer(this.player, false);

            if (this.level.getLevelSettings().hasSecondParticleController()) {
                ParticleController second = this.level.getLevelSettings().getSecondParticleController();
                second.stopSpawnParticlesForPlayer(this.player);
                second.setHiddenViewer(this.player, false);
            }
        });

        this.safely("boss bar", this::removeBossBar);
        this.safely("light show", () -> {
            if (this.wonderRunner != null) this.wonderRunner.stopAll();
            if (this.lampRunner != null) this.lampRunner.resetAll();
            if (this.lightShowRunner != null) this.lightShowRunner.rollbackToBase();
        });
    }

    private void safely(@NonNull String what, @NonNull Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            this.getPlugin().getLogger().log(java.util.logging.Level.WARNING,
                Lang.raw(PlayerLang.of(this.player), "auto.game.safely.1") + what + ")", t);
        }
    }

    private void stopMusic() {
        if (this.musicMode == MusicMode.PIECES) {
            this.musicTracksManager.setTrackPiecesSendingEnabled(this, false);
            this.musicTracksManager.getPlatform().stopPlayingTrackPiece(this.player, this.lastTrackPieceNumber);
            this.lastTrackPieceNumber = 0;
        } else if (this.musicMode == MusicMode.FULL_TRACK) {
            this.musicTracksManager.getPlatform().stopPlayingTrackFull(this.player);
        } else if (this.musicMode == MusicMode.CHECKPOINTS) {
            this.stopSliceTask();
            this.musicTracksManager.getPlatform().stopPlayingSlice(this.player, this.currentSlice);
            this.currentSlice = 0;
        }
    }

    @NonNull
    public RunTracker getRunTracker() {
        return this.runTracker;
    }

    public boolean hasModifier(@NonNull Modifier modifier) {
        if (this.displayTimecode) return false;
        return this.modifiers.isActive(modifier);
    }

    public double getDisplayAccuracy() {
        if (this.runTracker.getTotalJudged() == 0) {
            return 100.0D;
        }
        double movementAcc = this.gameMoveHandler.getAccuracyChecker().getAccuracy() * 100.0D;
        double jumpAcc = this.runTracker.getAccuracy();
        return Math.max(0.0D, Math.min(100.0D, 0.4D * movementAcc + 0.6D * jumpAcc));
    }

    @NonNull
    public AccuracyGrade getCurrentGrade() {
        return this.runTracker.gradeFor(this.getDisplayAccuracy());
    }

    @NonNull
    public AccuracyGrade getGradeCap() {
        return this.runTracker.getGradeCap();
    }

    public void registerJump(@NonNull JumpResult result) {
        if (this.currentState != State.RUNNING) return;

        this.runTracker.registerJump(result);

        // КРАСНАЯ ВСПЫШКА НА ПРОМАХЕ.
        //
        // Тот же эффект, что доступен строителю в зонах прыжка под именем «Покраснение».
        // Там его ставят вручную и на попадание; здесь он срабатывает на промахе сам, на
        // любой карте. Надпись MISS выводится в том же месте, что и очки за удачный
        // прыжок, и на скорости эти две строчки различаются плохо - а вспышка по краям
        // экрана читается мгновенно и не требует смотреть в конкретную точку.
        if (result == JumpResult.MISS) {
            try {
                ru.sortix.parkourbeat.world.RedVignetteSender.flash(this.getPlugin(), this.player);
            } catch (Throwable ignored) {
            }
        }

        // Туториал должен реагировать на промах СРАЗУ и ощутимо, иначе правило
        // "прыгать только на метках" остаётся просто текстом на экране.
        if (result == JumpResult.MISS) {
            try {
                ru.sortix.parkourbeat.tutorial.TutorialManager tutorial =
                    this.getPlugin().get(ru.sortix.parkourbeat.tutorial.TutorialManager.class);
                if (tutorial.isActive(this.player)) tutorial.onMiss(this.player, this);
            } catch (Throwable ignored) {
            }
        }

        Component points;
        if (result == JumpResult.MISS) {
            if (this.displayTimecode) {
                points = PbText.of("&7MISS | &c-1HP");
            } else {
                points = PbText.of("&7MISS");
            }
        } else {
            points = PbText.of(result.formatPoints());
        }

        this.player.showTitle(Title.title(
            Component.empty(),
            points,
            Title.Times.of(Duration.ZERO, Duration.ofMillis(310), Duration.ofMillis(135))
        ));

        if (this.hasModifier(Modifier.PERFECT) && result != JumpResult.PERFECT) {
            this.failLevel(this.loseTitle(), null);
            return;
        }

        if (this.hasModifier(Modifier.SUDDEN_DEATH) && (result == JumpResult.OK || result == JumpResult.MISS)) {
            this.failLevel(this.loseTitle(), null);
            return;
        }
    }

    private void tickGradeEffects() {
        if (this.displayTimecode) return;

        AccuracyGrade grade = this.getCurrentGrade();
        this.lastGrade = grade;
        int interval = grade.getBleedIntervalSeconds();
        if (interval <= 0) return;

        // На поднятой сложности кровотечение идёт заметно чаще.
        long intervalMillis = (long) Math.max(200.0D, (interval * 1000.0D) / this.getDamageRateScale());

        long now = System.currentTimeMillis();
        if (this.lastBleedAtMillis == 0L) {
            this.lastBleedAtMillis = now;
            this.applyDamage(3.0D);
            return;
        }
        if (now - this.lastBleedAtMillis < intervalMillis) return;
        this.lastBleedAtMillis = now;

        this.applyDamage(3.0D);
    }

    public enum State {
        PREPARING,
        READY,
        RUNNING,
    }

    public long getSongTimeMillis() {
        if (this.songStartedAtMillis == 0L) return 0L;
        long end = this.songStoppedAtMillis == 0L ? System.currentTimeMillis() : this.songStoppedAtMillis;
        return Math.max(0L, end - this.songStartedAtMillis);
    }

    @NonNull
    public String getSongTimecode() {
        return TimeUtils.formatTimecode(this.getSongTimeMillis());
    }

    @Nullable
    public LightShowRunner getLightShowRunner() {
        return this.lightShowRunner;
    }

    private LightShowRunner ensureLightShowRunner() {
        if (this.lightShowRunner == null) {
            Consumer<LevelBossBarColor> barColorConsumer = barColor -> this.bossBarColorOverride = barColor;
            this.lightShowRunner = new LightShowRunner(
                this.getPlugin(), this.player, this.level.getLightShow(), barColorConsumer);
        }
        return this.lightShowRunner;
    }

    public void onEnterLevel() {
        // НА DIGGER-КАРТЕ ПРОИГРЫВАТЕЛЬ ШОУ НЕ СОЗДАЁТСЯ ВОВСЕ.
        //
        // Прошлая правка убрала только startShow() на старте забега - и этого не
        // хватило: здесь, при входе на уровень, ensureLightShowRunner() создаёт его
        // заново и ставит опорное небо. Дальше игровой цикл тикает этот проигрыватель,
        // он остаётся в состоянии IDLE и раз в двадцать тиков ДОСЫЛАЕТ игроку то самое
        // опорное время. Ровно секунда между откатами - это его KEEP_ALIVE_TICKS.
        //
        // Шоу на такой карте целиком принадлежит забегу, поэтому создавать второй
        // проигрыватель нельзя ни при каких условиях, даже «просто чтобы был».
        if (!this.level.getLevelSettings().getGameSettings().isDiggerLevel()) {
            this.ensureLightShowRunner().snapToBase();
        }
        this.startGameTask();
    }

    public void shutdown() {
        this.stopGameTask();
        this.removeBossBar();
        if (this.lightShowRunner != null) {
            this.lightShowRunner.shutdown();
            this.lightShowRunner = null;
        }
        this.bossBarColorOverride = null;
    }

    private void startGameTask() {
        if (this.gameTask != null && !this.gameTask.isCancelled()) return;
        this.gameTask = Bukkit.getScheduler().runTaskTimer(this.getPlugin(), this::onGameTick, 1L, 1L);
    }

    private void stopGameTask() {
        if (this.gameTask == null) return;
        if (!this.gameTask.isCancelled()) this.gameTask.cancel();
        this.gameTask = null;
    }

    @NonNull
    private LevelBossBarColor getBossBarColor() {
        LevelBossBarColor override = this.bossBarColorOverride;
        if (override != null) return override;
        return this.level.getLevelSettings().getGameSettings().getBossBarColor();
    }

    private void createBossBar() {
        this.removeBossBar();

        if (this.level.getLevelSettings().getGameSettings().isHideBossBar()) return;

        this.bossBar = BossBar.bossBar(
            Component.empty(), 0.0f, this.getBossBarColor().getBarColor(), BossBar.Overlay.PROGRESS);
        this.player.showBossBar(this.bossBar);

        if (this.displayTimecode) {
            this.technicalBossBar = BossBar.bossBar(
                Component.empty(), 0.0f, this.getBossBarColor().getBarColor(), BossBar.Overlay.PROGRESS);
            this.player.showBossBar(this.technicalBossBar);
        }
    }

    private void removeBossBar() {
        if (this.bossBar != null) {
            this.player.hideBossBar(this.bossBar);
            this.bossBar = null;
        }
        if (this.technicalBossBar != null) {
            this.player.hideBossBar(this.technicalBossBar);
            this.technicalBossBar = null;
        }
    }

    private void onGameTick() {
        if (!this.player.isOnline()) {
            this.stopGameTask();
            return;
        }

        this.updateBossBar();

        if (this.currentState == State.RUNNING) {
            this.tickGradeEffects();
            this.updateReachedCheckpoint();
            this.tickCheckpointAttempts();

            // На 360-уровне забег заканчивается только на блоке финиша, а не по
            // проценту пройденного расстояния: трассы там нет, считать по ней нечего.
            if (this.level.getLevelSettings().getGameSettings().isThreeSixtyLevel()) {
                if (!this.allowEndlessRun && this.gameMoveHandler.isThreeSixtyFinishReached(this.player)) {
                    this.completeLevel();
                    return;
                }
            } else {
                boolean isShortTestLevel = this.displayTimecode && this.level.getLevelSettings().getWorldSettings().getWaypoints().size() < 4;

                if (!this.allowEndlessRun && !isShortTestLevel && this.getPassedProgress() >= 0.999f) {
                    this.completeLevel();
                    return;
                }
            }
        }

        try {
            if (this.portalRunner == null) {
                this.portalRunner = new ru.sortix.parkourbeat.levels.PortalRunner(this.getPlugin(), this.level, this.player);
            }
            this.portalRunner.tick(this.currentState == State.RUNNING);
        } catch (Exception e) {
            this.getPlugin().getLogger().log(java.util.logging.Level.WARNING,
                "Unable to tick portals of player " + this.player.getName(), e);
        }

        LightShowRunner runner = this.lightShowRunner;
        if (runner != null) {
            try {
                // На 360-уровне шкала шоу - это высота, а не длина: элементы по-прежнему
                // хранятся в миллисекундах, но позицию в них задаёт подъём игрока.
                double distance;
                if (this.level.getLevelSettings().getGameSettings().isThreeSixtyLevel()) {
                    distance = this.getPassedHeight();
                } else {
                    distance = this.packetsAdapter.isWatchingPosition(this.player)
                        ? this.getPassedDistance(true)
                        : this.getPassedDistance(false);
                }
                long positionMillis = Math.round((distance / BLOCKS_PER_SECOND) * 1000.0D);

                runner.tick(positionMillis);

                // Чудоэффекты идут по той же шкале, что и остальное цветовое шоу:
                // у каждого бегущего своя позиция в песне, поэтому и показ у каждого свой.
                if (this.wonderRunner == null) {
                    this.getPlugin().get(ru.sortix.parkourbeat.utils.wonder.WonderStorage.class)
                        .ensureLoaded(this.level);
                    this.wonderRunner = new ru.sortix.parkourbeat.levels.wonder.WonderRunner(
                        this.player, this.level, this.level.getLightShow().getWonderEffects());
                }
                this.wonderRunner.tick(positionMillis, this.currentState == State.RUNNING);

                if (this.lampRunner == null && this.level.getWorld() != null) {
                    this.lampRunner = new ru.sortix.parkourbeat.levels.lamps.LampRunner(
                        this.level.getWorld(), this.level.getLightShow().getLampWalls());
                }
                if (this.lampRunner != null) {
                    this.lampRunner.tick(positionMillis, this.currentState == State.RUNNING);
                }
            } catch (Exception e) {
                this.getPlugin().getLogger().log(java.util.logging.Level.SEVERE,
                    "Unable to tick lightshow of player " + this.player.getName(), e);
            }
        }
    }

    private void updateBossBar() {
        if (this.bossBar == null) return;

        float progress = this.getPassedProgress();
        LevelBossBarColor barColor = this.getBossBarColor();

        // На 360-уровне в полосе показывается ТАЙМЕР, а не проценты: там нет трассы,
        // по которой считались бы проценты - есть время, за которое ты добрался.
        boolean threeSixty = this.level.getLevelSettings().getGameSettings().isThreeSixtyLevel();

        // На 360-уровне в полосе стоит ТАЙМЕР, а заполняется она по набранной высоте:
        // процентов пройденного пути там нет, а время забега - есть.
        Component name = Component.text(threeSixty
                ? this.getSongTimecode()
                : String.format("%d%%", Math.round(progress * 100)))
            .color(barColor.getTextColor())
            .decoration(TextDecoration.BOLD, true);

        if (this.displayTimecode && !threeSixty) {
            name = name
                .append(Component.text(" - ")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.BOLD, false))
                .append(Component.text(this.getSongTimecode())
                    .color(barColor.getTextColor())
                    .decoration(TextDecoration.BOLD, true));
        }

        this.bossBar.color(barColor.getBarColor());
        this.bossBar.name(name);
        this.bossBar.progress(progress);

        if (this.technicalBossBar != null) {
            double passedDistance = this.getPassedDistance(false);
            double totalDistance = this.level.getLevelSettings().getTotalLevelDistance();
            double fraction = totalDistance <= 0 ? 0 : Math.max(0, Math.min(1, passedDistance / totalDistance));
            long positionMillis = Math.round((passedDistance / BLOCKS_PER_SECOND) * 1000.0D);

            Component technicalName = Component.text(
                    String.format(java.util.Locale.ROOT, "LVL: %.2f%%", fraction * 100))
                .color(barColor.getTextColor())
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text(" - ")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.BOLD, false))
                .append(Component.text(formatPreciseTimecode(positionMillis))
                    .color(barColor.getTextColor())
                    .decoration(TextDecoration.BOLD, true));

            this.technicalBossBar.color(barColor.getBarColor());
            this.technicalBossBar.name(technicalName);
            this.technicalBossBar.progress((float) fraction);
        }
    }

    @NonNull
    private static String formatPreciseTimecode(long millis) {
        if (millis < 0) millis = 0;
        long totalHundredths = millis / 10L;
        long minutes = totalHundredths / 6000L;
        long seconds = (totalHundredths / 100L) % 60L;
        long hundredths = totalHundredths % 100L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, hundredths);
    }

    /**
     * ПРОЙДЕННАЯ ВЫСОТА НА 360-УРОВНЕ.
     * <p>
     * Отсчёт идёт ОТ СТАРТА К ФИНИШУ, а не от дна куба. Раньше высота считалась от
     * нижней границы площадки, и полоса вела себя странно: игрок появлялся уже
     * заполненным наполовину (платформа стоит в середине куба), а после финиша
     * оставалось ещё полполосы - до потолка.
     *
     * @return сколько блоков набрано от точки старта в сторону финиша, не меньше нуля
     */
    public double getPassedHeight() {
        double startY = this.getThreeSixtyStartY();
        double finishY = this.getThreeSixtyFinishY();

        double passed = finishY >= startY
            ? this.player.getLocation().getY() - startY
            : startY - this.player.getLocation().getY();

        if (passed < 0.0D) return 0.0D;
        return Math.min(passed, this.getThreeSixtyClimbBlocks());
    }

    private double getThreeSixtyStartY() {
        java.util.List<ru.sortix.parkourbeat.levels.Waypoint> waypoints =
            this.level.getLevelSettings().getWorldSettings().getWaypoints();
        if (waypoints.isEmpty()) return this.level.getCuboid().getMin().getY();
        return waypoints.get(0).getLocation().getY();
    }

    private double getThreeSixtyFinishY() {
        java.util.List<ru.sortix.parkourbeat.levels.Waypoint> waypoints =
            this.level.getLevelSettings().getWorldSettings().getWaypoints();
        if (waypoints.size() < 2) return this.level.getCuboid().getMax().getY();
        return waypoints.get(waypoints.size() - 1).getLocation().getY();
    }

    /**
     * Сколько блоков разделяют старт и финиш по высоте. Меньше блока не бывает:
     * иначе полоса делилась бы на ноль на плоской трассе.
     */
    public double getThreeSixtyClimbBlocks() {
        double climb = Math.abs(this.getThreeSixtyFinishY() - this.getThreeSixtyStartY());
        return climb < 1.0D ? 1.0D : climb;
    }

    public float getPassedProgress() {
        // На 360-уровне полоса показывает набранную высоту: процентов пройденного пути
        // там не существует, а «фантомный» прогресс по оси уровня двигался сам по себе.
        if (this.level.getLevelSettings().getGameSettings().isThreeSixtyLevel()) {
            float progress = (float) (this.getPassedHeight() / this.getThreeSixtyClimbBlocks());
            if (progress < 0f) return 0f;
            return Math.min(progress, 1f);
        }

        // На 2D-уровне игрок стоит на месте, а едет кубик: расстояние по координате
        // игрока тут всегда ноль, и прогресс надо брать у самого забега.
        if (ru.sortix.parkourbeat.twod.TwoDManager.isTwoD(this.level)) {
            try {
                return this.getPlugin().get(ru.sortix.parkourbeat.twod.TwoDManager.class)
                    .getProgress(this.player);
            } catch (Throwable t) {
                return 0f;
            }
        }

        float passedProgress = (float) (this.getPassedDistance(false) / this.level.getLevelSettings().getTotalLevelDistance());
        if (passedProgress < 0) return 0;
        if (passedProgress > 1) return 1;
        return passedProgress;
    }

    public double getPassedDistancePublic(boolean realtime) {
        return this.getPassedDistance(realtime);
    }

    private double getPassedDistance(boolean realtime) {
        LevelSettings levelSettings = this.level.getLevelSettings();

        double playerPos;
        if (realtime) {
            playerPos = levelSettings.getDirectionChecker().getCoordinate(this.packetsAdapter.getPosition(this.player));
        } else {
            playerPos = levelSettings.getDirectionChecker().getCoordinate(this.player.getLocation());
        }
        double startPos = levelSettings.getStartPosition();

        double passedDistance = playerPos < startPos
            ? startPos - playerPos
            : playerPos - startPos;

        return Math.max(0, Math.min(levelSettings.getTotalLevelDistance(), passedDistance));
    }
}
