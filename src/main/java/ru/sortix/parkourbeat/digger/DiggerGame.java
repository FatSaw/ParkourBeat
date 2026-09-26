package ru.sortix.parkourbeat.digger;

import lombok.Getter;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.DirectionChecker;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.PickaxeCue;
import ru.sortix.parkourbeat.player.music.MusicTracksManager;
import ru.sortix.parkourbeat.rating.AccuracyGrade;
import ru.sortix.parkourbeat.replay.ReplayManager;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.stats.RunResult;
import ru.sortix.parkourbeat.utils.text.PbText;
import ru.sortix.parkourbeat.utils.text.Theme;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * ОДИН ЗАБЕГ В РЕЖИМЕ «КОПАТЕЛЬ».
 * <p>
 * Игрок сидит на невидимом носителе, который везёт его вперёд по тоннелю с
 * постоянной скоростью, выведенной из BPM карты. Руды стоят обычными блоками в мире,
 * поэтому сервер не шлёт по ним НИ ОДНОГО пакета движения: клиент рисует статичный
 * тоннель сам, а едет только камера. Из-за этого картинка не зависит от пинга вообще.
 * <p>
 * Единственное место, где пинг вообще участвует, - момент прихода пакета разрушения,
 * и он компенсируется в {@link DiggerJudge}.
 * <p>
 * Игрок в творческом режиме: только он даёт мгновенное разрушение любой руды без
 * прокапывания, а значит и одинаковую механику для человека с любым железом и любым
 * каналом. Всё, что творческий режим разрешает лишнего, вырезано в
 * {@link DiggerManager}.
 */
public class DiggerGame {

    public enum State {
        COUNTDOWN,
        RUNNING,
        FINISHED
    }

    private final @NonNull ParkourBeat plugin;
    @Getter
    private final @NonNull Player player;
    @Getter
    private final @NonNull Level level;
    private final @NonNull DiggerLevelSettings settings;
    private final @NonNull World world;

    private final @NonNull DirectionChecker directionChecker;
    private final boolean axisX;
    private final int sign;

    /**
     * Профиль скорости карты: он же переводит время в расстояние и обратно.
     * <p>
     * Снимается ОДИН РАЗ на старте. Если строитель поправит зоны прямо во время чужого
     * заезда, тот доедет по старому профилю - это правильнее, чем менять геометрию
     * трассы под человеком на ходу.
     */
    private @NonNull DiggerSpeedProfile profile;

    private @NonNull Location startLocation;
    private double startCoordinate;

    /** Сколько блоков обязано быть между стартом и первой рудой. */
    private final double speed;

    private @NonNull List<DiggerNote> notes;

    /**
     * Что этот забег уже разобрал.
     * <p>
     * Живёт в забеге, а не в ноте: карта одна на всех, и на ней одновременно может
     * ехать хоть десять человек. Флаг на общем объекте означал бы, что первый
     * попавший гасит ноту всем остальным.
     */
    private final Set<Long> judgedKeys = new HashSet<>();

    /**
     * Руды, которых для этого игрока сейчас не существует.
     * <p>
     * Сюда попадают две разные вещи, и обе выглядят одинаково: ещё не появившиеся ноты
     * и уже сломанные. Руда НЕ ломается по-настоящему - событие отменяется, а игроку
     * отправляется поддельное изменение блока на воздух. В мире всё стоит на месте,
     * поэтому по одному тоннелю могут ехать хоть тридцать человек.
     * <p>
     * Читается из сетевого потока ProtocolLib, пишется из основного.
     */
    private final Map<Long, DiggerNote> ghosts = new ConcurrentHashMap<>();

    /**
     * Ноты, которые игрок реально увидел.
     * <p>
     * Промах засчитывается только по ним. Руда, которая никогда не появлялась на экране,
     * промахом быть не может по определению - а именно так выглядела разъехавшаяся
     * геометрия: десяток нот «пропущен» в первую же секунду забега.
     */
    private final Set<Long> revealedKeys = new HashSet<>();

    /** Спрятан ли барьер спавна лично от игрока. */
    private boolean barrierHidden = false;

    /**
     * На каком расстоянии призраков имеет смысл расставлять заново, блоков.
     * <p>
     * Должно накрывать всю прогрузку клиента, а не ближние чанки: когда сервер
     * досылает игроку чанк, тот приезжает с НАСТОЯЩИМИ блоками, и все руды в нём
     * проступают обратно. При прежних 64 блоках чанки дальше четырёх так и оставались
     * с открытой картой - ровно та же дыра, что и у окна прятания.
     */
    private static final int REASSERT_RANGE = 256;

    /** Докуда уже показаны. Идёт ровно по краю окна видимости. */
    private int revealPointer = 0;

    /**
     * Сколько ждём загрузки трека, прежде чем начать без него.
     * <p>
     * Четыре секунды: пак либо уже у игрока, либо доедет за это время. Ждать дольше
     * бессмысленно - человек просто стоит и смотрит в надпись.
     */
    private static final int MUSIC_WAIT_TICKS = 80;

    /** Сколько чанков по ходу движения держатся загруженными. */
    private static final int CHUNKS_AHEAD = 8;


    /** Указатель на ближайшую ещё не разобранную ноту - по нему ищутся пропуски. */
    private int missPointer = 0;

    /**
     * Сколько нот выброшено из забега как стоящие вплотную к старту.
     * <p>
     * Показывается строителю в тесте: молча терять ноты нельзя, иначе он будет искать
     * несуществующую ошибку в разметке.
     */
    private int skippedAtStart = 0;

    /** Промахов подряд: на шестом забег заканчивается. */
    private int missStreak = 0;

    /** Цвет боссбара, который просит световое шоу. null - оставить свой. */
    private @Nullable ru.sortix.parkourbeat.levels.settings.LevelBossBarColor bossBarColor = null;

    /** Забег провален, а не пройден. От этого зависит и заголовок, и запись в статистику. */
    private boolean failed = false;

    /** Забег брошен самим игроком через предмет выхода, а не проигран и не доигран. */
    private boolean quitByPlayer = false;

    @Getter
    private @NonNull DiggerScore score;
    private final @NonNull DiggerJudge judge;
    private final @NonNull DiggerShow show;

    /**
     * ВСЁ ВРЕМЕННОЕ СВЕТОВОЕ ШОУ УРОВНЯ.
     * <p>
     * Небо, погода, время суток, вспышки, качели неба, цвет боссбара - всё это живёт в
     * {@link LightShowRunner}, и он НИ РАЗУ не запускался в «Копателе»: его заводили
     * только обычная игра и предпросмотр в редакторе. Поэтому строитель выставлял
     * погоду и время, видел их в предпросмотре и не видел ни того, ни другого в заезде -
     * ровно то, что выглядит как «настройки не работают».
     * <p>
     * Запускается тем же способом, что и на обычном уровне, и тикается временем ТРЕКА,
     * а не системными часами: таймкоды светового шоу расставлены по музыке.
     */
    private final @NonNull ru.sortix.parkourbeat.levels.LightShowRunner lightShow;

    /**
     * Чудоэффекты и ламповые стены.
     * <p>
     * Их тикал ТОЛЬКО обычный игровой режим - ровно та же дыра, что была со световым
     * шоу. Строитель расставлял чудоэффекты, видел их в предпросмотре редактора и не
     * видел ни одного в заезде: в «Копателе» их некому было проигрывать.
     */
    private @Nullable ru.sortix.parkourbeat.levels.wonder.WonderRunner wonderRunner = null;
    private @Nullable ru.sortix.parkourbeat.levels.lamps.LampRunner lampRunner = null;

    /**
     * Идёт ли забег в зачёт.
     * <p>
     * Тест из редактора не пишется в статистику: строитель проезжает свою карту
     * по двадцать раз подряд, и все эти заезды в таблице рекордов не нужны никому.
     */
    private final boolean ranked;

    /** Кирка, которая сейчас в руке. Меняется только когда её реально надо сменить. */
    private @Nullable Material currentPickaxe = null;

    @Getter
    private @NonNull State state = State.COUNTDOWN;

    private @Nullable Entity carrier;
    private @Nullable BossBar bossBar;
    private final @NonNull DiggerHud hud;

    private long startMillis = 0L;

    /** Когда начался отсчёт. По нему ловится забег, застрявший до старта. */
    private final long createdMillis = System.currentTimeMillis();
    private long lastNoteMillis = 0L;
    private int ticks = 0;

    /**
     * Когда носитель последний раз двигали. Нужно, чтобы досчитать его положение
     * ВНУТРИ такта: удар приходит в любой момент между тактами, а координата
     * сущности обновляется только на такте.
     */
    private long lastMoveMillis = 0L;

    /** Что показать в HUD последним ударом. */
    private @NonNull String lastHitDisplay = "";
    private long lastHitAtMillis = 0L;

    /** Висит ли сейчас в экшнбаре наша подпись: чтобы погасить её ровно один раз. */
    private boolean actionBarBusy = false;

    /** Когда в последний раз сломали ноту - по нему отсекаются холостые взмахи. */
    private long lastBreakMillis = 0L;
    private long lastWastedPunishMillis = 0L;

    // Состояние игрока до забега - его надо вернуть в целости.
    private final @NonNull GameMode previousGameMode;
    private final ItemStack[] previousInventory;
    private final @NonNull Location previousLocation;
    private final boolean previousAllowFlight;

