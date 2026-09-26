package ru.sortix.parkourbeat.world;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.inventory.type.LevelsListMenu;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Смена ширины уровня: один чанк или четыре.
 * <p>
 * Блоки при этом НЕ переносятся, и это не упрощение, а суть решения. Широкая площадка
 * расширяется в обе стороны поровну от исходной полосы (см. {@code Level.widen}), поэтому
 * старая постройка сама оказывается ровно посередине новой - с точностью до блока.
 * Перекладывание миллионов блоков дало бы тот же результат, но потребовало бы вдобавок
 * сдвинуть спавн, путь частиц, барьеры, порталы, зоны и элементы светового шоу: каждая
 * забытая координата - это молча съехавший уровень.
 * <p>
 * Переоткрыть уровень всё же нужно: границы области редактирования считаются один раз,
 * когда уровень загружается в память.
 */
public class LevelWidthSwitcher {
    public static final int NARROW_CHUNKS = 1;
    public static final int WIDE_CHUNKS = 4;

    @NonNull
    public static String getDisplayName(int chunkWidth) {
        return chunkWidth >= WIDE_CHUNKS ? "&b4 чанка" : "&f1 чанк";
    }

    /**
     * Запускает смену ширины. Вызывается только из основного потока.
     */
    public static void switchWidth(@NonNull ParkourBeat plugin,
                                   @NonNull Level level,
                                   int targetChunkWidth,
                                   @NonNull Player initiator
    ) {
        LevelsManager levelsManager = plugin.get(LevelsManager.class);
        ActivityManager activityManager = plugin.get(ActivityManager.class);

        UUID levelId = level.getUniqueId();
        GameSettings gameSettings = level.getLevelSettings().getGameSettings();

        // Дуэльная карта живёт ровно в четырёх чанках: узкой ей быть нельзя, а другой
        // ширины у неё и не бывает. Менять тут нечего.
        if (gameSettings.isDuelLevel()) {
            send(initiator, "&cШирина дуэльной карты не меняется: ей нужны четыре чанка.");
            return;
        }

        int current = gameSettings.getChunkWidth() >= WIDE_CHUNKS ? WIDE_CHUNKS : NARROW_CHUNKS;
        int target = targetChunkWidth >= WIDE_CHUNKS ? WIDE_CHUNKS : NARROW_CHUNKS;

        if (current == target) {
            send(initiator, Lang.raw(PlayerLang.of(initiator), "auto.level_width_switcher.switch_width.1"));
            return;
        }

        if (levelsManager.isLevelLocked(levelId)) {
            send(initiator, Lang.raw(PlayerLang.of(initiator), "auto.level_width_switcher.switch_width.2"));
            return;
        }

        World world = level.getWorld();

        // Тестовый забег держит вторую активность поверх редактора - просим завершить,
        // как и при смене мира: разбирать её на ходу лишний источник ошибок.
        List<UUID> editorsToReturn = new ArrayList<>();
        for (Player player : new ArrayList<>(world.getPlayers())) {
            UserActivity activity = activityManager.getActivity(player);
            if (!(activity instanceof EditActivity)) continue;
            if (((EditActivity) activity).isTesting()) {
                send(initiator, Lang.raw(PlayerLang.of(initiator), "auto.level_width_switcher.switch_width.3")
                    + (player == initiator ? Lang.raw(PlayerLang.of(initiator), "auto.level_width_switcher.switch_width.4") : Lang.raw(PlayerLang.of(initiator), "auto.level_width_switcher.switch_width.5") + player.getName()));
                return;
            }
            editorsToReturn.add(player.getUniqueId());
        }

        // Сужение обратно до одного чанка отрезало бы всё, что построено по краям.
        if (target == NARROW_CHUNKS && !isBuildOnlyInCenter(level)) {
            send(initiator, Lang.raw(PlayerLang.of(initiator), "auto.level_width_switcher.switch_width.6"));
            send(initiator, Lang.raw(PlayerLang.of(initiator), "auto.level_width_switcher.switch_width.7"));
            return;
        }

        levelsManager.lockLevel(levelId);

        for (Player player : world.getPlayers()) {
            send(player, Lang.raw(PlayerLang.of(initiator), "auto.level_width_switcher.switch_width.8") + getDisplayName(target)
                + Lang.raw(PlayerLang.of(initiator), "auto.level_width_switcher.switch_width.9"));
        }

        gameSettings.setChunkWidth(target);

        // Настройки и блоки пишутся на диск ДО выгрузки мира: дальше мир уходит
        // из памяти, и всё несохранённое было бы потеряно.
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
                levelsManager.unlockLevel(levelId);

                if (!unloaded) {
                    broadcast(plugin, editorsToReturn,
                        Lang.raw(PlayerLang.of(initiator), "auto.level_width_switcher.switch_width.10"));
                } else {
                    broadcast(plugin, editorsToReturn,
                        Lang.raw(PlayerLang.of(initiator), "auto.level_width_switcher.switch_width.11") + getDisplayName(target));
                    if (target == WIDE_CHUNKS) {
                        broadcast(plugin, editorsToReturn,
                            Lang.raw(PlayerLang.of(initiator), "auto.level_width_switcher.switch_width.12"));
                    }
                }

                returnEditors(plugin, gameSettings, editorsToReturn);
            }))));
    }

    /**
     * Проверка перед сужением: не осталось ли чего-нибудь за пределами центрального чанка.
     * <p>
     * Блоки по всей длине уровня перебирать нельзя - это сотни тысяч проверок, поэтому
     * смотрим на то, что переносит смысл: точки пути и спавн. Постройку по краям игрок
     * увидит сам, а вот молча потерянный путь заметить трудно.
     */
    private static boolean isBuildOnlyInCenter(@NonNull Level level) {
        Cuboid narrow = Settings.getLevelFixedEditableArea()
            .get(level.getLevelSettings().getWorldSettings().getDirection());
        if (narrow == null) return true;

        double minZ = narrow.getMin().getZ();
        double maxZ = narrow.getMax().getZ();

        for (ru.sortix.parkourbeat.levels.Waypoint waypoint
            : level.getLevelSettings().getWorldSettings().getWaypoints()) {
            double z = waypoint.getLocation().getZ();
            if (z < minZ || z > maxZ) return false;
        }

        double spawnZ = level.getLevelSettings().getWorldSettings().getSpawn().getZ();
        return spawnZ >= minZ && spawnZ <= maxZ;
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

    private static void broadcast(@NonNull ParkourBeat plugin,
                                  @NonNull List<UUID> players,
                                  @NonNull String message) {
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;
            send(player, message);
        }
    }

    private static void send(@NonNull Player player, @NonNull String message) {
        player.sendMessage(PbText.of(message));
    }

    private static void sync(@NonNull ParkourBeat plugin, @NonNull Runnable runnable) {
        if (plugin.getServer().isPrimaryThread()) {
            runnable.run();
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }
}
