package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import javax.annotation.Nullable;

/**
 * Night vision starts blinking on the client once less than ten seconds are left on it.
 * A flash keeps the player inside that window on purpose.
 */
@Getter
@RequiredArgsConstructor
public enum FlashSpeed {
    /**
     * One eight second application, renewed as soon as it runs out.
     */
    X1(160, 1, LangOptions.lightshow_flashspeed_x1),
    /**
     * One second applications, twice every tick.
     */
    X2(20, 2, LangOptions.lightshow_flashspeed_x2),
    /**
     * One second applications, five times every tick.
     */
    X3(20, 5, LangOptions.lightshow_flashspeed_x3);

    public static final FlashSpeed DEFAULT = X1;

    private final int durationTicks;
    private final int applicationsPerTick;
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
    public FlashSpeed next() {
        FlashSpeed[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    @NonNull
    public static FlashSpeed byName(@Nullable String name, @NonNull FlashSpeed fallback) {
        if (name == null) return fallback;
        try {
            return FlashSpeed.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
