package ru.sortix.parkourbeat.player.music.platform;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_20_2to1_20_3.Protocol1_20_2To1_20_3;
import com.viaversion.viaversion.protocols.v1_20_2to1_20_3.packet.ClientboundPackets1_20_3;
import lombok.NonNull;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * РЕСУРСПАКИ СЛОЯМИ (клиенты 1.20.3+, у нас все на 26.2).
 * <p>
 * Клиент 1.20.3+ держит СТЕК паков и умеет снимать их по одному (по UUID). Сервер 1.16.5
 * этого не знает: его единственный пакет «поставь пак» ViaVersion переводит в
 * «СНЯТЬ ВСЕ паки» + «поставить этот». Поэтому каждый заход на уровень сносил всё,
 * а выход в лобби заново качал и применял базовый пак.
 * <p>
 * Здесь пакеты 1.20.3 уходят напрямую через ViaVersion, минуя этот перевод:
 * <ul>
 *   <li><b>CORE</b> - базовый пак сервера. Ставится один раз и больше не снимается.</li>
 *   <li><b>LEVEL</b> - пак уровня (музыка + его текстуры), лежит поверх базового.
 *       Новый уровень заменяет его (тот же UUID), выход в лобби снимает ТОЛЬКО его -
 *       без скачивания, базовый пак остаётся на месте.</li>
 * </ul>
 * Старые клиенты и сервер без ViaVersion - прежний путь. Выключить: {@link #ENABLED}.
 */
public final class PackLayers {
    private PackLayers() {
    }

    public static volatile boolean ENABLED = true;

    public static final UUID CORE_ID = UUID.nameUUIDFromBytes("parkourbeat:core".getBytes(StandardCharsets.UTF_8));
    public static final UUID LEVEL_ID = UUID.nameUUIDFromBytes("parkourbeat:level".getBytes(StandardCharsets.UTF_8));

    /** У кого базовый слой уже стоит. */
    private static final Set<UUID> CORE = ConcurrentHashMap.newKeySet();
    /** У кого сейчас стоит слой уровня. */
    private static final Set<UUID> LEVEL = ConcurrentHashMap.newKeySet();

    /**
     * Отправить пак слоем.
     *
     * @param coreTrack  это базовый пак (лобби), а не пак уровня
     * @param markLoaded вызвать, если загружать нечего (базовый уже стоит) - ответа клиента не будет
     * @return false - слои недоступны, отправь по-старому
     */
    public static boolean send(@NonNull Player player, @NonNull String url, byte[] sha1, boolean coreTrack,
                               @NonNull Runnable markLoaded) {
        if (!ENABLED) return false;
        UserConnection connection = connection(player);
        if (connection == null) return false;
        UUID id = player.getUniqueId();
        try {
            if (coreTrack) {
                if (LEVEL.remove(id)) pop(connection, LEVEL_ID);
                if (CORE.contains(id)) {
                    // Базовый пак уже у клиента: снят только слой уровня, качать нечего.
                    markLoaded.run();
                    return true;
                }
                push(connection, CORE_ID, url, sha1);
                CORE.add(id);
                return true;
            }
            push(connection, LEVEL_ID, url, sha1);
            LEVEL.add(id);
            return true;
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().log(Level.WARNING,
                "[ParkourBeat] layered resourcepack failed, using legacy send for " + player.getName(), t);
            return false;
        }
    }

    /** Снять только слой уровня (если он есть). */
    public static boolean popLevel(@NonNull Player player) {
        if (!ENABLED || !LEVEL.contains(player.getUniqueId())) return false;
        UserConnection connection = connection(player);
        if (connection == null) return false;
        try {
            pop(connection, LEVEL_ID);
            LEVEL.remove(player.getUniqueId());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void forget(@NonNull UUID player) {
        CORE.remove(player);
        LEVEL.remove(player);
    }

    private static void push(@NonNull UserConnection connection, @NonNull UUID id, @NonNull String url, byte[] sha1)
        throws Exception {
        PacketWrapper push = PacketWrapper.create(ClientboundPackets1_20_3.RESOURCE_PACK_PUSH, connection);
        push.write(Types.UUID, id);
        push.write(Types.STRING, url);
        push.write(Types.STRING, hex(sha1));
        push.write(Types.BOOLEAN, false);              // не обязательный - как и раньше
        push.write(Types.TRUSTED_OPTIONAL_TAG, null);  // без своего текста запроса
        push.scheduleSend(Protocol1_20_2To1_20_3.class);
    }

    private static void pop(@NonNull UserConnection connection, @NonNull UUID id) throws Exception {
        PacketWrapper pop = PacketWrapper.create(ClientboundPackets1_20_3.RESOURCE_PACK_POP, connection);
        pop.write(Types.OPTIONAL_UUID, id);
        pop.scheduleSend(Protocol1_20_2To1_20_3.class);
    }

    private static String hex(byte[] sha1) {
        if (sha1 == null) return "";
        StringBuilder out = new StringBuilder(sha1.length * 2);
        for (byte b : sha1) out.append(String.format("%02x", b & 0xFF));
        return out.toString();
    }

    private static UserConnection connection(@NonNull Player player) {
        try {
            Class.forName("com.viaversion.viaversion.protocols.v1_20_2to1_20_3.Protocol1_20_2To1_20_3");
        } catch (Throwable t) {
            return null;
        }
        UserConnection connection = Via.getManager().getConnectionManager().getConnectedClient(player.getUniqueId());
        if (connection == null || connection.getProtocolInfo() == null || connection.getProtocolInfo().getPipeline() == null) {
            return null;
        }
        return connection.getProtocolInfo().getPipeline().contains(Protocol1_20_2To1_20_3.class) ? connection : null;
    }
}
