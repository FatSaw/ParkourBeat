// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/levels/ParticleController.java
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
import ru.sortix.parkourbeat.levels.settings.WorldSettings;
import ru.sortix.parkourbeat.utils.GeometricUtils;
import ru.sortix.parkourbeat.utils.particle.ParticleUtils;
import ru.sortix.parkourbeat.utils.particle.type.ParticlePoint;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

@RequiredArgsConstructor
public class ParticleController {
    private static final double SEGMENT_LENGTH = 0.25;
    private double viewDistanceSquared = Math.pow(WorldSettings.DEFAULT_PARTICLE_VIEW_DISTANCE, 2);

    private static final int REVEAL_POINTS_PER_TICK = 1;
    private static final int CATCH_UP_TICKS = 2;

    @Getter
    private final @NonNull ParkourBeat plugin;
    @Getter
    private final @NonNull World world;
    private final @NonNull ConcurrentLinkedQueue<ParticlePoint> particlePoints = new ConcurrentLinkedQueue<>();
    private final Map<Player, Integer> revealedIndexes = new ConcurrentHashMap<>();
    private final @NonNull Set<Player> particleViewers = ConcurrentHashMap.newKeySet();

    private final @NonNull Set<Player> hiddenViewers = ConcurrentHashMap.newKeySet();
    private static final double HIDDEN_RADIUS = 5.0D;

    private boolean isLoaded = false;

    @lombok.Setter
    private @Nullable DirectionChecker directionChecker = null;

    @lombok.Setter
    private ru.sortix.parkourbeat.levels.Level colorCueLevel = null;
    private final java.util.List<ru.sortix.parkourbeat.levels.settings.ParticleColorCue> colorCues =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    public void setColorCues(@NonNull java.util.Collection<ru.sortix.parkourbeat.levels.settings.ParticleColorCue> cues) {
        this.colorCues.clear();
        this.colorCues.addAll(cues);
    }

    public void setViewDistance(double viewDistance) {
        this.viewDistanceSquared = viewDistance * viewDistance;
    }

    private static int calculateSegments(double length, double height) {
        double totalLength = Math.sqrt(length * length + height * height);
        int segments = (int) Math.ceil(totalLength / SEGMENT_LENGTH);
        return Math.max(segments, 1);
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

        List<ParticlePoint> built = new ArrayList<>();

        for (int i = 0; i < waypoints.size() - 1; i++) {
            Waypoint currentPoint = waypoints.get(i);
            Waypoint nextPoint = waypoints.get(i + 1);

            if (PortalPathFilter.isHidden(this.colorCueLevel, currentPoint.getLocation())) continue;

            double height = currentPoint.getHeight();

            // Создаем круг прыжка ДО создания самого пути.
            // Таким образом мы соблюдаем правильный хронологический порядок для рендера
            // без необходимости глобально сортировать все частицы (что ломало повороты)
            if (height > 0) {
                Color jumpColor = currentPoint.getJumpColor() != null
                    ? currentPoint.getJumpColor()
                    : ParticleUtils.invertRGB(currentPoint.getColor());
                this.addParticlePoints(
                    built,
                    this.createJumpParticleLocations(currentPoint.getLocation()),
                    jumpColor,
                    1.0f,
                    true
                );
            }

            this.addParticlePoints(
                built,
                createPathLocations(currentPoint.getLocation(), nextPoint.getLocation(), height),
                currentPoint.getColor(),
                1.0f,
                false
            );
        }

        // Мы удалили this.sortAlongLevel(built), который портил геометрию при 180-разворотах
        this.particlePoints.addAll(built);

        this.isLoaded = true;
        this.plugin.get(LevelsManager.class).addParticleController(this);
    }

