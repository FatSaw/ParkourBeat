package ru.sortix.parkourbeat.inventory.type.moderation;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.type.LevelsListMenu;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.ModerationStatus;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import java.util.logging.Level;

public class ModeratorConfirmationMenu extends ParkourBeatInventory {
    public ModeratorConfirmationMenu(@NonNull ParkourBeat plugin, String lang, @NonNull GameSettings settings, @NonNull Player moderator) {
        super(plugin, 6, lang, LangOptions.inventory_moderationconfirm_title.getComponent(lang));

        this.setItem(2, 4, ItemUtils.create(
            Material.RED_WOOL, meta -> {
                meta.displayName(LangOptions.inventory_moderationconfirm_reject_name.getComponent(lang));
                meta.lore(LangOptions.inventory_moderationconfirm_reject_lore.getComponents(lang));
            }), clickEvent -> {
            this.doConfirmation(settings, clickEvent.getPlayer(), false);
        });
        this.setItem(2, 6, ItemUtils.create(
            Material.GREEN_WOOL, meta -> {
            	meta.displayName(LangOptions.inventory_moderationconfirm_approve_name.getComponent(lang));
                meta.lore(LangOptions.inventory_moderationconfirm_approve_lore.getComponents(lang));
            }), clickEvent -> {
            this.doConfirmation(settings, clickEvent.getPlayer(), true);
        });
        if (moderator.hasPermission(PermissionConstants.EDIT_OTHERS_LEVELS_ON_MODERATION)) {
            this.setItem(3, 5, ItemUtils.create(
                Material.COMPARATOR, meta -> {
                	meta.displayName(LangOptions.inventory_moderationconfirm_edit_name.getComponent(lang));
                }), clickEvent -> {
                LevelsListMenu.startEditing(plugin, clickEvent.getPlayer(), settings, false);
            });
        }
        this.setItem(5, 5, ItemUtils.create(
            Material.REDSTONE_TORCH, meta -> {
            	meta.displayName(LangOptions.inventory_moderationconfirm_cancel_name.getComponent(lang));
                meta.lore(LangOptions.inventory_moderationconfirm_cancel_lore.getComponents(lang));
            }), clickEvent -> {
            clickEvent.getPlayer().closeInventory();
        });
    }

    private void doConfirmation(@NonNull GameSettings settings, @NonNull Player moderator, boolean approved) {
        if (!moderator.hasPermission(PermissionConstants.MODERATE_LEVELS)) {
            moderator.sendMessage(LangOptions.inventory_moderationconfirm_nopermission.getComponent(lang));
        } else if (settings.getModerationStatus() != ModerationStatus.ON_MODERATION) {
            moderator.sendMessage(LangOptions.inventory_moderationconfirm_notonmoderation.getComponent(lang));
        } else {
            ModerationStatus previousStatus = settings.getModerationStatus();
            try {
                settings.setModerationStatus(approved ? ModerationStatus.MODERATED : ModerationStatus.NOT_MODERATED);
                this.plugin.get(LevelsManager.class).saveGameSettings(settings);
                moderator.sendMessage((approved ? LangOptions.inventory_moderationconfirm_approved : LangOptions.inventory_moderationconfirm_rejected).getComponent(lang));
            } catch (Throwable t) {
                if (settings.getModerationStatus() != previousStatus) {
                    settings.setModerationStatus(previousStatus);
                }
                moderator.sendMessage(LangOptions.inventory_moderationconfirm_saveerror.getComponent(lang));
                this.plugin.getLogger().log(Level.SEVERE,
                    "Unable to moderate level " + settings.getUniqueId(), t);
            }
        }
        moderator.closeInventory();
    }
}
