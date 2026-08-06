package ru.sortix.parkourbeat.world;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.sortix.parkourbeat.levels.settings.JumpEffect;

import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Applies a single client-side jump effect, per player. Nothing here touches the server world.
 */
@UtilityClass
public class JumpEffectSender {

    public void play(@NonNull Plugin plugin, @NonNull Player player, @NonNull JumpEffect effect,
                     @Nullable String soundKey) {
        if (!player.isOnline()) return;
        try {
            switch (effect) {
                case TIME_PUSH -> {
                    // handled in PlayActivity via SkyTimeManager, nothing to do here
                }
                case JUMP_AIR -> playAirParticle(player);
                case JUMP_FIRE -> playFireParticle(player);
                case SOUND -> playSoundKey(player, soundKey);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                "[ParkourBeat] JumpEffectSender: не удалось применить эффект " + effect
                    + " игроку " + player.getName(), t);
        }
    }

    private void playSoundKey(@NonNull Player player, @Nullable String soundKey) {
        if (soundKey == null || soundKey.isEmpty()) return;
        float pitch = 1.0f;
        String key = soundKey;
        int at = soundKey.indexOf('@');
        if (at > 0) {
            key = soundKey.substring(0, at);
            try {
                pitch = Float.parseFloat(soundKey.substring(at + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        player.playSound(player.getLocation(), key, 1.0f, pitch);
    }

    /** "Воздушный" — cloud ~ ~0.5 ~ 0.5 0.2 0.5 1.2 45 */
    private void playAirParticle(@NonNull Player player) {
        Location loc = player.getLocation().add(0, 0.5, 0);
        player.spawnParticle(Particle.CLOUD, loc, 45, 0.5, 0.2, 0.5, 1.2);
    }

    /** "Огненный" — flame ~ ~0.8 ~ 0.3 0.3 0.3 1 60 */
    private void playFireParticle(@NonNull Player player) {
        Location loc = player.getLocation().add(0, 0.8, 0);
        player.spawnParticle(Particle.FLAME, loc, 60, 0.3, 0.3, 0.3, 1.0);
    }
}
