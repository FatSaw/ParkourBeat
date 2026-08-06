package ru.sortix.parkourbeat.stats;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.rating.StatisticsManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Заявки на сброс статистики: игрок просит через {@code /statreset},
 * модератор рассматривает во вкладке {@code /moder}.
 * <p>
 * Решение по заявке доносится до игрока даже если он был оффлайн — сообщение
 * покажется при следующем входе.
 */
public class StatResetRequestManager implements PluginManager, Listener {

    /** Сколько обещаем рассматривать заявку. */
    public static final int REVIEW_DAYS = 7;

    private final @NonNull ParkourBeat plugin;
    private final @NonNull StatsStorage storage;
    private final Map<UUID, StatResetRequest> requests = new ConcurrentHashMap<>();

    public StatResetRequestManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        StatsStorage storage = plugin.get(StatisticsManager.class).getStorage();
        this.storage = storage;
        for (StatResetRequest request : storage.loadResetRequests()) {
            this.requests.put(request.getPlayerId(), request);
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ------------------------------------------------------------------ игрок

    @Nullable
    public StatResetRequest get(@NonNull UUID playerId) {
        return this.requests.get(playerId);
    }

    public boolean hasPending(@NonNull UUID playerId) {
        StatResetRequest request = this.requests.get(playerId);
        return request != null && request.isPending();
    }

    /**
     * Создать заявку. Возвращает null, если заявка уже висит на рассмотрении.
     */
    @Nullable
    public StatResetRequest create(@NonNull Player player) {
        if (this.hasPending(player.getUniqueId())) return null;

        StatResetRequest request = new StatResetRequest(
            player.getUniqueId(), player.getName(), System.currentTimeMillis());
        this.requests.put(request.getPlayerId(), request);
        this.save(request);

        this.plugin.getLogger().info("Заявка на сброс статистики от " + player.getName());
        this.notifyModerators(player.getName());
        return request;
    }

    /** Отозвать собственную заявку. */
    public boolean cancel(@NonNull UUID playerId) {
        StatResetRequest request = this.requests.get(playerId);
        if (request == null || !request.isPending()) return false;
        this.requests.remove(playerId);
        this.storage.deleteResetRequest(playerId);
        return true;
    }

    // ------------------------------------------------------------------ модератор

    /** Заявки на рассмотрении, самые старые первыми. */
    @NonNull
    public List<StatResetRequest> getPending() {
        List<StatResetRequest> pending = new ArrayList<>();
        for (StatResetRequest request : this.requests.values()) {
            if (request.isPending()) pending.add(request);
        }
        pending.sort(Comparator.comparingLong(StatResetRequest::getRequestedAtMillis));
        return pending;
    }

    public int getPendingCount() {
        return this.getPending().size();
    }

    public void approve(@NonNull StatResetRequest request, @NonNull Player moderator) {
        if (!request.isPending()) return;

        this.plugin.get(StatisticsManager.class).resetPlayer(request.getPlayerId());

        request.setStatus(StatResetRequest.Status.APPROVED);
        request.setResolvedBy(moderator.getName());
        request.setResolvedAtMillis(System.currentTimeMillis());
        request.setNotified(false);
        this.save(request);

        this.plugin.getLogger().warning("Модератор " + moderator.getName()
            + " одобрил сброс статистики игрока " + request.getPlayerName());
        this.deliver(request);
    }

    public void reject(@NonNull StatResetRequest request, @NonNull Player moderator) {
        if (!request.isPending()) return;

        request.setStatus(StatResetRequest.Status.REJECTED);
        request.setResolvedBy(moderator.getName());
        request.setResolvedAtMillis(System.currentTimeMillis());
        request.setNotified(false);
        this.save(request);

        this.plugin.getLogger().info("Модератор " + moderator.getName()
            + " отклонил сброс статистики игрока " + request.getPlayerName());
        this.deliver(request);
    }

    // ------------------------------------------------------------------ уведомления

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        StatResetRequest request = this.requests.get(event.getPlayer().getUniqueId());
        if (request == null) return;

        if (!request.getPlayerName().equals(event.getPlayer().getName())) {
            request.setPlayerName(event.getPlayer().getName());
            this.save(request);
        }
        if (request.isPending() || request.isNotified()) return;

        // Сообщение о решении — с задержкой, чтобы не утонуло в спаме входа.
        this.plugin.getServer().getScheduler().runTaskLater(
            this.plugin, () -> this.deliver(request), 40L);
    }

    /** Показать игроку решение по заявке, если он онлайн. */
    private void deliver(@NonNull StatResetRequest request) {
        Player player = this.plugin.getServer().getPlayer(request.getPlayerId());
        if (player == null || !player.isOnline()) return;

        if (request.getStatus() == StatResetRequest.Status.APPROVED) {
            player.sendMessage(Component.text(
                "Ваш запрос на сброс статистики одобрен. Статистика обнулена.",
                NamedTextColor.GREEN));
        } else if (request.getStatus() == StatResetRequest.Status.REJECTED) {
            player.sendMessage(Component.text(
                "Ваш запрос на сброс статистики отклонён.", NamedTextColor.RED));
        } else {
            return;
        }

        request.setNotified(true);
        this.save(request);
        // Закрытая и доставленная заявка больше не нужна — освобождаем место
        // под следующую, если игрок захочет попросить ещё раз.
        this.requests.remove(request.getPlayerId());
        this.storage.deleteResetRequest(request.getPlayerId());
    }

    private void notifyModerators(@NonNull String playerName) {
        Component message = Component.text(
            "Новый запрос на сброс статистики: " + playerName + ". Смотри /moder",
            NamedTextColor.YELLOW);
        for (Player online : this.plugin.getServer().getOnlinePlayers()) {
            if (online.hasPermission(ru.sortix.parkourbeat.constant.PermissionConstants.MODERATE_LEVELS)) {
                online.sendMessage(message);
            }
        }
    }

    private void save(@NonNull StatResetRequest request) {
        this.storage.saveResetRequest(request);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        this.requests.clear();
    }
}
