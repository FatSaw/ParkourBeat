package ru.sortix.parkourbeat.commands;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.type.ServerMenu;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

@Command(
    name = "menu",
    aliases = {"меню", "pbmenu"})
@RequiredArgsConstructor
public class CommandMenu {

    private final ParkourBeat plugin;

    @Execute
    @Permission(COMMAND_PERMISSION + "menu")
    public void onCommand(@Context Player player) {
        new ServerMenu(this.plugin, PlayerLang.of(player), player).open(player);
    }
}
