package ru.sortix.parkourbeat.inventory;

import lombok.NonNull;
import net.kyori.adventure.text.Component;

import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

public class RegularItems {

	private static final HashMap<String, ItemStack> closeInventory, previousPage, nextPage;
	static {
		HashMap<String, ItemStack> acloseInventory = new HashMap<>(), apreviousPage = new HashMap<>(), anextPage = new HashMap<>();
		for(String locale : LangOptions.locales) {
			Component closetext = LangOptions.inventory_regularitems_close.getComponent(locale), previoustext = LangOptions.inventory_regularitems_previous.getComponent(locale), nexttext = LangOptions.inventory_regularitems_next.getComponent(locale);
			ItemStack closeitem = ItemUtils.modifyMeta(new ItemStack(Material.BARRIER), meta -> meta.displayName(closetext)), previousitem = ItemUtils.modifyMeta(Heads.getHeadByHash("5f133e91919db0acefdc272d67fd87b4be88dc44a958958824474e21e06d53e6"), meta -> meta.displayName(previoustext)), nextitem = ItemUtils.modifyMeta(Heads.getHeadByHash("e3fc52264d8ad9e654f415bef01a23947edbccccf649373289bea4d149541f70"), meta -> meta.displayName(nexttext));
			acloseInventory.put(locale, closeitem);
			apreviousPage.put(locale, previousitem);
			anextPage.put(locale, nextitem);
		}
		closeInventory = acloseInventory;
		previousPage = apreviousPage;
		nextPage = anextPage;
	}

    public static @NonNull ItemStack closeInventory(String lang) {
    	lang = LangOptions.replaceLocale(lang);
    	ItemStack item = closeInventory.get(lang);
    	if(item == null) {
    		item = closeInventory.get("");
    	}
    	return item.clone();
    }

    public static @NonNull ItemStack previousPage(String lang) {
    	lang = LangOptions.replaceLocale(lang);
    	ItemStack item = previousPage.get(lang);
    	if(item == null) {
    		item = previousPage.get("");
    	}
    	return item.clone();
    }

    public static @NonNull ItemStack nextPage(String lang) {
    	lang = LangOptions.replaceLocale(lang);
    	ItemStack item = nextPage.get(lang);
    	if(item == null) {
    		item = nextPage.get("");
    	}
    	return item.clone();
    }
}
