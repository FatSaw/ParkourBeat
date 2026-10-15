package ru.sortix.parkourbeat.replay;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.type.ReplayActivity;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.player.PlayerSettingsManager;
import ru.sortix.parkourbeat.player.music.MusicTrack;
import ru.sortix.parkourbeat.stats.RunResult;

import ru.sortix.parkourbeat.utils.text.PbText;
@UtilityClass
public class ReplayStarter {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public void start(@NonNull ParkourBeat plugin, @NonNull Player viewer, @NonNull RunResult run) {
        ReplayManager replays = plugin.get(ReplayManager.class);

        if (!replays.hasReplay(run.getRowId())) {
            viewer.sendMessage(PbText.of(Lang.raw(PlayerLang.of(viewer), "auto.replay_starter.start.1")));
            return;
        }

        if (plugin.get(PlayerSettingsManager.class).areReplaysHidden(run.getPlayerId())
            && !viewer.getUniqueId().equals(run.getPlayerId())) {
            viewer.sendMessage(PbText.of(Lang.raw(PlayerLang.of(viewer), "auto.replay_starter.start.2")));
            return;
        }

        viewer.closeInventory();
        viewer.sendMessage(PbText.of(Lang.raw(PlayerLang.of(viewer), "auto.replay_starter.start.3")));

        replays.loadAsync(run.getRowId(), data -> {
            if (data == null) {
                viewer.sendMessage(PbText.of(Lang.raw(PlayerLang.of(viewer), "auto.replay_starter.start.4")));
                return;
            }
            if (!viewer.isOnline()) return;

            plugin.get(LevelsManager.class).loadLevel(run.getLevelId(), null).thenAccept(level -> {
                if (level == null) {
                    viewer.sendMessage(PbText.of(Lang.raw(PlayerLang.of(viewer), "auto.replay_starter.start.5")));
                    return;
                }
                if (!viewer.isOnline()) return;

                startOnLevel(plugin, viewer, level, data, run);
            });
        });
    }

    public void startOnLevel(@NonNull ParkourBeat plugin, @NonNull Player viewer,
                             @NonNull Level level, @NonNull ReplayData data, @NonNull RunResult run) {
        ReplayActivity activity = new ReplayActivity(plugin, viewer, level, data, run);
        // Достаем точку телепортации для метода switchActivity
        Location start = data.getFrames().isEmpty() ? level.getSpawn() : data.getFrames().get(0).toLocation(level.getWorld());
        plugin.get(ActivityManager.class).switchActivity(viewer, activity, start);

        requestReplayPack(plugin, viewer, level, activity);
    }

    /**
     * Выдаёт зрителю ресурспак уровня и сообщает активности, чем всё кончилось.
     * <p>
     * Пак запрашивается ПОСЛЕ переключения активности: до него зритель формально всё ещё
     * на своём прошлом уровне, и текстуры того уровня уезжали в архив трека этого.
     * Уровень текстур всё равно называем явно - полагаться на порядок вызовов не стоит.
     * <p>
     * Реплей ждёт этот ответ, прежде чем включить музыку: иначе он стартовал вслепую
     * через три секунды и шёл молча, если пак ещё не доехал.
     */
    private void requestReplayPack(@NonNull ParkourBeat plugin, @NonNull Player viewer,
                                   @NonNull Level level, @NonNull ReplayActivity activity) {
        GameSettings settings = level.getLevelSettings().getGameSettings();
        MusicTrack track = settings.getMusicTrack();

        if (track == null || !track.isStillAvailable()) {
            activity.setNoMusicTrack();
            return;
        }

        java.util.UUID texturesLevelId = settings.isCustomTextures() ? level.getUniqueId() : null;
        track.setResourcepackAsync(plugin, viewer, texturesLevelId,
            result -> activity.setMusicPackReady(result != null && result.isOk()), null);
    }
}
