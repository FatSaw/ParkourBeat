package ru.sortix.parkourbeat.utils.lang;

import lombok.NonNull;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

/**
 * Язык игрока: единственное место, где решается, на каком языке ему всё показывать.
 * <p>
 * Раньше по коду было рассыпано {@code player.getLocale().toLowerCase()}, то есть язык
 * жёстко брался у клиента и поменять его игрок не мог. Теперь сначала спрашивается
 * выбор самого игрока (меню «Язык»), и только если выбран режим «авто» - локаль клиента.
 * <p>
 * Настройки живут в {@code PlayerSettingsManager}, но обращаться к нему напрямую отсюда
 * нельзя: класс нужен и в местах без ссылки на плагин (скорборд, предметы, статические
 * фабрики). Поэтому менеджер сам ставит сюда резолвер при запуске и снимает при
 * выключении - тот же приём, что и с {@code GameSettings.setFriendAccessResolver}.
 */
public final class PlayerLang {
    /**
     * Значение «брать язык у клиента». Пустая строка выбрана не случайно: именно так
     * обозначается локаль по умолчанию в {@link LangOptions}, и старые сохранённые
     * настройки без поля языка читаются как «авто» сами собой.
     */
    public static final String AUTO = "";

    /** Секция lang.yml, из которой берётся текст, если для локали своей секции нет. */
    public static final String DEFAULT_LOCALE = "default";

    private static volatile Function<UUID, String> resolver = null;

    private PlayerLang() {
    }

    /**
     * Ставится менеджером настроек. {@code null} - настройки недоступны (плагин
     * выключается или ещё не поднялся), тогда работает старое поведение «по клиенту».
     */
    public static void setResolver(@Nullable Function<UUID, String> newResolver) {
        resolver = newResolver;
    }

    /**
     * Язык, на котором нужно говорить с этим игроком.
     */
    @NonNull
    public static String of(@Nullable Player player) {
        if (player == null) return AUTO;

        String chosen = chosen(player.getUniqueId());
        if (chosen != null && !chosen.isEmpty()) return chosen;

        return LangOptions.replaceLocale(player.getLocale().toLowerCase(Locale.ROOT));
    }

    /**
     * Для команд: отправителем может быть консоль, ей отдаём язык по умолчанию.
     */
    @NonNull
    public static String of(@Nullable CommandSender sender) {
        return sender instanceof Player player ? of(player) : AUTO;
    }

    /**
     * @return выбранный игроком язык или {@code null}/пустая строка, если стоит «авто»
     */
    @Nullable
    public static String chosen(@NonNull UUID playerId) {
        Function<UUID, String> current = resolver;
        return current == null ? null : current.apply(playerId);
    }

    public static boolean isAuto(@NonNull UUID playerId) {
        String chosen = chosen(playerId);
        return chosen == null || chosen.isEmpty();
    }

    /**
     * Языки, которые реально есть в {@code lang.yml}, в порядке из файла.
     * <p>
     * Отдельного списка нигде не заводим: чтобы добавить язык, достаточно дописать
     * секцию в {@code lang.yml} - она сразу появится в меню.
     */
    @NonNull
    public static List<String> availableLocales() {
        List<String> result = new ArrayList<>();
        String[] locales = LangOptions.locales;
        if (locales == null) return result;
        for (String locale : locales) {
            // Пустая строка - это техническая «локаль по умолчанию», её дублирует
            // секция default, показывать в меню обе не нужно.
            if (locale == null || locale.isEmpty()) continue;
            result.add(locale);
        }
        return result;
    }

    /**
     * Секция lang.yml, по которой игрок реально видит интерфейс.
     * <p>
     * {@link #of} может вернуть локаль клиента вроде {@code de_de}, для которой своей
     * секции нет и текст берётся из default. Меню выбора должно подсветить именно ту
     * строку, которую игрок видит, поэтому здесь такие локали сводятся к default.
     */
    @NonNull
    public static String effectiveLocale(@Nullable Player player) {
        String locale = of(player);
        return availableLocales().contains(locale) ? locale : DEFAULT_LOCALE;
    }

    /**
     * Как язык называется на самом себе: English, Русский и так далее.
     * <p>
     * Берётся из ключа {@code language.name} внутри самой секции языка, поэтому имя
     * тоже переводится вместе с ним. Если ключа нет, показываем код секции - так сразу
     * видно, что в новый язык забыли вписать название.
     */
    @NonNull
    public static String displayName(@NonNull String locale) {
        String name = Lang.exact(locale, "language.name");
        if (name != null) return name;

        // Своей секции у локали нет - значит игрок видит default, так его и назовём.
        name = Lang.exact(DEFAULT_LOCALE, "language.name");
        return name == null ? locale : name;
    }

    /**
     * Иконка языка: голова с флагом. Текстура лежит в {@code language.icon} внутри
     * секции самого языка, чтобы новый язык добавлялся без правок кода.
     */
    @NonNull
    public static org.bukkit.inventory.ItemStack displayIcon(@NonNull String locale) {
        String texture = Lang.exact(locale, "language.icon");
        if (texture != null && !texture.isBlank()) {
            try {
                return ru.sortix.parkourbeat.inventory.Heads.getHeadByRawData(texture.trim());
            } catch (Exception e) {
                // Битую текстуру не считаем поводом уронить меню: покажем бумажку,
                // а сам язык всё равно останется выбираемым.
            }
        }
        return new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER);
    }

}
