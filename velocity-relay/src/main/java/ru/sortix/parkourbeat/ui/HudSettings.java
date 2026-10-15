package ru.sortix.parkourbeat.ui;

import lombok.NonNull;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Личные настройки отображения (меню «Интерфейс»). Хранятся прямо в данных игрока
 * (PersistentDataContainer) - переживают перезаход и рестарт, базы не нужно.
 */
public final class HudSettings {
    private HudSettings() {
    }

    /** Как показывать очки за прыжок. */
    public enum Points {
        /** Крупно в сабтайтле, как было: «+300». */
        CLASSIC,
        /** Мелко нашими цифрами в актионбаре: «+ꜱ00» не мешает при большом GUI. */
        MINI,
        /** Не показывать. */
        OFF
    }

    private static NamespacedKey key(@NonNull String name) {
        return new NamespacedKey(JavaPlugin.getProvidingPlugin(HudSettings.class), name);
    }

    @NonNull
    public static Points points(@NonNull Player player) {
        try {
            Byte value = player.getPersistentDataContainer().get(key("hud_points"), PersistentDataType.BYTE);
            if (value != null && value >= 0 && value < Points.values().length) return Points.values()[value];
        } catch (Throwable ignored) {
        }
        return Points.CLASSIC;
    }

    public static void setPoints(@NonNull Player player, @NonNull Points points) {
        player.getPersistentDataContainer().set(key("hud_points"), PersistentDataType.BYTE, (byte) points.ordinal());
    }

    /** Плашка оценки (ВЕЛИКОЛЕПНО и т.д.) в актионбаре. */
    public static boolean banner(@NonNull Player player) {
        try {
            PersistentDataContainer data = player.getPersistentDataContainer();
            Byte value = data.get(key("hud_banner"), PersistentDataType.BYTE);
            return value == null || value != 0;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setBanner(@NonNull Player player, boolean enabled) {
        player.getPersistentDataContainer().set(key("hud_banner"), PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
    }

    /** Полноэкранные переходы при заходе на уровень. */
    public static boolean transitions(@NonNull Player player) {
        return flag(player, "hud_transitions");
    }

    public static void setTransitions(@NonNull Player player, boolean enabled) {
        player.getPersistentDataContainer().set(key("hud_transitions"), PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
    }

    /** Анимация логотипа в табе и табло. */
    public static boolean logo(@NonNull Player player) {
        return flag(player, "hud_logo");
    }

    public static void setLogo(@NonNull Player player, boolean enabled) {
        player.getPersistentDataContainer().set(key("hud_logo"), PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
    }

    private static boolean flag(@NonNull Player player, @NonNull String name) {
        try {
            Byte value = player.getPersistentDataContainer().get(key(name), PersistentDataType.BYTE);
            return value == null || value != 0;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
