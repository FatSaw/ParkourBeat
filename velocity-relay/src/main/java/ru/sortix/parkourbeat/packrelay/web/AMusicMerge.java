package ru.sortix.parkourbeat.packrelay.web;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class AMusicMerge {
    private static final class State {
        private byte[] pack;
        private int entries;
        private int cdSize;
        private int cdOffset;
        private int commentLength;
    }

    private final ProxyServer server;
    private final Logger logger;
    /**
     * Semaphore, а не ReentrantLock: install и release прилетают из разных сетевых потоков,
     * а ReentrantLock умеет отпускаться только тем потоком, который его взял. Из-за этого
     * слияние залипало навсегда после первого же запроса, и все следующие install молчали.
     */
    private final Semaphore lock = new Semaphore(1);
    private final AtomicLong lockedAt = new AtomicLong(0L);

    private Object source;
    private Field packField;
    private Field entriesField;
    private Field cdSizeField;
    private Field cdOffsetField;
    private Field commentField;

    private static final long STALE_LOCK_MILLIS = 30_000L;

    private State original;
    private boolean ready = false;

    public AMusicMerge(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    public boolean isReady() {
        return this.ready;
    }

    public void init() {
        try {
            Optional<PluginContainer> container = this.server.getPluginManager().getPlugin("amusic");
            Object plugin = container.flatMap(PluginContainer::getInstance).orElse(null);
            if (plugin == null) return;

            Object localAMusic = read(plugin, "amusic");
            if (localAMusic == null) {
                this.logger.warn("AMusic core instance not found, custom textures disabled");
                return;
            }

            // Упаковщик лежит прямо в LocalAMusic (поле soundsource), а не внутри datamanager.
            // Раньше поиск шёл не там, поэтому слияние молча не включалось.
            Object soundSource = read(localAMusic, "soundsource");
            if (soundSource == null || field(soundSource, "mergepack") == null) {
                soundSource = findSoundSource(localAMusic);
            }
            if (soundSource == null) {
                Object data = read(localAMusic, "datamanager");
                if (data != null) soundSource = findSoundSource(data);
            }
            if (soundSource == null) {
                this.logger.warn("AMusic pack source not found, custom textures disabled."
                    + " Merge field 'mergepack' is missing in every candidate object");
                return;
            }

            this.source = soundSource;
            this.packField = field(soundSource, "mergepack");
            this.entriesField = field(soundSource, "mregeentriescount");
            this.cdSizeField = field(soundSource, "mergecdsize");
            this.cdOffsetField = field(soundSource, "mergecdoffset");
            this.commentField = field(soundSource, "mergecommentlength");

            if (this.packField == null || this.entriesField == null || this.cdSizeField == null
                || this.cdOffsetField == null || this.commentField == null) {
                this.logger.warn("AMusic merge fields not found, custom textures disabled");
                return;
            }

            this.original = this.capture();
            this.ready = true;
            this.logger.info("AMusic merge hook ready on {}, default merge pack: {} bytes",
                this.source.getClass().getSimpleName(),
                this.original.pack == null ? 0 : this.original.pack.length);
        } catch (Throwable t) {
            this.logger.warn("Unable to hook AMusic merge", t);
        }
    }

    private Object findSoundSource(Object holder) {
        Object data = holder;
        Class<?> type = data.getClass();
        while (type != null && type != Object.class) {
            for (Field candidate : type.getDeclaredFields()) {
                try {
                    candidate.setAccessible(true);
                    Object value = candidate.get(data);
                    if (value == null) continue;
                    if (field(value, "mergepack") != null) return value;
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    /**
     * Ставит текстуры конкретного уровня и держит лок до release(). Сборка пака у AMusic
     * читает mergepack прямо в момент упаковки, поэтому два одновременных запроса без лока
     * склеили бы игроку чужие текстуры.
     */
    public boolean install(Path zip) {
        if (!this.ready) return false;

        this.releaseIfStale();

        try {
            if (!this.lock.tryAcquire(5, TimeUnit.SECONDS)) {
                this.logger.warn("Merge lock is busy, texture pack skipped for this build");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        this.lockedAt.set(System.currentTimeMillis());

        try {
            if (zip == null || !Files.isRegularFile(zip)) {
                this.logger.warn("Texture pack file is missing: {}", String.valueOf(zip));
                this.apply(this.original);
                return true;
            }

            byte[] bytes = Files.readAllBytes(zip);
            State state = parse(bytes);
            if (state == null) {
                this.logger.warn("Texture pack is not a readable zip: {}", zip.getFileName());
                this.apply(this.original);
                return false;
            }
            this.apply(state);
            this.logger.info("Texture pack installed for build: {} ({} bytes, {} entries)",
                zip.getFileName(), bytes.length, state.entries);
            return true;
        } catch (Throwable t) {
            this.logger.warn("Unable to install texture pack {}", String.valueOf(zip), t);
            try {
                this.apply(this.original);
            } catch (Throwable ignored) {
            }
            this.lockedAt.set(0L);
            this.lock.release();
            return false;
        }
    }

    public void release() {
        if (!this.ready) return;
        if (this.lockedAt.getAndSet(0L) == 0L) return;

        try {
            this.apply(this.original);
        } catch (Throwable t) {
            this.logger.warn("Unable to restore default merge pack", t);
        } finally {
            this.lock.release();
        }
    }

    /**
     * Если бэкенд не прислал release (упал, перезагрузился, отвалился игрок),
     * слияние не должно оставаться занятым навсегда.
     */
    private void releaseIfStale() {
        long since = this.lockedAt.get();
        if (since == 0L) return;
        if (System.currentTimeMillis() - since < STALE_LOCK_MILLIS) return;

        this.logger.warn("Merge lock was held for too long without release, forcing it open");
        this.release();
    }

    private State capture() throws Exception {
        State state = new State();
        state.pack = (byte[]) this.packField.get(this.source);
        state.entries = this.entriesField.getInt(this.source);
        state.cdSize = this.cdSizeField.getInt(this.source);
        state.cdOffset = this.cdOffsetField.getInt(this.source);
        state.commentLength = this.commentField.getInt(this.source);
        return state;
    }

    private void apply(State state) throws Exception {
        this.packField.set(this.source, state.pack);
        this.entriesField.setInt(this.source, state.entries);
        this.cdSizeField.setInt(this.source, state.cdSize);
        this.cdOffsetField.setInt(this.source, state.cdOffset);
        this.commentField.setInt(this.source, state.commentLength);
    }

    /**
     * Читает End of Central Directory ровно так же, как это делает конструктор AMusic:
     * число записей, размер и смещение центральной директории, длину комментария.
     */
    static State parse(byte[] zip) {
        if (zip.length < 22) return null;

        int limit = Math.max(0, zip.length - 22 - 0xFFFF);
        for (int i = zip.length - 22; i >= limit; i--) {
            if (zip[i] != 0x50 || zip[i + 1] != 0x4b || zip[i + 2] != 0x05 || zip[i + 3] != 0x06) {
                continue;
            }

            int commentLength = ((zip[i + 21] & 0xFF) << 8) | (zip[i + 20] & 0xFF);
            if (i + 22 + commentLength != zip.length) continue;

            State state = new State();
            state.pack = zip;
            state.entries = ((zip[i + 11] & 0xFF) << 8) | (zip[i + 10] & 0xFF);
            state.cdSize = readInt(zip, i + 12);
            state.cdOffset = readInt(zip, i + 16);
            state.commentLength = commentLength;
            return state;
        }
        return null;
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset + 3] & 0xFF) << 24) | ((data[offset + 2] & 0xFF) << 16)
            | ((data[offset + 1] & 0xFF) << 8) | (data[offset] & 0xFF);
    }

    private static Field field(Object target, String name) {
        Class<?> type = target.getClass();
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

    private static Object read(Object target, String name) {
        Field found = field(target, name);
        if (found == null) return null;
        try {
            return found.get(target);
        } catch (Throwable t) {
            return null;
        }
    }
}
