package ru.sortix.parkourbeat.player.music.platform;

import lombok.NonNull;
import me.bomb.amusic.AMusic;
import me.bomb.amusic.ClientAMusic;
import me.bomb.amusic.Configuration;
import me.bomb.amusic.LocalAMusic;
import me.bomb.amusic.PackSender;
import me.bomb.amusic.PositionTracker;
import me.bomb.amusic.SoundStarter;
import me.bomb.amusic.SoundStopper;
import me.bomb.amusic.resource.EnumStatus;
import me.bomb.amusic.resource.StatusReport;
import me.bomb.amusic.resourceserver.ResourceManager;
import me.bomb.amusic.source.LocalConvertedSource;
import me.bomb.amusic.source.LocalUnconvertedSource;
import me.bomb.amusic.source.MusicdirFStaticPackSource;
import me.bomb.amusic.source.MusicdirPackSource;
import me.bomb.amusic.source.PackSource;
import me.bomb.amusic.source.SoundSource;
import me.bomb.amusic.source.StaticPackSource;
import me.bomb.amusic.util.AMusicLogger;
import me.bomb.amusic.util.HexUtils;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.PluginManager;

import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.player.music.MusicTrack;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AMusicPlatform extends MusicPlatform {

    /**
     * Максимальное время ожидания ЛЮБОГО асинхронного колбэка AMusic.
     * AMusic выполняет запросы в ThreadPoolExecutor с ограниченной очередью:
     * при переполнении бросается RejectedExecutionException, а если задача упадёт
     * внутри — Consumer не будет вызван никогда. Любой такой случай подвешивал
     * плагин навсегда, поэтому каждый вызов страхуется таймаутом.
     */
    private static final long AMUSIC_CALLBACK_TIMEOUT_TICKS = 20L * 15L;
    private static final long AMUSIC_PACK_TIMEOUT_TICKS = 20L * 60L;

    private final Logger logger;
    private final Server server;
    private final AMusic aMusic;
    private final String configerrors;
    private final ParkourBeat plugin;
    private final MusicPackDispatcher dispatcher;

    public AMusicPlatform(ParkourBeat plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.logger = plugin.getLogger();
        this.dispatcher = new MusicPackDispatcher(plugin);

        AMusicLogger.setLogger(new me.bomb.amusic.util.Logger() {
            final java.util.logging.Logger logger = AMusicPlatform.this.logger;

            @Override
            public void warn(String msg) {
                logger.warning(msg);
            }

            @Override
            public void info(String msg) {
                logger.info(msg);
            }

            @Override
            public void error(String msg) {
                logger.severe(msg);
            }
        });

        Path plugindir = plugin.getDataFolder().toPath().resolve("amusic"),
            configfile = plugindir.resolve("config.yml"),
            defaultresourcepackfile = plugindir.resolve("resourcepack.zip"),
            musicdir = plugindir.resolve("Music"),
            packeddir = plugindir.resolve("Packed");
        FileSystem fs = plugindir.getFileSystem();
        FileSystemProvider fsp = fs.provider();
        try {
            fsp.createDirectory(plugindir);
        } catch (IOException ignored) {
        }

        Configuration config = new Configuration(plugindir.getFileSystem(), configfile, musicdir, packeddir, true, true);
        this.configerrors = config.errors;
        if (config.use) {
            try {
                fsp.createDirectory(musicdir);
            } catch (IOException ignored) {
            }
            try {
                fsp.createDirectory(packeddir);
            } catch (IOException ignored) {
            }
            if (config.connectuse) {
                this.aMusic = new ClientAMusic(config);
            } else {
                final ConcurrentHashMap<Object, InetAddress> playerips =
                    config.sendpackstrictaccess || config.uploadstrictaccess
                        ? new ConcurrentHashMap<Object, InetAddress>(16, 0.75f, 1) : null;
                Runtime runtime = Runtime.getRuntime();
                SoundSource source = config.encoderuse
                    ? new LocalUnconvertedSource(runtime, config.musicdir, config.packsizelimit, config.encoderbinary,
                    config.encoderbitrate, config.encoderchannels, config.encodersamplingrate,
                    config.packthreadcoefficient, config.packthreadlimitcount)
                    : new LocalConvertedSource(config.musicdir, config.packsizelimit,
                    config.packthreadcoefficient, config.packthreadlimitcount);
                PackSource packsource = new MusicdirFStaticPackSource(
                    new MusicdirPackSource(musicdir, config.packsizelimit),
                    new StaticPackSource(defaultresourcepackfile, config.packsizelimit));
                final AMusicUtils utils = new AMusicUtils(plugin.getServer());
                LocalAMusic amusic = new LocalAMusic(config, source, packsource, utils, utils, utils,
                    playerips == null ? null : playerips.values());
                final PositionTracker positiontracker = amusic.positiontracker;
                final ResourceManager resourcemanager = amusic.resourcemanager;
                PluginManager pluginmanager = plugin.getServer().getPluginManager();
                if (resourcemanager != null) {
                    pluginmanager.registerEvents(new AMusicEventListener(amusic, resourcemanager, positiontracker,
                        playerips, config.joinplaylist, config.waitacception), plugin);
                }
                this.aMusic = amusic;
            }
        } else {
            this.aMusic = null;
        }
    }

    @Override
    public void enable() {
        if (!this.configerrors.isEmpty()) {
            this.logger.severe("AMusic config initialization errors: \n".concat(this.configerrors));
            return;
        }
        if (this.aMusic == null) return;
        this.dispatcher.enable();
        this.aMusic.enable();
    }

    @Override
    public void disable() {
        this.dispatcher.disable();
        if (this.aMusic == null || !this.configerrors.isEmpty()) return;
        this.aMusic.disable();
    }

    public @NonNull MusicPackDispatcher getDispatcher() {
        return this.dispatcher;
    }

    private boolean isUsable() {
        return this.aMusic != null && this.configerrors.isEmpty();
    }

    /**
     * Оборачивает Consumer так, что он гарантированно сработает ровно один раз:
     * либо от AMusic, либо по таймауту с заранее заданным значением.
     */
    private <T> Consumer<T> guarded(String what, Consumer<T> consumer, T fallback, long timeoutTicks) {
        AtomicBoolean fired = new AtomicBoolean(false);
        Consumer<T> once = value -> {
            if (!fired.compareAndSet(false, true)) return;
            try {
                consumer.accept(value);
            } catch (Throwable t) {
                this.logger.log(Level.SEVERE, "AMusic callback failed: " + what, t);
            }
        };
        try {
            this.server.getScheduler().runTaskLater(this.plugin, () -> {
                if (fired.get()) return;
                this.logger.warning("AMusic did not answer in time: " + what + " (используем значение по умолчанию)");
                once.accept(fallback);
            }, timeoutTicks);
        } catch (Throwable ignored) {
            // плагин выключается — планировщик недоступен, страховка не нужна
        }
        return once;
    }

    @NonNull
    @Override
    protected void loadAllTracksFromStorage(Consumer<MusicTrack> trackConsumer, Runnable runafter) {
        AtomicBoolean finishedOnce = new AtomicBoolean(false);
        Runnable finish = () -> {
            if (finishedOnce.compareAndSet(false, true)) runafter.run();
        };

        if (!this.isUsable()) {
            finish.run();
            return;
        }

        Consumer<String[]> playlistsConsumer = playlists -> {
            if (playlists == null || playlists.length == 0) {
                finish.run();
                return;
            }
            final int count = playlists.length;
            AtomicInteger finishedCount = new AtomicInteger();

            for (String trackIdAndName : playlists) {
                if (trackIdAndName == null) {
                    if (finishedCount.incrementAndGet() == count) finish.run();
                    continue;
                }
                Consumer<String[]> tracksConsumer = this.guarded(
                    "getPlaylistSoundnames(" + trackIdAndName + ")",
                    tracks -> {
                        if (tracks != null && tracks.length > 0) {
                            trackConsumer.accept(new MusicTrack(this, trackIdAndName, trackIdAndName,
                                hasPieces(tracks)));
                        }
                        if (finishedCount.incrementAndGet() == count) finish.run();
                    }, null, AMUSIC_CALLBACK_TIMEOUT_TICKS);

                if (!this.aMusic.getPlaylistSoundnames(trackIdAndName, false, false, tracksConsumer)) {
                    tracksConsumer.accept(null);
                }
            }
        };

        Consumer<String[]> guardedPlaylists = this.guarded("getPlaylists()",
            playlistsConsumer, null, AMUSIC_CALLBACK_TIMEOUT_TICKS);
        try {
            this.aMusic.getPlaylists(false, false, guardedPlaylists);
        } catch (Throwable t) {
            this.logger.log(Level.SEVERE, "Unable to request playlists from AMusic", t);
            guardedPlaylists.accept(null);
        }
    }

    private static boolean hasPieces(@NonNull String[] tracks) {
        for (String t : tracks) {
            if ("1".equals(t)) return true;
        }
        return false;
    }

    @Override
    protected void loadTrackFromStorage(@NonNull String trackId, Consumer<MusicTrack> trackConsumer) {
        if (!this.isUsable()) {
            trackConsumer.accept(null);
            return;
        }
        Consumer<String[]> tracksConsumer = this.guarded("loadTrackFromStorage(" + trackId + ")", tracks -> {
            if (tracks == null || tracks.length == 0) {
                trackConsumer.accept(null);
                return;
            }
            trackConsumer.accept(new MusicTrack(this, trackId, trackId, hasPieces(tracks)));
        }, null, AMUSIC_CALLBACK_TIMEOUT_TICKS);

        try {
            if (!this.aMusic.getPlaylistSoundnames(trackId, false, false, tracksConsumer)) {
                tracksConsumer.accept(null);
            }
        } catch (Throwable t) {
            this.logger.log(Level.SEVERE, "Unable to load track \"" + trackId + "\" from AMusic", t);
            tracksConsumer.accept(null);
        }
    }

    @Override
    public void getPlayersLoadedTrack(@NonNull MusicTrack track, Consumer<List<Player>> playersConsumer) {
        if (!this.isUsable()) {
            playersConsumer.accept(null);
            return;
        }
        Consumer<UUID[]> uuidsConsumer = this.guarded("getPlayersLoaded(" + track.getId() + ")", playeruuids -> {
            if (playeruuids == null) {
                playersConsumer.accept(null);
                return;
            }
            List<Player> players = new ArrayList<>();
            for (UUID uuid : playeruuids) {
                Player player = this.server.getPlayer(uuid);
                if (player != null) players.add(player);
            }
            playersConsumer.accept(players);
        }, null, AMUSIC_CALLBACK_TIMEOUT_TICKS);

        try {
            if (!this.aMusic.getPlayersLoaded(track.getId(), uuidsConsumer)) {
                uuidsConsumer.accept(null);
            }
        } catch (Throwable t) {
            this.logger.log(Level.SEVERE, "Unable to request loaded players from AMusic", t);
            uuidsConsumer.accept(null);
        }
    }

    @Override
    protected void loadOrUpdateResourcepackFile(@NonNull MusicTrack track, Consumer<Boolean> statusConsumer) {
        if (!this.isUsable()) {
            statusConsumer.accept(false);
            return;
        }
        Consumer<Boolean> guarded = this.guarded("loadPack(pack only, " + track.getId() + ")",
            statusConsumer, false, AMUSIC_PACK_TIMEOUT_TICKS);
        StatusReport report = new StatusReport() {
            @Override
            public void onStatusResponse(EnumStatus status) {
                guarded.accept(EnumStatus.PACKED == status);
            }
        };
        try {
            if (!this.aMusic.loadPack(null, track.getId(), true, report)) {
                guarded.accept(false);
            }
        } catch (Throwable t) {
            this.logger.log(Level.SEVERE, "Unable to repack track \"" + track.getId() + "\"", t);
            guarded.accept(false);
        }
    }

    @Override
    public void setResourcepackTrack(@NonNull Player player, @NonNull MusicTrack track,
                                     Consumer<Boolean> statusConsumer) {
        this.setResourcepackTrack(player, track, result -> statusConsumer.accept(result.isOk()), null);
    }

    /**
     * Расширенная версия: отдаёт подробную причину, а не просто boolean.
     *
     * @param onSent вызывается в момент реальной отправки пака (для actionbar'а/прогресса), может быть null.
     */
    public void setResourcepackTrack(@NonNull Player player,
                                     @NonNull MusicTrack track,
                                     @NonNull Consumer<MusicPackDispatcher.Result> resultConsumer,
                                     Runnable onSent) {
        if (!this.isUsable()) {
            resultConsumer.accept(MusicPackDispatcher.Result.DISPATCH_ERROR);
            return;
        }

        Runnable action = () -> {
            if (!player.isOnline()) {
                resultConsumer.accept(MusicPackDispatcher.Result.PLAYER_LEFT);
                return;
            }
            UUID playeruuid = player.getUniqueId();
            String trackId = track.getId();

            this.dispatcher.request(player, trackId, resultConsumer, () -> {
                StatusReport report = new StatusReport() {
                    @Override
                    public void onStatusResponse(EnumStatus status) {
                        if (status == EnumStatus.DISPATCHED) return;
                        // Пак даже не был отправлен: playlist не найден, данные заблокированы и т.п.
                        AMusicPlatform.this.logger.warning("AMusic не отправил пак \"" + trackId
                            + "\" игроку " + player.getName() + ": " + status);
                        AMusicPlatform.this.dispatcher.abort(playeruuid, trackId,
                            MusicPackDispatcher.Result.DISPATCH_ERROR);
                    }
                };
                if (!this.aMusic.loadPack(new UUID[]{playeruuid}, trackId, false, report)) {
                    this.dispatcher.abort(playeruuid, trackId, MusicPackDispatcher.Result.DISPATCH_ERROR);
                    return;
                }
                if (onSent != null) onSent.run();
            });
        };

        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            this.server.getScheduler().runTask(this.plugin, action);
        }
    }

    @Override
    public void getResourcepackTrack(@NonNull Player player, Consumer<MusicTrack> trackConsumer) {
        if (!this.isUsable()) {
            trackConsumer.accept(null);
            return;
        }
        UUID uuid = player.getUniqueId();

        Consumer<String> consumer = this.guarded("getPackName(" + player.getName() + ")", trackId -> {
            if (trackId == null) {
                trackConsumer.accept(null);
                return;
            }
            // AMusic считает пак установленным сразу после отправки пакета,
            // ещё до того как клиент его применил (или отклонил).
            // Доверяем только подтверждённому клиентом статусу.
            String confirmed = this.dispatcher.getConfirmedTrackId(uuid);
            if (!trackId.equals(confirmed)) {
                trackConsumer.accept(null);
                return;
            }
            trackConsumer.accept(this.getTrackById(trackId));
        }, null, AMUSIC_CALLBACK_TIMEOUT_TICKS);

        try {
            if (!this.aMusic.getPackName(uuid, consumer)) {
                consumer.accept(null);
            }
        } catch (Throwable t) {
            this.logger.log(Level.SEVERE, "Unable to get current pack name from AMusic", t);
            consumer.accept(null);
        }
    }

    @Override
    public void disableRepeatMode(@NonNull Player player) {
        if (!this.isUsable()) return;
        this.aMusic.setRepeatMode(player.getUniqueId(), null);
    }

    @Override
    public void startPlayingTrackFull(@NonNull Player player) {
        if (!this.isUsable()) return;
        this.aMusic.playSound(player.getUniqueId(), "track");
    }

    @Override
    public void stopPlayingTrackFull(@NonNull Player player) {
        if (!this.isUsable()) return;
        this.aMusic.stopSound(player.getUniqueId());
    }

    @Override
    public void startPlayingTrackPiece(@NonNull Player player, int trackPieceNumber) {
        if (!this.isUsable()) return;
        this.aMusic.playSound(player.getUniqueId(), String.valueOf(trackPieceNumber));
    }

    @Override
    public void stopPlayingTrackPiece(@NonNull Player player, int trackPieceNumber) {
        if (!this.isUsable()) return;
        this.aMusic.stopSound(player.getUniqueId());
    }

    protected final static class AMusicUtils implements PackSender, SoundStarter, SoundStopper {
        private final Server server;

        protected AMusicUtils(Server server) {
            this.server = server;
        }

        @Override
        public void send(UUID uuid, String url, byte[] sha1) {
            if (uuid == null) return;
            Player player = this.server.getPlayer(uuid);
            if (player == null) return;
            try {
                player.setResourcePack(url, sha1);
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void startSound(UUID uuid, UUID soundhash, short id, byte partid) {
            if (uuid == null) return;
            String musicid = "amusic.music" + soundhash.toString() + HexUtils.shortToHex(id) + HexUtils.byteToHex(partid);
            Player player = this.server.getPlayer(uuid);
            if (player != null) player.playSound(player.getLocation(), musicid, SoundCategory.VOICE, 1.0f, 1.0f);
        }

        @Override
        public void stopSound(UUID uuid, UUID soundhash, short id, byte partid) {
            if (uuid == null) return;
            String musicid = "amusic.music" + soundhash.toString() + HexUtils.shortToHex(id) + HexUtils.byteToHex(partid);
            Player player = this.server.getPlayer(uuid);
            if (player != null) player.stopSound(musicid, SoundCategory.VOICE);
        }
    }

    public final class AMusicEventListener implements Listener {
        private final AMusic amusic;
        private final ResourceManager resourcemanager;
        private final PositionTracker positiontracker;
        private final ConcurrentHashMap<Object, InetAddress> playerips;
        private final String joinplaylist;
        private final boolean waitacception;

        protected AMusicEventListener(AMusic amusic, ResourceManager resourcemanager, PositionTracker positiontracker,
                                      ConcurrentHashMap<Object, InetAddress> playerips, String joinplaylist,
                                      boolean waitacception) {
            this.amusic = amusic;
            this.resourcemanager = resourcemanager;
            this.positiontracker = positiontracker;
            this.playerips = playerips;
            this.joinplaylist = joinplaylist;
            this.waitacception = waitacception;
        }

        @EventHandler
        public void playerJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            if (this.playerips != null && player.getAddress() != null) {
                this.playerips.put(player, player.getAddress().getAddress());
            }
            if (this.joinplaylist != null) {
                this.amusic.loadPack(new UUID[]{player.getUniqueId()}, this.joinplaylist, false, null);
            }
        }

        @EventHandler
        public void playerQuit(PlayerQuitEvent event) {
            Player player = event.getPlayer();
            UUID playeruuid = player.getUniqueId();
            this.amusic.logout(playeruuid);
            this.positiontracker.remove(playeruuid);
            this.resourcemanager.remove(playeruuid);
            if (this.playerips != null) this.playerips.remove(player);
        }

        @EventHandler
        public void playerRespawn(PlayerRespawnEvent event) {
            this.positiontracker.stopMusic(event.getPlayer().getUniqueId());
        }

        @EventHandler
        public void playerWorldChange(PlayerChangedWorldEvent event) {
            this.positiontracker.stopMusic(event.getPlayer().getUniqueId());
        }

        @EventHandler
        public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
            UUID uuid = event.getPlayer().getUniqueId();
            String status = event.getStatus().name();
            if ("ACCEPTED".equals(status)) {
                if (this.waitacception) this.resourcemanager.setAccepted(uuid);
                return;
            }
            if ("DECLINED".equals(status) || "FAILED_DOWNLOAD".equals(status)
                || "INVALID_URL".equals(status) || "FAILED_RELOAD".equals(status)
                || "SUCCESSFULLY_LOADED".equals(status)) {
                // Освобождаем токен в любом случае, иначе он висит в памяти навсегда
                // (в оригинале это делалось только при waitacception=true).
                this.resourcemanager.remove(uuid);
            }
        }
    }
}
