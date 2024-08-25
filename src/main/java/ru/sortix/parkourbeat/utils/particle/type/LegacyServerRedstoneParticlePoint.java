package ru.sortix.parkourbeat.utils.particle.type;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

public class LegacyServerRedstoneParticlePoint implements ParticlePoint {
    @Getter
    private final @NonNull Location location;
    private final double offsetX, offsetY, offsetZ;

    public LegacyServerRedstoneParticlePoint(@NonNull Location location, @NonNull Color color) {
        this.location = location;
        this.offsetX = color.getRed() / 255.0;
        this.offsetY = color.getGreen() / 255.0;
        this.offsetZ = color.getBlue() / 255.0;
    }

    @Override
    public void display(@NonNull Player player, boolean legacyClient) {
        player.spawnParticle(
            Particle.REDSTONE,
            this.location,
            0,
            this.offsetX, this.offsetY, this.offsetZ,
            1
        );
    }
}
