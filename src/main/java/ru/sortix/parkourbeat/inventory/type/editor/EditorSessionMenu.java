package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.type.LevelsListMenu;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.item.ItemsManager;
import ru.sortix.parkourbeat.item.editor.EditorItem;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

public class EditorSessionMenu extends ParkourBeatInventory {
    public EditorSessionMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 3, lang, LangOptions.inventory_edit_session_title.getComponent(lang));

        this.setItem(2, 4, ItemUtils.create(Material.COMPARATOR, meta -> {
            meta.displayName(LangOptions.inventory_edit_session_params_name.getComponent(lang));
            meta.lore(LangOptions.inventory_edit_session_params_lore.getComponents(lang));
        }), event -> {
            Player player = event.getPlayer();
            // Выдаём все инструменты, если их нет
            plugin.get(ItemsManager.class).putAllItems(player, EditorItem.class);
            new EditorMainMenu(plugin, lang, activity).open(player);
        });

        this.setItem(2, 6, ItemUtils.create(Material.WRITABLE_BOOK, meta -> {
            meta.displayName(LangOptions.inventory_edit_session_levels_name.getComponent(lang));
            meta.lore(LangOptions.inventory_edit_session_levels_lore.getComponents(lang));
        }), event -> {
            new LevelsListMenu(plugin, lang, LevelsListMenu.DisplayMode.SELF, event.getPlayer(), event.getPlayer().getUniqueId()).open(event.getPlayer());
        });
    }
}
