package ru.sortix.parkourbeat.digger;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * КИРКИ РЕЖИМА.
 * <p>
 * Кирка в «Копателе» - чистая косметика: игрок в творческом режиме, руда ломается
 * мгновенно любым инструментом, и на судейство материал не влияет вообще. Она нужна
 * ровно для вида, поэтому всегда зачарована - ради блеска.
 * <p>
 * Медной кирки в списке нет по той же причине, что и медной руды: в 1.16.5 её не
 * существует.
 */
@Getter
public enum DiggerPickaxe {

    WOODEN(Material.WOODEN_PICKAXE, "&6Деревянная"),
    STONE(Material.STONE_PICKAXE, "&7Каменная"),
    IRON(Material.IRON_PICKAXE, "&fЖелезная"),
    GOLDEN(Material.GOLDEN_PICKAXE, "&eЗолотая"),
    DIAMOND(Material.DIAMOND_PICKAXE, "&bАлмазная"),
    NETHERITE(Material.NETHERITE_PICKAXE, "&5Незеритовая");

    private final @NonNull Material material;
    private final @NonNull String display;

    DiggerPickaxe(@NonNull Material material, @NonNull String display) {
        this.material = material;
        this.display = display;
    }

    /** Кирка по умолчанию - незеритовая, как договорились. */
    @NonNull
    public static DiggerPickaxe getDefault() {
        return NETHERITE;
    }

    public static boolean isSupported(@Nullable Material material) {
        return byMaterial(material) != null;
    }

    @Nullable
    public static DiggerPickaxe byMaterial(@Nullable Material material) {
        if (material == null) return null;
        for (DiggerPickaxe pickaxe : values()) {
            if (pickaxe.material == material) return pickaxe;
        }
        return null;
    }

    @Nullable
    public static DiggerPickaxe byName(@Nullable String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        for (DiggerPickaxe pickaxe : values()) {
            if (pickaxe.name().equals(normalized) || pickaxe.material.name().equals(normalized)) return pickaxe;
        }
        return null;
    }

    @NonNull
    public DiggerPickaxe next() {
        DiggerPickaxe[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    /**
     * Предмет для руки игрока: зачарован, неломаем, без подсказок о чарах в описании -
     * нужен только блик.
     */
    @NonNull
    public ItemStack buildItem() {
        return buildItem(this.material);
    }

    @NonNull
    public static ItemStack buildItem(@NonNull Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.DIG_SPEED, 5, true);
            meta.addEnchant(Enchantment.DURABILITY, 3, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.setUnbreakable(true);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
