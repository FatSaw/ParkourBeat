package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import ru.sortix.parkourbeat.utils.TimeUtils;

import javax.annotation.Nullable;

/**
 * Holds the player on night vision II while it runs. The client blinks the effect once less
 * than ten seconds are left on it, and that blink is the flash.
 */
@Getter
public class FlashCue implements LightShowElement {
    public static final int DEFAULT_DURATION_MILLIS = 8_000;

    private int startMillis;
    private int endMillis;
    @Setter
    private @NonNull FlashSpeed speed;

    public FlashCue(int startMillis, int endMillis, @NonNull FlashSpeed speed) {
        this.startMillis = clamp(startMillis);
        this.endMillis = Math.max(this.startMillis, clamp(endMillis));
        this.speed = speed;
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

    public boolean isActive(long songTimeMillis) {
        return songTimeMillis >= this.startMillis && songTimeMillis < this.endMillis;
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
    public FlashCue copy() {
        return new FlashCue(this.startMillis, this.endMillis, this.speed);
    }

    @NonNull
    public String serialize() {
        return this.startMillis + " " + this.endMillis + " " + this.speed.name();
    }

    @Nullable
    public static FlashCue deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] args = input.trim().split(" ");
        if (args.length != 3) return null;
        try {
            return new FlashCue(
                Integer.parseInt(args[0]),
                Integer.parseInt(args[1]),
                FlashSpeed.byName(args[2], FlashSpeed.DEFAULT));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
