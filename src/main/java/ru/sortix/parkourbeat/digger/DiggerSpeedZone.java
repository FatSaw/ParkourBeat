package ru.sortix.parkourbeat.digger;

import lombok.Getter;
import lombok.NonNull;

import javax.annotation.Nullable;

/**
 * ОТРЕЗОК ТРЕКА, КОТОРЫЙ ЕДЕТСЯ БЫСТРЕЕ ИЛИ МЕДЛЕННЕЕ.
 * <p>
 * Зона задаётся таймкодами трека, а не координатами: строитель слышит, что дроп
 * начинается на 1:12, и ставит зону туда же. Переводить это в блоки - работа плагина,
 * тем более что расстояние до точки само зависит от того, какие зоны стоят до неё.
 * <p>
 * МНОЖИТЕЛЬ, А НЕ АБСОЛЮТНАЯ СКОРОСТЬ. Базовая скорость карты выводится из темпа, и
 * если строитель потом поправит BPM, зоны должны поехать вместе с ним, а не остаться
 * с зашитыми числами от прежнего темпа.
 * <p>
 * Пределы намеренно узкие. Ниже 0.5 руды начинают наползать друг на друга в один блок,
 * выше 2.0 окно попадания становится короче задержки среднего канала - карта перестаёт
 * быть проходимой не по вине игрока.
 */
@Getter
public class DiggerSpeedZone {

    public static final double MIN_MULTIPLIER = 0.50D;
    public static final double MAX_MULTIPLIER = 2.00D;

    /** Насколько долгой имеет смысл делать зону: короче - это рывок, а не часть трека. */
    public static final int MIN_LENGTH_MILLIS = 1000;

    private int startMillis;
    private int endMillis;
    private double multiplier;

    public DiggerSpeedZone(int startMillis, int endMillis, double multiplier) {
        this.startMillis = Math.max(0, startMillis);
        this.endMillis = Math.max(this.startMillis, endMillis);
        this.setMultiplier(multiplier);
    }

    public void setStartMillis(int startMillis) {
        this.startMillis = Math.max(0, startMillis);
        if (this.endMillis < this.startMillis) this.endMillis = this.startMillis;
    }

    public void setEndMillis(int endMillis) {
        this.endMillis = Math.max(this.startMillis, endMillis);
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, multiplier));
    }

    public int lengthMillis() {
        return this.endMillis - this.startMillis;
    }

    public boolean contains(double millis) {
        return millis >= this.startMillis && millis < this.endMillis;
    }

    /** Короткая подпись для меню: «1:12-1:28 ×1.20». */
    @NonNull
    public String describe() {
        return format(this.startMillis) + "-" + format(this.endMillis)
            + " ×" + String.format(java.util.Locale.ROOT, "%.2f", this.multiplier);
    }

    @NonNull
    private static String format(int millis) {
        long seconds = Math.max(0, millis) / 1000L;
        return (seconds / 60L) + ":" + String.format("%02d", seconds % 60L);
    }

    @NonNull
    public String serialize() {
        return this.startMillis + ";" + this.endMillis + ";"
            + String.format(java.util.Locale.ROOT, "%.4f", this.multiplier);
    }

    @Nullable
    public static DiggerSpeedZone deserialize(@Nullable String raw) {
        if (raw == null) return null;
        String[] parts = raw.split(";");
        if (parts.length < 3) return null;
        try {
            return new DiggerSpeedZone(
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Double.parseDouble(parts[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
