package ru.sortix.parkourbeat.packrelay.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Приводит {@code pack.mcmeta} в архиве к виду, который принимают новые клиенты.
 * <p>
 * Начиная с формата 64 клиент требует, чтобы пак, объявляющий поддержку более новой
 * версии, обязательно нёс поля {@code min_format} и {@code max_format}. Без них
 * загрузка валится с
 * {@code "Pack declares support for version newer than 64, but is missing mandatory
 * fields min_format and max_format"}, клиент уходит в fallback, и пак либо применяется
 * неправильно, либо не применяется вовсе — вместе со всей музыкой.
 * <p>
 * Ловить это на игроках бессмысленно: сборщики паков ставят {@code pack_format} руками,
 * и рано или поздно кто-то зальёт архив от свежей версии. Поэтому метаданные чиним сами
 * при загрузке, а автору ничего делать не нужно.
 */
public final class PackMetaFixer {
    /**
     * Формат, начиная с которого клиент требует диапазон версий.
     */
    private static final int RANGE_REQUIRED_FROM = 64;

    private PackMetaFixer() {
    }

    public static final class Result {
        public final byte[] data;
        public final boolean changed;
        public final String note;

        private Result(byte[] data, boolean changed, String note) {
            this.data = data;
            this.changed = changed;
            this.note = note;
        }
    }

    /**
     * @return архив с исправленным pack.mcmeta либо исходный, если чинить нечего
     */
    public static Result fix(byte[] zipBytes, Logger logger) {
        try {
            byte[] mcmeta = readEntry(zipBytes, "pack.mcmeta");
            if (mcmeta == null) return new Result(zipBytes, false, null);

            String fixed = fixMeta(new String(mcmeta, StandardCharsets.UTF_8));
            if (fixed == null) return new Result(zipBytes, false, null);

            byte[] rebuilt = replaceEntry(zipBytes, "pack.mcmeta",
                fixed.getBytes(StandardCharsets.UTF_8));
            if (rebuilt == null) return new Result(zipBytes, false, null);

            return new Result(rebuilt, true,
                "pack.mcmeta дополнен полями min_format и max_format");
        } catch (Throwable t) {
            if (logger != null) logger.warn("Unable to fix pack.mcmeta", t);
            return new Result(zipBytes, false, null);
        }
    }

    /**
     * @return исправленный json либо null, если правка не требуется
     */
    static String fixMeta(String json) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (Throwable t) {
            return null;
        }
        if (root == null || !root.isJsonObject()) return null;

        JsonObject object = root.getAsJsonObject();
        JsonElement packElement = object.get("pack");
        if (packElement == null || !packElement.isJsonObject()) return null;

        JsonObject pack = packElement.getAsJsonObject();
        JsonElement formatElement = pack.get("pack_format");
        if (formatElement == null || !formatElement.isJsonPrimitive()) return null;

        int format;
        try {
            format = formatElement.getAsInt();
        } catch (Throwable t) {
            return null;
        }

        // До 64 старые правила работают, трогать ничего не нужно.
        if (format < RANGE_REQUIRED_FROM) return null;
        // Диапазон уже указан автором — уважаем его выбор.
        if (pack.has("min_format") && pack.has("max_format")) return null;

        // supported_formats — старая форма записи диапазона. Если она есть,
        // разворачиваем её в новые поля, чтобы не потерять замысел автора.
        int min = format;
        int max = format;
        JsonElement supported = pack.get("supported_formats");
        if (supported != null) {
            if (supported.isJsonArray() && supported.getAsJsonArray().size() == 2) {
                try {
                    min = supported.getAsJsonArray().get(0).getAsInt();
                    max = supported.getAsJsonArray().get(1).getAsInt();
                } catch (Throwable ignored) {
                }
            } else if (supported.isJsonObject()) {
                JsonObject range = supported.getAsJsonObject();
                try {
                    if (range.has("min_inclusive")) min = range.get("min_inclusive").getAsInt();
                    if (range.has("max_inclusive")) max = range.get("max_inclusive").getAsInt();
                } catch (Throwable ignored) {
                }
            }
        }
        if (min > max) min = max;

        pack.addProperty("min_format", min);
        pack.addProperty("max_format", max);
        return object.toString();
    }

    private static byte[] readEntry(byte[] zipBytes, String name) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (!name.equals(entry.getName())) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                in.transferTo(out);
                return out.toByteArray();
            }
        }
        return null;
    }

    /**
     * Пересобрать архив, заменив одну запись. Архив переписывается целиком: правка
     * записи «на месте» потребовала бы ручной работы с central directory, а паки
     * заливаются редко, и лишняя пересборка тут ничего не стоит.
     */
    private static byte[] replaceEntry(byte[] zipBytes, String name, byte[] content) throws Exception {
        ByteArrayOutputStream result = new ByteArrayOutputStream(zipBytes.length + 1024);
        boolean written = false;

        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zipBytes));
             ZipOutputStream out = new ZipOutputStream(result)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ZipEntry copy = new ZipEntry(entry.getName());
                out.putNextEntry(copy);
                if (name.equals(entry.getName())) {
                    out.write(content);
                    written = true;
                } else {
                    in.transferTo(out);
                }
                out.closeEntry();
            }
            if (!written) {
                out.putNextEntry(new ZipEntry(name));
                out.write(content);
                out.closeEntry();
            }
        }
        return result.toByteArray();
    }
}
