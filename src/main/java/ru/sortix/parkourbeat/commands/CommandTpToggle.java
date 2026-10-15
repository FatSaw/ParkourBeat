package ru.sortix.parkourbeat.commands;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.player.PlayerSettingsManager;
import ru.sortix.parkourbeat.player.PlayerSettingsManager.TeleportAccess;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.lang.PlayerLang;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.Optional;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

/**
 * /tptoggle - кто может телепортироваться к игроку и наблюдать за ним.
 * <p>
 * Без аргумента режим переключается по кругу, с аргументом ставится напрямую:
 * {@code /tptoggle all|friends|none}.
 */
@Command(name = "tptoggle", aliases = {"tpaccept", "tpaccess"})
@RequiredArgsConstructor
public class CommandTpToggle {

    private final ParkourBeat plugin;

    @Execute
    @Permission(COMMAND_PERMISSION + "play")
    public void onCommand(@Context Player sender, @Arg Optional<String> modeOpt) {
        PlayerSettingsManager settings = this.plugin.get(PlayerSettingsManager.class);

        TeleportAccess access;
        if (modeOpt.isEmpty()) {
            access = settings.nextTeleportAccess(sender.getUniqueId());
        } else {
            TeleportAccess current = settings.getTeleportAccess(sender.getUniqueId());
            access = TeleportAccess.parse(modeOpt.get(), current);
            if (access == current && !matches(modeOpt.get(), current)) {
                sender.sendMessage(Lang.text(sender, "command.tptoggle.usage"));
                return;
            }
            settings.setTeleportAccess(sender.getUniqueId(), access);
        }

        settings.save();

        String lang = PlayerLang.of(sender);
        sender.sendMessage(Component.empty());
        for (Component line : Lang.lore(lang, "command.tptoggle.result",
            "%mode%", access.getDisplay(lang),
            "%description%", access.getDescription(lang))) {
            sender.sendMessage(line);
        }
        sender.sendMessage(Component.empty());
    }

    private static boolean matches(String raw, TeleportAccess access) {
        return TeleportAccess.parse(raw, access == TeleportAccess.ALL
            ? TeleportAccess.NOBODY : TeleportAccess.ALL) == access;
    }
}
