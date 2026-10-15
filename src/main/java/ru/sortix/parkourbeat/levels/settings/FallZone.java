package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import ru.sortix.parkourbeat.utils.TimeUtils;

import javax.annotation.Nullable;

@Getter
public class FallZone implements LightShowElement {
    public static final int DEFAULT_LENGTH_MILLIS = 5_000;
    public static final int MIN_DEATH_Y = -64;
    public static final int MAX_DEATH_Y = 320;

    private int startMillis;
    private int endMillis;
    @Setter
    private int deathY;
    @Setter
    private boolean enabled = true;

    public FallZone(int startMillis, int endMillis, int deathY) {
        this.startMillis = clampTime(startMillis);
        this.endMillis = Math.max(this.startMillis, clampTime(endMillis));
        this.deathY = clampY(deathY);
    }

    private static int clampTime(int millis) {
        return Math.max(0, Math.min(TimeUtils.MAX_TIMECODE_MILLIS, millis));
    }

    private static int clampY(int y) {
        return Math.max(MIN_DEATH_Y, Math.min(MAX_DEATH_Y, y));
    }

    @Override
    public boolean hasEnd() {
        return true;
    }

    @Override
    public void setStartMillis(int startMillis) {
        this.startMillis = clampTime(startMillis);
        if (this.endMillis < this.startMillis) this.endMillis = this.startMillis;
    }

    @Override
    public void setEndMillis(int endMillis) {
        this.endMillis = Math.max(this.startMillis, clampTime(endMillis));
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
    public FallZone copy() {
        FallZone copy = new FallZone(this.startMillis, this.endMillis, this.deathY);
        copy.enabled = this.enabled;
        return copy;
    }

    @NonNull
    public String serialize() {
        return this.startMillis + " " + this.endMillis + " " + this.deathY + " " + this.enabled;
    }

    @Nullable
    public static FallZone deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] args = input.trim().split(" ");
        if (args.length < 3) return null;
        try {
            FallZone zone = new FallZone(
                Integer.parseInt(args[0]),
                Integer.parseInt(args[1]),
                Integer.parseInt(args[2]));
            if (args.length >= 4) zone.enabled = Boolean.parseBoolean(args[3]);
            return zone;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
