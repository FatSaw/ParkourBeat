package ru.sortix.parkourbeat.ui;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.rating.AccuracyGrade;
import ru.sortix.parkourbeat.rating.JumpResult;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Все символы из ресурспака ParkourBeat Core в одном месте.
 * <p>
 * Каждая картинка из РП - это обычный символ из Private Use Area в шрифте
 * minecraft:default, поэтому его можно вставлять прямо в строку: в титл, в лор, в
 * табло, даже в lang.yml. Коды должны совпадать с font/default.json в РП -
 * таблица целиком лежит в README.
 * <p>
 * Символы-картинки всегда отдаются белыми и без жирного/курсива: цвет текста
 * перемножается с цветом картинки, а жирный рисует её дважды со сдвигом.
 */
public final class Glyphs {
    private Glyphs() {
    }

    /** Общий рубильник: false - везде вернётся обычный текст из lang.yml, как раньше. */
    public static volatile boolean BADGES_ENABLED = true;

    // ==================== ЛОКАЛЬ ====================

    /** Набор картинок по языку игрока: 0 - русский, 1 - английский, 2 - украинский. */
    public static int localeSet(@Nullable String locale) {
        String code = locale == null ? "" : locale.toLowerCase(Locale.ROOT);
        Integer cached = LOCALE_SETS.get(code);
        if (cached != null) return cached;
        int set = detectLocaleSet(code);
        LOCALE_SETS.put(code, set);
        return set;
    }

    private static final java.util.Map<String, Integer> LOCALE_SETS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * По коду и по названию языка из lang.yml. ВАЖНО: английский на сервере - это секция
     * «default», по коду её не узнать; раньше она считалась русской, и английские игроки
     * видели русские фоны меню, бейджи и плашки.
     */
    private static int detectLocaleSet(@NonNull String code) {
        if (code.startsWith("ru") || code.startsWith("be") || code.startsWith("kk")) return 0;
        if (code.startsWith("uk")) return 2;
        if (code.startsWith("en")) return 1;
        String name = "";
        try {
            name = ru.sortix.parkourbeat.utils.lang.PlayerLang.displayName(code.isEmpty() ? "default" : code)
                .toLowerCase(Locale.ROOT).replaceAll("[&\u00A7][0-9a-fk-or]", "");
        } catch (Throwable ignored) {
        }
        if (name.contains("\u0440\u0443\u0441") || name.contains("russ")) return 0;      // рус
        if (name.contains("\u0443\u043A\u0440") || name.contains("ukr")) return 2;       // укр
        if (name.contains("engl") || name.contains("\u0430\u043D\u0433\u043B")) return 1; // англ
        return 1; // «default» и всё неизвестное - английский
    }

    // ==================== БЕЙДЖИ ====================

    public enum Badge {
        LOST, FALL, MOVEBACK, STOPPED, PRESSRUN, NOTSPRINTING, COMPLETED, RECORD_PERSONAL, RECORD_GLOBAL;

        /**
         * Бейдж из двух половин: атлас шрифта 256x256, а длинный бейдж в титле шире,
         * целиком он бы не влез и нарисовался квадратом. Половины встык (-1 убирает
         * межбуквенный пиксель).
         */
        @NonNull
        public String chars(@Nullable String locale) {
            int set = 0x10 * localeSet(locale) + this.ordinal();
            return String.valueOf((char) (0xE100 + set)) + space(-1) + (char) (0xE180 + set);
        }

        @NonNull
        public Component component(@Nullable String locale) {
            return glyph(this.chars(locale));
        }
    }

    private static final Map<String, Badge> BADGE_KEYS = new HashMap<>();

    static {
        // LangOptions (ключ = имя константы)
        BADGE_KEYS.put("level_play_title_death", Badge.LOST);
        BADGE_KEYS.put("level_play_title_fall", Badge.FALL);
        BADGE_KEYS.put("level_play_title_moveback", Badge.MOVEBACK);
        BADGE_KEYS.put("level_play_title_stopped", Badge.STOPPED);
        BADGE_KEYS.put("level_play_title_pressrun", Badge.PRESSRUN);
        BADGE_KEYS.put("level_play_title_notsprinting", Badge.NOTSPRINTING);
        BADGE_KEYS.put("level_play_title_complete", Badge.COMPLETED);
        // Lang (ключ = путь в lang.yml)
        BADGE_KEYS.put("game.title.lost", Badge.LOST);
        BADGE_KEYS.put("game.title.completed", Badge.COMPLETED);
        BADGE_KEYS.put("game.title.record_personal", Badge.RECORD_PERSONAL);
        BADGE_KEYS.put("game.title.record_global", Badge.RECORD_GLOBAL);
    }

