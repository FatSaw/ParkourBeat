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
import me.bomb.amusic.resourceserver.ResourceManager;
import me.bomb.amusic.source.LocalConvertedSource;
import me.bomb.amusic.source.LocalUnconvertedSource;
import me.bomb.amusic.source.SoundSource;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;

import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.player.music.MusicTrack;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class AMusicPlatform extends MusicPlatform {
	
	private final Logger logger;
    private final AMusic aMusic;
    private final Path allTracksPath;
    private final String configerrors;
    
    public AMusicPlatform(ParkourBeat plugin) {
    	this.logger = plugin.getLogger();
    	Path plugindir = plugin.getDataFolder().toPath().resolve("amusic"), configfile = plugindir.resolve("config.yml"), musicdir = plugindir.resolve("Music"), packeddir = plugindir.resolve("Packed");
		FileSystem fs = plugindir.getFileSystem();
		FileSystemProvider fsp = fs.provider();
		try {
			fsp.createDirectory(plugindir);
		} catch (IOException e) {
		}
		Configuration config = new Configuration(plugindir.getFileSystem(), configfile, musicdir, packeddir, true, true);
		this.configerrors = config.errors;
		if(config.use) {
			try {
				fsp.createDirectory(musicdir);
			} catch (IOException e) {
			}
			try {
				fsp.createDirectory(packeddir);
			} catch (IOException e) {
			}
			if(config.connectuse) {
				ClientAMusic amusic = new ClientAMusic(config.connectifip, config.connectremoteip, config.connectport);
				this.aMusic = amusic;
				this.allTracksPath = null;
			} else {
				PackSender packsender = new PackSender() {
					@Override
					public void send(UUID uuid, String url, byte[] sha1) {
						if(uuid == null) {
							return;
						}
						Player player = Bukkit.getPlayer(uuid);
						player.setResourcePack(url, sha1);
					}
				};
				SoundStarter soundstarter = new SoundStarter() {
					@Override
					public void startSound(UUID uuid, short id) {
						if(uuid == null) {
							return;
						}
						Player player = Bukkit.getPlayer(uuid);
						player.playSound(player.getLocation(), "amusic.music".concat(Short.toString(id)), 1.0E9f, 1.0f);
					}
				};
				SoundStopper soundstopper = new SoundStopper() {
					@Override
					public void stopSound(UUID uuid, short id) {
						if(uuid == null) {
							return;
						}
						Player player = Bukkit.getPlayer(uuid);
						player.stopSound("amusic.music".concat(Short.toString(id)));
					}
				};
				final ConcurrentHashMap<Object,InetAddress> playerips = config.sendpackstrictaccess || config.uploadstrictaccess ? new ConcurrentHashMap<Object,InetAddress>(16,0.75f,1) : null;
				Runtime runtime = Runtime.getRuntime();
				SoundSource source = config.encoderuse ? new LocalUnconvertedSource(runtime, config.musicdir, config.packsizelimit, config.encoderbinary, config.encoderbitrate, config.encoderchannels, config.encodersamplingrate, config.packthreadcoefficient, config.packthreadlimitcount) : new LocalConvertedSource(config.musicdir, config.packsizelimit, config.packthreadcoefficient, config.packthreadlimitcount);
				LocalAMusic amusic = new LocalAMusic(config, source, packsender, soundstarter, soundstopper, playerips);
				final PositionTracker fpositiontracker = amusic.positiontracker;
				final ResourceManager fresourcemanager = amusic.resourcemanager;
				if(config.waitacception) {
					Listener statuslistener = new Listener() {
						final ResourceManager resourcemanager = fresourcemanager;
						@EventHandler
						public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
							Player player = event.getPlayer();
							UUID uuid = player.getUniqueId();
							Status status = event.getStatus();
							if(status==Status.ACCEPTED) {
								resourcemanager.setAccepted(uuid);
								return;
							}
							if(status==Status.DECLINED||status==Status.FAILED_DOWNLOAD) {
								resourcemanager.remove(uuid);
								return;
							}
							if(status==Status.SUCCESSFULLY_LOADED) {
								resourcemanager.remove(uuid); //Removes resource send if pack applied from client cache
							}
						}
					};
					plugin.getServer().getPluginManager().registerEvents(statuslistener, plugin);
				}
				Listener listener = new Listener() {
					final ResourceManager resourcemanager = fresourcemanager;
					final PositionTracker positiontracker = fpositiontracker;
					@EventHandler
					public void playerQuit(PlayerQuitEvent event) {
						Player player = event.getPlayer();
						UUID playeruuid = player.getUniqueId();
						positiontracker.remove(playeruuid);
						resourcemanager.remove(playeruuid);
					}
					@EventHandler
					public void playerRespawn(PlayerRespawnEvent event) {
						positiontracker.stopMusic(event.getPlayer().getUniqueId());
					}
					@EventHandler
					public void playerWorldChange(PlayerChangedWorldEvent event) {
						positiontracker.stopMusic(event.getPlayer().getUniqueId());
					}
				};
				plugin.getServer().getPluginManager().registerEvents(listener, plugin);
				this.aMusic = amusic;
				this.allTracksPath = musicdir;
			}
		} else {
			this.aMusic = null;
			this.allTracksPath = null;
		}
    }

    @Override
    public void enable() {
    	if(!this.configerrors.isEmpty()) {
    		logger.severe("AMusic config initialization errors: \n".concat(configerrors));
			return;
		}
    	if(aMusic == null) {
    		return;
    	}
    	aMusic.enable();
    }

    @Override
    public void disable() {
    	if(aMusic == null || this.configerrors.isEmpty()) {
    		return;
    	}
    	aMusic.disable();
    }
    
    @NonNull
    @Override
    protected List<MusicTrack> loadAllTracksFromStorage() throws Exception {
        List<MusicTrack> result = new ArrayList<>();
        try (Stream<Path> paths = Files.list(this.allTracksPath)) {
            paths.filter(Files::isDirectory).forEach(file -> {
                String trackIdAndName = file.getFileName().toString();
                boolean piecesSupported = new File(file.toFile(), "1.ogg").isFile();
                result.add(new MusicTrack(this, trackIdAndName, trackIdAndName, piecesSupported));
            });
        }
        return result;
    }

    @Override
    protected @Nullable MusicTrack loadTrackFromStorage(@NonNull String trackId) {
    	Path trackPath = this.allTracksPath.resolve(trackId);
        if (Files.isDirectory(trackPath)) {
            boolean piecesSupported = new File(trackPath.toFile(), "1.ogg").isFile();
            return new MusicTrack(this, trackId, trackId, piecesSupported);
        }
        return null;
    }

    @Override
    protected void loadOrUpdateResourcepackFile(@NonNull MusicTrack track) throws Exception {
        this.aMusic.loadPack(null, track.getId(), true, null);
    }

    @Override
    public void setResourcepackTrack(@NonNull Player player, @NonNull MusicTrack track) throws Exception {
    	UUID playeruuid = player.getUniqueId();
        this.aMusic.loadPack(playeruuid == null ? null : new UUID[] {playeruuid}, track.getId(), false, null);
    }

    @Nullable
    @Override
    public MusicTrack getResourcepackTrack(@NonNull Player player) {
        String trackId = this.aMusic.getPackName(player.getUniqueId());
        if (trackId == null) return null;
        return this.getTrackById(trackId);
    }

    @Override
    public void disableRepeatMode(@NonNull Player player) {
        this.aMusic.setRepeatMode(player.getUniqueId(), null);
    }

    @Override
    public void startPlayingTrackFull(@NonNull Player player) {
        this.aMusic.playSound(player.getUniqueId(), "track");
    }

    @Override
    public void stopPlayingTrackFull(@NonNull Player player) {
        this.aMusic.stopSound(player.getUniqueId());
    }

    @Override
    public void startPlayingTrackPiece(@NonNull Player player, int trackPieceNumber) {
        this.aMusic.playSound(player.getUniqueId(), String.valueOf(trackPieceNumber));
    }

    @Override
    public void stopPlayingTrackPiece(@NonNull Player player, int trackPieceNumber) {
        this.aMusic.stopSound(player.getUniqueId());
    }
}
