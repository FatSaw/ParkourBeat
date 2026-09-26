package ru.sortix.parkourbeat.packrelay.slicer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Режет Ogg Vorbis без ffmpeg, средствами самой JVM.
 * <p>
 * Ogg — контейнер из страниц, у каждой в заголовке лежит granule position, то есть номер
 * последнего сэмпла в этой странице. Чтобы получить кусок трека, не нужно ничего
 * перекодировать: достаточно взять три заголовочных пакета (identification, comment,
 * setup), приклеить к ним нужный диапазон страниц с данными, пересчитать granule
 * position, номера страниц и CRC каждой изменённой страницы.
 * <p>
 * Плюсы против ffmpeg: нет внешней зависимости, нет потери качества, работа занимает
 * миллисекунды вместо десятков секунд, и нагрузка на CPU прокси практически нулевая.
 * <p>
 * Минус ровно один: рез идёт по границам страниц, а не по конкретному сэмплу. Страница
 * Vorbis — это обычно меньше 100 мс, и фактическая длительность каждого куска всё равно
 * измеряется по granule position и уезжает на бэкенд, поэтому стыки сходятся точно.
 */
public final class OggSlicer {
    public static final class Piece {
        public final byte[] data;
        public final int durationMillis;

        Piece(byte[] data, int durationMillis) {
            this.data = data;
            this.durationMillis = durationMillis;
        }
    }

    public static final class Split {
        public final List<Piece> pieces;
        public final int totalMillis;

        Split(List<Piece> pieces, int totalMillis) {
            this.pieces = pieces;
            this.totalMillis = totalMillis;
        }
    }

    private static final class Page {
        int offset;
        int length;
        int headerType;
        long granule;
        int segmentCount;
    }

    private static final int[] CRC_TABLE = buildCrcTable();

    private OggSlicer() {
    }

