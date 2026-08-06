package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import ru.sortix.parkourbeat.utils.TimeUtils;

import javax.annotation.Nullable;

/**
 * Sweeps the sky between 11000 and 18000 and back, over and over, for as long as it runs.
 * The sweep is recomputed on every server tick, which is the finest step the client can be
 * fed, so the value never jumps.
 */
@Getter
public class SkyCycleCue implements LightShowElement {
    public static final long LOW_TIME = 11000L;
    public static final long HIGH_TIME = 18000L;

    public static final int MIN_CYCLE_MILLIS = 200;
    public static final int MAX_CYCLE_MILLIS = 6_000;
    public static final int DEFAULT_CYCLE_MILLIS = 3_000;

    private int startMillis;
    private int endMillis;
    private int cycleMillis;

    public SkyCycleCue(int startMillis, int endMillis, int cycleMillis) {
        this.startMillis = clamp(startMillis);
        this.endMillis = Math.max(this.startMillis, clamp(endMillis));
        this.setCycleMillis(cycleMillis);
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

    public void setCycleMillis(int cycleMillis) {
        this.cycleMillis = Math.max(MIN_CYCLE_MILLIS, Math.min(MAX_CYCLE_MILLIS, cycleMillis));
    }

    public boolean isActive(long songTimeMillis) {
        return songTimeMillis >= this.startMillis && songTimeMillis < this.endMillis;
    }

    /**
     * @return sky time for this moment, ping ponging between the two ends
     */
    public long getSkyTime(long songTimeMillis) {
        long elapsed = songTimeMillis - this.startMillis;
        if (elapsed < 0L) elapsed = 0L;

        long full = this.cycleMillis * 2L;
        long phase = elapsed % full;

        double progress = phase < this.cycleMillis
            ? (double) phase / (double) this.cycleMillis
            : 1.0D - ((double) (phase - this.cycleMillis) / (double) this.cycleMillis);

        return LOW_TIME + Math.round((HIGH_TIME - LOW_TIME) * progress);
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

    @NonNull
    public SkyCycleCue copy() {
        return new SkyCycleCue(this.startMillis, this.endMillis, this.cycleMillis);
    }

    @NonNull
    public String serialize() {
        return this.startMillis + " " + this.endMillis + " " + this.cycleMillis;
    }

    @Nullable
    public static SkyCycleCue deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] args = input.trim().split(" ");
        if (args.length != 3) return null;
        try {
            return new SkyCycleCue(
                Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
