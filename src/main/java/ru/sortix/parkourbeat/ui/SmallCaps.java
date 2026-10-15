package ru.sortix.parkourbeat.ui;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Минималистичный шрифт сервера: буквы заменяются обычными Unicode-символами
 * капителей. Это просто текст - ресурспак здесь не участвует.
 * <p>
 * Английский: ᴀ ʙ ᴄ ᴅ ᴇ ꜰ ɢ ʜ ɪ ᴊ ᴋ ʟ ᴍ ɴ ᴏ ᴘ ꞯ ʀ s ᴛ ᴜ ᴠ ᴡ x ʏ ᴢ
 * <br>Русский: ᴀ б ʙ г д ᴇ ё ж з и й ᴋ л ᴍ ʜ ᴏ п ᴘ ᴄ ᴛ у ф x ц ч ш щ ъ ы ь э ю я
 * <p>
 * Регистр не важен: заглавные идут в те же символы. Цифры и знаки не трогаются.
 * Строки в исходнике записаны через \\u, чтобы не зависеть от кодировки сборки.
 */
public final class SmallCaps {
    private SmallCaps() {
    }

    /** Включает автоматическую замену в табло и табе. */
    public static volatile boolean AUTO = true;

    private static final String EN_FROM = "abcdefghijklmnopqrstuvwxyz";
    // ᴀ ʙ ᴄ ᴅ ᴇ ꜰ ɢ ʜ ɪ ᴊ ᴋ ʟ ᴍ ɴ ᴏ ᴘ ꞯ ʀ s ᴛ ᴜ ᴠ ᴡ x ʏ ᴢ
    private static final String EN_TO =
        "\u1D00\u0299\u1D04\u1D05\u1D07\uA730\u0262\u029C\u026A\u1D0A\u1D0B\u029F\u1D0D"
            + "\u0274\u1D0F\u1D18\uA7AF\u0280s\u1D1B\u1D1C\u1D20\u1D21x\u028F\u1D22";

    // а б в г д е ё ж з и й к л м н о п р с т у ф х ц ч ш щ ъ ы ь э ю я
    private static final String RU_FROM =
        "\u0430\u0431\u0432\u0433\u0434\u0435\u0451\u0436\u0437\u0438\u0439\u043A\u043B\u043C\u043D\u043E\u043F"
            + "\u0440\u0441\u0442\u0443\u0444\u0445\u0446\u0447\u0448\u0449\u044A\u044B\u044C\u044D\u044E\u044F";
    // ᴀ б ʙ г д ᴇ ё ж з и й ᴋ л ᴍ ʜ ᴏ п ᴘ ᴄ ᴛ у ф x ц ч ш щ ъ ы ь э ю я
    private static final String RU_TO =
        "\u1D00\u0431\u0299\u0433\u0434\u1D07\u0451\u0436\u0437\u0438\u0439\u1D0B\u043B\u1D0D\u029C\u1D0F\u043F"
            + "\u1D18\u1D04\u1D1B\u0443\u0444x\u0446\u0447\u0448\u0449\u044A\u044B\u044C\u044D\u044E\u044F";

    static {
        if (EN_FROM.length() != EN_TO.length() || RU_FROM.length() != RU_TO.length()) {
            throw new IllegalStateException("SmallCaps: таблицы разной длины");
        }
    }

    /** Свои цифры капителями из РП (E700..E709) - в одну высоту с буквами. */
    public static volatile boolean DIGITS = true;

    public static char map(char c) {
        if (DIGITS && c >= '0' && c <= '9') return (char) (0xE700 + (c - '0'));
        char lower = Character.toLowerCase(c);
        int i = EN_FROM.indexOf(lower);
        if (i >= 0) return EN_TO.charAt(i);
        i = RU_FROM.indexOf(lower);
        if (i >= 0) return RU_TO.charAt(i);
        if (lower == '\u0456') return '\u026A'; // укр. і -> ɪ
        if (lower == '\u0457' || lower == '\u0454' || lower == '\u0491') return lower; // ї є ґ
        return c;
    }

