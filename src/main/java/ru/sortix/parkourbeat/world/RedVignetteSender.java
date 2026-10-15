// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/world/RedVignetteSender.java
package ru.sortix.parkourbeat.world;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class RedVignetteSender {

    private static final PacketType PACKET_TYPE;
    private static final boolean IS_1_17_PLUS;

    // Храним активные анимации, чтобы они не конфликтовали, если игрок прыгает часто
    private static final Map<UUID, BukkitTask> ACTIVE_FLASHES = new ConcurrentHashMap<>();

    static {
        PacketType resolvedType = null;
        boolean is17 = false;

        try {
            Object field = PacketType.Play.Server.class.getField("SET_BORDER_WARNING_DISTANCE").get(null);
            if (field instanceof PacketType) {
                PacketType pt = (PacketType) field;
                if (pt.isSupported()) {
                    resolvedType = pt;
                    is17 = true;
                }
            }
        } catch (Throwable ignored) {}

        if (resolvedType == null) {
            try {
                Object field = PacketType.Play.Server.class.getField("WORLD_BORDER").get(null);
                if (field instanceof PacketType) {
                    PacketType pt = (PacketType) field;
                    if (pt.isSupported()) {
                        resolvedType = pt;
                    }
                }
            } catch (Throwable ignored) {}
        }

        PACKET_TYPE = resolvedType;
        IS_1_17_PLUS = is17;
    }

    public static void flash(Plugin plugin, Player player) {
        if (!player.isOnline()) return;
        if (PACKET_TYPE == null) return;

        UUID uuid = player.getUniqueId();

        // Отменяем предыдущую анимацию затухания, если она ещё идёт
        BukkitTask existing = ACTIVE_FLASHES.remove(uuid);
        if (existing != null) existing.cancel();

        // Максимальная вспышка сразу же
        sendWarningDistance(player, 1000000000);

        BukkitTask task = new BukkitRunnable() {
            // intensity идет от 1.0 (очень красное) до 0.0 (нет эффекта)
            double intensity = 1.0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    ACTIVE_FLASHES.remove(uuid, this);
                    this.cancel();
                    return;
                }

                // Шаг затухания. 0.05 = 20 шагов до нуля (ровно 1 секунда анимации)
                intensity -= 0.05;

                // Завершение анимации
                if (intensity <= 0.0) {
                    sendWarningDistance(player, 5); // возвращаем дефолт
                    ACTIVE_FLASHES.remove(uuid, this);
                    this.cancel();
                    return;
                }

                // В майнкрафте: intensity = 1.0 - (расстояние_до_границы / дистанция_предупреждения)
                // Отсюда: дистанция_предупреждения = расстояние_до_границы / (1.0 - intensity)
                // Дефолтная граница всегда примерно в 30 миллионах блоков от игрока.
                // Ограничиваем сверху 1 млрд, чтобы не превысить лимит Integer.MAX_VALUE.
                int warningBlocks = (int) Math.min(1000000000.0, 30000000.0 / (1.00001 - intensity));

                sendWarningDistance(player, warningBlocks);
            }
        }.runTaskTimerAsynchronously(plugin, 2L, 1L); // Держим максимальную вспышку 2 тика, потом плавно затухаем

        ACTIVE_FLASHES.put(uuid, task);
    }

    private static void sendWarningDistance(Player player, int warningBlocks) {
        try {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            PacketContainer packet = pm.createPacket(PACKET_TYPE);

            if (IS_1_17_PLUS) {
                // В 1.17+ это короткий пакет, дистанция пишется в 0-й индекс
                packet.getIntegers().write(0, warningBlocks);
            } else {
                // В 1.8 - 1.16.5 это пакет WORLD_BORDER, ставим ему Action = SET_WARNING_BLOCKS
                packet.getWorldBorderActions().write(0, EnumWrappers.WorldBorderAction.SET_WARNING_BLOCKS);

                // В 1.16.5 есть 3 int-поля: maxSize, warningTime, warningBlocks.
                // Соответственно, warningBlocks — это строго индекс 2
                packet.getIntegers().write(2, warningBlocks);
            }

            pm.sendServerPacket(player, packet);
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING, "[ParkourBeat] Failed to send red vignette packet", t);
        }
    }
}
