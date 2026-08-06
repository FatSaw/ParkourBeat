package ru.sortix.parkourbeat.inventory;

import lombok.Getter;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class PaginatedMenu<P extends JavaPlugin, Item> extends PluginInventory<P> {
    private final int[] itemSlots;
    private final int itemsAmountOnPage;
    private final List<Item> allItems;
    private final @Getter int minPageNumber = 1;
    private @Getter int maxPageNumber = -1;
    private int currentPageNumber = -1;

    public PaginatedMenu(@NonNull P plugin, int rows, String lang, @NonNull Component title, int itemsMinSlotIndex, int itemsMaxSlotIndex) {
        super(plugin, rows, lang, title);
        this.allItems = new ArrayList<>();
        int amount = (itemsMaxSlotIndex - itemsMinSlotIndex) + 1;
        this.itemSlots = new int[amount];
        for (int i = 0; i < amount; i++) {
            this.itemSlots[i] = itemsMinSlotIndex + i;
        }
        this.itemsAmountOnPage = amount;
    }

    public PaginatedMenu(@NonNull P plugin, int rows, String lang, @NonNull Component title, int[] itemSlots) {
        super(plugin, rows, lang, title);
        this.itemSlots = itemSlots;
        this.itemsAmountOnPage = itemSlots.length;
        this.allItems = new ArrayList<>();
    }

    public void updateAllItems() {
        this.setItems(this.getAllItems());
    }

    private void setItems(@NonNull Collection<Item> items) {
        this.allItems.clear();
        this.allItems.addAll(items);
        this.maxPageNumber = Math.max(1, ((this.allItems.size() - 1) / this.itemsAmountOnPage) + 1);
        this.currentPageNumber = -1;
        this.displayPage(1);
    }

    private void displayPage(int pageNumber) {
        if (pageNumber < this.minPageNumber || pageNumber > this.maxPageNumber) return;
        this.currentPageNumber = pageNumber;
        this.clearInventory();

        int firstItemIndex = (pageNumber - 1) * this.itemsAmountOnPage;
        int lastItemIndex = Math.min((pageNumber * this.itemsAmountOnPage) - 1, this.allItems.size() - 1);

        int slotIndex = 0;
        for (int itemIndex = firstItemIndex; itemIndex <= lastItemIndex; itemIndex++) {
            Item item = this.allItems.get(itemIndex);
            this.setItem(this.itemSlots[slotIndex++], this.createItemDisplay(item), event -> this.onClick(event, item));
        }

        this.onPageDisplayed();
    }

    protected void setPreviousPageItem(int row, int column) {
        if (this.currentPageNumber > this.minPageNumber) {
            this.setItem(row, column, ItemUtils.modifyMeta(UIHeads.ARROW_LEFT.clone(), m -> m.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&eПредыдущая страница"))),
                event -> this.displayPage(this.currentPageNumber - 1));
        }
    }

    protected void setNextPageItem(int row, int column) {
        if (this.currentPageNumber < this.maxPageNumber) {
            this.setItem(row, column, ItemUtils.modifyMeta(UIHeads.ARROW_RIGHT.clone(), m -> m.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&eСледующая страница"))),
                event -> this.displayPage(this.currentPageNumber + 1));
        }
    }

    @NonNull protected abstract Collection<Item> getAllItems();
    @NonNull protected abstract ItemStack createItemDisplay(@NonNull Item item);
    protected abstract void onPageDisplayed();
    protected abstract void onClick(@NonNull ClickEvent event, @NonNull Item item);
}
