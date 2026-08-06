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
import java.util.Comparator;
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
    private final int minWorldHeight;
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

        if (waypoints.size() < 2) {
            throw new IllegalArgumentException("Unable to find start end finish points");
        }
        this.startWaypoint = waypoints.get(0).getLocation().toVector();
        this.finishWaypoint = waypoints.get(waypoints.size() - 1).getLocation().toVector();
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

    public void addStartAndFinishPoints(@NonNull World world) {
        WorldSettings defaultSettings = Settings.getDefaultSettings(this.environment);
        this.waypoints.add(new Waypoint(
            defaultSettings.getStartWaypoint().toLocation(world),
            0, EditTrackPointsItem.DEFAULT_PARTICLES_COLOR));
        this.waypoints.add(new Waypoint(
            defaultSettings.getFinishWaypoint().toLocation(world),
            0, EditTrackPointsItem.DEFAULT_PARTICLES_COLOR));
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

    public void sortWaypoints(@NonNull DirectionChecker directionChecker) {
        Comparator<Waypoint> comparator =
            Comparator.comparingDouble(waypoint -> directionChecker.getCoordinate(waypoint.getLocation()));

        if (directionChecker.isNegative()) comparator = comparator.reversed();

        this.waypoints.sort(comparator);

        Location prevLocation = null;
        for (Waypoint waypoint : this.waypoints) {
            if (waypoint.getLocation().equals(prevLocation)) {
                System.out.println("Duplicate point: " + prevLocation);
            }
            prevLocation = waypoint.getLocation();
        }
    }

    public void updateBorders() {
        this.startWaypoint = this.waypoints.get(0).getLocation().toVector();
        this.finishWaypoint = this.waypoints.get(this.waypoints.size() - 1).getLocation().toVector();
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
        result.setLightShow(this.lightShow.copy());
        result.setParticleViewDistance(this.particleViewDistance);
        result.setGlowViewDistance(this.glowViewDistance);
        for (GlowingBarrier barrier : this.glowingBarriers) {
            result.glowingBarriers.add(barrier.copy());
        }
        return result;
    }
}
