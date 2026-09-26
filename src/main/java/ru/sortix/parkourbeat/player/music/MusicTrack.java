package ru.sortix.parkourbeat.player.music;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.sortix.parkourbeat.player.music.platform.MusicPackDispatcher;
import ru.sortix.parkourbeat.player.music.platform.AMusicPlatform;
import ru.sortix.parkourbeat.player.music.platform.MusicPlatform;

import java.util.function.Consumer;
import java.util.logging.Level;

public class MusicTrack {

    private final @NonNull MusicPlatform platform;
    private final @NonNull String trackId;
    private final @NonNull String trackName;
    @Getter
    private final boolean piecesSupported;

    /**
     * Трек считается существующим, даже если платформа его ещё не видит в своём списке.
     * <p>
     * Нужно для нарезки под чекпоинты: плейлист с кусками создаётся на прокси прямо
     * сейчас, а список треков на бэкенде обновляется не мгновенно. Без этого флага
     * свежая нарезка отваливалась с DISPATCH_ERROR ещё до попытки собрать пак.
     */
    @Getter
    private final boolean forcedAvailable;

    public MusicTrack(@NonNull MusicPlatform platform,
                      @NonNull String trackId,
                      @NonNull String trackName,
                      boolean piecesSupported
    ) {
        this(platform, trackId, trackName, piecesSupported, false);
    }

    public MusicTrack(@NonNull MusicPlatform platform,
                      @NonNull String trackId,
                      @NonNull String trackName,
                      boolean piecesSupported,
                      boolean forcedAvailable
    ) {
        this.platform = platform;
        this.trackId = trackId;
        this.trackName = trackName;
        this.piecesSupported = piecesSupported;
        this.forcedAvailable = forcedAvailable;
    }

    @NonNull
    public String getId() {
        return this.trackId;
    }

    @NonNull
    public String getName() {
        return this.trackName;
    }

    public boolean isStillAvailable() {
        if (this.forcedAvailable) return true;
        return this.platform.getTrackById(this.getId()) != null;
    }

    public void isResourcepackCurrentlySet(@NonNull Player player, Consumer<Boolean> currentlySetConsumer) {
        this.platform.getResourcepackTrack(player, currentTrack ->
            currentlySetConsumer.accept(currentTrack != null && this.trackId.equals(currentTrack.trackId)));
    }

    /**
     * @param resultConsumer получает подробный результат. Вызывается ровно один раз, в основном потоке.
     * @param onSent         вызывается в момент фактической отправки пака клиенту, может быть null.
     */
    public void setResourcepackAsync(@NonNull Plugin plugin,
                                     @NonNull Player player,
                                     @NonNull Consumer<MusicPackDispatcher.Result> resultConsumer,
                                     Runnable onSent) {
        this.setResourcepackAsync(plugin, player, false, null, resultConsumer, onSent);
    }

    /**
     * Версия для случая, когда пак выдаётся ДО переключения игрока на уровень
     * (реплей, запуск игры, тест из редактора): текущая активность в этот момент
     * ещё старая, и определять текстуры по ней нельзя.
     *
     * @param texturesLevelId уровень, чьи текстуры должны попасть в пак, или null - никаких
     */
    public void setResourcepackAsync(@NonNull Plugin plugin,
                                     @NonNull Player player,
                                     @javax.annotation.Nullable java.util.UUID texturesLevelId,
                                     @NonNull Consumer<MusicPackDispatcher.Result> resultConsumer,
                                     Runnable onSent) {
        this.setResourcepackAsync(plugin, player, true, texturesLevelId, resultConsumer, onSent);
    }

    private void setResourcepackAsync(@NonNull Plugin plugin,
                                      @NonNull Player player,
                                      boolean texturesLevelKnown,
                                      @javax.annotation.Nullable java.util.UUID texturesLevelId,
                                      @NonNull Consumer<MusicPackDispatcher.Result> resultConsumer,
                                      Runnable onSent) {
        if (!this.isStillAvailable()) {
            // ВАЖНО: в старой версии здесь не было return, и колбэк вызывался дважды.
            resultConsumer.accept(MusicPackDispatcher.Result.DISPATCH_ERROR);
            return;
        }

        Consumer<MusicPackDispatcher.Result> logging = result -> {
            // Игрока это уже не блокирует, поэтому в консоль шумим только там,
            // где действительно что-то сломано на нашей стороне.
            if (result == MusicPackDispatcher.Result.DISPATCH_ERROR
                || result == MusicPackDispatcher.Result.DECLINED) {
                plugin.getLogger().log(Level.WARNING, Lang.raw(PlayerLang.of(player), "auto.music_track.set_resourcepack_async.1")
                    + this.getName() + "\" (" + this.getId() + Lang.raw(PlayerLang.of(player), "auto.music_track.set_resourcepack_async.2") + player.getName() + ": " + result);
            } else if (!result.isOk()) {
                plugin.getLogger().log(Level.INFO, Lang.raw(PlayerLang.of(player), "auto.music_track.set_resourcepack_async.3") + this.getName()
                    + Lang.raw(PlayerLang.of(player), "auto.music_track.set_resourcepack_async.4") + player.getName() + ": " + result);
            }
            resultConsumer.accept(result);
        };

        if (this.platform instanceof AMusicPlatform aMusicPlatform) {
            if (texturesLevelKnown) {
                aMusicPlatform.setResourcepackTrack(player, this, texturesLevelId, logging, onSent);
            } else {
                aMusicPlatform.setResourcepackTrack(player, this, logging, onSent);
            }
        } else {
            this.platform.setResourcepackTrack(player, this, success -> logging.accept(
                Boolean.TRUE.equals(success)
                    ? MusicPackDispatcher.Result.LOADED
                    : MusicPackDispatcher.Result.FAILED));
            if (onSent != null) onSent.run();
        }
    }
}
