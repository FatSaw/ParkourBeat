package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.sortix.parkourbeat.levels.DirectionChecker;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Set;

/**
 * Every preset is a per-player time override plus an optional weather override and night vision.
 * The tick values below are the ones picked in game, so change them only against what the
 * client actually renders.
 */
@Getter
@RequiredArgsConstructor
public enum SkyType {
    DAY(6000L, false, WeatherType.CLEAR, Material.YELLOW_STAINED_GLASS, Color.fromRGB(0x7EC8FF), true, LangOptions.lightshow_sky_day),
    EVENING(12300L, false, WeatherType.CLEAR, Material.ORANGE_STAINED_GLASS, Color.fromRGB(0xFF9B4A), true, LangOptions.lightshow_sky_evening),
    NIGHT(18000L, false, WeatherType.CLEAR, Material.BLACK_STAINED_GLASS, Color.fromRGB(0x1B2452), true, LangOptions.lightshow_sky_night),
    ORANGE(12900L, true, WeatherType.CLEAR, Material.ORANGE_CONCRETE, Color.fromRGB(0xFF7A18), false, LangOptions.lightshow_sky_orange),
    RED_PINK(14000L, true, WeatherType.CLEAR, Material.PINK_CONCRETE, Color.fromRGB(0xFF4D7E), false, LangOptions.lightshow_sky_redpink),
    PURPLE(13600L, true, WeatherType.CLEAR, Material.PURPLE_CONCRETE, Color.fromRGB(0x9B4DFF), true, LangOptions.lightshow_sky_purple),
    SOFT_WHITE(6000L, true, WeatherType.DOWNFALL, Material.WHITE_CONCRETE, Color.fromRGB(0xF2F2F2), true, LangOptions.lightshow_sky_softwhite);

    public static final SkyType DEFAULT = DAY;
    public static final long DAY_LENGTH_TICKS = 24000L;

    private static final int NIGHT_VISION_DURATION_TICKS = 1_000_000;

    private final long playerTime;
    private final boolean nightVision;
    private final @NonNull WeatherType weather;
    private final @NonNull Material iconMaterial;
    private final @NonNull Color markerColor;
    /**
     * The sunset gradient hangs over the west. Running towards +X the player looks the other
     * way and never sees it, so the warm presets are not offered for such levels.
     */
    private final boolean facingIndependent;
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

    public boolean isAvailableFor(@NonNull DirectionChecker.Direction direction) {
        if (this.facingIndependent) return true;
        return direction != DirectionChecker.Direction.POSITIVE_X;
    }

    @NonNull
    public static Set<SkyType> available(@NonNull DirectionChecker.Direction direction) {
        Set<SkyType> result = EnumSet.noneOf(SkyType.class);
        for (SkyType skyType : values()) {
            if (skyType.isAvailableFor(direction)) result.add(skyType);
        }
        return result;
    }

    @NonNull
    public static SkyType byName(@Nullable String name, @NonNull SkyType fallback) {
        if (name == null) return fallback;
        try {
            return SkyType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public void apply(@NonNull Player player) {
        applyState(player, this.playerTime, this.nightVision, this.weather);
    }

    public static void applyState(@NonNull Player player,
                                  long playerTime,
                                  boolean nightVision,
                                  @NonNull WeatherType weather
    ) {
        player.setPlayerTime(playerTime, false);
        player.setPlayerWeather(weather);
        setNightVision(player, nightVision);
    }

    public static void reset(@NonNull Player player) {
        player.resetPlayerTime();
        player.resetPlayerWeather();
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

    public static void setNightVision(@NonNull Player player, boolean enabled) {
        if (enabled) {
            if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) return;
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.NIGHT_VISION, NIGHT_VISION_DURATION_TICKS, 0, true, false, false));
        } else {
            if (!player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) return;
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    /**
     * Signed distance around the day cycle taking the shorter way, so a night to morning
     * walk does not rewind through the whole day.
     */
    public static long shortestDelta(long from, long to) {
        return ((((to - from) % DAY_LENGTH_TICKS) + DAY_LENGTH_TICKS + (DAY_LENGTH_TICKS / 2))
            % DAY_LENGTH_TICKS) - (DAY_LENGTH_TICKS / 2);
    }

    public static long interpolateTime(long from, long to, double progress) {
        double clamped = progress < 0.0D ? 0.0D : Math.min(progress, 1.0D);
        return normalizeTime(from + Math.round(shortestDelta(from, to) * clamped));
    }

    public static long normalizeTime(long time) {
        return ((time % DAY_LENGTH_TICKS) + DAY_LENGTH_TICKS) % DAY_LENGTH_TICKS;
    }
}
