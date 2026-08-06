package ru.sortix.parkourbeat.commands;

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

@Command(name = "lookangle", aliases = {"maxlookangle", "look-angle"})
public class CommandBackTolerance {

    private final ParkourBeat plugin;

    public CommandBackTolerance(ParkourBeat plugin) {
        this.plugin = plugin;
    }

    @Execute
    @Permission(COMMAND_PERMISSION + "lookangle")
    public void onCommand(@Context CommandSender sender, @Arg("degrees") double degrees) {
        if (degrees < 0) degrees = 0;
        if (degrees > 180) degrees = 180;
        GameMoveHandler.setMaxLookAngleAndSave(this.plugin, degrees);
        sender.sendMessage(Component.text(
            "Максимальный угол взгляда: " + degrees + "° (сохранено).", NamedTextColor.GREEN));
    }

    @Execute
    @Permission(COMMAND_PERMISSION + "lookangle")
    public void onShow(@Context CommandSender sender) {
        sender.sendMessage(Component.text(
            "Угол взгляда: " + GameMoveHandler.MAX_LOOK_ANGLE
                + "° | Допуск бега назад: " + GameMoveHandler.BACKWARD_TOLERANCE + " блоков", NamedTextColor.YELLOW));
    }
}
