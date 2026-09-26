package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.player.music.MusicTrack;
import ru.sortix.parkourbeat.player.music.TrackSlicerBridge;
import ru.sortix.parkourbeat.utils.text.PbText;


/**
 * ВЫБОР СЛОЖНОСТИ ПЕРЕД АВТОРАЗМЕТКОЙ.
 * <p>
 * Разбор трека приезжает один и тот же, а карта из него получается разная: сложность
 * решает, какие удары станут нотами. Поэтому спрашивается она ДО раскладки - перегенерить
 * ничего не придётся, разбор кэшируется на прокси.
 */
public class DiggerAutoMapMenu extends ParkourBeatInventory {

    private final @NonNull Level level;
    private final @NonNull DiggerLevelSettings settings;

    public DiggerAutoMapMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Level level) {
        super(plugin, 3, lang, PbText.of("&8Авторазметка: сложность"));
        this.level = level;
        this.settings = level.getLevelSettings().getGameSettings().getDiggerSettings();
        this.updateItems();
    }

    private void updateItems() {
        this.clearInventory();

        DiggerMapDifficulty[] values = DiggerMapDifficulty.values();
        for (int i = 0; i < values.length; i++) {
            DiggerMapDifficulty difficulty = values[i];
            // Только название. Жёсткость и паузу между нотами человек всё равно не
            // сравнивает в уме по четырём строкам подсказки - он выбирает по слову,
            // а числа смотрит потом в настройках карты, если они ему вообще нужны.
            this.setItem(2, 3 + i, ItemUtils.create(difficulty.getIcon(), meta -> {
                meta.displayName(PbText.item("&8◆ &6" + difficulty.getDisplay()));
            }), event -> this.run(event.getPlayer(), difficulty));
        }

        // НАСЫЩЕННОСТЬ ВЫБИРАЕТСЯ ОТДЕЛЬНО ОТ СЛОЖНОСТИ.
        //
        // Это разные вещи, и связывать их нельзя: сложность - про то, сколько нот и как
        // часто, насыщенность - про то, сколько света вокруг них. Человеку вполне может
        // хотеться лёгкую карту с полным разносом по эффектам или наоборот.
        //
        // Ряд стоит НАД сложностями, потому что выбирается первым: сложность запускает
        // разметку сразу, и после неё возвращаться к настройке уже некуда.
        DiggerShowIntensity[] intensities = DiggerShowIntensity.values();
        for (int i = 0; i < intensities.length; i++) {
            DiggerShowIntensity intensity = intensities[i];
            boolean chosen = intensity == this.intensity;

            // Тоже только название. Выбранный вариант виден по цвету и блеску, поэтому
            // строка «Выбрано» была не информацией, а повторением того, что и так видно.
            this.setItem(1, 4 + i, ItemUtils.create(intensity.getIcon(), meta -> {
                meta.displayName(PbText.item((chosen ? "&a&l" : "&8") + intensity.getDisplay()));
                if (chosen) meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }), event -> {
                this.intensity = intensity;
                event.getPlayer().playSound(event.getPlayer().getLocation(),
                    org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f, 1.4f);
                this.updateItems();
            });
        }

        this.fillBorder();
    }

    /** Выбранная насыщенность шоу. Держится до закрытия меню. */
    private DiggerShowIntensity intensity = DiggerShowIntensity.MEDIUM;

    /**
     * Заказать разбор и разложить карту.
     * <p>
     * Порядок попыток тот же, что и везде: готовый разбор скрипта, затем локальный файл,
     * затем прокси. Первое, что нашлось, и используется.
     */
    private void run(@NonNull Player player, @NonNull DiggerMapDifficulty difficulty) {
        MusicTrack track = this.level.getLevelSettings().getGameSettings().getMusicTrack();
        if (track == null) {
            player.sendMessage(PbText.of("&cСначала выбери трек уровня."));
            return;
        }

        ParkourBeat plugin = (ParkourBeat) this.plugin;
        player.closeInventory();

        DiggerAnalysis local = DiggerAnalysisStore.load(plugin, track.getId());
        if (local != null) {
            this.apply(player, local, difficulty, "разбор скрипта");
            return;
        }

        java.io.File file = DiggerTrackAnalyzer.findTrackFile(plugin, track.getId());
        if (file != null) {
            player.sendMessage(PbText.of("&7Разбираю трек..."));
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                DiggerTrackAnalyzer.Result result = DiggerTrackAnalyzer.analyze(file);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (result == null) {
                        this.askProxy(player, track, difficulty);
                        return;
                    }
                    DiggerAnalysis analysis = result.toAnalysis(track.getId());
                    analysis.setDurationMillis(result.durationMillis);
                    this.apply(player, analysis, difficulty, "локальный файл");
                });
            });
            return;
        }

        this.askProxy(player, track, difficulty);
    }

    private void askProxy(@NonNull Player player,
                          @NonNull MusicTrack track,
                          @NonNull DiggerMapDifficulty difficulty
    ) {
        ParkourBeat plugin = (ParkourBeat) this.plugin;
        TrackSlicerBridge bridge = plugin.get(TrackSlicerBridge.class);

        if (bridge == null || !bridge.requestAnalysis(player, track.getId(), analysis -> {
            if (!player.isOnline()) return;
            if (analysis == null) {
                player.sendMessage(PbText.of("&cПрокси не смогла разобрать трек."));
                player.sendMessage(PbText.of("&7Разложу по ровной сетке из настроек карты."));
                int bars = DiggerAutoMapper.barsFor(this.settings.getBpm(), 0);
                int placed = DiggerAutoMapper.generate(
                    this.level, bars, 2, DiggerAutoMapper.Pattern.ZIGZAG, true);
                player.sendMessage(PbText.of("&aРазметка&7: нот &f" + placed));
                return;
            }
            this.apply(player, analysis, difficulty, "разбор на прокси");
        })) {
            player.sendMessage(PbText.of("&cМост с прокси недоступен."));
            return;
        }

        player.sendMessage(PbText.of("&7Прокси разбирает трек, это несколько секунд..."));
    }

    /**
     * ПРОВЕРИТЬ ТОЧКУ ПОЯВЛЕНИЯ ПОСЛЕ РАЗМЕТКИ.
     * <p>
     * Разметка ставит блоки и переносит точку отсчёта карты - и после неё уровень порой
     * начинает считать точку появления неверной. Воспроизвести это у себя я не смог,
     * поэтому здесь не лечение конкретной причины, а проверка результата: годна ли
     * точка ПРЯМО СЕЙЧАС, по тем же двум правилам, по которым её проверяет сам уровень -
     * стоит ли она позади начала трассы и не завалена ли блоками.
     * <p>
     * Если нет, она отодвигается назад по направлению уровня, по блоку за раз, пока не
     * станет годной. Строителю об этом говорится прямо: молча двигать его точку - худший
     * вариант из возможных, он потом полдня будет искать, почему карта начинается не там.
     */
    private void ensureSpawnStillValid(@NonNull Player player) {
        org.bukkit.Location spawn = this.level.getSpawn();
        if (spawn == null) return;

        ru.sortix.parkourbeat.levels.settings.LevelSettings settings = this.level.getLevelSettings();
        if (ru.sortix.parkourbeat.world.LocationUtils.isValidSpawnPoint(spawn, settings)) return;

        ru.sortix.parkourbeat.levels.DirectionChecker checker = settings.getDirectionChecker();
        org.bukkit.util.Vector back = new org.bukkit.util.Vector();
        checker.add(back, -1.0D);

        for (int step = 1; step <= 8; step++) {
            org.bukkit.Location candidate = spawn.clone().add(back);
            if (ru.sortix.parkourbeat.world.LocationUtils.isValidSpawnPoint(candidate, settings)) {
                settings.getWorldSettings().setSpawn(candidate);
                player.sendMessage(PbText.of("&eТочка появления отодвинута назад на &f"
                    + step + " &eблок(а): на прежнем месте уровень считал её неверной."));
                return;
            }
            spawn = candidate;
        }

        player.sendMessage(PbText.of("&cТочку появления не удалось поправить автоматически. "
            + "Поставьте её вручную позади начала трассы."));
    }

    private void apply(@NonNull Player player,
                       @NonNull DiggerAnalysis analysis,
                       @NonNull DiggerMapDifficulty difficulty,
                       @NonNull String source
    ) {
        // ЧИСТЫЙ ЛИСТ ПЕРЕД РАЗМЕТКОЙ.
        //
        // Раньше новые ноты просто досыпались к старым. Разложить карту дважды - хоть
        // на другую сложность, хоть тем же нажатием второй раз - означало получить два
        // набора руд вперемешку: старые стоят на прежних местах, новые на своих, и
        // разобрать это можно только удалив всё вручную. Тем более что сложности
        // отличаются как раз плотностью, то есть смешивать их бессмысленно вдвойне.
        int removedNotes = this.settings.getNotesCount();
        this.settings.clearNotes(this.level.getWorld());
        if (removedNotes > 0) {
            player.sendMessage(PbText.of("&8Убрано прежних нот: " + removedNotes));
        }

        int before = analysis.size();
        analysis.filter(difficulty.getMinStrength(), difficulty.getMinGapMillis());
        // Счётчик берётся ПОСЛЕ отбора по силе: раньше здесь стояло число до него, и
        // на всех сложностях печаталось одно и то же «из 1500 ударов» независимо от
        // того, сколько их реально осталось.
        int kept = analysis.size();
        this.settings.setHardness(difficulty.getHardness());

        // ЗОНЫ СКОРОСТИ ПОДБИРАЮТСЯ ДО РАССТАНОВКИ НОТ.
        //
        // Порядок здесь не косметический: ноты кладутся по профилю скорости, поэтому
        // профиль обязан быть готов раньше. Поставить зоны после - значит разложить
        // карту под одну скорость, а ехать по другой.
        //
        // Прежние зоны заменяются целиком: смешивать свежий подбор с остатками от
        // предыдущего прогона - это гарантированные накладывающиеся отрезки.
        int zones = 0;
        if (DiggerTuning.AUTO_SPEED_ZONES) {
            this.settings.clearSpeedZones();
            for (DiggerSpeedZone zone : DiggerSpeedProfile.suggest(analysis)) {
                if (this.settings.addSpeedZone(zone)) zones++;
            }
        }

        int placed = DiggerAutoMapper.generateFromAudio(this.level, analysis,
            difficulty.getMinGapMillis(), true, true);

        // СВЕТОВОЕ ШОУ СОБИРАЕТСЯ ПО ТОМУ ЖЕ РАЗБОРУ, что и ноты: разделы трека, их
        // характер и такт у них общие, поэтому смена неба попадает ровно туда, где
        // меняется музыка, а не «примерно там же».
        DiggerAutoShow.Report showReport = null;
        if (DiggerTuning.AUTO_LIGHT_SHOW) {
            showReport = DiggerAutoShow.generate(this.level, analysis,
                this.intensity, DiggerTuning.AUTO_BIOME_ZONES);
        }

        player.sendMessage(PbText.of("&a" + difficulty.getDisplay() + "&7: нот &f" + placed
            + " &8(из " + kept + " отобранных, всего " + before + ")"));
        if (zones > 0) {
            player.sendMessage(PbText.of("&dЗоны скорости&7: &f" + zones
                + " &8(правятся в настройках карты)"));
        }
        this.ensureSpawnStillValid(player);

        if (showReport != null && showReport.total() > 0) {
            player.sendMessage(PbText.of("&bСветовое шоу&7: разделов &f" + showReport.sections
                + " &8| &7небо &f" + showReport.skyCues
                + " &7вспышки &f" + showReport.flashCues
                + " &7кирки &f" + showReport.pickaxeCues
                + " &7чудеса &f" + showReport.wonders
                + " &7биомы &f" + showReport.biomeZones));
        }
        player.sendMessage(PbText.of("&7Темп &f" + String.format("%.1f", analysis.getBpm())
            + "&7, жёсткость &f" + String.format("%.2f", difficulty.getHardness())
            + " &8(" + source + ")"));
    }
}
