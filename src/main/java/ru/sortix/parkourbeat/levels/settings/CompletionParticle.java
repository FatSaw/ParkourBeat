package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

/**
 * A fixed set of particle presets that can play on win / lose / fall. Each preset encodes the
 * exact /particle arguments the builder asked for. Everything is spawned per-player.
 */
@Getter
public enum CompletionParticle {
    NONE("None", Material.BARRIER, null, 0, 1, 0, 0, 0, 0, 0, null),
    WARPED_SPORE_NOSE("Nose spores", Material.WARPED_FUNGUS,
        safeParticle("WARPED_SPORE"), 0, 1, 0, 1.5, 1.5, 1.5, 150, null),
    DRAGON_BREATH("Dragon breath", Material.DRAGON_BREATH,
        Particle.DRAGON_BREATH, 0, 1, 0, 0.5, 0.5, 0.5, 80, null),
    GREEN_DECAY("Green decay", Material.TOTEM_OF_UNDYING,
        Particle.TOTEM, 0, 1, 0, 1, 1, 1, 100, null),
    WITCH_FLEW("Witch flew away", Material.CAULDRON,
        Particle.SPELL_WITCH, 0, 1, 0, 1, 1, 1, 100, null),
    OBSIDIAN_BREAK("Obsidian break", Material.CRYING_OBSIDIAN,
        Particle.BLOCK_CRACK, 0, 1, 0, 1, 1, 1, 300, Material.CRYING_OBSIDIAN),
    BLOOD_SPILL("Blood spill", Material.REDSTONE_BLOCK,
        Particle.ITEM_CRACK, 0, 0.5, 0, 0.2, 0.2, 0.2, 150, Material.REDSTONE_BLOCK),
    WHITE_FLASH("White flash", Material.WHITE_DYE,
        Particle.FLASH, 0, 1, 0, 0.2, 0.2, 0.2, 5, null),
    SCATTER_EYES("Scattering eyes", Material.ENDER_EYE,
        Particle.NAUTILUS, 0, 0.5, 0, 0.5, 2, 0.5, 50, null),
    GOLD_SPLIT("Gold split", Material.GOLD_BLOCK,
        Particle.ITEM_CRACK, 0, 0.5, 0, 0.3, 0.3, 0.3, 120, Material.GOLD_BLOCK);

    private final @NonNull String title;
    private final @NonNull Material icon;
    private final @Nullable Particle particle;
    private final double offsetXCenter, offsetYCenter, offsetZCenter;
    private final double spreadX, spreadY, spreadZ;
    private final int count;
    private final @Nullable Material blockOrItemData;

    CompletionParticle(@NonNull String title, @NonNull Material icon, @Nullable Particle particle,
                       double offsetXCenter, double offsetYCenter, double offsetZCenter,
                       double spreadX, double spreadY, double spreadZ,
                       int count, @Nullable Material blockOrItemData) {
        this.title = title;
        this.icon = icon;
        this.particle = particle;
        this.offsetXCenter = offsetXCenter;
        this.offsetYCenter = offsetYCenter;
        this.offsetZCenter = offsetZCenter;
        this.spreadX = spreadX;
        this.spreadY = spreadY;
        this.spreadZ = spreadZ;
        this.count = count;
        this.blockOrItemData = blockOrItemData;
    }

    @SuppressWarnings("deprecation")
    public void play(@NonNull Player player) {
        if (this.particle == null) return;
        Location loc = player.getLocation().add(
            this.offsetXCenter, this.offsetYCenter, this.offsetZCenter);
        try {
            if (this.particle == Particle.BLOCK_CRACK && this.blockOrItemData != null) {
                player.spawnParticle(this.particle, loc, this.count,
                    this.spreadX, this.spreadY, this.spreadZ, 0.5,
                    this.blockOrItemData.createBlockData());
            } else if (this.particle == Particle.ITEM_CRACK && this.blockOrItemData != null) {
                player.spawnParticle(this.particle, loc, this.count,
                    this.spreadX, this.spreadY, this.spreadZ, 0.6,
                    new org.bukkit.inventory.ItemStack(this.blockOrItemData));
            } else {
                player.spawnParticle(this.particle, loc, this.count,
                    this.spreadX, this.spreadY, this.spreadZ, 0.5);
            }
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    private static Particle safeParticle(@NonNull String name) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @NonNull
    public static CompletionParticle byName(@Nullable String name, @NonNull CompletionParticle fallback) {
        if (name == null) return fallback;
        try {
            return CompletionParticle.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    @NonNull
    public CompletionParticle next() {
        CompletionParticle[] all = values();
        return all[(this.ordinal() + 1) % all.length];
    }
}
