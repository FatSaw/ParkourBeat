package ru.sortix.parkourbeat.digger;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.player.PingManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * СУДЬЯ: ПРЕВРАЩАЕТ МОМЕНТ КЛИКА В РАНГ.
 * <p>
 * Задача ровно одна - сделать так, чтобы человек с пингом 150 играл наравне с
 * человеком с пингом 15. Работает это в три слоя:
 * <ol>
 *     <li>КОМПЕНСАЦИЯ. Пакет разрушения приходит на сервер спустя половину RTT после
 *         того, как игрок реально нажал кнопку. Половина пинга вычитается из времени
 *         прихода - ровно как хитрег с откатом в шутерах.</li>
 *     <li>ОКНА ПО ДЖИТТЕРУ. Компенсация убирает постоянную задержку, но не её дрожание.
 *         Дрожание измеряется по истории пинга, и окна расширяются на него с потолком.</li>
 *     <li>ЛИЧНЫЙ ОФФСЕТ. У всех разное железо, наушники и задержка вывода звука.
 *         По первым нотам считается медиана отклонений и дальше вычитается как
 *         постоянная поправка - это ровно то, что в ритм-играх делает калибровка.</li>
 * </ol>
 */
public class DiggerJudge {

    private final @NonNull Player player;
    private final @NonNull PingManager pingManager;

    /** Сглаженный пинг, чтобы одиночный выброс трекера не сдвинул судейство. */
    private double smoothedPing = -1.0D;

    /** Среднее отклонение пинга от сглаженного - оно же джиттер. */
    @Getter
    private double jitterMillis = 0.0D;

    private final Deque<Integer> pingHistory = new ArrayDeque<>();
    private static final int PING_HISTORY = 20;

    /** Сколько раз уже мерили пинг. Пока замеров мало, дрожание набирается быстрее. */
    private int pingSamples = 0;

    /** Оценки задержки по последним попаданиям - по их медиане и живёт поправка. */
    private final List<Long> calibrationSamples = new ArrayList<>();

    @Getter
    private long personalOffsetMillis = 0L;

    @Getter
    private boolean calibrated = false;

    /**
     * Пришёл ли судья на забег с ГОТОВОЙ поправкой.
     * <p>
     * Если да - двигать её резко нельзя: она уже верна, а первые ноты забега шумят
     * не меньше остальных. Если нет - наоборот, надо как можно быстрее нащупать.
     */
    private final boolean warmStart;

    // ==================== ПАМЯТЬ ПОПРАВКИ ====================

    /**
     * ПОПРАВКИ, ВЫУЧЕННЫЕ В ПРОШЛЫХ ЗАБЕГАХ.
     * <p>
     * Задержка игрока - это его наушники, его монитор и его канал. Она не меняется
     * между забегами, а прежний судья каждый раз начинал с нуля и заново её нащупывал.
     * Ровно поэтому начало забега судилось строже конца: первые ноты приходили
     * НЕКОМПЕНСИРОВАННЫМИ.
     * <p>
     * Хранится в памяти на время работы сервера. Переживать перезапуск здесь нечему:
     * после него поправка набирается заново за одну-две ноты, а не за полтора десятка.
     */
    private static final Map<UUID, Long> LEARNED_OFFSETS = new ConcurrentHashMap<>();

    /**
     * ОБЩАЯ ПОПРАВКА СЕРВЕРА - с чего начинает тот, кто играет впервые.
     * <p>
     * В личной задержке есть часть, одинаковая у всех: такт сервера, интерполяция
     * носителя на клиенте, дорога пакета туда и обратно. Она видна в поправках уже
     * игравших, и новичку незачем открывать её заново - он начинает со среднего по
     * серверу и дальше уводит его под себя.
     */
    private static volatile double globalOffsetMillis = 0.0D;

    /** Забыть выученное - например, когда игрок ушёл с сервера. */
    public static void forget(@NonNull UUID uuid) {
        LEARNED_OFFSETS.remove(uuid);
    }

    public DiggerJudge(@NonNull ParkourBeat plugin, @NonNull Player player) {
        this.player = player;
        this.pingManager = plugin.get(PingManager.class);

        Long learned = DiggerTuning.CALIBRATION_MEMORY
            ? LEARNED_OFFSETS.get(player.getUniqueId())
            : null;

        if (learned != null) {
            this.personalOffsetMillis = clampOffset(learned);
            this.warmStart = true;
            this.calibrated = true;
        } else {
            // Даже у новичка старт не с нуля: общая для сервера часть задержки известна.
            this.personalOffsetMillis = DiggerTuning.CALIBRATION_MEMORY
                ? clampOffset(Math.round(globalOffsetMillis))
                : 0L;
            this.warmStart = false;
            this.calibrated = DiggerTuning.AUTO_CALIBRATION_NOTES <= 0;
        }
    }

