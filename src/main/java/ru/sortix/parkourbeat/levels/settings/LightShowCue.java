package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import ru.sortix.parkourbeat.utils.TimeUtils;

import javax.annotation.Nullable;

/**
 * A sky change bound to a piece of the song: the transition starts at {@link #startMillis}
 * and is fully applied at {@link #endMillis}.
 */
@Getter
public class LightShowCue implements LightShowElement {
    public static final int DEFAULT_DURATION_MILLIS = 2_000;

    @Setter
    private @NonNull SkyType sky;
    @Setter
    private @NonNull LightShowSharpness sharpness;

    private int startMillis;
    private int endMillis;

    public LightShowCue(int startMillis,
                        int endMillis,
                        @NonNull SkyType sky,
                        @NonNull LightShowSharpness sharpness
    ) {
        this.sky = sky;
        this.sharpness = sharpness;
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

    @NonNull
    @Override
    public String getTimecode() {
        return this.getStartTimecode();
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

    public int getDurationMillis() {
        return this.endMillis - this.startMillis;
    }

    @NonNull
    public String getStartTimecode() {
        return TimeUtils.formatTimecode(this.startMillis);
    }

    @NonNull
    public String getEndTimecode() {
        return TimeUtils.formatTimecode(this.endMillis);
    }

    /**
     * @return 0.0 before the cue, 1.0 once it is fully applied
     */
    public double getProgress(long songTimeMillis) {
        if (songTimeMillis <= this.startMillis) return 0.0D;
        if (this.sharpness == LightShowSharpness.SHARP) return 1.0D;
        int duration = this.getDurationMillis();
        if (duration <= 0) return 1.0D;
        if (songTimeMillis >= this.endMillis) return 1.0D;
        return (double) (songTimeMillis - this.startMillis) / (double) duration;
    }

    @NonNull
    public LightShowCue copy() {
        return new LightShowCue(this.startMillis, this.endMillis, this.sky, this.sharpness);
    }

    @NonNull
    public String serialize() {
        return this.startMillis + " " + this.endMillis + " " + this.sky.name() + " " + this.sharpness.name();
    }

    @Nullable
    public static LightShowCue deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] args = input.trim().split(" ");
        if (args.length != 4) return null;

        int startMillis;
        int endMillis;
        try {
            startMillis = Integer.parseInt(args[0]);
            endMillis = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            return null;
        }

        return new LightShowCue(
            startMillis,
            endMillis,
            SkyType.byName(args[2], SkyType.DEFAULT),
            LightShowSharpness.byName(args[3], LightShowSharpness.DEFAULT)
        );
    }
}
