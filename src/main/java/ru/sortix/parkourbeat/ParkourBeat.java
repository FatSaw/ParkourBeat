package ru.sortix.parkourbeat;

import dev.rollczi.litecommands.LiteCommands;
import dev.rollczi.litecommands.argument.ArgumentKey;
import dev.rollczi.litecommands.bukkit.LiteBukkitFactory;
import dev.rollczi.litecommands.bukkit.LiteBukkitMessages;
import dev.rollczi.litecommands.message.LiteMessages;
import dev.rollczi.litecommands.schematic.SchematicFormat;
import lombok.NonNull;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.commands.*;
import ru.sortix.parkourbeat.commands.argument.GameSettingsArgumentResolver;
import ru.sortix.parkourbeat.commands.handler.DefaultInvalidUsageHandler;
import ru.sortix.parkourbeat.constant.Messages;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.inventory.InventoriesListener;
import ru.sortix.parkourbeat.item.ItemsManager;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.listeners.FixesListener;
import ru.sortix.parkourbeat.listeners.GamesListener;
import ru.sortix.parkourbeat.listeners.GlowingBarriersListener;
import ru.sortix.parkourbeat.listeners.LightShowWandListener;
import ru.sortix.parkourbeat.listeners.PhysicsListener;
import ru.sortix.parkourbeat.listeners.WorldEditGuardListener;
import ru.sortix.parkourbeat.physics.CustomPhysicsManager;
import ru.sortix.parkourbeat.player.PlayersCollisionManager;
import ru.sortix.parkourbeat.player.SkyTimeManager;
import ru.sortix.parkourbeat.world.GlowingBarriersManager;
import ru.sortix.parkourbeat.world.LevelWorldsManager;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.player.music.MusicTracksManager;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.world.WorldsListener;
import ru.sortix.parkourbeat.world.WorldsManager;
import ru.sortix.parkourbeat.worldedit.WorldEditAccessManager;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;

public class ParkourBeat extends JavaPlugin {
    private final Map<Class<?>, PluginManager> managers = new LinkedHashMap<>();

    private LiteCommands<CommandSender> liteCommands;

    public ParkourBeat() {
        LangOptions.loadLang(new File(getDataFolder(), "lang.yml"));
    }

    @Override
    public void onEnable() {
        this.registerAllManagers();
        Settings.load(this, this.get(WorldsManager.class), this.get(LevelsManager.class));
        this.saveDefaultConfig();
        ru.sortix.parkourbeat.game.movement.GameMoveHandler.MAX_LOOK_ANGLE =
            this.getConfig().getDouble("max_look_angle", 100.0D);
        ru.sortix.parkourbeat.game.movement.GameMoveHandler.BACKWARD_TOLERANCE =
            this.getConfig().getDouble("backward_tolerance", 0.0D);
        ru.sortix.parkourbeat.world.AutoLookSettings.load(this);
        this.registerAllCommands();
        this.registerAllListeners();
        this.restoreReloadState();
    }

    @Override
    public void onDisable() {
        this.saveReloadState();

        this.unregisterAllListeners();
        this.unregisterAllCommands();
        this.unregisterAllManagers();
        Settings.unload();
    }

    private void registerAllManagers() {
        this.registerManager(ItemsManager::new);
        this.registerManager(PlayersCollisionManager::new);
        this.registerManager(SkyTimeManager::new);
        this.registerManager(WorldsManager::new);
        this.registerManager(ActivityManager::new);
        this.registerManager(MusicTracksManager::new);
        this.registerManager(LevelsManager::new);
        this.registerManager(LevelWorldsManager::new);
        this.registerManager(GlowingBarriersManager::new);
        this.registerManager(PlayersInputManager::new);
        this.registerManager(CustomPhysicsManager::new);
        this.registerManager(WorldEditAccessManager::new);

        this.registerManager(ru.sortix.parkourbeat.player.PingManager::new);
        this.registerManager(ru.sortix.parkourbeat.inventory.LobbyItems::new);
        this.registerManager(ru.sortix.parkourbeat.rating.StatisticsManager::new);
        this.registerManager(ru.sortix.parkourbeat.stats.StatResetRequestManager::new);
        this.registerManager(ru.sortix.parkourbeat.player.PlayersVisibilityManager::new);
        this.registerManager(ru.sortix.parkourbeat.player.scoreboard.ScoreboardManager::new);
    }

