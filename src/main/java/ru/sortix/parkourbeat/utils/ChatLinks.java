package ru.sortix.parkourbeat.utils;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Built out of plain text and core component calls on purpose. The replaceText based
 * version depended on a newer adventure API than some servers ship and failed at runtime.
 */
@UtilityClass
public class ChatLinks {
    private final String KNOWN_TLDS = "com|net|org|ru|su|by|ua|kz|io|me|dev|gg|tv|cc|co|xyz|info|biz|pro|club|"
        + "site|online|shop|store|app|link|space|fun|live|team|wiki|blog|news|art|top|edu|gov";

    /**
     * Every quantifier is bounded: an unbounded nested group turns into exponential
     * backtracking on long words and would freeze the async chat thread.
     */
    private final Pattern URL_PATTERN = Pattern.compile(
        "(?:https?://|www\\.)[^\\s]{1,400}"
            + "|[a-z0-9][a-z0-9\\-]{0,62}(?:\\.[a-z0-9][a-z0-9\\-]{0,62}){0,3}"
            + "\\.(?:" + KNOWN_TLDS + ")(?![a-z])(?::\\d{1,5})?(?:/[^\\s]{0,400})?",
        Pattern.CASE_INSENSITIVE);

    private final String TRAILING_CHARS = ".,;:!?)]}\"'";

    /**
     * Returns the original component untouched when there is nothing to linkify,
     * and never throws: chat delivery must not depend on this succeeding.
     */
    @NonNull
    public Component makeLinksClickable(@NonNull Component message) {
        try {
            String plain = PlainComponentSerializer.plain().serialize(message);
            if (plain.isEmpty()) return message;

            Matcher matcher = URL_PATTERN.matcher(plain);
            if (!matcher.find()) return message;

            Component result = Component.empty();
            int cursor = 0;

            do {
                int start = matcher.start();
                int end = matcher.end();

                String matched = plain.substring(start, end);
                while (!matched.isEmpty() && TRAILING_CHARS.indexOf(matched.charAt(matched.length() - 1)) >= 0) {
                    matched = matched.substring(0, matched.length() - 1);
                    end--;
                }
                if (matched.isEmpty()) continue;

                if (start > cursor) {
                    result = result.append(Component.text(plain.substring(cursor, start)));
                }
                result = result.append(buildLink(matched));
                cursor = end;
            } while (matcher.find());

            if (cursor < plain.length()) {
                result = result.append(Component.text(plain.substring(cursor)));
            }
            return result;
        } catch (Throwable t) {
            return message;
        }
    }

    @NonNull
    private Component buildLink(@NonNull String matched) {
        String lowered = matched.toLowerCase(Locale.ROOT);
        String url = lowered.startsWith("http://") || lowered.startsWith("https://")
            ? matched
            : "https://" + matched;

        return Component.text(matched)
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.UNDERLINED, true)
            .clickEvent(ClickEvent.openUrl(url))
            .hoverEvent(HoverEvent.showText(Component.text(url, NamedTextColor.GRAY)));
    }
}
