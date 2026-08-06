package ru.sortix.parkourbeat.levels.dao.files;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.levels.ModerationStatus;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.levels.settings.LevelBossBarColor;
import ru.sortix.parkourbeat.player.music.MusicTrack;
import ru.sortix.parkourbeat.player.music.MusicTracksManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class GameSettingsDAO {
    private final @NonNull ParkourBeat plugin;

    public void write(@NonNull GameSettings gameSettings, @NonNull FileConfiguration config) {
        config.set("unique_name", gameSettings.getUniqueName());
        config.set("unique_number", gameSettings.getUniqueNumber());
        config.set("owner_id", gameSettings.getOwnerId().toString());
        config.set("owner_name", gameSettings.getOwnerName());
        config.set("display_name", gameSettings.getDisplayNameLegacy(false));
        config.set("level_name", null);
        config.set("created_at_mills", gameSettings.getCreatedAtMills());
        config.set("custom_physics_enabled", gameSettings.isCustomPhysicsEnabled());
        config.set("difficulty", gameSettings.getDifficulty().name());

        MusicTrack musicTrack = gameSettings.getMusicTrack();
        if (musicTrack != null) {
            config.set("music_track_id", musicTrack.getId());
            if (gameSettings.isUseTrackPieces()) config.set("use_track_pieces", true);
        }
        config.set("status", gameSettings.getModerationStatus().name());
        config.set("public_visible", gameSettings.isPublicVisible());
        config.set("boss_bar_color", gameSettings.getBossBarColor().name());
        config.set("hide_boss_bar", gameSettings.isHideBossBar());
        config.set("border_push_strength", gameSettings.getBorderPushStrength());

        List<String> coEditors = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : gameSettings.getCoEditors().entrySet()) {
            coEditors.add(entry.getKey() + " " + entry.getValue());
        }
        config.set("co_editors", coEditors);

        List<String> trustedList = new ArrayList<>();
        for (UUID trustedId : gameSettings.getTrustedCoEditors()) {
            trustedList.add(trustedId.toString());
        }
        config.set("trusted_co_editors", trustedList);
        List<String> ratingsList = new ArrayList<>();
        for (Map.Entry<UUID, LevelDifficulty> entry : gameSettings.getPlayerRatings().entrySet()) {
            ratingsList.add(entry.getKey().toString() + ":" + entry.getValue().name());
        }
        config.set("player_ratings", ratingsList);
    }

    @NonNull
    public GameSettings read(@NonNull UUID uniqueId, @NonNull FileConfiguration config) {
        String uniqueName = config.getString("unique_name", null);
        int uniqueNumber = config.getInt("unique_number", -1);
        if (uniqueNumber < 0) throw new IllegalArgumentException("Int \"unique_number\" not found");

        String ownerIdString = config.getString("owner_id", null);
        if (ownerIdString == null) throw new IllegalArgumentException("String \"owner_id\" not found");

        UUID ownerId = UUID.fromString(ownerIdString);
        String ownerName = config.getString("owner_name");
        if (ownerName == null) throw new IllegalArgumentException("String \"owner_name\" not found");

        String displayNameLegacy = config.getString("display_name");
        if (displayNameLegacy == null) throw new IllegalArgumentException("String \"display_name\" not found");
        Component displayName = LegacyComponentSerializer.legacySection().deserialize(displayNameLegacy);

        long createdAtMills = config.getLong("created_at_mills", -1);
        if (createdAtMills < 0) throw new IllegalArgumentException("Long \"created_at_mills\" not found");

        boolean customPhysicsEnabled = config.getBoolean("custom_physics_enabled", true);

        MusicTrack musicTrack = null;
        String trackUniqueId = config.getString("music_track_id");
        if (trackUniqueId != null) {
            musicTrack = this.plugin.get(MusicTracksManager.class).getPlatform().getTrackById(trackUniqueId);
            // ФИКС ПОТЕРИ ТРЕКОВ: Если AMusic еще не прогрузился, создаем заглушку, чтобы не стереть трек из файла!
            if (musicTrack == null) {
                boolean useTrackPieces = config.getBoolean("use_track_pieces", false);
                musicTrack = new MusicTrack(this.plugin.get(MusicTracksManager.class).getPlatform(), trackUniqueId, trackUniqueId, useTrackPieces);
            }
        }

        boolean useTrackPieces = musicTrack != null && musicTrack.isPiecesSupported() && config.getBoolean("use_track_pieces", false);

        ModerationStatus state;
        try { state = ModerationStatus.valueOf(config.getString("status", ModerationStatus.NOT_MODERATED.name())); }
        catch (IllegalArgumentException e) { state = ModerationStatus.NOT_MODERATED; }
        boolean publicVisible = config.getBoolean("public_visible", false);

        GameSettings gameSettings = new GameSettings(uniqueId, uniqueName, uniqueNumber, ownerId, ownerName, displayName, createdAtMills, customPhysicsEnabled, musicTrack, useTrackPieces, state, publicVisible);

        try { gameSettings.setDifficulty(LevelDifficulty.valueOf(config.getString("difficulty", "N_A"))); }
        catch (Exception e) { gameSettings.setDifficulty(LevelDifficulty.N_A); }

        gameSettings.setBossBarColor(LevelBossBarColor.byName(config.getString("boss_bar_color"), LevelBossBarColor.DEFAULT));
        gameSettings.setHideBossBar(config.getBoolean("hide_boss_bar", false));
        gameSettings.setBorderPushStrength(config.getDouble("border_push_strength", 0.0D));

        for (String rawCoEditor : config.getStringList("co_editors")) {
            if (rawCoEditor == null) continue;
            String[] args = rawCoEditor.trim().split(" ", 2);
            if (args.length < 1) continue;
            try {
                gameSettings.addCoEditor(UUID.fromString(args[0]), args.length > 1 && !args[1].isEmpty() ? args[1] : args[0]);
            } catch (IllegalArgumentException e) {}
        }
        for (String trustedIdStr : config.getStringList("trusted_co_editors")) {
            if (trustedIdStr == null) continue;
            try { gameSettings.setTrusted(UUID.fromString(trustedIdStr), true); }
            catch (IllegalArgumentException e) {}
        }
        for (String ratingStr : config.getStringList("player_ratings")) {
            if (ratingStr == null) continue;
            String[] parts = ratingStr.split(":");
            if (parts.length == 2) {
                try {
                    gameSettings.setPlayerRating(UUID.fromString(parts[0]), LevelDifficulty.valueOf(parts[1]));
                } catch (Exception ignored) {}
            }
        }
        return gameSettings;
    }
}
