package ru.sortix.parkourbeat.world;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.inventory.type.LevelsListMenu;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.dao.LevelSettingDAO;
import ru.sortix.parkourbeat.levels.dao.files.FileLevelSettingDAO;
import ru.sortix.parkourbeat.levels.settings.GameSettings;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import ru.sortix.parkourbeat.utils.text.PbText;
/**
 * Меняет измерение уровня, не трогая ни один блок и ни одну настройку.
 * <p>
 * Мир уровня - это обычная папка мира Bukkit, а измерение выбирается не содержимым
 * папки, а тем, какой {@code environment} передан в {@code WorldCreator} при загрузке.
 * Сами чанки лежат в подпапке, зависящей от измерения: обычный мир - в корне, ад -
 * в {@code DIM-1}, энд - в {@code DIM1}. Поэтому смена мира сводится к переименованию
 * пары папок и одной строки в {@code world_settings.yml}: это мгновенно на любом
 * размере уровня и не копирует ни байта. Настройки уровня (путь частиц, цветовое шоу,
 * порталы, барьеры, чекпоинты) лежат отдельным yaml внутри папки мира и не двигаются
 * вообще.
 */
public final class WorldTypeSwitcher {
    /** Всё, что относится к конкретному измерению и обязано переехать вместе с блоками. */
    private static final String[] DIMENSION_FOLDERS = {"region", "entities", "poi"};

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private WorldTypeSwitcher() {
    }

    @NonNull
    public static String getDisplayName(@NonNull World.Environment environment) {
        switch (environment) {
            case NETHER:
                return "&cАд";
            case THE_END:
                return "&dЭнд";
            default:
                return "&aОбычный мир";
        }
    }

