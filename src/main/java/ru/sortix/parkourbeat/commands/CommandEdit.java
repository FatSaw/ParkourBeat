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
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.type.editor.EditorSessionMenu;

import java.util.Optional;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

@Command(name = "edit")
@RequiredArgsConstructor
public class CommandEdit {
    private final ParkourBeat plugin;
    @Execute
    @Permission(COMMAND_PERMISSION + "edit")
    public void onCommand(@Context Player sender, @Arg("settings-players-owning") Optional<GameSettings> gameSettingsOpt) {
        String lang = sender.getLocale().toLowerCase();
        if (gameSettingsOpt.isEmpty()) {
            UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(sender);
            if (activity instanceof EditActivity editActivity && !editActivity.isTesting()) {
                new EditorSessionMenu(this.plugin, lang, editActivity).open(sender);
                return;
            }
            new LevelsListMenu(this.plugin, lang, LevelsListMenu.DisplayMode.SELF, sender, sender.getUniqueId()).open(sender);
            return;
        }
        LevelsListMenu.startEditing(this.plugin, sender, gameSettingsOpt.get());
    }
}