    private static long clampOffset(long value) {
        long limit = DiggerTuning.MAX_PERSONAL_OFFSET_MS;
        return Math.max(-limit, Math.min(limit, value));
    }

    // ==================== ПИНГ ====================

    /**
     * ЗАМЕР ПИНГА - РОВНО РАЗ ЗА ТИК, ИЗ ИГРОВОГО ЦИКЛА.
     * <p>
     * Раньше замер жил внутри {@code currentPing()}, а тот вызывался из судейства - и
     * значит СГЛАЖИВАНИЕ ШЛО ПО ПОПАДАНИЯМ, а не по времени. На плотном куске карты
     * пинг пересчитывался десять раз в секунду, на паузе - ни разу. Оценка дрожания
     * получалась не из сети, а из плотности нот: там, где нот много, окна судейства
     * сами собой разъезжались, а на редких нотах схлопывались.
     * <p>
     * Хуже того, за один удар вызовов было два - в компенсации и в пересчёте времени
     * клика, - и каждый двигал сглаживание. Теперь замер один и по часам, а судейство
     * только ЧИТАЕТ готовые числа.
     */
    public void tick() {
        int ping;
        try {
            ping = this.pingManager.getPing(this.player);
        } catch (Throwable e) {
            ping = 0;
        }
        if (ping < 0) ping = 0;
        ping = Math.min(ping, DiggerTuning.PING_CAP);

        this.pingSamples++;

        if (this.smoothedPing < 0.0D) {
            this.smoothedPing = ping;
        } else {
            // ДРОЖАНИЕ НАБИРАЕТСЯ БЫСТРО, А ПОТОМ УСПОКАИВАЕТСЯ.
            //
            // Оценка стартовала с нуля и ползла к настоящей величине десятками секунд.
            // А ведь на неё расширяются окна: пока она ноль, окна УЖЕ настоящих, и
            // первые ноты судятся строже последних. Человек бьёт одинаково весь трек,
            // а получает +100 в начале и +300 в конце - при том что дело не в нём.
            //
            // Поэтому первые замеры весят много: к первой ноте оценка уже близка к
            // правде. Дальше вес падает до прежнего, иначе одиночный лаг дёргал бы окна.
            double alpha = this.pingSamples < 30 ? 0.35D : 0.1D;
            this.jitterMillis += (Math.abs(ping - this.smoothedPing) - this.jitterMillis) * alpha;
            this.smoothedPing += (ping - this.smoothedPing) * 0.15D;
        }

        this.pingHistory.addLast(ping);
        while (this.pingHistory.size() > PING_HISTORY) this.pingHistory.removeFirst();
    }

    /** Сглаженный пинг. Только чтение: замер делает {@link #tick()}. */
    public int currentPing() {
        if (this.smoothedPing < 0.0D) return 0;
        return (int) Math.round(this.smoothedPing);
    }

    /**
     * Насколько удар пришёл позже, чем был сделан: половина пинга плюс личная поправка.
     * <p>
     * Нужна там, где судейство идёт не по часам, а по пройденному расстоянию: скорость
     * известна, и миллисекунды легко пересчитать в блоки.
     */
    public long compensationMillis() {
        long value = this.personalOffsetMillis;
        // ПОЛНЫЙ ПИНГ, А НЕ ПОЛОВИНА.
        //
        // Камера игрока - это позиция носителя, которую ему ПРИСЫЛАЕТ сервер: картинка
        // уже опаздывает на путь «сервер -> клиент». Потом удар летит обратно
        // «клиент -> сервер». Задержка складывается из обоих концов, то есть это весь
        // RTT. Половина компенсировала только обратный путь, и при 170 мс у игрока
        // стабильно висели ~85 мс некомпенсированного опоздания.
        if (DiggerTuning.PING_COMPENSATION) value += this.currentPing();
        return value;
    }

    /**
     * Момент, когда игрок РЕАЛЬНО нажал кнопку.
     *
     * @param arrivalMillis когда пакет разрушения дошёл до сервера
     */
    public long realClickMillis(long arrivalMillis) {
        if (!DiggerTuning.PING_COMPENSATION) return arrivalMillis - this.personalOffsetMillis;
        return arrivalMillis - this.currentPing() - this.personalOffsetMillis;
    }

    // ==================== РАНГ ====================

