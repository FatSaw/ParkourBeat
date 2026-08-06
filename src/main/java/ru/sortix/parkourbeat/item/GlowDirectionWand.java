package ru.sortix.parkourbeat.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import ru.sortix.parkourbeat.levels.settings.GlowExtension;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

public class GlowDirectionWand {

    public static boolean isWand(Plugin plugin, ItemStack stack) {
        if (stack == null || stack.getType() != Material.BONE) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "glow_wand"), PersistentDataType.BYTE);
    }

    public static GlowExtension getExtension(Plugin plugin, ItemStack stack) {
        if (stack == null || stack.getType() != Material.BONE) return GlowExtension.UP;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return GlowExtension.UP;
        String name = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "glow_wand_ext"), PersistentDataType.STRING);
        if (name == null) return GlowExtension.UP;
        try {
            return GlowExtension.valueOf(name);
        } catch (IllegalArgumentException e) {
            return GlowExtension.UP;
        }
    }

    public static ItemStack createWand(Plugin plugin, String lang, GlowExtension ext) {
        ItemStack stack = new ItemStack(Material.BONE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            meta.displayName(LangOptions.item_editor_glowwand_name.getComponent(lang,
                new Placeholders("%arrow%", ext.arrow)));

            meta.lore(LangOptions.item_editor_glowwand_lore.getComponents(lang,
                new Placeholders("%direction%", ext.langOption.get(lang))));

            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "glow_wand"), PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "glow_wand_ext"), PersistentDataType.STRING, ext.name());

            stack.setItemMeta(meta);
        }
        return stack;
    }
}
