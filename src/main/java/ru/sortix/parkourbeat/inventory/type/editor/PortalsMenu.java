package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.RegularItems;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.levels.settings.Portal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PortalsMenu extends PaginatedMenu<ParkourBeat, Portal> implements EditLevelMenu {
    private final @NonNull EditActivity activity;
    private final @NonNull Level level;

    public PortalsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 6, lang, FallZonesMenu.text(Lang.raw(lang, "auto.portals_menu.portals_menu.1")), 0, 5 * 9);
        this.activity = activity;
        this.level = activity.getLevel();
        this.updateAllItems();
    }

    @NonNull
    private LightShowSettings getLightShow() {
        return this.level.getLightShow();
    }

    @Override
    protected @NonNull Collection<Portal> getAllItems() {
        return new ArrayList<>(this.getLightShow().getPortals());
    }

    @Override
    protected @NonNull ItemStack createItemDisplay(@NonNull Portal portal) {
        boolean selected = this.activity.getSelectedPortal() == portal;
        return ItemUtils.create(portal.isEnabled()
            ? (selected ? Material.PURPLE_CONCRETE : Material.PURPLE_STAINED_GLASS)
            : Material.GRAY_STAINED_GLASS, meta -> {
            meta.displayName(FallZonesMenu.text((selected ? "&d" : "&5") + Lang.raw(this.lang, "auto.portals_menu.create_item_display.1")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.create_item_display.2") + portal.getEntry().format()));
            lore.add(FallZonesMenu.text("&7  " + portal.getEntry().getFacing().getDisplayName()
                + ", " + String.format(java.util.Locale.ROOT, "%.1f", portal.getEntry().getSize())
                + Lang.raw(this.lang, "auto.portals_menu.create_item_display.3") + portal.getEntry().getColorHex()));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.create_item_display.4") + portal.getExit().format()));
            lore.add(FallZonesMenu.text("&7  " + portal.getExit().getFacing().getDisplayName()
                + ", " + String.format(java.util.Locale.ROOT, "%.1f", portal.getExit().getSize())
                + Lang.raw(this.lang, "auto.portals_menu.create_item_display.5") + portal.getExit().getColorHex()));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.create_item_display.6") + (portal.isEnabled() ? Lang.raw(this.lang, "auto.portals_menu.create_item_display.7") : Lang.raw(this.lang, "auto.portals_menu.create_item_display.8"))));
            if (selected) {
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.create_item_display.9")));
            }
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.create_item_display.10")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.create_item_display.11")));
            meta.lore(lore);
        });
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull Portal portal) {
        Player player = event.getPlayer();
        if (event.isLeft()) {
            new PortalMenu(this.plugin, this.lang, this.activity, portal).open(player);
            return;
        }
        if (!event.isShift()) return;

        if (this.activity.getSelectedPortal() == portal) this.activity.setSelectedPortal(null);
        if (!this.getLightShow().removePortal(portal)) return;
        this.level.getLevelSettings().updateParticleLocations();
        player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.portals_menu.on_click.1")));
        this.updateAllItems();
    }

    @Override
    protected void onPageDisplayed() {
        this.setNextPageItem(6, 3);
        this.setPreviousPageItem(6, 7);

        if (this.getLightShow().getPortals().isEmpty()) {
            this.setItem(1, 5, ItemUtils.create(Material.BARRIER, meta -> {
                meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.on_page_displayed.1")));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.on_page_displayed.2")));
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.on_page_displayed.3")));
                meta.lore(lore);
            }), null);
        }

        this.setItem(6, 1, ItemUtils.create(Material.PURPLE_STAINED_GLASS, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.on_page_displayed.4")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.on_page_displayed.5")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.on_page_displayed.6")));
            meta.lore(lore);
        }), this::createPortal);

        this.setItem(6, 2, ItemUtils.create(Material.FISHING_ROD, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.on_page_displayed.7")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.on_page_displayed.8")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.on_page_displayed.9")));
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.on_page_displayed.10")));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            if (this.activity.getSelectedPortal() == null) {
                player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.portals_menu.on_page_displayed.11")));
                return;
            }
            ru.sortix.parkourbeat.listeners.PortalWandListener.give(this.plugin, player);
            player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.portals_menu.on_page_displayed.12")));
            player.closeInventory();
        });

        this.setItem(6, 5, RegularItems.closeInventory(this.lang),
            event -> event.getPlayer().closeInventory());

        this.setItem(6, 9, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portals_menu.on_page_displayed.13")))
        ), event -> new LightShowMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }

    private void createPortal(@NonNull ClickEvent event) {
        Player player = event.getPlayer();

        if (this.getLightShow().getPortals().size() >= LightShowSettings.MAX_CUES) {
            player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.portals_menu.create_portal.1")));
            return;
        }

        Vector base = player.getLocation().toVector();
        Portal portal = new Portal(
            new Portal.Side(base, Portal.Facing.WALL_Z, Color.fromRGB(0x00FFAA)),
            new Portal.Side(base.clone().add(new Vector(0, 0, 4)),
                Portal.Facing.WALL_Z, Color.fromRGB(0xAA00FF)));

        if (!this.getLightShow().addPortal(portal)) {
            player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.portals_menu.create_portal.2")));
            return;
        }

        this.activity.setSelectedPortal(portal);
        this.level.getLevelSettings().updateParticleLocations();
        int number = this.getLightShow().getPortalsAmount();
        player.closeInventory();

        player.showTitle(net.kyori.adventure.title.Title.title(
            FallZonesMenu.text("&d&l" + number + Lang.raw(PlayerLang.of(player), "auto.portals_menu.create_portal.3")),
            FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.portals_menu.create_portal.4")),
            net.kyori.adventure.title.Title.Times.of(
                java.time.Duration.ofMillis(200),
                java.time.Duration.ofMillis(2200),
                java.time.Duration.ofMillis(600))));

        ru.sortix.parkourbeat.listeners.PortalWandListener.give(this.plugin, player);

        player.sendMessage(FallZonesMenu.text("&8&m                                        "));
        player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.portals_menu.create_portal.5") + number + Lang.raw(PlayerLang.of(player), "auto.portals_menu.create_portal.6")));
        player.sendMessage(FallZonesMenu.text(""));
        player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.portals_menu.create_portal.7")));
        player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.portals_menu.create_portal.8")));
        player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.portals_menu.create_portal.9")));
        player.sendMessage(FallZonesMenu.text(""));
        player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.portals_menu.create_portal.10")));
        player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.portals_menu.create_portal.11")));
        player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.portals_menu.create_portal.12")));
        player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.portals_menu.create_portal.13")));
        player.sendMessage(FallZonesMenu.text("&8&m                                        "));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
    }
}
