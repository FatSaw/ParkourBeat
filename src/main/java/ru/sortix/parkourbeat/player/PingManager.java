package ru.sortix.parkourbeat.player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PingManager implements PluginManager, Listener {
    private final @NonNull ParkourBeat plugin;
    private final ProtocolManager protocolManager;
    private final BukkitTask pingTask;
    private final PacketAdapter adapter;

    private final Map<UUID, Long> lastPingTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> expectedIds = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerPings = new ConcurrentHashMap<>();

    public PingManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();

        this.adapter = new PacketAdapter(plugin, PacketType.Play.Client.KEEP_ALIVE) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (player == null) return;
                UUID uuid = player.getUniqueId();
                long receivedId = event.getPacket().getLongs().read(0);

                Long expectedId = expectedIds.get(uuid);
                if (expectedId != null && expectedId == receivedId) {
                    event.setCancelled(true);
                    expectedIds.remove(uuid);
                    Long sentTime = lastPingTimes.remove(uuid);
                    if (sentTime != null) {
                        int ping = (int) (System.currentTimeMillis() - sentTime);
                        playerPings.put(uuid, ping);
                    }
                }
            }
        };
        this.protocolManager.addPacketListener(this.adapter);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        this.pingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                this.sendPing(player);
            }
        }, 20L, 20L);
    }

    private void sendPing(Player player) {
        long id = -ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

        UUID uuid = player.getUniqueId();
        this.expectedIds.put(uuid, id);
        this.lastPingTimes.put(uuid, System.currentTimeMillis());

        PacketContainer packet = this.protocolManager.createPacket(PacketType.Play.Server.KEEP_ALIVE);
        packet.getLongs().write(0, id);
        try {
            this.protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to send keep alive to " + player.getName());
        }
    }

    public int getPing(Player player) {
        return this.playerPings.getOrDefault(player.getUniqueId(), player.getPing());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        this.lastPingTimes.remove(uuid);
        this.expectedIds.remove(uuid);
        this.playerPings.remove(uuid);
    }

    @Override
    public void disable() {
        if (this.pingTask != null) this.pingTask.cancel();
        if (this.adapter != null) this.protocolManager.removePacketListener(this.adapter);
        HandlerList.unregisterAll(this);
        this.lastPingTimes.clear();
        this.expectedIds.clear();
        this.playerPings.clear();
    }
}
