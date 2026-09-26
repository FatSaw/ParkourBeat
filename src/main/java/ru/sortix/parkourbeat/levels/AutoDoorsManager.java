package ru.sortix.parkourbeat.levels;

import lombok.NonNull;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.settings.AutoDoor;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import java.util.List;
import java.util.logging.Level;

/**
 * Двери пересчитываются раз на уровень, а не раз на игрока: состояние блока общее,
 * и два человека у одной двери иначе перещёлкивали бы её друг у друга.
 */
public class AutoDoorsManager implements PluginManager {
    /**
     * Раз в 2 тика: сам период опроса добавляет задержку к открытию, поэтому он держится
     * низким, но не равным одному тику, чтобы не считать расстояния каждый тик
     * для каждой двери каждого уровня.
     */
    private static final long PERIOD_TICKS = 2L;

    private final @NonNull ParkourBeat plugin;
    private final @NonNull BukkitTask task;

    public AutoDoorsManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.task = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    private void tick() {
        LevelsManager levelsManager;
        try {
            levelsManager = this.plugin.get(LevelsManager.class);
        } catch (Exception e) {
            return;
        }

        for (ru.sortix.parkourbeat.levels.Level level : levelsManager.getLoadedLevels()) {
            List<AutoDoor> doors = level.getLightShow().getAutoDoors();
            if (doors.isEmpty()) continue;
            if (level.getWorld().getPlayers().isEmpty()) continue;

            for (AutoDoor door : doors) {
                try {
                    AutoDoorEngine.tick(this.plugin, level, door);
                } catch (Exception e) {
                    this.plugin.getLogger().log(Level.WARNING,
                        "Unable to tick auto door " + door.format()
                            + " of level " + level.getUniqueId(), e);
                }
            }
        }
    }

    @Override
    public void disable() {
        if (!this.task.isCancelled()) this.task.cancel();
    }
}
