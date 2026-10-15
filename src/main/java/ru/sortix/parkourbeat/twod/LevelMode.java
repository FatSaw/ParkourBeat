package ru.sortix.parkourbeat.twod;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Material;

import javax.annotation.Nullable;

/**
 * Режим уровня. Выбирается один раз при создании в /create и дальше не меняется.
 * <p>
 * По умолчанию - {@link #THREE_D}: все старые уровни, у которых поля в файле нет,
 * читаются именно как обычные.
 * <ul>
 *     <li>{@link #THREE_D} - обычный паркур.</li>
 *     <li>{@link #TWO_D} - двумерный «геометрический» уровень.</li>
 *     <li>{@link #DUEL} - дуэльная карта на двоих: две трассы в одном мире.</li>
 *     <li>{@link #THREE_SIXTY} - «360»: труба квадратного сечения, где строить можно
 *         во все стороны, а не только по полу.</li>
 * </ul>
 */
@Getter
public enum LevelMode {
    THREE_D("3D", "&b3D-уровень", Material.GRASS_BLOCK),
    TWO_D("2D", "&d2D-уровень", Material.WHITE_STAINED_GLASS),
    DUEL("DUEL", "&6Дуэльная карта", Material.NETHERITE_HOE),
    THREE_SIXTY("360", "&a360-уровень", Material.QUARTZ_PILLAR),
    DIGGER("КОПАТЕЛЬ", "&5Копатель", Material.NETHERITE_PICKAXE);

    private final @NonNull String shortName;
    private final @NonNull String displayName;
    private final @NonNull Material icon;

    LevelMode(@NonNull String shortName, @NonNull String displayName, @NonNull Material icon) {
        this.shortName = shortName;
        this.displayName = displayName;
        this.icon = icon;
    }

    public boolean isTwoD() {
        return this == TWO_D;
    }

    public boolean isDuel() {
        return this == DUEL;
    }

    public boolean isThreeSixty() {
        return this == THREE_SIXTY;
    }

    /** Ритм-режим «Копатель»: руды-ноты в тоннеле и творческий режим у игрока. */
    public boolean isDigger() {
        return this == DIGGER;
    }

    /**
     * Следующий режим по кругу - именно так режим и выбирается в меню создания.
     */
    @NonNull
    public LevelMode next() {
        LevelMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    /**
     * Историческое переключение «3D - 2D». Оставлено ради старых вызовов; выбор режима
     * в меню идёт через {@link #next()}, потому что режимов давно больше двух.
     */
    @NonNull
    public LevelMode toggle() {
        return this == THREE_D ? TWO_D : THREE_D;
    }

    @NonNull
    public static LevelMode byName(@Nullable String name, @NonNull LevelMode fallback) {
        if (name == null) return fallback;
        String normalized = name.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) return fallback;
        if (normalized.equals("2D") || normalized.equals("TWO_D") || normalized.equals("TWOD")) return TWO_D;
        if (normalized.equals("3D") || normalized.equals("THREE_D") || normalized.equals("THREED")) return THREE_D;
        if (normalized.equals("DUEL") || normalized.equals("DUELS") || normalized.equals("VS")) return DUEL;
        if (normalized.equals("360") || normalized.equals("THREE_SIXTY")
            || normalized.equals("THREESIXTY") || normalized.equals("_360")) return THREE_SIXTY;
        if (normalized.equals("DIGGER") || normalized.equals("КОПАТЕЛЬ")
            || normalized.equals("MINER") || normalized.equals("DIG")) return DIGGER;
        try {
            return LevelMode.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
