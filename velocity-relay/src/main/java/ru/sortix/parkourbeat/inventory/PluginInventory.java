package ru.sortix.parkourbeat.inventory;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;

public abstract class PluginInventory<P extends JavaPlugin> implements InventoryHolder {
    /**
     * Сколько миллисекунд после открытия меню клики игнорируются.
     * <p>
     * Меню часто открываются друг поверх друга, и кнопки нередко попадают в тот же
     * слот, по которому только что кликнули. Из-за этого второй клик дабл-клика
     * прилетал уже НОВОМУ меню: нажал на уровень в списке (слот 22) — открылось меню
     * уровня, где в слоте 22 стоит «Играть», и игрока сразу уносило на уровень.
     * Небольшая пауза это полностью убирает и не мешает нормальным кликам.
     */
    private static final long OPEN_CLICK_GRACE_MILLIS = 250L;

    protected final @NonNull P plugin;
    protected final String lang;
    private final Inventory handle;
    private final Map<Integer, Consumer<ClickEvent>> clickActions = new HashMap<>();
    private long openedAtMillis = 0L;

    protected PluginInventory(@NonNull P plugin, int rows, String lang, @NonNull Component title) {
        this.plugin = plugin;
        this.lang = lang;
        this.handle = plugin.getServer().createInventory(this, rows * 9, ru.sortix.parkourbeat.ui.MenuTranslator.translate(title, lang));
    }

    protected PluginInventory(@NonNull P plugin, @NonNull InventoryType type, String lang, @NonNull Component title) {
        this.plugin = plugin;
        this.lang = lang;
        this.handle = plugin.getServer().createInventory(this, type, ru.sortix.parkourbeat.ui.MenuTranslator.translate(title, lang));
    }

    /**
     * Обводит меню чёрными стеклянными панелями по периметру.
     * <p>
     * Только пустые слоты: рамка не должна затирать уже расставленное содержимое,
     * поэтому её можно рисовать в любой момент отрисовки.
     */
    protected void fillBorder() {
        if (ru.sortix.parkourbeat.ui.MenuTheme.ENABLED) return; // рамку рисует фон окна (РП)
        ItemStack glass = ru.sortix.parkourbeat.item.ItemUtils.create(
            org.bukkit.Material.BLACK_STAINED_GLASS_PANE,
            meta -> meta.displayName(net.kyori.adventure.text.Component.empty()));

        int size = this.handle.getSize();
        int lastRowStart = size - 9;
        for (int slot = 0; slot < size; slot++) {
            boolean border = slot < 9 || slot >= lastRowStart || slot % 9 == 0 || slot % 9 == 8;
            if (!border) continue;
            if (this.handle.getItem(slot) != null) continue;
            this.setItem(slot, glass, null);
        }
    }

    // ================================================================ ОФОРМЛЕНИЕ
    /**
     * Перестановка кнопок по таблице: меню объявляет в static-блоке
     * {@code layout(МоёМеню.class, старыйРяд, стараяКолонка, новыйСлот, иконка)} - и все
     * старые вызовы setItem(ряд, колонка, ...) ставят предмет на новое место. Иконка:
     * null - оставить предмет; "left"/"right" - наши стрелки (вместо голов-стрелок).
     */
    private static final java.util.Map<Class<?>, java.util.Map<Integer, Object[]>> LAYOUTS = new java.util.HashMap<>();

    protected static void layout(@NonNull Class<?> menu, int oldRow, int oldColumn, int newSlot, @Nullable String icon) {
        LAYOUTS.computeIfAbsent(menu, k -> new java.util.HashMap<>()).put(oldRow * 10 + oldColumn, new Object[]{newSlot, icon});
    }

    protected void setItem(int row, int column, @Nullable ItemStack stack, @Nullable Consumer<ClickEvent> action) {
        java.util.Map<Integer, Object[]> map = LAYOUTS.get(this.getClass());
        Object[] target = map == null ? null : map.get(row * 10 + column);
        if (target != null) {
            String icon = (String) target[1];
            if (stack != null && icon != null) {
                ItemStack styled = (icon.equals("left") ? ru.sortix.parkourbeat.ui.Icons.Button.ARROW_LEFT
                    : ru.sortix.parkourbeat.ui.Icons.Button.ARROW_RIGHT).item(ru.sortix.parkourbeat.ui.Icons.ButtonState.NORMAL);
                org.bukkit.inventory.meta.ItemMeta from = stack.getItemMeta();
                org.bukkit.inventory.meta.ItemMeta to = styled.getItemMeta();
                if (from != null && to != null) {
                    if (from.hasDisplayName()) to.displayName(from.displayName());
                    if (from.hasLore()) to.lore(from.lore());
                    styled.setItemMeta(to);
                }
                stack = styled;
            }
            this.setItem((Integer) target[0], stack, action);
            return;
        }
        int slot = ((row - 1) * 9) + (column - 1);
        this.setItem(slot, stack, action);
    }

    protected void setItem(int slotIndex, @Nullable ItemStack stack, @Nullable Consumer<ClickEvent> action) {
        // Общая тема (ui/MenuTheme): заглушки убираются, «назад»/«закрыть»/переключатели - наши кнопки.
        ItemStack themed = ru.sortix.parkourbeat.ui.MenuTheme.apply(this.lang, stack);
        if (themed != null || action == null) stack = themed;
        // Английский игрок - русские фразы из кода меню переводятся по словарю (ui/MenuTranslator).
        stack = ru.sortix.parkourbeat.ui.MenuTranslator.translate(stack, this.lang);
        this.handle.setItem(slotIndex, ItemUtils.fixItalic(stack));
        if (stack == null) {
            if (action == null) this.clickActions.remove(slotIndex);
            else throw new IllegalArgumentException("Action must be null with null item");
        } else {
            if (action == null) this.clickActions.remove(slotIndex);
            else this.clickActions.put(slotIndex, action);
        }
    }

    protected void clearInventory() {
        this.handle.clear();
        this.clickActions.clear();
    }

    public void open(@NonNull Player player) {
        this.openedAtMillis = System.currentTimeMillis();
        player.openInventory(this.handle);
    }

    protected final void handle(@NonNull InventoryClickEvent event) {
        event.setCancelled(true);
        // Клик, прилетевший сразу после открытия, почти наверняка «сквозной»
        // от предыдущего меню — игнорируем его.
        if (System.currentTimeMillis() - this.openedAtMillis < OPEN_CLICK_GRACE_MILLIS) return;
        Consumer<ClickEvent> action = this.clickActions.get(event.getRawSlot());
        if (action == null) return;
        ClickEvent clickEvent = ClickEvent.newInstance(event);
        if (clickEvent == null) return;
        try {
            action.accept(clickEvent);
        } catch (Exception e) {
            this.plugin
                .getLogger()
                .log(
                    Level.SEVERE,
                    "Unable to handle action of player "
                        + event.getWhoClicked().getName() + " in inventory "
                        + this.getClass().getName() + " with raw slot index " + event.getRawSlot(),
                    e);
        }
    }

    protected final void handle(@NonNull InventoryDragEvent event) {
        event.setCancelled(true);
    }

    protected final void handle(@NonNull InventoryCloseEvent event) {
        this.onClose((Player) event.getPlayer());
    }

    protected void onClose(@NonNull Player player) {
    }

    @Override
    public final @NonNull Inventory getInventory() {
        return this.handle;
    }
}
