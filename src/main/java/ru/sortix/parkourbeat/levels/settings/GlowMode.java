package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import javax.annotation.Nullable;

@Getter
@RequiredArgsConstructor
public enum GlowMode {
    STATIC(Material.GLOWSTONE, LangOptions.glow_mode_static),
    BLINK(Material.REDSTONE_TORCH, LangOptions.glow_mode_blink),
    RGB_SLOW(Material.PURPLE_STAINED_GLASS, LangOptions.glow_mode_rgbslow),
    RGB_FAST(Material.MAGENTA_STAINED_GLASS, LangOptions.glow_mode_rgbfast);

    public static final GlowMode DEFAULT = STATIC;

    private final @NonNull Material iconMaterial;
    private final @NonNull LangOptions displayName;

    public boolean isAnimated() {
        return this != STATIC;
    }

    public boolean usesRainbow() {
        return this == RGB_SLOW || this == RGB_FAST;
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
    public GlowMode next() {
        GlowMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    @NonNull
    public static GlowMode byName(@Nullable String name, @NonNull GlowMode fallback) {
        if (name == null) return fallback;
        try {
            return GlowMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
