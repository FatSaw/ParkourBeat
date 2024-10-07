package ru.sortix.parkourbeat.commands;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.constant.Messages;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

@Command(name = "delete")
@RequiredArgsConstructor
public class CommandDelete {

    private final ParkourBeat plugin;

    public static void deleteLevel(
        @NonNull ParkourBeat plugin, @NonNull CommandSender sender, @NonNull GameSettings settings) {
        LevelsManager levelsManager = plugin.get(LevelsManager.class);
        ActivityManager activityManager = plugin.get(ActivityManager.class);
        Placeholders levelplaceholder = new Placeholders("%level%", settings.getDisplayNameLegacy());
        Level loadedLevel = levelsManager.getLoadedLevel(settings.getUniqueId());
        if (loadedLevel != null) {
            for (Player player : loadedLevel.getWorld().getPlayers()) {
                if (player != sender) {
                	LangOptions.level_editor_delete_already.sendMsg(player, levelplaceholder);
                }
                activityManager.switchActivity(player, null, Settings.getLobbySpawn());
            }
        }

        levelsManager.deleteLevelAsync(settings).thenAccept(successResult -> {
        	(Boolean.TRUE.equals(successResult) ? LangOptions.level_editor_delete_success : LangOptions.level_editor_delete_fail).sendMsg(sender, levelplaceholder);
        });
    }

    @Execute
    @Permission(COMMAND_PERMISSION + "delete")
    public String onCommand(@Context CommandSender sender, @Arg("settings-console-owning") GameSettings gameSettings) {
        if (sender instanceof Player) {
        	LangOptions.level_editor_delete_useitem.sendMsg(sender);
            return null;
        }

        if (!gameSettings.isOwner(sender, true, true)) {
        	LangOptions.level_editor_delete_notowner.sendMsg(sender);
            return null;
        }

        deleteLevel(this.plugin, sender, gameSettings);
        return null;
    }
}
