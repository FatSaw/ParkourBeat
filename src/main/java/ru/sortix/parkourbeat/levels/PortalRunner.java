package ru.sortix.parkourbeat.levels;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.levels.settings.Portal;

import java.util.List;

public class PortalRunner {
    private static final int RING_POINTS = 28;
    private static final int MIN_LOOKAHEAD_PING_MILLIS = 40;
    private static final double MAX_LOOKAHEAD_SECONDS = 0.20D;

    private static final long TELEPORT_COOLDOWN_MILLIS = 1200L;

    private final @NonNull ru.sortix.parkourbeat.ParkourBeat plugin;
    private final @NonNull Level level;
    private final @NonNull Player player;
    private long lastTeleportAt = 0L;

    public PortalRunner(@NonNull ru.sortix.parkourbeat.ParkourBeat plugin,
                        @NonNull Level level, @NonNull Player player) {
        this.plugin = plugin;
        this.level = level;
        this.player = player;
    }

    public void tick(boolean allowTeleport) {
        if (!this.player.isOnline()) return;
        if (this.player.getWorld() != this.level.getWorld()) return;

        List<Portal> portals = this.level.getLightShow().getPortals();
        if (portals.isEmpty()) return;

        Location playerLocation = this.player.getLocation();

        for (Portal portal : portals) {
            if (!portal.isEnabled()) continue;

            this.drawSide(portal, portal.getEntry(), false);
            this.drawSide(portal, portal.getExit(), true);

            if (!allowTeleport) continue;
            if (!this.touchesEntry(portal, playerLocation)) continue;
            this.teleport(portal, playerLocation);
        }
    }

    /**
     * Проверяем три точки по высоте игрока: на бегу центр может проскочить рамку
     * между тиками, а ноги или голова её всё равно заденут.
     */
    private boolean touchesEntry(@NonNull Portal portal, @NonNull Location location) {
        if (this.touchesAt(portal, location.getX(), location.getY(), location.getZ())) return true;

        double lead = this.lookaheadSeconds();
        if (lead <= 0.0D) return false;

        org.bukkit.util.Vector velocity = this.player.getVelocity();
        double dx = velocity.getX() * 20.0D * lead;
        double dz = velocity.getZ() * 20.0D * lead;
        if (dx * dx + dz * dz < 0.0004D) return false;

        return this.touchesAt(portal,
            location.getX() + dx, location.getY(), location.getZ() + dz);
    }

    private boolean touchesAt(@NonNull Portal portal, double x, double y, double z) {
        return portal.getEntry().contains(x, y + 0.1D, z)
            || portal.getEntry().contains(x, y + 0.9D, z)
            || portal.getEntry().contains(x, y + 1.7D, z);
    }

    /**
     * На больших задержках игрок успевает врезаться в стену с рамкой раньше, чем до него
     * доедет телепорт. Поэтому вход проверяется ещё и в точке, где игрок окажется через
     * половину его пинга. Упреждение ограничено сверху, чтобы на нормальном соединении
     * ничего не менялось, а на плохом портал не срабатывал за метр до рамки.
     */
    private double lookaheadSeconds() {
        int ping;
        try {
            ping = this.plugin.get(ru.sortix.parkourbeat.player.PingManager.class).getPing(this.player);
        } catch (Exception e) {
            return 0.0D;
        }

        if (ping <= MIN_LOOKAHEAD_PING_MILLIS) return 0.0D;

        double seconds = (ping - MIN_LOOKAHEAD_PING_MILLIS) / 1200.0D;
        return Math.min(seconds, MAX_LOOKAHEAD_SECONDS);
    }

    private void teleport(@NonNull Portal portal, @NonNull Location from) {
        long now = System.currentTimeMillis();
        if (now - this.lastTeleportAt < TELEPORT_COOLDOWN_MILLIS) return;
        this.lastTeleportAt = now;

        Location target = portal.getExit().toLocation(this.level.getWorld());
        target = this.applySpawnOffset(target, portal);

        if (!portal.getExit().isLookSet()) {
            target.setYaw(from.getYaw());
            target.setPitch(from.getPitch());
        }

        this.player.setFallDistance(0.0f);
        this.player.teleport(target);
        this.player.setFallDistance(0.0f);

        org.bukkit.util.Vector velocity = this.player.getVelocity();
        if (velocity.getY() < 0.0D) {
            velocity.setY(0.0D);
            this.player.setVelocity(velocity);
        }

        this.notifyTeleport(target);
        this.player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.25f, 2.0f);
        this.player.spawnParticle(Particle.REDSTONE, target.clone().add(0, 1, 0), 25,
            0.4, 0.6, 0.4, 0, new Particle.DustOptions(portal.getExit().getColor(), 1.4f));
    }

    /**
     * Выход стоит на плоскости рамки, поэтому игрока надо чуть сдвинуть
     * от неё, иначе он окажется внутри блока стены или пола.
     */
    @NonNull
    private Location applySpawnOffset(@NonNull Location target, @NonNull Portal portal) {
        Location result = target.clone();
        switch (portal.getExit().getFacing()) {
            case WALL_X -> result.add(0, -0.9D, 0);
            case WALL_Z -> result.add(0, -0.9D, 0);
            case FLOOR -> result.add(0, 0.2D, 0);
        }
        return result;
    }

    private void notifyTeleport(@NonNull Location exit) {
        try {
            ru.sortix.parkourbeat.activity.UserActivity activity = this.plugin
                .get(ru.sortix.parkourbeat.activity.ActivityManager.class).getActivity(this.player);

            ru.sortix.parkourbeat.activity.type.PlayActivity play = null;
            if (activity instanceof ru.sortix.parkourbeat.activity.type.PlayActivity found) {
                play = found;
            } else if (activity instanceof ru.sortix.parkourbeat.activity.type.EditActivity editor) {
                play = editor.getTestingActivity();
            }
            if (play != null) play.onPortalTeleport(exit);
        } catch (Exception ignored) {
        }
    }

    private void drawSide(@NonNull Portal portal, @NonNull Portal.Side side, boolean exit) {
        Location center = side.toLocation(this.level.getWorld());
        double view = portal.getViewDistance();
        if (center.distanceSquared(this.player.getLocation()) > view * view) return;

        Particle.DustOptions dust = new Particle.DustOptions(side.getColor(), exit ? 1.1f : 1.4f);
        double radius = side.getSize() / 2.0D;

        for (int i = 0; i < RING_POINTS; i++) {
            double angle = (Math.PI * 2 * i) / RING_POINTS;
            double a = Math.cos(angle) * radius;
            double b = Math.sin(angle) * radius;

            Location point = switch (side.getFacing()) {
                case WALL_X -> center.clone().add(0, b, a);
                case WALL_Z -> center.clone().add(a, b, 0);
                case FLOOR -> center.clone().add(a, 0, b);
            };
            this.player.spawnParticle(Particle.REDSTONE, point, 1, 0, 0, 0, 0, dust);
        }
    }
}
