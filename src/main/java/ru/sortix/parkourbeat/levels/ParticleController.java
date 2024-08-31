package ru.sortix.parkourbeat.levels;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.utils.GeometricUtils;
import ru.sortix.parkourbeat.utils.particle.ParticleUtils;
import ru.sortix.parkourbeat.utils.particle.type.ParticlePoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

@RequiredArgsConstructor
public class ParticleController {
    private static final double SEGMENT_LENGTH = 0.25;
    private static final double MAX_PARTICLES_VIEW_DISTANCE_SQUARED = Math.pow(10, 2);

    private final @NonNull ParkourBeat plugin;
    @Getter
    private final @NonNull World world;
    private final @NonNull ConcurrentLinkedQueue<ParticlePoint> particlePoints = new ConcurrentLinkedQueue<>();
    private final @NonNull Set<Player> particleViewers = ConcurrentHashMap.newKeySet();

    private boolean isLoaded = false;

    private static int calculateSegments(double length, double height) {
        // Рассчитываем количество сегментов на основе длины и высоты дуги
        double totalLength = Math.sqrt(length * length + height * height);
        int segments = (int) Math.ceil(totalLength / SEGMENT_LENGTH);
        // Гарантируем, что хотя бы один сегмент
        segments = Math.max(segments, 1);

        return segments;
    }

    @NonNull
    private static Vector cubicBezierInterpolation(@NonNull Vector p0,
                                                   @NonNull Vector p1,
                                                   @NonNull Vector p2,
                                                   @NonNull Vector p3,
                                                   double t
    ) {
        double u = 1 - t;
        double tt = t * t;
        double uu = u * u;
        double uuu = uu * u;
        double ttt = tt * t;

        Vector p = p0.clone().multiply(uuu);
        p.add(p1.clone().multiply(3 * uu * t));
        p.add(p2.clone().multiply(3 * u * tt));
        p.add(p3.clone().multiply(ttt));

        return p;
    }

    public void loadParticleLocations(@NonNull List<Waypoint> waypoints) {
        this.stopSpawnParticles();

        if (this.isLoaded) {
            this.isLoaded = false;
            this.particlePoints.clear();
        }

        for (int i = 0; i < waypoints.size() - 1; i++) {
            Waypoint currentPoint = waypoints.get(i);
            Waypoint nextPoint = waypoints.get(i + 1);

            double height = currentPoint.getHeight();
            this.addParticlePoints(
                createPathLocations(currentPoint.getLocation(), nextPoint.getLocation(), height),
                currentPoint.getColor(),
                1.0f
            );

            if (height > 0) {
                this.addParticlePoints(
                    this.createJumpParticleLocations(currentPoint.getLocation()),
                    ParticleUtils.invertRGB(currentPoint.getColor()),
                    1.0f
                );
            }
        }
        this.isLoaded = true;
        this.plugin.get(LevelsManager.class).addParticleController(this);
    }

    private void addParticlePoints(@NonNull Collection<Location> locations, @NonNull Color color, float size) {
        for (Location location : locations) {
            this.particlePoints.add(ParticleUtils.createRedstoneParticlePoint(location, color, size));
        }
    }

    private static long LAST_STACK_PRINTED_AT = 0;

