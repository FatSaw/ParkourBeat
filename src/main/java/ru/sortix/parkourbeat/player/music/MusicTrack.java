package ru.sortix.parkourbeat.player.music;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
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
    	Consumer<MusicTrack> trackConsumer = new Consumer<MusicTrack>() {
			@Override
			public void accept(MusicTrack currantTrack) {
				currentlySetConsumer.accept(currantTrack != null && MusicTrack.this.trackId.equals(currantTrack.trackId));
			}
    	};
        this.platform.getResourcepackTrack(player, trackConsumer);
    }

    public void setResourcepackAsync(@NonNull Plugin plugin, @NonNull Player player, Consumer<Boolean> booleanConsumer) {
        if (!this.isStillAvailable()) booleanConsumer.accept(false);
        this.platform.setResourcepackTrack(player, this, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean success) {
				if(!success) {
					plugin.getLogger().log(Level.SEVERE, "Не удалось запустить песню \"" + MusicTrack.this.getName() + "\" (" + MusicTrack.this.getId() + ") игроку " + player.getName());
				}
			}
		}.andThen(booleanConsumer));
    }
}
