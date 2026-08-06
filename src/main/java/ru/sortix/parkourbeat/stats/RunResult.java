package ru.sortix.parkourbeat.stats;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import javax.annotation.Nullable;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.rating.AccuracyGrade;
import ru.sortix.parkourbeat.rating.Modifier;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class RunResult {
    /** Внутренний id строки в таблице runs (0 — ещё не сохранено / это рекорд). */
    @Builder.Default
    private final long rowId = 0L;

    private final @NonNull UUID playerId;
    /** Ник на момент прохождения. */
    private final @NonNull String playerName;

    private final @NonNull UUID levelId;
    /** Название уровня (legacy-строка с &-цветами) на момент прохождения. */
    private final @NonNull String levelName;
    /** Сложность уровня на момент прохождения. Для PP используется АКТУАЛЬНАЯ (п.11.2). */
    private final @NonNull LevelDifficulty difficulty;

    /** Прогресс 0..100. */
    private final double progressPercent;
    /** Дошёл ли до финиша. */
    private final boolean completed;

    /** Точность 0..100 (формула osu). */
    private final double accuracy;
    /** Оценка на момент прохождения (п.13.1 — храним буквой, не пересчитываем). */
    private final @NonNull AccuracyGrade grade;

    /** Итоговые очки (уже с множителем модификаторов). */
    private final int score;
    /** Очки без множителя — для честного сравнения. */
    private final int rawScore;

    private final int maxCombo;

    private final int count300;
    private final int count100;
    private final int count50;
    private final int missCount;

    /** Активные модификаторы (снимок на момент старта забега). */
    @Builder.Default
    private final @NonNull Set<Modifier> modifiers = Collections.emptySet();
    /** Итоговый множитель. */
    @Builder.Default
    private final double multiplier = 1.0D;

    /** Время прохождения, мс. */
    private final long timeMillis;
    /** Дата и время, unix millis. */
    private final long timestamp;

    /** Флаг «аномалия» для ручной проверки (п.11.5). Никаких автобанов. */
    @Builder.Default
    private final boolean suspicious = false;

    /** FC — полное комбо (пройден до конца, ноль промахов). См. п.13.2. */
    public boolean isFullCombo() {
        return this.completed && this.missCount == 0;
    }

    public int getTotalJudged() {
        return this.count300 + this.count100 + this.count50 + this.missCount;
    }

    /** Коды модификаторов через запятую: {@code "HR,SD"}. Пустая строка, если их нет. */
    @NonNull
    public String getModifiersCodes() {
        return encodeModifiers(this.modifiers);
    }

    /** Человекочитаемый список модификаторов для лора. */
    @NonNull
    public String getModifiersDisplay() {
        if (this.modifiers.isEmpty()) return "&7нет";
        StringBuilder builder = new StringBuilder();
        for (Modifier modifier : orderedModifiers(this.modifiers)) {
            if (builder.length() > 0) builder.append("&7, ");
            builder.append(modifier.getColoredCode());
        }
        return builder.toString();
    }

    @NonNull
    public static String encodeModifiers(@NonNull Set<Modifier> modifiers) {
        StringBuilder builder = new StringBuilder();
        for (Modifier modifier : orderedModifiers(modifiers)) {
            if (builder.length() > 0) builder.append(',');
            builder.append(modifier.getCode());
        }
        return builder.toString();
    }

    @NonNull
    public static Set<Modifier> decodeModifiers(@Nullable String raw) {
        EnumSet<Modifier> result = EnumSet.noneOf(Modifier.class);
        if (raw == null || raw.trim().isEmpty()) return result;
        for (String part : raw.split(",")) {
            Modifier modifier = Modifier.byCode(part);
            if (modifier != null) result.add(modifier);
        }
        return result;
    }

    @NonNull
    private static java.util.List<Modifier> orderedModifiers(@NonNull Set<Modifier> modifiers) {
        java.util.List<Modifier> list = new java.util.ArrayList<>(modifiers);
        list.sort(java.util.Comparator.comparingInt(Enum::ordinal));
        return list;
    }
}
