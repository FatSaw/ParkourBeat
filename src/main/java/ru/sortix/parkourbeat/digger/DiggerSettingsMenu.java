package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;

/**
 * НАСТРОЙКИ КАРТЫ.
 * <p>
 * Главная ручка здесь - BPM: из него считается скорость полёта и шаг сетки, по
 * которой раскладываются руды. Поменял темп - карта переехала целиком, руды остались
 * на местах, ритм не поехал.
 */
public class DiggerSettingsMenu extends ParkourBeatInventory {

    private final @NonNull Level level;
    private final @NonNull DiggerLevelSettings settings;

    public DiggerSettingsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Level level) {
        super(plugin, 5, lang, PbText.of("&8Копатель: настройки карты"));
        this.level = level;
        this.settings = level.getLevelSettings().getGameSettings().getDiggerSettings();
        this.updateItems();
    }

    private void reopen(@NonNull Player player) {
        new DiggerSettingsMenu((ParkourBeat) this.plugin, this.lang, this.level).open(player);
    }

    private void click(@NonNull Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
    }

    /**
     * АВТОРАЗМЕТКА.
     * <p>
     * Без выбранного трека она бессмысленна: раскладывать ноты не подо что, и результат
     * всё равно придётся стирать. Поэтому трек проверяется первым.
     * <p>
     * Темп сервер сам из ogg не достанет - декодера звука в нём нет и не будет.
     * Его считает внешний скрипт, а плагин читает готовый разбор. Если разбора нет,
     * остаётся разметка по сетке заданного вручную BPM, и об этом честно говорится.
     */
    /**
     * РАЗМЕТКА ПО РОВНОЙ СЕТКЕ.
     * <p>
     * Запасной вариант на Shift: ноты ложатся строго по долям темпа из настроек карты,
     * без всякого разбора звука. Нужен, когда прокси недоступна или когда трек такой,
     * что автоматика на нём путается.
     * <p>
     * Длина трека всё равно выясняется - иначе разметка обрывается на условных тридцати
     * двух тактах, то есть примерно на минуте, какой бы длинной песня ни была.
     */
    private void runGridMap(@NonNull Player player) {
        ru.sortix.parkourbeat.player.music.MusicTrack track =
            this.level.getLevelSettings().getGameSettings().getMusicTrack();

        if (track == null) {
            player.sendMessage(PbText.of("&cСначала выбери трек уровня."));
            return;
        }

        ParkourBeat plugin = (ParkourBeat) this.plugin;
        player.closeInventory();

        // Файл рядом с сервером - читаем длину сами, это доли секунды.
        java.io.File local = DiggerTrackAnalyzer.findTrackFile(plugin, track.getId());
        if (local != null) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                DiggerTrackAnalyzer.Result result = DiggerTrackAnalyzer.analyze(local);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) this.gridMap(player, result == null ? 0 : result.durationMillis);
                });
            });
            return;
        }

        // Файлов здесь нет: музыка живёт на прокси, длину знает она.
        ru.sortix.parkourbeat.player.music.TrackSlicerBridge bridge =
            plugin.get(ru.sortix.parkourbeat.player.music.TrackSlicerBridge.class);

        if (bridge == null || !bridge.requestAnalysis(player, track.getId(), analysis -> {
            if (!player.isOnline()) return;
            this.gridMap(player, analysis == null ? 0 : analysis.getDurationMillis());
        })) {
            this.gridMap(player, 0);
            return;
        }

        player.sendMessage(PbText.of("&7Спрашиваю длину трека у прокси..."));
    }

    /** Разметка по ровной сетке темпа на всю известную длину трека. */
    private void gridMap(@NonNull Player player, int durationMillis) {
        int bars = DiggerAutoMapper.barsFor(this.settings.getBpm(), durationMillis);
        int placed = DiggerAutoMapper.generate(this.level, bars, 2, DiggerAutoMapper.Pattern.ZIGZAG, true);
        player.sendMessage(PbText.of("&aРазметка по сетке&7: нот &f" + placed
            + " &7на темпе &f" + this.settings.getBpm()
            + (durationMillis > 0 ? " &7(" + format(durationMillis) + ")" : "")));
    }

    @NonNull
    private static String format(int millis) {
        int seconds = millis / 1000;
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    private void updateItems() {
        this.clearInventory();

        // BPM
        this.setItem(2, 2, ItemUtils.create(Material.NOTE_BLOCK, meta -> {
            meta.displayName(PbText.item("&8◆ &6Темп (BPM): &f" + this.settings.getBpm()));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Темп трека. Из него считается скорость."));
            lore.add(PbText.item("&7Скорость: &f"
                + String.format("%.2f", this.settings.resolveSpeed()) + " бл/с"));
            lore.add(PbText.item("&7Блоков на долю: &f"
                + String.format("%.2f", this.settings.blocksPerBeat())));
            lore.add(Component.empty());
            lore.add(PbText.item("&8ЛКМ +1, ПКМ -1, Shift ×10"));
            meta.lore(lore);
        }), event -> {
            double step = event.isShift() ? 10.0D : 1.0D;
            this.settings.setBpm(this.settings.getBpm() + (event.isLeft() ? step : -step));
            this.click(event.getPlayer());
            this.reopen(event.getPlayer());
        });

        // Множитель скорости
        this.setItem(2, 4, ItemUtils.create(Material.SUGAR, meta -> {
            meta.displayName(PbText.item("&8◆ &6Множитель скорости: &f×"
                + String.format("%.2f", this.settings.getSpeedMultiplier())));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Когда темп определён верно,"));
            lore.add(PbText.item("&7но играть хочется быстрее."));
            lore.add(Component.empty());
            lore.add(PbText.item("&8ЛКМ +0.05, ПКМ -0.05"));
            meta.lore(lore);
        }), event -> {
            this.settings.setSpeedMultiplier(this.settings.getSpeedMultiplier() + (event.isLeft() ? 0.05D : -0.05D));
            this.click(event.getPlayer());
            this.reopen(event.getPlayer());
        });

        // Смещение первой доли
        this.setItem(2, 6, ItemUtils.create(Material.CLOCK, meta -> {
            meta.displayName(PbText.item("&8◆ &6Смещение первой доли: &f"
                + this.settings.getFirstBeatOffsetMillis() + " мс"));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Если трек начинается не с тишины,"));
            lore.add(PbText.item("&7вся карта сдвигается на это число."));
            lore.add(Component.empty());
            lore.add(PbText.item("&8ЛКМ +10, ПКМ -10, Shift ×10"));
            meta.lore(lore);
        }), event -> {
            int step = event.isShift() ? 100 : 10;
            this.settings.setFirstBeatOffsetMillis(
                this.settings.getFirstBeatOffsetMillis() + (event.isLeft() ? step : -step));
            this.click(event.getPlayer());
            this.reopen(event.getPlayer());
        });

        // Кирка
        this.setItem(2, 8, ItemUtils.create(this.settings.getPickaxe(), meta -> {
            DiggerPickaxe pickaxe = DiggerPickaxe.byMaterial(this.settings.getPickaxe());
            meta.displayName(PbText.item("&8◆ &6Кирка: &f"
                + (pickaxe == null ? "&7?" : pickaxe.getDisplay())));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Кирка в руке игрока на всём уровне."));
            lore.add(PbText.item("&7На скорость ломки не влияет:"));
            lore.add(PbText.item("&7в творческом режиме всё ломается сразу."));
            lore.add(Component.empty());
            lore.add(PbText.item("&8ЛКМ - следующая"));
            meta.lore(lore);
        }), event -> {
            DiggerPickaxe current = DiggerPickaxe.byMaterial(this.settings.getPickaxe());
            DiggerPickaxe next = (current == null ? DiggerPickaxe.getDefault() : current).next();
            this.settings.setPickaxe(next.getMaterial());
            this.click(event.getPlayer());
            this.reopen(event.getPlayer());
        });

        // Тоннель
        this.setItem(3, 3, ItemUtils.create(Material.STONE_BRICKS, meta -> {
            meta.displayName(PbText.item("&8◆ &6Ширина тоннеля: &f" + this.settings.getTunnelWidth()));
            meta.lore(java.util.Collections.singletonList(PbText.item("&8ЛКМ +1, ПКМ -1")));
        }), event -> {
            this.settings.setTunnelWidth(this.settings.getTunnelWidth() + (event.isLeft() ? 1 : -1));
            this.click(event.getPlayer());
            this.reopen(event.getPlayer());
        });

        this.setItem(3, 7, ItemUtils.create(Material.STONE_BRICK_SLAB, meta -> {
            meta.displayName(PbText.item("&8◆ &6Высота тоннеля: &f" + this.settings.getTunnelHeight()));
            meta.lore(java.util.Collections.singletonList(PbText.item("&8ЛКМ +1, ПКМ -1")));
        }), event -> {
            this.settings.setTunnelHeight(this.settings.getTunnelHeight() + (event.isLeft() ? 1 : -1));
            this.click(event.getPlayer());
            this.reopen(event.getPlayer());
        });

        // Жёсткость: за сколько до себя игрок видит руду
        this.setItem(3, 1, ItemUtils.create(Material.TARGET, meta -> {
            meta.displayName(PbText.item("&8◆ &6Жёсткость: &f"
                + String.format("%.3f", this.settings.getHardness())
                + " &8(" + this.settings.hardnessTitle() + "&8)"));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Руды появляются перед игроком на ходу,"));
            lore.add(PbText.item("&7а не стоят в тоннеле изначально."));
            lore.add(PbText.item("&7Жёсткость решает, насколько заранее."));
            lore.add(Component.empty());
            lore.add(PbText.item("&7Видно руду: &f"
                + String.format("%.1f", this.settings.reactionSeconds()) + " с"
                + " &8(" + String.format("%.0f", this.settings.revealDistance()) + " блоков)"));
            lore.add(Component.empty());
            lore.add(PbText.item("&81 - читается с листа, 10 - только наизусть"));
            lore.add(PbText.item("&8ЛКМ +0.25, ПКМ -0.25, Shift - шаг 0.05"));
            meta.lore(lore);
        }), event -> {
            double step = event.isShift() ? 0.05D : 0.25D;
            this.settings.setHardness(this.settings.getHardness() + (event.isLeft() ? step : -step));
            this.click(event.getPlayer());
            this.reopen(event.getPlayer());
        });

        // Точка отсчёта
        this.setItem(3, 5, ItemUtils.create(Material.TARGET, meta -> {
            meta.displayName(PbText.item("&8◆ &6Точка отсчёта"));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item(this.settings.hasOrigin()
                ? "&7Задана. Отсюда считаются все таймкоды."
                : "&cНе задана: берётся точка спавна уровня."));
            lore.add(Component.empty());
            lore.add(PbText.item("&8ЛКМ - поставить на своё место"));
            meta.lore(lore);
        }), event -> {
            this.settings.setOrigin(event.getPlayer().getLocation());
            event.getPlayer().sendMessage(PbText.of("&aТочка отсчёта перенесена сюда."));
            this.click(event.getPlayer());
            this.reopen(event.getPlayer());
        });

        // ВСЁ ОСТАЛЬНОЕ ТОЖЕ ЗДЕСЬ. Отдельных предметов в хотбаре у режима нет:
        // пять кнопок, каждую из которых надо помнить и не потерять, - хуже одного
        // меню, в которое всегда можно зайти.
        this.setItem(4, 2, ItemUtils.create(Material.DIAMOND_ORE, meta -> {
            meta.displayName(PbText.item("&8◆ &6Руды-ноты"));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Взять руду, которая станет нотой."));
            lore.add(PbText.item("&7Руда из обычного инвентаря - декор."));
            lore.add(PbText.item("&7Нот на карте: &f" + this.settings.getNotesCount()));
            meta.lore(lore);
        }), event -> new DiggerOresMenu((ParkourBeat) this.plugin, this.lang, this.level)
            .open(event.getPlayer()));

        this.setItem(4, 4, ItemUtils.create(Material.REPEATER, meta -> {
            meta.displayName(PbText.item("&8◆ &6Авторазметка"));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Разложить руды по треку."));
            lore.add(PbText.item("&7Темп и смещение берутся из разбора трека."));
            lore.add(Component.empty());
            lore.add(PbText.item("&8ЛКМ - выбрать сложность, Shift + ЛКМ - ровная сетка"));
            meta.lore(lore);
        }), event -> {
            if (event.isShift()) {
                this.runGridMap(event.getPlayer());
                return;
            }
            new DiggerAutoMapMenu((ParkourBeat) this.plugin, this.lang, this.level)
                .open(event.getPlayer());
        });

        this.setItem(4, 6, ItemUtils.create(Material.LIGHT_BLUE_DYE, meta -> {
            meta.displayName(PbText.item("&8◆ &6Сетка бита"));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Показать линии долей в мире."));
            lore.add(PbText.item("&7Руды ставятся строго на них."));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            player.closeInventory();
            ((ParkourBeat) this.plugin).get(DiggerManager.class).getEditor().toggleGrid(player);
        });

        this.setItem(4, 8, ItemUtils.create(Material.NOTE_BLOCK, meta -> {
            meta.displayName(PbText.item("&8◆ &6Звук попадания"));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Что слышно при ударе по руде."));
            lore.add(PbText.item(this.settings.hasCustomHitSound()
                ? "&8" + this.settings.getHitSoundKey()
                : "&8у каждой руды свой"));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            ru.sortix.parkourbeat.activity.UserActivity activity =
                ((ParkourBeat) this.plugin).get(ru.sortix.parkourbeat.activity.ActivityManager.class)
                    .getActivity(player);
            if (!(activity instanceof ru.sortix.parkourbeat.activity.type.EditActivity)) {
                player.sendMessage(PbText.of("&cЭто доступно только в редакторе."));
                return;
            }
            new ru.sortix.parkourbeat.inventory.type.editor.DiggerHitSoundMenu(
                (ParkourBeat) this.plugin, this.lang,
                (ru.sortix.parkourbeat.activity.type.EditActivity) activity).open(player);
        });

        // Снос разметки
        this.setItem(5, 3, ItemUtils.create(Material.SUGAR, meta -> {
            meta.displayName(PbText.item("&8◆ &dЗоны скорости"));
            java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
            lore.add(PbText.item("&7Отрезки трека, которые едутся"));
            lore.add(PbText.item("&7быстрее или медленнее остального."));
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(PbText.item("&7Сейчас зон: &f" + settings.getSpeedZonesCount()));
            lore.add(PbText.item("&8Пусто - карта едет одной скоростью."));
            meta.lore(lore);
        }), event -> new DiggerSpeedZonesMenu((ParkourBeat) this.plugin, this.lang, this.level).open(event.getPlayer()));

        this.setItem(5, 5, ItemUtils.create(Material.BARRIER, meta -> {
            meta.displayName(PbText.item("&8◆ &6Очистить разметку"));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Убирает все " + this.settings.getNotesCount() + " нот из карты."));
            lore.add(PbText.item("&7Блоки в мире остаются на месте."));
            lore.add(Component.empty());
            lore.add(PbText.item("&8Только Shift + ЛКМ"));
            meta.lore(lore);
        }), event -> {
            if (!event.isShift()) {
                event.getPlayer().sendMessage(PbText.of("&7Держи Shift, чтобы подтвердить."));
                return;
            }
            int count = this.settings.getNotesCount();
            this.settings.clearNotes(this.level.getWorld());
            event.getPlayer().sendMessage(PbText.of("&cУдалено нот: &f" + count));
            this.click(event.getPlayer());
            this.reopen(event.getPlayer());
        });

        this.fillBorder();
    }
}
