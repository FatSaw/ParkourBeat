package ru.sortix.parkourbeat.twod;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import lombok.Getter;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.DirectionChecker;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.player.PingManager;
import ru.sortix.parkourbeat.player.music.MusicTracksManager;
import ru.sortix.parkourbeat.player.music.platform.MusicPlatform;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.time.Duration;

/**
 * ОДИН ЗАБЕГ НА 2D-УРОВНЕ.
 * <p>
 * Устройство сцены:
 * <ul>
 *     <li>кубик - это falling_block с repeating_command_block (в ресурспаке это и есть
 *     кубик из Geometry Dash), посаженный на невидимый арморстенд;</li>
 *     <li>игрок сидит на ВТОРОМ невидимом арморстенде, отведённом вбок и чуть вперёд:
 *     именно он даёт тот самый «идеально 2D» угол, а заодно лишает игрока управления
 *     персонажем - остаётся только пробел;</li>
 *     <li>в режиме полёта между арморстендом кубика и самим кубиком вставляется лодка.</li>
 * </ul>
 * Физику считаем сами и целиком: ванильному движку тут доверять нечего, иначе кубик
 * будет тормозить о стены, скользить и застревать в углах.
 */
public class TwoDGame {

    public enum Mode {
        CUBE,
        FLY
    }

    private static final Title.Times ATTEMPT_TIMES =
        Title.Times.of(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(300));

    private final @NonNull ParkourBeat plugin;
    private final @NonNull Player player;
    private final @NonNull Level level;
    private final @NonNull TwoDInput input;
    private final boolean editorTest;

    private final @NonNull World world;
    private final @NonNull Vector forward;
    private final @NonNull Vector side;
    private final @NonNull Vector cameraDirection;
    private final float cameraYaw;

    private final @NonNull Location cubeSpawn;

    private ArmorStand cubeSeat;
    private TwoDCubeEntity cube;

    /** Текущий угол кувырка кубика, радианы. */
    private double cubeAngle = 0.0D;
    /** Угол, к которому кубик докручивается сейчас. */
    private double cubeTargetAngle = 0.0D;
    private Entity boat;
    private ArmorStand cameraSeat;

    private final Vector position = new Vector();
    private double verticalSpeed = 0.0D;
    private boolean onGround = false;
    private long lastGroundAtMillis = 0L;

    @Getter
    private Mode mode = Mode.CUBE;
    @Getter
    private int attempt = 1;
    @Getter
    private boolean active = false;

    private int deadTicks = 0;
    private int ticks = 0;
    private double cameraY = 0.0D;
    private boolean cameraInitialized = false;

    private double cubeMountOffset = 0.0D;

    /** На каком тике замерять смещение посадки. 0 - замер ещё не запланирован. */
    private int offsetMeasureAt = 0;

    /**
     * Сколько тиков ждать перед замером. Двух хватает, третий взят с запасом:
     * сервер подтягивает пассажира к носителю в своём тике, и до этого позиция
     * кубика ещё старая.
     */
    private static final int OFFSET_MEASURE_DELAY_TICKS = 3;
    private double cameraMountOffset = 0.0D;

    /** Камера привязана к арморстенду режимом наблюдателя, а не посадкой на него. */
    private final boolean spectatorCamera =
        TwoDTuning.CAMERA_MODE != null && TwoDTuning.CAMERA_MODE.trim().equalsIgnoreCase("SPECTATOR");

    /** Высота глаз арморстенда камеры: в режиме наблюдателя картинка идёт именно оттуда. */
    private double cameraEyeOffset = 0.0D;
    private boolean offsetsDirty = true;

    private final java.util.Set<Integer> collectedCoins = new java.util.HashSet<>();
    private int coinsCollected = 0;

    /**
     * Прыжок заряжается ПРИЗЕМЛЕНИЕМ.
     * <p>
     * Без этого зажатый пробел позволял прыгать в воздухе: буфер нажатия и койот-время
     * складывались, и второй прыжок засчитывался ещё до касания земли.
     */
    private boolean jumpArmed = true;

    /** Сколько тиков подряд кубик стоит на земле. */
    private int groundTicks = 0;

    /** Высота, ниже которой кубик считается упавшим. Едет вниз вместе с уровнем. */
    private double deathFloorY = Double.NEGATIVE_INFINITY;

    /** Когда началась текущая попытка: из этого считается время прохождения. */
    private long attemptStartedAt = System.currentTimeMillis();

    /**
     * Тики, в течение которых игрок может быть НЕ на камере и это нормально.
     * Нужны на время пересадки после дальнего возврата к старту: телепорт ссаживает
     * пассажира, и без этой отсрочки забег закончился бы сам собой.
     */
    private int remountGraceTicks = 0;

    /** Тики анимации финиша. */
    private int finishTicks = 0;
    private static final int FINISH_ANIMATION_TICKS = 36;

    /** Отложенный запуск музыки: включать её в тот же тик, что и выключать, нельзя. */
    private int musicStartDelay = -1;

    private org.bukkit.inventory.ItemStack[] savedInventory = null;

    /** Куда вернуть игрока по окончании забега. */
    private Location returnLocation;

    /**
     * Свой прогрессбар.
     * <p>
     * Обычный создаётся и обновляется внутри 3D-забега, а он на 2D-уровне не
     * стартует вообще: игрок никуда не бежит, едет кубик. Поэтому полоса тут своя,
     * но цвет и настройка "скрыть полосу" берутся у уровня - как и у всех остальных.
     */
    private net.kyori.adventure.bossbar.BossBar bossBar;

    private Location savedLocation;
    private GameMode savedGameMode;
    private boolean savedAllowFlight;
    private boolean savedFlying;

    public TwoDGame(@NonNull ParkourBeat plugin,
                    @NonNull Player player,
                    @NonNull Level level,
                    @NonNull TwoDInput input,
                    boolean editorTest,
                    @Nullable Location returnLocation) {
        this.plugin = plugin;
        this.player = player;
        this.level = level;
        this.input = input;
        this.editorTest = editorTest;
        this.returnLocation = returnLocation;
        this.world = level.getWorld();

        DirectionChecker.Direction direction =
            level.getLevelSettings().getDirectionChecker().direction();
        this.forward = TwoDGeometry.forwardVector(direction);
        this.cameraDirection = TwoDGeometry.cameraDirection(this.forward);
        this.side = this.cameraDirection.clone().multiply(-1.0D);
        this.cameraYaw = TwoDEntityUtils.yawOf(this.cameraDirection);

        this.cubeSpawn = TwoDGeometry.resolveCubeSpawn(level);
    }

    /**
     * Пройденная доля уровня, 0..1.
     * <p>
     * У 2D-уровня есть начало и конец: длина задана линией. Поэтому прогресс -
     * это просто путь кубика, поделённый на длину линии.
     */
    public float getPassedProgress() {
        double length = this.lineLength();
        if (length <= 0.0D) return 0f;

        double travelled = this.travelledDistance();
        if (travelled <= 0.0D) return 0f;
        if (travelled >= length) return 1f;
        return (float) (travelled / length);
    }

    public int getCoinsCollected() {
        return this.coinsCollected;
    }

    /** Где сейчас кубик. Маркеры ставятся по нему, а не по игроку: игрок стоит сбоку. */
    @NonNull
    public Location getCubeLocation() {
        return this.position.toLocation(this.world);
    }

    /**
     * ТОЧНОСТЬ 2D-ЗАБЕГА.
     * <p>
     * Первые несколько смертей почти бесплатны: уровень изучают методом проб, и
     * наказывать за это нечестно. Дальше штраф становится заметнее, но всё равно
     * мягким - 2D это не про попадания по нотам.
     * <p>
     * Считаются ТОЛЬКО УЖЕ ПРОПУЩЕННЫЕ монетки, а не все оставшиеся: иначе забег
     * начинался бы с заниженной точности, хотя игрок ещё ничего не потерял.
     */
    public static double attemptAccuracy(int deaths, int missedCoins) {
        int free = Math.min(Math.max(0, deaths), FREE_DEATHS);
        int paid = Math.max(0, deaths - FREE_DEATHS);

        double accuracy = 100.0D
            - free * FREE_DEATH_PENALTY
            - paid * DEATH_ACCURACY_PENALTY
            - Math.max(0, missedCoins) * COIN_ACCURACY_PENALTY;

        return Math.max(MIN_ACCURACY, Math.min(100.0D, accuracy));
    }

    /** Сколько первых смертей почти прощаются. */
    public static final int FREE_DEATHS = 5;
    /** Штраф за смерть из числа прощаемых, процентных пункта. */
    public static final double FREE_DEATH_PENALTY = 0.05D;
    /** Штраф за каждую следующую смерть, процентных пункта. */
    public static final double DEATH_ACCURACY_PENALTY = 0.25D;
    /** Штраф за пропущенную монетку, процентных пункта. */
    public static final double COIN_ACCURACY_PENALTY = 0.75D;
    /** Ниже этого точность в 2D не опускается. */
    public static final double MIN_ACCURACY = 80.0D;

    /** Время текущей попытки, мс. */
    public long getAttemptMillis() {
        return Math.max(0L, System.currentTimeMillis() - this.attemptStartedAt);
    }

    /**
     * Монетки, которые кубик уже ПРОЕХАЛ и не забрал.
     * <p>
     * Именно они и есть потеря: те, что ещё впереди, ничего не стоят.
     */
    public int getMissedCoins() {
        try {
            java.util.List<Vector> coins = this.level.getLevelSettings()
                .getGameSettings().getTwoDSettings().getCoins();
            if (coins.isEmpty()) return 0;

            double travelled = this.travelledDistance();
            int missed = 0;

            for (int i = 0; i < coins.size(); i++) {
                if (this.collectedCoins.contains(i)) continue;

                Vector coin = coins.get(i);
                double dx = coin.getX() - this.cubeSpawn.getX();
                double dz = coin.getZ() - this.cubeSpawn.getZ();
                double along = dx * this.forward.getX() + dz * this.forward.getZ();

                if (along < travelled - TwoDLevelSettings.COIN_PICKUP_RADIUS) missed++;
            }
            return missed;
        } catch (Throwable t) {
            return 0;
        }
    }

    public boolean isEditorTest() {
        return this.editorTest;
    }

    @NonNull
    public Location getCubeSpawn() {
        return this.cubeSpawn.clone();
    }

    // ==================== ЗАПУСК ====================

