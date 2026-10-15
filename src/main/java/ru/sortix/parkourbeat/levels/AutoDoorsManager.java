package ru.sortix.parkourbeat.levels;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import lombok.NonNull;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.settings.AutoDoor;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Автодвери. Открываются ПЕРСОНАЛЬНО: каждый игрок видит дверь открытой только тогда,
 * когда к ней подошёл он сам (подробности - в {@link AutoDoorEngine}).
 * <p>
 * Тик разбит на две половины:
 * <ol>
 *     <li>В начале тика решается, кто где стоит, и меняется настоящий блок в мире
 *     (открыт, если рядом хоть кто-то, - ради физики).</li>
 *     <li>В КОНЦЕ того же тика каждому игроку досылается его личное состояние двери.</li>
 * </ol>
 * Почему не сразу: смена настоящего блока уходит всем клиентам во время тика мира, то
 * есть ПОСЛЕ задач планировщика. Пошли мы личное состояние сразу - сервер тут же
 * перебил бы его общим, и дверь открывалась бы у всех. В конце тика наш пакет уходит
 * последним и остаётся на экране.
 */
public class AutoDoorsManager implements PluginManager, Listener {
    /**
     * Раз в 2 тика: сам период опроса добавляет задержку к открытию, поэтому он держится
     * низким, но не равным одному тику, чтобы не считать расстояния каждый тик
     * для каждой двери каждого уровня.
     */
    private static final long PERIOD_TICKS = 2L;

    /**
     * Раз во столько запусков игрокам, у которых личное состояние расходится с миром,
     * оно досылается заново. Клиент мог перезагрузить чанк (телепорт на чекпоинт,
     * дальность прорисовки) и получить вместе с ним настоящую дверь.
     */
    private static final int RESEND_EVERY_RUNS = 10;

    private final @NonNull ParkourBeat plugin;
    private final @NonNull BukkitTask task;

    /** Что сейчас видит игрок: ключ двери -> открыта ли она у него на экране. */
    private final Map<UUID, Map<String, Boolean>> visible = new HashMap<>();
    /** Досылки личного состояния, отложенные до конца тика. */
    private final List<Runnable> pendingViewerSync = new ArrayList<>();
    private int runs = 0;

    public AutoDoorsManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.task = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void tick() {
        LevelsManager levelsManager;
        try {
            levelsManager = this.plugin.get(LevelsManager.class);
        } catch (Exception e) {
            return;
        }

        this.runs++;
        boolean resend = this.runs % RESEND_EVERY_RUNS == 0;
        Set<UUID> seenPlayers = new HashSet<>();

        for (ru.sortix.parkourbeat.levels.Level level : levelsManager.getLoadedLevels()) {
            List<AutoDoor> doors = level.getLightShow().getAutoDoors();
            if (doors.isEmpty()) continue;
            World world = level.getWorld();
            List<Player> players = world.getPlayers();
            if (players.isEmpty()) continue;
            for (Player player : players) seenPlayers.add(player.getUniqueId());

            for (AutoDoor door : doors) {
                try {
                    this.tickDoor(world, door, players, resend);
                } catch (Exception e) {
                    this.plugin.getLogger().log(Level.WARNING,
                        "Unable to tick auto door " + door.format()
                            + " of level " + level.getUniqueId(), e);
                }
            }
        }

        // Ушёл с уровней - забываем, что он видел. Вернётся - получит чанки заново,
        // то есть настоящие двери, и учёт начнётся с чистого листа.
        this.visible.keySet().retainAll(seenPlayers);
    }

    private void tickDoor(@NonNull World world, @NonNull AutoDoor door,
                          @NonNull List<Player> players, boolean resend) {
        if (!door.isEnabled()) return;
        Block block = AutoDoorEngine.findOpenableBlock(world, door);
        if (block == null) return;

        // Кто из игроков должен видеть дверь открытой.
        Map<Player, Boolean> wantOpen = new LinkedHashMap<>();
        boolean anyoneNear = false;
        for (Player player : players) {
            boolean near = AutoDoorEngine.isNear(this.plugin, player, door);
            anyoneNear |= near;
            wantOpen.put(player, door.isInverted() != near);
        }

        // Настоящий блок - ради физики сервера. Звук здесь не играем: его услышали бы
        // все, а дверь открылась не для всех.
        boolean serverOpen = door.isInverted() != anyoneNear;
        boolean changed = AutoDoorEngine.setOpen(block, serverOpen, false);

        // Наблюдатели в радиус не входят, но видеть должны настоящее состояние.
        for (Map.Entry<Player, Boolean> entry : wantOpen.entrySet()) {
            if (entry.getKey().getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                entry.setValue(serverOpen);
            }
        }

        String key = doorKey(world, door);
        this.pendingViewerSync.add(() ->
            this.syncViewers(world, door, key, serverOpen, changed, resend, wantOpen));
    }

    @EventHandler
    private void onTickEnd(ServerTickEndEvent event) {
        if (this.pendingViewerSync.isEmpty()) return;
        List<Runnable> tasks = new ArrayList<>(this.pendingViewerSync);
        this.pendingViewerSync.clear();
        for (Runnable task : tasks) {
            try {
                task.run();
            } catch (Exception e) {
                this.plugin.getLogger().log(Level.WARNING, "Unable to sync auto door for viewers", e);
            }
        }
    }

    private void syncViewers(@NonNull World world, @NonNull AutoDoor door, @NonNull String key,
                             boolean serverOpen, boolean changed, boolean resend,
                             @NonNull Map<Player, Boolean> wantOpen) {
        Block block = AutoDoorEngine.findOpenableBlock(world, door);
        if (block == null) return;
        List<Block> parts = AutoDoorEngine.collectDoorBlocks(block);

        for (Map.Entry<Player, Boolean> entry : wantOpen.entrySet()) {
            Player player = entry.getKey();
            boolean want = entry.getValue();
            if (!player.isOnline() || player.getWorld() != world) continue;

            Map<String, Boolean> seen = this.visible.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
            Boolean recorded = seen.get(key);

            // Что игрок видел до этого тика. Без записи - то, что лежит в мире: чанк
            // пришёл ему вместе с настоящей дверью.
            boolean before = recorded != null ? recorded : (changed ? !serverOpen : serverOpen);
            // Что у него на экране прямо сейчас: если мир поменялся, сервер только что
            // разослал всем настоящую дверь поверх личной.
            boolean onScreen = changed ? serverOpen : before;

            if (onScreen != want || (resend && want != serverOpen)) {
                AutoDoorEngine.sendVisibleState(player, parts, want);
            }
            if (recorded != null && before != want && door.isPlaySound()) {
                AutoDoorEngine.playSoundFor(player, block, want);
            }
            seen.put(key, want);
        }
    }

    @NonNull
    private static String doorKey(@NonNull World world, @NonNull AutoDoor door) {
        return world.getUID() + ":" + door.getBlockX() + ":" + door.getBlockY() + ":" + door.getBlockZ();
    }

    @Override
    public void disable() {
        if (!this.task.isCancelled()) this.task.cancel();
        HandlerList.unregisterAll(this);
        this.pendingViewerSync.clear();
        this.visible.clear();
    }
}
