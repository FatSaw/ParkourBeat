package ru.sortix.parkourbeat.world;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

@UtilityClass
public class PerPlayerGlowSender {

    private final boolean AVAILABLE;
    private final byte GLOWING_BIT = 0x40;

    static {
        boolean available;
        try {
            Class.forName("com.comphenix.protocol.ProtocolLibrary");
            available = true;
        } catch (Throwable t) {
            available = false;
        }
        AVAILABLE = available;
    }

    public boolean isAvailable() {
        return AVAILABLE;
    }

    public void sendGlow(@NonNull Player viewer, @NonNull Entity entity, boolean glowing) {
        if (!AVAILABLE) return;
        if (!viewer.isOnline()) return;

        try {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            PacketContainer packet = pm.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, entity.getEntityId());

            WrappedDataWatcher watcher = new WrappedDataWatcher(entity);
            byte baseFlags = 0;
            WrappedWatchableObject existing = watcher.getWatchableObject(0);
            if (existing != null && existing.getValue() instanceof Byte) {
                baseFlags = (Byte) existing.getValue();
            }

            byte newFlags = glowing
                ? (byte) (baseFlags | GLOWING_BIT)
                : (byte) (baseFlags & ~GLOWING_BIT);

            WrappedDataWatcher.Serializer byteSerializer =
                WrappedDataWatcher.Registry.get(Byte.class);
            WrappedDataWatcher.WrappedDataWatcherObject flagsObject =
                new WrappedDataWatcher.WrappedDataWatcherObject(0, byteSerializer);

            List<WrappedWatchableObject> objects = new ArrayList<>();
            objects.add(new WrappedWatchableObject(flagsObject, newFlags));

            packet.getWatchableCollectionModifier().write(0, objects);

            pm.sendServerPacket(viewer, packet);
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING,
                "[ParkourBeat] PerPlayerGlowSender: не удалось отправить glow-пакет игроку "
                    + viewer.getName(), t);
        }
    }
}