    public boolean start() {
        if (this.active) return true;

        this.savedLocation = this.player.getLocation().clone();
        this.savedGameMode = this.player.getGameMode();
        this.savedAllowFlight = this.player.getAllowFlight();
        this.savedFlying = this.player.isFlying();

        this.resetToSpawn();
        this.attempt = 1;

        try {
            this.spawnEntities();
        } catch (Throwable t) {
            this.plugin.getLogger().warning("2D: не удалось создать сцену забега: " + t);
            this.despawnEntities();
            return false;
        }

        if (!this.isCameraAttached()) {
            this.despawnEntities();
            this.player.sendMessage(Lang.text(this.player, "twod.camera_failed"));
            return false;
        }

        if (!this.spectatorCamera) {
            this.player.setGameMode(GameMode.ADVENTURE);
            this.player.setAllowFlight(false);
            this.player.setFlying(false);
        }
        this.player.setFallDistance(0f);
        this.player.addPotionEffect(new PotionEffect(
            PotionEffectType.INVISIBILITY, 1_000_000, 0, false, false, false));
        this.applySlowness();

        if (this.editorTest) {
            // В тесте у строителя не должно остаться ничего, кроме выхода из теста.
            this.savedInventory = this.player.getInventory().getContents();
            this.player.getInventory().clear();
            try {
                ru.sortix.parkourbeat.item.ItemsManager items =
                    this.plugin.get(ru.sortix.parkourbeat.item.ItemsManager.class);

                items.putItem(this.player, ru.sortix.parkourbeat.item.editor.type.TestGameItem.class);
                // Маркеры нужны строителю именно во время теста - ставить их под бит
                // иначе негде. На 2D-уровне это ровно та же задача, что и на обычном.
                items.putItem(this.player, ru.sortix.parkourbeat.item.editor.type.CreateMarkerItem.class);
            } catch (Throwable ignored) {
            }
        }

        this.createBossBar();

        this.input.track(this.player);
        // В режиме наблюдателя гасить пакеты поворота не нужно и вредно: клиент их
        // и так не шлёт, а угол берётся у арморстенда.
        // В тесте строителю голову НЕ фиксируем: ему нужно осматривать уровень,
        // а не проверять, как выглядит идеальное 2D. Игроку - фиксируем.
        this.input.setRotationLocked(this.player,
            !this.editorTest && !this.spectatorCamera && TwoDTuning.LOCK_CAMERA);
        this.input.setLockedAngles(this.player, this.cameraYaw, TwoDTuning.CAMERA_PITCH);
        this.active = true;
        this.ticks = 0;

        this.restartMusic();
        this.sendActionBar();
        return true;
    }

    private void resetToSpawn() {
        this.position.setX(this.cubeSpawn.getX());
        this.position.setY(this.cubeSpawn.getY());
        this.position.setZ(this.cubeSpawn.getZ());
        this.verticalSpeed = 0.0D;
        this.onGround = false;
        this.groundTicks = 0;
        this.jumpArmed = true;
        this.speedFactor = this.currentSpeedFactor();
        this.deathFloorY = this.cubeSpawn.getY() - TwoDTuning.FALL_DEATH_DEPTH;
        this.lastGroundAtMillis = System.currentTimeMillis();
        this.attemptStartedAt = System.currentTimeMillis();
        this.mode = Mode.CUBE;
        this.cubeAngle = 0.0D;
        this.cubeTargetAngle = 0.0D;
        this.cameraInitialized = false;
        // Смещение носителя ЗДЕСЬ НЕ СБРАСЫВАЕМ.
        //
        // Именно из-за пересчёта кубик и уезжал вниз с каждой попыткой: замер брал
        // живые позиции сущностей в момент смерти, а они в этот момент где угодно -
        // умер в прыжке, значит замер выше, умер на земле - ниже. Ошибка копилась.
        // Смещение зависит только от типа носителя и замеряется один раз за сцену.
    }

    // ==================== СУЩНОСТИ ====================

    /** Спрятать служебную сущность от всех, кроме владельца забега. */
    private void ownEntity(@Nullable Entity entity) {
        if (entity == null) return;
        try {
            this.plugin.get(TwoDManager.class).getVisibility().own(entity, this.player);
        } catch (Throwable ignored) {
        }
    }

    private void releaseEntity(@Nullable Entity entity) {
        if (entity == null) return;
        try {
            this.plugin.get(TwoDManager.class).getVisibility().release(entity);
        } catch (Throwable ignored) {
        }
    }

