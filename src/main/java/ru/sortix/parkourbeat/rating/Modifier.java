package ru.sortix.parkourbeat.rating;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;

import javax.annotation.Nullable;

/**
 * A gameplay modifier the player can toggle before a run, in the spirit of BeatSaber /
 * osu! / Geometry Dash. Each one changes how the level plays and applies a score
 * multiplier to the final tally.
 * <p>
 * The short two-letter code is what is written under the item in the selection menu
 * and (for {@link #PRACTICE}) appended to the scoreboard accuracy line.
 */
@Getter
@RequiredArgsConstructor
public enum Modifier {
    /**
     * PRACTICE — free-play. No score is tallied. Each fall rewinds the player to the
     * previous jump instead of ending the run, and the music keeps playing (it is not
     * stopped or stretched). You can only actually lose if accuracy drops below 45%.
     * While active, the scoreboard accuracy line gets " &f| PC" appended.
     */
    PRACTICE("PC", "PRACTICE", "&b", Material.EMERALD, 0.0D),

    /**
     * PERFECT — only a +300 (perfect) jump keeps the run alive; anything else is an
     * instant loss.
     */
    PERFECT("PF", "PERFECT", "&e", Material.DIAMOND, 1.5D),

    /**
     * HIDDEN — the particle path within ~3 blocks in front of the player fades out
     * smoothly but quickly, osu!-hidden style.
     */
    HIDDEN("ES", "HIDDEN", "&a", Material.SNOWBALL, 1.2D),

    /**
     * HARD — double HP drain, and the run is lost the moment combo reaches +50.
     */
    HARD("SD", "HARD", "&c", Material.FIRE_CHARGE, 2.0D),

    /**
     * HIGH RISK — you get exactly half a heart for the whole level.
     */
    HIGH_RISK("HR", "HIGH RISK", "&4", Material.COOKED_BEEF, 1.4D);

    /**
     * Two-letter code (PC / PF / ES / SD / HR).
     */
    private final @NonNull String code;

    /**
     * Long display name (PRACTICE / PERFECT / HIDDEN / HARD / HIGH RISK).
     */
    private final @NonNull String displayName;

    /**
     * Legacy-ampersand color prefix used for the code label.
     */
    private final @NonNull String colorPrefix;

    /**
     * Material shown for this modifier in the selection menu, matching the reference
     * screenshot as closely as the vanilla palette allows.
     */
    private final @NonNull Material icon;

    /**
     * Final-score multiplier applied when this modifier is active.
     */
    private final double scoreMultiplier;

    @Nullable
    public static Modifier byCode(@Nullable String code) {
        if (code == null) return null;
        for (Modifier modifier : values()) {
            if (modifier.code.equalsIgnoreCase(code.trim())) return modifier;
        }
        return null;
    }

    @Nullable
    public static Modifier byName(@Nullable String name) {
        if (name == null) return null;
        try {
            return Modifier.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @NonNull
    public String getColoredCode() {
        return this.colorPrefix + "&l" + this.code;
    }
}
