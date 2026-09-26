package ru.sortix.parkourbeat.utils.text;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Разбор оформленных строк плагина.
 * <p>
 * Полностью совместим со старым синтаксисом: любая строка с {@code &amp;} или {@code §}
 * читается как раньше, только цвета берутся из {@link Theme}, а не ванильные.
 * Поверх этого понимает три вещи:
 * <ul>
 *   <li>{@code &amp;#RRGGBB} - произвольный цвет;
 *   <li>{@code <g:название>текст</g>} - именованный градиент из {@link Theme};
 *   <li>{@code <g:#RRGGBB:#RRGGBB:...>текст</g>} - градиент по своим точкам.
 * </ul>
 * Градиент раскрашивает видимые символы, поэтому пробелы внутри него тоже считаются:
 * иначе на строке из нескольких слов переходы получались бы рваными.
 * <p>
 * Курсив в предметах инвентаря гасится сразу здесь: Minecraft включает его сам,
 * и без этого каждое меню пришлось бы чистить вручную.
 */
public final class PbText {
    private static final char AMP = '&';
    private static final char SECTION = '\u00A7';

    private PbText() {
    }

    /**
     * Разбирает строку для чата, заголовков и прочего текста вне инвентаря.
     */
    @NonNull
    public static Component of(@Nullable String input) {
        return parse(input, false);
    }

    /**
     * То же самое, но для названий и описаний предметов: гасит ванильный курсив.
     */
    @NonNull
    public static Component item(@Nullable String input) {
        return parse(input, true);
    }

    /**
     * Оборачивает чужой текст тегом, который отключает палитру внутри.
     * <p>
     * Нужен там, где название подставляется в готовую строку интерфейса: сама
     * строка остаётся тематической, а название - авторским.
     */
    @NonNull
    public static String keepColors(@Nullable String input) {
        if (input == null || input.isEmpty()) return "";
        return "<v>" + input + "</v>";
    }

    /**
     * Чужой текст: названия уровней и треков, ники. Цветовые коды в нём означают
     * ровно то, что имел в виду автор, поэтому палитра плагина не применяется.
     */
    @NonNull
    public static Component vanilla(@Nullable String input) {
        return parse(input, false, true);
    }

    @NonNull
    private static Component parse(@Nullable String input, boolean forItem) {
        return parse(input, forItem, false);
    }

    @NonNull
    private static Component parse(@Nullable String input, boolean forItem, boolean vanillaColors) {
        if (input == null || input.isEmpty()) return Component.empty();

        List<Component> parts = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        TextColor color = null;
        Set<TextDecoration> decorations = EnumSet.noneOf(TextDecoration.class);

        int i = 0;
        int length = input.length();

        while (i < length) {
            char current = input.charAt(i);

            // <v>текст</v> - чужой текст: цвета в нём ванильные, палитра не применяется
            if (current == '<' && input.startsWith("<v>", i)) {
                int closeIndex = input.indexOf("</v>", i);
                if (closeIndex > 0) {
                    flush(parts, buffer, color, decorations);
                    parts.add(parse(input.substring(i + 3, closeIndex), false, true));
                    i = closeIndex + 4;
                    continue;
                }
            }

            // <g:...>текст</g>
            if (current == '<' && input.startsWith("<g:", i)) {
                int tagEnd = input.indexOf('>', i);
                int closeIndex = tagEnd < 0 ? -1 : input.indexOf("</g>", tagEnd);
                if (tagEnd > 0 && closeIndex > 0) {
                    TextColor[] stops = parseStops(input.substring(i + 3, tagEnd));
                    if (stops != null && stops.length >= 2) {
                        flush(parts, buffer, color, decorations);
                        String inner = input.substring(tagEnd + 1, closeIndex);
                        parts.add(gradient(inner, stops, decorations));
                        i = closeIndex + 4;
                        continue;
                    }
                }
            }

            if ((current == AMP || current == SECTION) && i + 1 < length) {
                char next = input.charAt(i + 1);

                // &#RRGGBB
                if (next == '#' && i + 8 <= length - 1 + 1) {
                    TextColor hex = parseHex(input, i + 2);
                    if (hex != null) {
                        flush(parts, buffer, color, decorations);
                        color = hex;
                        decorations.clear();
                        i += 8;
                        continue;
                    }
                }

                TextColor named = vanillaColors
                    ? Theme.vanillaByLegacyCode(next)
                    : Theme.byLegacyCode(next);
                if (named != null) {
                    flush(parts, buffer, color, decorations);
                    color = named;
                    // Ванильное правило: цвет сбрасывает оформление.
                    decorations.clear();
                    i += 2;
                    continue;
                }

                TextDecoration decoration = decorationByCode(next);
                if (decoration != null) {
                    flush(parts, buffer, color, decorations);
                    decorations.add(decoration);
                    i += 2;
                    continue;
                }

                if (Character.toLowerCase(next) == 'r') {
                    flush(parts, buffer, color, decorations);
                    color = null;
                    decorations.clear();
                    i += 2;
                    continue;
                }
            }

            buffer.append(current);
            i++;
        }

        flush(parts, buffer, color, decorations);

        Component result = parts.isEmpty()
            ? Component.empty()
            : Component.text().append(parts).build();

        return forItem ? result.decoration(TextDecoration.ITALIC, false) : result;
    }

    private static void flush(@NonNull List<Component> parts,
                              @NonNull StringBuilder buffer,
                              @Nullable TextColor color,
                              @NonNull Set<TextDecoration> decorations
    ) {
        if (buffer.length() == 0) return;

        TextComponent.Builder builder = Component.text().content(buffer.toString());
        if (color != null) builder.color(color);
        for (TextDecoration decoration : decorations) {
            builder.decorate(decoration);
        }
        parts.add(builder.build());
        buffer.setLength(0);
    }

    /**
     * Красит текст переходом по точкам. Каждый символ получает свой оттенок,
     * поэтому длина строки на плавность не влияет.
     */
    @NonNull
    private static Component gradient(@NonNull String text,
                                      @NonNull TextColor[] stops,
                                      @NonNull Set<TextDecoration> decorations
    ) {
        // Внутри градиента цветовые коды не имеют смысла, а вот сам текст должен
        // остаться читаемым, поэтому коды просто вырезаются.
        String plain = stripCodes(text);
        if (plain.isEmpty()) return Component.empty();

        int length = plain.codePointCount(0, plain.length());
        TextComponent.Builder builder = Component.text();

        int index = 0;
        int offset = 0;
        while (offset < plain.length()) {
            int codePoint = plain.codePointAt(offset);
            int charCount = Character.charCount(codePoint);

            double position = length <= 1 ? 0.0D : (double) index / (double) (length - 1);
            TextComponent.Builder part = Component.text()
                .content(new String(Character.toChars(codePoint)))
                .color(colorAt(stops, position));
            for (TextDecoration decoration : decorations) {
                part.decorate(decoration);
            }
            builder.append(part.build());

            offset += charCount;
            index++;
        }
        return builder.build();
    }

    @NonNull
    private static TextColor colorAt(@NonNull TextColor[] stops, double position) {
        if (stops.length == 1) return stops[0];

        double scaled = position * (stops.length - 1);
        int segment = (int) Math.floor(scaled);
        if (segment >= stops.length - 1) return stops[stops.length - 1];
        if (segment < 0) return stops[0];

        return lerp(scaled - segment, stops[segment], stops[segment + 1]);
    }

    /**
     * Смешивание двух цветов вручную.
     * <p>
     * В Adventure для этого есть TextColor.lerp, но он появился не сразу, и на
     * версии библиотеки, с которой собирается плагин, его ещё нет. Считать три
     * канала дешевле, чем привязываться к версии.
     */
    @NonNull
    private static TextColor lerp(double t, @NonNull TextColor from, @NonNull TextColor to) {
        if (t <= 0.0D) return from;
        if (t >= 1.0D) return to;

        int red = (int) Math.round(from.red() + (to.red() - from.red()) * t);
        int green = (int) Math.round(from.green() + (to.green() - from.green()) * t);
        int blue = (int) Math.round(from.blue() + (to.blue() - from.blue()) * t);
        return TextColor.color(clampChannel(red), clampChannel(green), clampChannel(blue));
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @Nullable
    private static TextColor[] parseStops(@NonNull String definition) {
        if (definition.isEmpty()) return null;

        if (!definition.contains("#")) {
            return Theme.gradientByName(definition);
        }

        String[] raw = definition.split(":");
        List<TextColor> stops = new ArrayList<>(raw.length);
        for (String piece : raw) {
            piece = piece.trim();
            if (piece.isEmpty()) continue;
            if (piece.charAt(0) != '#') {
                TextColor[] named = Theme.gradientByName(piece);
                if (named == null) return null;
                for (TextColor color : named) stops.add(color);
                continue;
            }
            TextColor color = parseHex(piece, 1);
            if (color == null) return null;
            stops.add(color);
        }
        return stops.size() < 2 ? null : stops.toArray(new TextColor[0]);
    }

    /**
     * @param start индекс первого из шести символов цвета
     */
    @Nullable
    private static TextColor parseHex(@NonNull String input, int start) {
        if (start + 6 > input.length()) return null;
        int value = 0;
        for (int i = start; i < start + 6; i++) {
            int digit = Character.digit(input.charAt(i), 16);
            if (digit < 0) return null;
            value = (value << 4) | digit;
        }
        return TextColor.color(value);
    }

    @Nullable
    private static TextDecoration decorationByCode(char code) {
        switch (Character.toLowerCase(code)) {
            case 'k': return TextDecoration.OBFUSCATED;
            case 'l': return TextDecoration.BOLD;
            case 'm': return TextDecoration.STRIKETHROUGH;
            case 'n': return TextDecoration.UNDERLINED;
            case 'o': return TextDecoration.ITALIC;
            default: return null;
        }
    }

    @NonNull
    private static String stripCodes(@NonNull String input) {
        input = input.replace("<v>", "").replace("</v>", "");
        StringBuilder result = new StringBuilder(input.length());
        int i = 0;
        while (i < input.length()) {
            char current = input.charAt(i);
            if ((current == AMP || current == SECTION) && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                if (next == '#' && parseHex(input, i + 2) != null) {
                    i += 8;
                    continue;
                }
                if (Theme.byLegacyCode(next) != null
                    || decorationByCode(next) != null
                    || Character.toLowerCase(next) == 'r') {
                    i += 2;
                    continue;
                }
            }
            result.append(current);
            i++;
        }
        return result.toString();
    }
}
