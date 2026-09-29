package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * РАЗБОР ТРЕКА ПРЯМО НА СЕРВЕРЕ.
 * <p>
 * Файлы треков лежат рядом, в папке AMusic, поэтому спрашивать у строителя длину и темп
 * незачем - и то, и другое можно достать самому.
 * <p>
 * ДЛИНА берётся точно: у последней страницы ogg в заголовке лежит номер последнего
 * сэмпла, а частота дискретизации - в самой первой. Делим одно на другое и получаем
 * длительность до миллисекунды, ничего не декодируя.
 * <p>
 * ТЕМП берётся приблизительно, зато без декодера. Vorbis пишет с переменным битрейтом:
 * громкие места занимают больше байт, тихие меньше. Значит размеры страниц во времени -
 * это огибающая громкости трека, пусть и грубая. По ней считается поток онсетов, а по
 * нему автокорреляция: период, на котором огибающая лучше всего совпадает сама с собой,
 * и есть длина доли. На электронной музыке с чётким битом попадает уверенно, на живой
 * записи может ошибиться вдвое - поэтому результат всегда можно перебить руками.
 * <p>
 * Точный разбор через внешний скрипт (с настоящим спектром и разделением по полосам)
 * остаётся и имеет приоритет: если его файл есть, берётся он.
 */
public final class DiggerTrackAnalyzer {

    private DiggerTrackAnalyzer() {
    }

    /** Частота, к которой приводится огибающая перед поиском темпа. */
    private static final int ENVELOPE_HZ = 100;

    private static final double MIN_BPM = 70.0D;
    private static final double MAX_BPM = 200.0D;

    /** Результат разбора. */
    public static final class Result {
        public final double bpm;
        public final int durationMillis;
        public final int firstBeatMillis;
        public final @NonNull List<DiggerAnalysis.Onset> onsets;

        Result(double bpm, int durationMillis, int firstBeatMillis, @NonNull List<DiggerAnalysis.Onset> onsets) {
            this.bpm = bpm;
            this.durationMillis = durationMillis;
            this.firstBeatMillis = firstBeatMillis;
            this.onsets = onsets;
        }

        @NonNull
        public DiggerAnalysis toAnalysis(@NonNull String trackId) {
            DiggerAnalysis analysis = new DiggerAnalysis(trackId, this.bpm, this.firstBeatMillis);
            for (DiggerAnalysis.Onset onset : this.onsets) {
                analysis.addOnset(onset);
            }
            analysis.sort();
            return analysis;
        }
    }

    // ==================== ПОИСК ФАЙЛА ====================

    /**
     * Найти ogg трека в папке AMusic.
     * <p>
     * Раскладка там простая: {@code amusic/Music/<название набора>/<файл>.ogg}. Ищем
     * сначала папку с именем трека, а если её нет - любой ogg, чьё имя совпадает.
     */
    /** Куда смотрел поиск в последний раз - для команды диагностики. */
    @NonNull
    public static java.util.List<File> searchRoots(@NonNull Plugin plugin) {
        java.util.List<File> roots = new ArrayList<>();
        if (!DiggerTuning.MUSIC_FOLDER.isEmpty()) roots.add(new File(DiggerTuning.MUSIC_FOLDER));
        roots.add(new File(plugin.getDataFolder(), "amusic/Music"));
        roots.add(new File(plugin.getDataFolder(), "amusic/Packed"));
        roots.add(plugin.getDataFolder());
        return roots;
    }

