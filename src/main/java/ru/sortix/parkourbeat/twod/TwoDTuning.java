package ru.sortix.parkourbeat.twod;

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
public final class TwoDTuning {
    private TwoDTuning() {
    }

    // ==================== ВНЕШНИЙ ВИД КУБИКА ====================

    /**
     * Какой стороной повёрнут repeating_command_block (в ресурспаке это и есть кубик
     * из Geometry Dash). "AUTO" - сторона считается сама: к камере всегда развёрнута
     * «морда» блока. Если в ресурспаке текстура кубика лежит на другой стороне -
     * ставим сюда любую из NORTH/SOUTH/EAST/WEST/UP/DOWN и выравниваем вручную.
     */
    public static String CUBE_FACE = "AUTO";

    /**
     * Доворот самой сущности falling_block в градусах. Клиент по-разному крутит
     * падающие блоки на разных версиях, поэтому это отдельная ручка сверху к CUBE_FACE.
     */
    public static float CUBE_YAW_OFFSET = 0.0f;

    /**
     * Вертикальный сдвиг кубика относительно расчётной позиции. Правится, если кубик
     * визуально утоплен в пол или, наоборот, висит над ним.
     */
    public static double CUBE_Y_OFFSET = 0.0D;

    /**
     * ЧЕМ РИСОВАТЬ КУБИК.
     * <p>
     * AUTO - BlockDisplay, если сервер его знает (1.19.4+), иначе falling_block.
     * HEAD - арморстенд с блоком на голове: единственный способ крутить кубик на
     * старых версиях, но блок на голове рисуется примерно в 0.625 блока, так что
     * под него стоит уменьшить и хитбокс (cube_half).
     * FALLING_BLOCK - как было, без вращения.
     */
    public static String CUBE_STYLE = "AUTO";

    /** Сдвиг арморстенда со стилем HEAD: подгоняет блок на голове под центр кубика. */
    public static double CUBE_HEAD_Y_OFFSET = -1.44D;

    /**
     * Кувыркать кубик при прыжке, как в оригинале.
     * <p>
     * Работает на серверах 1.19.4+ через BlockDisplay. Игроки со старых клиентов
     * вращения не увидят, всем остальным оно достаётся бесплатно.
     */
    public static boolean CUBE_ROTATION = true;

    /** Сколько полуоборотов кубик делает за один прыжок. */
    public static double CUBE_SPINS_PER_JUMP = 1.0D;

    // ==================== КАМЕРА (ТОТ САМЫЙ «2D-УГОЛ») ====================

    /** На сколько блоков камера отодвинута вбок от кубика. */
    public static double CAMERA_DISTANCE = 14.0D;

    /**
     * Насколько камера уведена ВПЕРЁД по ходу уровня.
     * <p>
     * ПО УМОЛЧАНИЮ НОЛЬ, и это важно. В оригинале проекция ортографическая, поэтому
     * там кубик можно сдвинуть в левую треть экрана без последствий. У Minecraft
     * проекция перспективная: любой увод камеры вдоль уровня разворачивает кубик
     * боком к зрителю, и никакого "идеального 2D" уже не получается. Камера стоит
     * ровно напротив кубика, лицом к его грани.
     */
    public static double CAMERA_LEAD = 0.0D;

    /** Высота глаз камеры над низом кубика. */
    public static double CAMERA_EYE_HEIGHT = 0.9D;

    /** Наклон камеры. 0 - строго горизонтально, «идеальное 2D». */
    public static float CAMERA_PITCH = 0.0f;

    /**
     * Сглаживание вертикального следования камеры за кубиком (0..1).
     * 1 - камера прибита к кубику намертво, прыжки трясут экран.
     */
    public static double CAMERA_SMOOTH = 0.35D;

    /**
     * КАК ДЕРЖИТСЯ КАМЕРА.
     * <p>
     * SPECTATOR - игрок становится наблюдателем и привязывается к невидимому
     * арморстенду. Клиент в этом режиме вообще не даёт крутить головой: угол берётся
     * у сущности, а не у мыши. Это самый чистый способ, и он не требует ни пакетов,
     * ни доворотов каждый тик.
     * <p>
     * RIDE - старый способ: игрок сидит на арморстенде, а угол ему досылается пакетом.
     * Нужен, если в режиме наблюдателя клиент перестаёт присылать нажатия.
     */
    public static String CAMERA_MODE = "RIDE";

