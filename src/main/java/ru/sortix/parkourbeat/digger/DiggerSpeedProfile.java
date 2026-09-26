package ru.sortix.parkourbeat.digger;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ПЕРЕВОД ВРЕМЕНИ В РАССТОЯНИЕ, КОГДА СКОРОСТЬ НЕПОСТОЯННА.
 * <p>
 * ЭТО САМАЯ ХРУПКАЯ ЧАСТЬ ЗОН СКОРОСТИ, и вот почему. Пока скорость одна на весь трек,
 * расстояние - это просто время, умноженное на неё, и разметка с забегом считают его
 * одинаково, даже не подозревая друг о друге. Стоит скорости начать меняться - и
 * умножение перестаёт работать: расстояние становится ИНТЕГРАЛОМ скорости по времени.
 * Если разметка и забег посчитают этот интеграл хоть немного по-разному, ноты окажутся
 * не там, где их ждёт судья, и промахи посыплются на ровном месте.
 * <p>
 * Поэтому обе стороны обязаны ходить сюда, и только сюда. Класс умеет ровно три вещи:
 * <ul>
 *     <li>{@link #distanceAt} - сколько блоков проехано к этому моменту трека;</li>
 *     <li>{@link #millisAt} - обратное, в какой момент игрок окажется на этом
 *         расстоянии. Именно им забег превращает координату руды обратно в таймкод;</li>
 *     <li>{@link #speedAt} - мгновенная скорость, по ней считаются окна попадания.</li>
 * </ul>
 * Внутри - кусочно-линейная функция: зоны режутся на непересекающиеся отрезки, и для
 * каждой границы один раз считается накопленное расстояние. Дальше оба перевода -
 * это двоичный поиск по границам и одно умножение, то есть операция постоянной цены
 * даже на сотне зон.
 */
public final class DiggerSpeedProfile {

    /** Граница отрезка: момент трека, накопленное к нему расстояние и скорость дальше. */
    private static final class Segment {
        final double startMillis;
        final double startDistance;
        final double speed;

        Segment(double startMillis, double startDistance, double speed) {
            this.startMillis = startMillis;
            this.startDistance = startDistance;
            this.speed = speed;
        }
    }

    private final double baseSpeed;
    private final List<Segment> segments;

    private DiggerSpeedProfile(double baseSpeed, List<Segment> segments) {
        this.baseSpeed = baseSpeed;
        this.segments = segments;
    }

    /** Профиль без единой зоны: скорость постоянна, как было до всей этой затеи. */
    @NonNull
    public static DiggerSpeedProfile flat(double baseSpeed) {
        double speed = Math.max(0.01D, baseSpeed);
        List<Segment> only = new ArrayList<>(1);
        only.add(new Segment(0.0D, 0.0D, speed));
        return new DiggerSpeedProfile(speed, only);
    }

    /**
     * Собрать профиль из базовой скорости и списка зон.
     * <p>
     * Зоны приводятся в порядок здесь, а не при вводе: строитель может двигать их как
     * угодно, а список обязан оставаться корректным. Пересечения режутся по принципу
     * «кто начался раньше, тот и прав» - иначе на стыке двух зон скорость зависела бы
     * от порядка в файле, а это ровно тот сорт ошибок, который потом ищут неделю.
     */
    @NonNull
    public static DiggerSpeedProfile of(double baseSpeed, @NonNull List<DiggerSpeedZone> zones) {
        double speed = Math.max(0.01D, baseSpeed);
        if (zones.isEmpty()) return flat(speed);

        List<DiggerSpeedZone> sorted = new ArrayList<>(zones);
        sorted.sort((a, b) -> Integer.compare(a.getStartMillis(), b.getStartMillis()));

        List<Segment> segments = new ArrayList<>(sorted.size() * 2 + 1);
        double cursorMillis = 0.0D;
        double cursorDistance = 0.0D;

        for (DiggerSpeedZone zone : sorted) {
            double from = Math.max(cursorMillis, zone.getStartMillis());
            double to = zone.getEndMillis();
            if (to <= from) continue;

            // Дырка перед зоной едется базовой скоростью.
            if (from > cursorMillis) {
                segments.add(new Segment(cursorMillis, cursorDistance, speed));
                cursorDistance += (from - cursorMillis) / 1000.0D * speed;
                cursorMillis = from;
            }

            double zoneSpeed = speed * zone.getMultiplier();
            segments.add(new Segment(cursorMillis, cursorDistance, zoneSpeed));
            cursorDistance += (to - cursorMillis) / 1000.0D * zoneSpeed;
            cursorMillis = to;
        }

        // Хвост после последней зоны - снова базовая скорость, и так до конца трека.
        segments.add(new Segment(cursorMillis, cursorDistance, speed));

        if (segments.isEmpty() || segments.get(0).startMillis > 0.0D) {
            segments.add(0, new Segment(0.0D, 0.0D, speed));
        }
        return new DiggerSpeedProfile(speed, segments);
    }

    public double getBaseSpeed() {
        return this.baseSpeed;
    }

    /** Есть ли вообще что-то, кроме постоянной скорости. */
    public boolean isFlat() {
        return this.segments.size() <= 1;
    }

    /** Сколько блоков от старта проехано к этому моменту трека. */
    public double distanceAt(double millis) {
        if (millis <= 0.0D) return 0.0D;
        Segment segment = this.segmentByMillis(millis);
        return segment.startDistance + (millis - segment.startMillis) / 1000.0D * segment.speed;
    }

    /**
     * В какой момент трека игрок окажется на этом расстоянии.
     * <p>
     * Строго обратна {@link #distanceAt}: функция монотонная, потому что скорость
     * всегда положительна, значит обратная существует и однозначна.
     */
    public double millisAt(double distance) {
        if (distance <= 0.0D) return 0.0D;
        Segment segment = this.segmentByDistance(distance);
        return segment.startMillis + (distance - segment.startDistance) / segment.speed * 1000.0D;
    }

    /** Мгновенная скорость в этот момент трека, блоков в секунду. */
    public double speedAt(double millis) {
        return this.segmentByMillis(Math.max(0.0D, millis)).speed;
    }

    /** Скорость в точке, заданной расстоянием, а не временем. */
    public double speedAtDistance(double distance) {
        return this.segmentByDistance(Math.max(0.0D, distance)).speed;
    }

    private Segment segmentByMillis(double millis) {
        int low = 0;
        int high = this.segments.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (this.segments.get(mid).startMillis <= millis) low = mid;
            else high = mid - 1;
        }
        return this.segments.get(low);
    }

    private Segment segmentByDistance(double distance) {
        int low = 0;
        int high = this.segments.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (this.segments.get(mid).startDistance <= distance) low = mid;
            else high = mid - 1;
        }
        return this.segments.get(low);
    }

    // ==================== АВТОПОДБОР ====================

    /**
     * ПОДОБРАТЬ ЗОНЫ ПО САМОМУ ТРЕКУ.
     * <p>
     * Идея простая: там, где ударов много и они сильные, музыка плотная - и ехать надо
     * быстрее, чтобы руды не наползали друг на друга и чтобы кусок читался как
     * кульминация. Там, где редко и тихо, - медленнее, иначе проигрыш проносится мимо
     * пустым коридором.
     * <p>
     * Трек режется на окна по {@link #WINDOW_MILLIS}, в каждом считается плотность
     * ударов и их средняя сила. Обе величины переводятся в ранг среди всех окон - то
     * же решение, что и с силой отдельного удара: абсолютные пороги не переживают
     * смену жанра, ранги переживают.
     * <p>
     * Соседние окна с близким множителем СКЛЕИВАЮТСЯ. Без этого получилась бы сотня
     * зон по восемь секунд, каждая чуть быстрее предыдущей, и вместо музыкальной формы
     * вышла бы дрожь.
     */
    private static final int WINDOW_MILLIS = 8000;

    /**
     * Насколько сильно автоподбор позволяет себе отклоняться от базовой скорости.
     * <p>
     * 0.35 вместо прежних 0.18. Восемнадцать процентов - это разница, которую видно на
     * приборах и не видно в игре: между тихим куском и припевом скорость менялась с
     * 7.8 до 9.2 блока в секунду, и человек этого просто не замечал.
     * <p>
     * На трети разброс становится честным: спокойный кусок едется в полтора раза
     * медленнее припева, и смена части песни ЧУВСТВУЕТСЯ телом, а не только слышится.
     * Выше трети идти нельзя - окно попадания в быстрых зонах начинает требовать
     * реакции, которой у человека на среднем канале связи попросту нет.
     */
    private static final double AUTO_RANGE = 0.35D;

    /** Ближе этого множители считаются одинаковыми и окна склеиваются. */
    private static final double MERGE_EPSILON = 0.04D;

    @NonNull
    public static List<DiggerSpeedZone> suggest(@NonNull DiggerAnalysis analysis) {
        List<DiggerAnalysis.Onset> onsets = analysis.getOnsets();
        if (onsets.size() < 8) return Collections.emptyList();

        int lastMillis = onsets.get(onsets.size() - 1).getMillis();
        int windows = lastMillis / WINDOW_MILLIS + 1;
        if (windows < 3) return Collections.emptyList();

        double[] energy = new double[windows];
        int[] counts = new int[windows];

        for (DiggerAnalysis.Onset onset : onsets) {
            int window = onset.getMillis() / WINDOW_MILLIS;
            if (window < 0 || window >= windows) continue;
            energy[window] += onset.getStrength();
            counts[window]++;
        }

        // Плотность и сила вместе: окно из двух мощных ударов не должно обгонять окно
        // из двадцати средних, и наоборот.
        double[] score = new double[windows];
        for (int i = 0; i < windows; i++) {
            score[i] = counts[i] == 0 ? 0.0D : energy[i] * Math.sqrt(counts[i]);
        }

        double[] sorted = score.clone();
        java.util.Arrays.sort(sorted);

        double[] multipliers = new double[windows];
        for (int i = 0; i < windows; i++) {
            double rank = rankOf(sorted, score[i]);

            // Ранг 0.5 - ровно базовая скорость, края - плюс-минус AUTO_RANGE.
            double multiplier = 1.0D + (rank - 0.5D) * 2.0D * AUTO_RANGE;

            // САМЫЕ ТИХИЕ МЕСТА ТОРМОЗЯТ СИЛЬНЕЕ ОСТАЛЬНЫХ.
            //
            // Линейная шкала даёт проигрышу ту же скидку, что и рядовому куплету, хотя
            // это совершенно разные вещи: в куплете ноты есть и ехать надо, а в чистом
            // проигрыше ехать не за чем, и полная скорость там читается как пустой
            // коридор. Нижняя пятая часть трека получает дополнительное замедление,
            // и провал в музыке становится провалом в движении.
            if (rank < 0.20D) {
                multiplier -= (0.20D - rank) / 0.20D * 0.15D;
            }

            multipliers[i] = Math.max(DiggerSpeedZone.MIN_MULTIPLIER, multiplier);
        }

        smooth(multipliers);

        List<DiggerSpeedZone> zones = new ArrayList<>();
        int from = 0;
        for (int i = 1; i <= windows; i++) {
            boolean last = i == windows;
            if (!last && Math.abs(multipliers[i] - multipliers[from]) < MERGE_EPSILON) continue;

            double sum = 0.0D;
            for (int j = from; j < i; j++) sum += multipliers[j];
            double merged = sum / (i - from);

            int startMillis = from * WINDOW_MILLIS;
            int endMillis = i * WINDOW_MILLIS;

            // Зоны вокруг единицы не пишем вовсе: пустая зона «ехать как обычно» только
            // засоряет список строителю, а на движение не влияет никак.
            if (Math.abs(merged - 1.0D) >= MERGE_EPSILON
                && endMillis - startMillis >= DiggerSpeedZone.MIN_LENGTH_MILLIS) {
                zones.add(new DiggerSpeedZone(startMillis, endMillis, merged));
            }
            from = i;
        }

        return zones;
    }

    /**
     * Сгладить множители по трём соседним окнам.
     * <p>
     * Скорость обязана меняться плавно: резкий скачок посреди трека игрок читает как
     * лаг сервера, а не как задумку. Одного прохода хватает - он убирает одиночные
     * выбросы и оставляет форму.
     */
    private static void smooth(double[] values) {
        if (values.length < 3) return;
        double[] copy = values.clone();
        for (int i = 0; i < values.length; i++) {
            double sum = copy[i];
            int count = 1;
            if (i > 0) {
                sum += copy[i - 1];
                count++;
            }
            if (i < values.length - 1) {
                sum += copy[i + 1];
                count++;
            }
            values[i] = sum / count;
        }
    }

    private static double rankOf(double[] sorted, double value) {
        if (sorted.length <= 1) return 0.5D;
        int index = java.util.Arrays.binarySearch(sorted, value);
        if (index < 0) index = -index - 1;
        return index / (double) (sorted.length - 1);
    }
}