    /**
     * @param deltaMillis отклонение удара от таймкода ноты: минус - раньше, плюс - позже
     * @param rare        редкая ли руда: у них окна уже
     */
    /**
     * Ранг по отклонению В БЛОКАХ.
     *
     * @param deltaBlocks минус - ударил раньше, чем руда дошла до точки удара
     * @param speed       блоков в секунду: нужна, чтобы учесть дрожание пинга
     */
    @NonNull
    public DiggerHit judge(double deltaBlocks, double speed, boolean rare) {
        double abs = Math.abs(deltaBlocks);

        // Дрожание пинга - это миллисекунды, переводим его в блоки по скорости.
        double jitterMillis = Math.min(this.jitterMillis * DiggerTuning.JITTER_WINDOW_FACTOR,
            DiggerTuning.JITTER_WINDOW_CAP_MS);

        // ПОБЛАЖКА ЗА СКОРОСТЬ И ПИНГ.
        //
        // Окна заданы в блоках, а значит на быстрой карте они дают МЕНЬШЕ времени: те же
        // полблока при 10 блоках в секунду - это 50 миллисекунд вместо 64 при восьми.
        // Человек на этом не выигрывает ничего, он просто обязан попадать точнее ровно
        // там, где всё и так летит быстрее. А если у него ещё и пинг, то компенсация
        // снимает среднюю задержку, но не дрожание вокруг неё - и на скорости это
        // дрожание стоит дороже.
        //
        // Поэтому окна растягиваются пропорционально превышению скорости над базовой и
        // отдельно - по пингу. Не «чтобы было легче», а чтобы цена ошибки в миллисекундах
        // не зависела от того, насколько быстрый кусок трека сейчас играет.
        double relief = this.relief(speed);
        double jitterBlocks = jitterMillis * speed / 1000.0D;
        double factor = (rare ? DiggerTuning.RARE_WINDOW_FACTOR : 1.0D) * relief;

        if (abs <= DiggerTuning.PERFECT_BLOCKS * factor + jitterBlocks) return DiggerHit.PERFECT;
        if (abs <= DiggerTuning.GOOD_BLOCKS * factor + jitterBlocks) return DiggerHit.GOOD;
        if (abs <= DiggerTuning.OK_BLOCKS * factor + jitterBlocks) return DiggerHit.OK;
        return DiggerHit.MISS;
    }

    /** Поблажка за скорость и пинг. Одна на судейство и на пропуск нот. */
    private double relief(double speed) {
        double speedRelief = 1.0D;
        if (speed > DiggerTuning.RELIEF_BASE_SPEED) {
            speedRelief += (speed / DiggerTuning.RELIEF_BASE_SPEED - 1.0D)
                * DiggerTuning.SPEED_RELIEF_FACTOR;
        }
        double ping = Math.max(0.0D, this.smoothedPing);
        double pingRelief = 1.0D + Math.min(ping, DiggerTuning.PING_RELIEF_CAP_MS)
            / 1000.0D * DiggerTuning.PING_RELIEF_FACTOR;
        return Math.min(DiggerTuning.RELIEF_LIMIT, speedRelief * pingRelief);
    }

    /**
     * САМОЕ ПОЗДНЕЕ ПОЛОЖЕНИЕ КАМЕРЫ (в «сыром» отклонении, без компенсации), при
     * котором удар по ноте ещё может прийти и быть засчитан.
     * <p>
     * Раньше пропуск считался по голому окну OK - без поблажки и без компенсации пинга,
     * а сам удар судился С НИМИ. У игрока с пингом удар, который судья засчитал бы,
     * физически не успевал долететь: тик уже записал ноту в промахи, а пришедший следом
     * удар отбрасывался как повторный. Теперь граница пропуска - это ровно граница
     * судейства плюс вся задержка удара плюс такт запаса.
     */
    public double missWindowBlocks(double speed, boolean rare) {
        double jitterBlocks = Math.min(this.jitterMillis * DiggerTuning.JITTER_WINDOW_FACTOR,
            DiggerTuning.JITTER_WINDOW_CAP_MS) * speed / 1000.0D;
        double factor = (rare ? DiggerTuning.RARE_WINDOW_FACTOR : 1.0D) * this.relief(speed);
        double compensationBlocks = Math.max(0L, this.compensationMillis()) * speed / 1000.0D;
        double tickBlocks = speed * 0.05D;
        return DiggerTuning.OK_BLOCKS * factor + jitterBlocks + compensationBlocks + tickBlocks;
    }

    // ==================== КАЛИБРОВКА ====================

