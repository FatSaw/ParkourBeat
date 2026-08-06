package ru.sortix.parkourbeat.player.music;

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

    public MusicTrack(@NonNull MusicPlatform platform,
                      @NonNull String trackId,
                      @NonNull String trackName,
                      boolean piecesSupported
    ) {
        this.platform = platform;
        this.trackId = trackId;
        this.trackName = trackName;
        this.piecesSupported = piecesSupported;
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
                plugin.getLogger().log(Level.WARNING, "Не удалось выдать ресурспак трека \""
                    + this.getName() + "\" (" + this.getId() + ") игроку " + player.getName() + ": " + result);
            } else if (!result.isOk()) {
                plugin.getLogger().log(Level.INFO, "Ресурспак трека \"" + this.getName()
                    + "\" для " + player.getName() + ": " + result);
            }
            resultConsumer.accept(result);
        };

        if (this.platform instanceof AMusicPlatform aMusicPlatform) {
            aMusicPlatform.setResourcepackTrack(player, this, logging, onSent);
        } else {
            this.platform.setResourcepackTrack(player, this, success -> logging.accept(
                Boolean.TRUE.equals(success)
                    ? MusicPackDispatcher.Result.LOADED
                    : MusicPackDispatcher.Result.FAILED));
            if (onSent != null) onSent.run();
        }
    }
}
