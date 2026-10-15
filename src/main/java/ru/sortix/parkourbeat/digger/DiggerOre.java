package ru.sortix.parkourbeat.digger;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * РУДЫ-НОТЫ РЕЖИМА «КОПАТЕЛЬ».
 * <p>
 * Каждая руда - это тип ноты: свой цвет искр, свой звук, свой множитель очков.
 * Медной руды здесь нет намеренно: в 1.16.5 её не существует, а плагин собирается
 * именно под эту версию. Каменных вариантов (deepslate) по той же причине нет.
 * <p>
 * Множитель НЕ отменяет судейство по времени: очки всё равно даёт точность попадания
 * (+300 / +100 / +50), а руда лишь умножает результат. Древние обломки - дроп-нота:
 * пятикратные очки и полный залп светового шоу.
 */
@Getter
public enum DiggerOre {

    COAL(Material.COAL_ORE, "Уголь", "&8Уголь",
        Color.fromRGB(0x4A4A4A), 1.0D, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.8f),

    IRON(Material.IRON_ORE, "Железо", "&7Железо",
        Color.fromRGB(0xD8AF93), 1.0D, Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 1.0f),

    GOLD(Material.GOLD_ORE, "Золото", "&eЗолото",
        Color.fromRGB(0xFCEE4B), 1.25D, Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f),

    REDSTONE(Material.REDSTONE_ORE, "Редстоун", "&cРедстоун",
        Color.fromRGB(0xFF2A2A), 1.0D, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f),

    LAPIS(Material.LAPIS_ORE, "Лазурит", "&9Лазурит",
        Color.fromRGB(0x1F5AE8), 1.0D, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.7f),

    DIAMOND(Material.DIAMOND_ORE, "Алмаз", "&bАлмаз",
        Color.fromRGB(0x4AEDD9), 2.0D, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.4f),

    EMERALD(Material.EMERALD_ORE, "Изумруд", "&aИзумруд",
        Color.fromRGB(0x17DD62), 2.0D, Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 1.5f),

    QUARTZ(Material.NETHER_QUARTZ_ORE, "Кварц Незера", "&fКварц",
        Color.fromRGB(0xEDE8DE), 1.25D, Sound.BLOCK_NOTE_BLOCK_HAT, 1.8f),

    NETHER_GOLD(Material.NETHER_GOLD_ORE, "Золото Незера", "&6Золото Незера",
        Color.fromRGB(0xFFB03A), 1.5D, Sound.BLOCK_NOTE_BLOCK_PLING, 1.3f),

    ANCIENT_DEBRIS(Material.ANCIENT_DEBRIS, "Древние обломки", "&5Дроп-нота",
        Color.fromRGB(0x9B4DFF), 5.0D, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f);

    /** Блок, который реально стоит в тоннеле. */
    private final @NonNull Material material;
    /** Имя для меню и подсказок. */
    private final @NonNull String display;
    /** Цветное имя предмета в инвентаре строителя. */
    private final @NonNull String coloredName;
    /** Цвет искр REDSTONE при разрушении и цвет луча END_ROD. */
    private final @NonNull Color color;
    /** Во сколько раз умножаются очки за попадание по этой руде. */
    private final double scoreMultiplier;
    /** Звук попадания. */
    private final @NonNull Sound sound;
    /** Базовый питч звука; поверх него игра доворачивает питч по ступени лада. */
    private final float pitch;

    DiggerOre(@NonNull Material material,
              @NonNull String display,
              @NonNull String coloredName,
              @NonNull Color color,
              double scoreMultiplier,
              @NonNull Sound sound,
              float pitch
    ) {
        this.material = material;
        this.display = display;
        this.coloredName = coloredName;
        this.color = color;
        this.scoreMultiplier = scoreMultiplier;
        this.sound = sound;
        this.pitch = pitch;
    }

    /** Дроп-нота: на неё вешается полный залп шоу и максимальные очки. */
    public boolean isDrop() {
        return this == ANCIENT_DEBRIS;
    }

    /** Редкая руда - акцент трека, судится строже обычной. */
    public boolean isRare() {
        return this.scoreMultiplier >= 2.0D;
    }

    @Nullable
    public static DiggerOre byMaterial(@Nullable Material material) {
        if (material == null) return null;
        for (DiggerOre ore : values()) {
            if (ore.material == material) return ore;
        }
        return null;
    }

    @Nullable
    public static DiggerOre byName(@Nullable String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        for (DiggerOre ore : values()) {
            if (ore.name().equals(normalized) || ore.material.name().equals(normalized)) return ore;
        }
        return null;
    }

    /**
     * Руда по силе онсета.
     *
     * @param strength сила удара в треке, 0..1
     * @param bass     попал ли онсет в низкую полосу частот
     * @deprecated Пороги здесь абсолютные, а сила удара зависит от сведения трека:
     *     на плотном мастеринге почти всё оказывается выше 0.92, и карта превращается
     *     в сплошные дроп-ноты. Пользуйтесь {@link #byRank}, который смотрит на
     *     положение удара СРЕДИ ОСТАЛЬНЫХ и поэтому работает одинаково на любом треке.
     */
    @Deprecated
    @NonNull
    public static DiggerOre byIntensity(double strength, boolean bass) {
        if (strength >= 0.92D) return ANCIENT_DEBRIS;
        if (strength >= 0.78D) return bass ? REDSTONE : DIAMOND;
        if (strength >= 0.62D) return bass ? LAPIS : EMERALD;
        if (strength >= 0.45D) return bass ? NETHER_GOLD : GOLD;
        if (strength >= 0.28D) return bass ? IRON : QUARTZ;
        return COAL;
    }

    /**
     * РУДА ПО МЕСТУ УДАРА В ОБЩЕМ РЯДУ.
     * <p>
     * Ранг - это доля ударов трека, которые слабее этого: 0 у самого тихого, 1 у
     * самого громкого. В отличие от абсолютной силы он не зависит ни от мастеринга,
     * ни от жанра, поэтому доля редких руд на карте получается ОДИНАКОВАЯ всегда,
     * а не «то ни одной, то через каждую».
     * <p>
     * Дроп-нота сюда не попадает вовсе: одного лишь ранга ей мало, ей нужен ещё и
     * запас по времени от предыдущей, а это знает только тот, кто раскладывает карту.
     *
     * @param rank положение удара среди остальных, 0..1
     * @param bass попал ли онсет в низкую полосу частот
     */
    @NonNull
    public static DiggerOre byRank(double rank, boolean bass) {
        if (rank >= 0.90D) return bass ? REDSTONE : DIAMOND;
        if (rank >= 0.72D) return bass ? LAPIS : EMERALD;
        if (rank >= 0.45D) return bass ? NETHER_GOLD : GOLD;
        if (rank >= 0.20D) return bass ? IRON : QUARTZ;
        return COAL;
    }
}
