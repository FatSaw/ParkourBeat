package ru.sortix.parkourbeat.packrelay.web;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class WebBridge {
    public static final ChannelIdentifier CHANNEL =
        MinecraftChannelIdentifier.create("parkourbeat", "web");

    private final Object plugin;
    private final ProxyServer server;
    private final Logger logger;
    private final WebConfig config;
    private final UploadTokens tokens;
    private final TrackRegistry registry;
    private final UploadServer uploadServer;
    private TextureRegistry textures;
    private AMusicMerge merge;
    private final java.util.Map<String, String> levelNames = new java.util.concurrent.ConcurrentHashMap<>();

    public WebBridge(Object plugin, ProxyServer server, Logger logger, WebConfig config,
                     UploadTokens tokens, TrackRegistry registry, UploadServer uploadServer) {
        this.plugin = plugin;
        this.server = server;
        this.logger = logger;
        this.config = config;
        this.tokens = tokens;
        this.registry = registry;
        this.uploadServer = uploadServer;
    }

    public void setTextureSupport(TextureRegistry textures, AMusicMerge merge) {
        this.textures = textures;
        this.merge = merge;
        this.uploadServer.setTextureSupport(textures,
            (playerId, levelId, size, files) -> {
                Player player = this.server.getPlayer(playerId).orElse(null);
                if (player == null) return;
                this.write(player, "tex_ready", levelId + "\u0000" + size + "\u0000" + files);
            },
            id -> this.levelNames.getOrDefault(id, id));
    }

    public void register() {
        this.server.getChannelRegistrar().register(CHANNEL);
        this.uploadServer.setListener((playerId, trackId) -> {
            Optional<Player> player = this.server.getPlayer(playerId);
            player.ifPresent(value -> {
                this.sendOwnedTracks(value);
                this.write(value, "uploaded", trackId);
            });
        });
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.getId().equals(event.getIdentifier().getId())) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof ServerConnection connection)) return;
        Player player = connection.getPlayer();

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            String action = in.readUTF();
            switch (action) {
                case "own":
                    this.sendOwnedTracks(player);
                    return;
                case "upload":
                    this.issueUploadLink(player);
                    return;
                case "delete":
                    this.deleteTrack(player, in.readUTF());
                    return;
                case "tex_url":
                    this.issueTextureLink(player, in.readUTF(), in.readUTF());
                    return;
                case "tex_range":
                    this.setVersionRange(player, in.readUTF(), in.readUTF());
                    return;
                case "tex_delete":
                    this.deleteTextures(player, in.readUTF());
                    return;
                case "tex_install":
                    this.installTextures(player, in.readUTF());
                    return;
                case "tex_release":
                    if (this.merge != null) this.merge.release();
                    return;
                case "tex_unload":
                    this.unloadPacks(player);
                    return;
                default:
                    this.logger.warn("Unknown web bridge action: {}", action);
            }
        } catch (Exception e) {
            this.logger.warn("Unable to handle web bridge message", e);
        }
    }

    @Subscribe
    public void onServerConnected(ServerPostConnectEvent event) {
        this.server.getScheduler()
            .buildTask(this.plugin, () -> this.sendOwnedTracks(event.getPlayer()))
            .delay(1, TimeUnit.SECONDS)
            .schedule();
    }

    private void issueUploadLink(Player player) {
        int used = this.registry.countOwnedBy(player.getUniqueId());
        if (used >= this.config.maxTracksPerPlayer) {
            this.write(player, "limit", String.valueOf(this.config.maxTracksPerPlayer));
            return;
        }
        UploadTokens.Session session = this.tokens.issue(player.getUniqueId(), player.getUsername());
        this.write(player, "url", this.uploadServer.buildUploadUrl(session.token));
    }

    private void deleteTrack(Player player, String trackId) {
        TrackRegistry.Entry entry = this.registry.get(trackId);
        if (entry == null || !player.getUniqueId().toString().equals(entry.ownerUuid)) {
            this.write(player, "denied", trackId);
            return;
        }
        this.registry.remove(trackId);
        this.logger.info("Track {} unregistered by owner {}", trackId, player.getUsername());
        this.sendOwnedTracks(player);
    }

    private void issueTextureLink(Player player, String levelId, String levelName) {
        if (this.textures == null) {
            this.write(player, "tex_unavailable", levelId);
            return;
        }
        this.levelNames.put(levelId, levelName);
        UploadTokens.Session session = this.tokens.issue(
            player.getUniqueId(), player.getUsername(), "texture", levelId);
        this.write(player, "tex_link", this.uploadServer.buildTextureUrl(session.token));
    }

    private void setVersionRange(Player player, String levelId, String range) {
        if (this.textures == null) return;
        TextureRegistry.Entry entry = this.textures.get(levelId);
        if (entry == null) {
            entry = new TextureRegistry.Entry();
            entry.levelId = levelId;
            entry.ownerUuid = player.getUniqueId().toString();
            entry.ownerName = player.getUsername();
            entry.createdAt = System.currentTimeMillis();
        }
        entry.versionRange = "NONE".equals(range) ? null : range;
        this.textures.put(entry);
        this.logger.info("Level {} texture version range set to {} by {}",
            levelId, range, player.getUsername());
    }

    private void deleteTextures(Player player, String levelId) {
        if (this.textures == null) return;
        this.textures.remove(levelId);
        this.logger.info("Level {} texture pack removed by {}", levelId, player.getUsername());
        this.write(player, "tex_removed", levelId);
    }

    /**
     * Ставит текстуры уровня в слияние AMusic и держит лок, пока бэкенд не пришлёт tex_release.
     * Собирать пак между install и release может только этот уровень.
     */
    private void installTextures(Player player, String levelId) {
        if (this.merge == null || !this.merge.isReady() || this.textures == null) {
            this.logger.warn("Texture install requested for {} but merge hook is not ready", levelId);
            this.write(player, "tex_install_failed", levelId);
            return;
        }

        boolean installed;
        try {
            installed = this.merge.install(
                this.textures.get(levelId) == null ? null : this.textures.zipOf(levelId));
        } catch (Throwable t) {
            this.logger.warn("Texture install failed for {}", levelId, t);
            installed = false;
        }

        this.write(player, installed ? "tex_installed" : "tex_install_failed", levelId);
    }

    /**
     * С 1.20.3 ресурспаки у клиента стакаются, а не заменяются: отправка следующего пака
     * не убирает предыдущий, и текстуры уровня остаются висеть в лобби. Поэтому паки
     * снимаются явно. На старых клиентах метод недоступен - там замена работает по-старому.
     */
    private void unloadPacks(Player player) {
        try {
            player.clearResourcePacks();
            this.logger.info("Resource packs cleared for {}", player.getUsername());
        } catch (Throwable t) {
            this.logger.info("Client of {} does not support pack removal, relying on replacement",
                player.getUsername());
        }
        this.write(player, "tex_unloaded", "");
    }

    private void sendOwnedTracks(Player player) {
        List<TrackRegistry.Entry> owned = this.registry.ownedBy(player.getUniqueId());
        StringBuilder builder = new StringBuilder();
        for (TrackRegistry.Entry entry : owned) {
            if (builder.length() > 0) builder.append('\u0000');
            builder.append(entry.trackId);
        }
        this.write(player, "own", builder.toString());
    }

    private void write(Player player, String action, String payload) {
        Optional<ServerConnection> connection = player.getCurrentServer();
        if (connection.isEmpty()) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                UUID uuid = player.getUniqueId();
                out.writeLong(uuid.getMostSignificantBits());
                out.writeLong(uuid.getLeastSignificantBits());
                out.writeUTF(action);
                out.writeUTF(payload);
            }
            connection.get().sendPluginMessage(CHANNEL, bytes.toByteArray());
        } catch (Exception e) {
            this.logger.warn("Unable to send web bridge message", e);
        }
    }
}
