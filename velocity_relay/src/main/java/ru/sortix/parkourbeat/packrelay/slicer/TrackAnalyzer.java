package ru.sortix.parkourbeat.packrelay.slicer;

import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * РАЗБОР ТРЕКА: ДЛИНА, ТЕМП, ФАЗА ДОЛИ И УДАРЫ.
 * <p>
 * Живёт на прокси, потому что музыка лежит здесь же, а на игровом сервере её нет вовсе.
 * Здесь же есть ffmpeg, а значит можно не гадать по размерам страниц ogg, а честно
 * раскодировать звук и посмотреть на спектр.
 * <p>
 * ЧТО ЗДЕСЬ ПРОИСХОДИТ И ПОЧЕМУ ИМЕННО ТАК:
 * <ol>
 *     <li>ffmpeg разворачивает песню в моно 22050 Гц, 16 бит, прямо в поток.</li>
 *     <li>Окнами по 1024 отсчёта считается спектр. Громкость сжимается логарифмом:
 *         ухо слышит именно так, и без этого тихий куплет не даёт ни одного удара,
 *         а дроп даёт сплошную стену.</li>
 *     <li>Поток онсетов считается отдельно по трём полосам: бочка и бас, середина,
 *         тарелки. Из каждой полосы вычитается СКОЛЬЗЯЩАЯ МЕДИАНА - именно она
 *         отделяет удар от общего роста громкости в припеве.</li>
 *     <li>Темп ищется автокорреляцией с приором вокруг 125 BPM, чтобы не улететь
 *         в половинный или двойной.</li>
 *     <li>ФАЗА ДОЛИ ищется отдельно: перебором всех смещений внутри одной доли
 *         выбирается то, на котором сетка лучше всего ложится на реальные удары.
 *         Без этого шага «первой долей» считался первый попавшийся онсет, то есть
 *         буквально первый шорох в записи.</li>
 *     <li>Удары ПРИТЯГИВАЮТСЯ К СЕТКЕ шестнадцатых, и всё, что от неё далеко,
 *         выбрасывается. Это и есть разница между «ноты примерно под музыку» и
 *         «ноты в ритм».</li>
 *     <li>Сила удара считается не по абсолютной громкости, а по РАНГУ среди всех
 *         ударов трека, и домножается на вес метрической позиции: первая доля такта
 *         весит больше шестнадцатой. Поэтому акценты попадают на сильные доли, а не
 *         туда, где сведение случайно оказалось погромче.</li>
 * </ol>
 * Без ffmpeg остаётся разбор по заголовкам ogg - грубее, но лучше, чем ничего.
 */
public final class TrackAnalyzer {

    /** Частота, к которой сводится звук. Выше 11 кГц ритму взяться неоткуда. */
    private static final int SAMPLE_RATE = 22050;

    /** Размер окна анализа и шаг между окнами. 512 отсчётов - это 43 кадра в секунду. */
    private static final int WINDOW = 1024;
    private static final int HOP = 512;

    /** Границы полос в герцах. */
    private static final double LOW_HZ = 250.0D;
    private static final double MID_HZ = 2000.0D;

    private static final double MIN_BPM = 70.0D;
    private static final double MAX_BPM = 200.0D;

    /**
     * Вокруг какого темпа искать в первую очередь.
     * <p>
     * Автокорреляция физически не может отличить темп от его половины и удвоения:
     * на 117 BPM она одинаково хорошо совпадает и на 58.5, и на 235. Приор вокруг
     * танцевального диапазона решает эту неоднозначность так же, как её решает
     * человек - выбирая ту скорость, под которую естественно топать ногой.
     */
    private static final double TEMPO_PRIOR_BPM = 125.0D;
    /** Ширина приора в октавах темпа. Одна октава - это ровно вдвое. */
    private static final double TEMPO_PRIOR_OCTAVES = 0.9D;

    /** Дробление доли, к которому притягиваются удары. 4 - шестнадцатые. */
    private static final int GRID_DIVISION = 4;

    /**
     * Насколько далеко от узла сетки удар ещё считается своим, в долях шага сетки.
     * <p>
     * 0.40 - это примерно 50 мс при 117 BPM. Живая игра и лёгкий свинг в это
     * укладываются, а случайный щелчок ровно между шестнадцатыми - уже нет.
     */
    private static final double GRID_TOLERANCE = 0.40D;

    /**
     * ЗАДЕРЖКА АНАЛИЗА, В КАДРАХ.
     * <p>
     * Окно вдвое длиннее шага, поэтому удар впервые попадает в спектр за два кадра до
     * того кадра, чьё время мы бы ему приписали: получается систематическое опережение
     * ровно на длину окна, около 46 мс. Постоянное для всего трека, поэтому просто
     * прибавляется обратно при переводе кадров в миллисекунды.
     * <p>
     * Сама сетка при этом считается по НЕИСПРАВЛЕННЫМ кадрам - иначе поправка вошла бы
     * дважды. Здесь она добавляется только на выходе.
     */
    private static final double LATENCY_FRAMES = (double) WINDOW / HOP;

    /** Потолок числа ударов в ответе: пакет плагин-сообщения не резиновый. */
    public static final int MAX_ONSETS = 1500;

    /** Один удар в треке. */
    public static final class Onset {
        public final int millis;
        /** Сила удара, 0..1. */
        public final double strength;
        /** 0 - низ, 1 - середина, 2 - верх. */
        public final int band;

        Onset(int millis, double strength, int band) {
            this.millis = millis;
            this.strength = strength;
            this.band = band;
        }
    }

    public static final class Result {
        public final boolean success;
        public final String error;
        public final int durationMillis;
        public final double bpm;
        public final int firstBeatMillis;
        public final List<Onset> onsets;

        private Result(boolean success, String error, int durationMillis, double bpm,
                       int firstBeatMillis, List<Onset> onsets) {
            this.success = success;
            this.error = error;
            this.durationMillis = durationMillis;
            this.bpm = bpm;
            this.firstBeatMillis = firstBeatMillis;
            this.onsets = onsets;
        }

        static Result fail(String error) {
            return new Result(false, error, 0, 0.0D, 0, Collections.emptyList());
        }

