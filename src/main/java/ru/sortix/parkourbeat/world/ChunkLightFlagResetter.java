package ru.sortix.parkourbeat.world;

import lombok.NonNull;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Сбрасывает в сохранённых чанках флаг "свет уже посчитан".
 * <p>
 * Ад и энд живут без солнечного света, поэтому в их чанках лежит только свет от блоков.
 * Если такие чанки просто переехали в обычный мир, сервер верит записанному флагу и
 * заново свет не считает - уровень остаётся с чужим освещением. Достаточно погасить
 * один байт NBT (isLightOn / LightPopulated), и движок пересчитает свет сам при загрузке
 * чанка - это на порядок дешевле и надёжнее, чем трогать сами массивы света.
 * <p>
 * Формат региона: 4 КиБ таблицы смещений, 4 КиБ отметок времени, дальше чанки, каждый
 * выровнен по секторам в 4 КиБ. Файл переписывается целиком во временный файл и только
 * потом подменяет оригинал, поэтому оборванная запись не может испортить уровень.
 */
final class ChunkLightFlagResetter {
    private static final int SECTOR = 4096;
    private static final int MAX_SECTORS_PER_CHUNK = 255;

    /** TAG_Byte + длина имени + "isLightOn" */
    private static final byte[] IS_LIGHT_ON = {
        0x01, 0x00, 0x09, 'i', 's', 'L', 'i', 'g', 'h', 't', 'O', 'n'
    };

    /** Старое имя того же флага (миры, созданные до 1.14) */
    private static final byte[] LIGHT_POPULATED = {
        0x01, 0x00, 0x0E, 'L', 'i', 'g', 'h', 't', 'P', 'o', 'p', 'u', 'l', 'a', 't', 'e', 'd'
    };

    private ChunkLightFlagResetter() {
    }

    static void resetAll(@NonNull File regionDir, @NonNull Logger logger) {
        if (!regionDir.isDirectory()) return;

        File[] files = regionDir.listFiles((dir, name) -> name.endsWith(".mca"));
        if (files == null) return;

        for (File file : files) {
            try {
                resetFile(file);
            } catch (Throwable e) {
                // Один битый регион не должен отменять смену мира целиком:
                // худшее, что случится - в этом куске уровня останется старый свет.
                logger.log(Level.WARNING, "Не удалось сбросить свет в " + file.getAbsolutePath(), e);
            }
        }
    }

