package ru.sortix.parkourbeat.twod;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import lombok.NonNull;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * СЛУЖЕБНЫЕ СУЩНОСТИ ЗАБЕГА ВИДИТ ТОЛЬКО ЕГО ХОЗЯИН.
 * <p>
 * Кубик, камера и лодка существуют на сервере по-настоящему, поэтому по умолчанию их
 * видят все, кто рядом: на общем уровне игроки наблюдали чужие кубики, летящие сквозь
 * трассу. Монеток это не касается - они общие для уровня и должны быть видны всем.
 * <p>
 * Спрятать сущность средствами Bukkit на старых версиях нельзя ({@code hideEntity}
 * появился только в 1.18), поэтому пакет появления просто не отправляется чужим
 * клиентам. Это надёжнее скрытия: клиент о сущности вообще не узнаёт, и мигать
 * ей нечем.
 */
public class TwoDVisibility {

    private final @NonNull ParkourBeat plugin;
    /** Сущность -> кому её видно. */
    private final Map<Integer, UUID> owners = new ConcurrentHashMap<>();
    private @Nullable PacketAdapter adapter;

    public TwoDVisibility(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.register();
    }

    private void register() {
        List<PacketType> types = new ArrayList<>();
        addType(types, "SPAWN_ENTITY");
        addType(types, "SPAWN_ENTITY_LIVING");
        addType(types, "NAMED_ENTITY_SPAWN");
        if (types.isEmpty()) return;

        try {
            PacketAdapter created = new PacketAdapter(this.plugin, types.toArray(new PacketType[0])) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    try {
                        Player viewer = event.getPlayer();
                        if (viewer == null) return;

                        int entityId = event.getPacket().getIntegers().read(0);
                        UUID owner = TwoDVisibility.this.owners.get(entityId);
                        if (owner == null) return;
                        if (owner.equals(viewer.getUniqueId())) return;

                        event.setCancelled(true);
                    } catch (Throwable ignored) {
                    }
                }
            };
            ProtocolLibrary.getProtocolManager().addPacketListener(created);
            this.adapter = created;
        } catch (Throwable t) {
            this.plugin.getLogger().warning(
                "2D: не удалось скрыть служебные сущности от чужих игроков: " + t);
        }
    }

    private static void addType(@NonNull List<PacketType> types, @NonNull String name) {
        try {
            Field field = PacketType.Play.Server.class.getField(name);
            Object value = field.get(null);
            if (value instanceof PacketType type && !types.contains(type)) types.add(type);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Закрепить сущность за игроком: остальные её больше не увидят.
     * <p>
     * Тем, кто уже успел её получить, отправляем удаление: сущность могла родиться
     * раньше, чем мы о ней сказали.
     */
    public void own(@Nullable Entity entity, @NonNull Player owner) {
        if (entity == null) return;
        this.owners.put(entity.getEntityId(), owner.getUniqueId());
        this.destroyForOthers(entity, owner);
    }

    public void release(@Nullable Entity entity) {
        if (entity == null) return;
        this.owners.remove(entity.getEntityId());
    }

    private void destroyForOthers(@NonNull Entity entity, @NonNull Player owner) {
        try {
            com.comphenix.protocol.ProtocolManager manager = ProtocolLibrary.getProtocolManager();

            com.comphenix.protocol.events.PacketContainer packet =
                manager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            try {
                packet.getIntLists().write(0, java.util.Collections.singletonList(entity.getEntityId()));
            } catch (Throwable t) {
                packet.getIntegerArrays().write(0, new int[]{entity.getEntityId()});
            }

            for (Player viewer : entity.getWorld().getPlayers()) {
                if (viewer.getUniqueId().equals(owner.getUniqueId())) continue;
                manager.sendServerPacket(viewer, packet);
            }
        } catch (Throwable ignored) {
        }
    }

    public void disable() {
        this.owners.clear();
        if (this.adapter == null) return;
        try {
            ProtocolLibrary.getProtocolManager().removePacketListener(this.adapter);
        } catch (Throwable ignored) {
        }
        this.adapter = null;
    }
}
