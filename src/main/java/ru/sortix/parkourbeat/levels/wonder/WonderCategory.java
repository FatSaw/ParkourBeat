package ru.sortix.parkourbeat.levels.wonder;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Material;

/** Разделы библиотеки. Порядок здесь — порядок вкладок в меню. */
@Getter
public enum WonderCategory {
    TEXT(Material.NAME_TAG),
    SKY(Material.NETHER_STAR),
    FIRE(Material.BLAZE_POWDER),
    LOVE(Material.POPPY),
    MAGIC(Material.END_CRYSTAL),
    SHAPE(Material.HONEYCOMB),
    PATH(Material.POWERED_RAIL),
    HIT(Material.TNT),
    SCENE(Material.BEACON);

    private final @NonNull Material icon;

    /** Подпись на языке зрителя: тексты живут в lang.yml, а не в константах. */
    @NonNull
    public String getDisplay(String locale) {
        return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale,
            "wonder.category." + this.name().toLowerCase(java.util.Locale.ROOT) + ".name");
    }

    @NonNull
    public String getHint(String locale) {
        return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale,
            "wonder.category." + this.name().toLowerCase(java.util.Locale.ROOT) + ".hint");
    }

    WonderCategory(@NonNull Material icon) {
        this.icon = icon;
    }
}