    public void tickParticles() {
        if (!this.isLoaded) {
            this.plugin.getLogger().severe(
                "Unable to tick particles in world " + this.world.getName() + ": "
                    + "Controller not loaded");
            return;
        }

        for (Player player : this.particleViewers) {
            try {
                this.displayPlayerParticles(player);
            } catch (Exception e) {
                if (System.currentTimeMillis() - LAST_STACK_PRINTED_AT > 5_000) {
                    LAST_STACK_PRINTED_AT = System.currentTimeMillis();
                    this.plugin.getLogger().log(Level.SEVERE,
                        "Unable to tick particles in world " + this.world.getName()
                            + " of player " + player.getName(), e);
                } else {
                    this.plugin.getLogger().log(Level.SEVERE,
                        "Unable to tick particles in world " + this.world.getName()
                            + " of player " + player.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private void displayPlayerParticles(@NonNull Player player) {
        if (!player.isOnline()) {
            throw new IllegalStateException("Player is not online!");
        }
        if (player.getWorld() != this.world) {
            throw new IllegalStateException("Wrong player world: " + player.getWorld().getName());
        }

        // TODO Отправлять лишь частицы из двух ближайших секций:
        //  https://github.com/Slomix/ParkourBeat/issues/17
        //noinspection UnnecessaryLocalVariable
        Iterable<ParticlePoint> locations = this.particlePoints;
        ParticleUtils.displayRedstoneParticles(
            true, // TODO Detect is player version is 1.12.2 or older
            player,
            locations,
            MAX_PARTICLES_VIEW_DISTANCE_SQUARED
        );
    }

    public void startSpawnParticles(@NonNull Player player) {
        if (false && player.getWorld() != this.world) {
            throw new IllegalStateException(
                "Player is not in world " + this.world.getName() + "!\nPlayer world: " + player.getWorld().getName());
        }

        this.particleViewers.add(player);
    }

    public void stopSpawnParticlesForPlayer(@NonNull Player player) {
        this.particleViewers.remove(player);
    }

    public void stopSpawnParticles() {
        this.plugin.get(LevelsManager.class).removeParticleController(this);
    }

    public boolean isLoaded() {
        return this.isLoaded;
    }

    @NonNull
    private List<Location> createPathLocations(@NonNull Location start, @NonNull Location end, double height) {
        if (height == 0) {
            return this.createStraightPathLocations(start, end);
        } else {
            return this.createCurvedPathLocations(start, end, height);
        }
    }

    @NonNull
    private List<Location> createStraightPathLocations(@NonNull Location start, @NonNull Location end) {
        List<Location> result = new ArrayList<>();
        Vector vector = end.toVector().subtract(start.toVector());
        double length = vector.length();
        vector.normalize();

        double points = length * 4;

        for (double i = 0; i < length; i += length / points) {
            result.add(
                start.clone()
                    .add(vector.clone().multiply(i))
                    .add(0, 0.2, 0)
            );
        }

        return result;
    }

    @NonNull
    private List<Location> createCurvedPathLocations(@NonNull Location start, @NonNull Location end, double height) {
        List<Location> result = new ArrayList<>();

        Vector startVector = start.toVector();
        Vector endVector = end.toVector();

        double length = startVector.distance(endVector); // Длина отрезка
        int segments = calculateSegments(length, height);

        // Определение точек управления для кубической интерполяции
        Vector control1 = startVector.clone().midpoint(endVector).add(new Vector(0, height, 0));
        Vector control2 = endVector.clone().midpoint(startVector).add(new Vector(0, height, 0));

        double ratio;
        Vector interpolated;
        for (int t = 0; t <= segments; t++) {
            ratio = t / (double) segments;
            interpolated = cubicBezierInterpolation(startVector, control1, control2, endVector, ratio);
            result.add(new Location(
                start.getWorld(),
                interpolated.getX(), interpolated.getY(), interpolated.getZ()
            ));
        }

        return result;
    }

    private static final List<Vector> JUMP_CIRCLE_OFFSETS = GeometricUtils.createCircleOffsets(0.3f, 10);

    @NonNull
    private Collection<Location> createJumpParticleLocations(@NonNull Location middle) {
        // 4 частицы вокруг точки отрыва от земли в такой форме: ⁛
        // Подробнее: https://discord.com/channels/1079842075853459526/1216842384592343050/1227248288965726269
        List<Location> result = new ArrayList<>();
        for (Vector offset : JUMP_CIRCLE_OFFSETS) {
            result.add(middle.clone().add(offset));
        }
        return result;
    }
}
