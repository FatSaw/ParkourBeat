// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/commands/CommandLevelStat.java
package ru.sortix.parkourbeat.commands;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.type.LevelTopMenu;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

@Command(name = "lvlstat", aliases = {"lvlstats"})
@RequiredArgsConstructor
public class CommandLevelStat {

    private final ParkourBeat plugin;

    @Execute
    @Permission(COMMAND_PERMISSION + "play")
    public void onCommand(@Context Player sender, @Arg("settings-players-all") GameSettings gameSettings) {
        if (!gameSettings.isAccessibleForPlaying(sender, true)) {
            LangOptions.level_play_noaccess.sendMsg(sender);
            return;
        }
        new LevelTopMenu(this.plugin, PlayerLang.of(sender), gameSettings, sender).open(sender);
    }
}
