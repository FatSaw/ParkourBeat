package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import javax.annotation.Nullable;

@Getter
@RequiredArgsConstructor
public enum LevelBossBarColor {
    YELLOW(BossBar.Color.YELLOW, NamedTextColor.YELLOW, Material.YELLOW_WOOL, Color.fromRGB(0xFFE14D), LangOptions.lightshow_bossbar_yellow),
    PINK(BossBar.Color.PINK, NamedTextColor.LIGHT_PURPLE, Material.PINK_WOOL, Color.fromRGB(0xFF7BD5), LangOptions.lightshow_bossbar_pink),
    RED(BossBar.Color.RED, NamedTextColor.RED, Material.RED_WOOL, Color.fromRGB(0xFF3B3B), LangOptions.lightshow_bossbar_red),
    GREEN(BossBar.Color.GREEN, NamedTextColor.GREEN, Material.LIME_WOOL, Color.fromRGB(0x4DFF66), LangOptions.lightshow_bossbar_green),
    BLUE(BossBar.Color.BLUE, NamedTextColor.AQUA, Material.LIGHT_BLUE_WOOL, Color.fromRGB(0x4DD2FF), LangOptions.lightshow_bossbar_blue),
    PURPLE(BossBar.Color.PURPLE, NamedTextColor.DARK_PURPLE, Material.PURPLE_WOOL, Color.fromRGB(0x9B4DFF), LangOptions.lightshow_bossbar_purple),
    WHITE(BossBar.Color.WHITE, NamedTextColor.WHITE, Material.WHITE_WOOL, Color.fromRGB(0xFFFFFF), LangOptions.lightshow_bossbar_white);

    public static final LevelBossBarColor DEFAULT = YELLOW;

    private final @NonNull BossBar.Color barColor;
    private final @NonNull NamedTextColor textColor;
    private final @NonNull Material iconMaterial;
    private final @NonNull Color markerColor;
    private final @NonNull LangOptions displayName;

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
    public static LevelBossBarColor byName(@Nullable String name, @NonNull LevelBossBarColor fallback) {
        if (name == null) return fallback;
        try {
            return LevelBossBarColor.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
