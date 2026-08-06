package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.SkyType;

import java.util.List;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SelectSkyMenu extends ParkourBeatInventory implements EditLevelMenu {
    private final @NonNull SkyType currentSky;
    private final @NonNull BiConsumer<Player, SkyType> onSelect;
    private final @NonNull Consumer<Player> onBack;

    public SelectSkyMenu(@NonNull ParkourBeat plugin,
                         String lang,
                         @NonNull EditActivity activity,
                         @NonNull SkyType currentSky,
                         @NonNull BiConsumer<Player, SkyType> onSelect,
                         @NonNull Consumer<Player> onBack
    ) {
        super(plugin, 3, lang, LangOptions.inventory_editorsky_title.getComponent(lang));
        this.currentSky = currentSky;
        this.onSelect = onSelect;
        this.onBack = onBack;

        List<SkyType> allSkyTypes = new java.util.ArrayList<>(SkyType.available(
            activity.getLevel().getLevelSettings().getWorldSettings().getDirection()));
        allSkyTypes.sort(java.util.Comparator.comparingInt(Enum::ordinal));
        for (int index = 0; index < allSkyTypes.size() && index < 7; index++) {
            SkyType skyType = allSkyTypes.get(index);
            boolean selected = skyType == this.currentSky;
            this.setItem(
                2,
                index + 2,
                ItemUtils.create(skyType.getIconMaterial(), meta -> {
                    meta.displayName(skyType.getDisplayName(lang));
                    meta.lore((selected
                        ? LangOptions.inventory_editorsky_lore_selected
                        : LangOptions.inventory_editorsky_lore_notselected).getComponents(lang));
                    meta.addItemFlags(ItemFlag.values());
                }),
                event -> this.onSelect.accept(event.getPlayer(), skyType));
        }

        this.setItem(
            3,
            5,
            ItemUtils.create(Material.REDSTONE_TORCH, meta ->
                meta.displayName(LangOptions.inventory_editorsky_back.getComponent(lang))),
            event -> this.onBack.accept(event.getPlayer()));
    }
}
