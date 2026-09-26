package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.DirectionChecker;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.Locale;

/**
 * АВТОРАЗМЕТКА.
 * <p>
 * Размечать четыреста нот руками человек согласится ровно один раз, поэтому черновик
 * карты кладётся автоматически, а строитель уже правит его под трек.
 * <p>
 * Здесь работает генератор по чистой сетке BPM: доли известны из темпа, дальше руды
 * раскладываются выбранным узором. Анализа самого звука тут нет - он приезжает
 * отдельно через питон-мост нарезки треков (onset-детект по трём полосам частот),
 * и его результат втекает в тот же {@link #placeNote} через список моментов.
 * Поэтому узоры и укладка написаны сразу так, чтобы не переписывать их потом.
 */
public final class DiggerAutoMapper {

    private DiggerAutoMapper() {
    }

    /** Узор укладки. */
    public enum Pattern {
        /** Слева направо и обратно: самый читаемый, годится под куплет. */
        ZIGZAG,
        /** Лесенка снизу вверх: заставляет тянуться прицелом. */
        STAIRS,
        /** Шахматка: два ряда через один, стандарт для быстрых кусков. */
        CHECKER,
        /** Стрим по центру: чистая долбёжка на одном месте, для дропа. */
        STREAM;

        @NonNull
        public static Pattern byName(@NonNull String raw) {
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return ZIGZAG;
            }
        }
    }

    /**
     * Быстрый запуск: сначала пробуем разбор трека, иначе ровную сетку.
     * <p>
     * Без выбранного трека не делаем ничего: раскладывать ноты не подо что, и весь
     * результат пришлось бы стирать.
     */
    public static void openOrRun(@NonNull ParkourBeat plugin, @NonNull Player player, @NonNull Level level) {
        ru.sortix.parkourbeat.player.music.MusicTrack track =
            level.getLevelSettings().getGameSettings().getMusicTrack();
        if (track == null) {
            player.sendMessage(PbText.of("&cСначала выбери трек уровня."));
            return;
        }

        DiggerAnalysis analysis = DiggerAnalysisStore.load(plugin, track.getId());
        if (analysis != null) {
            int placed = generateFromAudio(level, analysis, 0, true, true);
            player.sendMessage(PbText.of("&dРазметка по звуку&7: нот &f" + placed
                + "&7, BPM &f" + String.format("%.2f", analysis.getBpm())));
            return;
        }

        int placed = generate(level, 32, 2, Pattern.ZIGZAG, true);
        player.sendMessage(PbText.of("&dРазметка по сетке&7: нот &f" + placed
            + " &7на BPM &f" + level.getLevelSettings().getGameSettings()
            .getDiggerSettings().getBpm()));
        player.sendMessage(PbText.of("&8Разбора трека нет - темп взят из настроек карты."));
    }

    /**
     * Разложить руды по сетке бита.
     *
     * @param bars        сколько тактов размечать (такт - четыре доли)
     * @param subdivision дробление доли: 1 - на каждую долю, 2 - на половинки, 4 - на четверти
     * @param pattern     узор укладки
     * @param placeBlocks ставить ли блоки в мире, а не только регистрировать ноты
     * @return сколько нот реально положено
     */
    /**
     * Сколько тактов нужно, чтобы закрыть трек целиком.
     *
     * @param durationMillis длительность трека; 0 - неизвестна, тогда берётся значение по умолчанию
     */
    public static int barsFor(double bpm, int durationMillis) {
        if (durationMillis <= 0) return 32;
        double beatMillis = DiggerTuning.beatMillis(bpm);
        return Math.max(1, (int) Math.ceil(durationMillis / (beatMillis * 4.0D)));
    }

    public static int generate(@NonNull Level level,
                               int bars,
                               int subdivision,
                               @NonNull Pattern pattern,
                               boolean placeBlocks
    ) {
        DiggerLevelSettings settings = level.getLevelSettings().getGameSettings().getDiggerSettings();
        World world = level.getWorld();

        Location origin = settings.getOrigin(world);
        if (origin == null) {
            origin = defaultOrigin(level);
            settings.setOrigin(origin);
        }

        DirectionChecker checker = level.getLevelSettings().getDirectionChecker();
        DirectionChecker.Direction direction = checker.direction();
        boolean axisX = direction == DirectionChecker.Direction.POSITIVE_X
            || direction == DirectionChecker.Direction.NEGATIVE_X;
        int sign = checker.isNegative() ? -1 : 1;

        bars = Math.max(1, Math.min(512, bars));
        subdivision = Math.max(1, Math.min(4, subdivision));

        double blocksPerBeat = Math.max(0.5D, settings.blocksPerBeat());
        double step = blocksPerBeat / subdivision;

        int originX = origin.getBlockX();
        int originZ = origin.getBlockZ();
        int baseY = origin.getBlockY();

        int half = Math.max(1, settings.getTunnelWidth() / 2);
        int height = Math.max(1, settings.getTunnelHeight() - 1);

        int total = bars * 4 * subdivision;
        int placed = 0;

        // Столько первых долей пропускаем: иначе первая же нота стоит вплотную к
        // спавну, и увидеть её невозможно - забег начинается с промаха.
        double leadInBlocks = settings.resolveSpeed();

        for (int index = 0; index < total; index++) {
            int beat = index / subdivision;
            boolean onBeat = index % subdivision == 0;
            boolean barStart = onBeat && beat % 4 == 0;

            // Между долями ноты ставим не всегда, иначе куплет превращается в стену.
            if (!onBeat && pattern != Pattern.STREAM && index % 2 != 0) continue;

            int lane = lane(pattern, index, half);
            int y = baseY + verticalOffset(pattern, index, height);

            double distance = step * index;
            if (distance < leadInBlocks) continue;

            // СДВИГ НА ТОЧКУ УДАРА. Блок ставится не туда, где игрок окажется на долю,
            // а на столько блоков дальше, на сколько игрок тянется вперёд. Тогда в
            // момент, когда доля звучит, руда как раз оказывается на расстоянии удара,
            // и попадание в ритм совпадает с идеальным попаданием. Без этого сдвига
            // удар точно в бит считался опоздавшим на всю длину вытянутой руки.
            int along = (int) Math.round(distance + DiggerTuning.HIT_LEAD_BLOCKS) * sign;

            int x = axisX ? originX + along : originX + lane;
            int z = axisX ? originZ + lane : originZ + along;

            DiggerOre ore = pick(index, beat, barStart, subdivision);
            if (placeNote(settings, world, x, y, z, ore, placeBlocks)) placed++;
        }

        return placed;
    }


    // ==================== РАЗМЕТКА ПО ЗВУКУ ====================

    /**
     * Разложить руды по реальным ударам трека.
     * <p>
     * Здесь нет никакой сетки: каждая нота встаёт ровно туда, где по расчёту окажется
     * игрок в момент удара. Полоса частот решает высоту (бас внизу, хай-хэты вверху),
     * сила удара - какая это будет руда.
     *
     * @param minGapMillis минимальный промежуток между нотами; 0 - взять из темпа
     * @return сколько нот положено
     */
    public static int generateFromAudio(@NonNull Level level,
                                        @NonNull DiggerAnalysis analysis,
                                        int minGapMillis,
                                        boolean applyBpm,
                                        boolean placeBlocks
    ) {
        DiggerLevelSettings settings = level.getLevelSettings().getGameSettings().getDiggerSettings();
        World world = level.getWorld();

        if (applyBpm) {
            settings.setBpm(analysis.getBpm());
            // СМЕЩЕНИЕ ПЕРВОЙ ДОЛИ ЗДЕСЬ ОБНУЛЯЕТСЯ, И ЭТО НЕ ПОТЕРЯ ДАННЫХ.
            //
            // Анализатор уже притянул каждый удар к сетке, фаза которой в его времена
            // и заложена: таймкоды онсетов - это абсолютное время трека, а не отсчёт
            // от первой доли. Записать фазу ещё и сюда значило бы учесть её дважды.
            //
            // Именно это и происходило: разметка вычитала смещение из времени удара, а
            // забег вёз игрока по чистому времени трека, ничего обратно не прибавляя.
            // Вся карта уезжала вперёд ровно на фазу - на трёх сотнях миллисекунд это
            // читается как «бьёшь ровно, а засчитывается рано».
            settings.setFirstBeatOffsetMillis(0);
            settings.setTrackDurationMillis(analysis.getDurationMillis());
        }

        // По умолчанию не ближе восьмой доли: быстрее человек просто не успевает
        // навести прицел на новую руду.
        if (minGapMillis <= 0) {
            minGapMillis = (int) Math.round(DiggerTuning.beatMillis(settings.getBpm()) / 2.0D);
        }
        analysis.thin(minGapMillis);

        // ТОЧКА ОТСЧЁТА - СПАВН УРОВНЯ, а не координаты шаблона.
        //
        // Раньше сюда подставлялся барьер из нуля координат, и разметка ложилась за
        // сотню блоков от того места, где строитель на самом деле работает. Спавн
        // уровня - единственное место, про которое точно известно, что забег начнётся
        // именно там.
        Location origin = settings.getOrigin(world);
        if (origin == null) {
            origin = defaultOrigin(level);
            settings.setOrigin(origin);
        }

        DirectionChecker checker = level.getLevelSettings().getDirectionChecker();
        DirectionChecker.Direction direction = checker.direction();
        boolean axisX = direction == DirectionChecker.Direction.POSITIVE_X
            || direction == DirectionChecker.Direction.NEGATIVE_X;
        int sign = checker.isNegative() ? -1 : 1;

        int originX = origin.getBlockX();
        int originZ = origin.getBlockZ();
        int baseY = origin.getBlockY();

        int half = Math.max(1, settings.getTunnelWidth() / 2);
        int height = Math.max(1, settings.getTunnelHeight() - 1);
        double speed = settings.resolveSpeed();
        DiggerSpeedProfile profile = settings.speedProfile();

        // РАЗГОН ПЕРЕД ПЕРВОЙ НОТОЙ - тот же, что и у забега.
        //
        // Считается в БЛОКАХ, а не в секундах, и берётся из общей крутилки: забег
        // выбрасывает из судейства всё, что ближе этого расстояния к старту, и класть
        // туда ноты означало бы молча их терять. Раньше здесь стояла своя отдельная
        // секунда разгона, которой на медленном треке не хватало.
        double leadInBlocks = DiggerTuning.START_GAP_BLOCKS;

        // РАНГИ СИЛЫ. Считаются ОДИН РАЗ по всему списку, уже после прореживания:
        // руда выбирается по месту удара среди остальных, а не по абсолютной громкости.
        double[] sortedStrengths = new double[analysis.getOnsets().size()];
        for (int i = 0; i < sortedStrengths.length; i++) {
            sortedStrengths[i] = analysis.getOnsets().get(i).getStrength();
        }
        java.util.Arrays.sort(sortedStrengths);

        // ДРОП-НОТЫ ВЫБИРАЮТСЯ ЗАРАНЕЕ, А НЕ ПО ХОДУ.
        //
        // Раньше дроп ставился на любой удар, чей ранг выше порога и до которого прошло
        // достаточно времени. Порог был жёсткий - верхние полтора процента, - и на треке,
        // где сила ударов распределена ровно, в них не попадал НИ ОДИН удар: дроп-нот на
        // карте не оказывалось вовсе. Именно это и наблюдалось.
        //
        // Теперь наоборот: сначала берутся самые сильные удары трека, из них жадно
        // отбираются те, что стоят достаточно далеко друг от друга, и получившийся
        // список объявляется дропами. Их количество задано долей от длины трека, поэтому
        // дроп-ноты есть ВСЕГДА, и ровно там, где трек бьёт сильнее всего.
        java.util.Set<Integer> dropMillis = chooseDrops(analysis);

        int placed = 0;
        for (DiggerAnalysis.Onset onset : analysis.getOnsets()) {
            // Смещение первой доли уже учтено в таймкодах карты, поэтому его надо снять.
            double seconds = (onset.getMillis() - settings.getFirstBeatOffsetMillis()) / 1000.0D;
            if (seconds < 0.0D) continue;

            // РАССТОЯНИЕ БЕРЁТСЯ ИЗ ПРОФИЛЯ, А НЕ УМНОЖЕНИЕМ.
            //
            // При зонах скорости расстояние - это интеграл скорости по времени, и
            // забег считает его ровно тем же объектом. Любое расхождение здесь
            // означало бы ноты не там, где их ждёт судья.
            // ЗА КОНЕЦ ТРЕКА НОТ НЕ БЫВАЕТ.
            //
            // Согласование повторов дорисовывает удары там, где их не расслышал
            // детектор, и это правильно внутри песни. Но группа может зацепить и
            // последний такт, за которым идёт затухание: тогда в чистой тишине
            // появляются руды, взявшиеся из соседнего повтора. Слышно это сразу -
            // ноты есть, музыки нет.
            if (analysis.getDurationMillis() > 0
                && onset.getMillis() > analysis.getDurationMillis()) {
                continue;
            }

            double distance = profile.distanceAt(seconds * 1000.0D);
            if (distance < leadInBlocks) continue;

            int along = (int) Math.round(distance + DiggerTuning.HIT_LEAD_BLOCKS) * sign;

            // ЦЕНТРАЛЬНАЯ КОЛОНКА НЕ ИСПОЛЬЗУЕТСЯ. Через неё проходит сама камера:
            // руда, поставленная туда, влетает игроку прямо в лицо, и увидеть её
            // до удара невозможно - она закрывает собой весь обзор.
            // ПОЛОЖЕНИЕ НОТЫ ОПРЕДЕЛЯЕТСЯ МУЗЫКОЙ, А НЕ ПОРЯДКОВЫМ НОМЕРОМ.
            //
            // Здесь стоял счётчик index - номер ноты от начала карты, - и от его
            // чётности зависела сторона тоннеля. Из-за этого один и тот же проигрыш,
            // повторённый в треке трижды, каждый раз ложился ПО-РАЗНОМУ: перед вторым
            // повтором успевало пройти нечётное число нот, и вся фигура отражалась
            // зеркально. Играется это как три разных куска вместо одного знакомого -
            // именно то, что в ритм-играх убивает всё удовольствие от повторов.
            //
            // Теперь сторона считается из ПОЗИЦИИ УДАРА В ТАКТЕ. Одинаковые музыкальные
            // фразы стоят на одинаковых долях, значит и рисунок у них выходит один и
            // тот же - в какой бы части трека они ни встретились.
            int slot = beatSlot(onset.getMillis(), analysis.getBpm());

            int lane;
            int y;
            switch (onset.getBand()) {
                case LOW:
                    lane = (slot / 2) % 2 == 0 ? -2 : 2;
                    y = baseY;
                    break;
                case HIGH:
                    // Верхний ряд - НЕ потолок. Руда под самым сводом на пяти блоках
                    // впереди уходит за край обзора, и увидеть её нельзя: чтобы
                    // посмотреть туда, надо задрать голову и потерять весь тоннель.
                    lane = slot % 2 == 0 ? -2 : 2;
                    y = baseY + Math.min(2, height);
                    break;
                default:
                    lane = sideLane(slot, Math.max(2, Math.min(3, half)));
                    y = baseY + Math.min(1, height);
                    break;
            }

            int x = axisX ? originX + along : originX + lane;
            int z = axisX ? originZ + lane : originZ + along;

            boolean bass = onset.getBand() == DiggerAnalysis.Band.LOW;
            double rank = rankOf(sortedStrengths, onset.getStrength());

            // ДРОП-НОТА - СОБЫТИЕ, А НЕ ТИП УДАРА.
            //
            // Раньше она ставилась на любой удар силой выше 0.92, а на плотно сведённом
            // треке таких - половина карты. Полный залп шоу на каждом шагу перестаёт
            // читаться как акцент и превращается в мигание, за которым не видно руд.
            // Поэтому условий теперь два, и оба обязательны: удар должен входить в
            // верхний процент трека И отстоять от предыдущего дропа на несколько секунд.
            DiggerOre ore = dropMillis.contains(onset.getMillis())
                ? DiggerOre.ANCIENT_DEBRIS
                : DiggerOre.byRank(rank, bass);

            if (placeNote(settings, world, x, y, z, ore, placeBlocks)) placed++;
        }

        return placed;
    }

    /**
     * Номер шестнадцатой доли внутри такта, 0..15.
     * <p>
     * Это и есть «место удара в музыке»: от него зависит сторона тоннеля, поэтому
     * одинаковые фразы дают одинаковый рисунок, где бы в треке они ни встретились.
     * <p>
     * Округление, а не отбрасывание дробной части: время узла сетки - дробное число
     * миллисекунд, округлённое при записи, и отбрасывание то и дело соскакивало на
     * соседнюю шестнадцатую.
     */
    private static int beatSlot(int millis, double bpm) {
        if (bpm <= 0.0D) return 0;
        double sixteenth = 60000.0D / bpm / 4.0D;
        int slot = (int) Math.round(millis / sixteenth);
        return ((slot % 16) + 16) % 16;
    }

    /**
     * ВЫБРАТЬ МОМЕНТЫ ПОД ДРОП-НОТЫ.
     * <p>
     * Жадно, от самого сильного удара к слабому: каждый следующий берётся, только если
     * до всех уже взятых не меньше {@link DiggerTuning#DROP_MIN_GAP_MILLIS}. Так дропы
     * гарантированно приходятся на самые громкие места трека и при этом не сбиваются в
     * кучу.
     * <p>
     * Количество считается от ДЛИНЫ трека, а не от порога силы: на трёхминутной карте
     * это примерно десяток дропов - достаточно, чтобы они запомнились, и мало, чтобы
     * приелись. Порог по силе здесь не годится вовсе, потому что распределение силы у
     * каждого трека своё, а «сколько дропов на песню» - величина постоянная.
     */
    @NonNull
    private static java.util.Set<Integer> chooseDrops(@NonNull DiggerAnalysis analysis) {
        java.util.List<DiggerAnalysis.Onset> onsets = analysis.getOnsets();
        if (onsets.isEmpty()) return java.util.Collections.emptySet();

        int lastMillis = onsets.get(onsets.size() - 1).getMillis();
        int budget = Math.max(3, lastMillis / DROP_EVERY_MILLIS);

        java.util.List<DiggerAnalysis.Onset> strongest = new java.util.ArrayList<>(onsets);
        strongest.sort((a, b) -> Double.compare(b.getStrength(), a.getStrength()));

        java.util.List<Integer> chosen = new java.util.ArrayList<>(budget);
        for (DiggerAnalysis.Onset onset : strongest) {
            if (chosen.size() >= budget) break;

            boolean tooClose = false;
            for (int already : chosen) {
                if (Math.abs(onset.getMillis() - already) < DiggerTuning.DROP_MIN_GAP_MILLIS) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) chosen.add(onset.getMillis());
        }

        return new java.util.HashSet<>(chosen);
    }

    /** Примерно раз во сколько миллисекунд трека хочется видеть дроп-ноту. */
    private static final int DROP_EVERY_MILLIS = 18000;


    /**
     * Доля ударов, которые слабее данного.
     *
     * @param sorted силы всех ударов по возрастанию
     */
    private static double rankOf(double[] sorted, double strength) {
        if (sorted.length <= 1) return 1.0D;
        int index = java.util.Arrays.binarySearch(sorted, strength);
        if (index < 0) index = -index - 1;
        return index / (double) (sorted.length - 1);
    }

    /**
     * Колонка, гарантированно не совпадающая с камерой.
     * <p>
     * Ноль пропускается: ровно там летит голова игрока.
     */
    private static int sideLane(int index, int half) {
        // Ближе двух блоков к оси камеры руду не ставим: она оказывается вплотную к
        // лицу, закрывает обзор и физически не успевает попасть в поле зрения.
        int side = index % 2 == 0 ? -1 : 1;
        int distance = 2 + (index / 2) % Math.max(1, half - 1);
        return side * distance;
    }

    /** Откуда считать карту, если строитель точку отсчёта не ставил. */
    @NonNull
    public static Location defaultOrigin(@NonNull Level level) {
        Location spawn = level.getSpawn();
        if (spawn != null) return spawn.clone();
        return DiggerWorldTemplate.builderSpawn(level.getWorld());
    }

    /** Смещение поперёк тоннеля. */
    private static int lane(@NonNull Pattern pattern, int index, int half) {
        switch (pattern) {
            case STREAM:
                // Даже «стрим по центру» идёт не по центру: там летит камера.
                return index % 2 == 0 ? -1 : 1;
            case STAIRS:
                return -half + (index % (half * 2 + 1));
            case CHECKER:
                return index % 2 == 0 ? -half : half;
            case ZIGZAG:
            default: {
                int period = half * 4;
                int position = index % period;
                int value = position <= half * 2 ? position - half : half * 3 - position;
                return value == 0 ? 1 : value;
            }
        }
    }

    /** Смещение по высоте. */
    private static int verticalOffset(@NonNull Pattern pattern, int index, int height) {
        switch (pattern) {
            case STAIRS:
                return index % (height + 1);
            case CHECKER:
                return index % 4 < 2 ? 0 : Math.min(2, height);
            case STREAM:
                return Math.min(1, height);
            case ZIGZAG:
            default:
                return index % 3 == 0 ? Math.min(1, height) : 0;
        }
    }

    /**
     * Какая руда встанет на этот момент.
     * <p>
     * Акценты расставлены по музыкальной логике, а не случайно: начало такта заметно
     * сильнее середины, а первая доля каждого четвёртого такта - это почти всегда
     * либо смена части, либо дроп.
     */
    @NonNull
    private static DiggerOre pick(int index, int beat, boolean barStart, int subdivision) {
        if (barStart && beat % 16 == 0) return DiggerOre.ANCIENT_DEBRIS;
        if (barStart) return DiggerOre.DIAMOND;
        if (index % subdivision == 0) return beat % 2 == 0 ? DiggerOre.REDSTONE : DiggerOre.IRON;
        return DiggerOre.COAL;
    }

    /** Положить одну ноту. Возвращает false, если место занято или карта переполнена. */
    private static boolean placeNote(@NonNull DiggerLevelSettings settings,
                                     @NonNull World world,
                                     int x, int y, int z,
                                     @NonNull DiggerOre ore,
                                     boolean placeBlocks
    ) {
        if (settings.isNote(x, y, z)) return false;
        if (!settings.addNote(new DiggerNote(x, y, z, ore))) return false;

        if (placeBlocks) {
            Block block = world.getBlockAt(x, y, z);
            block.setType(ore.getMaterial(), false);
        }
        return true;
    }
}
