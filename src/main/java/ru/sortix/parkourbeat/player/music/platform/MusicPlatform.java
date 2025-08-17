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

    public void reloadAllTracksList() throws Exception {
        this.tracksById.clear();
        Consumer<List<MusicTrack>> tracksConsumer = new Consumer<List<MusicTrack>>() {
			
			@Override
			public void accept(List<MusicTrack> tracks) {
				tracks.stream().sorted((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName())).forEach(track -> MusicPlatform.this.tracksById.put(track.getId(), track));
			}
		};
        this.loadAllTracksFromStorage(tracksConsumer);
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
				try {
					MusicPlatform.this.loadOrUpdateResourcepackFile(track);
					MusicPlatform.this.tracksById.put(track.getId(), track);
				} catch (Exception e) {
					e.printStackTrace();
				}
		        
			}
    		
    	};
    	this.loadTrackFromStorage(trackId, atrackConsumer.andThen(trackConsumer));
    }
    
    public abstract void enable();
    
    public abstract void disable();

    @NonNull
    protected abstract void loadAllTracksFromStorage(Consumer<List<MusicTrack>> tracksConsumer) throws Exception;

    @Nullable
    protected abstract void loadTrackFromStorage(@NonNull String trackId, Consumer<MusicTrack> trackConsumer) throws Exception;

    protected abstract void loadOrUpdateResourcepackFile(@NonNull MusicTrack track) throws Exception;

    public abstract void setResourcepackTrack(@NonNull Player player, @NonNull MusicTrack track) throws Exception;

    @Nullable
    public abstract void getResourcepackTrack(@NonNull Player player, Consumer<MusicTrack> trackConsumer);

    public abstract void disableRepeatMode(@NonNull Player player);

    public abstract void startPlayingTrackFull(@NonNull Player player);

    public abstract void stopPlayingTrackFull(@NonNull Player player);

    public abstract void startPlayingTrackPiece(@NonNull Player player, int trackPieceNumber);

    public abstract void stopPlayingTrackPiece(@NonNull Player player, int trackPieceNumber);
}
