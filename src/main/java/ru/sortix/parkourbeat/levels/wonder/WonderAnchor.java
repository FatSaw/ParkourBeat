package ru.sortix.parkourbeat.levels.wonder;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Material;

/**
 * Куда именно ставится эффект в момент срабатывания.
 * <p>
 * Точка считается один раз на старте, поэтому эффект остаётся на месте, пока игрок
 * пробегает мимо — кроме {@link #FOLLOW}, который едет за игроком.
 */
@Getter
public enum WonderAnchor {
    PATH(Material.POWERED_RAIL),
    AHEAD(Material.ARROW),
    OVERHEAD(Material.FEATHER),
    FOLLOW(Material.LEAD),
    FIXED(Material.COMPASS);

    private final @NonNull Material icon;

    /** Подпись на языке зрителя: тексты живут в lang.yml, а не в константах. */
    @NonNull
    public String getDisplay(String locale) {
        return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale,
            "wonder.anchor." + this.name().toLowerCase(java.util.Locale.ROOT) + ".name");
    }

    @NonNull
    public String getHint(String locale) {
        return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale,
            "wonder.anchor." + this.name().toLowerCase(java.util.Locale.ROOT) + ".hint");
    }

    WonderAnchor(@NonNull Material icon) {
        this.icon = icon;
    }

    @NonNull
    public WonderAnchor next() {
        WonderAnchor[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    @NonNull
    public static WonderAnchor byName(String name, @NonNull WonderAnchor def) {
        if (name == null) return def;
        for (WonderAnchor anchor : values()) if (anchor.name().equalsIgnoreCase(name)) return anchor;
        return def;
    }
}
