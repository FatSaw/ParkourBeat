package ru.sortix.parkourbeat.listeners;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.World;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.LevelsManager;

@RequiredArgsConstructor
public class PhysicsListener implements Listener {
    private final @NonNull ParkourBeat plugin;
    private final java.util.Map<java.util.UUID, Long> lastPushAt = new java.util.concurrent.ConcurrentHashMap<>();

    private boolean isLevelWorld(@NonNull World world) {
        return this.plugin.get(LevelsManager.class).getLoadedLevel(world) != null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void on(BlockPhysicsEvent event) {
        if (!this.isLevelWorld(event.getBlock().getWorld())) return;
        if (connectsToNeighbours(event.getBlock().getType())) return;
        event.setCancelled(true);
    }

    private static boolean connectsToNeighbours(@NonNull org.bukkit.Material type) {
        if (org.bukkit.Tag.STAIRS.isTagged(type)) return true;
        if (org.bukkit.Tag.FENCES.isTagged(type)) return true;
        if (org.bukkit.Tag.WALLS.isTagged(type)) return true;
        if (org.bukkit.Tag.FENCE_GATES.isTagged(type)) return true;
        if (type == org.bukkit.Material.GLASS_PANE) return true;
        if (type == org.bukkit.Material.IRON_BARS) return true;
        return type.name().endsWith("_STAINED_GLASS_PANE");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void on(BlockFromToEvent event) {
        if (this.isLevelWorld(event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void on(BlockFadeEvent event) {
        if (this.isLevelWorld(event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void on(BlockSpreadEvent event) {
        if (this.isLevelWorld(event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void on(LeavesDecayEvent event) {
        if (this.isLevelWorld(event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void on(EntityChangeBlockEvent event) {
        if (!this.isLevelWorld(event.getEntity().getWorld())) return;
        if (event.getEntity() instanceof FallingBlock) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.lastPushAt.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void on(org.bukkit.event.player.PlayerMoveEvent event) {
        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        org.bukkit.entity.Player player = event.getPlayer();
        org.bukkit.World world = to.getWorld();
        if (world == null) return;

        ru.sortix.parkourbeat.levels.Level level = this.plugin.get(LevelsManager.class).getLoadedLevel(world);
        if (level == null) return;

        double strength = level.getLevelSettings().getGameSettings().getBorderPushStrength();
        if (strength <= 0.0D) return;

        if (level.isLocationInside(to)) return;

        long now = System.currentTimeMillis();
        Long last = this.lastPushAt.get(player.getUniqueId());
        if (last != null && now - last < 400L) return;
        this.lastPushAt.put(player.getUniqueId(), now);

        ru.sortix.parkourbeat.levels.DirectionChecker checker =
            level.getLevelSettings().getDirectionChecker();
        boolean trackAlongX =
            checker.direction() == ru.sortix.parkourbeat.levels.DirectionChecker.Direction.POSITIVE_X
                || checker.direction() == ru.sortix.parkourbeat.levels.DirectionChecker.Direction.NEGATIVE_X;

        org.bukkit.util.Vector min = level.getCuboid().getMin();
        org.bukkit.util.Vector max = level.getCuboid().getMax();

        org.bukkit.util.Vector velocity = player.getVelocity();
        org.bukkit.util.Vector push = new org.bukkit.util.Vector(0, 0, 0);

        if (trackAlongX) {
            double centerZ = (min.getZ() + max.getZ()) / 2.0D;
            double dirZ = Math.signum(centerZ - to.getZ());
            if (dirZ == 0) dirZ = 1;
            push.setZ(dirZ * strength);
            push.setX(velocity.getX());
        } else {
            double centerX = (min.getX() + max.getX()) / 2.0D;
            double dirX = Math.signum(centerX - to.getX());
            if (dirX == 0) dirX = 1;
            push.setX(dirX * strength);
            push.setZ(velocity.getZ());
        }
        push.setY(0.25D);

        player.setVelocity(push);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_SLIME_SQUISH, 1f, 1.4f);
    }
}
