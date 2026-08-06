package ru.sortix.parkourbeat.world;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import lombok.NonNull;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Level worlds must never have real weather of their own: rain there piles up snow layers in
 * cold biomes and fights the per-player weather the lightshow sends. Advancements are cut off
 * as well, both the chat line through the game rule and the toast through the packet.
 */
public class LevelWorldsManager implements PluginManager {
    private static final int SWEEP_PERIOD_TICKS = 100;

    private final @NonNull ParkourBeat plugin;
    private final Set<UUID> preparedWorlds = new HashSet<>();
    private final BukkitTask sweepTask;
    private final PacketAdapter advancementsAdapter;

    public LevelWorldsManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;

        this.advancementsAdapter = new PacketAdapter(plugin, PacketType.Play.Server.ADVANCEMENTS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                if (player == null) return;
                // PacketAdapter has its own field named plugin, which shadows the parameter here
                ParkourBeat parkourBeat = LevelWorldsManager.this.plugin;
                if (parkourBeat.get(ActivityManager.class).getActivity(player) == null) return;
                event.setCancelled(true);
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(this.advancementsAdapter);

        this.sweepTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin, this::sweep, SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS);
    }

    private void sweep() {
        LevelsManager levelsManager = this.plugin.get(LevelsManager.class);

        for (World world : this.plugin.getServer().getWorlds()) {
            if (levelsManager.getLoadedLevel(world) == null) continue;
            if (!this.preparedWorlds.add(world.getUID())) {
                this.keepWeatherOff(world);
                continue;
            }
            this.prepare(world);
        }

        this.preparedWorlds.removeIf(worldId -> this.plugin.getServer().getWorld(worldId) == null);
    }

    private void prepare(@NonNull World world) {
        try {
            // Hostile mobs are wiped on peaceful, and the glowing barriers ride on shulkers
            if (world.getDifficulty() == Difficulty.PEACEFUL) world.setDifficulty(Difficulty.EASY);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.MOB_GRIEFING, false);
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Unable to set game rules of world " + world.getName(), e);
        }
        this.keepWeatherOff(world);
    }

    private void keepWeatherOff(@NonNull World world) {
        if (world.hasStorm()) world.setStorm(false);
        if (world.isThundering()) world.setThundering(false);
        if (world.getWeatherDuration() < Integer.MAX_VALUE / 2) {
            world.setWeatherDuration(Integer.MAX_VALUE);
        }
    }

    @Override
    public void disable() {
        this.sweepTask.cancel();
        ProtocolLibrary.getProtocolManager().removePacketListener(this.advancementsAdapter);
        this.preparedWorlds.clear();
    }
}
