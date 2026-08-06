package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import javax.annotation.Nullable;

@Getter
@RequiredArgsConstructor
public enum LightShowSharpness {
    /**
     * The sky switches to the cue value in one frame, right at the timecode.
     */
    SHARP(LangOptions.lightshow_sharpness_sharp),
    /**
     * The sky slides to the cue value across the span between the start and the end
     * timecode of the cue.
     */
    SMOOTH(LangOptions.lightshow_sharpness_smooth);

    public static final LightShowSharpness DEFAULT = SMOOTH;

    private final @NonNull LangOptions displayName;

    @NonNull
    public Component getDisplayName(@Nullable String locale) {
        return this.displayName.getComponent(locale == null ? "" : locale);
    }

    @NonNull
    public String getDisplayNameString(@Nullable String locale) {
        String value = this.displayName.get(locale == null ? "" : locale);
        return value == null ? this.name() : value;
    }

    @NonNull
    public LightShowSharpness next() {
        LightShowSharpness[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    @NonNull
    public static LightShowSharpness byName(@Nullable String name, @NonNull LightShowSharpness fallback) {
        if (name == null) return fallback;
        try {
            return LightShowSharpness.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
