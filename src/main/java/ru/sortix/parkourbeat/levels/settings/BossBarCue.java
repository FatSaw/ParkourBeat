package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import ru.sortix.parkourbeat.utils.TimeUtils;

import javax.annotation.Nullable;

/**
 * A boss bar colour change bound to a moment of the song. Boss bar colours are a fixed
 * client side set, so there is nothing to interpolate: the change is applied at the timecode.
 */
@Getter
public class BossBarCue implements LightShowElement {
    @Setter
    private @NonNull LevelBossBarColor color;

    private int timeMillis;

    public BossBarCue(int timeMillis, @NonNull LevelBossBarColor color) {
        this.color = color;
        this.setTimeMillis(timeMillis);
    }

    @Override
    public int getStartMillis() {
        return this.timeMillis;
    }

    @Override
    public void setStartMillis(int startMillis) {
        this.setTimeMillis(startMillis);
    }

    public void setTimeMillis(int timeMillis) {
        this.timeMillis = Math.max(0, Math.min(TimeUtils.MAX_TIMECODE_MILLIS, timeMillis));
    }

    @NonNull
    @Override
    public String getTimecode() {
        return TimeUtils.formatTimecode(this.timeMillis);
    }

    @NonNull
    public BossBarCue copy() {
        return new BossBarCue(this.timeMillis, this.color);
    }

    @NonNull
    public String serialize() {
        return this.timeMillis + " " + this.color.name();
    }

    @Nullable
    public static BossBarCue deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] args = input.trim().split(" ");
        if (args.length != 2) return null;

        int timeMillis;
        try {
            timeMillis = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            return null;
        }

        return new BossBarCue(timeMillis, LevelBossBarColor.byName(args[1], LevelBossBarColor.DEFAULT));
    }
}
