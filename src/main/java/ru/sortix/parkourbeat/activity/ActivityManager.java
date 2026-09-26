package ru.sortix.parkourbeat.activity;

import com.comphenix.protocol.ProtocolLibrary;
import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.SpectateActivity;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.player.PlayersCollisionManager;
import ru.sortix.parkourbeat.world.TeleportUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ActivityManager implements PluginManager {
    private final ParkourBeat plugin;
    private final ActivityListener listener;
    @Getter
    private final ActivityPacketsAdapterImpl packetsAdapter;
    private final Map<Player, UserActivity> activities = new ConcurrentHashMap<>();
    private final BukkitTask movementController;

    public ActivityManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.listener = new ActivityListener(this);
        this.packetsAdapter = new ActivityPacketsAdapterImpl(this.plugin);

        this.plugin.getServer().getPluginManager().registerEvents(this.listener, this.plugin);
        ProtocolLibrary.getProtocolManager().addPacketListener(this.packetsAdapter);
        this.movementController = this.plugin.getServer().getScheduler()
            .runTaskTimer(
                this.plugin,
                () -> {
                    for (UserActivity activity : this.activities.values()) {
                        activity.onTick();
                    }
                },
                1L,
                1L);
    }

    @Override
    public void disable() {
        this.movementController.cancel();
        ProtocolLibrary.getProtocolManager().removePacketListener(this.packetsAdapter);
        HandlerList.unregisterAll(this.listener);
        for (Player player : new HashSet<>(this.activities.keySet())) {
            this.setActivity(player, null);
        }
    }

    @Nullable
    public UserActivity getActivity(@NonNull Player player) {
        return this.activities.get(player);
    }

    @NonNull
    public Collection<UserActivity> getAllActivities() {
        return this.activities.values();
    }

    /**
     * startActivity() и endActivity() умеют телепортировать игрока, а телепортация вызывает
     * PlayerTeleportEvent, который через updateTargetLocationActivity снова заходит сюда.
     * Без этого замка получалась бесконечная рекурсия и StackOverflowError
     * (например, /tp на игрока, стоящего на уровне).
     */
    private final Set<Player> switchingActivity = ConcurrentHashMap.newKeySet();

    private void setActivity(@NonNull Player player, @Nullable UserActivity newActivity) {
        UserActivity previousActivity = this.activities.get(player);

        if (previousActivity == newActivity) return;

        if (!this.switchingActivity.add(player)) return;
        try {
            if (previousActivity != null) {
                this.activities.remove(player);
                try {
                    previousActivity.endActivity();
                } catch (Throwable e) {
                    // Активность всё равно считается завершённой. Раньше она возвращалась в карту,
                    // и игрок оставался с активностью чужого мира: каждое его движение снова вело
                    // сюда через doActivityAction, снова падало на том же месте, и консоль заливало
                    // одной и той же ошибкой, а игрок не мог ни двигаться, ни выйти из этого состояния.
                    this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Unable to end activity " + previousActivity.getClass().getSimpleName()
                            + " of player " + player.getName(), e);
                }
            }

            if (newActivity != null) {
                // Активность кладётся в карту ДО запуска: иначе вложенный вызов не увидит её
                // и создаст ещё одну активность тому же игроку.
                this.activities.put(player, newActivity);
                try {
                    newActivity.startActivity();
                } catch (Throwable e) {
                    this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Unable to start activity " + newActivity.getClass().getSimpleName()
                            + " of player " + player.getName(), e);
                    this.activities.remove(player);
                    this.updatePlayerCollisions(player);
                    return;
                }
            }
        } finally {
            this.switchingActivity.remove(player);
        }

        this.updatePlayerCollisions(player);
    }

    /**
     * Having an activity means being on a level, so player pushing is turned off there
     * and stays enabled in the lobby.
     */
    private void updatePlayerCollisions(@NonNull Player player) {
        try {
            this.plugin.get(PlayersCollisionManager.class)
                .setCollisionsDisabled(player, this.activities.containsKey(player));
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Unable to update collisions of player " + player.getName(), e);
        }
    }

    @NonNull
    public CompletableFuture<Boolean> switchActivity(@NonNull Player player,
                                                     @Nullable UserActivity newActivity,
                                                     @Nullable Location targetLocation
    ) {
        UserActivity previousActivity = this.getActivity(player);

        this.setActivity(player, newActivity);

        if (targetLocation == null) {
            return CompletableFuture.completedFuture(true);
        }

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        TeleportUtils.teleportAsync(this.plugin, player, targetLocation).thenAccept(success -> {
            if (!success) {
                this.setActivity(player, previousActivity);
            }
            result.complete(success);
        });

        return result;
    }

    protected void updateTargetLocationActivity(@NonNull Player player, @NonNull World targetWorld) {
        Level targetLevel = this.plugin.get(LevelsManager.class).getLoadedLevel(targetWorld);

        if (targetLevel == null) {
            this.setActivity(player, null);
            // Страховка: если завершение активности по какой-то причине не довело дело до конца,
            // игрок уходил в лобби с небом уровня (полностью белое небо и чужое время суток).
            try {
                ru.sortix.parkourbeat.levels.settings.SkyType.reset(player);
            } catch (Throwable e) {
                this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Unable to reset sky of player " + player.getName(), e);
            }
        } else {
            UserActivity previousActivity = this.getActivity(player);
            if (previousActivity == null || !previousActivity.isValidWorld(targetWorld)) {
                this.setActivity(player, new SpectateActivity(this.plugin, player, targetLevel));
            }
        }
    }

    @NonNull
    public Collection<Player> getPlayersOnTheLevel(@NonNull Level level) {
        Collection<Player> result = new ArrayList<>();
        for (Player player : level.getWorld().getPlayers()) {
            UserActivity activity = this.getActivity(player);
            if (activity != null && activity.getLevel() == level) result.add(player);
        }
        return result;
    }
}