    /** Весь текст подряд, без исключений. Для уже готовых строк без кодов. */
    @NonNull
    public static String convert(@Nullable String plain) {
        if (plain == null || plain.isEmpty()) return "";
        StringBuilder out = new StringBuilder(plain.length());
        for (int i = 0; i < plain.length(); i++) out.append(map(plain.charAt(i)));
        return out.toString();
    }

    /**
     * Строка-шаблон из lang.yml. Не трогает:
     * <ul>
     *   <li>коды цвета {@code &a}, {@code §a}, {@code &#RRGGBB};</li>
     *   <li>теги {@code <...>} и содержимое {@code <v>...</v>} (чужой текст: ники, названия);</li>
     *   <li>плейсхолдеры {@code %name%} - то, что в них подставится, останется как есть;</li>
     *   <li>JSON-строки целиком (начинаются с { или [).</li>
     * </ul>
     */
    @NonNull
    public static String convertTemplate(@Nullable String input) {
        if (input == null || input.isEmpty()) return input == null ? "" : input;
        char first = input.charAt(0);
        if (first == '{' || first == '[') return input;

        StringBuilder out = new StringBuilder(input.length());
        int i = 0;
        int n = input.length();
        while (i < n) {
            char c = input.charAt(i);

            if ((c == '&' || c == '\u00A7') && i + 1 < n) {
                if (input.charAt(i + 1) == '#' && i + 8 <= n) {
                    out.append(input, i, i + 8);
                    i += 8;
                } else {
                    out.append(c).append(input.charAt(i + 1));
                    i += 2;
                }
                continue;
            }

            if (c == '<') {
                if (input.startsWith("<v>", i)) {
                    int close = input.indexOf("</v>", i);
                    if (close > 0) {
                        out.append(input, i, close + 4);
                        i = close + 4;
                        continue;
                    }
                }
                int end = input.indexOf('>', i);
                if (end > 0 && end - i < 64) {
                    out.append(input, i, end + 1);
                    i = end + 1;
                    continue;
                }
            }

            if (c == '%') {
                int end = input.indexOf('%', i + 1);
                if (end > i + 1 && end - i < 40 && input.substring(i + 1, end).indexOf(' ') < 0) {
                    out.append(input, i, end + 1);
                    i = end + 1;
                    continue;
                }
            }

            out.append(map(c));
            i++;
        }
        return out.toString();
    }

    /** Тот же перевод для уже собранного компонента (весь текст внутри). */
    @NonNull
    public static Component apply(@NonNull Component component) {
        Component result = component;
        if (component instanceof TextComponent) {
            result = ((TextComponent) component).content(convert(((TextComponent) component).content()));
        }
        List<Component> children = component.children();
        if (!children.isEmpty()) {
            List<Component> mapped = new ArrayList<>(children.size());
            for (Component child : children) mapped.add(apply(child));
            result = result.children(mapped);
        }
        return result;
    }

    /** Ключи, которые переводятся автоматически: табло, таб, «Прогресс», предметы лобби. */
    public static boolean isAutoKey(@NonNull String key) {
        return AUTO && (key.startsWith("scoreboard_") || key.startsWith("scoreboard.")
            || key.startsWith("tablist_") || key.startsWith("tablist.")
            || key.equals("level_play_progress") || key.startsWith("item.lobby.")
            || key.startsWith("game.subtitle.") || key.startsWith("game.summary."));
    }

    private static final java.util.regex.Pattern NUMERIC = java.util.regex.Pattern.compile(
        "^[#\\d\\s.,:%x\u00D7/+\\-]*\\d[#\\d\\s.,:%x\u00D7/+\\-]*(\\s?(\u043C\u0441|ms|\u0441|s|%))?$");

    /**
     * Значение плейсхолдера: числа («63 мс», «94.72%», «x299») переводятся, всё
     * остальное (ники, названия) остаётся как есть.
     */
    @NonNull
    public static String placeholder(@Nullable String value) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;
        String plain = value.replaceAll("(?i)[&\u00A7](#[0-9a-f]{6}|[0-9a-fk-or])", "").trim();
        return NUMERIC.matcher(plain).matches() ? convertTemplate(value) : value;
    }
}
