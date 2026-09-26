package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;
import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.wonder.WonderCategory;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.text.PbText;
import ru.sortix.parkourbeat.utils.wonder.WonderTimeline;

import java.util.ArrayList;
import java.util.List;

public class WonderAddMenu extends ParkourBeatInventory implements EditLevelMenu {

    private static final Object[][] GROUPS = {
        {"Надписи", Material.NAME_TAG, WonderCategory.TEXT,
            "Слова песни, названия, отсчёты"},
        {"Небо и тепло", Material.NETHER_STAR, WonderCategory.SKY,
            "Звездопады, кометы, сердца, лепестки"},
        {"Огонь и удары", Material.BLAZE_POWDER, WonderCategory.FIRE,
            "Пламя, взрывы, вспышки в такт биту"},
        {"Магия и фигуры", Material.END_CRYSTAL, WonderCategory.MAGIC,
            "Руны, порталы, кольца, коридоры, сцены"}
    };

    private final @NonNull EditActivity activity;

    public WonderAddMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 4, lang, PbText.of(Lang.raw(lang, "auto.wonder_add_menu.wonder_add_menu.1")));
        this.activity = activity;
        this.updateItems();
    }

    public void updateItems() {
        this.clearInventory();

        int[] columns = {2, 4, 6, 8};
        for (int i = 0; i < GROUPS.length; i++) {
            Object[] group = GROUPS[i];
            String title = (String) group[0];
            Material icon = (Material) group[1];
            WonderCategory category = (WonderCategory) group[2];
            String hint = (String) group[3];

            this.setItem(2, columns[i], ItemUtils.create(icon, meta -> {
                meta.displayName(PbText.of("&f" + title).decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(line("&7" + hint));
                lore.add(Component.empty());
                lore.add(line(Lang.raw(this.lang, "auto.wonder_add_menu.update_items.1")));
                meta.lore(lore);
            }), event -> new WonderPresetsMenu(this.plugin, this.lang, this.activity, null, category, true)
                .open(event.getPlayer()));
        }

        this.setItem(3, 5, ItemUtils.create(Material.CLOCK, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_add_menu.update_items.2")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();

            int here = this.timecodeOf(this.firstViewer());
            if (here >= 0) {
                lore.add(line(Lang.raw(this.lang, "auto.wonder_add_menu.update_items.3") + TimeUtils.formatTimecode(here)));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_add_menu.update_items.4")));
            } else {
                lore.add(line(Lang.raw(this.lang, "auto.wonder_add_menu.update_items.5")));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_add_menu.update_items.6")));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_add_menu.update_items.7")));
            }
            meta.lore(lore);
        }), null);

        this.setItem(3, 3, ItemUtils.create(Material.WRITABLE_BOOK, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_add_menu.update_items.8")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_add_menu.update_items.9")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_add_menu.update_items.10")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_add_menu.update_items.11")));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            player.closeInventory();
            WonderManual.sendCommands(player);
            WonderManual.send(player);
        });

        this.setItem(3, 7, ItemUtils.create(Material.ENCHANTED_BOOK, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_add_menu.update_items.12")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_add_menu.update_items.13")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_add_menu.update_items.14")));
            meta.lore(lore);
        }), event -> new WonderAiMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));

        this.fillBorder();

        this.setItem(4, 5, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_add_menu.update_items.15")).decoration(TextDecoration.ITALIC, false))
        ), event -> new WonderEffectsMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }

    private Player firstViewer() {
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (player.getWorld().equals(this.activity.getLevel().getWorld())) return player;
        }
        return null;
    }

    private int timecodeOf(Player player) {
        if (player == null) return -1;
        return WonderTimeline.millisAt(this.activity.getLevel(), player.getLocation(), 6.0D);
    }

    private static Component line(@NonNull String text) {
        return PbText.of(text).decoration(TextDecoration.ITALIC, false);
    }
}
