package ru.sortix.parkourbeat.inventory.type.moderation;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

import java.util.Arrays;
import java.util.logging.Level;

public class ModeratorConfirmationMenu extends ParkourBeatInventory {
    public ModeratorConfirmationMenu(@NonNull ParkourBeat plugin, @NonNull GameSettings settings, @NonNull Player moderator) {
        super(plugin, 6, Component.text("Модерация уровня"));

        this.setItem(2, 4, ItemUtils.create(
            Material.RED_WOOL, meta -> {
                meta.displayName(Component.text("Отклонить уровень", NamedTextColor.GOLD));
                meta.lore(Arrays.asList(
                    Component.text("Уровень останется в разделе", NamedTextColor.YELLOW),
                    Component.text("\"Пользовательские\"", NamedTextColor.YELLOW)
                ));
            }), clickEvent -> {
            this.doConfirmation(settings, clickEvent.getPlayer(), false);
        });
        this.setItem(2, 6, ItemUtils.create(
            Material.GREEN_WOOL, meta -> {
                meta.displayName(Component.text("Одобрить уровень", NamedTextColor.GOLD));
                meta.lore(Arrays.asList(
                    Component.text("Уровень станет публичным", NamedTextColor.YELLOW),
                    Component.text("и рейтинговым", NamedTextColor.YELLOW)
                ));
            }), clickEvent -> {
            this.doConfirmation(settings, clickEvent.getPlayer(), true);
        });
        if (moderator.hasPermission(PermissionConstants.EDIT_OTHERS_LEVELS_ON_MODERATION)) {
            this.setItem(3, 5, ItemUtils.create(
                Material.COMPARATOR, meta -> {
                    meta.displayName(Component.text("Редактировать уровень", NamedTextColor.GOLD));
                }), clickEvent -> {
                LevelsListMenu.startEditing(plugin, clickEvent.getPlayer(), settings, false);
            });
        }
        this.setItem(5, 5, ItemUtils.create(
            Material.REDSTONE_TORCH, meta -> {
                meta.displayName(Component.text("Отмена", NamedTextColor.GOLD));
                meta.lore(Arrays.asList(
                    Component.text("Нажмите, чтобы", NamedTextColor.YELLOW),
                    Component.text("закрыть меню", NamedTextColor.YELLOW)
                ));
            }), clickEvent -> {
            clickEvent.getPlayer().closeInventory();
        });
    }

    private void doConfirmation(@NonNull GameSettings settings, @NonNull Player moderator, boolean approved) {
        if (!moderator.hasPermission(PermissionConstants.MODERATE_LEVELS)) {
            moderator.sendMessage("Недостаточно прав");
        } else if (settings.getModerationStatus() != ModerationStatus.ON_MODERATION) {
            moderator.sendMessage("Уровень не на модерации");
        } else {
            ModerationStatus previousStatus = settings.getModerationStatus();
            try {
                settings.setModerationStatus(approved ? ModerationStatus.MODERATED : ModerationStatus.NOT_MODERATED);
                this.plugin.get(LevelsManager.class).saveLevelSettings(settings.getOwnerId());
                moderator.sendMessage("Уровень " + (approved ? "подтверждён" : "отклонён"));
            } catch (Throwable t) {
                if (settings.getModerationStatus() != previousStatus) {
                    settings.setModerationStatus(previousStatus);
                }
                moderator.sendMessage("Техническая ошибка при подтверждении уровня");
                this.plugin.getLogger().log(Level.SEVERE,
                    "Unable to moderate level " + settings.getUniqueId(), t);
            }
        }
        moderator.closeInventory();
    }
}
