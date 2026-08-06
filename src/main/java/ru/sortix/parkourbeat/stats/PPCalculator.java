package ru.sortix.parkourbeat.stats;

import lombok.NonNull;
import ru.sortix.parkourbeat.levels.LevelDifficulty;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * PP-рейтинг в духе беатлиадер
 * ВАЖНО: сложность здесь берётся АКТУАЛЬНАЯ (из настроек уровня прямо сейчас),
 * а не та, что была на момент прохождения Поэтому
 * PP всегда считается на лету и нигде не хранится
 */
public final class PPCalculator {
    /** Коэффициент затухания веса каждого следующего результата. */
    public static final double WEIGHT_DECAY = 0.95D;
    /** Показатель степени у точности: делает разницу 96% и 99% ощутимой. */
    public static final double ACCURACY_EXPONENT = 3.0D;

    private PPCalculator() {
    }

    /** Базовый вес сложности. N/A (и удалённый уровень) не даёт PP вообще. */
    public static double getDifficultyWeight(@Nullable LevelDifficulty difficulty) {
        if (difficulty == null) return 0.0D;
        switch (difficulty) {
            case EASY:
                return 40.0D;
            case HARD:
                return 100.0D;
            case EXPERT:
                return 200.0D;
            case EXPERT_PLUS:
                return 350.0D;
            case N_A:
            default:
                return 0.0D;
        }
    }
    public static double calculatePP(@NonNull RunResult record, @Nullable LevelDifficulty currentDifficulty) {
        // Незавершённое прохождение PP не даёт вообще.
        if (!record.isCompleted()) return 0.0D;

        double base = getDifficultyWeight(currentDifficulty);
        if (base <= 0.0D) return 0.0D;

        double accuracy = Math.max(0.0D, Math.min(100.0D, record.getAccuracy()));
        double accuracyFactor = Math.pow(accuracy / 100.0D, ACCURACY_EXPONENT);

        double multiplier = record.getMultiplier();
        if (multiplier <= 0.0D) return 0.0D; // PRACTICE и подобное

        return base * accuracyFactor * multiplier;
    }

    public static double weightedTotal(@NonNull List<Double> values) {
        if (values.isEmpty()) return 0.0D;

        List<Double> sorted = new java.util.ArrayList<>(values);
        sorted.removeIf(value -> value == null || value <= 0.0D);
        if (sorted.isEmpty()) return 0.0D;
        Collections.sort(sorted, Collections.reverseOrder());

        double total = 0.0D;
        double weight = 1.0D;
        for (double value : sorted) {
            total += value * weight;
            weight *= WEIGHT_DECAY;
        }
        return total;
    }
}
