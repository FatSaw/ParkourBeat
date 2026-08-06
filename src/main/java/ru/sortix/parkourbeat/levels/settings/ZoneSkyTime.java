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
public enum ZoneSkyTime {
    /**
     * Leaves the sky to the lightshow.
     */
    FROM_LIGHTSHOW(null, Material.CLOCK, LangOptions.glow_zonetime_lightshow),
    DAY(6000L, Material.YELLOW_STAINED_GLASS, LangOptions.glow_zonetime_day),
    NIGHT(18000L, Material.BLACK_STAINED_GLASS, LangOptions.glow_zonetime_night);

    public static final ZoneSkyTime DEFAULT = FROM_LIGHTSHOW;

    private final @Nullable Long playerTime;
    private final @NonNull Material iconMaterial;
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
    public ZoneSkyTime next() {
        ZoneSkyTime[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    @NonNull
    public static ZoneSkyTime byName(@Nullable String name, @NonNull ZoneSkyTime fallback) {
        if (name == null) return fallback;
        try {
            return ZoneSkyTime.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
