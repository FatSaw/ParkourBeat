package ru.sortix.parkourbeat.player.friends;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.UUID;

/**
 * Один друг в списке конкретного игрока.
 * <p>
 * Дружба симметрична (в списках обоих), а вот права - нет: то, что вы пустили
 * человека на свои приватные уровни, не означает обратного.
 */
@Getter
public class FriendEntry {
    private final @NonNull UUID playerId;
    @Setter
    private @NonNull String playerName;
    private final long friendsSinceMillis;

    /** Пускать на мои приватные уровни (учитывается только в режиме {@link FriendAccess#SELECTED}). */
    @Setter
    private boolean privateAccess;
    /** Разрешить строить на моих уровнях (режим SELECTED). */
    @Setter
    private boolean buildAccess;
    /** Разрешить телепорт ко мне, даже если телепорты закрыты для всех. */
    @Setter
    private boolean teleportAccess = true;
    /** Уведомлять меня, когда этот друг заходит на сервер. */
    @Setter
    private boolean joinNotifications = true;

    public FriendEntry(@NonNull UUID playerId, @NonNull String playerName, long friendsSinceMillis) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.friendsSinceMillis = friendsSinceMillis;
    }
}