    /** Бейдж, которым заменяется строка с этим ключом, или null - строка остаётся текстом. */
    @Nullable
    public static Badge badgeForKey(@NonNull String key) {
        return BADGES_ENABLED ? BADGE_KEYS.get(key) : null;
    }

    // ==================== СЛОЖНОСТИ, ОЦЕНКИ, РЕЙТЫ ====================

    /** Плашка сложности (8px, как строка текста): ru - оригиналы, en/uk - тем же шрифтом. */
    public static char banner(@NonNull LevelDifficulty difficulty, @Nullable String locale) {
        int set = localeSet(locale);
        return (char) ((set == 1 ? 0xE208 : set == 2 ? 0xE2B0 : 0xE200) + difficulty.ordinal());
    }

    /** Большой куб сложности: под сабтайтл (там он ровно 1:1). */
    public static char cubeBig(@NonNull LevelDifficulty difficulty) {
        return (char) (0xE210 + difficulty.ordinal());
    }

    /** Маленький куб сложности: под строку лора/табло/чата. */
    public static char cubeSmall(@NonNull LevelDifficulty difficulty) {
        return (char) (0xE218 + difficulty.ordinal());
    }

    /** Буква оценки SS/S/A/B/C/D, для R - красная F. */
    public static char grade(@NonNull AccuracyGrade grade) {
        return (char) (0xE220 + grade.ordinal());
    }

    /** Плашка попадания: ВЕЛИКОЛЕПНО / ОТЛИЧНО / ПЛОХО / УЖАСНО. */
    public static char rate(@NonNull JumpResult result, @Nullable String locale) {
        int set = localeSet(locale);
        return (char) ((set == 1 ? 0xE234 : set == 2 ? 0xE2B8 : 0xE230) + result.ordinal());
    }

    public static final char ICON_LEVEL = '\uE240';
    public static final char ICON_LEVELS = '\uE241';
    public static final char ICON_ONLINE = '\uE242';
    public static final char ICON_NA = '\uE243';

    // ==================== ЛОГОТИПЫ (анимация блика) ====================

    public static final char LOGO_TAB = '\uE001';
    public static final char LOGO_SCORE = '\uE002';
    private static final char LOGO_TAB_ANIM = '\uE600';
    private static final char LOGO_SCORE_ANIM = '\uE660';
    /**
     * Два 3D-эффекта по очереди (кадры собраны в РП, по одному на тик):
     * <ul>
     *   <li>ВОЛНА - баннер разрезан на 32 объёмные плитки, по ним бежит наклон
     *       верхом к камере с перспективой и торцами (46 кадров);</li>
     *   <li>ПОКАЧИВАНИЕ - весь баннер толстой плашкой затухающе качается вокруг
     *       вертикальной оси, видна боковая грань (48 кадров).</li>
     * </ul>
     * Цикл 12 секунд: волна, пауза, покачивание, пауза.
     */
    private static final int LOGO_WAVE_FRAMES = 46;
    private static final int LOGO_SWING_FRAMES = 48;
    private static final long LOGO_FRAME_MS = 50L;
    private static final long LOGO_CYCLE_MS = 12000L;
    private static final long LOGO_SWING_AT_MS = 6000L;

    private static int logoFrame(long now) {
        long t = now % LOGO_CYCLE_MS;
        if (t < LOGO_WAVE_FRAMES * LOGO_FRAME_MS) return (int) (t / LOGO_FRAME_MS);
        long s = t - LOGO_SWING_AT_MS;
        if (s >= 0 && s < LOGO_SWING_FRAMES * LOGO_FRAME_MS) return LOGO_WAVE_FRAMES + (int) (s / LOGO_FRAME_MS);
        return -1;
    }

    public static char tabLogo(long now) {
        int f = logoFrame(now);
        return f < 0 ? LOGO_TAB : (char) (LOGO_TAB_ANIM + f);
    }

