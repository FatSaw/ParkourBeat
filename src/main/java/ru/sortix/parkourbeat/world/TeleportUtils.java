package ru.sortix.parkourbeat.world;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

@UtilityClass
public class TeleportUtils {
    /**
     * Это трюк для установки корректной позиции игроку после телепортации в другой мир.
     * При телепортации в другой мир используется алгоритм респауна игрока, который корректирует точку спауна,
     * в частности делает её более безопасной. Телепортация на высочайшую точку (координата Y) отключается переводом
     * опции world-settings.default.disable-teleportation-suffocation-check в значение true (paper.yml),
     * однако в этом случае к локации добавляется небольшой разброс по X и Z.
     * Данная корректировка происходит уже после PlayerTeleportEvent, поэтому для исправления данной проблемы это
     * событие не подходит. PlayerSpawnLocationEvent и PlayerRespawnEvent при этом не вызываются вовсе.
     * Как иначе решить данную проблему - пока неизвестно.
     * Данным костылём проблему удалось минимизировать,
     * однако даже при текущем подходе игрока всё равно смещает немного вбок.
     */
    private static final boolean USE_SECOND_TELEPORTS = true;
    private static final boolean DEBUG_TELEPORTATIONS = false;

    private final boolean ASYNC_TELEPORT_SUPPORTED;

    static {
        boolean asyncTeleportSupported;
        try {
            Entity.class.getDeclaredMethod("teleportAsync", Location.class);
            asyncTeleportSupported = true;
        } catch (NoSuchMethodException e) {
            asyncTeleportSupported = false;
        }
        ASYNC_TELEPORT_SUPPORTED = asyncTeleportSupported;
    }

    public boolean teleportSync(@NonNull Plugin plugin, @NonNull Player player, @NonNull Location location) {
        Location sourceLoc = player.getLocation();
        WorldsListener.CHUNKS_LOADED = 0;
        long startedAtMills = System.currentTimeMillis();
        player.setFallDistance(0f);
        boolean useSecondTeleport = player.getWorld() != location.getWorld() && USE_SECOND_TELEPORTS;
        boolean success = player.teleport(location);
        if (useSecondTeleport && success) {
            success = player.teleport(location);
        }
        long durationMills = System.currentTimeMillis() - startedAtMills;
        if (WorldsListener.CHUNKS_LOADED > 0 && ASYNC_TELEPORT_SUPPORTED) {
            plugin.getLogger()
                .log(
                    Level.WARNING,
                    Lang.raw(PlayerLang.of(player), "auto.teleport_utils.teleport_sync.1") + player.getName()
                        + Lang.raw(PlayerLang.of(player), "auto.teleport_utils.teleport_sync.2") + toString(sourceLoc)
                        + Lang.raw(PlayerLang.of(player), "auto.teleport_utils.teleport_sync.3") + toString(location)
                        + Lang.raw(PlayerLang.of(player), "auto.teleport_utils.teleport_sync.4") + durationMills + Lang.raw(PlayerLang.of(player), "auto.teleport_utils.teleport_sync.5")
                        + Lang.raw(PlayerLang.of(player), "auto.teleport_utils.teleport_sync.6") + WorldsListener.CHUNKS_LOADED + Lang.raw(PlayerLang.of(player), "auto.teleport_utils.teleport_sync.7"),
                    new RuntimeException(Lang.raw(PlayerLang.of(player), "auto.teleport_utils.teleport_sync.8")));
        }
        if (!success) {
            player.sendMessage(Lang.raw(PlayerLang.of(player), "auto.teleport_utils.teleport_sync.9"));
        }
        return success;
    }

    @NonNull
    public CompletableFuture<Boolean> teleportAsync(
        @NonNull Plugin plugin,
        @NonNull Player player,
        @NonNull Location location
    ) {
        if (DEBUG_TELEPORTATIONS) {
            plugin.getLogger().log(Level.INFO,
                "Teleporting " + player.getName()
                    + " from " + toString(player.getLocation()) + " to " + toString(location),
                new RuntimeException("DEBUG"));
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (ASYNC_TELEPORT_SUPPORTED) {
            player.setFallDistance(0f);
            boolean useSecondTeleport = player.getWorld() != location.getWorld() && USE_SECOND_TELEPORTS;
            player.teleportAsync(location).thenAccept(success -> {
                if (useSecondTeleport && success) {
                    success = player.teleport(location);
                }
                if (!success) {
                    player.sendMessage(Lang.raw(PlayerLang.of(player), "auto.teleport_utils.teleport_async.1"));
                }
                result.complete(success);
            });
        } else {
            plugin.getServer()
                .getScheduler()
                .runTaskLater(plugin, () -> result.complete(teleportSync(plugin, player, location)), 1L);
        }
        return result;
    }

    @NonNull
    private String toString(@NonNull Location loc) {
        String worldName = loc.getWorld() == null ? null : loc.getWorld().getName();
        if (worldName == null) {
            worldName = "null";
        } else {
            int splitter = worldName.lastIndexOf("/");
            if (splitter >= 0) {
                worldName = worldName.substring(splitter + "/".length());
            }
        }
        return "|"
            + worldName
            + " "
            + loc.getBlockX()
            + " "
            + loc.getBlockY()
            + " "
            + loc.getBlockZ()
            + "|";
    }
}
