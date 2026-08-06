package ru.sortix.parkourbeat.world;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * Досылает клиенту чанк заново, чтобы он перерисовал биомы без перезахода на уровень
 */
@UtilityClass
public class BiomeRefresher {

    private final boolean AVAILABLE;

    private Method craftChunkGetHandle;
    private Method craftPlayerGetHandle;
    private Field entityPlayerConnection;
    private Method playerConnectionSendPacket;
    private Constructor<?> mapChunkConstructor;

    // --- необязательная часть: пакет освещения ---
    private boolean lightAvailable = false;
    private Method craftWorldGetHandle;
    private Method worldGetChunkProvider;
    private Method chunkProviderGetLightEngine;
    private Constructor<?> chunkCoordPairConstructor;
    private Constructor<?> lightUpdateConstructor;

    static {
        boolean available;
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            available = init(version);
        } catch (Throwable t) {
            available = false;
        }
        AVAILABLE = available;
    }

    private boolean init(@NonNull String version) {
        try {
            String nms = "net.minecraft.server." + version + ".";
            String obc = "org.bukkit.craftbukkit." + version + ".";

            Class<?> craftChunk = Class.forName(obc + "CraftChunk");
            Class<?> craftPlayer = Class.forName(obc + "entity.CraftPlayer");
            Class<?> entityPlayer = Class.forName(nms + "EntityPlayer");
            Class<?> playerConnection = Class.forName(nms + "PlayerConnection");
            Class<?> packet = Class.forName(nms + "Packet");
            Class<?> nmsChunk = Class.forName(nms + "Chunk");
            Class<?> mapChunk = Class.forName(nms + "PacketPlayOutMapChunk");

            craftChunkGetHandle = craftChunk.getMethod("getHandle");
            craftPlayerGetHandle = craftPlayer.getMethod("getHandle");
            entityPlayerConnection = entityPlayer.getField("playerConnection");
            playerConnectionSendPacket = playerConnection.getMethod("sendPacket", packet);

            mapChunkConstructor = mapChunk.getConstructor(nmsChunk, int.class);

            initLight(version, nms, obc);

            return true;
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING,
                "[ParkourBeat] BiomeRefresher: не удалось инициализировать NMS для " + version
                    + ", динамическая смена биома отключена", t);
            return false;
        }
    }

    private void initLight(@NonNull String version, @NonNull String nms, @NonNull String obc) {
        try {
            Class<?> craftWorld = Class.forName(obc + "CraftWorld");
            Class<?> nmsWorld = Class.forName(nms + "World");
            Class<?> chunkCoordPair = Class.forName(nms + "ChunkCoordIntPair");
            Class<?> lightEngineClass = Class.forName(nms + "LightEngine");
            Class<?> lightUpdate = Class.forName(nms + "PacketPlayOutLightUpdate");

            craftWorldGetHandle = craftWorld.getMethod("getHandle");
            worldGetChunkProvider = nmsWorld.getMethod("getChunkProvider");
            chunkProviderGetLightEngine = Class.forName(nms + "IChunkProvider").getMethod("getLightEngine");
            chunkCoordPairConstructor = chunkCoordPair.getConstructor(int.class, int.class);

            // Сигнатура пакета света между версиями менялась, поэтому ищем
            // подходящий конструктор, а не полагаемся на конкретную.
            for (Constructor<?> constructor : lightUpdate.getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length < 2) continue;
                if (!parameters[0].equals(chunkCoordPair)) continue;
                if (!parameters[1].isAssignableFrom(lightEngineClass)
                    && !lightEngineClass.isAssignableFrom(parameters[1])) continue;

                boolean tailIsBooleans = true;
                for (int i = 2; i < parameters.length; i++) {
                    if (parameters[i] != boolean.class) {
                        tailIsBooleans = false;
                        break;
                    }
                }
                if (!tailIsBooleans) continue;

                lightUpdateConstructor = constructor;
                break;
            }

            lightAvailable = lightUpdateConstructor != null;
            if (!lightAvailable) {
                Bukkit.getLogger().info("[ParkourBeat] BiomeRefresher: пакет освещения для "
                    + version + " не найден, обходимся без него");
            }
        } catch (Throwable t) {
            lightAvailable = false;
        }
    }

    public boolean isAvailable() {
        return AVAILABLE;
    }

    public void refreshChunkFor(@NonNull Player player, @NonNull Chunk chunk) {
        if (!AVAILABLE) return;
        if (!player.isOnline()) return;
        if (player.getWorld() != chunk.getWorld()) return;

        try {
            Object nmsChunk = craftChunkGetHandle.invoke(chunk);
            Object entityPlayer = craftPlayerGetHandle.invoke(player);
            Object connection = entityPlayerConnection.get(entityPlayer);
            Object lightPacket = buildLightPacket(chunk);
            if (lightPacket != null) {
                playerConnectionSendPacket.invoke(connection, lightPacket);
            }
            Object mapPacket = mapChunkConstructor.newInstance(nmsChunk, 65535);
            playerConnectionSendPacket.invoke(connection, mapPacket);

        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING,
                "[ParkourBeat] BiomeRefresher: ошибка отправки чанка "
                    + chunk.getX() + "," + chunk.getZ() + " игроку " + player.getName(), t);
        }
    }

    private Object buildLightPacket(@NonNull Chunk chunk) {
        if (!lightAvailable) return null;
        try {
            Object nmsWorld = craftWorldGetHandle.invoke(chunk.getWorld());
            Object chunkProvider = worldGetChunkProvider.invoke(nmsWorld);
            Object lightEngine = chunkProviderGetLightEngine.invoke(chunkProvider);
            Object coords = chunkCoordPairConstructor.newInstance(chunk.getX(), chunk.getZ());

            Class<?>[] parameters = lightUpdateConstructor.getParameterTypes();
            Object[] arguments = new Object[parameters.length];
            arguments[0] = coords;
            arguments[1] = lightEngine;
            for (int i = 2; i < parameters.length; i++) {
                arguments[i] = Boolean.TRUE;
            }
            return lightUpdateConstructor.newInstance(arguments);
        } catch (Throwable t) {
            lightAvailable = false;
            return null;
        }
    }

    public void refreshChunksAround(@NonNull Player player, int chunkRadius) {
        if (!AVAILABLE) return;
        if (!player.isOnline()) return;

        World world = player.getWorld();
        Chunk center = player.getLocation().getChunk();
        int cx = center.getX();
        int cz = center.getZ();

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                if (!world.isChunkLoaded(x, z)) continue;
                refreshChunkFor(player, world.getChunkAt(x, z));
            }
        }
    }

    public void refreshChunksForAll(@NonNull World world, @NonNull Set<Long> chunkKeys) {
        if (!AVAILABLE) return;

        for (Player player : world.getPlayers()) {
            Set<Long> sent = new HashSet<>();
            Chunk pc = player.getLocation().getChunk();
            int viewRadius = 8;
            for (long key : chunkKeys) {
                int x = (int) (key & 0xFFFFFFFFL);
                int z = (int) (key >> 32);
                if (Math.abs(x - pc.getX()) > viewRadius) continue;
                if (Math.abs(z - pc.getZ()) > viewRadius) continue;
                if (!world.isChunkLoaded(x, z)) continue;
                if (!sent.add(key)) continue;
                refreshChunkFor(player, world.getChunkAt(x, z));
            }
        }
    }

    public long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }
}
