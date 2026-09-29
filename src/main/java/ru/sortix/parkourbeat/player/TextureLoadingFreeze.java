package ru.sortix.parkourbeat.player;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TextureLoadingFreeze implements PluginManager, Listener {
    private static final long MAX_FREEZE_MILLIS = 30_000L;

    private final @NonNull ParkourBeat plugin;
    private final Map<UUID, Long> frozen = new ConcurrentHashMap<>();

    public TextureLoadingFreeze(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void freeze(@NonNull Player player) {
        this.frozen.put(player.getUniqueId(), System.currentTimeMillis());

        player.showTitle(Title.title(
            Component.text(Lang.raw(PlayerLang.of(player), "auto.texture_loading_freeze.freeze.1"), NamedTextColor.AQUA),
            Component.text(Lang.raw(PlayerLang.of(player), "auto.texture_loading_freeze.freeze.2"), NamedTextColor.GRAY),
            Title.Times.of(Duration.ofMillis(200), Duration.ofSeconds(30), Duration.ofMillis(300))));
    }

    public void unfreeze(@NonNull Player player) {
        if (this.frozen.remove(player.getUniqueId()) == null) return;
        player.clearTitle();
    }

    public boolean isFrozen(@NonNull Player player) {
        Long since = this.frozen.get(player.getUniqueId());
        if (since == null) return false;

        // Страховка: если ответ так и не пришёл, игрок не остаётся замороженным навсегда.
        if (System.currentTimeMillis() - since > MAX_FREEZE_MILLIS) {
            this.unfreeze(player);
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(@NonNull PlayerMoveEvent event) {
        if (!this.isFrozen(event.getPlayer())) return;

        // Поворот головы оставляем, двигаться с места не даём.
        if (event.getFrom().getX() == event.getTo().getX()
            && event.getFrom().getY() == event.getTo().getY()
            && event.getFrom().getZ() == event.getTo().getZ()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(@NonNull PlayerQuitEvent event) {
        this.frozen.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        this.frozen.clear();
    }
}
