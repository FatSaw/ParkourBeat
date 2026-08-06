package ru.sortix.parkourbeat.rating;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum JumpResult {
    PERFECT(300, "&b"),
    GOOD(100, "&e"),
    OK(50, "&c"),
    MISS(0, "&7");

    /**
     * Raw base points before the combo multiplier.
     */
    private final int basePoints;

    /**
     * Legacy-ampersand color prefix for the "+300 / +100 / +50" sub-title.
     */
    private final @NonNull String colorPrefix;

    public boolean isHit() {
        return this != MISS;
    }

    /**
     * The signed accuracy nudge (in "accuracy identifier" terms, mapped later to a
     * small percentage delta by the accuracy model).
     */
    public double getAccuracyDelta() {
        switch (this) {
            case PERFECT: return +1.0D;
            case GOOD:    return -0.5D;
            case OK:      return -1.0D;
            case MISS:
            default:      return -4.0D;
        }
    }

    @NonNull
    public String formatPoints() {
        return this.colorPrefix + "+" + this.basePoints;
    }
}
