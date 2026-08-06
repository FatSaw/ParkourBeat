package ru.sortix.parkourbeat.world;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import ru.sortix.parkourbeat.levels.DirectionChecker;
import ru.sortix.parkourbeat.levels.settings.LevelSettings;

@UtilityClass
public class LocationUtils {
    /**
     * Довернуть локацию строго вдоль направления уровня: взгляд по горизонту
     * (pitch = 0) и точно вперёд по оси трассы. Та же логика, что применяется
     * при установке точки спавна строителем — просто вынесена, чтобы её можно
     * было переиспользовать при входе игрока на уровень.
     *
     * @return новый объект, исходная локация не меняется
     */
    @NonNull
    public Location alignToDirection(@NonNull Location location, @NonNull DirectionChecker directionChecker) {
        Location aligned = location.clone();
        aligned.setPitch(0f);
        switch (directionChecker.direction()) {
            case POSITIVE_X:
                aligned.setYaw(-90f);
                break;
            case NEGATIVE_X:
                aligned.setYaw(90f);
                break;
            case POSITIVE_Z:
                aligned.setYaw(0f);
                break;
            case NEGATIVE_Z:
                aligned.setYaw(180f);
                break;
        }
        return aligned;
    }

    @SuppressWarnings("RedundantIfStatement")
    public boolean isValidSpawnPoint(@NonNull Location spawnLocation,
                                     @NonNull LevelSettings levelSettings
    ) {
        if (!levelSettings.getDirectionChecker()
            .isCorrectDirection(spawnLocation, levelSettings.getStartWaypointLoc())
        ) {
            return false;
        }

        if (BoundingBoxUtils.isBoundingBoxOverlapsWithAnyBlock(
            spawnLocation.getWorld(),
            BoundingBoxUtils.createBoundingBoxAtPos(0.6F, 1.8F, 0.6F, spawnLocation),
            true,
            true
        )) {
            return false;
        }

        return true;
    }
}
