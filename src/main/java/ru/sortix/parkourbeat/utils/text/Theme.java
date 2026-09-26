package ru.sortix.parkourbeat.utils.text;

import lombok.NonNull;
import net.kyori.adventure.text.format.TextColor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Единая палитра плагина.
 * <p>
 * Здесь два разных механизма, и их важно не путать.
 * <p>
 * ПЕРВЫЙ - переназначение старых цветовых кодов. Во всём плагине уже написаны тысячи
 * строк вида "&amp;eТекст", и переписывать их руками смысла нет: вместо этого каждый
 * из шестнадцати кодов получает здесь свой RGB-оттенок. Меняете одну строку в этой
 * таблице - меняется вид всего плагина сразу, и ни одна строка текста не трогается.
 * Поэтому же оформление остаётся согласованным: два разных меню физически не могут
 * разъехаться по оттенкам.
 * <p>
 * ВТОРОЙ - именованные градиенты для заголовков и важных строк. Их немного и они
 * ставятся руками там, где нужен акцент, потому что градиент на каждой строке
 * перестаёт быть акцентом.
 */
public final class Theme {
    private Theme() {
    }

    // ==================== ПАЛИТРА ====================
    // Стиль ParkourBeat: пурпурный, белый, чёрный - примерно в таком соотношении.
    // Поэтому два самых ходовых кода уводят именно туда: &6 (названия предметов)
    // стал пурпурным вместо золотого, а &e (описания) - почти белым вместо жёлтого.
    // Остальные коды остаются собой по смыслу - зелёный это "включено", красный
    // это "ошибка", - но подтянуты к общей гамме, чтобы не выпадать из неё.

    public static final TextColor BLACK = TextColor.color(0x0E0C13);
    public static final TextColor DARK_BLUE = TextColor.color(0x4A3E7A);
    public static final TextColor DARK_GREEN = TextColor.color(0x43A06A);
    public static final TextColor DARK_AQUA = TextColor.color(0x5E93B5);
    public static final TextColor DARK_RED = TextColor.color(0xA83E52);
    public static final TextColor DARK_PURPLE = TextColor.color(0x7B33D6);
    /** Основной акцент: им подписаны названия почти всех предметов в меню. */
    public static final TextColor GOLD = TextColor.color(0xB86BFF);
    public static final TextColor GRAY = TextColor.color(0xA9A4BA);
    public static final TextColor DARK_GRAY = TextColor.color(0x565070);
    public static final TextColor BLUE = TextColor.color(0x7C97F2);
    public static final TextColor GREEN = TextColor.color(0x63D18C);
    public static final TextColor AQUA = TextColor.color(0x86DCEA);
    public static final TextColor RED = TextColor.color(0xF07286);
    public static final TextColor LIGHT_PURPLE = TextColor.color(0xD98CFF);
    /** Основной цвет описаний: почти белый, с лёгким уходом в сиреневый. */
    public static final TextColor YELLOW = TextColor.color(0xDED8EE);
    public static final TextColor WHITE = TextColor.color(0xFFFFFF);

    /**
     * Ванильный цвет по коду. Нужен для чужого текста: названий уровней, треков,
     * ников. Их писали не мы, и наша палитра к ним отношения не имеет - автор
     * выбрал &6, значит должно быть золото, а не пурпур.
     *
     * @return цвет или null, если это не код цвета
     */
    @Nullable
    public static TextColor vanillaByLegacyCode(char code) {
        switch (Character.toLowerCase(code)) {
            case '0': return TextColor.color(0x000000);
            case '1': return TextColor.color(0x0000AA);
            case '2': return TextColor.color(0x00AA00);
            case '3': return TextColor.color(0x00AAAA);
            case '4': return TextColor.color(0xAA0000);
            case '5': return TextColor.color(0xAA00AA);
            case '6': return TextColor.color(0xFFAA00);
            case '7': return TextColor.color(0xAAAAAA);
            case '8': return TextColor.color(0x555555);
            case '9': return TextColor.color(0x5555FF);
            case 'a': return TextColor.color(0x55FF55);
            case 'b': return TextColor.color(0x55FFFF);
            case 'c': return TextColor.color(0xFF5555);
            case 'd': return TextColor.color(0xFF55FF);
            case 'e': return TextColor.color(0xFFFF55);
            case 'f': return TextColor.color(0xFFFFFF);
            default: return null;
        }
    }

    /**
     * @param code символ кода цвета (0-9, a-f), регистр не важен
     * @return цвет палитры или null, если это не код цвета
     */
    @Nullable
    public static TextColor byLegacyCode(char code) {
        switch (Character.toLowerCase(code)) {
            case '0': return BLACK;
            case '1': return DARK_BLUE;
            case '2': return DARK_GREEN;
            case '3': return DARK_AQUA;
            case '4': return DARK_RED;
            case '5': return DARK_PURPLE;
            case '6': return GOLD;
            case '7': return GRAY;
            case '8': return DARK_GRAY;
            case '9': return BLUE;
            case 'a': return GREEN;
            case 'b': return AQUA;
            case 'c': return RED;
            case 'd': return LIGHT_PURPLE;
            case 'e': return YELLOW;
            case 'f': return WHITE;
            default: return null;
        }
    }

