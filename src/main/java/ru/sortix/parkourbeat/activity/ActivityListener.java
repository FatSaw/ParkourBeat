package ru.sortix.parkourbeat.activity;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

@RequiredArgsConstructor
public class ActivityListener implements Listener {
    private final @NonNull ActivityManager manager;

    /**
     * Активность назначается сразу, синхронно. Откладывать нельзя: между телепортом и
     * следующим тиком игрок оказался бы на уровне вообще без активности, а половина
     * обработчиков считает такое состояние ошибкой ("Произошла техническая ошибка")
     * и вдобавок не успевали включиться частицы.
     * От рекурсии телепортов защищается сама активность - см. SpectateActivity.setTargetPlayer().
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;
        if (event.getFrom().getWorld() == to.getWorld()) return;
        this.manager.updateTargetLocationActivity(event.getPlayer(), to.getWorld());
    }

    @EventHandler
    public void on(PlayerQuitEvent event) {
        this.manager.switchActivity(event.getPlayer(), null, null);
        this.manager.getPacketsAdapter().onPlayerQuit(event.getPlayer());
    }
}
