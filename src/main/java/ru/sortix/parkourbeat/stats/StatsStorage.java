// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/stats/StatsStorage.java
package ru.sortix.parkourbeat.stats;

import lombok.Getter;
import lombok.NonNull;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.rating.AccuracyGrade;

import javax.annotation.Nullable;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StatsStorage {
    private static final String TABLE_PLAYERS = "pb_players";
    private static final String TABLE_RECORDS = "pb_records";
    private static final String TABLE_RUNS = "pb_runs";
    public static final int MAX_HISTORY_PER_PLAYER = 36;
    private static final String TABLE_RESET_REQUESTS = "pb_statreset_requests";

    private final @NonNull Logger logger;
    private final @NonNull File databaseFile;

    private Connection connection;
    private @Getter boolean available = false;

    public StatsStorage(@NonNull Logger logger, @NonNull File databaseFile) {
        this.logger = logger;
        this.databaseFile = databaseFile;
    }

    public synchronized void open() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            this.logger.severe("Драйвер SQLite не найден! Статистика будет работать ТОЛЬКО в памяти "
                + "и потеряется при перезапуске. Добавьте org.xerial:sqlite-jdbc в зависимости плагина.");
            this.available = false;
            return;
        }

        try {
            File parent = this.databaseFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                this.logger.warning("Не удалось создать папку для базы: " + parent);
            }
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + this.databaseFile.getAbsolutePath());
            this.createTables();
            this.available = true;
            this.logger.info("База статистики открыта: " + this.databaseFile.getName());
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось открыть базу статистики", e);
            this.available = false;
        }
    }

    public synchronized void close() {
        this.available = false;
        if (this.connection == null) return;
        try {
            this.connection.close();
        } catch (SQLException e) {
            this.logger.log(Level.WARNING, "Не удалось закрыть базу статистики", e);
        }
        this.connection = null;
    }

    private void createTables() throws SQLException {
        try (Statement statement = this.connection.createStatement()) {
            statement.executeUpdate("PRAGMA journal_mode=WAL");
            statement.executeUpdate("PRAGMA synchronous=NORMAL");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE_PLAYERS + " ("
                + "player_uuid TEXT PRIMARY KEY,"
                + "player_name TEXT NOT NULL,"
                + "first_join_at INTEGER NOT NULL,"
                + "playtime_millis INTEGER NOT NULL DEFAULT 0,"
                + "total_attempts INTEGER NOT NULL DEFAULT 0"
                + ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE_RECORDS + " ("
                + "player_uuid TEXT NOT NULL,"
                + "level_uuid TEXT NOT NULL,"
                + "player_name TEXT NOT NULL,"
                + "level_name TEXT NOT NULL,"
                + "difficulty TEXT NOT NULL,"
                + "progress REAL NOT NULL,"
                + "completed INTEGER NOT NULL,"
                + "accuracy REAL NOT NULL,"
                + "grade TEXT NOT NULL,"
                + "score INTEGER NOT NULL,"
                + "raw_score INTEGER NOT NULL,"
                + "max_combo INTEGER NOT NULL,"
                + "count300 INTEGER NOT NULL,"
                + "count100 INTEGER NOT NULL,"
                + "count50 INTEGER NOT NULL,"
                + "misses INTEGER NOT NULL,"
                + "modifiers TEXT NOT NULL,"
                + "multiplier REAL NOT NULL,"
                + "time_millis INTEGER NOT NULL,"
                + "achieved_at INTEGER NOT NULL,"
                + "suspicious INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (player_uuid, level_uuid)"
                + ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE_RUNS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "player_uuid TEXT NOT NULL,"
                + "level_uuid TEXT NOT NULL,"
                + "player_name TEXT NOT NULL,"
                + "level_name TEXT NOT NULL,"
                + "difficulty TEXT NOT NULL,"
                + "progress REAL NOT NULL,"
                + "completed INTEGER NOT NULL,"
                + "accuracy REAL NOT NULL,"
                + "grade TEXT NOT NULL,"
                + "score INTEGER NOT NULL,"
                + "raw_score INTEGER NOT NULL,"
                + "max_combo INTEGER NOT NULL,"
                + "count300 INTEGER NOT NULL,"
                + "count100 INTEGER NOT NULL,"
                + "count50 INTEGER NOT NULL,"
                + "misses INTEGER NOT NULL,"
                + "modifiers TEXT NOT NULL,"
                + "multiplier REAL NOT NULL,"
                + "time_millis INTEGER NOT NULL,"
                + "played_at INTEGER NOT NULL,"
                + "suspicious INTEGER NOT NULL DEFAULT 0"
                + ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE_RESET_REQUESTS + " ("
                + "player_uuid TEXT PRIMARY KEY,"
                + "player_name TEXT NOT NULL,"
                + "requested_at INTEGER NOT NULL,"
                + "status TEXT NOT NULL,"
                + "resolved_by TEXT,"
                + "resolved_at INTEGER NOT NULL DEFAULT 0,"
                + "notified INTEGER NOT NULL DEFAULT 0"
                + ")");

            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_records_player "
                + "ON " + TABLE_RECORDS + " (player_uuid)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_records_level "
                + "ON " + TABLE_RECORDS + " (level_uuid)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_records_level_score "
                + "ON " + TABLE_RECORDS + " (level_uuid, score DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_runs_player_time "
                + "ON " + TABLE_RUNS + " (player_uuid, played_at DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_runs_level_time "
                + "ON " + TABLE_RUNS + " (level_uuid, played_at DESC)");
        }
    }

    public synchronized void recalculateAllScores() {
        if (!this.available) return;
        try {
            this.connection.setAutoCommit(false);

            // Обновляем историю забегов
            try (Statement stmt = this.connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, raw_score, modifiers FROM " + TABLE_RUNS)) {
                try (PreparedStatement update = this.connection.prepareStatement("UPDATE " + TABLE_RUNS + " SET score = ?, multiplier = ? WHERE id = ?")) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        int rawScore = rs.getInt("raw_score");
                        Set<ru.sortix.parkourbeat.rating.Modifier> mods = RunResult.decodeModifiers(rs.getString("modifiers"));
                        double multiplier = 1.0;
                        for (ru.sortix.parkourbeat.rating.Modifier m : mods) multiplier *= m.getScoreMultiplier();
                        int newScore = (int) Math.round(rawScore * multiplier);

                        update.setInt(1, newScore);
                        update.setDouble(2, multiplier);
                        update.setLong(3, id);
                        update.addBatch();
                    }
                    update.executeBatch();
                }
            }

            // Обновляем главные рекорды (топы)
            try (Statement stmt = this.connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT player_uuid, level_uuid, raw_score, modifiers FROM " + TABLE_RECORDS)) {
                try (PreparedStatement update = this.connection.prepareStatement("UPDATE " + TABLE_RECORDS + " SET score = ?, multiplier = ? WHERE player_uuid = ? AND level_uuid = ?")) {
                    while (rs.next()) {
                        String playerUuid = rs.getString("player_uuid");
                        String levelUuid = rs.getString("level_uuid");
                        int rawScore = rs.getInt("raw_score");
                        Set<ru.sortix.parkourbeat.rating.Modifier> mods = RunResult.decodeModifiers(rs.getString("modifiers"));
                        double multiplier = 1.0;
                        for (ru.sortix.parkourbeat.rating.Modifier m : mods) multiplier *= m.getScoreMultiplier();
                        int newScore = (int) Math.round(rawScore * multiplier);

                        update.setInt(1, newScore);
                        update.setDouble(2, multiplier);
                        update.setString(3, playerUuid);
                        update.setString(4, levelUuid);
                        update.addBatch();
                    }
                    update.executeBatch();
                }
            }

            this.connection.commit();
            this.connection.setAutoCommit(true);
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось пересчитать очки в БД", e);
        }
    }

    @Getter
    public static class StoredPlayer {
        private final @NonNull UUID playerId;
        private final @NonNull String playerName;
        private final long firstJoinAtMillis;
        private final long playtimeMillis;
        private final long totalAttempts;

        public StoredPlayer(@NonNull UUID playerId, @NonNull String playerName,
                            long firstJoinAtMillis, long playtimeMillis, long totalAttempts) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.firstJoinAtMillis = firstJoinAtMillis;
            this.playtimeMillis = playtimeMillis;
            this.totalAttempts = totalAttempts;
        }
    }

    @NonNull
    public synchronized List<StoredPlayer> loadAllPlayers() {
        List<StoredPlayer> result = new ArrayList<>();
        if (!this.available) return result;
        String sql = "SELECT player_uuid, player_name, first_join_at, playtime_millis, total_attempts FROM " + TABLE_PLAYERS;
        try (PreparedStatement statement = this.connection.prepareStatement(sql);
             ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                UUID id = parseUuid(set.getString("player_uuid"));
                if (id == null) continue;
                result.add(new StoredPlayer(
                    id, set.getString("player_name"), set.getLong("first_join_at"),
                    set.getLong("playtime_millis"), set.getLong("total_attempts")
                ));
            }
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось загрузить профили игроков", e);
        }
        return result;
    }

    @Nullable
    public synchronized PlayerProfile loadPlayerProfile(@NonNull UUID playerId, @NonNull String defaultName) {
        if (!this.available) return null;
        PlayerProfile profile = null;
        String sqlPlayer = "SELECT * FROM " + TABLE_PLAYERS + " WHERE player_uuid = ?";
        try (PreparedStatement stmt = this.connection.prepareStatement(sqlPlayer)) {
            stmt.setString(1, playerId.toString());
            try (ResultSet set = stmt.executeQuery()) {
                if (set.next()) {
                    profile = new PlayerProfile(playerId, set.getString("player_name"));
                    profile.setFirstJoinAtMillis(set.getLong("first_join_at"));
                    profile.setPlaytimeMillis(set.getLong("playtime_millis"));
                    profile.setTotalAttempts(set.getLong("total_attempts"));
                }
            }
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось загрузить профиль " + playerId, e);
        }

        if (profile == null) return null;

        String sqlRecords = "SELECT * FROM " + TABLE_RECORDS + " WHERE player_uuid = ?";
        try (PreparedStatement stmt = this.connection.prepareStatement(sqlRecords)) {
            stmt.setString(1, playerId.toString());
            try (ResultSet set = stmt.executeQuery()) {
                while (set.next()) {
                    RunResult record = read(set, false);
                    if (record != null) profile.putRecord(record);
                }
            }
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось загрузить рекорды " + playerId, e);
        }

        return profile;
    }

    public synchronized void savePlayer(@NonNull PlayerProfile profile) {
        if (!this.available) return;
        String sql = "INSERT OR REPLACE INTO " + TABLE_PLAYERS
            + " (player_uuid, player_name, first_join_at, playtime_millis, total_attempts)"
            + " VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, profile.getPlayerId().toString());
            statement.setString(2, profile.getPlayerName());
            statement.setLong(3, profile.getFirstJoinAtMillis());
            statement.setLong(4, profile.getPlaytimeMillis());
            statement.setLong(5, profile.getTotalAttempts());
            statement.executeUpdate();
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось сохранить профиль " + profile.getPlayerName(), e);
        }
    }

    @NonNull
    public synchronized List<RunResult> loadPlayerRecords(@NonNull UUID playerId) {
        List<RunResult> result = new ArrayList<>();
        if (!this.available) return result;
        String sql = "SELECT * FROM " + TABLE_RECORDS + " WHERE player_uuid = ?";
        try (PreparedStatement stmt = this.connection.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            try (ResultSet set = stmt.executeQuery()) {
                while (set.next()) {
                    RunResult record = read(set, false);
                    if (record != null) result.add(record);
                }
            }
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось загрузить рекорды игрока " + playerId, e);
        }
        return result;
    }

    @NonNull
    public synchronized List<RunResult> loadLevelTop(@NonNull UUID levelId) {
        List<RunResult> result = new ArrayList<>();
        if (!this.available) return result;
        String sql = "SELECT * FROM " + TABLE_RECORDS + " WHERE level_uuid = ?";
        try (PreparedStatement stmt = this.connection.prepareStatement(sql)) {
            stmt.setString(1, levelId.toString());
            try (ResultSet set = stmt.executeQuery()) {
                while (set.next()) {
                    RunResult record = read(set, false);
                    if (record != null) result.add(record);
                }
            }
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось загрузить топ уровня " + levelId, e);
        }
        return result;
    }

    public synchronized void saveRecord(@NonNull RunResult record) {
        if (!this.available) return;
        String sql = "INSERT OR REPLACE INTO " + TABLE_RECORDS + " ("
            + "player_uuid, level_uuid, player_name, level_name, difficulty, progress, completed,"
            + "accuracy, grade, score, raw_score, max_combo, count300, count100, count50, misses,"
            + "modifiers, multiplier, time_millis, achieved_at, suspicious"
            + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, record.getPlayerId().toString());
            statement.setString(2, record.getLevelId().toString());
            fillCommon(statement, record, 3);
            statement.executeUpdate();
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось сохранить рекорд игрока " + record.getPlayerName(), e);
        }
    }

    public synchronized void deleteRecords(@NonNull UUID levelId) {
        if (!this.available) return;
        try (PreparedStatement statement = this.connection.prepareStatement(
            "DELETE FROM " + TABLE_RECORDS + " WHERE level_uuid = ?")) {
            statement.setString(1, levelId.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось удалить рекорды уровня " + levelId, e);
        }
    }

    public synchronized void insertRun(@NonNull RunResult run) {
        if (!this.available) return;
        String sql = "INSERT INTO " + TABLE_RUNS + " ("
            + "player_uuid, level_uuid, player_name, level_name, difficulty, progress, completed,"
            + "accuracy, grade, score, raw_score, max_combo, count300, count100, count50, misses,"
            + "modifiers, multiplier, time_millis, played_at, suspicious"
            + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, run.getPlayerId().toString());
            statement.setString(2, run.getLevelId().toString());
            fillCommon(statement, run, 3);
            statement.executeUpdate();
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось записать прохождение игрока " + run.getPlayerName(), e);
            return;
        }
        this.trimHistory(run.getPlayerId());
    }

    /**
     * Забеги, которые нельзя удалять при обрезке истории (у них сохранён реплей).
     * Ставится извне, чтобы хранилище не зависело от системы реплеев напрямую.
     */
    private volatile java.util.function.LongPredicate protectedRunIds = null;

    public void setProtectedRunIds(java.util.function.LongPredicate predicate) {
        this.protectedRunIds = predicate;
    }

    /**
     * Обрезает историю игрока.
     * <p>
     * Раньше это был один DELETE, оставлявший последние N забегов. Вместе со строкой
     * исчезал и реплей: файл на диске оставался, но найти его было уже неоткуда - отсюда
     * и жалобы, что записи "не сохраняются навсегда". Теперь строки с сохранённым реплеем
     * переживают обрезку, сколько бы новых забегов сверху ни легло.
     */
    private synchronized void trimHistory(@NonNull UUID playerId) {
        if (!this.available) return;

        java.util.List<Long> candidates = new java.util.ArrayList<>();
        String select = "SELECT id FROM " + TABLE_RUNS + " WHERE player_uuid = ?"
            + " ORDER BY played_at DESC, id DESC";
        try (PreparedStatement statement = this.connection.prepareStatement(select)) {
            statement.setString(1, playerId.toString());
            try (java.sql.ResultSet result = statement.executeQuery()) {
                int index = 0;
                while (result.next()) {
                    long id = result.getLong(1);
                    // Первые MAX_HISTORY_PER_PLAYER оставляем в любом случае.
                    if (index++ < MAX_HISTORY_PER_PLAYER) continue;
                    candidates.add(id);
                }
            }
        } catch (SQLException e) {
            this.logger.log(Level.WARNING, "Не удалось прочитать историю игрока " + playerId, e);
            return;
        }

        java.util.function.LongPredicate protection = this.protectedRunIds;
        java.util.List<Long> toDelete = new java.util.ArrayList<>(candidates.size());
        for (long id : candidates) {
            if (protection != null) {
                try {
                    if (protection.test(id)) continue;
                } catch (Exception ignored) {
                }
            }
            toDelete.add(id);
        }
        if (toDelete.isEmpty()) return;

        try (PreparedStatement statement = this.connection.prepareStatement(
            "DELETE FROM " + TABLE_RUNS + " WHERE id = ?")) {
            for (long id : toDelete) {
                statement.setLong(1, id);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            this.logger.log(Level.WARNING, "Не удалось обрезать историю игрока " + playerId, e);
        }
    }

    @NonNull
    public synchronized java.util.Set<Long> loadExistingRunIds(@NonNull UUID playerId) {
        java.util.Set<Long> result = new java.util.HashSet<>();
        if (!this.available) return result;
        try (PreparedStatement statement = this.connection.prepareStatement(
            "SELECT id FROM " + TABLE_RUNS + " WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) result.add(set.getLong("id"));
            }
        } catch (SQLException e) {
            this.logger.log(Level.WARNING, "Не удалось загрузить id попыток " + playerId, e);
        }
        return result;
    }

    public synchronized long findLastRunId(@NonNull UUID playerId) {
        if (!this.available) return 0L;
        try (PreparedStatement statement = this.connection.prepareStatement(
            "SELECT id FROM " + TABLE_RUNS + " WHERE player_uuid = ?"
                + " ORDER BY played_at DESC, id DESC LIMIT 1")) {
            statement.setString(1, playerId.toString());
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) return set.getLong("id");
            }
        } catch (SQLException e) {
            this.logger.log(Level.WARNING, "Не удалось найти последнюю попытку " + playerId, e);
        }
        return 0L;
    }

    @NonNull
    public synchronized List<RunResult> loadRecentRuns(@NonNull UUID playerId, int limit) {
        List<RunResult> result = new ArrayList<>();
        if (!this.available) return result;
        String sql = "SELECT * FROM " + TABLE_RUNS + " WHERE player_uuid = ? ORDER BY played_at DESC LIMIT ?";
        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, limit);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    RunResult run = read(set, true);
                    if (run != null) result.add(run);
                }
            }
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось загрузить историю игрока " + playerId, e);
        }
        return result;
    }

    /**
     * Лучшие забеги всех игроков.
     * <p>
     * Сортировка та же, по которой сравнивают результаты в топах: сначала очки,
     * при равенстве - точность, затем свежесть.
     */
    @NonNull
    public synchronized List<RunResult> loadBestRuns(int limit) {
        List<RunResult> result = new ArrayList<>();
        if (!this.available) return result;
        String sql = "SELECT * FROM " + TABLE_RUNS + " WHERE completed = 1"
            + " ORDER BY score DESC, accuracy DESC, played_at DESC LIMIT ?";
        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    RunResult run = read(set, true);
                    if (run != null) result.add(run);
                }
            }
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось загрузить лучшие прохождения", e);
        }
        return result;
    }

    @NonNull
    public synchronized List<StatResetRequest> loadResetRequests() {
        List<StatResetRequest> result = new ArrayList<>();
        if (!this.available) return result;
        try (PreparedStatement statement = this.connection.prepareStatement(
            "SELECT * FROM " + TABLE_RESET_REQUESTS);
             ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                UUID id = parseUuid(set.getString("player_uuid"));
                if (id == null) continue;
                StatResetRequest.Status status;
                try {
                    status = StatResetRequest.Status.valueOf(set.getString("status"));
                } catch (IllegalArgumentException e) {
                    status = StatResetRequest.Status.PENDING;
                }
                result.add(new StatResetRequest(
                    id,
                    set.getString("player_name"),
                    set.getLong("requested_at"),
                    status,
                    set.getString("resolved_by"),
                    set.getLong("resolved_at"),
                    set.getInt("notified") != 0
                ));
            }
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось загрузить заявки на сброс статистики", e);
        }
        return result;
    }

    public synchronized void saveResetRequest(@NonNull StatResetRequest request) {
        if (!this.available) return;
        String sql = "INSERT OR REPLACE INTO " + TABLE_RESET_REQUESTS
            + " (player_uuid, player_name, requested_at, status, resolved_by, resolved_at, notified)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, request.getPlayerId().toString());
            statement.setString(2, request.getPlayerName());
            statement.setLong(3, request.getRequestedAtMillis());
            statement.setString(4, request.getStatus().name());
            statement.setString(5, request.getResolvedBy());
            statement.setLong(6, request.getResolvedAtMillis());
            statement.setInt(7, request.isNotified() ? 1 : 0);
            statement.executeUpdate();
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось сохранить заявку на сброс от "
                + request.getPlayerName(), e);
        }
    }

    public synchronized void deleteResetRequest(@NonNull UUID playerId) {
        if (!this.available) return;
        try (PreparedStatement statement = this.connection.prepareStatement(
            "DELETE FROM " + TABLE_RESET_REQUESTS + " WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось удалить заявку на сброс " + playerId, e);
        }
    }

    public synchronized void deletePlayerData(@NonNull UUID playerId) {
        if (!this.available) return;
        String id = playerId.toString();
        String[] statements = {
            "DELETE FROM " + TABLE_RUNS + " WHERE player_uuid = ?",
            "DELETE FROM " + TABLE_RECORDS + " WHERE player_uuid = ?",
            "DELETE FROM " + TABLE_PLAYERS + " WHERE player_uuid = ?"
        };
        for (String sql : statements) {
            try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
                statement.setString(1, id);
                statement.executeUpdate();
            } catch (SQLException e) {
                this.logger.log(Level.SEVERE, "Не удалось удалить статистику игрока " + playerId, e);
            }
        }
    }

    public synchronized void deleteEverything() {
        if (!this.available) return;
        try (Statement statement = this.connection.createStatement()) {
            statement.executeUpdate("DELETE FROM " + TABLE_RUNS);
            statement.executeUpdate("DELETE FROM " + TABLE_RECORDS);
            statement.executeUpdate("DELETE FROM " + TABLE_PLAYERS);
            statement.executeUpdate("VACUUM");
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось очистить базу статистики", e);
        }
    }

    private static void fillCommon(@NonNull PreparedStatement statement,
                                   @NonNull RunResult run,
                                   int offset) throws SQLException {
        int i = offset;
        statement.setString(i++, run.getPlayerName());
        statement.setString(i++, run.getLevelName());
        statement.setString(i++, run.getDifficulty().name());
        statement.setDouble(i++, run.getProgressPercent());
        statement.setInt(i++, run.isCompleted() ? 1 : 0);
        statement.setDouble(i++, run.getAccuracy());
        statement.setString(i++, run.getGrade().name());
        statement.setInt(i++, run.getScore());
        statement.setInt(i++, run.getRawScore());
        statement.setInt(i++, run.getMaxCombo());
        statement.setInt(i++, run.getCount300());
        statement.setInt(i++, run.getCount100());
        statement.setInt(i++, run.getCount50());
        statement.setInt(i++, run.getMissCount());
        statement.setString(i++, run.getModifiersCodes());
        statement.setDouble(i++, run.getMultiplier());
        statement.setLong(i++, run.getTimeMillis());
        statement.setLong(i++, run.getTimestamp());
        statement.setInt(i, run.isSuspicious() ? 1 : 0);
    }

    @Nullable
    private RunResult read(@NonNull ResultSet set, boolean isRun) throws SQLException {
        UUID playerId = parseUuid(set.getString("player_uuid"));
        UUID levelId = parseUuid(set.getString("level_uuid"));
        if (playerId == null || levelId == null) return null;

        return RunResult.builder()
            .rowId(isRun ? set.getLong("id") : 0L)
            .playerId(playerId)
            .playerName(set.getString("player_name"))
            .levelId(levelId)
            .levelName(set.getString("level_name"))
            .difficulty(parseDifficulty(set.getString("difficulty")))
            .progressPercent(set.getDouble("progress"))
            .completed(set.getInt("completed") != 0)
            .accuracy(set.getDouble("accuracy"))
            .grade(parseGrade(set.getString("grade")))
            .score(set.getInt("score"))
            .rawScore(set.getInt("raw_score"))
            .maxCombo(set.getInt("max_combo"))
            .count300(set.getInt("count300"))
            .count100(set.getInt("count100"))
            .count50(set.getInt("count50"))
            .missCount(set.getInt("misses"))
            .modifiers(RunResult.decodeModifiers(set.getString("modifiers")))
            .multiplier(set.getDouble("multiplier"))
            .timeMillis(set.getLong("time_millis"))
            .timestamp(set.getLong(isRun ? "played_at" : "achieved_at"))
            .suspicious(set.getInt("suspicious") != 0)
            .build();
    }

    @Nullable
    private static UUID parseUuid(@Nullable String raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @NonNull
    private static LevelDifficulty parseDifficulty(@Nullable String raw) {
        if (raw == null) return LevelDifficulty.N_A;
        try {
            return LevelDifficulty.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return LevelDifficulty.N_A;
        }
    }

    @NonNull
    private static AccuracyGrade parseGrade(@Nullable String raw) {
        if (raw == null) return AccuracyGrade.R;
        try {
            return AccuracyGrade.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return AccuracyGrade.R;
        }
    }
}