    /**
     * Скормить судье отклонение засчитанного удара.
     * <p>
     * Берётся именно медиана, а не среднее: одна нота, по которой человек ударил
     * с большим опозданием, не должна утянуть поправку за собой.
     *
     * @param deltaMillis остаток - насколько удар разошёлся с нотой ПОСЛЕ вычета
     *                    действующей поправки
     */
    public void feedCalibration(long deltaMillis) {
        if (Math.abs(deltaMillis) > DiggerTuning.OK_MS * 2L) return;

        // ПОПРАВКА ПРИМЕНЯЕТСЯ С ПЕРВОЙ ЖЕ НОТЫ, А НЕ С ВОСЬМОЙ.
        //
        // Раньше здесь стоял выход: пока не набралось восемь отклонений, поправка
        // оставалась нулевой. То есть первые СЕМЬ нот заведомо судились без учёта
        // задержки игрока, а дальше поправка ползла к цели по 35% за ноту - до
        // настоящей величины она добиралась нот к четырнадцати.
        //
        // Именно это и видно в игре: начало забега - «+100», «+50», а к середине те же
        // самые удары дают «+300». Игрок не начинает играть лучше; это судья наконец
        // перестаёт считать его задержку его ошибкой.
        //
        // Калибровка при этом по-прежнему идёт ВЕСЬ забег, а не замирает: задержка не
        // постоянна, пинг плавает, клиент проседает по кадрам. Меняется только скорость
        // схождения - с пустыми руками поправка нащупывается за одну-две ноты, а когда
        // набрана, двигается медленно и не дёргается от одного смазанного удара.

        // В ОКНЕ ЛЕЖИТ ОЦЕНКА ЗАДЕРЖКИ, А НЕ ОСТАТОК.
        //
        // Сюда приходит отклонение, посчитанное УЖЕ С УЧЁТОМ действующей поправки, то
        // есть остаток. Прежний код складывал остатки в окно и брал их медиану за
        // искомое значение целиком. Ошибки тут две.
        //
        // Первая: точка равновесия у такой формулы одна - поправка равна остатку, а
        // остаток равен «настоящая задержка минус поправка». Отсюда поправка выходила
        // РОВНО ПОЛОВИНОЙ задержки, и судья до конца забега компенсировал половину
        // того, что должен был.
        //
        // Вторая: поправка меняется от ноты к ноте, значит остатки в окне посчитаны
        // при РАЗНЫХ поправках и складывать их вместе нельзя. Поэтому в окно кладётся
        // сумма - действующая поправка плюс остаток, - и это уже оценка самой задержки,
        // одинаковая по смыслу для всех записей окна.
        this.calibrationSamples.add(this.personalOffsetMillis + deltaMillis);
        while (this.calibrationSamples.size() > CALIBRATION_WINDOW) {
            this.calibrationSamples.remove(0);
        }

        List<Long> sorted = new ArrayList<>(this.calibrationSamples);
        Collections.sort(sorted);
        long target = clampOffset(sorted.get(sorted.size() / 2));

        // Двигаемся к цели плавно: рывок поправки посреди трека сам по себе сбивает.
        // Но шаг не постоянный - он тем крупнее, чем меньше судья пока знает.
        long step = Math.round((target - this.personalOffsetMillis) * this.gain());

        // ПОТОЛОК НА ОДИН ШАГ.
        //
        // Без него первая же нота, смазанная на треть секунды, уводила бы поправку в
        // упор и портила следующие три. С потолком крупная задержка всё равно
        // набирается за пару нот, а случайный промах по времени стоит одной ноты.
        long cap = this.warmStart ? 30L : 110L;
        if (step > cap) step = cap;
        if (step < -cap) step = -cap;

        this.personalOffsetMillis = clampOffset(this.personalOffsetMillis + step);

        if (this.calibrationSamples.size() >= DiggerTuning.AUTO_CALIBRATION_NOTES) {
            this.calibrated = true;
        }
        this.remember();
    }

    /**
     * НАСКОЛЬКО РЕЗКО ДВИГАТЬ ПОПРАВКУ.
     * <p>
     * Пришли с готовой - двигаем еле-еле, она уже верна. Пришли пустыми - первые ноты
     * тянем почти целиком, потому что каждая нота, отсуженная без поправки, это
     * незаслуженное «+100».
     */
    private double gain() {
        if (this.warmStart) return 0.30D;
        int samples = this.calibrationSamples.size();
        if (samples <= 1) return 0.75D;
        if (samples <= 4) return 0.55D;
        if (samples < DiggerTuning.AUTO_CALIBRATION_NOTES) return 0.45D;
        return 0.30D;
    }

    /**
     * Отложить поправку до следующего забега - и подмешать её в общую по серверу.
     * <p>
     * Общая ведёт себя осторожно: она нужна тем, кто ещё ни разу не играл, и ошибиться
     * ей дороже, чем личной.
     */
    private void remember() {
        if (!DiggerTuning.CALIBRATION_MEMORY) return;
        LEARNED_OFFSETS.put(this.player.getUniqueId(), this.personalOffsetMillis);
        globalOffsetMillis += (this.personalOffsetMillis - globalOffsetMillis) * 0.02D;
    }

    /** По скольким последним ударам считается поправка. */
    private static final int CALIBRATION_WINDOW = 24;

    /** Сколько нот осталось до конца калибровки. */
    public int calibrationLeft() {
        if (this.calibrated) return 0;
        return Math.max(0, DiggerTuning.AUTO_CALIBRATION_NOTES - this.calibrationSamples.size());
    }
}
