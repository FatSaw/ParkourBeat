package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import ru.sortix.parkourbeat.levels.BiomeApplier;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.BiomeZone;
import ru.sortix.parkourbeat.levels.settings.BossBarCue;
import ru.sortix.parkourbeat.levels.settings.FlashCue;
import ru.sortix.parkourbeat.levels.settings.FlashSpeed;
import ru.sortix.parkourbeat.levels.settings.LevelBiome;
import ru.sortix.parkourbeat.levels.settings.LevelBossBarColor;
import ru.sortix.parkourbeat.levels.settings.LightShowCue;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.levels.settings.LightShowSharpness;
import ru.sortix.parkourbeat.levels.settings.SkyCycleCue;
import ru.sortix.parkourbeat.levels.settings.SkyType;
import ru.sortix.parkourbeat.levels.settings.ZoneSkyTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * АВТОМАТИЧЕСКОЕ СВЕТОВОЕ ШОУ ПО РАЗБОРУ ТРЕКА.
 * <p>
 * Главная мысль здесь одна, и она не про свет, а про музыку: у песни есть ФОРМА.
 * Вступление, куплет, нарастание, припев, спад, брейк. Шоу, которое одинаково мигает
 * всю дорогу, читается как гирлянда - оно не врёт, но и не говорит ничего. Шоу,
 * которое меняется вместе с формой, читается как оформление именно этой песни.
 * <p>
 * Поэтому здесь два шага, и первый важнее второго:
 * <ol>
 *     <li>ТРЕК РЕЖЕТСЯ НА РАЗДЕЛЫ. Не по таймеру, а по тому, где реально меняется
 *         плотность и сила ударов. Соседние окна с похожей энергией склеиваются, и
 *         остаются куски по 8-40 секунд - примерно та длина, которой живут части песни.</li>
 *     <li>КАЖДОМУ РАЗДЕЛУ ПОДБИРАЕТСЯ ОФОРМЛЕНИЕ по его характеру, а НЕ по номеру.
 *         Спокойному - плавный переход неба и медленные качели, попадающие в такт.
 *         Жёсткому - резкая смена и вспышки. Причём вспышки на подъёме и на дропе
 *         разные: на подъёме редкие и мягкие, на дропе частые.</li>
 * </ol>
 * ЧТО ЗДЕСЬ СОЗНАТЕЛЬНО НЕ ДЕЛАЕТСЯ. Нет вспышек в тихих местах - там они читаются
 * как сбой, а не как эффект. Нет смены неба чаще чем раз в восемь секунд - глаз не
 * успевает принять цвет за новый и видит просто мельтешение. И нет ни одного элемента
 * на первых секундах: вступление должно быть спокойным, иначе игрок не успевает
 * понять, где он и куда едет.
 */
public final class DiggerAutoShow {

    private DiggerAutoShow() {
    }

    /**
     * ВСЁ МЕРЯЕТСЯ В ТАКТАХ, А НЕ В СЕКУНДАХ. ЭТО ГЛАВНОЕ ЗДЕСЬ.
     * <p>
     * Раньше трек резался окнами по четыре тысячи миллисекунд - по секундомеру. Границы
     * разделов вставали на 4.000, 8.000, 12.000 и так далее, и никакого отношения к
     * музыке эти числа не имели. А на границах разделов стоит ВСЁ оформление: смена
     * неба, вспышки, чудо, кирка. То есть каждый элемент шоу попадал в случайное место
     * такта - иногда в долю, чаще между долями. Отсюда и ощущение, что шоу живёт своей
     * жизнью рядом с музыкой.
     * <p>
     * Теперь единица измерения - ТАКТ, а точка отсчёта - фаза первой доли из разбора.
     * Любая граница здесь кратна такту, значит и любой элемент шоу приходится ровно на
     * долю. Ничего дополнительно подгонять не нужно: это получается само.
     */
    private static final int WINDOW_BARS = 2;

    /** Короче этого раздел не бывает: оформление не успевает прочитаться. */
    private static final int MIN_SECTION_BARS = 4;

    /** Сколько тактов в начале трека не оформляются вовсе. */
    private static final int INTRO_QUIET_BARS = 2;

    /** Длиннее этого нарастание перестаёт быть нарастанием и становится частью песни. */
    private static final int MAX_BUILD_MILLIS = 24000;

    /** Насколько должна отличаться энергия, чтобы это считалось новым разделом. */
    private static final double SECTION_EPSILON = 0.17D;

    /**
     * СЕТКА ТАКТОВ ТРЕКА.
     * <p>
     * Два числа, из которых выводится всё остальное: длина такта и момент первой доли.
     * Фаза берётся из разбора - анализатор уже определил, какая шестнадцатая начинает
     * такт, и приписал её ко времени ударов.
     */
    private static final class Grid {
        final double barMillis;
        final double beatMillis;
        final int phaseMillis;

        Grid(double bpm, int phaseMillis) {
            this.beatMillis = bpm > 0.0D ? 60000.0D / bpm : 500.0D;
            this.barMillis = this.beatMillis * 4.0D;
            this.phaseMillis = Math.max(0, phaseMillis);
        }

        /** Момент начала такта с этим номером, мс. */
        int barAt(int bar) {
            return (int) Math.round(this.phaseMillis + bar * this.barMillis);
        }

        /** Ближайшая доля к этому моменту - чтобы посадить на неё элемент шоу. */
        int snapToBeat(int millis) {
            double beats = (millis - this.phaseMillis) / this.beatMillis;
            return (int) Math.round(this.phaseMillis + Math.round(beats) * this.beatMillis);
        }
    }

    /** Характер раздела - от него зависит вообще всё оформление. */
    private enum Mood {
        /** Тишина, вступление, брейк. Небо почти не двигается. */
        CALM,
        /** Куплет. Медленное дыхание неба в такт. */
        GENTLE,
        /** Нарастание перед припевом. Небо ползёт к горячему краю. */
        BUILD,
        /** Припев. Резкая смена и редкие вспышки. */
        HARD,
        /** Дроп, самое плотное место трека. Всё сразу. */
        PEAK
    }

    /** Кусок трека с уже определённым характером. */
    private static final class Section {
        final int startMillis;
        final int endMillis;
        final double energy;
        Mood mood;

        Section(int startMillis, int endMillis, double energy) {
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.energy = energy;
        }

        int length() {
            return this.endMillis - this.startMillis;
        }
    }

    /**
     * Состояние сборки на время одного вызова.
     * <p>
     * Именно ОБЪЕКТ, а не статические поля: погода и цвет боссбара пишутся только при
     * смене, и если помнить прошлое значение в статике, второй запуск авторазметки
     * решит, что менять нечего, и не поставит ни одной реплики. На сервере с двумя
     * строителями это ломалось бы ещё и вперемешку.
     */
    private static final class Ctx {
        final Report report = new Report();
        LevelBossBarColor lastBarColor = null;
        DiggerShowIntensity intensity = DiggerShowIntensity.MEDIUM;
        Grid grid = null;
        int skyEveryBeats = 0;
        int lastPickaxeMillis = Integer.MIN_VALUE / 2;
        int lastWonderMillis = Integer.MIN_VALUE / 2;
        int pickaxeTurn = 0;
        int wonderTurn = 0;
    }

