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
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.LevelsManager;

/**
 * Freezes block physics inside ParkourBeat level worlds so builders' scenery stays put:
 * water and lava do not flow, sand and gravel do not fall, leaves do not decay and redstone
 * or other physics-driven updates do not reshape the map while it is being played.
 */
@RequiredArgsConstructor
public class PhysicsListener implements Listener {
    private final @NonNull ParkourBeat plugin;

    private boolean isLevelWorld(@NonNull World world) {
        return this.plugin.get(LevelsManager.class).getLoadedLevel(world) != null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void on(BlockPhysicsEvent event) {
        if (!this.isLevelWorld(event.getBlock().getWorld())) return;
        // BlockPhysicsEvent covers two very different things: destructive physics (water/lava
        // flowing, sand/gravel falling, unsupported blocks popping off) AND harmless shape
        // recalculation, where a block re-reads its neighbours to connect — glass/iron pane
        // "north/south/east/west", fence and wall connections, stair inner/outer corner shape.
        // Cancelling everything froze the destructive stuff but also broke those connections, so
        // panes and stairs no longer joined up. Let the connecting blocks recompute their shape;
        // they never move or disappear from a physics tick, so allowing it is safe.
        if (connectsToNeighbours(event.getBlock().getType())) return;
        event.setCancelled(true);
    }

    private static boolean connectsToNeighbours(@NonNull org.bukkit.Material type) {
        if (org.bukkit.Tag.STAIRS.isTagged(type)) return true;
        if (org.bukkit.Tag.FENCES.isTagged(type)) return true;
        if (org.bukkit.Tag.WALLS.isTagged(type)) return true;
        if (org.bukkit.Tag.FENCE_GATES.isTagged(type)) return true;
        // Glass/iron panes have no dedicated tag on 1.16, so match them by name/type.
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
        // Falling sand/gravel turning into a placed block, or landing, is blocked so the
        // column keeps floating exactly as the builder placed it.
        if (event.getEntity() instanceof FallingBlock) event.setCancelled(true);
    }

    private final java.util.Map<java.util.UUID, Long> lastPushAt = new java.util.concurrent.ConcurrentHashMap<>();

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

        // Rate-limit so it fires once per crossing, not every tick spent outside.
        long now = System.currentTimeMillis();
        Long last = this.lastPushAt.get(player.getUniqueId());
        if (last != null && now - last < 400L) return;
        this.lastPushAt.put(player.getUniqueId(), now);

        // The track runs along one axis (forward); the other axis is left-right. We only bounce
        // on the left-right axis, back toward the track centre, and we keep the player's forward
        // speed. That gives the sideways "kick off the wall" without ever shoving them backward,
        // so the "no running back" check never triggers.
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
            // Forward is X, cross is Z. Bounce along Z toward the centre Z.
            double centerZ = (min.getZ() + max.getZ()) / 2.0D;
            double dirZ = Math.signum(centerZ - to.getZ());
            if (dirZ == 0) dirZ = 1;
            push.setZ(dirZ * strength);
            // Keep forward (X) momentum so the run continues down the track.
            push.setX(velocity.getX());
        } else {
            // Forward is Z, cross is X. Bounce along X toward the centre X.
            double centerX = (min.getX() + max.getX()) / 2.0D;
            double dirX = Math.signum(centerX - to.getX());
            if (dirX == 0) dirX = 1;
            push.setX(dirX * strength);
            push.setZ(velocity.getZ());
        }
        // A small upward pop so the player clears the wall edge, plus a slight forward nudge feel.
        push.setY(0.25D);

        player.setVelocity(push);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_SLIME_SQUISH, 1f, 1.4f);
    }
}
