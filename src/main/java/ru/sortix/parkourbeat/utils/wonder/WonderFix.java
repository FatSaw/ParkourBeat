package ru.sortix.parkourbeat.utils.wonder;

import lombok.NonNull;
import ru.sortix.parkourbeat.levels.wonder.WonderPresets;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class WonderFix {

    private static final Set<String> PARAM_KEYS = new HashSet<>(Arrays.asList(
        "steps", "usteps", "mode", "radius", "sides", "t", "u", "particle", "psize", "refresh",
        "motion", "mspeed", "vx", "vy", "vz", "trail", "tgap", "jitter", "chance",
        "ox", "oy", "oz", "zoom", "rotx", "roty", "rotz", "px", "lgap", "align", "outline",
        "burst", "drift", "driftt", "thick", "from",
        "in", "out", "int", "outt", "flyd", "face", "spin", "cull", "view"
    ));

    private static final Set<String> PLACEMENT_KEYS = new HashSet<>(Arrays.asList(
        "anchor", "dist", "height", "side", "scale", "color", "start", "end",
        "approach", "turn", "font", "words"
    ));

    private static final Pattern GLUED = Pattern.compile("\\*(?=[A-Za-z_][A-Za-z_0-9]*:)");

    private static final String PREFIX = "/pbllmeffects";

    private WonderFix() {
    }

    @Nullable
    public static String repair(@NonNull String rawLine) {
        String line = rawLine.trim();
        String prefix = "";
        if (line.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
            prefix = PREFIX + " ";
            line = line.substring(PREFIX.length()).trim();
        }
        if (line.isEmpty()) return null;

        String[] args = tidy(line).split("\\s+");
        String body;
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add": body = add(args); break;
            case "preset": body = preset(args); break;
            case "text": body = args.length < 4 ? null : join("text", args, 1); break;
            case "edit": body = args.length < 3 ? null : join("edit", args, 1); break;
            case "del": case "delete": case "remove":
                body = args.length < 2 ? null : "del " + args[1].replace("#", "");
                break;
            case "clear": body = "clear"; break;
            case "list": body = "list"; break;
            default: return null;
        }
        return body == null ? null : prefix + body;
    }

    @Nullable
    private static String add(@NonNull String[] args) {
        if (args.length < 4) return null;
        String tail = join("", args, 3).trim();
        if (parses(tail)) return trim("add " + args[1] + " " + args[2] + " " + tail);

        List<String> geometry = new ArrayList<>();
        List<String> params = new ArrayList<>();
        List<String> placement = new ArrayList<>();
        List<String> junk = new ArrayList<>();

        boolean afterAt = false;
        for (int i = 3; i < args.length; i++) {
            String token = args[i];
            if (token.startsWith("@")) {
                afterAt = true;
                token = token.substring(1);
                if (token.isEmpty()) continue;
            }
            sort(token, afterAt, geometry, params, placement, junk);
        }

        List<String> clean = new ArrayList<>();
        for (String piece : String.join(";", geometry).split(";")) {
            String part = piece.trim();
            if (part.isEmpty()) continue;
            if (part.contains("=") || isShape(part)) clean.add(part);
            else sort(part, true, clean, params, placement, junk);
        }

        if (clean.isEmpty()) {
            String preset = preset(junk);
            if (preset == null) return null;
            return trim("preset " + args[1] + " " + args[2] + " " + preset + " " + String.join(" ", placement));
        }

        String spec = String.join(";", clean);
        if (!params.isEmpty()) spec += " @ " + String.join(" ", params);
        return trim("add " + args[1] + " " + args[2] + " " + spec + " " + String.join(" ", placement));
    }

    @Nullable
    private static String preset(@NonNull String[] args) {
        if (args.length < 4) return null;

        List<String> keys = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            String token = args[i];
            if (key(token) != null && !token.toLowerCase(Locale.ROOT).startsWith("preset:")) keys.add(token);
            else names.add(token);
        }

        String preset = preset(names);
        if (preset == null) return null;
        return trim("preset " + args[1] + " " + args[2] + " " + preset + " " + String.join(" ", keys));
    }

    private static boolean parses(@NonNull String spec) {
        if (spec.isEmpty()) return false;
        for (String layer : spec.split("\\|")) {
            int at = layer.indexOf('@');
            String geometry = (at >= 0 ? layer.substring(0, at) : layer).trim();
            if (geometry.isEmpty()) return false;

            for (String token : geometry.split("\\s+")) {
                String key = key(token);
                if (key != null && (PARAM_KEYS.contains(key) || PLACEMENT_KEYS.contains(key))) return false;
            }
            if (isShape(geometry)) continue;
            for (String part : geometry.split(";")) {
                if (part.trim().isEmpty()) continue;
                if (!part.contains("=")) return false;
            }
        }
        return true;
    }

    private static void sort(@NonNull String token,
                             boolean afterAt,
                             @NonNull List<String> geometry,
                             @NonNull List<String> params,
                             @NonNull List<String> placement,
                             @NonNull List<String> junk
    ) {
        String key = key(token);
        if (key != null) {
            if (PLACEMENT_KEYS.contains(key)) {
                placement.add(token);
                return;
            }
            if (PARAM_KEYS.contains(key)) {
                params.add(token);
                return;
            }
        }
        if (token.contains("=") || isShape(token)) geometry.add(token);
        else if (afterAt) params.add(token);
        else junk.add(token);
    }

    @Nullable
    private static String key(@NonNull String token) {
        int colon = token.indexOf(':');
        if (colon <= 0 || colon == token.length() - 1) return null;
        if (token.indexOf('=') >= 0) return null;
        String name = token.substring(0, colon);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return null;
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private static boolean isShape(@NonNull String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return lower.startsWith("text:") || lower.startsWith("pix:");
    }

    @Nullable
    private static String preset(@NonNull List<String> tokens) {
        for (String raw : tokens) {
            String token = raw.trim().toLowerCase(Locale.ROOT);
            if (token.startsWith("preset:")) token = token.substring(7);
            if (!token.isEmpty() && WonderPresets.byId(token) != null) return token;
        }
        return null;
    }

    @NonNull
    private static String tidy(@NonNull String line) {
        String result = GLUED.matcher(line).replaceAll(" ");
        result = result.replaceAll("\\s*;\\s*", ";");
        result = result.replaceAll("\\s*=\\s*", "=");
        result = result.replaceAll("\\s*@\\s*", " @ ");
        return result.trim().replaceAll("\\s+", " ");
    }

    @NonNull
    private static String join(@NonNull String action, @NonNull String[] args, int from) {
        StringBuilder sb = new StringBuilder(action);
        for (int i = from; i < args.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    @NonNull
    private static String trim(@NonNull String line) {
        return line.trim().replaceAll("\\s+", " ");
    }
}
