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
import ru.sortix.parkourbeat.inventory.RegularItems;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.AutoDoorEngine;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.AutoDoor;
import ru.sortix.parkourbeat.listeners.AutoDoorWandListener;

import java.util.ArrayList;
import java.util.List;

public class AutoDoorMenu extends ParkourBeatInventory implements EditLevelMenu {
    private final @NonNull EditActivity activity;
    private final @NonNull Level level;
    private final @NonNull AutoDoor door;

    public AutoDoorMenu(@NonNull ParkourBeat plugin, String lang,
                        @NonNull EditActivity activity, @NonNull AutoDoor door) {
        super(plugin, 5, lang, FallZonesMenu.text(Lang.raw(lang, "auto.auto_door_menu.auto_door_menu.1")));
        this.activity = activity;
        this.level = activity.getLevel();
        this.door = door;
        this.updateItems();
    }

    public void updateItems() {
        this.clearInventory();

        String type = AutoDoorEngine.describeType(this.level.getWorld(), this.door);

        this.setItem(1, 5, ItemUtils.create(Material.OAK_DOOR, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.1") + this.door.format()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.2") + type));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.3") + this.door.formatRadius() + Lang.raw(this.lang, "auto.auto_door_menu.update_items.4")));
            meta.lore(lore);
        }), null);

        this.setItem(3, 2, ItemUtils.create(Material.RED_CONCRETE, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.5")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.6") + fmt(AutoDoor.RADIUS_STEP) + Lang.raw(this.lang, "auto.auto_door_menu.update_items.7")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.8") + fmt(AutoDoor.RADIUS_STEP * 4) + Lang.raw(this.lang, "auto.auto_door_menu.update_items.9")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.10") + fmt(AutoDoor.MIN_RADIUS) + Lang.raw(this.lang, "auto.auto_door_menu.update_items.11")));
            meta.lore(lore);
        }), event -> this.changeRadius(event, -1));

        this.setItem(3, 3, ItemUtils.create(Material.LIME_CONCRETE, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.12")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.13") + fmt(AutoDoor.RADIUS_STEP) + Lang.raw(this.lang, "auto.auto_door_menu.update_items.14")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.15") + fmt(AutoDoor.RADIUS_STEP * 4) + Lang.raw(this.lang, "auto.auto_door_menu.update_items.16")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.17") + fmt(AutoDoor.MAX_RADIUS) + Lang.raw(this.lang, "auto.auto_door_menu.update_items.18")));
            meta.lore(lore);
        }), event -> this.changeRadius(event, 1));

        this.setItem(3, 5, ItemUtils.create(
            this.door.isInverted() ? Material.PISTON : Material.STICKY_PISTON, meta -> {
                meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.19")));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(this.door.isInverted()
                    ? Lang.raw(this.lang, "auto.auto_door_menu.update_items.20")
                    : Lang.raw(this.lang, "auto.auto_door_menu.update_items.21")));
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.22")));
                meta.lore(lore);
            }), event -> {
            this.door.setInverted(!this.door.isInverted());
            AutoDoorEngine.resetAll(this.level);
            this.click(event.getPlayer());
        });

        this.setItem(3, 7, ItemUtils.create(
            this.door.isPlaySound() ? Material.NOTE_BLOCK : Material.BARRIER, meta -> {
                meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.23")));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(this.door.isPlaySound()
                    ? Lang.raw(this.lang, "auto.auto_door_menu.update_items.24")
                    : Lang.raw(this.lang, "auto.auto_door_menu.update_items.25")));
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.26")));
                meta.lore(lore);
            }), event -> {
            this.door.setPlaySound(!this.door.isPlaySound());
            this.click(event.getPlayer());
        });

        this.setItem(3, 8, ItemUtils.create(
            this.door.isEnabled() ? Material.LEVER : Material.GRAY_DYE, meta -> {
                meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.27")));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(this.door.isEnabled()
                    ? Lang.raw(this.lang, "auto.auto_door_menu.update_items.28")
                    : Lang.raw(this.lang, "auto.auto_door_menu.update_items.29")));
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.30")));
                meta.lore(lore);
            }), event -> {
            this.door.setEnabled(!this.door.isEnabled());
            AutoDoorEngine.resetAll(this.level);
            this.click(event.getPlayer());
        });

        this.setItem(5, 1, ItemUtils.create(Material.TRIPWIRE_HOOK, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.31")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.32")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.33")));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            this.activity.setSelectedAutoDoor(this.door);
            AutoDoorWandListener.give(this.plugin, player);
            player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.auto_door_menu.update_items.34")));
            player.closeInventory();
        });

        this.setItem(5, 3, ItemUtils.create(Material.ENDER_PEARL, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.35")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.36")));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            this.activity.setSelectedAutoDoor(this.door);
            player.closeInventory();
            org.bukkit.Location target = this.door.getCenter(this.level.getWorld());
            target.setYaw(player.getLocation().getYaw());
            target.setPitch(player.getLocation().getPitch());
            ru.sortix.parkourbeat.world.TeleportUtils.teleportAsync(this.plugin, player, target);
        });

        this.setItem(5, 5, RegularItems.closeInventory(this.lang),
            event -> event.getPlayer().closeInventory());

        this.setItem(5, 7, ItemUtils.create(Material.LAVA_BUCKET, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.37")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.38")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.39")));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            if (this.activity.getSelectedAutoDoor() == this.door) {
                this.activity.setSelectedAutoDoor(null);
            }
            this.level.getLightShow().removeAutoDoor(this.door);
            AutoDoorEngine.resetAll(this.level);
            player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.auto_door_menu.update_items.40")));
            new AutoDoorsMenu(this.plugin, this.lang, this.activity).open(player);
        });

        this.setItem(5, 9, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.auto_door_menu.update_items.41")))
        ), event -> new AutoDoorsMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }

    private void changeRadius(@NonNull ClickEvent event, int sign) {
        // ClickEvent знает только левый клик, правый - это "не левый".
        double step = AutoDoor.RADIUS_STEP * (event.isLeft() ? 1 : 4);
        this.door.setRadius(this.door.getRadius() + step * sign);
        this.click(event.getPlayer());
    }

    private void click(@NonNull Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.4f);
        this.updateItems();
    }

    @NonNull
    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
