package ru.sortix.parkourbeat.packrelay;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import me.bomb.amusic.api.AMusic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import ru.sortix.parkourbeat.packrelay.slicer.SlicerBridge;
import ru.sortix.parkourbeat.packrelay.slicer.TrackAnalyzer;
import ru.sortix.parkourbeat.packrelay.slicer.TrackSlicer;
import ru.sortix.parkourbeat.packrelay.web.TextureRegistry;
import ru.sortix.parkourbeat.packrelay.web.TrackRegistry;
import ru.sortix.parkourbeat.packrelay.web.UploadServer;
import ru.sortix.parkourbeat.packrelay.web.UploadTokens;
import ru.sortix.parkourbeat.packrelay.web.WebBridge;
import ru.sortix.parkourbeat.packrelay.web.WebConfig;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(
    id = "parkourbeatpackrelay",
    name = "ParkourBeatPackRelay",
    version = "1.1.0",
    dependencies = {
    		@Dependency(id = "amusic"),
    		@Dependency(id = "geyser", optional = true)
    },
    description = "Resource pack status relay and AMusic reliability patches",
    authors = {"ParkourBeat"})
public final class ParkourBeatPackRelay {
    private static final ChannelIdentifier CHANNEL =
        MinecraftChannelIdentifier.create("parkourbeat", "packstatus");
    private static final long RETRY_DELAY_MS = 250L;
    private static final int MAX_RELAY_RETRIES = 20;

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Map<UUID, String> lastStatus = new ConcurrentHashMap<>();

    private RelayConfig config;
    private PackWatchdog watchdog;
    private WebConfig webConfig;
    private UploadServer uploadServer;
    private WebBridge webBridge;
    private SlicerBridge slicerBridge;
    private AMusic amusic;
    private PackMergeSourceMap packmergesource;
    
    @Inject
    public ParkourBeatPackRelay(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        this.config = RelayConfig.load(this.dataDirectory, this.logger);
        this.server.getChannelRegistrar().register(CHANNEL);
        
        AMusic amusic = null;
        PackMergeSourceMap packmergesource = null;
        try {
        	AMusicInitializator amusicinit = new AMusicInitializator(this, this.server, this.logger, this.dataDirectory, this.config.patchStrictAccess, this.config.patchWaitAcception);
        	amusic = amusicinit.amusic;
        	packmergesource = amusicinit.packmergesource;
        } catch (IllegalStateException e) {
        	logger.warn(e.getMessage());
        }
        this.amusic = amusic;
        this.packmergesource = packmergesource;
        
        this.watchdog = new PackWatchdog(this, this.server, this.logger, this.config);
        this.watchdog.start();

        this.startWeb();
        this.startSlicer();
        this.registerCommand();
        this.logger.info("ParkourBeatPackRelay enabled, channel {}", CHANNEL.getId());
    }

    /**
     * Нарезка треков под чекпоинты уровней. Работает только на прокси: ogg-файлы лежат
     * здесь, а не на игровом сервере.
     */
    private void startSlicer() {
        try {
            // Принудительно выдаем права на запуск из-под Java
            new java.io.File(this.config.ffmpegPath).setExecutable(true);
            new java.io.File(this.config.ffprobePath).setExecutable(true);

            Path musicDirectory = Path.of(this.webConfig == null
                ? "plugins/amusic/Music"
                : this.webConfig.musicDirectory);

            TrackSlicer slicer = new TrackSlicer(this.logger, musicDirectory,
                this.config.ffmpegPath, this.config.ffprobePath, this.config.sliceQuality);

            // Разбор трека для ритм-режима: длина, темп и удары по частотным полосам.
            // Ему нужен только ffmpeg, тот же, что и нарезке.
            TrackAnalyzer analyzer = new TrackAnalyzer(this.logger, musicDirectory,
                this.config.ffmpegPath);

            this.slicerBridge = new SlicerBridge(this, this.server, this.logger,
                slicer, amusic, analyzer);
            this.slicerBridge.register();
            this.server.getEventManager().register(this, this.slicerBridge);

            // Проверка тяжёлая только на первый взгляд: это два вызова -version.
            // Зато администратор узнает о проблеме сразу при старте, а не когда
            // строитель нажмёт "нарезать" и получит невнятную ошибку.
            this.server.getScheduler().buildTask(this, () -> {
                // ogg режется встроенным делителем, ffmpeg нужен только для mp3 и прочей
                // экзотики. Его отсутствие — не ошибка, а просто отсутствие бонуса.
                if (slicer.isFfmpegAvailable()) {
                    this.logger.info("Track slicer ready: built-in ogg splitter + ffmpeg for other formats");
                } else {
                    this.logger.info("Track slicer ready: built-in ogg splitter"
                        + " (ffmpeg not found, non-ogg tracks cannot be sliced)");
                }
            }).delay(3, TimeUnit.SECONDS).schedule();
            this.logger.info("Track slicer started, music directory: {}",
                musicDirectory.toAbsolutePath());
        } catch (Throwable t) {
            this.logger.error("Unable to start track slicer", t);
        }
    }

