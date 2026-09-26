package ru.sortix.parkourbeat.world;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.sortix.parkourbeat.levels.settings.JumpEffect;

import javax.annotation.Nullable;
import java.util.logging.Level;

@UtilityClass
public class JumpEffectSender {

    public void play(@NonNull Plugin plugin, @NonNull Player player, @NonNull JumpEffect effect,
                     @Nullable String soundKey) {
        if (!player.isOnline()) return;
        try {
            switch (effect) {
                case TIME_PUSH -> {
                }
                case JUMP_AIR -> playAirParticle(player);
                case JUMP_FIRE -> playFireParticle(player);
                case JUMP_SWEEP -> playSweepParticle(player);
                case JUMP_BUBBLE -> playBubbleParticle(player);
                case JUMP_RED_SCREEN -> RedVignetteSender.flash(plugin, player);
                case SOUND -> playSoundKey(player, soundKey);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                Lang.raw(PlayerLang.of(player), "auto.jump_effect_sender.play.1") + effect
                    + Lang.raw(PlayerLang.of(player), "auto.jump_effect_sender.play.2") + player.getName(), t);
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

    private void playAirParticle(@NonNull Player player) {
        Location loc = player.getLocation().add(0, 0.5, 0);
        player.spawnParticle(Particle.CLOUD, loc, 45, 0.5, 0.2, 0.5, 1.2);
    }

    private void playSweepParticle(@NonNull Player player) {
        Location loc = frontOf(player, 1.2D, 1.1D);
        player.spawnParticle(Particle.SWEEP_ATTACK, loc, 80, 0.9, 0.7, 0.9, 0.0);
    }

    private void playBubbleParticle(@NonNull Player player) {
        Location loc = frontOf(player, 1.0D, 1.0D);
        player.spawnParticle(Particle.BUBBLE_POP, loc, 220, 0.9, 0.8, 0.9, 0.00001);
    }

    @NonNull
    private Location frontOf(@NonNull Player player, double forward, double up) {
        Location loc = player.getLocation().clone();
        org.bukkit.util.Vector dir = loc.getDirection().setY(0);
        if (dir.lengthSquared() > 0.0001) dir.normalize().multiply(forward);
        else dir.zero();
        return loc.add(dir).add(0, up, 0);
    }

    private void playFireParticle(@NonNull Player player) {
        Location loc = player.getLocation().add(0, 0.8, 0);
        player.spawnParticle(Particle.FLAME, loc, 60, 0.3, 0.3, 0.3, 1.0);
    }
}
