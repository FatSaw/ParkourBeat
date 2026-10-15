package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.ui.Glyphs;
import ru.sortix.parkourbeat.ui.HudSettings;
import ru.sortix.parkourbeat.ui.Icons;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.List;

/**
 * ИНТЕРФЕЙС - что показывать во время забега.
 * <pre>
 *   ОЧКИ     [классика][мини][выкл]     - «+300» крупно в сабтайтле / мелко в актионбаре / нет
 *   ПЛАШКА   [вкл][выкл]                - ВЕЛИКОЛЕПНО / ОТЛИЧНО ... в актионбаре
 *   [<-]
 * </pre>
 */
public class HudSettingsMenu extends ParkourBeatInventory {
    private final @NonNull Player viewer;

    public HudSettingsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer) {
        super(plugin, 6, lang, Glyphs.menuTitle((char) (0xE56D + Glyphs.localeSet(lang)),
            Icons.Icon.EYE.component().append(Component.text(" "))
                .append(PbText.of("&f" + t(lang, "Интерфейс", "Interface", "Інтерфейс")))));
        this.viewer = viewer;
        this.render();
    }

    private void render() {
        this.clearInventory();
        HudSettings.Points points = HudSettings.points(this.viewer);
        this.option(4, 8500, points == HudSettings.Points.CLASSIC,
            t(this.lang, "Классика", "Classic", "Класика"),
            t(this.lang, "&7Крупно в сабтайтле: &f+300", "&7Large subtitle: &f+300", "&7Великим у сабтайтлі: &f+300"),
            () -> HudSettings.setPoints(this.viewer, HudSettings.Points.CLASSIC));
        this.option(5, 8503, points == HudSettings.Points.MINI,
            t(this.lang, "Мини", "Mini", "Міні"),
            t(this.lang, "&7Мелко в актионбаре - не мешает при большом GUI",
                "&7Small in the action bar - fine with a large GUI", "&7Дрібно в актіонбарі - не заважає при великому GUI"),
            () -> HudSettings.setPoints(this.viewer, HudSettings.Points.MINI));
        this.option(6, Icons.Button.OFF.modelData(Icons.ButtonState.NORMAL) - Icons.ButtonState.NORMAL.ordinal(),
            points == HudSettings.Points.OFF,
            t(this.lang, "Выкл", "Off", "Вимк"),
            t(this.lang, "&7Очки за прыжок не показываются", "&7Jump points are hidden", "&7Очки за стрибок не показуються"),
            () -> HudSettings.setPoints(this.viewer, HudSettings.Points.OFF));

        boolean banner = HudSettings.banner(this.viewer);
        this.option(13, Icons.Button.SWITCH_ON.modelData(Icons.ButtonState.NORMAL), banner,
            t(this.lang, "Плашка: вкл", "Banner: on", "Плашка: увімк"),
            t(this.lang, "&7ВЕЛИКОЛЕПНО / ОТЛИЧНО ... в актионбаре", "&7PERFECT / GREAT ... in the action bar",
                "&7ЧУДОВО / ВІДМІННО ... в актіонбарі"),
            () -> HudSettings.setBanner(this.viewer, true));
        this.option(14, Icons.Button.SWITCH_OFF.modelData(Icons.ButtonState.NORMAL), !banner,
            t(this.lang, "Плашка: выкл", "Banner: off", "Плашка: вимк"),
            t(this.lang, "&7Без плашки в актионбаре", "&7No banner in the action bar", "&7Без плашки в актіонбарі"),
            () -> HudSettings.setBanner(this.viewer, false));

        boolean transitions = HudSettings.transitions(this.viewer);
        this.option(22, Icons.Button.SWITCH_ON.modelData(Icons.ButtonState.NORMAL), transitions,
            t(this.lang, "Переходы: вкл", "Transitions: on", "Переходи: увімк"),
            t(this.lang, "&7Анимация экрана при заходе на уровень", "&7Screen animation when entering a level",
                "&7Анімація екрана при вході на рівень"),
            () -> HudSettings.setTransitions(this.viewer, true));
        this.option(23, Icons.Button.SWITCH_OFF.modelData(Icons.ButtonState.NORMAL), !transitions,
            t(this.lang, "Переходы: выкл", "Transitions: off", "Переходи: вимк"),
            t(this.lang, "&7Сразу на уровень, без анимации", "&7Straight to the level, no animation", "&7Одразу на рівень, без анімації"),
            () -> HudSettings.setTransitions(this.viewer, false));

        boolean logo = HudSettings.logo(this.viewer);
        this.option(31, Icons.Button.SWITCH_ON.modelData(Icons.ButtonState.NORMAL), logo,
            t(this.lang, "Логотип: анимация", "Logo: animated", "Логотип: анімація"),
            t(this.lang, "&7Свет бежит по логотипу в табе и табло", "&7Light runs over the logo in tab and sidebar",
                "&7Світло біжить логотипом у табі та табло"),
            () -> HudSettings.setLogo(this.viewer, true));
        this.option(32, Icons.Button.SWITCH_OFF.modelData(Icons.ButtonState.NORMAL), !logo,
            t(this.lang, "Логотип: статичный", "Logo: static", "Логотип: статичний"),
            t(this.lang, "&7Логотип без анимации", "&7Logo without animation", "&7Логотип без анімації"),
            () -> HudSettings.setLogo(this.viewer, false));

        this.setItem(45, ItemUtils.modifyMeta(Icons.Button.ARROW_LEFT.item(Icons.ButtonState.NORMAL), meta ->
                meta.displayName(Lang.item(this.lang, "inventory.common.back"))),
            event -> new SettingsMenu(this.plugin, this.lang, this.viewer).open(this.viewer));
    }

    /** Кнопка-вариант: base - CustomModelData обычного состояния (выбранное = base + 1). */
    private void option(int slot, int base, boolean selected, @NonNull String name, @NonNull String hint,
                        @NonNull Runnable apply) {
        ItemStack item = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(base + (selected ? 1 : 0));
        meta.displayName(PbText.item((selected ? "<i:check> " : "") + "&f" + name));
        meta.lore(List.of(PbText.item(hint), Component.empty(), PbText.item(selected
            ? "<i:check> &a" + t(this.lang, "Выбрано", "Selected", "Обрано")
            : "<i:lmb> &7" + t(this.lang, "Нажмите, чтобы выбрать", "Click to select", "Натисніть, щоб обрати"))));
        item.setItemMeta(meta);
        this.setItem(slot, item, event -> {
            apply.run();
            ru.sortix.parkourbeat.ui.UiSounds.toggle(this.viewer);
            this.render();
        });
    }

    @NonNull
    private static String t(String lang, @NonNull String ru, @NonNull String en, @NonNull String uk) {
        int set = Glyphs.localeSet(lang);
        return set == 1 ? en : set == 2 ? uk : ru;
    }
}
