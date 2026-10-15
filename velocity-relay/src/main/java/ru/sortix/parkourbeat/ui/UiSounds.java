package ru.sortix.parkourbeat.ui;

import lombok.NonNull;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Свои звуки интерфейса (РП: assets/parkourbeat/sounds/ui/*.ogg).
 * <p>
 * {@code /customsounds} - выключить/включить у себя; {@code /customsounds all} (оператор) -
 * у всех сразу, сохраняется в plugins/&lt;плагин&gt;/customsounds.txt. Выключено - играют
 * прежние ванильные звуки.
 */
public final class UiSounds {
    private UiSounds() {
    }

    public enum Kind {
        CLICK(Sound.UI_BUTTON_CLICK, 1.2F), TOGGLE(Sound.UI_BUTTON_CLICK, 1.4F), DENY(Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F),
        PAGE(Sound.UI_BUTTON_CLICK, 1.0F), OPEN(Sound.BLOCK_NOTE_BLOCK_PLING, 1.3F),
        TRANSITION_IN(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8F), TRANSITION_OUT(Sound.BLOCK_NOTE_BLOCK_HAT, 1.4F);

        final Sound vanilla;
        final float pitch;

        Kind(Sound vanilla, float pitch) {
            this.vanilla = vanilla;
            this.pitch = pitch;
        }
    }

    private static volatile Boolean global;

    private static File file() {
        return new File(JavaPlugin.getProvidingPlugin(UiSounds.class).getDataFolder(), "customsounds.txt");
    }

    public static boolean globalEnabled() {
        Boolean cached = global;
        if (cached != null) return cached;
        boolean value = true;
        try {
            File f = file();
            if (f.exists()) value = !new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim().equalsIgnoreCase("off");
        } catch (Throwable ignored) {
        }
        global = value;
        return value;
    }

    public static void setGlobal(boolean enabled) {
        global = enabled;
        try {
            File f = file();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), (enabled ? "on" : "off").getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    private static NamespacedKey key() {
        return new NamespacedKey(JavaPlugin.getProvidingPlugin(UiSounds.class), "custom_sounds");
    }

    public static boolean personal(@NonNull Player player) {
        try {
            Byte v = player.getPersistentDataContainer().get(key(), PersistentDataType.BYTE);
            return v == null || v != 0;
        } catch (Throwable t) {
            return true;
        }
    }

    public static void setPersonal(@NonNull Player player, boolean enabled) {
        player.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
    }

    public static void play(@NonNull Player player, @NonNull Kind kind, float volume) {
        if (globalEnabled() && personal(player)) {
            player.playSound(player.getLocation(), "parkourbeat:ui." + kind.name().toLowerCase(java.util.Locale.ROOT),
                SoundCategory.MASTER, volume, 1.0F);
        } else {
            player.playSound(player.getLocation(), kind.vanilla, volume * 0.8F, kind.pitch);
        }
    }

    public static void click(@NonNull Player player) { play(player, Kind.CLICK, 0.7F); }

    public static void toggle(@NonNull Player player) { play(player, Kind.TOGGLE, 0.7F); }

    public static void deny(@NonNull Player player) { play(player, Kind.DENY, 0.8F); }

    public static void page(@NonNull Player player) { play(player, Kind.PAGE, 0.6F); }
}
