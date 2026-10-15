package ru.sortix.parkourbeat.utils;

import lombok.NonNull;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * СОХРАНЕНИЕ БЕЗ ЗАПИСИ НА ДИСК В ГЛАВНОМ ПОТОКЕ.
 * <p>
 * На медленном диске (новый хостинг) даже открыть файл занимает секунды, а автосохранение
 * редактируемых уровней раз в 15 секунд делало {@code world.save()} в главном потоке:
 * кроме чанков он синхронно пишет level.dat и файлы data/*.dat. Сервер замирал на 10-15 с
 * (дамп «The server has not responded» указывал ровно на LevelsManager.saveEditedLevels).
 * <ul>
 *   <li>{@link #saveChunks} - только изменённые чанки; Paper сериализует их здесь, а
 *       пишет в своём потоке ввода-вывода. level.dat/data сохранятся при закрытии мира.</li>
 *   <li>{@link #write} - файлы настроек: текст готовится сразу, пишется в отдельном потоке;
 *       если содержимое не изменилось - не пишется вовсе.</li>
 * </ul>
 */
public final class FastSave {
    private FastSave() {
    }

    private static volatile Method getHandle, getChunkProvider, saveChunks;
    private static volatile boolean broken;

    /** Сохранить изменённые чанки мира без синхронной записи. false - не вышло (вызови world.save()). */
    public static boolean saveChunks(@NonNull World world) {
        if (broken) return false;
        try {
            if (getHandle == null) {
                getHandle = world.getClass().getMethod("getHandle");
            }
            Object handle = getHandle.invoke(world);
            if (getChunkProvider == null) {
                getChunkProvider = handle.getClass().getMethod("getChunkProvider");
            }
            Object provider = getChunkProvider.invoke(handle);
            if (saveChunks == null) {
                saveChunks = provider.getClass().getMethod("save", boolean.class);
            }
            saveChunks.invoke(provider, false);
            return true;
        } catch (Throwable t) {
            broken = true;
            org.bukkit.Bukkit.getLogger().log(Level.WARNING,
                "[ParkourBeat] fast chunk save unavailable, falling back to world.save()", t);
            return false;
        }
    }

    // ==================== файлы ====================

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ParkourBeat-FileSave");
        // daemon: пустой поток не должен держать JVM при выключении - перед ним зовут flush().
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<String, String> LAST = new ConcurrentHashMap<>();

    /** Записать текст в файл в фоне (атомарно, через временный файл). */
    public static void write(@NonNull File file, @NonNull String content) {
        String key = file.getAbsolutePath();
        if (content.equals(LAST.get(key))) return;
        LAST.put(key, content);
        IO.execute(() -> {
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.isDirectory()) parent.mkdirs();
                File tmp = new File(parent, file.getName() + ".tmp");
                Files.write(tmp.toPath(), content.getBytes(StandardCharsets.UTF_8));
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                LAST.remove(key);
                org.bukkit.Bukkit.getLogger().log(Level.SEVERE, "[ParkourBeat] unable to save " + file, e);
            }
        });
    }

    /** Дождаться записи всех файлов (при выключении сервера). */
    public static void flush() {
        try {
            IO.submit(() -> { }).get(30, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        }
    }
}
