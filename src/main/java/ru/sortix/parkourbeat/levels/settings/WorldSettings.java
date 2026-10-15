// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/levels/settings/WorldSettings.java
package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.item.editor.type.EditTrackPointsItem;
import ru.sortix.parkourbeat.levels.DirectionChecker;
import ru.sortix.parkourbeat.levels.Waypoint;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class WorldSettings {
    public static final int MAX_GLOWING_BARRIERS = 512;
    public static final double DEFAULT_PARTICLE_VIEW_DISTANCE = 7.5D;
    public static final double MIN_VIEW_DISTANCE = 1.0D;
    public static final double MAX_VIEW_DISTANCE = 32.0D;
    public static final double DEFAULT_GLOW_VIEW_DISTANCE = 3.0D;

    private final @NonNull World.Environment environment;
    private final @NonNull List<Waypoint> waypoints;

    /**
     * Второй путь из частиц - только у дуэльных карт.
     * <p>
     * На любом другом уровне список пуст и не стоит ничего. Границы уровня (старт,
     * финиш, нижняя высота) он не задаёт: у обеих трасс дуэли они общие и считаются
     * по основному пути.
     */
    private final @NonNull List<Waypoint> secondWaypoints = new ArrayList<>();
    private int minWorldHeight;
    private final @NonNull DirectionChecker.Direction direction;

    @Setter
    private @NonNull Location spawn;

    @Setter
    private @NonNull Vector startWaypoint;

    @Setter
    private @NonNull Vector finishWaypoint;

    private @NonNull LightShowSettings lightShow = new LightShowSettings();

    @Getter
    private double particleViewDistance = DEFAULT_PARTICLE_VIEW_DISTANCE;
    @Getter
    private double glowViewDistance = DEFAULT_GLOW_VIEW_DISTANCE;

    private final List<GlowingBarrier> glowingBarriers = new ArrayList<>();

    public WorldSettings(
        @NonNull World.Environment environment,
        @NonNull DirectionChecker.Direction direction,
        @NonNull Location spawn,
        @NonNull List<Waypoint> waypoints
    ) {

        this.environment = environment;
        this.spawn = spawn;
        this.waypoints = waypoints;
        this.direction = direction;
        this.minWorldHeight = this.findMinWorldHeight();

        this.startWaypoint = fallbackStart(waypoints, spawn);
        this.finishWaypoint = fallbackFinish(waypoints, this.startWaypoint, direction);
    }

    @NonNull
    private static Vector fallbackStart(@NonNull List<Waypoint> waypoints, @NonNull Location spawn) {
        if (waypoints.isEmpty()) return spawn.toVector();
        return waypoints.get(0).getLocation().toVector();
    }

    /**
     * Финиш - это ВСЕГДА последняя точка пути из частиц. Отдельной сущностью он не
     * существует и в редакторе не ставится: строитель просто доводит путь до нужного
     * места, и последняя поставленная точка становится концом уровня.
     * <p>
     * Единственное исключение - уровень, у которого пути ещё нет (одна стартовая точка
     * сразу после создания). Финиш там условный, на блок вперёд по направлению уровня,
     * иначе старт и финиш совпали бы и длина трассы вышла бы нулевой.
     */
    @NonNull
    private static Vector fallbackFinish(@NonNull List<Waypoint> waypoints,
                                         @NonNull Vector start,
                                         @NonNull DirectionChecker.Direction direction) {
        if (waypoints.size() >= 2) {
            return waypoints.get(waypoints.size() - 1).getLocation().toVector();
        }
        Vector result = start.clone();
        new DirectionChecker(direction).add(result, 1.0D);
        return result;
    }

    public void setLightShow(@NonNull LightShowSettings lightShow) {
        this.lightShow = lightShow;
    }

    public void setParticleViewDistance(double particleViewDistance) {
        this.particleViewDistance = clampViewDistance(particleViewDistance, DEFAULT_PARTICLE_VIEW_DISTANCE);
    }

    public void setGlowViewDistance(double glowViewDistance) {
        this.glowViewDistance = clampViewDistance(glowViewDistance, DEFAULT_GLOW_VIEW_DISTANCE);
    }

    private static double clampViewDistance(double value, double fallback) {
        if (Double.isNaN(value) || value <= 0.0D) return fallback;
        return Math.max(MIN_VIEW_DISTANCE, Math.min(MAX_VIEW_DISTANCE, value));
    }

    /**
     * Путь указанной стороны дуэли. Для первой стороны - обычный путь уровня.
     */
    @NonNull
    public List<Waypoint> getWaypoints(@NonNull ru.sortix.parkourbeat.duel.DuelSide side) {
        return side.isFirst() ? this.waypoints : this.secondWaypoints;
    }

    /**
     * То же, но с подстраховкой: если второй путь ещё не построен, отдаётся первый.
     * Иначе игрок второй стороны оказался бы на уровне вообще без трассы.
     */
    @NonNull
    public List<Waypoint> getWaypointsOrPrimary(@NonNull ru.sortix.parkourbeat.duel.DuelSide side) {
        if (side.isFirst() || this.secondWaypoints.isEmpty()) return this.waypoints;
        return this.secondWaypoints;
    }

    public void setSecondWaypoints(@NonNull List<Waypoint> waypoints) {
        this.secondWaypoints.clear();
        this.secondWaypoints.addAll(waypoints);
    }

    @NonNull
    public List<GlowingBarrier> getGlowingBarriers() {
        return Collections.unmodifiableList(this.glowingBarriers);
    }

    @Nullable
    public GlowingBarrier findGlowingBarrier(int x, int y, int z) {
        for (GlowingBarrier barrier : this.glowingBarriers) {
            if (barrier.getX() == x && barrier.getY() == y && barrier.getZ() == z) return barrier;
        }
        return null;
    }

    public boolean addGlowingBarrier(@NonNull GlowingBarrier barrier) {
        if (this.glowingBarriers.size() >= MAX_GLOWING_BARRIERS) return false;
        this.removeGlowingBarrier(barrier.getX(), barrier.getY(), barrier.getZ());
        this.glowingBarriers.add(barrier);
        return true;
    }

    public boolean removeGlowingBarrier(int x, int y, int z) {
        return this.glowingBarriers.removeIf(
            barrier -> barrier.getX() == x && barrier.getY() == y && barrier.getZ() == z);
    }

    public void setGlowingBarriers(@NonNull List<GlowingBarrier> barriers) {
        this.glowingBarriers.clear();
        for (GlowingBarrier barrier : barriers) {
            if (this.glowingBarriers.size() >= MAX_GLOWING_BARRIERS) break;
            this.glowingBarriers.add(barrier);
        }
    }

    /**
     * Поставить уровню единственную точку - стартовую.
     * <p>
     * ФИНИША В ШАБЛОНЕ БОЛЬШЕ НЕТ. Пока уровень создавался с парой «старт-финиш»,
     * финиш стоял в заранее известном месте, а строитель тянул трассу от старта куда
     * хотел - и почти всегда проходил финишную точку насквозь. Получался уровень, где
     * финиш идёт РАНЬШЕ старта: сначала конец, потом начало. Проходить такое нельзя.
     * <p>
     * Теперь финиш - это просто последняя точка пути из частиц, то есть та, которую
     * строитель поставил последней. Раньше старта он оказаться не может физически.
     * Сам старт при необходимости переносится через меню редактора.
     */
    public void addStartPoint(@NonNull World world) {
        WorldSettings defaultSettings = Settings.getDefaultSettings(this.environment);
        this.waypoints.add(new Waypoint(
            defaultSettings.getStartWaypoint().toLocation(world),
            0, EditTrackPointsItem.DEFAULT_PARTICLES_COLOR));
    }

    /**
     * Перенести стартовую точку уровня в указанное место.
     * <p>
     * Двигается именно нулевая точка списка: порядок точек - это и есть порядок
     * прохождения, поэтому старт обязан оставаться нулевым. Если точек нет вообще
     * (пустой уровень), точка создаётся.
     */
    public void moveStartPoint(@NonNull Location location) {
        if (this.waypoints.isEmpty()) {
            this.waypoints.add(new Waypoint(location, 0, EditTrackPointsItem.DEFAULT_PARTICLES_COLOR));
        } else {
            this.waypoints.get(0).setLocation(location);
        }
        this.updateBorders();
    }

    private int findMinWorldHeight() {
        if (this.waypoints.isEmpty()) {
            return 0;
        }

        int minWorldHeight = Integer.MAX_VALUE;
        for (Waypoint waypoint : this.waypoints) {
            minWorldHeight = Math.min(minWorldHeight, waypoint.getLocation().getBlockY());
        }
        return minWorldHeight;
    }

    public void updateBorders() {
        this.startWaypoint = fallbackStart(this.waypoints, this.spawn);
        this.finishWaypoint = fallbackFinish(this.waypoints, this.startWaypoint, this.direction);
        this.recalculateMinWorldHeight();
    }

    public void recalculateMinWorldHeight() {
        this.minWorldHeight = this.findMinWorldHeight();
    }

    @NonNull
    public WorldSettings setWorld(@NonNull World.Environment environment, @Nullable World world) {
        Location spawn = this.getSpawn().clone();
        spawn.setWorld(world);

        DirectionChecker.Direction direction = this.getDirection();

        List<Waypoint> waypoints = new ArrayList<>(this.getWaypoints());
        for (Waypoint waypoint : waypoints) {
            waypoint.getLocation().setWorld(world);
        }

        WorldSettings result = new WorldSettings(environment, direction, spawn, waypoints);

        // Второй путь дуэли переезжает вместе с основным: иначе смена мира уровня
        // молча стирала бы половину карты.
        List<Waypoint> secondCopy = new ArrayList<>(this.secondWaypoints);
        for (Waypoint waypoint : secondCopy) {
            waypoint.getLocation().setWorld(world);
        }
        result.setSecondWaypoints(secondCopy);

        result.setLightShow(this.lightShow.copy());
        result.setParticleViewDistance(this.particleViewDistance);
        result.setGlowViewDistance(this.glowViewDistance);
        for (GlowingBarrier barrier : this.glowingBarriers) {
            result.glowingBarriers.add(barrier.copy());
        }
        return result;
    }
}
