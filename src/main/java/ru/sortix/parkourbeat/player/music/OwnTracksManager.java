package ru.sortix.parkourbeat.player.music;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.type.editor.SelectSongMenu;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import ru.sortix.parkourbeat.utils.text.PbText;
public class OwnTracksManager implements PluginManager, Listener, PluginMessageListener {
    public static final String CHANNEL = "parkourbeat:web";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final @NonNull ParkourBeat plugin;
    private final Map<UUID, Set<String>> ownedTracks = new ConcurrentHashMap<>();
    private volatile boolean bridgeSeen = false;

    public OwnTracksManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public boolean isBridgeAvailable() {
        return this.bridgeSeen;
    }

    @NonNull
    public Set<String> getOwnedTracks(@NonNull Player player) {
        Set<String> owned = this.ownedTracks.get(player.getUniqueId());
        return owned == null ? Collections.emptySet() : owned;
    }

    public boolean owns(@NonNull Player player, @NonNull String trackId) {
        return this.getOwnedTracks(player).contains(trackId);
    }

    public void requestOwnedTracks(@NonNull Player player) {
        this.write(player, "own", null);
    }

    public void requestUploadLink(@NonNull Player player) {
        if (!this.bridgeSeen) {
            player.sendMessage(PbText.of(
                Lang.raw(PlayerLang.of(player), "auto.own_tracks_manager.request_upload_link.1")));
            return;
        }
        this.write(player, "upload", null);
    }

    public void requestDelete(@NonNull Player player, @NonNull String trackId) {
        this.write(player, "delete", trackId);
    }

