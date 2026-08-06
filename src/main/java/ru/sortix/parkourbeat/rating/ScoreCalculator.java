package ru.sortix.parkourbeat.rating;

import lombok.NonNull;

public final class ScoreCalculator {
    /** Fitted coefficient A in total(n) = A·n^B. */
    private static final double CURVE_A = 293.86D;
    /** Fitted exponent B in total(n) = A·n^B. */
    private static final double CURVE_B = 1.0678D;

    private ScoreCalculator() {
    }

    /**
     * Total score after {@code perfectCount} consecutive perfect hits, per the fitted
     * curve. Used both to derive per-jump deltas and for any "expected total" checks.
     */
    public static double curveTotal(int perfectCount) {
        if (perfectCount <= 0) return 0.0D;
        return CURVE_A * Math.pow(perfectCount, CURVE_B);
    }

    /**
     * Score awarded for a hit that lands at the given combo position.
     *
     * @param result       the jump outcome (MISS awards nothing)
     * @param comboBefore  the combo count *before* this jump (0 for the first)
     * @return the non-negative points to add
     */
    public static int award(@NonNull JumpResult result, int comboBefore) {
        if (!result.isHit()) return 0;

        // The k-th hit (1-indexed) closes the gap between curveTotal(k-1) and curveTotal(k).
        int k = comboBefore + 1;
        double perfectDelta = curveTotal(k) - curveTotal(k - 1);
        // Never drop below the raw base for a perfect, guards the very first hit.
        if (perfectDelta < JumpResult.PERFECT.getBasePoints()) {
            perfectDelta = JumpResult.PERFECT.getBasePoints();
        }

        double fraction = result.getBasePoints() / (double) JumpResult.PERFECT.getBasePoints();
        return (int) Math.round(perfectDelta * fraction);
    }
}
