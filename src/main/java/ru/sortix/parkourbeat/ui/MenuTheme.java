package ru.sortix.parkourbeat.ui;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ОБЩАЯ ТЕМА ДЛЯ ВСЕХ МЕНЮ - применяется в {@code ParkourBeatInventory.setItem}.
 * <p>
 * Кнопку узнаём по смыслу, а не по материалу: красный факел или барьер в каком-то меню
 * может быть содержимым (например, блок барьера), а не кнопкой.
 * <ul>
 *   <li>стеклянные заглушки без названия - убираются, под ними видно фон;</li>
 *   <li>предмет с названием «Назад» (inventory.common.back) - наша стрелка;</li>
 *   <li>«Закрыть» (inventory.common.close) - красный крестик;</li>
 *   <li>зелёный/серый краситель с подсказкой «переключить» - переключатель вкл/выкл.</li>
 * </ul>
 * Название, описание и количество предмета сохраняются. Выключить: {@link #ENABLED}.
 */
public final class MenuTheme {
    private MenuTheme() {
    }

    public static volatile boolean ENABLED = true;

    private static final Map<String, Set<String>> BACK = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> CLOSE = new ConcurrentHashMap<>();
    private static final Map<String, String> TOGGLE = new ConcurrentHashMap<>();

    @Nullable
    public static ItemStack apply(@Nullable String lang, @Nullable ItemStack stack) {
        if (!ENABLED || stack == null) return stack;
        try {
            Material type = stack.getType();
            ItemMeta meta = stack.getItemMeta();
            String name = meta != null && meta.hasDisplayName() ? plain(meta.displayName()) : "";

            if ((type == Material.BLACK_STAINED_GLASS_PANE || type == Material.GRAY_STAINED_GLASS_PANE)
                && name.isEmpty()) {
                return null;
            }
            if (meta == null || name.isEmpty()) return stack;

            String key = lang == null ? "" : lang;
            if (texts(BACK, key, lang, true).contains(name)) return copy(stack, Icons.Button.ARROW_LEFT.item(Icons.ButtonState.NORMAL));
            if (texts(CLOSE, key, lang, false).contains(name)) return copy(stack, Icons.cancel());

            if ((type == Material.LIME_DYE || type == Material.GRAY_DYE) && meta.hasLore()) {
                String hint = TOGGLE.computeIfAbsent(key, k -> clean(Lang.raw(lang, "inventory.common.toggle")));
                List<Component> lore = meta.lore();
                if (!hint.isEmpty() && lore != null) {
                    for (Component line : lore) {
                        if (plain(line).contains(hint)) {
                            return copy(stack, (type == Material.LIME_DYE ? Icons.Button.SWITCH_ON : Icons.Button.SWITCH_OFF)
                                .item(Icons.ButtonState.NORMAL));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return stack;
    }

    @NonNull
    private static Set<String> texts(@NonNull Map<String, Set<String>> cache, @NonNull String key,
                                     @Nullable String lang, boolean back) {
        return cache.computeIfAbsent(key, k -> {
            Set<String> out = new HashSet<>();
            if (back) {
                out.add(clean(Lang.raw(lang, "inventory.common.back")));
                try { out.add(clean(LangOptions.inventory_regularitems_previous.get(lang))); } catch (Throwable ignored) { }
            } else {
                out.add(clean(Lang.raw(lang, "inventory.common.close")));
                try { out.add(clean(LangOptions.inventory_regularitems_close.get(lang))); } catch (Throwable ignored) { }
            }
            out.remove("");
            out.remove("inventory.common.back");
            out.remove("inventory.common.close");
            return out;
        });
    }

    @NonNull
    private static ItemStack copy(@NonNull ItemStack source, @NonNull ItemStack target) {
        ItemMeta from = source.getItemMeta();
        ItemMeta to = target.getItemMeta();
        if (from != null && to != null) {
            if (from.hasDisplayName()) to.displayName(from.displayName());
            if (from.hasLore()) to.lore(from.lore());
            target.setItemMeta(to);
        }
        target.setAmount(Math.max(1, Math.min(64, source.getAmount())));
        return target;
    }

    @NonNull
    private static String plain(@Nullable Component component) {
        if (component == null) return "";
        return clean(PlainComponentSerializer.plain().serialize(component));
    }

    @NonNull
    private static String clean(@Nullable String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)[&\u00A7](#[0-9a-f]{6}|[0-9a-fk-or])", "")
            .replaceAll("<[^>]{1,40}>", "").trim().toLowerCase(Locale.ROOT);
    }
}
