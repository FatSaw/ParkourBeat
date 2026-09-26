package ru.sortix.parkourbeat.player;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.TextureVersionRange;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import ru.sortix.parkourbeat.utils.text.PbText;
public class CustomTexturesManager implements PluginManager, Listener, PluginMessageListener {
    public static final String CHANNEL = "parkourbeat:web";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final @NonNull ParkourBeat plugin;
    private final ConcurrentHashMap<UUID, java.util.function.Consumer<Boolean>> installWaiters =
        new ConcurrentHashMap<>();
    /**
     * Игрок -> уровень, чьи текстуры сейчас лежат на клиенте.
     * <p>
     * Раньше здесь было просто множество: было известно, что текстуры есть, но не чьи.
     * Из-за этого переход уровень -> уровень нельзя было отличить от повторного захода
     * на тот же уровень, и снятие пака не делалось вообще ни там, ни там.
     */
    private final ConcurrentHashMap<UUID, UUID> texturedPlayers = new ConcurrentHashMap<>();
    private volatile boolean bridgeSeen = false;

    public CustomTexturesManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public boolean isBridgeAvailable() {
        return this.bridgeSeen;
    }

    public void requestUploadLink(@NonNull Player player, @NonNull UUID levelId, @NonNull String levelName) {
        this.write(player, "tex_url", levelId.toString(), levelName);
    }

    public void setVersionRange(@NonNull Player player, @NonNull UUID levelId,
                                @NonNull TextureVersionRange range) {
        this.write(player, "tex_range", levelId.toString(), range.name());
    }

    public void clearVersionRange(@NonNull Player player, @NonNull UUID levelId) {
        this.write(player, "tex_range", levelId.toString(), "NONE");
    }

    public void deleteTextures(@NonNull Player player, @NonNull UUID levelId) {
        this.write(player, "tex_delete", levelId.toString());
    }

