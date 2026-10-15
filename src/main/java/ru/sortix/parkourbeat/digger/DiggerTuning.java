package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import ru.sortix.parkourbeat.ParkourBeat;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ВСЕ КРУТИЛКИ 2D-РЕЖИМА В ОДНОМ МЕСТЕ.
 * <p>
 * Это ровно те «удобные переменные», о которых просил заказчик: поворот кубика,
 * угол камеры, физика прыжка, компенсация пинга. Значения живут в обычных статических
 * полях (то есть их видно и правится прямо в коде), но дополнительно читаются и
 * пишутся в config.yml в секцию {@code two_d} - чтобы админ мог крутить их прямо на
 * сервере командой {@code /pb 2d set <ключ> <значение>} без пересборки плагина.
 * <p>
 * Ничего из этого не относится к конкретному уровню: у уровня свои настройки в
 * {@link TwoDLevelSettings} (спавн кубика и длина линии).
 */
public final class DiggerTuning {
    private DiggerTuning() {
    }

    // ==================== СКОРОСТЬ И СЕТКА ====================

    /**
     * СКОЛЬКО БЛОКОВ ЗАНИМАЕТ ОДНА ДОЛЯ.
     * <p>
     * Это и есть связь музыки с геометрией. При 4 блоках на долю четверть доли -
     * ровно один блок, и строитель физически не может поставить руду мимо ритма.
     * Для быстрых жанров (dnb, 170+ BPM) имеет смысл 2, иначе скорость становится
     * неиграбельной.
     */
    public static double BLOCKS_PER_BEAT = 4.0D;

    /** Ниже этой скорости уровень превращается в слайд-шоу. */
    public static double MIN_SPEED = 2.0D;

    /** Выше этой - игрок не успевает даже увидеть руду. */
    public static double MAX_SPEED = 26.0D;

    /** BPM по умолчанию для только что созданного уровня. */
    public static double DEFAULT_BPM = 128.0D;

    // ==================== СУДЕЙСТВО ====================

    /**
     * ГДЕ НАХОДИТСЯ РУДА В МОМЕНТ ИДЕАЛЬНОГО УДАРА, блоков впереди камеры.
     * <p>
     * Ноль здесь не годится: чтобы попасть, игрок должен ДОТЯНУТЬСЯ до руды, а тянется
     * он вперёд. Пока эта величина была нулевой, засчитывался только удар по руде,
     * поравнявшейся с камерой, - то есть по той, которую уже почти пронесло мимо.
     * Все остальные удары шли «слишком рано» и превращались в промахи.
     */
    public static double HIT_LEAD_BLOCKS = 2.0D;

    /**
     * ОКНА ПОПАДАНИЯ В БЛОКАХ, а не в миллисекундах.
     * <p>
     * В блоках потому, что игрок видит расстояние, а не время: на медленной и быстрой
     * карте «попал впритык» должно выглядеть одинаково. Внутри они всё равно переводятся
     * в миллисекунды по скорости - для дрожания пинга и калибровки.
     */
    /**
     * Окно идеального попадания, блоков.
     * <p>
     * Поднято с 0.9 до 1.6. Прежние 0.9 блока - это 115 миллисекунд на базовой скорости,
     * и звучит это неплохо ровно до тех пор, пока не вспомнить, из чего складывается
     * промах по времени: половина пинга туда-обратно, дрожание канала, задержка вывода
     * звука в наушниках, время реакции. У человека со средним каналом всё вместе легко
     * даёт разброс шире этого окна - и он всю карту получает «хорошо», ни разу не
     * ошибившись по существу.
     * <p>
     * 1.6 блока - это около 200 мс, и это ровно тот порядок, на котором работают окна в
     * ритм-играх. Целиться по-прежнему надо: за окном стоят «хорошо» и «засчитано», а
     * ранг SS теперь требует ЧИСТОГО прохождения, где ни одного «хорошо» быть не должно.
     */
    public static double PERFECT_BLOCKS = 1.6D;
    /** Окно «хорошо». Раздвинуто вслед за идеальным, чтобы «засчитано» не съело его. */
    public static double GOOD_BLOCKS = 2.6D;
    public static double OK_BLOCKS = 3.6D;

    /** Окно идеального попадания, мс. Пересчитывается из блоков по скорости карты. */
    public static int PERFECT_MS = 100;
    /** Окно хорошего попадания, мс. Даёт +100. */
    public static int GOOD_MS = 200;
    /** Последнее окно, мс. Даёт +50. За ним - промах. */
    public static int OK_MS = 330;

    /** Очки за каждый ранг до умножения на руду и комбо. */
    public static int SCORE_PERFECT = 300;
    public static int SCORE_GOOD = 100;
    public static int SCORE_OK = 50;

    /** Редкие руды судятся строже: окна умножаются на это число. */
    public static double RARE_WINDOW_FACTOR = 0.8D;