    public static char scoreLogo(long now) {
        int f = logoFrame(now);
        return f < 0 ? LOGO_SCORE : (char) (LOGO_SCORE_ANIM + f);
    }

    // ==================== ПЕРЕХОДЫ ====================

    /** Кадры закрытия/открытия - числа совпадают с тем, что собрано в РП. */
    public enum Transition {
        N_A(0, 8, 7), EASY(1, 8, 7), NORMAL(2, 8, 7), HARD(3, 8, 7), EXPERT(4, 6, 5), EXPERT_PLUS(5, 9, 8);

        private final int set;
        public final int inFrames;
        public final int outFrames;

        Transition(int set, int inFrames, int outFrames) {
            this.set = set;
            this.inFrames = inFrames;
            this.outFrames = outFrames;
        }

        private char base() {
            return (char) (0xE300 + 0x20 * this.set);
        }

        public char in(int frame) {
            return (char) (this.base() + Math.max(0, Math.min(this.inFrames - 1, frame)));
        }

        /** Экран полностью закрыт. */
        public char full() {
            return this.in(this.inFrames - 1);
        }

        public char out(int frame) {
            return (char) (this.base() + this.inFrames + Math.max(0, Math.min(this.outFrames - 1, frame)));
        }

        @NonNull
        public static Transition of(@Nullable LevelDifficulty difficulty) {
            if (difficulty == null) return N_A;
            return values()[Math.min(difficulty.ordinal(), values().length - 1)];
        }
    }

    // ==================== ПРОБЕЛЫ ====================

    /** Пробел произвольной ширины (в пикселях шрифта), в т.ч. отрицательный. */
    @NonNull
    public static String space(int pixels) {
        if (pixels == 0) return "";
        boolean negative = pixels < 0;
        int left = Math.abs(pixels);
        StringBuilder out = new StringBuilder();
        for (int bit = 7; bit >= 0; bit--) {
            int value = 1 << bit;
            while (left >= value) {
                out.append((char) ((negative ? 0xF801 : 0xF811) + bit));
                left -= value;
            }
        }
        return out.toString();
    }

    // ==================== ПИЛЮЛЯ ПОД ТЕКСТ (как у HollowCube) ====================

    private static final char PILL_LEFT = '\uE050';
    private static final char PILL_MID8 = '\uE051';
    private static final char PILL_MID1 = '\uE052';
    private static final char PILL_RIGHT = '\uE053';
    private static final int PILL_PAD = 4;

    /**
     * Тёмная полупрозрачная подложка ровно под ширину текста.
     * <p>
     * Ширина считается по таблице ванильного шрифта, поэтому для текста из
     * обычных символов она точная, для экзотики - с погрешностью в пиксель-два.
     */
    @NonNull
    public static Component pill(@NonNull Component text) {
        int width = width(text);
        int total = width + PILL_PAD * 2;
        int mid = Math.max(0, total - 4);

        StringBuilder bg = new StringBuilder();
        bg.append(PILL_LEFT).append(space(-1));
        for (int i = 0; i < mid / 8; i++) bg.append(PILL_MID8).append(space(-1));
        for (int i = 0; i < mid % 8; i++) bg.append(PILL_MID1).append(space(-1));
        bg.append(PILL_RIGHT).append(space(-1));
        bg.append(space(-(total - PILL_PAD)));

        return Component.text()
            .append(glyph(bg.toString()))
            .append(text)
            .append(Component.text(space(PILL_PAD)))
            .build();
    }

    /** Ширина компонента в пикселях шрифта (с учётом жирного). */
    public static int width(@NonNull Component component) {
        return width(component, false);
    }

    private static int width(@NonNull Component component, boolean parentBold) {
        TextDecoration.State state = component.decoration(TextDecoration.BOLD);
        boolean bold = state == TextDecoration.State.NOT_SET ? parentBold : state == TextDecoration.State.TRUE;
        int sum = 0;
        if (component instanceof TextComponent) {
            String content = ((TextComponent) component).content();
            for (int i = 0; i < content.length(); i++) sum += advance(content.charAt(i)) + (bold ? 1 : 0);
        }
        for (Component child : component.children()) sum += width(child, bold);
        return sum;
    }