    /** Принудительно держать взгляд игрока в 2D-плоскости (только для режима RIDE). */
    public static boolean LOCK_CAMERA = true;

    /**
     * Как часто клиенту отправляется принудительный угол, тиков.
     * <p>
     * Чаще - жёстче фиксация, но и пакетов больше. Двух тиков хватает, чтобы мышью
     * камеру было не увести вообще.
     */
    public static int CAMERA_LOCK_PERIOD = 1;

    /**
     * На какое расстояние ставится точка, на которую клиенту велят смотреть.
     * <p>
     * Угол клиент считает ОТ СВОЕЙ позиции, а она всегда чуть отличается от серверной.
     * На близкой точке эта разница превращается в заметный перекос и дрожание; на
     * далёкой ошибка в целый блок даёт сотые доли градуса.
     */
    public static double LOOK_TARGET_DISTANCE = 4096.0D;

    /**
     * Слать разворот только когда игрок реально дёрнул мышкой.
     * <p>
     * Иначе пакет уходит каждый тик просто так и камера мелко дрожит.
     */
    public static boolean CAMERA_LOCK_ON_CHANGE = true;

    /**
     * Отладочная строка в актионбаре: состояние земли, скорость, режим.
     * Нужна ровно для одного - показать, почему прыжок сработал или не сработал.
     */
    public static boolean DEBUG = false;

    /** Замедление, выдаваемое игроку в забеге ради узкого FOV. */
    public static int SLOWNESS_LEVEL = 7;

    // ==================== ФИЗИКА ====================

    /**
     * Скорость кубика в блоках в секунду.
     * <p>
     * База 3D-бега это 5.6123; здесь она поднята на 5.35%, как и просили.
     * У уровня может быть своя скорость, она перебивает эту.
     */
    public static double SPEED = 5.9126D;

    /**
     * Гравитация в режиме кубика, блоков за тик в квадрате.
     * <p>
     * ПРЫЖОК ДОЛЖЕН БЫТЬ РЕЗКИМ. В оригинале кубик находится в воздухе около
     * половины секунды, а не секунду: тяжёлая гравитация плюс сильный толчок дают
     * ту самую короткую дугу, по которой игра и читается.
     */
    public static double GRAVITY = 0.140D;

    /**
     * Стартовая скорость прыжка, блоков за тик.
     * <p>
     * С нынешней гравитацией это высота около 1.7 блока и примерно 11 тиков в
     * воздухе. Крутить высоту прыжка нужно именно здесь.
     */
    public static double JUMP_VELOCITY = 0.68D;

    /** Максимальная скорость падения. */
    public static double MAX_FALL_SPEED = 1.90D;

    /**
     * Зажатый пробел прыгает сам, как только кубик коснулся земли.
     * Без этого длинные цепочки прыжков приходится выщёлкивать вручную.
     */
    public static boolean HOLD_TO_JUMP = true;

    /** Половина ширины хитбокса кубика. Меньше 0.5 - значит чуть прощаем касания углов. */
    public static double CUBE_HALF = 0.42D;

    /** На сколько блоков ниже точки старта кубик считается упавшим в пропасть. */
    public static double FALL_DEATH_DEPTH = 12.0D;

    /** Удар макушкой о блок убивает (как в оригинале) или просто гасит скорость. */
    public static boolean CEILING_KILLS = false;

    /** Пауза перед перезапуском попытки, тиков. Проигрыш должен быть быстрым. */
    public static int RESPAWN_DELAY_TICKS = 8;

    /**
     * Сколько тиков кубик должен простоять на земле, прежде чем сможет прыгнуть снова.
     * <p>
     * Ноль выглядит как прыжок из воздуха: при удержании кнопки кубик отрывается от
     * земли раньше, чем клиент успевает дорисовать приземление, и игрок видит,
     * что прыжок случился в полёте. Два тика клиенту хватает.
     */
    public static int MIN_GROUND_TICKS = 2;

