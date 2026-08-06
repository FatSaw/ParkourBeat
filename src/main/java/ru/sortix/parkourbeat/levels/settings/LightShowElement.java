package ru.sortix.parkourbeat.levels.settings;

import lombok.NonNull;

/**
 * Anything on the lightshow timeline that the wand can point at. Point elements report the
 * same value for the start and the end and ignore attempts to move the end.
 */
public interface LightShowElement {
    int getStartMillis();

    void setStartMillis(int startMillis);

    default boolean hasEnd() {
        return false;
    }

    default int getEndMillis() {
        return this.getStartMillis();
    }

    default void setEndMillis(int endMillis) {
    }

    @NonNull
    String getTimecode();
}
