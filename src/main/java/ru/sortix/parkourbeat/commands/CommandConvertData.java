package ru.sortix.parkourbeat.commands;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import org.bukkit.command.CommandSender;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.constant.Messages;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.utils.shedule.FutureUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

@Command(name = "convertdata")
public class CommandConvertData {

    private final ParkourBeat plugin;
    private final LevelsManager levelsManager;

    public CommandConvertData(ParkourBeat plugin) {
        this.plugin = plugin;
        this.levelsManager = this.plugin.get(LevelsManager.class);
    }

    @Execute
    @Permission(COMMAND_PERMISSION + "convertdata")
    public void onCommand(@Context CommandSender sender,
                          @Arg("settings-console-owning") Optional<GameSettings> gameSettingsOpt
    ) {
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            if (gameSettingsOpt.isPresent()) {
                upgradeDataOnLevel(sender, gameSettingsOpt.get());
            } else {
                upgradeDataOnAllLevels(sender);
            }
        });
    }

    private void upgradeDataOnLevel(CommandSender sender, GameSettings gameSettings) {
        levelsManager.upgradeDataAsync(gameSettings.getUniqueId(), null).thenAccept(successResult -> (Boolean.TRUE.equals(successResult) ? LangOptions.level_convertation_one_success : LangOptions.level_convertation_one_fail).sendMsg(sender, new Placeholders("%level%", gameSettings.getDisplayNameLegacy())));
    }

    private void upgradeDataOnAllLevels(CommandSender sender) {
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        List<GameSettings> allSettings = new ArrayList<>(levelsManager.getAvailableLevelsSettings());
        allSettings.sort(Comparator.comparingLong(GameSettings::getCreatedAtMills));

        for (GameSettings settings : allSettings) {
            futures.add(
                levelsManager.upgradeDataAsync(settings.getUniqueId(), null).thenAccept(successResult -> {
                    if (Boolean.TRUE.equals(successResult)) {
                        success.getAndIncrement();
                    } else {
                        failed.getAndIncrement();
                    }
                }));
        }
        FutureUtils.mergeOneByOne(futures).thenAccept(unused -> LangOptions.level_convertation_multiple.sendMsg(sender, new Placeholders("%successcount%", Integer.toString(success.get())), new Placeholders("%failcount%", Integer.toString(failed.get()))));
    }
}
