package ru.sortix.parkourbeat.commands;

import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.inventory.type.LevelsListMenu;

@Command(
    name = "moderate",
    aliases = {"moder", "moderation"})
@RequiredArgsConstructor
public class CommandModerate {

    private final ParkourBeat plugin;

    @Execute
    @Permission(PermissionConstants.MODERATE_LEVELS)
    public void onCommand(@Context Player sender) {
        new LevelsListMenu(this.plugin, LevelsListMenu.DisplayMode.MODERATION, sender, sender.getUniqueId()).open(sender);
    }
}
