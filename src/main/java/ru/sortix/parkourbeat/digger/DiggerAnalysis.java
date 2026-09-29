package ru.sortix.parkourbeat.digger;

import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * РАЗБОР ТРЕКА.
 * <p>
 * То, что посчитал внешний анализатор: темп, смещение первой доли и список ударов
 * (онсетов) с их силой и частотной полосой. Из этого собирается черновая карта, по
 * которой уже реально попадать в музыку, а не в ровную сетку.
 * <p>
 * Формат нарочно текстовый и построчный - его читает {@link DiggerAnalysisStore}
 * без единой сторонней библиотеки, и его же руками можно поправить в блокноте.
 */
@Getter
public class DiggerAnalysis {

    /** Частотная полоса удара: от неё зависит, куда в тоннеле встанет руда. */
    public enum Band {
        /** Бочка и бас - нижний ряд. */
        LOW,
        /** Малый барабан, вокал, синты - середина. */
        MID,
        /** Хай-хэты и щелчки - верхний ряд. */
        HIGH;

        @NonNull
        public static Band byName(@NonNull String raw) {
            switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "low":
                case "bass":
                    return LOW;
                case "high":
                case "hat":
                    return HIGH;
                default:
                    return MID;
            }
        }
    }

    /** Один удар в треке. */
    @Getter
    public static class Onset {
        private final int millis;
        /** Сила удара, 0..1. По ней выбирается руда. */
        private final double strength;
        private final @NonNull Band band;

        public Onset(int millis, double strength, @NonNull Band band) {
            this.millis = Math.max(0, millis);
            this.strength = Math.max(0.0D, Math.min(1.0D, strength));
            this.band = band;
        }
    }

    private final @NonNull String trackId;
    private double bpm;
    private int offsetMillis;

    /** Длина трека, если её удалось узнать. 0 - неизвестна. */
    private int durationMillis = 0;
    private final List<Onset> onsets = new ArrayList<>();

    public DiggerAnalysis(@NonNull String trackId, double bpm, int offsetMillis) {
        this.trackId = trackId;
        this.bpm = bpm;
        this.offsetMillis = offsetMillis;
    }

    public void setBpm(double bpm) {
        if (bpm > 0.0D) this.bpm = bpm;
    }

    public void setOffsetMillis(int offsetMillis) {
        this.offsetMillis = offsetMillis;
    }

    public void setDurationMillis(int durationMillis) {
        this.durationMillis = Math.max(0, durationMillis);
    }

    /**
     * Оставить только те удары, которые попадут в карту выбранной сложности.
     *
     * @param minStrength слабее этого удара нота не ставится
     * @param minGapMillis минимальный промежуток между соседними нотами
     */
    public void filter(double minStrength, int minGapMillis) {
        this.onsets.removeIf(onset -> onset.getStrength() < minStrength);
        this.thin(minGapMillis);
    }

    public void addOnset(@NonNull Onset onset) {
        this.onsets.add(onset);
    }

    public void sort() {
        this.onsets.sort(Comparator.comparingInt(Onset::getMillis));
    }

    public int size() {
        return this.onsets.size();
    }

    /**
     * Проредить список: убрать удары, стоящие ближе указанного промежутка друг к другу.
     * <p>
     * Анализатор охотно находит по десять онсетов на долю, а играть такое нельзя:
     * между двумя нотами обязан быть промежуток, за который человек физически успевает
     * навести прицел и щёлкнуть.
     *
     * @param minGapMillis минимальный промежуток между соседними нотами
     */
    public void thin(int minGapMillis) {
        if (minGapMillis <= 0 || this.onsets.isEmpty()) return;
        this.sort();

        List<Onset> result = new ArrayList<>(this.onsets.size());
        for (Onset onset : this.onsets) {
            // ПЕРВЫЙ УДАР ПРОХОДИТ БЕЗУСЛОВНО: сравнивать его не с чем.
            //
            // Раньше здесь стоял сторож lastMillis = Integer.MIN_VALUE, и выражение
            // onset.getMillis() - lastMillis переполняло int: 1000 - (-2147483648)
            // не влезает в 32 бита и даёт -2147482648, то есть заведомо меньше любого
            // промежутка. Первая нота выбрасывалась, сторож оставался нетронутым, и
            // ровно то же повторялось для всех остальных - список опустошался целиком,
            // сколько бы ударов ни нашёл анализатор.
            if (result.isEmpty()) {
                result.add(onset);
                continue;
            }

            Onset last = result.get(result.size() - 1);
            if (onset.getMillis() - last.getMillis() < minGapMillis) {
                // Из двух соседних оставляем тот, что бьёт сильнее.
                if (onset.getStrength() > last.getStrength()) {
                    result.set(result.size() - 1, onset);
                }
                continue;
            }
            result.add(onset);
        }

        this.onsets.clear();
        this.onsets.addAll(result);
    }
}