    /**
     * Скорость, относительно которой считается поблажка. Примерно 120 BPM.
     * <p>
     * Ниже неё поблажки нет вовсе: там окна и так дают достаточно времени.
     */
    public static double RELIEF_BASE_SPEED = 8.0D;

    /**
     * Вес «хорошего» попадания в ТОЧНОСТИ (не в очках).
     * <p>
     * Подбиралось в два захода, и оба крайних варианта оказались неверными.
     * <p>
     * На 0.33 (доля очков «хорошо» от «идеально») забег из 188 идеальных, 79 хороших и
     * девяти промахов давал 76% и ранг B - при том что игрок попал по 96% нот. Ранг
     * рушился от первой же неточности.
     * <p>
     * На 0.92 тот же забег давал 93.65% и ранг S, а четыре идеальных с одним хорошим -
     * сразу SS. Это другая крайность: SS переставал что-либо значить, его брали на
     * любом небрежном прохождении.
     * <p>
     * На 0.75 тот же забег даёт 88% и ранг A - честная оценка для 79 неточностей и
     * девяти промахов. При этом одно «хорошо» на десять идеальных всё ещё оставляет SS,
     * а на четыре - уже опускает до S. То есть SS требует чистоты, но достижим.
     */
    public static double ACCURACY_GOOD_WEIGHT = 0.75D;

    /** Вес «засчитано» в точности. Заметно дешевле, но всё ещё не промах. */
    public static double ACCURACY_OK_WEIGHT = 0.40D;

    /**
     * Насколько окна растягиваются за превышение скорости.
     * <p>
     * 0.7 означает, что вдвое более быстрая карта получает окна на 70% шире. Не единица:
     * полная компенсация сделала бы быстрые куски такими же лёгкими, как медленные, а
     * они и должны быть чуть сложнее - просто не втрое.
     */
    public static double SPEED_RELIEF_FACTOR = 0.7D;

    /** Насколько окна растягиваются за пинг, на каждую секунду пинга. */
    public static double PING_RELIEF_FACTOR = 0.9D;

    /** Пинг выше этого дальше окон не расширяет: иначе лаг превращается в преимущество. */
    public static int PING_RELIEF_CAP_MS = 200;

    /** Потолок общей поблажки. Вдвое - предел, за которым судейство перестаёт судить. */
    public static double RELIEF_LIMIT = 2.0D;

    /** Компенсировать ли пинг при судействе. Выключать только для отладки. */
    public static boolean PING_COMPENSATION = true;

    /** Пинг выше этого в компенсацию не идёт - защита от выбросов трекера. */
    public static int PING_CAP = 400;

    /**
     * НА СКОЛЬКО РАСШИРЯТЬ ОКНА ПОД ДЖИТТЕР.
     * <p>
     * Компенсация убирает постоянную задержку, но не её дрожание. Окно растёт на
     * это число, умноженное на измеренное отклонение пинга, и упирается в потолок.
     */
    public static double JITTER_WINDOW_FACTOR = 2.0D;
    public static int JITTER_WINDOW_CAP_MS = 60;

    /**
     * Сколько ударов нужно, чтобы калибровка считалась состоявшейся.
     * <p>
     * ПРИМЕНЯЕТСЯ поправка с первой же ноты - это число на неё больше не влияет.
     * Прежде оно означало «до стольких нот судим вообще без поправки», и ровно из-за
     * этого начало забега давало «+100» там, где середина давала «+300».
     */
    public static int AUTO_CALIBRATION_NOTES = 8;

    /**
     * ПОМНИТЬ ЛИ ЛИЧНУЮ ПОПРАВКУ МЕЖДУ ЗАБЕГАМИ.
     * <p>
     * Задержка у игрока своя и постоянная: наушники, монитор, канал. Заново нащупывать
     * её на каждом забеге - значит каждый раз отдавать первые ноты судье, который ещё
     * не знает, с кем имеет дело.
     * <p>
     * Выключать имеет смысл только при отладке судейства, когда нужен чистый забег
     * без всякой предыстории.
     */
    public static boolean CALIBRATION_MEMORY = true;

    /**
     * Потолок личной поправки, мс.
     * <p>
     * Было 120, и этого не хватало: у игрока с каналом на двести миллисекунд остаточная
     * задержка упиралась в потолок, а всё, что за ним, так и оставалось несжатым.
     */
    public static int MAX_PERSONAL_OFFSET_MS = 260;

    // ==================== КОМБО ====================

    /** Через сколько попаданий подряд множитель растёт на ступень. */
    public static int COMBO_STEP = 8;
    /** Потолок множителя. */
    public static int COMBO_MAX_MULTIPLIER = 8;

    /**
     * КАК РАСТЁТ МНОЖИТЕЛЬ.
     * <p>
     * {@code DOUBLE} - удвоением: 1, 2, 4, 8. Ступеней получается мало,
     * зато каждая ощущается.
     * <p>
     * {@code LINEAR} - по единице: 1, 2, 3, 4, 5. Растёт чаще, срыв стоит дешевле,
     * разброс очков между сильным и слабым забегом меньше.
     */
    public static String COMBO_MULTIPLIER_MODE = "DOUBLE";
    /** Сбрасывать ли комбо в ноль при промахе. false - делить пополам. */
    public static boolean COMBO_RESET_ON_MISS = true;