    private void registerManager(@NonNull Function<ParkourBeat, PluginManager> commandConstructor) {
        PluginManager manager;
        try {
            manager = commandConstructor.apply(this);
        } catch (Exception e) {
            throw new RuntimeException("Unable to create manager", e);
        }
        if (this.managers.put(manager.getClass(), manager) != null) {
            throw new IllegalStateException("Duplicate manager with class " + manager.getClass());
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private void registerAllCommands() {
        liteCommands = LiteBukkitFactory.builder(getName().toLowerCase(Locale.ROOT), this)
            .commands(
                new CommandConvertData(this),
                new CommandCreate(this),
                new CommandDelete(this),
                new CommandEdit(this),
                new CommandModerate(this),
                new CommandPhysicsDebug(this),
                new CommandPlay(this),
                new CommandSpawn(this),
                new CommandStatus(this),
                new CommandStatReset(this),
                new CommandTemplate(this),
                new CommandTest(this),
                new CommandTpToWorld(this),
                new CommandUpdateTrack(this),
                new CommandBackTolerance(this),
                new CommandBackTol(this),
                new CommandAutoLook(this)
            )
            .argument(GameSettings.class, ArgumentKey.of("settings-console-owning"), new GameSettingsArgumentResolver(get(LevelsManager.class), false, true, true))
            .argument(GameSettings.class, ArgumentKey.of("settings-players-owning"), new GameSettingsArgumentResolver(get(LevelsManager.class), true, false, true))
            .argument(GameSettings.class, ArgumentKey.of("settings-players-all"), new GameSettingsArgumentResolver(get(LevelsManager.class), true, false, false))
            .message(LiteBukkitMessages.PLAYER_ONLY, Messages.PLAYER_ONLY)
            .message(LiteMessages.MISSING_PERMISSIONS, Messages.MISSING_PERMISSION)
            .invalidUsage(new DefaultInvalidUsageHandler())
            .schematicGenerator(SchematicFormat.angleBrackets())
            .build();
    }

    private void registerAllListeners() {
        this.registerListener(FixesListener::new);
        this.registerListener(GamesListener::new);
        this.registerListener(GlowingBarriersListener::new);
        this.registerListener(LightShowWandListener::new);
        this.registerListener(WorldsListener::new);
        this.registerListener(InventoriesListener::new);
        this.registerListener(PhysicsListener::new);
        this.registerListener(WorldEditGuardListener::new);
        this.registerListener(ru.sortix.parkourbeat.listeners.LobbyItemsListener::new);
        this.registerListener(ru.sortix.parkourbeat.listeners.StatisticsListener::new);
    }

    private void registerListener(@NonNull Function<ParkourBeat, Listener> listenerConstructor) {
        Listener listener = listenerConstructor.apply(this);
        this.getServer().getPluginManager().registerEvents(listener, this);
    }

    private void unregisterAllManagers() {
        List<PluginManager> managersToDisable = new ArrayList<>(this.managers.values());
        Collections.reverse(managersToDisable);
        for (PluginManager manager : managersToDisable) {
            unregisterSafely(manager::disable);
        }
        this.managers.clear();
    }

    private void unregisterAllCommands() {
        unregisterSafely(() -> {
            if (liteCommands != null) {
                liteCommands.unregister();
                liteCommands = null;
            }
        });
    }


    private void saveReloadState() {
        try {
            File file = new File(getDataFolder(), "reload_state.yml");
            org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
            ru.sortix.parkourbeat.activity.ActivityManager activityManager = get(ru.sortix.parkourbeat.activity.ActivityManager.class);

            boolean hasData = false;
            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                ru.sortix.parkourbeat.activity.UserActivity activity = activityManager.getActivity(player);
                if (activity != null) {
                    String type = null;
                    if (activity instanceof ru.sortix.parkourbeat.activity.type.EditActivity) type = "EDIT";
                    else if (activity instanceof ru.sortix.parkourbeat.activity.type.PlayActivity) type = "PLAY";

                    if (type != null) {
                        String path = player.getUniqueId().toString();
                        config.set(path + ".level", activity.getLevel().getUniqueId().toString());
                        config.set(path + ".activity", type);
                        hasData = true;

                        player.showTitle(net.kyori.adventure.title.Title.title(
                            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&d&lParkourBeat перезагружается"),
                            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&fПодождите немного, скоро мы вернём вас..."),
                            net.kyori.adventure.title.Title.Times.of(
                                java.time.Duration.ofMillis(200),
                                java.time.Duration.ofSeconds(10),
                                java.time.Duration.ofSeconds(1)
                            )
                        ));
                    }
                }
            }
            if (hasData) {
                config.save(file);
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to save reload state", e);
        }
    }

    private void restoreReloadState() {
        try {
            File file = new File(getDataFolder(), "reload_state.yml");
            if (!file.exists()) return;

            org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            file.delete();

            ru.sortix.parkourbeat.levels.LevelsManager levelsManager = get(ru.sortix.parkourbeat.levels.LevelsManager.class);
            getServer().getScheduler().runTaskLater(this, () -> {
                for (String uuidStr : config.getKeys(false)) {
                    try {
                        UUID playerId = UUID.fromString(uuidStr);
                        org.bukkit.entity.Player player = getServer().getPlayer(playerId);
                        if (player == null || !player.isOnline()) continue;

                        UUID levelId = UUID.fromString(config.getString(uuidStr + ".level"));
                        String type = config.getString(uuidStr + ".activity");

                        ru.sortix.parkourbeat.levels.settings.GameSettings settings = levelsManager.getAvailableLevelSettings(levelId);
                        if (settings != null) {
                            if ("EDIT".equals(type)) {
                                ru.sortix.parkourbeat.inventory.type.LevelsListMenu.startEditing(this, player, settings, false);
                            } else if ("PLAY".equals(type)) {
                                ru.sortix.parkourbeat.inventory.type.LevelsListMenu.startPlaying(this, player, settings);
                            }
                        }
                    } catch (Exception ex) {
                        getLogger().log(Level.WARNING, "Failed to restore session for " + uuidStr, ex);
                    }
                }
            }, 20L);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to restore reload state", e);
        }
    }

    private void unregisterAllListeners() {
        unregisterSafely(() -> HandlerList.unregisterAll(this));
    }

    private void unregisterSafely(Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException e) {
            this.getLogger().log(Level.SEVERE, "An occurred error while disabling plugin", e);
        }
    }

    @NonNull
    public <M extends PluginManager> M get(@NonNull Class<M> managerClass) {
        Object manager = this.managers.get(managerClass);
        if (manager == null) {
            throw new IllegalArgumentException("Manager with class " + managerClass.getName() + " not found");
        }
        try {
            return managerClass.cast(manager);
        } catch (ClassCastException ex) {
            throw new IllegalArgumentException(
                "Manager " + manager.getClass().getName() + " isn't " + managerClass.getName());
        }
    }
}
