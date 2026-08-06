package ru.sortix.parkourbeat.rating;

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
    private static final long AUTOSAVE_INTERVAL_TICKS = 20L * 60L * 5L;
    public static final int HISTORY_SIZE = 20;
    private static final long LEADERBOARD_CACHE_MILLIS = 3000L;

    protected final @NonNull ParkourBeat plugin;
    private final @Getter @NonNull StatsStorage storage;
    private final @NonNull ExecutorService ioExecutor;

    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, RunResult>> recordsByLevel = new ConcurrentHashMap<>();

    private final Map<UUID, ModifierSet> selectedModifiers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStarts = new ConcurrentHashMap<>();

    private volatile List<ProfileSummary> cachedLeaderboard = null;
    private volatile long cachedLeaderboardAt = 0L;

    private BukkitTask autosaveTask;
    private @Getter boolean loaded = false;

    public StatisticsManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.storage = new StatsStorage(plugin.getLogger(), new File(plugin.getDataFolder(), "statistics.db"));
        this.ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ParkourBeat-Stats-IO");
            thread.setDaemon(true);
            return thread;
        });

        this.storage.open();
        this.loadEverything();

        this.autosaveTask = Bukkit.getScheduler().runTaskTimer(
            plugin, this::autosave, AUTOSAVE_INTERVAL_TICKS, AUTOSAVE_INTERVAL_TICKS);
    }

    private void loadEverything() {
        long startedAt = System.currentTimeMillis();

        for (StatsStorage.StoredPlayer stored : this.storage.loadAllPlayers()) {
            PlayerProfile profile = new PlayerProfile(stored.getPlayerId(), stored.getPlayerName());
            profile.setFirstJoinAtMillis(stored.getFirstJoinAtMillis());
            profile.setPlaytimeMillis(stored.getPlaytimeMillis());
            profile.setTotalAttempts(stored.getTotalAttempts());
            this.profiles.put(profile.getPlayerId(), profile);
        }

        int records = 0;
        for (RunResult record : this.storage.loadAllRecords()) {
            PlayerProfile profile = this.profiles.get(record.getPlayerId());
            if (profile == null) {
                profile = new PlayerProfile(record.getPlayerId(), record.getPlayerName());
                profile.setFirstJoinAtMillis(record.getTimestamp());
                this.profiles.put(profile.getPlayerId(), profile);
            }
            profile.putRecord(record);
            this.indexRecord(record);
            records++;
        }

        this.loaded = true;
        this.plugin.getLogger().info("Статистика загружена: профилей — " + this.profiles.size()
            + ", рекордов — " + records + " (" + (System.currentTimeMillis() - startedAt) + " мс)");
    }

    private void indexRecord(@NonNull RunResult record) {
        this.recordsByLevel
            .computeIfAbsent(record.getLevelId(), id -> new ConcurrentHashMap<>())
            .put(record.getPlayerId(), record);
    }

    @NonNull
    public PlayerProfile getProfile(@NonNull UUID playerId, @NonNull String playerName) {
        return this.profiles.computeIfAbsent(playerId, id -> {
            PlayerProfile profile = new PlayerProfile(id, playerName);
            profile.setDirty(true);
            return profile;
        });
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
        PlayerProfile profile = this.getProfile(player.getUniqueId(), player.getName());
        if (!profile.getPlayerName().equals(player.getName())) {
            profile.setPlayerName(player.getName());
            profile.setDirty(true);
        }
        if (profile.getFirstJoinAtMillis() <= 0L) {
            profile.setFirstJoinAtMillis(System.currentTimeMillis());
            profile.setDirty(true);
        }
        this.sessionStarts.put(player.getUniqueId(), System.currentTimeMillis());
        if (profile.isDirty()) this.savePlayerAsync(profile);
    }

    public void handleQuit(@NonNull Player player) {
        this.flushSession(player.getUniqueId());
        PlayerProfile profile = this.profiles.get(player.getUniqueId());
        if (profile != null) this.savePlayerAsync(profile);
        this.sessionStarts.remove(player.getUniqueId());
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
        Map<UUID, RunResult> records = this.recordsByLevel.get(levelId);
        if (records == null || records.isEmpty()) return Collections.emptyList();
        List<RunResult> top = new ArrayList<>(records.values());
        top.sort(RecordComparison.BEST_FIRST);
        return top;
    }

    @Nullable
    public RunResult getGlobalRecord(@NonNull UUID levelId) {
        Map<UUID, RunResult> records = this.recordsByLevel.get(levelId);
        if (records == null || records.isEmpty()) return null;
        RunResult best = null;
        for (RunResult record : records.values()) {
            if (RecordComparison.isBetter(record, best)) best = record;
        }
        return best;
    }

    public int getLevelTopSize(@NonNull UUID levelId) {
        Map<UUID, RunResult> records = this.recordsByLevel.get(levelId);
        return records == null ? 0 : records.size();
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

        this.submitIo(() -> this.storage.insertRun(run));

        if (run.isSuspicious()) {
            this.plugin.getLogger().warning("Подозрительный результат: " + run.getPlayerName()
                + " на уровне " + run.getLevelId() + " — " + run.getScore() + " очков за "
                + run.getTimeMillis() + " мс. Стоит проверить вручную.");
        }

        RunResult previousPersonal = profile.getRecord(run.getLevelId());
        boolean isPersonalRecord = RecordComparison.isBetter(run, previousPersonal);

        RunResult previousGlobal = this.getGlobalRecord(run.getLevelId());
        boolean isGlobalRecord = false;

        if (isPersonalRecord) {
            profile.putRecord(run);
            this.indexRecord(run);
            this.submitIo(() -> this.storage.saveRecord(run));

            boolean sameHolder = previousGlobal != null
                && previousGlobal.getPlayerId().equals(run.getPlayerId());
            isGlobalRecord = RecordComparison.isBetter(run, previousGlobal) && !sameHolder;
        }

        this.savePlayerAsync(profile);
        this.invalidateLeaderboard();

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

        for (RunResult record : profile.getAllRecords()) {
            LevelDifficulty current = this.getCurrentDifficulty(record.getLevelId());

            if (record.isCompleted()) {
                totalScore += record.getScore();
                totalAccuracy += record.getAccuracy();
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

            ppValues.add(PPCalculator.calculatePP(record, current));
        }

        double averageAccuracy = completedLevels > 0 ? (totalAccuracy / completedLevels) : 0.0D;

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
        return PPCalculator.calculatePP(record, this.getCurrentDifficulty(record.getLevelId()));
    }

    public enum SortKey {
        PP("&dПо PP-рейтингу"),
        SCORE("&eПо очкам"),
        ACCURACY("&bПо точности"),
        LEVELS("&aПо кол-ву уровней");

        private final @NonNull String display;

        SortKey(@NonNull String display) {
            this.display = display;
        }

        @NonNull
        public String getDisplay() {
            return this.display;
        }

        @NonNull
        public SortKey next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    @NonNull
    public List<ProfileSummary> getLeaderboard(@NonNull SortKey key) {
        List<ProfileSummary> sorted = new ArrayList<>(this.getAllSummaries());
        sorted.sort(comparatorFor(key));
        return sorted;
    }

    @NonNull
    private List<ProfileSummary> getAllSummaries() {
        long now = System.currentTimeMillis();
        List<ProfileSummary> cached = this.cachedLeaderboard;
        if (cached != null && now - this.cachedLeaderboardAt < LEADERBOARD_CACHE_MILLIS) {
            return cached;
        }
        List<ProfileSummary> summaries = new ArrayList<>(this.profiles.size());
        for (PlayerProfile profile : this.profiles.values()) {
            if (!hasCompletedAnything(profile)) continue;
            summaries.add(this.summarize(profile));
        }
        this.cachedLeaderboard = summaries;
        this.cachedLeaderboardAt = now;
        return summaries;
    }

    /** Сколько игроков вообще участвует в рейтинге. */
    public int getRankedPlayersCount() {
        return this.getAllSummaries().size();
    }

    private void invalidateLeaderboard() {
        this.cachedLeaderboard = null;
        this.cachedLeaderboardAt = 0L;
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
            .thenComparing(Comparator.comparingDouble(ProfileSummary::getAverageAccuracy).reversed())
            .thenComparing(ProfileSummary::getPlayerName, String.CASE_INSENSITIVE_ORDER);
    }

    public int getDisplayRank(@NonNull UUID playerId) {
        PlayerProfile profile = this.profiles.get(playerId);
        if (profile == null || !hasCompletedAnything(profile)) return 0;
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
        PlayerProfile profile = this.profiles.get(playerId);
        boolean hasStatistics = profile != null && hasCompletedAnything(profile);
        return ru.sortix.parkourbeat.stats.StatsFormat.rankPrefix(this.getDisplayRank(playerId), hasStatistics);
    }

    public int getLeaderboardPosition(@NonNull SortKey key, @NonNull UUID playerId) {
        List<ProfileSummary> leaderboard = this.getLeaderboard(key);
        for (int i = 0; i < leaderboard.size(); i++) {
            if (leaderboard.get(i).getPlayerId().equals(playerId)) return i + 1;
        }
        return 0;
    }

    // ------------------------------------------------------------------ сброс статистики

    /**
     * Полностью стереть статистику одного игрока: профиль, все рекорды и историю.
     *
     * @return true, если что-то было удалено
     */
    public boolean resetPlayer(@NonNull UUID playerId) {
        PlayerProfile profile = this.profiles.remove(playerId);

        for (Map<UUID, RunResult> levelRecords : this.recordsByLevel.values()) {
            levelRecords.remove(playerId);
        }
        this.recordsByLevel.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        this.selectedModifiers.remove(playerId);
        this.sessionStarts.remove(playerId);

        this.submitIo(() -> this.storage.deletePlayerData(playerId));
        this.invalidateLeaderboard();
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
        int count = this.profiles.size();

        this.profiles.clear();
        this.recordsByLevel.clear();
        this.selectedModifiers.clear();
        this.sessionStarts.clear();

        this.submitIo(this.storage::deleteEverything);
        this.invalidateLeaderboard();

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

    /** Найти профиль по нику среди уже известных (регистр не важен). */
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

        for (UUID playerId : new ArrayList<>(this.sessionStarts.keySet())) {
            this.flushSession(playerId);
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
        this.recordsByLevel.clear();
        this.selectedModifiers.clear();
        this.sessionStarts.clear();
        this.invalidateLeaderboard();
        this.loaded = false;
    }
}