    /** Что получилось - чтобы сказать строителю в чат, а не заставлять его считать. */
    public static final class Report {
        public int sections;
        public int skyCues;
        public int flashCues;
        public int pickaxeCues;
        public int wonders;
        public int biomeZones;
        public int bossBarCues;

        public int total() {
            return this.skyCues + this.flashCues
                + this.pickaxeCues + this.wonders + this.biomeZones + this.bossBarCues;
        }
    }

    /**
     * Собрать шоу под разобранный трек.
     * <p>
     * ВСЁ ПРЕЖНЕЕ ШОУ СТИРАЕТСЯ. Дописывать поверх нельзя: у неба, погоды и биомов
     * состояние сквозное, и половина старых реплик поверх новых даёт не «богаче», а
     * цвет, который зависит от того, в каком порядке лежат элементы в файле.
     *
     * @param biomes ставить ли биом-зоны. Они запекаются в блоки и стоят дорого,
     *               поэтому решение принимает строитель, а не мы за него
     */
    @NonNull
    public static Report generate(@NonNull Level level,
                                  @NonNull DiggerAnalysis analysis,
                                  @NonNull DiggerShowIntensity intensity,
                                  boolean biomes
    ) {
        Ctx ctx = new Ctx();
        ctx.intensity = intensity;
        Report report = ctx.report;

        LightShowSettings show = level.getLightShow();
        clear(level, show);

        Grid grid = new Grid(analysis.getBpm(), analysis.getOffsetMillis());
        ctx.grid = grid;

        List<Section> sections = split(analysis, grid);
        if (sections.isEmpty()) return report;
        report.sections = sections.size();

        double beatMillis = grid.beatMillis;
        int barMillis = (int) Math.round(grid.barMillis);

        // БАЗОВОЕ НЕБО ПОДБИРАЕТСЯ ПОД ТРЕК, А НЕ ЗАБИВАЕТСЯ НОЧЬЮ.
        //
        // Ночь удобна тем, что на тёмном фоне руды видно лучше всего - но это довод
        // за читаемость, а не за музыку. Спокойному треку с редкими тихими ударами
        // ночь идёт; плотному и быстрому куда естественнее рассвет или вечер, и
        // держать его в темноте значит спорить с самой песней.
        //
        // Решает средняя энергия трека: чем он спокойнее, тем темнее опора.
        SkyType baseSky = baseSkyFor(sections);
        show.setBaseSky(baseSky);

        ctx.skyEveryBeats = fitSkyBudget(sections, grid, intensity);

        SkyType previousSky = baseSky;
        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            Mood nextMood = i + 1 < sections.size() ? sections.get(i + 1).mood : null;
            previousSky = decorate(show, section, nextMood, previousSky, beatMillis, barMillis, ctx);
        }

        if (biomes) applyBiomes(level, sections, intensity.getMaxBiomeZones(), grid, report);