        static Result ok(int durationMillis, double bpm, int firstBeatMillis, List<Onset> onsets) {
            return new Result(true, null, durationMillis, bpm, firstBeatMillis, onsets);
        }
    }

    /** Сырой пик потока до притягивания к сетке. */
    private static final class Peak {
        final int frame;
        final double raw;
        final int band;

        Peak(int frame, double raw, int band) {
            this.frame = frame;
            this.raw = raw;
            this.band = band;
        }
    }

    private final Logger logger;
    private final Path musicDirectory;
    private final String ffmpeg;

    public TrackAnalyzer(Logger logger, Path musicDirectory, String ffmpeg) {
        this.logger = logger;
        this.musicDirectory = musicDirectory;
        this.ffmpeg = resolveFfmpeg(logger, ffmpeg);
    }

    /**
     * НАЙТИ FFMPEG И СДЕЛАТЬ ЕГО ЗАПУСКАЕМЫМ.
     * <p>
     * На игровых контейнерах root-доступа нет, ставить пакеты некуда, и админ просто
     * кладёт готовый бинарник рядом с плагином. Панель при этом почти всегда теряет
     * флаг запуска, а поставить его через файловый менеджер нельзя - поэтому флаг
     * ставится здесь сам.
     */
    private static String resolveFfmpeg(Logger logger, String configured) {
        List<String> candidates = new ArrayList<>();
        if (configured != null && !configured.isBlank()) candidates.add(configured.trim());
        candidates.add("plugins/parkourbeatpackrelay/bin/ffmpeg");
        candidates.add("plugins/parkourbeatpackrelay/ffmpeg");
        candidates.add("bin/ffmpeg");
        candidates.add("ffmpeg");

        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (!Files.isRegularFile(path)) continue;

            java.io.File file = path.toFile();
            if (!file.canExecute() && file.setExecutable(true)) {
                logger.info("ffmpeg найден по пути {}, выставлен флаг запуска", path);
            }
            return path.toAbsolutePath().toString();
        }

