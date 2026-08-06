package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.levels.ModerationStatus;
import ru.sortix.parkourbeat.player.music.MusicTrack;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
public class GameSettings {
    public static final int MAX_CO_EDITORS = 16;

    private final @NonNull UUID uniqueId;
    private final @Nullable String uniqueName;
    private final int uniqueNumber;

    private final @NonNull UUID ownerId;
    private final @NonNull String ownerName;
    @Setter
    private @NonNull Component displayName;

    private final long createdAtMills;
    private @Setter boolean customPhysicsEnabled;
    @Nullable
    private @Setter MusicTrack musicTrack;
    private @Setter boolean useTrackPieces;
    @Setter
    private @NonNull ModerationStatus moderationStatus;
    @Setter
    private boolean publicVisible;
    @Setter
    private @NonNull LevelBossBarColor bossBarColor = LevelBossBarColor.DEFAULT;
    @Setter
    private boolean hideBossBar = false;
    @Setter
    private double borderPushStrength = 0.0D;

    @Setter
    private @NonNull LevelDifficulty difficulty = LevelDifficulty.N_A;

    private final Map<UUID, String> coEditors = new LinkedHashMap<>();
    private final Set<UUID> trustedCoEditors = new HashSet<>();
    private final Map<UUID, LevelDifficulty> playerRatings = new java.util.HashMap<>();

    public Map<UUID, LevelDifficulty> getPlayerRatings() {
        return Collections.unmodifiableMap(this.playerRatings);
    }

    public void setPlayerRating(UUID uuid, LevelDifficulty diff) {
        this.playerRatings.put(uuid, diff);
    }

    public GameSettings(@NonNull UUID uniqueId, @Nullable String uniqueName, int uniqueNumber, @NonNull UUID ownerId, @NonNull String ownerName, @NonNull Component displayName, long createdAtMills, @NonNull ModerationStatus moderationStatus) {
        this.uniqueId = uniqueId;
        this.uniqueName = uniqueName;
        this.uniqueNumber = uniqueNumber;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.displayName = displayName;
        this.createdAtMills = createdAtMills;
        this.moderationStatus = moderationStatus;
    }

    public GameSettings(@NonNull UUID uniqueId, @Nullable String uniqueName, int uniqueNumber, @NonNull UUID ownerId, @NonNull String ownerName, @NonNull Component displayName, long createdAtMills, boolean customPhysicsEnabled, @Nullable MusicTrack musicTrack, boolean useTrackPieces, @NonNull ModerationStatus moderationStatus, boolean publicVisible) {
        this(uniqueId, uniqueName, uniqueNumber, ownerId, ownerName, displayName, createdAtMills, moderationStatus);
        this.customPhysicsEnabled = customPhysicsEnabled;
        this.musicTrack = musicTrack;
        this.useTrackPieces = useTrackPieces;
        this.publicVisible = publicVisible;
    }

    @NonNull
    public Component getDisplayName() {
        return this.displayName.colorIfAbsent(NamedTextColor.GOLD);
    }

    @NonNull
    public String getDisplayNameLegacy() {
        return LegacyComponentSerializer.legacySection().serialize(this.displayName.colorIfAbsent(NamedTextColor.GOLD));
    }

    @NonNull
    public String getDisplayNameLegacy(boolean useDefaultColor) {
        if (useDefaultColor) return this.getDisplayNameLegacy();
        return LegacyComponentSerializer.legacySection().serialize(this.displayName);
    }

    public boolean isOwner(@NonNull UUID playerId) {
        return this.ownerId.equals(playerId);
    }

    public boolean isOwner(@NonNull CommandSender sender, boolean bypassForAdmins, boolean bypassMsg) {
        if (sender instanceof Player) {
            if (this.ownerId.equals(((Player) sender).getUniqueId())) return true;
            if (bypassForAdmins && sender.hasPermission(PermissionConstants.EDIT_OTHERS_LEVELS)) {
                if (bypassMsg) LangOptions.level_editor_permissionbypass.sendMsg(sender);
                return true;
            }
            return false;
        }
        return sender instanceof ConsoleCommandSender;
    }

    @NonNull
    public Map<UUID, String> getCoEditors() { return Collections.unmodifiableMap(this.coEditors); }

    @NonNull
    public Set<UUID> getTrustedCoEditors() { return Collections.unmodifiableSet(this.trustedCoEditors); }

    public boolean isTrusted(@NonNull UUID playerId) { return this.trustedCoEditors.contains(playerId); }

    public void setTrusted(@NonNull UUID playerId, boolean trusted) {
        if (trusted) this.trustedCoEditors.add(playerId);
        else this.trustedCoEditors.remove(playerId);
    }

    public boolean isCoEditor(@NonNull UUID playerId) { return this.coEditors.containsKey(playerId); }

    @Nullable
    public String getCoEditorName(@NonNull UUID playerId) { return this.coEditors.get(playerId); }

    public boolean addCoEditor(@NonNull UUID playerId, @NonNull String playerName) {
        if (this.isOwner(playerId) || this.coEditors.containsKey(playerId) || this.coEditors.size() >= MAX_CO_EDITORS) return false;
        this.coEditors.put(playerId, playerName);
        return true;
    }

    public boolean removeCoEditor(@NonNull UUID playerId) {
        this.trustedCoEditors.remove(playerId);
        return this.coEditors.remove(playerId) != null;
    }

    public boolean canEdit(@NonNull CommandSender sender, boolean bypassForAdmins, boolean bypassMsg) {
        if (sender instanceof Player player) {
            UUID playerId = player.getUniqueId();
            if (this.ownerId.equals(playerId) || this.coEditors.containsKey(playerId)) return true;
            if (bypassForAdmins && sender.hasPermission(PermissionConstants.EDIT_OTHERS_LEVELS)) {
                if (bypassMsg) LangOptions.level_editor_permissionbypass.sendMsg(sender);
                return true;
            }
            return false;
        }
        return sender instanceof ConsoleCommandSender;
    }

    public boolean canEdit(@NonNull UUID playerId) { return this.isOwner(playerId) || this.coEditors.containsKey(playerId); }

    public boolean isAccessibleForPlaying(@NonNull CommandSender sender, boolean bypassForAdmins) {
        if (this.publicVisible) return true;
        return this.canEdit(sender, bypassForAdmins, false);
    }
}
