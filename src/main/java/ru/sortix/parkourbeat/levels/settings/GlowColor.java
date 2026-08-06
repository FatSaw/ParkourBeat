package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import javax.annotation.Nullable;

/**
 * The outline of a glowing entity takes the colour of the scoreboard team it belongs to,
 * so the sixteen chat colours are exactly the palette available here.
 */
@Getter
@RequiredArgsConstructor
public enum GlowColor {
    DARK_RED(ChatColor.DARK_RED, Material.RED_TERRACOTTA, LangOptions.glow_color_darkred),
    RED(ChatColor.RED, Material.RED_CONCRETE, LangOptions.glow_color_red),
    GOLD(ChatColor.GOLD, Material.ORANGE_CONCRETE, LangOptions.glow_color_gold),
    YELLOW(ChatColor.YELLOW, Material.YELLOW_CONCRETE, LangOptions.glow_color_yellow),
    DARK_GREEN(ChatColor.DARK_GREEN, Material.GREEN_CONCRETE, LangOptions.glow_color_darkgreen),
    GREEN(ChatColor.GREEN, Material.LIME_CONCRETE, LangOptions.glow_color_green),
    DARK_AQUA(ChatColor.DARK_AQUA, Material.CYAN_CONCRETE, LangOptions.glow_color_darkaqua),
    AQUA(ChatColor.AQUA, Material.LIGHT_BLUE_CONCRETE, LangOptions.glow_color_aqua),
    DARK_BLUE(ChatColor.DARK_BLUE, Material.BLUE_CONCRETE, LangOptions.glow_color_darkblue),
    BLUE(ChatColor.BLUE, Material.BLUE_TERRACOTTA, LangOptions.glow_color_blue),
    DARK_PURPLE(ChatColor.DARK_PURPLE, Material.PURPLE_CONCRETE, LangOptions.glow_color_darkpurple),
    LIGHT_PURPLE(ChatColor.LIGHT_PURPLE, Material.MAGENTA_CONCRETE, LangOptions.glow_color_lightpurple),
    WHITE(ChatColor.WHITE, Material.WHITE_CONCRETE, LangOptions.glow_color_white),
    GRAY(ChatColor.GRAY, Material.LIGHT_GRAY_CONCRETE, LangOptions.glow_color_gray),
    DARK_GRAY(ChatColor.DARK_GRAY, Material.GRAY_CONCRETE, LangOptions.glow_color_darkgray),
    BLACK(ChatColor.BLACK, Material.BLACK_CONCRETE, LangOptions.glow_color_black);

    public static final GlowColor DEFAULT = AQUA;

    /**
     * Order used by the two cycling modes.
     */
    public static final GlowColor[] RAINBOW = {RED, GOLD, YELLOW, GREEN, AQUA, BLUE, LIGHT_PURPLE};

    private final @NonNull ChatColor chatColor;
    private final @NonNull Material iconMaterial;
    private final @NonNull LangOptions displayName;

    /**
     * Scoreboard team names are capped at sixteen characters, which the full colour names
     * do not fit into.
     */
    @NonNull
    public String getTeamName() {
        return "pb_glow_" + this.ordinal();
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
    public static GlowColor byName(@Nullable String name, @NonNull GlowColor fallback) {
        if (name == null) return fallback;
        try {
            return GlowColor.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