    // ==================== ГРАДИЕНТЫ ====================

    private static final Map<String, TextColor[]> GRADIENTS = new HashMap<>();

    private static void gradient(@NonNull String name, int... colors) {
        TextColor[] stops = new TextColor[colors.length];
        for (int i = 0; i < colors.length; i++) {
            stops[i] = TextColor.color(colors[i]);
        }
        GRADIENTS.put(name, stops);
    }

    static {
        // Пурпур - главный цвет плагина, поэтому все акценты уходят в него.
        gradient("accent", 0xD98CFF, 0x9440F0);
        gradient("title", 0xE3BBFF, 0xA655F5);
        // Успех и ошибка не пурпурные намеренно: их смысл важнее стиля.
        gradient("good", 0x9BF0B8, 0x45C77A);
        gradient("bad", 0xFF9AAB, 0xE04A63);
        gradient("muted", 0xC4BFD4, 0x8B85A0);
        gradient("exp", 0xF0A8FF, 0xB040F0);
        gradient("line", 0x2A2536, 0x565070, 0x2A2536);
    }

    /**
     * @return копия точек градиента или null, если имени нет
     */
    @Nullable
    public static TextColor[] gradientByName(@NonNull String name) {
        TextColor[] stops = GRADIENTS.get(name.toLowerCase(Locale.ROOT));
        return stops == null ? null : stops.clone();
    }

    // ==================== ВАНИЛЬНЫЕ ЦВЕТА ====================
    // Не всякий цвет в плагине - оформление. Оценка точности, модификаторы,
    // сложность - это опознавательные знаки: игрок читает их по цвету, не вчитываясь.
    // Такие места пишутся не кодом &6, а прямым &#RRGGBB отсюда, и перекраска
    // палитры их не трогает.

    public static final String V_BLACK = "&#000000";
    public static final String V_DARK_BLUE = "&#0000AA";
    public static final String V_DARK_GREEN = "&#00AA00";
    public static final String V_DARK_AQUA = "&#00AAAA";
    public static final String V_DARK_RED = "&#AA0000";
    public static final String V_DARK_PURPLE = "&#AA00AA";
    public static final String V_GOLD = "&#FFAA00";
    public static final String V_GRAY = "&#AAAAAA";
    public static final String V_DARK_GRAY = "&#555555";
    public static final String V_BLUE = "&#5555FF";
    public static final String V_GREEN = "&#55FF55";
    public static final String V_AQUA = "&#55FFFF";
    public static final String V_RED = "&#FF5555";
    public static final String V_LIGHT_PURPLE = "&#FF55FF";
    public static final String V_YELLOW = "&#FFFF55";
    public static final String V_WHITE = "&#FFFFFF";

    /**
     * Красный для сообщений о провале забега: падение, смерть, отпущенный бег.
     * Насыщеннее ванильного, но не тёмный - его видно на бегу, и он не пугает.
     */
    public static final String V_FAIL = "&#FF3B3B";

    // ==================== СИМВОЛЫ ====================
    // Только те знаки, которые заведомо есть в шрифте игры. Экзотика вроде часов
    // или шестерёнок на части клиентов рисуется квадратиком, поэтому её здесь нет.

    /** Маркер пункта, как в шапке на скриншоте. */
    public static final String DIAMOND = "\u25C6";      // ◆
    /** Маркер вложенного пункта. */
    public static final String DIAMOND_HOLLOW = "\u25C7"; // ◇
    /** Украшение заголовка. */
    public static final String STAR = "\u2726";          // ✦
    public static final String STAR_HOLLOW = "\u2727";   // ✧
    public static final String GEM = "\u2756";           // ❖
    public static final String SQUARE = "\u25AA";        // ▪
    public static final String ARROW = "\u00BB";         // »
    public static final String ARROW_LEFT = "\u00AB";    // «
    public static final String TRIANGLE = "\u25B8";      // ▸
    public static final String BULLET = "\u2022";        // •
    public static final String DOT = "\u00B7";           // ·
    public static final String CHECK = "\u2714";         // ✔
    public static final String CROSS = "\u2716";         // ✖
    public static final String WARN = "\u26A0";          // ⚠
    public static final String NOTE = "\u266A";          // ♪
    public static final String UP = "\u25B2";            // ▲
    public static final String DOWN = "\u25BC";          // ▼
    public static final String DASH = "\u2500";          // ─
    public static final String PIPE = "\u2502";          // │

    /**
     * Тонкая линия-разделитель заданной длины.
     */
    @NonNull
    public static String line(int length) {
        return DASH.repeat(Math.max(0, length));
    }
}
