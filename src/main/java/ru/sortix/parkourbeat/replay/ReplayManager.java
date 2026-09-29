// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/replay/ReplayManager.java
package ru.sortix.parkourbeat.replay;

import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.rating.JumpResult;

import javax.annotation.Nullable;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ReplayManager implements PluginManager {
    private static final int MAX_REPLAYS_PER_PLAYER = 36;

    private final @NonNull ParkourBeat plugin;
    private final @NonNull File folder;

    private final Map<UUID, List<ReplayFrame>> recording = new ConcurrentHashMap<>();
    private final Map<UUID, List<ReplayJump>> recordingJumps = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> recordingEnabled = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> swinging = ConcurrentHashMap.newKeySet();

    public ReplayManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "replays");
        if (!this.folder.exists() && !this.folder.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку реплеев");
        }
    }

    public boolean isRecordingEnabled(@NonNull UUID playerId) {
        return this.recordingEnabled.contains(playerId);
    }

    public void setRecordingEnabled(@NonNull UUID playerId, boolean enabled) {
        if (enabled) this.recordingEnabled.add(playerId);
        else {
            this.recordingEnabled.remove(playerId);
            this.recording.remove(playerId);
            this.recordingJumps.remove(playerId);
        }
    }

    public void startRecording(@NonNull Player player) {
        if (!this.isRecordingEnabled(player.getUniqueId())) return;
        this.recording.put(player.getUniqueId(), new ArrayList<>());
        this.recordingJumps.put(player.getUniqueId(), new ArrayList<>());
    }

    public void cancelRecording(@NonNull UUID playerId) {
        this.recording.remove(playerId);
        this.recordingJumps.remove(playerId);
    }

    public boolean isRecording(@NonNull UUID playerId) {
        return this.recording.containsKey(playerId);
    }

    public void recordSwing(@NonNull Player player) {
        if (this.isRecording(player.getUniqueId())) {
            this.swinging.add(player.getUniqueId());
        }
    }

    public void recordFrame(@NonNull Player player) {
        List<ReplayFrame> frames = this.recording.get(player.getUniqueId());
        if (frames == null) return;
        if (frames.size() >= ReplayData.MAX_FRAMES) {
            this.recording.remove(player.getUniqueId());
            this.recordingJumps.remove(player.getUniqueId());
            return;
        }
        boolean swing = this.swinging.remove(player.getUniqueId());
        frames.add(ReplayFrame.of(player.getLocation(), player.isSneaking(), player.isSprinting(), swing));
    }

    public void recordJump(@NonNull Player player, @NonNull JumpResult result) {
        List<ReplayFrame> frames = this.recording.get(player.getUniqueId());
        List<ReplayJump> jumps = this.recordingJumps.get(player.getUniqueId());
        if (frames != null && jumps != null) {
            jumps.add(new ReplayJump(frames.size(), result));
        }
    }

    public List<ReplayFrame> extractRecording(@NonNull Player player) {
        return this.recording.remove(player.getUniqueId());
    }

    public List<ReplayJump> extractJumps(@NonNull Player player) {
        return this.recordingJumps.remove(player.getUniqueId());
    }

    public void saveReplayAsync(List<ReplayFrame> frames, List<ReplayJump> jumps, UUID playerId, String playerName, UUID levelId, long runId) {
        ReplayData data = new ReplayData(playerId, playerName, levelId, System.currentTimeMillis(), frames, jumps == null ? new ArrayList<>() : jumps);
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            this.save(runId, data);
            this.pruneOldReplays(playerId);
        });
    }

    @NonNull
    private File fileOf(long runId) {
        return new File(this.folder, "run_" + runId + ".pbr");
    }

    private void save(long runId, @NonNull ReplayData data) {
        File target = this.fileOf(runId);
        File temp = new File(target.getAbsolutePath() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(
            new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(temp))))) {
            data.write(out);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Не удалось сохранить реплей " + runId, e);
            temp.delete();
            return;
        }
        if (target.exists() && !target.delete()) {
            this.plugin.getLogger().warning("Не удалось заменить реплей " + runId);
        }
        if (!temp.renameTo(target)) {
            this.plugin.getLogger().warning("Не удалось переименовать реплей " + runId);
            temp.delete();
        }
    }

    public boolean hasReplay(long runId) {
        return runId > 0L && this.fileOf(runId).isFile();
    }

    public void loadAsync(long runId, @NonNull Consumer<ReplayData> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            ReplayData data = this.loadBlocking(runId);
            Bukkit.getScheduler().runTask(this.plugin, () -> callback.accept(data));
        });
    }

    @Nullable
    private ReplayData loadBlocking(long runId) {
        File file = this.fileOf(runId);
        if (!file.isFile()) return null;
        try (DataInputStream in = new DataInputStream(
            new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
            return ReplayData.read(in);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Не удалось прочитать реплей " + runId, e);
            return null;
        }
    }

    public void deleteReplay(long runId) {
        File file = this.fileOf(runId);
        if (file.isFile() && !file.delete()) {
            this.plugin.getLogger().warning("Не удалось удалить реплей " + runId);
        }
    }

    /**
     * Чистит лишние записи игрока.
     * <p>
     * Метод существовал и раньше, но досчитывал количество и молча выходил, ничего не удаляя:
     * файлы копились без предела. Теперь у игрока остаются последние
     * {@link #MAX_REPLAYS_PER_PLAYER} записей плюс все закреплённые - их игрок вывел в
     * профиль сам, и молча стирать их нельзя.
     */
    private void pruneOldReplays(@NonNull UUID playerId) {
        try {
            java.util.Set<Long> alive = this.plugin
                .get(ru.sortix.parkourbeat.rating.StatisticsManager.class)
                .getStorage().loadExistingRunIds(playerId);
            if (alive.isEmpty()) return;

            java.util.Set<Long> pinned;
            try {
                pinned = this.plugin.get(ru.sortix.parkourbeat.player.PlayerSettingsManager.class)
                    .getPinnedReplays(playerId);
            } catch (Exception e) {
                pinned = java.util.Set.of();
            }

            // Свои файлы игрока, от новых к старым: id растёт вместе с забегами.
            java.util.List<Long> own = new java.util.ArrayList<>();
            for (long id : alive) {
                if (this.hasReplay(id)) own.add(id);
            }
            own.sort(java.util.Comparator.reverseOrder());

            int kept = 0;
            for (long id : own) {
                if (pinned.contains(id)) continue;
                if (++kept <= MAX_REPLAYS_PER_PLAYER) continue;
                this.deleteReplay(id);
            }
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.FINE, "Не удалось почистить реплеи", e);
        }
    }

    @Nullable
    private static Long parseRunId(@NonNull String fileName) {
        if (!fileName.startsWith("run_") || !fileName.endsWith(".pbr")) return null;
        try {
            return Long.parseLong(fileName.substring(4, fileName.length() - 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void disable() {
        this.recording.clear();
        this.recordingJumps.clear();
        this.recordingEnabled.clear();
        this.swinging.clear();
    }
}
