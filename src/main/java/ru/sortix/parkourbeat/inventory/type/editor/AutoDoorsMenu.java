package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.RegularItems;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.AutoDoorEngine;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.AutoDoor;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.listeners.AutoDoorWandListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AutoDoorsMenu extends PaginatedMenu<ParkourBeat, AutoDoor> implements EditLevelMenu {
    private final @NonNull EditActivity activity;
    private final @NonNull Level level;

    public AutoDoorsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 6, lang, FallZonesMenu.text(Lang.raw(lang, "auto.auto_doors_menu.auto_doors_menu.1")), 0, 5 * 9);
        this.activity = activity;
        this.level = activity.getLevel();
        this.updateAllItems();
    }

    @NonNull
    private LightShowSettings getLightShow() {
        return this.level.getLightShow();
    }

    @Override
    protected @NonNull Collection<AutoDoor> getAllItems() {
        return new ArrayList<>(this.getLightShow().getAutoDoors());
    }

    @Override
    protected @NonNull ItemStack createItemDisplay(@NonNull AutoDoor door) {
        boolean selected = this.activity.getSelectedAutoDoor() == door;
        String type = AutoDoorEngine.describeType(this.level.getWorld(), door);
        boolean missing = "блок не найден".equals(type);

        Material icon;
        if (missing) icon = Material.BARRIER;
        else if (!door.isEnabled()) icon = Material.GRAY_STAINED_GLASS;
        else icon = selected ? Material.OAK_DOOR : Material.IRON_DOOR;

        return ItemUtils.create(icon, meta -> {
            meta.displayName(FallZonesMenu.text((selected ? "&e" : "&6") + Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.1") + door.format()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.2") + type));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.3") + door.formatRadius() + Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.4")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.5") + (door.isInverted()
                ? Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.6")
                : Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.7"))));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.8") + (door.isPlaySound() ? Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.9") : Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.10"))));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.11") + (door.isEnabled() ? Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.12") : Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.13"))));
            if (missing) {
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.14")));
            }
            if (selected) {
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.15")));
            }
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.16")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.17")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.create_item_display.18")));
            meta.lore(lore);
        });
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull AutoDoor door) {
        Player player = event.getPlayer();

        if (event.isLeft()) {
            this.activity.setSelectedAutoDoor(door);
            new AutoDoorMenu(this.plugin, this.lang, this.activity, door).open(player);
            return;
        }

        if (event.isShift()) {
            if (this.activity.getSelectedAutoDoor() == door) this.activity.setSelectedAutoDoor(null);
            if (!this.getLightShow().removeAutoDoor(door)) return;
            player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.auto_doors_menu.on_click.1")));
            this.updateAllItems();
            return;
        }

        this.activity.setSelectedAutoDoor(door);
        player.closeInventory();
        org.bukkit.Location target = door.getCenter(this.level.getWorld());
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        ru.sortix.parkourbeat.world.TeleportUtils.teleportAsync(this.plugin, player,
            target.clone().add(0, 0, 0));
        player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.auto_doors_menu.on_click.2") + door.format()));
    }

    @Override
    protected void onPageDisplayed() {
        this.setNextPageItem(6, 3);
        this.setPreviousPageItem(6, 7);

        if (this.getLightShow().getAutoDoors().isEmpty()) {
            this.setItem(1, 5, ItemUtils.create(Material.BARRIER, meta -> {
                meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.on_page_displayed.1")));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.on_page_displayed.2")));
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.on_page_displayed.3")));
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.on_page_displayed.4")));
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.on_page_displayed.5")));
                meta.lore(lore);
            }), null);
        }

        this.setItem(6, 1, ItemUtils.create(Material.TRIPWIRE_HOOK, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.on_page_displayed.6")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.on_page_displayed.7")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.on_page_displayed.8")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.on_page_displayed.9")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.on_page_displayed.10")));
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.on_page_displayed.11")));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            AutoDoorWandListener.give(this.plugin, player);
            player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.auto_doors_menu.on_page_displayed.12")));
            player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.auto_doors_menu.on_page_displayed.13")));
            player.closeInventory();
        });

        this.setItem(6, 2, ItemUtils.create(Material.REDSTONE_LAMP, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.on_page_displayed.14")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.on_page_displayed.15")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.on_page_displayed.16")));
            meta.lore(lore);
        }), event -> {
            AutoDoorEngine.resetAll(this.level);
            event.getPlayer().sendMessage(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.on_page_displayed.17")));
        });

        this.setItem(6, 5, RegularItems.closeInventory(this.lang),
            event -> event.getPlayer().closeInventory());

        this.setItem(6, 9, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_doors_menu.on_page_displayed.18")))
        ), event -> new LightShowMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }
}
