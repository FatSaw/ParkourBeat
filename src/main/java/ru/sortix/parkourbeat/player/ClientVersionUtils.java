package ru.sortix.parkourbeat.player;

import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientVersionUtils {
    private static Boolean viaAvailable = null;
    private static final Map<String, Integer> PROTOCOL_CACHE = new ConcurrentHashMap<>();

    private ClientVersionUtils() {
    }

    private static boolean isViaAvailable() {
        if (viaAvailable != null) return viaAvailable;
        viaAvailable = Bukkit.getPluginManager().getPlugin("ViaVersion") != null;
        return viaAvailable;
    }

    /**
     * Номер протокола клиента. ViaVersion знает настоящую версию даже когда сервер 1.16.5,
     * а игрок зашёл с 1.21. Без ViaVersion возвращается -1 и проверки версий пропускаются.
     */
    public static int getProtocol(@NonNull Player player) {
        if (!isViaAvailable()) return -1;
        try {
            Class<?> via = Class.forName("com.viaversion.viaversion.api.Via");
            Object api = via.getMethod("getAPI").invoke(null);
            Object version = api.getClass()
                .getMethod("getPlayerVersion", java.util.UUID.class)
                .invoke(api, player.getUniqueId());
            return version instanceof Integer ? (Integer) version : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Номер протокола по названию версии, из таблицы самой ViaVersion.
     * Так номера не приходится знать заранее, и новые версии игры ничего не ломают.
     */
    public static int resolveProtocol(@NonNull String versionName) {
        Integer cached = PROTOCOL_CACHE.get(versionName);
        if (cached != null) return cached;

        int resolved = lookupProtocol(versionName);
        PROTOCOL_CACHE.put(versionName, resolved);
        return resolved;
    }

    private static int lookupProtocol(@NonNull String versionName) {
        if (!isViaAvailable()) return -1;
        try {
            Class<?> type = Class.forName(
                "com.viaversion.viaversion.api.protocol.version.ProtocolVersion");

            Object version = null;
            try {
                version = type.getMethod("getClosest", String.class).invoke(null, versionName);
            } catch (NoSuchMethodException ignored) {
            }

            if (version == null) return -1;

            try {
                Object number = version.getClass().getMethod("getVersion").invoke(version);
                if (number instanceof Integer) return (Integer) number;
            } catch (NoSuchMethodException ignored) {
            }

            Object number = version.getClass().getMethod("getOriginalVersion").invoke(version);
            return number instanceof Integer ? (Integer) number : -1;
        } catch (Throwable t) {
            return -1;
        }
    }
}
