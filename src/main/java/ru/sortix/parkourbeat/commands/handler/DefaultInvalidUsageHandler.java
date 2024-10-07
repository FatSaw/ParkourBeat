package ru.sortix.parkourbeat.commands.handler;

import dev.rollczi.litecommands.handler.result.ResultHandlerChain;
import dev.rollczi.litecommands.invalidusage.InvalidUsage;
import dev.rollczi.litecommands.invalidusage.InvalidUsageHandler;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.schematic.Schematic;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import ru.sortix.parkourbeat.utils.lang.LangOptions;

public class DefaultInvalidUsageHandler implements InvalidUsageHandler<CommandSender> {

    @Override
    public void handle(
        Invocation<CommandSender> invocation,
        InvalidUsage<CommandSender> commandSenderInvalidUsage,
        ResultHandlerChain<CommandSender> resultHandlerChain) {
        CommandSender sender = invocation.sender();
        Schematic schematic = commandSenderInvalidUsage.getSchematic();
        String lang = "";
        if(sender instanceof Player) {
        	lang = ((Player)sender).getLocale().toLowerCase();
        }
        String usage = LangOptions.command_usage.get(lang);
        if (schematic.isOnlyFirst()) {
            sender.sendMessage(String.format("%s: %s", usage, schematic.first()));
            return;
        }

        sender.sendMessage(String.format("%s", usage));
        for (String scheme : schematic.all()) {
            sender.sendMessage(String.format(" - %s", scheme));
        }
    }
}
