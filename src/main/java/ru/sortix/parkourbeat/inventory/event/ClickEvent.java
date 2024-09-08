package ru.sortix.parkourbeat.inventory.event;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ClickEvent {
    private final @NonNull Player player;
    private final @Nullable ItemStack clickedItem;
    private final boolean left;
    private final boolean shift;

    @Nullable
    public static ClickEvent newInstance(@NonNull InventoryClickEvent event) {
        boolean isLeft;
        if (event.getClick().isLeftClick()) {
            isLeft = true;
        } else if (event.getClick().isRightClick()) {
            isLeft = false;
        } else {
            return null;
        }

        Player player = (Player) event.getWhoClicked();

        ItemStack clickedItem = event.getCurrentItem();

        boolean isShift = event.getClick().isShiftClick();

        return new ClickEvent(player, clickedItem, isLeft, isShift);
    }

    public boolean hasClickedItem() {
        return this.clickedItem != null;
    }

    @NonNull
    public ItemStack getClickedItem() {
        if (this.clickedItem == null) throw new IllegalStateException("No clicked item present");
        return this.clickedItem;
    }
}
