// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/utils/TimeUtils.java
package ru.sortix.parkourbeat.utils;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.util.Locale;

@UtilityClass
public class TimeUtils {
    public final int MAX_TIMECODE_MILLIS = (60 * 60 * 1000) - 1;

    // StackWalker работает невероятно быстро и позволяет "подсмотреть" кто вызвал метод
    private final StackWalker WALKER = StackWalker.getInstance();

    @NonNull
    public String formatTimecode(long millis) {
        if (millis < 0) millis = 0;
        long totalSeconds = millis / 1000L;
        long remMillis = millis % 1000L;

        if (remMillis > 0) {
            // Проверяем стек вызовов: если нас просят отформатировать время менюшки
            // из пакета редактора (inventory.type.editor.*) или настроек (levels.settings.*),
            // то возвращаем точное значение с миллисекундами.
            boolean isEditor = WALKER.walk(stream -> stream.limit(8).anyMatch(f -> {
                String className = f.getClassName();
                return className.contains(".settings.") || className.contains(".editor.");
            }));

            if (isEditor) {
                return String.format(Locale.ROOT, "%02d:%02d.%03d", totalSeconds / 60L, totalSeconds % 60L, remMillis);
            }
        }

        // Если это скорборд, топы, конец игры или чат — возвращаем красиво и без миллисекунд
        return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

    @NonNull
    public String formatSeconds(long millis) {
        if (millis <= 0) return "0";
        return String.format(Locale.ROOT, "%.2f", millis / 1000.0D);
    }

    /**
     * Accepts "73", "73.5", "1:13", "01:13.250"
     *
     * @return milliseconds or -1 if the input cannot be parsed
     */
    public int parseTimecode(@NonNull String input) {
        String value = input.trim().replace(',', '.');
        if (value.isEmpty()) return -1;

        String[] parts = value.split(":");
        if (parts.length < 1 || parts.length > 3) return -1;

        double total = 0.0D;
        for (String part : parts) {
            if (part.isEmpty()) return -1;
            double parsed;
            try {
                parsed = Double.parseDouble(part);
            } catch (NumberFormatException e) {
                return -1;
            }
            if (parsed < 0.0D) return -1;
            total = (total * 60.0D) + parsed;
        }

        long millis = Math.round(total * 1000.0D);
        if (millis < 0 || millis > MAX_TIMECODE_MILLIS) return -1;
        return (int) millis;
    }

    public int parseMillis(@NonNull String input) {
        String value = input.trim().replace(',', '.');
        if (value.isEmpty()) return -1;
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return -1;
        }
        if (parsed < 0.0D) return -1;
        long millis = Math.round(parsed);
        if (millis > MAX_TIMECODE_MILLIS) return -1;
        return (int) millis;
    }
}
