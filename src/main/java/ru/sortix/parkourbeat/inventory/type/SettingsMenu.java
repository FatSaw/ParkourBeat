package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.player.PlayerSettingsManager;
import ru.sortix.parkourbeat.ui.Glyphs;
import ru.sortix.parkourbeat.ui.Icons;
import ru.sortix.parkourbeat.ui.Icons.Button;
import ru.sortix.parkourbeat.ui.Icons.ButtonState;
import ru.sortix.parkourbeat.utils.lang.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * НАСТРОЙКИ - четыре карточки во всю ширину окна.
 * <pre>
 *   [ СТАТУС ИГРЫ ............... (o ) ]   - вкл/выкл
 *   [ МОИ РЕПЛЕИ ................ ( o) ]
 *   [ ТЕЛЕПОРТ КО МНЕ ........... [◉] ]   - все / друзья / никто
 *   [ ДОСТУП К РЕПЛЕЯМ .......... [◉] ]
 *   [<-]
 * </pre>
 * Вся карточка - одна кнопка: клик по любому её месту переключает настройку, при
 * наведении обводится карточка целиком. Справа - текущее состояние.
 * Тексты - те же ключи lang.yml.
 */
public class SettingsMenu extends ParkourBeatInventory {

    public SettingsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer) {
        super(plugin, 6, lang, Glyphs.menuTitle(Glyphs.menuSettings(lang),
            Icons.Icon.GEAR.component().append(Component.text(" "))
                .append(Lang.item(lang, "inventory.settings.title"))));
        this.render(viewer);
    }

    private void render(@NonNull Player viewer) {
        this.clearInventory();
        PlayerSettingsManager settings = this.plugin.get(PlayerSettingsManager.class);

        boolean hidden = settings.isPlayingStatusHidden(viewer.getUniqueId());
        this.card(0, hidden ? Button.SWITCH_OFF : Button.SWITCH_ON, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.settings.status.name"));
            List<Component> lore = new ArrayList<>(Lang.lore(this.lang, "inventory.settings.status.lore"));
            lore.add(Lang.item(this.lang, hidden ? "inventory.settings.status.off" : "inventory.settings.status.on"));
            lore.add(Component.empty());
            lore.add(Lang.item(this.lang, "inventory.common.toggle"));
            meta.lore(lore);
        }, () -> settings.setPlayingStatusHidden(viewer.getUniqueId(), !hidden), viewer);

        boolean replaysHidden = settings.areReplaysHidden(viewer.getUniqueId());
        this.card(1, replaysHidden ? Button.SWITCH_OFF : Button.SWITCH_ON, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.settings.replays.name"));
            List<Component> lore = new ArrayList<>(Lang.lore(this.lang, "inventory.settings.replays.lore"));
            lore.add(Lang.item(this.lang, replaysHidden ? "inventory.settings.replays.off" : "inventory.settings.replays.on"));
            lore.add(Component.empty());
            lore.add(Lang.item(this.lang, "inventory.common.toggle"));
            meta.lore(lore);
        }, () -> settings.setReplaysHidden(viewer.getUniqueId(), !replaysHidden), viewer);

        PlayerSettingsManager.TeleportAccess access = settings.getTeleportAccess(viewer.getUniqueId());
        this.card(2, switch (access) {
            case ALL -> Button.ACCESS_ALL;
            case FRIENDS -> Button.ACCESS_FRIENDS;
            case NOBODY -> Button.ACCESS_NOBODY;
        }, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.settings.teleport.name"));
            List<Component> lore = new ArrayList<>(Lang.lore(this.lang, "inventory.settings.teleport.lore",
                "%mode%", access.getDisplay(this.lang),
                "%description%", access.getDescription(this.lang)));
            lore.add(Component.empty());
            lore.add(Lang.item(this.lang, "inventory.common.toggle"));
            meta.lore(lore);
        }, () -> settings.nextTeleportAccess(viewer.getUniqueId()), viewer);

        PlayerSettingsManager.ReplayAccess replayAccess = settings.getReplayAccess(viewer.getUniqueId());
        this.card(3, switch (replayAccess) {
            case ALL -> Button.ACCESS_ALL;
            case FRIENDS -> Button.ACCESS_FRIENDS;
            case NOBODY -> Button.ACCESS_NOBODY;
        }, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.settings.replayaccess.name"));
            List<Component> lore = new ArrayList<>(Lang.lore(this.lang, "inventory.settings.replayaccess.lore",
                "%mode%", replayAccess.getDisplay(this.lang),
                "%description%", replayAccess.getDescription(this.lang)));
            lore.add(Component.empty());
            lore.add(Lang.item(this.lang, "inventory.common.toggle"));
            meta.lore(lore);
        }, () -> settings.nextReplayAccess(viewer.getUniqueId()), viewer);

        // 5-я карточка: «Интерфейс» - как показывать очки за прыжок и плашку оценки.
        ItemStack hud = Glyphs.withHover(ItemUtils.modifyMeta(Icons.empty(), meta -> {
            int set = Glyphs.localeSet(this.lang);
            meta.displayName(ru.sortix.parkourbeat.utils.text.PbText.item("<i:eye> &f"
                + (set == 1 ? "Interface" : set == 2 ? "Інтерфейс" : "Интерфейс")));
            meta.lore(List.of(ru.sortix.parkourbeat.utils.text.PbText.item(set == 1
                ? "&7Jump points and rating banner during a run"
                : set == 2 ? "&7Очки за стрибок і плашка оцінки під час забігу"
                : "&7Очки за прыжок и плашка оценки во время забега")));
        }), 0, 4, 8, 4);
        Consumer<ru.sortix.parkourbeat.inventory.event.ClickEvent> openHud = event ->
            new HudSettingsMenu(this.plugin, this.lang, viewer).open(viewer);
        for (int c = 0; c < 8; c++) this.setItem(4 * 9 + c, hud, openHud);
        this.setItem(4 * 9 + 8, Glyphs.withHover(ItemUtils.modifyMeta(Button.ARROW_RIGHT.item(ButtonState.NORMAL),
            meta -> meta.displayName(hud.getItemMeta().displayName())), 0, 4, 8, 4), openHud);

        this.setItem(5 * 9, ItemUtils.modifyMeta(Button.ARROW_LEFT.item(ButtonState.NORMAL), meta ->
                meta.displayName(Lang.item(this.lang, "inventory.common.back"))),
            event -> new ServerMenu(this.plugin, this.lang, viewer).open(viewer));
    }

    /** Карточка-строка: 8 прозрачных слотов + состояние справа, всё - одна кнопка с общей рамкой. */
    private void card(int row, @NonNull Button state, @NonNull Consumer<org.bukkit.inventory.meta.ItemMeta> describe,
                      @NonNull Runnable toggle, @NonNull Player viewer) {
        PlayerSettingsManager settings = this.plugin.get(PlayerSettingsManager.class);
        Consumer<ru.sortix.parkourbeat.inventory.event.ClickEvent> action = event -> {
            toggle.run();
            settings.save();
            ru.sortix.parkourbeat.ui.UiSounds.click(viewer);
            this.render(viewer);
        };
        ItemStack body = Glyphs.withHover(ItemUtils.modifyMeta(Icons.empty(), describe), 0, row, 8, row);
        for (int c = 0; c < 8; c++) this.setItem(row * 9 + c, body, action);
        this.setItem(row * 9 + 8, Glyphs.withHover(ItemUtils.modifyMeta(state.item(ButtonState.NORMAL), describe), 0, row, 8, row), action);
    }
}
