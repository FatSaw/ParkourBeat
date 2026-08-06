package ru.sortix.parkourbeat.utils.particle.type;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface ParticlePoint {
    @NonNull
    Location getLocation();

    void display(@NonNull Player player, boolean legacyClient);

    default void display(@NonNull Player player, boolean legacyClient, @NonNull org.bukkit.Color colorOverride) {
        this.display(player, legacyClient);
    }

    default boolean isJumpTrigger() {
        return false;
    }

    default void setJumpTrigger(boolean jumpTrigger) {
    }
}
