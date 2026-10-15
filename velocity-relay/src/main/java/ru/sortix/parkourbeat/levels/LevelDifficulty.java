package ru.sortix.parkourbeat.levels;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Сложности уровня.
 * <p>
 * Названия обёрнуты в {@code <v>}: это опознавательные цвета, такие же как у оценок за
 * прыжок, и палитра оформления их перекрашивать не должна.
 * <p>
 * Порядок важен: по нему сортируются списки уровней, и по нему же берутся картинки
 * из ресурспака (кубы, плашки, переходы). NORMAL вставлен между EASY и HARD; в
 * файлах сложность хранится по имени, поэтому старые уровни не сдвигаются.
 * <p>
 * Иконки - больше не головы, а бумага с CustomModelData: текстуры лежат в РП
 * (assets/parkourbeat/textures/item/difficulty).
 */
@Getter
@RequiredArgsConstructor
public enum LevelDifficulty {
    N_A("<v>§7N/A</v>"),
    EASY("<v>§aEASY</v>"),
    NORMAL("<v>§eNORMAL</v>"),
    HARD("<v>§6HARD</v>"),
    EXPERT("<v>§c§oEXPERT</v>"),
    EXPERT_PLUS("<v>§d§o§lEXPERT+</v>");

    /** CustomModelData иконок: 7100 + порядковый номер (совпадает с РП). */
    public static final int MODEL_DATA_BASE = 7100;

    private final String displayName;

    public int getCustomModelData() {
        return MODEL_DATA_BASE + this.ordinal();
    }

    /** Иконка сложности для меню (куб из ресурспака). */
    @NonNull
    public ItemStack createIcon() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(this.getCustomModelData());
        item.setItemMeta(meta);
        return item;
    }
}
