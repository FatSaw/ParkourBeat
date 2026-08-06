package ru.sortix.parkourbeat.data;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.DirectionChecker;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.dao.LevelSettingDAO;
import ru.sortix.parkourbeat.levels.settings.WorldSettings;
import ru.sortix.parkourbeat.utils.ConfigUtils;
import ru.sortix.parkourbeat.world.Cuboid;
import ru.sortix.parkourbeat.world.WorldsManager;

import javax.annotation.Nullable;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

@UtilityClass
public class Settings {
    private boolean isLoaded = false;

    private @Getter Location lobbySpawn;

    private @Getter Map<DirectionChecker.Direction, Cuboid> levelFixedEditableArea;

    private @Getter Map<World.Environment, WorldSettings> defaultSettings;

    public void load(@NonNull ParkourBeat plugin, @NonNull WorldsManager worldsManager, @NonNull LevelsManager levelsManager) {
        if (isLoaded) throw new IllegalStateException("Settings already loaded");

        plugin.saveDefaultConfig();

        ConfigurationSection rootConfig = plugin.getConfig();

        ConfigurationSection lobbyConfig = rootConfig.getConfigurationSection("lobby");
        if (lobbyConfig == null) {
            throw new IllegalArgumentException("Section \"default_level\" not found");
        }
        lobbySpawn = getLocation(lobbyConfig, "spawn_pos", worldsManager, true);
        lobbySpawn.getWorld().setSpawnLocation(lobbySpawn);

        ConfigurationSection allLevelsConfig = rootConfig.getConfigurationSection("all_levels");
        if (allLevelsConfig == null) {
            throw new IllegalArgumentException("Section \"all_levels\" not found");
        }

        levelFixedEditableArea = new HashMap<>();
        for (String key : allLevelsConfig.getKeys(false)) {
            try {
                DirectionChecker.Direction direction = DirectionChecker.Direction.valueOf(key);
                ConfigurationSection directionConfig = allLevelsConfig.getConfigurationSection(key);
                if (directionConfig == null) throw new IllegalArgumentException("Not a section");
                levelFixedEditableArea.put(direction, new Cuboid(
                    ConfigUtils.parseVector(directionConfig.getString("min_editable_point")),
                    ConfigUtils.parseVector(directionConfig.getString("max_editable_point"))
                ));
            } catch (Exception e) {
                throw new IllegalArgumentException("Unable to load all_levels." + key, e);
            }
        }

        defaultSettings = new HashMap<>();
        LevelSettingDAO levelSettingDAO = levelsManager.getLevelsSettings().getLevelSettingDAO();

        for (World.Environment env : World.Environment.values()) {
            File settingsDir = new File(new File(plugin.getDataFolder(), "pb_default_level_" + env.name()), "parkourbeat");
            if (settingsDir.isDirectory()) {
                try {
                    defaultSettings.put(env, levelSettingDAO.loadLevelWorldSettings(settingsDir));
                } catch (Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Unable to load default settings for " + env, e);
                }
            }
        }

        File legacySettingsDir = new File(new File(plugin.getDataFolder(), "pb_default_level"), "parkourbeat");
        if (legacySettingsDir.isDirectory()) {
            try {
                WorldSettings legacy = levelSettingDAO.loadLevelWorldSettings(legacySettingsDir);
                for (World.Environment env : World.Environment.values()) {
                    defaultSettings.putIfAbsent(env, legacy);
                }
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Unable to load legacy default settings", e);
            }
        }

        if (defaultSettings.isEmpty()) {
            throw new RuntimeException("Unable to load any default level settings! Please create pb_default_level directory.");
        }

        isLoaded = true;
    }

    public static void unload() {
        isLoaded = false;
        lobbySpawn = null;
        levelFixedEditableArea = null;
        if (defaultSettings != null) defaultSettings.clear();
        defaultSettings = null;
    }

    public static WorldSettings getDefaultSettings(World.Environment env) {
        WorldSettings settings = defaultSettings.get(env);
        if (settings == null) {
            settings = defaultSettings.get(World.Environment.NORMAL);
        }
        if (settings == null) {
            settings = defaultSettings.values().iterator().next();
        }
        return settings;
    }

    @NonNull
    private WorldCreator newWorldCreator(@NonNull String worldName) {
        WorldCreator worldCreator = new WorldCreator(worldName);
        worldCreator.seed(0L);
        worldCreator.environment(World.Environment.NORMAL);
        worldCreator.type(WorldType.FLAT);
        worldCreator.generateStructures(false);
        return worldCreator;
    }

    @NonNull
    private Location getLocation(
        @NonNull ConfigurationSection config,
        @NonNull String key,
        @Nullable WorldsManager worldsManager,
        boolean parseYawPitch) {
        ConfigurationSection section = config.getConfigurationSection(key);
        double x = section.getDouble("x", 0);
        double y = section.getDouble("y", 0);
        double z = section.getDouble("z", 0);
        float yaw = parseYawPitch ? (float) section.getDouble("yaw", 0) : 0f;
        float pitch = parseYawPitch ? (float) section.getDouble("pitch", 0) : 0f;

        World world;
        if (worldsManager == null) {
            world = null;
        } else {
            String worldName = section.getString("world");
            if (worldName == null) {
                throw new IllegalArgumentException("World name not provided");
            }
            try {
                WorldCreator worldCreator = newWorldCreator(worldName);
                world = worldsManager
                    .createWorldFromDefaultContainer(worldCreator, worldsManager.getCurrentThreadExecutor())
                    .join();
                if (world == null) {
                    throw new IllegalArgumentException("Unable to create bukkit world from default container");
                }
            } catch (Exception e) {
                throw new RuntimeException("Unable to load world from config", e);
            }
        }

        return new Location(world, x, y, z, yaw, pitch);
    }
}
