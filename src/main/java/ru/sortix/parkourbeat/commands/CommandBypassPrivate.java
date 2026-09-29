package ru.sortix.parkourbeat.commands;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.player.PlayerSettingsManager;

import ru.sortix.parkourbeat.utils.text.PbText;
@Command(name = "bypassprivate")
@RequiredArgsConstructor
public class CommandBypassPrivate {

    private final ParkourBeat plugin;

    @Execute
    @Permission("parkourbeat.command.bypassprivate")
    public void onCommand(@Context Player sender) {
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
        PlayerSettingsManager settings = this.plugin.get(PlayerSettingsManager.class);

        if (settings.hasPrivateBypass(sender.getUniqueId())) {
            sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_bypass_private.on_command.1")));
            return;
        }

        settings.grantPrivateBypass(sender.getUniqueId());
        sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_bypass_private.on_command.2")));
        sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_bypass_private.on_command.3")));
    }
}
