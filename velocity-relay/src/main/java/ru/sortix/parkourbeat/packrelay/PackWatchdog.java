package ru.sortix.parkourbeat.packrelay;

import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class PackWatchdog {
    private static final class Attempt {
        private volatile ResourcePackInfo info;
        private volatile long offeredAt = System.currentTimeMillis();
        private volatile int retries = 0;
        private volatile boolean accepted = false;
        private volatile boolean finished = false;
        private volatile String lastStatus = "PENDING";
    }

    private final Object plugin;
    private final ProxyServer server;
    private final Logger logger;
    private final RelayConfig config;
    private final Map<UUID, Attempt> attempts = new ConcurrentHashMap<>();

    public PackWatchdog(Object plugin, ProxyServer server, Logger logger, RelayConfig config) {
        this.plugin = plugin;
        this.server = server;
        this.logger = logger;
        this.config = config;
    }

    public void start() {
        if (!this.config.stuckWatchdog && !this.config.autoRetry) return;
        this.server.getScheduler()
            .buildTask(this.plugin, this::tick)
            .delay(5, TimeUnit.SECONDS)
            .repeat(2, TimeUnit.SECONDS)
            .schedule();
    }

    public String describe(UUID uuid) {
        Attempt attempt = this.attempts.get(uuid);
        if (attempt == null) return "нет активных паков";
        return attempt.lastStatus
            + ", попыток: " + attempt.retries
            + ", ждём " + (System.currentTimeMillis() - attempt.offeredAt) + " мс"
            + (attempt.accepted ? ", принят клиентом" : "");
    }

    public void forget(UUID uuid) {
        this.attempts.remove(uuid);
    }

    public void onStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String status = event.getStatus().name();

        Attempt attempt = this.attempts.computeIfAbsent(uuid, key -> new Attempt());
        attempt.lastStatus = status;

        ResourcePackInfo info = packInfo(event);
        if (info != null) attempt.info = info;

        switch (status) {
            case "ACCEPTED":
            case "DOWNLOADED":
                attempt.accepted = true;
                attempt.offeredAt = System.currentTimeMillis();
                return;
            case "SUCCESSFUL":
            case "SUCCESSFULLY_LOADED":
                attempt.finished = true;
                this.attempts.remove(uuid);
                return;
            case "DECLINED":
                attempt.finished = true;
                this.attempts.remove(uuid);
                if (this.config.coachDeclined) this.coachDeclined(player);
                return;
            case "FAILED_DOWNLOAD":
            case "FAILED_RELOAD":
            case "INVALID_URL":
            case "DISCARDED":
                this.retry(player, attempt, status);
                return;
            default:
        }
    }

    private void retry(Player player, Attempt attempt, String reason) {
        if (!this.config.autoRetry) {
            this.attempts.remove(player.getUniqueId());
            return;
        }
        if (attempt.info == null) {
            this.logger.warn("Cannot retry pack for {}: pack info unavailable on this Velocity version",
                player.getUsername());
            this.attempts.remove(player.getUniqueId());
            return;
        }
        if (attempt.retries >= this.config.retryAttempts) {
            this.logger.warn("Pack for {} failed {} time(s) with {}, giving up",
                player.getUsername(), attempt.retries, reason);
            this.attempts.remove(player.getUniqueId());
            player.sendMessage(Component.text(
                "Не удалось загрузить музыкальный ресурспак. Проверьте, что в настройках сервера"
                    + " ресурспаки разрешены, и попробуйте переподключиться.", NamedTextColor.RED));
            return;
        }

        attempt.retries++;
        attempt.accepted = false;
        long delay = this.config.retryDelayMillis * attempt.retries;

        this.logger.info("Retrying pack for {} after {} (attempt {}/{}, in {} ms)",
            player.getUsername(), reason, attempt.retries, this.config.retryAttempts, delay);

        ResourcePackInfo info = attempt.info;
        this.server.getScheduler().buildTask(this.plugin, () -> {
            if (!player.isActive()) {
                this.attempts.remove(player.getUniqueId());
                return;
            }
            attempt.offeredAt = System.currentTimeMillis();
            this.send(player, info);
        }).delay(delay, TimeUnit.MILLISECONDS).schedule();
    }

    @SuppressWarnings("deprecation")
    private void send(Player player, ResourcePackInfo info) {
        try {
            player.sendResourcePackOffer(info);
        } catch (Throwable t) {
            this.logger.warn("Unable to resend pack to {}: {}", player.getUsername(), String.valueOf(t));
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (Player player : this.server.getAllPlayers()) {
            UUID uuid = player.getUniqueId();
            Collection<ResourcePackInfo> pendingPacks = pending(player);

            if (pendingPacks.isEmpty()) {
                Attempt done = this.attempts.get(uuid);
                if (done != null && done.finished) this.attempts.remove(uuid);
                continue;
            }

            Attempt attempt = this.attempts.computeIfAbsent(uuid, key -> new Attempt());
            if (attempt.info == null) attempt.info = pendingPacks.iterator().next();

            if (!this.config.stuckWatchdog) continue;
            if (attempt.accepted) continue;
            if (now - attempt.offeredAt < this.config.stuckTimeoutMillis) continue;

            this.retry(player, attempt, "NO_REPLY");
        }
    }

    private void coachDeclined(Player player) {
        player.sendMessage(Component.text(
            "Вы отклонили музыкальный ресурспак. Без него музыка на уровнях играть не будет.",
            NamedTextColor.YELLOW));
        player.sendMessage(Component.text(
            "Включить: список серверов -> изменить сервер -> \"Ресурспаки сервера: Включено\".",
            NamedTextColor.GRAY));
    }

    @SuppressWarnings("deprecation")
    private static Collection<ResourcePackInfo> pending(Player player) {
        try {
            return player.getPendingResourcePacks();
        } catch (Throwable t) {
            return java.util.Collections.emptyList();
        }
    }

    @SuppressWarnings("deprecation")
    private static ResourcePackInfo packInfo(PlayerResourcePackStatusEvent event) {
        try {
            return event.getPackInfo();
        } catch (Throwable t) {
            return null;
        }
    }
}
