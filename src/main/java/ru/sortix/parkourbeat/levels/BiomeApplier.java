package ru.sortix.parkourbeat.levels;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import ru.sortix.parkourbeat.levels.settings.BiomeZone;
import ru.sortix.parkourbeat.levels.settings.LevelBiome;
import ru.sortix.parkourbeat.world.BiomeRefresher;
import ru.sortix.parkourbeat.world.Cuboid;

import javax.annotation.Nullable;

@UtilityClass
public class BiomeApplier {
    private final int Y_STEP = 4;
    private static final long MAX_CHUNKS = 4096L;

    public void applyAll(@NonNull Level level) {
        // Зон может быть под сотню, а сохранение мира переписывает его целиком.
        // Раскладываем все, сохраняем один раз в конце.
        boolean any = false;
        for (BiomeZone zone : level.getLightShow().getBiomeZones()) {
            if (apply(level, zone.getStartMillis(), zone.getEndMillis(), zone.getBiome(), false)) {
                any = true;
            }
        }
        if (any) level.getWorld().save();
    }

    public boolean apply(@NonNull Level level, @NonNull BiomeZone zone) {
        return apply(level, zone.getStartMillis(), zone.getEndMillis(), zone.getBiome(), true);
    }

    /**
     * Покрасить зону, не сохраняя мир.
     * <p>
     * Нужно, когда зон расставляют СРАЗУ МНОГО. Сохранение мира - операция на весь мир
     * целиком, а не на изменённый кусок; вызывать её на каждую из полусотни зон значит
     * полсотни раз переписать всё, что есть на диске. Разложить их, а сохранить один раз
     * в конце - тот же результат за сотую долю работы.
     *
     * @param save сохранять ли мир сразу после этой зоны
     */
    public boolean apply(@NonNull Level level, @NonNull BiomeZone zone, boolean save) {
        return apply(level, zone.getStartMillis(), zone.getEndMillis(), zone.getBiome(), save);
    }

    public boolean reset(@NonNull Level level, @NonNull BiomeZone zone) {
        return apply(level, zone.getStartMillis(), zone.getEndMillis(), LevelBiome.DEFAULT, true);
    }

    public boolean reset(@NonNull Level level, @NonNull BiomeZone zone, boolean save) {
        return apply(level, zone.getStartMillis(), zone.getEndMillis(), LevelBiome.DEFAULT, save);
    }

    /** Обычная зона: спавн не накрываем, мир сохраняем. */
    private boolean apply(@NonNull Level level, int fromMillis, int toMillis,
                          @NonNull LevelBiome levelBiome, boolean save) {
        return apply(level, fromMillis, toMillis, levelBiome, false, save);
    }

