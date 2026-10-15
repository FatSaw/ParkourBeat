package ru.sortix.parkourbeat.world;

import lombok.NonNull;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Rotatable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.Waypoint;
import ru.sortix.parkourbeat.levels.settings.AutoDoor;
import ru.sortix.parkourbeat.levels.settings.Checkpoint;
import ru.sortix.parkourbeat.levels.settings.GlowingBarrier;
import ru.sortix.parkourbeat.levels.settings.HelperMarker;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.levels.settings.Portal;
import ru.sortix.parkourbeat.levels.settings.WorldSettings;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ПОДНЯТЬ МИР И РАЗВЕРНУТЬ ЕГО.
 * <p>
 * Задача выглядит как «сдвинуть блоки», но блоки здесь - меньшая половина дела.
 * Уровень - это ещё и спавн, путь из частиц обеих трасс, чекпоинты, порталы,
 * автодвери, светящиеся барьеры, ламповые стены, точки чудоэффектов и метки-помощники.
 * Забыть любую из них - значит получить карту, у которой постройка переехала, а
 * невидимая механика осталась на старом месте. Ищут такое потом неделями, поэтому
 * список ниже намеренно исчерпывающий, ровно как в {@link LevelShifter}.
 * <p>
 * РАБОТАЕТ С МИРОМ И НАСТРОЙКАМИ, А НЕ С ЗАГРУЖЕННЫМ УРОВНЕМ. Разница не косметическая.
 * У живого уровня есть граница области редактирования, а за ней стоит чистильщик
 * {@link OutsideBlocksCleaner}: он стирает всё, что появилось снаружи. Разворот же
 * ПЕРЕНОСИТ постройку на другую сторону от спавна - то есть ровно туда, куда уровню
 * строить нельзя. Постройка честно переезжала и тут же стиралась.
 * <p>
 * Поэтому переносится не уровень, а временный мир, поднятый из папки шаблона: у него
 * нет ни границ, ни чистильщика, ни игроков, ни редактора. См.
 * {@link ru.sortix.parkourbeat.commands.CommandTemplate}.
 * <p>
 * РАЗВОРОТ - ЭТО ПОВОРОТ НА 180° ВОКРУГ СПАВНА, а не отражение. Разница существенная:
 * отражение вывернуло бы лестницы и ступени наизнанку и поменяло местами право и лево,
 * а поворот оставляет постройку такой же, просто смотрящей в другую сторону. Уровень,
 * идущий на +X, после этого идёт на -X и остаётся в тех же чанках.
 * <p>
 * РАБОТА ИДЁТ В ТРИ ПРОХОДА, а не в один. Исходная и новая области при повороте
 * пересекаются: половина блоков переезжает туда, где ещё стоят другие блоки. Писать
 * «на ходу» здесь нельзя ни при каком порядке обхода - постройка съест сама себя.
 * Поэтому сперва всё читается в память, потом старое место очищается целиком, и только
 * потом кладётся новое.
 */
public class LevelTransformer {

    /** Сколько чанков читать за такт. Чтение снимка дешевле записи, поэтому их больше. */
    private static final int CHUNKS_PER_TICK = 4;

    /** Сколько блоков ставить за такт. */
    private static final int BLOCKS_PER_TICK = 12000;

    /**
     * Предел на размер уровня.
     * <p>
     * Не столько защита от памяти, сколько от недоразумения: если под команду случайно
     * попал не шаблон, а большая построенная карта, лучше сказать об этом вслух, чем
     * молча жевать её полминуты.
     */
    private static final int MAX_BLOCKS = 3_000_000;

    private LevelTransformer() {
    }

    // ==================== ПРЕОБРАЗОВАНИЕ КООРДИНАТ ====================

