package ru.sortix.parkourbeat.digger;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import lombok.NonNull;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * ЛИЧНЫЕ СУЩНОСТИ.
 * <p>
 * Панели забега - обычные стойки брони, а значит их видит весь сервер. На турнире,
 * где по одному тоннелю едут тридцать человек, каждый увидел бы вокруг себя тридцать
 * чужих счётчиков. Поэтому пакеты о таких сущностях выпускаются только их владельцу,
 * а для всех остальных сущность просто не существует.
 * <p>
 * Скрытие идёт на уровне пакетов, а не через API: скрывать произвольные сущности
 * от отдельного игрока Bukkit научился много позже 1.16.5, и другого пути тут нет.
 * <p>
 * Без ProtocolLib панели останутся общими - режим не ломается, но на турнир их лучше
 * выключить: {@code /pb digger set holograms false}.
 */
public class DiggerEntityHider {

    /** id сущности - владелец. Пишется из основного потока, читается из сетевого. */
    private static final Map<Integer, UUID> OWNERS = new ConcurrentHashMap<>();

    /** Пакеты, у которых id сущности лежит первым целым числом. */
    private static final String[] ENTITY_PACKETS = {
        "SPAWN_ENTITY_LIVING",
        "SPAWN_ENTITY",
        "ENTITY_METADATA",
        "REL_ENTITY_MOVE",
        "REL_ENTITY_MOVE_LOOK",
        "ENTITY_LOOK",
        "ENTITY_TELEPORT",
        "ENTITY_VELOCITY",
        "ENTITY_HEAD_ROTATION",
        "ENTITY_EQUIPMENT",
        "ENTITY_STATUS",
        "ENTITY_EFFECT",
    };

    // ==================== ПОЗИЦИЯ КАЖДЫЙ ТИК ====================

    /**
     * СУЩНОСТИ, ЧЬЮ ПОЗИЦИЮ ВЛАДЕЛЬЦУ ШЛЁМ МЫ САМИ.
     * <p>
     * На 1.16.5 трекер сервера шлёт движение стойки брони раз в ТРИ такта. Камера
     * игрока сидит на такой стойке, значит получает новую позицию раз в 150 мс, и
     * клиент тянет её три такта до следующей. Пока пакеты приходят ровно - едет гладко.
     * Стоит одному опоздать на пару десятков миллисекунд, и камера стоит, а потом
     * прыгает: это и есть «дёрганые движения» у игрока с пингом.
     * <p>
     * Поэтому для таких сущностей пакеты движения трекера владельцу НЕ выпускаются,
     * а вместо них каждый такт уходит абсолютная позиция. Абсолютная, а не сдвиг:
     * сдвиги копят ошибку, если хоть один потеряется или смешается с чужими, а
     * телепорт каждый раз ставит точку заново. Остальные игроки получают обычный
     * поток трекера - им эта сущность всё равно не видна или не важна.
     */
    private static final Map<Integer, UUID> DRIVEN = new ConcurrentHashMap<>();

    private static volatile @Nullable ProtocolManager sharedProtocol = null;

    /** Отключается при первой же ошибке отправки: тогда живём на пакетах трекера. */
    private static volatile boolean pushWorks = true;

    public static void drive(int entityId, @NonNull UUID owner) {
        if (sharedProtocol == null || !pushWorks) return;
        DRIVEN.put(entityId, owner);
    }

    public static void undrive(int entityId) {
        DRIVEN.remove(entityId);
    }

