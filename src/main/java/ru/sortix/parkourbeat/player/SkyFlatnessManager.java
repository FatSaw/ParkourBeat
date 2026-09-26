package ru.sortix.parkourbeat.player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.SkyMode;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ПОЛНОЕ НЕБО НА ЛЮБОЙ ВЫСОТЕ.
 * <p>
 * Тёмную нижнюю половину неба рисует клиент, и решает он это сам. Смотрит он на две
 * вещи: считать ли мир ПЛОСКИМ и где сейчас камера. В неплоском мире горизонт стоит на
 * шестьдесят третьей высоте, и всё, что ниже, закрывается тёмной плоскостью пустоты.
 * В плоском горизонт опускается на дно мира, и небо полное везде.
 * <p>
 * Флаг плоскости клиент получает ровно один раз - в пакете входа в мир, - и берётся он
 * из {@code level.dat}. Отсюда и вся путаница в режимах: шаблонные уровни копируются
 * вместе с чужим {@code level.dat} и приезжают плоскими, а «Копатель» и 360 создаются
 * пустым миром и плоскими не считаются. Высотой площадки можно вылечить вторых, но не
 * первых: сколько ни опускай спавн на плоском уровне, небо там останется полным.
 * <p>
 * Поэтому флаг правится по дороге к клиенту. Это не обход механики, а тот же самый
 * флаг, просто выставленный по выбору строителя, а не по тому, из какого шаблона
 * случайно приехал мир.
 * <p>
 * РАБОТАЕТ ТОЛЬКО ТАМ, ГДЕ ВЫБОР БЫЛ СДЕЛАН. У уровня без записи в файле (а это все
 * уровни, созданные до появления настройки) пакет не трогается вовсе: они должны
 * выглядеть ровно так, как выглядели вчера.
 */
public class SkyFlatnessManager implements PluginManager, Listener {

    /**
     * ЧИСЛО ЛОГИЧЕСКИХ ЗНАЧЕНИЙ В ПАКЕТАХ 1.16.5 И МЕСТО ФЛАГА СРЕДИ НИХ.
     * <p>
     * Полей с именами в пакете нет - они обфусцированы, - поэтому обращаться приходится
     * по порядку объявления. В {@code PacketPlayOutRespawn} логических полей ровно три:
     * отладочный мир, плоскость, сохранять ли данные игрока. В
     * {@code PacketPlayOutLogin} их пять: хардкор, урезанная отладка, экран смерти,
     * отладочный мир, плоскость.
     * <p>
     * Порядок сверен по официальным маппингам Mojang для 1.16.5:
     * {@code ClientboundRespawnPacket} - dimensionType, dimension, seed, playerGameType,
     * previousPlayerGameType, isDebug, isFlat, keepAllPlayerData;
     * {@code ClientboundLoginPacket} - playerId, seed, hardcore, gameType,
     * previousGameType, levels, registryHolder, dimensionType, dimension, maxPlayers,
     * chunkRadius, reducedDebugInfo, showDeathScreen, isDebug, isFlat.
     * <p>
     * Количество проверяется перед записью. Это и есть защита от чужой версии: если
     * сервер окажется не 1.16.5 и порядок полей другой, число не совпадёт, и менеджер
     * просто ничего не сделает - вместо того, чтобы молча выставить игроку хардкор
     * вместо плоскости.
     */
    private static final int RESPAWN_BOOLEANS = 3;
    private static final int RESPAWN_FLAT_INDEX = 1;
    private static final int LOGIN_BOOLEANS = 5;
    private static final int LOGIN_FLAT_INDEX = 4;

    private final @NonNull ParkourBeat plugin;

    /**
     * Плоскость по имени мира.
     * <p>
     * Отдельная карта, а не поход в менеджер уровней: пакет уходит из сетевого потока,
     * а хранилище загруженных уровней - обычная карта, которую правит основной поток.
     * Читать её оттуда - это гонка ради одного логического значения.
     */
    private final Map<String, Boolean> flatByWorld = new ConcurrentHashMap<>();

