package ru.sortix.parkourbeat.world;

import lombok.NonNull;
import ru.sortix.parkourbeat.ParkourBeat;

/**
 * Глобальный переключатель автовыравнивания камеры при входе на уровень.
 * <p>
 * По умолчанию включён: игрок попадает на уровень уже смотрящим строго вдоль
 * трассы, ровно как строитель при установке точки спавна. Администратор может
 * выключить это командой {@code /offautolook} — значение сохраняется в config.yml
 * и переживает рестарт.
 */
public final class AutoLookSettings {
    public static final String CONFIG_KEY = "auto_look";

    /** Читается при включении плагина из config.yml. */
    public static boolean ENABLED = true;

    private AutoLookSettings() {
    }

    public static void load(@NonNull ParkourBeat plugin) {
        ENABLED = plugin.getConfig().getBoolean(CONFIG_KEY, true);
    }

    public static void setAndSave(@NonNull ParkourBeat plugin, boolean enabled) {
        ENABLED = enabled;
        plugin.getConfig().set(CONFIG_KEY, enabled);
        plugin.saveConfig();
    }
}
