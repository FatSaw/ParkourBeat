package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import ru.sortix.parkourbeat.utils.TimeUtils;

import javax.annotation.Nullable;

/**
 * A stretch of the level, named by the same timecodes everything else uses, whose biome is
 * rewritten in the world. Unlike the other elements this one is baked into the blocks rather
 * than played back, so it is the same for everybody standing there.
 */
@Getter
public class BiomeZone implements LightShowElement {
    public static final int DEFAULT_LENGTH_MILLIS = 5_000;

    @Setter
    private @NonNull LevelBiome biome;
    /**
     * A cold biome only shows snow while something is falling from the sky, so a snowy zone
     * has to bring its own rain along.
     */
    @Setter
    private boolean forceRain;
    /**
     * Biome fog and water take their saturation from the light of the sky, so day and night
     * look noticeably different inside the same biome.
     */
    @Setter
    private @NonNull ZoneSkyTime skyTime;

    private int startMillis;
    private int endMillis;

    public BiomeZone(int startMillis,
                     int endMillis,
                     @NonNull LevelBiome biome,
                     boolean forceRain,
                     @NonNull ZoneSkyTime skyTime
    ) {
        this.biome = biome;
        this.forceRain = forceRain;
        this.skyTime = skyTime;
        this.startMillis = clamp(startMillis);
        this.endMillis = Math.max(this.startMillis, clamp(endMillis));
    }

    private static int clamp(int millis) {
        return Math.max(0, Math.min(TimeUtils.MAX_TIMECODE_MILLIS, millis));
    }

    @Override
    public boolean hasEnd() {
        return true;
    }

    @Override
    public void setStartMillis(int startMillis) {
        this.startMillis = clamp(startMillis);
        if (this.endMillis < this.startMillis) this.endMillis = this.startMillis;
    }

    @Override
    public void setEndMillis(int endMillis) {
        this.endMillis = Math.max(this.startMillis, clamp(endMillis));
    }

    @NonNull
    @Override
    public String getTimecode() {
        return TimeUtils.formatTimecode(this.startMillis);
    }

    @NonNull
    public String getStartTimecode() {
        return TimeUtils.formatTimecode(this.startMillis);
    }

    @NonNull
    public String getEndTimecode() {
        return TimeUtils.formatTimecode(this.endMillis);
    }

    public boolean contains(long songTimeMillis) {
        return songTimeMillis >= this.startMillis && songTimeMillis <= this.endMillis;
    }

    @NonNull
    public BiomeZone copy() {
        return new BiomeZone(this.startMillis, this.endMillis, this.biome, this.forceRain, this.skyTime);
    }

    @NonNull
    public String serialize() {
        return this.startMillis + " " + this.endMillis + " " + this.biome.name()
            + " " + this.forceRain + " " + this.skyTime.name();
    }

    @Nullable
    public static BiomeZone deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] args = input.trim().split(" ");
        if (args.length < 3) return null;
        try {
            return new BiomeZone(
                Integer.parseInt(args[0]),
                Integer.parseInt(args[1]),
                LevelBiome.byName(args[2], LevelBiome.DEFAULT),
                args.length <= 3 || Boolean.parseBoolean(args[3]),
                args.length > 4
                    ? ZoneSkyTime.byName(args[4], ZoneSkyTime.DEFAULT)
                    : ZoneSkyTime.DEFAULT);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