    private void write(@NonNull Player player, @NonNull String action, String payload) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeUTF(action);
                if (payload != null) out.writeUTF(payload);
            }
            player.sendPluginMessage(this.plugin, CHANNEL, bytes.toByteArray());
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Unable to send web bridge request", e);
        }
    }

    @Override
    public void onPluginMessageReceived(@NonNull String channel, @NonNull Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        this.bridgeSeen = true;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            long most = in.readLong();
            long least = in.readLong();
            UUID target = new UUID(most, least);
            String action = in.readUTF();
            String payload = in.readUTF();

            this.plugin.getServer().getScheduler().runTask(this.plugin,
                () -> this.handle(target, action, payload));
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Unable to read web bridge message", e);
        }
    }

    private void handle(@NonNull UUID target, @NonNull String action, @NonNull String payload) {
        Player player = this.plugin.getServer().getPlayer(target);

        switch (action) {
            case "own": {
                Set<String> owned = new LinkedHashSet<>();
                if (!payload.isEmpty()) {
                    for (String id : payload.split("\u0000")) {
                        if (!id.isEmpty()) owned.add(id);
                    }
                }
                this.ownedTracks.put(target, owned);
                if (player != null) this.refreshMenus(player);
                return;
            }
            case "url": {
                if (player == null) return;
                player.sendMessage(Component.empty());
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.own_tracks_manager.handle.1")));
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.own_tracks_manager.handle.2")));
                player.sendMessage(Component.text(payload, NamedTextColor.LIGHT_PURPLE)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(payload))
                    .hoverEvent(HoverEvent.showText(
                        Component.text(Lang.raw(PlayerLang.of(player), "auto.own_tracks_manager.handle.3"), NamedTextColor.GRAY))));
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.own_tracks_manager.handle.4")));
                player.sendMessage(Component.empty());
                return;
            }
            case "limit": {
                // ЛИМИТ ЗАПОМИНАЕТСЯ, А НЕ ТОЛЬКО ПЕЧАТАЕТСЯ.
                //
                // Настоящий лимит живёт на прокси (max-tracks-per-player) и приезжает
                // сюда вот этим сообщением. Раньше он просто выводился в чат и тут же
                // забывался, а меню показывало число, вписанное в языковой файл руками.
                // Стоило поменять лимит на прокси - и меню начинало врать, причём узнать
                // об этом можно было только упёршись в лимит.
                rememberLimit(payload);

                if (player == null) return;
                player.sendMessage(PbText.of(
                    Lang.raw(PlayerLang.of(player), "auto.own_tracks_manager.handle.5") + payload
                        + Lang.raw(PlayerLang.of(player), "auto.own_tracks_manager.handle.6")));
                return;
            }
            case "uploaded": {
                this.prepareUploadedTrack(player, payload);
                return;
            }
            case "denied": {
                if (player == null) return;
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.own_tracks_manager.handle.7")));
                return;
            }
            default:
        }
    }

    /**
     * Прокси только положила файл в папку. Бэкенд про него ещё ничего не знает: чтобы трек
     * появился в списке, его надо запаковать в ресурспак - ровно то, что делает /updatetrack.
     * Без этого шага трек висел на диске и не показывался в меню до ручной команды.
     */
    /**
     * Сколько своих треков разрешено держать.
     * <p>
     * Минус один - пока прокси не сказала. В этом случае меню не называет числа вовсе:
     * лучше промолчать, чем назвать выдуманное.
     */
    private volatile int trackLimit = -1;

    /** Последний лимит, присланный прокси, или -1, если она ещё не говорила. */
    public int getTrackLimit() {
        return this.trackLimit;
    }

    private void rememberLimit(@NonNull String payload) {
        try {
            int value = Integer.parseInt(payload.trim());
            if (value > 0) this.trackLimit = value;
        } catch (NumberFormatException ignored) {
            // Прокси прислала не число - значит и запоминать нечего.
        }
    }

    private void prepareUploadedTrack(Player player, @NonNull String trackId) {
        if (player != null) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.own_tracks_manager.prepare_uploaded_track.1") + trackId));
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.own_tracks_manager.prepare_uploaded_track.2")));
        }

        MusicTracksManager tracks;
        try {
            tracks = this.plugin.get(MusicTracksManager.class);
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Unable to prepare uploaded track " + trackId, e);
            return;
        }

        try {
            tracks.updateTrackArchive(null, trackId, true);
        } catch (Throwable t) {
            this.plugin.getLogger().log(Level.WARNING, "Unable to pack uploaded track " + trackId, t);
        }

        this.awaitTrack(player, trackId, tracks, 0);
    }

    /**
     * Упаковка асинхронная и колбэка наружу не отдаёт, поэтому просто ждём появления трека
     * в списке. Полминуты с запасом хватает даже на длинный файл.
     */
    private void awaitTrack(Player player, @NonNull String trackId,
                            @NonNull MusicTracksManager tracks, int attempt) {
        if (attempt > 30) {
            this.plugin.getLogger().warning("Загруженный трек \"" + trackId
                + Lang.raw(PlayerLang.of(player), "auto.own_tracks_manager.await_track.1"));
            if (player != null && player.isOnline()) {
                player.sendMessage(PbText.of(
                    Lang.raw(PlayerLang.of(player), "auto.own_tracks_manager.await_track.2")));
            }
            return;
        }

        this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            if (tracks.getPlatform().getTrackById(trackId) == null) {
                this.awaitTrack(player, trackId, tracks, attempt + 1);
                return;
            }

            if (player != null && player.isOnline()) {
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.own_tracks_manager.await_track.3")));
                this.requestOwnedTracks(player);
                this.refreshMenus(player);
            }
        }, 20L);
    }

    private void refreshMenus(@NonNull Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof SelectSongMenu menu) {
            menu.updateAllItems();
        }
    }

    @EventHandler
    public void onQuit(@NonNull PlayerQuitEvent event) {
        this.ownedTracks.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        try {
            this.plugin.getServer().getMessenger().unregisterIncomingPluginChannel(this.plugin, CHANNEL);
            this.plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(this.plugin, CHANNEL);
        } catch (Throwable ignored) {
        }
        this.ownedTracks.clear();
    }
}
