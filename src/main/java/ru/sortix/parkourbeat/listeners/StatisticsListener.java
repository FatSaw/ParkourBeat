package ru.sortix.parkourbeat.listeners;

import lombok.NonNull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.rating.StatisticsManager;

/**
 * Держит в актуальном состоянии профиль игрока: заводит его при первом заходе,
 * обновляет ник при смене (п.11.1) и копит время на ParkourBeat по сессиям (п.9).
 * <p>
 * Само автосохранение раз в 5 минут живёт внутри {@link StatisticsManager} —
 * чтобы краш не съедал часы.
 */
public final class StatisticsListener implements Listener {
    private final @NonNull ParkourBeat plugin;

    public StatisticsListener(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void on(@NonNull PlayerJoinEvent event) {
        try {
            this.plugin.get(StatisticsManager.class).handleJoin(event.getPlayer());
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Не удалось обработать вход игрока в статистике", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void on(@NonNull PlayerQuitEvent event) {
        try {
            this.plugin.get(StatisticsManager.class).handleQuit(event.getPlayer());
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Не удалось обработать выход игрока в статистике", e);
        }
    }
}
