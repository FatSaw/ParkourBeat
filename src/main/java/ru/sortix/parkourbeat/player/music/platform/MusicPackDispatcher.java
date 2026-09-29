// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/player/music/platform/MusicPackDispatcher.java
package ru.sortix.parkourbeat.player.music.platform;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

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

    private static final long NO_REPLY_TIMEOUT_MS = 24_000L;

    private static final long APPLY_TIMEOUT_MS = 120_000L;

    private static final long WATCHDOG_PERIOD_TICKS = 10L;

    public static final String RELAY_CHANNEL = "parkourbeat:packstatus";
    private static final long RELAY_ALIVE_MS = 60_000L;

    // Статические кэши нужны для сохранения состояний при перезагрузке самого плагина (/pb reload).
    // Позволяет не мигать заголовком "Загрузка музыки...", если она уже загружена.
    private static final Map<UUID, String> RELOAD_CACHE_CONFIRMED = new ConcurrentHashMap<>();
    private static final Map<UUID, java.util.Set<String>> RELOAD_CACHE_SHOWN = new ConcurrentHashMap<>();

    public enum Result {
        LOADED(true),
        SLOW_APPLY(true),
        DECLINED(false),
        FAILED(false),
        NO_REPLY(false),
        UNKNOWN(true),
        DISPATCH_ERROR(false),
        PLAYER_LEFT(false),
        SUPERSEDED(false);

        private final boolean ok;

        Result(boolean ok) {
            this.ok = ok;
        }

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
        private volatile boolean titleShown = false;

        private Pending(String trackId) {
            this.trackId = trackId;
            this.deadline = System.currentTimeMillis() + NO_REPLY_TIMEOUT_MS;
        }
    }

    private final @NonNull Plugin plugin;
    private final @NonNull Logger logger;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    private final Map<UUID, String> confirmed = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.Set<String>> shownTitles = new ConcurrentHashMap<>();

    private BukkitTask watchdog;
    private volatile boolean shutdown = false;
    private volatile long lastRelayMessageAt = 0L;
    private volatile boolean relayWarned = false;

    public MusicPackDispatcher(@NonNull Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void enable() {
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
        try {
            this.plugin.getServer().getMessenger()
                .registerIncomingPluginChannel(this.plugin, RELAY_CHANNEL, new PackStatusRelay(this));
        } catch (Throwable t) {
            this.logger.log(Level.WARNING, "Unable to register resourcepack status relay channel", t);
        }

        // Восстанавливаем кэши, которые могли остаться от предыдущего экземпляра плагина (до перезагрузки)
        this.confirmed.putAll(RELOAD_CACHE_CONFIRMED);
        RELOAD_CACHE_CONFIRMED.clear();
        this.shownTitles.putAll(RELOAD_CACHE_SHOWN);
        RELOAD_CACHE_SHOWN.clear();

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
        try {
            this.plugin.getServer().getMessenger()
                .unregisterIncomingPluginChannel(this.plugin, RELAY_CHANNEL);
        } catch (Throwable ignored) {
        }

        // Скидываем состояния в статические мапы, чтобы они "пережили" перезагрузку
        RELOAD_CACHE_CONFIRMED.putAll(this.confirmed);
        RELOAD_CACHE_SHOWN.putAll(this.shownTitles);

        for (Map.Entry<UUID, Pending> entry : new ArrayList<>(this.pending.entrySet())) {
            this.complete(entry.getKey(), entry.getValue(), Result.DISPATCH_ERROR);
        }
        this.pending.clear();
        this.confirmed.clear();
        this.shownTitles.clear();
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
                existing.callbacks.add(callback);
                return;
            }
            this.complete(uuid, existing, Result.SUPERSEDED);
        }

        String confirmedTrack = this.confirmed.get(uuid);
        boolean alreadyLoaded = trackId.equals(confirmedTrack);

        Pending request = new Pending(trackId);
        request.callbacks.add(callback);
        this.pending.put(uuid, request);

        if (!alreadyLoaded) {
            this.confirmed.remove(uuid);
        }
        this.showLoadingTitle(player, request);

        try {
            dispatchAction.run();
        } catch (Throwable t) {
            this.logger.log(Level.SEVERE,
                "Unable to dispatch resourcepack \"" + trackId + "\" to " + player.getName(), t);
            this.complete(uuid, request, Result.DISPATCH_ERROR);
        }
    }

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
        this.applyStatus(event.getPlayer().getUniqueId(), event.getStatus().name());
    }

    public void onRelayStatus(@NonNull UUID uuid, @NonNull String status) {
        this.lastRelayMessageAt = System.currentTimeMillis();
        if (Bukkit.isPrimaryThread()) {
            this.applyStatus(uuid, status);
            return;
        }
        if (this.shutdown) return;
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.applyStatus(uuid, status));
    }

    public boolean isRelayAlive() {
        return System.currentTimeMillis() - this.lastRelayMessageAt < RELAY_ALIVE_MS;
    }

    private void applyStatus(@NonNull UUID uuid, @NonNull String rawStatus) {
        String status = rawStatus.toUpperCase(java.util.Locale.ROOT);
        if (status.equals("SUCCESSFUL")) status = "SUCCESSFULLY_LOADED";
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
                this.confirmed.remove(uuid);
                return;
            }
            default:
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        this.confirmed.remove(uuid);
        this.shownTitles.remove(uuid);
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
            if (request.accepted) {
                this.complete(uuid, request, Result.SLOW_APPLY);
                continue;
            }
            if (this.isRelayAlive()) {
                this.complete(uuid, request, Result.NO_REPLY);
                continue;
            }
            this.warnAboutMissingRelay();
            this.complete(uuid, request, Result.UNKNOWN);
        }
    }

    @NonNull
    private static String statusHint(@NonNull Result result) {
        switch (result) {
            case NO_REPLY:
                return " (релей статусов работает, но клиент не ответил: ViaVersion или пакет не дошёл)";
            case SLOW_APPLY:
                return " (клиент принял пак, но ещё перезагружает ресурсы)";
            case UNKNOWN:
                return " (статус недоступен, считаем успехом)";
            default:
                return "";
        }
    }

    private void warnAboutMissingRelay() {
        if (this.relayWarned) return;
        this.relayWarned = true;
        this.logger.warning("Статус ресурспака недоступен: пак отправляет прокси, а плагин-релей"
            + " ParkourBeatPackRelay на Velocity не установлен или молчит."
            + " Пока что результат загрузки считается успешным вслепую.");
    }

    private static final net.kyori.adventure.text.Component LOADING_TITLE =
        net.kyori.adventure.text.Component.text("Загрузка музыки...",
            net.kyori.adventure.text.format.NamedTextColor.WHITE);
    private static final net.kyori.adventure.text.Component LOADING_SUBTITLE =
        net.kyori.adventure.text.Component.text("\u266B",
            net.kyori.adventure.text.format.NamedTextColor.AQUA);

    private static final net.kyori.adventure.title.Title.Times LOADING_TIMES =
        net.kyori.adventure.title.Title.Times.of(
            java.time.Duration.ofMillis(150),
            java.time.Duration.ofMillis(2000),
            java.time.Duration.ofMillis(200));

    private static final String SILENT_TRACK = "ParkourBeatCore";

    private void showLoadingTitle(@NonNull Player player, @NonNull Pending request) {
        if (SILENT_TRACK.equals(request.trackId)) return;

        java.util.Set<String> shown = this.shownTitles.computeIfAbsent(
            player.getUniqueId(), key -> ConcurrentHashMap.newKeySet());
        if (!shown.add(request.trackId)) return;

        request.titleShown = true;
        try {
            player.showTitle(net.kyori.adventure.title.Title.title(
                LOADING_TITLE,
                LOADING_SUBTITLE,
                LOADING_TIMES));
        } catch (Throwable ignored) {
        }
    }

    private void hideLoadingTitle(@NonNull UUID uuid, @NonNull Pending request, @NonNull Result result) {
        Player player = this.plugin.getServer().getPlayer(uuid);
        if (player == null) return;

        if (result != Result.LOADED && result != Result.UNKNOWN && result != Result.SLOW_APPLY) {
            java.util.Set<String> shown = this.shownTitles.get(uuid);
            if (shown != null) shown.clear();
        }

        Runnable run = () -> {
            if (!player.isOnline()) return;

            // Сбрасываем тайтл только если именно мы его показали!
            // Иначе очистим чужой тайтл (Например "Подготовка уровня...")
            if (request.titleShown) {
                try {
                    player.clearTitle();
                } catch (Throwable ignored) {
                }
            }

            if (result != Result.DECLINED) return;
            player.sendMessage(net.kyori.adventure.text.Component.text(
                Lang.raw(PlayerLang.of(player), "auto.music_pack_dispatcher.hide_loading_title.1")
                    + Lang.raw(PlayerLang.of(player), "auto.music_pack_dispatcher.hide_loading_title.2")
                    + Lang.raw(PlayerLang.of(player), "auto.music_pack_dispatcher.hide_loading_title.3"),
                net.kyori.adventure.text.format.NamedTextColor.AQUA));
        };

        if (Bukkit.isPrimaryThread() || this.shutdown) {
            run.run();
        } else {
            this.plugin.getServer().getScheduler().runTask(this.plugin, run);
        }
    }

    private void complete(@NonNull UUID uuid, @NonNull Pending request, @NonNull Result result) {
        if (!request.finished.compareAndSet(false, true)) return;
        this.pending.remove(uuid, request);

        this.hideLoadingTitle(uuid, request, result);

        if (result == Result.NO_REPLY || result == Result.SLOW_APPLY || result == Result.UNKNOWN) {
            this.logger.info("Resourcepack \"" + request.trackId + "\" for " + uuid
                + ": " + result + " after " + (System.currentTimeMillis() - request.startedAt) + " ms"
                + statusHint(result));
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
