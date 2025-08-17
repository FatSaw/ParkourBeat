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
import me.bomb.amusic.source.MusicdirFStaticPackSource;
import me.bomb.amusic.source.MusicdirPackSource;
import me.bomb.amusic.source.PackSource;
import me.bomb.amusic.source.SoundSource;
import me.bomb.amusic.source.StaticPackSource;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.bukkit.plugin.PluginManager;

import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.player.music.MusicTrack;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class AMusicPlatform extends MusicPlatform {
	
	private final Logger logger;
    private final AMusic aMusic;
    private final String configerrors;
    
    public AMusicPlatform(ParkourBeat plugin) {
    	this.logger = plugin.getLogger();
    	Path plugindir = plugin.getDataFolder().toPath().resolve("amusic"), configfile = plugindir.resolve("config.yml"), defaultresourcepackfile = plugindir.resolve("resourcepack.zip"), musicdir = plugindir.resolve("Music"), packeddir = plugindir.resolve("Packed");
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
				ClientAMusic amusic = new ClientAMusic(config);
				this.aMusic = amusic;
			} else {
				final ConcurrentHashMap<Object,InetAddress> playerips = config.sendpackstrictaccess || config.uploadstrictaccess ? new ConcurrentHashMap<Object,InetAddress>(16,0.75f,1) : null;
				Runtime runtime = Runtime.getRuntime();
				SoundSource source = config.encoderuse ? new LocalUnconvertedSource(runtime, config.musicdir, config.packsizelimit, config.encoderbinary, config.encoderbitrate, config.encoderchannels, config.encodersamplingrate, config.packthreadcoefficient, config.packthreadlimitcount) : new LocalConvertedSource(config.musicdir, config.packsizelimit, config.packthreadcoefficient, config.packthreadlimitcount);
				PackSource packsource = new MusicdirFStaticPackSource(new MusicdirPackSource(musicdir, config.packsizelimit), new StaticPackSource(defaultresourcepackfile, config.packsizelimit));
				final AMusicUtils utils = new AMusicUtils(plugin.getServer());
				LocalAMusic amusic = new LocalAMusic(config, source, packsource, utils, utils, utils, playerips == null ? null : playerips.values());
				final PositionTracker positiontracker = amusic.positiontracker;
				final ResourceManager resourcemanager = amusic.resourcemanager;
				PluginManager pluginmanager = plugin.getServer().getPluginManager();
				if(resourcemanager != null) {
					pluginmanager.registerEvents(new AMusicEventListener(resourcemanager, positiontracker, playerips, config.waitacception), plugin);
				}
				this.aMusic = amusic;
			}
		} else {
			this.aMusic = null;
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
    protected void loadAllTracksFromStorage(Consumer<List<MusicTrack>> tracksConsumer) throws Exception {

    	Consumer<String[]> playlistsConsumer = new Consumer<String[]>() {
			@Override
			public void accept(String[] playlists) {

		    	List<MusicTrack> result = new ArrayList<>();
				int i = playlists.length;
		        while(--i > -1) {
		        	String trackIdAndName = playlists[i];
		        	Consumer<String[]> tracksConsumer = new Consumer<String[]>() {
		    			@Override
		    			public void accept(String[] tracks) {
		    				int j = tracks.length;
				        	if(j == 0) {
				        		return;
				        	}
				        	boolean piecesSupported = false;
				        	while(--j > -1) {
				        		if(tracks[j].equals("1")) {
				        			piecesSupported = true;
				        			break;
				        		}
				        	}
				        	result.add(new MusicTrack(AMusicPlatform.this, trackIdAndName, trackIdAndName, piecesSupported));
		    			}
		        	};
		        	aMusic.getPlaylistSoundnames(trackIdAndName, false, tracksConsumer);
		        			        }
		        tracksConsumer.accept(result);
			}
		};
        aMusic.getPlaylists(false, playlistsConsumer);
    }

    @Override
    protected void loadTrackFromStorage(@NonNull String trackId, Consumer<MusicTrack> trackConsumer) {
    	Consumer<String[]> tracksConsumer = new Consumer<String[]>() {
			@Override
			public void accept(String[] tracks) {
				int j = tracks.length;
		    	if(j == 0) {
		    		trackConsumer.accept(null);
		    		return;
		    	}
		    	boolean piecesSupported = false;
		    	while(--j > -1) {
		    		if(tracks[j].equals("1")) {
		    			piecesSupported = true;
		    			break;
		    		}
		    	}
		    	trackConsumer.accept(new MusicTrack(AMusicPlatform.this, trackId, trackId, piecesSupported));
			}
    	};
    	aMusic.getPlaylistSoundnames(trackId, false, tracksConsumer);
    	
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
    public void getResourcepackTrack(@NonNull Player player, Consumer<MusicTrack> trackConsumer) {
    	Consumer<String> consumer = new Consumer<String>() {
			@Override
			public void accept(String trackId) {
				if(trackId == null) {
					trackConsumer.accept(null);
					return;
				}
				trackConsumer.accept(AMusicPlatform.this.getTrackById(trackId));
			}
		};
		this.aMusic.getPackName(player.getUniqueId(), consumer);
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
    protected final static class AMusicUtils implements PackSender, SoundStarter, SoundStopper {
    	
    	private final Server server;
    	
    	protected AMusicUtils(Server server) {
    		this.server = server;
    	}

    	@Override
		public void send(UUID uuid, String url, byte[] sha1) {
			if(uuid == null) {
				return;
			}
			Player player = server.getPlayer(uuid);
			player.setResourcePack(url, sha1);
		}
    	
    	@Override
		public void startSound(UUID uuid, short id) {
			if(uuid == null) {
				return;
			}
			Player player = server.getPlayer(uuid);
			//player.playSound(player.getLocation(), "amusic.music".concat(Short.toString(id)), SoundCategory.VOICE, 1.0f, 1.0f); //Add sound volume configuration 1.12.2 and previous not supported if this used
			player.playSound(player.getLocation(), "amusic.music".concat(Short.toString(id)), 1.0E9f, 1.0f);
		}
    	
    	@Override
		public void stopSound(UUID uuid, short id) {
			if(uuid == null) {
				return;
			}
			Player player = server.getPlayer(uuid);
			player.stopSound("amusic.music".concat(Short.toString(id)));
		}
    	
    }
    
    public final class AMusicEventListener implements Listener {
    	private final ResourceManager resourcemanager;
    	private final PositionTracker positiontracker;
    	private final ConcurrentHashMap<Object,InetAddress> playerips;
    	private final boolean waitacception;
    	protected AMusicEventListener(ResourceManager resourcemanager, PositionTracker positiontracker, ConcurrentHashMap<Object,InetAddress> playerips, boolean waitacception) {
    		this.resourcemanager = resourcemanager;
    		this.positiontracker = positiontracker;
    		this.playerips = playerips;
    		this.waitacception = waitacception;
    	}
    	@EventHandler
    	public void playerJoin(PlayerJoinEvent event) {
    		if(playerips == null) return;
    		Player player = event.getPlayer();
    		playerips.put(player, player.getAddress().getAddress());
    	}
    	@EventHandler
    	public void playerQuit(PlayerQuitEvent event) {
    		Player player = event.getPlayer();
    		UUID playeruuid = player.getUniqueId();
    		positiontracker.remove(playeruuid);
    		resourcemanager.remove(playeruuid);
    		if(playerips == null) return;
    		playerips.remove(player);
    	}
    	@EventHandler
    	public void playerRespawn(PlayerRespawnEvent event) {
    		positiontracker.stopMusic(event.getPlayer().getUniqueId());
    	}
    	@EventHandler
    	public void playerWorldChange(PlayerChangedWorldEvent event) {
    		positiontracker.stopMusic(event.getPlayer().getUniqueId());
    	}
    	@EventHandler
		public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
    		if(!waitacception) {
    			return;
    		}
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
    }
}
