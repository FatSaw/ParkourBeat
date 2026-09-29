package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;
import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.FallZoneRenderer;
import ru.sortix.parkourbeat.levels.settings.FallZone;
import ru.sortix.parkourbeat.listeners.FallZoneWandListener;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;

import java.util.ArrayList;
import java.util.List;

public class FallZoneMenu extends LightShowElementMenu<FallZone> {

    public FallZoneMenu(@NonNull ParkourBeat plugin, String lang,
                        @NonNull EditActivity activity, @NonNull FallZone zone) {
        super(plugin, lang, activity, zone, FallZonesMenu.text(Lang.raw(lang, "auto.fall_zone_menu.fall_zone_menu.1")));
        this.updateItems();
    }

    @Override
    protected void addSpecificItems() {
        this.setItem(1, 5, ItemUtils.create(Material.LADDER, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.fall_zone_menu.add_specific_items.1")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.fall_zone_menu.add_specific_items.2")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.fall_zone_menu.add_specific_items.3") + this.element.getDeathY()));
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.fall_zone_menu.add_specific_items.4")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.fall_zone_menu.add_specific_items.5")));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            if (!event.isLeft()) {
                this.element.setDeathY(player.getLocation().getBlockY());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                this.updateItems();
                return;
            }
            this.requestDeathY(player);
        });

        boolean enabled = this.element.isEnabled();
        this.setItem(1, 7, ItemUtils.create(enabled ? Material.LIME_DYE : Material.GRAY_DYE, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.fall_zone_menu.add_specific_items.6")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(enabled ? Lang.raw(this.lang, "auto.fall_zone_menu.add_specific_items.7") : Lang.raw(this.lang, "auto.fall_zone_menu.add_specific_items.8")));
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.fall_zone_menu.add_specific_items.9")));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            this.element.setEnabled(!this.element.isEnabled());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            this.updateItems();
        });

        this.setItem(1, 3, ItemUtils.create(Material.SPECTRAL_ARROW, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.fall_zone_menu.add_specific_items.10")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.fall_zone_menu.add_specific_items.11")));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            player.closeInventory();
            FallZoneRenderer.preview(this.plugin, player, this.level);
        });
    }

    private void requestDeathY(@NonNull Player player) {
        player.closeInventory();
        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) return;

        player.sendMessage(FallZonesMenu.text(Lang.raw(this.lang, "auto.fall_zone_menu.request_death_y.1")));
        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) return;
            int value;
            try {
                value = Integer.parseInt(message.trim());
            } catch (NumberFormatException e) {
                player.sendMessage(FallZonesMenu.text(Lang.raw(this.lang, "auto.fall_zone_menu.request_death_y.2")));
                return;
            }
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                this.element.setDeathY(value);
                player.sendMessage(FallZonesMenu.text(Lang.raw(this.lang, "auto.fall_zone_menu.request_death_y.3") + this.element.getDeathY()));
                this.reopen(player);
            });
        });
    }

    @Override
    protected @NonNull ItemStack createWandIcon() {
        return FallZoneWandListener.createItem(this.plugin);
    }

    @Override
    protected void giveWand(@NonNull Player player) {
        FallZoneWandListener.give(this.plugin, player);
        player.sendMessage(FallZonesMenu.text(Lang.raw(this.lang, "auto.fall_zone_menu.give_wand.1")
            + Lang.raw(this.lang, "auto.fall_zone_menu.give_wand.2")));
    }

    @Override
    protected boolean removeElement() {
        return this.getLightShow().removeFallZone(this.element);
    }

    @Override
    protected void openListMenu(@NonNull Player player) {
        new FallZonesMenu(this.plugin, this.lang, this.activity).open(player);
    }

    @Override
    protected void reopen(@NonNull Player player) {
        new FallZoneMenu(this.plugin, this.lang, this.activity, this.element).open(player);
    }
}