    /**
     * Само преобразование: подъём по Y и, возможно, поворот на 180° вокруг блока
     * {@code (pivotX, pivotZ)}.
     * <p>
     * Отдельным классом, потому что применяется к двум разным видам координат - целым
     * блочным и дробным точечным, - и ошибиться на полблока между ними проще всего.
     * Блок {@code x} переходит в {@code 2*pivot - x}, а точка внутри него - в
     * {@code 2*pivot + 1 - x}: центр блока обязан остаться центром блока.
     */
    public static final class Transform {
        private final boolean rotate;
        private final int deltaY;
        private final int pivotX;
        private final int pivotZ;

        public Transform(boolean rotate, int deltaY, int pivotX, int pivotZ) {
            this.rotate = rotate;
            this.deltaY = deltaY;
            this.pivotX = pivotX;
            this.pivotZ = pivotZ;
        }

        public int blockX(int x) {
            return this.rotate ? 2 * this.pivotX - x : x;
        }

        public int blockZ(int z) {
            return this.rotate ? 2 * this.pivotZ - z : z;
        }

        public int blockY(int y) {
            return y + this.deltaY;
        }

        public double pointX(double x) {
            return this.rotate ? 2 * this.pivotX + 1 - x : x;
        }

        public double pointZ(double z) {
            return this.rotate ? 2 * this.pivotZ + 1 - z : z;
        }

        public double pointY(double y) {
            return y + this.deltaY;
        }

        public float yaw(float yaw) {
            return this.rotate ? yaw + 180.0f : yaw;
        }

        public boolean isRotating() {
            return this.rotate;
        }

        @NonNull
        public Vector apply(@NonNull Vector vector) {
            return new Vector(
                this.pointX(vector.getX()),
                this.pointY(vector.getY()),
                this.pointZ(vector.getZ()));
        }

        public void applyInPlace(@NonNull Location location) {
            double x = this.pointX(location.getX());
            double y = this.pointY(location.getY());
            double z = this.pointZ(location.getZ());
            float yaw = this.yaw(location.getYaw());
            location.setX(x);
            location.setY(y);
            location.setZ(z);
            location.setYaw(yaw);
        }
    }

    // ==================== ЗАПУСК ====================

    /**
     * Перенести уровень целиком.
     *
     * @param deltaY   на сколько блоков поднять (отрицательное - опустить)
     * @param rotate   разворачивать ли на 180° вокруг спавна
     * @param progress строка хода работы; вызывается в основном потоке
     * @param onFinish {@code null} - успех, иначе причина отказа
     */
    public static void transformBlocksAsync(@NonNull ParkourBeat plugin,
                                            @NonNull World world,
                                            @NonNull Transform transform,
                                            @NonNull Consumer<String> progress,
                                            @NonNull Consumer<String> onFinish) {
        List<int[]> chunks = generatedChunks(world);
        if (chunks.isEmpty()) {
            onFinish.accept(null);
            return;
        }

        progress.accept("читаю " + chunks.size() + " чанков");
        collect(plugin, world, chunks, 0, new ArrayList<>(), transform, progress, onFinish);
    }

    // ==================== ПРОХОД 1: ЧТЕНИЕ ====================

    /** Один запомненный блок: где стоял, чем был и что в нём лежало. */
    private static final class Saved {
        final int x;
        final int y;
        final int z;
        final @NonNull BlockData data;
        @Nullable String[] signLines;
        @Nullable ItemStack[] contents;