    /**
     * Куда игрок едет прямо сейчас.
     * <p>
     * Пакет о смене мира уходит РАНЬШЕ, чем игрок оказывается в новом мире, поэтому
     * спросить {@code player.getWorld()} в момент отправки нельзя - он ответит про
     * старый мир. Зато событие телепорта происходит ещё раньше пакета и знает точку
     * назначения; её и запоминаем.
     */
    private final Map<UUID, String> pendingWorld = new ConcurrentHashMap<>();

    private final PacketAdapter adapter;

    public SkyFlatnessManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        this.adapter = new PacketAdapter(plugin,
            PacketType.Play.Server.RESPAWN, PacketType.Play.Server.LOGIN) {
            @Override
            public void onPacketSending(PacketEvent event) {
                SkyFlatnessManager.this.rewrite(event);
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(this.adapter);
    }

    // ==================== ЧТО ИЗВЕСТНО ПРО МИРЫ ====================

    /**
     * Запомнить выбор уровня. Вызывается при загрузке и создании уровня.
     *
     * @param skyMode null - выбора не было, пакеты этого мира не трогаются
     */
    public void remember(@NonNull World world, @Nullable SkyMode skyMode) {
        if (skyMode == null) {
            this.flatByWorld.remove(world.getName());
            return;
        }
        this.flatByWorld.put(world.getName(), skyMode.isFull());
    }

    /** Забыть мир: уровень выгружен. */
    public void forget(@NonNull World world) {
        this.flatByWorld.remove(world.getName());
    }

    // ==================== КУДА ЕДЕТ ИГРОК ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(@NonNull PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) return;
        this.pendingWorld.put(event.getPlayer().getUniqueId(), event.getTo().getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(@NonNull PlayerRespawnEvent event) {
        World world = event.getRespawnLocation().getWorld();
        if (world == null) return;
        this.pendingWorld.put(event.getPlayer().getUniqueId(), world.getName());
    }

    @EventHandler
    public void onQuit(@NonNull PlayerQuitEvent event) {
        this.pendingWorld.remove(event.getPlayer().getUniqueId());
    }

    // ==================== САМА ПОДМЕНА ====================

    private void rewrite(@NonNull PacketEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Boolean flat = this.flatFor(player);
        if (flat == null) return;

        try {
            StructureModifier<Boolean> booleans = event.getPacket().getBooleans();

            if (event.getPacketType() == PacketType.Play.Server.RESPAWN) {
                if (booleans.size() != RESPAWN_BOOLEANS) return;
                booleans.write(RESPAWN_FLAT_INDEX, flat);
                return;
            }

            if (booleans.size() != LOGIN_BOOLEANS) return;
            booleans.write(LOGIN_FLAT_INDEX, flat);
        } catch (Exception e) {
            // Пакет ушёл как есть - это всего лишь небо, ронять из-за него вход в мир
            // было бы несоразмерно.
            this.plugin.getLogger().fine("Unable to rewrite sky flatness for " + player.getName());
        }
    }

    /**
     * Плоскость для мира, в который игрок въезжает.
     * <p>
     * Сначала точка назначения из события телепорта, и она же отсюда снимается: один
     * телепорт - один пакет. Если её нет (вход на сервер, где телепорта не было),
     * берётся текущий мир - при входе он уже настоящий.
     *
     * @return null - про этот мир ничего не известно, пакет не трогаем
     */
    @Nullable
    private Boolean flatFor(@NonNull Player player) {
        String pending = this.pendingWorld.remove(player.getUniqueId());
        if (pending != null) {
            Boolean known = this.flatByWorld.get(pending);
            if (known != null) return known;
            // Мир назначения известен и он НЕ уровень с выбором: трогать нечего.
            return null;
        }

        World current = player.getWorld();
        return current == null ? null : this.flatByWorld.get(current.getName());
    }

    @Override
    public void disable() {
        ProtocolLibrary.getProtocolManager().removePacketListener(this.adapter);
        HandlerList.unregisterAll(this);
        this.flatByWorld.clear();
        this.pendingWorld.clear();
    }
}
