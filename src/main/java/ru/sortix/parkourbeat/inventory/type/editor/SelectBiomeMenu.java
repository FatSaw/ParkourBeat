package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.LevelBiome;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SelectBiomeMenu extends ParkourBeatInventory implements EditLevelMenu {
    public SelectBiomeMenu(@NonNull ParkourBeat plugin,
                           String lang,
                           @SuppressWarnings("unused") @NonNull EditActivity activity,
                           @NonNull LevelBiome currentBiome,
                           @NonNull BiConsumer<Player, LevelBiome> onSelect,
                           @NonNull Consumer<Player> onBack
    ) {
        super(plugin, 4, lang, LangOptions.inventory_editorbiomeselect_title.getComponent(lang));

        List<LevelBiome> biomes = LevelBiome.supported();
        for (int index = 0; index < biomes.size() && index < 14; index++) {
            LevelBiome biome = biomes.get(index);
            boolean selected = biome == currentBiome;
            int row = (index / 7) + 1;
            int column = (index % 7) + 2;
            this.setItem(
                row,
                column,
                ItemUtils.create(biome.getIconMaterial(), meta -> {
                    meta.displayName(biome.getDisplayName(lang));
                    meta.lore((selected
                        ? LangOptions.inventory_editorbiomeselect_lore_selected
                        : LangOptions.inventory_editorbiomeselect_lore_notselected).getComponents(lang));
                    meta.addItemFlags(ItemFlag.values());
                }),
                event -> onSelect.accept(event.getPlayer(), biome));
        }

        this.setItem(
            4,
            5,
            ItemUtils.create(Material.REDSTONE_TORCH, meta ->
                meta.displayName(LangOptions.inventory_editorbiomeselect_back.getComponent(lang))),
            event -> onBack.accept(event.getPlayer()));
    }
}
