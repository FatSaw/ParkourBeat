package ru.sortix.parkourbeat.packrelay.web;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OggValidator {
    public enum Status {
        OK,
        NOT_OGG_EXTENSION,
        NBS_EXTENSION,
        BAD_MAGIC,
        BROKEN_STRUCTURE,
        NOT_VORBIS
    }

    public static final class Result {
        public final Status status;
        public final String detail;
        public final int channels;
        public final int sampleRate;

        private Result(Status status, String detail, int channels, int sampleRate) {
            this.status = status;
            this.detail = detail;
            this.channels = channels;
            this.sampleRate = sampleRate;
        }

        public boolean isOk() {
            return this.status == Status.OK;
        }
    }

    private static final class Page {
        int offset;
        int length;
        int headerType;
        long serial;
        int segmentCount;
        int segmentTableOffset;
        int dataOffset;
    }

    private static final int[] CRC_TABLE = buildCrcTable();

    private OggValidator() {
    }

    public static Result check(byte[] data, String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".nbs")) {
            return new Result(Status.NBS_EXTENSION, null, 0, 0);
        }
        if (!lower.endsWith(".ogg")) {
            return new Result(Status.NOT_OGG_EXTENSION, null, 0, 0);
        }
        if (data.length < 58) {
            return new Result(Status.BAD_MAGIC, "файл слишком короткий", 0, 0);
        }
        if (data[0] != 'O' || data[1] != 'g' || data[2] != 'g' || data[3] != 'S') {
            return new Result(Status.BAD_MAGIC, "нет сигнатуры OggS", 0, 0);
        }

        List<Page> pages;
        try {
            pages = readPages(data);
        } catch (IllegalStateException e) {
            return new Result(Status.BROKEN_STRUCTURE, e.getMessage(), 0, 0);
        }
        if (pages.isEmpty()) {
            return new Result(Status.BROKEN_STRUCTURE, "не найдено ни одной страницы", 0, 0);
        }
        if ((pages.get(0).headerType & 0x02) == 0) {
            return new Result(Status.BROKEN_STRUCTURE, "первая страница не помечена как начальная", 0, 0);
        }
        if ((pages.get(pages.size() - 1).headerType & 0x04) == 0) {
            return new Result(Status.BROKEN_STRUCTURE, "поток не закрыт, файл обрезан", 0, 0);
        }

        long serial = pages.get(0).serial;
        List<int[]> packets = readPacketRanges(data, pages, serial, 3);
        if (packets.isEmpty()) {
            return new Result(Status.BROKEN_STRUCTURE, "не найден первый пакет", 0, 0);
        }

        int[] identification = packets.get(0);
        int identOffset = identification[0];
        int identLength = identification[1];
        if (identLength < 30 || !isVorbisHeader(data, identOffset, 1)) {
            return new Result(Status.NOT_VORBIS, "это не Vorbis-поток", 0, 0);
        }

        int channels = data[identOffset + 11] & 0xFF;
        int sampleRate = readIntLE(data, identOffset + 12);
        if (channels < 1 || sampleRate < 8000 || sampleRate > 192000) {
            return new Result(Status.NOT_VORBIS, "некорректные параметры аудио", channels, sampleRate);
        }
        if (packets.size() < 2 || !isVorbisHeader(data, packets.get(1)[0], 3)) {
            return new Result(Status.BROKEN_STRUCTURE, "нет заголовка комментариев", channels, sampleRate);
        }

        return new Result(Status.OK, null, channels, sampleRate);
    }

    /**
     * Комментарий переписывается на месте, ровно той же длины, поэтому таблицы сегментов
     * и границы страниц остаются нетронутыми. Меняется только содержимое пакета
     * и контрольные суммы затронутых страниц.
     */
    public static byte[] stripMetadata(byte[] data) {
        try {
            List<Page> pages = readPages(data);
            if (pages.isEmpty()) return data;

            long serial = pages.get(0).serial;
            List<int[]> packets = readPacketRanges(data, pages, serial, 2);
            if (packets.size() < 2) return data;

            int commentOffset = packets.get(1)[0];
            int commentLength = packets.get(1)[1];
            if (commentLength < 16) return data;
            if (!isVorbisHeader(data, commentOffset, 3)) return data;

            byte[] replacement = buildEmptyComment(commentLength);
            byte[] result = data.clone();
            System.arraycopy(replacement, 0, result, commentOffset, commentLength);

            for (Page page : pages) {
                if (page.serial != serial) continue;
                if (page.offset + page.length <= commentOffset) continue;
                if (page.offset >= commentOffset + commentLength) continue;
                rewriteCrc(result, page);
            }
            return result;
        } catch (Throwable t) {
            return data;
        }
    }

    private static byte[] buildEmptyComment(int totalLength) {
        byte[] packet = new byte[totalLength];
        packet[0] = 3;
        packet[1] = 'v';
        packet[2] = 'o';
        packet[3] = 'r';
        packet[4] = 'b';
        packet[5] = 'i';
        packet[6] = 's';

        int vendorLength = totalLength - 16;
        writeIntLE(packet, 7, vendorLength);
        for (int i = 0; i < vendorLength; i++) {
            packet[11 + i] = ' ';
        }
        writeIntLE(packet, 11 + vendorLength, 0);
        packet[totalLength - 1] = 1;
        return packet;
    }

    private static List<Page> readPages(byte[] data) {
        List<Page> pages = new ArrayList<>();
        int offset = 0;

        while (offset + 27 <= data.length) {
            if (data[offset] != 'O' || data[offset + 1] != 'g'
                || data[offset + 2] != 'g' || data[offset + 3] != 'S') {
                throw new IllegalStateException("нарушена структура страниц");
            }
            if (data[offset + 4] != 0) {
                throw new IllegalStateException("неизвестная версия Ogg");
            }

            Page page = new Page();
            page.offset = offset;
            page.headerType = data[offset + 5] & 0xFF;
            page.serial = readIntLE(data, offset + 14) & 0xFFFFFFFFL;
            page.segmentCount = data[offset + 26] & 0xFF;
            page.segmentTableOffset = offset + 27;
            page.dataOffset = page.segmentTableOffset + page.segmentCount;

            if (page.dataOffset > data.length) {
                throw new IllegalStateException("обрезанная таблица сегментов");
            }

            int payload = 0;
            for (int i = 0; i < page.segmentCount; i++) {
                payload += data[page.segmentTableOffset + i] & 0xFF;
            }
            page.length = 27 + page.segmentCount + payload;

            if (page.offset + page.length > data.length) {
                throw new IllegalStateException("страница выходит за пределы файла");
            }

            pages.add(page);
            offset += page.length;
        }

        if (offset != data.length) {
            throw new IllegalStateException("в конце файла лишние байты");
        }
        return pages;
    }

    private static List<int[]> readPacketRanges(byte[] data, List<Page> pages, long serial, int limit) {
        List<int[]> packets = new ArrayList<>();
        int packetStart = -1;
        int packetLength = 0;

        for (Page page : pages) {
            if (page.serial != serial) continue;
            int cursor = page.dataOffset;

            for (int i = 0; i < page.segmentCount; i++) {
                int size = data[page.segmentTableOffset + i] & 0xFF;
                if (packetStart < 0) {
                    packetStart = cursor;
                    packetLength = 0;
                }
                packetLength += size;
                cursor += size;

                if (size < 255) {
                    packets.add(new int[]{packetStart, packetLength});
                    packetStart = -1;
                    if (packets.size() >= limit) return packets;
                }
            }
        }
        return packets;
    }

    private static boolean isVorbisHeader(byte[] data, int offset, int type) {
        if (offset + 7 > data.length) return false;
        return (data[offset] & 0xFF) == type
            && data[offset + 1] == 'v' && data[offset + 2] == 'o' && data[offset + 3] == 'r'
            && data[offset + 4] == 'b' && data[offset + 5] == 'i' && data[offset + 6] == 's';
    }

    private static void rewriteCrc(byte[] data, Page page) {
        for (int i = 0; i < 4; i++) {
            data[page.offset + 22 + i] = 0;
        }
        int crc = 0;
        int end = page.offset + page.length;
        for (int i = page.offset; i < end; i++) {
            crc = (crc << 8) ^ CRC_TABLE[((crc >>> 24) & 0xFF) ^ (data[i] & 0xFF)];
        }
        writeIntLE(data, page.offset + 22, crc);
    }

    private static int[] buildCrcTable() {
        int[] table = new int[256];
        for (int i = 0; i < 256; i++) {
            int value = i << 24;
            for (int bit = 0; bit < 8; bit++) {
                value = (value & 0x80000000) != 0 ? (value << 1) ^ 0x04c11db7 : value << 1;
            }
            table[i] = value;
        }
        return table;
    }

    private static int readIntLE(byte[] data, int offset) {
        return (data[offset] & 0xFF)
            | ((data[offset + 1] & 0xFF) << 8)
            | ((data[offset + 2] & 0xFF) << 16)
            | ((data[offset + 3] & 0xFF) << 24);
    }

    private static void writeIntLE(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    public static String describe(Result result) {
        return result.channels + " ch, " + result.sampleRate + " Hz";
    }

    static {
        if (CRC_TABLE.length != 256) throw new IllegalStateException();
    }

    public static String utf8(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }
}
