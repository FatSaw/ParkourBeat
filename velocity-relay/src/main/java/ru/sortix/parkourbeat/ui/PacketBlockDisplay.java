package ru.sortix.parkourbeat.ui;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.data.MappingData;
import com.viaversion.viaversion.api.minecraft.Quaternion;
import com.viaversion.viaversion.api.minecraft.Vector3f;
import com.viaversion.viaversion.api.minecraft.entities.EntityTypes1_19_4;
import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import com.viaversion.viaversion.api.minecraft.entitydata.types.EntityDataTypes1_19_4;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.version.Types1_19_4;
import com.viaversion.viaversion.api.type.types.version.Types1_20_2;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.Protocol1_16_4To1_17;
import com.viaversion.viaversion.protocols.v1_17_1to1_18.Protocol1_17_1To1_18;
import com.viaversion.viaversion.protocols.v1_17to1_17_1.Protocol1_17To1_17_1;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.Protocol1_18_2To1_19;
import com.viaversion.viaversion.protocols.v1_18to1_18_2.Protocol1_18To1_18_2;
import com.viaversion.viaversion.protocols.v1_19_1to1_19_3.Protocol1_19_1To1_19_3;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.Protocol1_19_3To1_19_4;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.packet.ClientboundPackets1_19_4;
import com.viaversion.viaversion.protocols.v1_19to1_19_1.Protocol1_19To1_19_1;
import com.viaversion.viaversion.protocols.v1_20to1_20_2.Protocol1_20To1_20_2;
import com.viaversion.viaversion.protocols.v1_20to1_20_2.packet.ClientboundPackets1_20_2;
import lombok.NonNull;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * BLOCK DISPLAY НА СЕРВЕРЕ 1.16.5.
 * <p>
 * В Bukkit 1.16.5 такой сущности нет, а у клиентов 26.2 она есть. Поэтому сущность
 * существует только у клиента одного игрока: пакеты пишутся в формате 1.19.4 и уходят
 * через ViaVersion (как {@link PacketHologram}). Сервер о ней не знает - нет ни
 * хитбокса, ни трекера, ни нагрузки.
 * <p>
 * Что умеет:
 * <ul>
 *   <li>любой блок (id состояния 1.16.5 переводится в 1.19.4 по таблицам ViaVersion);</li>
 *   <li>вращение вокруг СОБСТВЕННОГО ЦЕНТРА с плавностью на клиенте;</li>
 *   <li>перемещение с плавностью на клиенте (поле 1.20.2+), без рывков 20 тиков.</li>
 * </ul>
 * Модель ставится центром в точку сущности. Любая ошибка ловится и пишется в лог,
 * пакет при этом не уходит, так что кикнуть игрока она не может.
 */
public final class PacketBlockDisplay {
    // индексы данных Display/BlockDisplay в формате 1.19.4
    private static final int INTERP_START = 8, INTERP_DURATION = 9, TRANSLATION = 10, SCALE = 11,
        LEFT_ROTATION = 12, RIGHT_ROTATION = 13, VIEW_RANGE = 16, BLOCK_STATE = 22;
    /** Плавность перемещения - поле Display в формате 1.20.2. */
    private static final int TELEPORT_DURATION_1_20_2 = 10;

    private final int entityId = ThreadLocalRandom.current().nextInt(30_000_000, 40_000_000);
    private final UUID uuid = UUID.randomUUID();
    private final UUID viewer;
    private final int blockState;
    private boolean shown;
    private boolean interpolationFlip;
    private float scale = 1.0F;
    /** Последний поворот (кватернион x, y, z, w) - чтобы менять масштаб, не сбивая поворот. */
    private float qx, qy, qz, qw = 1.0F;

    private PacketBlockDisplay(@NonNull Player viewer, int blockState) {
        this.viewer = viewer.getUniqueId();
        this.blockState = blockState;
    }

