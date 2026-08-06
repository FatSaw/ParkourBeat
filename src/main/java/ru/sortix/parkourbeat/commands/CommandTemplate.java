package ru.sortix.parkourbeat.commands;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.dao.LevelSettingDAO;
import ru.sortix.parkourbeat.levels.dao.files.FileLevelSettingDAO;
import ru.sortix.parkourbeat.levels.settings.WorldSettings;
import ru.sortix.parkourbeat.utils.java.CopyDirVisitor;

import java.io.File;
import java.nio.file.Files;

@Command(name = "template")
public class CommandTemplate {

    private final ParkourBeat plugin;

    public CommandTemplate(ParkourBeat plugin) {
        this.plugin = plugin;
    }

    @Execute(name = "set")
    @Permission("parkourbeat.command.template.set")
    public void onCommand(@Context Player sender, @Arg("environment") String envName) {
        World.Environment targetEnv;
        try {
            targetEnv = World.Environment.valueOf(envName.toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cНеверное измерение! Используйте: NORMAL, NETHER или THE_END");
            return;
        }

        UserActivity activity = plugin.get(ActivityManager.class).getActivity(sender);
        if (!(activity instanceof EditActivity)) {
            sender.sendMessage("§cВы должны находиться в редакторе уровня, чтобы сохранить его как шаблон.");
            return;
        }
        EditActivity editActivity = (EditActivity) activity;
        Level level = editActivity.getLevel();
        World world = level.getWorld();

        sender.sendMessage("§eСохранение уровня как шаблона для " + targetEnv.name() + "...");

        world.save();
        plugin.get(LevelsManager.class).saveLevelSettings(level.getUniqueId());

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LevelSettingDAO baseDao = plugin.get(LevelsManager.class).getLevelsSettings().getLevelSettingDAO();
                if (!(baseDao instanceof FileLevelSettingDAO)) {
                    sender.sendMessage("§cШаблоны поддерживаются только при файловом хранении уровней.");
                    return;
                }
                FileLevelSettingDAO dao = (FileLevelSettingDAO) baseDao;

                File targetDir = new File(plugin.getDataFolder(), "pb_default_level_" + targetEnv.name());
                deleteDirectory(targetDir);
                targetDir.mkdirs();

                File sourceRegionDir = new File(world.getWorldFolder(), getRegionFolder(world.getEnvironment()));
                File targetRegionDir = new File(targetDir, getRegionFolder(targetEnv));

                if (targetRegionDir.getParentFile() != null) {
                    targetRegionDir.getParentFile().mkdirs();
                }
                targetRegionDir.mkdirs();

                if (sourceRegionDir.exists()) {
                    Files.walkFileTree(sourceRegionDir.toPath(), new CopyDirVisitor(plugin.getLogger(), sourceRegionDir.toPath(), targetRegionDir.toPath()));
                }

                File sourceSettingsDir = new File(dao.getBukkitWorldDirectory(level.getUniqueId()), "parkourbeat");
                File targetSettingsDir = new File(targetDir, "parkourbeat");
                targetSettingsDir.mkdirs();

                File sourceWorldSettingsFile = new File(sourceSettingsDir, "world_settings.yml");
                File targetWorldSettingsFile = new File(targetSettingsDir, "world_settings.yml");

                if (sourceWorldSettingsFile.exists()) {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(sourceWorldSettingsFile);
                    config.set("environment", targetEnv.name());
                    config.save(targetWorldSettingsFile);

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        try {
                            WorldSettings newDefault = dao.loadLevelWorldSettings(targetSettingsDir);
                            Settings.getDefaultSettings().put(targetEnv, newDefault);
                            sender.sendMessage("§aШаблон для измерения " + targetEnv.name() + " успешно сохранен и применен!");
                        } catch (Exception e) {
                            sender.sendMessage("§cШаблон сохранен, но не удалось перезагрузить его в памяти.");
                            e.printStackTrace();
                        }
                    });
                } else {
                    sender.sendMessage("§cФайл world_settings.yml не найден в исходном уровне.");
                }
            } catch (Exception e) {
                sender.sendMessage("§cПроизошла ошибка при сохранении шаблона.");
                e.printStackTrace();
            }
        });
    }

    private String getRegionFolder(World.Environment env) {
        switch (env) {
            case NETHER: return "DIM-1/region";
            case THE_END: return "DIM1/region";
            default: return "region";
        }
    }

    private void deleteDirectory(File directory) {
        if (!directory.exists()) return;
        File[] allContents = directory.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directory.delete();
    }
}