    private static void resetFile(@NonNull File file) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());
        if (data.length < SECTOR * 2) return;

        byte[] locations = new byte[SECTOR];
        ByteArrayOutputStream body = new ByteArrayOutputStream(Math.max(SECTOR, data.length));

        int nextSector = 2;
        boolean changed = false;

        for (int index = 0; index < 1024; index++) {
            int entry = index * 4;

            int offset = ((data[entry] & 0xFF) << 16)
                | ((data[entry + 1] & 0xFF) << 8)
                | (data[entry + 2] & 0xFF);
            int count = data[entry + 3] & 0xFF;

            if (offset < 2 || count <= 0) continue;

            int start = offset * SECTOR;
            if (start >= data.length) continue;

            int end = Math.min(start + count * SECTOR, data.length);

            byte[] block = null;
            byte[] patched = tryPatchChunk(data, start);
            if (patched != null) {
                block = patched;
                changed = true;
            }
            if (block == null) {
                // Чанк не разобрался (внешний файл .mcc, незнакомое сжатие, обрезанный
                // хвост файла) - переносим его байты как есть, ничего не теряя.
                block = padToSector(Arrays.copyOfRange(data, start, end));
            }

            int sectors = block.length / SECTOR;
            if (sectors <= 0 || sectors > MAX_SECTORS_PER_CHUNK) continue;

            body.write(block, 0, block.length);

            locations[entry] = (byte) ((nextSector >> 16) & 0xFF);
            locations[entry + 1] = (byte) ((nextSector >> 8) & 0xFF);
            locations[entry + 2] = (byte) (nextSector & 0xFF);
            locations[entry + 3] = (byte) sectors;

            nextSector += sectors;
        }

        if (!changed) return;

        File temp = new File(file.getParentFile(), file.getName() + ".pbtmp");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
            out.write(locations);
            out.write(data, SECTOR, SECTOR); // отметки времени переносятся без изменений
            body.writeTo(out);
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * @return готовый выровненный блок чанка, если флаг реально поменялся, иначе null
     */
    private static byte[] tryPatchChunk(@NonNull byte[] data, int start) {
        if (start + 5 > data.length) return null;

        int length = ((data[start] & 0xFF) << 24)
            | ((data[start + 1] & 0xFF) << 16)
            | ((data[start + 2] & 0xFF) << 8)
            | (data[start + 3] & 0xFF);
        if (length <= 0 || start + 4 + length > data.length) return null;

        byte compression = data[start + 4];
        byte[] payload = Arrays.copyOfRange(data, start + 5, start + 4 + length);

        byte[] result;
        try {
            switch (compression) {
                case 1: {
                    byte[] raw = gunzip(payload);
                    if (!patchNbt(raw)) return null;
                    result = gzip(raw);
                    break;
                }
                case 2: {
                    byte[] raw = inflate(payload);
                    if (!patchNbt(raw)) return null;
                    result = deflate(raw);
                    break;
                }
                case 3: {
                    byte[] raw = payload.clone();
                    if (!patchNbt(raw)) return null;
                    result = raw;
                    break;
                }
                default:
                    return null;
            }
        } catch (Throwable e) {
            return null;
        }

        byte[] block = buildBlock(compression, result);
        if (block.length / SECTOR > MAX_SECTORS_PER_CHUNK) return null;
        return block;
    }

    @NonNull
    private static byte[] buildBlock(byte compression, @NonNull byte[] payload) {
        int length = payload.length + 1;
        int total = length + 4;
        int sectors = (total + SECTOR - 1) / SECTOR;

        byte[] block = new byte[sectors * SECTOR];
        block[0] = (byte) ((length >>> 24) & 0xFF);
        block[1] = (byte) ((length >>> 16) & 0xFF);
        block[2] = (byte) ((length >>> 8) & 0xFF);
        block[3] = (byte) (length & 0xFF);
        block[4] = compression;
        System.arraycopy(payload, 0, block, 5, payload.length);
        return block;
    }

    @NonNull
    private static byte[] padToSector(@NonNull byte[] raw) {
        if (raw.length % SECTOR == 0) return raw;
        int sectors = (raw.length + SECTOR - 1) / SECTOR;
        return Arrays.copyOf(raw, sectors * SECTOR);
    }

    private static boolean patchNbt(@NonNull byte[] raw) {
        boolean changed = patchTag(raw, IS_LIGHT_ON);
        if (patchTag(raw, LIGHT_POPULATED)) changed = true;
        return changed;
    }

    private static boolean patchTag(@NonNull byte[] raw, @NonNull byte[] pattern) {
        boolean changed = false;
        int limit = raw.length - pattern.length - 1;

        search:
        for (int i = 0; i <= limit; i++) {
            if (raw[i] != pattern[0]) continue;
            for (int j = 1; j < pattern.length; j++) {
                if (raw[i + j] != pattern[j]) continue search;
            }
            int valueIndex = i + pattern.length;
            if (raw[valueIndex] != 0) {
                raw[valueIndex] = 0;
                changed = true;
            }
            i = valueIndex;
        }
        return changed;
    }

    @NonNull
    private static byte[] inflate(@NonNull byte[] input) throws IOException {
        try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(input))) {
            return readAll(in);
        }
    }

    @NonNull
    private static byte[] deflate(@NonNull byte[] input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length / 4 + 64);
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try {
            try (DeflaterOutputStream stream = new DeflaterOutputStream(out, deflater)) {
                stream.write(input);
            }
        } finally {
            deflater.end();
        }
        return out.toByteArray();
    }

    @NonNull
    private static byte[] gunzip(@NonNull byte[] input) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(input))) {
            return readAll(in);
        }
    }

    @NonNull
    private static byte[] gzip(@NonNull byte[] input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length / 4 + 64);
        try (GZIPOutputStream stream = new GZIPOutputStream(out)) {
            stream.write(input);
        }
        return out.toByteArray();
    }

    @NonNull
    private static byte[] readAll(@NonNull InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(65536);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
