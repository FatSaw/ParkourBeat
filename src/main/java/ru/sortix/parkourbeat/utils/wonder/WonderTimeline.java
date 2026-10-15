package ru.sortix.parkourbeat.utils.wonder;

import lombok.NonNull;
import org.bukkit.Location;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.Waypoint;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Перевод времени песни в точку на трассе.
 * <p>
 * Игрок движется с постоянной скоростью, поэтому таймкод однозначно превращается в
 * пройденное расстояние, а расстояние — в точку на ломаной из вейпоинтов. Благодаря этому
 * предпросмотр показывается там же, где эффект увидит бегущий, а не перед носом строителя.
 */
public final class WonderTimeline {

    private WonderTimeline() {
    }

    /** Ровно та же скорость, по которой цветовое шоу считает позицию в песне. */
    public static double blocksPerSecond() {
        return ru.sortix.parkourbeat.game.Game.BLOCKS_PER_SECOND;
    }

    /** Ожидаемая длительность уровня: длина трассы, поделённая на скорость бега. */
    public static int levelDurationMillis(@NonNull Level level) {
        try {
            double distance = level.getLevelSettings().getTotalLevelDistance();
            return (int) Math.round(distance / blocksPerSecond() * 1000.0D);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Обратная задача: на каком таймкоде игрок стоит сейчас.
     * <p>
     * Позиция проецируется на ближайший отрезок ломаной, накопленная длина делится на
     * скорость бега. Возвращает -1, если строитель отошёл от трассы дальше maxDistance.
     */
    public static int millisAt(@NonNull Level level, @NonNull Location where, double maxDistance) {
        List<Waypoint> waypoints;
        try {
            waypoints = level.getLevelSettings().getWorldSettings().getWaypoints();
        } catch (Throwable t) {
            return -1;
        }
        if (waypoints == null || waypoints.size() < 2) return -1;

        double passed = 0, bestDistance = Double.MAX_VALUE, bestAlong = -1;

        for (int i = 1; i < waypoints.size(); i++) {
            Location from = waypoints.get(i - 1).getLocation();
            Location to = waypoints.get(i).getLocation();
            if (from == null || to == null || from.getWorld() == null) continue;

            double ax = from.getX(), ay = from.getY(), az = from.getZ();
            double bx = to.getX(), by = to.getY(), bz = to.getZ();
            double dx = bx - ax, dy = by - ay, dz = bz - az;
            double segment = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (segment <= 1.0E-6D) continue;

            // Проекция точки на отрезок, зажатая его концами
            double t = ((where.getX() - ax) * dx + (where.getY() - ay) * dy + (where.getZ() - az) * dz)
                / (segment * segment);
            t = Math.max(0, Math.min(1, t));

            double px = ax + dx * t, py = ay + dy * t, pz = az + dz * t;
            double distance = Math.sqrt(Math.pow(where.getX() - px, 2)
                + Math.pow(where.getY() - py, 2) + Math.pow(where.getZ() - pz, 2));

            if (distance < bestDistance) {
                bestDistance = distance;
                bestAlong = passed + segment * t;
            }
            passed += segment;
        }

        if (bestAlong < 0 || bestDistance > maxDistance) return -1;
        return (int) Math.round(bestAlong / blocksPerSecond() * 1000.0D);
    }

    /**
     * Точка трассы на заданном таймкоде. null, если путь ещё не построен —
     * тогда вызывающий откатывается на позицию игрока.
     */
    @Nullable
    public static Location locationAt(@NonNull Level level, int millis) {
        List<Waypoint> waypoints;
        try {
            waypoints = level.getLevelSettings().getWorldSettings().getWaypoints();
        } catch (Throwable t) {
            return null;
        }
        if (waypoints == null || waypoints.size() < 2) return null;

        double target = Math.max(0.0D, millis / 1000.0D * blocksPerSecond());
        double passed = 0.0D;

        for (int i = 1; i < waypoints.size(); i++) {
            Location from = waypoints.get(i - 1).getLocation();
            Location to = waypoints.get(i).getLocation();
            if (from == null || to == null || from.getWorld() == null) continue;

            double segment = from.distance(to);
            if (segment <= 1.0E-6D) continue;

            if (passed + segment >= target) {
                double ratio = (target - passed) / segment;
                Location result = from.clone().add(
                    (to.getX() - from.getX()) * ratio,
                    (to.getY() - from.getY()) * ratio,
                    (to.getZ() - from.getZ()) * ratio);
                // Смотрим вдоль движения: эффекты с face:player встанут лицом к бегущему
                result.setDirection(to.toVector().subtract(from.toVector()));
                result.setPitch(0f);
                return result;
            }
            passed += segment;
        }

        Location last = waypoints.get(waypoints.size() - 1).getLocation();
        return last == null ? null : last.clone();
    }
}