    /**
     * Сколько тиков кубик стоит на земле перед АВТОМАТИЧЕСКИМ прыжком (зажатая кнопка).
     * <p>
     * В оригинале удержание отталкивает кубик в тот же момент, когда он коснулся
     * земли. Каждый тик ожидания - это лишние 0.3 блока пути по земле, а за цикл
     * "прыжок-приземление" набегает больше половины блока: связки, рассчитанные на
     * ритм оригинала, начинают перелетаться.
     * <p>
     * Ноль - как в оригинале. Но учтите: клиент рисует сущность с задержкой около
     * трёх тиков, поэтому при нуле касание земли может быть не видно на экране.
     * Это ровно тот компромисс, из-за которого здесь появилась отдельная настройка.
     */
    public static int AUTO_JUMP_GROUND_TICKS = 0;

    // ==================== РЕЖИМ ПОЛЁТА (ЛОДКА) ====================

    /** Гравитация в полёте, заметно мягче: иначе кораблик неуправляем. */
    public static double FLY_GRAVITY = 0.055D;

    /**
     * Разовый толчок в момент нажатия. Маленький: основную работу делает удержание,
     * иначе кораблик дёргается рывками и им невозможно управлять.
     */
    public static double FLY_THRUST = 0.0D;

    /** Тяга, пока кнопка зажата (за тик). Это и есть основное управление в полёте. */
    public static double FLY_HOLD_THRUST = 0.030D;

    /** Ограничение скорости в полёте вверх и вниз. */
    public static double FLY_MAX_UP = 0.20D;
    public static double FLY_MAX_DOWN = 0.20D;

    /**
     * Насколько быстро скорость в полёте подтягивается к целевой.
     * 1 значит мгновенно и дёргано, 0.2 даёт плавный, предсказуемый кораблик.
     */
    public static double FLY_RESPONSE = 0.14D;

    /**
     * Вертикальный сдвиг вагонетки относительно низа кубика.
     * <p>
     * Вагонетка высокая, поэтому её опускаем: кубик обязан сидеть НА ней, а не тонуть
     * внутри. Сам кубик при этом никуда не двигается, вагонетка только декорация.
     */
    public static double BOAT_Y_OFFSET = -0.62D;

    /** Плотность следа за кубиком. */
    public static int TRAIL_AMOUNT = 6;

    /** Разброс следа за кубиком. */
    public static double TRAIL_SPREAD = 0.18D;

    // ==================== ПИНГ ====================

    /**
     * Базовое окно «буфера прыжка»: нажатие, прилетевшее чуть раньше приземления,
     * не теряется, а срабатывает в момент касания земли. К нему прибавляется пинг.
     */
    public static long JUMP_BUFFER_BASE_MILLIS = 100L;

    /**
     * Койот-время выключено намеренно.
     * <p>
     * Прыжок разрешён только с земли: любое окно "уже в воздухе, но ещё можно"
     * ощущается как прыжок из ниоткуда. Нажатие при этом не теряется - оно ждёт
     * приземления в буфере.
     */
    public static long COYOTE_BASE_MILLIS = 0L;

    /** Учитывать ли пинг вообще (выключение делает игру одинаковой для всех). */
    public static boolean PING_COMPENSATION = true;

    /** Больше этого пинга (мс) уровень начинает притормаживать. */
    public static int PING_SLOWDOWN_START = 140;

    /** Максимальное замедление уровня на большом пинге (0.25 = на четверть медленнее). */
    public static double PING_SLOWDOWN_MAX = 0.25D;

    /** Верхняя граница учитываемого пинга, чтобы разрыв связи не ломал физику. */
    public static int PING_CAP = 400;

    // ==================== ЛИНИЯ ИЗ ЧАСТИЦ ====================

    /**
     * Сдвиг линии вбок от оси движения кубика.
     * <p>
     * Линия стоит на месте: она показывает трассу, а не следует за строителем.
     */
    public static double LINE_SIDE_OFFSET = 0.75D;

    /** Высота линии относительно низа кубика. */
    public static double LINE_HEIGHT = 0.5D;

    /** Шаг между точками линии, блоков. */
    public static double LINE_STEP = 0.5D;

    /** Как часто перерисовывается линия, тиков. */
    public static int LINE_PERIOD_TICKS = 4;

    /** Каждая N-я точка линии рисуется «искрой», остальные - техническим маркером. */
    public static int LINE_SPARK_EVERY = 6;

