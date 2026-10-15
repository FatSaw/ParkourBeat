package ru.sortix.parkourbeat.twod;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * ПОДСВЕТКА ФЛАГОВ ПЕРЕХОДА В ПОЛЁТ.
 * <p>
 * Из передней стороны такого флага, той, что смотрит на кубик, валит портальная
 * дымка. Смысл чисто игровой: игрок должен видеть смену режима заранее, а не
 * узнавать о ней в момент, когда кубик уже взлетел.
 * <p>
 * Блоки ищем полосой вдоль трассы и только раз в несколько тиков: сканировать весь
 * уровень каждый тик было бы дорого и совершенно незачем.
 */
public final class TwoDBannerFx {
    private TwoDBannerFx() {
    }

    /** На сколько блоков вверх от линии ищем флаги. */
    private static final int HEIGHT_UP = 4;
    /** И на сколько вниз. */
    private static final int HEIGHT_DOWN = 2;

    public static void render(@NonNull Player viewer,
                              @NonNull World world,
                              @NonNull Vector origin,
                              @NonNull Vector forward,
                              @NonNull Vector side,
                              double from,
                              double to,
                              double baseY) {
        if (viewer.getWorld() != world) return;

        double start = Math.max(0.0D, from);
        if (to <= start) return;

        int baseBlockY = (int) Math.floor(baseY);

        for (double distance = start; distance <= to; distance += 1.0D) {
            int x = (int) Math.floor(origin.getX() + forward.getX() * distance);
            int z = (int) Math.floor(origin.getZ() + forward.getZ() * distance);

            for (int dy = -HEIGHT_DOWN; dy <= HEIGHT_UP; dy++) {
                Block block = world.getBlockAt(x, baseBlockY + dy, z);
                TwoDBanners.Type type = TwoDBanners.detect(block);
                if (type == null) continue;

                // Дымят оба вида флагов: переход в паркур игрок обязан видеть так же
                // заранее, как и переход в полёт.
                emit(viewer, world, block, side, type);
            }
        }
    }

    private static void emit(@NonNull Player viewer, @NonNull World world,
                             @NonNull Block block, @NonNull Vector side,
                             @NonNull TwoDBanners.Type type) {
        try {
            // Дымка идёт с лицевой стороны флага - той, куда он смотрит. Направление
            // берём у самого блока, а не у камеры: строитель разворачивает флаг сам,
            // и правильная сторона известна только ему.
            Vector direction = facing(block);
            if (direction == null) direction = side.clone().multiply(-1.0D);

            Location at = new Location(world,
                block.getX() + 0.5D + direction.getX() * 0.55D,
                block.getY() + 0.5D,
                block.getZ() + 0.5D + direction.getZ() * 0.55D);

            viewer.spawnParticle(Particle.PORTAL, at, 14, 0.18D, 0.6D, 0.18D, 0.4D);

            // У возврата в паркур дымка плотнее у земли: полёт тянет вверх,
            // паркур наоборот прижимает, и по виду частиц это должно быть понятно.
            if (type == TwoDBanners.Type.PARKOUR) {
                viewer.spawnParticle(Particle.PORTAL,
                    at.clone().subtract(0.0D, 0.35D, 0.0D), 8, 0.22D, 0.15D, 0.22D, 0.15D);
            }
        } catch (Throwable ignored) {
        }
    }

    @javax.annotation.Nullable
    private static Vector facing(@NonNull Block block) {
        try {
            org.bukkit.block.data.BlockData data = block.getBlockData();

            if (data instanceof org.bukkit.block.data.Directional directional) {
                org.bukkit.block.BlockFace face = directional.getFacing();
                return new Vector(face.getModX(), 0, face.getModZ());
            }
            if (data instanceof org.bukkit.block.data.Rotatable rotatable) {
                org.bukkit.block.BlockFace face = rotatable.getRotation();
                Vector result = new Vector(face.getModX(), 0, face.getModZ());
                if (result.lengthSquared() > 0) return result.normalize();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
