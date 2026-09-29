// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/inventory/type/SettingsMenu.java
package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.player.PlayerSettingsManager;
import ru.sortix.parkourbeat.utils.lang.Lang;

import java.util.ArrayList;
import java.util.List;

public class SettingsMenu extends ParkourBeatInventory {

    public SettingsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer) {
        super(plugin, 5, lang, Lang.item(lang, "inventory.settings.title"));
        this.render(viewer);
    }

    private void render(@NonNull Player viewer) {
        this.clearInventory();
        PlayerSettingsManager settings = this.plugin.get(PlayerSettingsManager.class);

        org.bukkit.inventory.ItemStack glass = ItemUtils.create(Material.BLACK_STAINED_GLASS_PANE, meta -> meta.displayName(Component.empty()));
        for (int i = 0; i < 45; i++) {
            if (i < 9 || i >= 36 || i % 9 == 0 || i % 9 == 8) {
                this.setItem(i, glass, null);
            }
        }

        boolean hidden = settings.isPlayingStatusHidden(viewer.getUniqueId());
        this.setItem(20, ItemUtils.create(hidden ? Material.GRAY_DYE : Material.LIME_DYE, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.settings.status.name"));
            List<Component> lore = new ArrayList<>(
                Lang.lore(this.lang, "inventory.settings.status.lore"));
            lore.add(Lang.item(this.lang, hidden
                ? "inventory.settings.status.off"
                : "inventory.settings.status.on"));
            lore.add(Component.empty());
            lore.add(Lang.item(this.lang, "inventory.common.toggle"));
            meta.lore(lore);
        }), event -> {
            settings.setPlayingStatusHidden(viewer.getUniqueId(), !hidden);
            settings.save();
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            this.render(viewer);
        });

        boolean replaysHidden = settings.areReplaysHidden(viewer.getUniqueId());
        this.setItem(22, ItemUtils.create(replaysHidden ? Material.GRAY_DYE : Material.LIME_DYE, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.settings.replays.name"));
            List<Component> lore = new ArrayList<>(
                Lang.lore(this.lang, "inventory.settings.replays.lore"));
            lore.add(Lang.item(this.lang, replaysHidden
                ? "inventory.settings.replays.off"
                : "inventory.settings.replays.on"));
            lore.add(Component.empty());
            lore.add(Lang.item(this.lang, "inventory.common.toggle"));
            meta.lore(lore);
        }), event -> {
            settings.setReplaysHidden(viewer.getUniqueId(), !replaysHidden);
            settings.save();
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            this.render(viewer);
        });

        PlayerSettingsManager.TeleportAccess access = settings.getTeleportAccess(viewer.getUniqueId());
        this.setItem(24, ItemUtils.create(switch (access) {
            case ALL -> Material.ENDER_PEARL;
            case FRIENDS -> Material.ENDER_EYE;
            case NOBODY -> Material.BARRIER;
        }, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.settings.teleport.name"));
            List<Component> lore = new ArrayList<>(
                Lang.lore(this.lang, "inventory.settings.teleport.lore",
                    "%mode%", access.getDisplay(this.lang),
                    "%description%", access.getDescription(this.lang)));
            lore.add(Component.empty());
            lore.add(Lang.item(this.lang, "inventory.common.toggle"));
            meta.lore(lore);
        }), event -> {
            settings.nextTeleportAccess(viewer.getUniqueId());
            settings.save();
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            this.render(viewer);
        });

        PlayerSettingsManager.ReplayAccess replayAccess =
            settings.getReplayAccess(viewer.getUniqueId());
        this.setItem(25, ItemUtils.create(switch (replayAccess) {
            case ALL -> Material.LIME_STAINED_GLASS_PANE;
            case FRIENDS -> Material.YELLOW_STAINED_GLASS_PANE;
            case NOBODY -> Material.RED_STAINED_GLASS_PANE;
        }, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.settings.replayaccess.name"));
            List<Component> lore = new ArrayList<>(
                Lang.lore(this.lang, "inventory.settings.replayaccess.lore",
                    "%mode%", replayAccess.getDisplay(this.lang),
                    "%description%", replayAccess.getDescription(this.lang)));
            lore.add(Component.empty());
            lore.add(Lang.item(this.lang, "inventory.common.toggle"));
            meta.lore(lore);
        }), event -> {
            settings.nextReplayAccess(viewer.getUniqueId());
            settings.save();
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            this.render(viewer);
        });

        this.setItem(40, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(Lang.item(this.lang, "inventory.common.back"))
        ), event -> new ServerMenu(this.plugin, this.lang, viewer).open(viewer));
    }
}
