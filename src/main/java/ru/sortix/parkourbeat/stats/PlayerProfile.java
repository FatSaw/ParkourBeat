package ru.sortix.parkourbeat.stats;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class PlayerProfile {
    private final @NonNull UUID playerId;

    @Setter
    private @NonNull String playerName;

    @Setter
    private long firstJoinAtMillis;

    @Setter
    private long playtimeMillis;

    @Setter
    private long totalAttempts;

    private final Map<UUID, RunResult> records = new ConcurrentHashMap<>();

    @Setter
    private volatile boolean dirty = false;

    public PlayerProfile(@NonNull UUID playerId, @NonNull String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.firstJoinAtMillis = System.currentTimeMillis();
    }

    @Nullable
    public RunResult getRecord(@NonNull UUID levelId) {
        return this.records.get(levelId);
    }

    public void putRecord(@NonNull RunResult record) {
        this.records.put(record.getLevelId(), record);
    }

    public void removeRecord(@NonNull UUID levelId) {
        this.records.remove(levelId);
    }

    public void clearRecords() {
        this.records.clear();
    }

    @NonNull
    public Collection<RunResult> getAllRecords() {
        return Collections.unmodifiableCollection(this.records.values());
    }

    public void addAttempt() {
        this.totalAttempts++;
        this.dirty = true;
    }

    public void addPlaytime(long millis) {
        if (millis <= 0L) return;
        this.playtimeMillis += millis;
        this.dirty = true;
    }
}
