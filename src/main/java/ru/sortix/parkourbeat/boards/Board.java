package ru.sortix.parkourbeat.boards;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class Board {

    public static final int TILE = 128;

    private final @NonNull String id;
    private final @NonNull BoardType type;
    private final @NonNull String worldName;
    private final @NonNull BlockFace face;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final int width;
    private final int height;
    private final int[] mapIds;
    private final List<UUID> frames = new ArrayList<>();

    public Board(@NonNull String id,
                 @NonNull BoardType type,
                 @NonNull String worldName,
                 @NonNull BlockFace face,
                 int originX,
                 int originY,
                 int originZ,
                 int width,
                 int height,
                 @NonNull int[] mapIds
    ) {
        this.id = id;
        this.type = type;
        this.worldName = worldName;
        this.face = face;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.width = width;
        this.height = height;
        this.mapIds = mapIds;
    }

    public int pixelWidth() {
        return this.width * TILE;
    }

    public int pixelHeight() {
        return this.height * TILE;
    }

    public int tiles() {
        return this.width * this.height;
    }

    @Nullable
    public World world() {
        return Bukkit.getWorld(this.worldName);
    }

    @NonNull
    public BlockFace rightFace() {
        switch (this.face) {
            case SOUTH: return BlockFace.EAST;
            case EAST: return BlockFace.NORTH;
            case WEST: return BlockFace.SOUTH;
            default: return BlockFace.WEST;
        }
    }

    public static boolean isFlat(@NonNull BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.SOUTH
            || face == BlockFace.EAST || face == BlockFace.WEST;
    }

    public int wallX(int column) {
        return this.originX + this.rightFace().getModX() * column;
    }

    public int wallY(int row) {
        return this.originY - row;
    }

    public int wallZ(int column) {
        return this.originZ + this.rightFace().getModZ() * column;
    }

    @Nullable
    public Location center() {
        World world = this.world();
        if (world == null) return null;
        BlockFace right = this.rightFace();
        double x = this.originX + 0.5D + right.getModX() * (this.width - 1) / 2.0D + this.face.getModX();
        double y = this.originY + 0.5D - (this.height - 1) / 2.0D;
        double z = this.originZ + 0.5D + right.getModZ() * (this.width - 1) / 2.0D + this.face.getModZ();
        return new Location(world, x, y, z);
    }

    /**
     * Точка попадания взгляда в экран.
     *
     * @return пиксель {x, y} внутри полотна борда либо null, если луч мимо
     */
    @Nullable
    public int[] trace(@NonNull Location eye, @NonNull Vector direction, double maxDistance) {
        int nx = this.face.getModX();
        int nz = this.face.getModZ();

        double planeCoord;
        double eyeCoord;
        double dirCoord;
        if (nx != 0) {
            planeCoord = this.originX + (nx > 0 ? 1 : 0);
            eyeCoord = eye.getX();
            dirCoord = direction.getX();
            if (dirCoord * nx >= 0) return null;
        } else {
            planeCoord = this.originZ + (nz > 0 ? 1 : 0);
            eyeCoord = eye.getZ();
            dirCoord = direction.getZ();
            if (dirCoord * nz >= 0) return null;
        }
        if (Math.abs(dirCoord) < 1.0E-6D) return null;

        double t = (planeCoord - eyeCoord) / dirCoord;
        if (t < 0.0D || t > maxDistance) return null;

        double hx = eye.getX() + direction.getX() * t;
        double hy = eye.getY() + direction.getY() * t;
        double hz = eye.getZ() + direction.getZ() * t;

        BlockFace right = this.rightFace();
        double u;
        if (right.getModX() != 0) {
            double leftEdge = right.getModX() > 0 ? this.originX : this.originX + 1;
            u = (hx - leftEdge) * right.getModX();
        } else {
            double leftEdge = right.getModZ() > 0 ? this.originZ : this.originZ + 1;
            u = (hz - leftEdge) * right.getModZ();
        }
        double v = (this.originY + 1) - hy;

        if (u < 0.0D || u >= this.width || v < 0.0D || v >= this.height) return null;

        int px = (int) (u / this.width * this.pixelWidth());
        int py = (int) (v / this.height * this.pixelHeight());
        if (px < 0) px = 0;
        if (py < 0) py = 0;
        if (px >= this.pixelWidth()) px = this.pixelWidth() - 1;
        if (py >= this.pixelHeight()) py = this.pixelHeight() - 1;
        return new int[]{px, py};
    }

    public int tileIndex(int column, int row) {
        return row * this.width + column;
    }

    public int mapIdAt(int column, int row) {
        return this.mapIds[this.tileIndex(column, row)];
    }

    public boolean ownsMap(int mapId) {
        for (int id : this.mapIds) {
            if (id == mapId) return true;
        }
        return false;
    }
}
