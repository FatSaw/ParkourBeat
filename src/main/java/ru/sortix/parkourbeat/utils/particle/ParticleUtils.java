package ru.sortix.parkourbeat.utils.particle;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.utils.java.ClassUtils;
import ru.sortix.parkourbeat.utils.particle.type.LegacyServerRedstoneParticlePoint;
import ru.sortix.parkourbeat.utils.particle.type.ModernServerRedstoneParticlePoint;
import ru.sortix.parkourbeat.utils.particle.type.ParticlePoint;

@UtilityClass
public class ParticleUtils {
    private final boolean dustOptionsSupport = ClassUtils.isClassPresent("org.bukkit.Particle$DustOptions");

    @NonNull
    public ParticlePoint createRedstoneParticlePoint(@NonNull Location location, @NonNull Color color, float size) {
        if (dustOptionsSupport) {
            return new ModernServerRedstoneParticlePoint(location, color, size);
        } else {
            return new LegacyServerRedstoneParticlePoint(location, color); // size is unsupported on legacy servers
        }
    }

    public void displayRedstoneParticles(
        boolean legacyClient,
        @NonNull Player player,
        @NonNull Iterable<ParticlePoint> points,
        double maxDistanceSquared
    ) {
        Location playerLoc = player.getLocation();
        for (ParticlePoint point : points) {
            if (point.getLocation().distanceSquared(playerLoc) > maxDistanceSquared) continue;
            point.display(player, legacyClient);
        }
    }

    @NonNull
    public Color invertRGB(@NonNull Color color) {
        return Color.fromRGB(0xFFFFFF - color.asRGB());
    }

    @NonNull
    public Color invertARGB(@NonNull Color color) { // untested
        return Color.fromRGB((0xFFFFFF - color.asRGB()) | 0xFF000000);
    }
}
