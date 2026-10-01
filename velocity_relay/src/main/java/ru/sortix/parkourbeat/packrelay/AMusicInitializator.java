package ru.sortix.parkourbeat.packrelay;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.EventHandler;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import me.bomb.amusic.LocalAMusic;
import me.bomb.amusic.ServerAMusic;
import me.bomb.amusic.api.AMusic;
import me.bomb.amusic.api.PackSender;
import me.bomb.amusic.config.Configuration;
import me.bomb.amusic.hook.GeyserHook;
import me.bomb.amusic.lang.LangLoader;
import me.bomb.amusic.permission.AMusicPermission;
import me.bomb.amusic.resourcepack.Data;
import me.bomb.amusic.resourcepack.LocalSoundSource;
import me.bomb.amusic.resourcepack.PackMergeEntryFile;
import me.bomb.amusic.resourcepack.server.ResourceManager;
import me.bomb.amusic.tracker.PositionTracker;
import me.bomb.amusic.util.AMusicLogger;
import me.bomb.amusic.velocity.VelocityMessageSender;
import me.bomb.amusic.velocity.VelocityPackSender;
import me.bomb.amusic.velocity.VelocitySoundStarter;
import me.bomb.amusic.velocity.VelocitySoundStopper;
import me.bomb.amusic.velocity.command.LoadmusicCommand;
import me.bomb.amusic.velocity.command.PlaymusicCommand;
import me.bomb.amusic.velocity.command.RepeatCommand;
import me.bomb.amusic.velocity.event.DisconnectHandler;
import me.bomb.amusic.velocity.event.PlayerResourcePackStatusHandler;

public final class AMusicInitializator {
	
	public final AMusic amusic;
	public final PackMergeSourceMap packmergesource;
	
