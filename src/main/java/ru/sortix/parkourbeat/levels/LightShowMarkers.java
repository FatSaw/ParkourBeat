package ru.sortix.parkourbeat.levels;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.levels.settings.BiomeZone;
import ru.sortix.parkourbeat.levels.settings.BossBarCue;
import ru.sortix.parkourbeat.levels.settings.FlashCue;
import ru.sortix.parkourbeat.levels.settings.SkyCycleCue;
import ru.sortix.parkourbeat.levels.settings.WeatherCue;
import ru.sortix.parkourbeat.levels.settings.LightShowCue;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.world.Cuboid;

/**
 * Draws every lightshow trigger as a coloured line across the level, so the author sees
 * where a cue fires instead of having to read timecodes.
 *
 * SPELL_MOB carries its colour in the offset fields with a count of zero, which is the only
 * way to get an arbitrary colour out of that particle.
 */
@UtilityClass
public class LightShowMarkers {
    public final int RENDER_PERIOD_TICKS = 10;

    private final double STEP = 0.5D;
    private final double STEP_HORIZONTAL = 0.40D;
    private final double VERTICAL_HEIGHT = 3.0D;
    private final double MAX_RENDER_DISTANCE_SQUARED = 90.0D * 90.0D;

    private final Color CYCLE_COLOR = Color.fromRGB(0x00E5FF);
    private final Color FLASH_COLOR = Color.fromRGB(0xFFF176);
    private final Color WEATHER_COLOR = Color.fromRGB(0x4FC3F7);
    private final Color BIOME_COLOR = Color.fromRGB(0x8BC34A);

    public void render(@NonNull Player viewer, @NonNull Level level) {
        LightShowSettings lightShow = level.getLightShow();

        for (LightShowCue cue : lightShow.getSkyCues()) {
            drawLine(viewer, level, cue.getStartMillis(), cue.getSky().getMarkerColor(), true);
            if (cue.getDurationMillis() > 0) {
                drawLine(viewer, level, cue.getEndMillis(), cue.getSky().getMarkerColor(), false);
            }
        }
        for (BossBarCue cue : lightShow.getBossBarCues()) {
            drawLine(viewer, level, cue.getTimeMillis(), cue.getColor().getMarkerColor(), true);
        }
        for (SkyCycleCue cue : lightShow.getSkyCycleCues()) {
            drawLine(viewer, level, cue.getStartMillis(), CYCLE_COLOR, true);
            drawLine(viewer, level, cue.getEndMillis(), CYCLE_COLOR, false);
        }
        for (FlashCue cue : lightShow.getFlashCues()) {
            drawLine(viewer, level, cue.getStartMillis(), FLASH_COLOR, true);
            drawLine(viewer, level, cue.getEndMillis(), FLASH_COLOR, false);
        }
        for (WeatherCue cue : lightShow.getWeatherCues()) {
            drawLine(viewer, level, cue.getTimeMillis(), WEATHER_COLOR, true);
        }
        for (BiomeZone zone : lightShow.getBiomeZones()) {
            drawLine(viewer, level, zone.getStartMillis(), BIOME_COLOR, true);
            drawLine(viewer, level, zone.getEndMillis(), BIOME_COLOR, false);
        }
    }

    private void drawLine(@NonNull Player viewer,
                          @NonNull Level level,
                          int timeMillis,
                          @NonNull Color color,
                          boolean withColumn
    ) {
        double coordinate = LightShowPositions.toCoordinate(level, timeMillis);
        boolean alongX = LightShowPositions.isAlongX(level);

        Cuboid cuboid = level.getCuboid();
        double from = alongX ? cuboid.getMin().getZ() : cuboid.getMin().getX();
        double to = alongX ? cuboid.getMax().getZ() : cuboid.getMax().getX();

        double baseY = level.getLevelSettings().getWorldSettings().getMinWorldHeight();
        Location viewerLocation = viewer.getLocation();

        double viewerCoordinate = alongX ? viewerLocation.getX() : viewerLocation.getZ();
        double coordinateDelta = viewerCoordinate - coordinate;
        if ((coordinateDelta * coordinateDelta) > MAX_RENDER_DISTANCE_SQUARED) return;

        for (double offset = from; offset <= to; offset += STEP_HORIZONTAL) {
            Location location = alongX
                ? new Location(level.getWorld(), coordinate, baseY, offset)
                : new Location(level.getWorld(), offset, baseY, coordinate);
            spawn(viewer, location, color);
        }

        if (!withColumn) return;

        double middle = (from + to) / 2.0D;
        for (double height = STEP; height <= VERTICAL_HEIGHT; height += STEP) {
            Location location = alongX
                ? new Location(level.getWorld(), coordinate, baseY + height, middle)
                : new Location(level.getWorld(), middle, baseY + height, coordinate);
            spawn(viewer, location, color);
        }
    }

    private void spawn(@NonNull Player viewer, @NonNull Location location, @NonNull Color color) {
        viewer.spawnParticle(
            Particle.SPELL_MOB,
            location,
            0,
            color.getRed() / 255.0D,
            color.getGreen() / 255.0D,
            color.getBlue() / 255.0D,
            1.0D
        );
    }
}