    public DiggerGame(@NonNull ParkourBeat plugin, @NonNull Player player, @NonNull Level level) {
        this(plugin, player, level, false);
    }

    public DiggerGame(@NonNull ParkourBeat plugin, @NonNull Player player, @NonNull Level level, boolean ranked) {
        this.plugin = plugin;
        this.player = player;
        this.level = level;
        this.ranked = ranked;
        this.settings = level.getLevelSettings().getGameSettings().getDiggerSettings();
        this.world = level.getWorld();

        this.directionChecker = level.getLevelSettings().getDirectionChecker();
        DirectionChecker.Direction direction = this.directionChecker.direction();
        this.axisX = direction == DirectionChecker.Direction.POSITIVE_X
            || direction == DirectionChecker.Direction.NEGATIVE_X;
        this.sign = this.directionChecker.isNegative() ? -1 : 1;

        // ОДИН СПАВН НА ВСЕХ.
        //
        // Строитель появляется в редакторе на спавне уровня, и точка отсчёта карты
        // обязана быть там же: иначе руды и забег считают расстояния от разных мест.
        // Точка отсчёта, если её кто-то сдвинул, подтягивается обратно к спавну.
        Location origin = DiggerAutoMapper.defaultOrigin(level);
        if (this.settings.getOrigin(this.world) == null) this.settings.setOrigin(origin);
        this.startLocation = origin.clone();
        this.startCoordinate = this.directionChecker.getCoordinate(this.startLocation);

        this.speed = this.settings.resolveSpeed();
        this.profile = this.settings.speedProfile();

        this.notes = this.settings.sortedNotes(this.axisX, this.sign);
        this.score = new DiggerScore(this.notes.size());
        this.judge = new DiggerJudge(plugin, player);
        this.show = new DiggerShow(plugin, player, level, this.axisX, this.sign);
        this.lightShow = new ru.sortix.parkourbeat.levels.LightShowRunner(
            plugin, player, level.getLightShow(), barColor -> this.bossBarColor = barColor);
        this.hud = new DiggerHud(this.world, player, this.axisX, this.sign);

        // НОТЫ ВПЛОТНУЮ К СТАРТУ ПРОСТО НЕ УЧАСТВУЮТ.
        //
        // Раньше вместо этого старт забега сдвигался НАЗАД на недостающие блоки, и
        // игрок появлялся до двенадцати блоков позади того места, где стоит строитель.
        // Два разных спавна на одной карте - это гарантированная путаница: строитель
        // ставит руду там, где стоит, а проверяет её оттуда, где не стоял никогда.
        //
        // Нота, до которой от старта меньше длины разгона, физически непроходима:
        // она влетает в лицо раньше, чем её вообще успеет нарисовать клиент. Считать
        // её промахом нечестно, поэтому она выбрасывается из забега целиком - на
        // саму карту это не влияет, в редакторе она остаётся на месте.
        List<DiggerNote> playable = new ArrayList<>(this.notes.size());
        int dropped = 0;
        for (DiggerNote note : this.notes) {
            if (this.distanceOf(note) < DiggerTuning.START_GAP_BLOCKS) {
                dropped++;
                continue;
            }
            playable.add(note);
        }
        if (dropped > 0) {
            this.notes = playable;
            this.score = new DiggerScore(this.notes.size());
            this.skippedAtStart = dropped;
        }

        for (DiggerNote note : this.notes) {
            this.lastNoteMillis = Math.max(this.lastNoteMillis, this.noteMillis(note));
        }

        this.previousGameMode = player.getGameMode();
        this.previousInventory = player.getInventory().getContents().clone();
        this.previousLocation = player.getLocation().clone();
        this.previousAllowFlight = player.getAllowFlight();
    }

    // ==================== ВРЕМЯ И ГЕОМЕТРИЯ ====================

    /** Печатать ли в чат числа судейства. Включается командой. */
    private boolean debug = false;

    public void setDebug(boolean debug) {
        this.debug = debug;
        if (!debug) return;
        this.player.sendMessage(PbText.of("&8[копатель] &7старт=&f"
            + String.format("%.1f", this.startCoordinate)
            + " &7ось=&f" + (this.axisX ? "X" : "Z")
            + " &7знак=&f" + this.sign
            + " &7скорость=&f" + String.format("%.2f", this.speed)
            + " &7нот=&f" + this.notes.size()));

        int shown = 0;
        for (DiggerNote note : this.notes) {
            if (shown++ >= 5) break;
            this.player.sendMessage(PbText.of("&8[копатель] &7нота &f"
                + note.getOre().getDisplay() + " &7на &f"
                + String.format("%.1f", this.distanceOf(note)) + " &7блоках"));
        }
    }

    /**
     * Отметка о том, что событие разрушения ДО НАС ДОШЛО.
     * <p>
     * Отдельной строкой, потому что именно её отсутствие означает, что событие гасит
     * кто-то другой раньше нас, и искать причину надо не в судействе.
     */
    public void debugBreakArrived(boolean cancelledByOthers) {
        this.debug("событие разрушения получено"
            + (cancelledByOthers ? " (было отменено другим слушателем)" : ""));
    }

    private void debug(@NonNull String message) {
        if (!this.debug || !this.player.isOnline()) return;
        this.player.sendMessage(PbText.of("&8[копатель] &7" + message));
    }

    /**
     * Когда забег заканчивается, мс от начала трека.
     * <p>
     * Забег доигрывает до конца музыки, а не до последней ноты: в тихом аутро нот нет.
     * Если длительность трека неизвестна - последняя нота плюс аутро.
     */
    private long endMillis() {
        return Math.max(
            this.lastNoteMillis + DiggerTuning.OUTRO_MILLIS,
            this.settings.getTrackDurationMillis());
    }

    /** Сколько миллисекунд идёт трек. До старта - отрицательное. */
    public long trackMillis() {
        if (this.startMillis == 0L) return -1L;
        return System.currentTimeMillis() - this.startMillis;
    }

    /**
     * Таймкод ноты: момент, когда по ней надо ударить.
     * <p>
     * Из расстояния вычитается точка удара: блок стоит на длину вытянутой руки дальше
     * того места, где игрок окажется на долю, и без этой поправки таймкод уезжал бы
     * вперёд ровно на неё.
     */
    public long noteMillis(@NonNull DiggerNote note) {
        return this.settings.millisAt(this.distanceOf(note) - DiggerTuning.HIT_LEAD_BLOCKS);
    }

    /**
     * СКОЛЬКО ИГРОК ПРОЕХАЛ НА САМОМ ДЕЛЕ.
     * <p>
     * Именно по этому числу идёт всё судейство, а не по часам трека. Разница
     * принципиальная: любая заминка сервера, подгрузка чанка или лаг на миллисекунды
     * расходят часы и реальное положение камеры, и тогда игрок бьёт по руде, которая
     * ПЕРЕД НИМ, а судья считает, что он опоздал на полсекунды. Ровно от этого все
     * удары превращались в промахи.
     */
    private double travelled() {
        Entity carrier = this.carrier;
        Location at = carrier != null && carrier.isValid()
            ? carrier.getLocation()
            : this.player.getLocation();
        double coordinate = this.axisX ? at.getX() : at.getZ();
        return (coordinate - this.startCoordinate) * this.sign;
    }

    /**
     * ГДЕ КАМЕРА ПРЯМО СЕЙЧАС, А НЕ НА ПРОШЛОМ ТАКТЕ.
     * <p>
     * Координата сущности обновляется раз в такт, а удар приходит когда угодно между
     * тактами. Значит судейство берёт положение, устаревшее в среднем на полтакта, и
     * до пятидесяти миллисекунд - это на обычной скорости почти полблока чистого
     * разброса, накинутого поверх настоящей ошибки игрока. Отсюда и одиночные «+100»
     * там, где человек бил ровно.
     * <p>
     * Дотягиваем координату по времени и по местной скорости. Больше такта не
     * досчитываем никогда: если сервер провис, носитель НЕ ехал, и предполагать
     * обратное значило бы засчитывать движение, которого не было.
     */
    private double travelledNow() {
        double base = this.travelled();
        if (this.lastMoveMillis == 0L) return base;

        long elapsed = System.currentTimeMillis() - this.lastMoveMillis;
        if (elapsed <= 0L) return base;
        if (elapsed > 50L) elapsed = 50L;

        return base + this.profile.speedAtDistance(base) * elapsed / 1000.0D;
    }

    /** Расстояние ноты от старта вдоль оси уровня, блоков. */
    private double distanceOf(@NonNull DiggerNote note) {
        double coordinate = (this.axisX ? note.getX() : note.getZ()) + 0.5D;
        return (coordinate - this.startCoordinate) * this.sign;
    }

    /** Единичный вектор направления уровня. */
    @NonNull
    private Vector directionVector() {
        return this.axisX
            ? new Vector(this.sign, 0.0D, 0.0D)
            : new Vector(0.0D, 0.0D, this.sign);
    }

    /** Где носитель обязан быть в этот момент трека. */
    @NonNull
    private Location expectedLocation(long millis) {
        double distance = this.profile.distanceAt(Math.max(0L, millis));
        Location location = this.startLocation.clone();
        location.add(this.directionVector().multiply(distance));
        location.setY(location.getY() + DiggerTuning.CARRIER_Y_OFFSET);
        return location;
    }

    // ==================== ЗАПУСК ====================

