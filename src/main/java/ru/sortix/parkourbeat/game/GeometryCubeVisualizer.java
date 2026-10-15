package ru.sortix.parkourbeat.game;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.sortix.parkourbeat.ParkourBeat;

import java.lang.reflect.Method;

public class GeometryCubeVisualizer {

    private final @NonNull ParkourBeat plugin;
    private final @NonNull Player player;
    private ArmorStand seat;
    private FallingBlock cube;

    private boolean offsetCalculated = false;
    private double mountOffset = 0.0d;

    public GeometryCubeVisualizer(@NonNull ParkourBeat plugin, @NonNull Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void spawn() {
        if (this.seat != null || this.cube != null) return;

        Location loc = this.player.getLocation();

        // Спавним невидимый стенд-подиум (такой же как для ковров)
        this.seat = loc.getWorld().spawn(loc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setGravity(false);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
        });

        // Спавним сам куб (repeating_command_block)
        this.cube = loc.getWorld().spawnFallingBlock(loc, Material.REPEATING_COMMAND_BLOCK.createBlockData());
        this.cube.setGravity(false);
        this.cube.setDropItem(false);
        this.cube.setHurtEntities(false);
        this.cube.setInvulnerable(true);
        this.cube.setSilent(true);
        this.cube.setTicksLived(1);

        this.seat.addPassenger(this.cube);

        // Полностью прячем игрока (невидимость без частиц и иконок)
        this.player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 1_000_000, 0, false, false, false));
    }

    public void tick() {
        if (this.seat == null || this.cube == null || !this.seat.isValid() || !this.cube.isValid() || !this.seat.equals(this.cube.getVehicle())) {
            this.despawn();
            this.spawn();
            return;
        }

        this.cube.setTicksLived(1);

        // Динамически высчитываем смещение пассажира на первом тике,
        // чтобы кубик идеально стоял на ногах игрока (на земле).
        if (!this.offsetCalculated) {
            double sy = this.seat.getLocation().getY();
            double cy = this.cube.getLocation().getY();
            this.mountOffset = cy - sy;
            if (!Double.isFinite(this.mountOffset) || Math.abs(this.mountOffset) > 3.0d) {
                this.mountOffset = 0.0d;
            }
            this.offsetCalculated = true;
        }

        Location target = this.player.getLocation();
        double seatY = target.getY() - this.mountOffset;

        // Плавно двигаем невидимый стенд-подиум на координаты игрока через NMS,
        // сохраняя идеальную 60fps интерполяцию кубика на клиенте.
        if (!moveEntityRaw(this.seat, target.getX(), seatY, target.getZ(), target.getYaw(), 0f)) {
            // Фолбэк, если NMS отвалился
            this.seat.setVelocity(new org.bukkit.util.Vector(target.getX(), seatY, target.getZ()).subtract(this.seat.getLocation().toVector()));
        }
    }

    public void despawn() {
        if (this.seat != null) {
            this.seat.remove();
            this.seat = null;
        }
        if (this.cube != null) {
            this.cube.remove();
            this.cube = null;
        }
        this.offsetCalculated = false;
        this.player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    // ==================== NMS ЛОГИКА (из SpawnToolsManager) ====================

    private static Method handleMethod;
    private static Method setLocationMethod;
    private static boolean nmsUnavailable = false;

    private static boolean moveEntityRaw(
        @NonNull Entity entity, double x, double y, double z, float yaw, float pitch) {
        if (nmsUnavailable) return false;
        try {
            Method handle = handleMethod;
            if (handle == null || !handle.getDeclaringClass().isInstance(entity)) {
                handle = entity.getClass().getMethod("getHandle");
                handleMethod = handle;
            }
            Object nmsEntity = handle.invoke(entity);

            Method setLocation = setLocationMethod;
            if (setLocation == null || !setLocation.getDeclaringClass().isInstance(nmsEntity)) {
                setLocation = nmsEntity
                    .getClass()
                    .getMethod(
                        "setLocation",
                        double.class,
                        double.class,
                        double.class,
                        float.class,
                        float.class);
                setLocationMethod = setLocation;
            }
            setLocation.invoke(nmsEntity, x, y, z, yaw, pitch);
            return true;
        } catch (Throwable throwable) {
            nmsUnavailable = true;
            return false;
        }
    }
}
