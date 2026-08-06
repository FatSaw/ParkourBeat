package ru.sortix.parkourbeat.world;

import lombok.NonNull;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.WorldInitEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.dao.LevelSettingDAO;

import java.util.logging.Logger;

public class WorldsListener implements Listener {
    public static int CHUNKS_LOADED = 0;

    private final Logger logger;
    private final LevelsManager levelsManager;
    private final ParkourBeat plugin;

    public WorldsListener(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.levelsManager = plugin.get(LevelsManager.class);
    }

    private boolean isLevelWorld(@NonNull World world) {
        return this.levelsManager.getLevelsSettings().getLevelSettingDAO().isLevelWorld(world);
    }

    @EventHandler
    private void on(WorldInitEvent event) {
        LevelSettingDAO levelSettingDAO = this.levelsManager.getLevelsSettings().getLevelSettingDAO();
        if (!levelSettingDAO.isLevelWorld(event.getWorld())) return;
        this.levelsManager.prepareLevelWorld(event.getWorld(), false);
    }

    @EventHandler
    private void on(PlayerChangedWorldEvent event) {
        World fromWorld = event.getFrom();
        if (fromWorld.getPlayerCount() <= 0) {
            Level level = this.levelsManager.getLoadedLevel(fromWorld);
            if (level != null) {
                boolean saveChunks = false;
                this.levelsManager.unloadLevelAsync(level.getUniqueId(), saveChunks).thenAccept(success -> {
                    if (!success) {
                        this.logger.warning("Не удалось выгрузить мир уровня " + level.getUniqueId());
                    }
                });
            }
        }
    }

    @EventHandler
    private void on(ChunkLoadEvent event) {
        CHUNKS_LOADED++;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void on(PortalCreateEvent event) {
        if (this.isLevelWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void on(PlayerAdvancementDoneEvent event) {
        if (!this.isLevelWorld(event.getPlayer().getWorld())) return;
        org.bukkit.advancement.Advancement advancement = event.getAdvancement();
        org.bukkit.advancement.AdvancementProgress progress =
            event.getPlayer().getAdvancementProgress(advancement);
        for (String criteria : progress.getAwardedCriteria()) {
            progress.revokeCriteria(criteria);
        }
    }
}
