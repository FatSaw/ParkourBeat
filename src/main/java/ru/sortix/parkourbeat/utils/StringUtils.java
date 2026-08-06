package ru.sortix.parkourbeat.utils;

import lombok.NonNull;

import javax.annotation.Nullable;
import java.util.UUID;

public class StringUtils {
    private static final int UUID_LENGTH = "00000000-0000-0000-0000-000000000000".length();

    @Nullable
    public static UUID parseUUID(@NonNull String string) {
        if (string.length() != UUID_LENGTH || string.split("-").length != 5) {
            return null;
        }
        try {
            return UUID.fromString(string);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    public static boolean containsCustomFont(@NonNull String text) {
        for (char c : text.toCharArray()) {
            // Диапазон Private Use Area, где хранятся все иконки и шрифты!!!!
            if (c >= '\uE000' && c <= '\uF8FF') {
                return true;
            }
        }
        return false;
    }
}
