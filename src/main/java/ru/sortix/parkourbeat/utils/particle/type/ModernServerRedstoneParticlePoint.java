package ru.sortix.parkourbeat.utils.particle.type;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

public class ModernServerRedstoneParticlePoint implements ParticlePoint {
    @Getter
    private final @NonNull Location location;
    private final double offsetX, offsetY, offsetZ;
    private final @NonNull Particle.DustOptions legacyClientsDustOptions; // 1.12.2-
    private final @NonNull Particle.DustOptions modernClientsDustOptions; // 1.13+

    public ModernServerRedstoneParticlePoint(@NonNull Location location, @NonNull Color color, float size) {
        this.location = location;
        this.offsetX = color.getRed() / 255.0;
        this.offsetY = color.getGreen() / 255.0;
        this.offsetZ = color.getBlue() / 255.0;
        this.legacyClientsDustOptions = new Particle.DustOptions(
            // legacy clients do not display correct color if red == 0
            color.getRed() != 0 ? color : Color.fromRGB(1, color.getGreen(), color.getBlue()),
            // not actually a size, but colors contrast
            1.0f
        );
        this.modernClientsDustOptions = new Particle.DustOptions(
            color,
            size
        );
    }

    @Override
    public void display(@NonNull Player player, boolean legacyClient) {
        player.spawnParticle(
            Particle.REDSTONE,
            this.location,
            0,
            this.offsetX, this.offsetY, this.offsetZ,
            1,
            legacyClient ? this.legacyClientsDustOptions : this.modernClientsDustOptions
        );
    }
}