        Saved(int x, int y, int z, @NonNull BlockData data) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
        }
    }

    private static void collect(@NonNull ParkourBeat plugin,
                                @NonNull World world,
                                @NonNull List<int[]> chunks,
                                int index,
                                @NonNull List<Saved> saved,
                                @NonNull Transform transform,
                                @NonNull Consumer<String> progress,
                                @NonNull Consumer<String> onFinish) {
        if (!plugin.isEnabled()) {
            onFinish.accept("плагин выключается");
            return;
        }

        if (index >= chunks.size()) {
            progress.accept("прочитано блоков: " + saved.size() + ", очищаю старое место...");
            clear(plugin, world, saved, 0, transform, progress, onFinish);
            return;
        }

        int end = Math.min(index + CHUNKS_PER_TICK, chunks.size());
        for (int i = index; i < end; i++) {
            int[] coords = chunks.get(i);
            try {
                readChunk(world, coords[0], coords[1], saved);
            } catch (Throwable t) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Не удалось прочитать чанк " + coords[0] + " " + coords[1], t);
            }
        }

        if (saved.size() > MAX_BLOCKS) {
            onFinish.accept("уровень слишком большой: больше " + MAX_BLOCKS
                + " блоков. Это точно шаблон?");
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () ->
            collect(plugin, world, chunks, end, saved, transform, progress, onFinish));
    }

    /**
     * Прочитать один чанк.
     * <p>
     * Читается СНИМОК, а не живые блоки: снимок снимается разом и дальше опрашивается
     * без обращения к миру, а это разница в разы на миллионах ячеек. Сверху колонка
     * обрезается по самому высокому блоку - в пустом мире уровня выше постройки нет
     * ничего, и перебирать оставшуюся сотню слоёв незачем.
     */
    private static void readChunk(@NonNull World world, int chunkX, int chunkZ,
                                  @NonNull List<Saved> saved) {
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        // Верхняя граница колонок нужна - по ней и обрезается перебор.
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(true, false, false);

        int minY = minHeight(world);

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int highest = snapshot.getHighestBlockYAt(localX, localZ);
                for (int y = minY; y <= highest; y++) {
                    BlockData data = snapshot.getBlockData(localX, y, localZ);
                    if (data.getMaterial() == Material.AIR) continue;

                    int worldX = (chunkX << 4) + localX;
                    int worldZ = (chunkZ << 4) + localZ;
                    Saved entry = new Saved(worldX, y, worldZ, data.clone());

                    // Таблички и сундуки одними блочными данными не описываются:
                    // надпись и содержимое живут отдельно, и потерять их - значит
                    // потерять часть уровня молча.
                    //
                    // Состояние спрашивается ТОЛЬКО у тех блоков, у которых оно бывает.
                    // getState() собирает объект на каждый вызов, и дёргать его на
                    // каждом камне постройки означало бы выбросить весь выигрыш от
                    // чтения снимком.
                    if (needsState(data.getMaterial())) {
                        BlockState state = world.getBlockAt(worldX, y, worldZ).getState();
                        if (state instanceof Sign sign) {
                            entry.signLines = sign.getLines().clone();
                        } else if (state instanceof Container container) {
                            entry.contents = container.getInventory().getContents().clone();
                        }
                    }

                    saved.add(entry);
                }
            }
        }
    }

    /** Блоки, у которых есть содержимое или надпись. */
    private static final java.util.Set<Material> STATEFUL = java.util.EnumSet.of(
        Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.HOPPER,
        Material.DISPENSER, Material.DROPPER, Material.FURNACE, Material.BLAST_FURNACE,
        Material.SMOKER, Material.BREWING_STAND, Material.LECTERN, Material.JUKEBOX);

    private static boolean needsState(@NonNull Material material) {
        if (STATEFUL.contains(material)) return true;
        String name = material.name();
        return name.endsWith("_SIGN") || name.endsWith("SHULKER_BOX");
    }

    // ==================== ПРОХОД 2: ОЧИСТКА ====================

    private static void clear(@NonNull ParkourBeat plugin,
                              @NonNull World world,
                              @NonNull List<Saved> saved,
                              int index,
                              @NonNull Transform transform,
                              @NonNull Consumer<String> progress,
                              @NonNull Consumer<String> onFinish) {
        if (!plugin.isEnabled()) {
            onFinish.accept("плагин выключается");
            return;
        }

        if (index >= saved.size()) {
            progress.accept("кладу блоки на новое место...");
            place(plugin, world, saved, 0, transform, progress, onFinish);
            return;
        }

        int end = Math.min(index + BLOCKS_PER_TICK, saved.size());
        for (int i = index; i < end; i++) {
            Saved entry = saved.get(i);
            Block block = world.getBlockAt(entry.x, entry.y, entry.z);

            // Содержимое вынимается перед сносом: иначе сундук выбросит вещи на пол,
            // и уровень после переноса окажется засыпан предметами.
            if (entry.contents != null) {
                try {
                    BlockState state = block.getState();
                    if (state instanceof Container container) container.getInventory().clear();
                } catch (Throwable ignored) {
                }
            }

            block.setType(Material.AIR, false);
        }

        plugin.getServer().getScheduler().runTask(plugin, () ->
            clear(plugin, world, saved, end, transform, progress, onFinish));
    }

    // ==================== ПРОХОД 3: УКЛАДКА ====================

    private static void place(@NonNull ParkourBeat plugin,
                              @NonNull World world,
                              @NonNull List<Saved> saved,
                              int index,
                              @NonNull Transform transform,
                              @NonNull Consumer<String> progress,
                              @NonNull Consumer<String> onFinish) {
        if (!plugin.isEnabled()) {
            onFinish.accept("плагин выключается");
            return;
        }

        if (index >= saved.size()) {
            onFinish.accept(null);
            return;
        }

        int minY = minHeight(world);
        int maxY = world.getMaxHeight() - 1;

        int end = Math.min(index + BLOCKS_PER_TICK, saved.size());
        for (int i = index; i < end; i++) {
            Saved entry = saved.get(i);

            int x = transform.blockX(entry.x);
            int y = transform.blockY(entry.y);
            int z = transform.blockZ(entry.z);
            if (y < minY || y > maxY) continue;

            Block target = world.getBlockAt(x, y, z);
            try {
                target.setBlockData(rotateData(entry.data, transform.isRotating()), false);
                restoreState(target, entry);
            } catch (Throwable t) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Не удалось поставить блок на " + x + " " + y + " " + z, t);
            }
        }

        plugin.getServer().getScheduler().runTask(plugin, () ->
            place(plugin, world, saved, end, transform, progress, onFinish));
    }

    private static void restoreState(@NonNull Block target, @NonNull Saved entry) {
        if (entry.signLines != null) {
            BlockState state = target.getState();
            if (state instanceof Sign sign) {
                for (int line = 0; line < entry.signLines.length && line < 4; line++) {
                    sign.setLine(line, entry.signLines[line]);
                }
                sign.update(true, false);
            }
            return;
        }

        if (entry.contents != null) {
            BlockState state = target.getState();
            if (state instanceof Container container) {
                container.getInventory().setContents(entry.contents);
                container.update(true, false);
            }
        }
    }

    // ==================== ПОВОРОТ БЛОЧНЫХ ДАННЫХ ====================

    /**
     * Развернуть сам блок вслед за уровнем.
     * <p>
     * Без этого поворот получается половинчатым: постройка стоит на новом месте, но все
     * лестницы, факелы, таблички и двери по-прежнему смотрят в старую сторону. Поворот
     * ровно на 180°, поэтому вся работа сводится к замене направления на противоположное.
     * <p>
     * ВЕРТИКАЛЬ НЕ ТРОГАЕТСЯ. Блок, смотрящий вверх, после поворота вокруг вертикальной
     * оси смотрит туда же - подменять его на «вниз» было бы уже отражением.
     */
    @NonNull
    private static BlockData rotateData(@NonNull BlockData data, boolean rotate) {
        if (!rotate) return data.clone();

        BlockData copy = data.clone();

        try {
            if (copy instanceof Rotatable rotatable) {
                // Таблички, баннеры и головы: шестнадцать положений, и у каждого есть
                // ровно противоположное.
                rotatable.setRotation(rotatable.getRotation().getOppositeFace());
            }

            if (copy instanceof Directional directional) {
                BlockFace facing = directional.getFacing();
                BlockFace opposite = facing.getOppositeFace();
                if (facing != BlockFace.UP && facing != BlockFace.DOWN
                    && directional.getFaces().contains(opposite)) {
                    directional.setFacing(opposite);
                }
            }

            if (copy instanceof MultipleFacing multiple) {
                // Заборы, стёкла и стены: набор сторон меняется целиком и сразу,
                // по одной их менять нельзя - север успел бы затереть юг.
                boolean north = multiple.hasFace(BlockFace.NORTH);
                boolean south = multiple.hasFace(BlockFace.SOUTH);
                boolean east = multiple.hasFace(BlockFace.EAST);
                boolean west = multiple.hasFace(BlockFace.WEST);

                setFaceIfAllowed(multiple, BlockFace.NORTH, south);
                setFaceIfAllowed(multiple, BlockFace.SOUTH, north);
                setFaceIfAllowed(multiple, BlockFace.EAST, west);
                setFaceIfAllowed(multiple, BlockFace.WEST, east);
            }

            if (copy instanceof Rail rail) {
                Rail.Shape shape = oppositeShape(rail.getShape());
                if (rail.getShapes().contains(shape)) rail.setShape(shape);
            }
        } catch (Throwable ignored) {
            // Блок с непредусмотренным набором свойств переезжает как есть: криво
            // повёрнутая ступенька - это мелочь, а падение посреди переноса - нет.
        }

        return copy;
    }

    private static void setFaceIfAllowed(@NonNull MultipleFacing facing,
                                         @NonNull BlockFace face, boolean value) {
        if (!facing.getAllowedFaces().contains(face)) return;
        facing.setFace(face, value);
    }

    @NonNull
    private static Rail.Shape oppositeShape(@NonNull Rail.Shape shape) {
        switch (shape) {
            case ASCENDING_EAST:
                return Rail.Shape.ASCENDING_WEST;
            case ASCENDING_WEST:
                return Rail.Shape.ASCENDING_EAST;
            case ASCENDING_NORTH:
                return Rail.Shape.ASCENDING_SOUTH;
            case ASCENDING_SOUTH:
                return Rail.Shape.ASCENDING_NORTH;
            case SOUTH_EAST:
                return Rail.Shape.NORTH_WEST;
            case NORTH_WEST:
                return Rail.Shape.SOUTH_EAST;
            case SOUTH_WEST:
                return Rail.Shape.NORTH_EAST;
            case NORTH_EAST:
                return Rail.Shape.SOUTH_WEST;
            default:
                // Прямые рельсы вдоль оси после разворота лежат вдоль той же оси.
                return shape;
        }
    }

    // ==================== НАСТРОЙКИ УРОВНЯ ====================

    /**
     * Перенести всё, что хранит координаты.
     * <p>
     * Список повторяет {@link LevelShifter#shiftSettings}, и повторяет намеренно: это
     * не дублирование кода, а один и тот же перечень мест, где уровень хранит точки.
     * Разъедутся они только вместе с самим форматом настроек, и тогда чинить придётся
     * оба места - что как раз и правильно.
     */
    public static void applySettings(@NonNull WorldSettings worldSettings,
                                     @NonNull Transform transform) {
        Location spawn = worldSettings.getSpawn();
        if (spawn != null) transform.applyInPlace(spawn);

        for (Waypoint waypoint : worldSettings.getWaypoints()) {
            transform.applyInPlace(waypoint.getLocation());
        }

        // Вторая трасса дуэльной карты едет вместе с первой.
        for (Waypoint waypoint : worldSettings.getSecondWaypoints()) {
            transform.applyInPlace(waypoint.getLocation());
        }

        List<GlowingBarrier> barriers = new ArrayList<>();
        for (GlowingBarrier barrier : worldSettings.getGlowingBarriers()) {
            barriers.add(new GlowingBarrier(
                transform.blockX(barrier.getX()),
                transform.blockY(barrier.getY()),
                transform.blockZ(barrier.getZ()),
                barrier.getColor(), barrier.getMode(), barrier.getPeek(), barrier.getExtension()));
        }
        worldSettings.setGlowingBarriers(barriers);

        LightShowSettings lightShow = worldSettings.getLightShow();

        for (Checkpoint checkpoint : lightShow.getCheckpoints()) {
            checkpoint.setPosition(transform.apply(checkpoint.getPosition()));
        }

        for (ru.sortix.parkourbeat.levels.wonder.WonderEffect effect : lightShow.getWonderEffects()) {
            Location fixed = effect.getFixedLocation();
            if (fixed == null) continue;
            Location moved = fixed.clone();
            transform.applyInPlace(moved);
            effect.setFixedLocation(moved);
        }

        for (ru.sortix.parkourbeat.levels.lamps.LampWall wall : lightShow.getLampWalls()) {
            wall.setCorners(
                transform.blockX(wall.getX1()), transform.blockY(wall.getY1()), transform.blockZ(wall.getZ1()),
                transform.blockX(wall.getX2()), transform.blockY(wall.getY2()), transform.blockZ(wall.getZ2()));
        }

        for (Portal portal : lightShow.getPortals()) {
            for (Portal.Side side : List.of(portal.getEntry(), portal.getExit())) {
                side.setPosition(transform.apply(side.getPosition()));
            }
        }

        for (AutoDoor door : lightShow.getAutoDoors()) {
            door.setPosition(
                transform.blockX(door.getBlockX()),
                transform.blockY(door.getBlockY()),
                transform.blockZ(door.getBlockZ()));
        }

        List<HelperMarker> markers = new ArrayList<>();
        for (HelperMarker marker : lightShow.getHelperMarkers()) {
            markers.add(new HelperMarker(transform.apply(marker.getPosition()), marker.getKind()));
        }
        lightShow.getHelperMarkers().clear();
        lightShow.getHelperMarkers().addAll(markers);

        // Границы, старт и финиш пересчитываются из точек пути и сами по себе не
        // переезжают: у них своя копия координат, снятая при последней правке.
        worldSettings.updateBorders();
    }

    // ==================== ЧАНКИ ====================

    private static int minHeight(@NonNull World world) {
        try {
            // getMinHeight() появился в 1.17: на 1.16 мир всегда начинается с нуля.
            return world.getMinHeight();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Какие чанки уровня вообще существуют.
     * <p>
     * Область редактирования тянется на десятки тысяч блоков, и перебирать её целиком
     * бессмысленно. Имена файлов региона дают КВАДРАТ в тридцать два чанка, внутри
     * которого что-то есть, а {@code isChunkGenerated} отсеивает из него пустые - без
     * этой проверки на каждый файл региона приходилось бы читать тысячу чанков, из
     * которых построена от силы дюжина.
     */
    @NonNull
    private static List<int[]> generatedChunks(@NonNull World world) {
        List<int[]> result = new ArrayList<>();

        for (Chunk chunk : world.getLoadedChunks()) {
            result.add(new int[]{chunk.getX(), chunk.getZ()});
        }

        File regionDir = new File(world.getWorldFolder(), regionFolder(world));
        File[] files = regionDir.listFiles();
        if (files == null) return result;

        for (File file : files) {
            String[] parts = file.getName().split("\\.");
            if (parts.length < 4 || !parts[0].equals("r")) continue;

            int regionX;
            int regionZ;
            try {
                regionX = Integer.parseInt(parts[1]);
                regionZ = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {
                continue;
            }

            for (int x = regionX * 32; x < regionX * 32 + 32; x++) {
                for (int z = regionZ * 32; z < regionZ * 32 + 32; z++) {
                    if (contains(result, x, z)) continue;
                    if (!world.isChunkGenerated(x, z)) continue;
                    result.add(new int[]{x, z});
                }
            }
        }

        return result;
    }

    private static boolean contains(@NonNull List<int[]> chunks, int x, int z) {
        for (int[] chunk : chunks) {
            if (chunk[0] == x && chunk[1] == z) return true;
        }
        return false;
    }

    @NonNull
    private static String regionFolder(@NonNull World world) {
        switch (world.getEnvironment()) {
            case NETHER:
                return "DIM-1/region";
            case THE_END:
                return "DIM1/region";
            default:
                return "region";
        }
    }
}
