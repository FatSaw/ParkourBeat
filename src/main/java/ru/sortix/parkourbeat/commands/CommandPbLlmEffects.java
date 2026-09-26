package ru.sortix.parkourbeat.commands;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.join.Join;
import dev.rollczi.litecommands.annotations.permission.Permission;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.type.editor.WonderEffectsMenu;
import ru.sortix.parkourbeat.utils.text.PbText;
import ru.sortix.parkourbeat.utils.wonder.WonderCommands;

/**
 * Технический ввод чудоэффектов.
 * <p>
 * Ровно этот формат выдают и встроенный помощник, и любая внешняя модель по инструкции
 * из репозитория. Строителю достаточно вставить строки в чат по одной.
 */
@Command(name = "pbllmeffects", aliases = {"pbllm", "pbeffects"})
public class CommandPbLlmEffects {

    private final ParkourBeat plugin;

    public CommandPbLlmEffects(ParkourBeat plugin) {
        this.plugin = plugin;
    }

    @Execute
    @Permission("parkourbeat.command.pbllmeffects")
    public void onCommand(@Context Player player, @Join String line) {
        if (line == null || line.trim().isEmpty()) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_pb_llm_effects.on_command.1")));
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_pb_llm_effects.on_command.2")));
            return;
        }

        UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(player);
        if (!(activity instanceof EditActivity)) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_pb_llm_effects.on_command.3")));
            return;
        }
        EditActivity editActivity = (EditActivity) activity;

        WonderCommands.Result result = WonderCommands.execute(
            player, editActivity.getLevel().getLightShow(), line);

        for (String messageLine : result.message.split("\n")) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_pb_llm_effects.on_command.4") + messageLine));
        }

        if (result.changed) {
            ru.sortix.parkourbeat.utils.wonder.WonderSave.now(this.plugin, editActivity.getLevel());
            editActivity.updateInventoriesOfAllEditors(
                WonderEffectsMenu.class, WonderEffectsMenu::updateAllItems);
        }
    }
}
