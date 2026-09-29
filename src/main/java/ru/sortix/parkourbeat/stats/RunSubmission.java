package ru.sortix.parkourbeat.stats;

import lombok.Getter;
import lombok.NonNull;

import javax.annotation.Nullable;

/**
 * Результат отправки прохождения в статистику. Нужен {@code Game}, чтобы решить,
 * какой титл показать и какие строки дельты дописать в чат (п.4 ТЗ).
 */
@Getter
public class RunSubmission {
    private final @NonNull RunResult run;

    /** Побит личный рекорд. */
    private final boolean personalRecord;
    /** Прошлый личный рекорд (null, если это первое прохождение уровня). */
    private final @Nullable RunResult previousPersonalRecord;

    /** Побит глобальный рекорд уровня. */
    private final boolean globalRecord;
    /** У кого рекорд отобран (null, если рекорда ещё не было). */
    private final @Nullable RunResult previousGlobalRecord;

    /** Место игрока в топе уровня ПОСЛЕ сохранения (1 — первый, 0 — не в топе). */
    private final int topPosition;
    /** Всего результатов в топе уровня. */
    private final int topSize;

    public RunSubmission(@NonNull RunResult run,
                         boolean personalRecord,
                         @Nullable RunResult previousPersonalRecord,
                         boolean globalRecord,
                         @Nullable RunResult previousGlobalRecord,
                         int topPosition,
                         int topSize) {
        this.run = run;
        this.personalRecord = personalRecord;
        this.previousPersonalRecord = previousPersonalRecord;
        this.globalRecord = globalRecord;
        this.previousGlobalRecord = previousGlobalRecord;
        this.topPosition = topPosition;
        this.topSize = topSize;
    }

    /**
     * Прирост прогресса в процентных пунктах относительно прошлого личного рекорда.
     * Если рекорда не было — весь пройденный процент.
     */
    public double getProgressDelta() {
        if (this.previousPersonalRecord == null) return this.run.getProgressPercent();
        return this.run.getProgressPercent() - this.previousPersonalRecord.getProgressPercent();
    }

    /** Прирост очков относительно прошлого личного рекорда. */
    public int getScoreDelta() {
        if (this.previousPersonalRecord == null) return this.run.getScore();
        return this.run.getScore() - this.previousPersonalRecord.getScore();
    }

    @NonNull
    public static RunSubmission notRecorded(@NonNull RunResult run) {
        return new RunSubmission(run, false, null, false, null, 0, 0);
    }
}
