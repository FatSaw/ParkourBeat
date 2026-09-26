// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/levels/LevelsManager.java
package ru.sortix.parkourbeat.levels;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.item.editor.type.EditTrackPointsItem;
import ru.sortix.parkourbeat.levels.dao.LevelSettingDAO;
import ru.sortix.parkourbeat.levels.dao.files.FileLevelSettingDAO;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.levels.settings.LevelSettings;
import ru.sortix.parkourbeat.levels.settings.WorldSettings;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.utils.StringUtils;
import ru.sortix.parkourbeat.world.OutsideBlocksCleaner;
import ru.sortix.parkourbeat.world.WorldsManager;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import ru.sortix.parkourbeat.utils.text.PbText;

public class LevelsManager implements PluginManager {
    @Getter
    private final ParkourBeat plugin;

    private final WorldsManager worldsManager;

    @Getter
    private final LevelSettingsManager levelsSettings;

    private final AvailableLevelsCollection availableLevels;
    private final Map<UUID, Level> loadedLevelsById = new HashMap<>();
    private final Map<World, Level> loadedLevelsByWorld = new HashMap<>();
    private final Set<ParticleController> particleControllers = new HashSet<>();
    private final BukkitTask particlesRenderingTask;
    private final BukkitTask autoSaveTask;
    private int nextLevelNumber = 1;

    private static final long AUTOSAVE_PERIOD_TICKS = 20L * 15L;

    private final java.util.Set<UUID> changedWorlds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> lockedLevels = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public LevelsManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.worldsManager = plugin.get(WorldsManager.class);
        this.levelsSettings = new LevelSettingsManager(new FileLevelSettingDAO(this));
        this.availableLevels = new AvailableLevelsCollection(this.plugin.getLogger());
        this.loadAvailableLevelNames();