    /**
     * Сколько промахов подряд заканчивают забег. 0 - никогда.
     * <p>
     * Забег без всякого риска перестаёт быть забегом: провалив вступление, игрок
     * дослушивает трек до конца просто потому, что уйти некуда.
     */
    public static int FAIL_AFTER_MISSES = 6;

    // ==================== НОСИТЕЛЬ ====================

    /**
     * ВЫСОТА ПОСАДКИ над точкой отсчёта карты, блоков.
     * <p>
     * На прежней десятой доле блока камера ехала практически по полу тоннеля: нижний
     * ряд руд оказывался на уровне подбородка, и чтобы вообще его увидеть, приходилось
     * смотреть вниз - а вместе с ним из кадра уходили верхние ряды. Подъём почти на
     * полный блок ставит камеру между нижним и средним рядом, и все три ряда попадают
     * в обзор сразу.
     * <p>
     * На судейство не влияет: оно считает расстояние вдоль оси уровня, высота в него
     * не входит вовсе. Меняется командой {@code /pb digger set carrier_y_offset}.
     */
    public static double CARRIER_Y_OFFSET = 0.9D;

    /**
     * РАЗГОН: ближе этого расстояния к старту нот не бывает, блоков.
     * <p>
     * Одно число на две стороны сразу. Авторазметка ничего не кладёт ближе, а забег
     * ничего не судит ближе - раньше у них были разные представления о том, где
     * начинается карта, и первые ноты то улетали в спавн, то выбрасывались как
     * непроходимые.
     * <p>
     * Смысл именно в расстоянии, а не в секундах: руду надо успеть УВИДЕТЬ, а видно
     * её с фиксированного расстояния, которое от темпа не зависит.
     */
    public static double START_GAP_BLOCKS = 12.0D;

    /**
     * Сколько минимум времени между двумя дроп-нотами, мс.
     * <p>
     * Дроп - это полный залп шоу: вспышка, лучи, разряд по ламповым стенам. Событие
     * такой громкости работает ровно до тех пор, пока оно редкое. Шесть секунд - это
     * примерно раз в три такта на среднем темпе, то есть акцент на смене части, а не
     * фон.
     */
    public static int DROP_MIN_GAP_MILLIS = 6000;

    /**
     * Разряжать ли ламповые стены уровня на дроп-ноте.
     * <p>
     * Часть того самого залпа, который убран из оформления дропа: от него остался
     * только луч вверх. Стена в пару сотен ламп вспыхивает разом прямо перед камерой
     * и на несколько тиков закрывает собой следующие руды - то есть самая заметная
     * нота карты портит пару нот после себя.
     * <p>
     * Выключено, но не выброшено: строитель, который развесил лампы именно ради этого,
     * возвращает их одной командой.
     */
    public static boolean DROP_BURN_LAMPS = false;

    /**
     * Бить ли раскатом грома на дроп-ноте.
     * <p>
     * Выключено по той же причине, что и лампы. Отдельным ключом, а не вместе с ними:
     * звук и картинка мешают по-разному, и хотеть вернуть можно только одно из двух.
     */
    public static boolean DROP_THUNDER = false;

    // ==================== ТОННЕЛЬ ====================

    /** Внутренняя ширина тоннеля по умолчанию, блоков. */
    public static int DEFAULT_TUNNEL_WIDTH = 5;
    /** Внутренняя высота тоннеля по умолчанию, блоков. */
    public static int DEFAULT_TUNNEL_HEIGHT = 5;
    /** Предел, за который строителю уходить незачем: дальше руды вне досягаемости. */
    public static int MAX_TUNNEL_SIZE = 9;

    /**
     * ВЫСОТА, НА КОТОРОЙ ЖИВЁТ ВЕСЬ УРОВЕНЬ.
     * <p>
     * Сто двадцать, а не тридцать и не пятьдесят. Тоннель «Копателя» строится вверх
     * так же охотно, как вперёд: ламповые стены, потолочные конструкции, декор над
     * головой. На полусотне потолок мира оказывается в семидесяти блоках, и на
     * сколько-нибудь высокой сцене строитель упирается в него посреди работы, а
     * переносить готовую карту вверх уже нечем.
     * <p>
     * Снизу при этом тоже нужен запас: провалы, шахты и нижние ярусы уходят под
     * уровень пола, и сто двадцать оставляют под ним больше сотни блоков.
     */
    public static int SPAWN_Y = 120;

    /**
     * Собирать ли световое шоу при авторазметке.
     * <p>
     * Затирает прежнее шоу целиком - дописывать поверх нельзя, у неба и погоды
     * состояние сквозное. Поэтому строитель, оформивший карту руками, это выключает.
     */
    public static boolean AUTO_LIGHT_SHOW = true;

