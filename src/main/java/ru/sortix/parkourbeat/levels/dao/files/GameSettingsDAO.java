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
        config.set("difficulty_multiplier", gameSettings.getDifficultyMultiplier());
        config.set("sliced_playlist_id", gameSettings.getSlicedPlaylistId());
        config.set("checkpoint_attempts", gameSettings.getCheckpointAttempts());
        config.set("slice_offsets_millis", new ArrayList<>(gameSettings.getSliceOffsetsMillis()));
        config.set("slice_durations_millis", new ArrayList<>(gameSettings.getSliceDurationsMillis()));
        config.set("custom_textures", gameSettings.isCustomTextures());
        config.set("chunk_width", gameSettings.getChunkWidth());
        config.set("level_mode", gameSettings.getLevelMode().name());
        config.set("sky_mode", gameSettings.getSkyMode() == null
            ? null : gameSettings.getSkyMode().name());
        config.set("sprint_damage", gameSettings.isSprintDamage());
        config.set("copy_made", gameSettings.isCopyMade());
        config.set("copied_from", gameSettings.getCopiedFrom() == null
            ? null : gameSettings.getCopiedFrom().toString());
        gameSettings.getTwoDSettings().write(config, "two_d");
        gameSettings.getDiggerSettings().write(config, "digger");
        config.set("texture_version_range", gameSettings.getTextureVersionRange() == null
            ? null : gameSettings.getTextureVersionRange().name());

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

        gameSettings.setCustomTextures(config.getBoolean("custom_textures", false));
        // Режим читается ПЕРВЫМ: от него зависят и допустимая ширина (дуэльная карта
        // бывает только широкой), и значение урона за бег по умолчанию.
        gameSettings.setLevelMode(ru.sortix.parkourbeat.twod.LevelMode.byName(
            config.getString("level_mode"), ru.sortix.parkourbeat.twod.LevelMode.THREE_D));
        gameSettings.setChunkWidth(config.getInt("chunk_width", 1));

        // Ключа нет - значит выбора не было: у уровня остаётся то небо, которое у него
        // получилось само. Подставлять сюда FULL нельзя: это молча поменяло бы вид всех
        // уже построенных 360-уровней, у которых половина неба уже часть оформления.
        gameSettings.setSkyMode(ru.sortix.parkourbeat.levels.SkyMode.byName(
            config.getString("sky_mode")));
        // У уровней, созданных до появления настройки, поля в файле нет: значение
        // берётся по режиму, то есть для всех старых уровней урон остаётся включённым.
        gameSettings.setSprintDamage(config.getBoolean("sprint_damage",
            gameSettings.defaultSprintDamage()));
        gameSettings.setCopyMade(config.getBoolean("copy_made", false));
        String copiedFrom = config.getString("copied_from");
        if (copiedFrom != null && !copiedFrom.isEmpty()) {
            try {
                gameSettings.setCopiedFrom(UUID.fromString(copiedFrom));
            } catch (IllegalArgumentException ignored) {
            }
        }
        gameSettings.setTwoDSettings(
            ru.sortix.parkourbeat.twod.TwoDLevelSettings.read(config, "two_d"));
        gameSettings.setDiggerSettings(
            ru.sortix.parkourbeat.digger.DiggerLevelSettings.read(config, "digger"));
        gameSettings.setTextureVersionRange(ru.sortix.parkourbeat.levels.TextureVersionRange
            .byName(config.getString("texture_version_range")));
        try { gameSettings.setDifficulty(LevelDifficulty.valueOf(config.getString("difficulty", "N_A"))); }
        catch (Exception e) { gameSettings.setDifficulty(LevelDifficulty.N_A); }
        gameSettings.setDifficultyMultiplier(config.getDouble("difficulty_multiplier",
            ru.sortix.parkourbeat.levels.settings.GameSettings.MIN_DIFFICULTY_MULTIPLIER));
        gameSettings.setCheckpointAttempts(config.getInt("checkpoint_attempts",
            ru.sortix.parkourbeat.levels.settings.GameSettings.DEFAULT_CHECKPOINT_ATTEMPTS));
        gameSettings.setSliceResult(
            config.getString("sliced_playlist_id"),
            config.getIntegerList("slice_offsets_millis"),
            config.getIntegerList("slice_durations_millis"));

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
