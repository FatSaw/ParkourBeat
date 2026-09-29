// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/levels/settings/LevelSettings.java
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

    /**
     * Второй путь дуэльной карты рисуется отдельным контроллером.
     * <p>
     * Один контроллер умеет показывать ровно одну ломаную, и это правильно: он ведёт
     * для каждого зрителя своё «проявление» пути по мере продвижения. Две трассы в
     * одном контроллере проявлялись бы вместе, и игрок видел бы чужой путь ровно так
     * же, как свой. Поэтому у второй стороны свой контроллер, создаваемый по первому
     * требованию - на обычных уровнях его не существует вовсе.
     */
    private @javax.annotation.Nullable ParticleController secondParticleController = null;

    private final @NonNull ParkourBeat plugin;
    private @NonNull World world;
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
        this.plugin = plugin;
        this.world = world;
        this.worldSettings = worldSettings;
        this.gameSettings = gameSettings;
        this.directionChecker = new DirectionChecker(worldSettings.getDirection());
        this.particleController = new ParticleController(plugin, world);
        this.particleController.setDirectionChecker(this.directionChecker);

        this.recalculateWaypoints(world);
    }

    public void recalculateWaypoints(@NonNull World world) {
        this.world = world;
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

    /**
     * На 360-уровне путей из частиц нет вовсе.
     * <p>
     * Точки старта и финиша в настройках остаются - по ним считаются направление
     * уровня, его границы и момент завершения забега, - но не рисуются: на 360 забег
     * начинается сам при входе, а бежать по линии там нечего.
     */
    public void updateParticleLocations() {
        this.getParticleController().loadParticleLocations(
            this.gameSettings.isThreeSixtyLevel()
                ? java.util.Collections.emptyList()
                : this.getWorldSettings().getWaypoints());
    }

    /**
     * Контроллер второго пути. Создаётся при первом обращении - то есть только на
     * дуэльных картах.
     */
    @NonNull
    public ParticleController getSecondParticleController() {
        ParticleController controller = this.secondParticleController;
        if (controller == null) {
            controller = new ParticleController(this.plugin, this.world);
            controller.setDirectionChecker(this.directionChecker);
            this.secondParticleController = controller;
        }
        return controller;
    }

    public boolean hasSecondParticleController() {
        return this.secondParticleController != null;
    }

    /**
     * Пересобрать частицы второго пути. Вызывается после каждой правки красной палочкой.
     */
    public void updateSecondParticleLocations() {
        this.getSecondParticleController()
            .loadParticleLocations(this.getWorldSettings().getSecondWaypoints());
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