	public AMusicInitializator(Object plugin, ProxyServer server, Logger logger, Path dataDirectory, boolean patchStrictAccess, boolean patchWaitAcception) {
		me.bomb.amusic.util.Logger amusiclogger = new me.bomb.amusic.util.Logger() {
			@Override
			public void warn(String msg) {
				logger.warn(msg);
			}
			
			@Override
			public void info(String msg) {
				logger.info(msg);
			}
			
			@Override
			public void error(String msg) {
				logger.error(msg);
			}
		};
		AMusicLogger.setLogger(amusiclogger);
		Path plugindir = dataDirectory.resolve("amusic"), mergezip = plugindir.resolve("resourcepack.zip"), configfile = plugindir.resolve("config.yml"), langfile = plugindir.resolve("lang.yml"), musicdir = plugindir.resolve("Music"), packeddir = plugindir.resolve("Packed");
		FileSystem fs = plugindir.getFileSystem();
		FileSystemProvider fsp = fs.provider();
		try {
			fsp.createDirectory(plugindir);
		} catch (IOException e) {
		}
		Configuration config = new Configuration(fs, configfile, "config.yml", false);
		String configerrors = config.errors;
		if(!configerrors.isEmpty()) {
			throw new IllegalStateException("AMusic config initialization errors: \n".concat(configerrors));
		}
		if(!config.use) {
			throw new IllegalStateException("Disabled in config.");
		}
		try {
			fsp.createDirectory(musicdir);
		} catch (IOException e) {
		}
		try {
			fsp.createDirectory(packeddir);
		} catch (IOException e) {
		}
		boolean sendpackstrictaccess = config.sendpackstrictaccess;
		int waitacceptioncount = config.waitacceptioncount;
		
		//What a hell that patch thing needed for, this options can be set in amusic config
		//its like option to not set other option in another config?
		if(patchStrictAccess) {
			sendpackstrictaccess = false; //STRICTACCESS DISABLE
		}
		if(patchWaitAcception) {
			waitacceptioncount = 0; //DISABLE WAITACCEPTION
		}
		//
		
		ConcurrentHashMap<Object,InetAddress> playerips = sendpackstrictaccess ? new ConcurrentHashMap<Object,InetAddress>(16,0.75f,1) : null;
		PackSender packsender = new VelocityPackSender(server);
		LocalSoundSource lczs = new LocalSoundSource(musicdir, config.packsizelimit, config.packsizelimit, config.packthreadcoefficient, config.packthreadlimitcount);
		PositionTracker positiontracker = new PositionTracker(new VelocitySoundStarter(server), new VelocitySoundStopper(server));
		ResourceManager resourcemanager = new ResourceManager(packsender, positiontracker, config.sendpackhost, config.packsizelimit, config.tokensalt, sendpackstrictaccess ? playerips.values() : null, config.sendpackifip, config.sendpackport, config.sendpackbacklog, config.sendpacktimeout, config.sendpackserverfactory, config.sendpackacceptthreads, config.sendpackexecutorsender, waitacceptioncount, config.waitacceptionwait, config.waitacceptionschedulerthreads);
		PackMergeSourceMap packmergesource = new PackMergeSourceMap(new PackMergeEntryFile(mergezip, config.packsizelimit), config.packsizelimit);
		Data datamanager = config.ramcache ? config.diskstore ? Data.getLocalCachedStorage(!config.processpack, lczs, packmergesource, packeddir) : Data.getRamStorage(!config.processpack, lczs, packmergesource) : config.diskstore ? Data.getLocalStorage(!config.processpack, lczs, packmergesource, packeddir) : Data.getNoStorage(!config.processpack, lczs, packmergesource);
		LocalAMusic amusic;
		if(config.connectuse) {
			amusic = new ServerAMusic(amusiclogger, config.executor, lczs, positiontracker, resourcemanager, datamanager, config.connectifip, config.connectremoteip, config.connectport, config.connecttimeout, config.connectbacklog, config.connectserverfactory, config.serverexecutor);
		} else {
			amusic = new LocalAMusic(amusiclogger, config.executor, lczs, positiontracker, resourcemanager, datamanager);
		}
		LangLoader lang = new LangLoader(langfile, "lang_rgb.yml", new VelocityMessageSender());
		final ConcurrentHashMap<UUID, EnumSet<AMusicPermission>> playerspermission = new ConcurrentHashMap<UUID, EnumSet<AMusicPermission>>();
		LoadmusicCommand loadmusic = null;
		PlaymusicCommand playmusic = null;
		RepeatCommand repeat = null;
		if(config.usecmd) {
			loadmusic = new LoadmusicCommand(server, amusic, lang, playerspermission);
			playmusic = new PlaymusicCommand(server, amusic, lang, playerspermission);
			repeat = new RepeatCommand(server, amusic, lang, playerspermission);
		}
		LoginHandlerPB login = null;
		DisconnectHandler disconnect = null;
		PlayerResourcePackStatusHandler resourcepackstatus;
		
		login = new LoginHandlerPB(amusic, playerspermission, playerips, config.joinplaylist);
		disconnect = new DisconnectHandler(amusic, playerspermission, playerips);
		resourcepackstatus = new PlayerResourcePackStatusHandler(amusic.resourcemanager);
		
		amusic.enable();
		GeyserHook geyser = null;
		try {
			geyser = new GeyserHook(this, amusic.datamanager);
			AMusicLogger.info("Geyser hook loaded");
		} catch (NoClassDefFoundError e) {
		}
		
		CommandManager cmdmanager = null;
		CommandMeta loadmusicmeta = null, playmusicmeta = null, repeatmeta = null;
		
		if(config.usecmd) {
			cmdmanager = server.getCommandManager();
			if(loadmusic != null) {
				loadmusicmeta = cmdmanager.metaBuilder("loadmusic").plugin(plugin).build();
				cmdmanager.register(loadmusicmeta, loadmusic);
			}
			if(playmusic != null) {
				playmusicmeta = cmdmanager.metaBuilder("playmusic").plugin(plugin).build();
				cmdmanager.register(playmusicmeta, playmusic);
			}
			if(repeat != null) {
				repeatmeta = cmdmanager.metaBuilder("repeat").plugin(plugin).build();
				cmdmanager.register(repeatmeta, repeat);
			}
		}
		EventManager eventmanager = server.getEventManager();
		ProxyShutdownHandlerPB proxyshutdown = new ProxyShutdownHandlerPB(amusic, geyser, cmdmanager, loadmusicmeta, playmusicmeta, repeatmeta, eventmanager, plugin, login, disconnect, resourcepackstatus);
		
		if(login != null) eventmanager.register(plugin, LoginEvent.class, login);
		if(disconnect != null) eventmanager.register(plugin, DisconnectEvent.class, disconnect);
		if(resourcepackstatus != null) eventmanager.register(plugin, PlayerResourcePackStatusEvent.class, resourcepackstatus);
		eventmanager.register(plugin, ProxyShutdownEvent.class, proxyshutdown);
		this.amusic = amusic;
		this.packmergesource = packmergesource;
	}
	
