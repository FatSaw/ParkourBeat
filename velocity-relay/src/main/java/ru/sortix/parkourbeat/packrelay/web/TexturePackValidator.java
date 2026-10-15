package ru.sortix.parkourbeat.packrelay.web;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class TexturePackValidator {
    public enum Status {
        OK,
        NOT_ZIP,
        NO_MCMETA,
        UNSAFE_PATH,
        TOO_MANY_FILES,
        TOO_BIG_UNPACKED,
        BROKEN
    }

    public static final class Result {
        public final Status status;
        public final String detail;
        public final int fileCount;
        public final long unpackedSize;

        private Result(Status status, String detail, int fileCount, long unpackedSize) {
            this.status = status;
            this.detail = detail;
            this.fileCount = fileCount;
            this.unpackedSize = unpackedSize;
        }

        public boolean isOk() {
            return this.status == Status.OK;
        }
    }

    // Обычный текстурпак спокойно содержит десятки тысяч файлов: каждый блок, предмет,
    // модель и звук лежат отдельно. Лимиты здесь только против zip-бомб, а не против
    // нормальных паков, поэтому они заведомо выше любого вменяемого архива.
    private static final int MAX_FILES = 60_000;
    private static final long MAX_UNPACKED_BYTES = 2048L * 1024L * 1024L;
    private static final long MAX_SINGLE_FILE = 128L * 1024L * 1024L;

    private TexturePackValidator() {
    }

    public static Result check(byte[] data, String fileName) {
        if (fileName != null && !fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return new Result(Status.NOT_ZIP, "нужен архив .zip", 0, 0);
        }
        if (data.length < 22 || data[0] != 'P' || data[1] != 'K') {
            return new Result(Status.NOT_ZIP, "нет сигнатуры PK", 0, 0);
        }

        boolean hasMcmeta = false;
        int files = 0;
        long unpacked = 0;

        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');

                if (name.startsWith("/") || name.contains("../") || name.contains(":")) {
                    return new Result(Status.UNSAFE_PATH, name, files, unpacked);
                }
                if (entry.isDirectory()) continue;

                if (++files > MAX_FILES) {
                    return new Result(Status.TOO_MANY_FILES, null, files, unpacked);
                }

                long entrySize = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    entrySize += read;
                    unpacked += read;
                    if (entrySize > MAX_SINGLE_FILE || unpacked > MAX_UNPACKED_BYTES) {
                        return new Result(Status.TOO_BIG_UNPACKED, name, files, unpacked);
                    }
                }

                if (name.equals("pack.mcmeta")) hasMcmeta = true;
            }
        } catch (Exception e) {
            return new Result(Status.BROKEN, String.valueOf(e.getMessage()), files, unpacked);
        }

        if (files == 0) return new Result(Status.BROKEN, "архив пуст", 0, 0);
        if (!hasMcmeta) return new Result(Status.NO_MCMETA, null, files, unpacked);

        return new Result(Status.OK, null, files, unpacked);
    }
}
