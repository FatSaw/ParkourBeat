package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;

/**
 * МЕНЮ РУД-НОТ.
 * <p>
 * Единственный законный источник нот: руда, взятая отсюда, помечена внутри и при
 * постановке попадает в карту. Любая другая руда - декор, и судья её не видит.
 */
public class DiggerOresMenu extends ParkourBeatInventory {

    private final @NonNull Level level;

    public DiggerOresMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Level level) {
        super(plugin, 4, lang, PbText.of("&8Копатель: руды-ноты"));
        this.level = level;
        this.updateItems();
    }

    private void updateItems() {
        this.clearInventory();

        DiggerOre[] ores = DiggerOre.values();
        for (int i = 0; i < ores.length; i++) {
            DiggerOre ore = ores[i];
            int row = 1 + i / 5;
            int column = 3 + i % 5;

            this.setItem(row, column, DiggerEditor.buildOreItem(ore, 64), event -> {
                Player player = event.getPlayer();
                int amount = event.isShift() ? 1 : 64;
                player.getInventory().addItem(DiggerEditor.buildOreItem(ore, amount));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.4f);
                player.sendActionBar(PbText.of("&a" + ore.getDisplay() + " &7в инвентаре"));
            });
        }

        DiggerLevelSettings settings = this.level.getLevelSettings().getGameSettings().getDiggerSettings();
        this.setItem(4, 5, ItemUtils.create(Material.BOOK, meta -> {
            meta.displayName(PbText.item("&8◆ &6Карта"));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Нот: &f" + settings.getNotesCount()));
            lore.add(PbText.item("&7BPM: &f" + settings.getBpm()));
            lore.add(PbText.item("&7Скорость: &f"
                + String.format("%.2f", settings.resolveSpeed()) + " бл/с"));
            lore.add(PbText.item("&7Блоков на долю: &f"
                + String.format("%.2f", settings.blocksPerBeat())));
            lore.add(Component.empty());
            lore.add(PbText.item("&8ЛКМ - взять стак, Shift - одну штуку"));
            meta.lore(lore);
        }), null);

        this.fillBorder();
    }
}