        return configured == null || configured.isBlank() ? "ffmpeg" : configured.trim();
    }

    // ==================== ФАЙЛ ====================

    private Path findSource(String trackId) {
        Path folder = this.musicDirectory.resolve(trackId);
        if (!Files.isDirectory(folder)) return null;

        Path preferred = folder.resolve("track.ogg");
        if (Files.isRegularFile(preferred)) return preferred;

        try (Stream<Path> files = Files.list(folder)) {
            Path best = null;
            long bestSize = -1L;
            for (Path path : files.toList()) {
                if (!Files.isRegularFile(path)) continue;
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".ogg") && !name.endsWith(".mp3") && !name.endsWith(".wav")) continue;

                long size = Files.size(path);
                if (size > bestSize) {
                    bestSize = size;
                    best = path;
                }
            }
            return best;
        } catch (IOException e) {
            return null;
        }
    }

    // ==================== РАЗБОР ====================

    public Result analyze(String trackId) {
        Path source = this.findSource(trackId);
        if (source == null) return Result.fail("трек не найден: " + trackId);

        short[] samples = null;
        try {
            samples = this.decode(source);
        } catch (Exception e) {
            this.logger.info("ffmpeg недоступен ({}), разбираю трек по заголовкам ogg",
                e.getMessage());
        }

        if (samples == null || samples.length < SAMPLE_RATE) {
            return this.analyzeWithoutFfmpeg(source);
        }

        int durationMillis = (int) (samples.length * 1000L / SAMPLE_RATE);

        int frames = (samples.length - WINDOW) / HOP;
        if (frames < 32) return Result.fail("трек слишком короткий для разбора");

        double framesPerSecond = SAMPLE_RATE / (double) HOP;

        // ---------- 1. СПЕКТРАЛЬНЫЙ ПОТОК ПО ТРЁМ ПОЛОСАМ ----------

        double[][] flux = new double[3][frames];
        double[] previous = null;

        double[] window = hann();
        double[] re = new double[WINDOW];
        double[] im = new double[WINDOW];

        int lowBin = (int) Math.floor(LOW_HZ * WINDOW / SAMPLE_RATE);
        int midBin = (int) Math.floor(MID_HZ * WINDOW / SAMPLE_RATE);

        for (int frame = 0; frame < frames; frame++) {
            int offset = frame * HOP;
            for (int i = 0; i < WINDOW; i++) {
                re[i] = samples[offset + i] / 32768.0D * window[i];
                im[i] = 0.0D;
            }
            fft(re, im);

            double[] magnitude = new double[WINDOW / 2];
            for (int i = 0; i < magnitude.length; i++) {
                // ЛОГАРИФМ, А НЕ СЫРАЯ АМПЛИТУДА.
                //
                // Разница громкостей между тихим куплетом и дропом - десятки раз.
                // На линейной шкале весь куплет уходит под порог, а дроп выдаёт
                // сплошную стену. Логарифм сжимает динамику ровно так, как её слышит
                // ухо, и один и тот же порог начинает работать на всём треке.
                double value = Math.sqrt(re[i] * re[i] + im[i] * im[i]);
                magnitude[i] = Math.log1p(24.0D * value);
            }

            if (previous != null) {
                for (int i = 1; i < magnitude.length; i++) {
                    // Только РОСТ громкости: затухание ритма не задаёт.
                    double delta = magnitude[i] - previous[i];
                    if (delta <= 0.0D) continue;

                    int band = i < lowBin ? 0 : (i < midBin ? 1 : 2);
                    flux[band][frame] += delta;
                }
            }
            previous = magnitude;
        }

        // ---------- 2. ВЫЧИТАНИЕ СКОЛЬЗЯЩЕЙ МЕДИАНЫ ----------

        int medianWindow = (int) Math.round(framesPerSecond * 0.4D);
        for (double[] band : flux) {
            subtractRunningMedian(band, medianWindow);
            scaleToUnit(band);
        }

        double[] combined = new double[frames];
        for (int i = 0; i < frames; i++) {
            combined[i] = flux[0][i] + flux[1][i] + flux[2][i];
        }
        scaleToUnit(combined);

        // ---------- 3. ТЕМП И ФАЗА ----------

        double bpm = estimateBpm(combined, framesPerSecond);
        if (bpm <= 0.0D) return Result.fail("темп не определился");

        double beatFrames = 60.0D * framesPerSecond / bpm;
        double phaseFrames = estimatePhase(combined, beatFrames);
        int firstBeatMillis = (int) Math.round(
            (phaseFrames + LATENCY_FRAMES) * 1000.0D / framesPerSecond);

        // ---------- 4. ПИКИ ----------

        List<Peak> peaks = new ArrayList<>();
        for (int band = 0; band < 3; band++) {
            collectPeaks(flux[band], band, framesPerSecond, peaks);
        }
        if (peaks.isEmpty()) return Result.fail("в треке не нашлось ни одного удара");

        // ---------- 4.5. УТОЧНЕНИЕ ТЕМПА И ФАЗЫ ПО САМИМ УДАРАМ ----------

        double[] refined = refineByPeaks(peaks, bpm, framesPerSecond);
        bpm = refined[0];
        beatFrames = 60.0D * framesPerSecond / bpm;
        phaseFrames = refined[1];
        firstBeatMillis = (int) Math.round((phaseFrames + LATENCY_FRAMES) * 1000.0D / framesPerSecond);

        // ---------- 5. ПРИТЯГИВАНИЕ К СЕТКЕ ----------

        double gridStep = beatFrames / GRID_DIVISION;
        Map<Integer, Peak> onGrid = new HashMap<>();
        int rejected = snapToGrid(peaks, phaseFrames, gridStep, onGrid);
        if (onGrid.isEmpty()) return Result.fail("удары не легли на сетку темпа");

        // ---------- 5.2. ГДЕ НАЧИНАЕТСЯ ТАКТ ----------

        int downbeat = findDownbeat(onGrid);
        if (downbeat != 0) {
            onGrid = shiftNodes(onGrid, downbeat);
            phaseFrames += downbeat * gridStep;
            firstBeatMillis = (int) Math.round(
                (phaseFrames + LATENCY_FRAMES) * 1000.0D / framesPerSecond);
        }

        // ---------- 5.5. СОГЛАСОВАНИЕ ПОВТОРОВ ----------

        int unified = unifyRepeats(onGrid, flux, phaseFrames, gridStep);

        // ---------- 6. СИЛА ПО РАНГУ И МЕТРИЧЕСКОМУ ВЕСУ ----------

        List<Onset> onsets = buildOnsets(onGrid, phaseFrames, gridStep, framesPerSecond, false);

        // ---------- 7. ПОТОЛОК ----------

        if (onsets.size() > MAX_ONSETS) {
            List<Onset> strongest = new ArrayList<>(onsets);
            strongest.sort((a, b) -> Double.compare(b.strength, a.strength));
            onsets = new ArrayList<>(strongest.subList(0, MAX_ONSETS));
            onsets.sort((a, b) -> Integer.compare(a.millis, b.millis));
        }

        this.logger.info(
            "Разбор: длина {} мс, темп {}, фаза {} мс, ударов {} (сырых пиков {}, вне сетки {}, согласовано повторов {})",
            durationMillis, String.format(Locale.ROOT, "%.1f", bpm),
            firstBeatMillis, onsets.size(), peaks.size(), rejected, unified);

        return Result.ok(durationMillis, bpm, firstBeatMillis, onsets);
    }

    /**
     * Притянуть пики к узлам сетки.
     * <p>
     * На каждый узел остаётся максимум один удар: слипание решается само собой,
     * побеждает тот, что бьёт сильнее.
     *
     * @return сколько пиков отвергнуто как не попавшие в сетку
     */
    private static int snapToGrid(List<Peak> peaks,
                                  double phaseFrames,
                                  double gridStep,
                                  Map<Integer, Peak> out
    ) {
        int rejected = 0;
        for (Peak peak : peaks) {
            double position = (peak.frame - phaseFrames) / gridStep;
            int node = (int) Math.round(position);
            if (node < 0) continue;

            if (Math.abs(position - node) > GRID_TOLERANCE) {
                rejected++;
                continue;
            }

            Peak previous = out.get(node);
            if (previous == null || peak.raw > previous.raw) out.put(node, peak);
        }
        return rejected;
    }

    /**
     * НАЙТИ, КАКАЯ ШЕСТНАДЦАТАЯ НАЧИНАЕТ ТАКТ.
     * <p>
     * Уточнение темпа по ударам даёт очень точный ШАГ сетки, но фазу знает лишь с
     * точностью до одного шага: оно не различает, какая из шестнадцатых - сильная доля.
     * Само по себе это ничему не мешает, ноты всё равно лягут на музыку. Мешает другое:
     * от положения в такте зависят и метрический вес, и группировка повторов. С
     * произвольной привязкой главный акцент трека регулярно доставался бы, например,
     * третьей шестнадцатой - то есть слабейшей позиции такта.
     * <p>
     * Ищется просто: для каждой из шестнадцати позиций складывается сила всех ударов,
     * которые на неё попали за весь трек. Побеждает самая тяжёлая - в подавляющем
     * большинстве музыки на первой доле стоит бочка, и она перевешивает всё остальное.
     */
    private static int findDownbeat(Map<Integer, Peak> onGrid) {
        double[] weight = new double[NODES_PER_BAR];
        for (Map.Entry<Integer, Peak> entry : onGrid.entrySet()) {
            int slot = ((entry.getKey() % NODES_PER_BAR) + NODES_PER_BAR) % NODES_PER_BAR;

            // Низкая полоса весит больше: сильную долю задаёт бочка, а не тарелка.
            double bandWeight = entry.getValue().band == 0 ? 2.0D : 1.0D;
            weight[slot] += entry.getValue().raw * bandWeight;
        }

        int best = 0;
        for (int slot = 1; slot < NODES_PER_BAR; slot++) {
            if (weight[slot] > weight[best]) best = slot;
        }
        return best;
    }

    /** Сдвинуть нумерацию узлов так, чтобы сильная доля оказалась нулевой. */
    private static Map<Integer, Peak> shiftNodes(Map<Integer, Peak> onGrid, int shift) {
        Map<Integer, Peak> result = new HashMap<>(onGrid.size() * 2);
        for (Map.Entry<Integer, Peak> entry : onGrid.entrySet()) {
            int node = entry.getKey() - shift;
            if (node < 0) continue;
            result.put(node, entry.getValue());
        }
        return result;
    }

    /** Сколько шестнадцатых в такте. Четырёхдольный размер, как у подавляющего большинства. */
    private static final int NODES_PER_BAR = GRID_DIVISION * 4;

    /**
     * Насколько похожими должны быть такты, чтобы считаться одним и тем же местом песни.
     * <p>
     * 0.88 по косинусу - это уже «тот же самый рисунок, сыгранный чуть иначе». Ниже
     * начинают слипаться просто похожие по плотности куски, и согласование испортило бы
     * настоящую разницу между ними.
     */
    private static final double REPEAT_SIMILARITY = 0.88D;

    /**
     * СОГЛАСОВАТЬ ПОВТОРЯЮЩИЕСЯ ТАКТЫ.
     * <p>
     * Зачем это вообще нужно. Порог обнаружения удара - величина местная: он ползёт за
     * громкостью, за плотностью, за тем, что происходило секунду назад. Поэтому один и
     * тот же проигрыш, встретившийся в треке трижды, каждый раз распознаётся ЧУТЬ
     * по-разному: здесь нашлось двенадцать ударов, там девять, а тихая шестнадцатая в
     * конце фразы то проходит порог, то нет. На слух это один и тот же кусок, а на
     * карте - три разных, и играются они как три незнакомых места вместо одного
     * выученного. Именно это ломает удовольствие от повторов в ритм-играх.
     * <p>
     * Что делается. Каждый такт описывается вектором из энергии по трём полосам в
     * каждой из шестнадцати долей - это и есть его РИСУНОК, безразличный к общей
     * громкости после нормировки. Такты с косинусной близостью выше
     * {@link #REPEAT_SIMILARITY} собираются в группу, и внутри группы каждая доля
     * решается ГОЛОСОВАНИЕМ: если удар на ней нашёлся в большинстве повторов, он
     * добавляется всем остальным; если в меньшинстве - убирается у всех.
     * <p>
     * Почему голосование, а не объединение. Объединение по «есть хоть где-то» тянет в
     * карту каждый ложный пик, а пересечение по «есть везде» вычищает половину
     * настоящих ударов. Большинство - единственный вариант, который чинит и пропуски,
     * и ложные срабатывания одновременно.
     * <p>
     * Что здесь сознательно НЕ делается. Такты, у которых не нашлось похожих, не
     * трогаются вовсе: сбивка в конце фразы обязана остаться сбивкой, а не быть
     * приглаженной под соседей.
     *
     * @return сколько долей было исправлено
     */
    private static int unifyRepeats(Map<Integer, Peak> onGrid,
                                    double[][] flux,
                                    double phaseFrames,
                                    double gridStep
    ) {
        int lastNode = 0;
        for (int node : onGrid.keySet()) lastNode = Math.max(lastNode, node);

        int bars = lastNode / NODES_PER_BAR + 1;
        if (bars < 4) return 0;

        // ---- рисунок каждого такта ----
        double[][] shape = new double[bars][NODES_PER_BAR * 3];
        for (int bar = 0; bar < bars; bar++) {
            for (int slot = 0; slot < NODES_PER_BAR; slot++) {
                int node = bar * NODES_PER_BAR + slot;
                int frame = (int) Math.round(phaseFrames + node * gridStep);
                for (int band = 0; band < 3; band++) {
                    double value = 0.0D;
                    // Берём максимум в окрестности узла: удар может лежать на кадр в
                    // сторону, и точное попадание в один кадр здесь ничего не даёт.
                    for (int d = -1; d <= 1; d++) {
                        int index = frame + d;
                        if (index < 0 || index >= flux[band].length) continue;
                        value = Math.max(value, flux[band][index]);
                    }
                    shape[bar][slot * 3 + band] = value;
                }
            }
            normalize(shape[bar]);
        }

        // ---- группы похожих тактов ----
        boolean[] taken = new boolean[bars];
        int fixed = 0;

        for (int bar = 0; bar < bars; bar++) {
            if (taken[bar]) continue;

            List<Integer> group = new ArrayList<>();
            group.add(bar);
            taken[bar] = true;

            for (int other = bar + 1; other < bars; other++) {
                if (taken[other]) continue;
                if (cosine(shape[bar], shape[other]) < REPEAT_SIMILARITY) continue;
                group.add(other);
                taken[other] = true;
            }

            if (group.size() < 2) continue;
            fixed += voteWithinGroup(onGrid, group);
        }

        return fixed;
    }

    /**
     * Решить голосованием, каким должен быть общий рисунок группы, и привести к нему все.
     */
    private static int voteWithinGroup(Map<Integer, Peak> onGrid, List<Integer> group) {
        int majority = group.size() / 2 + 1;
        int fixed = 0;

        for (int slot = 0; slot < NODES_PER_BAR; slot++) {
            List<Peak> present = new ArrayList<>();
            for (int bar : group) {
                Peak peak = onGrid.get(bar * NODES_PER_BAR + slot);
                if (peak != null) present.add(peak);
            }

            // Дорисовывать удар можно только там, где ЕСТЬ ЗВУК. Иначе группа,
            // зацепившая затухание в конце трека, насыпает руд в чистую тишину.
            if (present.size() >= majority && present.size() < group.size()) {
                // Удар есть у большинства - значит он есть у всех. Сила берётся средняя,
                // полоса - самая частая: дорисованный удар обязан звучать как остальные
                // в этой же группе, а не как случайный пик рядом.
                double strength = 0.0D;
                for (Peak peak : present) strength += peak.raw;
                strength /= present.size();
                int band = commonBand(present);

                for (int bar : group) {
                    int node = bar * NODES_PER_BAR + slot;
                    if (onGrid.containsKey(node)) continue;
                    // Кадр здесь номинальный: время всё равно считается по узлу сетки.
                    onGrid.put(node, new Peak(node, strength, band));
                    fixed++;
                }
            } else if (!present.isEmpty() && present.size() < majority) {
                // Удар нашёлся у меньшинства - это ложное срабатывание, а не задумка.
                for (int bar : group) {
                    int node = bar * NODES_PER_BAR + slot;
                    if (onGrid.remove(node) != null) fixed++;
                }
            }
        }

        return fixed;
    }

    private static int commonBand(List<Peak> peaks) {
        int[] counts = new int[3];
        for (Peak peak : peaks) counts[peak.band]++;
        int best = 0;
        for (int band = 1; band < 3; band++) {
            if (counts[band] > counts[best]) best = band;
        }
        return best;
    }

    /** Привести вектор к единичной длине: сравнивать надо рисунок, а не громкость. */
    private static void normalize(double[] vector) {
        double sum = 0.0D;
        for (double value : vector) sum += value * value;
        if (sum <= 0.0D) return;

        double length = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) vector[i] /= length;
    }

    /** Косинус между двумя уже нормированными векторами. */
    private static double cosine(double[] a, double[] b) {
        double sum = 0.0D;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    /**
     * Собрать итоговые удары: время по сетке, сила по рангу и метрическому весу.
     *
     * @param bandFromStrength выводить ли полосу из силы - так делается только там,
     *                         где спектра нет вовсе
     */
    private static List<Onset> buildOnsets(Map<Integer, Peak> onGrid,
                                           double phaseFrames,
                                           double gridStep,
                                           double framesPerSecond,
                                           boolean bandFromStrength
    ) {
        List<Integer> nodes = new ArrayList<>(onGrid.keySet());
        Collections.sort(nodes);

        double[] sortedRaw = new double[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) sortedRaw[i] = onGrid.get(nodes.get(i)).raw;
        Arrays.sort(sortedRaw);

        List<Onset> onsets = new ArrayList<>(nodes.size());
        for (int node : nodes) {
            Peak peak = onGrid.get(node);

            // РАНГ, А НЕ ГРОМКОСТЬ.
            //
            // Абсолютная громкость сжата логарифмом и упирается в потолок: на плотном
            // сведении почти все удары оказываются «максимальными», и любой порог по
            // силе перестаёт что-либо различать - отсюда и дроп-ноты на каждом шагу.
            // Ранг же по построению распределён равномерно, поэтому и сложности, и
            // выбор руды начинают работать одинаково на любом треке.
            double strength = clamp01(rankOf(sortedRaw, peak.raw) * metricWeight(node));

            int millis = (int) Math.round(
                (phaseFrames + node * gridStep + LATENCY_FRAMES) * 1000.0D / framesPerSecond);
            int band = bandFromStrength
                ? (strength > 0.6D ? 0 : (strength > 0.3D ? 1 : 2))
                : peak.band;

            onsets.add(new Onset(Math.max(0, millis), strength, band));
        }
        return onsets;
    }

    /**
     * Раскодировать трек в моно 16 бит через ffmpeg.
     * <p>
     * Поток читается прямо из процесса: писать восьмимегабайтный wav на диск ради
     * одного прохода незачем.
     */
    private short[] decode(Path source) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
            this.ffmpeg, "-v", "error",
            "-i", source.toAbsolutePath().toString(),
            "-f", "s16le", "-acodec", "pcm_s16le",
            "-ac", "1", "-ar", String.valueOf(SAMPLE_RATE),
            "-");

        // ОШИБКИ В НИКУДА, А НЕ В ТРУБУ.
        //
        // Раньше stderr никто не читал. Буфер трубы - 64 КБ: стоило ffmpeg разговориться
        // на битом файле, как он упирался в полный буфер и вставал намертво до самого
        // таймаута в две минуты, а игрок всё это время смотрел в «прокси разбирает трек».
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);

        Process process = builder.start();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = process.getInputStream()) {
            byte[] chunk = new byte[1 << 16];
            int read;
            while ((read = in.read(chunk)) > 0) {
                bytes.write(chunk, 0, read);
                // Полчаса музыки в память не поместятся и не нужны.
                if (bytes.size() > 400 * 1024 * 1024) break;
            }
        }
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return null;
        }

        byte[] raw = bytes.toByteArray();
        short[] samples = new short[raw.length / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) ((raw[i * 2] & 0xFF) | (raw[i * 2 + 1] << 8));
        }
        return samples;
    }

    // ==================== БЕЗ FFMPEG ====================

    /**
     * ЗАПАСНОЙ РАЗБОР ПО ЗАГОЛОВКАМ OGG.
     * <p>
     * Длина получается ТОЧНАЯ: у последней страницы в заголовке лежит номер последнего
     * сэмпла, частота дискретизации - в первой, делим одно на другое.
     * <p>
     * А вот ритм - на глаз. Vorbis пишет с переменным битрейтом: громкие места занимают
     * больше байт, поэтому размеры страниц во времени дают грубую огибающую громкости.
     * Сетка темпа применяется и здесь - она вытягивает даже такой разбор.
     */
    private Result analyzeWithoutFfmpeg(Path source) {
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".ogg")) {
            return Result.fail("нужен ffmpeg: разбирать не-ogg без него нечем");
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(source);
        } catch (IOException e) {
            return Result.fail("не удалось прочитать файл трека");
        }

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

            long granule = 0L;
            for (int i = 7; i >= 0; i--) {
                granule = (granule << 8) | (bytes[position + 6 + i] & 0xFFL);
            }

            int segments = bytes[position + 26] & 0xFF;
            int tableStart = position + 27;
            if (tableStart + segments > bytes.length) break;

            int dataSize = 0;
            for (int i = 0; i < segments; i++) dataSize += bytes[tableStart + i] & 0xFF;

            int dataStart = tableStart + segments;
            if (dataStart + dataSize > bytes.length) break;

            if (sampleRate == 0 && dataSize >= 16 && (bytes[dataStart] & 0xFF) == 0x01) {
                sampleRate = (bytes[dataStart + 12] & 0xFF)
                    | ((bytes[dataStart + 13] & 0xFF) << 8)
                    | ((bytes[dataStart + 14] & 0xFF) << 16)
                    | ((bytes[dataStart + 15] & 0xFF) << 24);
            }
            if (granule > 0) lastGranule = granule;

            if (sampleRate > 0 && granule > 0) {
                times.add(granule / (double) sampleRate);
                sizes.add((double) dataSize);
            }

            position = dataStart + dataSize;
        }

        if (sampleRate <= 0 || lastGranule <= 0 || times.size() < 16) {
            return Result.fail("файл не разобрался как ogg");
        }

        int durationMillis = (int) Math.round(lastGranule * 1000.0D / sampleRate);

        int framesPerSecond = 100;
        int frames = Math.max(16, durationMillis * framesPerSecond / 1000);
        double[] envelope = new double[frames];
        double[] counts = new double[frames];

        for (int i = 0; i < times.size(); i++) {
            int frame = (int) Math.floor(times.get(i) * framesPerSecond);
            if (frame < 0 || frame >= frames) continue;
            envelope[frame] += sizes.get(i);
            counts[frame]++;
        }

        double last = 0.0D;
        for (int i = 0; i < frames; i++) {
            if (counts[i] > 0) {
                envelope[i] /= counts[i];
                last = envelope[i];
            } else {
                envelope[i] = last;
            }
        }

        double[] flux = new double[frames];
        for (int i = 1; i < frames; i++) flux[i] = Math.max(0.0D, envelope[i] - envelope[i - 1]);
        subtractRunningMedian(flux, framesPerSecond / 2);
        scaleToUnit(flux);

        double bpm = estimateBpm(flux, framesPerSecond);
        if (bpm <= 0.0D) return Result.fail("темп не определился");

        double beatFrames = 60.0D * framesPerSecond / bpm;
        double phaseFrames = estimatePhase(flux, beatFrames);
        double gridStep = beatFrames / GRID_DIVISION;

        List<Peak> peaks = new ArrayList<>();
        collectPeaks(flux, 1, framesPerSecond, peaks);

        Map<Integer, Peak> onGrid = new HashMap<>();
        snapToGrid(peaks, phaseFrames, gridStep, onGrid);
        if (onGrid.isEmpty()) return Result.fail("удары не легли на сетку темпа");

        // Полос без спектра не существует, поэтому полоса выводится из силы.
        List<Onset> onsets = buildOnsets(onGrid, phaseFrames, gridStep, framesPerSecond, true);
        if (onsets.size() > MAX_ONSETS) {
            List<Onset> strongest = new ArrayList<>(onsets);
            strongest.sort((a, b) -> Double.compare(b.strength, a.strength));
            onsets = new ArrayList<>(strongest.subList(0, MAX_ONSETS));
            onsets.sort((a, b) -> Integer.compare(a.millis, b.millis));
        }

        int firstBeatMillis = (int) Math.round(
            (phaseFrames + LATENCY_FRAMES) * 1000.0D / framesPerSecond);
        this.logger.info("Разбор без ffmpeg: длина {} мс, темп {}, ударов {}",
            durationMillis, Math.round(bpm), onsets.size());
        return Result.ok(durationMillis, bpm, firstBeatMillis, onsets);
    }

    // ==================== МАТЕМАТИКА ====================

    private static double clamp01(double value) {
        if (value < 0.0D) return 0.0D;
        return Math.min(value, 1.0D);
    }

    /**
     * МЕТРИЧЕСКИЙ ВЕС УЗЛА СЕТКИ.
     * <p>
     * Первая доля такта - опора, на неё в музыке приходится главный акцент. Третья
     * доля слабее, восьмушки ещё слабее, шестнадцатые - это уже заполнение. Без этого
     * веса «самым сильным» ударом трека регулярно оказывается случайный щелчок в
     * середине такта, а акценты рассыпаются где попало вместо начала фраз.
     */
    static double metricWeight(int node) {
        int period = GRID_DIVISION * 4;
        int inBar = ((node % period) + period) % period;

        if (inBar == 0) return 1.00D;                        // первая доля такта
        if (inBar == GRID_DIVISION * 2) return 0.88D;        // третья доля
        if (inBar % GRID_DIVISION == 0) return 0.80D;        // вторая и четвёртая доли
        if (inBar % (GRID_DIVISION / 2) == 0) return 0.62D;  // восьмые
        return 0.45D;                                        // шестнадцатые
    }

    /** Доля значений, которые не больше данного. 0 - самый слабый удар, 1 - самый сильный. */
    static double rankOf(double[] sorted, double value) {
        if (sorted.length <= 1) return 1.0D;
        int index = Arrays.binarySearch(sorted, value);
        if (index < 0) index = -index - 1;
        return index / (double) (sorted.length - 1);
    }

    private static double[] hann() {
        double[] result = new double[WINDOW];
        for (int i = 0; i < WINDOW; i++) {
            result[i] = 0.5D - 0.5D * Math.cos(2.0D * Math.PI * i / (WINDOW - 1));
        }
        return result;
    }

    /** Обычное быстрое преобразование Фурье на месте, основание два. */
    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double tmp = re[i]; re[i] = re[j]; re[j] = tmp;
                tmp = im[i]; im[i] = im[j]; im[j] = tmp;
            }
        }

        for (int length = 2; length <= n; length <<= 1) {
            double angle = -2.0D * Math.PI / length;
            double wRe = Math.cos(angle);
            double wIm = Math.sin(angle);
            for (int i = 0; i < n; i += length) {
                double curRe = 1.0D;
                double curIm = 0.0D;
                for (int j = 0; j < length / 2; j++) {
                    double uRe = re[i + j];
                    double uIm = im[i + j];
                    double vRe = re[i + j + length / 2] * curRe - im[i + j + length / 2] * curIm;
                    double vIm = re[i + j + length / 2] * curIm + im[i + j + length / 2] * curRe;

                    re[i + j] = uRe + vRe;
                    im[i + j] = uIm + vIm;
                    re[i + j + length / 2] = uRe - vRe;
                    im[i + j + length / 2] = uIm - vIm;

                    double nextRe = curRe * wRe - curIm * wIm;
                    curIm = curRe * wIm + curIm * wRe;
                    curRe = nextRe;
                }
            }
        }
    }

    /**
     * Вычесть из потока его же скользящую медиану.
     * <p>
     * Это и есть «удар», отделённый от «стало громче». Медиана, а не среднее: среднее
     * само тянется вверх теми самыми пиками, которые мы ищем, и в громком месте порог
     * уезжает за них. Медиана на выбросы не реагирует вовсе.
     * <p>
     * Считается по ПРОРЕЖЕННОЙ выборке окна: полная сортировка на каждом кадре - это
     * минуты работы на трёхминутном треке, а на положение порога прореживание не
     * влияет практически никак.
     */
    static void subtractRunningMedian(double[] values, int window) {
        if (window < 3 || values.length < window) return;

        double[] result = new double[values.length];
        int samples = Math.min(window, 33);
        double[] buffer = new double[samples];

        for (int i = 0; i < values.length; i++) {
            int from = Math.max(0, i - window / 2);
            int to = Math.min(values.length, i + window / 2 + 1);

            int step = Math.max(1, (to - from) / samples);
            int count = 0;
            for (int j = from; j < to && count < samples; j += step) {
                buffer[count++] = values[j];
            }
            if (count == 0) continue;

            double[] sample = Arrays.copyOf(buffer, count);
            Arrays.sort(sample);
            result[i] = Math.max(0.0D, values[i] - sample[count / 2]);
        }

        System.arraycopy(result, 0, values, 0, values.length);
    }

    /** Растянуть поток в 0..1 по 98-му перцентилю, чтобы редкий выброс не съедал всю шкалу. */
    static void scaleToUnit(double[] values) {
        if (values.length == 0) return;

        double[] sorted = values.clone();
        Arrays.sort(sorted);

        double max = sorted[(int) (sorted.length * 0.98D)];
        if (max <= 0.0D) max = sorted[sorted.length - 1];
        if (max <= 0.0D) return;

        for (int i = 0; i < values.length; i++) {
            values[i] = Math.min(1.0D, values[i] / max);
        }
    }

    /**
     * ТЕМП: ГРЕБЁНКА ПО КРАТНЫМ, НА ДРОБНЫХ СДВИГАХ.
     * <p>
     * Здесь два решения, и оба обязательны - поодиночке ни одно не работает.
     * <p>
     * ПЕРВОЕ: СДВИГ ДРОБНЫЙ. Кадр длится 23 миллисекунды, поэтому целые сдвиги дают
     * чудовищно грубую шкалу темпа: сдвиг 21 кадр - это 123 BPM, сдвиг 22 - уже 117.5,
     * а между ними нет ничего. Темп 120 на такой шкале просто НЕ СУЩЕСТВУЕТ, его period
     * равен 21.53 кадра. Значение потока между кадрами берётся линейно, и шкала темпа
     * становится непрерывной.
     * <p>
     * ВТОРОЕ: СЧЁТ ПО КРАТНЫМ. Совпадение на одной доле есть у многих периодов, в том
     * числе у ложных: рисунок с точкой даёт сильный отклик на полутора долях, и трек в
     * 120 BPM уверенно определялся как 80.7. Отличает настоящую долю то, что на ней
     * стоит ВСЯ МЕТРИЧЕСКАЯ ЛЕСЕНКА: две доли, четыре доли, такт. У полуторной доли
     * такой поддержки нет - её удвоение и учетверение попадают в пустоту.
     * <p>
     * Почему это не заработало бы без дробных сдвигов: удвоение настоящего периода 21.53
     * равно 43.07, а «удвоение» округлённого 22 - это 44, мимо. Именно поэтому счёт по
     * кратным на целых сдвигах ошибался ещё хуже, чем без него.
     * <p>
     * Приор вокруг {@link #TEMPO_PRIOR_BPM} остаётся: он разбирает последнюю
     * неоднозначность, которую лесенка снять не может - между темпом и его половиной
     * метрическая поддержка одинаковая, и выбрать помогает только то соображение, что
     * под 120 топают ногой чаще, чем под 60.
     */
    static double estimateBpm(double[] flux, double framesPerSecond) {
        if (flux.length < 64) return 0.0D;

        // Грубый проход по всему диапазону, затем уточнение вокруг победителя.
        double bestBpm = searchBpm(flux, framesPerSecond, MIN_BPM, MAX_BPM, 0.25D);
        if (bestBpm <= 0.0D) return 0.0D;

        // ТОЧНОСТЬ ТЕМПА ВАЖНЕЕ, ЧЕМ КАЖЕТСЯ.
        //
        // Ошибка в четверть BPM выглядит пустяком, но она НАКАПЛИВАЕТСЯ: за две минуты
        // при 120 BPM это уже целая шестнадцатая расхождения к концу трека. Сетка
        // медленно уезжает относительно музыки, и один и тот же проигрыш в начале и в
        // конце песни оказывается на разных долях такта - то есть согласовывать повторы
        // становится не с чем. Поэтому вокруг найденного темпа делается второй проход
        // с шагом в сотую долю.
        return searchBpm(flux, framesPerSecond,
            Math.max(MIN_BPM, bestBpm - 0.5D),
            Math.min(MAX_BPM, bestBpm + 0.5D),
            0.01D);
    }

    /** Перебор темпа в заданных границах с заданным шагом. */
    private static double searchBpm(double[] flux, double framesPerSecond,
                                    double fromBpm, double toBpm, double step
    ) {
        double best = -1.0D;
        double bestBpm = 0.0D;

        // Шаг по ТЕМПУ, а не по сдвигу: на быстрых темпах соседние целые сдвиги
        // отличаются на единицы BPM, и перебор по сдвигам просто не увидел бы нужного.
        for (double bpm = fromBpm; bpm <= toBpm; bpm += step) {
            double lag = 60.0D * framesPerSecond / bpm;
            if (lag * 2.0D >= flux.length) continue;

            // Доля, две доли, четыре доли. Веса убывают: чем длиннее промежуток, тем
            // меньше в нём музыки и больше случайного совпадения.
            double comb = autocorrelation(flux, lag)
                + 0.75D * autocorrelation(flux, lag * 2.0D)
                + 0.5D * autocorrelation(flux, lag * 4.0D);

            double octaves = Math.log(bpm / TEMPO_PRIOR_BPM) / Math.log(2.0D);
            double normalized = octaves / TEMPO_PRIOR_OCTAVES;
            double score = comb * Math.exp(-0.5D * normalized * normalized);

            if (score > best) {
                best = score;
                bestBpm = bpm;
            }
        }

        return bestBpm;
    }

    /**
     * Совпадение потока с самим собой на ДРОБНОМ сдвиге.
     * <p>
     * Значение между кадрами берётся линейно. Без этого шкала темпа получается
     * ступенчатой, а вместе с ней ступенчатой становится и вся сетка карты.
     */
    private static double autocorrelation(double[] flux, double lag) {
        if (lag < 1.0D || lag >= flux.length - 1) return 0.0D;

        double sum = 0.0D;
        int count = 0;
        for (int i = (int) Math.ceil(lag); i < flux.length; i++) {
            double shifted = i - lag;
            int index = (int) shifted;
            double fraction = shifted - index;
            sum += flux[i] * (flux[index] * (1.0D - fraction) + flux[index + 1] * fraction);
            count++;
        }
        return count == 0 ? 0.0D : sum / count;
    }

    /**
     * УТОЧНИТЬ ТЕМП И ФАЗУ ПО ПОЛОЖЕНИЮ САМИХ УДАРОВ.
     * <p>
     * Автокорреляция смотрит на весь поток целиком, включая шум между ударами, и дальше
     * сотых долей BPM не видит. А ошибка в сотые доли НАКАПЛИВАЕТСЯ: 0.18 BPM при темпе
     * 120 - это одна восьмая доли расхождения к концу двухминутного трека. Сетка
     * медленно уезжает от музыки, и один и тот же проигрыш в начале и в конце песни
     * оказывается на разных долях такта. Согласовывать повторы после такого уже не с чем.
     * <p>
     * Здесь темп проверяется по ТОЧНЫМ положениям найденных ударов, а это гораздо более
     * острый инструмент. Для каждого пробного темпа берётся остаток положения каждого
     * удара от шага сетки и превращается в угол на окружности. Если темп верный, все
     * углы смотрят примерно в одну сторону и их сумма как векторов получается длинной;
     * если темп чуть-чуть неверен, углы медленно расползаются по всей окружности и
     * сумма схлопывается. Длина этой суммы и есть мера правильности.
     * <p>
     * Приятный побочный результат: направление той же суммы сразу даёт ФАЗУ, и её не
     * приходится искать отдельным перебором.
     * <p>
     * Диапазон поиска узкий - плюс-минус процент. Это уточнение, а не повторный поиск:
     * какую именно долю считать долей, уже решено, и пересматривать это решение здесь
     * нельзя, иначе уточнение уведёт темп в соседнюю метрическую ступень.
     *
     * @return массив из двух чисел: уточнённый темп и фаза в кадрах
     */
    private static double[] refineByPeaks(List<Peak> peaks, double bpm, double framesPerSecond) {
        if (peaks.size() < 16) {
            return new double[]{bpm, 0.0D};
        }

        double bestConcentration = -1.0D;
        double bestBpm = bpm;
        double bestPhase = 0.0D;

        for (double candidate = bpm * 0.99D; candidate <= bpm * 1.01D; candidate += 0.005D) {
            double step = 60.0D * framesPerSecond / candidate / GRID_DIVISION;
            if (step < 1.0D) continue;

            double x = 0.0D;
            double y = 0.0D;
            double weight = 0.0D;

            for (Peak peak : peaks) {
                double angle = 2.0D * Math.PI * (peak.frame % step) / step;
                x += peak.raw * Math.cos(angle);
                y += peak.raw * Math.sin(angle);
                weight += peak.raw;
            }
            if (weight <= 0.0D) continue;

            double concentration = Math.sqrt(x * x + y * y) / weight;
            if (concentration <= bestConcentration) continue;

            bestConcentration = concentration;
            bestBpm = candidate;

            // Направление суммы - это средний остаток, то есть и есть смещение сетки.
            double mean = Math.atan2(y, x);
            if (mean < 0.0D) mean += 2.0D * Math.PI;
            bestPhase = mean / (2.0D * Math.PI) * step;
        }

        return new double[]{bestBpm, bestPhase};
    }

    /**
     * ФАЗА ПЕРВОЙ ДОЛИ.
     * <p>
     * Темп говорит, какой длины доля, но ничего не говорит о том, где она начинается.
     * Перебираем все смещения внутри одной доли и берём то, на котором сумма потока
     * в узлах сетки максимальна: это и есть положение, при котором сетка совпала с
     * реальными ударами.
     * <p>
     * Раньше вместо этого за первую долю принимался первый попавшийся онсет - то есть
     * первый шорох в записи, часто вообще до вступления.
     */
    static double estimatePhase(double[] flux, double beatFrames) {
        if (beatFrames < 2.0D || flux.length < beatFrames * 4.0D) return 0.0D;

        int steps = Math.max(8, (int) Math.round(beatFrames));
        double bestScore = -1.0D;
        double bestPhase = 0.0D;

        for (int step = 0; step < steps; step++) {
            double phase = beatFrames * step / steps;

            double score = 0.0D;
            for (double position = phase; position < flux.length - 1; position += beatFrames) {
                int index = (int) position;
                double fraction = position - index;
                score += flux[index] * (1.0D - fraction) + flux[index + 1] * fraction;
            }

            if (score > bestScore) {
                bestScore = score;
                bestPhase = phase;
            }
        }

        return bestPhase;
    }

    /**
     * Пики потока: локальные максимумы выше скользящего порога.
     * <p>
     * Порог здесь мягче прежнего, потому что грубую работу уже сделало вычитание
     * медианы, а всё лишнее отсеет сетка темпа.
     */
    static void collectPeaks(double[] flux, int band, double framesPerSecond, List<Peak> out) {
        int window = Math.max(1, (int) Math.round(framesPerSecond / 2.0D));

        for (int i = 1; i < flux.length - 1; i++) {
            if (flux[i] <= flux[i - 1] || flux[i] < flux[i + 1]) continue;

            double sum = 0.0D;
            int count = 0;
            for (int j = Math.max(0, i - window); j < Math.min(flux.length, i + window); j++) {
                sum += flux[j];
                count++;
            }
            double threshold = (sum / Math.max(1, count)) * 1.25D + 0.02D;
            if (flux[i] < threshold) continue;

            out.add(new Peak(i, flux[i], band));
        }
    }
}