    /** Сколько всего ogg и zip нашлось - помогает понять, ищем ли мы вообще там, где надо. */
    public static int countAudio(@Nullable File root, int depth) {
        if (root == null || !root.exists() || depth > 4) return 0;
        if (root.isFile()) {
            String name = root.getName().toLowerCase(Locale.ROOT);
            return name.endsWith(".ogg") || name.endsWith(".zip") ? 1 : 0;
        }
        File[] files = root.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File file : files) count += countAudio(file, depth + 1);
        return count;
    }

    @Nullable
    public static File findTrackFile(@NonNull Plugin plugin, @NonNull String trackId) {
        String needle = normalize(trackId);

        for (File root : searchRoots(plugin)) {
            File found = searchOgg(root, needle, 0);
            if (found != null) return found;
        }
        return null;
    }

    @Nullable
    private static File findTrackFileLegacy(@NonNull Plugin plugin, @NonNull String trackId) {
        String needle = normalize(trackId);

        // 1. Готовые треки AMusic: amusic/Music/<набор>/<файл>.ogg
        File found = searchOgg(new File(plugin.getDataFolder(), "amusic/Music"), needle, 0);
        if (found != null) return found;

        // 2. Собранные ресурспаки: amusic/Packed/<набор>.zip - ogg лежит внутри архива.
        found = searchOgg(new File(plugin.getDataFolder(), "amusic/Packed"), needle, 0);
        if (found != null) return found;

        // 3. Последняя попытка - весь каталог плагина: раскладка могла быть изменена
        // руками, а искать один файл дешевле, чем требовать его от строителя.
        return searchOgg(plugin.getDataFolder(), needle, 0);
    }

    /** Имя для сравнения: регистр и разделители значения не имеют. */
    @NonNull
    private static String normalize(@NonNull String value) {
        StringBuilder result = new StringBuilder();
        for (char c : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) result.append(c);
        }
        return result.toString();
    }

    /**
     * Рекурсивный поиск ogg или архива с ним.
     * <p>
     * Совпадение по имени - предпочтительное, но не обязательное: если в каталоге
     * ровно один подходящий файл, берётся он. Требовать точного имени нельзя, потому
     * что набор на диске может называться иначе, чем трек в настройках уровня.
     */
    @Nullable
    private static File searchOgg(@Nullable File root, @NonNull String needle, int depth) {
        if (root == null || !root.exists() || depth > 4) return null;

        if (root.isFile()) {
            String name = root.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".ogg")) return root;
            if (name.endsWith(".zip") && matches(normalize(root.getName()), needle)) return root;
            return null;
        }

        File[] files = root.listFiles();
        if (files == null) return null;

        // Сначала то, что похоже на имя трека.
        for (File file : files) {
            if (!matches(normalize(file.getName()), needle)) continue;
            File result = searchOgg(file, needle, depth + 1);
            if (result != null) return result;
        }
        for (File file : files) {
            File result = searchOgg(file, needle, depth + 1);
            if (result != null) return result;
        }
        return null;
    }

    private static boolean matches(@NonNull String name, @NonNull String needle) {
        if (needle.isEmpty()) return false;
        String plain = name.endsWith("zip") ? name.substring(0, name.length() - 3) : name;
        return plain.contains(needle) || needle.contains(plain);
    }

    // ==================== РАЗБОР ====================

    /**
     * Прочитать файл целиком и вытащить длину, темп и удары.
     * <p>
     * Вызывать ТОЛЬКО асинхронно: чтение мегабайтного файла в игровом такте недопустимо.
     */
    @Nullable
    public static Result analyze(@NonNull File file) {
        if (!file.isFile()) return null;

        // Архив ресурспака: ogg лежит внутри, распаковывать целиком незачем.
        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file)) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    java.util.zip.ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    if (!entry.getName().toLowerCase(Locale.ROOT).endsWith(".ogg")) continue;

                    try (java.io.InputStream in = zip.getInputStream(entry)) {
                        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                        byte[] chunk = new byte[8192];
                        int read;
                        while ((read = in.read(chunk)) > 0) buffer.write(chunk, 0, read);
                        return analyze(buffer.toByteArray());
                    }
                }
            } catch (IOException e) {
                return null;
            }
            return null;
        }

        try {
            byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
            return analyze(data);
        } catch (IOException e) {
            return null;
        }
    }

    /** Тот же разбор, но по уже прочитанным байтам. */
    @Nullable
    public static Result analyze(byte[] bytes) {
        if (bytes == null || bytes.length < 64) return null;

        List<Double> times = new ArrayList<>();
        List<Double> sizes = new ArrayList<>();
        int sampleRate = 0;
        long lastGranule = 0L;

        int position = 0;
        while (position + 27 < bytes.length) {
            if (bytes[position] != 'O' || bytes[position + 1] != 'g'
                || bytes[position + 2] != 'g' || bytes[position + 3] != 'S') {
                position++;
                continue;
            }

            long granule = readLong(bytes, position + 6);
            int segments = bytes[position + 26] & 0xFF;
            int tableStart = position + 27;
            if (tableStart + segments > bytes.length) break;

            int dataSize = 0;
            for (int i = 0; i < segments; i++) dataSize += bytes[tableStart + i] & 0xFF;

            int dataStart = tableStart + segments;
            if (dataStart + dataSize > bytes.length) break;

            if (sampleRate == 0) {
                sampleRate = readSampleRate(bytes, dataStart, dataSize);
            }
            if (granule > 0) lastGranule = granule;

            if (sampleRate > 0 && granule > 0) {
                times.add(granule / (double) sampleRate);
                sizes.add((double) dataSize);
            }

            position = dataStart + dataSize;
        }

        if (sampleRate <= 0 || lastGranule <= 0 || times.size() < 16) return null;

        int durationMillis = (int) Math.round(lastGranule * 1000.0D / sampleRate);
        double[] envelope = resample(times, sizes, durationMillis);
        double[] flux = flux(envelope);

        double bpm = estimateBpm(flux);
        List<DiggerAnalysis.Onset> onsets = peaks(flux);
        int firstBeat = onsets.isEmpty() ? 0 : onsets.get(0).getMillis();

        return new Result(bpm, durationMillis, firstBeat, onsets);
    }

    private static long readLong(byte[] data, int offset) {
        long value = 0L;
        for (int i = 7; i >= 0; i--) {
            value = (value << 8) | (data[offset + i] & 0xFFL);
        }
        return value;
    }

    /**
     * Частота дискретизации из опознавательного заголовка Vorbis.
     * Пакет начинается с байта 0x01 и слова "vorbis", дальше версия, каналы и частота.
     */
    private static int readSampleRate(byte[] data, int offset, int length) {
        if (length < 16 || offset + 16 > data.length) return 0;
        if ((data[offset] & 0xFF) != 0x01) return 0;
        if (data[offset + 1] != 'v' || data[offset + 2] != 'o' || data[offset + 3] != 'r'
            || data[offset + 4] != 'b' || data[offset + 5] != 'i' || data[offset + 6] != 's') return 0;

        return (data[offset + 12] & 0xFF)
            | ((data[offset + 13] & 0xFF) << 8)
            | ((data[offset + 14] & 0xFF) << 16)
            | ((data[offset + 15] & 0xFF) << 24);
    }

    /** Страницы идут неравномерно, поэтому огибающую приводим к ровной сетке. */
    private static double[] resample(@NonNull List<Double> times, @NonNull List<Double> sizes, int durationMillis) {
        int frames = Math.max(16, durationMillis * ENVELOPE_HZ / 1000);
        double[] result = new double[frames];
        double[] counts = new double[frames];

        for (int i = 0; i < times.size(); i++) {
            int frame = (int) Math.floor(times.get(i) * ENVELOPE_HZ);
            if (frame < 0 || frame >= frames) continue;
            result[frame] += sizes.get(i);
            counts[frame]++;
        }

        double last = 0.0D;
        for (int i = 0; i < frames; i++) {
            if (counts[i] > 0) {
                result[i] /= counts[i];
                last = result[i];
            } else {
                result[i] = last;
            }
        }
        return result;
    }

    /** Поток онсетов: интересен только рост громкости, спад ритма не задаёт. */
    private static double[] flux(double[] envelope) {
        double[] result = new double[envelope.length];
        for (int i = 1; i < envelope.length; i++) {
            result[i] = Math.max(0.0D, envelope[i] - envelope[i - 1]);
        }

        double max = 0.0D;
        for (double value : result) max = Math.max(max, value);
        if (max <= 0.0D) return result;
        for (int i = 0; i < result.length; i++) result[i] /= max;
        return result;
    }

    /** Автокорреляция: ищем период, на котором поток онсетов лучше всего повторяется. */
    private static double estimateBpm(double[] flux) {
        int minLag = (int) Math.floor(60.0D * ENVELOPE_HZ / MAX_BPM);
        int maxLag = (int) Math.ceil(60.0D * ENVELOPE_HZ / MIN_BPM);
        if (maxLag >= flux.length / 2) maxLag = flux.length / 2 - 1;
        if (minLag >= maxLag) return DiggerTuning.DEFAULT_BPM;

        double bestScore = -1.0D;
        int bestLag = minLag;

        for (int lag = minLag; lag <= maxLag; lag++) {
            double score = 0.0D;
            for (int i = lag; i < flux.length; i++) {
                score += flux[i] * flux[i - lag];
            }
            score /= (flux.length - lag);

            if (score > bestScore) {
                bestScore = score;
                bestLag = lag;
            }
        }

        double bpm = 60.0D * ENVELOPE_HZ / bestLag;

        // Половинный и двойной темп дают почти такую же автокорреляцию, поэтому
        // результат подтягивается в привычный диапазон танцевальной музыки.
        while (bpm < 90.0D) bpm *= 2.0D;
        while (bpm > 180.0D) bpm /= 2.0D;
        return bpm;
    }

    /** Удары: локальные максимумы потока выше скользящего порога. */
    @NonNull
    private static List<DiggerAnalysis.Onset> peaks(double[] flux) {
        List<DiggerAnalysis.Onset> result = new ArrayList<>();
        int window = ENVELOPE_HZ / 2;

        for (int i = 1; i < flux.length - 1; i++) {
            if (flux[i] <= flux[i - 1] || flux[i] < flux[i + 1]) continue;

            double sum = 0.0D;
            int count = 0;
            for (int j = Math.max(0, i - window); j < Math.min(flux.length, i + window); j++) {
                sum += flux[j];
                count++;
            }
            double threshold = (sum / Math.max(1, count)) * 1.5D + 0.02D;
            if (flux[i] < threshold) continue;

            int millis = i * 1000 / ENVELOPE_HZ;
            // Полосу без спектра не определить, поэтому сильные удары считаем низом,
            // остальные серединой: на раскладку по рядам этого хватает.
            DiggerAnalysis.Band band = flux[i] > 0.55D
                ? DiggerAnalysis.Band.LOW
                : DiggerAnalysis.Band.MID;
            result.add(new DiggerAnalysis.Onset(millis, Math.min(1.0D, flux[i]), band));
        }
        return result;
    }
}
