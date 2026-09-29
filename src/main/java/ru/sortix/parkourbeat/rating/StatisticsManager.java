package ru.sortix.parkourbeat.rating;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;
import ru.sortix.parkourbeat.utils.lang.Lang;
import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.replay.ReplayFrame;
import ru.sortix.parkourbeat.replay.ReplayJump;
import ru.sortix.parkourbeat.replay.ReplayManager;
import ru.sortix.parkourbeat.stats.PPCalculator;
import ru.sortix.parkourbeat.stats.PlayerProfile;
import ru.sortix.parkourbeat.stats.ProfileSummary;
import ru.sortix.parkourbeat.stats.RecordComparison;
import ru.sortix.parkourbeat.stats.RunResult;
import ru.sortix.parkourbeat.stats.RunSubmission;
import ru.sortix.parkourbeat.stats.StatsStorage;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

public class StatisticsManager implements PluginManager {

    public static final double TWO_D_ACCURACY_WEIGHT = 0.1D;
    private static final long AUTOSAVE_INTERVAL_TICKS = 20L * 60L * 5L;
    public static final int HISTORY_SIZE = 20;

    protected final @NonNull ParkourBeat plugin;
    private final @Getter @NonNull StatsStorage storage;
    private final @NonNull ExecutorService ioExecutor;

    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, LevelTopCache> levelTopsCache = new ConcurrentHashMap<>();

    private final Map<UUID, ModifierSet> selectedModifiers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStarts = new ConcurrentHashMap<>();

    private volatile List<ProfileSummary> cachedLeaderboard = new ArrayList<>();

    private BukkitTask autosaveTask;
    private BukkitTask leaderboardUpdateTask;
    private @Getter boolean loaded = false;

    private static class LevelTopCache {
        final List<RunResult> top;
        final long expiresAt;
        LevelTopCache(List<RunResult> top, long expiresAt) {
            this.top = top;
            this.expiresAt = expiresAt;
        }
    }

