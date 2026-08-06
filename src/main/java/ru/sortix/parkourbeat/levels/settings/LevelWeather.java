package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.WeatherType;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import javax.annotation.Nullable;

@Getter
@RequiredArgsConstructor
public enum LevelWeather {
    /**
     * Whatever the current sky preset asks for.
     */
    AUTO(null, Material.CLOCK, LangOptions.lightshow_weather_auto),
    CLEAR(WeatherType.CLEAR, Material.GLASS, LangOptions.lightshow_weather_clear),
    /**
     * Rain, or snow if the biome under the player is a cold one.
     */
    RAIN(WeatherType.DOWNFALL, Material.WATER_BUCKET, LangOptions.lightshow_weather_rain);

    public static final LevelWeather DEFAULT = AUTO;

    private final @Nullable WeatherType weatherType;
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
    public LevelWeather next() {
        LevelWeather[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    @NonNull
    public static LevelWeather byName(@Nullable String name, @NonNull LevelWeather fallback) {
        if (name == null) return fallback;
        try {
            return LevelWeather.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