        show.sort();
        show.bumpRevision();
        return report;
    }

    /**
     * Убрать всё, что шоу поставило раньше.
     * <p>
     * БИОМ-ЗОНЫ СНИМАЮТСЯ ОСОБО. Остальные элементы шоу проигрываются игроку и исчезают
     * вместе с записью в списке, а биом ЗАПЕКАЕТСЯ В САМИ БЛОКИ мира. Удалить зону из
     * списка означало лишь забыть, что биом когда-то ставили: в мире он оставался
     * навсегда. Отсюда и «в световом шоу таких зон нет, а на карте они есть» - это
     * следы прошлых прогонов авторазметки, которые уже некому было убрать.
     */
    private static void clear(@NonNull Level level, @NonNull LightShowSettings show) {
        for (LightShowCue cue : new ArrayList<>(show.getSkyCues())) show.removeSkyCue(cue);
        for (SkyCycleCue cue : new ArrayList<>(show.getSkyCycleCues())) show.removeSkyCycleCue(cue);
        for (FlashCue cue : new ArrayList<>(show.getFlashCues())) show.removeFlashCue(cue);
        for (BossBarCue cue : new ArrayList<>(show.getBossBarCues())) show.removeBossBarCue(cue);
        for (ru.sortix.parkourbeat.levels.settings.PickaxeCue cue
            : new ArrayList<>(show.getPickaxeCues())) show.removePickaxeCue(cue);
        for (ru.sortix.parkourbeat.levels.wonder.WonderEffect effect
            : new ArrayList<>(show.getWonderEffects())) show.removeWonderEffect(effect);
        for (BiomeZone zone : new ArrayList<>(show.getBiomeZones())) {
            try {
                // Сначала вернуть блокам исходный биом, и только потом забыть зону.
                BiomeApplier.reset(level, zone);
            } catch (Throwable ignored) {
                // Мир мог быть не загружен - тогда зону всё равно надо убрать из списка.
            }
            show.removeBiomeZone(zone);
        }
    }

    // ==================== ФОРМА ТРЕКА ====================

    /**
     * РАЗРЕЗАТЬ ТРЕК ПО СМЕНЕ ЭНЕРГИИ.
     * <p>
     * Энергия окна - это сумма сил ударов, помноженная на корень из их количества.
     * Корень, а не само количество: иначе барабанная дробь из тридцати тихих
     * шестнадцатых перевешивала бы четыре удара дропа, хотя на слух всё наоборот.
     * <p>
     * Дальше окна переводятся в РАНГ среди всех окон трека и склеиваются, пока
     * соседние отличаются меньше чем на {@link #SECTION_EPSILON}. Ранг здесь по той же
     * причине, что и везде в этом плагине: абсолютные пороги живут ровно до первого
     * трека с другим мастерингом.
     */
    @NonNull
    private static List<Section> split(@NonNull DiggerAnalysis analysis, @NonNull Grid grid) {
        List<DiggerAnalysis.Onset> onsets = analysis.getOnsets();
        if (onsets.size() < 16) return new ArrayList<>();

        int lastMillis = onsets.get(onsets.size() - 1).getMillis();

        // Окно теперь - это WINDOW_BARS тактов, отсчитанных от фазы первой доли.
        double windowMillis = grid.barMillis * WINDOW_BARS;
        int windows = (int) ((lastMillis - grid.phaseMillis) / windowMillis) + 1;
        if (windows < 4) return new ArrayList<>();

        double[] energy = new double[windows];
        int[] counts = new int[windows];
        for (DiggerAnalysis.Onset onset : onsets) {
            int window = (int) Math.floor((onset.getMillis() - grid.phaseMillis) / windowMillis);
            if (window < 0 || window >= windows) continue;
            energy[window] += onset.getStrength();
            counts[window]++;
        }
        for (int i = 0; i < windows; i++) {
            energy[i] = counts[i] == 0 ? 0.0D : energy[i] * Math.sqrt(counts[i]);
        }

        double[] sorted = energy.clone();
        Arrays.sort(sorted);
        double[] rank = new double[windows];
        for (int i = 0; i < windows; i++) rank[i] = rankOf(sorted, energy[i]);

        List<Section> sections = new ArrayList<>();
        int from = 0;
        for (int i = 1; i <= windows; i++) {
            boolean last = i == windows;
            if (!last && Math.abs(rank[i] - rank[from]) < SECTION_EPSILON) continue;

            double sum = 0.0D;
            for (int j = from; j < i; j++) sum += rank[j];
            // Границы раздела - это кратные такту моменты, а не круглые секунды.
            sections.add(new Section(
                grid.barAt(from * WINDOW_BARS), grid.barAt(i * WINDOW_BARS), sum / (i - from)));
            from = i;
        }

        merge(sections, grid);
        assignMoods(sections);
        mergeSameMood(sections);
        return sections;
    }

    /**
     * Склеить слишком короткие разделы с соседями.
     * <p>
     * Раздел в четыре секунды - это не часть песни, а случайный провал в плотности:
     * пауза перед припевом, дырка между фразами. Оформлять его отдельно означает
     * поставить смену неба на ровном месте.
     */
    private static void merge(@NonNull List<Section> sections, @NonNull Grid grid) {
        int index = 1;
        while (index < sections.size()) {
            Section current = sections.get(index);
            if (current.length() >= grid.barMillis * MIN_SECTION_BARS) {
                index++;
                continue;
            }

            Section previous = sections.get(index - 1);
            double weightedEnergy =
                (previous.energy * previous.length() + current.energy * current.length())
                    / (double) (previous.length() + current.length());

            Section merged = new Section(previous.startMillis, current.endMillis, weightedEnergy);
            sections.set(index - 1, merged);
            sections.remove(index);
        }

        // Первый раздел тоже может оказаться огрызком - тогда он прилипает ко второму.
        if (sections.size() > 1 && sections.get(0).length() < grid.barMillis * MIN_SECTION_BARS) {
            Section first = sections.get(0);
            Section second = sections.get(1);
            sections.set(0, new Section(first.startMillis, second.endMillis, second.energy));
            sections.remove(1);
        }
    }

    /**
     * СКЛЕИТЬ СОСЕДНИЕ РАЗДЕЛЫ ОДНОГО ХАРАКТЕРА.
     * <p>
     * Нарезка по энергии честно видит разницу между двумя половинами припева, но
     * ОФОРМЛЕНИЕ у них выходит одинаковое - а значит на их стыке небо дёргается без
     * всякой причины. Именно из этого и складывалось ощущение мигания: смен неба было
     * вдвое больше, чем настоящих смен в музыке.
     * <p>
     * После склейки смена неба приходится ровно на смену части песни и никуда больше.
     */
    private static void mergeSameMood(@NonNull List<Section> sections) {
        int index = 1;
        while (index < sections.size()) {
            Section previous = sections.get(index - 1);
            Section current = sections.get(index);
            if (previous.mood != current.mood) {
                index++;
                continue;
            }

            double energy =
                (previous.energy * previous.length() + current.energy * current.length())
                    / (double) (previous.length() + current.length());

            Section merged = new Section(previous.startMillis, current.endMillis, energy);
            merged.mood = previous.mood;
            sections.set(index - 1, merged);
            sections.remove(index);
        }
    }

    /**
     * НАЗНАЧИТЬ ХАРАКТЕР КАЖДОМУ РАЗДЕЛУ.
     * <p>
     * Характер - это не только уровень энергии, но и НАПРАВЛЕНИЕ. Раздел средней
     * плотности перед самым громким местом трека и такой же раздел после него звучат
     * совершенно по-разному: первый - нарастание, второй - спад. Абсолютный порог
     * этой разницы не видит, поэтому здесь смотрим и на соседа справа.
     */
    private static void assignMoods(@NonNull List<Section> sections) {
        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            double energy = section.energy;

            Section next = i + 1 < sections.size() ? sections.get(i + 1) : null;

            if (energy >= 0.86D) {
                section.mood = Mood.PEAK;
            } else if (energy >= 0.62D) {
                section.mood = Mood.HARD;
            } else if (energy >= 0.30D) {
                // ПОДВОДКА - ЭТО НЕ ПРОСТО «ДАЛЬШЕ ГРОМЧЕ».
                //
                // Три условия сразу, и каждое отсеивает свою ошибку. Кусок сам по себе
                // должен быть не тихим - иначе подводкой становится вступление, за
                // которым, разумеется, всегда идёт что-то громче. Следующий кусок должен
                // быть по-настоящему громким, а не просто чуть плотнее. И подводка должна
                // быть КОРОТКОЙ: нарастание на полминуты - это уже не нарастание, а
                // отдельная часть песни, и тянуть по нему переход неба тридцать секунд
                // значит не показать никакого перехода вовсе.
                boolean risingIntoLoud = next != null
                    && next.energy >= 0.62D
                    && next.energy - energy > 0.22D;

                section.mood = risingIntoLoud && section.length() <= MAX_BUILD_MILLIS
                    ? Mood.BUILD
                    : Mood.GENTLE;
            } else {
                section.mood = Mood.CALM;
            }
        }
    }

    // ==================== ОФОРМЛЕНИЕ ====================

    /**
     * Расставить элементы одного раздела.
     *
     * @return небо, на котором раздел закончился - оно станет исходным для следующего
     */
    private static SkyType decorate(@NonNull LightShowSettings show,
                                    @NonNull Section section,
                                    @javax.annotation.Nullable Mood nextMood,
                                    @NonNull SkyType previousSky,
                                    double beatMillis,
                                    int barMillis,
                                    @NonNull Ctx ctx
    ) {
        // Вступление не трогаем вообще.
        int start = Math.max(section.startMillis, ctx.grid.barAt(INTRO_QUIET_BARS));
        if (start >= section.endMillis) return previousSky;

        SkyType target = skyFor(section.mood, previousSky);

        // ПЕРЕХОД ДЛИНОЙ В ТАКТЫ, А НЕ В КРУГЛЫЕ СЕКУНДЫ.
        //
        // Небо, доехавшее до нового цвета ровно к началу фразы, читается как часть
        // музыки. То же небо, доехавшее за «две секунды», приезжает в случайное место
        // такта, и связь с треком теряется - при том что длительность почти та же.
        int transition;
        LightShowSharpness sharpness;
        switch (section.mood) {
            case CALM:
                transition = barMillis * 4;          // четыре такта: почти незаметно
                sharpness = LightShowSharpness.SMOOTH;
                break;
            case GENTLE:
                transition = barMillis * 2;
                sharpness = LightShowSharpness.SMOOTH;
                break;
            case BUILD:
                // Подводка тянется весь раздел: небо доезжает ровно к дропу. Но не
                // дольше восьми тактов - за этим порогом изменение становится таким
                // медленным, что его уже никто не замечает.
                transition = Math.min(barMillis * 8, Math.max(barMillis, section.endMillis - start));
                sharpness = LightShowSharpness.SMOOTH;
                break;
            default:
                // Припев и дроп начинаются РЕЗКО. Плавный въезд в дроп - это худшее,
                // что можно сделать: самый громкий момент трека размазывается по
                // четырём секундам и перестаёт быть моментом.
                transition = Math.max(120, (int) Math.round(beatMillis / 2));
                sharpness = LightShowSharpness.SHARP;
                break;
        }
        transition = Math.min(transition, Math.max(200, section.length()));

        // Смена неба садится РОВНО НА ДОЛЮ. Раздел и так начинается с такта, но после
        // ограничений по длине переход мог съехать - округляем обратно.
        int at = ctx.grid.snapToBeat(start);
        if (target != previousSky
            && show.addSkyCue(new LightShowCue(at, at + transition, target, sharpness))) {
            ctx.report.skyCues++;
        }

        decoratePhraseSky(show, section, target, start + transition, barMillis, ctx);
        decorateFlashes(show, section, nextMood, start, barMillis, ctx);
        decorateBossBar(show, section, start, ctx);
        decoratePickaxe(show, section, start, ctx);
        decorateWonders(show, section, start, barMillis, ctx);

        return target;
    }

    /**
     * ЧУДОЭФФЕКТЫ - ТО, ЧЕГО НЕ УМЕЕТ НЕБО.
     * <p>
     * Небо может только менять цвет, и любые попытки выжать из него событие сводятся к
     * миганию. Чудоэффект - это отдельная фигура в воздухе: он читается сам по себе и
     * не трогает освещение, поэтому им можно отмечать доли, не портя картинку.
     */

    /** Пресеты, которые разворачиваются слишком широко, чтобы висеть на уровне глаз. */
    private static final java.util.Set<String> BIG_WONDERS = java.util.Set.of(
        "fire_rings", "portal_ring", "halo", "constellation", "fire_wall",
        "magic_circle", "sphere", "ring", "shape_grid", "shape_wave", "love_ring",
        "scene_god_eye", "scene_city", "scene_meteor_night", "eye", "gate", "arch_row");

    /**
     * ЗАПРЕЩЁННЫЕ ПРЕСЕТЫ.
     * <p>
     * Полярное сияние на ограничении частиц разворачивается в редкую сетку точек вместо
     * полотна, а полумесяц читается как случайная дуга и ни к какой музыке не идёт.
     */
    private static final java.util.Set<String> BANNED_WONDERS = java.util.Set.of(
        "aurora", "moon",
        // Романтическое: к произвольному треку не идёт ни при каком характере.
        "heart", "heart_beat", "love_ring", "butterflies", "petals", "halo", "rose",
        // Надписи из нетекстовых категорий.
        "scene_lyric");

    /**
     * ВСЯ БИБЛИОТЕКА, РАЗЛОЖЕННАЯ ПО ХАРАКТЕРАМ.
     * <p>
     * Раньше здесь были списки из трёх-четырёх идентификаторов, набранных руками, - и
     * восемь десятков готовых эффектов просто лежали без дела. Теперь пул собирается из
     * самих КАТЕГОРИЙ библиотеки: какие бы пресеты в неё ни добавили потом, они попадут
     * в подбор сами, без правки этого файла.
     * <p>
     * Текстовые не берутся НИКОГДА, целой категорией. Они выводят слова - «ВПЕРЁД» и
     * прочее, - и посреди чужого трека это выглядит не оформлением, а чьей-то забытой
     * надписью. Их место в ручной разметке, где строитель сам решает, что написать.
     * <p>
     * Романтическая категория не берётся по той же причине: сердце, кольцо из сердец и
     * бабочки - это оформление для конкретного повода, а не для произвольного трека. На
     * дропе техно сердечко читается как чужой эффект, случайно попавший в карту.
     */
    @NonNull
    private static java.util.List<ru.sortix.parkourbeat.levels.wonder.WonderPreset> poolFor(
        @NonNull Mood mood
    ) {
        ru.sortix.parkourbeat.levels.wonder.WonderCategory[] categories;
        switch (mood) {
            case PEAK:
                categories = new ru.sortix.parkourbeat.levels.wonder.WonderCategory[]{
                    ru.sortix.parkourbeat.levels.wonder.WonderCategory.FIRE,
                    ru.sortix.parkourbeat.levels.wonder.WonderCategory.HIT,
                    ru.sortix.parkourbeat.levels.wonder.WonderCategory.SCENE};
                break;
            case HARD:
                categories = new ru.sortix.parkourbeat.levels.wonder.WonderCategory[]{
                    ru.sortix.parkourbeat.levels.wonder.WonderCategory.FIRE,
                    ru.sortix.parkourbeat.levels.wonder.WonderCategory.MAGIC,
                    ru.sortix.parkourbeat.levels.wonder.WonderCategory.SHAPE};
                break;
            case BUILD:
                categories = new ru.sortix.parkourbeat.levels.wonder.WonderCategory[]{
                    ru.sortix.parkourbeat.levels.wonder.WonderCategory.PATH,
                    ru.sortix.parkourbeat.levels.wonder.WonderCategory.MAGIC,
                    ru.sortix.parkourbeat.levels.wonder.WonderCategory.SKY};
                break;
            case GENTLE:
                categories = new ru.sortix.parkourbeat.levels.wonder.WonderCategory[]{
                    ru.sortix.parkourbeat.levels.wonder.WonderCategory.SKY,
                    ru.sortix.parkourbeat.levels.wonder.WonderCategory.SHAPE};
                break;
            default:
                categories = new ru.sortix.parkourbeat.levels.wonder.WonderCategory[]{
                    ru.sortix.parkourbeat.levels.wonder.WonderCategory.SKY,
                    ru.sortix.parkourbeat.levels.wonder.WonderCategory.SHAPE};
                break;
        }

        java.util.List<ru.sortix.parkourbeat.levels.wonder.WonderPreset> pool = new ArrayList<>();
        for (ru.sortix.parkourbeat.levels.wonder.WonderCategory category : categories) {
            for (ru.sortix.parkourbeat.levels.wonder.WonderPreset preset
                : ru.sortix.parkourbeat.levels.wonder.WonderPresets.byCategory(category)) {
                if (BANNED_WONDERS.contains(preset.getId())) continue;
                pool.add(preset);
            }
        }
        return pool;
    }

    /**
     * Взять следующий эффект из пула, по кругу.
     * <p>
     * Счётчик общий на весь трек, а не на характер: так соседние разделы почти никогда
     * не получают один и тот же эффект, даже если характер у них одинаковый.
     */
    @javax.annotation.Nullable
    private static ru.sortix.parkourbeat.levels.wonder.WonderPreset pickWonder(
        @NonNull Mood mood, @NonNull Ctx ctx
    ) {
        java.util.List<ru.sortix.parkourbeat.levels.wonder.WonderPreset> pool = poolFor(mood);
        if (pool.isEmpty()) return null;
        return pool.get(Math.floorMod(ctx.wonderTurn++, pool.size()));
    }

    private static void decorateWonders(@NonNull LightShowSettings show,
                                        @NonNull Section section,
                                        int start,
                                        int barMillis,
                                        @NonNull Ctx ctx
    ) {
        // ЧУДО ТОЖЕ СТАВИТСЯ ПО ВСЕЙ ДЛИНЕ РАЗДЕЛА.
        //
        // По одному на раздел - это пять-шесть штук на трек, сколько бы ни просили.
        // Шаг задаётся в тактах, поэтому каждое чудо приходится на долю, а не на
        // случайное место между ними.
        double step = ctx.grid.barMillis * ctx.intensity.getWonderGapBars();
        if (step <= 0.0D) return;

        for (double at = start; at < section.endMillis - step / 2.0D; at += step) {
            int from = ctx.grid.snapToBeat((int) Math.round(at));
            if (from - ctx.lastWonderMillis < step - 1) continue;

            ru.sortix.parkourbeat.levels.wonder.WonderPreset preset = pickWonder(section.mood, ctx);
            if (preset == null) return;
            String presetId = preset.getId();

            ru.sortix.parkourbeat.levels.wonder.WonderEffect effect = preset.toEffect(from);
            if (effect.getEndMillis() > section.endMillis) {
                effect.setEndMillis(Math.max(from + barMillis, section.endMillis));
            }

            if (BIG_WONDERS.contains(presetId)) {
                effect.setAnchor(ru.sortix.parkourbeat.levels.wonder.WonderAnchor.OVERHEAD);
                effect.setHeight(22.0D);
                effect.setDistance(30.0D);
                effect.setScale(1.6D);
            }

            if (show.addWonderEffect(effect)) {
                ctx.lastWonderMillis = from;
                ctx.report.wonders++;
            }
        }
    }

    /**
     * КИРКА НА ДРОПЕ - РЕДКО И ТОЛЬКО ТАМ.
     * <p>
     * На механику она не влияет никак: в творческом режиме руда ломается с одного клика
     * чем угодно. Это чистое оформление, и работает оно ровно потому, что смена руки -
     * событие редкое. Кирка, меняющаяся каждый куплет, превращается в мельтешение в
     * нижней части экрана, за которым перестают следить.
     * <p>
     * Поэтому только на самых плотных разделах и не чаще одного раза в
     * промежуток, заданный насыщенностью.
     * <p>
     * МАТЕРИАЛ ЗАВИСИТ ОТ ХАРАКТЕРА РАЗДЕЛА, а не прибит к золоту. Кирка - такой же
     * инструмент оформления, как небо: тихому месту идёт тусклый камень, дропу -
     * алмаз. Одна и та же кирка на всех сменах не несёт никакой информации, она просто
     * мигает в руке.
     * <p>
     * Незеритовая не используется НИКОГДА: она и так лежит в руке по умолчанию, и
     * реплика с тем же предметом ничего не меняет - именно поэтому смены и не было видно.
     */

    /**
     * Кирка под характер раздела.
     * <p>
     * Внутри одного характера материал ЧЕРЕДУЕТСЯ: два припева подряд с одинаковой
     * киркой снова означали бы отсутствие смены.
     */
    @NonNull
    private static org.bukkit.Material pickaxeFor(@NonNull Mood mood, @NonNull Ctx ctx) {
        org.bukkit.Material[] options;
        switch (mood) {
            case PEAK:
                options = new org.bukkit.Material[]{
                    org.bukkit.Material.DIAMOND_PICKAXE, org.bukkit.Material.GOLDEN_PICKAXE};
                break;
            case HARD:
                options = new org.bukkit.Material[]{
                    org.bukkit.Material.IRON_PICKAXE, org.bukkit.Material.DIAMOND_PICKAXE};
                break;
            case BUILD:
                options = new org.bukkit.Material[]{
                    org.bukkit.Material.GOLDEN_PICKAXE, org.bukkit.Material.IRON_PICKAXE};
                break;
            default:
                options = new org.bukkit.Material[]{
                    org.bukkit.Material.STONE_PICKAXE, org.bukkit.Material.WOODEN_PICKAXE};
                break;
        }
        return options[Math.floorMod(ctx.pickaxeTurn++, options.length)];
    }

    private static void decoratePickaxe(@NonNull LightShowSettings show,
                                        @NonNull Section section,
                                        int start,
                                        @NonNull Ctx ctx
    ) {
        if (section.mood == Mood.CALM && ctx.intensity != DiggerShowIntensity.DENSE) return;

        // КИРКА МЕНЯЕТСЯ ВНУТРИ РАЗДЕЛА, А НЕ ТОЛЬКО НА ЕГО НАЧАЛЕ.
        //
        // Раньше реплика ставилась одна на раздел - отсюда и «чё так мало». Но кирка
        // живёт столько, сколько указано в реплике, и никто не мешает нарезать раздел
        // на куски по несколько тактов и дать каждому свою.
        double step = ctx.grid.barMillis * ctx.intensity.getPickaxeGapBars();
        if (step <= 0.0D) return;

        for (double at = start; at < section.endMillis - step / 2.0D; at += step) {
            int from = ctx.grid.snapToBeat((int) Math.round(at));
            int to = (int) Math.min(section.endMillis, Math.round(at + step));
            if (to - from < ctx.grid.barMillis) continue;

            if (show.addPickaxeCue(new ru.sortix.parkourbeat.levels.settings.PickaxeCue(
                from, to, pickaxeFor(section.mood, ctx)))) {
                ctx.lastPickaxeMillis = from;
                ctx.report.pickaxeCues++;
            }
        }
    }

    /**
     * ОПОРНОЕ НЕБО ВСЕЙ КАРТЫ.
     * <p>
     * Считается по средней энергии разделов. Тихий трек получает ночь, средний -
     * поздний вечер, плотный и быстрый - вечер. Дальше в светлую сторону не уходим ни
     * при каких условиях: на светлом небе руды теряются, а читаемость нот здесь важнее
     * любой красоты.
     */
    @NonNull
    private static SkyType baseSkyFor(@NonNull List<Section> sections) {
        double sum = 0.0D;
        int length = 0;
        for (Section section : sections) {
            sum += section.energy * section.length();
            length += section.length();
        }
        if (length == 0) return SkyType.NIGHT;

        double average = sum / length;
        if (average >= 0.62D) return SkyType.EVENING;
        if (average >= 0.42D) return SkyType.LATE_EVENING;
        return SkyType.NIGHT;
    }

    /**
     * Небо раздела.
     * <p>
     * Ночь - опора всей карты: на тёмном небе руды и лучи видно лучше всего. Остальные
     * цвета - отклонения от неё, и чем громче раздел, тем дальше отклонение.
     * <p>
     * Если подобранный цвет совпал с предыдущим, берётся соседний: два раздела подряд
     * под одним небом означают, что смены не произошло вовсе, а именно смена и есть
     * то, ради чего всё это ставится.
     */
    @NonNull
    private static SkyType skyFor(@NonNull Mood mood, @NonNull SkyType previous) {
        SkyType primary;
        SkyType alternative;
        switch (mood) {
            case CALM:
                primary = SkyType.NIGHT;
                alternative = SkyType.LATE_EVENING;
                break;
            case GENTLE:
                primary = SkyType.LATE_EVENING;
                alternative = SkyType.PURPLE;
                break;
            case BUILD:
                primary = SkyType.PURPLE;
                alternative = SkyType.EVENING;
                break;
            case HARD:
                primary = SkyType.EVENING;
                alternative = SkyType.PURPLE;
                break;
            default:
                // ДРОП - ЕДИНСТВЕННОЕ МЕСТО С ГОРЯЧИМ НЕБОМ.
                //
                // Раньше горячие цвета доставались и припеву, и дропу, а между ними
                // лежал SOFT_WHITE - почти белое небо, на котором руды теряются вовсе.
                // Теперь вся палитра держится в тёмной половине, и оранжевый остаётся
                // редким событием: именно поэтому он и работает, когда случается.
                primary = SkyType.ORANGE;
                alternative = SkyType.RED_PINK;
                break;
        }
        return primary == previous ? alternative : primary;
    }

    /**
     * КАЧЕЛИ НЕБА - ДЫХАНИЕ ТРЕКА.
     * <p>
     * {@link SkyCycleCue} гоняет небо между двумя краями и обратно, и её период здесь
     * ВСЕГДА кратен такту. Это и есть просьба «плавно под бит»: качели с периодом в
     * четыре такта на спокойном куске дают именно медленное дыхание в темпе песни, а
     * не абстрактное мигание рядом с ней.
     * <p>
     * На жёстких разделах период короче - один такт, - и качели превращаются в пульс.
     * На дропе их нет вовсе: там уже работают вспышки, и две мигалки поверх друг друга
     * дают кашу.
     */
    /**
     * УЛОЖИТЬ ПУЛЬС НЕБА В ЛИМИТ РЕПЛИК.
     * <p>
     * Список реплик неба ограничен сверху: {@code LightShowSettings.MAX_CUES}. При
     * пульсе через долю трёхминутный трек просит под три сотни смен, а влезет сто
     * двадцать восемь - и дальше {@code addSkyCue} просто перестаёт принимать.
     * <p>
     * Хуже всего то, КАК это выглядит: реплики расставляются по порядку, поэтому лимит
     * съедается началом трека, а последняя треть остаётся без единой смены. Карта
     * начинается ярко и глохнет к концу - худший из возможных исходов, ведь к концу
     * обычно и приходится самое громкое место.
     * <p>
     * Поэтому шаг подбирается ЗАРАНЕЕ под длину трека: если при выбранном шаге смен
     * выходит больше лимита, шаг удваивается, пока не влезет. Плотность получается
     * ниже заказанной, зато ровная по всему треку - а редкий, но равномерный пульс
     * читается куда лучше, чем частый в начале и никакого в конце.
     */
    private static int fitSkyBudget(@NonNull List<Section> sections,
                                    @NonNull Grid grid,
                                    @NonNull DiggerShowIntensity intensity
    ) {
        int everyBeats = intensity.getSkyEveryBeats();
        if (everyBeats <= 0) return 0;

        // Смены на границах разделов тоже занимают место в том же списке.
        int budget = ru.sortix.parkourbeat.levels.settings.LightShowSettings.MAX_CUES
            - sections.size() - 4;
        if (budget <= 0) return 0;

        double covered = 0.0D;
        for (Section section : sections) {
            if (intensity == DiggerShowIntensity.DENSE) covered += section.length();
            else if (section.mood == Mood.PEAK || section.mood == Mood.HARD) covered += section.length();
        }
        if (covered <= 0.0D) return everyBeats;

        while (covered / (grid.beatMillis * everyBeats) > budget && everyBeats < 64) {
            everyBeats *= 2;
        }
        return everyBeats;
    }

    /**
     * НЕБО ВНУТРИ РАЗДЕЛА, ПО ФРАЗАМ.
     * <p>
     * Смена на границах разделов отмечает форму песни - но разделов на трек всего
     * несколько, и оформление получается редким. Музыка же устроена мельче: внутри
     * припева есть фразы по четыре и восемь тактов, и слух их слышит как отдельные
     * куски, даже когда громкость не меняется.
     * <p>
     * Поэтому на громких разделах небо переключается по фразовой сетке, туда-обратно
     * между двумя близкими цветами. Именно ТУДА-ОБРАТНО и именно между близкими:
     * бесконечный проход по всей палитре читался бы как случайная смена, а возврат к
     * тому же цвету через фразу - как ритм.
     * <p>
     * Это НЕ качели неба: там был плавный ход к противоположному краю и обратно с
     * периодом в секунду, который выглядел миганием. Здесь резкая смена ровно на долю,
     * и между сменами небо стоит неподвижно целую фразу.
     */
    private static void decoratePhraseSky(@NonNull LightShowSettings show,
                                          @NonNull Section section,
                                          @NonNull SkyType sectionSky,
                                          int from,
                                          int barMillis,
                                          @NonNull Ctx ctx
    ) {
        int everyBeats = ctx.skyEveryBeats;
        if (everyBeats <= 0) return;
        // На «насыщеннее» фразовая смена идёт везде, кроме полной тишины: там она
        // читалась бы как поломка, а не как оформление.
        // На «насыщеннее» пульс идёт по всему треку. В тишине он вдвое реже - там и
        // музыка реже, и пульс в темпе дропа читался бы как чужой.
        if (ctx.intensity == DiggerShowIntensity.DENSE) {
            if (section.mood == Mood.CALM) everyBeats *= 2;
        } else if (section.mood != Mood.PEAK && section.mood != Mood.HARD) {
            return;
        }

        SkyType other = phrasePartner(sectionSky);

        // РИСУНОК, А НЕ РАВНОМЕРНЫЙ ШАГ.
        //
        // Равные промежутки - это метроном: он попадает в долю, но ничего не говорит о
        // музыке. Настоящая ритмическая фигура неравномерна, и именно неравномерность
        // делает её узнаваемой - «раз-и-два, раз-и-два» слышно, а «раз-раз-раз-раз» нет.
        //
        // Здесь рисунок задан позициями шестнадцатых внутри такта и повторяется каждый
        // такт. Повтор обязателен: фигура, которая не повторяется, - это не фигура, а
        // случайность. Чем громче раздел, тем плотнее и синкопированнее рисунок.
        int[] pattern = patternFor(section.mood, everyBeats);
        double sixteenth = ctx.grid.beatMillis / 4.0D;
        double barLen = ctx.grid.barMillis;

        boolean useOther = true;
        int firstBar = (int) Math.ceil((from - ctx.grid.phaseMillis) / barLen);

        for (int bar = firstBar; ; bar++) {
            double barStart = ctx.grid.phaseMillis + bar * barLen;
            if (barStart >= section.endMillis) break;

            for (int slot : pattern) {
                int at = (int) Math.round(barStart + slot * sixteenth);
                if (at < from || at >= section.endMillis) continue;

            SkyType sky = useOther ? other : sectionSky;
            useOther = !useOther;

            // ДЛИНА ПЕРЕХОДА ЗАВИСИТ ОТ ТОГО, КУДА ОН ПОПАЛ.
            //
            // Смена на первой доле такта - это опора, её надо ставить резко. Смена на
            // слабой доле между опорами - это подголосок, и такой же резкий щелчок там
            // спорил бы с главным. Разная длина перехода превращает ровную череду смен
            // в рисунок с сильными и слабыми местами, то есть в ритм, а не в метроном.
            boolean downbeat = Math.abs(Math.round((at - ctx.grid.phaseMillis)
                / ctx.grid.barMillis) * ctx.grid.barMillis
                - (at - ctx.grid.phaseMillis)) < ctx.grid.beatMillis / 2.0D;
            int length = downbeat ? 60 : Math.max(90, (int) (ctx.grid.beatMillis / 2));

            if (show.addSkyCue(new LightShowCue(at, at + length, sky,
                downbeat ? LightShowSharpness.SHARP : LightShowSharpness.SMOOTH))) {
                ctx.report.skyCues++;
            }
            }
        }
    }

    /**
     * Ритмический рисунок раздела: позиции шестнадцатых внутри такта.
     * <p>
     * Ряды подобраны так, как их строят в самой музыке. На спокойном - только опора.
     * На среднем - опора и середина такта, то есть «раз ... три». На громком добавляется
     * слабая доля перед опорой, отчего фигура начинает тянуть вперёд. На дропе рисунок
     * синкопированный: 0-3-6 - это классическое «три-три-два», которое слышно в любом
     * танцевальном треке и которое ухо узнаёт мгновенно.
     *
     * @param everyBeats шаг из настройки насыщенности; чем он больше, тем рисунок реже
     */
    @NonNull
    private static int[] patternFor(@NonNull Mood mood, int everyBeats) {
        // Настройка попросила редко - отдаём только опору, рисунок тут не построить.
        if (everyBeats >= 8) return new int[]{0};
        if (everyBeats >= 4) {
            return mood == Mood.PEAK || mood == Mood.HARD ? new int[]{0, 8} : new int[]{0};
        }

        switch (mood) {
            case PEAK:
                return new int[]{0, 3, 6, 8, 11, 14};
            case HARD:
                return new int[]{0, 4, 6, 8, 12, 14};
            case BUILD:
                return new int[]{0, 6, 8, 12};
            case GENTLE:
                return new int[]{0, 8, 12};
            default:
                return new int[]{0, 8};
        }
    }

    /**
     * Пара к небу раздела: соседний оттенок, а не противоположный.
     * <p>
     * Близкий цвет даёт ощущение пульса; далёкий - ощущение, что что-то сломалось.
     */
    @NonNull
    private static SkyType phrasePartner(@NonNull SkyType sky) {
        switch (sky) {
            case ORANGE:
                return SkyType.RED_PINK;
            case RED_PINK:
                return SkyType.ORANGE;
            case EVENING:
                return SkyType.LATE_EVENING;
            case LATE_EVENING:
                return SkyType.EVENING;
            case PURPLE:
                return SkyType.LATE_EVENING;
            default:
                return SkyType.PURPLE;
        }
    }

    /*
     * КАЧЕЛЕЙ НЕБА ЗДЕСЬ БОЛЬШЕ НЕТ.
     *
     * SkyCycleCue гоняет небо между двумя краями и обратно. В замысле это дыхание в
     * темпе трека; на живом тесте человек видит, как небо раз в секунду уезжает к
     * опорному значению и возвращается, и читает это ровно как мигание, а не как
     * оформление.
     *
     * Сначала я решил, что виноват период, и оставил качели только на подводках. Это
     * сократило мигание до «определённых моментов», но не убрало: такова сама механика,
     * у неё есть обратный ход. Плавного дыхания неба этим инструментом не сделать,
     * поэтому подводка теперь размечается вспышкой - у неё обратного хода нет.
     */

    /**
     * ВСПЫШКИ - ТОЛЬКО ТАМ, ГДЕ ОНИ ЧИТАЮТСЯ КАК ЭФФЕКТ.
     * <p>
     * То есть на подводке и в громких разделах, и нигде больше. Вспышка в тихом месте
     * не воспринимается как оформление - её читают как сбой света или лаг.
     * <p>
     * Скорость разная и это существенно: на подводке {@link FlashSpeed#X1} даёт редкое
     * тяжёлое дыхание, к дропу оно ускоряется до X3. Одна и та же скорость на всём
     * треке - это ровно та гирлянда, которой здесь быть не должно.
     */
    private static void decorateFlashes(@NonNull LightShowSettings show,
                                        @NonNull Section section,
                                        @javax.annotation.Nullable Mood nextMood,
                                        int start,
                                        int barMillis,
                                        @NonNull Ctx ctx
    ) {
        FlashSpeed speed;
        int from = start;
        switch (section.mood) {
            case BUILD:
                // Только последние два такта подводки: вспышки с самого её начала
                // съедают эффект к моменту, когда он нужен.
                speed = FlashSpeed.X1;
                from = Math.max(start, section.endMillis - barMillis * 2);
                break;
            case GENTLE:
            case CALM:
                // ПОДВОДКА ХВОСТОМ спокойного раздела перед громким: нарастание надо
                // обозначить, а небо при этом оставить на месте.
                //
                // На «насыщеннее» вспышка ставится и просто так, на весь спокойный
                // раздел: там задача не беречь внимание, а залить трек светом.
                if (nextMood != Mood.PEAK && nextMood != Mood.HARD) {
                    if (!ctx.intensity.isFlashEverywhere()) return;
                    speed = FlashSpeed.X1;
                    break;
                }
                speed = FlashSpeed.X1;
                from = Math.max(start, section.endMillis - barMillis * 3);
                break;
            case PEAK:
                speed = ctx.intensity == DiggerShowIntensity.DENSE ? FlashSpeed.X3 : FlashSpeed.X2;
                break;
            case HARD:
                if (ctx.intensity == DiggerShowIntensity.LIGHT) return;
                speed = FlashSpeed.X2;
                break;
            default:
                return;
        }

        if (section.endMillis - from < barMillis) return;
        if (show.addFlashCue(new FlashCue(from, section.endMillis, speed))) ctx.report.flashCues++;
    }

    /**
     * Погода: дождь на самых плотных разделах и чистое небо на остальных.
     * <p>
     * Реплика ставится ТОЛЬКО при смене - иначе на каждый раздел приходилось бы по
     * реплике, и половина из них не меняла бы ничего, зато съедала лимит в 128 элементов.
     */
    /*
     * ПОГОДЫ ЗДЕСЬ НЕТ И НЕ БУДЕТ.
     *
     * Раньше на самых плотных разделах включался дождь. В тоннеле он не виден вовсе -
     * над головой камень, - зато он гасит освещение, приглушает цвет неба и добавляет
     * шум поверх трека. То есть цена есть, а эффекта нет. Метод убран целиком, а не
     * оставлен выключенным: возвращать тут нечего.
     */

    /** Цвет боссбара идёт за характером раздела: он на экране всегда и держит настроение. */
    private static void decorateBossBar(@NonNull LightShowSettings show,
                                        @NonNull Section section,
                                        int start,
                                        @NonNull Ctx ctx
    ) {
        LevelBossBarColor color;
        switch (section.mood) {
            case CALM:
                color = LevelBossBarColor.BLUE;
                break;
            case GENTLE:
                color = LevelBossBarColor.PURPLE;
                break;
            case BUILD:
                color = LevelBossBarColor.YELLOW;
                break;
            case HARD:
                color = LevelBossBarColor.PINK;
                break;
            default:
                color = LevelBossBarColor.RED;
                break;
        }
        if (color == ctx.lastBarColor) return;
        ctx.lastBarColor = color;
        if (show.addBossBarCue(new BossBarCue(start, color))) ctx.report.bossBarCues++;
    }

    // ==================== БИОМЫ ====================

    /**
     * БИОМ-ЗОНЫ - САМАЯ ДОРОГАЯ ЧАСТЬ ШОУ, ПОЭТОМУ ИХ МАЛО.
     * <p>
     * Каждая зона переписывает биом в блоках на всю длину куска и на всю высоту мира.
     * Ставить их на каждый раздел нельзя: трёхминутный трек с двадцатью разделами
     * положил бы сервер на минуту.
     * <p>
     * Поэтому берутся только САМЫЕ ГРОМКИЕ разделы, не больше четырёх, и красятся они
     * по одному за тик - расставлять биомы синхронно означало бы перекрасить всё
     * внутри одного тика, а это гарантированный фриз.
     */

    private static void applyBiomes(@NonNull Level level,
                                    @NonNull List<Section> sections,
                                    int maxZones,
                                    @NonNull Grid grid,
                                    @NonNull Report report
    ) {
        // БИОМ НАРЕЗАЕТСЯ ПО ТАКТАМ, А НЕ ПО РАЗДЕЛАМ.
        //
        // Здесь стояла одна зона на раздел - и сколько бы ни задавали потолок, зон
        // выходило ровно столько, сколько разделов, то есть семь-восемь. Потолок в
        // девяносто восемь просто никогда не достигался.
        //
        // Теперь каждый раздел режется на куски по нескольку тактов, и каждому куску
        // достаётся свой биом из ряда этого характера. Шаг подбирается так, чтобы на
        // весь трек вышло примерно столько зон, сколько просит насыщенность: заданное
        // число - это плотность на трек, а не «сколько влезет».
        //
        // Резать по тактам обязательно: биом красит небо, и смена цвета посреди фразы
        // читается так же плохо, как смена неба мимо доли.
        int firstMillis = grid.barAt(INTRO_QUIET_BARS);
        int lastMillis = 0;
        for (Section section : sections) lastMillis = Math.max(lastMillis, section.endMillis);

        double covered = Math.max(0, lastMillis - firstMillis);
        if (covered <= 0.0D) return;

        // Шаг в ЦЕЛЫХ тактах и не меньше одного. Такт - это нижняя граница, за которой
        // смена биома перестаёт быть сменой цвета и превращается в мельтешение: биом
        // красит небо целиком, и менять его чаще, чем раз в такт, некуда.
        //
        // Отсюда же следует, что на коротком треке потолок может и не набраться: если
        // при шаге в один такт зон выходит пятьдесят, то пятьдесят и будет, сколько бы
        // ни просили. Это не ограничение реализации, а музыкальный предел.
        int stepBars = (int) Math.max(1, Math.round(covered / Math.max(1, maxZones) / grid.barMillis));
        double stepMillis = grid.barMillis * stepBars;

        List<BiomeZone> queued = new ArrayList<>();
        int biomeTurn = 0;

        for (Section section : sections) {
            double from = Math.max(section.startMillis, firstMillis);
            while (from < section.endMillis) {
                if (queued.size() >= maxZones) break;

                int start = (int) Math.round(from);
                int end = (int) Math.min(section.endMillis, Math.round(from + stepMillis));
                from += stepMillis;
                if (end - start < grid.barMillis) continue;

                BiomeZone zone = new BiomeZone(
                    start, end, biomeFor(section.mood, biomeTurn++),
                    false, ZoneSkyTime.FROM_LIGHTSHOW);

                if (level.getLightShow().addBiomeZone(zone)) {
                    queued.add(zone);
                    report.biomeZones++;
                }
            }
            if (queued.size() >= maxZones) break;
        }

        // По одной зоне за тик: BiomeApplier переписывает биом во всех блоках зоны и
        // сохраняет мир. Четыре таких вызова подряд в одном тике - это секунды фриза.
        scheduleBiomes(level, queued, 0);
    }

    /**
     * Биом под характер раздела.
     * <p>
     * Ряд подобран по ЦВЕТУ НЕБА, который эти биомы дают, и идёт от холодного к
     * горячему - так же, как палитра самого шоу.
     */
    @NonNull
    private static LevelBiome biomeFor(@NonNull Mood mood, int turn) {
        // ВНУТРИ ХАРАКТЕРА БИОМ ЧЕРЕДУЕТСЯ.
        //
        // Одна строка соответствия «характер - биом» означала, что все припевы трека
        // получают один и тот же цвет неба, все куплеты другой, и на этом разнообразие
        // кончается: на весь трек выходит три-четыре оттенка. А биом - это самый крупный
        // мазок во всём шоу, он красит небо и туман целиком, и повторять его так часто
        // жалко.
        //
        // Ряды подобраны по ЦВЕТУ, который биом даёт небу, и не пересекаются между
        // характерами: громкое остаётся горячим, тихое холодным, но внутри каждого есть
        // из чего выбрать.
        LevelBiome[] options;
        switch (mood) {
            case PEAK:
                options = new LevelBiome[]{
                    LevelBiome.NETHER, LevelBiome.CRIMSON, LevelBiome.BASALT_DELTAS};
                break;
            case HARD:
                options = new LevelBiome[]{
                    LevelBiome.CRIMSON, LevelBiome.BADLANDS, LevelBiome.NETHER,
                    LevelBiome.SOUL_SAND_VALLEY};
                break;
            case BUILD:
                options = new LevelBiome[]{
                    LevelBiome.SOUL_SAND_VALLEY, LevelBiome.BASALT_DELTAS,
                    LevelBiome.DESERT, LevelBiome.BADLANDS};
                break;
            case GENTLE:
                options = new LevelBiome[]{
                    LevelBiome.WARPED, LevelBiome.DARK_FOREST, LevelBiome.JUNGLE,
                    LevelBiome.SWAMP, LevelBiome.THE_END};
                break;
            default:
                options = new LevelBiome[]{
                    LevelBiome.THE_END, LevelBiome.SNOWY, LevelBiome.WARPED,
                    LevelBiome.PLAINS};
                break;
        }
        return options[Math.floorMod(turn, options.length)];
    }

    private static void scheduleBiomes(@NonNull Level level, @NonNull List<BiomeZone> zones, int index) {
        if (index >= zones.size()) return;

        org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("ParkourBeat");
        if (plugin == null) {
            for (BiomeZone zone : zones) BiomeApplier.apply(level, zone, false);
            return;
        }

        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                // БЕЗ СОХРАНЕНИЯ НА КАЖДОЙ ЗОНЕ.
                //
                // Сохранение мира переписывает весь мир целиком, а не изменённый кусок.
                // На трёх десятках зон это тридцать полных сохранений подряд - сервер
                // встаёт колом на минуты. Сохраняем один раз, когда разложены все.
                BiomeApplier.apply(level, zones.get(index), false);
            } catch (Throwable ignored) {
                // Зона могла не влезть в лимит чанков - это не повод бросать остальные.
            }

            if (index + 1 >= zones.size()) {
                try {
                    level.getWorld().save();
                } catch (Throwable ignored) {
                }
                return;
            }
            scheduleBiomes(level, zones, index + 1);
        }, index == 0 ? 1L : 2L);
    }

    private static double rankOf(double[] sorted, double value) {
        if (sorted.length <= 1) return 0.5D;
        int index = Arrays.binarySearch(sorted, value);
        if (index < 0) index = -index - 1;
        return index / (double) (sorted.length - 1);
    }
}
