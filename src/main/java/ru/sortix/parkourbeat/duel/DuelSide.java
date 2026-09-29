package ru.sortix.parkourbeat.duel;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Color;
import org.bukkit.Material;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Сторона дуэльного уровня: путь первого игрока или путь второго.
 * <p>
 * Дуэльный уровень - это две трассы в одном мире шириной в четыре чанка. Каждая
 * сторона живёт своим списком точек, своим цветом частиц и своими прыжковыми
 * кольцами: прыжок засчитывается только на своей стороне, чужие кольца для игрока
 * не существуют вовсе.
 */
@Getter
public enum DuelSide {
    /** Синяя трасса, путь первого игрока. Ставится железной мотыгой. */
    FIRST("Путь 1-го игрока", "&9", Color.fromRGB(0x2E6BFF), Material.IRON_HOE, "first", 2),
    /** Красная трасса, путь второго игрока. Ставится незеритовой мотыгой. */
    SECOND("Путь 2-го игрока", "&c", Color.fromRGB(0xFF2E2E), Material.NETHERITE_HOE, "second", 3);

    private final @NonNull String displayName;
    /** Цветовой код для чата и лора, чтобы стороны нельзя было перепутать. */
    private final @NonNull String colorCode;
    private final @NonNull Color particlesColor;
    private final @NonNull Material wandMaterial;
    /** Метка предмета в его данных: по ней палочка опознаётся после переименования. */
    private final @NonNull String markerValue;
    /** Слот палочки в хотбаре редактора. */
    private final int wandSlot;

    DuelSide(@NonNull String displayName,
             @NonNull String colorCode,
             @NonNull Color particlesColor,
             @NonNull Material wandMaterial,
             @NonNull String markerValue,
             int wandSlot) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.particlesColor = particlesColor;
        this.wandMaterial = wandMaterial;
        this.markerValue = markerValue;
        this.wandSlot = wandSlot;
    }

    public boolean isFirst() {
        return this == FIRST;
    }

    @NonNull
    public DuelSide other() {
        return this == FIRST ? SECOND : FIRST;
    }

    @NonNull
    public String getColoredName() {
        return this.colorCode + this.displayName;
    }

    @NonNull
    public static DuelSide byName(@Nullable String name, @NonNull DuelSide fallback) {
        if (name == null) return fallback;
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) return fallback;
        if (normalized.equals("1") || normalized.equals("FIRST")
            || normalized.equals("BLUE") || normalized.equals("WHITE")) return FIRST;
        if (normalized.equals("2") || normalized.equals("SECOND")
            || normalized.equals("RED") || normalized.equals("BLACK")) return SECOND;
        try {
            return DuelSide.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
