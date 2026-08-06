package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import javax.annotation.Nullable;

@Getter
public class GlowingBarrier {
    private final int x;
    private final int y;
    private final int z;
    @Setter
    private @NonNull GlowColor color;
    @Setter
    private @NonNull GlowMode mode;
    @Setter
    private float peek;
    @Setter
    private @NonNull GlowExtension extension;

    public GlowingBarrier(int x, int y, int z,
                          @NonNull GlowColor color,
                          @NonNull GlowMode mode,
                          float peek,
                          @NonNull GlowExtension extension
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
        this.mode = mode;
        this.peek = peek;
        this.extension = extension;
    }

    public GlowingBarrier(int x, int y, int z,
                          @NonNull GlowColor color,
                          @NonNull GlowMode mode
    ) {
        this(x, y, z, color, mode, 0f, GlowExtension.UP);
    }

    @NonNull
    public String getPositionKey() {
        return this.x + ":" + this.y + ":" + this.z;
    }

    @NonNull
    public static String getPositionKey(@NonNull Block block) {
        return block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    @NonNull
    public Location toLocation(@NonNull World world) {
        return new Location(world, this.x + 0.5D, this.y, this.z + 0.5D);
    }

    public boolean isAt(@NonNull Block block) {
        return block.getX() == this.x && block.getY() == this.y && block.getZ() == this.z;
    }

    @NonNull
    public GlowingBarrier copy() {
        return new GlowingBarrier(this.x, this.y, this.z, this.color, this.mode, this.peek, this.extension);
    }

    @NonNull
    public String serialize() {
        return this.x + " " + this.y + " " + this.z + " " + this.color.name() + " " + this.mode.name() + " " + this.peek + " " + this.extension.name();
    }

    @Nullable
    public static GlowingBarrier deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] args = input.trim().split(" ");
        if (args.length < 5) return null;
        try {
            float peek = 0f;
            if (args.length > 5) {
                try {
                    peek = Float.parseFloat(args[5]);
                } catch (NumberFormatException ignored) {
                }
            }
            GlowExtension ext = GlowExtension.UP;
            if (args.length > 6) {
                try {
                    ext = GlowExtension.valueOf(args[6]);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return new GlowingBarrier(
                Integer.parseInt(args[0]),
                Integer.parseInt(args[1]),
                Integer.parseInt(args[2]),
                GlowColor.byName(args[3], GlowColor.DEFAULT),
                GlowMode.byName(args[4], GlowMode.DEFAULT),
                peek,
                ext);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