    /** Ширина символа ванильного шрифта вместе с межбуквенным пикселем. */
    public static int advance(char c) {
        switch (c) {
            case ' ': return 4;
            case '!': case '\'': case ',': case '.': case ':': case ';': case 'i': case '|': return 2;
            case '`': case 'l': return 3;
            case '"': case '(': case ')': case '*': case 'I': case '[': case ']': case 't': case '{': case '}': return 4;
            case '<': case '>': case 'f': case 'k': return 5;
            case '@': case '~': return 7;
            default: return 6;
        }
    }

    // ==================== ФОНЫ МЕНЮ ====================

    public static char menuModifiers(@Nullable String locale) { return (char) (0xE50C + localeSet(locale)); }

    public static char menuRecords(@Nullable String locale) { return (char) (0xE50F + localeSet(locale)); }

    public static char menuProfile(@Nullable String locale) { return (char) (0xE512 + localeSet(locale)); }

    /** Витрина 7x3 со страницами (топ игроков). */
    public static char menuTop(@Nullable String locale) { return (char) (0xE515 + localeSet(locale)); }

    /** Витрина 7x3 без страниц (реплеи). */
    public static char menuReplays(@Nullable String locale) { return (char) (0xE518 + localeSet(locale)); }

    /** Фон главного меню (ru / en / uk). */
    public static char menuMain(@Nullable String locale) {
        return (char) (0xE509 + localeSet(locale));
    }

    /**
     * Плашка попадания для актионбара (x2), ступень прозрачности 0..3
     * (0 - полная, 3 - почти погасла).
     */
    public static char rateBar(@NonNull JumpResult result, @Nullable String locale, int fade) {
        return (char) (0xE800 + Math.max(0, Math.min(3, fade)) * 0x10 + localeSet(locale) * 4 + result.ordinal());
    }

    /** Фон списка уровней (ru / en / uk). */
    public static char menuLevels(@Nullable String locale) {
        return (char) (0xE503 + localeSet(locale));
    }

    /** Фон меню оценки уровня (ru / en / uk). */
    public static char menuRate(@Nullable String locale) {
        return (char) (0xE506 + localeSet(locale));
    }

