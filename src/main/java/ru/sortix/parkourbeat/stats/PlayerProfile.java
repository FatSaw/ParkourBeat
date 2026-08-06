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

    /** Актуальный ник, обновляется при входе (п.11.1 — всё завязано на UUID). */
    @Setter
    private @NonNull String playerName;

    /** Дата первого захода на ParkourBeat, unix millis. */
    @Setter
    private long firstJoinAtMillis;

    /** Суммарное время на ParkourBeat, мс. Копится по сессиям. */
    @Setter
    private long playtimeMillis;

    /** Суммарное количество попыток (все завершённые прохождения, кроме PRACTICE). */
    @Setter
    private long totalAttempts;

    /** Рекорды: levelId -> лучшее прохождение. */
    private final Map<UUID, RunResult> records = new ConcurrentHashMap<>();

    /** true — профиль изменился и должен быть сохранён ближайшим автосейвом. */
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
