package ru.sortix.parkourbeat.commands;

import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.inventory.type.AllLevelsMenu;
import ru.sortix.parkourbeat.utils.lang.PlayerLang;

/**
 * {@code /alllvls} - список ВСЕХ уровней сервера, включая приватные.
 * <p>
 * Право то же, что и на правку чужих уровней: кому можно зайти в чужой уровень, тому
 * можно и увидеть, что он вообще существует.
 */
@Command(name = "alllvls", aliases = {"alllevels", "лвлы"})
@RequiredArgsConstructor
public class CommandAllLevels {
    private final ParkourBeat plugin;

    @Execute
    @Permission(PermissionConstants.EDIT_OTHERS_LEVELS)
    public void onCommand(@Context Player sender) {
        new AllLevelsMenu(this.plugin, PlayerLang.of(sender)).open(sender);
    }
}
