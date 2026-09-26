package ru.sortix.parkourbeat.commands;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.join.Join;
import dev.rollczi.litecommands.annotations.permission.Permission;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.boards.Board;
import ru.sortix.parkourbeat.boards.BoardType;
import ru.sortix.parkourbeat.boards.BoardsManager;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.Locale;

/**
 * Установка экранов.
 * <p>
 * Строитель выкладывает из наблюдателей прямоугольник любой формы, смотрит на него и пишет
 * тип борда. Размер, угол и сторона считаются сами: плагину достаточно одного блока под
 * прицелом, чтобы найти всю стенку целиком.
 */
@Command(name = "boards", aliases = {"board", "pbboards"})
public class CommandBoards {

    private final ParkourBeat plugin;

    public CommandBoards(ParkourBeat plugin) {
        this.plugin = plugin;
    }

    @Execute
    @Permission("parkourbeat.command.boards")
    public void onCommand(@Context Player player, @Join String line) {
        BoardsManager manager = this.plugin.get(BoardsManager.class);
        String argument = line == null ? "" : line.trim().toLowerCase(Locale.ROOT);

        if (argument.isEmpty() || argument.equals("help") || argument.equals("?")) {
            this.help(player, manager);
            return;
        }

        if (argument.equals("list")) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.on_command.1") + manager.all().size()));
            for (Board board : manager.all()) {
                player.sendMessage(PbText.of("&8 - &f" + board.getId() + " &8· &7" + board.getType().getDisplay()
                    + " &8· &7" + board.getWidth() + "x" + board.getHeight()
                    + " &8· &7" + board.getWorldName() + " " + board.getOriginX() + " "
                    + board.getOriginY() + " " + board.getOriginZ()));
            }
            return;
        }

        if (argument.equals("reload")) {
            manager.reload();
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.on_command.2")));
            return;
        }

        if (argument.equals("remove") || argument.equals("del") || argument.equals("delete")) {
            Board board = manager.looking(player);
            if (board == null) {
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.on_command.3")));
                return;
            }
            manager.remove(board);
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.on_command.4") + board.getId() + Lang.raw(PlayerLang.of(player), "auto.command_boards.on_command.5")));
            return;
        }

        BoardType type = BoardType.byKey(argument);
        if (type == null) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.on_command.6") + BoardType.keys()));
            return;
        }

        String problem = manager.create(player, type);
        if (problem != null) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.on_command.7") + problem));
            return;
        }
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.on_command.8") + type.getDisplay() + Lang.raw(PlayerLang.of(player), "auto.command_boards.on_command.9")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.on_command.10")));
    }

    private void help(Player player, BoardsManager manager) {
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.help.1")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.help.2")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.help.3")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.help.4")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.help.5")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.help.6")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.help.7")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.help.8")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_boards.help.9") + manager.all().size()));
    }
}
