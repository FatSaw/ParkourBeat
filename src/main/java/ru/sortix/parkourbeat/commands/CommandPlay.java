// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/commands/CommandPlay.java
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
import ru.sortix.parkourbeat.inventory.type.LevelDetailsMenu;
import ru.sortix.parkourbeat.inventory.type.LevelsListMenu;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import java.util.Optional;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

@Command(
    name = "play",
    aliases = {"parkourbeat", "pb", "levels", "level", "lvl", "lvls"})
@RequiredArgsConstructor
public class CommandPlay {

    private final ParkourBeat plugin;

    @Execute
    @Permission(COMMAND_PERMISSION + "play")
    public void onCommand(@Context Player sender, @Arg("settings-players-all") Optional<GameSettings> gameSettingsOpt) {
        this.open(sender, gameSettingsOpt);
    }

    @Execute(name = "play")
    @Permission(COMMAND_PERMISSION + "play")
    public void onPlaySub(@Context Player sender, @Arg("settings-players-all") Optional<GameSettings> gameSettingsOpt) {
        this.open(sender, gameSettingsOpt);
    }

    private void open(Player sender, Optional<GameSettings> gameSettingsOpt) {
        String lang = PlayerLang.of(sender);
        if (gameSettingsOpt.isEmpty()) {
            new LevelsListMenu(this.plugin, lang, LevelsListMenu.DisplayMode.RANKED, sender, sender.getUniqueId()).open(sender);
            return;
        }
        GameSettings settings = gameSettingsOpt.get();
        if (!settings.isAccessibleForPlaying(sender, true)) {
            LangOptions.level_play_noaccess.sendMsg(sender);
            return;
        }
        new LevelDetailsMenu(this.plugin, lang, settings, sender).open(sender);
    }
}
