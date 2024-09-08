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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isLevelAccessibleForPlaying(@NonNull Player player, boolean bypassForAdmins, boolean sendMessages) {
        GameSettings settings = this.levelSettings.getGameSettings();
        if (settings.isPublicVisible()) return true;
        if (settings.isOwner(player, bypassForAdmins, sendMessages)) return true;
        if (sendMessages) player.sendMessage("У вас нет доступа для игры на этом уровне");
        return false;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isLevelAccessibleForEditing(@NonNull Player player, boolean bypassForAdmins, boolean sendMessages) {
        // Если редактирование, то проверяем, что владелец и не на модерации
        GameSettings settings = this.levelSettings.getGameSettings();
        if (!settings.isOwner(player, bypassForAdmins, false)) {
            if (sendMessages) player.sendMessage("У вас нет доступа для редактирования этого уровня");
            return false;
        }
        if (settings.getModerationStatus() == ModerationStatus.ON_MODERATION
            && !player.hasPermission(PermissionConstants.EDIT_OTHERS_LEVELS_ON_MODERATION)
        ) {
            if (sendMessages) player.sendMessage("Уровень находится на редактировании");
            return false;
        }
        if (sendMessages) settings.isOwner(player, bypassForAdmins, true); // send bypass message
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