    /**
     * Отправить владельцу абсолютную позицию сущности в обход слушателей.
     *
     * @return false, если отправить не вышло - тогда сущность снимается с ручного
     *         управления и дальше её ведёт обычный трекер
     */
    public static boolean pushPosition(@NonNull Player receiver, int entityId,
                                       double x, double y, double z, float yaw, float pitch) {
        ProtocolManager protocol = sharedProtocol;
        if (protocol == null || !pushWorks || !DRIVEN.containsKey(entityId)) return false;
        try {
            com.comphenix.protocol.events.PacketContainer packet =
                protocol.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
            packet.getIntegers().write(0, entityId);
            packet.getDoubles().write(0, x).write(1, y).write(2, z);
            packet.getBytes()
                .write(0, (byte) (int) (yaw * 256.0F / 360.0F))
                .write(1, (byte) (int) (pitch * 256.0F / 360.0F));
            packet.getBooleans().writeSafely(0, false);
            // filters = false: пакет не проходит через наши же слушатели и не режется.
            protocol.sendServerPacket(receiver, packet, false);
            return true;
        } catch (Throwable t) {
            pushWorks = false;
            DRIVEN.clear();
            return false;
        }
    }

    private static boolean isMovement(@NonNull PacketType type) {
        return type == PacketType.Play.Server.REL_ENTITY_MOVE
            || type == PacketType.Play.Server.REL_ENTITY_MOVE_LOOK
            || type == PacketType.Play.Server.ENTITY_TELEPORT;
    }

    public static void own(int entityId, @NonNull UUID owner) {
        OWNERS.put(entityId, owner);
    }

    public static void forget(int entityId) {
        OWNERS.remove(entityId);
        DRIVEN.remove(entityId);
    }

    public static void forgetAll(@NonNull UUID owner) {
        OWNERS.values().removeIf(owner::equals);
        DRIVEN.values().removeIf(owner::equals);
    }

    private final @NonNull ParkourBeat plugin;
    private final @Nullable ProtocolManager protocolManager;
    private final List<PacketAdapter> adapters = new ArrayList<>();

    public DiggerEntityHider(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;

        ProtocolManager protocol = null;
        try {
            protocol = ProtocolLibrary.getProtocolManager();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                "Копатель: ProtocolLib недоступен, панели забега увидят все игроки", t);
        }
        this.protocolManager = protocol;
        sharedProtocol = protocol;
        if (protocol == null) return;

        for (String name : ENTITY_PACKETS) {
            PacketType type = type(name);
            if (type == null) continue;
            try {
                PacketAdapter adapter = new PacketAdapter(plugin, type) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        try {
                            DiggerEntityHider.this.filter(event);
                        } catch (Throwable ignored) {
                            // Скрытие панелей не имеет права ронять отправку пакетов.
                        }
                    }
                };
                protocol.addPacketListener(adapter);
                this.adapters.add(adapter);
            } catch (Throwable ignored) {
            }
        }
    }

    @Nullable
    private static PacketType type(@NonNull String name) {
        try {
            return (PacketType) PacketType.Play.Server.class.getField(name).get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private void filter(@NonNull PacketEvent event) {
        if (OWNERS.isEmpty() && DRIVEN.isEmpty()) return;

        Player receiver = event.getPlayer();
        if (receiver == null) return;

        Integer entityId = event.getPacket().getIntegers().readSafely(0);
        if (entityId == null) return;

        // Движение ведомой сущности её владельцу шлём мы сами, каждый такт.
        UUID driver = DRIVEN.get(entityId);
        if (driver != null && pushWorks && driver.equals(receiver.getUniqueId())
            && isMovement(event.getPacketType())) {
            event.setCancelled(true);
            return;
        }

        UUID owner = OWNERS.get(entityId);
        if (owner == null) return;
        if (owner.equals(receiver.getUniqueId())) return;

        // Чужая панель. Для этого игрока её не существует.
        event.setCancelled(true);
    }

    public void disable() {
        OWNERS.clear();
        DRIVEN.clear();
        sharedProtocol = null;
        if (this.protocolManager == null) return;
        for (PacketAdapter adapter : this.adapters) {
            try {
                this.protocolManager.removePacketListener(adapter);
            } catch (Throwable ignored) {
            }
        }
        this.adapters.clear();
    }
}
