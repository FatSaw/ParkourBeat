package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;
import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.Portal;
import ru.sortix.parkourbeat.listeners.PortalWandListener;

import java.util.ArrayList;
import java.util.List;

public class PortalMenu extends ParkourBeatInventory implements EditLevelMenu {
    private final @NonNull EditActivity activity;
    private final @NonNull Level level;
    private final @NonNull Portal portal;

    public PortalMenu(@NonNull ParkourBeat plugin, String lang,
                      @NonNull EditActivity activity, @NonNull Portal portal) {
        super(plugin, 5, lang, FallZonesMenu.text(Lang.raw(lang, "auto.portal_menu.portal_menu.1")));
        this.activity = activity;
        this.level = activity.getLevel();
        this.portal = portal;
        this.activity.setSelectedPortal(portal);
        this.updateItems();
    }

    public void updateItems() {
        this.clearInventory();

        this.setItem(1, 5, ItemUtils.create(Material.FISHING_ROD, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_menu.update_items.1")));
            meta.lore(lore("", Lang.raw(this.lang, "auto.portal_menu.update_items.2"), Lang.raw(this.lang, "auto.portal_menu.update_items.3"), "",
                Lang.raw(this.lang, "auto.portal_menu.update_items.4")));
        }), event -> {
            Player player = event.getPlayer();
            PortalWandListener.give(this.plugin, player);
            player.sendMessage(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_menu.update_items.5")));
            player.closeInventory();
        });

        this.setItem(2, 3, ItemUtils.create(Material.LIME_CONCRETE, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_menu.update_items.6")));
            meta.lore(lore("",
                Lang.raw(this.lang, "auto.portal_menu.update_items.7") + this.portal.getEntry().format(),
                Lang.raw(this.lang, "auto.portal_menu.update_items.8") + this.portal.getEntry().getFacing().getDisplayName(),
                Lang.raw(this.lang, "auto.portal_menu.update_items.9") + fmt(this.portal.getEntry().getSize()) + Lang.raw(this.lang, "auto.portal_menu.update_items.10"),
                Lang.raw(this.lang, "auto.portal_menu.update_items.11") + this.portal.getEntry().getColorHex(),
                Lang.raw(this.lang, "auto.portal_menu.update_items.12") + this.portal.getEntry().formatLook(),
                "", Lang.raw(this.lang, "auto.portal_menu.update_items.13")));
        }), event -> new PortalSideMenu(this.plugin, this.lang, this.activity, this.portal, true)
            .open(event.getPlayer()));

        this.setItem(2, 7, ItemUtils.create(Material.LIGHT_BLUE_CONCRETE, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_menu.update_items.14")));
            meta.lore(lore("",
                Lang.raw(this.lang, "auto.portal_menu.update_items.15") + this.portal.getExit().format(),
                Lang.raw(this.lang, "auto.portal_menu.update_items.16") + this.portal.getExit().getFacing().getDisplayName(),
                Lang.raw(this.lang, "auto.portal_menu.update_items.17") + fmt(this.portal.getExit().getSize()) + Lang.raw(this.lang, "auto.portal_menu.update_items.18"),
                Lang.raw(this.lang, "auto.portal_menu.update_items.19") + this.portal.getExit().getColorHex(),
                Lang.raw(this.lang, "auto.portal_menu.update_items.20") + this.portal.getExit().formatLook(),
                "", Lang.raw(this.lang, "auto.portal_menu.update_items.21")));
        }), event -> new PortalSideMenu(this.plugin, this.lang, this.activity, this.portal, false)
            .open(event.getPlayer()));

        this.setItem(3, 3, ItemUtils.create(Material.ENDER_EYE, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_menu.update_items.22")));
            meta.lore(lore("", Lang.raw(this.lang, "auto.portal_menu.update_items.23"),
                Lang.raw(this.lang, "auto.portal_menu.update_items.24"),
                Lang.raw(this.lang, "auto.portal_menu.update_items.25") + fmt(this.portal.getViewDistance()) + Lang.raw(this.lang, "auto.portal_menu.update_items.26"),
                Lang.raw(this.lang, "auto.portal_menu.update_items.27") + fmt(Portal.MIN_VIEW_DISTANCE) + Lang.raw(this.lang, "auto.portal_menu.update_items.28") + fmt(Portal.MAX_VIEW_DISTANCE)
                    + Lang.raw(this.lang, "auto.portal_menu.update_items.29") + fmt(Portal.DEFAULT_VIEW_DISTANCE),
                "", Lang.raw(this.lang, "auto.portal_menu.update_items.30")));
        }), event -> {
            Player player = event.getPlayer();
            this.portal.setViewDistance(this.portal.getViewDistance() + (event.isLeft() ? 4.0D : -4.0D));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            this.updateItems();
        });

        boolean enabled = this.portal.isEnabled();
        this.setItem(3, 5, ItemUtils.create(enabled ? Material.LIME_DYE : Material.GRAY_DYE, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_menu.update_items.31")));
            meta.lore(lore("", enabled ? Lang.raw(this.lang, "auto.portal_menu.update_items.32") : Lang.raw(this.lang, "auto.portal_menu.update_items.33"),
                "", Lang.raw(this.lang, "auto.portal_menu.update_items.34")));
        }), event -> {
            Player player = event.getPlayer();
            this.portal.setEnabled(!this.portal.isEnabled());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            this.level.getLevelSettings().updateParticleLocations();
            this.updateItems();
        });

        this.setItem(5, 3, ItemUtils.create(Material.BARRIER, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_menu.update_items.35")));
            meta.lore(lore("", Lang.raw(this.lang, "auto.portal_menu.update_items.36")));
        }), event -> {
            Player player = event.getPlayer();
            if (this.activity.getSelectedPortal() == this.portal) this.activity.setSelectedPortal(null);
            this.level.getLightShow().removePortal(this.portal);
            this.level.getLevelSettings().updateParticleLocations();
            player.sendMessage(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_menu.update_items.37")));
            new PortalsMenu(this.plugin, this.lang, this.activity).open(player);
        });

        this.setItem(5, 5, ItemUtils.create(Material.ENDER_PEARL, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_menu.update_items.38")));
            meta.lore(lore("", Lang.raw(this.lang, "auto.portal_menu.update_items.39")));
        }), event -> {
            Player player = event.getPlayer();
            player.closeInventory();
            player.teleport(this.portal.getEntry().toLocation(this.level.getWorld()));
        });

        this.setItem(5, 7, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_menu.update_items.40")))
        ), event -> new PortalsMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }

    static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    static List<Component> lore(String... lines) {
        List<Component> result = new ArrayList<>();
        for (String line : lines) {
            if (line.isEmpty()) result.add(Component.empty());
            else result.add(FallZonesMenu.text(line));
        }
        return result;
    }
}
