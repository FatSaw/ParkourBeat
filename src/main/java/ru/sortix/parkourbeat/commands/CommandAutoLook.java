package ru.sortix.parkourbeat.commands;

import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.world.AutoLookSettings;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

/**
 * Переключатель автовыравнивания камеры при входе на уровень.
 * <p>
 * {@code /offautolook} — выключить (или включить обратно, если уже выключено).
 * Значение пишется в config.yml, так что рестарт его не сбросит.
 */
@Command(name = "offautolook", aliases = {"autolook", "auto-look"})
public class CommandAutoLook {

    private final ParkourBeat plugin;

    public CommandAutoLook(ParkourBeat plugin) {
        this.plugin = plugin;
    }

    @Execute
    @Permission(COMMAND_PERMISSION + "autolook")
    public void onToggle(@Context CommandSender sender) {
        boolean enabled = !AutoLookSettings.ENABLED;
        AutoLookSettings.setAndSave(this.plugin, enabled);

        if (enabled) {
            sender.sendMessage(Component.text(
                "Автовыравнивание камеры при входе на уровень ВКЛЮЧЕНО.",
                NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(
                "Автовыравнивание камеры при входе на уровень ВЫКЛЮЧЕНО. "
                    + "Игроки будут заходить с той камерой, что была.",
                NamedTextColor.YELLOW));
        }
    }
}
