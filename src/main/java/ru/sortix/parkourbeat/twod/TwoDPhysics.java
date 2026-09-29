package ru.sortix.parkourbeat.twod;

import lombok.NonNull;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import javax.annotation.Nullable;

/**
 * Столкновения кубика с миром.
 * <p>
 * Правило ровно как в Geometry Dash: приземляться на верх блока можно, врезаться в
 * блок сбоку - нельзя. Поэтому вертикальное и горизонтальное перемещение считаются
 * отдельно, а не одним движением.
 * <p>
 * Формы блоков берутся настоящие ({@link Block#getBoundingBox()}), поэтому шипы,
 * плиты и ступеньки работают сами собой, без отдельных списков материалов. Проходимые
 * блоки (трава, баннеры, вода) кубику не мешают вообще.
 */
public final class TwoDPhysics {
    private TwoDPhysics() {
    }

    /** Небольшой зазор, чтобы кубик не «прилипал» к полу и потолку из-за округлений. */
    public static final double EPSILON = 0.02D;

    @NonNull
    public static BoundingBox cubeBox(double x, double y, double z, double half) {
        return new BoundingBox(x - half, y, z - half, x + half, y + 1.0D, z + half);
    }

    private static boolean isBlocking(@Nullable Block block) {
        if (block == null) return false;
        if (block.isEmpty()) return false;
        try {
            if (block.isPassable()) return false;
        } catch (Throwable ignored) {
        }
        return true;
    }

    @Nullable
    private static BoundingBox blockBox(@Nullable Block block) {
        if (!isBlocking(block)) return null;
        try {
            BoundingBox box = block.getBoundingBox();
            if (box.getWidthX() <= 0 || box.getHeight() <= 0 || box.getWidthZ() <= 0) return null;
            return box;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Есть ли твёрдый блок внутри коробки.
     */
    public static boolean collides(@NonNull World world, @NonNull BoundingBox box) {
        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.floor(box.getMaxX() - 1.0E-7D);
        int minY = (int) Math.floor(box.getMinY());
        int maxY = (int) Math.floor(box.getMaxY() - 1.0E-7D);
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.floor(box.getMaxZ() - 1.0E-7D);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BoundingBox blockBox = blockBox(world.getBlockAt(x, y, z));
                    if (blockBox == null) continue;
                    if (blockBox.overlaps(box)) return true;
                }
            }
        }
        return false;
    }

    /**
     * Верх ближайшей опоры под кубиком при падении с {@code fromY} до {@code toY}.
     *
     * @return высота, на которую надо поставить низ кубика, или {@link Double#NaN}, если опоры нет
     */
    public static double findGround(@NonNull World world, double x, double z, double half,
                                    double fromY, double toY) {
        int minX = (int) Math.floor(x - half);
        int maxX = (int) Math.floor(x + half - 1.0E-7D);
        int minZ = (int) Math.floor(z - half);
        int maxZ = (int) Math.floor(z + half - 1.0E-7D);
        int minY = (int) Math.floor(toY) - 1;
        int maxY = (int) Math.floor(fromY + EPSILON);

        double best = Double.NaN;
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int by = minY; by <= maxY; by++) {
                    BoundingBox blockBox = blockBox(world.getBlockAt(bx, by, bz));
                    if (blockBox == null) continue;

                    // Блок должен перекрываться с кубиком по горизонтали...
                    if (blockBox.getMaxX() <= x - half || blockBox.getMinX() >= x + half) continue;
                    if (blockBox.getMaxZ() <= z - half || blockBox.getMinZ() >= z + half) continue;

                    double top = blockBox.getMaxY();
                    // ...и находиться между старой и новой высотой, иначе это не опора,
                    // а стена, в которую кубик врезается (её разбирает горизонтальный шаг).
                    if (top > fromY + EPSILON) continue;
                    if (top < toY) continue;

                    if (Double.isNaN(best) || top > best) best = top;
                }
            }
        }
        return best;
    }

    /**
     * Низ ближайшего потолка над кубиком при движении вверх.
     *
     * @return высота низа блока или {@link Double#NaN}, если потолка нет
     */
    public static double findCeiling(@NonNull World world, double x, double z, double half,
                                     double fromTopY, double toTopY) {
        int minX = (int) Math.floor(x - half);
        int maxX = (int) Math.floor(x + half - 1.0E-7D);
        int minZ = (int) Math.floor(z - half);
        int maxZ = (int) Math.floor(z + half - 1.0E-7D);
        int minY = (int) Math.floor(fromTopY - EPSILON);
        int maxY = (int) Math.floor(toTopY) + 1;

        double best = Double.NaN;
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int by = minY; by <= maxY; by++) {
                    BoundingBox blockBox = blockBox(world.getBlockAt(bx, by, bz));
                    if (blockBox == null) continue;

                    if (blockBox.getMaxX() <= x - half || blockBox.getMinX() >= x + half) continue;
                    if (blockBox.getMaxZ() <= z - half || blockBox.getMinZ() >= z + half) continue;

                    double bottom = blockBox.getMinY();
                    if (bottom < fromTopY - EPSILON) continue;
                    if (bottom > toTopY) continue;

                    if (Double.isNaN(best) || bottom < best) best = bottom;
                }
            }
        }
        return best;
    }
}