    /**
     * Ставить ли при этом ещё и биом-зоны.
     * <p>
     * Отдельно от остального шоу, потому что цена другая на порядок: биом переписывается
     * в самих блоках на всю высоту мира, а не проигрывается игроку. Даже с раскладкой по
     * одной зоне за тик это заметная работа, поэтому решение остаётся за человеком.
     */
    public static boolean AUTO_BIOME_ZONES = true;

    /**
     * Подбирать ли зоны скорости при авторазметке.
     * <p>
     * Выключается тем, кто расставляет их руками: автоподбор затирает прежние зоны
     * целиком, и делать это молча поверх ручной работы было бы свинством.
     */
    public static boolean AUTO_SPEED_ZONES = true;

    /**
     * Вспыхивать ли рудой в момент её появления.
     * <p>
     * Ровно одна частица на ноту. Выключается тем, кому мешает даже она.
     */
    public static boolean REVEAL_FLASH = true;


    // ==================== ЗАБЕГ ====================

    /**
     * Пауза между готовностью трека и началом отсчёта, тиков.
     * <p>
     * Тридцать тиков - полторы секунды. Клиент рапортует о готовности ресурспака раньше,
     * чем успевает нарисовать мир, и отсчёт начинался поверх чёрного экрана.
     */
    public static int MUSIC_SETTLE_TICKS = 30;

    /**
     * Делать ли игрока невидимым на время заезда.
     * <p>
     * Своя модель и броня попадают в кадр при развороте камеры и мешают читать ноты.
     * Первого лица это не касается: руку с киркой видно по-прежнему, она здесь нужна.
     */
    public static boolean INVISIBLE_PLAYER = true;

    /**
     * Сколько миллисекунд держится подпись попадания в экшнбаре.
     * <p>
     * Короткая жизнь здесь - не экономия, а динамика: подпись должна успеть смениться
     * до следующей ноты, иначе на экране застывает одно число вместо отклика на удар.
     */
    public static int HIT_DISPLAY_MILLIS = 120;

    /** Открывать ли экран выбора после заезда. В тесте из редактора не открывается никогда. */
    public static boolean END_MENU = true;

    /** Через сколько тиков после финиша открыть экран: сначала титры, потом выбор. */
    public static int END_MENU_DELAY_TICKS = 45;

    /** Прятать ли игровой скорборд на время заезда. */
    public static boolean HIDE_SCOREBOARD = true;

    /** Отсчёт перед стартом, секунд. Как в дуэлях: 3, 2, 1, поехали. */
    public static int COUNTDOWN_SECONDS = 3;

    /** Сколько ждать после последней ноты перед подведением итогов, мс. */
    public static int OUTRO_MILLIS = 2500;

    /**
     * КАК ЛОМАЕТСЯ РУДА.
     * <p>
     * true - поштучно, у каждого своя. Разрушение отменяется, а игроку уходит
     * поддельный воздух: блок остаётся в мире, и по тоннелю могут ехать хоть десять
     * человек сразу, не выедая ноты друг другу. Цена - мигание длиной в один тик,
     * когда сервер успевает вернуть настоящий блок раньше нашего пакета. От пинга
     * это не зависит: оба пакета летят вместе.
     * <p>
     * false - руда ломается по-настоящему, как обычный блок. Мигания нет вообще, но
     * тоннель общий: второй игрок приедет на пустоту. Блоки восстанавливаются при
     * старте каждого забега. Годится, когда карту точно проходят по одному.
     * <p>
     * На ощущение от ломки это НЕ влияет никак: в творческом режиме клиент убирает
     * блок сам, не дожидаясь сервера, и делает это одинаково в обоих случаях.
     */
    public static boolean PER_PLAYER_ORES = true;

    /** Разрешать ли ломать НЕ ноты (декор) во время забега. */
    public static boolean ALLOW_BREAKING_DECOR = false;

    /**
     * НАКАЗЫВАТЬ ЛИ ЗА ХОЛОСТЫЕ УДАРЫ.
     * <p>
     * Без этого выгодная стратегия - зажать ЛКМ и водить прицелом: руды сносятся сами,
     * а промахнуться невозможно. Поэтому взмах, который не попал ни по одной ноте,
     * считается промахом и рушит комбо.
     */
    public static boolean PUNISH_AIR_SWINGS = true;

    /**
     * Сколько миллисекунд после наказания холостые удары не считаются.
     * Зажатая кнопка шлёт взмахи пачками, и без паузы одно случайное движение
     * снимало бы комбо пять раз подряд.
     */
    public static int AIR_SWING_COOLDOWN_MS = 260;

    /** Окно, в которое взмах ещё может «догнать» разрушение и не считаться холостым. */
    public static int AIR_SWING_GRACE_MS = 80;

