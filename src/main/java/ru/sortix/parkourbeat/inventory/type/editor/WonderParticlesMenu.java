package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.wonder.WonderEffect;
import ru.sortix.parkourbeat.utils.text.PbText;
import ru.sortix.parkourbeat.utils.wonder.WonderSpec;

import java.util.ArrayList;
import java.util.List;

/** Выбор типа частиц для эффекта. */
public class WonderParticlesMenu extends ParkourBeatInventory implements EditLevelMenu {

    private static final String[][] PARTICLES = {
        {"end_rod", "END_ROD", "Белые искры, основа всего"},
        {"flame", "BLAZE_POWDER", "Тёплое пламя"},
        {"soul_fire", "SOUL_LANTERN", "Бирюзовый огонь душ"},
        {"soul", "SOUL_SAND", "Медленные души"},
        {"spark", "FIREWORK_STAR", "Салютные искры"},
        {"crit", "IRON_SWORD", "Резкие удары"},
        {"magic_crit", "ENCHANTED_BOOK", "Магический удар"},
        {"enchant", "ENCHANTING_TABLE", "Руны из стола зачарований"},
        {"witch", "POTION", "Фиолетовая ворожба"},
        {"portal", "OBSIDIAN", "Портальная пыль"},
        {"dragon", "DRAGON_BREATH", "Дыхание дракона"},
        {"cloud", "WHITE_WOOL", "Плотные облака"},
        {"smoke", "COAL", "Дым"},
        {"totem", "TOTEM_OF_UNDYING", "Праздничные блёстки"},
        {"happy", "BONE_MEAL", "Зелёные искры радости"},
        {"heart", "POPPY", "Сердечки"},
        {"note", "NOTE_BLOCK", "Ноты"},
        {"lava", "LAVA_BUCKET", "Лавовые брызги"},
        {"snow", "SNOWBALL", "Снег"},
        {"dust", "REDSTONE", "Любой цвет, но дороже всех"}
    };

    private final @NonNull EditActivity activity;
    private final @NonNull WonderEffect effect;

    public WonderParticlesMenu(@NonNull ParkourBeat plugin, String lang,
                               @NonNull EditActivity activity, @NonNull WonderEffect effect) {
        super(plugin, 5, lang, PbText.of(Lang.raw(lang, "auto.wonder_particles_menu.wonder_particles_menu.1")));
        this.activity = activity;
        this.effect = effect;
        this.updateItems();
    }

    public void updateItems() {
        this.clearInventory();
        String current = WonderSpec.get(this.effect.getSpec(), "particle");
        if (current == null) current = "end_rod";

        int slot = 0;
        for (String[] entry : PARTICLES) {
            int row = 2 + (slot / 7);
            int column = 2 + (slot % 7);
            slot++;
            if (row > 4) break;

            Material icon;
            try {
                icon = Material.valueOf(entry[1]);
            } catch (IllegalArgumentException e) {
                icon = Material.PAPER;
            }
            boolean selected = entry[0].equalsIgnoreCase(current);
            final Material finalIcon = icon;

            this.setItem(row, column, ItemUtils.create(selected ? Material.LIME_DYE : finalIcon, meta -> {
                meta.displayName(PbText.of((selected ? "&a▸ " : "&f") + entry[0])
                    .decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(line("&7" + entry[2]));
                if (entry[0].equals("dust")) {
                    lore.add(line(Lang.raw(this.lang, "auto.wonder_particles_menu.update_items.1")));
                }
                lore.add(Component.empty());
                lore.add(line(Lang.raw(this.lang, "auto.wonder_particles_menu.update_items.2")));
                meta.lore(lore);
            }), event -> {
                this.effect.setSpec(WonderSpec.set(this.effect.getSpec(), "particle", entry[0]));
                Player player = event.getPlayer();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.5f);
                new WonderEffectMenu(this.plugin, this.lang, this.activity, this.effect).open(player);
            });
        }

        this.fillBorder();
        this.setItem(5, 5, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_particles_menu.update_items.3")).decoration(TextDecoration.ITALIC, false))
        ), event -> new WonderEffectMenu(this.plugin, this.lang, this.activity, this.effect)
            .open(event.getPlayer()));
    }

    private static Component line(@NonNull String text) {
        return PbText.of(text).decoration(TextDecoration.ITALIC, false);
    }
}