    public void start() {
        this.restoreNoteBlocks();

        // ВСЯ КАРТА ПРЯЧЕТСЯ СРАЗУ, ещё до отсчёта: к моменту, когда игрок увидит
        // тоннель, в нём не должно быть видно ни одной руды - ни рядом, ни на горизонте.
        this.hideEverything();
        this.updateSpawns(0.0D);
        this.preparePlayer();
        this.reapplyInventoryLater();

        // РОВНО ТА ЖЕ ТОЧКА, ГДЕ СТОИТ СТРОИТЕЛЬ. Высота посадки добавляется отдельно
        // и на геометрию карты не влияет: она поднимает только камеру.
        Location spawn = this.startLocation.clone();
        spawn.setY(spawn.getY() + DiggerTuning.CARRIER_Y_OFFSET);
        spawn.setYaw(this.axisX ? (this.sign > 0 ? -90.0f : 90.0f) : (this.sign > 0 ? 0.0f : 180.0f));
        spawn.setPitch(0.0f);
        this.player.teleport(spawn);

        this.carrier = DiggerCarrier.spawn(this.world, this.startLocation.clone(), this.player);
        DiggerEntityHider.drive(this.carrier.getEntityId(), this.player.getUniqueId());
        this.hud.spawn(spawn);
        this.hideSpawnBarrier();

        if (this.skippedAtStart > 0 && !this.ranked) {
            this.player.sendMessage(PbText.of("&eВплотную к старту&7: пропущено нот &f"
                + this.skippedAtStart + " &8(ближе " + (int) DiggerTuning.START_GAP_BLOCKS + " блоков)"));
        }

        // В тесте боссбар показывается всегда: строителю нужен таймкод, чтобы знать,
        // на какой секунде ставить вспышку, смену неба или кирку.
        if (DiggerTuning.SHOW_BOSS_BAR || !this.ranked) {
            this.bossBar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SEGMENTED_10);
            this.bossBar.addPlayer(this.player);
        }

        // Шоу заводится вместе с забегом и сразу встаёт на базовое небо уровня.
        this.lightShow.startShow();