        this.particlesRenderingTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (ParticleController controller : this.particleControllers) {
                controller.tickParticles();
            }
        }, 0, 5);

        this.autoSaveTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
            this::saveEditedLevels, AUTOSAVE_PERIOD_TICKS, AUTOSAVE_PERIOD_TICKS);
    }

    public void markWorldChanged(@NonNull UUID levelId) {
        this.changedWorlds.add(levelId);
    }

    public boolean isLevelLocked(@NonNull UUID levelId) {
        return this.lockedLevels.contains(levelId);
    }

    public void lockLevel(@NonNull UUID levelId) {
        this.lockedLevels.add(levelId);
    }

    public void unlockLevel(@NonNull UUID levelId) {
        this.lockedLevels.remove(levelId);
    }

    public void markWorldChanged(@NonNull World world) {
        Level level = this.getLoadedLevel(world);
        if (level != null) this.changedWorlds.add(level.getUniqueId());
    }

    public void saveEditedLevels() {
        UUID worldToSave = null;

        for (Level level : new ArrayList<>(this.loadedLevelsById.values())) {
            if (!level.isEditing()) continue;

            try {
                this.saveLevelSettings(level.getUniqueId());
            } catch (Exception e) {
                this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Не удалось сохранить настройки уровня " + level.getUniqueId(), e);
            }

            if (worldToSave == null && this.changedWorlds.contains(level.getUniqueId())) {
                worldToSave = level.getUniqueId();
            }
        }

        if (worldToSave == null) return;
        this.changedWorlds.remove(worldToSave);

        Level level = this.loadedLevelsById.get(worldToSave);
        if (level == null) return;

        try {
            this.dropBlocksOutsideLevel(level);
            level.getWorld().save();
        } catch (Exception e) {
            this.changedWorlds.add(worldToSave);
            this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Не удалось автосохранить мир уровня " + worldToSave, e);
        }
    }

    public File getDefaultLevelDirectory(World.Environment env) {
        return this.getDefaultLevelDirectory(env, 1);
    }

    /**
     * @param chunkWidth ширина будущего уровня в чанках
     * @return папка шаблона; для широких уровней сначала ищется своя база,
     * а если её ещё не сохранили - берётся обычная
     */
    /**
     * @param twoD ищем базу для 2D-уровня
     */
    public File getDefaultLevelDirectory(World.Environment env, int chunkWidth, boolean twoD) {
        if (twoD) {
            if (chunkWidth >= 4) {
                File wide = new File(this.plugin.getDataFolder(),
                    "pb_default_level_2D_" + env.name() + "_4C");
                if (wide.isDirectory()) return wide;
            }
            File dir = new File(this.plugin.getDataFolder(), "pb_default_level_2D_" + env.name());
            if (dir.isDirectory()) return dir;
        }
        return this.getDefaultLevelDirectory(env, chunkWidth);
    }

    public File getDefaultLevelDirectory(World.Environment env, int chunkWidth) {
        if (chunkWidth >= 4) {
            File wide = new File(this.plugin.getDataFolder(),
                "pb_default_level_" + env.name() + "_4C");
            if (wide.isDirectory()) return wide;
        }
        File dir = new File(this.plugin.getDataFolder(), "pb_default_level_" + env.name());
        if (dir.isDirectory()) {
            return dir;
        }
        return new File(this.plugin.getDataFolder(), "pb_default_level");
    }

    private void loadAvailableLevelNames() {
        for (GameSettings gameSettings :
            this.levelsSettings.getLevelSettingDAO().loadAllAvailableLevelGameSettingsSync()
        ) {
            this.availableLevels.add(gameSettings);
        }
        for (GameSettings gameSettings : this.availableLevels) {
            if (this.nextLevelNumber <= gameSettings.getUniqueNumber()) {
                this.nextLevelNumber = gameSettings.getUniqueNumber() + 1;
            }
        }
    }

    @NonNull
    public Collection<GameSettings> getAvailableLevelsSettings() {
        return Collections.unmodifiableCollection(Lists.newArrayList(this.availableLevels.iterator()));
    }

    @NonNull
    public CompletableFuture<Level> createLevel(
        @NonNull World.Environment environment, @NonNull UUID ownerId, @NonNull String ownerName, @NonNull String levelName) {
        return this.createLevel(environment, ownerId, ownerName, levelName, 1);
    }

    /**
     * @param chunkWidth ширина уровня в чанках (1 или 4)
     */
    @NonNull
    public CompletableFuture<Level> createLevel(
        @NonNull World.Environment environment, @NonNull UUID ownerId, @NonNull String ownerName,
        @NonNull String levelName, int chunkWidth) {
        return this.createLevel(environment, ownerId, ownerName, levelName, chunkWidth,
            ru.sortix.parkourbeat.twod.LevelMode.THREE_D);
    }

    /**
     * @param levelMode обычный 3D-уровень или 2D-уровень
     */
    @NonNull
    public CompletableFuture<Level> createLevel(
        @NonNull World.Environment environment, @NonNull UUID ownerId, @NonNull String ownerName,
        @NonNull String levelName, int chunkWidth,
        @NonNull ru.sortix.parkourbeat.twod.LevelMode levelMode) {
        return this.createLevel(environment, ownerId, ownerName, levelName, chunkWidth, levelMode,
            SkyMode.FULL);
    }

    /**
     * @param skyMode полное небо или его половина; влияет только на высоту площадки и
     *                только у режимов, которые создаются пустым миром
     */
    @NonNull
    public CompletableFuture<Level> createLevel(
        @NonNull World.Environment environment, @NonNull UUID ownerId, @NonNull String ownerName,
        @NonNull String levelName, int chunkWidth,
        @NonNull ru.sortix.parkourbeat.twod.LevelMode levelMode,
        @NonNull SkyMode skyMode) {
        boolean twoD = levelMode.isTwoD();

        // У шаблонных режимов небо и так полное - выбор к ним не применяется вовсе.
        SkyMode sky = SkyMode.isAvailableFor(levelMode) ? skyMode : SkyMode.FULL;

        // ВЫСОТА ПЛОЩАДКИ - ЭТО И ЕСТЬ ВЫБОР НЕБА.
        //
        // У «Копателя» полное небо по-прежнему берёт высоту из его собственных настроек:
        // там она правится админом и означает ещё и запас под потолочный декор. Половина
        // неба - общая низкая высота, своего смысла у неё в «Копателе» нет.
        int platformY = levelMode.isDigger() && sky.isFull()
            ? ru.sortix.parkourbeat.digger.DiggerTuning.SPAWN_Y
            : sky.getPlatformY();
        // Дуэльная карта бывает только широкой: на узкой полосе двум трассам не разойтись.
        int levelChunkWidth = levelMode.isDuel()
            ? ru.sortix.parkourbeat.duel.DuelManager.DUEL_CHUNK_WIDTH
            : chunkWidth;
        CompletableFuture<Level> result = new CompletableFuture<>();
        UUID levelId = this.getNextLevelId();
        WorldCreator worldCreator = this.levelsSettings.getLevelSettingDAO().newWorldCreator(levelId);
        worldCreator.generator(this.worldsManager.getEmptyGenerator());
        worldCreator.environment(environment);
        worldCreator.generateStructures(false);

        // ТИП МИРА - ЭТО И ЕСТЬ ФЛАГ ПЛОСКОСТИ В level.dat.
        //
        // На блоки он не влияет ничем: генерацию всё равно делает наш пустой генератор,
        // поставленный строкой выше. Зато сервер запишет в level.dat нужный тип, а
        // оттуда флаг уедет клиенту - и небо у мира, созданного пустым, станет таким,
        // каким его выбрали, без всякой подмены пакетов.
        //
        // Работает только там, где level.dat создаётся с нуля. Шаблонные режимы
        // копируют чужой файл поверх, и их небо держится уже подменой пакета.
        if (SkyMode.isAvailableFor(levelMode)) {
            try {
                worldCreator.type(skyMode.isFull()
                    ? org.bukkit.WorldType.FLAT
                    : org.bukkit.WorldType.NORMAL);
            } catch (Throwable ignored) {
            }
        }

        // 360-УРОВЕНЬ СОЗДАЁТСЯ БЕЗ ШАБЛОНА.
        //
        // Там нужна пустота, и копировать ради этого построенный шаблон, чтобы потом
        // стирать его чанк за чанком, бессмысленно: мир просто создаётся пустым.
        CompletableFuture<World> worldFuture;
        if (levelMode.isThreeSixty() || levelMode.isDigger()) {
            worldFuture = this.worldsManager.createEmptyWorld(worldCreator);
        } else {
            File defaultLevelDirectory = this.getDefaultLevelDirectory(environment, levelChunkWidth, twoD);
            if (!defaultLevelDirectory.isDirectory()) {
                this.plugin
                    .getLogger()
                    .severe("Default level directory not found: " + defaultLevelDirectory.getAbsolutePath());
                result.complete(null);
                return result;
            }
            worldFuture = this.worldsManager.createWorldFromCustomDirectory(worldCreator, defaultLevelDirectory);
        }

        worldFuture
            .thenAccept(world -> {
                if (world == null) {
                    result.complete(null);
                    return;
                }
                try {
                    this.prepareLevelWorld(world, true);

                    // «Копатель» создаётся вообще пустым: один блок камня на Y=100,
                    // чтобы строителю было куда встать. Тоннель он тянет сам.
                    if (levelMode.isDigger()) {
                        ru.sortix.parkourbeat.digger.DiggerWorldTemplate.prepare(world, platformY);
                    }

                    int uniqueNumber = this.nextLevelNumber++;
                    Component displayName = PbText.vanilla(levelName);

                    LevelSettings levelSettings = LevelSettings.create(
                        this.plugin,
                        world,
                        environment,
                        levelId,
                        uniqueNumber,
                        displayName,
                        ownerId,
                        ownerName
                    );

                    // Режим выставляется ПЕРВЫМ: от него зависят и допустимая ширина,
                    // и значения по умолчанию (например, урон за отпущенный бег).
                    // Ширина - до первой записи настроек, иначе область редактирования
                    // посчитается по значению по умолчанию.
                    levelSettings.getGameSettings().setLevelMode(levelMode);
                    levelSettings.getGameSettings().applyModeDefaults();
                    levelSettings.getGameSettings().setChunkWidth(levelChunkWidth);
                    levelSettings.getGameSettings().setSkyMode(
                        SkyMode.isAvailableFor(levelMode) ? sky : null);

                    // Для широких уровней берём их собственный шаблон, если он сохранён:
                    // старт, финиш и спавн у него свои, от узкой базы они не подходят.
                    WorldSettings defaultSettings =
                        Settings.getDefaultSettings(environment, levelChunkWidth, twoD);

                    // ТОЛЬКО СТАРТ, БЕЗ ШАБЛОННОГО ПУТИ.
                    //
                    // Раньше новый уровень получал сразу пару "старт-финиш" из шаблона.
                    // Финиш при этом стоял в заранее заданном месте, а строитель вёл
                    // трассу от старта куда ему нужно - и, как правило, проходил мимо
                    // шаблонного финиша или дальше него. В списке точек порядок - это
                    // порядок прохождения, поэтому уровень оказывался с финишем ПЕРЕД
                    // стартом: сначала конец, потом начало. Пройти такое нельзя.
                    //
                    // Теперь ставится ровно одна точка - стартовая. Финишем становится
                    // последняя точка пути из частиц, то есть та, которую строитель
                    // поставил последней; раньше старта она не окажется никогда.
                    // Сам старт при необходимости переносится через меню редактора
                    // (кнопка над "Точкой спавна").
                    org.bukkit.Location templateStart =
                        defaultSettings.getStartWaypoint().toLocation(world);

                    levelSettings.getWorldSettings().getWaypoints().clear();

                    // У ДУЭЛЬНОЙ КАРТЫ НЕТ НИ ОДНОЙ ГОТОВОЙ ТОЧКИ.
                    //
                    // Трасс две, у каждой свой старт и свой финиш - концы её собственного
                    // пути. Шаблонная точка на общем месте только мешала: новые точки
                    // вставлялись «от неё», и обе трассы кривились к одному началу.
                    // Строитель ставит обе линии с нуля, куда считает нужным.
                    // У дуэльной карты точек нет вовсе (у каждой стороны свой старт),
                    // у 360-уровня - тоже: забег там начинается с первого движения, а
                    // единственная нужная точка - финиш, и её ставит строитель.
                    if (!levelMode.isDuel() && !levelMode.isThreeSixty()) {
                        // У «Копателя» стартовая точка поднимается вместе со спавном:
                        // иначе она остаётся на шаблонной высоте, а по ней считаются
                        // и границы уровня, и нижняя высота мира.
                        if (levelMode.isDigger()) {
                            templateStart.setY(platformY + 1.0D);

                            // Стартовая линия уходит на блок вперёд, чтобы спавн оказался
                            // ПОЗАДИ неё - именно этого требует проверка точки старта.
                            int ahead = ru.sortix.parkourbeat.digger
                                .DiggerWorldTemplate.START_AHEAD_BLOCKS;
                            switch (levelSettings.getDirectionChecker().direction()) {
                                case POSITIVE_X -> templateStart.add(ahead, 0.0D, 0.0D);
                                case NEGATIVE_X -> templateStart.add(-ahead, 0.0D, 0.0D);
                                case POSITIVE_Z -> templateStart.add(0.0D, 0.0D, ahead);
                                case NEGATIVE_Z -> templateStart.add(0.0D, 0.0D, -ahead);
                            }
                        }
                        levelSettings.getWorldSettings().getWaypoints().add(new Waypoint(
                            templateStart, 0, EditTrackPointsItem.DEFAULT_PARTICLES_COLOR));
                    }

                    // Спавн тоже берётся из шаблона: без этого новый уровень появлялся
                    // со спавном по умолчанию, а не там, где его поставил админ.
                    // Мир у скопированной точки чужой - переставляем на новый, иначе
                    // телепорт уедет в мир-шаблон.
                    org.bukkit.Location templateSpawn = defaultSettings.getSpawn().clone();
                    templateSpawn.setWorld(world);

                    // «КОПАТЕЛЬ» ЖИВЁТ НА СВОЕЙ ВЫСОТЕ И ШАБЛОН ЕМУ НЕ УКАЗ.
                    //
                    // Шаблон pb_default_level рассчитан на обычный уровень и ставит спавн
                    // где-то на Y=28. Для тоннеля это не просто «низко»: на такой высоте
                    // небо у игрока чёрное, потому что клиент красит его по высоте, а
                    // строить вверх остаётся меньше сотни блоков - ламповые стены и
                    // потолочный декор упираются в границу мира посреди работы.
                    //
                    // DiggerWorldTemplate.prepare() кладёт опору и ставит спавн МИРА на
                    // нужной высоте, но строкой ниже сюда приезжал шаблонный спавн УРОВНЯ
                    // и затирал его. Играют же именно по спавну уровня - поэтому карта и
                    // продолжала создаваться внизу, сколько бы spawn_y ни правили.
                    if (levelMode.isDigger()) {
                        templateSpawn = ru.sortix.parkourbeat.digger.DiggerWorldTemplate
                            .builderSpawn(world, levelSettings.getDirectionChecker(), platformY);
                    }

                    // 360-УРОВЕНЬ ТОЖЕ СТОИТ ТАМ, ГДЕ ЕМУ СКАЗАЛИ.
                    //
                    // Он создаётся пустым миром, без шаблонного level.dat, - значит
                    // клиент не считает его плоским и рисует тёмную нижнюю половину неба
                    // всюду ниже горизонта. Шаблонный спавн лежит где-то на тридцатой
                    // высоте, и до сих пор 360-уровни получали половину неба НЕ ПО ВЫБОРУ,
                    // а просто потому, что спавн достался им от обычного уровня. Правило
                    // «высоко - значит полное небо» работало только у «Копателя».
                    if (levelMode.isThreeSixty()) {
                        templateSpawn.setY(platformY + 1.0D);
                    }

                    levelSettings.getWorldSettings().setSpawn(templateSpawn);

                    // Список точек заменили, но границы уровня (старт, финиш и нижняя
                    // высота мира) считаются из него отдельным вызовом. Без него в полях
                    // оставались значения от прежней базы: путь из частиц рисовался уже
                    // новый, а маркеры старта и финиша висели на старом месте - и
                    // перескакивали только после первого клика по частице, потому что
                    // редактор как раз и дёргает updateBorders().
                    levelSettings.getWorldSettings().updateBorders();

                    levelSettings.recalculateWaypoints(world);
                    levelSettings.updateParticleLocations();

                    world.setSpawnLocation(levelSettings.getWorldSettings().getSpawn());

                    // 360-уровень начинается с пустоты и каменной платформы под ногами,
                    // а не с готовой трассы шаблона: строить там будут во все стороны,
                    // и шаблонный пол только мешал бы.
                    if (levelMode.isThreeSixty()) {
                        prepareThreeSixtyWorld(world, levelSettings);
                    }

                    Level level = new Level(levelSettings, world);
                    level.setEditing(true);

                    this.availableLevels.add(level.getLevelSettings().getGameSettings());
                    this.levelsSettings.addLevelSettings(levelId, levelSettings);
                    this.loadedLevelsById.put(levelId, level);
                    this.loadedLevelsByWorld.put(world, level);
                    this.rememberSky(level);
                    result.complete(level);
                } catch (Exception e) {
                    this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Unable to create level", e);
                    result.complete(null);
                }
            });
        return result;
    }

    /**
     * Готовит мир 360-уровня: пустота и каменная платформа со спавном на ней.
     * <p>
     * Мир всё равно копируется из обычного шаблона - в нём лежат файлы настроек, без
     * которых уровня не существует. Поэтому построенную трассу шаблона мы стираем, а
     * взамен кладём площадку под ноги.
     * <p>
     * Стираются только те чанки, которые в шаблоне действительно есть: несуществующие
     * отсеиваются по {@code isChunkGenerated} и не загружаются, иначе создание уровня
     * упиралось бы в генерацию тысяч пустых чанков.
     */
    private static void prepareThreeSixtyWorld(@NonNull World world, @NonNull LevelSettings settings) {
        org.bukkit.Location spawn = settings.getWorldSettings().getSpawn();

        int chunks = Math.max(1, settings.getGameSettings().getChunkWidth());
        int size = chunks * 16;
        int floorY = spawn.getBlockY() - 1;

        // Платформа кладётся РОВНО ПО ПЛОЩАДКЕ УРОВНЯ - по тем же чанкам, по которым
        // считаются границы (см. Level.threeSixtyArea). Раньше она была фиксированной
        // полосой в 17 блоков вдоль, и на нескольких чанках оставался голый пол только
        // под спавном, а остальная площадка висела над пустотой.
        int minX = ((spawn.getBlockX() >> 4) - (chunks - 1) / 2) * 16;
        int minZ = ((spawn.getBlockZ() >> 4) - (chunks - 1) / 2) * 16;

        for (int x = minX; x < minX + size; x++) {
            for (int z = minZ; z < minZ + size; z++) {
                world.getBlockAt(x, floorY, z).setType(org.bukkit.Material.STONE, false);
            }
        }
    }

    /**
     * КОПИЯ УРОВНЯ.
     * <p>
     * Копируется папка мира целиком, вместе с настройками уровня, которые лежат внутри
     * неё - поэтому в копию попадает ровно всё: блоки, путь из частиц (оба, если карта
     * дуэльная), световое шоу, маркеры, порталы, зоны падения, барьеры, чекпоинты,
     * трек, название. Пересобирать это по полю за раз было бы бессмысленно и опасно:
     * любое забытое поле - молча потерянная часть уровня.
     * <p>
     * НЕ копируется то, что уровень заработал, а не построил: статус модерации, оценка
     * сложности, оценки игроков и рекорды. Копия появляется приватной и незарейтингованной,
     * как только что созданный уровень.
     * <p>
     * Копия разрешена ровно одна и только с оригинала - см. {@link GameSettings#canBeCopied()}.
     *
     * @return настройки копии или null, если скопировать не удалось
     */
    @NonNull
    public CompletableFuture<GameSettings> copyLevelAsync(@NonNull Level sourceLevel,
                                                          @NonNull UUID ownerId,
                                                          @NonNull String ownerName) {
        CompletableFuture<GameSettings> result = new CompletableFuture<>();
        GameSettings source = sourceLevel.getLevelSettings().getGameSettings();

        if (!source.canBeCopied() || this.isLevelLocked(source.getUniqueId())) {
            result.complete(null);
            return result;
        }

        // Копируем то, что видит строитель, а не то, что лежало на диске час назад.
        this.saveLevelSettingsAndBlocks(sourceLevel);

        UUID newLevelId = this.getNextLevelId();
        World.Environment environment =
            sourceLevel.getLevelSettings().getWorldSettings().getEnvironment();

        WorldCreator worldCreator = this.levelsSettings.getLevelSettingDAO().newWorldCreator(newLevelId);
        worldCreator.generator(this.worldsManager.getEmptyGenerator());
        worldCreator.environment(environment);
        worldCreator.generateStructures(false);

        File sourceDirectory = sourceLevel.getWorld().getWorldFolder();

        this.worldsManager
            .createWorldFromCustomDirectory(worldCreator, sourceDirectory, true)
            .thenAccept(world -> {
                if (world == null) {
                    result.complete(null);
                    return;
                }
                try {
                    this.prepareLevelWorld(world, true);

                    // Настройки читаются из СКОПИРОВАННЫХ файлов, а не переписываются
                    // из объекта в памяти: чтение с диска - это тот же путь, которым
                    // грузится любой уровень, и оно гарантированно ничего не забудет.
                    LevelSettings template =
                        this.levelsSettings.getLevelSettingDAO().loadLevelSettings(newLevelId, null);
                    if (template == null) {
                        throw new IllegalStateException("Unable to read copied level settings");
                    }

                    GameSettings copy = new GameSettings(
                        newLevelId,
                        null,
                        this.nextLevelNumber++,
                        ownerId,
                        ownerName,
                        copiedDisplayName(source),
                        System.currentTimeMillis(),
                        ModerationStatus.NOT_MODERATED
                    );
                    applyCopiedSettings(template.getGameSettings(), copy);
                    copy.setCopiedFrom(source.getUniqueId());

                    LevelSettings levelSettings = new LevelSettings(
                        this.plugin, world, template.getWorldSettings(), copy);
                    levelSettings.getWorldSettings().updateBorders();
                    levelSettings.recalculateWaypoints(world);
                    levelSettings.updateParticleLocations();
                    if (copy.isDuelLevel()) levelSettings.updateSecondParticleLocations();

                    world.setSpawnLocation(levelSettings.getWorldSettings().getSpawn());

                    Level level = new Level(levelSettings, world);
                    level.setEditing(true);

                    this.availableLevels.add(copy);
                    // addLevelSettings сразу пишет настройки на диск, затирая
                    // скопированный game_settings.yml оригинала.
                    this.levelsSettings.addLevelSettings(newLevelId, levelSettings);
                    this.loadedLevelsById.put(newLevelId, level);
                    this.loadedLevelsByWorld.put(world, level);
                    this.rememberSky(level);

                    // Оригинал помечается только после успеха: сорвавшаяся копия не
                    // должна съедать единственную попытку.
                    source.setCopyMade(true);
                    this.saveGameSettings(source);

                    result.complete(copy);
                } catch (Exception e) {
                    this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Unable to copy level " + source.getUniqueId(), e);
                    result.complete(null);
                }
            });
        return result;
    }

    /**
     * Название копии - это название оригинала с пометкой в конце.
     * <p>
     * Без пометки в списке оказывались бы два уровня с одинаковым именем, и понять,
     * какой из них какой, можно было бы только по номеру.
     */
    @NonNull
    private static Component copiedDisplayName(@NonNull GameSettings source) {
        return source.getDisplayName().append(Component.text(" (копия)"));
    }

    /**
     * Переносит в копию всё, что относится к устройству уровня, и НЕ переносит того,
     * что уровень заработал.
     */
    private static void applyCopiedSettings(@NonNull GameSettings from, @NonNull GameSettings to) {
        // Режим - первым: от него зависят допустимая ширина и значения по умолчанию.
        to.setLevelMode(from.getLevelMode());
        to.applyModeDefaults();
        to.setChunkWidth(from.getChunkWidth());

        // Небо переезжает вместе с картой: папка мира копируется целиком, значит у копии
        // та же высота площадки и тот же level.dat, - и выглядеть она обязана так же.
        to.setSkyMode(from.getSkyMode());
        to.setSprintDamage(from.isSprintDamage());
        to.setTwoDSettings(from.getTwoDSettings());

        to.setCustomPhysicsEnabled(from.isCustomPhysicsEnabled());
        to.setMusicTrack(from.getMusicTrack());
        to.setUseTrackPieces(from.isUseTrackPieces());
        to.setBossBarColor(from.getBossBarColor());
        to.setHideBossBar(from.isHideBossBar());
        to.setBorderPushStrength(from.getBorderPushStrength());
        to.setCheckpointAttempts(from.getCheckpointAttempts());
        // Жёсткость геймплея - это настройка трассы, а не заработанная оценка,
        // поэтому она переезжает. А вот рейтинговая сложность и голоса игроков - нет.
        to.setDifficultyMultiplier(from.getDifficultyMultiplier());

        for (java.util.Map.Entry<UUID, String> coEditor : from.getCoEditors().entrySet()) {
            if (to.addCoEditor(coEditor.getKey(), coEditor.getValue())
                && from.isTrusted(coEditor.getKey())) {
                to.setTrusted(coEditor.getKey(), true);
            }
        }

        // Нарезка трека принадлежит плейлисту оригинала: копия перережет свой,
        // когда строитель этого захочет.
        to.clearSliceResult();

        // Ресурспак уровня лежит не в мире, а на стороне сервиса текстур и привязан
        // к id оригинала. Скопировать его отсюда нельзя, поэтому копия начинает без него.
        to.setCustomTextures(false);
        to.setTextureVersionRange(null);
    }

    @NonNull
    private UUID getNextLevelId() {
        UUID result;
        do {
            result = UUID.randomUUID();
        } while (this.availableLevels.byUniqueId(result) != null);
        return result;
    }

    @NonNull
    public CompletableFuture<Level> loadLevel(@NonNull UUID levelId, @Nullable GameSettings gameSettings) {
        if (gameSettings == null) {
            gameSettings = this.availableLevels.byUniqueId(levelId);
        }
        CompletableFuture<Level> result = new CompletableFuture<>();

        Level level = getLoadedLevel(levelId);
        if (level != null) {
            result.complete(level);
            return result;
        }

        if (this.lockedLevels.contains(levelId)) {
            result.complete(null);
            return result;
        }

        WorldCreator worldCreator = this.levelsSettings.getLevelSettingDAO().newWorldCreator(levelId);
        worldCreator.generator(this.worldsManager.getEmptyGenerator());
        World.Environment env = this.levelsSettings.getLevelSettingDAO().loadLevelEnvironment(levelId);
        worldCreator.environment(env);
        worldCreator.generateStructures(false);

        GameSettings finalGameSettings = gameSettings;
        this.worldsManager
            .createWorldFromDefaultContainer(worldCreator, this.worldsManager.getSyncExecutor())
            .thenAccept(world -> {
                if (world == null) {
                    result.complete(null);
                    return;
                }
                try {
                    this.prepareLevelWorld(world, false);

                    LevelSettings levelSettings = this.levelsSettings.loadLevelSettings(levelId, finalGameSettings);
                    Level loadedLevel = new Level(levelSettings, world);
                    this.loadedLevelsById.put(levelId, loadedLevel);
                    this.loadedLevelsByWorld.put(world, loadedLevel);
                    this.rememberSky(loadedLevel);

                    result.complete(loadedLevel);
                } catch (Exception e) {
                    this.plugin
                        .getLogger()
                        .log(java.util.logging.Level.SEVERE, "Не удалось загрузить уровень " + levelId, e);
                    result.complete(null);
                }
            });
        return result;
    }

    @NonNull
    public CompletableFuture<Boolean> deleteLevelAsync(@NonNull GameSettings settings) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        UUID levelId = settings.getUniqueId();
        this.unloadLevelAsync(levelId, false).thenAccept(success -> {
            if (!success) {
                result.complete(false);
                return;
            }


            this.availableLevels.remove(settings);
            this.levelsSettings.getLevelSettingDAO().deleteLevelWorldAndSettings(levelId);
            try {
                this.plugin.get(ru.sortix.parkourbeat.activity.EditorSessionsManager.class)
                    .removeLevel(levelId);
            } catch (Exception ignored) {
            }
            result.complete(true);
        });
        return result;
    }

    @NonNull
    public CompletableFuture<Boolean> unloadLevelAsync(@NonNull UUID levelId, boolean saveChunks) {
        Level level = this.getLoadedLevel(levelId);
        if (level == null) return CompletableFuture.completedFuture(true);

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        LevelSettingDAO dao = this.levelsSettings.getLevelSettingDAO();

        ru.sortix.parkourbeat.twod.TwoDCoins.despawn(level);
        CompletableFuture<Boolean> worldUnloading;
        World world = dao.getBukkitWorld(levelId);
        if (world == null) {
            worldUnloading = CompletableFuture.completedFuture(true);
        } else {
            worldUnloading = new CompletableFuture<>();
            this.plugin
                .get(WorldsManager.class)
                .unloadBukkitWorld(
                    world,
                    saveChunks,
                    level::isChunkInside,
                    Settings.getLobbySpawn(),
                    true
                )
                .thenAccept(worldUnloading::complete);
        }

        worldUnloading.thenAccept(success -> {
            if (!success) {
                result.complete(false);
                return;
            }
            this.levelsSettings.unloadLevelSettings(levelId);
            this.loadedLevelsById.remove(levelId);
            this.loadedLevelsByWorld.remove(world);
            try {
                this.plugin.get(ru.sortix.parkourbeat.player.SkyFlatnessManager.class)
                    .forget(world);
            } catch (Throwable ignored) {
            }
            result.complete(true);
        });

        return result;
    }

    @NonNull
    public CompletableFuture<Boolean> upgradeDataAsync(
        @NonNull UUID levelId, @Nullable Consumer<LevelSettings> updater) {
        boolean unload = this.getLoadedLevel(levelId) == null;
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        this.loadLevel(levelId, null).thenAccept(level -> {
            LevelSettings settings;
            try {
                settings = this.levelsSettings.loadLevelSettings(
                    levelId, level.getLevelSettings().getGameSettings());
            } catch (Exception e) {
                this.plugin
                    .getLogger()
                    .log(
                        java.util.logging.Level.SEVERE,
                        "Не удалось загрузить данные уровня " + levelId + " для конвертации",
                        e);
                result.complete(false);
                return;
            }
            boolean success = true;
            if (updater != null) {
                try {
                    updater.accept(settings);
                } catch (Exception e) {
                    this.plugin
                        .getLogger()
                        .log(
                            java.util.logging.Level.SEVERE,
                            "Не удалось произвести конвертацию уровня " + levelId,
                            e);
                    success = false;
                }
            }
            try {
                this.levelsSettings.saveLevelSettings(levelId);
            } catch (Exception e) {
                this.plugin
                    .getLogger()
                    .log(
                        java.util.logging.Level.SEVERE,
                        "Не удалось сохранить данные уровня " + levelId + " после конвертации",
                        e);
                success = false;
            }
            if (unload) {
                boolean finalSuccess = success;
                this.unloadLevelAsync(levelId, false).thenAccept(success2 -> result.complete(finalSuccess && success2));
            } else {
                result.complete(success);
            }
        });
        return result;
    }

    @NonNull
    public Collection<Level> getLoadedLevels() {
        return new ArrayList<>(this.loadedLevelsById.values());
    }

    public void saveLevelSettings(@NonNull UUID levelId) {
        this.levelsSettings.saveLevelSettings(levelId);
    }

    public void saveGameSettings(@NonNull GameSettings gameSettings) {
        this.levelsSettings.saveGameSettings(gameSettings);
    }

    /**
     * Убирает всё, что построено за границей уровня, чтобы оно не попало в сохранение.
     * <p>
     * Вызывать строго ПЕРЕД {@code world.save()}. Раньше здесь стояла отгрузка таких чанков
     * без сохранения, но чанк, в котором стоит сам строитель, отгрузить невозможно - и его
     * содержимое всё равно уходило на диск. Теперь блоки удаляются, а очищенный чанк
     * сохраняется намеренно: так стирается и то, что успело записаться раньше.
     */
    private void dropBlocksOutsideLevel(@NonNull Level level) {
        for (org.bukkit.Chunk chunk : level.getWorld().getLoadedChunks()) {
            if (level.isChunkInside(chunk)) continue;
            // Пустой чанк за границей записывать на диск незачем - отгружаем без сохранения.
            if (!OutsideBlocksCleaner.clearChunk(chunk)) chunk.unload(false);
        }
    }

    public void saveLevelSettingsAndBlocks(@NonNull Level level) {
        this.saveLevelSettings(level.getUniqueId());
        try {
            World world = level.getWorld();
            this.dropBlocksOutsideLevel(level);

            world.save();
        } catch (Exception e) {
            this.plugin
                .getLogger()
                .log(
                    java.util.logging.Level.SEVERE,
                    "Unable to save world " + level.getWorld().getName(),
                    e);
        }
    }

    @Nullable
    public Level getLoadedLevel(@NonNull UUID levelId) {
        return this.loadedLevelsById.get(levelId);
    }

    @Nullable
    public Level getLoadedLevel(@NonNull World world) {
        return this.loadedLevelsByWorld.get(world);
    }

    @Nullable
    public GameSettings getAvailableLevelSettings(@NonNull UUID levelId) {
        return this.availableLevels.byUniqueId(levelId);
    }

    @NonNull
    public List<String> getUniqueLevelNames(@NonNull String levelNamePrefix, @Nullable CommandSender owner, boolean bypassForAdmins) {
        levelNamePrefix = levelNamePrefix.toLowerCase();

        List<String> result = new ArrayList<>();

        String uniqueName;
        if (owner == null) {
            for (GameSettings gameSettings : this.availableLevels.withUniqueNames()) {
                uniqueName = gameSettings.getUniqueName();
                if (uniqueName == null || !uniqueName.startsWith(levelNamePrefix)) continue;
                result.add(uniqueName);
            }
        } else {
            for (GameSettings gameSettings : this.availableLevels.withUniqueNames()) {
                if (!gameSettings.canEdit(owner, bypassForAdmins, false)) continue;
                uniqueName = gameSettings.getUniqueName();
                if (uniqueName == null || !uniqueName.startsWith(levelNamePrefix)) continue;
                result.add(uniqueName);
            }
        }

        return result;
    }

    /**
     * Рассказать менеджеру неба, плоским ли считать этот мир.
     * <p>
     * Пакет о входе в мир уходит из сетевого потока, а хранилище уровней правит
     * основной: спрашивать уровень прямо из пакета - это гонка ради одного логического
     * значения. Поэтому значение кладётся сюда один раз, при загрузке уровня.
     */
    private void rememberSky(@NonNull Level level) {
        try {
            this.plugin.get(ru.sortix.parkourbeat.player.SkyFlatnessManager.class)
                .remember(level.getWorld(), level.getLevelSettings().getGameSettings().getSkyMode());
        } catch (Throwable ignored) {
        }
    }

    public void prepareLevelWorld(@NonNull World world, boolean updateGameRules) {
        world.setKeepSpawnInMemory(false);
        world.setAutoSave(false);

        setBooleanGameRule(world, "SPECTATORS_GENERATE_CHUNKS", true);

        if (!updateGameRules) return;

        setBooleanGameRule(world, "ANNOUNCE_ADVANCEMENTS", false);
        setBooleanGameRule(world, "DISABLE_ELYTRA_MOVEMENT_CHECK", true);
        setBooleanGameRule(world, "DO_DAYLIGHT_CYCLE", false);
        setBooleanGameRule(world, "DO_ENTITY_DROPS", false);
        setBooleanGameRule(world, "DO_FIRE_TICK", false);
        setBooleanGameRule(world, "DO_LIMITED_CRAFTING", true);
        setBooleanGameRule(world, "DO_MOB_LOOT", false);
        setBooleanGameRule(world, "DO_MOB_SPAWNING", false);
        setBooleanGameRule(world, "DO_TILE_DROPS", false);
        setBooleanGameRule(world, "DO_WEATHER_CYCLE", false);
        setBooleanGameRule(world, "KEEP_INVENTORY", true);
        setBooleanGameRule(world, "LOG_ADMIN_COMMANDS", true);
        setBooleanGameRule(world, "MOB_GRIEFING", false);
        setBooleanGameRule(world, "NATURAL_REGENERATION", false);
        setBooleanGameRule(world, "REDUCED_DEBUG_INFO", false);
        setBooleanGameRule(world, "SHOW_DEATH_MESSAGES", false);
        setBooleanGameRule(world, "DISABLE_RAIDS", true);
        setBooleanGameRule(world, "DO_INSOMNIA", false);
        setBooleanGameRule(world, "DO_IMMEDIATE_RESPAWN", true);
        setBooleanGameRule(world, "DROWNING_DAMAGE", false);
        setBooleanGameRule(world, "FALL_DAMAGE", false);
        setBooleanGameRule(world, "FIRE_DAMAGE", false);
        setBooleanGameRule(world, "DO_PATROL_SPAWNING", false);
        setBooleanGameRule(world, "DO_TRADER_SPAWNING", false);
        setBooleanGameRule(world, "FORGIVE_DEAD_PLAYERS", true);
        setBooleanGameRule(world, "UNIVERSAL_ANGER", false);
        setIntegerGameRule(world, "RANDOM_TICK_SPEED", 0);
        setIntegerGameRule(world, "SPAWN_RADIUS", 0);
    }

    @Nullable
    private static GameRule<?> findGameRule(@NonNull String name) {
        try {
            Object value = GameRule.class.getField(name).get(null);
            if (value instanceof GameRule) return (GameRule<?>) value;
        } catch (Throwable ignored) {
        }
        return GameRule.getByName(name);
    }

    private void setBooleanGameRule(@NonNull World world, @NonNull String name, boolean newValue) {
        GameRule<?> rule = findGameRule(name);
        if (rule == null || rule.getType() != Boolean.class) return;
        world.setGameRule((GameRule<Boolean>) rule, newValue);
    }

    private void setIntegerGameRule(@NonNull World world, @NonNull String name, int newValue) {
        GameRule<?> rule = findGameRule(name);
        if (rule == null || rule.getType() != Integer.class) return;
        world.setGameRule((GameRule<Integer>) rule, newValue);
    }

    @Nullable
    public GameSettings findLevel(@NonNull String levelUniqueNameOrIdOrNumber) {
        UUID levelId = StringUtils.parseUUID(levelUniqueNameOrIdOrNumber);
        if (levelId != null) {
            return this.availableLevels.byUniqueId(levelId);
        }
        try {
            return this.availableLevels.byUniqueNumber(Integer.parseInt(levelUniqueNameOrIdOrNumber));
        } catch (NumberFormatException e) {
            return this.availableLevels.byUniqueName(levelUniqueNameOrIdOrNumber);
        }
    }

    @Override
    public void disable() {
        if (!this.particlesRenderingTask.isCancelled()) {
            this.particlesRenderingTask.cancel();
        }
        if (!this.autoSaveTask.isCancelled()) {
            this.autoSaveTask.cancel();
        }

        Location spawn = Settings.getLobbySpawn();
        for (Map.Entry<World, Level> entry : this.loadedLevelsByWorld.entrySet()) {
            Level level = entry.getValue();
            if (!level.isEditing()) continue;

            World world = entry.getKey();

            this.levelsSettings.saveLevelSettings(level.getUniqueId());
            try {
                this.dropBlocksOutsideLevel(level);
                world.save();
            } catch (Exception e) {
                this.plugin.getLogger().log(
                    java.util.logging.Level.SEVERE,
                    "Unable to save world " + world.getName() + " on disable",
                    e);
            }

            level.setEditing(false);

            this.worldsManager.unloadBukkitWorld(
                world,
                true,
                level::isChunkInside,
                spawn,
                false
            );
        }
    }

    public void addParticleController(@NonNull ParticleController controller) {
        this.particleControllers.add(controller);
    }

    public void removeParticleController(@NonNull ParticleController controller) {
        this.particleControllers.remove(controller);
    }
}
