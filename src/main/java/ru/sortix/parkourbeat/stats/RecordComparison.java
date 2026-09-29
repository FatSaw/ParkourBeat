package ru.sortix.parkourbeat.stats;

import lombok.NonNull;

import javax.annotation.Nullable;
import java.util.Comparator;

public final class RecordComparison {
    /** Лучшее первым. Используется для топа уровня и для сортировки рекордов. */
    public static final Comparator<RunResult> BEST_FIRST = (a, b) -> compare(b, a);

    private RecordComparison() {
    }

    /**
     * @return true, если {@code candidate} лучше, чем {@code current} (или current == null).
     */
    public static boolean isBetter(@NonNull RunResult candidate, @Nullable RunResult current) {
        if (current == null) return true;
        return compare(candidate, current) > 0;
    }

    /**
     * @return >0 если a лучше b, <0 если хуже, 0 если полностью равны.
     */
    public static int compare(@NonNull RunResult a, @NonNull RunResult b) {
        // 1. Пройденный до финиша всегда сильнее непройденного.
        if (a.isCompleted() != b.isCompleted()) {
            return a.isCompleted() ? 1 : -1;
        }

        if (!a.isCompleted()) {
            // 2. Оба не дошли — сравниваем ТОЛЬКО прогресс.
            int byProgress = Double.compare(a.getProgressPercent(), b.getProgressPercent());
            if (byProgress != 0) return byProgress;
            // Полностью равный прогресс — рекорд не обновляем, но для сортировки
            // топа приятнее показывать более точное прохождение выше.
            return Double.compare(a.getAccuracy(), b.getAccuracy());
        }

        // 3. Оба дошли: очки → точность → время.
        int byScore = Integer.compare(a.getScore(), b.getScore());
        if (byScore != 0) return byScore;

        int byAccuracy = Double.compare(a.getAccuracy(), b.getAccuracy());
        if (byAccuracy != 0) return byAccuracy;

        // Меньшее время лучше.
        return Long.compare(b.getTimeMillis(), a.getTimeMillis());
    }
}
