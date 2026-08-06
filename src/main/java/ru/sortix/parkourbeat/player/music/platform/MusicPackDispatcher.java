package ru.sortix.parkourbeat.player.music.platform;

import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class MusicPackDispatcher implements Listener {

    /** Сколько ждём хоть какой-нибудь ответ клиента (ACCEPTED/DECLINED/...). */
    private static final long NO_REPLY_TIMEOUT_MS = 8_000L;
    /** Сколько ждём применения пака ПОСЛЕ того, как клиент ответил ACCEPTED. */
    private static final long APPLY_TIMEOUT_MS = 120_000L;
    /** Период проверки дедлайнов. */
    private static final long WATCHDOG_PERIOD_TICKS = 10L;

    public enum Result {
        /** Клиент прислал SUCCESSFULLY_LOADED. */
        LOADED(true),
        /** Клиент подтвердил приём, но не успел применить за APPLY_TIMEOUT_MS. Скорее всего всё-таки применит. */
        SLOW_APPLY(true),
        /** Игрок отключил ресурспаки в настройках. */
        DECLINED(false),
        /** Клиент не смог скачать/применить пак. */
        FAILED(false),
        /** Клиент вообще не ответил — пакет не дошёл, ViaVersion, прокси и т.п. */
        NO_REPLY(false),
        /** Не удалось даже отправить пак (AMusic отверг задачу, очередь переполнена, playlist не найден). */
        DISPATCH_ERROR(false),
        /** Игрок вышел. */
        PLAYER_LEFT(false),
        /** Поверх пришёл новый запрос на другой трек. */
        SUPERSEDED(false);

        private final boolean ok;

        Result(boolean ok) {
            this.ok = ok;
        }

        /** true — игроку можно давать играть, ошибку показывать не нужно. */
        public boolean isOk() {
            return this.ok;
        }
    }

    private static final class Pending {
        private final String trackId;
        private final long startedAt = System.currentTimeMillis();
        private final List<Consumer<Result>> callbacks = new CopyOnWriteArrayList<>();
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private volatile boolean accepted = false;
        private volatile long deadline;

        private Pending(String trackId) {
            this.trackId = trackId;
            this.deadline = System.currentTimeMillis() + NO_REPLY_TIMEOUT_MS;
        }
    }

    private final @NonNull Plugin plugin;
    private final @NonNull Logger logger;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    /** Треки, применение которых клиент реально подтвердил. */
    private final Map<UUID, String> confirmed = new ConcurrentHashMap<>();
    private BukkitTask watchdog;
    private volatile boolean shutdown = false;

    public MusicPackDispatcher(@NonNull Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void enable() {
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
        this.watchdog = this.plugin.getServer().getScheduler().runTaskTimer(
            this.plugin, this::checkDeadlines, WATCHDOG_PERIOD_TICKS, WATCHDOG_PERIOD_TICKS);
    }

    public void disable() {
        this.shutdown = true;
        if (this.watchdog != null) {
            this.watchdog.cancel();
            this.watchdog = null;
        }
        HandlerList.unregisterAll(this);
        for (Map.Entry<UUID, Pending> entry : new ArrayList<>(this.pending.entrySet())) {
            this.complete(entry.getKey(), entry.getValue(), Result.DISPATCH_ERROR);
        }
        this.pending.clear();
        this.confirmed.clear();
    }


    @Nullable
    public String getConfirmedTrackId(@NonNull UUID playerUuid) {
        return this.confirmed.get(playerUuid);
    }

    public boolean isPending(@NonNull UUID playerUuid) {
        return this.pending.containsKey(playerUuid);
    }

    public long getPendingMillis(@NonNull UUID playerUuid) {
        Pending p = this.pending.get(playerUuid);
        return p == null ? -1L : System.currentTimeMillis() - p.startedAt;
    }

    /**
     * Запросить установку пака. Вызывать только из основного потока.
     *
     * @param dispatchAction действие, которое реально отправляет пак (AMusic.loadPack).
     *                       Вызывается уже ПОСЛЕ регистрации ожидания, чтобы не потерять
     *                       статус, если клиент ответит мгновенно.
     * @param callback       будет вызван ровно один раз, в основном потоке.
     */
    public void request(@NonNull Player player,
                        @NonNull String trackId,
                        @NonNull Consumer<Result> callback,
                        @NonNull Runnable dispatchAction) {
        if (this.shutdown) {
            callback.accept(Result.DISPATCH_ERROR);
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            this.plugin.getServer().getScheduler().runTask(this.plugin,
                () -> this.request(player, trackId, callback, dispatchAction));
            return;
        }
        if (!player.isOnline()) {
            callback.accept(Result.PLAYER_LEFT);
            return;
        }

        UUID uuid = player.getUniqueId();
        Pending existing = this.pending.get(uuid);
        if (existing != null) {
            if (existing.trackId.equals(trackId)) {
                // Такой же запрос уже в полёте — просто подписываемся на его результат,
                // а не отправляем игроку второй пак (это и вызывает рассинхрон статусов).
                existing.callbacks.add(callback);
                return;
            }
            this.complete(uuid, existing, Result.SUPERSEDED);
        }

        Pending request = new Pending(trackId);
        request.callbacks.add(callback);
        this.pending.put(uuid, request);
        this.confirmed.remove(uuid);

        try {
            dispatchAction.run();
        } catch (Throwable t) {
            this.logger.log(Level.SEVERE,
                "Unable to dispatch resourcepack \"" + trackId + "\" to " + player.getName(), t);
            this.complete(uuid, request, Result.DISPATCH_ERROR);
        }
    }

    /**
     * Принудительно завершить ожидание. Можно звать из любого потока.
     * Используется, когда AMusic сообщил об ошибке ещё до отправки пакета.
     */
    public void abort(@NonNull UUID playerUuid, @NonNull String trackId, @NonNull Result result) {
        if (!Bukkit.isPrimaryThread()) {
            if (this.shutdown) return;
            this.plugin.getServer().getScheduler().runTask(this.plugin,
                () -> this.abort(playerUuid, trackId, result));
            return;
        }
        Pending request = this.pending.get(playerUuid);
        if (request == null || !request.trackId.equals(trackId)) return;
        this.complete(playerUuid, request, result);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String status = event.getStatus().name();
        Pending request = this.pending.get(uuid);

        switch (status) {
            case "ACCEPTED":
            case "DOWNLOADED": {
                if (request == null) return;
                request.accepted = true;
                request.deadline = System.currentTimeMillis() + APPLY_TIMEOUT_MS;
                return;
            }
            case "SUCCESSFULLY_LOADED": {
                if (request == null) return;
                this.confirmed.put(uuid, request.trackId);
                this.complete(uuid, request, Result.LOADED);
                return;
            }
            case "DECLINED": {
                this.confirmed.remove(uuid);
                if (request == null) return;
                this.complete(uuid, request, Result.DECLINED);
                return;
            }
            case "FAILED_DOWNLOAD":
            case "INVALID_URL":
            case "FAILED_RELOAD": {
                this.confirmed.remove(uuid);
                if (request == null) return;
                this.complete(uuid, request, Result.FAILED);
                return;
            }
            case "DISCARDED": {
                // Пак снят с клиента, но текущий запрос это не отменяет.
                this.confirmed.remove(uuid);
                return;
            }
            default:
                // Неизвестный статус будущей версии — игнорируем, дедлайн отработает сам.
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        this.confirmed.remove(uuid);
        Pending request = this.pending.get(uuid);
        if (request != null) this.complete(uuid, request, Result.PLAYER_LEFT);
    }

    private void checkDeadlines() {
        if (this.pending.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Pending> entry : this.pending.entrySet()) {
            UUID uuid = entry.getKey();
            Pending request = entry.getValue();
            Player player = this.plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                this.complete(uuid, request, Result.PLAYER_LEFT);
                continue;
            }
            if (now < request.deadline) continue;
            this.complete(uuid, request, request.accepted ? Result.SLOW_APPLY : Result.NO_REPLY);
        }
    }

    private void complete(@NonNull UUID uuid, @NonNull Pending request, @NonNull Result result) {
        if (!request.finished.compareAndSet(false, true)) return;
        this.pending.remove(uuid, request);

        if (result == Result.NO_REPLY || result == Result.SLOW_APPLY) {
            this.logger.info("Resourcepack \"" + request.trackId + "\" for " + uuid
                + ": " + result + " after " + (System.currentTimeMillis() - request.startedAt) + " ms"
                + (result == Result.NO_REPLY
                ? " (клиент не прислал ни одного статуса: ViaVersion/прокси/пакет не дошёл)"
                : " (клиент принял пак, но ещё перезагружает ресурсы)"));
        }

        List<Consumer<Result>> callbacks = new ArrayList<>(request.callbacks);
        request.callbacks.clear();
        Runnable run = () -> {
            for (Consumer<Result> callback : callbacks) {
                try {
                    callback.accept(result);
                } catch (Throwable t) {
                    this.logger.log(Level.SEVERE, "Resourcepack callback failed", t);
                }
            }
        };
        if (Bukkit.isPrimaryThread() || this.shutdown) {
            run.run();
        } else {
            this.plugin.getServer().getScheduler().runTask(this.plugin, run);
        }
    }
}
