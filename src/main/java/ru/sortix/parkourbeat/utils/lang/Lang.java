package ru.sortix.parkourbeat.utils.lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Доступ к {@code lang.yml} по свободному ключу.
 * <p>
 * {@link LangOptions} - перечисление: чтобы вынести туда строку, нужно вписать её
 * и в перечисление, и в оба языка, а имя ключа при этом жёстко связано с именем
 * константы. Для сотен строк, зашитых прямо в меню, это слишком дорого, поэтому
 * здесь ключ - обычная строка вида {@code "editor.worldtype.name"}.
 * <p>
 * Формат ключей и файла тот же самый, локали ищутся так же, поэтому обе системы
 * спокойно живут рядом и читают один и тот же файл.
 */
public final class Lang {
    private static final Map<String, Map<String, String>> VALUES = new HashMap<>();
    private static final String DEFAULT_LOCALE = "default";

    private Lang() {
    }

    /**
     * Перечитывает файл. Вызывается из {@link LangOptions#reload}.
     */
    static void load(@NonNull SimpleConfiguration config, @NonNull String[] locales) {
        VALUES.clear();
        for (String locale : locales) {
            String prefix = "localisation\0" + locale + "\0";
            collect(config, prefix, locale);
        }
    }

    private static void collect(@NonNull SimpleConfiguration config,
                                @NonNull String prefix,
                                @NonNull String locale
    ) {
        for (String key : config.getKeysUnder(prefix)) {
            String value = config.getStringOrDefault(prefix + key, null);
            if (value == null) continue;
            value = value.replace("\r", "").replace("\\n", "\n");
            VALUES.computeIfAbsent(key.replace('\0', '.'), k -> new HashMap<>()).put(locale, value);
        }
    }

    /**
     * @return строка для локали игрока, либо ключ целиком, если его нет в файле:
     * так пропущенный перевод сразу видно в игре, а не выясняется по пустому месту
     */
    @NonNull
    public static String raw(@Nullable Player player, @NonNull String key, @NonNull String... replacements) {
        return raw(player == null ? null : PlayerLang.of(player), key, replacements);
    }

    /**
     * Значение строго для указанной локали, без отката на default.
     * <p>
     * Нужно там, где откат вреден: например, название языка в меню выбора. С откатом
     * все языки без своего {@code language.name} назывались бы «English».
     *
     * @return {@code null}, если в этой секции ключа нет
     */
    @Nullable
    public static String exact(@Nullable String locale, @NonNull String key) {
        if (locale == null) return null;
        Map<String, String> byLocale = VALUES.get(key);
        if (byLocale == null) return null;
        return byLocale.get(LangOptions.replaceLocale(locale.toLowerCase()));
    }

    /**
     * Вариант для меню: там на руках уже есть код локали, а игрока может не быть.
     */
    @NonNull
    public static String raw(@Nullable String locale, @NonNull String key, @NonNull String... replacements) {
        Map<String, String> byLocale = VALUES.get(key);
        String value = null;

        if (byLocale != null) {
            if (locale != null) {
                value = byLocale.get(LangOptions.replaceLocale(locale.toLowerCase()));
            }
            if (value == null) value = byLocale.get(DEFAULT_LOCALE);
        }
        if (value == null) value = key;

        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace(replacements[i], replacements[i + 1]);
        }
        return value;
    }

    @NonNull
    public static Component text(@Nullable String locale, @NonNull String key, @NonNull String... replacements) {
        return PbText.of(raw(locale, key, replacements));
    }

    @NonNull
    public static Component item(@Nullable String locale, @NonNull String key, @NonNull String... replacements) {
        return PbText.item(raw(locale, key, replacements));
    }

    @NonNull
    public static Component text(@Nullable Player player, @NonNull String key, @NonNull String... replacements) {
        return PbText.of(raw(player, key, replacements));
    }

    /**
     * То же самое для названий и описаний предметов: гасит ванильный курсив.
     */
    @NonNull
    public static Component item(@Nullable Player player, @NonNull String key, @NonNull String... replacements) {
        return PbText.item(raw(player, key, replacements));
    }

    /**
     * Описание предмета: строки разделяются переводом строки прямо в lang.yml.
     */
    @NonNull
    public static java.util.List<Component> lore(@Nullable Player player,
                                                 @NonNull String key,
                                                 @NonNull String... replacements
    ) {
        return lore(player == null ? null : PlayerLang.of(player), key, replacements);
    }

    /**
     * Вариант для меню: локаль уже известна, игрока под рукой может не быть.
     */
    @NonNull
    public static java.util.List<Component> lore(@Nullable String locale,
                                                 @NonNull String key,
                                                 @NonNull String... replacements
    ) {
        String value = raw(locale, key, replacements);
        java.util.List<Component> lines = new java.util.ArrayList<>();
        for (String line : value.split("\n", -1)) {
            lines.add(line.isEmpty() ? Component.empty() : PbText.item(line));
        }
        return lines;
    }

    public static boolean has(@NonNull String key) {
        return VALUES.containsKey(key);
    }
}
