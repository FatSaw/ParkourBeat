package ru.sortix.parkourbeat.utils.text;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ОЧЕРЕДЬ НА АКТИОНБАР.
 * <p>
 * В актионбар пишут двое: постоянный предпросмотр редактора (проценты и таймкод) и
 * разовые уведомления от инструментов (точка добавлена, длина уровня изменилась,
 * монетка поставлена). Предпросмотр обновляется каждый тик, поэтому раньше он затирал
 * уведомление в тот же тик, в котором оно появлялось, и строитель его просто не видел.
 * <p>
 * Решение простое: уведомление помечает игрока занятым на пару секунд, а всё, что
 * пишется постоянно, на это время замолкает.
 */
public final class ActionBarPriority {
    private ActionBarPriority() {
    }

    /** Сколько уведомление держит актионбар за собой. */
    public static final long NOTICE_MILLIS = 2200L;

    private static final Map<UUID, Long> BUSY_UNTIL = new ConcurrentHashMap<>();

    /**
     * Отправить разовое уведомление и придержать актионбар за собой.
     */
    public static void notice(@NonNull Player player, @NonNull Component message) {
        BUSY_UNTIL.put(player.getUniqueId(), System.currentTimeMillis() + NOTICE_MILLIS);
        player.sendActionBar(message);
    }

    /**
     * Пометить, что игроку только что написали в актионбар мимо этого класса.
     */
    public static void notice(@NonNull Player player) {
        BUSY_UNTIL.put(player.getUniqueId(), System.currentTimeMillis() + NOTICE_MILLIS);
    }

    /**
     * @return true, если актионбар сейчас занят уведомлением и постоянному тексту
     * туда лезть нельзя
     */
    public static boolean isBusy(@NonNull Player player) {
        Long until = BUSY_UNTIL.get(player.getUniqueId());
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            BUSY_UNTIL.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public static void release(@NonNull Player player) {
        BUSY_UNTIL.remove(player.getUniqueId());
    }
}
