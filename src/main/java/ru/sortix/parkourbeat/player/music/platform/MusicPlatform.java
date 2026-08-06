package ru.sortix.parkourbeat.player.music.platform;

import lombok.NonNull;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.player.music.MusicTrack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public abstract class MusicPlatform {


    private volatile Map<String, MusicTrack> tracksById = Collections.emptyMap();

    public void reloadAllTracksList(Runnable runAfter) {
        Map<String, MusicTrack> collected = Collections.synchronizedMap(new LinkedHashMap<String, MusicTrack>());
        AtomicBoolean swapped = new AtomicBoolean(false);

        Consumer<MusicTrack> trackConsumer = track -> {
            if (track == null || track.getId() == null) return;
            collected.put(track.getId(), track);
        };

        Runnable finish = () -> {
            if (!swapped.compareAndSet(false, true)) return;
            synchronized (collected) {
                MusicPlatform.this.tracksById = Collections.unmodifiableMap(
                    new LinkedHashMap<>(collected));
            }
            if (runAfter != null) runAfter.run();
        };

        this.loadAllTracksFromStorage(trackConsumer, finish);
    }

    public final @NonNull List<MusicTrack> getAllTracks() {
        return new ArrayList<>(this.tracksById.values());
    }

    @Nullable
    public final MusicTrack getTrackById(@NonNull String trackId) {
        return this.tracksById.get(trackId);
    }

    /** Добавить/обновить один трек, не перезагружая весь список. */
    protected final void putTrack(@NonNull MusicTrack track) {
        Map<String, MusicTrack> updated = new LinkedHashMap<>(this.tracksById);
        updated.put(track.getId(), track);
        this.tracksById = Collections.unmodifiableMap(updated);
    }

    public final void tryToLoadOrUpdateResourcepackFile(@NonNull String trackId,
                                                        Consumer<MusicTrack> trackConsumer) {
        this.loadTrackFromStorage(trackId, track -> {
            if (track != null) {
                this.loadOrUpdateResourcepackFile(track, success -> {
                    if (Boolean.TRUE.equals(success)) MusicPlatform.this.putTrack(track);
                });
            }
            if (trackConsumer != null) trackConsumer.accept(track);
        });
    }

    public abstract void enable();

    public abstract void disable();

    protected abstract void loadAllTracksFromStorage(Consumer<MusicTrack> trackConsumer, Runnable runafter);

    protected abstract void loadTrackFromStorage(@NonNull String trackId, Consumer<MusicTrack> trackConsumer);

    public abstract void getPlayersLoadedTrack(@NonNull MusicTrack track, Consumer<List<Player>> playersConsumer);

    protected abstract void loadOrUpdateResourcepackFile(@NonNull MusicTrack track, Consumer<Boolean> statusConsumer);

    public abstract void setResourcepackTrack(@NonNull Player player, @NonNull MusicTrack track,
                                              Consumer<Boolean> statusConsumer);

    public abstract void getResourcepackTrack(@NonNull Player player, Consumer<MusicTrack> trackConsumer);

    public abstract void disableRepeatMode(@NonNull Player player);

    public abstract void startPlayingTrackFull(@NonNull Player player);

    public abstract void stopPlayingTrackFull(@NonNull Player player);

    public abstract void startPlayingTrackPiece(@NonNull Player player, int trackPieceNumber);

    public abstract void stopPlayingTrackPiece(@NonNull Player player, int trackPieceNumber);
}
