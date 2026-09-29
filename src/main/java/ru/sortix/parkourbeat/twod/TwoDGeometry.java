package ru.sortix.parkourbeat.twod;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.levels.DirectionChecker;
import ru.sortix.parkourbeat.levels.Level;

import javax.annotation.Nullable;

/**
 * Векторная арифметика 2D-уровня: куда едет кубик, где стоит камера и какой стороной
 * повёрнут блок. Вынесено отдельно, потому что тем же самым пользуются и редактор,
 * и сам забег.
 */
public final class TwoDGeometry {
    private TwoDGeometry() {
    }

    @NonNull
    public static Vector forwardVector(@NonNull DirectionChecker.Direction direction) {
        return switch (direction) {
            case POSITIVE_X -> new Vector(1, 0, 0);
            case NEGATIVE_X -> new Vector(-1, 0, 0);
            case POSITIVE_Z -> new Vector(0, 0, 1);
            case NEGATIVE_Z -> new Vector(0, 0, -1);
        };
    }

    /**
     * Направление ВЗГЛЯДА камеры.
     * <p>
     * Подобрано так, чтобы ход уровня шёл по экрану слева направо - как в оригинале.
     * Экранное «вправо» для взгляда D это (-Dz, Dx); приравняв его к ходу уровня F,
     * получаем ровно эту формулу.
     */
    @NonNull
    public static Vector cameraDirection(@NonNull Vector forward) {
        return new Vector(forward.getZ(), 0.0D, -forward.getX());
    }

    @NonNull
    public static Vector sideVector(@NonNull Vector forward) {
        return cameraDirection(forward).multiply(-1.0D);
    }

    @NonNull
    public static BlockFace faceOf(@NonNull Vector horizontal) {
        if (Math.abs(horizontal.getX()) >= Math.abs(horizontal.getZ())) {
            return horizontal.getX() >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return horizontal.getZ() >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    /**
     * Точка спавна кубика: то, что поставил строитель, а если он этого не сделал -
     * начало пути уровня. Уровень остаётся играбельным сразу после создания.
     */
    @NonNull
    public static Location resolveCubeSpawn(@NonNull Level level) {
        try {
            Location configured = level.getLevelSettings().getGameSettings()
                .getTwoDSettings().getCubeSpawn(level.getWorld());
            if (configured != null) return configured;
        } catch (Throwable ignored) {
        }

        try {
            Location start = level.getLevelSettings().getStartWaypointLoc();
            if (start != null) {
                Location result = start.clone();
                result.setX(result.getBlockX() + 0.5D);
                result.setZ(result.getBlockZ() + 0.5D);
                return result;
            }
        } catch (Throwable ignored) {
        }

        Location spawn = level.getSpawn().clone();
        spawn.setX(spawn.getBlockX() + 0.5D);
        spawn.setZ(spawn.getBlockZ() + 0.5D);
        return spawn;
    }

    /**
     * Где будет стоять камера в момент старта. Нужно, чтобы телепортировать туда
     * игрока ЗАРАНЕЕ: клиент должен успеть прогрузить чанки и увидеть арморстенд,
     * иначе посадка на него из другого конца уровня просто не доезжает до клиента.
     */
    @NonNull
    public static Location cameraStart(@NonNull Level level) {
        Location spawn = resolveCubeSpawn(level);
        Vector forward = forwardVector(level.getLevelSettings().getDirectionChecker().direction());
        Vector side = sideVector(forward);

        double x = spawn.getX() + side.getX() * TwoDTuning.CAMERA_DISTANCE
            + forward.getX() * TwoDTuning.CAMERA_LEAD;
        double z = spawn.getZ() + side.getZ() * TwoDTuning.CAMERA_DISTANCE
            + forward.getZ() * TwoDTuning.CAMERA_LEAD;
        double y = spawn.getY() + TwoDTuning.CAMERA_EYE_HEIGHT - 1.62D;

        Location result = new Location(level.getWorld(), x, y, z);
        result.setYaw(ru.sortix.parkourbeat.twod.TwoDEntityUtils.yawOf(cameraDirection(forward)));
        result.setPitch(TwoDTuning.CAMERA_PITCH);
        return result;
    }

    /**
     * Тип лодки. В 1.21 общий BOAT разделили на породы дерева, поэтому имя ищется.
     */
    /**
     * Транспорт для режима полёта. Вагонетка вместо лодки: она ниже, не качается и
     * не пытается плыть, поэтому кубик на ней сидит ровно.
     */
    @Nullable
    public static EntityType flightVehicleType() {
        // Лодка, а не вагонетка: вагонетка выше, и кубик в ней сидит криво.
        for (String name : new String[]{"BOAT", "OAK_BOAT"}) {
            try {
                return EntityType.valueOf(name);
            } catch (Throwable ignored) {
            }
        }
        return boatType();
    }

    @Nullable
    public static EntityType boatType() {
        for (String name : new String[]{"BOAT", "OAK_BOAT", "OAK_CHEST_BOAT"}) {
            try {
                return EntityType.valueOf(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
