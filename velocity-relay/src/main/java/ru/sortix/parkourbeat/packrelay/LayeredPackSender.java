package ru.sortix.parkourbeat.packrelay;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.bomb.amusic.api.PackSender;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.slf4j.Logger;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * РЕСУРСПАКИ СЛОЯМИ (клиенты 1.20.3+).
 * <p>
 * Клиент 1.20.3+ держит стек паков и умеет снимать их по одному (по UUID). Раньше
 * каждый пак AMusic уходил со своим UUID (от адреса, а адрес у каждой отправки новый),
 * поэтому паки копились, и при выходе с уровня приходилось снимать ВСЕ (clearResourcePacks),
 * а базовый пак качать и применять заново.
 * <p>
 * Теперь два постоянных слота:
 * <ul>
 *   <li><b>CORE</b> - базовый пак (ParkourBeatCore). Ставится один раз; повторная отправка
 *       того же пака ничего не качает - снимается только слой уровня, а бэкенду сразу
 *       уходит статус SUCCESSFUL.</li>
 *   <li><b>LEVEL</b> - пак трека уровня. Новый уровень заменяет его (тот же UUID),
 *       выход в лобби снимает только его ({@link #removeLevel}).</li>
 * </ul>
 * Старые клиенты - прежний VelocityPackSender.
 */
public final class LayeredPackSender implements PackSender {
    public static final UUID CORE_ID = UUID.nameUUIDFromBytes("parkourbeat:core".getBytes(StandardCharsets.UTF_8));
    public static final UUID LEVEL_ID = UUID.nameUUIDFromBytes("parkourbeat:level".getBytes(StandardCharsets.UTF_8));

    /** Выключить слои (всё как раньше). */
    public static volatile boolean ENABLED = true;
    /** Пересылка статуса на бэкенд (ставит ParkourBeatPackRelay). */
    public static volatile BiConsumer<Player, String> statusRelay;

    private static final Map<UUID, byte[]> CORE_LOADED = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> LEVEL_LOADED = new ConcurrentHashMap<>();

    private final ProxyServer server;
    private final PackSender legacy;
    private final Supplier<byte[]> coreSha1;
    private final Logger logger;

    public LayeredPackSender(ProxyServer server, PackSender legacy, Supplier<byte[]> coreSha1, Logger logger) {
        this.server = server;
        this.legacy = legacy;
        this.coreSha1 = coreSha1;
        this.logger = logger;
    }

    @Override
    public void send(UUID uuid, String url, byte[] sha1) {
        Optional<Player> found = uuid == null ? Optional.empty() : this.server.getPlayer(uuid);
        if (found.isEmpty()) return;
        Player player = found.get();
        if (!ENABLED || player.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_20_3) < 0) {
            this.legacy.send(uuid, url, sha1);
            return;
        }
        try {
            byte[] core = null;
            try {
                core = this.coreSha1.get();
            } catch (Throwable ignored) {
            }
            boolean isCore = core != null && sha1 != null && Arrays.equals(core, sha1);

            if (isCore) {
                removeLevel(player);
                byte[] loaded = CORE_LOADED.get(uuid);
                if (loaded != null && Arrays.equals(loaded, sha1)) {
                    // Базовый пак уже у клиента: качать нечего, снят только слой уровня.
                    this.logger.info("Packs: {} back to core (level layer removed, nothing to download)", player.getUsername());
                    BiConsumer<Player, String> relay = statusRelay;
                    if (relay != null) relay.accept(player, "SUCCESSFUL");
                    return;
                }
                this.push(player, CORE_ID, url, sha1);
                CORE_LOADED.put(uuid, sha1.clone());
                this.logger.info("Packs: {} core layer sent", player.getUsername());
                return;
            }

            // Пак уровня: тот же UUID - клиент заменит прошлый уровень, базовый не трогается.
            this.push(player, LEVEL_ID, url, sha1);
            LEVEL_LOADED.put(uuid, Boolean.TRUE);
            this.logger.info("Packs: {} level layer sent", player.getUsername());
        } catch (Throwable t) {
            this.logger.warn("Layered pack send failed for {}, falling back", player.getUsername(), t);
            this.legacy.send(uuid, url, sha1);
        }
    }

    private void push(Player player, UUID id, String url, byte[] sha1) {
        String hash = sha1 == null ? "" : hex(sha1);
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(id, URI.create(url), hash);
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
            .packs(info)
            .replace(false)   // НЕ снимать остальные паки
            .required(false)
            .build());
    }

    /**
     * Снять только слой уровня. true - снят (или клиент новый и снимать было нечего),
     * false - клиент старый, слоёв нет.
     */
    public static boolean removeLevel(Player player) {
        if (!ENABLED || player.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_20_3) < 0) return false;
        if (LEVEL_LOADED.remove(player.getUniqueId()) != null) {
            player.removeResourcePacks(LEVEL_ID);
        }
        return true;
    }

    public static void forget(UUID player) {
        CORE_LOADED.remove(player);
        LEVEL_LOADED.remove(player);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format("%02x", b & 0xFF));
        return out.toString();
    }
}