    private void addParticlePoints(@NonNull List<ParticlePoint> target, @NonNull Collection<Location> locations, @NonNull Color color, float size, boolean isJumpTrigger) {
        for (Location location : locations) {
            ParticlePoint point = ParticleUtils.createRedstoneParticlePoint(location, color, size);
            if (isJumpTrigger) {
                point.setJumpTrigger(true);
            }
            target.add(point);
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
                }
            }
        }
    }

    private void displayPlayerParticles(@NonNull Player player) {
        if (!player.isOnline() || player.getWorld() != this.world) {
            this.particleViewers.remove(player);
            this.revealedIndexes.remove(player);
            this.easedColors.remove(player.getUniqueId());
            this.easedJumpColors.remove(player.getUniqueId());
            this.hiddenViewers.remove(player);
            return;
        }

        Location playerLocation = player.getLocation();

        ru.sortix.parkourbeat.activity.UserActivity activity = this.plugin.get(ru.sortix.parkourbeat.activity.ActivityManager.class).getActivity(player);
        if (activity instanceof ru.sortix.parkourbeat.activity.type.ReplayActivity replayActivity) {
            playerLocation = replayActivity.getCurrentLocation();
        }

        int index = 0;
        int lastInRange = -1;
        for (ParticlePoint point : this.particlePoints) {
            if (point.getLocation().distanceSquared(playerLocation) <= this.viewDistanceSquared) {
                lastInRange = index;
            }
            index++;
        }
        if (lastInRange < 0) return;

        int revealed = this.advanceRevealedIndex(player, lastInRange);
        Color pathColor = this.easedColorFor(player, playerLocation);
        Color jumpColor = this.easedJumpColorFor(player, playerLocation);

        index = 0;
        boolean hidden = this.hiddenViewers.contains(player);
        for (ParticlePoint point : this.particlePoints) {
            if (index > revealed) break;
            if (point.getLocation().distanceSquared(playerLocation) <= this.viewDistanceSquared) {
                if (hidden && !point.isJumpTrigger() && point.getLocation().distanceSquared(playerLocation) <= HIDDEN_RADIUS * HIDDEN_RADIUS) {
                    index++;
                    continue;
                }
                if (point.isJumpTrigger()) {
                    if (jumpColor != null) {
                        point.display(player, true, jumpColor);
                    } else {
                        point.display(player, true);
                    }
                } else {
                    if (pathColor != null) {
                        point.display(player, true, pathColor);
                    } else {
                        point.display(player, true);
                    }
                }
            }
            index++;
        }
    }

    public void setHiddenViewer(@NonNull Player player, boolean hidden) {
        if (hidden) this.hiddenViewers.add(player);
        else this.hiddenViewers.remove(player);
    }

    private final java.util.Map<java.util.UUID, double[]> easedColors = new java.util.concurrent.ConcurrentHashMap<>();

    @Nullable
    private Color easedColorFor(@NonNull Player player, @NonNull Location playerLocation) {
        if (this.colorCueLevel == null) return null;

        Integer targetRgb = null;
        if (!this.colorCues.isEmpty()) {
            long millis = ru.sortix.parkourbeat.levels.LightShowPositions.toTimeMillis(this.colorCueLevel, playerLocation);
            for (ru.sortix.parkourbeat.levels.settings.ParticleColorCue cue : this.colorCues) {
                if (cue.contains(millis)) {
                    targetRgb = cue.getColor() & 0xFFFFFF;
                    break;
                }
            }
        }

        double[] state = this.easedColors.get(player.getUniqueId());

        if (targetRgb == null) {
            if (state == null) return null;
            state[3] += (0.0D - state[3]) * 0.93D;
            if (state[3] < 0.05D) {
                this.easedColors.remove(player.getUniqueId());
                return null;
            }
            this.easedColors.put(player.getUniqueId(), state);
            int r0 = Math.max(0, Math.min(255, (int) Math.round(state[0])));
            int g0 = Math.max(0, Math.min(255, (int) Math.round(state[1])));
            int b0 = Math.max(0, Math.min(255, (int) Math.round(state[2])));
            return Color.fromRGB(r0, g0, b0);
        }

        double tr = (targetRgb >> 16) & 0xFF, tg = (targetRgb >> 8) & 0xFF, tb = targetRgb & 0xFF;
        if (state == null) {
            state = new double[]{tr, tg, tb, 0.0D};
        }
        state[0] += (tr - state[0]) * 0.93D;
        state[1] += (tg - state[1]) * 0.93D;
        state[2] += (tb - state[2]) * 0.93D;
        state[3] += (1.0D - state[3]) * 0.93D;
        this.easedColors.put(player.getUniqueId(), state);

        int r = Math.max(0, Math.min(255, (int) Math.round(state[0])));
        int g = Math.max(0, Math.min(255, (int) Math.round(state[1])));
        int b = Math.max(0, Math.min(255, (int) Math.round(state[2])));
        return Color.fromRGB(r, g, b);
    }

    private final java.util.Map<java.util.UUID, double[]> easedJumpColors = new java.util.concurrent.ConcurrentHashMap<>();

    @Nullable
    private Color easedJumpColorFor(@NonNull Player player, @NonNull Location playerLocation) {
        if (this.colorCueLevel == null) return null;

        Integer targetRgb = null;
        if (!this.colorCues.isEmpty()) {
            long millis = ru.sortix.parkourbeat.levels.LightShowPositions.toTimeMillis(this.colorCueLevel, playerLocation);
            for (ru.sortix.parkourbeat.levels.settings.ParticleColorCue cue : this.colorCues) {
                if (!cue.contains(millis)) continue;
                targetRgb = switch (cue.getJumpColorMode()) {
                    case SAME -> cue.getColor() & 0xFFFFFF;
                    case CUSTOM -> cue.getJumpColor() & 0xFFFFFF;
                    default -> ParticleUtils.invertRGB(Color.fromRGB(cue.getColor() & 0xFFFFFF)).asRGB();
                };
                break;
            }
        }

        double[] state = this.easedJumpColors.get(player.getUniqueId());

        if (targetRgb == null) {
            if (state == null) return null;
            state[3] += (0.0D - state[3]) * 0.93D;
            if (state[3] < 0.05D) {
                this.easedJumpColors.remove(player.getUniqueId());
                return null;
            }
            this.easedJumpColors.put(player.getUniqueId(), state);
            return toColor(state);
        }

        double tr = (targetRgb >> 16) & 0xFF, tg = (targetRgb >> 8) & 0xFF, tb = targetRgb & 0xFF;
        if (state == null) {
            state = new double[]{tr, tg, tb, 0.0D};
        }
        state[0] += (tr - state[0]) * 0.93D;
        state[1] += (tg - state[1]) * 0.93D;
        state[2] += (tb - state[2]) * 0.93D;
        state[3] += (1.0D - state[3]) * 0.93D;
        this.easedJumpColors.put(player.getUniqueId(), state);

        return toColor(state);
    }

    @NonNull
    private static Color toColor(@NonNull double[] state) {
        int r = Math.max(0, Math.min(255, (int) Math.round(state[0])));
        int g = Math.max(0, Math.min(255, (int) Math.round(state[1])));
        int b = Math.max(0, Math.min(255, (int) Math.round(state[2])));
        return Color.fromRGB(r, g, b);
    }

    private int advanceRevealedIndex(@NonNull Player player, int targetIndex) {
        ru.sortix.parkourbeat.activity.UserActivity activity = this.plugin.get(ru.sortix.parkourbeat.activity.ActivityManager.class).getActivity(player);
        if (activity instanceof ru.sortix.parkourbeat.activity.type.ReplayActivity) {
            this.revealedIndexes.put(player, targetIndex);
            return targetIndex;
        }

        Integer current = this.revealedIndexes.get(player);
        if (current == null) {
            this.revealedIndexes.put(player, targetIndex);
            return targetIndex;
        }

        int revealed = current;
        if (revealed < targetIndex) {
            int gap = targetIndex - revealed;
            int step = Math.max(REVEAL_POINTS_PER_TICK, gap / CATCH_UP_TICKS);
            revealed = Math.min(targetIndex, revealed + step);
        } else if (revealed > targetIndex) {
            revealed = targetIndex;
        }

        this.revealedIndexes.put(player, revealed);
        return revealed;
    }

    public void startSpawnParticles(@NonNull Player player) {
        // На 2D-уровне пути из частиц нет как явления: там всё задаёт линия кубика,
        // а лишние частицы только мешают читать геометрию.
        if (ru.sortix.parkourbeat.twod.TwoDManager.isTwoD(this.colorCueLevel)) return;
        this.particleViewers.add(player);
    }

    public void stopSpawnParticlesForPlayer(@NonNull Player player) {
        this.revealedIndexes.remove(player);
        this.particleViewers.remove(player);
        this.hiddenViewers.remove(player);
    }

    public void stopSpawnParticles() {
        this.revealedIndexes.clear();
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

        // Предотвращение ошибки NaN (если точки абсолютно совпадают)
        if (length > 0) {
            vector.normalize();
        }

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

        double length = startVector.distance(endVector);
        int segments = calculateSegments(length, height);

        // Распределяем контрольные точки по направлению (25% и 75% пути) вместо одной точки в центре,
        // чтобы получалась правильная плавная дуга прыжка
        Vector direction = endVector.clone().subtract(startVector);
        Vector control1 = startVector.clone().add(direction.clone().multiply(0.25)).add(new Vector(0, height, 0));
        Vector control2 = startVector.clone().add(direction.clone().multiply(0.75)).add(new Vector(0, height, 0));

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
        List<Location> result = new ArrayList<>();
        for (Vector offset : JUMP_CIRCLE_OFFSETS) {
            result.add(middle.clone().add(offset));
        }
        return result;
    }
}
