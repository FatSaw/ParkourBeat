package ru.sortix.parkourbeat.commands;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.duel.DuelManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.Optional;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

/**
 * {@code /duel <карта>} - встать в очередь на дуэль.
 * <p>
 * Дуэль - это два игрока на одной карте, каждый по своей трассе. Первый, кто ввёл
 * команду, ждёт соперника; второй запускает забег для обоих. Повторная команда на той
 * же карте выводит из очереди.
 */
@Command(name = "duel", aliases = {"duels", "vs"})
@RequiredArgsConstructor
public class CommandDuel {
    private final ParkourBeat plugin;

    @Execute
    @Permission(COMMAND_PERMISSION + "play")
    public void onCommand(@Context Player sender,
                          @Arg("settings-players-all") Optional<GameSettings> settingsOpt) {
        if (settingsOpt.isEmpty()) {
            // Без аргумента открываем список - но только дуэльных карт: искать их
            // глазами среди сотни обычных уровней никто не станет.
            ru.sortix.parkourbeat.inventory.type.LevelsListMenu menu =
                new ru.sortix.parkourbeat.inventory.type.LevelsListMenu(
                    this.plugin,
                    ru.sortix.parkourbeat.utils.lang.PlayerLang.of(sender),
                    ru.sortix.parkourbeat.inventory.type.LevelsListMenu.DisplayMode.RANKED,
                    sender,
                    sender.getUniqueId());
            menu.setOnlyDuel(true);
            menu.open(sender);
            sender.sendMessage(PbText.of("&7Выберите карту - вы встанете в очередь на дуэль."));
            return;
        }
        this.plugin.get(DuelManager.class).joinQueue(sender, settingsOpt.get());
    }
}
