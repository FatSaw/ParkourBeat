// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/commands/CommandLevelStatReset.java
package ru.sortix.parkourbeat.commands;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.utils.text.PbText;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

/**
 * СБРОС ТОПА ОДНОГО УРОВНЯ.
 * <p>
 * Нужна там, где меняется сам способ подсчёта: точность, веса, формула очков. Записи в
 * топе хранят уже готовое число, а не исходные попадания, поэтому пересчитать их задним
 * числом нечем - старый рекорд остаётся посчитанным по старым правилам и стоит рядом с
 * новыми, будучи с ними несопоставимым.
 * <p>
 * УДАЛЯЮТСЯ ТОЛЬКО РЕКОРДЫ. История забегов остаётся: это личный архив игрока, он
 * ничего не искажает, и стирать его заодно с топом было бы грубо.
 * <p>
 * АЛИАСОВ У КОМАНДЫ НЕТ, И ЭТО ВАЖНО. Сначала я повесил на неё {@code statreset} - и она
 * склеилась с уже существующей командой того же имени, которая принимает ИМЯ ИГРОКА.
 * LiteCommands объединяет одноимённые команды в одно дерево, поэтому ввод уходил в чужой
 * обработчик, а тот честно отвечал, что такого игрока в статистике нет. Любое новое имя
 * здесь нужно сперва проверять на совпадение с существующими.
 */
@Command(name = "lvlstatreset")
@RequiredArgsConstructor
public class CommandLevelStatReset {

    private final ParkourBeat plugin;

    /**
     * @param level номер уровня, его уникальное имя или UUID - что удобнее под рукой
     */
    @Execute
    @Permission(COMMAND_PERMISSION + "moderate")
    public void onCommand(@Context CommandSender sender, @Arg String level) {
        // Разбор строки делает LevelsManager: он уже умеет и номер, и имя, и UUID, и
        // повторять эту логику здесь означало бы разойтись с ней при первой же правке.
        GameSettings settings = this.plugin.get(LevelsManager.class).findLevel(level);
        if (settings == null) {
            sender.sendMessage(PbText.of("&cУровень \"" + level + "\" не найден."));
            sender.sendMessage(PbText.of("&7Укажите номер уровня, его имя или UUID."));
            return;
        }

        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        if (statistics == null) {
            sender.sendMessage(PbText.of("&cСтатистика недоступна."));
            return;
        }

        int removed = statistics.resetLevelRecords(settings.getUniqueId());

        sender.sendMessage(PbText.of("&aТоп сброшен: &f"
            + settings.getDisplayNameLegacy(false)
            + " &8(#" + settings.getUniqueNumber() + ")"));
        sender.sendMessage(PbText.of("&7Убрано записей: &f" + removed
            + " &8(история забегов не тронута)"));
    }
}
