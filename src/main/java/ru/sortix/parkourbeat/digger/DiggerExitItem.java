package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * ВЫХОД ИЗ ТЕСТОВОГО ЗАБЕГА.
 * <p>
 * Отдельный предмет нужен потому, что забег «Копателя» перебирает инвентарь под себя,
 * и алмаз выхода из теста легко потерять при любой ошибке. Изумруд в последнем слоте -
 * страховка: пока он в руках, из теста всегда есть выход, и покидать уровень ради
 * этого не придётся.
 */
public final class DiggerExitItem {

    public static final NamespacedKey KEY = NamespacedKey.fromString("parkourbeat:digger_exit");

    private DiggerExitItem() {
    }

    @NonNull
    public static ItemStack build() {
        ItemStack stack = new ItemStack(Material.EMERALD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(PbText.item("&aВыйти из теста"));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7ПКМ - вернуться в редактор."));
            meta.lore(lore);
            if (KEY != null) {
                meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static boolean is(@Nullable ItemStack stack) {
        if (stack == null || stack.getType() != Material.EMERALD || KEY == null) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    // ==================== ВЫХОД ИЗ ЗАБЕГА ====================

    public static final NamespacedKey QUIT_KEY = NamespacedKey.fromString("parkourbeat:digger_quit");

    /**
     * ВЫЙТИ ИЗ ЗАБЕГА, НЕ ДОИГРЫВАЯ.
     * <p>
     * Раньше выйти можно было двумя способами: дослушать трек до конца или набрать
     * шесть промахов подряд. Оба скверные. Человек, который понял, что карта не его,
     * сидел в тоннеле ещё две минуты, а тот, кто торопился, СПЕЦИАЛЬНО мазал по нотам,
     * чтобы забег закончился, - то есть выходом служила механика провала.
     * <p>
     * Пустота структуры выбрана нарочно: у неё нет ни модели в мире, ни веса в кадре,
     * а в хотбаре она выглядит как отдельная служебная клетка, а не как ещё одна кирка.
     * Стоит в девятом слоте, дальше всех от рабочего первого, - промахнуться по ней
     * в горячке заезда нечем.
     */
    @NonNull
    public static ItemStack buildQuit() {
        ItemStack stack = new ItemStack(Material.STRUCTURE_VOID);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(PbText.item("&cВыйти из игры"));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7ПКМ или ЛКМ - завершить забег"));
            lore.add(PbText.item("&7и открыть экран выбора."));
            lore.add(PbText.item("&8Результат в статистику не пойдёт."));
            meta.lore(lore);
            if (QUIT_KEY != null) {
                meta.getPersistentDataContainer().set(QUIT_KEY, PersistentDataType.BYTE, (byte) 1);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static boolean isQuit(@Nullable ItemStack stack) {
        if (stack == null || stack.getType() != Material.STRUCTURE_VOID || QUIT_KEY == null) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(QUIT_KEY, PersistentDataType.BYTE);
    }
}
