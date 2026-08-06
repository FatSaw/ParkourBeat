package ru.sortix.parkourbeat.utils.particle.type;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

public class ModernServerRedstoneParticlePoint implements ParticlePoint {
    @Getter
    private final @NonNull Location location;
    private final double offsetX, offsetY, offsetZ;
    private final @NonNull Particle.DustOptions legacyClientsDustOptions;
    private final @NonNull Particle.DustOptions modernClientsDustOptions;

    @Getter
    @Setter
    private boolean jumpTrigger = false;

    public ModernServerRedstoneParticlePoint(@NonNull Location location, @NonNull Color color, float size) {
        this.location = location;
        this.offsetX = color.getRed() / 255.0;
        this.offsetY = color.getGreen() / 255.0;
        this.offsetZ = color.getBlue() / 255.0;
        this.legacyClientsDustOptions = new Particle.DustOptions(
            color.getRed() != 0 ? color : Color.fromRGB(1, color.getGreen(), color.getBlue()),
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

    @Override
    public void display(@NonNull Player player, boolean legacyClient, @NonNull Color colorOverride) {
        if (legacyClient) {
            Color safe = colorOverride.getRed() != 0
                ? colorOverride
                : Color.fromRGB(1, colorOverride.getGreen(), colorOverride.getBlue());
            player.spawnParticle(
                Particle.REDSTONE,
                this.location,
                0,
                safe.getRed() / 255.0, safe.getGreen() / 255.0, safe.getBlue() / 255.0,
                1,
                new Particle.DustOptions(safe, 1.0f)
            );
        } else {
            player.spawnParticle(
                Particle.REDSTONE,
                this.location,
                0,
                colorOverride.getRed() / 255.0, colorOverride.getGreen() / 255.0, colorOverride.getBlue() / 255.0,
                1,
                new Particle.DustOptions(colorOverride, this.modernClientsDustOptions.getSize())
            );
        }
    }
}
