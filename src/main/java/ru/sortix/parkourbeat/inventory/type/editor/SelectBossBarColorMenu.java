package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.LevelBossBarColor;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SelectBossBarColorMenu extends ParkourBeatInventory implements EditLevelMenu {
    private final @NonNull BiConsumer<Player, LevelBossBarColor> onSelect;
    private final @NonNull Consumer<Player> onBack;

    public SelectBossBarColorMenu(@NonNull ParkourBeat plugin,
                                  String lang,
                                  @SuppressWarnings("unused") @NonNull EditActivity activity,
                                  @NonNull LevelBossBarColor currentColor,
                                  @NonNull BiConsumer<Player, LevelBossBarColor> onSelect,
                                  @NonNull Consumer<Player> onBack
    ) {
        super(plugin, 3, lang, LangOptions.inventory_editorbossbar_title.getComponent(lang));
        this.onSelect = onSelect;
        this.onBack = onBack;

        LevelBossBarColor[] allColors = LevelBossBarColor.values();
        for (int index = 0; index < allColors.length && index < 7; index++) {
            LevelBossBarColor color = allColors[index];
            boolean selected = color == currentColor;
            this.setItem(
                2,
                index + 2,
                ItemUtils.create(color.getIconMaterial(), meta -> {
                    meta.displayName(color.getDisplayName(lang));
                    meta.lore((selected
                        ? LangOptions.inventory_editorbossbar_lore_selected
                        : LangOptions.inventory_editorbossbar_lore_notselected).getComponents(lang));
                    meta.addItemFlags(ItemFlag.values());
                }),
                event -> this.onSelect.accept(event.getPlayer(), color));
        }

        this.setItem(
            3,
            5,
            ItemUtils.create(Material.REDSTONE_TORCH, meta ->
                meta.displayName(LangOptions.inventory_editorbossbar_back.getComponent(lang))),
            event -> this.onBack.accept(event.getPlayer()));
    }
}
