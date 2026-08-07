package ru.sortix.parkourbeat.player.music.platform;

import lombok.NonNull;

import me.bomb.amusic.AMusic;
import me.bomb.amusic.ClientAMusic;
import me.bomb.amusic.Configuration;
import me.bomb.amusic.GeyserHook;
import me.bomb.amusic.LocalAMusic;
import me.bomb.amusic.MessageSender;
import me.bomb.amusic.PackSender;
import me.bomb.amusic.PositionTracker;
import me.bomb.amusic.SoundStarter;
import me.bomb.amusic.SoundStopper;
import me.bomb.amusic.bukkit.SpigotMessageSender;
import me.bomb.amusic.bukkit.command.LoadmusicCommand;
import me.bomb.amusic.bukkit.command.PlaymusicCommand;
import me.bomb.amusic.bukkit.command.RepeatCommand;
import me.bomb.amusic.bukkit.command.SelectorProcessor;
import me.bomb.amusic.bukkit.command.UploadmusicCommand;
import me.bomb.amusic.bukkit.event.PlayerChangedWorldHandler;
import me.bomb.amusic.bukkit.event.PlayerQuitHandler;
import me.bomb.amusic.bukkit.event.PlayerResourcePackStatusHandler;
import me.bomb.amusic.bukkit.event.PlayerRespawnHandler;
import me.bomb.amusic.packedinfo.Data;
import me.bomb.amusic.packedinfo.LocalConvertedZerocopySource;
import me.bomb.amusic.permission.AMusicPermission;
import me.bomb.amusic.resource.EnumStatus;
import me.bomb.amusic.resource.StatusReport;
import me.bomb.amusic.resourceserver.ResourceManager;
import me.bomb.amusic.uploader.UploadManager;
import me.bomb.amusic.util.AMusicLogger;
import me.bomb.amusic.util.HexUtils;
import me.bomb.amusic.util.LangLoader;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;