    /**
     * Создать и показать игроку блок с центром в точке (x, y, z).
     *
     * @return null, если показать нельзя (нет ViaVersion, клиент старше 1.19.4, блок не нашёлся)
     */
    public static PacketBlockDisplay show(@NonNull Player viewer, @NonNull BlockData block,
                                          double x, double y, double z, int teleportTicks) {
        UserConnection connection = connection(viewer);
        if (connection == null) return null;
        int state = blockState1194(block);
        if (state < 0) return null;
        PacketBlockDisplay display = new PacketBlockDisplay(viewer, state);
        try {
            display.spawn(connection, x, y, z, teleportTicks);
            return display;
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().log(Level.WARNING, "[ParkourBeat] BlockDisplay show failed", t);
            return null;
        }
    }

    private void spawn(@NonNull UserConnection connection, double x, double y, double z, int teleportTicks) throws Exception {
        PacketWrapper spawn = PacketWrapper.create(ClientboundPackets1_19_4.ADD_ENTITY, connection);
        spawn.write(Types.VAR_INT, this.entityId);
        spawn.write(Types.UUID, this.uuid);
        spawn.write(Types.VAR_INT, EntityTypes1_19_4.BLOCK_DISPLAY.getId());
        spawn.write(Types.DOUBLE, x);
        spawn.write(Types.DOUBLE, y);
        spawn.write(Types.DOUBLE, z);
        // Поворот самой сущности - всегда 0: тогда система координат модели совпадает
        // с мировой, и ось вращения можно задавать прямо в мировых координатах.
        spawn.write(Types.BYTE, (byte) 0);
        spawn.write(Types.BYTE, (byte) 0);
        spawn.write(Types.BYTE, (byte) 0);
        spawn.write(Types.VAR_INT, 0);
        spawn.write(Types.SHORT, (short) 0);
        spawn.write(Types.SHORT, (short) 0);
        spawn.write(Types.SHORT, (short) 0);
        spawn.scheduleSend(Protocol1_19_3To1_19_4.class);

        EntityDataTypes1_19_4 types = Types1_19_4.ENTITY_DATA_TYPES;
        List<EntityData> data = new ArrayList<>();
        data.add(new EntityData(BLOCK_STATE, types.blockStateType, this.blockState));
        data.add(new EntityData(VIEW_RANGE, types.floatType, 4.0F));
        this.addTransform(data, types);
        PacketWrapper meta = PacketWrapper.create(ClientboundPackets1_19_4.SET_ENTITY_DATA, connection);
        meta.write(Types.VAR_INT, this.entityId);
        meta.write(Types1_19_4.ENTITY_DATA_LIST, data);
        meta.scheduleSend(Protocol1_19_3To1_19_4.class);

        if (teleportTicks > 0 && connection.getProtocolInfo().getPipeline().contains(Protocol1_20To1_20_2.class)) {
            PacketWrapper smooth = PacketWrapper.create(ClientboundPackets1_20_2.SET_ENTITY_DATA, connection);
            smooth.write(Types.VAR_INT, this.entityId);
            List<EntityData> list = new ArrayList<>();
            list.add(new EntityData(TELEPORT_DURATION_1_20_2, Types1_20_2.ENTITY_DATA_TYPES.varIntType,
                Math.min(59, teleportTicks)));
            smooth.write(Types1_20_2.ENTITY_DATA_LIST, list);
            smooth.scheduleSend(Protocol1_20To1_20_2.class);
        }
        this.shown = true;
    }

    /** Центр модели -> точка (x, y, z). Плавность - та, что задана при показе. */
    public void teleport(double x, double y, double z) {
        UserConnection connection = this.liveConnection();
        if (connection == null) return;
        try {
            PacketWrapper move = PacketWrapper.create(ClientboundPackets1_19_4.TELEPORT_ENTITY, connection);
            move.write(Types.VAR_INT, this.entityId);
            move.write(Types.DOUBLE, x);
            move.write(Types.DOUBLE, y);
            move.write(Types.DOUBLE, z);
            move.write(Types.BYTE, (byte) 0);
            move.write(Types.BYTE, (byte) 0);
            move.write(Types.BOOLEAN, false);
            move.scheduleSend(Protocol1_19_3To1_19_4.class);
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().log(Level.WARNING, "[ParkourBeat] BlockDisplay move failed", t);
        }
    }