    /**
     * ГДЕ ЛЕЖАТ ФАЙЛЫ ТРЕКОВ.
     * <p>
     * Пусто - искать внутри папки плагина (amusic/Music и amusic/Packed). Если музыка
     * хранится в другом месте, например рядом с прокси, сюда пишется абсолютный путь.
     * Без файла определить длину и темп трека нечем: сервер не декодирует звук.
     */
    public static String MUSIC_FOLDER = "";

    // ==================== ПАНЕЛИ ====================

    /**
     * ПОКАЗЫВАТЬ ЛИ ПАНЕЛИ В МИРЕ ВМЕСТО ОБЫЧНОГО ИНТЕРФЕЙСА.
     * <p>
     * Боссбар и экшнбар лепятся к краям экрана, а в ритм-игре глаз держится центра и по краям попросту не смотрит.
     * Панели висят там, куда игрок и так глядит.
     */
    public static boolean HOLOGRAMS = true;

    /** Насколько панели вынесены вперёд от камеры, блоков. */
    public static double HOLOGRAM_FORWARD = 4.5D;

    /** Насколько вбок, блоков. Больше - дальше от центра кадра. */
    public static double HOLOGRAM_SIDE = 2.1D;

    /** Высота относительно носителя, блоков. */
    public static double HOLOGRAM_HEIGHT = 1.1D;

    /** Расстояние между строками панели, блоков. */
    public static double HOLOGRAM_LINE_GAP = 0.23D;

    /** На сколько блоков строка голограммы выдвигается вперёд к игроку при комбо. */
    public static double HOLOGRAM_PUNCH_DISTANCE = 0.28D;

    /**
     * Рисовать ли кольцо множителя из частиц вокруг правой панели.
     * <p>
     * По умолчанию выключено: голограммы и так читаются, а кольцо - это три десятка
     * пакетов частиц каждый тик на каждого игрока.
     */
    public static boolean HOLOGRAM_RING = false;

    /** Дублировать ли счёт в экшнбар. По умолчанию нет: панелей достаточно. */
    public static boolean SHOW_ACTIONBAR = false;

    /** Показывать ли боссбар с прогрессом трека. */
    public static boolean SHOW_BOSS_BAR = false;

    // ==================== ЭФФЕКТЫ ====================

    /**
     * Во сколько раз больше ванильного сыпать осколков блока.
     * Ванильное разрушение даёт скупую горстку, для шоу этого мало.
     */
    public static double CRACK_MULTIPLIER = 8.0D;

    /** Высота луча END_ROD над сломанной рудой, блоков. */
    public static double BEAM_HEIGHT = 6.0D;
    /** Только на идеальных попаданиях или на всех. */
    public static boolean BEAM_ON_PERFECT_ONLY = false;

    /** Общий множитель плотности частиц - на случай, если сервер начнёт задыхаться. */
    public static double PARTICLE_DENSITY = 1.0D;

    // ==================== ФОРМУЛЫ ====================

    /**
     * Скорость из BPM. Одна доля всегда занимает {@link #BLOCKS_PER_BEAT} блоков,
     * поэтому сетка бита совпадает с сеткой блоков на любом темпе.
     *
     * @return блоков в секунду
     */
    public static double speedFromBpm(double bpm) {
        if (Double.isNaN(bpm) || Double.isInfinite(bpm) || bpm <= 0.0D) bpm = DEFAULT_BPM;
        double speed = bpm / 60.0D * BLOCKS_PER_BEAT;
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
    }

    /** Длина одной доли в миллисекундах. */
    public static double beatMillis(double bpm) {
        if (bpm <= 0.0D) bpm = DEFAULT_BPM;
        return 60000.0D / bpm;
    }

    // ==================== КОНФИГ ====================

    private interface Accessor {
        @NonNull
        String get();

        void set(@NonNull String raw) throws IllegalArgumentException;
    }

    private static final Map<String, Accessor> KEYS = new LinkedHashMap<>();

    private static void registerDouble(@NonNull String key, @NonNull java.util.function.DoubleSupplier getter,
                                       @NonNull java.util.function.DoubleConsumer setter) {
        KEYS.put(key, new Accessor() {
            @Override
            public @NonNull String get() {
                return String.valueOf(getter.getAsDouble());
            }

            @Override
            public void set(@NonNull String raw) {
                setter.accept(Double.parseDouble(raw.replace(',', '.')));
            }
        });
    }

    private static void registerInt(@NonNull String key, @NonNull java.util.function.IntSupplier getter,
                                    @NonNull java.util.function.IntConsumer setter) {
        KEYS.put(key, new Accessor() {
            @Override
            public @NonNull String get() {
                return String.valueOf(getter.getAsInt());
            }

            @Override
            public void set(@NonNull String raw) {
                setter.accept(Integer.parseInt(raw.trim()));
            }
        });
    }

    private static void registerLong(@NonNull String key, @NonNull java.util.function.LongSupplier getter,
                                     @NonNull java.util.function.LongConsumer setter) {
        KEYS.put(key, new Accessor() {
            @Override
            public @NonNull String get() {
                return String.valueOf(getter.getAsLong());
            }

            @Override
            public void set(@NonNull String raw) {
                setter.accept(Long.parseLong(raw.trim()));
            }
        });
    }

