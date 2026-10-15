package ru.sortix.parkourbeat.activity;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.Level;

@Getter
@RequiredArgsConstructor
public abstract class UserActivity {
    protected final @NonNull ParkourBeat plugin;
    protected final @NonNull Player player;
    protected final @NonNull Level level;

    public boolean isValidWorld(@NonNull World world) {
        return this.getLevel().getWorld() == world;
    }

    public abstract void startActivity();

    public abstract void on(@NonNull PlayerMoveEvent event);

    public abstract void onTick();

    public abstract void on(@NonNull PlayerToggleSprintEvent event);

    public abstract void on(@NonNull PlayerToggleSneakEvent event);

    /**
     * Настоящий прыжок игрока по данным сервера.
     * <p>
     * Событие paper-специфичное и на Bedrock через Geyser приходит не всегда, поэтому
     * оно не обязательно к обработке: это уточнение, а не единственный источник правды.
     */
    public void on(@NonNull com.destroystokyo.paper.event.player.PlayerJumpEvent event) {
    }

    public abstract int getFallHeight();

    public abstract void onPlayerFall();

    public abstract void endActivity();

    /**
     * Игрок сейчас строит уровень: сам редактор или тестовый забег внутри него.
     */
    public boolean isEditorMode() {
        return false;
    }

    /**
     * Тестовый забег (переопределяется в EditActivity).
     */
    public boolean isTesting() {
        return false;
    }

    /**
     * Игрок вышел за пределы построенного пути вдоль оси уровня: точек частиц тут нет,
     * значит и высоте смерти взяться неоткуда. Внутри пути ничего не меняется.
     */
    public boolean isOutsidePathSpan() {
        // Обычный забег — поблажек нет
        if (!this.isEditorMode()) return false;

        // В редакторе поблажка работает ТОЛЬКО во время тестового забега.
        // Если мы просто строим (isTesting() == false), то возвращаем false,
        // чтобы игрок при падении телепортировался на спавн.
        if (this instanceof ru.sortix.parkourbeat.activity.type.EditActivity && !this.isTesting()) {
            return false;
        }

        // У 360-уровня нет путей из частиц, поэтому "пространства пути" не существует.
        if (this.level.getLevelSettings().getGameSettings().isThreeSixtyLevel()) {
            return false;
        }

        try {
            ru.sortix.parkourbeat.levels.settings.LevelSettings settings =
                this.level.getLevelSettings();
            ru.sortix.parkourbeat.levels.DirectionChecker checker = settings.getDirectionChecker();
            ru.sortix.parkourbeat.levels.settings.WorldSettings worldSettings =
                settings.getWorldSettings();

            if (worldSettings.getWaypoints().size() < 2) return true;

            double playerCoordinate = checker.getCoordinate(this.player.getLocation());
            double startCoordinate = checker.getCoordinate(worldSettings.getStartWaypoint());
            double finishCoordinate = checker.getCoordinate(worldSettings.getFinishWaypoint());

            double from = Math.min(startCoordinate, finishCoordinate);
            double to = Math.max(startCoordinate, finishCoordinate);

            return playerCoordinate < from || playerCoordinate > to;
        } catch (Exception e) {
            return false;
        }
    }

    protected int getFallHeight(boolean isEditing) {
        if (isEditing) return -5;
        return this.level.getLevelSettings().getWorldSettings().getMinWorldHeight() - 1;
    }
}
