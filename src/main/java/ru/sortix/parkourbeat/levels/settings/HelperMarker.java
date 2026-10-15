package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;

@Getter
public class HelperMarker {
    public enum Kind {
        LEFT,
        RIGHT
    }

    private final @NonNull Vector position;
    private final @NonNull Kind kind;

    public HelperMarker(@NonNull Vector position, @NonNull Kind kind) {
        this.position = position.clone();
        this.kind = kind;
    }

    @NonNull
    public HelperMarker copy() {
        return new HelperMarker(this.position, this.kind);
    }

    @NonNull
    public String format() {
        return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f",
            this.position.getX(), this.position.getY(), this.position.getZ());
    }

    @NonNull
    public String serialize() {
        return this.position.getX() + " " + this.position.getY() + " " + this.position.getZ()
            + " " + this.kind.name();
    }

    @Nullable
    public static HelperMarker deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] args = input.trim().split(" ");
        if (args.length < 3) return null;
        try {
            Vector position = new Vector(
                Double.parseDouble(args[0]),
                Double.parseDouble(args[1]),
                Double.parseDouble(args[2]));

            Kind kind = Kind.LEFT;
            if (args.length >= 4) {
                try {
                    kind = Kind.valueOf(args[3]);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return new HelperMarker(position, kind);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
