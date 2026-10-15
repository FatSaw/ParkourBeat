package ru.sortix.parkourbeat.boards;

import lombok.Getter;
import lombok.NonNull;

import javax.annotation.Nullable;
import java.util.Locale;

@Getter
public enum BoardType {
    LEVELS("Уровни", "levels"),
    TOP("Топ игроков", "top"),
    LOGO("Логотип", "logo");

    private final @NonNull String display;
    private final @NonNull String key;

    BoardType(@NonNull String display, @NonNull String key) {
        this.display = display;
        this.key = key;
    }

    @Nullable
    public static BoardType byKey(@Nullable String raw) {
        if (raw == null) return null;
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        for (BoardType type : values()) {
            if (type.key.equals(lower) || type.name().toLowerCase(Locale.ROOT).equals(lower)) return type;
        }
        return null;
    }

    @NonNull
    public static String keys() {
        StringBuilder sb = new StringBuilder();
        for (BoardType type : values()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(type.key);
        }
        return sb.toString();
    }
}