    private boolean apply(@NonNull Level level, int fromMillis, int toMillis,
                          @NonNull LevelBiome levelBiome, boolean coverSpawn, boolean save) {
        Biome biome = levelBiome.resolve();
        if (biome == null) return false;

        World world = level.getWorld();
        Cuboid cuboid = level.getCuboid();
        boolean alongX = LightShowPositions.isAlongX(level);

        double fromCoordinate = toCoordinate(level, fromMillis);
        double toCoordinate = toCoordinate(level, toMillis);
        int minCoordinate = (int) Math.floor(Math.min(fromCoordinate, toCoordinate));
        int maxCoordinate = (int) Math.ceil(Math.max(fromCoordinate, toCoordinate));

        int crossMin = (int) Math.floor(alongX ? cuboid.getMin().getZ() : cuboid.getMin().getX());
        int crossMax = (int) Math.ceil(alongX ? cuboid.getMax().getZ() : cuboid.getMax().getX());

        if (coverSpawn) {
            Location spawn = level.getLevelSettings().getWorldSettings().getSpawn();
            if (spawn != null) {
                int spawnMain = (int) Math.floor(alongX ? spawn.getX() : spawn.getZ());
                int spawnCross = (int) Math.floor(alongX ? spawn.getZ() : spawn.getX());
                int pad = 8;
                minCoordinate = Math.min(minCoordinate, spawnMain - pad);
                maxCoordinate = Math.max(maxCoordinate, spawnMain + pad);
                crossMin = Math.min(crossMin, spawnCross - pad);
                crossMax = Math.max(crossMax, spawnCross + pad);
            }
        }

        int minY = 0;
        int maxY = world.getMaxHeight() - 1;

        minCoordinate = Math.floorDiv(minCoordinate, 4) * 4;
        maxCoordinate = Math.floorDiv(maxCoordinate + 3, 4) * 4 + 3;
        // ПОПЕРЁК КРАСИМ ТОЛЬКО ТОННЕЛЬ, А НЕ ВЕСЬ УРОВЕНЬ.
        //
        // Границы берутся из кубоида уровня, а он у «Копателя» огромный: тоннель
        // строят с запасом во все стороны, и поперечник выходил под три сотни блоков.
        // Полторы тысячи чанков на одну зону - это и минуты работы, и мегабайты в
        // памяти под сами чанки, которые пришлось загрузить и удержать.
        //
        // Видно игроку при этом ровно ширину тоннеля плюс стены: он едет по прямой и
        // никуда с неё не сворачивает. Всё, что дальше, красится совершенно впустую.
        if (ru.sortix.parkourbeat.digger.DiggerManager.isDigger(level)) {
            ru.sortix.parkourbeat.digger.DiggerLevelSettings digger =
                level.getLevelSettings().getGameSettings().getDiggerSettings();

            Location diggerOrigin = digger.getOrigin(world);
            double centre = diggerOrigin == null
                ? 0.0D
                : (alongX ? diggerOrigin.getZ() : diggerOrigin.getX());

            // Ширина тоннеля плюс небольшой запас на стены и декор по бокам.
            int halfWidth = Math.max(4, digger.getTunnelWidth() / 2 + 6);
            crossMin = Math.max(crossMin, (int) Math.floor(centre) - halfWidth);
            crossMax = Math.min(crossMax, (int) Math.ceil(centre) + halfWidth);
        }

        crossMin = Math.floorDiv(crossMin, 4) * 4;
        crossMax = Math.floorDiv(crossMax + 3, 4) * 4 + 3;

        java.util.Set<Long> affectedChunks = new java.util.HashSet<>();
        java.util.List<org.bukkit.Chunk> ticketedChunks = new java.util.ArrayList<>();

        org.bukkit.plugin.Plugin owningPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("ParkourBeat");

        // Единственная добавка к исходной логике: отказ вместо падения сервера.
        // Зона в тысячу секунд разворачивалась в миллионы setBiome за один тик.
        long chunksWide = ((long) (maxCoordinate - minCoordinate) >> 4) + 2L;
        long chunksLong = ((long) (crossMax - crossMin) >> 4) + 2L;
        if (chunksWide * chunksLong > MAX_CHUNKS) {
            org.bukkit.Bukkit.getLogger().warning("[ParkourBeat] Биом-зона слишком большая: "
                + (chunksWide * chunksLong) + " чанков при лимите " + MAX_CHUNKS
                + ". Зона пропущена, уменьшите её длительность.");
            return false;
        }

        java.util.Set<Long> allChunkCoords = new java.util.TreeSet<>();

        for (int main = minCoordinate; main <= maxCoordinate; main++) {
            for (int cross = crossMin; cross <= crossMax; cross++) {
                int x = alongX ? main : cross;
                int z = alongX ? cross : main;

                int chunkX = x >> 4;
                int chunkZ = z >> 4;
                long chunkKey = BiomeRefresher.chunkKey(chunkX, chunkZ);

                if (affectedChunks.add(chunkKey)) {
                    org.bukkit.Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    if (owningPlugin != null) {
                        chunk.addPluginChunkTicket(owningPlugin);
                        ticketedChunks.add(chunk);
                    } else {
                        chunk.load(true);
                    }
                    allChunkCoords.add(((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL));
                }

                for (int y = minY; y <= maxY; y += Y_STEP) {
                    world.setBiome(x, y, z, biome);
                }
                world.setBiome(x, maxY, z, biome);
            }
        }

        // ОДНА СТРОКА ИТОГА, А НЕ СПИСОК ВСЕХ ЧАНКОВ.
        //
        // Здесь перечислялись координаты КАЖДОГО прокрашенного чанка. На карте
        // «Копателя» их полторы тысячи: строка выходит под двадцать килобайт, и она
        // не только пишется в файл, но и живёт в памяти - сначала в StringBuilder,
        // потом в самой строке, потом в очереди логгера, потом в буфере консоли. И так
        // на каждую биом-зону при каждой авторазметке. Полезной информации в этом
        // списке нет никакой: важны границы и количество, а они и так в первой части.
        org.bukkit.Bukkit.getLogger().info("[ParkourBeat] Биом " + biome
            + ": вдоль " + minCoordinate + ".." + maxCoordinate
            + ", поперёк " + crossMin + ".." + crossMax
            + ", чанков " + allChunkCoords.size());

        if (save) world.save();

        if (owningPlugin != null) {
            for (org.bukkit.Chunk chunk : ticketedChunks) {
                chunk.removePluginChunkTicket(owningPlugin);
            }
        }

        if (BiomeRefresher.isAvailable() && !world.getPlayers().isEmpty()) {
            BiomeRefresher.refreshChunksForAll(world, affectedChunks);
        }
        return true;
    }

    public void applyLevelWide(@NonNull Level level, @NonNull LevelBiome levelBiome) {
        int startMillis = 0;
        int endMillis;

        // ДЛИНА УРОВНЯ У «КОПАТЕЛЯ» СЧИТАЕТСЯ ПО НОТАМ, А НЕ ПО ПУТИ ИЗ ЧАСТИЦ.
        //
        // Пути у него нет и не будет: тоннель строитель тянет руками, точки трассы не
        // расставляются вовсе. Поэтому finishWaypoint у такой карты стоит фактически на
        // спавне, и «биом на весь уровень» красил ровно четыре секунды от старта - те
        // самые две-три секунды, после которых биом обрывался.
        //
        // Конец карты здесь - самая дальняя руда-нота, переведённая в таймкод скоростью
        // самого режима. Запас в четыре секунды остаётся тот же, что и у обычного уровня.
        if (ru.sortix.parkourbeat.digger.DiggerManager.isDigger(level)) {
            endMillis = diggerEndMillis(level);
        } else {
            endMillis = LightShowPositions.toTimeMillis(
                level, level.getLevelSettings().getWorldSettings().getFinishWaypoint());
        }
        // Сначала красим весь уровень, потом возвращаем поверх него зоны - и только
        // после этого сохраняем. Сохранять между этими шагами незачем: промежуточное
        // состояние всё равно никому не нужно.
        apply(level, startMillis, endMillis + 4000, levelBiome, true, false);

        for (BiomeZone zone : level.getLightShow().getBiomeZones()) {
            apply(level, zone.getStartMillis(), zone.getEndMillis(), zone.getBiome(), false);
        }

        level.getWorld().save();
    }

    /**
     * Таймкод в координату.
     * <p>
     * Вся режимозависимость живёт теперь в {@link LightShowPositions}: и прямой перевод,
     * и обратный там уже учитывают скорость «Копателя» и его зоны скорости. Держать
     * здесь вторую копию той же логики означало бы рано или поздно её расподобить.
     */
    private double toCoordinate(@NonNull Level level, int timeMillis) {
        return LightShowPositions.toCoordinate(level, timeMillis);
    }

    /**
     * Таймкод последней ноты карты «Копателя».
     * <p>
     * Расстояние до самой дальней руды, делённое на скорость режима. Ноль, если руд
     * ещё нет - тогда красить попросту нечего.
     */
    private int diggerEndMillis(@NonNull Level level) {
        ru.sortix.parkourbeat.digger.DiggerLevelSettings digger =
            level.getLevelSettings().getGameSettings().getDiggerSettings();
        if (digger.getNotesCount() == 0) return 0;

        boolean alongX = LightShowPositions.isAlongX(level);
        int sign = level.getLevelSettings().getDirectionChecker().isNegative() ? -1 : 1;

        Location origin = digger.getOrigin(level.getWorld());
        double originCoordinate = origin == null
            ? level.getLevelSettings().getStartPosition()
            : level.getLevelSettings().getDirectionChecker().getCoordinate(origin);

        double farthest = 0.0D;
        for (ru.sortix.parkourbeat.digger.DiggerNote note : digger.getAllNotes()) {
            double coordinate = (alongX ? note.getX() : note.getZ()) + 0.5D;
            farthest = Math.max(farthest, (coordinate - originCoordinate) * sign);
        }
        if (farthest <= 0.0D) return 0;

        // Обратный перевод - тем же профилем, что и прямой.
        double millis = digger.speedProfile().millisAt(farthest);
        return (int) Math.min(ru.sortix.parkourbeat.utils.TimeUtils.MAX_TIMECODE_MILLIS,
            Math.round(millis));
    }

    @Nullable
    public BiomeZone findZoneAt(@NonNull Level level, int timeMillis) {
        for (BiomeZone zone : level.getLightShow().getBiomeZones()) {
            if (timeMillis >= zone.getStartMillis() && timeMillis <= zone.getEndMillis()) return zone;
        }
        return null;
    }
}
