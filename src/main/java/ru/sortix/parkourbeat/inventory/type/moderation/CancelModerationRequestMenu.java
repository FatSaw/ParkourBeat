package ru.sortix.parkourbeat.inventory.type.moderation;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.type.LevelsListMenu;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.ModerationStatus;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

public class CancelModerationRequestMenu extends ParkourBeatInventory {
    public CancelModerationRequestMenu(@NonNull ParkourBeat plugin, String lang, @NonNull GameSettings settings) {
        super(plugin, 5, lang, LangOptions.inventory_moderationrequest_title.getComponent(lang));

        this.setItem(3, 5, ItemUtils.create(
            Material.BARRIER, meta -> {
                meta.displayName(LangOptions.inventory_moderationrequest_remove_name.getComponent(lang));
                meta.lore(LangOptions.inventory_moderationrequest_remove_lore.getComponents(lang));
            }), clickEvent -> {
            Player player = clickEvent.getPlayer();
            switch (settings.getModerationStatus()) {
                case NOT_MODERATED, ON_MODERATION -> {
                    settings.setModerationStatus(ModerationStatus.NOT_MODERATED);
                    player.sendMessage(LangOptions.inventory_moderationrequest_removed.getComponent(lang));
                    LevelsListMenu.startEditing(plugin, player, settings);
                }
                case MODERATED -> {
                	player.sendMessage(LangOptions.inventory_moderationrequest_moderated.getComponent(lang));
                    player.closeInventory();
                }
                default -> throw new IllegalStateException("Unexpected value: " + settings.getModerationStatus());
            }
        });
        this.setItem(5, 5, ItemUtils.create(
            Material.REDSTONE_TORCH, meta -> {
            	meta.displayName(LangOptions.inventory_moderationrequest_cancel_name.getComponent(lang));
                meta.lore(LangOptions.inventory_moderationrequest_cancel_lore.getComponents(lang));
            }), clickEvent -> {
            clickEvent.getPlayer().closeInventory();
        });
    }
}
