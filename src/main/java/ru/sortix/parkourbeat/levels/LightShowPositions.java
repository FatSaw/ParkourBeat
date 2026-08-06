package ru.sortix.parkourbeat.levels;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.levels.settings.LevelSettings;
import ru.sortix.parkourbeat.utils.TimeUtils;

/**
 * The song position is tied to the distance travelled along the level, which is the same
 * relation the track pieces already use. That makes a point on the level and a timecode
 * two ways of naming the same thing.
 */
@UtilityClass
public class LightShowPositions {
    /**
     * Exact, not snapped to the block: whatever line the player pointed at is the trigger.
     */
    public int toTimeMillis(@NonNull Level level, @NonNull Vector position) {
        long millis = Math.round((getSignedDistance(level, position) / Game.BLOCKS_PER_SECOND) * 1000.0D);
        if (millis < 0L) return 0;
        return (int) Math.min(millis, TimeUtils.MAX_TIMECODE_MILLIS);
    }

    /**
     * Negative behind the start waypoint, which is where the spawn platform sits. Without the
     * sign a player standing before the level reads as being at the very first moment of it.
     */
    public double getSignedDistance(@NonNull Level level, @NonNull Vector position) {
        LevelSettings levelSettings = level.getLevelSettings();
        double delta = levelSettings.getDirectionChecker().getCoordinate(position)
            - levelSettings.getStartPosition();
        return levelSettings.getDirectionChecker().isNegative() ? -delta : delta;
    }

    public double getSignedDistance(@NonNull Level level, @NonNull Location location) {
        return getSignedDistance(level, location.toVector());
    }

    public int toTimeMillis(@NonNull Level level, @NonNull Location location) {
        return toTimeMillis(level, location.toVector());
    }

    /**
     * @return coordinate along the level direction that a timecode points at
     */
    public double toCoordinate(@NonNull Level level, int timeMillis) {
        LevelSettings levelSettings = level.getLevelSettings();
        double distance = (timeMillis / 1000.0D) * Game.BLOCKS_PER_SECOND;
        double start = levelSettings.getStartPosition();
        return levelSettings.getDirectionChecker().isNegative() ? start - distance : start + distance;
    }

    public boolean isAlongX(@NonNull Level level) {
        return switch (level.getLevelSettings().getWorldSettings().getDirection()) {
            case POSITIVE_X, NEGATIVE_X -> true;
            case POSITIVE_Z, NEGATIVE_Z -> false;
        };
    }
}
