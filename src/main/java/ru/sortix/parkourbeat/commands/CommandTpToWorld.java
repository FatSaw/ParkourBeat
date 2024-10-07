package ru.sortix.parkourbeat.commands;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.type.LevelsListMenu;
import ru.sortix.parkourbeat.levels.settings.GameSettings;

import java.util.Optional;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

@Command(name = "tptoworld")
@RequiredArgsConstructor
public class CommandTpToWorld {

    private final ParkourBeat plugin;

    @Execute
    @Permission(COMMAND_PERMISSION + "tptoworld")
    public void onCommand(@Context Player sender, @Arg("settings-players-all") Optional<GameSettings> gameSettingsOpt) {
        if (gameSettingsOpt.isEmpty()) {

        	String lang = sender.getLocale().toLowerCase();
            new LevelsListMenu(this.plugin, lang, LevelsListMenu.DisplayMode.RANKED, sender, sender.getUniqueId()).open(sender);
            return;
        }
        LevelsListMenu.startSpectating(this.plugin, sender, gameSettingsOpt.get());
    }
}