    @NonNull
    private ArmorStand spawnSeat(@NonNull Location location) {
        return this.world.spawn(location, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setGravity(false);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setCustomNameVisible(false);
            stand.setRemoveWhenFarAway(false);
            stand.addScoreboardTag(TwoDManager.ENTITY_TAG);
            this.ownEntity(stand);
        });
    }

    private void spawnEntities() {
        Location cubeLocation = this.position.toLocation(this.world);

        this.cubeSeat = this.spawnSeat(cubeLocation);
        this.cube = TwoDCubeEntity.spawn(this.world, cubeLocation, this.cubeBlockData());
        this.cube.getEntity().setInvulnerable(true);
        this.cube.getEntity().setSilent(true);
        this.cube.getEntity().addScoreboardTag(TwoDManager.ENTITY_TAG);
        this.ownEntity(this.cube.getEntity());
        if (!this.cube.isStandalone()) this.cubeSeat.addPassenger(this.cube.getEntity());
        this.cubeAngle = 0.0D;
        this.cubeTargetAngle = 0.0D;

        Location cameraLocation = this.cameraLocation(this.position.getY());
        this.cameraSeat = this.spawnSeat(cameraLocation);

        if (this.spectatorCamera) {
            // РЕЖИМ НАБЛЮДАТЕЛЯ.
            //
            // Привязанный наблюдатель берёт угол обзора у сущности, а не у мыши:
            // головой в этом режиме не покрутить в принципе, никакими доворотами
            // этого добиваться не надо. Выход - SHIFT, ровно как в ванили.
            try {
                this.cameraEyeOffset = this.cameraSeat.getEyeHeight();
            } catch (Throwable t) {
                this.cameraEyeOffset = 0.0D;
            }

            this.player.setGameMode(GameMode.SPECTATOR);
            this.player.teleport(cameraLocation);
            this.player.setSpectatorTarget(this.cameraSeat);
            return;
        }

        // СНАЧАЛА РАЗВОРАЧИВАЕМ ИГРОКА, ПОТОМ САЖАЕМ.
        // Обычный телепорт ссаживает пассажира, поэтому единственный момент, когда
        // угол можно выставить наверняка, это до посадки. Иначе первые секунды забега
        // игрок смотрит куда попало и наводится руками.
        Location facing = cameraLocation.clone();
        facing.setYaw(this.cameraYaw);
        facing.setPitch(TwoDTuning.CAMERA_PITCH);
        try {
            this.player.teleport(facing);
        } catch (Throwable ignored) {
        }

        this.cameraSeat.addPassenger(this.player);
    }

    @NonNull
    private BlockData cubeBlockData() {
        BlockData data = Material.REPEATING_COMMAND_BLOCK.createBlockData();
        BlockFace face = TwoDTuning.getManualCubeFace();
        if (face == null) face = TwoDGeometry.faceOf(this.side);

        if (data instanceof Directional directional) {
            try {
                if (directional.getFaces().contains(face)) directional.setFacing(face);
            } catch (Throwable ignored) {
            }
        }
        return data;
    }

    private void despawnEntities() {
        this.removeBoat();
        if (this.cube != null) {
            this.releaseEntity(this.cube.getEntity());
            this.cube.remove();
            this.cube = null;
        }
        if (this.cubeSeat != null) {
            this.releaseEntity(this.cubeSeat);
            this.cubeSeat.remove();
            this.cubeSeat = null;
        }
        if (this.cameraSeat != null) {
            this.releaseEntity(this.cameraSeat);
            this.cameraSeat.remove();
            this.cameraSeat = null;
        }
    }

    private void removeBoat() {
        if (this.boat == null) return;
        try {
            if (this.cube != null && !this.cube.isStandalone() && this.cube.isValid()
                && this.cubeSeat != null && this.cubeSeat.isValid()) {
                this.boat.removePassenger(this.cube.getEntity());
                this.cubeSeat.addPassenger(this.cube.getEntity());
                this.offsetsDirty = true;
            }
        } catch (Throwable ignored) {
        }
        this.releaseEntity(this.boat);
        this.boat.remove();
        this.boat = null;
        // Смещение носителя НЕ пересчитываем.
        //
        // Сразу после пересадки позиция пассажира на сервере ещё старая, и замер
        // давал мусор - те самые 0.1 блока щели между кубиком и землёй после
        // возврата из полёта. Смещение у нас одно на весь забег, оно замерено при
        // создании сцены и меняться не может.
    }

    /**
     * Транспорт полёта.
     * <p>
     * Кубик В НЁМ НЕ СИДИТ: пассажира клиент рисует по своим правилам, и в высокой
     * вагонетке кубик тонул бы по самую крышу. Вагонетка просто едет рядом, ровно
     * под кубиком, и двигаем мы её сами.
     */
    private void spawnBoat() {
        if (this.boat != null) return;
        if (this.cube == null) return;

        EntityType type = TwoDGeometry.flightVehicleType();
        if (type == null) return;

        try {
            Location location = this.position.toLocation(this.world);
            location.setY(location.getY() + TwoDTuning.BOAT_Y_OFFSET);
            Entity spawned = this.world.spawnEntity(location, type);
            spawned.setGravity(false);
            spawned.setSilent(true);
            spawned.setInvulnerable(true);
            spawned.setPersistent(false);
            spawned.addScoreboardTag(TwoDManager.ENTITY_TAG);
            // Отдельная метка на будущее: по ней лодку легко найти и подменить
            // модель в ресурспаке, не трогая остальные служебные сущности.
            spawned.addScoreboardTag(TwoDManager.FLIGHT_VEHICLE_TAG);
            this.ownEntity(spawned);

            if (!this.cube.isStandalone() && this.cubeSeat != null) {
                // Две сущности, которые двигают отдельно, клиент сглаживает по-разному
                // и они разъезжаются. Поэтому кубик садится в лодку, а лодка - на
                // носителя: едет вся связка целиком.
                this.cubeSeat.removePassenger(this.cube.getEntity());
                this.cubeSeat.addPassenger(spawned);
                spawned.addPassenger(this.cube.getEntity());
                this.offsetsDirty = true;
            }

            this.boat = spawned;
        } catch (Throwable t) {
            this.plugin.getLogger().warning("2D: не удалось выдать транспорт для полёта: " + t);
            this.boat = null;
        }
    }

    // ==================== ТИК ====================

    public void tick() {
        if (!this.active) return;

        if (!this.player.isOnline() || this.player.getWorld() != this.world) {
            this.stop(true);
            return;
        }

        // Игрок отцепился от камеры - это и есть SHIFT, то есть "закончить игру".
        // Но не во время пересадки: там отцепление наше собственное.
        if (this.remountGraceTicks > 0) {
            this.remountGraceTicks--;
        } else if (!this.isCameraAttached()) {
            this.stop(true);
            return;
        }

        // Пока идёт пауза после проигрыша, кубика намеренно нет - это не поломка сцены.
        if (this.deadTicks <= 0 && (this.cube == null || !this.cube.isValid()
            || this.cubeSeat == null || !this.cubeSeat.isValid())) {
            // Сущность потерялась (выгрузка чанка, сторонний плагин) - пересобираем сцену.
            this.despawnAndRespawnScene();
            if (!this.active) return;
        }

        this.ticks++;
        this.player.setFallDistance(0f);
        if (this.cube != null) this.cube.keepAlive();
        this.tickMusic();

        if (this.finishTicks > 0) {
            this.finishTicks--;
            this.tickFinishAnimation();
            this.updateEntities();
            if (this.finishTicks == 0) this.finishNow();
            return;
        }

        if (this.deadTicks > 0) {
            this.deadTicks--;
            if (this.deadTicks == 0) this.respawnAttempt();
            this.updateEntities();
            return;
        }

        this.tickInput();
        this.tickPhysics();
        // Физика могла закончить забег: врезались, упали или доехали до финиша.
        if (!this.active || this.deadTicks > 0) return;
        this.tickBanners();
        this.tickCoins();
        this.tickFlyTrail();
        this.tickGroundTrail();
        this.tickBannerFx();
        this.updateEntities();

        this.updateBossBar();

        // Скорборд игрока во время забега подменяется, а вместе с ним теряется
        // команда подсветки монеток. Возвращаем её раз в секунду.
        if (this.ticks % 20 == 0) TwoDCoins.syncGlowTeam(this.player, this.level);

        if (TwoDTuning.DEBUG || this.ticks % 20 == 0) this.sendActionBar();
    }

    /**
     * Камера всё ещё держит игрока? В режиме наблюдателя это привязка к сущности,
     * в обычном - посадка на неё. И то и другое рвётся по SHIFT, что нам и нужно.
     */
    private boolean isCameraAttached() {
        if (this.cameraSeat == null) return false;
        if (this.spectatorCamera) {
            return this.cameraSeat.equals(this.player.getSpectatorTarget());
        }
        return this.cameraSeat.equals(this.player.getVehicle());
    }

    private void despawnAndRespawnScene() {
        boolean wasRiding = this.isCameraAttached();
        if (!wasRiding) {
            this.stop(true);
            return;
        }
        try {
            if (this.cube != null) this.cube.remove();
            if (this.cubeSeat != null) this.cubeSeat.remove();
            this.removeBoat();

            Location cubeLocation = this.position.toLocation(this.world);
            this.cubeSeat = this.spawnSeat(cubeLocation);
            this.cube = TwoDCubeEntity.spawn(this.world, cubeLocation, this.cubeBlockData());
            this.cube.getEntity().setInvulnerable(true);
            this.cube.getEntity().setSilent(true);
            this.cube.getEntity().addScoreboardTag(TwoDManager.ENTITY_TAG);
            this.ownEntity(this.cube.getEntity());
        this.ownEntity(this.cube.getEntity());
            if (!this.cube.isStandalone()) this.cubeSeat.addPassenger(this.cube.getEntity());
            this.offsetsDirty = true;
        } catch (Throwable t) {
            this.plugin.getLogger().warning("2D: не удалось восстановить кубик: " + t);
            this.stop(true);
        }
    }

    // ==================== ВВОД И ПИНГ ====================

    /** Сглаженный пинг. Мгновенный скачет от тика к тику и дёргал бы физику. */
    private double smoothedPing = -1.0D;

    /**
     * Множитель скорости, зафиксированный на время прыжка.
     * <p>
     * Пересчитывается только пока кубик на земле - см. {@link #speedPerTick()}.
     */
    private double speedFactor = 1.0D;

    private int getPing() {
        if (!TwoDTuning.PING_COMPENSATION) return 0;
        try {
            int ping = this.plugin.get(PingManager.class).getPing(this.player);
            if (ping < 0) ping = 0;
            ping = Math.min(ping, TwoDTuning.PING_CAP);

            // Скользящее среднее: одиночный всплеск пинга не должен менять
            // длину конкретного прыжка.
            if (this.smoothedPing < 0) this.smoothedPing = ping;
            else this.smoothedPing += (ping - this.smoothedPing) * 0.05D;

            return (int) Math.round(this.smoothedPing);
        } catch (Throwable t) {
            return 0;
        }
    }

    private void tickInput() {
        long press = this.input.getLastPressAt(this.player);
        if (press <= 0L) {
            if (this.mode == Mode.FLY) return;
            // ЗАЖАТЫЙ ПРОБЕЛ ПРЫГАЕТ САМ.
            // Клиент шлёт фронт нажатия ровно один раз, поэтому без этой ветки
            // удержание пробела давало один прыжок и мёртвую тишину дальше.
            // Удержание отталкивает кубик сразу при касании земли, как в оригинале:
            // у автопрыжка своя, более короткая задержка.
            if (this.mode == Mode.CUBE && TwoDTuning.HOLD_TO_JUMP
                && this.canJump(TwoDTuning.AUTO_JUMP_GROUND_TICKS)
                && this.input.isHeld(this.player)) {
                this.doJump();
            }
            return;
        }

        long now = System.currentTimeMillis();
        int ping = this.getPing();

        if (this.mode == Mode.FLY) {
            // В полёте нажатие срабатывает сразу: это не прыжок с земли, ждать нечего.
            if (now - press <= TwoDTuning.JUMP_BUFFER_BASE_MILLIS + ping) {
                this.verticalSpeed += TwoDTuning.FLY_THRUST;
            }
            this.input.consumePress(this.player);
            return;
        }

        // Нижняя граница окна - примерно тик: пакет о нажатии просто не может
        // прилететь мгновенно, и без этого запаса прыжок не сработал бы никогда.
        long buffer = Math.max(60L, TwoDTuning.JUMP_BUFFER_BASE_MILLIS + ping);
        if (now - press > buffer) {
            // Нажатие протухло - оно относилось к прошлой попытке или к прошлой земле.
            this.input.consumePress(this.player);
            return;
        }

        // НАЖАЛ В ВОЗДУХЕ - НАЖАТИЕ СГОРЕЛО.
        //
        // Именно это и выглядело как второй прыжок: нажатие висело в буфере и
        // срабатывало в момент касания земли, то есть уже после того, как игрок
        // "прыгнул" в воздухе. В оригинале никакого буфера нет: не стоишь на земле -
        // нажатие в пустоту. Удержание при этом работает как раньше.
        if (!this.onGround) {
            this.input.consumePress(this.player);
            return;
        }

        // Стоим на земле, но приземление ещё не дорисовано: нажатие не сжигаем,
        // оно сработает через тик-другой само.
        if (!this.canJump() && this.jumpArmed) return;

        // ПРЫГАЕМ ТОЛЬКО С ЗЕМЛИ.
        // Нажатие, прилетевшее в воздухе, не пропадает: оно ждёт в буфере и сработает
        // в момент касания земли. Никаких прыжков из ниоткуда.
        if (this.canJump()) {
            this.doJump();
            this.input.consumePress(this.player);
        }
        // Если земли нет - нажатие остаётся в буфере и сработает при касании.
    }

    /**
     * Прыжок возможен ровно в одном случае: кубик стоит на земле и с момента
     * приземления ещё не прыгал.
     */
    private boolean canJump() {
        return this.canJump(TwoDTuning.MIN_GROUND_TICKS);
    }

    private boolean canJump(int requiredGroundTicks) {
        if (this.mode != Mode.CUBE) return false;
        if (!this.onGround || !this.jumpArmed) return false;

        // ЖДЁМ, ПОКА КЛИЕНТ ДОРИСУЕТ ПРИЗЕМЛЕНИЕ.
        //
        // Физика ставит кубик на землю мгновенно, а клиент доводит его туда ещё
        // пару тиков. Если разрешить прыжок в тот же тик, игрок увидит отрыв от
        // земли ДО касания - то есть ровно тот самый "прыжок в воздухе".
        return this.groundTicks >= requiredGroundTicks;
    }

    private void doJump() {
        this.verticalSpeed = TwoDTuning.JUMP_VELOCITY;
        this.onGround = false;
        this.groundTicks = 0;
        this.jumpArmed = false;
        this.playJumpSound();
        this.tryAutoMarker();

        // Кувырок как в оригинале: пол-оборота на прыжок, докрутка по времени полёта.
        this.cubeTargetAngle = this.cubeAngle + Math.PI * Math.max(0.0D, TwoDTuning.CUBE_SPINS_PER_JUMP);
    }

    /**
     * АВТОМАРКЕР НА ПРЫЖОК.
     * <p>
     * На обычных уровнях его ставит обработчик прыжка игрока, но в 2D игрок не
     * прыгает вообще - прыгает кубик. Поэтому точку ставим здесь и по позиции
     * кубика: строителю нужно место на трассе, а не место, где стоял он сам.
     */
    private void tryAutoMarker() {
        if (!this.editorTest) return;

        try {
            ru.sortix.parkourbeat.activity.UserActivity activity =
                this.plugin.get(ru.sortix.parkourbeat.activity.ActivityManager.class)
                    .getActivity(this.player);
            if (!(activity instanceof ru.sortix.parkourbeat.activity.type.EditActivity editActivity)) return;
            if (!editActivity.isAutoJumpMarkers()) return;

            ru.sortix.parkourbeat.levels.settings.HelperMarker marker =
                new ru.sortix.parkourbeat.levels.settings.HelperMarker(
                    this.position.clone(),
                    ru.sortix.parkourbeat.levels.settings.HelperMarker.Kind.LEFT);

            if (!this.level.getLightShow().addHelperMarker(marker)) return;

            ru.sortix.parkourbeat.utils.text.ActionBarPriority.notice(this.player,
                Lang.text(this.player, "twod.jumpmarker", "%count%",
                    String.valueOf(this.level.getLightShow().getHelperMarkers().size())));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Довод кубика до целевого угла. В воздухе он крутится, на земле встаёт ровно:
     * промежуточный угол на земле выглядит как сломанная модель.
     */
    private void tickCubeRotation() {
        if (this.cube == null || !this.cube.canRotate()) return;
        if (this.mode == Mode.FLY) {
            // Кораблик не кувыркается, он наклоняется по вертикальной скорости.
            double tilt = Math.max(-0.6D, Math.min(0.6D, this.verticalSpeed * 1.4D));
            this.cubeAngle = tilt;
            this.cube.applyRotation(this.cubeAngle, this.side, 2);
            return;
        }

        if (this.onGround) {
            double snapped = Math.round(this.cubeAngle / (Math.PI / 2.0D)) * (Math.PI / 2.0D);
            if (Math.abs(snapped - this.cubeAngle) > 1.0E-4D) {
                this.cubeAngle = snapped;
                this.cubeTargetAngle = snapped;
                this.cube.applyRotation(this.cubeAngle, this.side, 2);
            }
            return;
        }

        if (Math.abs(this.cubeTargetAngle - this.cubeAngle) < 1.0E-4D) return;

        double step = (this.cubeTargetAngle - this.cubeAngle) * 0.28D;
        this.cubeAngle += step;
        this.cube.applyRotation(this.cubeAngle, this.side, 2);
    }

    private void playJumpSound() {
        try {
            this.player.playSound(this.player.getLocation(), Sound.ENTITY_SLIME_JUMP, 0.5f, 1.6f);
        } catch (Throwable ignored) {
        }
    }

    private double levelSpeed() {
        try {
            return this.level.getLevelSettings().getGameSettings().getTwoDSettings().resolveSpeed();
        } catch (Throwable t) {
            return TwoDTuning.SPEED;
        }
    }

    /**
     * Скорость кубика за тик.
     * <p>
     * МНОЖИТЕЛЬ ПИНГА ЗАМОРОЖЕН НА ВРЕМЯ ПРЫЖКА.
     * <p>
     * Иначе получалось вот что: пинг гуляет вокруг порога замедления, скорость
     * пересчитывается каждый тик, и один и тот же прыжок каждый раз оказывается
     * разной длины. Играть по такому уровню невозможно - глазомер не работает, а
     * выглядит это как "блоки перелетаю через раз".
     * <p>
     * Пока кубик на земле, множитель обновляется. Оторвался - до самого приземления
     * летит с тем, что было в момент отрыва. Дуга получается ровно такой, какой её
     * видно в момент прыжка.
     */
    private double speedPerTick() {
        double speed = this.levelSpeed() / 20.0D;

        if (this.onGround || this.mode == Mode.FLY) {
            this.speedFactor = this.currentSpeedFactor();
        }
        return speed * this.speedFactor;
    }

    private double currentSpeedFactor() {
        if (!TwoDTuning.PING_COMPENSATION) return 1.0D;

        int ping = this.getPing();
        if (ping <= TwoDTuning.PING_SLOWDOWN_START) return 1.0D;

        // На большом пинге уровень едет чуть медленнее: реакция игрока физически
        // не успевает за оригинальной скоростью, и уровень становится непроходимым.
        double extra = (ping - TwoDTuning.PING_SLOWDOWN_START) / 1000.0D;
        double slowdown = Math.min(TwoDTuning.PING_SLOWDOWN_MAX, extra);
        return 1.0D - slowdown;
    }

    // ==================== ФИЗИКА ====================

    private void tickPhysics() {
        double half = TwoDTuning.CUBE_HALF;
        double gravity = this.mode == Mode.FLY ? TwoDTuning.FLY_GRAVITY : TwoDTuning.GRAVITY;

        if (this.mode == Mode.FLY) {
            // УПРАВЛЕНИЕ КОРАБЛИКОМ.
            //
            // Зажата кнопка - тянем вверх, отпущена - вниз, и скорость не прыгает
            // рывками, а плавно подтягивается к целевой. Именно эта плавность и делает
            // полёт управляемым: раньше каждое нажатие било импульсом и кораблик
            // швыряло.
            boolean held = this.input.isHeld(this.player);
            double target = held ? TwoDTuning.FLY_MAX_UP : -TwoDTuning.FLY_MAX_DOWN;
            double response = TwoDTuning.FLY_RESPONSE;
            if (held) response += TwoDTuning.FLY_HOLD_THRUST;

            this.verticalSpeed += (target - this.verticalSpeed) * Math.min(1.0D, response);
            this.verticalSpeed = Math.max(-TwoDTuning.FLY_MAX_DOWN,
                Math.min(TwoDTuning.FLY_MAX_UP, this.verticalSpeed));
        } else {
            this.verticalSpeed -= gravity;
            this.verticalSpeed = Math.max(-TwoDTuning.MAX_FALL_SPEED, this.verticalSpeed);
        }

        this.onGround = false;

        // ДВИЖЕНИЕ РАЗБИВАЕТСЯ НА МЕЛКИЕ ШАГИ.
        //
        // Раньше за тик кубик сначала проходил ВСЮ вертикаль, потом ВСЮ горизонталь.
        // При прыжке это значит: сперва подскочил, и только потом поехал вперёд -
        // то есть перелезал через угол блока, в который на самом деле должен был
        // врезаться. Отсюда и "перелетаю блоки, а в Geometry Dash так нельзя":
        // там движение непрерывное, а не в два больших рывка.
        //
        // Теперь тик делится на шаги не длиннее 0.1 блока, и в каждом шаге вертикаль
        // и горизонталь идут вместе. Траектория получается почти непрерывной, углы
        // блоков ловятся честно. Ни одна настройка при этом не меняется: это не новая
        // физика, а та же самая, посчитанная точнее.
        // Скорость считаем ОДИН РАЗ на тик и делим на подшаги: пересчёт внутри
        // цикла давал бы разную длину шагов в пределах одного прыжка.
        double totalHorizontal = this.speedPerTick();
        double totalVertical = this.verticalSpeed;

        int steps = (int) Math.ceil(
            Math.max(Math.abs(totalVertical), Math.abs(totalHorizontal)) / SUBSTEP_LENGTH);
        steps = Math.max(1, Math.min(MAX_SUBSTEPS, steps));

        double stepHorizontal = totalHorizontal / steps;
        double stepVertical = totalVertical / steps;

        for (int i = 0; i < steps; i++) {
            if (!this.moveSubStep(half, stepHorizontal, stepVertical)) return;
        }

        // ВРЕМЯ НА ЗЕМЛЕ СЧИТАЕТСЯ ПО ТИКАМ, А НЕ ПО ПОДШАГАМ.
        //
        // Это и сломало приземление после дробления движения: подшагов в тике
        // шесть-семь, счётчик набирал нужные два ещё внутри того же тика, и кубик
        // уходил в новый прыжок в тот же момент, когда коснулся земли. Клиент такое
        // касание нарисовать не успевает - выглядело как прыжки без касания земли.
        if (this.onGround) this.groundTicks++;
        else this.groundTicks = 0;

        // СОШЁЛ С КРАЯ - ЗНАЧИТ УЖЕ В ВОЗДУХЕ.
        //
        // Опора проверяется по итогу тика, уже на новом месте: иначе кубик считался
        // бы стоящим на земле ещё почти полтора блока после края площадки.
        if (this.onGround) {
            double stillGround = TwoDPhysics.findGround(this.world,
                this.position.getX(), this.position.getZ(), half,
                this.position.getY(), this.position.getY() - 0.06D);

            if (Double.isNaN(stillGround)) {
                this.onGround = false;
                this.groundTicks = 0;
                this.jumpArmed = false;
            }
        }

        // ФИНИШ - ЭТО КОНЕЦ ЛИНИИ.
        // Длина уровня задаётся строителем через палочку частиц, поэтому линия и есть
        // единственный источник правды о том, где уровень заканчивается.
        if (this.travelledDistance() >= this.lineLength()) {
            this.complete();
        }
    }

    /** Длина одного подшага, блоков. Меньше - точнее и дороже. */
    private static final double SUBSTEP_LENGTH = 0.1D;
    /** Предохранитель на случай безумных значений скорости в настройках. */
    private static final int MAX_SUBSTEPS = 24;

    /**
     * Один маленький шаг движения.
     *
     * @return false, если забег на этом закончился (столкновение, падение, финиш)
     */
    private boolean moveSubStep(double half, double stepHorizontal, double stepVertical) {
        // ПОРЯДОК ВНУТРИ ПОДШАГА ЗАВИСИТ ОТ НАПРАВЛЕНИЯ.
        //
        // На подъёме сначала едем вперёд, потом вверх: иначе кубик успевает
        // приподняться и обойти угол блока, в который должен был врезаться -
        // те самые перелёты. На спуске наоборот, сначала вниз: только так
        // работает приземление на верх блока.
        if (stepVertical > 0.0D) {
            if (!this.moveHorizontal(half, stepHorizontal)) return false;
            return this.moveVertical(half, stepVertical);
        }

        if (!this.moveVertical(half, stepVertical)) return false;
        return this.moveHorizontal(half, stepHorizontal);
    }

    private boolean moveVertical(double half, double stepVertical) {
        double x = this.position.getX();
        double y = this.position.getY();
        double z = this.position.getZ();

        // --- Вертикаль ---
        if (stepVertical <= 0.0D) {
            double newY = y + stepVertical;
            double ground = TwoDPhysics.findGround(this.world, x, z, half, y, newY);

            if (!Double.isNaN(ground)) {
                y = ground;
                this.verticalSpeed = 0.0D;
                this.onGround = true;
                this.jumpArmed = true;
                this.lastGroundAtMillis = System.currentTimeMillis();
                // Пол смерти едет вместе с уровнем: спуск вниз по лестнице это
                // нормальный геймплей, а не падение в пропасть.
                this.deathFloorY = y - TwoDTuning.FALL_DEATH_DEPTH;
            } else {
                y = newY;
            }
        } else {
            double newY = y + stepVertical;
            double ceiling = TwoDPhysics.findCeiling(this.world, x, z, half, y + 1.0D, newY + 1.0D);

            if (!Double.isNaN(ceiling)) {
                if (TwoDTuning.CEILING_KILLS) {
                    this.position.setY(y);
                    this.crash();
                    return false;
                }
                y = ceiling - 1.0D - TwoDPhysics.EPSILON;
                this.verticalSpeed = 0.0D;
            } else {
                y = newY;
            }
        }

        this.position.setY(y);

        // Провалился ниже уровня - это тоже проигрыш, как в оригинале.
        if (y < this.deathFloorY || y < this.world.getMinHeight() + 1) {
            this.crash();
            return false;
        }
        return true;
    }

    private boolean moveHorizontal(double half, double stepHorizontal) {
        double x = this.position.getX();
        double y = this.position.getY();
        double z = this.position.getZ();

        // --- Горизонталь ---
        double newX = x + this.forward.getX() * stepHorizontal;
        double newZ = z + this.forward.getZ() * stepHorizontal;

        BoundingBox forwardBox = new BoundingBox(
            newX - half, y + 0.06D, newZ - half,
            newX + half, y + 1.0D - 0.06D, newZ + half);

        if (TwoDPhysics.collides(this.world, forwardBox)) {
            // Нажатие было вовремя, просто дошло до сервера с опозданием:
            // засчитываем прыжок и пропускаем остаток шага, а не убиваем игрока.
            if (this.tryLagCompensatedJump()) return false;

            this.crash();
            return false;
        }

        this.position.setX(newX);
        this.position.setZ(newZ);

        // Шип проверяем на каждом подшаге: на скорости кубик успевал бы проскочить
        // сквозь него между двумя проверками раз в тик.
        if (this.touchesSpike()) {
            this.crash();
            return false;
        }
        return true;
    }

    /**
     * КАСАНИЕ ШИПА.
     * <p>
     * Шип убивает с любой стороны, включая приземление сверху - в этом вся его суть.
     * Обычная проверка столкновений тут не годится: она специально разрешает вставать
     * на верх блока, иначе по уровню нельзя было бы бежать.
     */
    private boolean touchesSpike() {
        TwoDLevelSettings settings;
        try {
            settings = this.level.getLevelSettings().getGameSettings().getTwoDSettings();
        } catch (Throwable t) {
            return false;
        }
        if (settings.getSpikesAmount() == 0) return false;

        double half = TwoDTuning.CUBE_HALF;
        BoundingBox box = TwoDPhysics.cubeBox(
            this.position.getX(), this.position.getY() + 0.02D, this.position.getZ(), half);

        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.floor(box.getMaxX() - 1.0E-7D);
        int minY = (int) Math.floor(box.getMinY());
        int maxY = (int) Math.floor(box.getMaxY() - 1.0E-7D);
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.floor(box.getMaxZ() - 1.0E-7D);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!settings.isSpike(x, y, z)) continue;

                    // СМОТРИМ НА НАСТОЯЩУЮ ФОРМУ БЛОКА, А НЕ НА ЕГО КЛЕТКУ.
                    //
                    // Раньше шипом считался весь кубометр: открытый люк убивал так же,
                    // как полный блок, хотя перепрыгнуть его можно было спокойно.
                    // Теперь берётся реальная коробка блока - у открытого люка это
                    // тонкая пластина у стены, у закрытого плита у пола, у полного
                    // блока весь куб.
                    if (this.spikeBoxHits(x, y, z, box)) return true;
                }
            }
        }
        return false;
    }

    /** Пересекает ли кубик настоящую форму блока-шипа. */
    private boolean spikeBoxHits(int x, int y, int z, @NonNull BoundingBox cube) {
        try {
            org.bukkit.block.Block block = this.world.getBlockAt(x, y, z);
            BoundingBox blockBox = block.getBoundingBox();

            // У блока без формы (воздух, трава, открытая калитка без коллизии)
            // коробка нулевая - для таких берём клетку целиком: строитель пометил
            // её осознанно, значит это оформление шипа из ресурспака.
            if (blockBox.getWidthX() <= 0 || blockBox.getHeight() <= 0 || blockBox.getWidthZ() <= 0) {
                blockBox = new BoundingBox(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
            } else {
                // Небольшой запас: касание строго по грани не должно убивать,
                // иначе бег по полу рядом с шипом становится лотереей.
                blockBox = blockBox.clone().expand(-SPIKE_TOLERANCE, -SPIKE_TOLERANCE, -SPIKE_TOLERANCE);
                if (blockBox.getVolume() <= 0) return false;
            }

            return blockBox.overlaps(cube);
        } catch (Throwable t) {
            return true;
        }
    }

    /** Запас на касание грани шипа, блоков. */
    private static final double SPIKE_TOLERANCE = 0.03D;

    /**
     * Последний шанс перед проигрышем: игрок нажал пробел вовремя, но пакет шёл дольше,
     * чем кубик ехал до стены. Прыжок засчитывается задним числом.
     */
    private boolean tryLagCompensatedJump() {
        if (this.mode != Mode.CUBE) return false;
        if (!TwoDTuning.PING_COMPENSATION) return false;

        long press = this.input.getLastPressAt(this.player);
        if (press <= 0L) return false;

        long now = System.currentTimeMillis();
        int ping = this.getPing();
        if (now - press > TwoDTuning.JUMP_BUFFER_BASE_MILLIS + ping) return false;

        // Компенсация работает только когда кубик стоит на земле: иначе она
        // превращается в тот самый прыжок из воздуха.
        if (!this.canJump()) return false;

        this.doJump();
        this.input.consumePress(this.player);
        return true;
    }

    // ==================== БАННЕРЫ ====================

    private void tickBanners() {
        // ПЕРЕХОД СРАБАТЫВАЕТ, КОГДА КУБИК ПРОХОДИТ СКВОЗЬ ФЛАГ.
        //
        // Раньше хватало касания краем: кубик шириной 0.84 задевал флаг почти за
        // полблока до него, и режим менялся раньше, чем игрок видел встречу.
        // Теперь смотрим на центр кубика, а не на его габарит.
        double half = 0.05D;
        BoundingBox box = TwoDPhysics.cubeBox(
            this.position.getX(), this.position.getY(), this.position.getZ(), half);

        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.floor(box.getMaxX() - 1.0E-7D);
        int minY = (int) Math.floor(box.getMinY());
        int maxY = (int) Math.floor(box.getMaxY() - 1.0E-7D);
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.floor(box.getMaxZ() - 1.0E-7D);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = this.world.getBlockAt(x, y, z);
                    TwoDBanners.Type type = TwoDBanners.detect(block);
                    if (type == null) continue;
                    this.applyBanner(type);
                    return;
                }
            }
        }
    }

    /**
     * Портальная дымка перед флагами полёта. Она видна заранее и говорит игроку,
     * где кубик сменит режим, а не ставит его перед фактом.
     */
    private void tickBannerFx() {
        if (this.ticks % 4 != 0) return;
        double from = this.travelledDistance() - 2.0D;
        TwoDBannerFx.render(this.player, this.world, this.cubeSpawn.toVector(),
            this.forward, this.side, from, from + 40.0D, this.cubeSpawn.getY());
    }

    private void applyBanner(@NonNull TwoDBanners.Type type) {
        if (type == TwoDBanners.Type.FLY) {
            if (this.mode == Mode.FLY) return;
            this.mode = Mode.FLY;
            this.spawnBoat();
            this.verticalSpeed = Math.max(this.verticalSpeed, 0.0D);
            this.player.playSound(this.player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
            this.player.showTitle(Title.title(Component.empty(),
                Lang.text(this.player, "twod.title.flight"), ATTEMPT_TIMES));
        } else {
            if (this.mode == Mode.CUBE) return;
            this.mode = Mode.CUBE;
            this.removeBoat();
            this.player.playSound(this.player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
            this.player.showTitle(Title.title(Component.empty(),
                Lang.text(this.player, "twod.title.parkour"), ATTEMPT_TIMES));
        }
    }

    // ==================== ПРОИГРЫШ ====================

    private static final Particle FLASH_PARTICLE = resolveParticle("FLASH", "EXPLOSION_LARGE", "EXPLOSION_HUGE");

    /**
     * ПРОИГРЫШ.
     * <p>
     * Кубик исчезает во вспышке: сначала белый flash прямо в его центре, следом
     * разлетающиеся осколки и короткий резкий звук. Сущность кубика убирается сразу -
     * так исчезновение видно мгновенно, а не через долю секунды, когда клиент
     * доведёт его до места.
     */
    private void crash() {
        if (this.deadTicks > 0) return;

        this.verticalSpeed = 0.0D;
        this.deadTicks = Math.max(1, TwoDTuning.RESPAWN_DELAY_TICKS);

        try {
            Location at = this.position.toLocation(this.world).add(0.0D, 0.5D, 0.0D);

            if (FLASH_PARTICLE != null) {
                this.player.spawnParticle(FLASH_PARTICLE, at, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                // Вторая вспышка чуть в стороне и чуть позже по кадру даёт объём:
                // одна точка читается как пятно, две - как взрыв.
                this.player.spawnParticle(FLASH_PARTICLE,
                    at.clone().add(this.forward.getX() * 0.3D, 0.15D, this.forward.getZ() * 0.3D),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
            }

            // Осколки летят вперёд по ходу движения: кубик врезался, а не лопнул.
            this.player.spawnParticle(Particle.CRIT, at, 18, 0.25D, 0.25D, 0.25D, 0.35D);
            this.player.spawnParticle(Particle.END_ROD, at, 10, 0.1D, 0.1D, 0.1D, 0.18D);

            Object trail = trailBlockData();
            if (BLOCK_TRAIL_PARTICLE != null && trail != null) {
                this.player.spawnParticle(BLOCK_TRAIL_PARTICLE, at, 20,
                    0.3D, 0.3D, 0.3D, 0.15D, trail);
            }

            this.player.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.7f);
            this.player.playSound(at, Sound.BLOCK_GLASS_BREAK, 0.8f, 0.8f);
        } catch (Throwable ignored) {
        }

        if (this.boat != null) {
            try {
                Location at = this.position.toLocation(this.world);
                this.player.playSound(at, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.7f);
                this.player.spawnParticle(Particle.ITEM_CRACK, at, 24, 0.3D, 0.2D, 0.3D, 0.2D,
                    new org.bukkit.inventory.ItemStack(Material.OAK_PLANKS));
            } catch (Throwable ignored) {
            }
            this.removeBoat();
        }

        this.shatterCube();
        this.stopMusic();
    }

    /**
     * КУБИК РАЗЛЕТАЕТСЯ НАПОПОЛАМ.
     * <p>
     * Сущность убирается сразу, а на её месте остаётся облако осколков ИМЕННО ЭТОГО
     * блока: в ресурспаке у повторяющегося командного блока лежит текстура кубика,
     * поэтому осколки выглядят как куски самого кубика, а не абстрактная пыль.
     * <p>
     * Осколки идут двумя половинами, разлетающимися в стороны от линии разлома -
     * так читается разрыв надвое, а не просто взрыв.
     */
    private void shatterCube() {
        Location center = this.position.toLocation(this.world).add(0.0D, 0.5D, 0.0D);
        this.hideCube();

        try {
            // Тип частицы разрушения на разных версиях называется по-разному,
            // поэтому берём уже разрешённый вариант.
            Particle particle = BLOCK_TRAIL_PARTICLE;
            if (particle == null) return;

            Object data = this.cubeBlockData();
            double half = TwoDTuning.CUBE_HALF;

            for (int side = -1; side <= 1; side += 2) {
                for (int i = 0; i < 22; i++) {
                    double alongOffset = (Math.random() - 0.5D) * half * 2.0D;
                    double up = (Math.random() - 0.5D) * 0.9D;
                    double lateral = side * (0.05D + Math.random() * half);

                    Location at = new Location(this.world,
                        center.getX() + this.forward.getX() * alongOffset + this.side.getX() * lateral,
                        center.getY() + up,
                        center.getZ() + this.forward.getZ() * alongOffset + this.side.getZ() * lateral);

                    // Скорость направлена от линии разлома: половины уходят врозь.
                    double speed = 0.18D + Math.random() * 0.22D;
                    this.player.spawnParticle(particle, at, 0,
                        this.side.getX() * side * speed,
                        0.10D + Math.random() * 0.12D,
                        this.side.getZ() * side * speed,
                        1.0D, data);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** Убрать сущность кубика до следующей попытки. */
    private void hideCube() {
        try {
            if (this.cube != null) {
                this.releaseEntity(this.cube.getEntity());
                this.cube.remove();
                this.cube = null;
            }
        } catch (Throwable ignored) {
        }
    }

    /** Вернуть кубик на место перед новой попыткой. */
    private void showCube() {
        if (this.cube != null) return;
        try {
            Location at = this.position.toLocation(this.world);
            this.cube = TwoDCubeEntity.spawn(this.world, at, this.cubeBlockData());
            this.cube.getEntity().setInvulnerable(true);
            this.cube.getEntity().setSilent(true);
            this.cube.getEntity().addScoreboardTag(TwoDManager.ENTITY_TAG);
            this.ownEntity(this.cube.getEntity());
        this.ownEntity(this.cube.getEntity());
            if (!this.cube.isStandalone() && this.cubeSeat != null && this.cubeSeat.isValid()) {
                this.cubeSeat.addPassenger(this.cube.getEntity());
            }
            this.offsetsDirty = true;
        } catch (Throwable t) {
            this.plugin.getLogger().warning("2D: не удалось вернуть кубик: " + t);
        }
    }

    private void respawnAttempt() {
        this.attempt++;
        this.coinsCollected = 0;
        this.restoreCoins();
        this.removeBoat();
        this.resetToSpawn();
        this.showCube();
        this.input.consumePress(this.player);
        this.restartMusic();
        this.returnToStart();

        this.player.showTitle(Title.title(Component.empty(),
            Lang.text(this.player, "twod.title.attempt",
                "%attempt%", String.valueOf(this.attempt)), ATTEMPT_TIMES));
    }

    /**
     * ВОЗВРАТ ИГРОКА К СТАРТУ ПОСЛЕ ПРОИГРЫША.
     * <p>
     * Носители после смерти отправляются к началу уровня одним прыжком. Если игрок
     * умер далеко, этот прыжок оказывается длиннее дальности слежения за сущностями:
     * клиент теряет носителя из виду и остаётся стоять на месте, хотя на сервере
     * забег уже идёт с начала. Именно поэтому "далеко от старта - не тепает".
     * <p>
     * Поэтому на большом расстоянии игрока переносим отдельно и заново сажаем на
     * камеру следующим тиком - клиент успевает получить сущность заново.
     */
    private void returnToStart() {
        if (this.cameraSeat == null || !this.cameraSeat.isValid()) return;

        Location camera = this.cameraLocation(this.position.getY());
        camera.setYaw(this.cameraYaw);
        camera.setPitch(TwoDTuning.CAMERA_PITCH);

        // Сначала переставляем сами носители, чтобы сцена уже стояла на месте.
        TwoDEntityUtils.moveRaw(this.cameraSeat,
            camera.getX(), camera.getY() - this.cameraMountOffset, camera.getZ(),
            this.cameraYaw, TwoDTuning.CAMERA_PITCH);

        if (this.cubeSeat != null && this.cubeSeat.isValid()) {
            TwoDEntityUtils.moveRaw(this.cubeSeat,
                this.position.getX(), this.position.getY() - this.cubeMountOffset,
                this.position.getZ(), this.cameraYaw, 0f);
        }

        try {
            if (this.player.getWorld() != this.world) return;
            // Рядом - клиент доедет сам, дёргать посадку незачем.
            if (this.player.getLocation().distanceSquared(camera) < 64.0D) return;

            this.remountGraceTicks = 5;
            this.player.teleport(camera);

            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                if (!this.active || !this.player.isOnline()) return;
                if (this.cameraSeat == null || !this.cameraSeat.isValid()) return;

                if (this.spectatorCamera) {
                    this.player.setSpectatorTarget(this.cameraSeat);
                } else {
                    this.cameraSeat.addPassenger(this.player);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    // ==================== ОТОБРАЖЕНИЕ ====================

    private void updateEntities() {
        if (this.cubeSeat == null) return;
        if (this.cube == null) {
            // Кубика сейчас нет (пауза после проигрыша), но камеру двигать надо.
            this.updateCamera();
            return;
        }

        // ЗАМЕР СМЕЩЕНИЯ ПОСАДКИ - С ЗАДЕРЖКОЙ.
        //
        // Сервер подтягивает пассажира к носителю в своём тике, поэтому сразу после
        // посадки позиция кубика ещё старая и замер даёт мусор. Ровно из-за этого
        // раньше и появлялась щель под кубиком. Ждём несколько тиков, замеряем один
        // раз и больше не трогаем: смещение зависит только от типа носителя.
        if (this.offsetsDirty) {
            if (this.offsetMeasureAt == 0) {
                this.offsetMeasureAt = this.ticks + OFFSET_MEASURE_DELAY_TICKS;
            } else if (this.ticks >= this.offsetMeasureAt) {
                this.cubeMountOffset = this.cube.isStandalone() ? 0.0D : safeOffset(
                    this.cube.getEntity().getLocation().getY() - this.cubeSeat.getLocation().getY());
                if (this.cameraSeat != null) {
                    this.cameraMountOffset =
                        safeOffset(this.player.getLocation().getY() - this.cameraSeat.getLocation().getY());
                }
                this.offsetsDirty = false;
                this.offsetMeasureAt = 0;
            }
        }

        double targetCubeY = this.cube.toEntityY(this.position.getY()) + TwoDTuning.CUBE_Y_OFFSET;
        this.tickCubeRotation();

        float yaw = this.cameraYaw + TwoDTuning.CUBE_YAW_OFFSET;

        if (this.cube.isStandalone()) {
            TwoDEntityUtils.moveRaw(this.cube.getEntity(),
                this.position.getX(), targetCubeY, this.position.getZ(), yaw, 0f);
        } else {
            // Двигаем носителя, кубик приезжает пассажиром. Из-за этого его высота
            // отличается от высоты носителя на смещение посадки - его и вычитаем.
            TwoDEntityUtils.moveRaw(this.cubeSeat,
                this.position.getX(), targetCubeY - this.cubeMountOffset, this.position.getZ(), yaw, 0f);
        }

        if (this.boat != null && this.cube.isStandalone()) {
            TwoDEntityUtils.moveRaw(this.boat,
                this.position.getX(),
                this.position.getY() + TwoDTuning.BOAT_Y_OFFSET,
                this.position.getZ(), yaw, 0f);
        }

        this.updateCamera();
    }

    private void updateCamera() {
        if (this.cameraSeat == null) return;

        double desiredEyeY = this.position.getY() + TwoDTuning.CAMERA_EYE_HEIGHT;
        if (!this.cameraInitialized) {
            this.cameraY = desiredEyeY;
            this.cameraInitialized = true;
        } else {
            double smooth = Math.max(0.02D, Math.min(1.0D, TwoDTuning.CAMERA_SMOOTH));
            this.cameraY += (desiredEyeY - this.cameraY) * smooth;
        }

        Location camera = this.cameraLocation(this.cameraY - TwoDTuning.CAMERA_EYE_HEIGHT);

        if (this.spectatorCamera) {
            // Наблюдатель смотрит из ГЛАЗ арморстенда, поэтому ставим стенд так,
            // чтобы его глаза оказались на нужной высоте.
            TwoDEntityUtils.moveRaw(this.cameraSeat,
                camera.getX(), this.cameraY - this.cameraEyeOffset, camera.getZ(),
                this.cameraYaw, TwoDTuning.CAMERA_PITCH);
            return;
        }

        TwoDEntityUtils.moveRaw(this.cameraSeat,
            camera.getX(), camera.getY() - this.cameraMountOffset, camera.getZ(),
            this.cameraYaw, TwoDTuning.CAMERA_PITCH);

        if (!TwoDTuning.LOCK_CAMERA || this.editorTest) return;
        if (this.ticks % Math.max(1, TwoDTuning.CAMERA_LOCK_PERIOD) != 0) return;

        // Поправляем угол только когда игрок реально отвернулся, плюс раз в секунду
        // на всякий случай. Отправка каждый тик заставляла камеру дрожать: клиент
        // и сервер непрерывно спорили об угле.
        boolean turned = this.input.consumeRotationDirty(this.player);
        if (TwoDTuning.CAMERA_LOCK_ON_CHANGE && !turned && this.ticks % 20 != 0) return;

        // Точка, на которую смотрит клиент, ставится ОЧЕНЬ далеко.
        // Угол он считает от своей позиции, а она всегда чуть отличается от серверной;
        // на близкой точке эта разница даёт перекос вбок, на далёкой она исчезает.
        double distance = TwoDTuning.LOOK_TARGET_DISTANCE;
        double eyeY = this.cameraY;

        TwoDEntityUtils.lockRotation(this.player,
            camera.getX() + this.cameraDirection.getX() * distance,
            eyeY - Math.tan(Math.toRadians(TwoDTuning.CAMERA_PITCH)) * distance,
            camera.getZ() + this.cameraDirection.getZ() * distance,
            this.cameraYaw, TwoDTuning.CAMERA_PITCH);
    }

    private static double safeOffset(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > 3.0D) return 0.0D;
        return value;
    }

    @NonNull
    private Location cameraLocation(double cubeBottomY) {
        double x = this.position.getX()
            + this.side.getX() * TwoDTuning.CAMERA_DISTANCE
            + this.forward.getX() * TwoDTuning.CAMERA_LEAD;
        double z = this.position.getZ()
            + this.side.getZ() * TwoDTuning.CAMERA_DISTANCE
            + this.forward.getZ() * TwoDTuning.CAMERA_LEAD;

        // Ноги игрока ниже глаз: сажаем камеру так, чтобы глаза были на нужной высоте.
        double y = cubeBottomY + TwoDTuning.CAMERA_EYE_HEIGHT - 1.62D;
        return new Location(this.world, x, y, z, this.cameraYaw, TwoDTuning.CAMERA_PITCH);
    }

    // ==================== СЛЕД ВЕТРА В ПОЛЁТЕ ====================

    private static final Particle WIND_PARTICLE = resolveParticle("GUST", "CLOUD");
    private static final Particle WIND_SMALL_PARTICLE = resolveParticle("SMALL_GUST", "CLOUD", "SMOKE_NORMAL");
    private static final Particle FINISH_SPARK_PARTICLE = resolveParticle("FIREWORK", "FIREWORKS_SPARK", "CRIT");

    @Nullable
    private static Particle resolveParticle(@NonNull String... names) {
        for (String name : names) {
            try {
                return Particle.valueOf(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * Кораблик тянет за собой ветер. Это не украшение: по следу видно, куда кубик
     * летел последние полсекунды, и насколько сильно его тянет вниз.
     */
    private static final Particle BLOCK_TRAIL_PARTICLE = resolveParticle("BLOCK", "BLOCK_CRACK", "BLOCK_DUST");
    private static Object trailBlockData = null;
    private static boolean trailBlockDataResolved = false;

    @Nullable
    private static Object trailBlockData() {
        if (!trailBlockDataResolved) {
            trailBlockDataResolved = true;
            try {
                Material material = Material.matchMaterial("YELLOW_STAINED_GLASS_PANE");
                trailBlockData = material == null ? null : material.createBlockData();
            } catch (Throwable t) {
                trailBlockData = null;
            }
        }
        return trailBlockData;
    }

    /**
     * След на полу за кубиком. Осколки стекла мелкие и не заслоняют геометрию,
     * в отличие от облаков: те годятся только для кораблика.
     */
    private void tickGroundTrail() {
        if (BLOCK_TRAIL_PARTICLE == null) return;
        // В режиме кубика след идёт только по земле, в полёте - всегда: там он
        // показывает траекторию кораблика вдобавок к ветру.
        if (this.mode == Mode.CUBE && !this.onGround) return;

        Object data = trailBlockData();
        if (data == null) return;

        try {
            double behind = TwoDTuning.CUBE_HALF + 0.15D;
            double height = this.mode == Mode.FLY ? 0.35D : 0.10D;

            Location at = new Location(this.world,
                this.position.getX() - this.forward.getX() * behind,
                this.position.getY() + height,
                this.position.getZ() - this.forward.getZ() * behind);

            double spread = TwoDTuning.TRAIL_SPREAD;
            this.player.spawnParticle(BLOCK_TRAIL_PARTICLE, at,
                Math.max(1, TwoDTuning.TRAIL_AMOUNT), spread, spread * 0.6D, spread, 0.0D, data);
        } catch (Throwable ignored) {
        }
    }

    private void tickFlyTrail() {
        if (this.mode != Mode.FLY) return;
        if (this.ticks % 2 != 0) return;

        try {
            double behind = 0.55D;
            Location at = new Location(this.world,
                this.position.getX() - this.forward.getX() * behind,
                this.position.getY() + 0.45D,
                this.position.getZ() - this.forward.getZ() * behind);

            Particle particle = this.ticks % 8 == 0 ? WIND_PARTICLE : WIND_SMALL_PARTICLE;
            if (particle == null) return;
            this.player.spawnParticle(particle, at, 1, 0.08D, 0.08D, 0.08D, 0.0D);
        } catch (Throwable ignored) {
        }
    }

    // ==================== МОНЕТКИ ====================

    private void tickCoins() {
        java.util.List<Vector> coins;
        try {
            coins = this.level.getLevelSettings().getGameSettings().getTwoDSettings().getCoins();
        } catch (Throwable t) {
            return;
        }
        if (coins.isEmpty()) return;

        double radius = TwoDLevelSettings.COIN_PICKUP_RADIUS;
        double centerY = this.position.getY() + 0.5D;

        // Кубик проходит за тик почти треть блока, поэтому монетку проверяем не по
        // точке, а по отрезку "где был - где стал": иначе на скорости её можно
        // проскочить насквозь и ничего не заметить.
        // Тут нужна только длина шага, а не пересчёт множителя: speedPerTick()
        // при вызове с земли обновляет замороженный множитель, и лишний вызов
        // сбивал бы дугу прыжка.
        double travelled = this.levelSpeed() / 20.0D * this.speedFactor;

        for (int i = 0; i < coins.size(); i++) {
            if (this.collectedCoins.contains(i)) continue;

            Vector coin = coins.get(i);
            double dy = coin.getY() + 0.25D - centerY;
            if (Math.abs(dy) > radius + 0.5D) continue;

            double dx = coin.getX() - this.position.getX();
            double dz = coin.getZ() - this.position.getZ();

            // Проекция на ось движения: сколько монетка не доехала до кубика.
            double along = dx * this.forward.getX() + dz * this.forward.getZ();
            double lateral = dx * this.side.getX() + dz * this.side.getZ();

            if (Math.abs(lateral) > radius) continue;
            if (along > radius || along < -(radius + travelled)) continue;

            this.collectCoin(i, coin);
        }
    }

    private void collectCoin(int index, @NonNull Vector coin) {
        this.collectedCoins.add(index);
        this.coinsCollected++;

        try {
            // Монетку именно УБИРАЕМ, а не прячем лично у игрока: скрытие сущности
            // есть не на всех версиях и молча ничего не делает, из-за чего монетка
            // оставалась на месте и выглядела неподобранной. Все монетки уровня
            // возвращаются на место при следующей попытке.
            Entity entity = TwoDCoins.getEntity(this.level, index);
            if (entity != null) {
                TwoDEntityUtils.hideEntity(this.plugin, this.player, entity);
                entity.remove();
            }
        } catch (Throwable ignored) {
        }

        try {
            Location at = coin.toLocation(this.world);
            this.player.playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.6f);
            this.player.spawnParticle(Particle.END_ROD, at, 14, 0.2D, 0.2D, 0.2D, 0.02D);
        } catch (Throwable ignored) {
        }
    }

    private void restoreCoins() {
        if (this.collectedCoins.isEmpty()) return;
        this.collectedCoins.clear();

        // Пересоздаём весь набор: собранные монетки были удалены, а не спрятаны.
        try {
            TwoDCoins.refresh(this.plugin, this.level, this.editorTest);
        } catch (Throwable ignored) {
        }
    }

    // ==================== ФИНИШ ====================

    private void complete() {
        if (this.finishTicks > 0) return;
        this.finishTicks = FINISH_ANIMATION_TICKS;
        this.stopMusic();
        try {
            this.player.playSound(this.player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.2f);
        } catch (Throwable ignored) {
        }
    }

    /**
     * АНИМАЦИЯ ФИНИША.
     * <p>
     * Кубик затягивает воронкой: спираль из end_rod сжимается к его центру, частицы
     * летят внутрь, звук ползёт вверх по тону. Считается это одной формулой на тик,
     * поэтому сервер даже не замечает.
     */
    private void tickFinishAnimation() {
        double progress = 1.0D - (this.finishTicks / (double) FINISH_ANIMATION_TICKS);

        // ЗАСВЕТ ПРЯМО ПЕРЕД ЛИЦОМ.
        //
        // Частицы ставятся вплотную к глазам игрока и с каждым тиком их всё больше,
        // пока не закроют экран целиком. Это дёшево (никакой геометрии, просто
        // случайные точки в маленьком объёме) и выглядит как затягивание в свет.
        try {
            Location eye = this.player.getEyeLocation();
            double distance = 0.55D;
            Location front = eye.clone().add(
                this.cameraDirection.getX() * distance, 0.0D, this.cameraDirection.getZ() * distance);

            int amount = 6 + (int) (progress * 40.0D);
            double spread = 0.35D + progress * 0.9D;

            this.player.spawnParticle(Particle.END_ROD, front, amount,
                spread, spread, spread, 0.0D);

            if (this.finishTicks % 6 == 0) {
                float pitch = 0.8f + (float) progress * 1.2f;
                this.player.playSound(this.player.getLocation(),
                    Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, pitch);
            }
            if (FINISH_SPARK_PARTICLE != null && this.finishTicks % 4 == 0) {
                this.player.spawnParticle(FINISH_SPARK_PARTICLE, front, 2, 0.2D, 0.2D, 0.2D, 0.01D);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * ЗАПИСЬ ПРОХОЖДЕНИЯ В ОБЩУЮ СТАТИСТИКУ.
     * <p>
     * 2D-уровень конечен, поэтому он попадает в ту же таблицу и тот же рейтинг, что
     * и обычные - никакой отдельной системы. Показатели переведены в привычные:
     * монетка это попадание (300), пропущенная монетка - слабое попадание (50),
     * смерть - промах. Точность считается той же формулой, что и везде, поэтому
     * оценка, PP и место в топе получаются честными сами собой.
     */
    private void submitRun(long timeMillis) {
        if (this.editorTest) return;

        try {
            ru.sortix.parkourbeat.levels.settings.GameSettings settings =
                this.level.getLevelSettings().getGameSettings();

            // Уровень без сложности не прошёл модерацию: рейтинг по нему не пишем,
            // ровно как и в обычном режиме.
            if (settings.getDifficulty() == ru.sortix.parkourbeat.levels.LevelDifficulty.N_A) return;

            int totalCoins = settings.getTwoDSettings().getCoinsAmount();
            int collected = Math.min(this.coinsCollected, totalCoins);
            int missedCoins = Math.max(0, totalCoins - collected);
            int deaths = Math.max(0, this.attempt - 1);

            // Та же формула, что и на табло: игрок видит ровно то число, которое
            // потом уйдёт в статистику. На финише все непройденные монетки уже
            // позади, поэтому пропущенные считаются от общего количества.
            double accuracy = attemptAccuracy(deaths, missedCoins);

            ru.sortix.parkourbeat.rating.AccuracyGrade grade =
                ru.sortix.parkourbeat.rating.AccuracyGrade.evaluate(
                    collected, 0, missedCoins, deaths, accuracy);

            // Очки: база за прохождение, монетки и штраф за смерти. Множителя нет -
            // модификаторы в 2D не применяются.
            int rawScore = 1000 + collected * 500 - deaths * 100;
            rawScore = Math.max(100, rawScore);

            ru.sortix.parkourbeat.stats.RunResult run =
                ru.sortix.parkourbeat.stats.RunResult.builder()
                    .playerId(this.player.getUniqueId())
                    .playerName(this.player.getName())
                    .levelId(this.level.getUniqueId())
                    .levelName(settings.getDisplayNameLegacy(false))
                    .difficulty(settings.getDifficulty())
                    .progressPercent(100.0D)
                    .completed(true)
                    .accuracy(accuracy)
                    .grade(grade)
                    .score(rawScore)
                    .rawScore(rawScore)
                    .maxCombo(collected)
                    .count300(collected)
                    .count100(0)
                    .count50(missedCoins)
                    .missCount(deaths)
                    .multiplier(1.0D)
                    .timeMillis(timeMillis)
                    .timestamp(System.currentTimeMillis())
                    .build();

            this.plugin.get(ru.sortix.parkourbeat.rating.StatisticsManager.class).submitRun(run);
        } catch (Throwable t) {
            this.plugin.getLogger().warning("2D: не удалось записать прохождение: " + t);
        }
    }

    private void finishNow() {
        this.submitRun(System.currentTimeMillis() - this.attemptStartedAt);

        int totalCoins;
        try {
            totalCoins = this.level.getLevelSettings().getGameSettings().getTwoDSettings().getCoinsAmount();
        } catch (Throwable t) {
            totalCoins = 0;
        }

        String subtitle = totalCoins > 0
            ? Lang.raw(this.player, "twod.summary.coins",
            "%collected%", String.valueOf(this.coinsCollected),
            "%total%", String.valueOf(totalCoins))
            : Lang.raw(this.player, "twod.summary.attempts",
                "%attempt%", String.valueOf(this.attempt));

        try {
            this.player.playSound(this.player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            this.player.showTitle(Title.title(
                Lang.text(this.player, "game.title.completed"), PbText.of(subtitle),
                Title.Times.of(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500))));
        } catch (Throwable ignored) {
        }

        this.stop(true);
    }

    private double travelledDistance() {
        double dx = this.position.getX() - this.cubeSpawn.getX();
        double dz = this.position.getZ() - this.cubeSpawn.getZ();
        return dx * this.forward.getX() + dz * this.forward.getZ();
    }

    private double lineLength() {
        try {
            return this.level.getLevelSettings().getGameSettings().getTwoDSettings().getLineLength();
        } catch (Throwable t) {
            return TwoDLevelSettings.DEFAULT_LINE_LENGTH;
        }
    }

    private void createBossBar() {
        this.removeBossBar();
        try {
            if (this.level.getLevelSettings().getGameSettings().isHideBossBar()) return;

            ru.sortix.parkourbeat.levels.settings.LevelBossBarColor color =
                this.level.getLevelSettings().getGameSettings().getBossBarColor();

            this.bossBar = net.kyori.adventure.bossbar.BossBar.bossBar(
                Component.empty(), 0.0f, color.getBarColor(),
                net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS);
            this.player.showBossBar(this.bossBar);
        } catch (Throwable t) {
            this.bossBar = null;
        }
    }

    private void updateBossBar() {
        if (this.bossBar == null) return;
        try {
            float progress = this.getPassedProgress();

            ru.sortix.parkourbeat.levels.settings.LevelBossBarColor color =
                this.level.getLevelSettings().getGameSettings().getBossBarColor();

            Component name = Component.text(Math.round(progress * 100) + "%")
                .color(color.getTextColor())
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true);

            // Номер попытки прямо на полосе: в 2D он значит примерно то же, что
            // проценты - показывает, сколько раз уровень уже сопротивлялся.
            if (this.attempt > 1) {
                name = name.append(Component.text(Lang.raw(this.player, "twod.attempt_suffix",
                    "%attempt%", String.valueOf(this.attempt)))
                    .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false));
            }

            this.bossBar.color(color.getBarColor());
            this.bossBar.name(name);
            this.bossBar.progress(Math.max(0f, Math.min(1f, progress)));
        } catch (Throwable ignored) {
        }
    }

    private void removeBossBar() {
        if (this.bossBar == null) return;
        try {
            this.player.hideBossBar(this.bossBar);
        } catch (Throwable ignored) {
        }
        this.bossBar = null;
    }

    private void sendActionBar() {
        if (TwoDTuning.DEBUG) {
            this.player.sendActionBar(PbText.of(String.format(java.util.Locale.ROOT,
                Lang.raw(PlayerLang.of(this.player), "auto.two_d_game.send_action_bar.1"),
                this.onGround ? Lang.raw(PlayerLang.of(this.player), "auto.two_d_game.send_action_bar.2") : Lang.raw(PlayerLang.of(this.player), "auto.two_d_game.send_action_bar.3"),
                this.jumpArmed ? Lang.raw(PlayerLang.of(this.player), "auto.two_d_game.send_action_bar.4") : Lang.raw(PlayerLang.of(this.player), "auto.two_d_game.send_action_bar.5"),
                this.verticalSpeed,
                TwoDEntityUtils.getLastRotationMethod())));
            return;
        }
        this.player.sendActionBar(Lang.text(this.player, "twod.actionbar.quit"));
    }

    // ==================== МУЗЫКА ====================

    /**
     * Замедление ради узкого FOV: ходить игрок всё равно не может, его везёт
     * арморстенд, зато паркур становится видно гораздо лучше.
     */
    private void applySlowness() {
        if (TwoDTuning.SLOWNESS_LEVEL <= 0) return;
        PotionEffectType type = slownessType();
        if (type == null) return;
        try {
            this.player.addPotionEffect(new PotionEffect(
                type, 1_000_000, TwoDTuning.SLOWNESS_LEVEL - 1, false, false, false));
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static PotionEffectType slownessType() {
        for (String name : new String[]{"SLOWNESS", "SLOW"}) {
            try {
                PotionEffectType type = PotionEffectType.getByName(name);
                if (type != null) return type;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @Nullable
    private MusicPlatform platform() {
        try {
            if (this.level.getLevelSettings().getGameSettings().getMusicTrack() == null) return null;
            return this.plugin.get(MusicTracksManager.class).getPlatform();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Перезапуск трека делается В ДВА ТИКА.
     * <p>
     * Остановка и запуск в одном тике приводили к тому, что клиент получал команды
     * в неправильном порядке: после проигрыша музыка то не выключалась, то не
     * включалась обратно. Теперь сначала честно глушим, а включаем спустя пару тиков.
     */
    private void restartMusic() {
        this.stopMusic();
        if (this.platform() == null) return;
        this.musicStartDelay = 3;
    }

    private void tickMusic() {
        if (this.musicStartDelay < 0) return;
        this.musicStartDelay--;
        if (this.musicStartDelay != 0) return;
        this.musicStartDelay = -1;

        MusicPlatform platform = this.platform();
        if (platform == null) return;
        try {
            platform.disableRepeatMode(this.player);
            platform.startPlayingTrackFull(this.player);
        } catch (Throwable ignored) {
        }
    }

    private void stopMusic() {
        this.musicStartDelay = -1;
        MusicPlatform platform = this.platform();
        if (platform == null) return;
        try {
            platform.stopPlayingTrackFull(this.player);
        } catch (Throwable ignored) {
        }
    }

    // ==================== ЗАВЕРШЕНИЕ ====================

    /**
     * @param teleportBack вернуть игрока на спавн-лобби уровня
     */
    public void stop(boolean teleportBack) {
        if (!this.active) return;
        this.active = false;

        this.stopMusic();
        this.removeBossBar();
        this.restoreCoins();
        this.input.setRotationLocked(this.player, false);
        this.input.untrack(this.player);

        try {
            if (this.spectatorCamera && this.player.getSpectatorTarget() != null) {
                this.player.setSpectatorTarget(null);
            }
            if (this.player.isInsideVehicle()) this.player.leaveVehicle();
        } catch (Throwable ignored) {
        }
        this.despawnEntities();

        if (this.player.isOnline()) {
            try {
                this.player.removePotionEffect(PotionEffectType.INVISIBILITY);
                PotionEffectType slowness = slownessType();
                if (slowness != null) this.player.removePotionEffect(slowness);
                this.player.sendActionBar(Component.empty());
                this.player.setFallDistance(0f);
            } catch (Throwable ignored) {
            }

            if (this.editorTest && this.savedInventory != null) {
                try {
                    this.player.getInventory().setContents(this.savedInventory);
                } catch (Throwable ignored) {
                }
                this.savedInventory = null;
            }

            if (teleportBack) {
                // Возвращаем туда, откуда игрока забрали. Для строителя это его место
                // в редакторе, для игрока - спавн-лобби уровня.
                Location target = this.returnLocation != null
                    ? this.returnLocation
                    : this.level.getSpawn();

                // Телепорт делаем следующим тиком: в этом игрок ещё числится
                // пассажиром, и сервер такой телепорт просто отменит.
                Player player = this.player;
                try {
                    this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                        try {
                            ru.sortix.parkourbeat.world.TeleportUtils
                                .teleportAsync(this.plugin, player, target);
                        } catch (Throwable ignored) {
                        }
                    });
                } catch (Throwable ignored) {
                }
            }

            if (this.editorTest) {
                try {
                    this.player.setGameMode(this.savedGameMode == null ? GameMode.CREATIVE : this.savedGameMode);
                    this.player.setAllowFlight(this.savedAllowFlight);
                    this.player.setFlying(this.savedFlying);
                } catch (Throwable ignored) {
                }
            } else {
                try {
                    this.player.setGameMode(GameMode.ADVENTURE);
                    this.player.setAllowFlight(false);
                    this.player.setFlying(false);
                } catch (Throwable ignored) {
                }
            }

            if (this.spectatorCamera && this.player.getGameMode() == GameMode.SPECTATOR) {
                try {
                    this.player.setGameMode(this.editorTest && this.savedGameMode != null
                        ? this.savedGameMode : GameMode.ADVENTURE);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
