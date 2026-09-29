package ru.sortix.parkourbeat.commands;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.digger.DiggerAnalysis;
import ru.sortix.parkourbeat.digger.DiggerAnalysisStore;
import ru.sortix.parkourbeat.digger.DiggerAutoMapper;
import ru.sortix.parkourbeat.digger.DiggerLevelSettings;
import ru.sortix.parkourbeat.digger.DiggerManager;
import ru.sortix.parkourbeat.digger.DiggerTuning;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.PickaxeCue;
import ru.sortix.parkourbeat.player.music.MusicTrack;
import org.bukkit.Material;
import ru.sortix.parkourbeat.utils.text.PbText;

/**
 * Команды режима «Копатель»: крутилки сервера, инструменты строителя и авторазметка.
 */
@Command(name = "digger")
@RequiredArgsConstructor
public class CommandDigger {

    private final ParkourBeat plugin;

    @Execute(name = "list")
    @Permission("parkourbeat.command.digger")
    public void onList(@Context CommandSender sender) {
        sender.sendMessage(PbText.of("&dКопатель &7- настройки сервера:"));
        for (String key : DiggerTuning.keys()) {
            sender.sendMessage(PbText.of("&8- &7" + key + "&8: &f" + DiggerTuning.get(key)));
        }
    }

    @Execute(name = "get")
    @Permission("parkourbeat.command.digger")
    public void onGet(@Context CommandSender sender, @Arg("key") String key) {
        Object value = DiggerTuning.get(key);
        if (value == null) {
            sender.sendMessage(PbText.of("&cНет такой настройки: &f" + key));
            return;
        }
        sender.sendMessage(PbText.of("&7" + key + "&8: &f" + value));
    }

    @Execute(name = "set")
    @Permission("parkourbeat.command.digger")
    public void onSet(@Context CommandSender sender, @Arg("key") String key, @Arg("value") String value) {
        if (!DiggerTuning.set(key, value)) {
            sender.sendMessage(PbText.of("&cНе принято: &f" + key + " &7= &f" + value));
            return;
        }
        DiggerTuning.save(this.plugin.getConfig().createSection("digger"));
        this.plugin.saveConfig();
        sender.sendMessage(PbText.of("&a" + key + " &7= &f" + DiggerTuning.get(key)));
    }

    @Execute(name = "tools")
    @Permission("parkourbeat.command.digger")
    public void onTools(@Context Player player) {
        Level level = this.manager().getEditedDiggerLevel(player);
        if (level == null) {
            player.sendMessage(PbText.of("&cЭто работает только в редакторе карты «Копателя»."));
            return;
        }
        this.manager().getEditor().giveTools(player);
    }

    @Execute(name = "grid")
    @Permission("parkourbeat.command.digger")
    public void onGrid(@Context Player player) {
        this.manager().getEditor().toggleGrid(player);
    }

    @Execute(name = "bpm")
    @Permission("parkourbeat.command.digger")
    public void onBpm(@Context Player player, @Arg("bpm") Double bpm) {
        Level level = this.manager().getEditedDiggerLevel(player);
        if (level == null) {
            player.sendMessage(PbText.of("&cЭто работает только в редакторе карты «Копателя»."));
            return;
        }
        DiggerLevelSettings settings = level.getLevelSettings().getGameSettings().getDiggerSettings();
        settings.setBpm(bpm);
        player.sendMessage(PbText.of("&aBPM: &f" + settings.getBpm()
            + " &7-> скорость &f" + String.format("%.2f", settings.resolveSpeed()) + " бл/с"
            + " &7(&f" + String.format("%.2f", settings.blocksPerBeat()) + " &7блока на долю)"));
    }

    @Execute(name = "automap")
    @Permission("parkourbeat.command.digger")
    public void onAutomap(@Context Player player,
                          @Arg("тактов") Integer bars,
                          @Arg("дробление") Integer subdivision,
                          @Arg("узор") String pattern
    ) {
        Level level = this.manager().getEditedDiggerLevel(player);
        if (level == null) {
            player.sendMessage(PbText.of("&cЭто работает только в редакторе карты «Копателя»."));
            return;
        }
        int placed = DiggerAutoMapper.generate(level, bars, subdivision,
            DiggerAutoMapper.Pattern.byName(pattern), true);
        player.sendMessage(PbText.of("&dАвторазметка&7: разложено нот &f" + placed));
    }

