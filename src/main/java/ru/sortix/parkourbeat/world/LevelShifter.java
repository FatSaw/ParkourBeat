// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/world/LevelShifter.java
package ru.sortix.parkourbeat.world;

import lombok.NonNull;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.Waypoint;
import ru.sortix.parkourbeat.levels.settings.AutoDoor;
import ru.sortix.parkourbeat.levels.settings.Checkpoint;
import ru.sortix.parkourbeat.levels.settings.GlowingBarrier;
import ru.sortix.parkourbeat.levels.settings.HelperMarker;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.levels.settings.Portal;
import ru.sortix.parkourbeat.levels.settings.WorldSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Сдвигает всё содержимое уровня по оси Z.
 * <p>
 * Нужно при смене ширины: четыре чанка - число ЧЁТНОЕ, центрального чанка у такой
 * площадки нет, её середина проходит по границе между вторым и третьим. Поэтому
 * построенную полосу приходится именно переносить, а не расширять границы вокруг неё:
 * иначе внешние края уровня перестают совпадать с границами чанков, и это сразу видно
 * по F3+G.
 * <p>
 * Блоки переносятся порциями по одному чанку за такт: длинный уровень - это миллионы
 * блоков, и делать это одним куском значит повесить сервер на несколько секунд.
 */
public class LevelShifter {

    /** Сколько чанков по X обрабатывается за один такт. */
    private static final int CHUNKS_PER_TICK = 2;

    /**
     * Переносит блоки и настройки уровня на {@code deltaZ} блоков.
     *
     * @param sourceMinZ левая граница переносимой полосы (включительно)
     * @param sourceMaxZ правая граница переносимой полосы (включительно)
     * @param onFinish   вызывается в основном потоке по завершении
     */
    public static void shiftAsync(@NonNull ParkourBeat plugin,
                                  @NonNull Level level,
                                  int sourceMinZ,
                                  int sourceMaxZ,
                                  int deltaZ,
                                  @NonNull Consumer<Boolean> onFinish) {
        if (deltaZ == 0) {
            onFinish.accept(true);
            return;
        }

        World world = level.getWorld();
        List<Integer> chunkXs = collectBuiltChunkX(world, sourceMinZ, sourceMaxZ);
        if (chunkXs.isEmpty()) {
            shiftSettings(level, deltaZ);
            onFinish.accept(true);
            return;
        }

        int minY = Math.max(getMinHeight(world), 0);
        int maxY = world.getMaxHeight() - 1;

        int minChunkZ_src = sourceMinZ >> 4;
        int maxChunkZ_src = sourceMaxZ >> 4;
        int minChunkZ_dst = (sourceMinZ + deltaZ) >> 4;
        int maxChunkZ_dst = (sourceMaxZ + deltaZ) >> 4;

        int minChunkZ = Math.min(minChunkZ_src, minChunkZ_dst);
        int maxChunkZ = Math.max(maxChunkZ_src, maxChunkZ_dst);

        processNextChunk(plugin, world, level, chunkXs, 0, minChunkZ, maxChunkZ, sourceMinZ, sourceMaxZ, deltaZ, minY, maxY, onFinish);
    }