    private static void registerBoolean(@NonNull String key, @NonNull java.util.function.BooleanSupplier getter,
                                        @NonNull java.util.function.Consumer<Boolean> setter) {
        KEYS.put(key, new Accessor() {
            @Override
            public @NonNull String get() {
                return String.valueOf(getter.getAsBoolean());
            }

            @Override
            public void set(@NonNull String raw) {
                setter.accept(Boolean.parseBoolean(raw.trim()));
            }
        });
    }

    private static void registerString(@NonNull String key, @NonNull java.util.function.Supplier<String> getter,
                                       @NonNull java.util.function.Consumer<String> setter) {
        KEYS.put(key, new Accessor() {
            @Override
            public @NonNull String get() {
                return getter.get();
            }

            @Override
            public void set(@NonNull String raw) {
                setter.accept(raw.trim().toUpperCase(Locale.ROOT));
            }
        });
    }

    static {
        registerDouble("blocks_per_beat", () -> BLOCKS_PER_BEAT,
            raw -> BLOCKS_PER_BEAT = Math.max(0.5D, Math.min(16.0D, raw)));
        registerDouble("min_speed", () -> MIN_SPEED, raw -> MIN_SPEED = Math.max(0.5D, raw));
        registerDouble("max_speed", () -> MAX_SPEED, raw -> MAX_SPEED = Math.max(MIN_SPEED, raw));
        registerDouble("default_bpm", () -> DEFAULT_BPM, raw -> DEFAULT_BPM = Math.max(20.0D, raw));

        registerDouble("hit_lead_blocks", () -> HIT_LEAD_BLOCKS,
            raw -> HIT_LEAD_BLOCKS = Math.max(0.0D, Math.min(6.0D, raw)));
        registerDouble("perfect_blocks", () -> PERFECT_BLOCKS,
            raw -> PERFECT_BLOCKS = Math.max(0.2D, raw));
        registerDouble("good_blocks", () -> GOOD_BLOCKS,
            raw -> GOOD_BLOCKS = Math.max(PERFECT_BLOCKS, raw));
        registerDouble("ok_blocks", () -> OK_BLOCKS,
            raw -> OK_BLOCKS = Math.max(GOOD_BLOCKS, raw));
        registerInt("perfect_ms", () -> PERFECT_MS, raw -> PERFECT_MS = Math.max(10, raw));
        registerInt("good_ms", () -> GOOD_MS, raw -> GOOD_MS = Math.max(PERFECT_MS, raw));
        registerInt("ok_ms", () -> OK_MS, raw -> OK_MS = Math.max(GOOD_MS, raw));
        registerInt("score_perfect", () -> SCORE_PERFECT, raw -> SCORE_PERFECT = raw);
        registerInt("score_good", () -> SCORE_GOOD, raw -> SCORE_GOOD = raw);
        registerInt("score_ok", () -> SCORE_OK, raw -> SCORE_OK = raw);
        registerDouble("accuracy_good_weight", () -> ACCURACY_GOOD_WEIGHT,
            raw -> ACCURACY_GOOD_WEIGHT = Math.max(0.0D, Math.min(1.0D, raw)));
        registerDouble("accuracy_ok_weight", () -> ACCURACY_OK_WEIGHT,
            raw -> ACCURACY_OK_WEIGHT = Math.max(0.0D, Math.min(1.0D, raw)));
        registerDouble("relief_base_speed", () -> RELIEF_BASE_SPEED,
            raw -> RELIEF_BASE_SPEED = Math.max(1.0D, raw));
        registerDouble("speed_relief_factor", () -> SPEED_RELIEF_FACTOR,
            raw -> SPEED_RELIEF_FACTOR = Math.max(0.0D, Math.min(2.0D, raw)));
        registerDouble("ping_relief_factor", () -> PING_RELIEF_FACTOR,
            raw -> PING_RELIEF_FACTOR = Math.max(0.0D, Math.min(3.0D, raw)));
        registerInt("ping_relief_cap_ms", () -> PING_RELIEF_CAP_MS,
            raw -> PING_RELIEF_CAP_MS = Math.max(0, Math.min(500, raw)));
        registerDouble("relief_limit", () -> RELIEF_LIMIT,
            raw -> RELIEF_LIMIT = Math.max(1.0D, Math.min(4.0D, raw)));
        registerDouble("rare_window_factor", () -> RARE_WINDOW_FACTOR,
            raw -> RARE_WINDOW_FACTOR = Math.max(0.2D, Math.min(1.0D, raw)));

        registerBoolean("ping_compensation", () -> PING_COMPENSATION, raw -> PING_COMPENSATION = raw);
        registerInt("ping_cap", () -> PING_CAP, raw -> PING_CAP = Math.max(50, raw));
        registerDouble("jitter_window_factor", () -> JITTER_WINDOW_FACTOR,
            raw -> JITTER_WINDOW_FACTOR = Math.max(0.0D, raw));
        registerInt("jitter_window_cap_ms", () -> JITTER_WINDOW_CAP_MS,
            raw -> JITTER_WINDOW_CAP_MS = Math.max(0, raw));
        registerInt("auto_calibration_notes", () -> AUTO_CALIBRATION_NOTES,
            raw -> AUTO_CALIBRATION_NOTES = Math.max(0, raw));
        registerBoolean("calibration_memory", () -> CALIBRATION_MEMORY,
            raw -> CALIBRATION_MEMORY = raw);
        registerInt("max_personal_offset_ms", () -> MAX_PERSONAL_OFFSET_MS,
            raw -> MAX_PERSONAL_OFFSET_MS = Math.max(0, raw));

        registerInt("combo_step", () -> COMBO_STEP, raw -> COMBO_STEP = Math.max(1, raw));
        registerInt("combo_max_multiplier", () -> COMBO_MAX_MULTIPLIER,
            raw -> COMBO_MAX_MULTIPLIER = Math.max(1, raw));
        registerString("combo_multiplier_mode", () -> COMBO_MULTIPLIER_MODE,
            raw -> COMBO_MULTIPLIER_MODE = raw.startsWith("L") ? "LINEAR" : "DOUBLE");
        registerInt("fail_after_misses", () -> FAIL_AFTER_MISSES,
            raw -> FAIL_AFTER_MISSES = Math.max(0, raw));
        registerBoolean("combo_reset_on_miss", () -> COMBO_RESET_ON_MISS,
            raw -> COMBO_RESET_ON_MISS = raw);

        registerDouble("carrier_y_offset", () -> CARRIER_Y_OFFSET, raw -> CARRIER_Y_OFFSET = raw);
        registerDouble("start_gap_blocks", () -> START_GAP_BLOCKS,
            raw -> START_GAP_BLOCKS = Math.max(0.0D, Math.min(64.0D, raw)));
        registerBoolean("auto_light_show", () -> AUTO_LIGHT_SHOW,
            raw -> AUTO_LIGHT_SHOW = raw);
        registerBoolean("auto_biome_zones", () -> AUTO_BIOME_ZONES,
            raw -> AUTO_BIOME_ZONES = raw);
        registerBoolean("auto_speed_zones", () -> AUTO_SPEED_ZONES,
            raw -> AUTO_SPEED_ZONES = raw);
        registerBoolean("reveal_flash", () -> REVEAL_FLASH, raw -> REVEAL_FLASH = raw);
        registerBoolean("drop_burn_lamps", () -> DROP_BURN_LAMPS,
            raw -> DROP_BURN_LAMPS = raw);
        registerBoolean("drop_thunder", () -> DROP_THUNDER,
            raw -> DROP_THUNDER = raw);
        registerInt("drop_min_gap_millis", () -> DROP_MIN_GAP_MILLIS,
            raw -> DROP_MIN_GAP_MILLIS = Math.max(0, Math.min(60000, raw)));

        registerInt("default_tunnel_width", () -> DEFAULT_TUNNEL_WIDTH,
            raw -> DEFAULT_TUNNEL_WIDTH = clampTunnel(raw));
        registerInt("default_tunnel_height", () -> DEFAULT_TUNNEL_HEIGHT,
            raw -> DEFAULT_TUNNEL_HEIGHT = clampTunnel(raw));
        registerInt("max_tunnel_size", () -> MAX_TUNNEL_SIZE, raw -> MAX_TUNNEL_SIZE = Math.max(3, raw));
        registerInt("spawn_y", () -> SPAWN_Y, raw -> SPAWN_Y = Math.max(1, Math.min(250, raw)));

        registerInt("music_settle_ticks", () -> MUSIC_SETTLE_TICKS,
            raw -> MUSIC_SETTLE_TICKS = Math.max(0, Math.min(200, raw)));
        registerInt("hit_display_millis", () -> HIT_DISPLAY_MILLIS,
            raw -> HIT_DISPLAY_MILLIS = Math.max(40, Math.min(3000, raw)));
        registerBoolean("invisible_player", () -> INVISIBLE_PLAYER,
            raw -> INVISIBLE_PLAYER = raw);
        registerBoolean("end_menu", () -> END_MENU, raw -> END_MENU = raw);
        registerInt("end_menu_delay_ticks", () -> END_MENU_DELAY_TICKS,
            raw -> END_MENU_DELAY_TICKS = Math.max(0, Math.min(200, raw)));
        registerBoolean("hide_scoreboard", () -> HIDE_SCOREBOARD,
            raw -> HIDE_SCOREBOARD = raw);
        registerInt("countdown_seconds", () -> COUNTDOWN_SECONDS,
            raw -> COUNTDOWN_SECONDS = Math.max(0, Math.min(10, raw)));
        registerInt("outro_millis", () -> OUTRO_MILLIS, raw -> OUTRO_MILLIS = Math.max(0, raw));
        registerBoolean("per_player_ores", () -> PER_PLAYER_ORES,
            raw -> PER_PLAYER_ORES = raw);
        registerBoolean("allow_breaking_decor", () -> ALLOW_BREAKING_DECOR,
            raw -> ALLOW_BREAKING_DECOR = raw);
        registerBoolean("punish_air_swings", () -> PUNISH_AIR_SWINGS,
            raw -> PUNISH_AIR_SWINGS = raw);
        registerInt("air_swing_cooldown_ms", () -> AIR_SWING_COOLDOWN_MS,
            raw -> AIR_SWING_COOLDOWN_MS = Math.max(0, raw));
        registerInt("air_swing_grace_ms", () -> AIR_SWING_GRACE_MS,
            raw -> AIR_SWING_GRACE_MS = Math.max(0, raw));

        registerString("music_folder", () -> MUSIC_FOLDER, raw -> MUSIC_FOLDER = raw);
        registerBoolean("holograms", () -> HOLOGRAMS, raw -> HOLOGRAMS = raw);
        registerDouble("hologram_forward", () -> HOLOGRAM_FORWARD,
            raw -> HOLOGRAM_FORWARD = Math.max(1.0D, Math.min(24.0D, raw)));
        registerDouble("hologram_side", () -> HOLOGRAM_SIDE,
            raw -> HOLOGRAM_SIDE = Math.max(0.0D, Math.min(12.0D, raw)));
        registerDouble("hologram_height", () -> HOLOGRAM_HEIGHT,
            raw -> HOLOGRAM_HEIGHT = Math.max(-4.0D, Math.min(8.0D, raw)));
        registerDouble("hologram_line_gap", () -> HOLOGRAM_LINE_GAP,
            raw -> HOLOGRAM_LINE_GAP = Math.max(0.1D, Math.min(2.0D, raw)));
        registerDouble("hologram_punch_distance", () -> HOLOGRAM_PUNCH_DISTANCE,
            raw -> HOLOGRAM_PUNCH_DISTANCE = Math.max(0.0D, Math.min(2.0D, raw)));
        registerBoolean("hologram_ring", () -> HOLOGRAM_RING, raw -> HOLOGRAM_RING = raw);
        registerBoolean("show_actionbar", () -> SHOW_ACTIONBAR, raw -> SHOW_ACTIONBAR = raw);
        registerBoolean("show_boss_bar", () -> SHOW_BOSS_BAR, raw -> SHOW_BOSS_BAR = raw);
        registerDouble("crack_multiplier", () -> CRACK_MULTIPLIER,
            raw -> CRACK_MULTIPLIER = Math.max(0.0D, Math.min(64.0D, raw)));
        registerDouble("beam_height", () -> BEAM_HEIGHT, raw -> BEAM_HEIGHT = Math.max(0.0D, raw));
        registerBoolean("beam_on_perfect_only", () -> BEAM_ON_PERFECT_ONLY,
            raw -> BEAM_ON_PERFECT_ONLY = raw);
        registerDouble("particle_density", () -> PARTICLE_DENSITY,
            raw -> PARTICLE_DENSITY = Math.max(0.0D, Math.min(4.0D, raw)));
    }

