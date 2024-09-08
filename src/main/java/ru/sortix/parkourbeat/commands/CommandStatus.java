package ru.sortix.parkourbeat.commands;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.ModerationStatus;
import ru.sortix.parkourbeat.levels.settings.GameSettings;

import java.util.Optional;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

@Command(
    name = "edit"
)
@RequiredArgsConstructor
public class CommandStatus {

    private final ParkourBeat plugin;

    @Execute
    @Permission(COMMAND_PERMISSION + "status")
    public void onCommand(@Context Player sender,
                          @Arg("settings-console-owning") GameSettings gameSettings,
                          @Arg Optional<String> newStatusNameOpt
    ) {
        ModerationStatus currentStatus = gameSettings.getModerationStatus();

        if (newStatusNameOpt.isEmpty()) {
            sender.sendMessage("Статус уровня: " + currentStatus.getDisplayName());
            return;
        }

        ModerationStatus newStatus;
        try {
            newStatus = ModerationStatus.valueOf(newStatusNameOpt.get());
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("Неверный новый статус: " + newStatusNameOpt);
            return;
        }

        if (newStatus == currentStatus) {
            sender.sendMessage("Статус уровня уже " + currentStatus.getDisplayName());
            return;
        }

        gameSettings.setModerationStatus(newStatus);
        sender.sendMessage("Статус уровня изменён: " + currentStatus.getDisplayName() + " -> " + newStatus.getDisplayName());
    }
}
