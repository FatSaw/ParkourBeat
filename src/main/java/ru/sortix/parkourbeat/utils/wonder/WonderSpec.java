package ru.sortix.parkourbeat.utils.wonder;

import lombok.NonNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Правка описания эффекта по кусочкам.
 * <p>
 * Строка вида "text:СЛОВА @ px:0.28 font:pixel | x=... @ steps:200" редактируется точечно:
 * поменять шрифт, частицу, число шагов, слова. Благодаря этому меню, ИИ и команды меняют
 * одно и то же представление, и ни одна ветка не строит свой формат поверх чужого.
 */
public final class WonderSpec {

    private WonderSpec() {
    }

    /** Значение параметра первого слоя, где он встретится. */
    @Nullable
    public static String get(@NonNull String spec, @NonNull String key) {
        for (String layer : spec.split("\\|")) {
            int at = layer.indexOf('@');
            if (at < 0) continue;
            for (String token : layer.substring(at + 1).trim().split("\\s+")) {
                int colon = token.indexOf(':');
                if (colon > 0 && token.substring(0, colon).equalsIgnoreCase(key)) {
                    return token.substring(colon + 1);
                }
            }
        }
        return null;
    }

    /** Проставить параметр во все слои. value = null убирает его. */
    @NonNull
    public static String set(@NonNull String spec, @NonNull String key, @Nullable String value) {
        List<String> layers = new ArrayList<>();
        for (String raw : spec.split("\\|")) {
            String layer = raw.trim();
            if (layer.isEmpty()) continue;

            int at = layer.indexOf('@');
            String geometry = (at >= 0 ? layer.substring(0, at) : layer).trim();
            String params = at >= 0 ? layer.substring(at + 1).trim() : "";

            StringBuilder rebuilt = new StringBuilder();
            boolean replaced = false;
            for (String token : params.isEmpty() ? new String[0] : params.split("\\s+")) {
                int colon = token.indexOf(':');
                if (colon > 0 && token.substring(0, colon).equalsIgnoreCase(key)) {
                    replaced = true;
                    if (value == null) continue;
                    token = key + ":" + value;
                }
                if (rebuilt.length() > 0) rebuilt.append(' ');
                rebuilt.append(token);
            }
            if (!replaced && value != null) {
                if (rebuilt.length() > 0) rebuilt.append(' ');
                rebuilt.append(key).append(':').append(value);
            }

            layers.add(rebuilt.length() == 0 ? geometry : geometry + " @ " + rebuilt);
        }
        return String.join(" | ", layers);
    }

    public static boolean isText(@NonNull String spec) {
        return spec.trim().toLowerCase(Locale.ROOT).startsWith("text:");
    }

    /** Слова текстового слоя, уже с пробелами вместо подчёркиваний. */
    @Nullable
    public static String words(@NonNull String spec) {
        String first = spec.split("\\|")[0].trim();
        if (!first.toLowerCase(Locale.ROOT).startsWith("text:")) return null;
        String body = first.substring(5);
        int at = body.indexOf('@');
        if (at >= 0) body = body.substring(0, at);
        return body.trim().replace('_', ' ');
    }

    @NonNull
    public static String withWords(@NonNull String spec, @NonNull String words) {
        String[] layers = spec.split("\\|");
        String first = layers[0].trim();
        if (!first.toLowerCase(Locale.ROOT).startsWith("text:")) return spec;

        int at = first.indexOf('@');
        String params = at >= 0 ? first.substring(at) : "";
        layers[0] = "text:" + words.trim().replace(' ', '_') + (params.isEmpty() ? "" : " " + params.trim());

        StringBuilder sb = new StringBuilder();
        for (String layer : layers) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(layer.trim());
        }
        return sb.toString();
    }

    /** Максимальный refresh по всем слоям: по нему видно, как быстро эффект «зажигается». */
    public static int maxRefresh(@NonNull String spec) {
        int max = 0;
        for (String layer : spec.split("\\|")) {
            int at = layer.indexOf('@');
            if (at < 0) continue;
            for (String token : layer.substring(at + 1).trim().split("\\s+")) {
                if (!token.toLowerCase(Locale.ROOT).startsWith("refresh:")) continue;
                try {
                    max = Math.max(max, Integer.parseInt(token.substring(8).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max;
    }

    /**
     * Ограничить refresh сверху.
     * <p>
     * Точки эффекта раскидываются по тикам ровно на refresh кадров: при refresh 15 фигура
     * набирается три четверти секунды, и короткая вспышка успевает кончиться, не собравшись.
     * Для всего, что живёт меньше полутора секунд, потолок опускается принудительно.
     */
    @NonNull
    public static String capRefresh(@NonNull String spec, int max) {
        List<String> layers = new ArrayList<>();
        for (String raw : spec.split("\\|")) {
            String layer = raw.trim();
            if (layer.isEmpty()) continue;

            int at = layer.indexOf('@');
            String geometry = (at >= 0 ? layer.substring(0, at) : layer).trim();
            String params = at >= 0 ? layer.substring(at + 1).trim() : "";

            StringBuilder rebuilt = new StringBuilder();
            boolean seen = false;
            for (String token : params.isEmpty() ? new String[0] : params.split("\\s+")) {
                if (token.toLowerCase(Locale.ROOT).startsWith("refresh:")) {
                    seen = true;
                    int current;
                    try {
                        current = Integer.parseInt(token.substring(8).trim());
                    } catch (NumberFormatException e) {
                        current = max;
                    }
                    token = "refresh:" + Math.min(current, max);
                }
                if (rebuilt.length() > 0) rebuilt.append(' ');
                rebuilt.append(token);
            }
            if (!seen) {
                if (rebuilt.length() > 0) rebuilt.append(' ');
                rebuilt.append("refresh:").append(max);
            }
            layers.add(rebuilt.length() == 0 ? geometry : geometry + " @ " + rebuilt);
        }
        return String.join(" | ", layers);
    }

    /** Сколько слоёв рисуют dust: он дороже end_rod и по трафику, и по кадрам. */
    public static boolean usesDust(@NonNull String spec) {
        return spec.toLowerCase(Locale.ROOT).contains("particle:dust");
    }
}
