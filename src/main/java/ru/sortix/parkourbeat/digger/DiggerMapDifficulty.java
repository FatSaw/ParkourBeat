package ru.sortix.parkourbeat.digger;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Material;

import java.util.Locale;

/**
 * СЛОЖНОСТЬ АВТОРАЗМЕТКИ.
 * <p>
 * Разбор трека находит все удары подряд - на плотной электронике их по десять в секунду.
 * Сложность решает, какие из них станут нотами: насколько сильным должен быть удар и как
 * часто ноты могут идти. Из одного и того же разбора получается и спокойная карта под
 * новичка, и стена под эксперта, без всякой перегенерации.
 * <p>
 * Жёсткость карты (за сколько секунд видно руду) выставляется заодно: на плотной карте
 * длинное окно видимости превращает тоннель в кашу из руд, а на редкой короткое окно
 * не даёт вообще ничего разглядеть.
 */
@Getter
public enum DiggerMapDifficulty {

    EASY("Лёгкая", Material.LIME_DYE, 0.55D, 480, 1.6D,
        "Только сильные удары, не чаще двух в секунду"),
    HARD("Сложная", Material.YELLOW_DYE, 0.38D, 260, 2.4D,
        "Заметные удары, до четырёх в секунду"),
    EXPERT("Эксперт", Material.ORANGE_DYE, 0.24D, 170, 3.4D,
        "Почти всё, что слышно в треке"),
    EXPERT_PLUS("Эксперт+", Material.REDSTONE, 0.14D, 110, 4.6D,
        "Все удары подряд, включая мелкие");

    private final @NonNull String display;
    private final @NonNull Material icon;
    /** Слабее этого удара нота не ставится. */
    private final double minStrength;
    /** Минимальный промежуток между нотами, мс. */
    private final int minGapMillis;
    /** Жёсткость карты, которая выставляется вместе с разметкой. */
    private final double hardness;
    private final @NonNull String hint;

    DiggerMapDifficulty(@NonNull String display, @NonNull Material icon,
                        double minStrength, int minGapMillis, double hardness,
                        @NonNull String hint) {
        this.display = display;
        this.icon = icon;
        this.minStrength = minStrength;
        this.minGapMillis = minGapMillis;
        this.hardness = hardness;
        this.hint = hint;
    }

    @NonNull
    public static DiggerMapDifficulty byName(@NonNull String raw) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace("+", "_PLUS").replace(" ", "_");
        for (DiggerMapDifficulty value : values()) {
            if (value.name().equals(normalized)) return value;
        }
        return HARD;
    }
}
