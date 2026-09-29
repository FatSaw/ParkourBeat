package ru.sortix.parkourbeat.packrelay.slicer;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Режет ogg-трек на куски по отметкам чекпоинтов уровня.
 * <p>
 * Живёт на прокси, потому что сами файлы лежат здесь: {@code <music>/<trackId>/track.ogg}.
 * Результат кладётся в ОТДЕЛЬНЫЙ плейлист {@code <music>/<playlistId>/part1.ogg ...}, а не
 * поверх исходника: один трек может стоять на десятке уровней с разными чекпоинтами.
 * <p>
 * Куски перекодируются, а не копируются потоком. {@code -c copy} режет ogg только по
 * границам страниц и промахивается мимо отметки на десятые доли секунды — на стыке это
 * слышно. Перекодирование даёт точный рез, а фактическая длительность каждого куска
 * измеряется уже по готовому файлу и уезжает на бэкенд: именно по ней заводится
 * следующий кусок.
 */
public final class TrackSlicer {
    /** Больше пяти чекпоинтов бэкенд и не пришлёт, но проверить дешевле, чем чинить. */
    public static final int MAX_CHECKPOINTS = 5;

    public static final class Result {
        public final boolean success;
        public final String error;
        public final List<Integer> offsetsMillis;
        public final List<Integer> durationsMillis;

        private Result(boolean success, String error,
                       List<Integer> offsetsMillis, List<Integer> durationsMillis) {
            this.success = success;
            this.error = error;
            this.offsetsMillis = offsetsMillis;
            this.durationsMillis = durationsMillis;
        }

        static Result ok(List<Integer> offsets, List<Integer> durations) {
            return new Result(true, null, offsets, durations);
        }

        static Result fail(String error) {
            return new Result(false, error, List.of(), List.of());
        }
    }

    /**
     * Имя папки трека или плейлиста. Ровно тот же набор символов, что разрешает
     * загрузчик треков: буквы, цифры, пробел, дефис и подчёркивание. Ни точек, ни
     * разделителей пути — обход каталога невозможен по построению.
     */
    private static final java.util.regex.Pattern SAFE_ID =
        java.util.regex.Pattern.compile("^[\\p{L}\\p{N} _-]{1,80}$");

    /**
     * Плейлист нарезки ОБЯЗАН оканчиваться на {@code __cp<число>}.
     * <p>
     * Это не косметика, а защита от потери данных: загрузчик разрешает подчёркивания в
     * названиях, поэтому игрок может залить трек с именем вроде {@code song__cp3}. Без
     * этой проверки плюс проверки на {@code track.ogg} ниже нарезка затёрла бы чужой
     * загруженный трек вместе с файлом.
     */
    private static final java.util.regex.Pattern SLICE_PLAYLIST =
        java.util.regex.Pattern.compile("^.+__cp\\d{1,9}$");

    private final Logger logger;
    private final Path musicDirectory;
    private final String ffmpeg;
    private final String ffprobe;
    private final int quality;

    public TrackSlicer(Logger logger, Path musicDirectory, String ffmpeg, String ffprobe, int quality) {
        this.logger = logger;
        this.musicDirectory = musicDirectory;
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.quality = Math.max(0, Math.min(10, quality));
    }

    /**
     * Расширения, которые считаем звуком. Имя файла в папке трека НЕ обязано быть
     * track.ogg: у части треков это mytrack.ogg, test.mp3 и так далее.
     */
    private static final List<String> AUDIO_EXTENSIONS =
        List.of(".ogg", ".mp3", ".wav", ".flac", ".m4a", ".aac", ".opus");

