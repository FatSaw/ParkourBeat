package ru.sortix.parkourbeat.inventory.type;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.player.PlayerSettingsManager;

import java.util.ArrayList;
import java.util.List;

public class MusicSettingsMenu extends ParkourBeatInventory {
    private static final int[] COLUMNS = {3, 4, 6, 7};

    public MusicSettingsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer) {
        super(plugin, 3, lang, ServerMenu.text(Lang.raw(PlayerLang.of(viewer), "auto.music_settings_menu.music_settings_menu.1")));
        this.render(viewer);
    }

    private void render(@NonNull Player viewer) {
        PlayerSettingsManager settings = this.plugin.get(PlayerSettingsManager.class);
        int current = settings.getMusicVolume(viewer.getUniqueId());

        for (int i = 0; i < PlayerSettingsManager.VOLUME_STEPS.length; i++) {
            int value = PlayerSettingsManager.VOLUME_STEPS[i];
            boolean selected = value == current;
            this.setItem(2, COLUMNS[i], ItemUtils.create(
                selected ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, meta -> {
                    meta.displayName(ServerMenu.text((selected ? "&a" : "&7") + Lang.raw(PlayerLang.of(viewer), "auto.music_settings_menu.render.1") + value + "%"));
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.empty());
                    lore.add(ServerMenu.text(selected ? Lang.raw(PlayerLang.of(viewer), "auto.music_settings_menu.render.2") : Lang.raw(PlayerLang.of(viewer), "auto.music_settings_menu.render.3")));
                    meta.lore(lore);
                }), event -> {
                Player player = event.getPlayer();
                settings.setMusicVolume(player.getUniqueId(), value);
                settings.save();
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
                new MusicSettingsMenu(this.plugin, this.lang, player).open(player);
            });
        }

        this.setItem(1, 5, ItemUtils.create(Material.NOTE_BLOCK, meta -> {
            meta.displayName(ServerMenu.text(Lang.raw(PlayerLang.of(viewer), "auto.music_settings_menu.render.4")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(ServerMenu.text(Lang.raw(PlayerLang.of(viewer), "auto.music_settings_menu.render.5")));
            lore.add(ServerMenu.text(Lang.raw(PlayerLang.of(viewer), "auto.music_settings_menu.render.6") + current + "%"));
            lore.add(Component.empty());
            lore.add(ServerMenu.text(Lang.raw(PlayerLang.of(viewer), "auto.music_settings_menu.render.7")));
            lore.add(ServerMenu.text(Lang.raw(PlayerLang.of(viewer), "auto.music_settings_menu.render.8")));
            meta.lore(lore);
        }), null);

        this.setItem(3, 5, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(ServerMenu.text(Lang.raw(PlayerLang.of(viewer), "auto.music_settings_menu.render.9")))
        ), event -> new SettingsMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));
    }
}
