package ru.sortix.parkourbeat.rating;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import ru.sortix.parkourbeat.utils.text.Theme;

@Getter
@RequiredArgsConstructor
public enum AccuracyGrade {
    // Цвета оценок заданы прямыми кодами, а не &e/&6: игрок узнаёт SS по жёлтому,
    // а S по золотому, и перекраска палитры плагина не должна их трогать.
    SS(Theme.V_YELLOW, "SS", 96.90D),
    S(Theme.V_GOLD, "S", 93.00D),
    A(Theme.V_DARK_AQUA, "A", 80.00D),
    B(Theme.V_DARK_GREEN, "B", 65.00D),
    C(Theme.V_DARK_PURPLE, "C", 50.00D),
    D(Theme.V_RED, "D", 34.30D),
    R(Theme.V_DARK_RED, "R", 0.0D);

    /** Только цвет, без буквы: нужен там, где им красят соседний текст. */
    private final @NonNull String colorCode;
    private final @NonNull String letter;
    private final double minAccuracyPercent;

    @NonNull
    public String getFormatted() {
        return this.colorCode + "&l" + this.letter;
    }

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
        return evaluate(count300, count100, count50, missCount, 0, accuracyPercent);
    }

    @NonNull
    public static AccuracyGrade evaluate(int count300, int count100, int count50, int missCount,
                                         int comboBreaks, double accuracyPercent) {
        int total = count300 + count100 + count50 + missCount;
        if (total <= 0) return byAccuracy(accuracyPercent);

        AccuracyGrade byAccuracy = byAccuracy(accuracyPercent);
        AccuracyGrade byHits = byHitRatios(count300, count100, count50, missCount, total);
        AccuracyGrade cap = hardCap(count100, count50, missCount, comboBreaks);

        return worst(worst(byAccuracy, byHits), cap);
    }

    @NonNull
    public static AccuracyGrade hardCap(int count100, int count50, int missCount) {
        return hardCap(count100, count50, missCount, 0);
    }

    /**
     * @param comboBreaks обрывы комбо - отпущенный на бегу Ctrl.
     *                    SS - это идеальный забег целиком, а не только идеальные прыжки,
     *                    поэтому один обрыв опускает потолок до S. Промах по кольцу
     *                    по-прежнему строже: он режет до A.
     */
    @NonNull
    public static AccuracyGrade hardCap(int count100, int count50, int missCount, int comboBreaks) {
        if (missCount > 0) return A;
        if (count100 > 0 || count50 > 0 || comboBreaks > 0) return S;
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