    private static boolean isAudioFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : AUDIO_EXTENSIONS) {
            if (name.endsWith(extension)) return true;
        }
        return false;
    }

    private static boolean isSlicePiece(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).matches("^part\\d+\\.ogg$");
    }

    /**
     * Найти исходный аудиофайл в папке трека.
     * <p>
     * Приоритет у track.ogg, дальше — любой другой звуковой файл. Если в папке лежит
     * несколько файлов, берём самый большой: это почти наверняка сама песня, а не
     * случайный короткий семпл рядом с ней.
     */
    private Path findSourceFile(Path folder) {
        if (!Files.isDirectory(folder)) return null;

        Path preferred = folder.resolve("track.ogg");
        if (Files.isRegularFile(preferred)) return preferred;

        Path best = null;
        long bestSize = -1L;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path path : files.toList()) {
                if (!Files.isRegularFile(path)) continue;
                if (!isAudioFile(path)) continue;
                if (isSlicePiece(path)) continue;
                long size = Files.size(path);
                if (size > bestSize) {
                    bestSize = size;
                    best = path;
                }
            }
        } catch (IOException e) {
            this.logger.warn("Unable to list track folder {}", folder, e);
            return null;
        }
        return best;
    }

    private static boolean isSafeId(String id) {
        if (id == null || id.isEmpty()) return false;
        if (id.contains("..") || id.contains("/") || id.contains("\\")) return false;
        if (!id.equals(id.trim())) return false;
        return SAFE_ID.matcher(id).matches();
    }

    /**
     * Итоговый путь обязан лежать внутри музыкальной папки. Проверяется уже после
     * нормализации: одной регулярки мало, если где-то поменяется способ склейки пути.
     */
    private boolean isInsideMusicDirectory(Path path) {
        Path root = this.musicDirectory.toAbsolutePath().normalize();
        Path target = path.toAbsolutePath().normalize();
        return target.startsWith(root) && !target.equals(root);
    }

    /**
     * Папка нарезки должна быть либо пустой/несуществующей, либо ранее созданной нами.
     * Наличие {@code track.ogg} означает загруженный трек живого пользователя — такую
     * папку не трогаем ни при каких условиях.
     */
    private boolean isSliceDirectorySafe(Path target) {
        if (!Files.exists(target)) return true;
        if (!Files.isDirectory(target)) return false;

        // Внутри допустимы только наши куски partN.ogg. Любой другой файл означает,
        // что папку занял загруженный трек живого пользователя, и трогать её нельзя:
        // имена треков разрешают подчёркивания, так что совпадение вполне возможно.
        try (Stream<Path> files = Files.list(target)) {
            return files.filter(Files::isRegularFile).allMatch(TrackSlicer::isSlicePiece);
        } catch (IOException e) {
            this.logger.warn("Unable to inspect slice folder {}", target, e);
            return false;
        }
    }

    private String validate(String sourceTrackId, String playlistId) {
        if (!isSafeId(sourceTrackId)) return "недопустимое имя трека";
        if (!isSafeId(playlistId)) return "недопустимое имя плейлиста нарезки";
        if (!SLICE_PLAYLIST.matcher(playlistId).matches()) {
            return "имя плейлиста нарезки должно оканчиваться на __cp<номер>";
        }
        if (sourceTrackId.equals(playlistId)) return "нарезка не может затирать исходный трек";

        Path source = this.musicDirectory.resolve(sourceTrackId);
        Path target = this.musicDirectory.resolve(playlistId);
        if (!this.isInsideMusicDirectory(source) || !this.isInsideMusicDirectory(target)) {
            return "путь выходит за пределы музыкальной папки";
        }
        if (!this.isSliceDirectorySafe(target)) {
            return "папка " + playlistId + " занята загруженным треком";
        }
        return null;
    }

    /**
     * Нарезка работает всегда: для ogg используется встроенный делитель, ffmpeg нужен
     * только для экзотических форматов вроде mp3.
     */
    public boolean isAvailable() {
        return true;
    }

    /**
     * @return true, если ffmpeg и ffprobe есть на машине. Проверка тихая: отсутствие
     * ffmpeg — это норма, а не ошибка.
     */
    public boolean isFfmpegAvailable() {
        return runQuiet(List.of(this.ffmpeg, "-version"), 10) != null
            && runQuiet(List.of(this.ffprobe, "-version"), 10) != null;
    }

    public Result slice(String sourceTrackId, String playlistId, List<Integer> rawOffsets) {
        String problem = this.validate(sourceTrackId, playlistId);
        if (problem != null) {
            this.logger.warn("Rejected slice request {} -> {}: {}", sourceTrackId, playlistId, problem);
            return Result.fail(problem);
        }
        if (rawOffsets == null || rawOffsets.isEmpty() || rawOffsets.size() > MAX_CHECKPOINTS) {
            return Result.fail("недопустимое количество отметок");
        }

        Path folder = this.musicDirectory.resolve(sourceTrackId);
        Path source = this.findSourceFile(folder);
        if (source == null) {
            return Result.fail("в папке " + sourceTrackId + " не найдено ни одного аудиофайла");
        }
        this.logger.info("Slicing source file {}", source.getFileName());

        // ОСНОВНОЙ ПУТЬ: ogg делится прямо в JVM, без внешних программ и перекодирования.
        if (OggSlicer.looksLikeOgg(source)) {
            return this.sliceOgg(source, playlistId, rawOffsets);
        }

        // Всё остальное (mp3, wav и прочее) без ffmpeg разобрать нечем.
        if (!this.isFfmpegAvailable()) {
            return Result.fail("файл " + source.getFileName() + " не в формате ogg,"
                + " для него нужен ffmpeg на прокси");
        }

        long totalMillis = this.probeDurationMillis(source);
        if (totalMillis <= 0) {
            return Result.fail("не удалось определить длительность трека");
        }

        List<Integer> offsets = normalizeOffsets(rawOffsets, totalMillis);
        if (offsets.isEmpty()) {
            return Result.fail("после проверки не осталось ни одной корректной отметки");
        }

        Path target = this.musicDirectory.resolve(playlistId);
        try {
            deleteDirectory(target);
            Files.createDirectories(target);
        } catch (IOException e) {
            return Result.fail("не удалось подготовить папку нарезки: " + e.getMessage());
        }

        List<Integer> durations = new ArrayList<>();
        int previous = 0;

        for (int i = 0; i <= offsets.size(); i++) {
            boolean last = i == offsets.size();
            int end = last ? (int) totalMillis : offsets.get(i);
            Path piece = target.resolve("part" + (i + 1) + ".ogg");

            if (!this.cut(source, piece, previous, last ? -1 : end)) {
                safeDelete(target);
                return Result.fail("ffmpeg не смог вырезать кусок " + (i + 1));
            }

            long measured = this.probeDurationMillis(piece);
            if (measured <= 0) {
                safeDelete(target);
                return Result.fail("кусок " + (i + 1) + " получился пустым");
            }
            durations.add((int) measured);
            previous = end;
        }

        this.logger.info("Track {} sliced into {} piece(s) as playlist {}",
            sourceTrackId, durations.size(), playlistId);
        return Result.ok(offsets, durations);
    }

    /**
     * Нарезка ogg встроенным делителем: читаем файл, режем по страницам, пишем куски.
     */
    private Result sliceOgg(Path source, String playlistId, List<Integer> rawOffsets) {
        byte[] data;
        try {
            data = Files.readAllBytes(source);
        } catch (IOException e) {
            return Result.fail("не удалось прочитать файл: " + e.getMessage());
        }

        int totalMillis;
        OggSlicer.Split split;
        try {
            totalMillis = OggSlicer.durationMillis(data);
            List<Integer> offsets = normalizeOffsets(rawOffsets, totalMillis);
            if (offsets.isEmpty()) {
                return Result.fail("после проверки не осталось ни одной корректной отметки");
            }
            split = OggSlicer.split(data, offsets);
        } catch (RuntimeException e) {
            this.logger.warn("Unable to split ogg {}", source, e);
            return Result.fail("файл повреждён или это не Vorbis: " + e.getMessage());
        }

        if (split.pieces.size() < 2) {
            return Result.fail("не удалось разделить трек на куски");
        }

        Path target = this.musicDirectory.resolve(playlistId);
        try {
            deleteDirectory(target);
            Files.createDirectories(target);
            for (int i = 0; i < split.pieces.size(); i++) {
                Files.write(target.resolve("part" + (i + 1) + ".ogg"), split.pieces.get(i).data);
            }
        } catch (IOException e) {
            safeDelete(target);
            return Result.fail("не удалось записать куски: " + e.getMessage());
        }

        List<Integer> durations = new ArrayList<>();
        List<Integer> actualOffsets = new ArrayList<>();
        int accumulated = 0;
        for (int i = 0; i < split.pieces.size(); i++) {
            int duration = split.pieces.get(i).durationMillis;
            if (duration <= 0) {
                safeDelete(target);
                return Result.fail("кусок " + (i + 1) + " получился пустым");
            }
            durations.add(duration);
            accumulated += duration;
            if (i < split.pieces.size() - 1) actualOffsets.add(accumulated);
        }

        this.logger.info("Track {} split into {} piece(s) as playlist {} (built-in ogg splitter)",
            source.getFileName(), durations.size(), playlistId);
        return Result.ok(actualOffsets, durations);
    }

    /**
     * Убрать нарезку уровня: чекпоинты сняли, файлы больше не нужны.
     */
    public boolean drop(String playlistId) {
        // Удаление папки — самая опасная операция во всём плагине. Проверки те же, что
        // при нарезке: без них присланная строка вида "../.." снесла бы половину сервера.
        if (!isSafeId(playlistId) || !SLICE_PLAYLIST.matcher(playlistId).matches()) {
            this.logger.warn("Rejected drop request for unsafe playlist id: {}", playlistId);
            return false;
        }
        Path target = this.musicDirectory.resolve(playlistId);
        if (!this.isInsideMusicDirectory(target)) {
            this.logger.warn("Rejected drop request outside music directory: {}", playlistId);
            return false;
        }
        if (!this.isSliceDirectorySafe(target)) {
            this.logger.warn("Refused to drop {}: directory holds an uploaded track", playlistId);
            return false;
        }
        if (!Files.isDirectory(target)) return true;
        try {
            deleteDirectory(target);
            this.logger.info("Sliced playlist {} removed", playlistId);
            return true;
        } catch (IOException e) {
            this.logger.warn("Unable to remove sliced playlist {}", playlistId, e);
            return false;
        }
    }

    /**
     * Отметки должны идти строго по возрастанию, помещаться в трек и не сливаться
     * в нулевые куски. Мусор отбрасывается молча: строитель мог поставить два чекпоинта
     * почти рядом, ронять из-за этого всю нарезку незачем.
     */
    private static List<Integer> normalizeOffsets(List<Integer> rawOffsets, long totalMillis) {
        List<Integer> sorted = new ArrayList<>(rawOffsets);
        sorted.sort(Comparator.naturalOrder());

        List<Integer> result = new ArrayList<>();
        int previous = 0;
        for (int offset : sorted) {
            if (offset <= previous + 1000) continue;
            if (offset >= totalMillis - 1000) continue;
            result.add(offset);
            previous = offset;
            if (result.size() >= MAX_CHECKPOINTS) break;
        }
        return result;
    }

    /**
     * @param endMillis -1 — резать до конца файла
     */
    private boolean cut(Path source, Path target, int startMillis, int endMillis) {
        List<String> command = new ArrayList<>();
        command.add(this.ffmpeg);
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.add("-y");
        // -ss ПОСЛЕ -i: медленнее, зато точный рез по сэмплу, а не по ближайшей странице
        command.add("-i");
        command.add(source.toAbsolutePath().toString());
        command.add("-ss");
        command.add(formatSeconds(startMillis));
        if (endMillis >= 0) {
            command.add("-to");
            command.add(formatSeconds(endMillis));
        }
        command.add("-vn");
        command.add("-c:a");
        command.add("libvorbis");
        command.add("-q:a");
        command.add(String.valueOf(this.quality));
        command.add(target.toAbsolutePath().toString());

        String output = run(command, 300);
        if (output == null) return false;
        return Files.isRegularFile(target);
    }

    private long probeDurationMillis(Path file) {
        String output = run(List.of(
            this.ffprobe,
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            file.toAbsolutePath().toString()), 60);

        if (output == null) return -1L;
        try {
            double seconds = Double.parseDouble(output.trim());
            return Math.round(seconds * 1000.0D);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * Как {@link #run}, но отсутствие программы не считается поводом для стектрейса:
     * ffmpeg на прокси теперь необязателен.
     */
    private String runQuiet(List<String> command, int timeoutSeconds) {
        try {
            return this.run(command, timeoutSeconds);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * @return вывод процесса, либо null если процесс упал, не нашёлся или завис
     */
    private String run(List<String> command, int timeoutSeconds) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();

            // Вывод читается ОТДЕЛЬНЫМ потоком. Если читать его здесь, то зависший
            // ffmpeg, не закрывший stdout, заблокировал бы поток на readLine навсегда,
            // и таймаут ниже никогда бы не сработал: нарезчик вставал бы намертво
            // до перезапуска прокси.
            StringBuilder output = new StringBuilder();
            final Process started = process;
            Thread drain = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (output) {
                            if (output.length() < 8192) output.append(line).append('\n');
                        }
                    }
                } catch (IOException ignored) {
                }
            }, "ParkourBeat-TrackSlicer-IO");
            drain.setDaemon(true);
            drain.start();

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                drain.interrupt();
                this.logger.warn("Command timed out: {}", command.get(0));
                return null;
            }
            drain.join(TimeUnit.SECONDS.toMillis(5));
            String text;
            synchronized (output) {
                text = output.toString();
            }
            if (process.exitValue() != 0) {
                this.logger.warn("Command {} failed: {}", command.get(0), text.trim());
                return null;
            }
            return text;
        } catch (IOException e) {
            if (process != null) process.destroyForcibly();
            // Отсутствие ffmpeg — штатная ситуация, стектрейс тут только пугает.
            this.logger.info("{} is not available: {}", command.get(0), e.getMessage());
            return null;
        } catch (InterruptedException e) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String formatSeconds(int millis) {
        return String.format(Locale.ROOT, "%d.%03d", millis / 1000, millis % 1000);
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private static void safeDelete(Path directory) {
        try {
            deleteDirectory(directory);
        } catch (IOException ignored) {
        }
    }
}
