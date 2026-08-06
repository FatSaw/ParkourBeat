package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Color;
import ru.sortix.parkourbeat.utils.TimeUtils;

import javax.annotation.Nullable;

/**
 * Overrides the path particle color across a timecode range. Named by the same timecodes
 * as every other cue. The color is applied per-player at render time, nothing is baked in.
 */
@Getter
public class ParticleColorCue implements LightShowElement {
    public static final int DEFAULT_LENGTH_MILLIS = 5_000;
    public static final int DEFAULT_COLOR = 0x00FF00;

    /** How the jump-indicator particles are coloured relative to the path colour. */
    public enum JumpColorMode {
        SAME,      // same as the path colour in this cue
        INVERTED,  // inverted path colour (default)
        CUSTOM;    // explicit hex in jumpColor

        @NonNull
        public static JumpColorMode byName(@Nullable String name, @NonNull JumpColorMode fallback) {
            if (name == null) return fallback;
            try {
                return JumpColorMode.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }
    }

    @Setter
    private int color;
    @Setter
    private @NonNull JumpColorMode jumpColorMode = JumpColorMode.INVERTED;
    @Setter
    private int jumpColor = 0xFF00FF;

    private int startMillis;
    private int endMillis;

    public ParticleColorCue(int startMillis, int endMillis, int color) {
        this.startMillis = clamp(startMillis);
        this.endMillis = Math.max(this.startMillis, clamp(endMillis));
        this.color = color & 0xFFFFFF;
    }

    private static int clamp(int millis) {
        return Math.max(0, Math.min(TimeUtils.MAX_TIMECODE_MILLIS, millis));
    }

    @NonNull
    public Color toBukkitColor() {
        return Color.fromRGB(this.color & 0xFFFFFF);
    }

    public boolean contains(long songTimeMillis) {
        return songTimeMillis >= this.startMillis && songTimeMillis <= this.endMillis;
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

    @NonNull
    public String getHexColor() {
        return String.format("#%06X", this.color & 0xFFFFFF);
    }

    @NonNull
    public ParticleColorCue copy() {
        ParticleColorCue c = new ParticleColorCue(this.startMillis, this.endMillis, this.color);
        c.jumpColorMode = this.jumpColorMode;
        c.jumpColor = this.jumpColor;
        return c;
    }

    @NonNull
    public String serialize() {
        return this.startMillis + " " + this.endMillis + " " + (this.color & 0xFFFFFF)
            + " " + this.jumpColorMode.name() + " " + (this.jumpColor & 0xFFFFFF);
    }

    @Nullable
    public static ParticleColorCue deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] args = input.trim().split(" ");
        if (args.length < 3) return null;
        try {
            int start = Integer.parseInt(args[0]);
            int end = Integer.parseInt(args[1]);
            int color = Integer.parseInt(args[2]);
            ParticleColorCue cue = new ParticleColorCue(start, end, color);
            if (args.length >= 5) {
                cue.jumpColorMode = JumpColorMode.byName(args[3], JumpColorMode.INVERTED);
                cue.jumpColor = Integer.parseInt(args[4]) & 0xFFFFFF;
            }
            return cue;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