    public StatisticsManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.storage = new StatsStorage(plugin.getLogger(), new File(plugin.getDataFolder(), "statistics.db"));
        this.ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ParkourBeat-Stats-IO");
            thread.setDaemon(true);
            return thread;
        });

        this.storage.open();

        this.updateGlobalLeaderboardCache();
        this.leaderboardUpdateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin, this::updateGlobalLeaderboardCache, 20L * 60L * 5L, 20L * 60L * 5L);

        this.autosaveTask = Bukkit.getScheduler().runTaskTimer(
            plugin, this::autosave, AUTOSAVE_INTERVAL_TICKS, AUTOSAVE_INTERVAL_TICKS);

        this.loaded = true;
    }

    @NonNull
    public PlayerProfile getProfile(@NonNull UUID playerId, @NonNull String playerName) {
        PlayerProfile profile = this.profiles.get(playerId);
        if (profile != null) return profile;

        profile = this.storage.loadPlayerProfile(playerId, playerName);
        if (profile == null) {
            profile = new PlayerProfile(playerId, playerName);
            profile.setFirstJoinAtMillis(System.currentTimeMillis());
            profile.setDirty(true);
        }
        this.profiles.put(playerId, profile);
        return profile;
    }

    @NonNull
    public PlayerProfile getProfile(@NonNull OfflinePlayer player) {
        String name = player.getName() != null ? player.getName() : player.getUniqueId().toString();
        return this.getProfile(player.getUniqueId(), name);
    }

    @Nullable
    public PlayerProfile getProfileIfKnown(@NonNull UUID playerId) {
        return this.profiles.get(playerId);
    }

    @NonNull
    public Collection<PlayerProfile> getAllProfiles() {
        return Collections.unmodifiableCollection(this.profiles.values());
    }

    public void handleJoin(@NonNull Player player) {
        this.submitIo(() -> {
            PlayerProfile profile = this.getProfile(player.getUniqueId(), player.getName());
            if (!profile.getPlayerName().equals(player.getName())) {
                profile.setPlayerName(player.getName());
                profile.setDirty(true);
            }
            if (profile.getFirstJoinAtMillis() <= 0L) {
                profile.setFirstJoinAtMillis(System.currentTimeMillis());
                profile.setDirty(true);
            }
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                this.sessionStarts.put(player.getUniqueId(), System.currentTimeMillis());
                if (profile.isDirty()) this.savePlayerAsync(profile);
            });
        });
    }

    public void handleQuit(@NonNull Player player) {
        this.flushSession(player.getUniqueId());
        PlayerProfile profile = this.profiles.remove(player.getUniqueId());
        if (profile != null) this.savePlayerAsync(profile);
        this.sessionStarts.remove(player.getUniqueId());
        this.selectedModifiers.remove(player.getUniqueId());
    }

    private void flushSession(@NonNull UUID playerId) {
        Long startedAt = this.sessionStarts.get(playerId);
        if (startedAt == null) return;
        long now = System.currentTimeMillis();
        PlayerProfile profile = this.profiles.get(playerId);
        if (profile != null) profile.addPlaytime(now - startedAt);
        this.sessionStarts.put(playerId, now);
    }

    private void autosave() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            this.flushSession(online.getUniqueId());
        }
        for (PlayerProfile profile : this.profiles.values()) {
            if (profile.isDirty()) this.savePlayerAsync(profile);
        }
    }

    private void savePlayerAsync(@NonNull PlayerProfile profile) {
        profile.setDirty(false);
        this.submitIo(() -> this.storage.savePlayer(profile));
    }

    @NonNull
    public ModifierSet getSelectedModifiers(@NonNull UUID playerId) {
        return this.selectedModifiers.computeIfAbsent(playerId, id -> new ModifierSet());
    }

    @Nullable
    public RunResult getRecord(@NonNull UUID playerId, @NonNull UUID levelId) {
        PlayerProfile profile = this.profiles.get(playerId);
        return profile == null ? null : profile.getRecord(levelId);
    }

    public double getPersonalBestProgress(@NonNull UUID playerId, @NonNull UUID levelId) {
        RunResult record = this.getRecord(playerId, levelId);
        return record == null ? 0.0D : record.getProgressPercent();
    }

    @NonNull
    public List<RunResult> getLevelTop(@NonNull UUID levelId) {
        LevelTopCache cache = this.levelTopsCache.get(levelId);
        if (cache != null && System.currentTimeMillis() < cache.expiresAt) {
            return cache.top;
        }
        List<RunResult> top = this.storage.loadLevelTop(levelId);
        top.sort(RecordComparison.BEST_FIRST);
        this.levelTopsCache.put(levelId, new LevelTopCache(top, System.currentTimeMillis() + 60000L));
        return top;
    }

    @Nullable
    public RunResult getGlobalRecord(@NonNull UUID levelId) {
        List<RunResult> top = this.getLevelTop(levelId);
        return top.isEmpty() ? null : top.get(0);
    }

    public int getLevelTopSize(@NonNull UUID levelId) {
        return this.getLevelTop(levelId).size();
    }

    public int getLevelTopPosition(@NonNull UUID levelId, @NonNull UUID playerId) {
        List<RunResult> top = this.getLevelTop(levelId);
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).getPlayerId().equals(playerId)) return i + 1;
        }
        return 0;
    }

    @NonNull
    public RunSubmission submitRun(@NonNull RunResult run) {
        if (run.getModifiers().contains(Modifier.PRACTICE)) {
            return RunSubmission.notRecorded(run);
        }

        PlayerProfile profile = this.getProfile(run.getPlayerId(), run.getPlayerName());
        if (!profile.getPlayerName().equals(run.getPlayerName())) {
            profile.setPlayerName(run.getPlayerName());
        }
        profile.addAttempt();

        List<ReplayFrame> replayFrames = null;
        List<ReplayJump> replayJumps = null;

        if (run.isCompleted()) {
            Player player = Bukkit.getPlayer(run.getPlayerId());
            if (player != null) {
                ReplayManager rm = this.plugin.get(ReplayManager.class);
                replayFrames = rm.extractRecording(player);
                replayJumps = rm.extractJumps(player);
            }
        }

        final List<ReplayFrame> finalFrames = replayFrames;
        final List<ReplayJump> finalJumps = replayJumps;

        this.submitIo(() -> {
            this.storage.insertRun(run);
            if (!run.isCompleted() || finalFrames == null || finalFrames.isEmpty()) return;

            long runId = this.storage.findLastRunId(run.getPlayerId());
            if (runId <= 0L) return;

            this.plugin.get(ReplayManager.class).saveReplayAsync(
                finalFrames, finalJumps, run.getPlayerId(), run.getPlayerName(), run.getLevelId(), runId);
        });

        if (run.isSuspicious()) {
            Player player = Bukkit.getPlayer(run.getPlayerId());
            String lang = player != null ? PlayerLang.of(player) : PlayerLang.DEFAULT_LOCALE;
            this.plugin.getLogger().warning("Подозрительный результат: " + run.getPlayerName()
                + Lang.raw(lang, "auto.statistics_manager.submit_run.1") + run.getLevelId() + " — " + run.getScore() + Lang.raw(lang, "auto.statistics_manager.submit_run.2")
                + run.getTimeMillis() + Lang.raw(lang, "auto.statistics_manager.submit_run.3"));
        }

        RunResult previousPersonal = profile.getRecord(run.getLevelId());
        boolean isPersonalRecord = RecordComparison.isBetter(run, previousPersonal);

        RunResult previousGlobal = this.getGlobalRecord(run.getLevelId());
        boolean isGlobalRecord = false;

        if (isPersonalRecord) {
            profile.putRecord(run);
            this.submitIo(() -> this.storage.saveRecord(run));
            this.levelTopsCache.remove(run.getLevelId());

            boolean sameHolder = previousGlobal != null
                && previousGlobal.getPlayerId().equals(run.getPlayerId());
            isGlobalRecord = RecordComparison.isBetter(run, previousGlobal) && !sameHolder;
            this.submitIo(this::updateGlobalLeaderboardCache);
        }

        this.savePlayerAsync(profile);

        int position = this.getLevelTopPosition(run.getLevelId(), run.getPlayerId());
        int size = this.getLevelTopSize(run.getLevelId());

        return new RunSubmission(run, isPersonalRecord, previousPersonal,
            isGlobalRecord, previousGlobal, position, size);
    }

    @Nullable
    public LevelDifficulty getCurrentDifficulty(@NonNull UUID levelId) {
        GameSettings settings = this.getLevelSettings(levelId);
        return settings == null ? null : settings.getDifficulty();
    }

    public double getCurrentHardness(@NonNull UUID levelId) {
        GameSettings settings = this.getLevelSettings(levelId);
        return settings == null
            ? GameSettings.MIN_DIFFICULTY_MULTIPLIER
            : settings.getDifficultyMultiplier();
    }

    @Nullable
    public GameSettings getLevelSettings(@NonNull UUID levelId) {
        try {
            return this.plugin.get(LevelsManager.class).getAvailableLevelSettings(levelId);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isRanked(@NonNull UUID levelId) {
        LevelDifficulty difficulty = this.getCurrentDifficulty(levelId);
        return difficulty != null && difficulty != LevelDifficulty.N_A;
    }

    @NonNull
    public ProfileSummary summarize(@NonNull PlayerProfile profile) {
        int completedLevels = 0;
        long totalScore = 0L;
        double totalAccuracy = 0.0D;
        int maxCombo = 0;
        LevelDifficulty hardestDifficulty = null;
        String hardestLevelName = null;

        Map<AccuracyGrade, Integer> grades = new EnumMap<>(AccuracyGrade.class);
        for (AccuracyGrade grade : AccuracyGrade.values()) grades.put(grade, 0);

        List<Double> ppValues = new ArrayList<>();
        double accuracyWeight = 0.0D;

        for (RunResult record : profile.getAllRecords()) {
            LevelDifficulty current = this.getCurrentDifficulty(record.getLevelId());

            if (record.isCompleted()) {
                totalScore += record.getScore();

                double accuracyWeightOfRecord = 1.0D;
                GameSettings recordSettings = this.getLevelSettings(record.getLevelId());
                if (recordSettings != null && recordSettings.getLevelMode().isTwoD()) {
                    accuracyWeightOfRecord = TWO_D_ACCURACY_WEIGHT;
                }
                totalAccuracy += record.getAccuracy() * accuracyWeightOfRecord;
                accuracyWeight += accuracyWeightOfRecord;
                if (record.getMaxCombo() > maxCombo) maxCombo = record.getMaxCombo();

                grades.merge(record.getGrade(), 1, Integer::sum);
                completedLevels++;

                if (current != null && current != LevelDifficulty.N_A
                    && (hardestDifficulty == null || current.ordinal() > hardestDifficulty.ordinal())) {
                    hardestDifficulty = current;
                    GameSettings settings = this.getLevelSettings(record.getLevelId());
                    hardestLevelName = settings != null ? settings.getDisplayNameLegacy(false) : record.getLevelName();
                }
            }

            ppValues.add(PPCalculator.calculatePP(record, current,
                this.getCurrentHardness(record.getLevelId())));
        }

        double averageAccuracy = accuracyWeight > 0.0D ? (totalAccuracy / accuracyWeight) : 0.0D;

        return new ProfileSummary(
            profile.getPlayerId(),
            profile.getPlayerName(),
            profile.getFirstJoinAtMillis(),
            profile.getPlaytimeMillis(),
            profile.getTotalAttempts(),
            this.countOwnLevels(profile.getPlayerId()),
            completedLevels,
            totalScore,
            averageAccuracy,
            maxCombo,
            hardestDifficulty,
            hardestLevelName,
            PPCalculator.weightedTotal(ppValues),
            grades,
            profile.getAllRecords().size()
        );
    }

    private int countOwnLevels(@NonNull UUID playerId) {
        try {
            int count = 0;
            for (GameSettings settings : this.plugin.get(LevelsManager.class).getAvailableLevelsSettings()) {
                if (settings.getOwnerId().equals(playerId)) count++;
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    public double getRecordPP(@NonNull RunResult record) {
        return PPCalculator.calculatePP(record,
            this.getCurrentDifficulty(record.getLevelId()),
            this.getCurrentHardness(record.getLevelId()));
    }

    public enum SortKey {
        PP("pp"),
        SCORE("score"),
        ACCURACY("accuracy"),
        LEVELS("levels");

        private final @NonNull String langKey;

        SortKey(@NonNull String langKey) {
            this.langKey = langKey;
        }

        @NonNull
        public String getDisplay(String locale) {
            return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale, "stats.sort." + this.langKey);
        }

        @NonNull
        public SortKey next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    @NonNull
    public List<ProfileSummary> getLeaderboard(@NonNull SortKey key) {
        List<ProfileSummary> sorted = new ArrayList<>(this.cachedLeaderboard);
        sorted.sort(comparatorFor(key));
        return sorted;
    }

    public int getRankedPlayersCount() {
        return this.cachedLeaderboard.size();
    }

    private void updateGlobalLeaderboardCache() {
        List<ProfileSummary> summaries = new ArrayList<>();
        for (StatsStorage.StoredPlayer stored : this.storage.loadAllPlayers()) {
            PlayerProfile profile = new PlayerProfile(stored.getPlayerId(), stored.getPlayerName());
            profile.setFirstJoinAtMillis(stored.getFirstJoinAtMillis());
            profile.setPlaytimeMillis(stored.getPlaytimeMillis());
            profile.setTotalAttempts(stored.getTotalAttempts());
            for (RunResult record : this.storage.loadPlayerRecords(stored.getPlayerId())) {
                profile.putRecord(record);
            }
            if (hasCompletedAnything(profile)) {
                summaries.add(this.summarize(profile));
            }
        }
        this.cachedLeaderboard = summaries;
    }

    @NonNull
    private static Comparator<ProfileSummary> comparatorFor(@NonNull SortKey key) {
        Comparator<ProfileSummary> comparator;
        switch (key) {
            case SCORE:
                comparator = Comparator.comparingLong(ProfileSummary::getTotalScore);
                break;
            case ACCURACY:
                comparator = Comparator.comparingDouble(ProfileSummary::getAverageAccuracy);
                break;
            case LEVELS:
                comparator = Comparator.comparingInt(ProfileSummary::getCompletedLevelsCount);
                break;
            case PP:
            default:
                comparator = Comparator.comparingDouble(ProfileSummary::getPp);
                break;
        }
        return comparator.reversed()
            .thenComparing(Comparator.comparingDouble(ProfileSummary::getPp).reversed())
            .thenComparing(Comparator.comparingLong(ProfileSummary::getTotalScore).reversed())
            .thenComparing(Comparator.comparingInt(ProfileSummary::getCompletedLevelsCount).reversed())
            .thenComparing(ProfileSummary::getPlayerName, String.CASE_INSENSITIVE_ORDER);
    }

    public int getDisplayRank(@NonNull UUID playerId) {
        int position = this.getLeaderboardPosition(SortKey.PP, playerId);
        return position > 0 ? position : 0;
    }

    public static boolean hasCompletedAnything(@NonNull PlayerProfile profile) {
        for (RunResult record : profile.getAllRecords()) {
            if (record.isCompleted()) return true;
        }
        return false;
    }

    @NonNull
    public String getRankLabel(@NonNull UUID playerId) {
        boolean hasStatistics = this.getDisplayRank(playerId) > 0;
        return ru.sortix.parkourbeat.stats.StatsFormat.rankPrefix(this.getDisplayRank(playerId), hasStatistics);
    }

    public int getLeaderboardPosition(@NonNull SortKey key, @NonNull UUID playerId) {
        List<ProfileSummary> leaderboard = this.getLeaderboard(key);
        for (int i = 0; i < leaderboard.size(); i++) {
            if (leaderboard.get(i).getPlayerId().equals(playerId)) return i + 1;
        }
        return 0;
    }

    public boolean resetPlayer(@NonNull UUID playerId) {
        PlayerProfile profile = this.profiles.remove(playerId);

        this.levelTopsCache.clear();

        this.selectedModifiers.remove(playerId);
        this.sessionStarts.remove(playerId);

        this.submitIo(() -> {
            this.storage.deletePlayerData(playerId);
            this.updateGlobalLeaderboardCache();
        });

        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            PlayerProfile fresh = this.getProfile(playerId, online.getName());
            fresh.setFirstJoinAtMillis(System.currentTimeMillis());
            fresh.setDirty(true);
            this.sessionStarts.put(playerId, System.currentTimeMillis());
            this.savePlayerAsync(fresh);
        }

        return profile != null;
    }

    public int resetEverything() {
        int count = this.cachedLeaderboard.size();

        this.profiles.clear();
        this.levelTopsCache.clear();
        this.selectedModifiers.clear();
        this.sessionStarts.clear();

        this.submitIo(() -> {
            this.storage.deleteEverything();
            this.updateGlobalLeaderboardCache();
        });

        long now = System.currentTimeMillis();
        for (Player online : Bukkit.getOnlinePlayers()) {
            PlayerProfile fresh = this.getProfile(online.getUniqueId(), online.getName());
            fresh.setFirstJoinAtMillis(now);
            fresh.setDirty(true);
            this.sessionStarts.put(online.getUniqueId(), now);
            this.savePlayerAsync(fresh);
        }

        return count;
    }

    /**
     * СБРОСИТЬ РЕКОРДЫ ОДНОГО УРОВНЯ.
     * <p>
     * Удаляются именно РЕКОРДЫ, а не история забегов: история - это личный архив
     * игрока, он ничего не искажает и стирать его вместе с топом было бы грубо.
     * Топ же хранит по одной записи на игрока, и когда меняется способ подсчёта
     * точности, старые записи становятся несопоставимы с новыми - пересчитать их
     * нечем, в базе лежит уже готовое число, а не исходные попадания.
     * <p>
     * Чистится в трёх местах сразу, и все три обязательны: таблица на диске, записи в
     * профилях, уже загруженных в память, и кэш топа. Забыть любое из них означает,
     * что рекорд вернётся - из памяти при следующем сохранении или из кэша при
     * следующем открытии меню.
     *
     * @return сколько записей убрано из памяти
     */
    public int resetLevelRecords(@NonNull UUID levelId) {
        int removed = 0;
        for (PlayerProfile profile : this.profiles.values()) {
            if (profile.getRecord(levelId) == null) continue;
            profile.removeRecord(levelId);
            removed++;
            this.savePlayerAsync(profile);
        }

        this.levelTopsCache.remove(levelId);
        this.submitIo(() -> {
            this.storage.deleteRecords(levelId);
            this.updateGlobalLeaderboardCache();
        });
        return removed;
    }

    public void recalculateScoresAsync(org.bukkit.command.CommandSender sender) {

        this.submitIo(() -> {
            this.storage.recalculateAllScores();
            this.levelTopsCache.clear();

            for (PlayerProfile profile : this.profiles.values()) {
                profile.clearRecords();
                for (RunResult record : this.storage.loadPlayerRecords(profile.getPlayerId())) {
                    profile.putRecord(record);
                }
            }
            this.updateGlobalLeaderboardCache();

            Bukkit.getScheduler().runTask(this.plugin, () -> {
                sender.sendMessage(ru.sortix.parkourbeat.utils.text.PbText.of(Lang.raw(PlayerLang.of(sender), "auto.statistics_manager.recalculate_scores_async.1")));
            });
        });
    }

    @Nullable
    public PlayerProfile findProfileByName(@NonNull String name) {
        for (PlayerProfile profile : this.profiles.values()) {
            if (profile.getPlayerName().equalsIgnoreCase(name)) return profile;
        }
        return null;
    }

    public void loadRecentRunsAsync(@NonNull UUID playerId, int limit, @NonNull Consumer<List<RunResult>> callback) {
        this.submitIo(() -> {
            final List<RunResult> runs = this.storage.loadRecentRuns(playerId, limit);
            if (!this.plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(this.plugin, () -> callback.accept(runs));
        });
    }

    public void loadBestRunsAsync(int limit, @NonNull Consumer<List<RunResult>> callback) {
        this.submitIo(() -> {
            final List<RunResult> runs = this.storage.loadBestRuns(limit);
            if (!this.plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(this.plugin, () -> callback.accept(runs));
        });
    }

    private void submitIo(@NonNull Runnable runnable) {
        if (this.ioExecutor.isShutdown()) return;
        try {
            this.ioExecutor.execute(() -> {
                try {
                    runnable.run();
                } catch (Throwable throwable) {
                    this.plugin.getLogger().log(Level.SEVERE, "Ошибка в потоке статистики", throwable);
                }
            });
        } catch (RuntimeException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Не удалось поставить задачу статистики в очередь", e);
        }
    }

    @Override
    public void disable() {
        if (this.autosaveTask != null) {
            this.autosaveTask.cancel();
            this.autosaveTask = null;
        }
        if (this.leaderboardUpdateTask != null) {
            this.leaderboardUpdateTask.cancel();
            this.leaderboardUpdateTask = null;
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            this.handleQuit(online);
        }

        Map<UUID, PlayerProfile> snapshot = new HashMap<>(this.profiles);
        for (PlayerProfile profile : snapshot.values()) {
            if (profile.isDirty()) {
                profile.setDirty(false);
                this.submitIo(() -> this.storage.savePlayer(profile));
            }
        }

        this.ioExecutor.shutdown();
        try {
            if (!this.ioExecutor.awaitTermination(15L, TimeUnit.SECONDS)) {
                this.plugin.getLogger().warning("Статистика не успела сохраниться за 15 секунд");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        this.storage.close();

        this.profiles.clear();
        this.levelTopsCache.clear();
        this.selectedModifiers.clear();
        this.sessionStarts.clear();
        this.cachedLeaderboard.clear();
        this.loaded = false;
    }
}
