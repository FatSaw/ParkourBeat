package ru.sortix.parkourbeat.rating;

import lombok.NonNull;

public final class JumpTriggerEvaluator {
    // 3 РАДИУСА СЗАДИ (при раннем прыжке):
    public static double BACK_PERFECT_RADIUS = 0.60D; // +300
    public static double BACK_GOOD_RADIUS    = 1.20D; // +100
    public static double BACK_OK_RADIUS      = 1.80D; // +50

    // 3 УДЛИНЁННЫХ РАДИУСА СПЕРЕДИ (при прыжке на краю блока):
    public static double FRONT_PERFECT_RADIUS = 0.95D; // +300
    public static double FRONT_GOOD_RADIUS    = 1.55D; // +100
    public static double FRONT_OK_RADIUS      = 2.15D; // +50

    // Ограничение по высоте (в блоках):
    public static double MAX_Y_DISTANCE = 2.50D;

    // ==================== МНОЖИТЕЛЬ СЛОЖНОСТИ УРОВНЯ ====================
    // Чем выше множитель, который выставил строитель в редакторе, тем уже окна попадания.
    // Идеальное окно (+300) сужается быстрее всех, затем +100, медленнее всего +50.
    // При множителе 1.0 все коэффициенты равны 1.0, то есть поведение полностью прежнее.

    /** Насколько агрессивно сужается окно +300 за каждую единицу сложности сверх 1.0. */
    public static final double PERFECT_TIGHTEN_PER_LEVEL = 0.35D;
    /** Насколько агрессивно сужается окно +100. */
    public static final double GOOD_TIGHTEN_PER_LEVEL = 0.15D;
    /** Насколько агрессивно сужается окно +50 (оно же окно детекта прыжка). */
    public static final double OK_TIGHTEN_PER_LEVEL = 0.06D;
    /** Насколько сужается допуск по высоте. */
    public static final double Y_TIGHTEN_PER_LEVEL = 0.05D;

    private JumpTriggerEvaluator() {
    }

    /**
     * Нормализует множитель сложности: любые мусорные значения превращаются в 1.0.
     */
    public static double normalizeDifficulty(double difficultyMultiplier) {
        if (Double.isNaN(difficultyMultiplier) || Double.isInfinite(difficultyMultiplier)) return 1.0D;
        return Math.max(1.0D, difficultyMultiplier);
    }

    private static double tighten(double radius, double difficultyMultiplier, double perLevel) {
        double extra = normalizeDifficulty(difficultyMultiplier) - 1.0D;
        if (extra <= 0.0D) return radius;
        return radius / (1.0D + perLevel * extra);
    }

    public static double backPerfectRadius(double difficultyMultiplier) {
        return tighten(BACK_PERFECT_RADIUS, difficultyMultiplier, PERFECT_TIGHTEN_PER_LEVEL);
    }

    public static double backGoodRadius(double difficultyMultiplier) {
        return tighten(BACK_GOOD_RADIUS, difficultyMultiplier, GOOD_TIGHTEN_PER_LEVEL);
    }

    public static double backOkRadius(double difficultyMultiplier) {
        return tighten(BACK_OK_RADIUS, difficultyMultiplier, OK_TIGHTEN_PER_LEVEL);
    }

    public static double frontPerfectRadius(double difficultyMultiplier) {
        return tighten(FRONT_PERFECT_RADIUS, difficultyMultiplier, PERFECT_TIGHTEN_PER_LEVEL);
    }

    public static double frontGoodRadius(double difficultyMultiplier) {
        return tighten(FRONT_GOOD_RADIUS, difficultyMultiplier, GOOD_TIGHTEN_PER_LEVEL);
    }

    /**
     * Окно +50 спереди. Оно же используется как радиус детекта прыжка вообще,
     * поэтому на высокой сложности мимо трека промахнуться становится сильно проще.
     */
    public static double frontOkRadius(double difficultyMultiplier) {
        return tighten(FRONT_OK_RADIUS, difficultyMultiplier, OK_TIGHTEN_PER_LEVEL);
    }

    public static double maxYDistance(double difficultyMultiplier) {
        return tighten(MAX_Y_DISTANCE, difficultyMultiplier, Y_TIGHTEN_PER_LEVEL);
    }

    /**
     * Оценка прыжка на стандартной сложности (множитель 1.0).
     */
    @NonNull
    public static JumpResult evaluate(double signedDelta) {
        return evaluate(signedDelta, 1.0D);
    }

    /**
     * Оценка прыжка с учётом множителя сложности уровня.
     */
    @NonNull
    public static JumpResult evaluate(double signedDelta, double difficultyMultiplier) {
        if (signedDelta <= 0) {
            double delta = Math.abs(signedDelta);
            if (delta <= backPerfectRadius(difficultyMultiplier)) return JumpResult.PERFECT;
            if (delta <= backGoodRadius(difficultyMultiplier)) return JumpResult.GOOD;
            if (delta <= backOkRadius(difficultyMultiplier)) return JumpResult.OK;
            return JumpResult.MISS;
        } else {
            if (signedDelta <= frontPerfectRadius(difficultyMultiplier)) return JumpResult.PERFECT;
            if (signedDelta <= frontGoodRadius(difficultyMultiplier)) return JumpResult.GOOD;
            if (signedDelta <= frontOkRadius(difficultyMultiplier)) return JumpResult.OK;
            return JumpResult.MISS;
        }
    }

    public static boolean isPassedUnjumped(double signedDelta) {
        return isPassedUnjumped(signedDelta, 1.0D);
    }

    public static boolean isPassedUnjumped(double signedDelta, double difficultyMultiplier) {
        return signedDelta > frontOkRadius(difficultyMultiplier);
    }
}
