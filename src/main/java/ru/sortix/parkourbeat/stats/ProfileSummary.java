package ru.sortix.parkourbeat.stats;

import lombok.Getter;
import lombok.NonNull;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.rating.AccuracyGrade;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Getter
public class ProfileSummary {
    private final @NonNull UUID playerId;
    private final @NonNull String playerName;

    private final long firstJoinAtMillis;
    private final long playtimeMillis;
    private final long totalAttempts;

    private final int ownLevelsCount;
    private final int completedLevelsCount;
    private final long totalScore;

    /** Средняя точность по всем пройденным уровням (как в osu!) */
    private final double averageAccuracy;

    private final int maxCombo;
    private final @Nullable LevelDifficulty hardestDifficulty;
    private final @Nullable String hardestLevelName;
    private final double pp;
    private final @NonNull Map<AccuracyGrade, Integer> gradeCounts;
    private final int recordsCount;

    public ProfileSummary(@NonNull UUID playerId,
                          @NonNull String playerName,
                          long firstJoinAtMillis,
                          long playtimeMillis,
                          long totalAttempts,
                          int ownLevelsCount,
                          int completedLevelsCount,
                          long totalScore,
                          double averageAccuracy,
                          int maxCombo,
                          @Nullable LevelDifficulty hardestDifficulty,
                          @Nullable String hardestLevelName,
                          double pp,
                          @NonNull Map<AccuracyGrade, Integer> gradeCounts,
                          int recordsCount) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.firstJoinAtMillis = firstJoinAtMillis;
        this.playtimeMillis = playtimeMillis;
        this.totalAttempts = totalAttempts;
        this.ownLevelsCount = ownLevelsCount;
        this.completedLevelsCount = completedLevelsCount;
        this.totalScore = totalScore;
        this.averageAccuracy = averageAccuracy;
        this.maxCombo = maxCombo;
        this.hardestDifficulty = hardestDifficulty;
        this.hardestLevelName = hardestLevelName;
        this.pp = pp;
        this.gradeCounts = new EnumMap<>(gradeCounts);
        this.recordsCount = recordsCount;
    }

    public boolean hasStatistics() {
        return this.completedLevelsCount > 0;
    }

    public int getGradeCount(@NonNull AccuracyGrade grade) {
        Integer value = this.gradeCounts.get(grade);
        return value == null ? 0 : value;
    }

    @NonNull
    public String getHardestDifficultyDisplay() {
        if (this.hardestDifficulty == null || this.hardestLevelName == null) return "§7—";
        return this.hardestLevelName + " §7(" + this.hardestDifficulty.getDisplayName() + "§7)";
    }
}
