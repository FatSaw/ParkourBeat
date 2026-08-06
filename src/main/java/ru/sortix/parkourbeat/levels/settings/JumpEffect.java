package ru.sortix.parkourbeat.levels.settings;

import lombok.NonNull;

import javax.annotation.Nullable;

/**
 * A single client-side effect fired when a player jumps inside a jump zone.
 */
public enum JumpEffect {
    TIME_PUSH,    // "Толкание времени" — smooth +300 time each jump
    JUMP_AIR,     // "Воздушный" — cloud particles
    JUMP_FIRE,    // "Огненный" — flame particles
    SOUND;        // opens the sound picker; the actual sound is stored on the zone

    @NonNull
    public static JumpEffect byName(@Nullable String name, @NonNull JumpEffect fallback) {
        if (name == null) return fallback;
        try {
            return JumpEffect.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
