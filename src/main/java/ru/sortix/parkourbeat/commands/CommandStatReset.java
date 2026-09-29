// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/commands/CommandStatReset.java
package ru.sortix.parkourbeat.commands;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

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
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.stats.PlayerProfile;
import ru.sortix.parkourbeat.stats.StatResetRequest;
import ru.sortix.parkourbeat.stats.StatResetRequestManager;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

@Command(name = "statreset")
@RequiredArgsConstructor
public class CommandStatReset {

    private static final long CONFIRM_WINDOW_MILLIS = 30_000L;
    private static final Map<String, Long> PENDING_CONFIRMS = new ConcurrentHashMap<>();

    private final ParkourBeat plugin;

    // ------------------------------------------------------------------ игрок

    @Execute
    @Permission(COMMAND_PERMISSION + "statreset")
    public void request(@Context Player sender) {
        StatResetRequestManager requests = this.plugin.get(StatResetRequestManager.class);

        StatResetRequest existing = requests.get(sender.getUniqueId());
        if (existing != null && existing.isPending()) {
            sender.sendMessage(Lang.text(PlayerLang.of(sender), "command.statreset.already_pending",
                "%days%", String.valueOf(existing.getAgeDays())));
            return;
        }

        StatResetRequest created = requests.create(sender);
        if (created == null) {
            sender.sendMessage(Lang.text(PlayerLang.of(sender), "command.statreset.create_failed"));
            return;
        }

        for (Component line : Lang.lore(PlayerLang.of(sender), "command.statreset.created",
            "%days%", String.valueOf(StatResetRequestManager.REVIEW_DAYS))) {
            sender.sendMessage(line);
        }
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
        if ("recalc".equalsIgnoreCase(argument)) {
            if (sender.hasPermission(COMMAND_PERMISSION + "statreset.all")) {
                sender.sendMessage(Lang.text(PlayerLang.of(sender), "command.statreset.recalc_started"));
                this.plugin.get(StatisticsManager.class).recalculateScoresAsync(sender);
            } else {
                sender.sendMessage(Lang.text(PlayerLang.of(sender), "command.statreset.noperm_recalc"));
            }
            return;
        }
        this.resetOther(sender, argument);
    }

    private void cancel(@NonNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.text(PlayerLang.of(sender), "command.playersonly"));
            return;
        }
        boolean cancelled = this.plugin.get(StatResetRequestManager.class).cancel(player.getUniqueId());
        player.sendMessage(Lang.text(player, cancelled
            ? "command.statreset.cancelled"
            : "command.statreset.nothing_pending"));
    }

    // ------------------------------------------------------------------ модерация

    private void resetOther(@NonNull CommandSender sender, @NonNull String target) {
        if (!sender.hasPermission(COMMAND_PERMISSION + "statreset.others")) {
            sender.sendMessage(Lang.text(PlayerLang.of(sender), "command.statreset.noperm_others"));
            return;
        }

        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        UUID targetId = resolve(statistics, target);
        if (targetId == null) {
            sender.sendMessage(Lang.text(PlayerLang.of(sender), "command.statreset.notfound",
                "%player%", target));
            return;
        }

        if (!confirm(sender, targetId.toString())) {
            sender.sendMessage(Lang.text(PlayerLang.of(sender), "command.statreset.confirm_player",
                "%player%", target));
            return;
        }

        boolean existed = statistics.resetPlayer(targetId);
        sender.sendMessage(Lang.text(PlayerLang.of(sender), existed
                ? "command.statreset.done_player"
                : "command.statreset.nothing_player",
            "%player%", target));
        this.plugin.getLogger().warning(senderName(sender) + Lang.raw(PlayerLang.of(sender), "auto.command_stat_reset.reset_other.1") + target);
    }

    private void resetAll(@NonNull CommandSender sender) {
        if (!sender.hasPermission(COMMAND_PERMISSION + "statreset.all")) {
            sender.sendMessage(Lang.text(PlayerLang.of(sender), "command.statreset.noperm_all"));
            return;
        }

        if (!confirm(sender, "*")) {
            sender.sendMessage(Lang.text(PlayerLang.of(sender), "command.statreset.confirm_all"));
            return;
        }

        int count = this.plugin.get(StatisticsManager.class).resetEverything();
        sender.sendMessage(Lang.text(PlayerLang.of(sender), "command.statreset.done_all",
            "%count%", String.valueOf(count)));
        this.plugin.getLogger().warning(senderName(sender)
            + Lang.raw(PlayerLang.of(sender), "auto.command_stat_reset.reset_all.1") + count + Lang.raw(PlayerLang.of(sender), "auto.command_stat_reset.reset_all.2"));
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