    /**
     * Повернуть на угол вокруг оси (в мировых координатах) через центр блока.
     *
     * @param ticks за сколько тиков клиент доводит поворот (0 - мгновенно)
     */
    public void rotate(double angle, double ax, double ay, double az, int ticks) {
        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1.0E-9D) {
            ax = 0; ay = 0; az = 1; len = 1;
        }
        double half = angle / 2.0D, sin = Math.sin(half);
        this.qx = (float) (ax / len * sin);
        this.qy = (float) (ay / len * sin);
        this.qz = (float) (az / len * sin);
        this.qw = (float) Math.cos(half);
        this.sendTransform(ticks);
    }

    /** Плавно изменить масштаб (центр остаётся на месте). */
    public void setScale(float scale, int ticks) {
        this.scale = scale;
        this.sendTransform(ticks);
    }

    private void sendTransform(int ticks) {
        UserConnection connection = this.liveConnection();
        if (connection == null) return;
        try {
            EntityDataTypes1_19_4 types = Types1_19_4.ENTITY_DATA_TYPES;
            List<EntityData> data = new ArrayList<>();
            // Новое значение «начала интерполяции» каждый раз (0 / -1): клиент запускает
            // интерполяцию по обновлению этого поля.
            this.interpolationFlip = !this.interpolationFlip;
            data.add(new EntityData(INTERP_START, types.varIntType, this.interpolationFlip ? 0 : -1));
            data.add(new EntityData(INTERP_DURATION, types.varIntType, Math.max(0, ticks)));
            this.addTransform(data, types);
            PacketWrapper meta = PacketWrapper.create(ClientboundPackets1_19_4.SET_ENTITY_DATA, connection);
            meta.write(Types.VAR_INT, this.entityId);
            meta.write(Types1_19_4.ENTITY_DATA_LIST, data);
            meta.scheduleSend(Protocol1_19_3To1_19_4.class);
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().log(Level.WARNING, "[ParkourBeat] BlockDisplay transform failed", t);
        }
    }

    /**
     * Трансформация: модель блока (0..1) сначала масштабируется и поворачивается вокруг
     * своего угла, затем сдвигается так, чтобы её ЦЕНТР попал в точку сущности:
     * сдвиг = -R(S * (0.5, 0.5, 0.5)).
     */
    private void addTransform(@NonNull List<EntityData> data, @NonNull EntityDataTypes1_19_4 types) {
        float h = 0.5F * this.scale;
        float[] c = rotateVector(this.qx, this.qy, this.qz, this.qw, h, h, h);
        data.add(new EntityData(TRANSLATION, types.vector3FType, new Vector3f(-c[0], -c[1], -c[2])));
        data.add(new EntityData(SCALE, types.vector3FType, new Vector3f(this.scale, this.scale, this.scale)));
        data.add(new EntityData(LEFT_ROTATION, types.quaternionType, new Quaternion(this.qx, this.qy, this.qz, this.qw)));
        data.add(new EntityData(RIGHT_ROTATION, types.quaternionType, new Quaternion(0F, 0F, 0F, 1F)));
    }

    /** v' = q * v * q^-1. */
    private static float[] rotateVector(float x, float y, float z, float w, float vx, float vy, float vz) {
        // t = 2 * cross(q.xyz, v);  v' = v + w * t + cross(q.xyz, t)
        float tx = 2 * (y * vz - z * vy);
        float ty = 2 * (z * vx - x * vz);
        float tz = 2 * (x * vy - y * vx);
        return new float[]{
            vx + w * tx + (y * tz - z * ty),
            vy + w * ty + (z * tx - x * tz),
            vz + w * tz + (x * ty - y * tx)
        };
    }

    public void hide() {
        if (!this.shown) return;
        this.shown = false;
        UserConnection connection = this.connectionOrNull();
        if (connection == null) return;
        try {
            PacketWrapper remove = PacketWrapper.create(ClientboundPackets1_19_4.REMOVE_ENTITIES, connection);
            remove.write(Types.VAR_INT_ARRAY_PRIMITIVE, new int[]{this.entityId});
            remove.scheduleSend(Protocol1_19_3To1_19_4.class);
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().log(Level.WARNING, "[ParkourBeat] BlockDisplay hide failed", t);
        }
    }

    public boolean isShown() {
        return this.shown;
    }

    private UserConnection liveConnection() {
        return this.shown ? this.connectionOrNull() : null;
    }

    private UserConnection connectionOrNull() {
        Player player = org.bukkit.Bukkit.getPlayer(this.viewer);
        return player == null ? null : connection(player);
    }

    // ==================== подключение и id блока ====================

    private static Boolean available;

    public static boolean isAvailable() {
        Boolean cached = available;
        if (cached != null) return cached;
        boolean ok;
        try {
            Class.forName("com.viaversion.viaversion.protocols.v1_19_3to1_19_4.Protocol1_19_3To1_19_4");
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        available = ok;
        return ok;
    }

    /** Увидит ли игрок BlockDisplay (клиент 1.19.4+ через ViaVersion). */
    public static boolean canShowTo(@NonNull Player player) {
        try {
            return connection(player) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static UserConnection connection(@NonNull Player player) {
        if (!isAvailable()) return null;
        UserConnection connection = Via.getManager().getConnectionManager().getConnectedClient(player.getUniqueId());
        if (connection == null || connection.getProtocolInfo() == null || connection.getProtocolInfo().getPipeline() == null) {
            return null;
        }
        return connection.getProtocolInfo().getPipeline().contains(Protocol1_19_3To1_19_4.class) ? connection : null;
    }

    /** Цепочка ViaVersion от 1.16.4/1.16.5 до 1.19.4 - по ней id состояния блока переводится в номер 1.19.4. */
    @SuppressWarnings("unchecked")
    private static final Class<? extends Protocol>[] CHAIN = new Class[]{
        Protocol1_16_4To1_17.class, Protocol1_17To1_17_1.class, Protocol1_17_1To1_18.class,
        Protocol1_18To1_18_2.class, Protocol1_18_2To1_19.class, Protocol1_19To1_19_1.class,
        Protocol1_19_1To1_19_3.class, Protocol1_19_3To1_19_4.class
    };
    private static final Map<String, Integer> STATE_CACHE = new ConcurrentHashMap<>();
    private static Method getState, getCombinedId;

    /** id состояния блока в нумерации 1.19.4, -1 если перевести не вышло. */
    public static int blockState1194(@NonNull BlockData block) {
        String key = block.getAsString();
        Integer cached = STATE_CACHE.get(key);
        if (cached != null) return cached;
        int id = -1;
        try {
            id = nmsStateId(block);
            for (Class<? extends Protocol> cls : CHAIN) {
                Protocol protocol = Via.getManager().getProtocolManager().getProtocol(cls);
                if (protocol == null) {
                    id = -1;
                    break;
                }
                MappingData mapping = protocol.getMappingData();
                if (mapping != null && mapping.getBlockStateMappings() != null) {
                    id = mapping.getNewBlockStateId(id);
                    if (id < 0) break;
                }
            }
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().log(Level.WARNING, "[ParkourBeat] BlockDisplay: block id for " + key, t);
            id = -1;
        }
        STATE_CACHE.put(key, id);
        return id;
    }

    /** Номер состояния блока на самом сервере (1.16.5): Block.getCombinedId(CraftBlockData.getState()). */
    private static int nmsStateId(@NonNull BlockData block) throws Exception {
        if (getState == null) {
            getState = block.getClass().getMethod("getState");
        }
        Object state = getState.invoke(block);
        if (getCombinedId == null) {
            Class<?> nmsBlock = Class.forName(state.getClass().getPackage().getName() + ".Block");
            for (Method m : nmsBlock.getMethods()) {
                if (m.getName().equals("getCombinedId") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(state.getClass()) && m.getReturnType() == int.class) {
                    getCombinedId = m;
                    break;
                }
            }
            if (getCombinedId == null) throw new IllegalStateException("Block.getCombinedId not found");
        }
        return (int) getCombinedId.invoke(null, state);
    }
}
