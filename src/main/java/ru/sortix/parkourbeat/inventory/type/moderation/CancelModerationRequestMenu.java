package ru.sortix.parkourbeat.inventory.type.moderation;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.type.LevelsListMenu;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.ModerationStatus;
import ru.sortix.parkourbeat.levels.settings.GameSettings;

import java.util.Arrays;

public class CancelModerationRequestMenu extends ParkourBeatInventory {
    public CancelModerationRequestMenu(@NonNull ParkourBeat plugin, @NonNull GameSettings settings) {
        super(plugin, 5, Component.text("Снятие с модерации"));

        this.setItem(3, 5, ItemUtils.create(
            Material.BARRIER, meta -> {
                meta.displayName(Component.text("Снять уровень с модерации", NamedTextColor.GOLD));
                meta.lore(Arrays.asList(
                    Component.text("Нажмите, чтобы вновь", NamedTextColor.YELLOW),
                    Component.text("разрешить редактирование", NamedTextColor.YELLOW)
                ));
            }), clickEvent -> {
            Player player = clickEvent.getPlayer();
            switch (settings.getModerationStatus()) {
                case NOT_MODERATED, ON_MODERATION -> {
                    settings.setModerationStatus(ModerationStatus.NOT_MODERATED);
                    player.sendMessage("Уровень снят с модерации");
                    LevelsListMenu.startEditing(plugin, player, settings);
                }
                case MODERATED -> {
                    player.sendMessage("Уровень уже прошёл модерацию");
                    player.closeInventory();
                }
                default -> throw new IllegalStateException("Unexpected value: " + settings.getModerationStatus());
            }
        });
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
}
