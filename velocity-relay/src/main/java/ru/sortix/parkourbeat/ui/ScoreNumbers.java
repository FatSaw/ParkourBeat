package ru.sortix.parkourbeat.ui;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.libs.gson.JsonParser;
import com.viaversion.viaversion.protocols.v1_20_2to1_20_3.Protocol1_20_2To1_20_3;
import com.viaversion.viaversion.protocols.v1_20_2to1_20_3.packet.ClientboundPackets1_20_3;
import com.viaversion.viaversion.util.ComponentUtil;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Убирает красные цифры справа в табло у игроков на 1.20.3+.
 * <p>
 * С 1.20.3 в пакете объектива есть поле NumberFormat. Сервер 1.16.5 его не знает,
 * поэтому сразу после каждого обновления табло отправляется то же обновление в формате
 * 1.20.3 (через ViaVersion, начиная с шага 1.20.2->1.20.3) с NumberFormat = blank -
 * клиент сам перестаёт рисовать числа. Сверено с ViaVersion 5.11.0.
 * <p>
 * Любая ошибка ловится и пакет не уходит. Выключить: {@link #ENABLED}.
 */
public final class ScoreNumbers {
    private ScoreNumbers() {
    }

    public static volatile boolean ENABLED = true;
    private static final byte METHOD_UPDATE = 2;
    private static final int RENDER_INTEGER = 0;
    private static final int FORMAT_BLANK = 0;

    public static void hide(@NonNull Player player, @NonNull String objective, @NonNull Component title) {
        if (!ENABLED || !PacketHologram.isAvailable()) return;
        try {
            UserConnection connection = Via.getManager().getConnectionManager().getConnectedClient(player.getUniqueId());
            if (connection == null || connection.getProtocolInfo() == null
                || connection.getProtocolInfo().getPipeline() == null
                || !connection.getProtocolInfo().getPipeline().contains(Protocol1_20_2To1_20_3.class)) {
                return;
            }
            PacketWrapper wrapper = PacketWrapper.create(ClientboundPackets1_20_3.SET_OBJECTIVE, connection);
            wrapper.write(Types.STRING, objective);
            wrapper.write(Types.BYTE, METHOD_UPDATE);
            wrapper.write(Types.TAG, ComponentUtil.jsonToTag(JsonParser.parseString(GsonComponentSerializer.gson().serialize(title))));
            wrapper.write(Types.VAR_INT, RENDER_INTEGER);
            wrapper.write(Types.BOOLEAN, true);
            wrapper.write(Types.VAR_INT, FORMAT_BLANK);
            wrapper.scheduleSend(Protocol1_20_2To1_20_3.class);
        } catch (Throwable ignored) {
        }
    }
}
