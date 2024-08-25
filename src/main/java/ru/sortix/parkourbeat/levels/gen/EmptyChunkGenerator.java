package ru.sortix.parkourbeat.levels.gen;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

@RequiredArgsConstructor
public class EmptyChunkGenerator extends ChunkGenerator {
    private final Server server;

    @Override
    @NonNull
    public ChunkData generateChunkData(@NonNull World world,
                                       @NonNull Random random,
                                       int x, int z,
                                       @NonNull BiomeGrid biome
    ) {
        return this.server.createChunkData(world);
    }

    @NonNull
    public ChunkData createVanillaChunkData(@NonNull World world, int x, int z) {
        return this.server.createChunkData(world);
    }

    @Override
    public boolean canSpawn(@NonNull World world, int x, int z) {
        return true;
    }
}