	public final static class LoginHandlerPB implements EventHandler<LoginEvent> {
		
		private final AMusic amusic;
		private final ConcurrentHashMap<UUID, EnumSet<AMusicPermission>> playerspermission;
		private final ConcurrentHashMap<Object,InetAddress> playerips;
		private final String joinplaylist;
		
		public LoginHandlerPB(AMusic amusic, ConcurrentHashMap<UUID, EnumSet<AMusicPermission>> playerspermission, ConcurrentHashMap<Object,InetAddress> playerips, String joinplaylist) {
			this.amusic = amusic;
			this.playerspermission = playerspermission;
			this.playerips = playerips;
			this.joinplaylist = joinplaylist;
		}

		@Override
		public void execute(LoginEvent event) {
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
            this.playerspermission.put(playeruuid, permissions);
			if(playerips != null) playerips.put(player, player.getRemoteAddress().getAddress());
			if(joinplaylist != null) amusic.loadResourcepack(new UUID[] {playeruuid}, joinplaylist, false, null);
		}

	}
	
	public final static class ProxyShutdownHandlerPB implements EventHandler<ProxyShutdownEvent> {

		private final AMusic amusic;
		private final GeyserHook geyser;
		private final CommandManager cmdmanager;
		private final CommandMeta loadmusicmeta, playmusicmeta, repeatmeta;
		private final EventManager eventmanager;
		private final Object plugin;
		private final LoginHandlerPB login;
		private final DisconnectHandler disconnect;
		private final PlayerResourcePackStatusHandler resourcepackstatus;
		
		public ProxyShutdownHandlerPB(AMusic amusic, GeyserHook geyser, CommandManager cmdmanager, CommandMeta loadmusicmeta, CommandMeta playmusicmeta, CommandMeta repeatmeta, EventManager eventmanager, Object plugin, LoginHandlerPB login, DisconnectHandler disconnect, PlayerResourcePackStatusHandler resourcepackstatus) {
			this.amusic = amusic;
			this.geyser = geyser;
			this.cmdmanager = cmdmanager;
			this.loadmusicmeta = loadmusicmeta;
			this.playmusicmeta = playmusicmeta;
			this.repeatmeta = repeatmeta;
			this.eventmanager = eventmanager;
			this.plugin = plugin;
			this.login = login;
			this.disconnect = disconnect;
			this.resourcepackstatus = resourcepackstatus;
		}
		
		@Override
		public void execute(ProxyShutdownEvent event) {
			if(this.geyser != null) {
				this.geyser.unregister();
			}
			if(this.cmdmanager != null) {
				if(this.loadmusicmeta != null) this.cmdmanager.unregister(this.loadmusicmeta);
				if(this.playmusicmeta != null) this.cmdmanager.unregister(this.playmusicmeta);
				if(this.repeatmeta != null) this.cmdmanager.unregister(this.repeatmeta);
			}
			if(this.login != null) this.eventmanager.unregisterListener(this.plugin, this.login);
			if(this.disconnect != null) this.eventmanager.unregisterListener(this.plugin, this.disconnect);
			if(this.resourcepackstatus != null) this.eventmanager.unregisterListener(this.plugin, this.resourcepackstatus);
			this.eventmanager.unregisterListener(this.plugin, event);
			this.amusic.disable();
		}
		
	}
	
}
