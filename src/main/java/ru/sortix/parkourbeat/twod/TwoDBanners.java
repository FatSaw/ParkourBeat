package ru.sortix.parkourbeat.twod;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Banner;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * ПЕРЕХОДЫ РЕЖИМОВ НА 2D-УРОВНЕ.
 * <p>
 * Строитель ставит обычный баннер любого цвета, а тип перехода зашит в его узор:
 * <ul>
 *     <li>треугольники вверх - переход в полёт (кубик садится в лодку);</li>
 *     <li>треугольники вниз - возврат в паркур (кубик снова бежит по земле).</li>
 * </ul>
 * Узор выбран специально: он виден прямо в мире, переживает копирование через
 * WorldEdit и не требует отдельного файла с координатами - а значит, не может
 * разъехаться с реальными блоками.
 * <p>
 * Если на версии сервера узоры вдруг не резолвятся (в 1.21 {@code PatternType}
 * перестал быть енумом), тип определяется по количеству узоров: 1 - полёт, 2 - паркур.
 */
public final class TwoDBanners {
    private TwoDBanners() {
    }

    public enum Type {
        FLY("&b&lПереход в полёт", "&7Кубик садится в лодку и летит"),
        PARKOUR("&a&lПереход в паркур", "&7Кубик снова бежит по земле");

        public final @NonNull String title;
        public final @NonNull String description;

        Type(@NonNull String title, @NonNull String description) {
            this.title = title;
            this.description = description;
        }
    }

    private static final String FLY_PATTERN_NAME = "TRIANGLES_TOP";
    private static final String PARKOUR_PATTERN_NAME = "TRIANGLES_BOTTOM";
    private static final String EXTRA_PATTERN_NAME = "STRIPE_MIDDLE";

    private static Boolean patternsResolved = null;

    @Nullable
    private static PatternType pattern(@NonNull String name) {
        // 1.13 - 1.20.x: PatternType это обычный енум.
        try {
            Method valueOf = PatternType.class.getMethod("valueOf", String.class);
            Object value = valueOf.invoke(null, name);
            if (value instanceof PatternType) return (PatternType) value;
        } catch (Throwable ignored) {
        }
        // 1.21+: PatternType это интерфейс со статическими константами.
        try {
            Field field = PatternType.class.getField(name);
            Object value = field.get(null);
            if (value instanceof PatternType) return (PatternType) value;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static final PatternType FLY_PATTERN = pattern(FLY_PATTERN_NAME);
    private static final PatternType PARKOUR_PATTERN = pattern(PARKOUR_PATTERN_NAME);
    private static final PatternType EXTRA_PATTERN = pattern(EXTRA_PATTERN_NAME);

    public static boolean arePatternsAvailable() {
        if (patternsResolved == null) {
            patternsResolved = FLY_PATTERN != null && PARKOUR_PATTERN != null && EXTRA_PATTERN != null;
        }
        return patternsResolved;
    }

    @NonNull
    private static List<Pattern> buildPatterns(@NonNull Type type) {
        List<Pattern> patterns = new ArrayList<>();
        if (!arePatternsAvailable()) return patterns;

        if (type == Type.FLY) {
            patterns.add(new Pattern(DyeColor.WHITE, FLY_PATTERN));
        } else {
            patterns.add(new Pattern(DyeColor.WHITE, PARKOUR_PATTERN));
            patterns.add(new Pattern(DyeColor.BLACK, EXTRA_PATTERN));
        }
        return patterns;
    }

    @NonNull
    public static Material bannerMaterial(@NonNull DyeColor color) {
        Material material = Material.matchMaterial(color.name() + "_BANNER");
        return material == null ? Material.WHITE_BANNER : material;
    }

    /**
     * Готовый предмет-баннер для строителя.
     */
    @NonNull
    public static ItemStack createItem(@NonNull Type type, @NonNull DyeColor color) {
        ItemStack stack = new ItemStack(bannerMaterial(color));
        org.bukkit.inventory.meta.ItemMeta rawMeta = stack.getItemMeta();
        if (rawMeta instanceof BannerMeta meta) {
            meta.setPatterns(buildPatterns(type));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(PbText.item(type.description));
            lore.add(PbText.item("&7Поставьте баннер на пути кубика."));
            lore.add(Component.empty());
            lore.add(PbText.item("&8Тип зашит в узор - цвет любой."));
            meta.lore(lore);
            meta.displayName(PbText.item(type.title));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Определить тип перехода по установленному в мире блоку.
     *
     * @return null, если это не наш баннер
     */
    @Nullable
    public static Type detect(@Nullable Block block) {
        if (block == null) return null;
        String typeName = block.getType().name();
        if (!typeName.endsWith("_BANNER")) return null;

        try {
            org.bukkit.block.BlockState state = block.getState();
            if (!(state instanceof Banner banner)) return null;

            List<Pattern> patterns = banner.getPatterns();
            if (patterns.isEmpty()) return null;

            if (arePatternsAvailable()) {
                for (Pattern pattern : patterns) {
                    if (pattern.getPattern().equals(FLY_PATTERN)) return Type.FLY;
                    if (pattern.getPattern().equals(PARKOUR_PATTERN)) return Type.PARKOUR;
                }
                return null;
            }

            // Запасной вариант: узоры на этой версии не читаются - считаем их количество.
            if (patterns.size() == 1) return Type.FLY;
            if (patterns.size() == 2) return Type.PARKOUR;
            return null;
        } catch (Throwable t) {
            return null;
        }
    }
}
