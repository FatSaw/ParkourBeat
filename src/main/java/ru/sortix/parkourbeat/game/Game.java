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
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.LightShowRunner;
import ru.sortix.parkourbeat.levels.ParticleController;
import ru.sortix.parkourbeat.levels.settings.CompletionParticle;
import ru.sortix.parkourbeat.levels.settings.LevelBossBarColor;
import ru.sortix.parkourbeat.levels.settings.LevelSettings;
import ru.sortix.parkourbeat.player.music.MusicTrack;
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
    private static final Component LOSE_TITLE =
        Component.text("Вы проиграли =(").color(NamedTextColor.RED);

    private static final Title.Times FINISH_REASON_TITLE_TIMES = Title.Times.of(Duration.ofMillis(500L), Duration.ofMillis(1500L), Duration.ofMillis(500L));

    private final @NonNull LevelsManager levelsManager;
    private final @NonNull MusicTracksManager musicTracksManager;
    private final @NonNull ActivityPacketsAdapter packetsAdapter;
    private final @NonNull Player player;
    private final @NonNull Level level;
    private final @NonNull GameMoveHandler gameMoveHandler;
    private final @NonNull MusicMode musicMode;
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
    private volatile LevelBossBarColor bossBarColorOverride = null;
    private volatile long songStartedAtMillis = 0L;
    private volatile long songStoppedAtMillis = 0L;
    private volatile int lastTrackPieceNumber = 0;

    private Game(@NonNull ParkourBeat plugin, @NonNull Player player, @NonNull Level level, @NonNull ModifierSet modifiers) {
        this.levelsManager = plugin.get(LevelsManager.class);
        this.musicTracksManager = plugin.get(MusicTracksManager.class);
        this.packetsAdapter = plugin.get(ActivityManager.class).getPacketsAdapter();
        this.player = player;
        this.level = level;
        this.modifiers = modifiers.copy();
        this.runTracker = new RunTracker(this.modifiers);
        this.gameMoveHandler = new GameMoveHandler(this);
        this.musicMode = level.getLevelSettings().getGameSettings().getMusicTrack() == null
            ? MusicMode.DISABLED
            : (level.getLevelSettings().getGameSettings().isUseTrackPieces()
            ? MusicMode.PIECES
            : MusicMode.FULL_TRACK);
        this.prepareGame(plugin);
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

        MusicTrack musicTrack = settings.getGameSettings().getMusicTrack();
        if (musicTrack == null || !musicTrack.isStillAvailable()) return;

        musicTrack.isResourcepackCurrentlySet(this.player, currentlySet -> {
            if (Boolean.TRUE.equals(currentlySet)) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!this.player.isOnline()) return;
                // Результат нужен только для консоли: игроку ничего не пишем
                // и ничем не мешаем. Ошибки видно в логе через MusicPackDispatcher.
                musicTrack.setResourcepackAsync(plugin, this.player, result -> {
                }, null);
            });
        });
    }

    @NonNull
    public ParkourBeat getPlugin() {
        return this.levelsManager.getPlugin();
    }

    public void start() {
        if (this.currentState != State.READY) return;

        this.setCurrentState(State.RUNNING);

        if (!this.player.isSprinting() || this.player.isSneaking()) {
            if (!this.hasModifier(Modifier.PRACTICE) && !isInWater(this.player)) {
                this.failLevel(LangOptions.level_play_title_pressrun.getComponent(player), null);
                return;
            }
        }

        this.refreshModifiers();
        this.resetRunProgress();

        this.level.getLevelSettings().getParticleController().startSpawnParticles(this.player);

        MusicPlatform musicPlatform = this.musicTracksManager.getPlatform();
        this.packetsAdapter.setWatchingPosition(this.player, true);
        if (this.musicMode == MusicMode.PIECES) {
            musicPlatform.disableRepeatMode(this.player);
            this.musicTracksManager.setTrackPiecesSendingEnabled(this, true);
            this.tryToSendTrackPiece();
        } else if (this.musicMode == MusicMode.FULL_TRACK) {
            musicPlatform.disableRepeatMode(this.player);
            musicPlatform.startPlayingTrackFull(this.player);
        }
        this.getPlugin().get(ru.sortix.parkourbeat.player.PlayersVisibilityManager.class)
            .hideOthersFor(this.player);

        this.songStartedAtMillis = System.currentTimeMillis();
        this.songStoppedAtMillis = 0L;
        this.runSubmitted = false;

        if (this.hasModifier(Modifier.HIGH_RISK)) {
            this.player.setHealth(1.0D);
        }

        this.level.getLevelSettings().getParticleController()
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

        this.ensureLightShowRunner().startShow();
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

    public void applyDamage(double amount) {
        if (this.displayTimecode) return;

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

        this.stopMusic();

        CompletionParticle fallParticle = this.level.getLightShow().getLoseParticle();

        if (isNewPR) {
            String gradeColor = grade.getFormatted().substring(0, 2);
            this.sendProgressRecordMessage(submission);

            Component title = LegacyComponentSerializer.legacyAmpersand().deserialize("&6&lНОВЫЙ РЕКОРД");
            Component subtitle = LegacyComponentSerializer.legacyAmpersand().deserialize(
                String.format(java.util.Locale.ROOT, "&fВы прошли уровень на %s%.0f%%", gradeColor, currentProgress)
            );

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
        double currentAcc = this.getDisplayAccuracy();
        AccuracyGrade grade = this.getCurrentGrade();
        int score = this.runTracker.getScore();
        int maxCombo = this.runTracker.getMaxCombo();
        int misses = this.runTracker.getMissCount();

        LevelDifficulty diff = this.level.getLevelSettings().getGameSettings().getDifficulty();
        boolean isUnranked = (diff == LevelDifficulty.N_A);
        RunSubmission submission = this.submitRunResult(true, 100.0D);
        boolean isGlobalRecord = submission != null && submission.isGlobalRecord();
        boolean isPersonalRecord = submission != null && submission.isPersonalRecord();

        Component title;
        Component subtitle;

        String gradeColor = grade.getFormatted().substring(0, 2);

        if (isUnranked) {
            title = LegacyComponentSerializer.legacyAmpersand().deserialize("&a&lУРОВЕНЬ ПРОЙДЕН");
            subtitle = LegacyComponentSerializer.legacyAmpersand().deserialize("&7&lUNRANKED");
        } else if (isGlobalRecord) {
            title = LegacyComponentSerializer.legacyAmpersand().deserialize("&a&lНОВЫЙ РЕКОРД");
            subtitle = LegacyComponentSerializer.legacyAmpersand().deserialize(
                String.format(java.util.Locale.ROOT, "&fВы набрали &e%d&f очков и %s%.2f%%&f точности", score, gradeColor, currentAcc)
            );
        } else if (isPersonalRecord) {
            title = LegacyComponentSerializer.legacyAmpersand().deserialize("&6&lНОВЫЙ РЕКОРД");
            subtitle = LegacyComponentSerializer.legacyAmpersand().deserialize(
                String.format(java.util.Locale.ROOT, "&fВы набрали &e%d&f очков и %s%.2f%%&f точности", score, gradeColor, currentAcc)
            );
        } else {
            title = LegacyComponentSerializer.legacyAmpersand().deserialize("&a&lУРОВЕНЬ ПРОЙДЕН");
            subtitle = LegacyComponentSerializer.legacyAmpersand().deserialize(
                "&fОценка: " + grade.getFormatted()
            );
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
    private RunSubmission submitRunResult(boolean completed, double progressPercent) {
        if (this.runSubmitted) return null;
        if (this.displayTimecode) return null;
        if (this.modifiers.isActive(Modifier.PRACTICE)) return null;
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
            .score(this.runTracker.getScore())
            .rawScore(this.runTracker.getRawScore())
            .maxCombo(this.runTracker.getMaxCombo())
            .count300(this.runTracker.getPerfectCount())
            .count100(this.runTracker.getGoodCount())
            .count50(this.runTracker.getOkCount())
            .missCount(this.runTracker.getMissCount())
            .modifiers(new java.util.HashSet<>(this.modifiers.getActive()))
            .multiplier(this.modifiers.getTotalMultiplier())
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
        StringBuilder message = new StringBuilder(String.format(java.util.Locale.ROOT,
            "&a&lУровень пройден\n" +
                "&fТочность: &e%.2f%% - %s\n" +
                "&fОчков: &e%d\n" +
                "&fМакс комбо: &ex%d\n" +
                "&fПромахов: &e%d",
            accuracy, grade.getFormatted(), score, maxCombo, misses
        ));

        if (misses == 0) {
            message.append(" &7[&b&lFC&7]");
        }

        if (submission != null) {
            RunResult previous = submission.getPreviousPersonalRecord();
            if (submission.isPersonalRecord() && previous != null && previous.isCompleted()) {
                int delta = submission.getScoreDelta();
                message.append(String.format(java.util.Locale.ROOT,
                    "\n&fПрошлый рекорд: &7очки %d &7→ %s%+d",
                    previous.getScore(), delta >= 0 ? "&a" : "&c", delta));
            }

            RunResult previousGlobal = submission.getPreviousGlobalRecord();
            if (submission.isGlobalRecord() && previousGlobal != null) {
                message.append(String.format(java.util.Locale.ROOT,
                    "\n&fПрошлый рекорд уровня: &7%s &7(&7%d&7)",
                    previousGlobal.getPlayerName(), previousGlobal.getScore()));
            }

            if (!isUnranked && submission.getTopPosition() > 0) {
                message.append(String.format(java.util.Locale.ROOT,
                    "\n&fМесто на уровне: %s#%d&r &7из &f%d",
                    positionColor(submission.getTopPosition()),
                    submission.getTopPosition(), submission.getTopSize()));

                StatisticsManager statisticsManager = this.getPlugin().get(StatisticsManager.class);
                int globalRank = statisticsManager.getDisplayRank(this.player.getUniqueId());
                if (globalRank > 0) {
                    message.append(String.format(java.util.Locale.ROOT,
                        "\n&fРанг на сервере: %s#%d&r &7из &f%d &7(по PP)",
                        positionColor(globalRank), globalRank,
                        statisticsManager.getRankedPlayersCount()));
                }
            }
        }

        if (isUnranked) {
            int levelId = this.level.getLevelSettings().getGameSettings().getUniqueNumber();
            message.append(String.format(java.util.Locale.ROOT,
                "\n\n&3Вы прошли уровень, и мы вас поздравляем! Но уровень не прошёл модерацию на хорошее качество. Однако вы можете оценить его сложность с помощью &n/play %d",
                levelId
            ));
        }

        this.player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message.toString()));
    }

    private void sendProgressRecordMessage(@Nullable RunSubmission submission) {
        if (submission == null) return;
        RunResult previous = submission.getPreviousPersonalRecord();
        if (previous == null || previous.isCompleted()) return;

        double delta = submission.getRun().getProgressPercent() - previous.getProgressPercent();
        this.player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
            String.format(java.util.Locale.ROOT,
                "&fПрошлый рекорд: &7прогресс %.0f%% &7→ &a+%.0f%%",
                previous.getProgressPercent(), delta)
        ));
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
        this.safely("stop music", this::stopMusic);

        this.safely("particles", () -> {
            ParticleController controller = this.level.getLevelSettings().getParticleController();
            controller.stopSpawnParticlesForPlayer(this.player);
            controller.setHiddenViewer(this.player, false);
        });

        this.safely("boss bar", this::removeBossBar);
        this.safely("light show", () -> {
            if (this.lightShowRunner != null) this.lightShowRunner.rollbackToBase();
        });
    }

    private void safely(@NonNull String what, @NonNull Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            this.getPlugin().getLogger().log(java.util.logging.Level.WARNING,
                "Ошибка при завершении забега (" + what + ")", t);
        }
    }

    private void stopMusic() {
        if (this.musicMode == MusicMode.PIECES) {
            this.musicTracksManager.setTrackPiecesSendingEnabled(this, false);
            this.musicTracksManager.getPlatform().stopPlayingTrackPiece(this.player, this.lastTrackPieceNumber);
            this.lastTrackPieceNumber = 0;
        } else if (this.musicMode == MusicMode.FULL_TRACK) {
            this.musicTracksManager.getPlatform().stopPlayingTrackFull(this.player);
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

        Component points;
        if (result == JumpResult.MISS) {
            if (this.displayTimecode) {
                points = LegacyComponentSerializer.legacyAmpersand().deserialize("&7MISS | &c-1HP");
            } else {
                points = LegacyComponentSerializer.legacyAmpersand().deserialize("&7MISS");
            }
        } else {
            points = LegacyComponentSerializer.legacyAmpersand().deserialize(result.formatPoints());
        }

        this.player.showTitle(Title.title(
            Component.empty(),
            points,
            Title.Times.of(Duration.ZERO, Duration.ofMillis(150), Duration.ofMillis(100))
        ));

        if (this.hasModifier(Modifier.PERFECT) && result != JumpResult.PERFECT) {
            this.failLevel(LOSE_TITLE, null);
            return;
        }

        if (this.hasModifier(Modifier.HARD) && (result == JumpResult.OK || result == JumpResult.MISS)) {
            this.failLevel(LOSE_TITLE, null);
            return;
        }
    }

    private void tickGradeEffects() {
        if (this.displayTimecode) return;

        AccuracyGrade grade = this.getCurrentGrade();
        this.lastGrade = grade;
        int interval = grade.getBleedIntervalSeconds();
        if (interval <= 0) return;

        long now = System.currentTimeMillis();
        if (this.lastBleedAtMillis == 0L) {
            this.lastBleedAtMillis = now;
            this.applyDamage(3.0D);
            return;
        }
        if (now - this.lastBleedAtMillis < interval * 1000L) return;
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
        this.ensureLightShowRunner().snapToBase();
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

            boolean isShortTestLevel = this.displayTimecode && this.level.getLevelSettings().getWorldSettings().getWaypoints().size() < 4;

            if (!this.allowEndlessRun && !isShortTestLevel && this.getPassedProgress() >= 0.999f) {
                this.completeLevel();
                return;
            }
        }

        LightShowRunner runner = this.lightShowRunner;
        if (runner != null) {
            try {
                double distance = this.packetsAdapter.isWatchingPosition(this.player)
                    ? this.getPassedDistance(true)
                    : this.getPassedDistance(false);
                long positionMillis = Math.round((distance / BLOCKS_PER_SECOND) * 1000.0D);

                runner.tick(positionMillis);
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

        Component name = Component.text(String.format("%d%%", Math.round(progress * 100)))
            .color(barColor.getTextColor())
            .decoration(TextDecoration.BOLD, true);

        if (this.displayTimecode) {
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

    public float getPassedProgress() {
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
