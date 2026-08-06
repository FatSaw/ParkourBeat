package ru.sortix.parkourbeat.levels;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Color;
import org.bukkit.Location;

import javax.annotation.Nullable;

@Getter
public class Waypoint {
    private final Color color;
    @Setter
    private Location location;
    @Setter
    private double height;
    /**
     * Colour of the jump-trigger particle ring at this waypoint. When null, the ring uses
     * the inverted path colour (the historical default).
     */
    @Setter
    private @Nullable Color jumpColor;

    public Waypoint(@NonNull Location location, double height, @NonNull Color color) {
        this(location, height, color, null);
    }

    public Waypoint(@NonNull Location location, double height, @NonNull Color color, @Nullable Color jumpColor) {
        this.location = location;
        this.color = color;
        this.height = height;
        this.jumpColor = jumpColor;
    }
}
