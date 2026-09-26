package ru.sortix.parkourbeat.packrelay.slicer;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Заставляет AMusic увидеть плейлист, созданный уже после старта прокси.
 * <p>
 * ПОЧЕМУ ЭТО ВООБЩЕ НУЖНО. В AMusic список запакованных ресурспаков живёт в
 * {@code Data.options} — обычной HashMap, которая заполняется в {@code Data.load()}.
 * А {@code load()} вызывается ровно один раз, из {@code LocalAMusic.enable()}, то есть
 * при запуске прокси. Папка плейлиста, появившаяся позже, в эту карту не попадает
 * никогда: {@code getResourcepack(name)} по ней вернёт null, и пак игроку просто не
 * уедет. Именно поэтому новые чекпоинты не применялись без перезахода, а удаление
 * чекпоинтов срабатывало сразу — там уровень возвращается на исходный плейлист,
 * который в карте был с самого начала.
 * <p>
 * Лечится вызовом {@code Data.update(id)} — штатным методом AMusic, который
 * запаковывает и регистрирует один плейлист. Полный {@code load()} остаётся запасным
 * вариантом: он перепаковывает вообще всё и на большой библиотеке треков стоит дорого.
 */
public final class AMusicIndexer {
    private final ProxyServer server;
    private final Logger logger;

    private Object dataManager;
    private Method updateMethod;
    private Method loadMethod;
    private boolean initialized = false;

    public AMusicIndexer(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    public synchronized void init() {
        if (this.initialized) return;
        this.initialized = true;

        try {
            Optional<PluginContainer> container = this.server.getPluginManager().getPlugin("amusic");
            Object plugin = container.flatMap(PluginContainer::getInstance).orElse(null);
            if (plugin == null) {
                this.logger.warn("AMusic plugin not found, new playlists will need a proxy restart");
                return;
            }

            Object localAMusic = read(plugin, "amusic");
            if (localAMusic == null) {
                this.logger.warn("AMusic core instance not found, playlist indexing disabled");
                return;
            }

            Object data = read(localAMusic, "datamanager");
            if (data == null) {
                this.logger.warn("AMusic datamanager not found, playlist indexing disabled");
                return;
            }

            this.dataManager = data;
            this.updateMethod = method(data.getClass(), "update", String.class);
            this.loadMethod = method(data.getClass(), "load");

            if (this.updateMethod == null && this.loadMethod == null) {
                this.logger.warn("AMusic Data has neither update() nor load(), indexing disabled");
                return;
            }
            this.logger.info("AMusic playlist indexer ready ({})", data.getClass().getSimpleName());
        } catch (Throwable t) {
            this.logger.warn("Unable to hook AMusic playlist indexer", t);
        }
    }

    /**
     * Проиндексировать плейлист, чтобы AMusic смог собрать и выдать по нему пак.
     *
     * @return true, если индексация выполнена
     */
    public synchronized boolean index(String playlistId) {
        this.init();
        if (this.dataManager == null) return false;

        if (this.updateMethod != null) {
            try {
                this.updateMethod.invoke(this.dataManager, playlistId);
                this.logger.info("Playlist {} indexed in AMusic", playlistId);
                return true;
            } catch (Throwable t) {
                this.logger.warn("Data.update({}) failed, falling back to full reload", playlistId, t);
            }
        }

        if (this.loadMethod != null) {
            try {
                this.loadMethod.invoke(this.dataManager);
                this.logger.info("AMusic playlists fully reloaded after creating {}", playlistId);
                return true;
            } catch (Throwable t) {
                this.logger.warn("Data.load() failed after creating {}", playlistId, t);
            }
        }
        return false;
    }

    private static Object read(Object target, String name) {
        Field found = fieldOf(target.getClass(), name);
        if (found == null) return null;
        try {
            return found.get(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Field fieldOf(Class<?> type, String name) {
        while (type != null && type != Object.class) {
            try {
                Field found = type.getDeclaredField(name);
                found.setAccessible(true);
                return found;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static Method method(Class<?> type, String name, Class<?>... args) {
        while (type != null && type != Object.class) {
            try {
                Method found = type.getDeclaredMethod(name, args);
                found.setAccessible(true);
                return found;
            } catch (NoSuchMethodException e) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
}