    public static int clampTunnel(int value) {
        if (value < 3) return 3;
        return Math.min(value, MAX_TUNNEL_SIZE);
    }

    @NonNull
    public static java.util.Set<String> keys() {
        return KEYS.keySet();
    }

    @Nullable
    public static Object get(@NonNull String key) {
        Accessor accessor = KEYS.get(key.toLowerCase(Locale.ROOT));
        return accessor == null ? null : accessor.get();
    }

    /**
     * @return false, если ключа нет или значение не разобралось
     */
    public static boolean set(@NonNull String key, @NonNull String value) {
        Accessor setter = KEYS.get(key.toLowerCase(Locale.ROOT));
        if (setter == null) return false;
        try {
            setter.set(value.trim());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void load(@Nullable ConfigurationSection section) {
        if (section == null) return;
        for (String key : KEYS.keySet()) {
            if (!section.contains(key)) continue;
            Object raw = section.get(key);
            if (raw == null) continue;
            set(key, String.valueOf(raw));
        }
    }

    public static void save(@NonNull ConfigurationSection section) {
        for (Map.Entry<String, Accessor> entry : KEYS.entrySet()) {
            // Для совместимости пишем те же типы, что и были.
            String val = entry.getValue().get();
            try {
                if (val.equalsIgnoreCase("true") || val.equalsIgnoreCase("false")) {
                    section.set(entry.getKey(), Boolean.parseBoolean(val));
                    continue;
                }
                if (val.contains(".")) {
                    section.set(entry.getKey(), Double.parseDouble(val));
                    continue;
                }
                section.set(entry.getKey(), Integer.parseInt(val));
            } catch (NumberFormatException e) {
                section.set(entry.getKey(), val);
            }
        }
    }
}
