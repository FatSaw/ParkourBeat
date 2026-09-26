package ru.sortix.parkourbeat.game.movement;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.levels.Waypoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Положение игрока ВДОЛЬ ПУТИ уровня, а не вдоль оси мира.
 * <p>
 * Обычным уровням хватает одной координаты направления: путь там идёт по прямой, и
 * «дальше по X» означает «дальше по трассе». На уровнях 360° трасса поворачивает, и эта
 * подмена ломается сразу: после поворота игрок бежит вперёд по пути, но назад по оси, и
 * правило «нельзя возвращаться» убивает его на ровном месте.
 * <p>
 * Здесь путь разворачивается в одну числовую ось - длину дуги от старта. Проекция ищется
 * не по всей трассе, а рядом с прошлым положением: если путь проходит сам рядом с собой
 * (петля, змейка, спираль - на 360° это обычное дело), глобальный поиск перекидывал бы
 * игрока на соседний виток и выдавал ложный откат.
 */
public class PathProgress {
    /** Насколько далеко назад по пути разрешено искать проекцию. */
    private static final double SEARCH_BACK = 6.0D;
    /** Насколько далеко вперёд - больше, чтобы длинный прыжок не терял путь. */
    private static final double SEARCH_FORWARD = 24.0D;
    /**
     * Если ближайшая точка окна дальше этого расстояния, игрок явно не там, где мы его
     * ждали (портал, телепорт, откат) - тогда ищем по всему пути.
     */
    private static final double WINDOW_TRUST_DISTANCE = 8.0D;

    private final @NonNull List<Vector> points;
    /** Длина дуги от старта до точки с тем же индексом. */
    private final double[] arcAt;

    @Getter
    private final double totalLength;

    public PathProgress(@NonNull List<Waypoint> waypoints) {
        this.points = new ArrayList<>(waypoints.size());
        for (Waypoint waypoint : waypoints) {
            this.points.add(waypoint.getLocation().toVector());
        }

        this.arcAt = new double[this.points.size()];
        double sum = 0.0D;
        for (int i = 1; i < this.points.size(); i++) {
            sum += this.points.get(i).distance(this.points.get(i - 1));
            this.arcAt[i] = sum;
        }
        this.totalLength = sum;
    }

    public boolean isUsable() {
        return this.points.size() >= 2 && this.totalLength > 0.0D;
    }

    /**
     * Результат проекции: где игрок на пути и насколько далеко он от него отошёл.
     */
    public record Projection(double arcLength, double distanceToPath) {
    }

    /**
     * @param location    где игрок сейчас
     * @param previousArc прошлая известная длина дуги или NaN, если её ещё нет
     */
    @NonNull
    public Projection project(@NonNull Location location, double previousArc) {
        Vector position = location.toVector();

        if (!Double.isNaN(previousArc)) {
            Projection windowed = this.projectInRange(position,
                previousArc - SEARCH_BACK, previousArc + SEARCH_FORWARD);
            if (windowed != null && windowed.distanceToPath() <= WINDOW_TRUST_DISTANCE) {
                return windowed;
            }
        }

        Projection global = this.projectInRange(position, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        return global == null ? new Projection(0.0D, Double.MAX_VALUE) : global;
    }

    /**
     * Длина дуги для неподвижной точки: считается один раз, поэтому ищем по всему пути.
     */
    public double arcOf(@NonNull Location location) {
        Projection projection = this.projectInRange(location.toVector(),
            Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        return projection == null ? 0.0D : projection.arcLength();
    }

    /**
     * Ищет ближайшую точку пути, рассматривая только отрезки, попадающие в окно длин дуги.
     *
     * @return null, если в окно не попал ни один отрезок
     */
    private Projection projectInRange(@NonNull Vector position, double minArc, double maxArc) {
        Projection best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int i = 1; i < this.points.size(); i++) {
            double segmentStart = this.arcAt[i - 1];
            double segmentEnd = this.arcAt[i];
            if (segmentEnd < minArc || segmentStart > maxArc) continue;

            Vector from = this.points.get(i - 1);
            Vector to = this.points.get(i);
            Vector segment = to.clone().subtract(from);

            double segmentLengthSquared = segment.lengthSquared();
            if (segmentLengthSquared <= 1.0E-6D) continue;

            // Доля отрезка, на которую проецируется игрок, зажатая в его границы.
            double t = position.clone().subtract(from).dot(segment) / segmentLengthSquared;
            if (t < 0.0D) t = 0.0D;
            else if (t > 1.0D) t = 1.0D;

            Vector closest = from.clone().add(segment.multiply(t));
            double distance = closest.distance(position);

            if (distance < bestDistance) {
                bestDistance = distance;
                best = new Projection(segmentStart + (segmentEnd - segmentStart) * t, distance);
            }
        }

        return best;
    }
}
