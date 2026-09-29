package ru.sortix.parkourbeat.stats;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Заявка игрока на сброс собственной статистики.
 * <p>
 * Сброс необратим, поэтому игрок его не выполняет — он его просит,
 * а модератор рассматривает во вкладке {@code /moder}.
 */
@Getter
public class StatResetRequest {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    private final @NonNull UUID playerId;
    private @NonNull String playerName;
    private final long requestedAtMillis;

    @Setter
    private @NonNull Status status;
    /** Ник модератора, закрывшего заявку. */
    @Setter
    private @Nullable String resolvedBy;
    @Setter
    private long resolvedAtMillis;
    /** Узнал ли игрок о решении. Если он был оффлайн — скажем при входе. */
    @Setter
    private boolean notified;

    public StatResetRequest(@NonNull UUID playerId, @NonNull String playerName, long requestedAtMillis) {
        this(playerId, playerName, requestedAtMillis, Status.PENDING, null, 0L, false);
    }

    public StatResetRequest(@NonNull UUID playerId,
                            @NonNull String playerName,
                            long requestedAtMillis,
                            @NonNull Status status,
                            @Nullable String resolvedBy,
                            long resolvedAtMillis,
                            boolean notified) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.requestedAtMillis = requestedAtMillis;
        this.status = status;
        this.resolvedBy = resolvedBy;
        this.resolvedAtMillis = resolvedAtMillis;
        this.notified = notified;
    }

    public void setPlayerName(@NonNull String playerName) {
        this.playerName = playerName;
    }

    public boolean isPending() {
        return this.status == Status.PENDING;
    }

    /** Сколько дней заявка уже висит. */
    public long getAgeDays() {
        return Math.max(0L, (System.currentTimeMillis() - this.requestedAtMillis) / 86_400_000L);
    }
}
