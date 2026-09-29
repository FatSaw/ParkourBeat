package ru.sortix.parkourbeat.player.friends;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Весь «социальный» профиль одного игрока: друзья, заявки и общие права для друзей.
 */
@Getter
public class PlayerFriends {
    private final @NonNull UUID playerId;
    @Setter
    private @NonNull String playerName;

    private final Map<UUID, FriendEntry> friends = new LinkedHashMap<>();
    /** Заявки, отправленные мной. */
    private final Set<UUID> outgoing = new LinkedHashSet<>();
    /** Заявки, присланные мне. */
    private final Set<UUID> incoming = new LinkedHashSet<>();

    /** Кто из друзей может заходить на мои приватные уровни. */
    @Setter
    private @NonNull FriendAccess privateLevelsAccess = FriendAccess.SELECTED;
    /** Кто из друзей может строить на моих уровнях (соредактор по факту). */
    @Setter
    private @NonNull FriendAccess buildAccess = FriendAccess.NONE;
    /** Показывать ли мне сообщения о заходе/выходе друзей. */
    @Setter
    private boolean joinNotifications = true;

    public PlayerFriends(@NonNull UUID playerId, @NonNull String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }

    @Nullable
    public FriendEntry getFriend(@NonNull UUID friendId) {
        return this.friends.get(friendId);
    }

    public boolean isFriend(@NonNull UUID friendId) {
        return this.friends.containsKey(friendId);
    }

    @NonNull
    public Collection<FriendEntry> getAllFriends() {
        return Collections.unmodifiableCollection(this.friends.values());
    }

    public int getFriendsCount() {
        return this.friends.size();
    }
}
