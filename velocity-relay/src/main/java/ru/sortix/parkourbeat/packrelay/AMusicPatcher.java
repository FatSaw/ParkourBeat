package ru.sortix.parkourbeat.packrelay;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

public final class AMusicPatcher {
    private final ProxyServer server;
    private final Logger logger;

    private boolean strictAccessPatched = false;
    private boolean waitAcceptionPatched = false;

    public AMusicPatcher(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    public boolean isStrictAccessPatched() {
        return this.strictAccessPatched;
    }

    public boolean isWaitAcceptionPatched() {
        return this.waitAcceptionPatched;
    }

    public void apply(RelayConfig config) {
        Object amusicPlugin = this.findAMusicInstance();
        if (amusicPlugin == null) {
            this.logger.warn("AMusic plugin instance not found, patches skipped");
            return;
        }

        Object localAMusic = readField(amusicPlugin, "amusic");
        if (localAMusic == null) {
            this.logger.warn("AMusic core instance not found, patches skipped");
            return;
        }

        Object resourceManager = readField(localAMusic, "resourcemanager");
        if (resourceManager == null) {
            this.logger.warn("AMusic ResourceManager not found, patches skipped");
            return;
        }

        if (config.patchWaitAcception) this.patchWaitAcception(resourceManager);
        if (config.patchStrictAccess) this.patchStrictAccess(resourceManager);
    }

    private Object findAMusicInstance() {
        Optional<PluginContainer> container = this.server.getPluginManager().getPlugin("amusic");
        if (container.isEmpty()) return null;
        return container.get().getInstance().orElse(null);
    }

    private void patchWaitAcception(Object resourceManager) {
        try {
            Field field = resourceManager.getClass().getDeclaredField("accepted");
            field.setAccessible(true);
            if (field.get(resourceManager) == null) {
                this.logger.info("waitacception already disabled, nothing to patch");
                this.waitAcceptionPatched = true;
                return;
            }
            field.set(resourceManager, null);
            this.waitAcceptionPatched = true;
            this.logger.info("Patched AMusic: waitacception gate disabled,"
                + " pack download no longer blocks on ACCEPTED status");
        } catch (Throwable t) {
            this.logger.warn("Unable to patch waitacception: {}", String.valueOf(t));
        }
    }

    private void patchStrictAccess(Object resourceManager) {
        try {
            Object serverManager = readField(resourceManager, "server");
            if (serverManager == null) {
                this.logger.warn("AMusic ServerManager not found, strictaccess patch skipped");
                return;
            }

            Object serverWatcher = readField(serverManager, "serverwatcher");
            if (serverWatcher == null) {
                this.logger.warn("AMusic ServerWatcher not started yet, strictaccess patch skipped");
                return;
            }

            Object connects = readField(serverWatcher, "connects");
            if (connects == null || !connects.getClass().isArray()) {
                this.logger.warn("AMusic accept threads not found, strictaccess patch skipped");
                return;
            }

            int patched = 0;
            int length = Array.getLength(connects);
            for (int i = 0; i < length; i++) {
                Object connect = Array.get(connects, i);
                if (connect == null) continue;
                if (replaceOnlineIps(connect)) patched++;
            }

            if (patched == 0) {
                this.logger.info("strictaccess is already off, nothing to patch");
                this.strictAccessPatched = true;
                return;
            }

            this.strictAccessPatched = true;
            this.logger.info("Patched AMusic: strictaccess bypassed on {} accept thread(s)."
                + " Pack downloads from a different IP than the login IP"
                + " (IPv6 vs IPv4, mobile NAT, VPN) will no longer get 403", patched);
        } catch (Throwable t) {
            this.logger.warn("Unable to patch strictaccess: {}", String.valueOf(t));
        }
    }

    private static boolean replaceOnlineIps(Object connect) {
        Class<?> type = connect.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!Collection.class.isAssignableFrom(field.getType())) continue;
                if (!field.getName().toLowerCase().contains("ip")) continue;
                try {
                    field.setAccessible(true);
                    if (field.get(connect) == null) continue;
                    field.set(connect, ALLOW_ALL);
                    return true;
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static Object readField(Object target, String name) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static final Collection<InetAddress> ALLOW_ALL = new AbstractCollection<InetAddress>() {
        @Override
        public boolean contains(Object o) {
            return true;
        }

        @Override
        public Iterator<InetAddress> iterator() {
            return Collections.<InetAddress>emptyList().iterator();
        }

        @Override
        public int size() {
            return 0;
        }
    };
}
