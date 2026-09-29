package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.player.AfkManager;
import ru.sortix.parkourbeat.player.PlayerSettingsManager;
import ru.sortix.parkourbeat.utils.lang.Lang;

import java.util.ArrayList;
import java.util.List;

public class AfkMenu extends ParkourBeatInventory {
    private static final int[] COLUMNS = {3, 4, 6, 7};

    public AfkMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer) {
        super(plugin, 4, lang, Lang.item(lang, "inventory.afk.title"));
        this.render(viewer);
    }

    private void render(@NonNull Player viewer) {
        AfkManager afk = this.plugin.get(AfkManager.class);
        PlayerSettingsManager settings = this.plugin.get(PlayerSettingsManager.class);

        boolean isAfk = afk.isAfk(viewer.getUniqueId());
        boolean pending = afk.isPending(viewer.getUniqueId());

        this.setItem(2, 5, ItemUtils.create(isAfk ? Material.GREEN_BED : Material.WHITE_BED, meta -> {
            meta.displayName(Lang.item(this.lang, isAfk
                ? "inventory.afk.toggle.name_leave"
                : "inventory.afk.toggle.name_enter"));
            List<Component> lore = new ArrayList<>(
                Lang.lore(this.lang, isAfk
                    ? "inventory.afk.toggle.lore_leave"
                    : "inventory.afk.toggle.lore_enter"));
            lore.add(Lang.item(this.lang, pending
                ? "inventory.afk.toggle.pending"
                : "inventory.afk.toggle.click"));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            afk.requestToggle(player, !isAfk);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            player.sendMessage(Lang.text(this.lang, "inventory.afk.toggle.notice"));
            player.closeInventory();
        });

        int current = settings.getAutoAfkMinutes(viewer.getUniqueId());
        for (int i = 0; i < AfkManager.AUTO_AFK_MINUTES.length; i++) {
            int minutes = AfkManager.AUTO_AFK_MINUTES[i];
            boolean selected = minutes == current;
            this.setItem(3, COLUMNS[i], ItemUtils.create(
                selected ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, meta -> {
                    String prefixKey = selected ? "selected_" : "";
                    meta.displayName(Lang.item(this.lang, minutes == 0
                            ? "inventory.afk.auto." + prefixKey + "name_off"
                            : "inventory.afk.auto." + prefixKey + "name",
                        "%minutes%", String.valueOf(minutes)));
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.empty());
                    lore.add(Lang.item(this.lang, minutes == 0
                            ? "inventory.afk.auto.lore_off"
                            : "inventory.afk.auto.lore",
                        "%minutes%", String.valueOf(minutes)));
                    lore.add(Component.empty());
                    lore.add(Lang.item(this.lang, selected
                        ? "inventory.common.selected"
                        : "inventory.common.select"));
                    meta.lore(lore);
                }), event -> {
                Player player = event.getPlayer();
                settings.setAutoAfkMinutes(player.getUniqueId(), minutes);
                settings.save();
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
                new AfkMenu(this.plugin, this.lang, player).open(player);
            });
        }

        this.setItem(3, 1, ItemUtils.create(Material.CLOCK, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.afk.header.name"));
            meta.lore(Lang.lore(this.lang, "inventory.afk.header.lore",
                "%current%", current == 0
                    ? Lang.raw(this.lang, "inventory.afk.header.off")
                    : Lang.raw(this.lang, "inventory.afk.header.minutes",
                    "%minutes%", String.valueOf(current))));
        }), null);

        this.setItem(4, 5, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(Lang.item(this.lang, "inventory.common.back"))
        ), event -> new ServerMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));
    }
}
