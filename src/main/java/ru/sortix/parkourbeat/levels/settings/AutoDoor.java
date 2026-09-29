package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;

/**
 * Дверь, которая сама открывается, когда игрок подходит ближе радиуса, и закрывается,
 * когда все отошли. Привязка идёт к координатам блока, а не к его типу: строитель может
 * заменить дуб на железную дверь или калитку, и настройка продолжит работать.
 */
@Getter
public class AutoDoor {
    public static final double MIN_RADIUS = 1.0D;
    public static final double MAX_RADIUS = 32.0D;
    public static final double DEFAULT_RADIUS = 4.0D;
    public static final double RADIUS_STEP = 0.5D;

    private int blockX;
    private int blockY;
    private int blockZ;

    private double radius = DEFAULT_RADIUS;
    @Setter
    private boolean enabled = true;
    /** Дверь стоит открытой и захлопывается, когда игрок подходит. */
    @Setter
    private boolean inverted = false;
    /** Звук открытия и закрытия слышно всем на уровне. */
    @Setter
    private boolean playSound = true;

    public AutoDoor(int blockX, int blockY, int blockZ) {
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
    }

    public void setPosition(int blockX, int blockY, int blockZ) {
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
    }

    public void setRadius(double radius) {
        this.radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
    }

    public boolean isSameBlock(int x, int y, int z) {
        return this.blockX == x && this.blockY == y && this.blockZ == z;
    }

    /**
     * Центр блока, а не его угол: иначе радиус считался бы от края и дверь открывалась
     * с одной стороны заметно раньше, чем с другой.
     */
    @NonNull
    public Location getCenter(@NonNull World world) {
        return new Location(world, this.blockX + 0.5D, this.blockY, this.blockZ + 0.5D);
    }

    @NonNull
    public Vector getCenterVector() {
        return new Vector(this.blockX + 0.5D, this.blockY, this.blockZ + 0.5D);
    }

    /**
     * По горизонтали. Высоту намеренно не учитываем: дверь на мосту не должна открываться
     * от игрока, пробегающего двадцатью блоками выше.
     */
    public boolean isInRadius(double x, double y, double z) {
        double dy = Math.abs(y - this.blockY);
        if (dy > 6.0D) return false;

        double dx = x - (this.blockX + 0.5D);
        double dz = z - (this.blockZ + 0.5D);
        return (dx * dx + dz * dz) <= this.radius * this.radius;
    }

    @NonNull
    public String format() {
        return this.blockX + " " + this.blockY + " " + this.blockZ;
    }

    @NonNull
    public String formatRadius() {
        return String.format(java.util.Locale.ROOT, "%.1f", this.radius);
    }

    @NonNull
    public AutoDoor copy() {
        AutoDoor copy = new AutoDoor(this.blockX, this.blockY, this.blockZ);
        copy.radius = this.radius;
        copy.enabled = this.enabled;
        copy.inverted = this.inverted;
        copy.playSound = this.playSound;
        return copy;
    }

    @NonNull
    public String serialize() {
        return this.blockX + " " + this.blockY + " " + this.blockZ
            + " " + this.radius + " " + this.enabled + " " + this.inverted + " " + this.playSound;
    }

    @Nullable
    public static AutoDoor deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] args = input.trim().split(" ");
        if (args.length < 3) return null;
        try {
            AutoDoor door = new AutoDoor(
                Integer.parseInt(args[0]),
                Integer.parseInt(args[1]),
                Integer.parseInt(args[2]));
            if (args.length >= 4) door.setRadius(Double.parseDouble(args[3]));
            if (args.length >= 5) door.enabled = Boolean.parseBoolean(args[4]);
            if (args.length >= 6) door.inverted = Boolean.parseBoolean(args[5]);
            if (args.length >= 7) door.playSound = Boolean.parseBoolean(args[6]);
            return door;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
