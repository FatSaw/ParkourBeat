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
    private JumpTriggerEvaluator() {
    }

    @NonNull
    public static JumpResult evaluate(double signedDelta) {
        if (signedDelta <= 0) {
            double delta = Math.abs(signedDelta);
            if (delta <= BACK_PERFECT_RADIUS) return JumpResult.PERFECT;
            if (delta <= BACK_GOOD_RADIUS) return JumpResult.GOOD;
            if (delta <= BACK_OK_RADIUS) return JumpResult.OK;
            return JumpResult.MISS;
        } else {
            if (signedDelta <= FRONT_PERFECT_RADIUS) return JumpResult.PERFECT;
            if (signedDelta <= FRONT_GOOD_RADIUS) return JumpResult.GOOD;
            if (signedDelta <= FRONT_OK_RADIUS) return JumpResult.OK;
            return JumpResult.MISS;
        }
    }

    public static boolean isPassedUnjumped(double signedDelta) {
        return signedDelta > FRONT_OK_RADIUS;
    }
}
