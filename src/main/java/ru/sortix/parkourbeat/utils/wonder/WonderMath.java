package ru.sortix.parkourbeat.utils.wonder;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Приводит формулы к тому виду, который движок реально понимает. */
public final class WonderMath {

    private static final Pattern IMPLICIT = Pattern.compile("(\\d(?:\\.\\d+)?)\\s*(?=[a-zA-Z(])");
    private static final Pattern VAR_PAREN = Pattern.compile("\\b([tuinTp])\\s*\\(");
    private static final String[] TRIG = {
        "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh", "noise", "rectx", "recty", "step4"
    };

    private WonderMath() {
    }

    @NonNull
    public static String normalizeSpec(@NonNull String spec) {
        List<String> layers = new ArrayList<>();
        for (String raw : spec.split("\\|")) {
            String layer = raw.trim();
            if (layer.isEmpty()) continue;

            int at = layer.indexOf('@');
            String geometry = (at >= 0 ? layer.substring(0, at) : layer).trim();
            String params = at >= 0 ? layer.substring(at + 1).trim() : "";

            if (!geometry.toLowerCase(Locale.ROOT).startsWith("text:")
                && !geometry.toLowerCase(Locale.ROOT).startsWith("pix:")) {
                geometry = fixMultiplication(geometry);
                params = fixRange(geometry, params);
            }
            layers.add(params.isEmpty() ? geometry : geometry + " @ " + params);
        }
        return String.join(" | ", layers);
    }

    /** "2t" и "3*sin" пишутся людьми, а движку нужна явная звёздочка. */
    @NonNull
    public static String fixMultiplication(@NonNull String geometry) {
        String result = geometry.replace("pi", "\u0001").replace("PI", "\u0001");
        Matcher matcher = IMPLICIT.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(1) + "*"));
        }
        matcher.appendTail(sb);
        result = sb.toString().replace("\u0001", "pi");
        result = VAR_PAREN.matcher(result).replaceAll("$1*(");
        return result.replace("*pi", "*pi").replace("=*", "=");
    }

    /**
     * Отрезки и ломаные пишут как x=-1+t, подразумевая t от нуля до единицы.
     * По умолчанию t идёт до 6.28, и фигура уезжает в другой конец карты.
     */
    @NonNull
    public static String fixRange(@NonNull String geometry, @NonNull String params) {
        if (params.toLowerCase(Locale.ROOT).contains("t:")) return params;
        if (!geometry.contains("t")) return params;
        String lower = geometry.toLowerCase(Locale.ROOT);
        for (String function : TRIG) if (lower.contains(function)) return params;
        return (params + " t:0..1").trim();
    }
}
