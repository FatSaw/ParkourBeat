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
            "Допуск бега назад: " + blocks + " блоков (сохранено). 0 = ваниль (строго).",
            NamedTextColor.GREEN));
    }

    @Execute
    @Permission(COMMAND_PERMISSION + "backtol")
    public void onShow(@Context CommandSender sender) {
        sender.sendMessage(Component.text(
            "Текущий допуск бега назад: " + GameMoveHandler.BACKWARD_TOLERANCE + " блоков", NamedTextColor.YELLOW));
    }
}
