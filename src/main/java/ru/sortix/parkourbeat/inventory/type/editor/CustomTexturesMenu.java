package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;
import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.RegularItems;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.TextureVersionRange;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.player.CustomTexturesManager;

import java.util.ArrayList;
import java.util.List;

public class CustomTexturesMenu extends ParkourBeatInventory implements EditLevelMenu {
    private final @NonNull EditActivity activity;
    private final @NonNull Level level;

    public CustomTexturesMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 5, lang, FallZonesMenu.text(Lang.raw(lang, "auto.custom_textures_menu.custom_textures_menu.1")));
        this.activity = activity;
        this.level = activity.getLevel();
        this.updateItems();
    }

    @javax.annotation.Nullable
    private CustomTexturesManager textures() {
        try {
            return this.plugin.get(CustomTexturesManager.class);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean requireManager(@NonNull Player player) {
        if (this.textures() != null) return true;
        player.sendMessage(FallZonesMenu.text(
            Lang.raw(this.lang, "auto.custom_textures_menu.require_manager.1")));
        return false;
    }

    @NonNull
    private GameSettings settings() {
        return this.level.getLevelSettings().getGameSettings();
    }

    public void updateItems() {
        this.clearInventory();

        GameSettings settings = this.settings();
        TextureVersionRange current = settings.getTextureVersionRange();

        this.setItem(1, 5, ItemUtils.create(
            settings.isCustomTextures() ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, meta -> {
                meta.displayName(FallZonesMenu.text(settings.isCustomTextures()
                    ? Lang.raw(this.lang, "auto.custom_textures_menu.update_items.1") : Lang.raw(this.lang, "auto.custom_textures_menu.update_items.2")));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.3")
                    + (current == null ? Lang.raw(this.lang, "auto.custom_textures_menu.update_items.4") : current.getLabel())));
                if (settings.isCustomTextures()) {
                    lore.add(Component.empty());
                    lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.5")));
                    lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.6")));
                }
                meta.lore(lore);
            }), null);

        int index = 0;
        for (TextureVersionRange range : TextureVersionRange.values()) {
            int row = 2 + index / 9;
            int column = 1 + index % 9;
            index++;
            if (row > 3) break;

            boolean selected = range == current;
            this.setItem(row, column, ItemUtils.create(
                selected ? Material.SUNFLOWER : Material.SUNFLOWER, meta -> {
                    meta.displayName(FallZonesMenu.text((selected ? "&a" : "&e") + range.getLabel()));
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.empty());
                    if (selected) {
                        lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.7")));
                    } else {
                        lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.8")));
                    }
                    meta.lore(lore);
                    if (selected) meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                }), event -> this.selectRange(event.getPlayer(), range));
        }

        this.setItem(4, 5, ItemUtils.create(
            current == null ? Material.LIME_DYE : Material.BARRIER, meta -> {
                meta.displayName(FallZonesMenu.text(current == null
                    ? Lang.raw(this.lang, "auto.custom_textures_menu.update_items.9") : Lang.raw(this.lang, "auto.custom_textures_menu.update_items.10")));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                if (current == null) {
                    lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.11")));
                } else {
                    lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.12")));
                    lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.13")));
                    lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.14")));
                }
                meta.lore(lore);
            }), event -> this.clearRange(event.getPlayer()));

        this.setItem(5, 3, ItemUtils.create(Material.PAPER, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.15")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.16")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.17")));
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.18")));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            if (!this.requireManager(player)) return;
            if (this.settings().getTextureVersionRange() == null) {
                player.sendMessage(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.19")));
                return;
            }
            player.closeInventory();
            this.textures().requestUploadLink(
                player, this.level.getUniqueId(), this.settings().getDisplayNameLegacy(false));
        });

        this.setItem(5, 5, RegularItems.closeInventory(this.lang),
            event -> event.getPlayer().closeInventory());

        if (settings.isCustomTextures()) {
            this.setItem(5, 7, ItemUtils.create(Material.LAVA_BUCKET, meta -> {
                meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.20")));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.21")));
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.22")));
                meta.lore(lore);
            }), event -> {
                Player player = event.getPlayer();
                if (!this.requireManager(player)) return;
                this.textures().deleteTextures(player, this.level.getUniqueId());
                player.closeInventory();
            });
        }

        this.setItem(5, 9, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.update_items.23")))
        ), event -> new EditorMainMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }

    private void clearRange(@NonNull Player player) {
        if (this.settings().getTextureVersionRange() == null) return;

        this.settings().setTextureVersionRange(null);
        this.updateItems();

        player.sendMessage(FallZonesMenu.text(Lang.raw(this.lang, "auto.custom_textures_menu.clear_range.1")));

        CustomTexturesManager manager = this.textures();
        if (manager != null) manager.clearVersionRange(player, this.level.getUniqueId());
    }

    private void selectRange(@NonNull Player player, @NonNull TextureVersionRange range) {
        this.settings().setTextureVersionRange(range);
        this.updateItems();

        player.sendMessage(FallZonesMenu.text(
            Lang.raw(this.lang, "auto.custom_textures_menu.select_range.1") + range.getLabel()));

        CustomTexturesManager manager = this.textures();
        if (manager == null) {
            player.sendMessage(FallZonesMenu.text(
                Lang.raw(this.lang, "auto.custom_textures_menu.select_range.2")));
            return;
        }
        manager.setVersionRange(player, this.level.getUniqueId(), range);
    }
}
