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

    /**
     * Прибавка к PP за каждую единицу жёсткости уровня сверх стандартной.
     * <p>
     * Жёсткость — это не рейтинговое название сложности, а реальная узость окон
     * попадания и сила урона, которую строитель выставляет вручную. Пройти уровень с
     * жёсткостью 9 объективно тяжелее, и совсем это не учитывать нечестно.
     * <p>
     * Коэффициент намеренно скромный: при максимальной жёсткости 10 прибавка выходит
     * +54%, то есть жёсткость подкручивает результат, но не перебивает рейтинговую
     * сложность уровня. Expert+ на стандартной жёсткости по-прежнему стоит заметно
     * больше, чем Easy на максимальной.
     */
    public static final double HARDNESS_BONUS_PER_LEVEL = 0.06D;

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
    /**
     * Множитель за жёсткость уровня.
     * <p>
     * Как и сложность, берётся АКТУАЛЬНЫЙ: строитель мог подкрутить жёсткость после
     * прохождения, и PP обязан пересчитаться вместе с ней.
     */
    public static double getHardnessMultiplier(double difficultyMultiplier) {
        if (Double.isNaN(difficultyMultiplier) || Double.isInfinite(difficultyMultiplier)) return 1.0D;
        double extra = Math.max(0.0D, difficultyMultiplier - 1.0D);
        return 1.0D + HARDNESS_BONUS_PER_LEVEL * extra;
    }

    public static double calculatePP(@NonNull RunResult record, @Nullable LevelDifficulty currentDifficulty) {
        return calculatePP(record, currentDifficulty, 1.0D);
    }

    public static double calculatePP(@NonNull RunResult record,
                                     @Nullable LevelDifficulty currentDifficulty,
                                     double currentHardness) {
        // Незавершённое прохождение PP не даёт вообще.
        if (!record.isCompleted()) return 0.0D;

        // N/A не даёт PP, и это же автоматически закрывает лазейку с жёсткостью:
        // строитель может выкрутить её хоть на максимум, но пока уровень не прошёл
        // модерацию и не получил рейтинговую сложность, рейтинга с него не будет.
        double base = getDifficultyWeight(currentDifficulty);
        if (base <= 0.0D) return 0.0D;

        double accuracy = Math.max(0.0D, Math.min(100.0D, record.getAccuracy()));
        double accuracyFactor = Math.pow(accuracy / 100.0D, ACCURACY_EXPONENT);

        double multiplier = record.getMultiplier();
        if (multiplier <= 0.0D) return 0.0D; // PRACTICE и подобное

        return base * accuracyFactor * multiplier * getHardnessMultiplier(currentHardness);
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
