package ru.sortix.parkourbeat.player;

import lombok.NonNull;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import ru.sortix.parkourbeat.utils.text.PbText;
public class DebugModeManager implements PluginManager, Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final @NonNull ParkourBeat plugin;
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public DebugModeManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean isEnabled(@NonNull Player player) {
        if (!this.enabled.contains(player.getUniqueId())) return false;
        if (!player.hasPermission(PermissionConstants.DEBUG_MODE)) {
            this.enabled.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public boolean toggle(@NonNull Player player) {
        UUID uuid = player.getUniqueId();
        if (this.enabled.remove(uuid)) return false;
        this.enabled.add(uuid);
        return true;
    }

    public void send(@NonNull Player player, @NonNull String message) {
        if (!this.isEnabled(player)) return;
        player.sendMessage(PbText.of("&8[&7debug&8] &7" + message));
    }

    @EventHandler
    public void onQuit(@NonNull PlayerQuitEvent event) {
        this.enabled.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        this.enabled.clear();
    }
}