    /** Шаг предпросмотра высоты проигрыша, блоков. */
    public static double FALL_PREVIEW_STEP = 2.0D;

    /** Сколько блоков позади строителя линия ещё рисуется. */
    public static double LINE_BEHIND = 8.0D;

    /** Сколько блоков линии видно за раз. Дальше её просто не рисуем. */
    public static double LINE_VIEW_DISTANCE = 48.0D;

    /** Высота столбиков-маркеров старта и финиша. */
    public static double MARKER_HEIGHT = 3.0D;

    // ==================== СТАРТ ====================

    /** Радиус вокруг стартового блока, при заходе в который начинается игра. */
    public static double START_RADIUS = 1.6D;

    /** Допустимая разница по высоте до стартового блока. */
    public static double START_HEIGHT_TOLERANCE = 2.5D;

    // ==================== ЗАГРУЗКА/СОХРАНЕНИЕ ====================

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
        registerString("cube_face", () -> CUBE_FACE, value -> CUBE_FACE = value);
        registerDouble("cube_yaw_offset", () -> CUBE_YAW_OFFSET, value -> CUBE_YAW_OFFSET = (float) value);
        registerDouble("cube_y_offset", () -> CUBE_Y_OFFSET, value -> CUBE_Y_OFFSET = value);
        registerDouble("camera_distance", () -> CAMERA_DISTANCE, value -> CAMERA_DISTANCE = value);
        registerDouble("camera_lead", () -> CAMERA_LEAD, value -> CAMERA_LEAD = value);
        registerDouble("camera_eye_height", () -> CAMERA_EYE_HEIGHT, value -> CAMERA_EYE_HEIGHT = value);
        registerDouble("camera_pitch", () -> CAMERA_PITCH, value -> CAMERA_PITCH = (float) value);
        registerDouble("camera_smooth", () -> CAMERA_SMOOTH, value -> CAMERA_SMOOTH = clamp01(value));
        registerBoolean("lock_camera", () -> LOCK_CAMERA, value -> LOCK_CAMERA = value);
        registerString("camera_mode", () -> CAMERA_MODE, value -> CAMERA_MODE = value);
        registerBoolean("debug", () -> DEBUG, value -> DEBUG = value);
        registerDouble("look_target_distance", () -> LOOK_TARGET_DISTANCE,
            value -> LOOK_TARGET_DISTANCE = Math.max(16.0D, Math.min(1_000_000.0D, value)));
        registerBoolean("camera_lock_on_change", () -> CAMERA_LOCK_ON_CHANGE,
            value -> CAMERA_LOCK_ON_CHANGE = value);
        registerInt("camera_lock_period", () -> CAMERA_LOCK_PERIOD,
            value -> CAMERA_LOCK_PERIOD = Math.max(1, Math.min(20, value)));
        registerInt("slowness_level", () -> SLOWNESS_LEVEL,
            value -> SLOWNESS_LEVEL = Math.max(0, Math.min(20, value)));
        registerDouble("fall_preview_step", () -> FALL_PREVIEW_STEP,
            value -> FALL_PREVIEW_STEP = Math.max(0.5D, Math.min(16.0D, value)));
        registerDouble("speed", () -> SPEED, value -> SPEED = Math.max(0.5D, value));
        registerDouble("gravity", () -> GRAVITY, value -> GRAVITY = Math.max(0.001D, value));
        registerDouble("jump_velocity", () -> JUMP_VELOCITY, value -> JUMP_VELOCITY = Math.max(0.05D, value));
        registerDouble("max_fall_speed", () -> MAX_FALL_SPEED, value -> MAX_FALL_SPEED = Math.max(0.1D, value));
        registerDouble("cube_half", () -> CUBE_HALF, value -> CUBE_HALF = Math.max(0.05D, Math.min(0.5D, value)));
        registerDouble("fall_death_depth", () -> FALL_DEATH_DEPTH, value -> FALL_DEATH_DEPTH = Math.max(2.0D, value));
        registerBoolean("ceiling_kills", () -> CEILING_KILLS, value -> CEILING_KILLS = value);
        registerBoolean("hold_to_jump", () -> HOLD_TO_JUMP, value -> HOLD_TO_JUMP = value);
        registerInt("min_ground_ticks", () -> MIN_GROUND_TICKS,
            value -> MIN_GROUND_TICKS = Math.max(0, Math.min(10, value)));
        registerInt("auto_jump_ground_ticks", () -> AUTO_JUMP_GROUND_TICKS,
            value -> AUTO_JUMP_GROUND_TICKS = Math.max(0, Math.min(10, value)));
        registerBoolean("cube_rotation", () -> CUBE_ROTATION, value -> CUBE_ROTATION = value);
        registerString("cube_style", () -> CUBE_STYLE, value -> CUBE_STYLE = value);
        registerDouble("cube_head_y_offset", () -> CUBE_HEAD_Y_OFFSET,
            value -> CUBE_HEAD_Y_OFFSET = value);
        registerDouble("cube_spins_per_jump", () -> CUBE_SPINS_PER_JUMP,
            value -> CUBE_SPINS_PER_JUMP = Math.max(0.0D, Math.min(8.0D, value)));
        registerDouble("fly_response", () -> FLY_RESPONSE,
            value -> FLY_RESPONSE = Math.max(0.02D, Math.min(1.0D, value)));
        registerBoolean("cube_rotation", () -> CUBE_ROTATION, value -> CUBE_ROTATION = value);
        registerDouble("cube_spins_per_jump", () -> CUBE_SPINS_PER_JUMP,
            value -> CUBE_SPINS_PER_JUMP = Math.max(0.0D, Math.min(8.0D, value)));
        registerDouble("fly_response", () -> FLY_RESPONSE,
            value -> FLY_RESPONSE = Math.max(0.02D, Math.min(1.0D, value)));
        registerInt("respawn_delay_ticks", () -> RESPAWN_DELAY_TICKS,
            value -> RESPAWN_DELAY_TICKS = Math.max(0, Math.min(200, value)));
        registerDouble("fly_gravity", () -> FLY_GRAVITY, value -> FLY_GRAVITY = Math.max(0.001D, value));
        registerDouble("fly_thrust", () -> FLY_THRUST, value -> FLY_THRUST = Math.max(0.01D, value));
        registerDouble("fly_hold_thrust", () -> FLY_HOLD_THRUST, value -> FLY_HOLD_THRUST = Math.max(0.0D, value));
        registerDouble("fly_max_up", () -> FLY_MAX_UP, value -> FLY_MAX_UP = Math.max(0.05D, value));
        registerDouble("fly_max_down", () -> FLY_MAX_DOWN, value -> FLY_MAX_DOWN = Math.max(0.05D, value));
        registerDouble("boat_y_offset", () -> BOAT_Y_OFFSET, value -> BOAT_Y_OFFSET = value);
        registerInt("trail_amount", () -> TRAIL_AMOUNT,
            value -> TRAIL_AMOUNT = Math.max(0, Math.min(40, value)));
        registerDouble("trail_spread", () -> TRAIL_SPREAD,
            value -> TRAIL_SPREAD = Math.max(0.0D, Math.min(1.0D, value)));
        registerLong("jump_buffer_millis", () -> JUMP_BUFFER_BASE_MILLIS,
            value -> JUMP_BUFFER_BASE_MILLIS = Math.max(0L, Math.min(1000L, value)));
        registerLong("coyote_millis", () -> COYOTE_BASE_MILLIS,
            value -> COYOTE_BASE_MILLIS = Math.max(0L, Math.min(1000L, value)));
        registerBoolean("ping_compensation", () -> PING_COMPENSATION, value -> PING_COMPENSATION = value);
        registerInt("ping_slowdown_start", () -> PING_SLOWDOWN_START,
            value -> PING_SLOWDOWN_START = Math.max(0, value));
        registerDouble("ping_slowdown_max", () -> PING_SLOWDOWN_MAX,
            value -> PING_SLOWDOWN_MAX = Math.max(0.0D, Math.min(0.75D, value)));
        registerInt("ping_cap", () -> PING_CAP, value -> PING_CAP = Math.max(50, value));
        registerDouble("line_side_offset", () -> LINE_SIDE_OFFSET, value -> LINE_SIDE_OFFSET = value);
        registerDouble("line_height", () -> LINE_HEIGHT, value -> LINE_HEIGHT = value);
        registerDouble("line_step", () -> LINE_STEP, value -> LINE_STEP = Math.max(0.1D, Math.min(4.0D, value)));
        registerInt("line_period_ticks", () -> LINE_PERIOD_TICKS,
            value -> LINE_PERIOD_TICKS = Math.max(1, Math.min(40, value)));
        registerInt("line_spark_every", () -> LINE_SPARK_EVERY,
            value -> LINE_SPARK_EVERY = Math.max(0, Math.min(64, value)));
        registerDouble("line_behind", () -> LINE_BEHIND, value -> LINE_BEHIND = Math.max(0.0D, value));
        registerDouble("line_view_distance", () -> LINE_VIEW_DISTANCE,
            value -> LINE_VIEW_DISTANCE = Math.max(4.0D, Math.min(256.0D, value)));
        registerDouble("marker_height", () -> MARKER_HEIGHT,
            value -> MARKER_HEIGHT = Math.max(0.5D, Math.min(16.0D, value)));
        registerDouble("start_radius", () -> START_RADIUS, value -> START_RADIUS = Math.max(0.5D, value));
        registerDouble("start_height_tolerance", () -> START_HEIGHT_TOLERANCE,
            value -> START_HEIGHT_TOLERANCE = Math.max(0.5D, value));
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    @NonNull
    public static List<String> getKeys() {
        return new ArrayList<>(KEYS.keySet());
    }

