package ru.sortix.parkourbeat.player.friends;

import lombok.NonNull;

import javax.annotation.Nullable;

/**
 * Кому из друзей выдано право (заходить на приватные уровни, строить и т.д.).
 * <p>
 * SELECTED - право выдаётся поштучно каждому другу в его карточке. Так владелец
 * может пустить одного человека и не открывать доступ всей пачке друзей сразу.
 */
public enum FriendAccess {
    NONE("none"),
    SELECTED("selected"),
    ALL("all");

    private final @NonNull String langKey;

    FriendAccess(@NonNull String langKey) {
        this.langKey = langKey;
    }

    @NonNull
    public String getDisplay(@Nullable String locale) {
        return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale, "friends.access." + this.langKey + ".name");
    }

    @NonNull
    public String getDescription(@Nullable String locale) {
        return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale, "friends.access." + this.langKey + ".lore");
    }

    @NonNull
    public FriendAccess next() {
        return switch (this) {
            case NONE -> SELECTED;
            case SELECTED -> ALL;
            case ALL -> NONE;
        };
    }

    /**
     * @param perFriendFlag персональный флаг конкретного друга (учитывается только в режиме SELECTED)
     */
    public boolean allows(boolean perFriendFlag) {
        return switch (this) {
            case NONE -> false;
            case SELECTED -> perFriendFlag;
            case ALL -> true;
        };
    }

    @NonNull
    public static FriendAccess parse(@Nullable String value, @NonNull FriendAccess fallback) {
        if (value == null) return fallback;
        try {
            return FriendAccess.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