    public static boolean looksLikeOgg(Path file) {
        try {
            if (!Files.isRegularFile(file)) return false;
            if (Files.size(file) < 64) return false;
            byte[] head = new byte[4];
            try (var in = Files.newInputStream(file)) {
                if (in.read(head) != 4) return false;
            }
            return head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S';
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Длительность файла в миллисекундах по последней granule position.
     */
    public static int durationMillis(byte[] data) {
        List<Page> pages = readPages(data);
        if (pages.isEmpty()) throw new IllegalStateException("файл не содержит страниц Ogg");
        int rate = readSampleRate(data, pages);
        long lastGranule = 0;
        for (Page page : pages) {
            if (page.granule > lastGranule && page.granule != -1L) lastGranule = page.granule;
        }
        return (int) (lastGranule * 1000L / rate);
    }

    /**
     * Разрезать трек по отметкам в миллисекундах.
     *
     * @param offsetsMillis отметки чекпоинтов, строго по возрастанию
     * @return N+1 кусок с фактическими длительностями
     */
    public static Split split(byte[] data, List<Integer> offsetsMillis) {
        List<Page> pages = readPages(data);
        if (pages.isEmpty()) throw new IllegalStateException("файл не содержит страниц Ogg");

        int sampleRate = readSampleRate(data, pages);

        // Первые три пакета Vorbis — заголовки, и они обязаны быть в КАЖДОМ куске,
        // иначе декодер не сможет прочитать поток. Ищем страницу, на которой
        // заканчивается setup-пакет: всё до неё включительно — заголовок.
        int headerPageEnd = findHeaderPagesEnd(data, pages);
        int headerBytesEnd = pages.get(headerPageEnd).offset + pages.get(headerPageEnd).length;
        byte[] header = new byte[headerBytesEnd];
        System.arraycopy(data, 0, header, 0, headerBytesEnd);

        long totalGranule = 0;
        for (Page page : pages) {
            if (page.granule != -1L && page.granule > totalGranule) totalGranule = page.granule;
        }

        // Границы кусков в сэмплах.
        List<Long> bounds = new ArrayList<>();
        for (int offset : offsetsMillis) {
            long granule = (long) offset * sampleRate / 1000L;
            if (granule <= 0 || granule >= totalGranule) continue;
            if (!bounds.isEmpty() && granule <= bounds.get(bounds.size() - 1)) continue;
            bounds.add(granule);
        }

        // Каждой границе сопоставляем индекс первой страницы данных, чья granule
        // уже перевалила за границу: именно с неё начнётся следующий кусок.
        List<Integer> cutPages = new ArrayList<>();
        int cursor = headerPageEnd + 1;
        for (long bound : bounds) {
            int previous = cursor;
            while (cursor < pages.size()
                && (pages.get(cursor).granule == -1L || pages.get(cursor).granule < bound)) {
                if (pages.get(cursor).granule != -1L) previous = cursor;
                cursor++;
            }
            if (cursor >= pages.size() - 1) break;

            // Берём ту из двух соседних страниц, чья granule ближе к нужной отметке.
            // Если всегда брать первую страницу ЗА границей, рез систематически уезжает
            // вперёд на целую страницу — на длинных страницах это заметные доли секунды.
            int chosen = cursor;
            if (previous > headerPageEnd && previous < cursor) {
                long after = pages.get(cursor).granule;
                long before = pages.get(previous).granule;
                if (bound - before < after - bound) chosen = previous;
            }
            if (chosen <= (cutPages.isEmpty() ? headerPageEnd : cutPages.get(cutPages.size() - 1))) {
                chosen = cursor;
            }

            cutPages.add(chosen);
            cursor = Math.max(cursor, chosen + 1);
        }

        List<Piece> pieces = new ArrayList<>();
        int startPage = headerPageEnd + 1;
        long previousGranule = 0;

        for (int i = 0; i <= cutPages.size(); i++) {
            boolean last = i == cutPages.size();
            int endPage = last ? pages.size() - 1 : cutPages.get(i);

            long endGranule = 0;
            for (int p = startPage; p <= endPage; p++) {
                if (pages.get(p).granule != -1L && pages.get(p).granule > endGranule) {
                    endGranule = pages.get(p).granule;
                }
            }
            if (endGranule <= previousGranule) endGranule = previousGranule;

            byte[] piece = buildPiece(data, pages, header, startPage, endPage, previousGranule, last);
            int millis = (int) ((endGranule - previousGranule) * 1000L / sampleRate);
            pieces.add(new Piece(piece, millis));

            previousGranule = endGranule;
            startPage = endPage + 1;
        }

        return new Split(pieces, (int) (totalGranule * 1000L / sampleRate));
    }

    /**
     * Собрать один кусок: заголовки плюс диапазон страниц данных.
     * <p>
     * У страниц переписываются granule position (сдвиг на начало куска), порядковые
     * номера, флаги начала и конца потока и CRC. Без этого плеер увидит поток,
     * который начинается с середины, и откажется его играть.
     */
    private static byte[] buildPiece(byte[] data, List<Page> pages, byte[] header,
                                     int startPage, int endPage,
                                     long granuleShift, boolean last) {
        int dataLength = 0;
        for (int p = startPage; p <= endPage; p++) dataLength += pages.get(p).length;

        byte[] result = new byte[header.length + dataLength];
        System.arraycopy(header, 0, result, 0, header.length);

        int write = header.length;
        for (int p = startPage; p <= endPage; p++) {
            Page page = pages.get(p);
            System.arraycopy(data, page.offset, result, write, page.length);
            write += page.length;
        }

        // Пересчёт заголовков скопированных страниц.
        List<Page> written = readPages(result);
        int sequence = 0;
        for (int i = 0; i < written.size(); i++) {
            Page page = written.get(i);
            boolean headerPage = i < countPages(header);

            int headerType = page.headerType;
            if (i == 0) {
                headerType |= 0x02;
            } else {
                headerType &= ~0x02;
            }
            if (i == written.size() - 1 && last) {
                headerType |= 0x04;
            } else {
                headerType &= ~0x04;
            }
            result[page.offset + 5] = (byte) headerType;

            if (!headerPage && page.granule != -1L) {
                long shifted = page.granule - granuleShift;
                if (shifted < 0) shifted = 0;
                writeLongLE(result, page.offset + 6, shifted);
            }

            writeIntLE(result, page.offset + 18, sequence++);
            rewriteCrc(result, page);
        }
        return result;
    }

    private static int countPages(byte[] data) {
        return readPages(data).size();
    }

    /**
     * Индекс последней страницы, на которой ещё лежат три заголовочных пакета Vorbis.
     */
    private static int findHeaderPagesEnd(byte[] data, List<Page> pages) {
        int completedPackets = 0;
        for (int i = 0; i < pages.size(); i++) {
            Page page = pages.get(i);
            int tableOffset = page.offset + 27;
            for (int s = 0; s < page.segmentCount; s++) {
                if ((data[tableOffset + s] & 0xFF) < 255) {
                    completedPackets++;
                    if (completedPackets >= 3) return i;
                }
            }
        }
        throw new IllegalStateException("не найдены заголовочные пакеты Vorbis");
    }

    private static int readSampleRate(byte[] data, List<Page> pages) {
        Page first = pages.get(0);
        int packetStart = first.offset + 27 + first.segmentCount;
        // identification header: 1 байт типа, "vorbis", версия (4), каналы (1), частота (4)
        int rate = readIntLE(data, packetStart + 12);
        if (rate <= 0) throw new IllegalStateException("не удалось прочитать частоту дискретизации");
        return rate;
    }

    private static List<Page> readPages(byte[] data) {
        List<Page> pages = new ArrayList<>();
        int offset = 0;

        while (offset + 27 <= data.length) {
            if (data[offset] != 'O' || data[offset + 1] != 'g'
                || data[offset + 2] != 'g' || data[offset + 3] != 'S') {
                throw new IllegalStateException("нарушена структура страниц Ogg");
            }

            Page page = new Page();
            page.offset = offset;
            page.headerType = data[offset + 5] & 0xFF;
            page.granule = readLongLE(data, offset + 6);
            page.segmentCount = data[offset + 26] & 0xFF;

            int tableOffset = offset + 27;
            if (tableOffset + page.segmentCount > data.length) {
                throw new IllegalStateException("обрезанная таблица сегментов");
            }

            int payload = 0;
            for (int i = 0; i < page.segmentCount; i++) {
                payload += data[tableOffset + i] & 0xFF;
            }
            page.length = 27 + page.segmentCount + payload;

            if (page.offset + page.length > data.length) {
                throw new IllegalStateException("страница выходит за пределы файла");
            }

            pages.add(page);
            offset += page.length;
        }
        return pages;
    }

    private static void rewriteCrc(byte[] data, Page page) {
        for (int i = 0; i < 4; i++) data[page.offset + 22 + i] = 0;
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

    private static long readLongLE(byte[] data, int offset) {
        long value = 0;
        for (int i = 7; i >= 0; i--) {
            value = (value << 8) | (data[offset + i] & 0xFFL);
        }
        return value;
    }

    private static void writeIntLE(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    private static void writeLongLE(byte[] data, int offset, long value) {
        for (int i = 0; i < 8; i++) {
            data[offset + i] = (byte) ((value >>> (i * 8)) & 0xFF);
        }
    }
}
