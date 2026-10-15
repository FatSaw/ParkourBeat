package ru.sortix.parkourbeat.ui;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.Vector3f;
import com.viaversion.viaversion.api.minecraft.entities.EntityTypes1_19_4;
import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import com.viaversion.viaversion.api.minecraft.entitydata.types.EntityDataTypes1_19_4;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.version.Types1_19_4;
import com.viaversion.viaversion.libs.gson.JsonParser;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.Protocol1_19_3To1_19_4;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.packet.ClientboundPackets1_19_4;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * БОЛЬШАЯ ГОЛОГРАММА (TextDisplay) на сервере 1.16.5.
 * <p>
 * Сервер 1.16.5 такой сущности не знает, поэтому пакеты собираются в формате 1.19.4
 * (там TextDisplay появился) и отдаются в цепочку ViaVersion начиная с шага
 * 1.19.3→1.19.4: дальше ViaVersion сама переводит их под версию игрока (26.2 и т.д.).
 * Сверено с ViaVersion 5.11.0 (classes: PacketWrapper, ClientboundPackets1_19_4,
 * EntityTypes1_19_4.TEXT_DISPLAY, Types1_19_4.ENTITY_DATA_LIST).
 * <p>
 * Что умеет: крупный текст (scale), полупрозрачный фон нужного цвета или без фона,
 * всегда лицом к игроку, несколько строк, наши иконки и плашки внутри текста.
 * <p>
 * Игрокам, у которых в цепочке нет шага 1.19.4 (старые клиенты), голограмма просто
 * не показывается - см. {@link #canShowTo}. Любая ошибка ловится и пишется в лог,
 * пакет при этом не уходит, так что кикнуть игрока она не может.
 * <p>
 * В plugin.yml нужен {@code softdepend: [ViaVersion]}, а в сборке - зависимость
 * com.viaversion:viaversion-api и viaversion-common 5.11.0 (provided / compileOnly).
 */
public final class PacketHologram {
    // индексы данных TextDisplay в 1.19.4
    private static final int SCALE = 11, BILLBOARD = 14, VIEW_RANGE = 16, TEXT = 22, LINE_WIDTH = 23,
        BACKGROUND = 24, TEXT_OPACITY = 25, STYLE = 26;
    /** FIXED: голограмма не крутится за игроком, а стоит как табличка - повернута как NPC. */
    private static final byte BILLBOARD_FIXED = 0;
    /** Если текст окажется повернут задом - поставить 180. */
    public static float YAW_OFFSET = 0F;
    private static final byte STYLE_SHADOW = 0x01;

    private static volatile Boolean available;

    /** «Всегда лицом к игроку» (для HUD) вместо неподвижной таблички. */
    public static final byte BILLBOARD_CENTER = 3;
    /**
     * Поворачивается за взглядом камеры только влево-вправо (ось - вертикаль), без наклона.
     * Значения режимов: 0 FIXED, 1 VERTICAL, 2 HORIZONTAL, 3 CENTER. ВАЖНО: 2 (HORIZONTAL)
     * берёт поворот самой сущности, а у пассажира он нулевой - текст встал бы в одну
     * сторону света, а не перед лицом.
     */
    public static final byte BILLBOARD_VERTICAL = 1;
    private static final int TRANSLATION = 10;

    /** Сдвиг текста внутри сущности (в её повёрнутой системе координат). */
    private float tx, ty, tz;
    private boolean interpolationFlip;

    /** Кто на ком «едет»: игрок -> id наших сущностей-пассажиров (пакет пассажиров заменяет весь список). */
    private static final java.util.Map<UUID, java.util.Set<Integer>> PASSENGERS = new ConcurrentHashMap<>();
    private UUID vehicle;

    private final int entityId = ThreadLocalRandom.current().nextInt(20_000_000, 30_000_000);
    private byte billboard = BILLBOARD_FIXED;
    /** Плавность перемещения в тиках (поле 1.20.2+). 0 - мгновенно. */
    private int teleportDuration = 0;
    /** Видно сквозь стены (TextDisplay see-through): чёткий текст, без полупрозрачного «двойника». */
    private boolean seeThrough = false;

    public PacketHologram seeThrough(boolean value) {
        this.seeThrough = value;
        return this;
    }
    private final UUID uuid = UUID.randomUUID();
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private Location location;
    private Component text;
    private float scale;
    private int background;

    /**
     * @param background цвет фона ARGB (0x00000000 - без фона, 0x66140620 - тёмная полупрозрачная плашка)
     */
    public PacketHologram(@NonNull Location location, @NonNull Component text, float scale, int background) {
        this.location = location.clone();
        this.text = text;
        this.scale = scale;
        this.background = background;
    }

    /** Режим поворота и плавность перемещения - до первого show(). */
    public PacketHologram style(byte billboard, int teleportDuration) {
        this.billboard = billboard;
        this.teleportDuration = Math.max(0, teleportDuration);
        return this;
    }

    /** Переставить голограмму у всех, кто её видит (при teleportDuration > 0 - плавно). */
    public void teleport(@NonNull Location location) {
        this.location = location.clone();
        for (UUID id : this.viewers) {
            Player player = org.bukkit.Bukkit.getPlayer(id);
            if (player == null) {
                this.viewers.remove(id);
                continue;
            }
            try {
                UserConnection connection = connection(player);
                if (connection == null) continue;
                PacketWrapper move = PacketWrapper.create(ClientboundPackets1_19_4.TELEPORT_ENTITY, connection);
                move.write(Types.VAR_INT, this.entityId);
                move.write(Types.DOUBLE, location.getX());
                move.write(Types.DOUBLE, location.getY());
                move.write(Types.DOUBLE, location.getZ());
                move.write(Types.BYTE, (byte) ((location.getYaw() + YAW_OFFSET) * 256.0F / 360.0F));
                move.write(Types.BYTE, (byte) 0);
                move.write(Types.BOOLEAN, false);
                move.scheduleSend(Protocol1_19_3To1_19_4.class);
            } catch (Throwable t) {
                org.bukkit.Bukkit.getLogger().log(Level.WARNING, "[ParkourBeat] TextDisplay move failed", t);
            }
        }
    }

    /** Начальный сдвиг - до show(). */
    public PacketHologram translation(float x, float y, float z) {
        this.tx = x;
        this.ty = y;
        this.tz = z;
        return this;
    }

    /** Плавно (на клиенте, со скоростью кадров) перевести сдвиг и масштаб за ticks тиков. */
    public void transform(float x, float y, float z, float scale, int ticks) {
        this.tx = x;
        this.ty = y;
        this.tz = z;
        this.scale = scale;
        // Значение «начала интерполяции» меняем каждый раз (0 / -1): клиент запускает новую
        // интерполяцию по изменению этого поля - одинаковое значение подряд могло не сработать.
        this.interpolationFlip = !this.interpolationFlip;
        int start = this.interpolationFlip ? 0 : -1;
        this.sendToViewers(types -> {
            List<EntityData> data = new ArrayList<>();
            data.add(new EntityData(8, types.varIntType, start));
            data.add(new EntityData(9, types.varIntType, Math.max(0, ticks)));
            data.add(new EntityData(TRANSLATION, types.vector3FType, new Vector3f(x, y, z)));
            data.add(new EntityData(SCALE, types.vector3FType, new Vector3f(scale, scale, scale)));
            return data;
        });
    }

    /**
     * Посадить «пассажиром» на игрока: клиент сам двигает пассажиров вместе с игроком
     * каждый кадр - без пакетов движения и без сетевых рывков.
     */
    public void mount(@NonNull Player player) {
        this.vehicle = player.getUniqueId();
        java.util.Set<Integer> set = PASSENGERS.computeIfAbsent(this.vehicle, k -> ConcurrentHashMap.newKeySet());
        set.add(this.entityId);
        sendPassengers(player, set);
    }

    private static void sendPassengers(@NonNull Player player, @NonNull java.util.Set<Integer> ids) {
        try {
            UserConnection connection = connection(player);
            if (connection == null) return;
            int[] array = new int[ids.size()];
            int i = 0;
            for (int id : ids) array[i++] = id;
            PacketWrapper wrapper = PacketWrapper.create(ClientboundPackets1_19_4.SET_PASSENGERS, connection);
            wrapper.write(Types.VAR_INT, player.getEntityId());
            wrapper.write(Types.VAR_INT_ARRAY_PRIMITIVE, array);
            wrapper.scheduleSend(Protocol1_19_3To1_19_4.class);
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().log(Level.WARNING, "[ParkourBeat] TextDisplay mount failed", t);
        }
    }

    /**
     * Сменить плавность перемещения (поле 1.20.2+, 0..59 тиков): следующий teleport()
     * клиент проведёт плавно за это время - одно перемещение вместо серии рывков.
     */
    public void setTeleportDuration(int ticks) {
        this.teleportDuration = Math.max(0, Math.min(59, ticks));
        for (UUID id : this.viewers) {
            Player player = org.bukkit.Bukkit.getPlayer(id);
            if (player == null) continue;
            try {
                UserConnection connection = connection(player);
                if (connection == null || !connection.getProtocolInfo().getPipeline().contains(
                    com.viaversion.viaversion.protocols.v1_20to1_20_2.Protocol1_20To1_20_2.class)) continue;
                PacketWrapper smooth = PacketWrapper.create(
                    com.viaversion.viaversion.protocols.v1_20to1_20_2.packet.ClientboundPackets1_20_2.SET_ENTITY_DATA, connection);
                smooth.write(Types.VAR_INT, this.entityId);
                List<EntityData> data = new ArrayList<>();
                data.add(new EntityData(10, com.viaversion.viaversion.api.type.types.version.Types1_20_2.ENTITY_DATA_TYPES.varIntType,
                    this.teleportDuration));
                smooth.write(com.viaversion.viaversion.api.type.types.version.Types1_20_2.ENTITY_DATA_LIST, data);
                smooth.scheduleSend(com.viaversion.viaversion.protocols.v1_20to1_20_2.Protocol1_20To1_20_2.class);
            } catch (Throwable t) {
                org.bukkit.Bukkit.getLogger().log(Level.WARNING, "[ParkourBeat] TextDisplay smoothing failed", t);
            }
        }
    }

    /** Плавно изменить масштаб за ticks тиков (у всех, кто видит). */
    public void setScale(float scale, int ticks) {
        this.scale = scale;
        this.sendToViewers(types -> {
            List<EntityData> data = new ArrayList<>();
            data.add(new EntityData(8, types.varIntType, 0));
            data.add(new EntityData(9, types.varIntType, Math.max(0, ticks)));
            data.add(new EntityData(SCALE, types.vector3FType, new Vector3f(scale, scale, scale)));
            return data;
        });
    }

    /** Прозрачность текста 0..255. */
    public void setOpacity(int alpha) {
        byte value = alpha >= 255 ? (byte) -1 : (byte) Math.max(0, alpha);
        this.sendToViewers(types -> {
            List<EntityData> data = new ArrayList<>();
            data.add(new EntityData(TEXT_OPACITY, types.byteType, value));
            return data;
        });
    }

    private void sendToViewers(@NonNull java.util.function.Function<EntityDataTypes1_19_4, List<EntityData>> builder) {
        for (UUID id : this.viewers) {
            Player player = org.bukkit.Bukkit.getPlayer(id);
            if (player == null) continue;
            try {
                UserConnection connection = connection(player);
                if (connection == null) continue;
                PacketWrapper wrapper = PacketWrapper.create(ClientboundPackets1_19_4.SET_ENTITY_DATA, connection);
                wrapper.write(Types.VAR_INT, this.entityId);
                wrapper.write(Types1_19_4.ENTITY_DATA_LIST, builder.apply(Types1_19_4.ENTITY_DATA_TYPES));
                wrapper.scheduleSend(Protocol1_19_3To1_19_4.class);
            } catch (Throwable t) {
                org.bukkit.Bukkit.getLogger().log(Level.WARNING, "[ParkourBeat] TextDisplay data failed", t);
            }
        }
    }

    /** Есть ли вообще ViaVersion на сервере. */
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

    @javax.annotation.Nullable
    private static UserConnection connection(@NonNull Player player) {
        if (!isAvailable()) return null;
        UserConnection connection = Via.getManager().getConnectionManager().getConnectedClient(player.getUniqueId());
        if (connection == null || connection.getProtocolInfo() == null || connection.getProtocolInfo().getPipeline() == null) {
            return null;
        }
        return connection.getProtocolInfo().getPipeline().contains(Protocol1_19_3To1_19_4.class) ? connection : null;
    }

    /** Увидит ли игрок такую голограмму (клиент 1.19.4+ через ViaVersion). */
    public static boolean canShowTo(@NonNull Player player) {
        try {
            return connection(player) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isShownTo(@NonNull Player player) {
        return this.viewers.contains(player.getUniqueId());
    }

    public void show(@NonNull Player player) {
        try {
            UserConnection connection = connection(player);
            if (connection == null) return;

            PacketWrapper spawn = PacketWrapper.create(ClientboundPackets1_19_4.ADD_ENTITY, connection);
            spawn.write(Types.VAR_INT, this.entityId);
            spawn.write(Types.UUID, this.uuid);
            spawn.write(Types.VAR_INT, EntityTypes1_19_4.TEXT_DISPLAY.getId());
            spawn.write(Types.DOUBLE, this.location.getX());
            spawn.write(Types.DOUBLE, this.location.getY());
            spawn.write(Types.DOUBLE, this.location.getZ());
            byte yaw = (byte) ((this.location.getYaw() + YAW_OFFSET) * 256.0F / 360.0F);
            spawn.write(Types.BYTE, (byte) 0);   // pitch
            spawn.write(Types.BYTE, yaw);        // yaw
            spawn.write(Types.BYTE, yaw);        // head yaw
            spawn.write(Types.VAR_INT, 0);       // data
            spawn.write(Types.SHORT, (short) 0);
            spawn.write(Types.SHORT, (short) 0);
            spawn.write(Types.SHORT, (short) 0);
            spawn.scheduleSend(Protocol1_19_3To1_19_4.class);

            this.sendData(connection, true);
            if (this.teleportDuration > 0 && connection.getProtocolInfo().getPipeline().contains(
                com.viaversion.viaversion.protocols.v1_20to1_20_2.Protocol1_20To1_20_2.class)) {
                // Плавность перемещения появилась в 1.20.2 (индекс 10 у Display).
                PacketWrapper smooth = PacketWrapper.create(
                    com.viaversion.viaversion.protocols.v1_20to1_20_2.packet.ClientboundPackets1_20_2.SET_ENTITY_DATA, connection);
                smooth.write(Types.VAR_INT, this.entityId);
                List<EntityData> data = new ArrayList<>();
                data.add(new EntityData(10, com.viaversion.viaversion.api.type.types.version.Types1_20_2.ENTITY_DATA_TYPES.varIntType,
                    this.teleportDuration));
                smooth.write(com.viaversion.viaversion.api.type.types.version.Types1_20_2.ENTITY_DATA_LIST, data);
                smooth.scheduleSend(com.viaversion.viaversion.protocols.v1_20to1_20_2.Protocol1_20To1_20_2.class);
            }
            this.viewers.add(player.getUniqueId());
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().log(Level.WARNING, "[ParkourBeat] TextDisplay show failed", t);
        }
    }

    public void hide(@NonNull Player player) {
        if (this.vehicle != null) {
            java.util.Set<Integer> set = PASSENGERS.get(this.vehicle);
            if (set != null) {
                set.remove(this.entityId);
                if (set.isEmpty()) PASSENGERS.remove(this.vehicle);
            }
            this.vehicle = null;
        }
        if (!this.viewers.remove(player.getUniqueId())) return;
        try {
            UserConnection connection = connection(player);
            if (connection == null) return;
            PacketWrapper remove = PacketWrapper.create(ClientboundPackets1_19_4.REMOVE_ENTITIES, connection);
            remove.write(Types.VAR_INT_ARRAY_PRIMITIVE, new int[]{this.entityId});
            remove.scheduleSend(Protocol1_19_3To1_19_4.class);
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().log(Level.WARNING, "[ParkourBeat] TextDisplay hide failed", t);
        }
    }

    /** Игрок сменил мир/вышел: клиент сам стёр сущность, просто забываем его. */
    public void forget(@NonNull UUID player) {
        this.viewers.remove(player);
    }

    /** Новый текст - сразу у всех, кто видит. */
    public void setText(@NonNull Component text) {
        this.text = text;
        for (UUID id : this.viewers) {
            Player player = org.bukkit.Bukkit.getPlayer(id);
            if (player == null) {
                this.viewers.remove(id);
                continue;
            }
            try {
                UserConnection connection = connection(player);
                if (connection != null) this.sendData(connection, false);
            } catch (Throwable t) {
                org.bukkit.Bukkit.getLogger().log(Level.WARNING, "[ParkourBeat] TextDisplay update failed", t);
            }
        }
    }

    private void sendData(@NonNull UserConnection connection, boolean full) throws Exception {
        EntityDataTypes1_19_4 types = Types1_19_4.ENTITY_DATA_TYPES;
        List<EntityData> data = new ArrayList<>();
        data.add(new EntityData(TEXT, types.componentType,
            JsonParser.parseString(GsonComponentSerializer.gson().serialize(this.text))));
        if (full) {
            data.add(new EntityData(SCALE, types.vector3FType, new Vector3f(this.scale, this.scale, this.scale)));
            data.add(new EntityData(TRANSLATION, types.vector3FType, new Vector3f(this.tx, this.ty, this.tz)));
            data.add(new EntityData(BILLBOARD, types.byteType, this.billboard));
            data.add(new EntityData(VIEW_RANGE, types.floatType, 1.5F));
            data.add(new EntityData(LINE_WIDTH, types.varIntType, 400));
            data.add(new EntityData(BACKGROUND, types.varIntType, this.background));
            data.add(new EntityData(TEXT_OPACITY, types.byteType, (byte) -1));
            data.add(new EntityData(STYLE, types.byteType, (byte) (STYLE_SHADOW | (this.seeThrough ? 0x02 : 0))));
        }
        PacketWrapper wrapper = PacketWrapper.create(ClientboundPackets1_19_4.SET_ENTITY_DATA, connection);
        wrapper.write(Types.VAR_INT, this.entityId);
        wrapper.write(Types1_19_4.ENTITY_DATA_LIST, data);
        wrapper.scheduleSend(Protocol1_19_3To1_19_4.class);
    }
}
