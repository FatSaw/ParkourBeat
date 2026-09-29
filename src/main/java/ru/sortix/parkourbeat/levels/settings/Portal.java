package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;

@Getter
public class Portal {
    public static final double MIN_SIZE = 1.0D;
    public static final double MAX_SIZE = 12.0D;
    public static final double DEFAULT_SIZE = 3.0D;

    public static final double MIN_VIEW_DISTANCE = 4.0D;
    public static final double MAX_VIEW_DISTANCE = 64.0D;
    public static final double DEFAULT_VIEW_DISTANCE = 24.0D;

    private static final double TRIGGER_DEPTH = 1.1D;
    private static final double TRIGGER_MARGIN = 0.4D;

    public enum Facing {
        WALL_X,
        WALL_Z,
        FLOOR;

        @NonNull
        public static Facing byName(@Nullable String name, @NonNull Facing fallback) {
            if (name == null) return fallback;
            try {
                return Facing.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }

        @NonNull
        public Facing next() {
            Facing[] values = Facing.values();
            return values[(this.ordinal() + 1) % values.length];
        }

        @NonNull
        public String getDisplayName() {
            return switch (this) {
                case WALL_X -> "Стена по X";
                case WALL_Z -> "Стена по Z";
                case FLOOR -> "На полу";
            };
        }
    }

    @Getter
    public static class Side {
        private @NonNull Vector position;
        @Setter
        private @NonNull Facing facing;
        @Setter
        private @NonNull Color color;
        private double size = DEFAULT_SIZE;
        private boolean lookSet = false;
        private float yaw = 0f;
        private float pitch = 0f;

        public Side(@NonNull Vector position, @NonNull Facing facing, @NonNull Color color) {
            this.position = position.clone();
            this.facing = facing;
            this.color = color;
        }

        public void setPosition(@NonNull Vector position) {
            this.position = position.clone();
        }

        public void setSize(double size) {
            this.size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
        }

        @NonNull
        public Location toLocation(@NonNull World world) {
            Location location = this.position.toLocation(world);
            if (this.lookSet) {
                location.setYaw(this.yaw);
                location.setPitch(this.pitch);
            }
            return location;
        }

        public void setLook(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.lookSet = true;
        }

        public void clearLook() {
            this.lookSet = false;
        }

        @NonNull
        public String formatLook() {
            if (!this.lookSet) return "как у игрока";
            return String.format(java.util.Locale.ROOT, "%.0f / %.0f", this.yaw, this.pitch);
        }

        @NonNull
        public String getColorHex() {
            return String.format("%06X", this.color.asRGB());
        }

        @NonNull
        public String format() {
            return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f",
                this.position.getX(), this.position.getY(), this.position.getZ());
        }

        /**
         * Рамка намеренно чуть толще визуальной: игрок на бегу проходит
         * больше блока за тик, тонкую плоскость он бы просто перепрыгнул.
         */
        public boolean contains(double x, double y, double z) {
            double half = (this.size / 2.0D) + TRIGGER_MARGIN;
            double dx = Math.abs(x - this.position.getX());
            double dy = Math.abs(y - this.position.getY());
            double dz = Math.abs(z - this.position.getZ());

            return switch (this.facing) {
                case WALL_X -> dx <= TRIGGER_DEPTH && dy <= half && dz <= half;
                case WALL_Z -> dz <= TRIGGER_DEPTH && dy <= half && dx <= half;
                case FLOOR -> dy <= TRIGGER_DEPTH && dx <= half && dz <= half;
            };
        }

        @NonNull
        public Side copy() {
            Side copy = new Side(this.position, this.facing, this.color);
            copy.size = this.size;
            copy.lookSet = this.lookSet;
            copy.yaw = this.yaw;
            copy.pitch = this.pitch;
            return copy;
        }

        @NonNull
        public String serialize() {
            return this.position.getX() + "/" + this.position.getY() + "/" + this.position.getZ()
                + "/" + this.facing.name() + "/" + this.color.asRGB() + "/" + this.size
                + "/" + this.lookSet + "/" + this.yaw + "/" + this.pitch;
        }

        @Nullable
        public static Side deserialize(@Nullable String input) {
            if (input == null) return null;
            String[] args = input.split("/");
            if (args.length < 5) return null;
            try {
                Side side = new Side(
                    new Vector(Double.parseDouble(args[0]), Double.parseDouble(args[1]),
                        Double.parseDouble(args[2])),
                    Facing.byName(args[3], Facing.WALL_X),
                    Color.fromRGB(Integer.parseInt(args[4])));
                if (args.length >= 6) side.setSize(Double.parseDouble(args[5]));
                if (args.length >= 9 && Boolean.parseBoolean(args[6])) {
                    side.setLook(Float.parseFloat(args[7]), Float.parseFloat(args[8]));
                }
                return side;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private final @NonNull Side entry;
    private final @NonNull Side exit;
    private double viewDistance = DEFAULT_VIEW_DISTANCE;
    @Setter
    private boolean enabled = true;

    public Portal(@NonNull Side entry, @NonNull Side exit) {
        this.entry = entry;
        this.exit = exit;
    }

    public void setViewDistance(double viewDistance) {
        this.viewDistance = Math.max(MIN_VIEW_DISTANCE, Math.min(MAX_VIEW_DISTANCE, viewDistance));
    }

    @NonNull
    public Portal copy() {
        Portal copy = new Portal(this.entry.copy(), this.exit.copy());
        copy.viewDistance = this.viewDistance;
        copy.enabled = this.enabled;
        return copy;
    }

    @NonNull
    public String serialize() {
        return this.entry.serialize() + " " + this.exit.serialize()
            + " " + this.viewDistance + " " + this.enabled;
    }

    @Nullable
    public static Portal deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] args = input.trim().split(" ");
        if (args.length < 2) return null;

        Side entry = Side.deserialize(args[0]);
        Side exit = Side.deserialize(args[1]);
        if (entry == null || exit == null) return null;

        Portal portal = new Portal(entry, exit);
        try {
            if (args.length >= 3) portal.setViewDistance(Double.parseDouble(args[2]));
            if (args.length >= 4) portal.enabled = Boolean.parseBoolean(args[3]);
        } catch (NumberFormatException ignored) {
        }
        return portal;
    }
}
