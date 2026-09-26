package ru.sortix.parkourbeat.levels;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.levels.settings.FallZone;

import javax.annotation.Nullable;
import java.util.List;

@UtilityClass
public class FallZoneRenderer {
    public static final int PREVIEW_TICKS = 60;
    private static final double STEP = 2.0D;
    private static final double VIEW_RADIUS = 40.0D;

    public int getDeathY(@NonNull Level level, double distanceMillis, int fallback) {
        FallZone zone = findZone(level, distanceMillis);
        return zone == null ? fallback : zone.getDeathY();
    }

    public int resolveFallHeight(@NonNull Level level, @NonNull org.bukkit.entity.Player player, int fallback) {
        if (level.getLightShow().getFallZones().isEmpty()) return fallback;
        try {
            ru.sortix.parkourbeat.ParkourBeat plugin = ru.sortix.parkourbeat.ParkourBeat.getPlugin(ru.sortix.parkourbeat.ParkourBeat.class);
            ru.sortix.parkourbeat.activity.UserActivity activity = plugin.get(ru.sortix.parkourbeat.activity.ActivityManager.class).getActivity(player);
            int timeMillis;
            if (activity instanceof ru.sortix.parkourbeat.activity.type.PlayActivity playActivity) {
                timeMillis = (int) playActivity.getGame().getSongTimeMillis();
            } else if (activity instanceof ru.sortix.parkourbeat.activity.type.EditActivity editActivity && editActivity.isTesting()) {
                timeMillis = (int) editActivity.getTestingActivity().getGame().getSongTimeMillis();
            } else {
                timeMillis = LightShowPositions.toTimeMillis(level, player.getLocation());
            }
            return getDeathY(level, timeMillis, fallback);
        } catch (Exception e) {
            return fallback;
        }
    }

    public boolean isBelowDeathLine(@NonNull Level level, @NonNull org.bukkit.entity.Player player, int fallback) {
        if (player.getWorld() != level.getWorld()) return false;
        int deathY = resolveFallHeight(level, player, fallback);
        return player.getLocation().getY() < deathY;
    }

    @Nullable
    public FallZone findZone(@NonNull Level level, double timeMillis) {
        List<FallZone> zones = level.getLightShow().getFallZones();
        for (FallZone zone : zones) {
            if (!zone.isEnabled()) continue;
            if (zone.contains((long) timeMillis)) return zone;
        }
        return null;
    }

    public int getDefaultDeathY(@NonNull Level level) {
        Integer customY = level.getLightShow().getDefaultDeathY();
        if (customY != null) return customY;
        return level.getLevelSettings().getWorldSettings().getMinWorldHeight() - 1;
    }

    public void preview(@NonNull Plugin plugin, @NonNull Player player, @NonNull Level level) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || this.ticks >= PREVIEW_TICKS) {
                    this.cancel();
                    return;
                }
                this.ticks += 5;
                drawGrid(player, level);
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void drawGrid(@NonNull Player player, @NonNull Level level) {
        Location origin = player.getLocation();
        Vector min = level.getCuboid().getMin();
        Vector max = level.getCuboid().getMax();

        double fromX = Math.max(min.getX(), origin.getX() - VIEW_RADIUS);
        double toX = Math.min(max.getX(), origin.getX() + VIEW_RADIUS);
        double fromZ = Math.max(min.getZ(), origin.getZ() - VIEW_RADIUS);
        double toZ = Math.min(max.getZ(), origin.getZ() + VIEW_RADIUS);

        int defaultY = getDefaultDeathY(level);

        for (double x = fromX; x <= toX; x += STEP) {
            for (double z = fromZ; z <= toZ; z += STEP) {
                int timeMillis = LightShowPositions.toTimeMillis(level, new Vector(x, origin.getY(), z));
                FallZone zone = findZone(level, timeMillis);
                int y = zone == null ? defaultY : zone.getDeathY();
                player.spawnParticle(Particle.DAMAGE_INDICATOR,
                    new Location(level.getWorld(), x, y + 0.5D, z), 1, 0, 0, 0, 0);
            }
        }
    }
}
