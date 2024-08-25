package ru.sortix.parkourbeat.utils.particle.type;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface ParticlePoint {
    @NonNull
    Location getLocation();

    void display(@NonNull Player player, boolean legacyClient);
}
