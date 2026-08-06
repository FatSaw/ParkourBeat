package ru.sortix.parkourbeat.player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import lombok.NonNull;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Единственное место, где кто-либо кого-либо прячет
 */
public class PlayersVisibilityManager implements PluginManager, Listener {

    private static final long WATCHDOG_PERIOD_TICKS = 20L * 5L;

    private static final boolean PROTOCOL_LIB_AVAILABLE;

    static {
        boolean available;
        try {
            Class.forName("com.comphenix.protocol.ProtocolLibrary");
            available = true;
        } catch (Throwable t) {
            available = false;
        }
        PROTOCOL_LIB_AVAILABLE = available;
    }

    private final @NonNull ParkourBeat plugin;
    /** Кто сейчас находится в забеге и не должен видеть остальных. */
    private final Set<UUID> hiddenViewers = ConcurrentHashMap.newKeySet();
    private BukkitTask watchdog;

    public PlayersVisibilityManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.watchdog = plugin.getServer().getScheduler().runTaskTimer(
            plugin, this::watchdogTick, WATCHDOG_PERIOD_TICKS, WATCHDOG_PERIOD_TICKS);
    }

    // ------------------------------------------------------------------ публичное API

    public void hideOthersFor(@NonNull Player viewer) {
        this.hiddenViewers.add(viewer.getUniqueId());
        for (Player other : this.plugin.getServer().getOnlinePlayers()) {
            if (other.getUniqueId().equals(viewer.getUniqueId())) continue;
            this.hide(viewer, other);
        }
    }

    public void restoreFor(@NonNull Player viewer) {
        this.hiddenViewers.remove(viewer.getUniqueId());
        if (!viewer.isOnline()) return;
        for (Player other : this.plugin.getServer().getOnlinePlayers()) {
            if (other.getUniqueId().equals(viewer.getUniqueId())) continue;
            this.show(viewer, other);
        }
    }

    public boolean isHiding(@NonNull Player viewer) {
        return this.hiddenViewers.contains(viewer.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.hiddenViewers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        for (UUID viewerId : this.hiddenViewers) {
            Player viewer = this.plugin.getServer().getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) this.hide(viewer, joined);
        }
        this.hiddenViewers.remove(joined.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        this.restoreFor(event.getPlayer());
    }

    // ------------------------------------------------------------------ сторож

    private void watchdogTick() {
        try {
            for (Player viewer : this.plugin.getServer().getOnlinePlayers()) {
                boolean shouldHide = this.hiddenViewers.contains(viewer.getUniqueId());
                for (Player other : this.plugin.getServer().getOnlinePlayers()) {
                    if (other.getUniqueId().equals(viewer.getUniqueId())) continue;
                    boolean canSee = viewer.canSee(other);
                    if (shouldHide) {
                        if (canSee) this.hide(viewer, other);
                        else this.sendTabEntry(viewer, other);
                    } else if (!canSee) {
                        // Никто не просил прятать — значит это залипшее состояние.
                        this.show(viewer, other);
                    }
                }
            }
        } catch (Throwable t) {
            this.plugin.getLogger().log(Level.WARNING, "Ошибка в стороже видимости игроков", t);
        }
    }

    // ------------------------------------------------------------------ низкий уровень

    private void hide(@NonNull Player viewer, @NonNull Player target) {
        try {
            viewer.hidePlayer(this.plugin, target);
        } catch (Throwable t) {
            return;
        }
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.sendTabEntry(viewer, target));
    }

    private void show(@NonNull Player viewer, @NonNull Player target) {
        try {
            viewer.showPlayer(this.plugin, target);
        } catch (Throwable ignored) {
        }
    }

    private void sendTabEntry(@NonNull Player viewer, @NonNull Player target) {
        if (!PROTOCOL_LIB_AVAILABLE) return;
        if (!viewer.isOnline() || !target.isOnline()) return;
        try {
            ProtocolManager manager = ProtocolLibrary.getProtocolManager();
            PacketContainer packet = manager.createPacket(PacketType.Play.Server.PLAYER_INFO);
            packet.getPlayerInfoAction().write(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER);
            net.kyori.adventure.text.Component tabName = target.playerListName();
            if (tabName == null) tabName = net.kyori.adventure.text.Component.text(target.getName());
            String jsonName = GsonComponentSerializer.gson().serialize(tabName);

            PlayerInfoData data = new PlayerInfoData(
                WrappedGameProfile.fromPlayer(target),
                Math.max(0, this.plugin.get(ru.sortix.parkourbeat.player.PingManager.class).getPing(target)),
                EnumWrappers.NativeGameMode.fromBukkit(target.getGameMode()),
                WrappedChatComponent.fromJson(jsonName)
            );
            List<PlayerInfoData> list = Collections.singletonList(data);
            packet.getPlayerInfoDataLists().write(0, list);

            manager.sendServerPacket(viewer, packet);
        } catch (Throwable t) {
        }
    }

    @Override
    public void disable() {
        if (this.watchdog != null) {
            this.watchdog.cancel();
            this.watchdog = null;
        }
        HandlerList.unregisterAll(this);
        for (Player viewer : this.plugin.getServer().getOnlinePlayers()) {
            this.hiddenViewers.remove(viewer.getUniqueId());
            for (Player other : this.plugin.getServer().getOnlinePlayers()) {
                if (other.getUniqueId().equals(viewer.getUniqueId())) continue;
                this.show(viewer, other);
            }
        }
        this.hiddenViewers.clear();
    }
}
