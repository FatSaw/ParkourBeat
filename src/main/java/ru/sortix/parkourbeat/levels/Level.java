package ru.sortix.parkourbeat.levels;

import lombok.Getter;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.levels.settings.LevelSettings;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.world.Cuboid;

import java.util.UUID;

@Getter
public class Level {
    private final @NonNull LevelSettings levelSettings;
    private final @NonNull World world;
    private final @NonNull Cuboid cuboid;
    private boolean isEditing = false;

    public Level(@NonNull LevelSettings levelSettings, @NonNull World world) {
        this.levelSettings = levelSettings;
        this.world = world;
        DirectionChecker.Direction direction = this.levelSettings.getWorldSettings().getDirection();
        this.cuboid = Settings.getLevelFixedEditableArea().get(direction);
        if (this.cuboid == null) {
            throw new IllegalArgumentException("Not fond config of direction " + direction);
        }
    }

    public void setEditing(boolean isEditing) {
        this.isEditing = isEditing;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Level)) return false;
        return ((Level) other).getUniqueId().equals(this.getUniqueId());
    }

    @Override
    public int hashCode() {
        return this.getUniqueId().hashCode();
    }

    @NonNull
    public Component getDisplayName() {
        return this.levelSettings.getGameSettings().getDisplayName();
    }

    @NonNull
    public UUID getUniqueId() {
        return this.levelSettings.getGameSettings().getUniqueId();
    }

    @NonNull
    public Location getSpawn() {
        return this.levelSettings.getWorldSettings().getSpawn();
    }

    /**
     * Pushes the view distances of this level into the runtime pieces that use them.
     */
    public void applyViewDistances() {
        this.levelSettings.getParticleController()
            .setViewDistance(this.levelSettings.getWorldSettings().getParticleViewDistance());

        this.levelSettings.getParticleController().setColorCueLevel(this);
        this.levelSettings.getParticleController()
            .setColorCues(this.getLightShow().getParticleColorCues());
    }

    public void refreshParticleColorCues() {
        this.levelSettings.getParticleController().setColorCueLevel(this);
        this.levelSettings.getParticleController()
            .setColorCues(this.getLightShow().getParticleColorCues());
    }

    @NonNull
    public LightShowSettings getLightShow() {
        return this.levelSettings.getWorldSettings().getLightShow();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isLevelAccessibleForPlaying(@NonNull Player player, boolean bypassForAdmins, boolean sendMessages) {
        GameSettings settings = this.levelSettings.getGameSettings();
        if (settings.isAccessibleForPlaying(player, bypassForAdmins)) return true;
        if (sendMessages) LangOptions.level_play_noaccess.sendMsg(player);
        return false;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isLevelAccessibleForEditing(@NonNull Player player, boolean bypassForAdmins, boolean sendMessages) {
        GameSettings settings = this.levelSettings.getGameSettings();
        if (!settings.canEdit(player, bypassForAdmins, false)) {
            if (sendMessages) LangOptions.level_editor_cantedit_notowner.sendMsg(player);
            return false;
        }
        if (settings.getModerationStatus() == ModerationStatus.ON_MODERATION
            && !player.hasPermission(PermissionConstants.EDIT_OTHERS_LEVELS_ON_MODERATION)
        ) {
            if (sendMessages) LangOptions.level_editor_cantedit_onmoderation.sendMsg(player);
            return false;
        }
        if (sendMessages) settings.canEdit(player, bypassForAdmins, true); // send bypass message
        return true;
    }

    public boolean isLocationInside(@NonNull Location location) {
        if (location.getWorld() != this.world) return false;
        return this.cuboid.isInside(location);
    }

    public boolean isPositionInside(double x, double y, double z) {
        return this.cuboid.isInside(x, y, z);
    }

    @SuppressWarnings({"PointlessBitwiseExpression", "OctalInteger", "RedundantIfStatement"})
    public boolean isChunkInside(@NonNull Chunk chunk) {
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        if (this.isPositionInside((chunkX << 4) | 00, 0, (chunkZ << 4) | 00)) return true;
        if (this.isPositionInside((chunkX << 4) | 15, 0, (chunkZ << 4) | 00)) return true;
        if (this.isPositionInside((chunkX << 4) | 00, 0, (chunkZ << 4) | 15)) return true;
        if (this.isPositionInside((chunkX << 4) | 15, 0, (chunkZ << 4) | 15)) return true;

        return false;
    }
}