    private void startWeb() {
        this.webConfig = WebConfig.load(this.dataDirectory, this.logger);
        if (!this.webConfig.enabled) {
            this.logger.info("Web uploader disabled in config");
            return;
        }

        try {
            Path musicDirectory = Path.of(this.webConfig.musicDirectory);
            Files.createDirectories(musicDirectory);

            TrackRegistry registry = new TrackRegistry(this.dataDirectory, this.logger);
            UploadTokens tokens = new UploadTokens(this.webConfig.tokenLifetimeMillis);

            this.uploadServer = new UploadServer(this.logger, this.webConfig, tokens, registry,
                musicDirectory, this.logger::info);
            this.uploadServer.start();

            this.webBridge = new WebBridge(this, this.server, this.logger, this.webConfig,
                tokens, registry, this.uploadServer);

            TextureRegistry textures = new TextureRegistry(this.dataDirectory, this.logger);

            this.webBridge.setTextureSupport(textures, this.packmergesource);

            this.webBridge.register();
            this.server.getEventManager().register(this, this.webBridge);
        } catch (Throwable t) {
            this.logger.error("Unable to start web uploader", t);
        }
    }

    @Subscribe
    public void onStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        String status = event.getStatus().name();
        this.lastStatus.put(player.getUniqueId(), status);

        if (this.config.verboseLog) {
            String backend = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("none");
            this.logger.info("Pack status {} from {} ip={} proto={} brand={} server={}",
                status,
                player.getUsername(),
                player.getRemoteAddress().getAddress().getHostAddress(),
                player.getProtocolVersion().getProtocol(),
                String.valueOf(player.getClientBrand()),
                backend);
        }

        this.watchdog.onStatus(event);
        if (this.config.relayToBackend) this.send(player, status, 0);
    }

    @Subscribe
    public void onServerConnected(ServerPostConnectEvent event) {
        String status = this.lastStatus.get(event.getPlayer().getUniqueId());
        if (status == null || !this.config.relayToBackend) return;
        this.server.getScheduler()
            .buildTask(this, () -> this.send(event.getPlayer(), status, 0))
            .delay(500, TimeUnit.MILLISECONDS)
            .schedule();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        this.lastStatus.remove(uuid);
        this.watchdog.forget(uuid);
    }

    private void send(Player player, String status, int attempt) {
        if (!player.isActive()) return;

        Optional<ServerConnection> connection = player.getCurrentServer();
        if (connection.isEmpty()) {
            this.retry(player, status, attempt);
            return;
        }

        byte[] payload;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                UUID uuid = player.getUniqueId();
                out.writeLong(uuid.getMostSignificantBits());
                out.writeLong(uuid.getLeastSignificantBits());
                out.writeUTF(status);
            }
            payload = bytes.toByteArray();
        } catch (Exception e) {
            this.logger.warn("Unable to encode pack status", e);
            return;
        }

        if (!connection.get().sendPluginMessage(CHANNEL, payload)) {
            this.retry(player, status, attempt);
        }
    }

    private void retry(Player player, String status, int attempt) {
        if (attempt >= MAX_RELAY_RETRIES) {
            this.logger.warn("Gave up forwarding pack status {} for {}", status, player.getUsername());
            return;
        }
        this.server.getScheduler()
            .buildTask(this, () -> this.send(player, status, attempt + 1))
            .delay(RETRY_DELAY_MS, TimeUnit.MILLISECONDS)
            .schedule();
    }

    private void registerCommand() {
        CommandManager manager = this.server.getCommandManager();
        CommandMeta meta = manager.metaBuilder("packrelay").plugin(this).build();

        manager.register(meta, (SimpleCommand) invocation -> {
            if (!invocation.source().hasPermission("parkourbeat.packrelay")) {
                invocation.source().sendMessage(Component.text("Нет прав", NamedTextColor.RED));
                return;
            }

            String[] args = invocation.arguments();

            invocation.source().sendMessage(Component.text("ParkourBeatPackRelay", NamedTextColor.GOLD));

            if (args.length >= 2 && args[0].equalsIgnoreCase("player")) {
                Optional<Player> target = this.server.getPlayer(args[1]);
                if (target.isEmpty()) {
                    invocation.source().sendMessage(Component.text("Игрок не найден", NamedTextColor.RED));
                    return;
                }
                UUID uuid = target.get().getUniqueId();
                invocation.source().sendMessage(Component.text(
                    "Последний статус: " + this.lastStatus.getOrDefault(uuid, "нет"), NamedTextColor.GRAY));
                invocation.source().sendMessage(Component.text(
                    "Ожидание: " + this.watchdog.describe(uuid), NamedTextColor.GRAY));
            }
        });
    }
}
