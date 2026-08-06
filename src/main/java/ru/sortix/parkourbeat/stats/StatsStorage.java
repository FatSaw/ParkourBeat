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
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Хранилище статистики на SQLite (п.10 ТЗ). Три таблицы: players, records, runs.
 * <p>
 * Все вызовы синхронизированы по одному соединению. Вызывать их следует
 * из executor'а {@link ru.sortix.parkourbeat.rating.StatisticsManager}, а не из
 * основного потока — единственное исключение — стартовая загрузка при включении.
 * <p>
 * Если драйвер SQLite недоступен (что на Spigot/Paper 1.16.5 практически
 * исключено — он идёт в комплекте с сервером), плагин продолжит работать
 * полностью в памяти, о чём будет громкое предупреждение в консоль.
 */
public class StatsStorage {
    private static final String TABLE_PLAYERS = "pb_players";
    private static final String TABLE_RECORDS = "pb_records";
    private static final String TABLE_RUNS = "pb_runs";
    private static final String TABLE_RESET_REQUESTS = "pb_statreset_requests";

    private final @NonNull Logger logger;
    private final @NonNull File databaseFile;

    private Connection connection;
    private @Getter boolean available = false;

    public StatsStorage(@NonNull Logger logger, @NonNull File databaseFile) {
        this.logger = logger;
        this.databaseFile = databaseFile;
    }

    // ------------------------------------------------------------------ жизненный цикл

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
                this.logger.warning("Не удалось создать папку для базы статистики: " + parent);
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

            // Индексы — иначе топ уровня будет тормозить (п.10).
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

    // ------------------------------------------------------------------ игроки

    /** Строка таблицы players. */
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
                    id,
                    set.getString("player_name"),
                    set.getLong("first_join_at"),
                    set.getLong("playtime_millis"),
                    set.getLong("total_attempts")
                ));
            }
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось загрузить профили игроков", e);
        }
        return result;
    }

    public synchronized void savePlayer(@NonNull PlayerProfile profile) {
        if (!this.available) return;
        // INSERT OR REPLACE, а не UPSERT — работает даже на старых версиях sqlite-jdbc.
        // Дату первого захода бережёт сам PlayerProfile: она никогда не увеличивается.
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

    // ------------------------------------------------------------------ рекорды

    @NonNull
    public synchronized List<RunResult> loadAllRecords() {
        List<RunResult> result = new ArrayList<>();
        if (!this.available) return result;
        try (PreparedStatement statement = this.connection.prepareStatement("SELECT * FROM " + TABLE_RECORDS);
             ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                RunResult record = read(set, false);
                if (record != null) result.add(record);
            }
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Не удалось загрузить рекорды", e);
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

    // ------------------------------------------------------------------ история

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
        }
    }

    /** Лента последних попыток игрока (п.6, «История»). */
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

    // ------------------------------------------------------------------ заявки на сброс

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

    // ------------------------------------------------------------------ сброс

    /** Удалить профиль, все рекорды и всю историю одного игрока. */
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

    /** Полностью очистить все три таблицы. */
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

    // ------------------------------------------------------------------ утилиты

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
