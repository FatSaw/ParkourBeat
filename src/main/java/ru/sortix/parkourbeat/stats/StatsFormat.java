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
        return LEGACY.deserialize(legacy);
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
     * Цвет ранга по диапазонам. Единственное место, где эти цвета заданы —
     * захочешь перекрасить, правь только здесь.
     * <pre>
     *   #1        &4
     *   #2        &c&l
     *   #3        &a&l
     *   #4–30     &3
     *   #31–50    &b
     *   #51–100   &6
     *   #101–200  &a
     *   #201+     &7
     * </pre>
     */
    @NonNull
    public static String positionColor(int position) {
        if (position <= 0) return "&7";
        if (position == 1) return "&4";
        if (position == 2) return "&c&l";
        if (position == 3) return "&a&l";
        if (position <= 30) return "&3";
        if (position <= 50) return "&b";
        if (position <= 100) return "&6";
        if (position <= 200) return "&a";
        return "&7";
    }

    /**
     * То же самое, но с поправкой на игроков без статистики: пока у человека нет
     * ни одного рекорда, он висит серым независимо от номера. Иначе второй по счёту
     * зарегистрированный игрок светился бы как призёр, ничего не пройдя.
     */
    @NonNull
    public static String positionColor(int position, boolean hasStatistics) {
        return hasStatistics ? positionColor(position) : "&7";
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
     * Цвет пинга по диапазонам:
     * <pre>
     *   0–130    &a
     *   131–210  &e
     *   211–299  &6
     *   300–400  &c
     *   401+     &4
     * </pre>
     * Промежуток 211–219 в исходных диапазонах не был задан — отнесён к &6,
     * чтобы между жёлтым и оранжевым не было дыры.
     */
    @NonNull
    public static String pingColor(int ping) {
        if (ping <= 130) return "&a";
        if (ping <= 210) return "&e";
        if (ping <= 299) return "&6";
        if (ping <= 400) return "&c";
        return "&4";
    }

    /** Пинг с уже подставленным цветом: {@code "&e187"}. */
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
        if (playerName != null && !playerName.isEmpty() && playerName.length() <= 16) {
            try {
                return Heads.getHeadByLicenseName(playerName);
            } catch (Exception ignored) {
            }
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
