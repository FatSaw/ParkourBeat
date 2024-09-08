package ru.sortix.parkourbeat.levels;

import lombok.Getter;
import lombok.NonNull;
import ru.sortix.parkourbeat.levels.dao.LevelSettingDAO;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.levels.settings.LevelSettings;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LevelSettingsManager {
    private final Map<UUID, LevelSettings> levelSettings = new HashMap<>();

    @Getter
    private final LevelSettingDAO levelSettingDAO;

    protected LevelSettingsManager(@NonNull LevelSettingDAO levelSettingDAO) {
        this.levelSettingDAO = levelSettingDAO;
    }

    public void addLevelSettings(@NonNull UUID levelId, @NonNull LevelSettings settings) {
        this.levelSettings.put(levelId, settings);
        this.levelSettingDAO.saveLevelSettings(settings);
    }

    public void unloadLevelSettings(@NonNull UUID levelId) {
        this.levelSettings.remove(levelId);
    }

    @NonNull
    public LevelSettings loadLevelSettings(@NonNull UUID levelId, @Nullable GameSettings gameSettings) {
        LevelSettings settings = this.levelSettings.get(levelId);
        if (settings != null) return settings;

        settings = this.levelSettingDAO.loadLevelSettings(levelId, gameSettings);
        if (settings == null) {
            throw new IllegalArgumentException("Failed to load settings for level " + levelId);
        }

        this.levelSettings.put(levelId, settings);
        return settings;
    }

    public void saveLevelSettings(@NonNull UUID levelId) {
        LevelSettings settings = this.levelSettings.get(levelId);
        if (settings == null) {
            throw new IllegalStateException(
                "Failed to save settings for level " + levelId + ": " + "Settings not found");
        }
        this.levelSettingDAO.saveLevelSettings(settings);
    }
}
