package ru.sortix.parkourbeat.commands;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.game.movement.GameMoveHandler;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

@Command(name = "backtol", aliases = {"backtolerance", "back-tolerance"})
public class CommandBackTol {

    private final ParkourBeat plugin;

    public CommandBackTol(ParkourBeat plugin) {
        this.plugin = plugin;
    }

    @Execute
    @Permission(COMMAND_PERMISSION + "backtol")
    public void onCommand(@Context CommandSender sender, @Arg("blocks") double blocks) {
        if (blocks < 0) blocks = 0;
        if (blocks > 20) blocks = 20;
        GameMoveHandler.setBackwardToleranceAndSave(this.plugin, blocks);
        sender.sendMessage(Component.text(
            Lang.raw(PlayerLang.of(sender), "auto.command_back_tol.on_command.1") + blocks + Lang.raw(PlayerLang.of(sender), "auto.command_back_tol.on_command.2"),
            NamedTextColor.GREEN));
    }

    @Execute
    @Permission(COMMAND_PERMISSION + "backtol")
    public void onShow(@Context CommandSender sender) {
        sender.sendMessage(Component.text(
            Lang.raw(PlayerLang.of(sender), "auto.command_back_tol.on_show.1") + GameMoveHandler.BACKWARD_TOLERANCE + Lang.raw(PlayerLang.of(sender), "auto.command_back_tol.on_show.2"), NamedTextColor.YELLOW));
    }
}
