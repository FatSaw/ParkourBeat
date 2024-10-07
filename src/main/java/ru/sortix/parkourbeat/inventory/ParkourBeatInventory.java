package ru.sortix.parkourbeat.inventory;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.event.inventory.InventoryType;
import ru.sortix.parkourbeat.ParkourBeat;

public abstract class ParkourBeatInventory extends PluginInventory<ParkourBeat> {
    protected ParkourBeatInventory(@NonNull ParkourBeat plugin, int rows, String lang, @NonNull Component title) {
        super(plugin, rows, lang, title);
    }

    protected ParkourBeatInventory(@NonNull ParkourBeat plugin, @NonNull InventoryType type, String lang, @NonNull Component title) {
        super(plugin, type, lang, title);
    }
}