    /**
     * Просит прокси подставить текстуры уровня и вызывает callback, когда она ответит.
     *
     * Ждать ответ синхронно нельзя: плагин-сообщения Bukkit приходят в основном потоке,
     * и блокировка основного потока не давала ответу дойти вообще никогда. Получался
     * гарантированный трёхсекундный простой и результат "текстур нет" в каждом заходе.
     */
    public void installTextures(@NonNull Player player, @NonNull UUID levelId,
                                @NonNull java.util.function.Consumer<Boolean> callback) {
        if (!this.bridgeSeen) {
            callback.accept(false);
            return;
        }

        UUID playerId = player.getUniqueId();
        java.util.concurrent.atomic.AtomicBoolean done =
            new java.util.concurrent.atomic.AtomicBoolean(false);

        this.installWaiters.put(playerId, installed -> {
            if (!done.compareAndSet(false, true)) return;
            this.installWaiters.remove(playerId);
            callback.accept(installed);
        });

        this.write(player, "tex_install", levelId.toString());

        this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            java.util.function.Consumer<Boolean> waiter = this.installWaiters.remove(playerId);
            if (waiter == null) return;
            if (!done.compareAndSet(false, true)) return;
            this.plugin.getLogger().warning("Прокси не ответила на установку текстур уровня "
                + levelId + Lang.raw(PlayerLang.of(player), "auto.custom_textures_manager.install_textures.1"));
            callback.accept(false);
        }, 60L);
    }

    public boolean hasTexturesLoaded(@NonNull Player player) {
        return this.texturedPlayers.containsKey(player.getUniqueId());
    }

    /**
     * @return уровень, чьи текстуры сейчас на клиенте, или null, если пак без текстур уровня
     */
    @javax.annotation.Nullable
    public UUID getLoadedTexturesLevel(@NonNull Player player) {
        return this.texturedPlayers.get(player.getUniqueId());
    }

    /**
     * Снимает пак уровня перед отправкой следующего. Вызывается строго перед новой загрузкой,
     * чтобы снятие и загрузка не гонялись друг с другом: иначе клиент успевал получить
     * подряд снятие, базовый пак и пак уровня, и в итоге играла тишина из базового.
     */
    public void unloadTextures(@NonNull Player player) {
        if (this.texturedPlayers.remove(player.getUniqueId()) == null) return;
        this.plugin.getLogger().info("Снимаем ресурспак уровня с игрока " + player.getName());
        this.write(player, "tex_unload", "");
    }

    public void markTexturesSent(@NonNull Player player, @NonNull UUID levelId) {
        this.texturedPlayers.put(player.getUniqueId(), levelId);
    }

    /**
     * Снимает текстуры чужого уровня, когда нового пака не будет вообще.
     * <p>
     * Обычно чужой пак снимает сама выдача пака нового уровня. Но уровень без трека
     * (или с недоступным треком) пак не выдаёт совсем, и без этого вызова игрок ходил бы
     * по нему в текстурах предыдущего уровня.
     */
    public void dropForeignTextures(@NonNull Player player, @javax.annotation.Nullable UUID currentLevelId) {
        UUID loaded = this.texturedPlayers.get(player.getUniqueId());
        if (loaded == null) return;
        if (loaded.equals(currentLevelId)) return;
        if (this.texturedPlayers.remove(player.getUniqueId()) == null) return;

        this.plugin.getLogger().info("Снимаем ресурспак уровня " + loaded
            + Lang.raw(PlayerLang.of(player), "auto.custom_textures_manager.drop_foreign_textures.1") + player.getName() + Lang.raw(PlayerLang.of(player), "auto.custom_textures_manager.drop_foreign_textures.2"));

        this.write(player, "tex_unload", "");
        this.sendBasePack(player);
    }

    /**
     * Ресурспак живёт у клиента до следующей команды загрузки. Уровень со своими текстурами
     * не заканчивается вместе с миром: без явной выгрузки его блоки и небо остаются
     * и на спавне, и на любом уровне, который не прислал свой пак.
     */
    @org.bukkit.event.EventHandler
    public void onWorldChange(@NonNull org.bukkit.event.player.PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!this.texturedPlayers.containsKey(player.getUniqueId())) return;

        this.plugin.getServer().getScheduler().runTaskLater(this.plugin,
            () -> this.restoreBasePack(player), 5L);
    }

    @org.bukkit.event.EventHandler
    public void onQuit(@NonNull org.bukkit.event.player.PlayerQuitEvent event) {
        this.texturedPlayers.remove(event.getPlayer().getUniqueId());
    }

    private void restoreBasePack(@NonNull Player player) {
        if (!player.isOnline()) {
            this.texturedPlayers.remove(player.getUniqueId());
            return;
        }

        try {
            ru.sortix.parkourbeat.activity.UserActivity activity =
                this.plugin.get(ru.sortix.parkourbeat.activity.ActivityManager.class).getActivity(player);

            // Переход на другой уровень обрабатывается перед отправкой его пака
            // (см. AMusicPlatform.unloadForeignTextures): там снятие и загрузка идут
            // строго друг за другом. Здесь остаётся только выход в лобби,
            // где нового пака никто не пришлёт.
            if (activity != null) return;
            if (this.texturedPlayers.remove(player.getUniqueId()) == null) return;

            this.plugin.getLogger().info("Снимаем ресурспак уровня с игрока " + player.getName());

            // Прокси сообщаем для её собственного учёта, а с клиента пак снимает
            // отправка базового: она чистит стек паков перед тем, как отдать новый.
            this.write(player, "tex_unload", "");
            this.sendBasePack(player);
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING,
                "Unable to restore base resourcepack for " + player.getName(), e);
        }
    }

    public void releaseTextures(@NonNull Player player) {
        if (!this.bridgeSeen) return;
        this.write(player, "tex_release", "");
    }

    private void write(@NonNull Player player, @NonNull String action, @NonNull String... payload) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeUTF(action);
                for (String value : payload) out.writeUTF(value);
            }
            player.sendPluginMessage(this.plugin, CHANNEL, bytes.toByteArray());
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Unable to send texture bridge request", e);
        }
    }

    @Override
    public void onPluginMessageReceived(@NonNull String channel, @NonNull Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        this.bridgeSeen = true;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            in.readLong();
            in.readLong();
            String action = in.readUTF();
            String payload = in.readUTF();
            this.handle(player, action, payload);
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Unable to read texture bridge message", e);
        }
    }

    private void handle(@NonNull Player player, @NonNull String action, @NonNull String payload) {
        switch (action) {
            case "tex_installed":
            case "tex_install_failed": {
                java.util.function.Consumer<Boolean> waiter =
                    this.installWaiters.get(player.getUniqueId());
                if (waiter != null) waiter.accept("tex_installed".equals(action));
                return;
            }
            case "tex_link": {
                player.sendMessage(Component.empty());
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.custom_textures_manager.handle.1")));
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.custom_textures_manager.handle.2")));
                player.sendMessage(Component.text(payload, NamedTextColor.LIGHT_PURPLE)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(payload)));
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.custom_textures_manager.handle.3")));
                player.sendMessage(Component.empty());
                return;
            }
            case "tex_ready": {
                this.plugin.getServer().getScheduler().runTask(this.plugin,
                    () -> this.onTexturesUploaded(player, payload));
                return;
            }
            case "tex_removed": {
                this.plugin.getServer().getScheduler().runTask(this.plugin,
                    () -> this.onTexturesRemoved(player, payload));
                return;
            }
            case "tex_unavailable": {
                player.sendMessage(PbText.of(
                    Lang.raw(PlayerLang.of(player), "auto.custom_textures_manager.handle.4")));
                return;
            }
            default:
        }
    }

    private void onTexturesUploaded(@NonNull Player player, @NonNull String payload) {
        String[] parts = payload.split("\u0000");
        UUID levelId = parseUuid(parts.length > 0 ? parts[0] : null);
        if (levelId == null) return;

        this.applyToLevel(levelId, true);
        this.repackLevelTrack(levelId);
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.custom_textures_manager.on_textures_uploaded.1")));
        player.sendMessage(PbText.of(
            Lang.raw(PlayerLang.of(player), "auto.custom_textures_manager.on_textures_uploaded.2")));
    }

    private void onTexturesRemoved(@NonNull Player player, @NonNull String payload) {
        UUID levelId = parseUuid(payload);
        if (levelId == null) return;

        this.applyToLevel(levelId, false);
        this.repackLevelTrack(levelId);
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.custom_textures_manager.on_textures_removed.1")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.custom_textures_manager.on_textures_removed.2")));
    }

    /**
     * Архив пака кэшируется у AMusic по имени трека и переживает удаление текстур:
     * без принудительной пересборки игроки продолжали получать пак со старыми текстурами
     * даже после того, как строитель их удалил.
     */
    private void repackLevelTrack(@NonNull UUID levelId) {
        try {
            ru.sortix.parkourbeat.levels.settings.GameSettings settings =
                this.plugin.get(ru.sortix.parkourbeat.levels.LevelsManager.class)
                    .getAvailableLevelSettings(levelId);
            if (settings == null || settings.getMusicTrack() == null) return;

            String trackId = settings.getMusicTrack().getId();
            ru.sortix.parkourbeat.player.music.MusicTracksManager music =
                this.plugin.get(ru.sortix.parkourbeat.player.music.MusicTracksManager.class);

            // Наш учёт содержимого архива после ручной пересборки уже недействителен.
            if (music.getPlatform() instanceof ru.sortix.parkourbeat.player.music.platform.AMusicPlatform amusic) {
                amusic.forgetPackedTextures(trackId);
            }

            music.updateTrackArchive(null, trackId, true);

            this.plugin.getLogger().info("Пересобираем пак трека \"" + trackId
                + "\" после удаления текстур уровня " + levelId);
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING,
                "Unable to repack track after texture removal for level " + levelId, e);
        }
    }

    private void applyToLevel(@NonNull UUID levelId, boolean hasTextures) {
        try {
            ru.sortix.parkourbeat.levels.LevelsManager levels =
                this.plugin.get(ru.sortix.parkourbeat.levels.LevelsManager.class);

            ru.sortix.parkourbeat.levels.settings.GameSettings settings =
                levels.getAvailableLevelSettings(levelId);
            if (settings == null) return;

            settings.setCustomTextures(hasTextures);
            if (hasTextures) settings.setPublicVisible(false);
            levels.saveLevelSettings(levelId);
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING,
                "Unable to apply texture state to level " + levelId, e);
        }
    }

    private void sendBasePack(@NonNull Player player) {
        if (!player.isOnline()) return;

        try {
            ru.sortix.parkourbeat.player.music.MusicTracksManager music =
                this.plugin.get(ru.sortix.parkourbeat.player.music.MusicTracksManager.class);

            ru.sortix.parkourbeat.player.music.MusicTrack basePack =
                new ru.sortix.parkourbeat.player.music.MusicTrack(
                    music.getPlatform(), "ParkourBeatCore", "ParkourBeatCore", false);

            // В базовом паке текстур уровня быть не должно НИКОГДА, поэтому уровень
            // называем явно (null). Иначе платформа берёт текущую активность игрока
            // и вмерживает её текстуры в архив самого базового пака.
            if (music.getPlatform() instanceof ru.sortix.parkourbeat.player.music.platform.AMusicPlatform amusic) {
                amusic.setResourcepackTrack(player, basePack, null, result -> {
                    if (result.isOk()) return;
                    this.plugin.getLogger().warning(
                        "Не удалось вернуть базовый ресурспак игроку " + player.getName());
                }, null);
                return;
            }

            music.getPlatform().setResourcepackTrack(player, basePack, success -> {
                if (success) return;
                this.plugin.getLogger().warning(
                    "Не удалось вернуть базовый ресурспак игроку " + player.getName());
            });
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING,
                "Unable to send base resourcepack to " + player.getName(), e);
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        try {
            this.plugin.getServer().getMessenger().unregisterIncomingPluginChannel(this.plugin, CHANNEL, this);
            this.plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(this.plugin, CHANNEL);
        } catch (Throwable ignored) {
        }
        this.installWaiters.clear();
        this.texturedPlayers.clear();
    }
}
