package ru.sortix.parkourbeat.twod;

import lombok.NonNull;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;

/**
 * ЛИНИЯ, ПО КОТОРОЙ ЕДЕТ КУБИК.
 * <p>
 * Видит её ТОЛЬКО строитель: в самом забеге она не рисуется, чтобы не мешать читать
 * геометрию уровня. Линия идёт сбоку от оси движения и заканчивается ровно там, где
 * заканчивается уровень: её длина и есть длина уровня.
 * <p>
 * Концы линии помечены столбиками: зелёный это старт, красный это финиш.
 */
public final class TwoDLine {
    private TwoDLine() {
    }

    private static final Particle LINE_PARTICLE = resolveFirst("ELECTRIC_SPARK", "END_ROD", "CRIT");
    private static final Particle ACCENT_PARTICLE = resolveFirst("END_ROD", "WAX_ON", "CRIT");

    private static final Particle.DustOptions START_DUST =
        new Particle.DustOptions(Color.fromRGB(0x2BFF55), 1.4f);
    private static final Particle.DustOptions FINISH_DUST =
        new Particle.DustOptions(Color.fromRGB(0xFF3B30), 1.4f);

    @Nullable
    private static Particle resolveFirst(@NonNull String... names) {
        for (String name : names) {
            try {
                return Particle.valueOf(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void point(@NonNull Player viewer, @NonNull Location location, boolean accent) {
        Particle particle = accent && ACCENT_PARTICLE != null ? ACCENT_PARTICLE : LINE_PARTICLE;
        if (particle == null) return;
        try {
            viewer.spawnParticle(particle, location, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        } catch (Throwable ignored) {
        }
    }

    @NonNull
    private static Location at(@NonNull World world, @NonNull Vector origin, @NonNull Vector forward,
                               @NonNull Vector side, double distance, double y, double sideOffset) {
        double x = origin.getX() + forward.getX() * distance + side.getX() * sideOffset;
        double z = origin.getZ() + forward.getZ() * distance + side.getZ() * sideOffset;
        return new Location(world, x, y, z);
    }

    /**
     * Линия не должна лежать внутри блоков уровня: если строитель стоит прямо на
     * трассе, отодвигаем её на минимальный отступ в его сторону.
     */
    private static double normalizeSideOffset(double sideOffset) {
        double minimum = Math.abs(TwoDTuning.LINE_SIDE_OFFSET);
        if (Math.abs(sideOffset) >= minimum) return sideOffset;
        return sideOffset < 0 ? -minimum : minimum;
    }

    /**
     * Нарисовать линию уровня строителю.
     *
     * @param origin     спавн кубика: отсюда линия начинается
     * @param lineLength длина уровня в блоках
     * @param around     позиция строителя вдоль оси уровня: вокруг неё и рисуем
     * @param baseY      высота, на которой лежит линия
     */
    public static void render(@NonNull Player viewer,
                              @NonNull World world,
                              @NonNull Vector origin,
                              @NonNull Vector forward,
                              @NonNull Vector side,
                              double lineLength,
                              double around,
                              double baseY,
                              double sideOffset) {
        if (viewer.getWorld() != world) return;
        if (lineLength <= 0.0D) return;

        double offset = normalizeSideOffset(sideOffset);

        double step = Math.max(0.1D, TwoDTuning.LINE_STEP);
        double height = baseY + TwoDTuning.LINE_HEIGHT;

        double from = Math.max(0.0D, around - TwoDTuning.LINE_BEHIND);
        double to = Math.min(lineLength, from + TwoDTuning.LINE_VIEW_DISTANCE);
        if (from > lineLength) from = Math.max(0.0D, lineLength - TwoDTuning.LINE_VIEW_DISTANCE);

        int index = 0;
        int accentEvery = TwoDTuning.LINE_SPARK_EVERY;

        for (double distance = from; distance <= to; distance += step) {
            boolean accent = accentEvery > 0 && index % accentEvery == 0;
            point(viewer, at(world, origin, forward, side, distance, height, offset), accent);
            index++;
        }

        renderMarker(viewer, world, origin, forward, side, 0.0D, baseY, true, offset);
        renderMarker(viewer, world, origin, forward, side, lineLength, baseY, false, offset);
    }

    private static final Particle.DustOptions FALL_DUST =
        new Particle.DustOptions(Color.fromRGB(0xFF8A00), 1.1f);

    /**
     * ПРЕДПРОСМОТР ВЫСОТЫ ПРОИГРЫША.
     * <p>
     * Одна точка на каждые два метра пути показывает, где именно кубик считается
     * упавшим. Высота считается от пола под этой точкой, а не от старта: спуск по
     * лестнице это нормальный геймплей, и линия смерти едет вниз вместе с ним.
     */
    public static void renderFallPreview(@NonNull Player viewer,
                                         @NonNull World world,
                                         @NonNull Vector origin,
                                         @NonNull Vector forward,
                                         @NonNull Vector side,
                                         double lineLength,
                                         double around,
                                         double baseY,
                                         double sideOffset) {
        if (viewer.getWorld() != world) return;
        if (lineLength <= 0.0D) return;

        double offset = normalizeSideOffset(sideOffset);
        double step = Math.max(0.5D, TwoDTuning.FALL_PREVIEW_STEP);
        double from = Math.max(0.0D, around - TwoDTuning.LINE_BEHIND);
        double to = Math.min(lineLength, from + TwoDTuning.LINE_VIEW_DISTANCE);

        for (double distance = from; distance <= to; distance += step) {
            Location probe = at(world, origin, forward, side, distance, baseY, 0.0D);
            double groundTop = findGroundTop(world, probe, baseY);
            double deathY = groundTop - TwoDTuning.FALL_DEATH_DEPTH;

            Location at = at(world, origin, forward, side, distance, deathY, offset);
            try {
                viewer.spawnParticle(Particle.REDSTONE, at, 1, 0.0D, 0.0D, 0.0D, 0.0D, FALL_DUST);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Верх ближайшего пола под точкой. Ищем сверху вниз, но не бесконечно: если под
     * трассой пусто, за пол считаем саму высоту линии.
     */
    private static double findGroundTop(@NonNull World world, @NonNull Location probe, double baseY) {
        int x = probe.getBlockX();
        int z = probe.getBlockZ();
        int top = (int) Math.floor(baseY) + 2;
        int bottom = Math.max(world.getMinHeight(), top - 64);

        for (int y = top; y >= bottom; y--) {
            try {
                org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                if (block.isEmpty() || block.isPassable()) continue;
                return block.getBoundingBox().getMaxY();
            } catch (Throwable ignored) {
                break;
            }
        }
        return baseY;
    }

    /**
     * Столбик на конце линии. Он же единственное место, где в 2D используется цветная
     * пыль: старт и финиш обязаны читаться с одного взгляда и не путаться с самой линией.
     */
    private static void renderMarker(@NonNull Player viewer,
                                     @NonNull World world,
                                     @NonNull Vector origin,
                                     @NonNull Vector forward,
                                     @NonNull Vector side,
                                     double distance,
                                     double baseY,
                                     boolean start,
                                     double sideOffset) {
        Particle.DustOptions dust = start ? START_DUST : FINISH_DUST;
        double height = Math.max(0.5D, TwoDTuning.MARKER_HEIGHT);

        try {
            for (double dy = 0.0D; dy <= height; dy += 0.35D) {
                Location location = at(world, origin, forward, side, distance, baseY + dy, sideOffset);
                viewer.spawnParticle(Particle.REDSTONE, location, 1, 0.0D, 0.0D, 0.0D, 0.0D, dust);
            }
        } catch (Throwable ignored) {
        }
    }
}
