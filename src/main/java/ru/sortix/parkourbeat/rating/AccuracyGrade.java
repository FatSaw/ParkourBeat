package ru.sortix.parkourbeat.rating;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccuracyGrade {
    SS("&e&lSS", 96.90D),
    S("&6&lS", 93.00D),
    A("&3&lA", 80.00D),
    B("&2&lB", 65.00D),
    C("&5&lC", 50.00D),
    D("&c&lD", 34.30D),
    R("&4&lR", 0.0D);

    private final @NonNull String formatted;
    private final double minAccuracyPercent;

    public int getBleedIntervalSeconds() {
        return this == R ? 3 : 0;
    }

    @NonNull
    public static AccuracyGrade byAccuracy(double accuracyPercent) {
        for (AccuracyGrade grade : values()) {
            if (accuracyPercent >= grade.minAccuracyPercent) {
                return grade;
            }
        }
        return R;
    }


    @NonNull
    public static AccuracyGrade evaluate(int count300, int count100, int count50, int missCount,
                                         double accuracyPercent) {
        int total = count300 + count100 + count50 + missCount;
        if (total <= 0) return byAccuracy(accuracyPercent);

        AccuracyGrade byAccuracy = byAccuracy(accuracyPercent);
        AccuracyGrade byHits = byHitRatios(count300, count100, count50, missCount, total);
        AccuracyGrade cap = hardCap(count100, count50, missCount);

        return worst(worst(byAccuracy, byHits), cap);
    }
    @NonNull
    public static AccuracyGrade hardCap(int count100, int count50, int missCount) {
        if (missCount > 0) return A;
        if (count100 > 0 || count50 > 0) return S;
        return SS;
    }
    @NonNull
    private static AccuracyGrade byHitRatios(int count300, int count100, int count50, int missCount, int total) {
        if (missCount == 0 && count100 == 0 && count50 == 0) return SS;

        double ratio300 = count300 / (double) total;
        double ratio50 = count50 / (double) total;

        if (missCount == 0 && ratio300 > 0.90D && ratio50 <= 0.01D) return S;
        if ((missCount == 0 && ratio300 > 0.80D) || ratio300 > 0.90D) return A;
        if ((missCount == 0 && ratio300 > 0.70D) || ratio300 > 0.80D) return B;
        if (ratio300 > 0.60D) return C;
        return D;
    }
    @NonNull
    public static AccuracyGrade worst(@NonNull AccuracyGrade a, @NonNull AccuracyGrade b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
    public boolean isAtLeast(@NonNull AccuracyGrade other) {
        return this.ordinal() <= other.ordinal();
    }
}
