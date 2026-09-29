package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.replay.ReplayManager;
import ru.sortix.parkourbeat.utils.lang.Lang;

public class RecordsMenu extends ParkourBeatInventory {

    public RecordsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer) {
        super(plugin, 4, lang, Lang.item(lang, "inventory.records.title"));
        this.render(viewer);
    }

    private void render(@NonNull Player viewer) {
        this.fillBorder();

        ReplayManager replays = this.plugin.get(ReplayManager.class);
        boolean recording = replays.isRecordingEnabled(viewer.getUniqueId());

        this.setItem(2, 4, ItemUtils.create(
            recording ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, meta -> {
                meta.displayName(Lang.item(this.lang, recording
                    ? "inventory.records.toggle.name_on"
                    : "inventory.records.toggle.name_off"));
                meta.lore(Lang.lore(this.lang, "inventory.records.toggle.lore"));
            }), event -> {
            Player player = event.getPlayer();
            replays.setRecordingEnabled(player.getUniqueId(), !recording);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
            new RecordsMenu(this.plugin, this.lang, player).open(player);
        });

        this.setItem(2, 6, ItemUtils.create(Material.REDSTONE, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.records.mine.name"));
            meta.lore(Lang.lore(this.lang, "inventory.records.mine.lore"));
        }), event -> new PlayerReplaysMenu(this.plugin, this.lang,
            event.getPlayer(), event.getPlayer().getUniqueId(), event.getPlayer().getName()).open(event.getPlayer()));

        this.setItem(2, 8, ItemUtils.create(Material.NETHER_STAR, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.records.all.name"));
            meta.lore(Lang.lore(this.lang, "inventory.records.all.lore"));
            meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }), event -> new AllReplaysMenu(this.plugin, this.lang, event.getPlayer(), false)
            .open(event.getPlayer()));

        this.setItem(3, 5, ItemUtils.create(Material.BOOK, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.records.help.name"));
            meta.lore(Lang.lore(this.lang, "inventory.records.help.lore"));
        }), null);

        this.setItem(4, 5, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(Lang.item(this.lang, "inventory.common.back"))
        ), event -> new ServerMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));
    }
}
