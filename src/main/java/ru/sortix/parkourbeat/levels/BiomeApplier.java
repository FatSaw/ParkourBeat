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

    public void applyAll(@NonNull Level level) {
        for (BiomeZone zone : level.getLightShow().getBiomeZones()) {
            apply(level, zone.getStartMillis(), zone.getEndMillis(), zone.getBiome());
        }
    }

    public boolean apply(@NonNull Level level, @NonNull BiomeZone zone) {
        return apply(level, zone.getStartMillis(), zone.getEndMillis(), zone.getBiome());
    }

    public boolean reset(@NonNull Level level, @NonNull BiomeZone zone) {
        return apply(level, zone.getStartMillis(), zone.getEndMillis(), LevelBiome.DEFAULT);
    }

    private boolean apply(@NonNull Level level, int fromMillis, int toMillis, @NonNull LevelBiome levelBiome) {
        return apply(level, fromMillis, toMillis, levelBiome, false);
    }

    private boolean apply(@NonNull Level level, int fromMillis, int toMillis,
                          @NonNull LevelBiome levelBiome, boolean coverSpawn) {
        Biome biome = levelBiome.resolve();
        if (biome == null) return false;

        World world = level.getWorld();
        Cuboid cuboid = level.getCuboid();
        boolean alongX = LightShowPositions.isAlongX(level);

        double fromCoordinate = LightShowPositions.toCoordinate(level, fromMillis);
        double toCoordinate = LightShowPositions.toCoordinate(level, toMillis);
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
        crossMin = Math.floorDiv(crossMin, 4) * 4;
        crossMax = Math.floorDiv(crossMax + 3, 4) * 4 + 3;

        java.util.Set<Long> affectedChunks = new java.util.HashSet<>();
        java.util.List<org.bukkit.Chunk> ticketedChunks = new java.util.ArrayList<>();

        org.bukkit.plugin.Plugin owningPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("ParkourBeat");

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

        StringBuilder sb = new StringBuilder("[ParkourBeat][BiomeDebug] biome=" + biome
            + " | main(вдоль)=" + minCoordinate + ".." + maxCoordinate
            + " cross(поперёк)=" + crossMin + ".." + crossMax
            + " alongX=" + alongX + " | прокрашено чанков=" + allChunkCoords.size() + " : ");
        for (long ck : allChunkCoords) {
            int ccx = (int) (ck >> 32);
            int ccz = (int) (ck & 0xFFFFFFFFL);
            sb.append("[").append(ccx).append(",").append(ccz).append("]");
        }
        org.bukkit.Bukkit.getLogger().info(sb.toString());

        world.save();

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
        int endMillis = LightShowPositions.toTimeMillis(
            level, level.getLevelSettings().getWorldSettings().getFinishWaypoint());
        apply(level, startMillis, endMillis + 4000, levelBiome, true);

        for (BiomeZone zone : level.getLightShow().getBiomeZones()) {
            apply(level, zone.getStartMillis(), zone.getEndMillis(), zone.getBiome());
        }
    }

    @Nullable
    public BiomeZone findZoneAt(@NonNull Level level, int timeMillis) {
        for (BiomeZone zone : level.getLightShow().getBiomeZones()) {
            if (timeMillis >= zone.getStartMillis() && timeMillis <= zone.getEndMillis()) return zone;
        }
        return null;
    }
}
