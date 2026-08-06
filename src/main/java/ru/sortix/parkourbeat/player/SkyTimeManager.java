package ru.sortix.parkourbeat.player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import lombok.NonNull;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bukkit flushes per-player time only with the vanilla broadcast, which happens once every
 * twenty ticks and carries a positive day time. A positive value tells the client to run its
 * own day cycle again, so the vanilla packet and a frozen lightshow sky fought each other and
 * the sky visibly jumped back and forth.
 *
 * Every outgoing time packet is rewritten here while a player has a frozen sky, and the
 * lightshow pushes its own packet on top of that as often as it needs.
 */
public class SkyTimeManager implements PluginManager {
    private final @NonNull ParkourBeat plugin;
    private final Map<UUID, Long> frozenTimes = new ConcurrentHashMap<>();
    private final PacketAdapter adapter;

    public SkyTimeManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.adapter = new PacketAdapter(plugin, PacketType.Play.Server.UPDATE_TIME) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                if (player == null) return;
                Long frozen = SkyTimeManager.this.frozenTimes.get(player.getUniqueId());
                if (frozen == null) return;
                try {
                    event.getPacket().getLongs().write(0, 0L);
                    event.getPacket().getLongs().write(1, toFrozenValue(frozen));
                } catch (Exception ignored) {
                }
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(this.adapter);
    }

    /**
     * The client fades rain out unless it is also told how strong it is, which is why the
     * plain begin raining packet only showed weather for about a second.
     */
    public void sendWeather(@NonNull Player player, boolean raining) {
        this.sendGameState(player, raining ? 2 : 1, 0.0F);
        this.sendGameState(player, 7, raining ? 1.0F : 0.0F);
        this.sendGameState(player, 8, 0.0F);
    }

    private void sendGameState(@NonNull Player player, int reason, float value) {
        try {
            PacketContainer packet = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.GAME_STATE_CHANGE);
            try {
                packet.getGameStateIDs().write(0, reason);
            } catch (Exception e) {
                packet.getIntegers().write(0, reason);
            }
            packet.getFloat().write(0, value);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            this.plugin.getLogger().fine("Unable to send weather packet to " + player.getName());
        }
    }

    private static long toFrozenValue(long dayTime) {
        long normalized = Math.abs(dayTime) % 24000L;
        return normalized == 0L ? -24000L : -normalized;
    }

    /**
     * Holds the client sky at the given day time and pushes it out immediately.
     */
    public void freeze(@NonNull Player player, long dayTime) {
        long normalized = Math.abs(dayTime) % 24000L;
        this.frozenTimes.put(player.getUniqueId(), normalized);
        this.sendPacket(player, toFrozenValue(normalized));
    }

    public void unfreeze(@NonNull Player player) {
        if (this.frozenTimes.remove(player.getUniqueId()) == null) return;
        player.resetPlayerTime();
        this.sendPacket(player, player.getWorld().getTime());
    }

    private void sendPacket(@NonNull Player player, long dayTime) {
        try {
            PacketContainer packet = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.UPDATE_TIME);
            packet.getLongs().write(0, 0L);
            packet.getLongs().write(1, dayTime);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            this.plugin.getLogger().fine("Unable to send time packet to " + player.getName());
        }
    }

    @Override
    public void disable() {
        ProtocolLibrary.getProtocolManager().removePacketListener(this.adapter);
        this.frozenTimes.clear();
    }
}
