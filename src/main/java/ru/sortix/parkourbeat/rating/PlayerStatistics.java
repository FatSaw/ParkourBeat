package ru.sortix.parkourbeat.rating;

import lombok.Getter;
import lombok.NonNull;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregate, all-time statistics for one player. This is intentionally a stub: every
 * value defaults to zero / empty and nothing is persisted yet. The real leaderboard /
 * rating system (BeatLeader / BeatScore style) will extend and fill this in a later
 * pass, which is why the fields and per-level records already exist here.
 *
 * @see StatisticsManager
 */
@Getter
public class PlayerStatistics {
    private final @NonNull UUID playerId;
    private @NonNull String playerName;

    // --- all-time aggregate values (all 0 for now) ---
    private long maxComboAllTime = 0L;
    private long totalScore = 0L;
    /** Best mean accuracy on a 0..100 scale ("just accuracy"). */
    private double bestAverageAccuracy = 0.0D;
    /** Hardest difficulty ever completed, null when none. */
    private @Nullable String hardestDifficultyCompleted = null;
    /** Count of distinct levels ever completed. */
    private int completedLevelsCount = 0;
    /** Number of levels this player has authored. */
    private int ownLevelsCount = 0;
    /** Epoch millis the account/profile was first seen. */
    private long accountCreatedAtMillis = 0L;
    /** Total time spent on ParkourBeat, in millis. */
    private long totalPlaytimeMillis = 0L;

    /** Per-level best records, keyed by level id. Empty for now. */
    private final Map<UUID, LevelRecord> levelRecords = new LinkedHashMap<>();

    public PlayerStatistics(@NonNull UUID playerId, @NonNull String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }

    public void setPlayerName(@NonNull String playerName) {
        this.playerName = playerName;
    }

    @NonNull
    public Map<UUID, LevelRecord> getLevelRecords() {
        return Collections.unmodifiableMap(this.levelRecords);
    }

    /**
     * A single player's best result on a single level. All-zero stub for now; the
     * rating pass will populate and persist these.
     */
    @Getter
    public static class LevelRecord {
        private final @NonNull UUID levelId;
        private double progressPercent = 0.0D;
        private long timeMillis = 0L;
        private double accuracy = 0.0D;
        private @NonNull AccuracyGrade grade = AccuracyGrade.R;
        private int maxCombo = 0;
        private int score = 0;

        public LevelRecord(@NonNull UUID levelId) {
            this.levelId = levelId;
        }
    }
}
