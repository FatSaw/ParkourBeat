package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import ru.sortix.parkourbeat.utils.TimeUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A stretch of the level, named by the same timecodes as everything else, where each player
 * jump fires one or more client-side effects. The effects are chosen either in order
 * (SEQUENTIAL) or at random (RANDOM) from the configured list.
 */
@Getter
public class JumpZone implements LightShowElement {
    public static final int DEFAULT_LENGTH_MILLIS = 5_000;

    public enum Mode {
        SEQUENTIAL,
        RANDOM;

        @NonNull
        public static Mode byName(@Nullable String name, @NonNull Mode fallback) {
            if (name == null) return fallback;
            try {
                return Mode.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }
    }

    private final List<JumpEffect> effects = new ArrayList<>();
    @Setter
    private @NonNull Mode mode;
    @Setter
    private @NonNull String soundKey = "block.note_block.hat@0.4";

    private int startMillis;
    private int endMillis;

    private transient int sequentialIndex = 0;

    public JumpZone(int startMillis, int endMillis, @NonNull List<JumpEffect> effects, @NonNull Mode mode) {
        this.effects.addAll(effects);
        this.mode = mode;
        this.startMillis = clamp(startMillis);
        this.endMillis = Math.max(this.startMillis, clamp(endMillis));
    }

    private static int clamp(int millis) {
        return Math.max(0, Math.min(TimeUtils.MAX_TIMECODE_MILLIS, millis));
    }

    public void addEffect(@NonNull JumpEffect effect) {
        this.effects.add(effect);
    }

    public void removeEffect(@NonNull JumpEffect effect) {
        this.effects.remove(effect);
    }

    public void clearEffects() {
        this.effects.clear();
    }

    /**
     * Returns the next effect to play according to the mode, or null when the list is empty.
     */
    @Nullable
    public JumpEffect nextEffect(@NonNull java.util.Random random) {
        // SOUND is handled separately (always plays), so it never participates in the rotation.
        java.util.List<JumpEffect> pool = new ArrayList<>();
        for (JumpEffect e : this.effects) {
            if (e != JumpEffect.SOUND) pool.add(e);
        }
        if (pool.isEmpty()) return null;
        if (this.mode == Mode.RANDOM) {
            return pool.get(random.nextInt(pool.size()));
        }
        JumpEffect effect = pool.get(Math.floorMod(this.sequentialIndex, pool.size()));
        this.sequentialIndex++;
        return effect;
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
    public JumpZone copy() {
        JumpZone c = new JumpZone(this.startMillis, this.endMillis, this.effects, this.mode);
        c.soundKey = this.soundKey;
        return c;
    }

    @NonNull
    public String serialize() {
        StringBuilder effectsPart = new StringBuilder();
        for (int i = 0; i < this.effects.size(); i++) {
            if (i > 0) effectsPart.append(",");
            effectsPart.append(this.effects.get(i).name());
        }
        if (effectsPart.length() == 0) effectsPart.append("NONE");
        return this.startMillis + " " + this.endMillis + " " + this.mode.name() + " " + effectsPart
            + " " + this.soundKey;
    }

    @Nullable
    public static JumpZone deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] args = input.trim().split(" ");
        if (args.length < 4) return null;
        try {
            int start = Integer.parseInt(args[0]);
            int end = Integer.parseInt(args[1]);
            Mode mode = Mode.byName(args[2], Mode.SEQUENTIAL);
            List<JumpEffect> effects = new ArrayList<>();
            if (!args[3].equalsIgnoreCase("NONE")) {
                for (String part : args[3].split(",")) {
                    if (part.isEmpty()) continue;
                    effects.add(JumpEffect.byName(part, JumpEffect.SOUND));
                }
            }
            JumpZone zone = new JumpZone(start, end, effects, mode);
            if (args.length >= 5 && !args[4].isEmpty()) {
                zone.setSoundKey(args[4]);
            }
            return zone;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
