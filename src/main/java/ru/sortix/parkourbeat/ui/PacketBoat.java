package ru.sortix.parkourbeat.ui;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.entities.EntityTypes1_19_4;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.Protocol1_19_3To1_19_4;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.packet.ClientboundPackets1_19_4;
import lombok.NonNull;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Лодка, которая есть только у клиента одного игрока (через ViaVersion).
 * <p>
 * Нужна полёту в 2D: кубик-BlockDisplay садится в неё пассажиром ТОЖЕ у клиента,
 * поэтому они двигаются как одно целое и не «плавают» друг относительно друга.
 * Настоящая серверная лодка обновлялась трекером раз в 3 тика, а кубик - каждый тик,
 * отсюда и разъезд при нажатии пробела.
 */
public final class PacketBoat {
    /**
     * Высота дна лодки над её точкой: на эту высоту встаёт низ кубика. Больше - кубик
     * глубже в лодке (борта 0.5625). Ванильное место пассажира - 0.1875.
     */
    public static double SEAT_HEIGHT = 0.1875D;

    private final int entityId = ThreadLocalRandom.current().nextInt(40_000_000, 50_000_000);
    private final UUID viewer;
    private boolean shown;

    private PacketBoat(@NonNull Player viewer) {
        this.viewer = viewer.getUniqueId();
    }

    public static PacketBoat show(@NonNull Player viewer, double x, double y, double z, float yaw) {
        UserConnection connection = PacketBlockDisplay.connectionFor(viewer);
        if (connection == null) return null;
        PacketBoat boat = new PacketBoat(viewer);
        try {
            PacketWrapper spawn = PacketWrapper.create(ClientboundPackets1_19_4.ADD_ENTITY, connection);
            spawn.write(Types.VAR_INT, boat.entityId);
            spawn.write(Types.UUID, UUID.randomUUID());
            spawn.write(Types.VAR_INT, EntityTypes1_19_4.BOAT.getId());
            spawn.write(Types.DOUBLE, x);
            spawn.write(Types.DOUBLE, y);
            spawn.write(Types.DOUBLE, z);
            byte angle = angle(yaw);
            spawn.write(Types.BYTE, (byte) 0);
            spawn.write(Types.BYTE, angle);
            spawn.write(Types.BYTE, angle);
            spawn.write(Types.VAR_INT, 0);
            spawn.write(Types.SHORT, (short) 0);
            spawn.write(Types.SHORT, (short) 0);
            spawn.write(Types.SHORT, (short) 0);
            spawn.scheduleSend(Protocol1_19_3To1_19_4.class);
            boat.shown = true;
            return boat;
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().log(Level.WARNING, "[ParkourBeat] packet boat show failed", t);
            return null;
        }
    }

    public int getEntityId() {
        return this.entityId;
    }

    public void teleport(double x, double y, double z, float yaw) {
        UserConnection connection = this.connection();
        if (connection == null) return;
        try {
            PacketWrapper move = PacketWrapper.create(ClientboundPackets1_19_4.TELEPORT_ENTITY, connection);
            move.write(Types.VAR_INT, this.entityId);
            move.write(Types.DOUBLE, x);
            move.write(Types.DOUBLE, y);
            move.write(Types.DOUBLE, z);
            move.write(Types.BYTE, angle(yaw));
            move.write(Types.BYTE, (byte) 0);
            move.write(Types.BOOLEAN, false);
            move.scheduleSend(Protocol1_19_3To1_19_4.class);
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().log(Level.WARNING, "[ParkourBeat] packet boat move failed", t);
        }
    }

    public void hide() {
        if (!this.shown) return;
        this.shown = false;
        UserConnection connection = this.connection0();
        if (connection == null) return;
        try {
            PacketWrapper remove = PacketWrapper.create(ClientboundPackets1_19_4.REMOVE_ENTITIES, connection);
            remove.write(Types.VAR_INT_ARRAY_PRIMITIVE, new int[]{this.entityId});
            remove.scheduleSend(Protocol1_19_3To1_19_4.class);
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().log(Level.WARNING, "[ParkourBeat] packet boat hide failed", t);
        }
    }

    private UserConnection connection() {
        return this.shown ? this.connection0() : null;
    }

    private UserConnection connection0() {
        Player player = org.bukkit.Bukkit.getPlayer(this.viewer);
        return player == null ? null : PacketBlockDisplay.connectionFor(player);
    }

    private static byte angle(float degrees) {
        return (byte) (degrees * 256.0F / 360.0F);
    }
}
