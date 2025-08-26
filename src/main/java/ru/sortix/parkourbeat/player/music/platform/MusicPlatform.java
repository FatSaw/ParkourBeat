package ru.sortix.parkourbeat.player.music.platform;

import lombok.NonNull;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.player.music.MusicTrack;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class MusicPlatform {
    private final Map<String, MusicTrack> tracksById = new LinkedHashMap<>();

    public void reloadAllTracksList(Runnable runAfter) {
        this.tracksById.clear();
        Consumer<MusicTrack> trackConsumer = new Consumer<MusicTrack>() {
			
			@Override
			public void accept(MusicTrack track) {
				if(track == null) {
					return;
				}
				MusicPlatform.this.tracksById.put(track.getId(), track);
			}
		};
        this.loadAllTracksFromStorage(trackConsumer, runAfter);
    }

    public final @NonNull List<MusicTrack> getAllTracks() {
        return List.copyOf(this.tracksById.values());
    }

    @Nullable
    public final MusicTrack getTrackById(@NonNull String trackId) {
        return this.tracksById.get(trackId);
    }

    @Nullable
    public final void tryToLoadOrUpdateResourcepackFile(@NonNull String trackId, Consumer<MusicTrack> trackConsumer) throws Exception {
    	Consumer<MusicTrack> atrackConsumer = new Consumer<MusicTrack>() {

			@Override
			public void accept(MusicTrack track) {
				if (track == null) {
					return;
				}
				MusicPlatform.this.loadOrUpdateResourcepackFile(track, new Consumer<Boolean>() {
					@Override
					public void accept(Boolean success) {
						if(success) {
							MusicPlatform.this.tracksById.put(track.getId(), track);
						}
					}
				});
			}
    		
    	};
    	this.loadTrackFromStorage(trackId, atrackConsumer.andThen(trackConsumer));
    	
    }
    
    public abstract void enable();
    
    public abstract void disable();

    @NonNull
    protected abstract void loadAllTracksFromStorage(Consumer<MusicTrack> trackConsumer, Runnable runafter);

    @Nullable
    protected abstract void loadTrackFromStorage(@NonNull String trackId, Consumer<MusicTrack> trackConsumer) throws Exception;

    public abstract void getPlayersLoadedTrack(@NonNull MusicTrack track, Consumer<List<Player>> playersConsumer);
    
    protected abstract void loadOrUpdateResourcepackFile(@NonNull MusicTrack track, Consumer<Boolean> statusConsumer);

    public abstract void setResourcepackTrack(@NonNull Player player, @NonNull MusicTrack track, Consumer<Boolean> statusConsumer);

    @Nullable
    public abstract void getResourcepackTrack(@NonNull Player player, Consumer<MusicTrack> trackConsumer);

    public abstract void disableRepeatMode(@NonNull Player player);

    public abstract void startPlayingTrackFull(@NonNull Player player);

    public abstract void stopPlayingTrackFull(@NonNull Player player);

    public abstract void startPlayingTrackPiece(@NonNull Player player, int trackPieceNumber);

    public abstract void stopPlayingTrackPiece(@NonNull Player player, int trackPieceNumber);
}
