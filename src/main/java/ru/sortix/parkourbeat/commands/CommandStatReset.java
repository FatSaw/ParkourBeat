package ru.sortix.parkourbeat.commands;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.stats.PlayerProfile;
import ru.sortix.parkourbeat.stats.StatResetRequest;
import ru.sortix.parkourbeat.stats.StatResetRequestManager;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

/**
 * {@code /statreset} — сброс статистики.
 * <pre>
 *   /statreset             оставить заявку на сброс своей статистики
 *   /statreset cancel      отозвать свою заявку
 *   /statreset &lt;ник&gt;       сбросить чужую сразу — parkourbeat.command.statreset.others
 *   /statreset *           сбросить весь сервер   — parkourbeat.command.statreset.all
 * </pre>
 * Обычный игрок сам ничего не стирает: он создаёт заявку, которую модератор
 * рассматривает во вкладке {@code /moder}. Так нельзя случайно снести
 * собственный прогресс, и решение всегда за живым человеком.
 */
@Command(name = "statreset")
@RequiredArgsConstructor
public class CommandStatReset {

    private static final long CONFIRM_WINDOW_MILLIS = 30_000L;
    /** Подтверждения для «жёстких» админских сбросов. Ключ: отправитель + цель. */
    private static final Map<String, Long> PENDING_CONFIRMS = new ConcurrentHashMap<>();

    private final ParkourBeat plugin;

    // ------------------------------------------------------------------ игрок

    @Execute
    @Permission(COMMAND_PERMISSION + "statreset")
    public void request(@Context Player sender) {
        StatResetRequestManager requests = this.plugin.get(StatResetRequestManager.class);

        StatResetRequest existing = requests.get(sender.getUniqueId());
        if (existing != null && existing.isPending()) {
            sender.sendMessage(Component.text(
                "Ваш запрос уже на рассмотрении (подан " + existing.getAgeDays()
                    + " дн. назад). Отозвать: /statreset cancel",
                NamedTextColor.YELLOW));
            return;
        }

        StatResetRequest created = requests.create(sender);
        if (created == null) {
            sender.sendMessage(Component.text(
                "Не удалось создать запрос, попробуйте позже.", NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text(
            "Запрос на сброс статистики отправлен модерации.", NamedTextColor.GREEN));
        sender.sendMessage(Component.text(
            "Мы рассмотрим его в течение " + StatResetRequestManager.REVIEW_DAYS
                + " дней и примем или отклоним. О решении вы узнаете в чате.",
            NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
            "Если передумаете — /statreset cancel", NamedTextColor.DARK_GRAY));
    }

    @Execute
    @Permission(COMMAND_PERMISSION + "statreset")
    public void withArgument(@Context CommandSender sender, @Arg String argument) {
        if ("cancel".equalsIgnoreCase(argument) || "отмена".equalsIgnoreCase(argument)) {
            this.cancel(sender);
            return;
        }
        if ("*".equals(argument) || "all".equalsIgnoreCase(argument)) {
            this.resetAll(sender);
            return;
        }
        this.resetOther(sender, argument);
    }

    private void cancel(@NonNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Только для игроков.", NamedTextColor.RED));
            return;
        }
        boolean cancelled = this.plugin.get(StatResetRequestManager.class).cancel(player.getUniqueId());
        player.sendMessage(cancelled
            ? Component.text("Запрос на сброс статистики отозван.", NamedTextColor.GREEN)
            : Component.text("У вас нет запроса на рассмотрении.", NamedTextColor.YELLOW));
    }

    // ------------------------------------------------------------------ модерация

    private void resetOther(@NonNull CommandSender sender, @NonNull String target) {
        if (!sender.hasPermission(COMMAND_PERMISSION + "statreset.others")) {
            sender.sendMessage(Component.text(
                "У вас нет прав сбрасывать чужую статистику. "
                    + "Свою можно попросить сбросить командой /statreset без аргументов.",
                NamedTextColor.RED));
            return;
        }

        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        UUID targetId = resolve(statistics, target);
        if (targetId == null) {
            sender.sendMessage(Component.text(
                "Игрок \"" + target + "\" не найден в статистике.", NamedTextColor.RED));
            return;
        }

        if (!confirm(sender, targetId.toString())) {
            sender.sendMessage(Component.text(
                "Это сотрёт ВСЮ статистику игрока " + target + " без возможности восстановления. "
                    + "Повторите команду в течение 30 секунд, чтобы подтвердить.",
                NamedTextColor.YELLOW));
            return;
        }

        boolean existed = statistics.resetPlayer(targetId);
        sender.sendMessage(Component.text(existed
                ? "Статистика игрока " + target + " сброшена."
                : "У игрока " + target + " и так не было статистики.",
            existed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        this.plugin.getLogger().warning(senderName(sender) + " сбросил статистику игрока " + target);
    }

    private void resetAll(@NonNull CommandSender sender) {
        if (!sender.hasPermission(COMMAND_PERMISSION + "statreset.all")) {
            sender.sendMessage(Component.text(
                "У вас нет прав сбрасывать статистику всего сервера.", NamedTextColor.RED));
            return;
        }

        if (!confirm(sender, "*")) {
            sender.sendMessage(Component.text(
                "ВНИМАНИЕ: это сотрёт статистику ВСЕХ игроков без возможности восстановления. "
                    + "Повторите /statreset * в течение 30 секунд, чтобы подтвердить.",
                NamedTextColor.RED));
            return;
        }

        int count = this.plugin.get(StatisticsManager.class).resetEverything();
        sender.sendMessage(Component.text(
            "Статистика сервера сброшена. Затронуто профилей: " + count, NamedTextColor.GREEN));
        this.plugin.getLogger().warning(senderName(sender)
            + " СБРОСИЛ ВСЮ СТАТИСТИКУ СЕРВЕРА (" + count + " профилей)");
    }

    // ------------------------------------------------------------------ утилиты

    private static boolean confirm(@NonNull CommandSender sender, @NonNull String targetKey) {
        String key = senderName(sender) + "/" + targetKey;
        long now = System.currentTimeMillis();

        PENDING_CONFIRMS.entrySet().removeIf(entry -> now - entry.getValue() > CONFIRM_WINDOW_MILLIS);

        Long requestedAt = PENDING_CONFIRMS.get(key);
        if (requestedAt != null && now - requestedAt <= CONFIRM_WINDOW_MILLIS) {
            PENDING_CONFIRMS.remove(key);
            return true;
        }
        PENDING_CONFIRMS.put(key, now);
        return false;
    }

    @Nullable
    private static UUID resolve(@NonNull StatisticsManager statistics, @NonNull String name) {
        PlayerProfile known = statistics.findProfileByName(name);
        if (known != null) return known.getPlayerId();

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline.getUniqueId() : null;
    }

    @NonNull
    private static String senderName(@NonNull CommandSender sender) {
        return sender instanceof Player ? sender.getName() : "CONSOLE";
    }
}
