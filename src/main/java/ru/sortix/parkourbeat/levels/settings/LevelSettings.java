package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.levels.DirectionChecker;
import ru.sortix.parkourbeat.levels.ModerationStatus;
import ru.sortix.parkourbeat.levels.ParticleController;

import java.util.UUID;

@Getter
public class LevelSettings {
    private final @NonNull WorldSettings worldSettings;
    private final @NonNull GameSettings gameSettings;
    private final @NonNull ParticleController particleController;
    private final @NonNull DirectionChecker directionChecker;
    private @NonNull Location startWaypoint, finishWaypoint;
    private double startPosition, finishPosition;
    private double minPosition, maxPosition;
    private double totalLevelDistance;

    public LevelSettings(@NonNull ParkourBeat plugin,
                         @NonNull World world,
                         @NonNull WorldSettings worldSettings,
                         @NonNull GameSettings gameSettings
    ) {
        this.worldSettings = worldSettings;
        this.gameSettings = gameSettings;
        this.directionChecker = new DirectionChecker(worldSettings.getDirection());
        this.particleController = new ParticleController(plugin, world);

        this.recalculateWaypoints(world);

        this.worldSettings.sortWaypoints(this.directionChecker);
    }

    public void recalculateWaypoints(@NonNull World world) {
        this.startWaypoint = this.worldSettings.getStartWaypoint().toLocation(world);
        this.finishWaypoint = this.worldSettings.getFinishWaypoint().toLocation(world);

        this.startPosition = this.directionChecker.getCoordinate(this.startWaypoint);
        this.finishPosition = this.directionChecker.getCoordinate(this.finishWaypoint);

        this.minPosition = Math.min(this.startPosition, this.finishPosition);
        this.maxPosition = Math.max(this.startPosition, this.finishPosition);

        this.totalLevelDistance = this.maxPosition - this.minPosition;
    }

    @NonNull
    public static LevelSettings create(
        @NonNull ParkourBeat plugin,
        @NonNull World world,
        @NonNull World.Environment environment,
        @NonNull UUID uniqueId,
        int uniqueNumber,
        @NonNull Component displayName,
        @NonNull UUID ownerId,
        @NonNull String ownerName
    ) {
        WorldSettings defaultSettings = Settings.getDefaultSettings(environment).setWorld(environment, world);

        if (environment == World.Environment.NETHER) {
            defaultSettings.getLightShow().setLevelBiome(LevelBiome.NETHER);
        } else if (environment == World.Environment.THE_END) {
            defaultSettings.getLightShow().setLevelBiome(LevelBiome.THE_END);
        }

        return new LevelSettings(
            plugin,
            world,
            defaultSettings,
            new GameSettings(
                uniqueId,
                null,
                uniqueNumber,
                ownerId,
                ownerName,
                displayName,
                System.currentTimeMillis(),
                ModerationStatus.NOT_MODERATED
            )
        );
    }

    public void updateParticleLocations() {
        this.getParticleController()
            .loadParticleLocations(this.getWorldSettings().getWaypoints());
    }

    @NonNull
    public Location getStartWaypointLoc() {
        return this.startWaypoint;
    }

    @NonNull
    public Location getFinishWaypointLoc() {
        return this.finishWaypoint;
    }
}
