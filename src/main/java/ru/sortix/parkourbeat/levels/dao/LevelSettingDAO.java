package ru.sortix.parkourbeat.levels.dao;

import lombok.NonNull;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.levels.settings.LevelSettings;
import ru.sortix.parkourbeat.levels.settings.WorldSettings;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.UUID;

public interface LevelSettingDAO {
    @Nullable
    LevelSettings loadLevelSettings(@NonNull UUID levelId, @Nullable GameSettings gameSettings);

    @NonNull WorldSettings loadLevelWorldSettings(@NonNull File settingsDir);

    default void saveLevelSettings(@NonNull LevelSettings settings) {
        this.saveGameSettings(settings.getGameSettings());
        this.saveWorldSettings(settings.getGameSettings().getUniqueId(), settings.getWorldSettings());
    }

    void saveGameSettings(@NonNull GameSettings gameSettings);

    void saveWorldSettings(@NonNull UUID levelId, @NonNull WorldSettings worldSettings);

    @Nullable
    World getBukkitWorld(@NonNull UUID levelId);

    void deleteLevelWorldAndSettings(@NonNull UUID levelId);

    @NonNull WorldCreator newWorldCreator(@NonNull UUID levelId);

    boolean isLevelWorld(@NonNull World world);

    @NonNull Collection<GameSettings> loadAllAvailableLevelGameSettingsSync();
}
