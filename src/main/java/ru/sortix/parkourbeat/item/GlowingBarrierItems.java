package ru.sortix.parkourbeat.item;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import ru.sortix.parkourbeat.levels.settings.GlowColor;
import ru.sortix.parkourbeat.levels.settings.GlowMode;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The glow settings ride along on the barrier item itself, so a player can carry several
 * differently configured stacks at once.
 */
@UtilityClass
public class GlowingBarrierItems {
    private NamespacedKey colorKey(@NonNull Plugin plugin) {
        return new NamespacedKey(plugin, "glow_color");
    }

    private NamespacedKey modeKey(@NonNull Plugin plugin) {
        return new NamespacedKey(plugin, "glow_mode");
    }

    @NonNull
    public ItemStack createGlowing(@NonNull Plugin plugin,
                                   String lang,
                                   @NonNull GlowColor color,
                                   @NonNull GlowMode mode,
                                   int amount
    ) {
        ItemStack stack = new ItemStack(Material.BARRIER, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.displayName(LangOptions.item_editor_glowbarrier_name.getComponent(
            lang, new Placeholders("%color%", color.getDisplayNameString(lang))));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>(
            LangOptions.item_editor_glowbarrier_lore.getComponents(lang,
                new Placeholders("%color%", color.getDisplayNameString(lang)),
                new Placeholders("%mode%", mode.getDisplayNameString(lang))));
        meta.lore(lore);

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(colorKey(plugin), PersistentDataType.STRING, color.name());
        container.set(modeKey(plugin), PersistentDataType.STRING, mode.name());

        stack.setItemMeta(meta);
        return stack;
    }

    @NonNull
    public ItemStack createPlain(String lang, int amount) {
        ItemStack stack = new ItemStack(Material.BARRIER, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.displayName(LangOptions.item_editor_plainbarrier_name.getComponent(lang));
        meta.lore(LangOptions.item_editor_plainbarrier_lore.getComponents(lang));
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isGlowing(@NonNull Plugin plugin, @Nullable ItemStack stack) {
        if (stack == null || stack.getType() != Material.BARRIER) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(colorKey(plugin), PersistentDataType.STRING);
    }

    @NonNull
    public GlowColor readColor(@NonNull Plugin plugin, @NonNull ItemStack stack) {
        return GlowColor.byName(read(plugin, stack, colorKey(plugin)), GlowColor.DEFAULT);
    }

    @NonNull
    public GlowMode readMode(@NonNull Plugin plugin, @NonNull ItemStack stack) {
        return GlowMode.byName(read(plugin, stack, modeKey(plugin)), GlowMode.DEFAULT);
    }

    @Nullable
    private String read(@NonNull Plugin plugin, @NonNull ItemStack stack, @NonNull NamespacedKey key) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public void give(@NonNull Player player, @NonNull ItemStack stack) {
        Inventory inventory = player.getInventory();
        for (ItemStack leftover : inventory.addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
    }
}
