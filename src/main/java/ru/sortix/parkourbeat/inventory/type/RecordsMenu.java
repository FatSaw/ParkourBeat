package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.replay.ReplayManager;
import ru.sortix.parkourbeat.ui.Glyphs;
import ru.sortix.parkourbeat.ui.Icons;
import ru.sortix.parkourbeat.ui.Icons.Button;
import ru.sortix.parkourbeat.ui.Icons.ButtonState;
import ru.sortix.parkourbeat.utils.lang.Lang;

/**
 * ЗАПИСИ.
 * <pre>
 *   ЗАПИСЬ    [REC]            - подсвечена, когда запись включена
 *   - - - - - - - - - -
 *   РЕПЛЕИ    [мои][все]
 *   [<-]                [?]
 * </pre>
 */
public class RecordsMenu extends ParkourBeatInventory {

    public RecordsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer) {
        super(plugin, 6, lang, Glyphs.menuTitle(Glyphs.menuRecords(lang),
            Icons.Icon.REC.component().append(Component.text(" "))
                .append(Lang.item(lang, "inventory.records.title"))));
        this.render(viewer);
    }

    private void render(@NonNull Player viewer) {
        ReplayManager replays = this.plugin.get(ReplayManager.class);
        boolean recording = replays.isRecordingEnabled(viewer.getUniqueId());

        this.setItem(9 + 4, ItemUtils.modifyMeta(Button.MM_REPLAYS.item(recording ? ButtonState.SELECTED : ButtonState.NORMAL), meta -> {
            meta.displayName(Lang.item(this.lang, recording
                ? "inventory.records.toggle.name_on"
                : "inventory.records.toggle.name_off"));
            meta.lore(Lang.lore(this.lang, "inventory.records.toggle.lore"));
        }), event -> {
            Player player = event.getPlayer();
            replays.setRecordingEnabled(player.getUniqueId(), !recording);
            ru.sortix.parkourbeat.ui.UiSounds.toggle(player);
            new RecordsMenu(this.plugin, this.lang, player).open(player);
        });

        this.setItem(3 * 9 + 4, ItemUtils.modifyMeta(Button.USER.item(ButtonState.NORMAL), meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.records.mine.name"));
            meta.lore(Lang.lore(this.lang, "inventory.records.mine.lore"));
        }), event -> new PlayerReplaysMenu(this.plugin, this.lang,
            event.getPlayer(), event.getPlayer().getUniqueId(), event.getPlayer().getName()).open(event.getPlayer()));

        this.setItem(3 * 9 + 5, ItemUtils.modifyMeta(Button.MM_RATING.item(ButtonState.NORMAL), meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.records.all.name"));
            meta.lore(Lang.lore(this.lang, "inventory.records.all.lore"));
        }), event -> new AllReplaysMenu(this.plugin, this.lang, event.getPlayer(), false)
            .open(event.getPlayer()));

        this.setItem(5 * 9 + 8, ItemUtils.modifyMeta(Button.MM_HOWTO.item(ButtonState.NORMAL), meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.records.help.name"));
            meta.lore(Lang.lore(this.lang, "inventory.records.help.lore"));
        }), null);

        this.setItem(5 * 9, ItemUtils.modifyMeta(Button.ARROW_LEFT.item(ButtonState.NORMAL), meta ->
            meta.displayName(Lang.item(this.lang, "inventory.common.back"))
        ), event -> new ServerMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));
    }
}
