package ru.sortix.parkourbeat.item;

import lombok.NonNull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.InventoryUtils;
import ru.sortix.parkourbeat.item.editor.type.EditTrackPointsItem;
import ru.sortix.parkourbeat.item.editor.type.EditorMenuItem;
import ru.sortix.parkourbeat.item.editor.type.TestGameItem;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ItemsManager implements PluginManager, Listener {
    private final Logger logger;
    private final Map<String, Map<ItemStack, UsableItem>> allItems = new HashMap<>();
    private final Map<String, Map<Class<? extends UsableItem>, UsableItem>> itemsByClass = new HashMap<>();

    public ItemsManager(@NonNull ParkourBeat plugin) {
        this.logger = plugin.getLogger();
        for(String locale : LangOptions.locales) {
        	this.registerItems(locale, new TestGameItem(plugin, locale, 0), new EditorMenuItem(plugin, locale, 1), new EditTrackPointsItem(plugin, locale, 2));
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void registerItems(String locale, @NonNull UsableItem... items) {
    	Map<ItemStack, UsableItem> allItems = new HashMap<>();
    	Map<Class<? extends UsableItem>, UsableItem> itemsByClass = new HashMap<>();
    	for (UsableItem usableItem : items) {
            ItemStack itemStack = usableItem.getItemStack();
            if (!itemStack.getType().isItem()) {
                this.logger.severe(usableItem.getClass().getName() + " is not an item");
                continue;
            }
            allItems.put(itemStack, usableItem);
            itemsByClass.put(usableItem.getClass(), usableItem);
        }
    	this.allItems.put(locale, allItems);
    	this.itemsByClass.put(locale, itemsByClass);
    }

    public void putItem(@NonNull Player player, @NonNull Class<? extends UsableItem> itemClass) {
    	String lang = LangOptions.replaceLocale(player.getLocale().toLowerCase());
    	Map<Class<? extends UsableItem>, UsableItem> itemsByClass = this.itemsByClass.get(lang);
    	if(itemsByClass==null) {
    		itemsByClass = this.itemsByClass.get("");
        }
        if(itemsByClass==null) return;
    	UsableItem editorItem = itemsByClass.get(itemClass);
        if (editorItem == null) {
            throw new IllegalArgumentException("Unable to find item by class " + itemClass.getName());
        }
        player.getInventory().setItem(editorItem.slot, editorItem.itemStack.clone());
    }

    public void putAllItems(@NonNull Player player, @NonNull Class<? extends UsableItem> itemsClass) {
        PlayerInventory inventory = player.getInventory();
        String lang = LangOptions.replaceLocale(player.getLocale().toLowerCase());
        Map<ItemStack, UsableItem> allItems = this.allItems.get(lang);
        if(allItems==null) {
        	allItems = this.allItems.get("");
        }
        if(allItems==null) return;
        for (UsableItem item : allItems.values()) {
            if (!itemsClass.isInstance(item)) continue;
            inventory.setItem(item.slot, item.itemStack.clone());
        }
    }

    @EventHandler
    private void on(@NonNull PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) return;
        if (InventoryUtils.isInventoryOpen(event.getPlayer())) return;
        if (event.getItem() == null) return;
        //Items not work if localisation changed during edit
        /*String lang = LangOptions.replaceLocale(event.getPlayer().getLocale().toLowerCase());
        Map<ItemStack, UsableItem> allItems = this.allItems.get(lang);
        if(allItems==null) {
        	allItems = this.allItems.get("");
        }
        if(allItems==null) return;
        UsableItem usableItem = allItems.get(event.getItem());*/
        UsableItem usableItem = null;
        for(Map<ItemStack, UsableItem> allItems : this.allItems.values()) {
            usableItem = allItems.get(event.getItem());
            if (usableItem != null) break;
        }
        if (usableItem == null) return;
        event.setCancelled(true);
        if (event.getPlayer().getCooldown(event.getItem().getType()) > 0) return;
        if (usableItem.getCooldownTicks() > 0) {
            event.getPlayer().setCooldown(event.getItem().getType(), usableItem.getCooldownTicks());
        }

        if (event.getHand() != EquipmentSlot.HAND) return;
        usableItem.onUse(event);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }
}
