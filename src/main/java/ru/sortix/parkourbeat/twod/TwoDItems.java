package ru.sortix.parkourbeat.twod;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Инструменты строителя, которые существуют только на 2D-уровнях.
 * <p>
 * Палочка пути на таком уровне не расставляет точки: точек там нет вообще. Она тянет
 * линию, то есть двигает финиш, поэтому и предмет у неё свой, со своим описанием.
 */
public final class TwoDItems {
    private TwoDItems() {
    }

    /** Тот же слот, что и у обычной палочки пути: строитель ищет её там же. */
    public static final int LINE_WAND_SLOT = 2;

    public static final Material LINE_WAND_MATERIAL = Material.BAMBOO;
    private static final String LINE_WAND_NAME = "&b&lДлина уровня";

    @NonNull
    public static ItemStack createLineWand() {
        return ItemUtils.fixItalic(ItemUtils.create(LINE_WAND_MATERIAL, meta -> {
            meta.displayName(PbText.item(LINE_WAND_NAME));
            if (TwoDCoins.MARKER_KEY != null) {
                meta.getPersistentDataContainer().set(TwoDCoins.MARKER_KEY,
                    org.bukkit.persistence.PersistentDataType.STRING, "wand");
            }

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(PbText.item("&aЛКМ &7- увеличить длину уровня"));
            lore.add(PbText.item("&cПКМ &7- уменьшить длину уровня"));
            lore.add(PbText.item("&8SHIFT + ПКМ по блоку - длина ровно до него"));
            lore.add(Component.empty());
            lore.add(PbText.item("&7Финиш стоит там, где кончается линия."));
            lore.add(PbText.item("&7Тянуть её можно и дальше паркура."));
            meta.lore(lore);
        }));
    }

    public static boolean isLineWand(@Nullable ItemStack stack) {
        return TwoDCoins.hasMarker(stack, LINE_WAND_MATERIAL, "wand");
    }

    public static final Material SPIKE_WAND_MATERIAL = Material.SPECTRAL_ARROW;
    private static final String SPIKE_WAND_NAME = "&8&lШипы";

    /**
     * Палочка шипов.
     * <p>
     * Шип - это блок целиком, а не точка на нём: в оригинале касание шипа смертельно
     * с любой стороны, включая приземление сверху. Поэтому и помечается блок.
     */
    @NonNull
    public static ItemStack createSpikeWand() {
        return ItemUtils.fixItalic(ItemUtils.create(SPIKE_WAND_MATERIAL, meta -> {
            meta.displayName(PbText.item(SPIKE_WAND_NAME));
            if (TwoDCoins.MARKER_KEY != null) {
                meta.getPersistentDataContainer().set(TwoDCoins.MARKER_KEY,
                    org.bukkit.persistence.PersistentDataType.STRING, "spike");
            }

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(PbText.item("&cЛКМ по блоку &7- сделать шипом"));
            lore.add(PbText.item("&aПКМ по блоку &7- убрать шип"));
            lore.add(Component.empty());
            lore.add(PbText.item("&7Касание шипа убивает кубик"));
            lore.add(PbText.item("&7с любой стороны, даже сверху."));
            lore.add(Component.empty());
            lore.add(PbText.item("&8Шипы помечены чёрной дымкой"));
            meta.lore(lore);
        }));
    }

    public static boolean isSpikeWand(@Nullable ItemStack stack) {
        return TwoDCoins.hasMarker(stack, SPIKE_WAND_MATERIAL, "spike");
    }
}
