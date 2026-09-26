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
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.player.DebugModeManager;

import ru.sortix.parkourbeat.utils.text.PbText;
@Command(name = "debugmode", aliases = {"pbdebug"})
@RequiredArgsConstructor
public class CommandDebugMode {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ParkourBeat plugin;

    @Execute
    @Permission(PermissionConstants.DEBUG_MODE)
    public void onCommand(@Context Player player) {
        boolean enabled = this.plugin.get(DebugModeManager.class).toggle(player);
        player.sendMessage(PbText.of(enabled
            ? Lang.raw(PlayerLang.of(player), "auto.command_debug_mode.on_command.1")
            : Lang.raw(PlayerLang.of(player), "auto.command_debug_mode.on_command.2")));
    }
}
