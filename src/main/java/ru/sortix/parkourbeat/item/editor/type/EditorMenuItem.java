package ru.sortix.parkourbeat.item.editor.type;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.type.editor.EditorMainMenu;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.item.editor.EditorItem;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

public class EditorMenuItem extends EditorItem {
    public EditorMenuItem(@NonNull ParkourBeat plugin, String lang, int slot) {
        super(plugin, lang, slot, 20, ItemUtils.create(Material.COMPARATOR, (meta) -> {
            meta.displayName(LangOptions.item_editor_parameters.getComponent(lang));
        }));
    }

    @Override
    public void onUse(@NonNull PlayerInteractEvent event, @NonNull EditActivity activity) {
    	new EditorMainMenu(this.plugin, lang, activity).open(event.getPlayer());
    }
}
