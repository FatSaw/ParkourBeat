package ru.sortix.parkourbeat.player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import lombok.NonNull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class PingManager implements PluginManager, Listener {
    private enum Mode {
        PING_PONG,
        TRANSACTION,
        PASSIVE
    }

    private static final long PROBE_PERIOD_TICKS = 10L;
    private static final long JOIN_GRACE_MILLIS = 3_000L;
    private static final long PROBE_EXPIRY_MILLIS = 10_000L;
    private static final int MAX_UNANSWERED = 4;
    private static final int SLOTS = 8;
    private static final double SMOOTHING = 0.55D;
    private static final long MAX_REASONABLE_PING = 60_000L;
    private static final int MAX_SEND_FAILURES = 20;

    private static final class Probes {
        private final int[] ids = new int[SLOTS];
        private final long[] times = new long[SLOTS];
        private int cursor = 0;

        private synchronized void add(int id, long time) {
            this.ids[this.cursor] = id;
            this.times[this.cursor] = time;
            this.cursor = (this.cursor + 1) % SLOTS;
        }

        private synchronized long take(int id) {
            for (int i = 0; i < SLOTS; i++) {
                if (this.ids[i] != id || this.times[i] == 0L) continue;
                long time = this.times[i];
                this.ids[i] = 0;
                this.times[i] = 0L;
                return time;
            }
            return 0L;
        }

        private synchronized int unanswered(long now) {
            int count = 0;
            for (int i = 0; i < SLOTS; i++) {
                if (this.times[i] == 0L) continue;
                if (now - this.times[i] > PROBE_EXPIRY_MILLIS) {
                    this.ids[i] = 0;
                    this.times[i] = 0L;
                    continue;
                }
                count++;
            }
            return count;
        }
    }

    private final @NonNull ParkourBeat plugin;
    private final @NonNull Mode mode;
    private final @Nullable ProtocolManager protocolManager;
    private final @Nullable PacketType outgoingType;
    private final @Nullable PacketAdapter adapter;
    private final @Nullable BukkitTask probeTask;

    private final Map<UUID, Probes> probes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();
    private final Map<UUID, Double> pings = new ConcurrentHashMap<>();
    private volatile int sendFailures = 0;
    private volatile boolean probingDisabled = false;

    public PingManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        Mode mode = Mode.PASSIVE;
        PacketType outgoing = null;
        PacketType incoming = null;
        ProtocolManager protocolManager = null;
        PacketAdapter adapter = null;

        try {
            protocolManager = ProtocolLibrary.getProtocolManager();

            PacketType serverPing = type(PacketType.Play.Server.class, "PING");
            PacketType clientPong = type(PacketType.Play.Client.class, "PONG");
            PacketType serverTransaction = type(PacketType.Play.Server.class, "TRANSACTION");
            PacketType clientTransaction = type(PacketType.Play.Client.class, "TRANSACTION");

            if (serverPing != null && clientPong != null) {
                mode = Mode.PING_PONG;
                outgoing = serverPing;
                incoming = clientPong;
            } else if (serverTransaction != null && clientTransaction != null) {
                mode = Mode.TRANSACTION;
                outgoing = serverTransaction;
                incoming = clientTransaction;
            } else {
                outgoing = PacketType.Play.Server.KEEP_ALIVE;
                incoming = PacketType.Play.Client.KEEP_ALIVE;
            }

            final Mode finalMode = mode;
            adapter = new PacketAdapter(plugin, outgoing, incoming) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    if (finalMode == Mode.PASSIVE) PingManager.this.onKeepAliveSent(event);
                }

                @Override
                public void onPacketReceiving(PacketEvent event) {
                    if (finalMode == Mode.PASSIVE) PingManager.this.onKeepAliveReceived(event);
                    else PingManager.this.onProbeResponse(event);
                }
            };
            protocolManager.addPacketListener(adapter);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Unable to hook ping packets", t);
            mode = Mode.PASSIVE;
            protocolManager = null;
            outgoing = null;
            adapter = null;
        }

        this.mode = mode;
        this.protocolManager = protocolManager;
        this.outgoingType = mode == Mode.PASSIVE ? null : outgoing;
        this.adapter = adapter;

        if (this.mode == Mode.PASSIVE || this.protocolManager == null) {
            this.probeTask = null;
        } else {
            this.probeTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::probeAll, PROBE_PERIOD_TICKS, PROBE_PERIOD_TICKS);
        }

        plugin.getLogger().info("Ping measurement mode: " + this.mode.name());
    }

    @Nullable
    private static PacketType type(@NonNull Class<?> holder, @NonNull String name) {
        try {
            Object value = holder.getField(name).get(null);
            if (!(value instanceof PacketType)) return null;
            PacketType type = (PacketType) value;
            return type.isSupported() ? type : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private void probeAll() {
        if (this.probingDisabled) return;

        long now = System.currentTimeMillis();
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            Long joined = this.joinedAt.get(uuid);
            if (joined != null && now - joined < JOIN_GRACE_MILLIS) continue;

            Probes slots = this.probes.computeIfAbsent(uuid, key -> new Probes());
            if (slots.unanswered(now) >= MAX_UNANSWERED) continue;

            this.probe(player, slots, now);
        }
    }

    private void probe(@NonNull Player player, @NonNull Probes slots, long now) {
        ProtocolManager protocolManager = this.protocolManager;
        PacketType outgoing = this.outgoingType;
        if (protocolManager == null || outgoing == null) return;

        int id = -ThreadLocalRandom.current().nextInt(1, Short.MAX_VALUE);

        try {
            PacketContainer packet = protocolManager.createPacket(outgoing);
            if (this.mode == Mode.PING_PONG) {
                packet.getIntegers().write(0, id);
            } else {
                packet.getIntegers().write(0, 0);
                packet.getShorts().write(0, (short) id);
                packet.getBooleans().write(0, false);
            }
            slots.add(id, now);
            protocolManager.sendServerPacket(player, packet);
            this.sendFailures = 0;
        } catch (Throwable t) {
            slots.take(id);
            if (++this.sendFailures >= MAX_SEND_FAILURES) {
                this.probingDisabled = true;
                this.plugin.getLogger().warning(
                    "Ping probing disabled after repeated failures, falling back to server ping");
            }
        }
    }

    private void onProbeResponse(@NonNull PacketEvent event) {
        try {
            Player player = event.getPlayer();
            if (player == null) return;

            int received = this.mode == Mode.PING_PONG
                ? event.getPacket().getIntegers().read(0)
                : event.getPacket().getShorts().read(0);

            if (received >= 0) return;

            Probes slots = this.probes.get(player.getUniqueId());
            if (slots == null) return;

            long sentAt = slots.take(received);
            if (sentAt == 0L) return;

            event.setCancelled(true);
            this.record(player.getUniqueId(), System.currentTimeMillis() - sentAt);
        } catch (Throwable ignored) {
        }
    }

    private void onKeepAliveSent(@NonNull PacketEvent event) {
        try {
            Player player = event.getPlayer();
            if (player == null) return;

            long id = event.getPacket().getLongs().read(0);
            Probes slots = this.probes.computeIfAbsent(player.getUniqueId(), key -> new Probes());
            slots.add((int) id, System.currentTimeMillis());
        } catch (Throwable ignored) {
        }
    }

    private void onKeepAliveReceived(@NonNull PacketEvent event) {
        try {
            Player player = event.getPlayer();
            if (player == null) return;

            long id = event.getPacket().getLongs().read(0);
            Probes slots = this.probes.get(player.getUniqueId());
            if (slots == null) return;

            long sentAt = slots.take((int) id);
            if (sentAt == 0L) return;

            this.record(player.getUniqueId(), System.currentTimeMillis() - sentAt);
        } catch (Throwable ignored) {
        }
    }

    private void record(@NonNull UUID uuid, long rtt) {
        if (rtt < 0L || rtt > MAX_REASONABLE_PING) return;
        this.pings.merge(uuid, (double) rtt,
            (previous, current) -> previous + (current - previous) * SMOOTHING);
    }

    private static int vanillaPing(@NonNull Player player) {
        try {
            return player.getPing();
        } catch (Throwable t) {
            return 0;
        }
    }

    public int getPing(@NonNull Player player) {
        Double value = this.pings.get(player.getUniqueId());
        if (value != null) return (int) Math.round(value);
        return Math.max(0, vanillaPing(player));
    }

    @EventHandler
    public void onJoin(@NonNull PlayerJoinEvent event) {
        this.joinedAt.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(@NonNull PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        this.probes.remove(uuid);
        this.joinedAt.remove(uuid);
        this.pings.remove(uuid);
    }

    @Override
    public void disable() {
        if (this.probeTask != null && !this.probeTask.isCancelled()) this.probeTask.cancel();
        if (this.protocolManager != null && this.adapter != null) {
            try {
                this.protocolManager.removePacketListener(this.adapter);
            } catch (Throwable ignored) {
            }
        }
        HandlerList.unregisterAll(this);
        this.probes.clear();
        this.joinedAt.clear();
        this.pings.clear();
    }
}
