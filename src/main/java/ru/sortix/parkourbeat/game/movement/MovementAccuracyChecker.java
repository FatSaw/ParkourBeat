package ru.sortix.parkourbeat.game.movement;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.levels.DirectionChecker;
import ru.sortix.parkourbeat.levels.Waypoint;

import java.util.List;

public class MovementAccuracyChecker {

    private static final double MAX_ALLOW_OFFSET = 0.1;

    /**
     * Насколько сужается коридор "прощаемого" отклонения от пути за каждую единицу
     * сложности сверх 1.0. При сложности 9 коридор сжимается в 5 раз.
     */
    private static final double CORRIDOR_TIGHTEN_PER_LEVEL = 0.5D;
    /**
     * Насколько сильнее штрафуется каждый блок отклонения за каждую единицу сложности
     * сверх 1.0. При сложности 9 штраф в 7 раз больнее: игроку кажется, что он идёт
     * чётко по пути, а точность всё равно проседает.
     */
    private static final double PENALTY_GROW_PER_LEVEL = 0.75D;

    private final @NonNull List<Waypoint> waypoints;
    private final @NonNull DirectionChecker directionChecker;
    /** Множитель сложности уровня, выставленный строителем в редакторе. */
    private final double difficultyMultiplier;
    private final double allowedOffset;
    private final double deviationPenalty;
    @Getter
    private double accuracy;
    private int currentSegment;
    private int totalSteps;
    private double totalOffset;

    public MovementAccuracyChecker(@NonNull List<Waypoint> waypoints, @NonNull DirectionChecker directionChecker) {
        this(waypoints, directionChecker, 1.0D);
    }

    public MovementAccuracyChecker(@NonNull List<Waypoint> waypoints, @NonNull DirectionChecker directionChecker, double difficultyMultiplier) {
        this.waypoints = waypoints;
        this.directionChecker = directionChecker;

        double difficulty = Double.isNaN(difficultyMultiplier) || Double.isInfinite(difficultyMultiplier)
            ? 1.0D : Math.max(1.0D, difficultyMultiplier);
        this.difficultyMultiplier = difficulty;

        double extra = difficulty - 1.0D;
        this.allowedOffset = MAX_ALLOW_OFFSET / (1.0D + CORRIDOR_TIGHTEN_PER_LEVEL * extra);
        this.deviationPenalty = 1.0D + PENALTY_GROW_PER_LEVEL * extra;

        this.reset();
    }

    public double getDifficultyMultiplier() {
        return this.difficultyMultiplier;
    }

    public void onPlayerLocationChange(@NonNull Location newLocation) {
        if (this.currentSegment >= this.waypoints.size() - 1) {
            return;
        }
        Location previousLocation = null;
        if (this.currentSegment < this.waypoints.size() - 2) {
            previousLocation = this.waypoints.get(this.currentSegment + 1).getLocation();
            if (this.directionChecker.isCorrectDirection(previousLocation, newLocation)) {
                this.currentSegment++;
            } else {
                previousLocation = null;
            }
        }

        Location point1 = previousLocation != null
            ? previousLocation
            : this.waypoints.get(this.currentSegment).getLocation();
        Location point2 = this.waypoints.get(this.currentSegment + 1).getLocation();

        double distanceToLine = calculateDistanceToLine(newLocation, point1, point2);

        if (distanceToLine > this.allowedOffset) {
            this.totalOffset += distanceToLine - this.allowedOffset;
        }
        this.totalSteps++;

        double averageDeviation = this.totalOffset / this.totalSteps;

        this.accuracy = 1.0 / (1.0 + averageDeviation * this.deviationPenalty);
    }

    /**
     * Перемотать указатель сегмента на позицию игрока, НЕ трогая накопленную точность.
     * <p>
     * Нужно после отката на чекпоинт. {@code currentSegment} умеет только расти, и без
     * перемотки игрок, вернувшийся назад, мерился бы против сегмента, который остался
     * далеко впереди: отклонение получалось огромным, и одна смерть выжигала точность
     * всего забега в ноль.
     */
    public void rewindTo(@NonNull Location location) {
        int segment = 0;
        while (segment < this.waypoints.size() - 2) {
            if (!this.directionChecker.isCorrectDirection(
                this.waypoints.get(segment + 1).getLocation(), location)) break;
            segment++;
        }
        this.currentSegment = segment;
    }

    /**
     * Слепок точности движения на момент взятия чекпоинта.
     */
    public record Snapshot(double accuracy, int currentSegment, int totalSteps, double totalOffset) {
    }

    @NonNull
    public Snapshot snapshot() {
        return new Snapshot(this.accuracy, this.currentSegment, this.totalSteps, this.totalOffset);
    }

    public void restore(@NonNull Snapshot snapshot) {
        this.accuracy = snapshot.accuracy();
        this.currentSegment = snapshot.currentSegment();
        this.totalSteps = snapshot.totalSteps();
        this.totalOffset = snapshot.totalOffset();
    }

    public void reset() {
        this.accuracy = 1;
        this.currentSegment = 0;
        this.totalSteps = 0;
        this.totalOffset = 0;
    }

    /**
     * Calculates the distance from a point to a line defined by two other points.
     *
     * @param point      the location of the point
     * @param linePoint1 the first location defining the line
     * @param linePoint2 the second location defining the line
     * @return the distance from the point to the line
     */
    private double calculateDistanceToLine(@NonNull Location point, @NonNull Location linePoint1, @NonNull Location linePoint2) {
        Vector lineVector = linePoint2.toVector().subtract(linePoint1.toVector());
        Vector pointVector = point.toVector().subtract(linePoint1.toVector());

        lineVector.setY(0);
        pointVector.setY(0);

        double dotProduct = lineVector.dot(pointVector);
        Vector projection = lineVector.multiply(dotProduct / lineVector.lengthSquared());

        Vector perpendicular = pointVector.subtract(projection);

        return perpendicular.length();
    }
}
