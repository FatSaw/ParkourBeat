package ru.sortix.parkourbeat.commands;

import ru.sortix.parkourbeat.utils.lang.Lang;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.type.PlayerStatisticsMenu;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.stats.PlayerProfile;
import ru.sortix.parkourbeat.stats.ProfileSummary;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.Optional;
import java.util.UUID;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

@Command(name = "stat", aliases = {"stats", "statistic"})
@RequiredArgsConstructor
public class CommandStat {

    private final ParkourBeat plugin;

    @Execute
    @Permission(COMMAND_PERMISSION + "play")
    public void onCommand(@Context Player sender, @Arg Optional<String> targetNameOpt) {
        String lang = PlayerLang.of(sender);

        if (targetNameOpt.isEmpty()) {
            new PlayerStatisticsMenu(this.plugin, lang, sender, sender.getUniqueId(), sender.getName()).open(sender);
            return;
        }

        String targetName = targetNameOpt.get();
        UUID targetId = null;
        String resolvedName = targetName;

        // 1. Ищем среди онлайна
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            targetId = online.getUniqueId();
            resolvedName = online.getName();
        } else {
            // 2. Ищем в кэше профилей плагина
            StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
            PlayerProfile profile = statistics.findProfileByName(targetName);
            if (profile != null) {
                targetId = profile.getPlayerId();
                resolvedName = profile.getPlayerName();
            } else {
                // 3. Ищем во всей базе лидеров (все игроки с хотя бы 1 пройденным уровнем)
                for (ProfileSummary summary : statistics.getLeaderboard(StatisticsManager.SortKey.PP)) {
                    if (summary.getPlayerName().equalsIgnoreCase(targetName)) {
                        targetId = summary.getPlayerId();
                        resolvedName = summary.getPlayerName();
                        break;
                    }
                }
            }
        }

        // 4. Фолбэк на кэш самого майнкрафта
        if (targetId == null) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
            if (offline.hasPlayedBefore() || offline.isOnline()) {
                targetId = offline.getUniqueId();
                resolvedName = offline.getName() != null ? offline.getName() : targetName;
            }
        }

        if (targetId == null) {
            sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_stat.on_command.1") + targetName + Lang.raw(PlayerLang.of(sender), "auto.command_stat.on_command.2")));
            return;
        }

        new PlayerStatisticsMenu(this.plugin, lang, sender, targetId, resolvedName).open(sender);
    }
}