    /** Пять звёзд по среднему (с половинками), например ★★★⯪☆. */
    @NonNull
    public static String stars(double average) {
        double rounded = Math.round(Math.max(0, Math.min(5, average)) * 2) / 2.0;
        StringBuilder out = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            if (rounded >= i) out.append(Icons.Icon.STAR.ch());
            else if (rounded >= i - 0.5) out.append(Icons.Icon.STAR_HALF.ch());
            else out.append(Icons.Icon.STAR_EMPTY.ch());
        }
        return out.toString();
    }

    /** Фон меню создания уровня (ru / en / uk). */
    public static char menuCreate(@Nullable String locale) {
        return (char) (0xE500 + localeSet(locale));
    }

    /**
     * Заголовок окна-сундука с нарисованным фоном.
     * <p>
     * Заголовок рисуется с x=8, поэтому сначала -8, затем фон шириной 176 px
     * (сдвиг 177 с межбуквенным пикселем), затем откат на x=10 - туда, где в шапке
     * окна стоит текст. Фон лежит под предметами: предметы рисуются глубже по Z.
     */
    @NonNull
    public static Component menuTitle(char background, @Nullable Component headerText) {
        Component title = glyph(space(-8) + background + space(10 - 177));
        return headerText == null ? title : title.append(headerText);
    }

    // ==================== РАМКА НАВЕДЕНИЯ ====================

    /** Размеры нарисованных рамок (w x h) - те же, что в РП (символы E520..E525). */
    private static final int[][] HOVER_SIZES = {{92, 20}, {74, 20}, {164, 20}, {164, 38}, {74, 38}, {92, 19}, {74, 19}};

    /**
     * Рамка вокруг кнопки из нескольких слотов, видимая только при наведении.
     * <p>
     * Как это работает: символ-рамка вставляется в название предмета, то есть в его
     * подсказку - а подсказка рисуется только когда на предмет навели курсор. Шейдер
     * текста из РП (оверлей pb_hover_*) узнаёт этот символ по пикселям-меткам и
     * переносит его на кнопку. Куда - закодировано цветом: R/G = смещение от центра
     * экрана (+128), B = 64 + половина ширины подсказки (чтобы рамка не легла поверх
     * самой подсказки; у тени текста все каналы меньше 64 - так шейдер её отличает).
     * Тот же символ стоит во всех слотах кнопки - поэтому обводится вся кнопка.
     *
     * @param c0 r0 c1 r1 - прямоугольник кнопки в слотах окна-сундука на 6 рядов
     */
    @NonNull
    public static Component hoverFrame(int c0, int r0, int c1, int r1, int tooltipWidth) {
        int x0 = 6 + 18 * c0;
        int y0 = 16 + 18 * r0;
        int w = 18 * (c1 - c0 + 1) + 2;
        int h = Math.min(16 + 18 * (r1 + 1) + 1, 124) - y0 + 1;
        int index = -1;
        for (int i = 0; i < HOVER_SIZES.length; i++) {
            if (HOVER_SIZES[i][0] == w && HOVER_SIZES[i][1] == h) index = i;
        }
        if (index < 0) return Component.empty();
        int b = 64 + Math.min(191, (tooltipWidth + 1) / 2);
        return Component.text((char) (0xE520 + index) + space(-(w + 1)))
            .color(net.kyori.adventure.text.format.TextColor.color(x0 - 88 + 128, y0 - 111 + 128, b))
            .decoration(TextDecoration.BOLD, false)
            .decoration(TextDecoration.ITALIC, false);
    }

    /** Вставляет рамку наведения в название предмета (ширина подсказки считается сама). */
    @NonNull
    public static org.bukkit.inventory.ItemStack withHover(@NonNull org.bukkit.inventory.ItemStack item,
                                                           int c0, int r0, int c1, int r1) {
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        Component name = meta.displayName() == null ? Component.empty() : meta.displayName();
        int width = width(name);
        java.util.List<Component> lore = meta.lore();
        if (lore != null) for (Component line : lore) width = Math.max(width, width(line));
        meta.displayName(Component.text()
            .decoration(TextDecoration.ITALIC, false)
            .append(hoverFrame(c0, r0, c1, r1, width + 8))
            .append(name)
            .build());
        item.setItemMeta(meta);
        return item;
    }

    /** Подпись-картинка в заголовке окна: символ, его x в окне и ширина картинки. */
    public static final class TitleLabel {
        final char ch;
        final int x;
        final int width;

        public TitleLabel(char ch, int x, int width) {
            this.ch = ch;
            this.x = x;
            this.width = width;
        }
    }

    /**
     * Как {@link #menuTitle}, но поверх фона кладутся подписи-картинки (например,
     * названия языков на своих карточках). Каждая подпись - символ со своей высотой
     * (ascent), поэтому она встаёт в нужный ряд, а по горизонтали - пробелами.
     */
    @NonNull
    public static Component menuTitle(char background, @Nullable Component headerText, @NonNull java.util.List<TitleLabel> labels) {
        StringBuilder out = new StringBuilder();
        out.append(space(-8)).append(background);
        int cursor = 177;
        for (TitleLabel label : labels) {
            out.append(space(label.x - cursor)).append(label.ch);
            cursor = label.x + label.width + 1;
        }
        out.append(space(10 - cursor));
        Component title = glyph(out.toString());
        return headerText == null ? title : title.append(headerText);
    }

    /** Фон списка 9x4: friends / leveltop / history. */
    public static char menuList(@NonNull String kind, @Nullable String locale) {
        int base = kind.equals("friends") ? 0xE560 : kind.equals("leveltop") ? 0xE563 : 0xE566;
        return (char) (base + localeSet(locale));
    }

    public static char menuEditor(@Nullable String locale) { return (char) (0xE537 + localeSet(locale)); }

    public static char menuDetails(@Nullable String locale) { return (char) (0xE51B + localeSet(locale)); }

    public static char menuSettings(@Nullable String locale) { return (char) (0xE540 + localeSet(locale)); }

    public static char menuLanguage(@Nullable String locale) { return (char) (0xE531 + localeSet(locale)); }

    public static char menuAfk(@Nullable String locale) { return (char) (0xE534 + localeSet(locale)); }

    // ==================== ВНУТРЕННЕЕ ====================

    @NonNull
    public static Component glyph(char ch) {
        return glyph(String.valueOf(ch));
    }

    @NonNull
    public static Component glyph(@NonNull String chars) {
        return Component.text(chars, NamedTextColor.WHITE)
            .decoration(TextDecoration.BOLD, false)
            .decoration(TextDecoration.ITALIC, false);
    }
}
