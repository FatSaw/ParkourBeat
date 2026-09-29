package ru.sortix.parkourbeat.levels.lamps;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;

/**
 * Включает и гасит лампы стены.
 * <p>
 * Свет ставится прямо в состояние блока, без редстоуна и без физики: иначе каждая клетка
 * дёргала бы соседей, и стена размером в пару сотен ламп клала бы тик сервера.
 */
public final class LampEngine {

    private LampEngine() {
    }

    public static boolean isLamp(@NonNull Block block) {
        return block.getType() == Material.REDSTONE_LAMP;
    }

    /**
     * Проявление и угасание работают поверх ЛЮБОЙ анимации, включая свой рисунок.
     * Клетки зажигаются и гаснут вразнобой, поэтому стена не щёлкает целиком.
     */
    private static boolean visible(int col, int row, double progress, @NonNull LampWall wall) {
        double in = wall.getFadeIn(), out = wall.getFadeOut();
        if (in <= 0 && out <= 0) return true;

        int seed = (col * 73856093) ^ (row * 19349663);
        seed = (seed ^ (seed >>> 13)) * 1274126177;
        double own = ((seed >>> 8) & 1023) / 1023.0D;

        if (in > 0 && progress < in && own > progress / in) return false;
        if (out > 0 && progress > 1 - out && own < (progress - (1 - out)) / out) return false;
        return true;
    }

    /** Разложить состояние стены на текущий момент. */
    public static void apply(@NonNull World world, @NonNull LampWall wall, double phase) {
        apply(world, wall, phase, 1.0D);
    }

    public static void apply(@NonNull World world, @NonNull LampWall wall, double phase, double progress) {
        int cols = wall.getColumns();
        int rows = wall.getRows();
        boolean[] pattern = wall.patternMask();

        for (int x = wall.getX1(); x <= wall.getX2(); x++) {
            for (int y = wall.getY1(); y <= wall.getY2(); y++) {
                for (int z = wall.getZ1(); z <= wall.getZ2(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!isLamp(block)) continue;

                    int[] cell = wall.cellOf(x, y, z);
                    if (cell == null) continue;
                    int col = cell[0], row = cell[1];

                    boolean lit = wall.getAnimation().lit(col, row, cols, rows, phase, pattern);
                    if (!visible(col, row, progress, wall)) lit = false;
                    if (wall.isInverted()) lit = !lit;
                    setLit(block, lit);
                }
            }
        }
    }

    /** Вернуть стену в спокойное состояние: при инверсии это «всё горит». */
    public static void reset(@NonNull World world, @NonNull LampWall wall) {
        boolean base = wall.isInverted();
        for (int x = wall.getX1(); x <= wall.getX2(); x++) {
            for (int y = wall.getY1(); y <= wall.getY2(); y++) {
                for (int z = wall.getZ1(); z <= wall.getZ2(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (isLamp(block)) setLit(block, base);
                }
            }
        }
    }

    private static void setLit(@NonNull Block block, boolean lit) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Lightable)) return;
        Lightable lightable = (Lightable) data;
        if (lightable.isLit() == lit) return;
        lightable.setLit(lit);
        block.setBlockData(lightable, false);
    }

    /**
     * Закрасить одну лампу по координатам блока.
     * @return false, если блок вне области стены
     */
    public static boolean paint(@NonNull LampWall wall, int x, int y, int z, boolean lit) {
        int[] cell = wall.cellOf(x, y, z);
        if (cell == null) return false;
        int col = cell[0], row = cell[1];
        int cols = wall.getColumns(), rows = wall.getRows();

        boolean[] mask = wall.patternMask();
        if (mask == null || mask.length != cols * rows) mask = new boolean[cols * rows];
        mask[row * cols + col] = lit;

        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            if (r > 0) sb.append('/');
            for (int c = 0; c < cols; c++) sb.append(mask[r * cols + c] ? '1' : '0');
        }
        wall.setPattern(sb.toString());
        return true;
    }

    /**
     * Найти всю стену по одной лампе.
     * <p>
     * Обходим соединённые лампы вширь и берём их общую коробку. Строителю больше не нужно
     * целиться в два угла: достаточно ткнуть в любую лампу постройки.
     */
    @NonNull
    public static int[] detectRegion(@NonNull World world, @NonNull Block start, int limit) {
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();

        int[] first = { start.getX(), start.getY(), start.getZ() };
        queue.add(first);
        seen.add(key(first[0], first[1], first[2]));

        int minX = first[0], minY = first[1], minZ = first[2];
        int maxX = first[0], maxY = first[1], maxZ = first[2];

        while (!queue.isEmpty() && seen.size() <= limit) {
            int[] at = queue.poll();
            minX = Math.min(minX, at[0]); maxX = Math.max(maxX, at[0]);
            minY = Math.min(minY, at[1]); maxY = Math.max(maxY, at[1]);
            minZ = Math.min(minZ, at[2]); maxZ = Math.max(maxZ, at[2]);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        int nx = at[0] + dx, ny = at[1] + dy, nz = at[2] + dz;
                        long id = key(nx, ny, nz);
                        if (!seen.add(id)) continue;
                        if (!isLamp(world.getBlockAt(nx, ny, nz))) continue;
                        queue.add(new int[] { nx, ny, nz });
                    }
                }
            }
        }
        return new int[] { minX, minY, minZ, maxX, maxY, maxZ };
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) | (((long) z & 0x3FFFFFF) << 26) | (((long) y & 0xFFF) << 52);
    }

    /** Показать нарисованный узор как есть, без анимации. */
    public static void showPattern(@NonNull World world, @NonNull LampWall wall) {
        LampWall shown = wall.copy();
        shown.setAnimation(LampAnimation.PATTERN);
        apply(world, shown, 0);
    }

    /** Сколько ламп реально стоит внутри области. */
    public static int countLamps(@NonNull World world, @NonNull LampWall wall) {
        int amount = 0;
        for (int x = wall.getX1(); x <= wall.getX2(); x++) {
            for (int y = wall.getY1(); y <= wall.getY2(); y++) {
                for (int z = wall.getZ1(); z <= wall.getZ2(); z++) {
                    if (isLamp(world.getBlockAt(x, y, z))) amount++;
                }
            }
        }
        return amount;
    }
}
