package ru.sortix.parkourbeat.boards;

import lombok.NonNull;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import ru.sortix.parkourbeat.ParkourBeat;

public class BoardsListener implements Listener {

    private final @NonNull BoardsManager manager;

    public BoardsListener(@NonNull ParkourBeat plugin) {
        this.manager = plugin.get(BoardsManager.class);
    }

    // ЛКМ (Взмах руки)
    @EventHandler(priority = EventPriority.LOW)
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            this.manager.handleInteraction(event.getPlayer(), false);
        }
    }

    // ПКМ (Клик правой кнопкой в воздухе или по блоку)
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            this.manager.handleInteraction(event.getPlayer(), true);
        }
    }

    // Эти два ивента нужны, чтобы защитить сами ItemFrame (карты) от ломания или кручения.
    // Логика кликов уже обработана выше, тут только глушим ванильное взаимодействие.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onRightClick(@NonNull PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame)) return;
        if (this.manager.byFrame(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(@NonNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof ItemFrame)) return;
        if (this.manager.byFrame(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(@NonNull HangingBreakEvent event) {
        if (!(event.getEntity() instanceof ItemFrame)) return;
        if (this.manager.byFrame(event.getEntity()) == null) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(@NonNull PlayerQuitEvent event) {
        this.manager.forget(event.getPlayer());
    }
}