    @Execute(name = "analyze")
    @Permission("parkourbeat.command.digger")
    public void onAnalyze(@Context Player player) {
        Level level = this.manager().getEditedDiggerLevel(player);
        if (level == null) {
            player.sendMessage(PbText.of("&cЭто работает только в редакторе карты «Копателя»."));
            return;
        }

        String trackId = this.trackIdOf(level);
        if (trackId == null) {
            player.sendMessage(PbText.of("&cНа уровень ещё не выбран трек."));
            return;
        }

        DiggerAnalysis analysis = DiggerAnalysisStore.load(this.plugin, trackId);
        if (analysis == null) {
            player.sendMessage(PbText.of("&cРазбор трека не найден."));
            player.sendMessage(PbText.of("&7Файл: &f" + DiggerAnalysisStore.fileOf(this.plugin, trackId).getPath()));
            player.sendMessage(PbText.of("&7Сделать его: &fpython3 digger_analyze.py <трек.ogg> " + trackId));
            return;
        }

        player.sendMessage(PbText.of("&aРазбор найден&7: BPM &f" + String.format("%.2f", analysis.getBpm())
            + "&7, смещение &f" + analysis.getOffsetMillis() + " мс"
            + "&7, ударов &f" + analysis.size()));
        player.sendMessage(PbText.of("&7Разложить их: &f/pb digger automapaudio"));
    }

    @Execute(name = "automapaudio")
    @Permission("parkourbeat.command.digger")
    public void onAutomapAudio(@Context Player player) {
        Level level = this.manager().getEditedDiggerLevel(player);
        if (level == null) {
            player.sendMessage(PbText.of("&cЭто работает только в редакторе карты «Копателя»."));
            return;
        }

        String trackId = this.trackIdOf(level);
        DiggerAnalysis analysis = trackId == null ? null : DiggerAnalysisStore.load(this.plugin, trackId);
        if (analysis == null) {
            player.sendMessage(PbText.of("&cРазбор трека не найден. Сначала &f/pb digger analyze"));
            return;
        }

        int placed = DiggerAutoMapper.generateFromAudio(level, analysis, 0, true, true);
        player.sendMessage(PbText.of("&dРазметка по звуку&7: разложено нот &f" + placed
            + " &7(BPM карты выставлен на &f" + String.format("%.2f", analysis.getBpm()) + "&7)"));
    }

    @Execute(name = "pickaxecue")
    @Permission("parkourbeat.command.digger")
    public void onPickaxeCue(@Context Player player,
                             @Arg("начало_мс") Integer start,
                             @Arg("конец_мс") Integer end,
                             @Arg("кирка") String pickaxe
    ) {
        Level level = this.manager().getEditedDiggerLevel(player);
        if (level == null) {
            player.sendMessage(PbText.of("&cЭто работает только в редакторе карты «Копателя»."));
            return;
        }

        Material material = Material.matchMaterial(pickaxe.toUpperCase(java.util.Locale.ROOT));
        if (material == null) material = Material.matchMaterial(pickaxe.toUpperCase(java.util.Locale.ROOT) + "_PICKAXE");
        if (material == null) {
            player.sendMessage(PbText.of("&cНеизвестная кирка: &f" + pickaxe));
            return;
        }

        PickaxeCue cue = new PickaxeCue(start, end, material);
        if (!level.getLightShow().addPickaxeCue(cue)) {
            player.sendMessage(PbText.of("&cСписок кьюсов переполнен."));
            return;
        }
        player.sendMessage(PbText.of("&aКирка &f" + cue.getPickaxe().name()
            + " &7на &f" + cue.getStartTimecode() + " - " + cue.getEndTimecode()));
    }

