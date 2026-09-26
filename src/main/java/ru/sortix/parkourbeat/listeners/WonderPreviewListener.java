package ru.sortix.parkourbeat.listeners;

import lombok.NonNull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.type.editor.WonderPreview;

/** Левый клик во время предпросмотра возвращает строителя в меню, откуда он вышел. */
public class WonderPreviewListener implements Listener {

    private final @NonNull ParkourBeat plugin;

    public WonderPreviewListener(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(@NonNull PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;
        if (!WonderPreview.isActive(event.getPlayer())) return;

        event.setCancelled(true);
        WonderPreview.handleLeftClick(this.plugin, event.getPlayer());
    }

    @EventHandler
    public void onQuit(@NonNull PlayerQuitEvent event) {
        WonderPreview.stop(this.plugin, event.getPlayer(), false);
    }
}
