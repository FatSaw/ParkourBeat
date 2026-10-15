package ru.sortix.parkourbeat.stats;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.inventory.Heads;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import ru.sortix.parkourbeat.utils.text.PbText;
import ru.sortix.parkourbeat.utils.text.Theme;

/**
 * Мелкие помощники для меню статистики: числа с пробелами, «вчера в 18:04»,
 * «142ч 18м», цвета мест в топе и головы игроков с фолбэком (п.11.6 ТЗ).
 */
public final class StatsFormat {
    public static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private StatsFormat() {
    }

    // ------------------------------------------------------------------ компоненты

    @NonNull
    public static Component text(@NonNull String legacy) {
        return PbText.of(legacy);
    }

    // ------------------------------------------------------------------ числа

    /** {@code 1284300 → "1 284 300"} — так проще читать большие суммы очков. */
    @NonNull
    public static String number(long value) {
        String raw = Long.toString(Math.abs(value));
        StringBuilder builder = new StringBuilder();
        int counter = 0;
        for (int i = raw.length() - 1; i >= 0; i--) {
            builder.append(raw.charAt(i));
            if (++counter % 3 == 0 && i > 0) builder.append(' ');
        }
        if (value < 0) builder.append('-');
        return builder.reverse().toString();
    }

    @NonNull
    public static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    @NonNull
    public static String percentRounded(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value);
    }

    @NonNull
    public static String pp(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    // ------------------------------------------------------------------ время

    /** {@code 142ч 18м}; для маленьких значений — минуты и секунды. */
    @NonNull
    public static String duration(long millis) {
        if (millis <= 0L) return "0м";
        long totalMinutes = millis / 60000L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours <= 0L) {
            if (minutes <= 0L) return (millis / 1000L) + "с";
            return minutes + "м";
        }
        return hours + "ч " + minutes + "м";
    }

    /** {@code 12.05.2026 в 16:52} */
    @NonNull
    public static String dateTime(long millis) {
        if (millis <= 0L) return "—";
        return new SimpleDateFormat("dd.MM.yyyy' в 'HH:mm", new Locale("ru")).format(new Date(millis));
    }

    /** {@code 11.05.2024} */
    @NonNull
    public static String date(long millis) {
        if (millis <= 0L) return "—";
        return new SimpleDateFormat("dd.MM.yyyy").format(new Date(millis));
    }

    /** {@code сегодня в 18:04} / {@code вчера в 18:04} / {@code 12.05 в 16:52} */
    @NonNull
    public static String relativeDateTime(long millis) {
        if (millis <= 0L) return "—";

        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(millis);

        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);

        String time = new SimpleDateFormat("HH:mm").format(new Date(millis));
        if (isSameDay(target, today)) return "сегодня в " + time;
        if (isSameDay(target, yesterday)) return "вчера в " + time;

        if (target.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
            return new SimpleDateFormat("dd.MM' в 'HH:mm").format(new Date(millis));
        }
        return new SimpleDateFormat("dd.MM.yyyy' в 'HH:mm").format(new Date(millis));
    }

    private static boolean isSameDay(@NonNull Calendar a, @NonNull Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
            && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    // ------------------------------------------------------------------ места

    /**
     * Используем константы Theme.V_*, чтобы избежать перекраски в серверную палитру
     */
    @NonNull
    public static String positionColor(int position) {
        if (position <= 0) return Theme.V_GRAY;
        if (position == 1) return Theme.V_DARK_RED;
        if (position == 2) return Theme.V_RED + "&l";
        if (position == 3) return Theme.V_GREEN + "&l";
        if (position <= 10) return Theme.V_GOLD;
        if (position <= 50) return Theme.V_GREEN;
        if (position <= 100) return Theme.V_AQUA;
        if (position <= 200) return Theme.V_DARK_AQUA;
        return Theme.V_GRAY;
    }

    /**
     * То же самое, но с поправкой на игроков без статистики: пока у человека нет
     * ни одного рекорда, он висит серым независимо от номера. Иначе второй по счёту
     * зарегистрированный игрок светился бы как призёр, ничего не пройдя.
     */
    @NonNull
    public static String positionColor(int position, boolean hasStatistics) {
        return hasStatistics ? positionColor(position) : Theme.V_GRAY;
    }

    @NonNull
    public static String position(int position) {
        return positionColor(position) + "#" + position;
    }

    @NonNull
    public static String position(int position, boolean hasStatistics) {
        return positionColor(position, hasStatistics) + "#" + position;
    }

    /**
     * Ранг, после которого идёт ник. Закрывается {@code &r&f}, иначе жирность и
     * цвет ранга (например {@code &c&l} у второго места) утекали бы на ник.
     */
    @NonNull
    public static String rankPrefix(int position, boolean hasStatistics) {
        return position(position, hasStatistics) + "&r&f";
    }

    @NonNull
    public static String rankPrefix(int position) {
        return position(position) + "&r&f";
    }

    /**
     * Цвет пинга с обходом серверной палитры
     */
    @NonNull
    public static String pingColor(int ping) {
        if (ping <= 130) return Theme.V_GREEN;
        if (ping <= 210) return Theme.V_YELLOW;
        if (ping <= 299) return Theme.V_GOLD;
        if (ping <= 400) return Theme.V_RED;
        return Theme.V_DARK_RED;
    }

    /** Пинг с уже подставленным цветом. */
    @NonNull
    public static String ping(int ping) {
        return pingColor(ping) + ping;
    }

    // ------------------------------------------------------------------ головы

    /**
     * Голова игрока. Онлайн — берём его настоящий профиль, оффлайн — по нику,
     * а если ник неизвестен, отдаём голову без скина (п.11.6 ТЗ: фолбэк, но
     * ни в коем случае не блокирующий запрос к Mojang в основном потоке).
     */
    @NonNull
    public static ItemStack playerHead(@NonNull UUID playerId, @Nullable String playerName) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            try {
                return Heads.getHeadByGamer(online);
            } catch (Exception ignored) {
                // упадём в фолбэк ниже
            }
        }
        try {
            return ru.sortix.parkourbeat.ParkourBeat.getPlugin(ru.sortix.parkourbeat.ParkourBeat.class)
                .get(ru.sortix.parkourbeat.inventory.HeadCache.class)
                .getHead(playerId, playerName);
        } catch (Throwable ignored) {
        }
        return Heads.getHeadWithoutSkin();
    }

    @NonNull
    public static ItemStack playerHead(@NonNull OfflinePlayer player) {
        return playerHead(player.getUniqueId(), player.getName());
    }

    @NonNull
    public static String safeName(@Nullable String name) {
        return name == null || name.isEmpty() ? "?" : name;
    }
}