import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.player.music.MusicTrack;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
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

    private final ParkourBeat plugin;
    
    private final Logger logger;
	private final Server server;
    
	private final AMusic amusic;
	private final ConcurrentHashMap<UUID, EnumSet<AMusicPermission>> playerspermission;
	private final ConcurrentHashMap<Object,InetAddress> playerips;
	private final boolean usecmd;
	private GeyserHook geyserhook = null;
	
	private final SimpleCommandMap commandmap;
	private final HashMap<String, Command> mapcommand;
	
	private final Command loadmusiccmd, playmusiccmd, playmusicuntrackablecmd, repeatcmd, uploadmusiccmd;
	
	private final PbPlayerJoinHandler playerjoin;
	private final PlayerQuitHandler playerquit;
	private final PlayerChangedWorldHandler playerchangedworld;
	private final PlayerRespawnHandler playerrespawn;
	private final PlayerResourcePackStatusHandler playerresourcepackstatus;
    private final MusicPackDispatcher dispatcher;

    public AMusicPlatform(ParkourBeat plugin) {
    	this.plugin = plugin;
    	this.server = plugin.getServer();
    	this.logger = plugin.getLogger();
    	this.dispatcher = new MusicPackDispatcher(plugin);
    	me.bomb.amusic.util.Logger logger = new me.bomb.amusic.util.Logger() {
			java.util.logging.Logger logger = AMusicPlatform.this.logger;
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
		};
		AMusicLogger.setLogger(logger);
		
		Path plugindir = plugin.getDataFolder().toPath().resolve("amusic"), configfile = plugindir.resolve("config.yml"), langfile = plugindir.resolve("lang.yml"), defaultresourcepackfile = plugindir.resolve("resourcepack.zip"), musicdir = plugindir.resolve("Music"), packeddir = plugindir.resolve("Packed");
		FileSystem fs = plugindir.getFileSystem();
		FileSystemProvider fsp = fs.provider();
		try {
			fsp.createDirectory(plugindir);
		} catch (IOException e) {
		}
		boolean waitacception = true;
		Configuration config = new Configuration(plugindir.getFileSystem(), configfile, musicdir, packeddir, waitacception, true);
		String configerrors = config.errors;
		if(!configerrors.isEmpty()) {
			throw new IllegalStateException("AMusic config initialization errors: \n".concat(configerrors));
		}
		SimpleCommandMap commandmap = null;
		HashMap<String, Command> mapcommand = null;
		LoadmusicCommand loadmusiccmd = null;
		PlaymusicCommand playmusiccmd = null;
		PlaymusicCommand playmusicuntrackablecmd = null;
		RepeatCommand repeatcmd = null;
		UploadmusicCommand uploadmusiccmd = null;
		if(config.use) {
			try {
				fsp.createDirectory(musicdir);
			} catch (IOException e) {
			}
			try {
				fsp.createDirectory(packeddir);
			} catch (IOException e) {
			}
			this.usecmd = config.usecmd;
			if(this.usecmd) {
				try {
					{
						PluginManager pluginmanager = server.getPluginManager();
						Field field = pluginmanager.getClass().getDeclaredField("commandMap");
						field.setAccessible(true);
						commandmap = (SimpleCommandMap) field.get(pluginmanager);
					}
					try {
						Method method = commandmap.getClass().getDeclaredMethod("getKnownCommands");
						mapcommand = (HashMap<String, Command>) method.invoke(commandmap);
					} catch (NoSuchMethodException | InvocationTargetException | SecurityException | IllegalArgumentException | IllegalAccessException e2) {
						try {
							Field field = commandmap.getClass().getDeclaredField("knownCommands");
							field.setAccessible(true);
							mapcommand = (HashMap<String, Command>) field.get(commandmap);
						} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e3) {
						}
					}
				} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e1) {
					e1.printStackTrace();
				}
				
			}
			MessageSender messagesender = new SpigotMessageSender();
			LangLoader lang = new LangLoader(langfile, "lang_rgb.yml", messagesender);
			ConcurrentHashMap<UUID, EnumSet<AMusicPermission>> playerspermission = new ConcurrentHashMap<UUID, EnumSet<AMusicPermission>>();
			PbPlayerJoinHandler playerjoin = null;
			PlayerQuitHandler playerquit = null;
			if(config.connectuse) {
				this.playerips = null;
				ClientAMusic amusic = new ClientAMusic(config.connectifip, config.connectremoteip, config.connectport, config.connectsocketfactory, config.executor);
				this.amusic = amusic;
				this.playerchangedworld = null;
				this.playerrespawn = null;
				this.playerresourcepackstatus = null;
				if(this.usecmd) {
					SelectorProcessor selectorprocessor = new SelectorProcessor(server, new Random());
					loadmusiccmd = new LoadmusicCommand(server, amusic, lang, playerspermission, selectorprocessor);
					playmusiccmd = new PlaymusicCommand(server, amusic, lang, playerspermission, selectorprocessor, true);
					playmusicuntrackablecmd = new PlaymusicCommand(server, amusic, lang, playerspermission, selectorprocessor, false);
					repeatcmd = new RepeatCommand(server, amusic, lang, playerspermission, selectorprocessor);
					uploadmusiccmd = new UploadmusicCommand(amusic, lang, playerspermission, config.uploadhost);
				}
			} else {
				PackSender packsender;
				SoundStarter soundstarter;
				SoundStopper soundstopper;
				
				waitacception = config.waitacception;
				playerips = config.sendpackstrictaccess || config.uploadstrictaccess ? new ConcurrentHashMap<Object,InetAddress>(16,0.75f,1) : null;
				LocalConvertedZerocopySource lczs = new LocalConvertedZerocopySource(defaultresourcepackfile, config.musicdir, config.packsizelimit, config.packsizelimit, config.packthreadcoefficient, config.packthreadlimitcount);
				AMusicUtils amusicutils = new AMusicUtils(server);
				PositionTracker positiontracker = new PositionTracker(amusicutils, amusicutils);
				ResourceManager resourcemanager = new ResourceManager(amusicutils, positiontracker, config.sendpackhost, config.packsizelimit, config.tokensalt, config.waitacception, config.sendpackstrictaccess ? playerips.values() : null, config.sendpackifip, config.sendpackport, config.sendpackbacklog, config.sendpacktimeout, config.sendpackserverfactory, (short) 2, config.sendpackexecutorchecker, config.sendpackexecutorsender);
				Data datamanager = config.ramcache ? config.diskstore ? Data.getLocalCachedStorage(!config.processpack, lczs, packeddir) : Data.getRamStorage(!config.processpack, lczs) : config.diskstore ? Data.getLocalStorage(!config.processpack, lczs, packeddir) : Data.getNoStorage(!config.processpack, lczs);
				UploadManager uploadmanager = config.uploaduse ? new UploadManager(config.uploadlifetime, config.uploadlimitsize, config.uploadlimitcount, config.musicdir, config.uploadstrictaccess ? playerips.values() : null, config.uploadifip, config.uploadport, config.uploadbacklog, config.uploadtimeout, config.uploadserverfactory, (short) 2) : null;
				LocalAMusic amusic = new LocalAMusic(logger, config.executor, lczs, positiontracker, resourcemanager, datamanager, uploadmanager);
				this.amusic = amusic;
				if(this.usecmd) {
					SelectorProcessor selectorprocessor = new SelectorProcessor(server, new Random());
					loadmusiccmd = new LoadmusicCommand(server, amusic, lang, playerspermission, selectorprocessor);
					playmusiccmd = new PlaymusicCommand(server, amusic, lang, playerspermission, selectorprocessor, true);
					playmusicuntrackablecmd = new PlaymusicCommand(server, amusic, lang, playerspermission, selectorprocessor, false);
					repeatcmd = new RepeatCommand(server, amusic, lang, playerspermission, selectorprocessor);
					uploadmusiccmd = new UploadmusicCommand(amusic, lang, playerspermission, config.uploadhost);
				}
				PlayerChangedWorldHandler playerchangedworld = null;
				PlayerRespawnHandler playerrespawn = null;
				PlayerResourcePackStatusHandler playerresourcepackstatus = null;
				try {
					playerchangedworld = new PlayerChangedWorldHandler(plugin, amusic.positiontracker);
				} catch (NoClassDefFoundError e) {
				}
				try {
					playerrespawn = new PlayerRespawnHandler(plugin, amusic.positiontracker);
				} catch (NoClassDefFoundError e) {
				}
				if(waitacception) {
					try {
						playerresourcepackstatus = new PlayerResourcePackStatusHandler(plugin, amusic.resourcemanager);
					} catch (NoClassDefFoundError e) {
					}
				}
				this.playerchangedworld = playerchangedworld;
				this.playerrespawn = playerrespawn;
				this.playerresourcepackstatus = playerresourcepackstatus;
			}
			try {
				playerjoin = new PbPlayerJoinHandler(plugin, amusic, playerspermission, playerips, config.joinplaylist);
			} catch (NoClassDefFoundError e) {
			}
			try {
				playerquit = new PlayerQuitHandler(plugin, amusic, playerspermission, playerips, uploadmusiccmd);
			} catch (NoClassDefFoundError e) {
			}
			this.playerjoin = playerjoin;
			this.playerquit = playerquit;
			this.playerspermission = playerspermission;
		} else {
			this.usecmd = false;
			this.playerspermission = null;
			this.playerips = null;
			this.amusic = null;
			this.playerjoin = null;
			this.playerquit = null;
			this.playerchangedworld = null;
			this.playerrespawn = null;
			this.playerresourcepackstatus = null;
		}
		this.commandmap = commandmap;
		this.mapcommand = mapcommand;
		this.loadmusiccmd = loadmusiccmd;
		this.playmusiccmd = playmusiccmd;
		this.playmusicuntrackablecmd = playmusicuntrackablecmd;
		this.repeatcmd = repeatcmd;
		this.uploadmusiccmd = uploadmusiccmd;
    }

    @Override
    public void enable() {
		if(this.amusic == null) {
			return;
		}
		if(this.mapcommand != null) {
			final String prefix = "parkourbeat:";
			if(this.loadmusiccmd != null) {
				String cmdname = this.loadmusiccmd.getName();
				this.mapcommand.put(prefix.concat(cmdname), this.loadmusiccmd);
				this.mapcommand.put(cmdname, this.loadmusiccmd);
				this.loadmusiccmd.register(commandmap);
			}
			if(this.playmusiccmd != null) {
				String cmdname = this.playmusiccmd.getName();
				this.mapcommand.put(prefix.concat(cmdname), this.playmusiccmd);
				this.mapcommand.put(cmdname, this.playmusiccmd);
				this.playmusiccmd.register(commandmap);
			}
			if(this.playmusicuntrackablecmd != null) {
				String cmdname = this.playmusicuntrackablecmd.getName();
				this.mapcommand.put(prefix.concat(cmdname), this.playmusicuntrackablecmd);
				this.mapcommand.put(cmdname, this.playmusicuntrackablecmd);
				this.playmusicuntrackablecmd.register(commandmap);
			}
			if(this.repeatcmd != null) {
				String cmdname = this.repeatcmd.getName();
				this.mapcommand.put(prefix.concat(cmdname), this.repeatcmd);
				this.mapcommand.put(cmdname, this.repeatcmd);
				this.repeatcmd.register(commandmap);
			}
			if(this.uploadmusiccmd != null) {
				String cmdname = this.uploadmusiccmd.getName();
				this.mapcommand.put(prefix.concat(cmdname), this.uploadmusiccmd);
				this.mapcommand.put(cmdname, this.uploadmusiccmd);
				this.uploadmusiccmd.register(commandmap);
			}
		}
		if(this.amusic instanceof LocalAMusic) {
			if(this.playerjoin != null) this.playerjoin.register();
			if(this.playerquit != null) this.playerquit.register();
			if(this.playerchangedworld != null) this.playerchangedworld.register();
			if(this.playerrespawn != null) this.playerrespawn.register();
			if(this.playerresourcepackstatus != null) this.playerresourcepackstatus.register();
		}
		if(this.playerips != null) {
			this.playerips.clear();
			for(Player player : server.getOnlinePlayers()) {
				this.playerips.put(player, player.getAddress().getAddress());
			}
		}
		if(this.playerspermission != null) {
			this.playerspermission.clear();
			
			for(Player player : server.getOnlinePlayers()) {
				this.playerips.put(player, player.getAddress().getAddress());
				EnumSet<AMusicPermission> permissions = EnumSet.noneOf(AMusicPermission.class);
				if(player.hasPermission("parkourbeat.loadmusic")) permissions.add(AMusicPermission.LOADMUSIC);
				if(player.hasPermission("parkourbeat.loadmusic.other")) permissions.add(AMusicPermission.LOADMUSIC_OTHER);
				if(player.hasPermission("parkourbeat.loadmusic.update")) permissions.add(AMusicPermission.LOADMUSIC_UPDATE);
				if(player.hasPermission("parkourbeat.playmusic")) permissions.add(AMusicPermission.PLAYMUSIC);
				if(player.hasPermission("parkourbeat.playmusic.other")) permissions.add(AMusicPermission.PLAYMUSIC_OTHER);
				if(player.hasPermission("parkourbeat.repeat")) permissions.add(AMusicPermission.REPEAT);
				if(player.hasPermission("parkourbeat.repeat.other")) permissions.add(AMusicPermission.REPEAT_OTHER);
				if(player.hasPermission("parkourbeat.uploadmusic")) permissions.add(AMusicPermission.UPLOADMUSIC);
				if(player.hasPermission("parkourbeat.uploadmusic.token")) permissions.add(AMusicPermission.UPLOADMUSIC_TOKEN);
				this.playerspermission.put(player.getUniqueId(), permissions);
			}
		}
		this.dispatcher.enable();
		this.amusic.enable();
		if(this.amusic instanceof LocalAMusic) {
			try {
				this.geyserhook = new GeyserHook(this, ((LocalAMusic) this.amusic).datamanager);
				logger.info("Geyser hook loaded");
			} catch (NoClassDefFoundError e) {
			}
		}
    }

    @Override
    public void disable() {
        this.dispatcher.disable();
        if(this.geyserhook != null) {
			this.geyserhook.unregister();
		}
		if(this.amusic == null) {
			return;
		}
		if(this.mapcommand != null) {
			final String prefix = "parkourbeat:";
			if(this.loadmusiccmd != null) {
				String cmdname = this.loadmusiccmd.getName();
				this.mapcommand.remove(prefix.concat(cmdname), this.loadmusiccmd);
				this.mapcommand.remove(cmdname, this.loadmusiccmd);
				this.loadmusiccmd.unregister(commandmap);
			}
			if(this.playmusiccmd != null) {
				String cmdname = this.playmusiccmd.getName();
				this.mapcommand.remove(prefix.concat(cmdname), this.playmusiccmd);
				this.mapcommand.remove(cmdname, this.playmusiccmd);
				this.playmusiccmd.unregister(commandmap);
			}
			if(this.playmusicuntrackablecmd != null) {
				String cmdname = this.playmusicuntrackablecmd.getName();
				this.mapcommand.remove(prefix.concat(cmdname), this.playmusicuntrackablecmd);
				this.mapcommand.remove(cmdname, this.playmusicuntrackablecmd);
				this.playmusicuntrackablecmd.unregister(commandmap);
			}
			if(this.repeatcmd != null) {
				String cmdname = this.repeatcmd.getName();
				this.mapcommand.remove(prefix.concat(cmdname), this.repeatcmd);
				this.mapcommand.remove(cmdname, this.repeatcmd);
				this.repeatcmd.unregister(commandmap);
			}
			if(this.uploadmusiccmd != null) {
				String cmdname = this.uploadmusiccmd.getName();
				this.mapcommand.remove(prefix.concat(cmdname), this.uploadmusiccmd);
				this.mapcommand.remove(cmdname, this.uploadmusiccmd);
				this.uploadmusiccmd.unregister(commandmap);
			}
		}
		if(this.playerjoin != null) this.playerjoin.unregister();
		if(this.playerquit != null) this.playerquit.unregister();
		if(this.playerchangedworld != null) this.playerchangedworld.unregister();
		if(this.playerrespawn != null) this.playerrespawn.unregister();
		if(this.playerresourcepackstatus != null) this.playerresourcepackstatus.unregister();
		if(this.playerips != null) this.playerips.clear();
		if(this.playerspermission != null) this.playerspermission.clear();
		this.amusic.disable();
    }

    public @NonNull MusicPackDispatcher getDispatcher() {
        return this.dispatcher;
    }

    private boolean isUsable() {
        return this.amusic != null;
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

                if (!this.amusic.getPlaylistSoundnames(trackIdAndName, false, false, tracksConsumer)) {
                    tracksConsumer.accept(null);
                }
            }
        };

        Consumer<String[]> guardedPlaylists = this.guarded("getPlaylists()",
            playlistsConsumer, null, AMUSIC_CALLBACK_TIMEOUT_TICKS);
        try {
            this.amusic.getPlaylists(false, false, guardedPlaylists);
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
            if (!this.amusic.getPlaylistSoundnames(trackId, false, false, tracksConsumer)) {
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
            if (!this.amusic.getPlayersLoaded(track.getId(), uuidsConsumer)) {
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
            if (!this.amusic.loadPack(null, track.getId(), true, report)) {
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
                if (!this.amusic.loadPack(new UUID[]{playeruuid}, trackId, false, report)) {
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
            if (!this.amusic.getPackName(uuid, consumer)) {
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
        this.amusic.setRepeatMode(player.getUniqueId(), null);
    }

    @Override
    public void startPlayingTrackFull(@NonNull Player player) {
        if (!this.isUsable()) return;
        this.amusic.playSound(player.getUniqueId(), "track");
    }

    @Override
    public void stopPlayingTrackFull(@NonNull Player player) {
        if (!this.isUsable()) return;
        this.amusic.stopSound(player.getUniqueId());
    }

    @Override
    public void startPlayingTrackPiece(@NonNull Player player, int trackPieceNumber) {
        if (!this.isUsable()) return;
        this.amusic.playSound(player.getUniqueId(), String.valueOf(trackPieceNumber));
    }

    @Override
    public void stopPlayingTrackPiece(@NonNull Player player, int trackPieceNumber) {
        if (!this.isUsable()) return;
        this.amusic.stopSound(player.getUniqueId());
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
    	public void startSound(UUID uuid, UUID soundhash, short id, byte part) {
    		if(uuid == null || soundhash == null) {
    			return;
    		}
    		String musicid = new StringBuilder("minecraft:amusic.internal.").append(soundhash.toString()).append(HexUtils.shortToHex(id)).append(HexUtils.byteToHex(part)).toString();
    		Player player = server.getPlayer(uuid);
    		player.playSound(player.getLocation(), musicid, SoundCategory.VOICE, 1.0f, 1.0f);
    	}
    	
    	@Override
    	public void startSound(UUID uuid, UUID soundhash, short id, byte part, double x, double y, double z, float volume, float pitch) {
    		if(uuid == null || soundhash == null) {
    			return;
    		}
    		String musicid = new StringBuilder("minecraft:amusic.internal.").append(soundhash.toString()).append(HexUtils.shortToHex(id)).append(HexUtils.byteToHex(part)).toString();
    		Player player = server.getPlayer(uuid);
    		player.playSound(new Location(player.getWorld(), x, y, z), musicid, SoundCategory.VOICE, volume, pitch);
    	}
    	
    	@Override
    	public void stopSound(UUID uuid, UUID soundhash, short id, byte part) {
    		if(uuid == null) {
    			return;
    		}
    		String musicid = new StringBuilder("minecraft:amusic.internal.").append(soundhash.toString()).append(HexUtils.shortToHex(id)).append(HexUtils.byteToHex(part)).toString();
    		Player player = server.getPlayer(uuid);
    		player.stopSound(musicid, SoundCategory.VOICE);
    	}
    	
    }
    
    public final static class PbPlayerJoinHandler extends RegisteredListener {
    	
    	private final HandlerList handlerlist;
    	private final AMusic amusic;
    	private final ConcurrentHashMap<UUID, EnumSet<AMusicPermission>> playerspermission;
    	private final ConcurrentHashMap<Object,InetAddress> playerips;
    	private final String joinplaylist;

    	public PbPlayerJoinHandler(Plugin plugin, AMusic amusic, ConcurrentHashMap<UUID, EnumSet<AMusicPermission>> playerspermission, ConcurrentHashMap<Object,InetAddress> playerips, String joinplaylist) throws NoClassDefFoundError {
    		super(null, null, null, plugin, true);
    		this.amusic = amusic;
    		this.playerspermission = playerspermission;
    		this.playerips = playerips;
    		this.joinplaylist = joinplaylist;
    		this.handlerlist = PlayerJoinEvent.getHandlerList();
    	}
    	
    	public void register() {
    		this.handlerlist.register(this);
    	}
    	
    	public void unregister() {
    		this.handlerlist.unregister(this);
    	}
    	
    	@Override
    	public Listener getListener() {
    		return null;
    	}
    	
    	@Override
    	public Plugin getPlugin() {
    		return super.getPlugin();
    	}
    	
    	@Override
    	public EventPriority getPriority() {
    		return EventPriority.LOWEST;
    	}
    	
    	@Override
    	public void callEvent(final Event eve) throws EventException {
    		PlayerJoinEvent event = (PlayerJoinEvent) eve;
    		Player player = event.getPlayer();
    		UUID playeruuid = player.getUniqueId();
    		EnumSet<AMusicPermission> permissions = EnumSet.noneOf(AMusicPermission.class);
    		if(player.hasPermission("parkourbeat.loadmusic")) permissions.add(AMusicPermission.LOADMUSIC);
			if(player.hasPermission("parkourbeat.loadmusic.other")) permissions.add(AMusicPermission.LOADMUSIC_OTHER);
			if(player.hasPermission("parkourbeat.loadmusic.update")) permissions.add(AMusicPermission.LOADMUSIC_UPDATE);
			if(player.hasPermission("parkourbeat.playmusic")) permissions.add(AMusicPermission.PLAYMUSIC);
			if(player.hasPermission("parkourbeat.playmusic.other")) permissions.add(AMusicPermission.PLAYMUSIC_OTHER);
			if(player.hasPermission("parkourbeat.repeat")) permissions.add(AMusicPermission.REPEAT);
			if(player.hasPermission("parkourbeat.repeat.other")) permissions.add(AMusicPermission.REPEAT_OTHER);
			if(player.hasPermission("parkourbeat.uploadmusic")) permissions.add(AMusicPermission.UPLOADMUSIC);
			if(player.hasPermission("parkourbeat.uploadmusic.token")) permissions.add(AMusicPermission.UPLOADMUSIC_TOKEN);
    		this.playerspermission.put(playeruuid, permissions);
    		if(this.playerips != null) this.playerips.put(playeruuid, player.getAddress().getAddress());
    		if(this.joinplaylist != null) this.amusic.loadPack(new UUID[] {playeruuid}, this.joinplaylist, false, null);
    	}
    	
    	@Override
    	public boolean isIgnoringCancelled() {
    		return true;
    	}
    }
}