    @Nullable
    public static String getValue(@NonNull String key) {
        Accessor accessor = KEYS.get(key.toLowerCase(Locale.ROOT));
        return accessor == null ? null : accessor.get();
    }

    /**
     * @return true, если ключ найден и значение разобрано
     */
    public static boolean setValue(@NonNull String key, @NonNull String rawValue) {
        Accessor accessor = KEYS.get(key.toLowerCase(Locale.ROOT));
        if (accessor == null) return false;
        try {
            accessor.set(rawValue);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Версия набора настроек.
     * <p>
     * Нужна вот зачем: команда {@code /pb 2d set} записывает в config.yml ВЕСЬ набор
     * ключей разом, после чего новые значения по умолчанию из обновлений плагина
     * до сервера уже не доходят - конфиг всегда сильнее. Поэтому при росте версии
     * перечисленные ниже ключи принудительно возвращаются к умолчанию, а всё
     * остальное, что настроил админ, остаётся как было.
     */
    public static final int CONFIG_VERSION = 2;

    /** Ключи, чьи значения по умолчанию изменились в этой версии. */
    private static final String[] RESET_IN_V2 = {
        "camera_lead", "slowness_level", "min_ground_ticks",
        "jump_buffer_millis", "coyote_millis", "camera_mode"
    };

    public static void load(@NonNull ParkourBeat plugin) {
        try {
            ConfigurationSection section = plugin.getConfig().getConfigurationSection("two_d");
            if (section == null) return;

            int storedVersion = section.getInt("config_version", 1);
            java.util.Set<String> forced = new java.util.HashSet<>();
            if (storedVersion < CONFIG_VERSION) {
                forced.addAll(java.util.Arrays.asList(RESET_IN_V2));
                plugin.getLogger().info("2D: настройки " + forced
                    + " возвращены к значениям по умолчанию (версия конфига " + storedVersion
                    + " -> " + CONFIG_VERSION + ")");
            }

            for (String key : section.getKeys(false)) {
                if (key.equals("config_version")) continue;
                if (forced.contains(key)) continue;

                Object value = section.get(key);
                if (value == null) continue;
                setValue(key, String.valueOf(value));
            }

            if (storedVersion < CONFIG_VERSION) save(plugin);
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось прочитать настройки two_d из config.yml: " + e.getMessage());
        }
    }

    public static void save(@NonNull ParkourBeat plugin) {
        try {
            for (Map.Entry<String, Accessor> entry : KEYS.entrySet()) {
                plugin.getConfig().set("two_d." + entry.getKey(), entry.getValue().get());
            }
            plugin.getConfig().set("two_d.config_version", CONFIG_VERSION);
            plugin.saveConfig();
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось сохранить настройки two_d в config.yml: " + e.getMessage());
        }
    }

    /**
     * Сторона блока-кубика, заданная админом вручную. null - считать автоматически.
     */
    @Nullable
    public static BlockFace getManualCubeFace() {
        String value = CUBE_FACE;
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("AUTO")) return null;
        try {
            return BlockFace.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