    private static void processNextChunk(@NonNull ParkourBeat plugin,
                                         @NonNull World world,
                                         @NonNull Level level,
                                         @NonNull List<Integer> chunkXs,
                                         int startIndex,
                                         int minChunkZ,
                                         int maxChunkZ,
                                         int sourceMinZ,
                                         int sourceMaxZ,
                                         int deltaZ,
                                         int minY,
                                         int maxY,
                                         @NonNull Consumer<Boolean> onFinish) {
        if (!plugin.isEnabled()) {
            onFinish.accept(false);
            return;
        }

        if (startIndex >= chunkXs.size()) {
            shiftSettings(level, deltaZ);
            onFinish.accept(true);
            return;
        }

        int endIndex = Math.min(startIndex + CHUNKS_PER_TICK, chunkXs.size());

        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int i = startIndex; i < endIndex; i++) {
            int chunkX = chunkXs.get(i);
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                futures.add(getChunkAtAsync(world, chunkX, cz));
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenAccept(v -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (int i = startIndex; i < endIndex; i++) {
                    int chunkX = chunkXs.get(i);
                    try {
                        shiftChunkColumn(world, chunkX, sourceMinZ, sourceMaxZ, deltaZ, minY, maxY);
                    } catch (Throwable t) {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Не удалось перенести блоки уровня в чанке X=" + chunkX, t);
                    }
                }

                processNextChunk(plugin, world, level, chunkXs, endIndex, minChunkZ, maxChunkZ, sourceMinZ, sourceMaxZ, deltaZ, minY, maxY, onFinish);
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Ошибка при асинхронной загрузке чанков", ex);
            plugin.getServer().getScheduler().runTask(plugin, () -> onFinish.accept(false));
            return null;
        });
    }

    @NonNull
    private static CompletableFuture<Chunk> getChunkAtAsync(@NonNull World world, int x, int z) {
        CompletableFuture<Chunk> future = new CompletableFuture<>();
        world.getChunkAtAsync(x, z, true, future::complete);
        return future;
    }

    private static int getMinHeight(@NonNull World world) {
        try {
            // getMinHeight() появился только в 1.17: на 1.16 мир всегда начинается с нуля.
            return world.getMinHeight();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Ищет, по каким X вообще есть застроенные чанки.
     * <p>
     * Область редактирования тянется на десятки тысяч блоков по X, и перебирать её всю
     * бессмысленно. Сгенерированные чанки - ровно то, что игрок трогал.
     */
    @NonNull
    private static List<Integer> collectBuiltChunkX(@NonNull World world, int minZ, int maxZ) {
        List<Integer> result = new ArrayList<>();

        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);

        for (Chunk chunk : world.getLoadedChunks()) {
            if (chunk.getZ() < minChunkZ || chunk.getZ() > maxChunkZ) continue;
            if (!result.contains(chunk.getX())) result.add(chunk.getX());
        }

        // Чанки могли быть выгружены: пробегаем по файлам региона, а не только по памяти.
        java.io.File regionDir = new java.io.File(world.getWorldFolder(), "region");
        java.io.File[] files = regionDir.listFiles();
        if (files != null) {
            for (java.io.File file : files) {
                String[] parts = file.getName().split("\\.");
                if (parts.length < 4 || !parts[0].equals("r")) continue;
                try {
                    int regionX = Integer.parseInt(parts[1]);
                    int regionZ = Integer.parseInt(parts[2]);
                    if (regionZ < Math.floorDiv(minChunkZ, 32)
                        || regionZ > Math.floorDiv(maxChunkZ, 32)) continue;

                    for (int chunkX = regionX * 32; chunkX < regionX * 32 + 32; chunkX++) {
                        if (!result.contains(chunkX)) result.add(chunkX);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        result.sort(deltaZOrder());
        return result;
    }

    /**
     * Порядок обхода по X не важен - переносим вдоль Z, а полосы по X независимы.
     */
    @NonNull
    private static java.util.Comparator<Integer> deltaZOrder() {
        return java.util.Comparator.naturalOrder();
    }

    /**
     * Переносит одну полосу шириной в чанк по X.
     * <p>
     * Исходная и целевая полосы не пересекаются (сдвиг больше ширины полосы), поэтому
     * порядок обхода Z значения не имеет и промежуточный буфер не нужен.
     */
    private static void shiftChunkColumn(@NonNull World world, int chunkX,
                                         int minZ, int maxZ, int deltaZ,
                                         int minY, int maxY) {
        int minX = chunkX * 16;
        int maxX = minX + 15;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block source = world.getBlockAt(x, y, z);
                    Block target = world.getBlockAt(x, y, z + deltaZ);

                    BlockData data = source.getBlockData();
                    boolean empty = source.getType() == Material.AIR;

                    if (!empty) {
                        BlockState sourceState = source.getState();
                        target.setBlockData(data, false);
                        copyContents(sourceState, target);
                    } else if (target.getType() != Material.AIR) {
                        target.setType(Material.AIR, false);
                    }
                }
            }
        }

        // Освобождаем исходную полосу только после того, как перенесли её целиком:
        // если что-то упадёт на середине, старая постройка ещё будет на месте.
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block source = world.getBlockAt(x, y, z);
                    if (source.getType() != Material.AIR) source.setType(Material.AIR, false);
                }
            }
        }
    }

    /**
     * Переносит содержимое блоков-контейнеров и текст табличек: одних блочных данных
     * для них мало, а терять надписи на уровне обидно.
     */
    private static void copyContents(@NonNull BlockState sourceState, @NonNull Block target) {
        try {
            if (sourceState instanceof Container sourceContainer) {
                BlockState targetState = target.getState();
                if (targetState instanceof Container targetContainer) {
                    targetContainer.getInventory().setContents(
                        sourceContainer.getInventory().getContents());
                    targetContainer.update(true, false);
                }
            } else if (sourceState instanceof Sign sourceSign) {
                BlockState targetState = target.getState();
                if (targetState instanceof Sign targetSign) {
                    for (int line = 0; line < 4; line++) {
                        targetSign.setLine(line, sourceSign.getLine(line));
                    }
                    targetSign.update(true, false);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Двигает всё, что хранит координаты в настройках уровня.
     * <p>
     * Список намеренно исчерпывающий: любая забытая координата - это молча съехавшая
     * часть уровня, которую обнаружат нескоро.
     */
    public static void shiftSettings(@NonNull Level level, int deltaZ) {
        WorldSettings worldSettings = level.getLevelSettings().getWorldSettings();

        worldSettings.getSpawn().add(0.0D, 0.0D, deltaZ);

        for (Waypoint waypoint : worldSettings.getWaypoints()) {
            waypoint.getLocation().add(0.0D, 0.0D, deltaZ);
        }

        // Вторая трасса дуэльной карты едет вместе с первой. Забыть её - значит
        // получить уровень, у которого одна половина уехала, а вторая осталась.
        for (Waypoint waypoint : worldSettings.getSecondWaypoints()) {
            waypoint.getLocation().add(0.0D, 0.0D, deltaZ);
        }

        // Барьеры неизменяемы - пересобираем список.
        List<GlowingBarrier> barriers = new ArrayList<>();
        for (GlowingBarrier barrier : worldSettings.getGlowingBarriers()) {
            barriers.add(new GlowingBarrier(
                barrier.getX(), barrier.getY(), barrier.getZ() + deltaZ,
                barrier.getColor(), barrier.getMode(), barrier.getPeek(), barrier.getExtension()));
        }
        worldSettings.setGlowingBarriers(barriers);

        LightShowSettings lightShow = worldSettings.getLightShow();

        for (Checkpoint checkpoint : lightShow.getCheckpoints()) {
            checkpoint.setPosition(checkpoint.getPosition().clone().add(
                new org.bukkit.util.Vector(0, 0, deltaZ)));
        }

        // Точки эффектов и ламповые стены живут в мировых координатах: без сдвига
        // расширение уровня оставило бы их на старом месте, отдельно от трассы.
        for (ru.sortix.parkourbeat.levels.wonder.WonderEffect effect : lightShow.getWonderEffects()) {
            org.bukkit.Location fixed = effect.getFixedLocation();
            if (fixed != null) effect.setFixedLocation(fixed.clone().add(0.0D, 0.0D, deltaZ));
        }

        for (ru.sortix.parkourbeat.levels.lamps.LampWall wall : lightShow.getLampWalls()) {
            wall.setCorners(
                wall.getX1(), wall.getY1(), wall.getZ1() + deltaZ,
                wall.getX2(), wall.getY2(), wall.getZ2() + deltaZ);
        }

        for (Portal portal : lightShow.getPortals()) {
            for (Portal.Side side : List.of(portal.getEntry(), portal.getExit())) {
                side.setPosition(side.getPosition().clone().add(
                    new org.bukkit.util.Vector(0, 0, deltaZ)));
            }
        }

        for (AutoDoor door : lightShow.getAutoDoors()) {
            door.setPosition(door.getBlockX(), door.getBlockY(), door.getBlockZ() + deltaZ);
        }

        // Метки-помощники тоже неизменяемы.
        List<HelperMarker> markers = new ArrayList<>();
        for (HelperMarker marker : lightShow.getHelperMarkers()) {
            markers.add(new HelperMarker(
                marker.getPosition().clone().add(new org.bukkit.util.Vector(0, 0, deltaZ)),
                marker.getKind()));
        }
        lightShow.getHelperMarkers().clear();
        lightShow.getHelperMarkers().addAll(markers);

        worldSettings.updateBorders();
        level.getLevelSettings().recalculateWaypoints(level.getWorld());
        level.getLevelSettings().updateParticleLocations();
        if (level.getLevelSettings().hasSecondParticleController()) {
            level.getLevelSettings().updateSecondParticleLocations();
        }
    }
}