        this.waitForMusic(0);
    }

    /**
     * Убрать барьер спавна с глаз игрока.
     * <p>
     * Опора строителя торчит прямо в кадре весь забег. Ломать её нельзя - строителю
     * она нужна, - поэтому она просто не показывается тому, кто едет.
     */
    private void hideSpawnBarrier() {
        Location barrier = this.spawnBarrier();
        if (this.world.getBlockAt(barrier).getType().isAir()) return;
        this.player.sendBlockChange(barrier, Material.AIR.createBlockData());
        this.barrierHidden = true;
    }

    private void restoreSpawnBarrier() {
        if (!this.barrierHidden || !this.player.isOnline()) return;
        this.barrierHidden = false;
        Location barrier = this.spawnBarrier();
        this.player.sendBlockChange(barrier, this.world.getBlockAt(barrier).getBlockData());
    }

    /**
     * ГДЕ ЛЕЖИТ ОПОРА СТРОИТЕЛЯ.
     * <p>
     * Высота берётся у СПАВНА УРОВНЯ, а не из общих настроек. Высота площадки стала
     * выбором при создании - от неё зависит, полное у карты небо или половинное, - и
     * на карте с половиной неба опора лежит на пятнадцатой высоте, а {@code spawn_y}
     * по-прежнему говорит сто двадцать. Прятался бы при этом воздух в сотне блоков над
     * тоннелем, а настоящая опора так и торчала бы в кадре весь заезд.
     */
    @NonNull
    private Location spawnBarrier() {
        Location spawn = this.level.getSpawn();
        int y = spawn != null && spawn.getWorld() == this.world
            ? spawn.getBlockY() - 1
            : DiggerTuning.SPAWN_Y;
        return new Location(this.world,
            DiggerWorldTemplate.SPAWN_X, y, DiggerWorldTemplate.SPAWN_Z);
    }

    /**
     * ЖДЁМ, ПОКА ДОЕДЕТ РЕСУРСПАК.
     * <p>
     * Забег начинался сразу, а пак у игрока в этот момент ещё качался - трек включался
     * секунд через десять после старта, и вся карта оказывалась не в такт. Ждём
     * подтверждения от раздатчика паков, но не бесконечно: если пак не доехал за
     * отведённое время, играем без него - тишина хуже, чем отсутствие забега.
     */
    private void waitForMusic(int waitedTicks) {
        if (this.state == State.FINISHED) return;

        if (waitedTicks >= MUSIC_WAIT_TICKS || this.isMusicReady()) {
            // Про неудачу молчим: в чат это писалось каждый заход и было чистым спамом,
            // а игрок и так услышит, что музыки нет.
            // ПАУЗА ПОСЛЕ ЗАГРУЗКИ ТРЕКА.
            //
            // Клиент сообщает о готовности ресурспака раньше, чем успевает показать
            // игроку тоннель: отсчёт начинался поверх ещё чёрного экрана, и первая
            // цифра проходила мимо. Полторы секунды - это ровно то время, за которое
            // человек успевает увидеть, где он стоит и куда смотрит.
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (this.state == State.FINISHED) return;
                this.runCountdown(DiggerTuning.COUNTDOWN_SECONDS);
            }, DiggerTuning.MUSIC_SETTLE_TICKS);
            return;
        }

        if (waitedTicks == 0) {
            this.player.showTitle(Title.title(
                PbText.of("&fЗагрузка музыки..."), PbText.of("&b\u266B"),
                Title.Times.of(Duration.ZERO, Duration.ofSeconds(5), Duration.ofMillis(200))));
        }

        Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.waitForMusic(waitedTicks + 10), 10L);
    }

    /** Подтверждён ли у игрока пак именно этого трека. */
    private boolean isMusicReady() {
        try {
            ru.sortix.parkourbeat.player.music.MusicTrack track =
                this.level.getLevelSettings().getGameSettings().getMusicTrack();
            if (track == null) return true;

            // Раздатчик паков живёт в конкретной платформе, а не в общем интерфейсе,
            // поэтому спрашиваем его осторожно: платформа может быть и другой.
            Object platform = this.plugin
                .get(ru.sortix.parkourbeat.player.music.MusicTracksManager.class).getPlatform();
            if (!(platform instanceof ru.sortix.parkourbeat.player.music.platform.AMusicPlatform)) {
                return true;
            }
            ru.sortix.parkourbeat.player.music.platform.MusicPackDispatcher dispatcher =
                ((ru.sortix.parkourbeat.player.music.platform.AMusicPlatform) platform).getDispatcher();
            if (dispatcher == null) return true;

            if (dispatcher.isPending(this.player.getUniqueId())) return false;
            String confirmed = dispatcher.getConfirmedTrackId(this.player.getUniqueId());
            return confirmed == null || confirmed.equals(track.getId());
        } catch (Throwable t) {
            return true;
        }
    }

    /** Отсчёт ровно как в дуэлях: 3, 2, 1 и поехали. */
    private void runCountdown(int left) {
        if (this.state == State.FINISHED) return;

        if (left <= 0) {
            this.player.showTitle(Title.title(
                PbText.of("&a&lПОЕХАЛИ"), Component.empty(),
                Title.Times.of(Duration.ZERO, Duration.ofMillis(400), Duration.ofMillis(200))));
            this.player.playSound(this.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            this.beginRun();
            return;
        }

        this.player.showTitle(Title.title(
            PbText.of("&e&l" + left), Component.empty(),
            Title.Times.of(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
        this.player.playSound(this.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);

        Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.runCountdown(left - 1), 20L);
    }

    private void beginRun() {
        this.state = State.RUNNING;
        this.startMillis = System.currentTimeMillis();

        // Запись реплея ведёт общий менеджер. Кадр - это позиция и поворот камеры,
        // а камерой здесь работает носитель, поэтому запись получается ровно тем же
        // способом, что и в обычном забеге, без единой отдельной ветки.
        if (this.ranked) {
            try {
                ReplayManager replays = this.plugin.get(ReplayManager.class);
                if (replays != null && replays.isRecordingEnabled(this.player.getUniqueId())) {
                    replays.startRecording(this.player);
                }
            } catch (Throwable ignored) {
            }
        }

        try {
            MusicTracksManager music = this.plugin.get(MusicTracksManager.class);
            if (music != null) music.getPlatform().startPlayingTrackFull(this.player);
        } catch (Throwable e) {
            this.plugin.getLogger().warning("Копатель: не удалось запустить трек: " + e.getMessage());
        }
    }

    /**
     * ИНВЕНТАРЬ ВЫДАЁТСЯ ЕЩЁ РАЗ, ЧУТЬ ПОЗЖЕ.
     * <p>
     * На сервере с лобби-плагинами предметы возвращаются игроку не сразу, а по своим
     * событиям: смена мира, смена режима игры, телепорт. Все они происходят В ТОТ ЖЕ
     * тик или через несколько после нашей выдачи, и хотбар сервера ложится поверх кирки.
     * <p>
     * Спорить с ними в лоб нечем - мы не знаем ни их порядка, ни их событий. Поэтому
     * выдача просто повторяется несколько раз в первые пару секунд: к моменту старта
     * отсчёта в руке гарантированно кирка, чья бы очередь ни была последней.
     */
    private void reapplyInventoryLater() {
        for (int delay : new int[]{5, 15, 30}) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (this.state == State.FINISHED) return;
                if (!this.player.isOnline()) return;
                this.applyInventory();
            }, delay);
        }
    }

    /** Девятый слот хотбара - предмет выхода из забега. Индексация с нуля. */
    private static final int QUIT_SLOT = 8;

    /** Разложить инвентарь забега. Вынесено отдельно, потому что повторяется. */
    private void applyInventory() {
        if (this.ranked) {
            // ЧИСТИМ ВСЁ, включая броню и левую руку: в обычную игру человек приходит
            // из лобби со своим хотбаром, и предметы лобби перекрывали кирку. Одного
            // clear() инвентаря для этого мало - предметы брони он не трогает.
            this.player.getInventory().clear();
            this.player.getInventory().setArmorContents(null);
            this.player.getInventory().setItemInOffHand(null);
            this.player.getInventory().setItem(0, DiggerPickaxe.buildItem(this.currentPickaxe()));

            // ВЫХОД ЛЕЖИТ В ДЕВЯТОМ СЛОТЕ, а не рядом с киркой: между ними вся ширина
            // хотбара, и попасть по нему случайно посреди заезда не выйдет.
            this.player.getInventory().setItem(QUIT_SLOT, DiggerExitItem.buildQuit());

            // Слот руки возвращаем на кирку ТОЛЬКО если игрок не держит выход: иначе
            // повторная выдача инвентаря выдёргивала бы предмет прямо из-под курсора.
            if (!DiggerExitItem.isQuit(this.player.getInventory().getItemInMainHand())) {
                this.player.getInventory().setHeldItemSlot(0);
            }
        } else {
            // Слоты 0 и 4 - предметы редактора: выход из теста и создание маркера.
            // Их трогать нельзя, иначе строитель не сможет ни выйти, ни отметить место.
            for (int slot = 1; slot < 9; slot++) {
                if (slot == 4) continue;
                this.player.getInventory().setItem(slot, null);
            }
            this.player.getInventory().setItem(1, DiggerPickaxe.buildItem(this.currentPickaxe()));
            this.player.getInventory().setHeldItemSlot(1);
        }
        this.player.updateInventory();
    }

    /** Кирка, которая должна быть в руке прямо сейчас: с учётом реплик светового шоу. */
    @NonNull
    private Material currentPickaxe() {
        return this.currentPickaxe != null ? this.currentPickaxe : this.settings.getPickaxe();
    }

    private void preparePlayer() {
        this.player.setGameMode(GameMode.CREATIVE);
        this.player.setAllowFlight(false);
        this.player.setFlying(false);

        // В ТЕСТЕ ИЗ РЕДАКТОРА ИНВЕНТАРЬ НЕ ЧИСТИТСЯ ЦЕЛИКОМ. В нулевом слоте лежит
        // алмаз выхода из теста, и раньше забег стирал его вместе со всем остальным -
        // выйти становилось нечем, оставалось только покинуть уровень, а это и роняло
        // мир вместе с забегом.
        this.applyInventory();
        this.currentPickaxe = this.settings.getPickaxe();

        // НЕВИДИМОСТЬ НА ВЕСЬ ЗАЕЗД.
        //
        // Игрок едет в кресле и ничего собой не загораживает - зато его собственная
        // модель и броня видны в кадре при любом развороте камеры и на плотной карте
        // мешают читать ноты. Эффект бесконечный по длительности и снимается на финише.
        if (DiggerTuning.INVISIBLE_PLAYER) {
            this.player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE, 0, false, false, false));
        }

        this.player.setFoodLevel(20);
        this.player.setSaturation(20.0f);
        this.player.setFireTicks(0);

        // Табло прячет ScoreboardManager: он пересобирает его каждый тик, и вырывать
        // табло отсюда бессмысленно - через тик оно вернулось бы обратно.
    }

    // ==================== ТИК ====================

    public void tick() {
        if (this.state == State.FINISHED) return;

        // Забег обязан умереть вместе с уровнем. Раньше он продолжал тикать после
        // выхода строителя из теста: мир выгружался, а сущности всё ещё двигались -
        // отсюда и ClosedChannelException, и зависший на десять секунд сервер.
        if (!this.player.isOnline()
            || this.player.getWorld() != this.world
            || Bukkit.getWorld(this.world.getUID()) == null) {
            this.abort();
            return;
        }

        this.ticks++;

        if (this.state == State.COUNTDOWN) {
            // ПИНГ МЕРЯЕТСЯ УЖЕ НА ОТСЧЁТЕ.
            //
            // Судейство расширяет окна на дрожание пинга, а дрожание надо сперва
            // измерить. Пока замеры начинались вместе с забегом, к первым нотам оценка
            // была нулевой, и окна у них оказывались уже, чем у всех остальных нот
            // карты. «Три, два, один» - это как раз те шесть десятков тактов, за
            // которые оценка успевает стать настоящей, и тратятся они всё равно впустую.
            this.judge.tick();

            // СТОРОЖ. Если отсчёт по какой-то причине не довёл забег до старта, игра
            // висела бы вечно - а вместе с ней запрет на постройку и ломку в редакторе.
            if (System.currentTimeMillis() - this.createdMillis > 15_000L) {
                this.abort();
                return;
            }
            // Тоннель обязан быть пустым уже на отсчёте: иначе за «три, два, один»
            // игрок успевает прочитать первые такты карты.
            this.updateSpawns(0.0D);
            return;
        }

        long millis = this.trackMillis();
        this.driveCarrier(millis);

        // Световое шоу идёт по времени ТРЕКА - тому же, по которому расставлены его
        // таймкоды. Считать его от старта забега нельзя: между ними ждали ресурспак.
        this.lightShow.tick(millis);

        // Пинг замеряется по часам, а не по попаданиям.
        this.judge.tick();

        // Чудоэффекты и лампы идут по той же шкале времени, что и всё шоу.
        this.tickWonders(millis, true);

        // Появление и пропуск нот считаются от РЕАЛЬНОГО положения камеры, уже после
        // того, как она сдвинулась в этом такте.
        double travelled = this.travelled();
        this.updateSpawns(travelled);
        this.expireNotes(travelled);
        this.applyPickaxeCue(millis);
        this.recordReplayFrame();
        this.updateHud(millis);

        // ЗАБЕГ ДОИГРЫВАЕТ ДО КОНЦА МУЗЫКИ, А НЕ ДО ПОСЛЕДНЕЙ НОТЫ.
        //
        // В тихом аутро анализатор не находит ударов - там нечего находить, - поэтому
        // последняя нота карты стоит на несколько секунд раньше конца песни. Забег
        // обрывался ровно на ней, и трек оставался недоигранным: со стороны это ровно
        // то же, что и «музыка кончилась раньше».
        if (millis >= this.endMillis()) {
            this.finish();
        }
    }

    /**
     * Прокрутить чудоэффекты и ламповые стены.
     * <p>
     * Оба создаются лениво: на карте, где их нет, не надо ни хранилища, ни объекта.
     * Ошибки здесь глушатся - упавший декоративный эффект не имеет права уронить забег.
     */
    private void tickWonders(long millis, boolean running) {
        try {
            if (this.wonderRunner == null) {
                this.plugin.get(ru.sortix.parkourbeat.utils.wonder.WonderStorage.class)
                    .ensureLoaded(this.level);
                this.wonderRunner = new ru.sortix.parkourbeat.levels.wonder.WonderRunner(
                    this.player, this.level, this.level.getLightShow().getWonderEffects());
            }
            this.wonderRunner.tick(millis, running);

            if (this.lampRunner == null) {
                this.lampRunner = new ru.sortix.parkourbeat.levels.lamps.LampRunner(
                    this.world, this.level.getLightShow().getLampWalls());
            }
            this.lampRunner.tick(millis, running);
        } catch (Throwable t) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Копатель: чудоэффекты не проиграны", t);
        }
    }

    /** Погасить всё, что зажгли чудоэффекты и лампы. */
    private void shutdownWonders() {
        try {
            if (this.wonderRunner != null) this.wonderRunner.shutdown();
            if (this.lampRunner != null) this.lampRunner.shutdown();
        } catch (Throwable ignored) {
        }
        this.wonderRunner = null;
        this.lampRunner = null;
    }

    /** Кадр реплея. Пишется только в зачётных забегах и только если игрок этого хочет. */
    private void recordReplayFrame() {
        if (!this.ranked) return;
        try {
            ReplayManager replays = this.plugin.get(ReplayManager.class);
            if (replays != null && replays.isRecording(this.player.getUniqueId())) {
                replays.recordFrame(this.player);
            }
        } catch (Throwable ignored) {
        }
    }

    private void driveCarrier(long millis) {
        Entity carrier = this.carrier;
        if (carrier == null || !carrier.isValid()) return;

        Location expected = this.expectedLocation(millis);

        // Движение НЕ пропускается никогда. Пропуск такта означал бы, что камера
        // отстала от музыки, а это ощущается как рывок и сбивает попадания.
        // Чанки вместо этого держатся загруженными заранее.
        this.keepChunksLoaded(expected);

        DiggerCarrier.move(carrier, expected);

        // ПОЗИЦИЯ КАМЕРЫ УХОДИТ КАЖДЫЙ ТАКТ, А НЕ РАЗ В ТРИ.
        //
        // Трекер 1.16.5 двигает стойку брони у клиента раз в 150 мс. При пинге с
        // дрожанием любой опоздавший пакет - это видимая остановка и рывок камеры.
        // Такт за тактом дрожание размазывается на 50 мс и почти не видно.
        Location sent = carrier.getLocation();
        DiggerEntityHider.pushPosition(this.player, carrier.getEntityId(),
            sent.getX(), sent.getY(), sent.getZ(), sent.getYaw(), sent.getPitch());

        this.hud.move(expected);
        this.lastMoveMillis = System.currentTimeMillis();
    }

    /**
     * ОКНО ЗАГРУЖЕННЫХ ЧАНКОВ ВПЕРЕДИ.
     * <p>
     * Мир «Копателя» пустой, но куски всё равно читаются с диска, и делать это в тот
     * момент, когда камера уже туда влетела, - значит остановить сервер на пару секунд.
     * Поэтому на восемь чанков вперёд ставятся билеты: они держат куски загруженными,
     * пока забег идёт, а позади снимаются.
     */
    private void keepChunksLoaded(@NonNull Location around) {
        if (this.ticks % 20 != 0) return;

        int chunkX = around.getBlockX() >> 4;
        int chunkZ = around.getBlockZ() >> 4;
        int stepX = this.axisX ? this.sign : 0;
        int stepZ = this.axisX ? 0 : this.sign;

        for (int i = -1; i <= CHUNKS_AHEAD; i++) {
            this.holdChunk(chunkX + stepX * i, chunkZ + stepZ * i);
        }

        // Позади билеты снимаем: иначе к концу трека в памяти висит весь тоннель.
        this.releaseChunk(chunkX - stepX * 4, chunkZ - stepZ * 4);
    }

    private void holdChunk(int chunkX, int chunkZ) {
        try {
            if (this.world.isChunkLoaded(chunkX, chunkZ)) {
                this.world.addPluginChunkTicket(chunkX, chunkZ, this.plugin);
                return;
            }
            this.world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> {
                if (chunk == null) return;
                try {
                    this.world.addPluginChunkTicket(chunkX, chunkZ, this.plugin);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
            // На сборках без асинхронной подгрузки просто полагаемся на трекер игрока.
        }
    }

    private void releaseChunk(int chunkX, int chunkZ) {
        try {
            this.world.removePluginChunkTicket(chunkX, chunkZ, this.plugin);
        } catch (Throwable ignored) {
        }
    }

    /** Снять все билеты забега: мир должен уметь выгрузиться после финиша. */
    private void releaseAllChunks() {
        try {
            this.world.removePluginChunkTickets(this.plugin);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Кирка на отрезке трека.
     * <p>
     * Вне отрезков в руке лежит кирка уровня, внутри - заданная в световом шоу.
     * Предмет пересобирается ТОЛЬКО при смене материала: дёргать инвентарь каждый
     * тик нельзя, у клиента от этого дрожит хотбар.
     */
    private void applyPickaxeCue(long millis) {
        Material material = this.settings.getPickaxe();
        try {
            PickaxeCue cue = this.level.getLightShow().getPickaxeCueAt(millis);
            if (cue != null) material = cue.getPickaxe();
        } catch (Throwable ignored) {
        }

        if (material == this.currentPickaxe) return;
        this.currentPickaxe = material;

        // В тесте кирка лежит в первом слоте: нулевой занят алмазом выхода из теста.
        int slot = this.ranked ? 0 : 1;
        this.player.getInventory().setItem(slot, DiggerPickaxe.buildItem(material));

        // РУКУ НЕ ПЕРЕКЛЮЧАЕМ, ЕСЛИ В НЕЙ ВЫХОД.
        //
        // Реплика кирки срабатывает на своём таймкоде, то есть в любой момент трека.
        // Игрок, взявший в руку выход, получил бы её обратно в руку через долю секунды -
        // и его клик ушёл бы в пустоту вместо выхода.
        if (DiggerExitItem.isQuit(this.player.getInventory().getItemInMainHand())) return;
        this.player.getInventory().setHeldItemSlot(slot);
    }

    /**
     * Ноты, чьё окно уже прошло, а по ним так и не ударили.
     * <p>
     * Список отсортирован по расстоянию, то есть и по времени, поэтому достаточно
     * двигать один указатель вперёд.
     */
    private void expireNotes(double travelled) {
        while (this.missPointer < this.notes.size()) {
            DiggerNote note = this.notes.get(this.missPointer);
            if (this.judgedKeys.contains(note.key())) {
                this.missPointer++;
                continue;
            }
            // Окно промаха - тоже в блоках из миллисекунд, и тоже по местной скорости.
            double deadline = this.distanceOf(note)
                - DiggerTuning.HIT_LEAD_BLOCKS
                + this.judge.missWindowBlocks(
                this.profile.speedAtDistance(this.distanceOf(note)),
                note.getOre().isRare());
            if (travelled <= deadline) break;

            this.judgedKeys.add(note.key());
            this.missPointer++;

            if (!this.revealedKeys.contains(note.key())) {
                // Руду не показывали - значит и пропустить её было нельзя.
                this.debug("нота вне поля зрения пропущена без штрафа: d="
                    + String.format("%.1f", this.distanceOf(note)));
                continue;
            }

            this.score.registerMiss();
            this.countMiss();
            DiggerEffects.miss(this.player, note.center(this.world));
            this.show.onBreakCombo(note.center(this.world));
            this.flashMiss();
            this.showHit(DiggerHit.MISS);
        }
    }

    // ==================== УДАРЫ ====================

    /**
     * Игрок сломал блок.
     *
     * @param arrivalMillis момент, когда пакет дошёл до сервера
     * @return false, если блок ломать нельзя и событие надо отменить
     */
    public boolean onBreak(@NonNull Block block, long arrivalMillis) {
        if (this.state != State.RUNNING) return false;

        DiggerNote note = this.settings.getNoteAt(block.getX(), block.getY(), block.getZ());
        if (note == null) {
            this.debug("сломан НЕ нотный блок " + block.getType()
                + " на " + block.getX() + " " + block.getY() + " " + block.getZ());
            // Декор, стена тоннеля, что угодно. Комбо рушится: тыкать наугад не выгодно.
            this.registerWasted();
            return DiggerTuning.ALLOW_BREAKING_DECOR;
        }

        this.lastBreakMillis = System.currentTimeMillis();

        // Поштучный режим: блок остаётся в мире, а пропадает только у того, кто попал.
        // Обычный режим: ломается по-настоящему и восстановится на старте следующего забега.
        if (DiggerTuning.PER_PLAYER_ORES) this.hideNote(note, true);

        boolean first = this.judgedKeys.add(note.key());
        boolean cancel = DiggerTuning.PER_PLAYER_ORES;
        // По одной ноте бьют дважды сплошь и рядом: клик и разрушение приходят разными
        // событиями. Второй раз не судим и не наказываем.
        if (!first) {
            this.debug("повторный удар по уже разобранной ноте: d="
                + String.format("%.1f", this.distanceOf(note))
                + " проехал=" + String.format("%.1f", this.travelledNow()));
            return !cancel;
        }

        // ТОЧКА УДАРА - НЕ ГОЛОВА ИГРОКА, А НЕСКОЛЬКО БЛОКОВ ПЕРЕД НЕЙ.
        //
        // Чтобы сломать руду, до неё надо дотянуться, а тянется игрок вперёд. Пока
        // идеальным считался удар по руде, поравнявшейся с камерой, засчитывались только
        // те, которые уже почти пронесло мимо, - всё остальное шло «слишком рано».
        // СКОРОСТЬ БЕРЁТСЯ МЕСТНАЯ, А НЕ БАЗОВАЯ.
        //
        // Судейство измеряет промах в БЛОКАХ, а окна попадания заданы в миллисекундах -
        // значит где-то надо делить на скорость. Пока она была одна на весь трек, годилась
        // базовая. С зонами скорости в быстром куске тот же отрыв в полблока означает уже
        // на пятую часть меньше времени, и окно, посчитанное по базовой, оказывается
        // растянутым: удары начинают засчитываться как опоздавшие, хотя игрок бьёт ровно.
        // Именно это и читается как «ритм отстаёт в некоторых местах».
        double localSpeed = this.profile.speedAtDistance(this.distanceOf(note));

        double compensation = localSpeed * this.judge.compensationMillis() / 1000.0D;
        double reach = this.travelledNow() + DiggerTuning.HIT_LEAD_BLOCKS - compensation;
        double blocks = reach - this.distanceOf(note);
        long delta = Math.round(blocks / localSpeed * 1000.0D);
        DiggerHit hit = this.judge.judge(blocks, localSpeed, note.getOre().isRare());

        this.debug("удар: проехал=" + String.format("%.1f", this.travelledNow())
            + " поправка=" + this.judge.getPersonalOffsetMillis() + " мс"
            + " нота=" + String.format("%.1f", this.distanceOf(note))
            + " отклонение=" + String.format("%.2f", blocks) + " бл"
            + " (" + delta + " мс) -> " + hit.name());

        Location center = note.center(this.world);
        if (hit.isHit()) {
            this.missStreak = 0;
            this.judge.feedCalibration(delta);
            int prevMultiplier = this.score.multiplier(); // <--- запоминаем старый множитель

            this.score.apply(hit, note.getOre());
            int multiplier = this.score.multiplier();
            DiggerEffects.hit(this.player, center, note.getOre(), hit, multiplier,
                this.settings.getHitSoundKey(), this.settings.getHitSoundPitch());
            this.show.onHit(center, note.getOre(), hit, multiplier);
            if (note.getOre().isDrop()) this.show.onDrop(center, multiplier);

            // Выдвигаем строчку с числом комбо
            this.hud.punchCombo();

            // Строчку множителя выдвигаем ТОЛЬКО когда он сменился
            if (multiplier != prevMultiplier) {
                this.hud.punchMultiplier();
            }
        } else {
            this.score.registerMiss();
            DiggerEffects.miss(this.player, center);
            this.show.onBreakCombo(center);
            this.countMiss();
            this.flashMiss();
        }
        this.showHit(hit);
        return !cancel;
    }

    /**
     * Вернуть в мир руды, снесённые прошлым забегом.
     * <p>
     * Нужно только в обычном режиме ломки: в поштучном блоки никуда не девались.
     * Ставится без обновления физики - соседние блоки от этого ничего не должны
     * почувствовать, а пара тысяч обновлений подряд положила бы тик.
     */
    private void restoreNoteBlocks() {
        if (DiggerTuning.PER_PLAYER_ORES) return;
        for (DiggerNote note : this.notes) {
            Block block = this.world.getBlockAt(note.getX(), note.getY(), note.getZ());
            if (block.getType() == note.getOre().getMaterial()) continue;
            block.setType(note.getOre().getMaterial(), false);
        }
    }

    /**
     * Спрятать руду лично от этого игрока.
     *
     * @param resend слать ли пакет ещё раз следующим тиком. Нужно только после
     *               разрушения: отменённая ломка заставляет сервер выслать клиенту
     *               настоящий блок обратно, и без второго пакета руда мигнула бы.
     *               При обычном прятании впереди игрока этого не происходит.
     */
    private void hideNote(@NonNull DiggerNote note, boolean resend) {
        this.ghosts.put(note.key(), note);

        Location location = note.toLocation(this.world);
        BlockData air = Material.AIR.createBlockData();
        this.player.sendBlockChange(location, air);

        if (!resend) return;
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (this.player.isOnline()) this.player.sendBlockChange(location, air);
        });
    }

    /** Показать руду: она вошла в окно видимости. */
    private void revealNote(@NonNull DiggerNote note) {
        this.revealedKeys.add(note.key());
        // Ключ снимается ПЕРВЫМ: пока позиция числится спрятанной, перехватчик пакетов
        // не выпустит к игроку даже наше собственное появление.
        this.ghosts.remove(note.key());
        this.player.sendBlockChange(note.toLocation(this.world),
            note.getOre().getMaterial().createBlockData());

        // ВСПЫШКА НА ПОЯВЛЕНИИ.
        //
        // Руда возникает из пустоты мгновенно, одним пакетом, и без метки этот момент
        // читается уже постфактум. Небольшая вспышка на её месте даёт кадр, за который
        // глаз цепляется сразу, и ноты начинают ПРИЛЕТАТЬ, а не просто обнаруживаться.
        //
        if (DiggerTuning.REVEAL_FLASH) {
            this.player.spawnParticle(Particle.FLASH, note.center(this.world), 1);
        }
    }

    /**
     * ПОЯВЛЕНИЕ РУД НА ХОДУ.
     * <p>
     * Тоннель для игрока пустой: руды возникают перед ним ровно за столько секунд,
     * сколько задано жёсткостью карты, и это главное отличие от «стоит всё сразу».
     * Карта, видимая целиком, читается наперёд и перестаёт быть ритм-игрой - в Beat
     * Saber ноты по этой же причине появляются на фиксированном расстоянии.
     * <p>
     * Оба указателя идут только вперёд: список отсортирован по расстоянию, а игрок
     * назад не едет.
     */
    private void updateSpawns(double travelled) {
        double revealEdge = travelled + this.settings.revealDistance();
        while (this.revealPointer < this.notes.size()) {
            DiggerNote note = this.notes.get(this.revealPointer);
            if (this.distanceOf(note) > revealEdge) break;
            // Уже разобранные не воскресают: их прячет судейство, а не окно видимости.
            if (!this.judgedKeys.contains(note.key())) this.revealNote(note);
            this.revealPointer++;
        }
    }

    /**
     * СПРЯТАТЬ ВСЮ КАРТУ ЦЕЛИКОМ, ОДИН РАЗ, ДО СТАРТА.
     * <p>
     * Раньше руды прятались скользящим окном на {@code HIDE_AHEAD = 96} блоков впереди
     * игрока. Девяносто шесть блоков - это ровно шесть чанков, и всё, что дальше,
     * оставалось обычными блоками в мире: на сервере с большой прогрузкой игрок видел
     * настоящий рисунок карты далеко впереди - то есть читал её наперёд, ради чего
     * окно видимости и затевалось.
     * <p>
     * Прятать окном вообще незачем. Скрытие руды - это один пакет изменения блока;
     * полторы тысячи пакетов один раз на старте, пока идёт отсчёт, не заметит ни
     * клиент, ни сеть. А вот появление руд по-прежнему идёт окном - в этом и был
     * смысл, и он никуда не делся.
     */
    private void hideEverything() {
        for (DiggerNote note : this.notes) {
            if (this.ghosts.containsKey(note.key())) continue;
            this.hideNote(note, false);
        }
    }

    /**
     * Вернуть игроку настоящий вид тоннеля.
     * <p>
     * В мире ничего не менялось, поэтому это именно перерисовка у клиента: он всё это
     * время считал, что руд нет, и без этого шага увидел бы дыры до перезахода в мир.
     */
    private void restoreHidden() {
        if (this.ghosts.isEmpty()) return;

        // Копия снимается до очистки: пока ключ на месте, перехватчик пакетов не
        // выпустит к игроку даже наше собственное восстановление.
        List<DiggerNote> restore = new ArrayList<>(this.ghosts.values());
        this.ghosts.clear();

        if (!this.player.isOnline()) return;
        for (DiggerNote note : restore) {
            Location location = note.toLocation(this.world);
            this.player.sendBlockChange(location, this.world.getBlockAt(location).getBlockData());
        }
    }

    /** Спрятана ли руда в этой точке лично от этого игрока. */
    public boolean isHidden(int x, int y, int z) {
        return this.ghosts.containsKey(DiggerNote.key(x, y, z));
    }

    public boolean hasHidden() {
        return !this.ghosts.isEmpty();
    }

    /**
     * Заново спрятать руды после того, как сервер переслал игроку целую секцию или чанк.
     * <p>
     * Рассылаются не все призраки подряд, а только те, что рядом: сломанные полминуты
     * назад остались далеко позади и в кадр всё равно не попадут, а пакеты на них -
     * это чистый трафик на пустом месте.
     */
    public void reassertHidden() {
        if (this.ghosts.isEmpty()) return;

        Location eye = this.player.getLocation();
        BlockData air = Material.AIR.createBlockData();
        for (DiggerNote note : this.ghosts.values()) {
            if (Math.abs(note.getX() - eye.getX()) > REASSERT_RANGE) continue;
            if (Math.abs(note.getZ() - eye.getZ()) > REASSERT_RANGE) continue;
            this.player.sendBlockChange(note.toLocation(this.world), air);
        }
    }

    /**
     * Взмах рукой. Если он ни во что не попал - это холостой удар.
     * <p>
     * Без этого выгодная стратегия - зажать ЛКМ и водить прицелом по тоннелю: руды
     * сносились бы сами, а промахнуться было бы нельзя. Поэтому пустой взмах рушит
     * комбо ровно как промах по ноте.
     */
    public void onSwing(long millis) {
        if (this.state != State.RUNNING) return;
        if (!DiggerTuning.PUNISH_AIR_SWINGS) return;
        if (millis - this.lastBreakMillis <= DiggerTuning.AIR_SWING_GRACE_MS) return;
        if (millis - this.lastWastedPunishMillis < DiggerTuning.AIR_SWING_COOLDOWN_MS) return;

        this.lastWastedPunishMillis = millis;
        this.registerWasted();
    }

    /**
     * Промах учтён. Здесь же решается, не пора ли заканчивать забег.
     * <p>
     * Считаются именно промахи ПОДРЯД: одна ошибка на сложном месте - это нормально,
     * а шесть штук без единого попадания означают, что игрок карту не тянет и дослушивать
     * трек ему незачем.
     */
    private void countMiss() {
        this.missStreak++;
        if (DiggerTuning.FAIL_AFTER_MISSES <= 0) return;
        if (this.missStreak < DiggerTuning.FAIL_AFTER_MISSES) return;
        this.fail();
    }

    private void fail() {
        if (this.state == State.FINISHED) return;
        this.failed = true;
        this.player.showTitle(Title.title(
            PbText.of(ru.sortix.parkourbeat.utils.text.Theme.V_RED + "&lПРОВАЛ"),
            PbText.of("&7" + DiggerTuning.FAIL_AFTER_MISSES + " промахов подряд"),
            Title.Times.of(Duration.ofMillis(100), Duration.ofMillis(2000), Duration.ofMillis(400))));
        this.player.playSound(this.player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.6f);
        this.finish();
    }

    /**
     * ВЫХОД ПО ЖЕЛАНИЮ ИГРОКА.
     * <p>
     * Не провал и не финиш. Провал - это приговор карте и игроку, он пишет в титры
     * «ПРОВАЛ» и считается неудачной попыткой; финиш означает, что трек доигран. Здесь
     * не случилось ни того, ни другого: человек просто решил, что дальше не поедет.
     * <p>
     * Поэтому в статистику забег НЕ уходит. Иначе выход превратился бы в инструмент:
     * начал неудачно - вышел, и попытка не засчиталась, а точность в профиле осталась
     * красивой. Всё остальное - возврат инвентаря, неба, экран выбора - работает ровно
     * так же, как после честного финиша.
     */
    public void quit() {
        if (this.state == State.FINISHED) return;
        this.quitByPlayer = true;
        this.player.playSound(this.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
        this.finish();
    }

    private void registerWasted() {
        this.debug("холостой удар");
        this.score.registerWastedSwing();
        DiggerEffects.wasted(this.player);
        this.show.onBreakCombo(this.player.getLocation());
        this.showHit(DiggerHit.MISS);
    }

    /**
     * КРАСНАЯ ВСПЫШКА НА ПРОМАХЕ.
     * <p>
     * Тот же эффект, что в 3D-уровнях лежит в зонах прыжка под именем «Покраснение».
     * Промах в «Копателе» до сих пор был слышен, но почти не виден: звук и частицы
     * уходят вниз, к руде, а глаза игрока в этот момент смотрят вперёд по тоннелю.
     * Вспышка по краям экрана попадает в поле зрения независимо от того, куда он
     * смотрит, - её нельзя пропустить, не отводя взгляда от трассы.
     * <p>
     * ХОЛОСТЫЕ УДАРЫ СЮДА НЕ ВХОДЯТ намеренно. Их можно выдать десяток в секунду, просто
     * зажав кнопку, и экран заливало бы красным без перерыва. Промах по ноте случается
     * не чаще, чем идут ноты.
     */
    private void flashMiss() {
        try {
            ru.sortix.parkourbeat.world.RedVignetteSender.flash(this.plugin, this.player);
        } catch (Throwable ignored) {
        }
    }

    private void showHit(@NonNull DiggerHit hit) {
        this.lastHitDisplay = hit.getDisplay();
        this.lastHitAtMillis = System.currentTimeMillis();
    }

    // ==================== HUD ====================

    private void updateHud(long millis) {
        // ПРИБАВКА ЖИВЁТ 120 МИЛЛИСЕКУНД, а не секунду.
        //
        // Секунда - это дольше, чем промежуток между нотами на плотной карте: подпись
        // от предыдущего попадания ещё висит, когда прилетает следующее, и на экране
        // получается одно застывшее число вместо отклика на каждый удар. Ста двадцати
        // миллисекунд глазу хватает, чтобы прочитать прибавку, и мало, чтобы она
        // слиплась со следующей - оценки начинают мелькать в такт игре.
        String hit = System.currentTimeMillis() - this.lastHitAtMillis
            < DiggerTuning.HIT_DISPLAY_MILLIS ? this.lastHitDisplay : "";

        // ОДНА ДЛИТЕЛЬНОСТЬ НА ВСЁ: и на таймер, и на полосу, и на момент финиша.
        //
        // Таймер показывал справа время ПОСЛЕДНЕЙ НОТЫ, а забег идёт дальше - до конца
        // трека или ещё 2.5 с аутро. Отсюда «2:31 / 2:29»: время честно уходило за
        // «лимит», который лимитом никогда не был. Левое число к тому же зажимается:
        // между концом и финишем проходит до такта, и перелёт на пару десятков
        // миллисекунд смотрелся бы тем же багом.
        long total = this.endMillis();
        this.hud.update(this.score, hit, Math.min(millis, total), total);

        if (DiggerTuning.SHOW_ACTIONBAR) {
            this.player.sendActionBar(PbText.of(Theme.V_WHITE + "&l" + this.score.getScore()
                + "   " + Theme.V_GRAY + "x" + this.score.multiplier()
                + "  " + Theme.V_DARK_GRAY + "|  " + Theme.V_AQUA + this.score.getCombo()
                + "   " + hit));
        } else if (!hit.isEmpty() || this.actionBarBusy) {
            // Экшнбар выключен, но подпись попадания всё равно должна жить свои 120 мс
            // и ГАСНУТЬ. Пустая строка следом - это и есть гашение: без неё клиент
            // держал бы последнюю надпись несколько секунд.
            this.player.sendActionBar(PbText.of(hit));
            this.actionBarBusy = !hit.isEmpty();
        }

        BossBar bar = this.bossBar;
        if (bar != null) {
            // Цвет боссбара тоже часть светового шоу: строитель ставит его на таймкод,
            // и в «Копателе» он теперь доезжает до игрока как на обычном уровне.
            ru.sortix.parkourbeat.levels.settings.LevelBossBarColor showColor = this.bossBarColor;
            if (showColor != null) {
                try {
                    bar.setColor(BarColor.valueOf(showColor.name()));
                } catch (IllegalArgumentException ignored) {
                    // Имя цвета не совпало с ванильным - оставляем прежний.
                }
            }

            double barTotal = Math.max(1L, total);
            bar.setProgress(Math.max(0.0D, Math.min(1.0D, millis / barTotal)));

            // Таймкод в том же виде, в каком его спрашивает световое шоу: минуты,
            // секунды и миллисекунды, чтобы значение можно было переписать один в один.
            long safe = Math.max(0L, millis);
            String timecode = String.format(java.util.Locale.ROOT, "%02d:%02d.%03d",
                safe / 60000L, (safe / 1000L) % 60L, safe % 1000L);

            // В РЕДАКТОРЕ - ТОЛЬКО ВРЕМЯ.
            //
            // Строитель гоняет тест не чтобы набрать очки, а чтобы сверить руду с
            // музыкой: ему нужен таймкод, который он тут же перенесёт в световое шоу.
            // Всё остальное - счётчик нот, точность, стрелки - в этот момент только
            // мешает читать единственное нужное число.
            if (this.ranked) {
                bar.setTitle(PbText.keepColors("§f" + timecode
                    + " §8| §7нот " + this.score.judgedNotes() + "/" + this.notes.size()
                    + " §8| §f" + String.format("%.1f", this.score.accuracy()) + "%"));
            } else {
                bar.setTitle(PbText.keepColors("§f" + timecode));
            }
        }
    }

    // ==================== ФИНИШ ====================

    public void finish() {
        if (this.state == State.FINISHED) return;
        this.state = State.FINISHED;

        try {
            MusicTracksManager music = this.plugin.get(MusicTracksManager.class);
            if (music != null) music.getPlatform().stopPlayingTrackFull(this.player);
        } catch (Throwable ignored) {
        }

        this.restoreHidden();
        this.player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        this.releaseSky();
        this.shutdownWonders();
        this.restoreSpawnBarrier();
        this.releaseAllChunks();
        this.hud.remove();

        if (this.carrier != null) DiggerEntityHider.undrive(this.carrier.getEntityId());
        DiggerCarrier.remove(this.carrier);
        this.carrier = null;

        if (this.bossBar != null) {
            this.bossBar.removeAll();
            this.bossBar = null;
        }

        this.restorePlayer();

        if (this.quitByPlayer) {
            // Реплей уходит туда же, куда и результат: обрывок до середины трека в
            // базе не нужен никому.
            try {
                ReplayManager replays = this.plugin.get(ReplayManager.class);
                if (replays != null) replays.cancelRecording(this.player.getUniqueId());
            } catch (Throwable ignored) {
            }
            this.player.sendMessage(PbText.of(Theme.V_GRAY + "Забег прерван. Результат не сохранён."));
        } else {
            this.sendResults();
            this.submitStats();
        }
        this.offerNextStep();
    }

    /**
     * ЧТО ДЕЛАТЬ ПОСЛЕ ЗАЕЗДА.
     * <p>
     * В обычной игре открывается экран выбора: сыграть ещё раз, другие уровни, спавн.
     * Без него человек оставался стоять в пустом тоннеле и выбирался оттуда командой -
     * ровно та точка, где люди уходят из режима, а не начинают второй заход.
     * <p>
     * В ТЕСТЕ ИЗ РЕДАКТОРА НИКАКОГО ЭКРАНА НЕТ. Строителю предлагать «другие уровни»
     * посреди работы над своим - бессмысленно и раздражает; он и так вернётся в
     * редактор, ему нужно просто продолжить строить.
     * <p>
     * Пауза перед открытием нужна, чтобы экран не перекрыл титры с результатом: сначала
     * человек читает, сколько набрал, и только потом выбирает, что дальше.
     */
    private void offerNextStep() {
        if (!this.ranked) return;
        if (!DiggerTuning.END_MENU) return;

        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!this.player.isOnline()) return;

            // За две секунды человек мог уже уйти сам - навязываться не надо.
            if (this.plugin.get(DiggerManager.class).isPlaying(this.player)) return;

            try {
                new DiggerEndMenu(this.plugin,
                    ru.sortix.parkourbeat.utils.lang.PlayerLang.of(this.player), this.level)
                    .open(this.player);
            } catch (Throwable t) {
                this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Копатель: экран после заезда не открылся", t);
            }
        }, DiggerTuning.END_MENU_DELAY_TICKS);
    }

    /**
     * Итоги забега.
     * <p>
     * Собираются из тех же строк языка, что и на обычном уровне: игрок не должен
     * гадать, почему в одном режиме итог выглядит так, а в другом иначе.
     */
    private void sendResults() {
        String lang = ru.sortix.parkourbeat.utils.lang.PlayerLang.of(this.player);
        double accuracy = this.score.accuracy();
        AccuracyGrade grade = this.score.grade();
        int score = (int) Math.min(Integer.MAX_VALUE, this.score.getScore());

        String scored = ru.sortix.parkourbeat.utils.lang.Lang.raw(lang, "game.subtitle.scored",
            "%score%", String.valueOf(score),
            "%accuracy%", String.format(java.util.Locale.ROOT, "%s%.2f%%",
                grade.getColorCode(), accuracy));

        this.player.showTitle(Title.title(
            this.failed
                ? ru.sortix.parkourbeat.utils.lang.LangOptions.level_play_title_death.getComponent(this.player)
                : ru.sortix.parkourbeat.utils.lang.Lang.text(lang, "game.title.completed"),
            PbText.of(scored),
            Title.Times.of(Duration.ofMillis(200), Duration.ofMillis(3000), Duration.ofMillis(500))));

        // ЗАГОЛОВОК ИТОГОВ ЗАВИСИТ ОТ ИСХОДА.
        //
        // Строка из языка начинается со слов «Уровень пройден» - на проваленном забеге
        // она прямо противоречит тому, что произошло. Поэтому у провала своя первая
        // строка, а из языковой берётся только хвост со статистикой.
        String stats = ru.sortix.parkourbeat.utils.lang.Lang.raw(lang, "game.summary.header",
            "%accuracy%", String.format(java.util.Locale.ROOT, "%.2f%%", accuracy),
            "%grade%", grade.getFormatted(),
            "%score%", String.valueOf(score),
            "%combo%", String.valueOf(this.score.getMaxCombo()),
            "%miss%", String.valueOf(this.score.getMissed()));

        if (this.failed) {
            // Заменяем ровно первую строку, всё остальное остаётся как было.
            int lineBreak = stats.indexOf('\n');
            stats = "&c&lВы проиграли" + (lineBreak < 0 ? "" : stats.substring(lineBreak));
        }

        StringBuilder message = new StringBuilder(stats);

        // Про «столько-то промахов подряд» здесь не пишем: причина уже видна по самой
        // строке промахов, а отдельная красная приписка на весь экран читается как
        // выговор и повторяет то, что и так сказано.
        if (!this.failed && this.score.isFullCombo()) {
            message.append(" &7[&b&lFC&7]");
        }

        this.player.sendMessage(PbText.of(message.toString()));
        this.player.sendMessage(PbText.of("&6+300 &f" + this.score.getPerfect()
            + "   &a+100 &f" + this.score.getGood()
            + "   &e+50 &f" + this.score.getOk()
            + "   &8промахов " + this.score.getWastedSwings()));

        this.player.playSound(this.player.getLocation(),
            this.score.isFullCombo() ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    /**
     * Записать забег в общую статистику.
     * <p>
     * {@link RunResult} уже умеет всё, что нужно ритм-режиму: 300/100/50, промахи,
     * комбо и точность. Поэтому «Копатель» ничего своего не заводит и попадает в те же
     * таблицы, что и остальные режимы.
     */
    private void submitStats() {
        if (!this.ranked) return;
        if (this.score.judgedNotes() == 0) return;

        try {
            StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
            if (statistics == null) return;

            ru.sortix.parkourbeat.levels.settings.GameSettings gameSettings =
                this.level.getLevelSettings().getGameSettings();

            double accuracy = this.score.accuracy();
            int finalScore = (int) Math.min(Integer.MAX_VALUE, this.score.getScore());

            RunResult run = RunResult.builder()
                .playerId(this.player.getUniqueId())
                .playerName(this.player.getName())
                .levelId(this.level.getUniqueId())
                .levelName(gameSettings.getDisplayNameLegacy(false))
                .difficulty(gameSettings.getDifficulty())
                .progressPercent(100.0D * this.score.judgedNotes() / Math.max(1, this.notes.size()))
                .completed(!this.failed)
                .accuracy(accuracy)
                .grade(this.score.grade())
                .score(finalScore)
                .rawScore(finalScore)
                .maxCombo(this.score.getMaxCombo())
                .count300(this.score.getPerfect())
                .count100(this.score.getGood())
                .count50(this.score.getOk())
                .missCount(this.score.getMissed())
                .timeMillis(Math.max(0L, this.trackMillis()))
                .timestamp(System.currentTimeMillis())
                .build();

            statistics.submitRun(run);
        } catch (Throwable e) {
            this.plugin.getLogger().warning("Копатель: не удалось записать забег: " + e.getMessage());
        }
    }

    private void restorePlayer() {
        this.player.setGameMode(this.previousGameMode);
        this.player.setAllowFlight(this.previousAllowFlight);
        this.player.getInventory().setContents(this.previousInventory);
        this.player.teleport(this.previousLocation);

        // Небо доводится ПОСЛЕ телепорта: только теперь известно, в каком мире игрок.
        this.resyncSky();
    }

    /**
     * ОТПУСТИТЬ НЕБО ИГРОКА.
     * <p>
     * Здесь стоял только {@code rollbackToBase()}, и это была ровно половина дела.
     * Откат - это ПЛАВНЫЙ переезд к опорному небу уровня, он размазан на несколько
     * десятков тактов и живёт за счёт того, что шоу продолжают тикать. А тикать оно
     * перестаёт в тот же миг: забег уже FINISHED, и цикл выходит на первой строке.
     * Откат так и замирал на полпути.
     * <p>
     * Хуже другое. Подмена неба держится не вызовом {@code setPlayerTime}, а слушателем
     * пакетов в {@link ru.sortix.parkourbeat.player.SkyTimeManager}: пока игрок числится
     * за шоу, КАЖДЫЙ уходящий ему пакет времени переписывается на замороженное значение.
     * Снимает эту запись только {@code shutdown()}, а его «Копатель» не вызывал НИКОГДА.
     * <p>
     * Отсюда и жалоба. Игрок уходит из «Копателя» на спавн, спавн шлёт ему свою ночь -
     * а слушатель по дороге меняет её на опорное небо покинутой карты. И так до конца
     * сессии: ни выход, ни вход на другой уровень запись не убирали. Обычные уровни
     * этим не болеют - {@code Game.shutdown()} у них есть.
     * <p>
     * {@code resetPlayerTime()} сам по себе тут бесполезен: он снимает подмену на
     * стороне сервера, но переписывание пакетов идёт ПОСЛЕ него.
     */
    private void releaseSky() {
        try {
            this.lightShow.rollbackToBase();
            this.lightShow.shutdown();
        } catch (Throwable ignored) {
        }
        this.player.resetPlayerTime();
        this.player.resetPlayerWeather();
    }

    /**
     * Досылка настоящего времени того мира, куда игрок приехал.
     * <p>
     * Небо отпускается ещё в мире «Копателя», поэтому последний пакет времени несёт
     * время ТОННЕЛЯ. Ванильная рассылка приходит раз в двадцать тактов, и до неё клиент
     * держит старое небо - на спавне это до секунды чужого вечера вместо ночи.
     */
    private void resyncSky() {
        this.pushWorldTime();

        // ПОВТОРЫ НУЖНЫ ИЗ-ЗА ЧУЖИХ ТЕЛЕПОРТОВ.
        //
        // Выход с уровня - это не один наш телепорт, а цепочка: мы возвращаем игрока на
        // прежнее место, следом менеджер активностей уводит его на спавн, а лобби-плагин
        // может двинуть ещё раз. Досылка после каждого шага дешевле, чем угадывать, чей
        // телепорт окажется последним.
        for (int delay : new int[]{1, 5, 20}) {
            Bukkit.getScheduler().runTaskLater(this.plugin, this::pushWorldTime, delay);
        }
    }

    private void pushWorldTime() {
        if (!this.player.isOnline()) return;
        try {
            this.plugin.get(ru.sortix.parkourbeat.player.SkyTimeManager.class).resync(this.player);
        } catch (Throwable ignored) {
        }
    }

    /** Аварийное завершение: выход с сервера, перезагрузка плагина, смерть мира. */
    public void abort() {
        if (this.state == State.FINISHED) return;
        this.state = State.FINISHED;

        try {
            MusicTracksManager music = this.plugin.get(MusicTracksManager.class);
            if (music != null) music.getPlatform().stopPlayingTrackFull(this.player);
        } catch (Throwable ignored) {
        }

        this.restoreHidden();
        this.player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        this.releaseSky();
        this.shutdownWonders();
        this.restoreSpawnBarrier();
        this.releaseAllChunks();
        this.hud.remove();

        if (this.carrier != null) DiggerEntityHider.undrive(this.carrier.getEntityId());
        DiggerCarrier.remove(this.carrier);
        this.carrier = null;
        if (this.bossBar != null) {
            this.bossBar.removeAll();
            this.bossBar = null;
        }

        // Забег брошен - реплей выбрасывается вместе с ним, иначе в базу уедет
        // обрывок до середины трека.
        try {
            ReplayManager replays = this.plugin.get(ReplayManager.class);
            if (replays != null) replays.cancelRecording(this.player.getUniqueId());
        } catch (Throwable ignored) {
        }

        if (this.player.isOnline()) this.restorePlayer();
    }

    @Nullable
    public Entity getCarrier() {
        return this.carrier;
    }
}
