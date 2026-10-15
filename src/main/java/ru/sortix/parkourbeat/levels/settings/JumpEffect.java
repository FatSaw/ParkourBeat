package ru.sortix.parkourbeat.levels.settings;

import lombok.NonNull;

import javax.annotation.Nullable;

public enum JumpEffect {
    TIME_PUSH,
    JUMP_AIR,
    JUMP_FIRE,
    JUMP_SWEEP,
    JUMP_BUBBLE,
    JUMP_RED_SCREEN,
    SOUND;

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
