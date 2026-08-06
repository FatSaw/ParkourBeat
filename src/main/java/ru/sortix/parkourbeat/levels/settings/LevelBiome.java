package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Getter
public enum LevelBiome {
    PLAINS(Material.GRASS_BLOCK, LangOptions.lightshow_biome_plains, "PLAINS"),
    SNOWY(Material.SNOW_BLOCK, LangOptions.lightshow_biome_snowy, "SNOWY_PLAINS", "SNOWY_TUNDRA", "ICE_SPIKES"),
    DESERT(Material.SAND, LangOptions.lightshow_biome_desert, "DESERT"),
    SWAMP(Material.LILY_PAD, LangOptions.lightshow_biome_swamp, "SWAMP", "SWAMPLAND"),
    JUNGLE(Material.JUNGLE_LEAVES, LangOptions.lightshow_biome_jungle, "JUNGLE"),
    DARK_FOREST(Material.DARK_OAK_LEAVES, LangOptions.lightshow_biome_darkforest, "DARK_FOREST", "ROOFED_FOREST"),
    BADLANDS(Material.RED_SAND, LangOptions.lightshow_biome_badlands, "BADLANDS", "MESA"),
    NETHER(Material.NETHERRACK, LangOptions.lightshow_biome_nether, "NETHER_WASTES", "NETHER"),
    CRIMSON(Material.CRIMSON_NYLIUM, LangOptions.lightshow_biome_crimson, "CRIMSON_FOREST"),
    WARPED(Material.WARPED_NYLIUM, LangOptions.lightshow_biome_warped, "WARPED_FOREST"),
    SOUL_SAND_VALLEY(Material.SOUL_SAND, LangOptions.lightshow_biome_soulsand, "SOUL_SAND_VALLEY"),
    BASALT_DELTAS(Material.BASALT, LangOptions.lightshow_biome_basalt, "BASALT_DELTAS"),
    THE_END(Material.END_STONE, LangOptions.lightshow_biome_theend, "THE_END", "END");

    public static final LevelBiome DEFAULT = PLAINS;

    private final @NonNull Material iconMaterial;
    private final @NonNull LangOptions displayName;
    private final String[] candidateNames;

    LevelBiome(@NonNull Material iconMaterial, @NonNull LangOptions displayName, String... candidateNames) {
        this.iconMaterial = iconMaterial;
        this.displayName = displayName;
        this.candidateNames = candidateNames;
    }

    @Nullable
    public Biome resolve() {
        for (String candidate : this.candidateNames) {
            try {
                return Biome.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    public boolean isSupported() {
        return this.resolve() != null;
    }

    @NonNull
    public Component getDisplayName(@Nullable String locale) {
        return this.displayName.getComponent(locale == null ? "" : locale);
    }

    @NonNull
    public String getDisplayNameString(@Nullable String locale) {
        String value = this.displayName.get(locale == null ? "" : locale);
        return value == null ? this.name() : value;
    }

    @NonNull
    public static List<LevelBiome> supported() {
        List<LevelBiome> result = new ArrayList<>();
        for (LevelBiome biome : values()) {
            if (biome.isSupported()) result.add(biome);
        }
        return result;
    }

    @NonNull
    public static LevelBiome byName(@Nullable String name, @NonNull LevelBiome fallback) {
        if (name == null) return fallback;
        try {
            return LevelBiome.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
