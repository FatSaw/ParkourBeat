package ru.sortix.parkourbeat.digger;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * ЗАЩИТА СЛОМАННЫХ РУД ОТ ВОЗВРАТА.
 * <p>
 * Разрушение руды отменяется на сервере, чтобы блок остался в мире для остальных
 * участников заезда. Но отменённое разрушение заставляет сервер немедленно выслать
 * игроку настоящий блок обратно - и руда, которую он только что снёс, мигает у него
 * на месте.
 * <p>
 * Вернуть руду на экран может три разных пакета, и каждый обрабатывается по-своему:
 * <ol>
 *     <li>ОДИН БЛОК. Пакет не выпускается вовсе: позиция известна точно, и решение
 *         принимается на месте.</li>
 *     <li>СЕКЦИЯ ЧАНКА. Формат этого пакета менялся между версиями, и разбирать его
 *         руками - гарантированная поломка на следующем обновлении. Поэтому пакет
 *         пропускается как есть, а следующим тиком призраки расставляются заново.</li>
 *     <li>ЧАНК ЦЕЛИКОМ. То же самое: пропустить и восстановить призраков после.</li>
 * </ol>
 * ProtocolLib необязателен: без него всё работает по-прежнему, только с мигáнием
 * длиной в тик. Поэтому подключение обёрнуто и не роняет режим.
 */
public class DiggerBlockHider {

    private final @NonNull ParkourBeat plugin;
    private final @NonNull DiggerManager manager;
    private final @Nullable ProtocolManager protocolManager;
    private final List<PacketAdapter> adapters = new ArrayList<>();

    public DiggerBlockHider(@NonNull ParkourBeat plugin, @NonNull DiggerManager manager) {
        this.plugin = plugin;
        this.manager = manager;

        ProtocolManager protocol = null;
        try {
            protocol = ProtocolLibrary.getProtocolManager();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                "Копатель: ProtocolLib недоступен, сломанные руды будут мигать на тик", t);
        }
        this.protocolManager = protocol;
        if (protocol == null) return;

        // Точечное изменение блока: позиция известна, пакет можно просто не выпускать.
        this.register(protocol, PacketType.Play.Server.BLOCK_CHANGE, event -> {
            DiggerGame game = this.gameOf(event);
            if (game == null) return;

            BlockPosition position = event.getPacket().getBlockPositionModifier().read(0);
            if (position == null) return;

            if (!game.isHidden(position.getX(), position.getY(), position.getZ())) return;

            // ВОЗДУХ ПРОПУСКАЕМ ВСЕГДА. Прятать руду мы умеем только одним способом -
            // отправив игроку воздух на её месте. Если резать все пакеты подряд, под
            // нож попадает и наш собственный: руда тогда не исчезает никогда, ломать
            // её невозможно, и выглядит это как приваченный регион.
            try {
                com.comphenix.protocol.wrappers.WrappedBlockData data =
                    event.getPacket().getBlockData().readSafely(0);
                if (data != null && data.getType() == org.bukkit.Material.AIR) return;
            } catch (Throwable ignored) {
                // Не смогли прочитать блок - лучше пропустить пакет, чем залипнуть.
                return;
            }

            event.setCancelled(true);
        });

        // Пакеты пачками: разбирать не пытаемся, просто восстанавливаем призраков после.
        this.register(protocol, this.type("MULTI_BLOCK_CHANGE"), this::scheduleReassert);
        this.register(protocol, this.type("MAP_CHUNK"), this::scheduleReassert);
    }

    /**
     * Тип пакета по имени поля.
     * <p>
     * Имена типов между версиями иногда меняются, и прямая ссылка на отсутствующее
     * поле уронила бы весь перехватчик - вместе с ним и защиту у одиночного блока,
     * которая работает всегда.
     */
    @Nullable
    private PacketType type(@NonNull String name) {
        try {
            return (PacketType) PacketType.Play.Server.class.getField(name).get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private void register(@NonNull ProtocolManager protocol,
                          @Nullable PacketType type,
                          @NonNull Consumer<PacketEvent> handler
    ) {
        if (type == null) return;
        try {
            PacketAdapter adapter = new PacketAdapter(this.plugin, type) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    try {
                        handler.accept(event);
                    } catch (Throwable ignored) {
                        // Перехватчик не имеет права ронять отправку пакетов.
                    }
                }
            };
            protocol.addPacketListener(adapter);
            this.adapters.add(adapter);
        } catch (Throwable t) {
            this.plugin.getLogger().log(Level.WARNING,
                "Копатель: не удалось перехватить пакет " + type, t);
        }
    }

    @Nullable
    private DiggerGame gameOf(@NonNull PacketEvent event) {
        Player player = event.getPlayer();
        if (player == null) return null;
        DiggerGame game = this.manager.getGame(player);
        if (game == null || game.getState() != DiggerGame.State.RUNNING) return null;
        return game;
    }

    /**
     * Расставить призраков заново следующим тиком.
     * <p>
     * Пакет обрабатывается в сетевом потоке, поэтому сама отправка уходит в основной:
     * трогать состояние забега откуда попало нельзя.
     */
    private void scheduleReassert(@NonNull PacketEvent event) {
        DiggerGame game = this.gameOf(event);
        if (game == null || !game.hasHidden()) return;

        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (game.getState() == DiggerGame.State.RUNNING) game.reassertHidden();
        });
    }

    public void disable() {
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
