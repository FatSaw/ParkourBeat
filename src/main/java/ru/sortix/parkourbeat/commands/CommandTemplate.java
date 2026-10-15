package ru.sortix.parkourbeat.commands;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import lombok.NonNull;
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
import ru.sortix.parkourbeat.utils.text.PbText;
import ru.sortix.parkourbeat.world.WorldsManager;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import ru.sortix.parkourbeat.world.LevelTransformer;
import org.bukkit.Location;

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
    public void onCommand(@Context Player sender, @Arg("environment") String envName,
                          @Arg("size") java.util.Optional<String> sizeArg) {
        // Второй аргумент - размер базы: "4c" сохраняет шаблон для широких уровней,
        // без него всё работает как раньше, для обычных одночанковых.
        boolean wide = sizeArg.isPresent() && sizeArg.get().equalsIgnoreCase("4c");

        // 2d_normal / 2d_nether / 2d_the_end - шаблоны двумерных уровней.
        // Измерение у них обычное, отличается только папка, в которую ложится база.
        String rawEnv = envName.trim();
        boolean twoD = rawEnv.toLowerCase(java.util.Locale.ROOT).startsWith("2d_");
        if (twoD) rawEnv = rawEnv.substring(3);

        World.Environment targetEnv;
        try {
            targetEnv = World.Environment.valueOf(rawEnv.toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.1")
                + Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.2"));
            return;
        }

        UserActivity activity = plugin.get(ActivityManager.class).getActivity(sender);
        if (!(activity instanceof EditActivity)) {
            sender.sendMessage(Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.3"));
            return;
        }
        EditActivity editActivity = (EditActivity) activity;
        Level level = editActivity.getLevel();
        World world = level.getWorld();

        final boolean isTwoD = twoD;
        sender.sendMessage(Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.4")
            + (isTwoD ? "2D " : "") + (wide ? Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.5") : "")
            + Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.6") + targetEnv.name() + "...");

        world.save();
        plugin.get(LevelsManager.class).saveLevelSettings(level.getUniqueId());

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LevelSettingDAO baseDao = plugin.get(LevelsManager.class).getLevelsSettings().getLevelSettingDAO();
                if (!(baseDao instanceof FileLevelSettingDAO)) {
                    sender.sendMessage(Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.7"));
                    return;
                }
                FileLevelSettingDAO dao = (FileLevelSettingDAO) baseDao;

                File targetDir = new File(plugin.getDataFolder(),
                    "pb_default_level_" + (isTwoD ? "2D_" : "") + targetEnv.name()
                        + (wide ? "_4C" : ""));
                deleteDirectory(targetDir);
                targetDir.mkdirs();

                // ЧЁРНОЕ НЕБО. Готовый шаблон копируется в новый мир целиком, как есть.
                // Раньше сюда клали только регионы, и level.dat терялся - Bukkit создавал
                // его заново со своими настройками измерения. Небо (точнее, нижняя его
                // половина под горизонтом) в таком мире перестаёт рисоваться: тот самый
                // MC-186115 / MC-257056. Штатный шаблон из поставки level.dat содержал,
                // поэтому до первого /template set всё выглядело правильно.
                copyWorldMeta(plugin, world.getWorldFolder(), targetDir);

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

                    // ЧЁРНОЕ НЕБО. Раньше в шаблон уезжал файл настроек уровня ЦЕЛИКОМ,
                    // вместе со световым шоу: базовым небом, метками смены неба, вспышками,
                    // погодой и биомными зонами. Всё это привязано к музыке конкретного
                    // уровня, а новый уровень пустой - метки срабатывают в неподходящий
                    // момент и оставляют небо тёмным. Ночное базовое небо строителя точно
                    // так же наследовалось каждым новым уровнем.
                    //
                    // Шаблон - это постройка, спавн и точки маршрута. Оформление света
                    // у каждого уровня своё, поэтому сбрасываем его на стандартное.
                    config.set("lightshow", null);
                    config.set("glowing_barriers", null);

                    // Старт и финиш нигде отдельно не хранятся - они вычисляются как
                    // первая и последняя точка пути из частиц. Значит, в шаблон должен
                    // попасть сам путь; проверяем это явно, чтобы админ не гадал, почему
                    // новые уровни получились с точками из старой базы.
                    int waypointsCount = config.getStringList("waypoints").size();

                    config.save(targetWorldSettingsFile);

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        try {
                            WorldSettings newDefault = dao.loadLevelWorldSettings(targetSettingsDir);
                            if (isTwoD) {
                                Settings.getTwoDDefaultSettings().put(targetEnv, newDefault);
                            } else if (wide) {
                                Settings.getWideDefaultSettings().put(targetEnv, newDefault);
                            } else {
                                Settings.getDefaultSettings().put(targetEnv, newDefault);
                            }
                            sender.sendMessage(Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.8") + (isTwoD ? "2D " : "")
                                + (wide ? Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.9") : "")
                                + Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.10") + targetEnv.name() + Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.11"));
                            sender.sendMessage(Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.12")
                                + Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.13"));
                            if (waypointsCount >= 2) {
                                sender.sendMessage(Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.14")
                                    + waypointsCount + Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.15")
                                    + Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.16"));
                            } else {
                                sender.sendMessage(Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.17")
                                    + Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.18")
                                    + Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.19"));
                            }
                        } catch (Exception e) {
                            sender.sendMessage(Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.20"));
                            e.printStackTrace();
                        }
                    });
                } else {
                    sender.sendMessage(Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.21"));
                }
            } catch (Exception e) {
                sender.sendMessage(Lang.raw(PlayerLang.of(sender), "auto.command_template.on_command.22"));
                e.printStackTrace();
            }
        });
    }

    // ==================== ПЕРЕНОС ШАБЛОНОВ ====================

    /**
     * ПОДНЯТЬ И РАЗВЕРНУТЬ ВСЕ ШАБЛОНЫ РАЗОМ.
     * <p>
     * {@code /pb template transform 3 rotate} - поднять на три блока и развернуть.
     * Без второго слова только поднимает. Отрицательное число опускает.
     * <p>
     * РАБОТАЕТ С ПАПКАМИ ШАБЛОНОВ, А НЕ С ЗАГРУЖЕННЫМИ УРОВНЯМИ. Уже построенные карты
     * не трогаются вовсе - ни одна.
     * <p>
     * Переносить постройку прямо в живом уровне нельзя, и это не осторожность, а опыт:
     * у уровня есть граница области редактирования, а за ней стоит чистильщик, который
     * стирает всё появившееся снаружи. Разворот же переносит постройку на другую
     * сторону от спавна - то есть ровно туда, куда уровню строить запрещено. Блоки
     * честно переезжали и тут же исчезали, а в чат сыпалось «за границей уровня».
     * <p>
     * Поэтому папка шаблона поднимается ВРЕМЕННЫМ МИРОМ. У временного мира нет ни
     * уровня, ни границ, ни чистильщика, ни игроков, ни редактора - там переносить
     * можно свободно. После работы папка шаблона заменяется целиком, а временный мир
     * стирается.
     */
    @Execute(name = "transform")
    @Permission("parkourbeat.command.template.set")
    public void onTransform(@Context CommandSender sender,
                            @Arg("blocks") int blocks,
                            @Arg("rotate") java.util.Optional<String> rotateArg) {
        boolean rotate = rotateArg.isPresent() && rotateArg.get().equalsIgnoreCase("rotate");

        if (blocks == 0 && !rotate) {
            sender.sendMessage(PbText.of("&cНечего делать: подъём ноль и разворота нет."
                + " Например: &f/pb template transform 3 rotate"));
            return;
        }

        List<File> templates = new ArrayList<>();
        File[] entries = this.plugin.getDataFolder().listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (!entry.isDirectory()) continue;
                if (!entry.getName().startsWith("pb_default_level")) continue;
                if (!new File(entry, "parkourbeat").isDirectory()) continue;
                templates.add(entry);
            }
        }
        templates.sort(java.util.Comparator.comparing(File::getName));

        if (templates.isEmpty()) {
            sender.sendMessage(PbText.of("&cНи одной папки шаблона не нашлось."));
            return;
        }

        sender.sendMessage(PbText.of("&7Найдено шаблонов: &f" + templates.size()
            + "&7. Копии лягут в &fpb_default_backup&7."));
        this.transformNext(sender, templates, 0, blocks, rotate);
    }

    private void transformNext(@NonNull CommandSender sender, @NonNull List<File> templates,
                               int index, int deltaY, boolean rotate) {
        if (index >= templates.size()) {
            sender.sendMessage(PbText.of("&aГотово. Шаблоны перенесены,"
                + " уже построенные уровни не тронуты."));
            return;
        }

        File template = templates.get(index);
        sender.sendMessage(PbText.of("&8[" + (index + 1) + "/" + templates.size() + "] &7"
            + template.getName()));

        this.transformTemplate(sender, template, deltaY, rotate,
            () -> this.transformNext(sender, templates, index + 1, deltaY, rotate));
    }

    private void transformTemplate(@NonNull CommandSender sender, @NonNull File template,
                                   int deltaY, boolean rotate, @NonNull Runnable next) {
        FileLevelSettingDAO dao = this.fileDao();
        if (dao == null) {
            sender.sendMessage(PbText.of("&cНастройки уровней хранятся не в файлах."));
            return;
        }

        File settingsDir = new File(template, "parkourbeat");
        WorldSettings settings;
        try {
            settings = dao.loadLevelWorldSettings(settingsDir);
        } catch (Exception e) {
            sender.sendMessage(PbText.of("&c  пропускаю: не читаются настройки"));
            next.run();
            return;
        }

        Location spawn = settings.getSpawn();
        if (spawn == null) {
            sender.sendMessage(PbText.of("&c  пропускаю: нет точки спавна"));
            next.run();
            return;
        }

        // ВЫСОТА ПРОВЕРЯЕТСЯ ДО НАЧАЛА. Уровень, у которого верхушка не влезла в мир, -
        // это молча срезанная постройка: блоки уже сняты со старого места.
        int newSpawnY = spawn.getBlockY() + deltaY;
        if (newSpawnY < 0 || newSpawnY > 250) {
            sender.sendMessage(PbText.of("&c  пропускаю: спавн уехал бы на Y=" + newSpawnY));
            next.run();
            return;
        }

        World.Environment environment = environmentOf(template.getName());
        LevelTransformer.Transform transform = new LevelTransformer.Transform(
            rotate, deltaY, spawn.getBlockX(), spawn.getBlockZ());

        String worldName = "pb_tpl_" + template.getName().toLowerCase(java.util.Locale.ROOT);
        if (this.plugin.getServer().getWorld(worldName) != null) {
            sender.sendMessage(PbText.of("&c  пропускаю: мир " + worldName + " уже загружен"));
            next.run();
            return;
        }

        try {
            this.backup(template);
        } catch (Exception e) {
            sender.sendMessage(PbText.of("&c  пропускаю: не вышло сделать копию"));
            this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Не удалось сохранить копию шаблона " + template.getName(), e);
            next.run();
            return;
        }

        // Остатки прошлого запуска: если сервер выключили посреди работы, папка
        // временного мира могла пережить перезапуск.
        deleteDirectory(new File(this.plugin.getServer().getWorldContainer(), worldName));

        WorldCreator creator = new WorldCreator(worldName);
        creator.generator(this.plugin.get(WorldsManager.class).getEmptyGenerator());
        creator.environment(environment);
        creator.generateStructures(false);

        this.plugin.get(WorldsManager.class)
            .createWorldFromCustomDirectory(creator, template, true)
            .thenAccept(world -> {
                if (world == null) {
                    sender.sendMessage(PbText.of("&c  пропускаю: мир не поднялся"));
                    next.run();
                    return;
                }

                LevelTransformer.transformBlocksAsync(this.plugin, world, transform,
                    line -> sender.sendMessage(PbText.of("&8  " + line)),
                    error -> {
                        if (error != null) {
                            sender.sendMessage(PbText.of("&c  " + error));
                            this.dropWorld(world, () -> next.run());
                            return;
                        }
                        this.finishTemplate(sender, template, world, settings, transform, next);
                    });
            });
    }

    /**
     * Записать результат обратно в папку шаблона.
     * <p>
     * Папка заменяется ЦЕЛИКОМ, а не по файлам. Разворот меняет набор занятых чанков:
     * там, где постройка была, её больше нет, и старые файлы региона обязаны исчезнуть.
     * Подкладывание новых поверх старых оставило бы на шаблоне обе копии - и до
     * поворота, и после.
     */
    private void finishTemplate(@NonNull CommandSender sender, @NonNull File template,
                                @NonNull World world, @NonNull WorldSettings settings,
                                @NonNull LevelTransformer.Transform transform,
                                @NonNull Runnable next) {
        File worldFolder = world.getWorldFolder();

        world.save();
        this.dropWorld(world, () -> {
            try {
                LevelTransformer.applySettings(settings, transform);

                deleteDirectory(template);
                //noinspection ResultOfMethodCallIgnored
                template.mkdirs();
                this.copyWorldMeta(this.plugin, worldFolder, template);

                // Регионы в copyWorldMeta не попадают - он их намеренно пропускает,
                // потому что при записи шаблона они кладутся с учётом смены измерения.
                // Здесь измерение то же самое, так что просто переносим как есть.
                for (String folder : new String[]{"region", "DIM-1/region", "DIM1/region"}) {
                    File source = new File(worldFolder, folder);
                    if (!source.isDirectory()) continue;
                    File target = new File(template, folder);
                    //noinspection ResultOfMethodCallIgnored
                    target.mkdirs();
                    Files.walkFileTree(source.toPath(), new CopyDirVisitor(
                        this.plugin.getLogger(), source.toPath(), target.toPath()));
                }

                File settingsDir = new File(template, "parkourbeat");
                //noinspection ResultOfMethodCallIgnored
                settingsDir.mkdirs();
                YamlConfiguration config = new YamlConfiguration();
                new ru.sortix.parkourbeat.levels.dao.files.WorldSettingsDAO()
                    .write(settings, config);
                config.save(new File(settingsDir, "world_settings.yml"));

                deleteDirectory(worldFolder);

                this.reloadTemplate(template.getName(), settings);
                sender.sendMessage(PbText.of("&a  перенесён"));
            } catch (Exception e) {
                sender.sendMessage(PbText.of("&c  не удалось записать обратно,"
                    + " копия лежит в pb_default_backup"));
                this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Не удалось записать шаблон " + template.getName(), e);
            }
            next.run();
        });
    }

    private void dropWorld(@NonNull World world, @NonNull Runnable next) {
        Location fallback = this.plugin.getServer().getWorlds().get(0).getSpawnLocation();
        this.plugin.get(WorldsManager.class)
            .unloadBukkitWorld(world, true, chunk -> true, fallback, false)
            .thenAccept(ok -> this.plugin.getServer().getScheduler()
                .runTask(this.plugin, next));
    }

    /**
     * Обновить шаблон в памяти, чтобы следующий {@code /pb create} взял новый.
     * <p>
     * Без этого новые уровни до перезапуска сервера создавались бы по старому шаблону,
     * а на диске лежал бы уже новый - разница, которую замечают не сразу.
     */
    private void reloadTemplate(@NonNull String folderName, @NonNull WorldSettings settings) {
        String name = folderName.substring("pb_default_level".length());
        if (name.startsWith("_")) name = name.substring(1);

        boolean wide = name.toUpperCase(java.util.Locale.ROOT).endsWith("_4C");
        boolean twoD = name.toUpperCase(java.util.Locale.ROOT).startsWith("2D_");
        World.Environment environment = environmentOf(folderName);

        if (twoD) {
            Settings.getTwoDDefaultSettings().put(environment, settings);
        } else if (wide) {
            Settings.getWideDefaultSettings().put(environment, settings);
        } else {
            Settings.getDefaultSettings().put(environment, settings);
        }
    }

    /** Копия папки шаблона. Если копия уже есть, новая не делается: первая ценнее. */
    private void backup(@NonNull File template) throws java.io.IOException {
        File backupRoot = new File(this.plugin.getDataFolder(), "pb_default_backup");
        File backup = new File(backupRoot, template.getName());
        if (backup.isDirectory()) return;

        //noinspection ResultOfMethodCallIgnored
        backup.mkdirs();
        Files.walkFileTree(template.toPath(),
            new CopyDirVisitor(this.plugin.getLogger(), template.toPath(), backup.toPath()));
    }

    /** Измерение по имени папки: pb_default_level_2D_NETHER, ..._THE_END_4C и так далее. */
    @NonNull
    private static World.Environment environmentOf(@NonNull String folderName) {
        String name = folderName.toUpperCase(java.util.Locale.ROOT);
        if (name.contains("THE_END")) return World.Environment.THE_END;
        if (name.contains("NETHER")) return World.Environment.NETHER;
        return World.Environment.NORMAL;
    }

    @Nullable
    private FileLevelSettingDAO fileDao() {
        LevelSettingDAO dao = this.plugin.get(LevelsManager.class)
            .getLevelsSettings().getLevelSettingDAO();
        return dao instanceof FileLevelSettingDAO ? (FileLevelSettingDAO) dao : null;
    }

    /**
     * Файлы мира, которые нельзя переносить в шаблон: они привязаны к конкретному
     * запущенному миру или к конкретным игрокам.
     */
    private static final java.util.Set<String> SKIPPED_WORLD_FILES = java.util.Set.of(
        "session.lock", "uid.dat", "level.dat_old",
        "playerdata", "stats", "advancements", "parkourbeat",
        "region", "DIM-1", "DIM1"
    );

    /**
     * Переносит в шаблон всё, кроме регионов (их кладут отдельно, с учётом смены
     * измерения) и служебных файлов. Главное здесь - level.dat.
     */
    private void copyWorldMeta(@NonNull ParkourBeat plugin, @NonNull File worldFolder,
                               @NonNull File targetDir) throws java.io.IOException {
        File[] entries = worldFolder.listFiles();
        if (entries == null) return;

        for (File entry : entries) {
            if (SKIPPED_WORLD_FILES.contains(entry.getName())) continue;

            File target = new File(targetDir, entry.getName());
            if (entry.isDirectory()) {
                target.mkdirs();
                Files.walkFileTree(entry.toPath(),
                    new CopyDirVisitor(plugin.getLogger(), entry.toPath(), target.toPath()));
            } else {
                Files.copy(entry.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
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