    /**
     * Запускает смену мира. Вызывается только из основного потока.
     */
    public static void switchEnvironment(@NonNull ParkourBeat plugin,
                                         @NonNull Level level,
                                         @NonNull World.Environment target,
                                         @NonNull Player initiator
    ) {
        LevelsManager levelsManager = plugin.get(LevelsManager.class);
        ActivityManager activityManager = plugin.get(ActivityManager.class);

        UUID levelId = level.getUniqueId();
        GameSettings gameSettings = level.getLevelSettings().getGameSettings();
        World.Environment current = level.getLevelSettings().getWorldSettings().getEnvironment();

        if (current == target) {
            send(initiator, Lang.raw(PlayerLang.of(initiator), "auto.world_type_switcher.switch_environment.1"));
            return;
        }

        if (levelsManager.isLevelLocked(levelId)) {
            send(initiator, Lang.raw(PlayerLang.of(initiator), "auto.world_type_switcher.switch_environment.2"));
            return;
        }

        World world = level.getWorld();

        // Тестовый забег держит вторую активность поверх редактора: снимать её на ходу
        // и потом собирать обратно - лишний источник ошибок, проще попросить выйти.
        List<UUID> editorsToReturn = new ArrayList<>();
        for (Player player : new ArrayList<>(world.getPlayers())) {
            UserActivity activity = activityManager.getActivity(player);
            if (!(activity instanceof EditActivity)) continue;
            if (((EditActivity) activity).isTesting()) {
                send(initiator, Lang.raw(PlayerLang.of(initiator), "auto.world_type_switcher.switch_environment.3")
                    + (player == initiator ? Lang.raw(PlayerLang.of(initiator), "auto.world_type_switcher.switch_environment.4") : Lang.raw(PlayerLang.of(initiator), "auto.world_type_switcher.switch_environment.5") + player.getName()));
                return;
            }
            editorsToReturn.add(player.getUniqueId());
        }

        levelsManager.lockLevel(levelId);

        for (Player player : world.getPlayers()) {
            send(player, Lang.raw(PlayerLang.of(initiator), "auto.world_type_switcher.switch_environment.6") + getDisplayName(target)
                + Lang.raw(PlayerLang.of(initiator), "auto.world_type_switcher.switch_environment.7"));
        }

        // Блоки и настройки записываются на диск ДО того, как мир уйдёт из памяти:
        // дальше работаем только с файлами, и терять нечего.
        levelsManager.saveLevelSettingsAndBlocks(level);

        Location lobby = Settings.getLobbySpawn();
        List<CompletableFuture<Boolean>> teleports = new ArrayList<>();
        for (Player player : new ArrayList<>(world.getPlayers())) {
            teleports.add(activityManager.switchActivity(player, null, lobby));
        }

        CompletableFuture<Void> playersLeft = teleports.isEmpty()
            ? CompletableFuture.completedFuture(null)
            : CompletableFuture.allOf(teleports.toArray(new CompletableFuture[0]));

        playersLeft.whenComplete((unused, error) -> sync(plugin, () ->
            levelsManager.unloadLevelAsync(levelId, true).thenAccept(unloaded -> sync(plugin, () -> {
                if (!unloaded) {
                    levelsManager.unlockLevel(levelId);
                    broadcast(plugin, editorsToReturn,
                        Lang.raw(PlayerLang.of(initiator), "auto.world_type_switcher.switch_environment.8"));
                    returnEditors(plugin, gameSettings, editorsToReturn);
                    return;
                }

                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    String failure;
                    try {
                        failure = migrate(plugin, levelId, current, target);
                    } catch (Throwable e) {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            Lang.raw(PlayerLang.of(initiator), "auto.world_type_switcher.switch_environment.9") + levelId, e);
                        failure = Lang.raw(PlayerLang.of(initiator), "auto.world_type_switcher.switch_environment.10");
                    }

                    String finalFailure = failure;
                    sync(plugin, () -> finish(plugin, levelId, gameSettings,
                        editorsToReturn, target, finalFailure));
                });
            }))));
    }

    private static void finish(@NonNull ParkourBeat plugin,
                               @NonNull UUID levelId,
                               @NonNull GameSettings gameSettings,
                               @NonNull List<UUID> editors,
                               @NonNull World.Environment target,
                               @Nullable String failure
    ) {
        LevelsManager levelsManager = plugin.get(LevelsManager.class);
        levelsManager.unlockLevel(levelId);

        if (failure != null) {
            broadcast(plugin, editors, "&cНе удалось сменить мир: " + failure);
            broadcast(plugin, editors, "&7Уровень остался в прежнем мире, ничего не потеряно");
        } else {
            broadcast(plugin, editors, "&aМир уровня изменён на " + getDisplayName(target));
        }

        returnEditors(plugin, gameSettings, editors);
    }

    private static void returnEditors(@NonNull ParkourBeat plugin,
                                      @NonNull GameSettings gameSettings,
                                      @NonNull List<UUID> editors
    ) {
        for (UUID editorId : editors) {
            Player player = plugin.getServer().getPlayer(editorId);
            if (player == null || !player.isOnline()) continue;
            LevelsListMenu.startEditing(plugin, player, gameSettings, false);
        }
    }

    /**
     * @return null при успехе, иначе короткая причина отказа для игрока
     */
    @Nullable
    private static String migrate(@NonNull ParkourBeat plugin,
                                  @NonNull UUID levelId,
                                  @NonNull World.Environment from,
                                  @NonNull World.Environment to
    ) throws IOException {
        File worldDir = getWorldDirectory(plugin, levelId);
        if (!worldDir.isDirectory()) return "папка мира не найдена";

        File settingsFile = new File(new File(worldDir, "parkourbeat"), "world_settings.yml");
        if (!settingsFile.isFile()) return "не найден world_settings.yml";

        File source = getDimensionDirectory(worldDir, from);
        File target = getDimensionDirectory(worldDir, to);

        List<File[]> moved = new ArrayList<>();
        try {
            for (String folder : DIMENSION_FOLDERS) {
                File sourceFolder = new File(source, folder);
                if (!sourceFolder.isDirectory()) continue;

                File targetFolder = new File(target, folder);
                // В целевой папке могут лежать остатки от прошлой смены мира:
                // они старее переносимых данных и должны уйти.
                deleteDirectory(targetFolder);
                Files.createDirectories(targetFolder.toPath().getParent());

                Files.move(sourceFolder.toPath(), targetFolder.toPath());
                moved.add(new File[]{sourceFolder, targetFolder});
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(settingsFile);
            config.set("environment", to.name());
            config.save(settingsFile);
        } catch (Throwable e) {
            // Откат в обратном порядке: на диске остаётся ровно то, что было до начала.
            for (int i = moved.size() - 1; i >= 0; i--) {
                File[] pair = moved.get(i);
                try {
                    Files.move(pair[1].toPath(), pair[0].toPath());
                } catch (Throwable ignored) {
                }
            }
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Не удалось перенести данные мира уровня " + levelId, e);
            return "не удалось перенести файлы мира";
        }

        // Свет пересчитывается только когда солнце появляется там, где его не было.
        if (hasSkyLight(to) && !hasSkyLight(from)) {
            ChunkLightFlagResetter.resetAll(new File(target, "region"), plugin.getLogger());
        }

        return null;
    }

    private static boolean hasSkyLight(@NonNull World.Environment environment) {
        return environment == World.Environment.NORMAL;
    }

    @NonNull
    private static File getDimensionDirectory(@NonNull File worldDir, @NonNull World.Environment environment) {
        switch (environment) {
            case NETHER:
                return new File(worldDir, "DIM-1");
            case THE_END:
                return new File(worldDir, "DIM1");
            default:
                return worldDir;
        }
    }

    @NonNull
    private static File getWorldDirectory(@NonNull ParkourBeat plugin, @NonNull UUID levelId) {
        LevelSettingDAO dao = plugin.get(LevelsManager.class).getLevelsSettings().getLevelSettingDAO();
        if (dao instanceof FileLevelSettingDAO) {
            return ((FileLevelSettingDAO) dao).getBukkitWorldDirectory(levelId).getAbsoluteFile();
        }
        return new File(new File(plugin.getDataFolder(), "levels"), levelId.toString()).getAbsoluteFile();
    }

    private static void deleteDirectory(@NonNull File directory) {
        if (!directory.exists()) return;
        File[] content = directory.listFiles();
        if (content != null) {
            for (File file : content) {
                deleteDirectory(file);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        directory.delete();
    }

    private static void sync(@NonNull ParkourBeat plugin, @NonNull Runnable action) {
        if (plugin.getServer().isPrimaryThread()) {
            action.run();
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, action);
    }

    private static void broadcast(@NonNull ParkourBeat plugin,
                                  @NonNull List<UUID> playerIds,
                                  @NonNull String legacyMessage
    ) {
        for (UUID playerId : playerIds) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;
            send(player, legacyMessage);
        }
    }

    private static void send(@NonNull Player player, @NonNull String legacyMessage) {
        Component component = PbText.of(legacyMessage);
        player.sendMessage(component);
    }
}