    @Execute(name = "clear")
    @Permission("parkourbeat.command.digger")
    public void onClear(@Context Player player) {
        Level level = this.manager().getEditedDiggerLevel(player);
        if (level == null) {
            player.sendMessage(PbText.of("&cЭто работает только в редакторе карты «Копателя»."));
            return;
        }
        DiggerLevelSettings settings = level.getLevelSettings().getGameSettings().getDiggerSettings();
        int count = settings.getNotesCount();
        settings.clearNotes(level.getWorld());
        player.sendMessage(PbText.of("&cРазметка очищена: &f" + count + " &cнот"));
    }

    /**
     * Числа судейства прямо в чат.
     * <p>
     * Без них разбираться в «почему промах» можно только вслепую: на глаз не видно ни
     * пройденного расстояния, ни отклонения, ни того, куда легли ноты.
     */
    @Execute(name = "debug")
    @Permission("parkourbeat.command.digger")
    public void onDebug(@Context Player player) {
        ru.sortix.parkourbeat.digger.DiggerGame game = this.manager().getGame(player);
        if (game == null) {
            player.sendMessage(PbText.of("&cЗабег не идёт. Запусти тест и повтори команду."));
            return;
        }
        game.setDebug(true);
        player.sendMessage(PbText.of("&aОтладка включена на этот забег."));
    }

    /**
     * Где лежит файл трека.
     * <p>
     * Разбор темпа умеет читать только то, что нашёл, и молчаливое «не найдено» ничего
     * не объясняет. Команда показывает, что именно нашлось и где искали.
     */
    @Execute(name = "track")
    @Permission("parkourbeat.command.digger")
    public void onTrack(@Context Player player) {
        Level level = this.manager().getEditedDiggerLevel(player);
        if (level == null) {
            player.sendMessage(PbText.of("&cЭто работает только в редакторе карты «Копателя»."));
            return;
        }

        MusicTrack track = level.getLevelSettings().getGameSettings().getMusicTrack();
        if (track == null) {
            player.sendMessage(PbText.of("&cТрек уровня не выбран."));
            return;
        }

        player.sendMessage(PbText.of("&7Трек: &f" + track.getId() + " &8(" + track.getName() + ")"));
        java.io.File file = ru.sortix.parkourbeat.digger.DiggerTrackAnalyzer
            .findTrackFile(this.plugin, track.getId());

        if (file == null) {
            player.sendMessage(PbText.of("&cФайл не найден. Искал в:"));
            for (java.io.File root : ru.sortix.parkourbeat.digger.DiggerTrackAnalyzer
                .searchRoots(this.plugin)) {
                int audio = ru.sortix.parkourbeat.digger.DiggerTrackAnalyzer.countAudio(root, 0);
                player.sendMessage(PbText.of("&8 " + root.getAbsolutePath()
                    + " &7" + (root.exists() ? "(есть, звуковых файлов " + audio + ")" : "(нет)")));
            }
            player.sendMessage(PbText.of("&7Если музыка лежит в другом месте:"));
            player.sendMessage(PbText.of("&f /pb digger set music_folder <путь к папке>"));
            return;
        }

        player.sendMessage(PbText.of("&aНайден: &f" + file.getAbsolutePath()));
        player.sendMessage(PbText.of("&7Разбираю..."));

        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            ru.sortix.parkourbeat.digger.DiggerTrackAnalyzer.Result result =
                ru.sortix.parkourbeat.digger.DiggerTrackAnalyzer.analyze(file);
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                if (!player.isOnline()) return;
                if (result == null) {
                    player.sendMessage(PbText.of("&cФайл найден, но не читается как ogg."));
                    return;
                }
                player.sendMessage(PbText.of("&aДлина: &f" + (result.durationMillis / 1000) + " с"
                    + " &7темп: &f" + String.format("%.1f", result.bpm)
                    + " &7ударов: &f" + result.onsets.size()));
            });
        });
    }

    @Execute(name = "stop")
    @Permission("parkourbeat.command.digger")
    public void onStop(@Context Player player) {
        this.manager().stop(player);
        player.sendMessage(PbText.of("&7Забег остановлен."));
    }

    @Nullable
    private String trackIdOf(@NonNull Level level) {
        MusicTrack track = level.getLevelSettings().getGameSettings().getMusicTrack();
        return track == null ? null : track.getId();
    }

    @NonNull
    private DiggerManager manager() {
        return this.plugin.get(DiggerManager.class);
    }
}
