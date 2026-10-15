package ru.sortix.parkourbeat.duel;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Две палочки пути дуэльного уровня.
 * <p>
 * Обычная палочка (блейз-род) ведёт единственный путь уровня и на дуэльном уровне не
 * выдаётся: путей там два, и класть их в один предмет означало бы прятать выбор
 * стороны в модификаторы клика. Вместо этого у каждой стороны свой инструмент -
 * железная мотыга для синего пути и незеритовая для красного.
 */
public final class DuelItems {
    private DuelItems() {
    }

    /** Метка палочек в данных предмета: название игрок может переписать, метку - нет. */
    public static final NamespacedKey MARKER_KEY = NamespacedKey.fromString("parkourbeat:duel_item");

    @NonNull
    public static ItemStack createWand(@NonNull DuelSide side) {
        return ItemUtils.fixItalic(ItemUtils.create(side.getWandMaterial(), meta -> {
            meta.displayName(PbText.item(side.getColorCode() + "&l" + side.getDisplayName()));
            if (MARKER_KEY != null) {
                meta.getPersistentDataContainer().set(MARKER_KEY, PersistentDataType.STRING, side.getMarkerValue());
            }

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(PbText.item("&aЛКМ &7- поставить точку пути"));
            lore.add(PbText.item("&cПКМ &7- убрать ближайшую точку"));
            lore.add(PbText.item("&8SHIFT + ЛКМ/ПКМ - высота прыжка"));
            lore.add(Component.empty());
            lore.add(PbText.item("&7Прыжковые кольца этого пути"));
            lore.add(PbText.item("&7засчитываются только "
                + side.getColorCode() + side.getDisplayName().toLowerCase(java.util.Locale.ROOT) + "&7."));
            meta.lore(lore);
        }));
    }

    public static boolean isWand(@Nullable ItemStack stack) {
        return sideOf(stack) != null;
    }

    /**
     * Какой стороне принадлежит палочка в руке. null - предмет не наш.
     */
    @Nullable
    public static DuelSide sideOf(@Nullable ItemStack stack) {
        if (stack == null || MARKER_KEY == null) return null;
        try {
            org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
            if (meta == null) return null;
            String stored = meta.getPersistentDataContainer().get(MARKER_KEY, PersistentDataType.STRING);
            if (stored == null) return null;
            for (DuelSide side : DuelSide.values()) {
                if (side.getMarkerValue().equals(stored) && stack.getType() == side.getWandMaterial()) {
                    return side;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
