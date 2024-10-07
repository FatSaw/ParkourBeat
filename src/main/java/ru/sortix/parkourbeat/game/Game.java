package ru.sortix.parkourbeat.game;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.ActivityPacketsAdapter;
import ru.sortix.parkourbeat.game.movement.GameMoveHandler;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.ParticleController;
import ru.sortix.parkourbeat.levels.settings.LevelSettings;
import ru.sortix.parkourbeat.player.music.MusicTrack;
import ru.sortix.parkourbeat.player.music.MusicTracksManager;
import ru.sortix.parkourbeat.player.music.platform.MusicPlatform;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.world.LocationUtils;
import ru.sortix.parkourbeat.world.TeleportUtils;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
public class Game {
    public static final double BLOCKS_PER_SECOND = 5.6123;

    private static final Title.Times FINISH_REASON_TITLE_TIMES = Title.Times.of(Duration.ofMillis(500L), Duration.ofMillis(500L), Duration.ofMillis(500L));

    private final @NonNull LevelsManager levelsManager;
    private final @NonNull MusicTracksManager musicTracksManager;
    private final @NonNull ActivityPacketsAdapter packetsAdapter;
    private final @NonNull Player player;
    private final @NonNull Level level;
    private final @NonNull GameMoveHandler gameMoveHandler;
    private final @NonNull MusicMode musicMode;
    @Setter
    private @NonNull State currentState = State.PREPARING;
    private BukkitTask bossBarTask;
    private BossBar bossBar;
    private volatile int lastTrackPieceNumber = 0;

    private Game(@NonNull ParkourBeat plugin, @NonNull Player player, @NonNull Level level) {
        this.levelsManager = plugin.get(LevelsManager.class);
        this.musicTracksManager = plugin.get(MusicTracksManager.class);
        this.packetsAdapter = plugin.get(ActivityManager.class).getPacketsAdapter();
        this.player = player;
        this.level = level;
        this.gameMoveHandler = new GameMoveHandler(this);
        this.musicMode = level.getLevelSettings().getGameSettings().getMusicTrack() == null
            ? MusicMode.DISABLED
            : (level.getLevelSettings().getGameSettings().isUseTrackPieces()
            ? MusicMode.PIECES
            : MusicMode.FULL_TRACK);
        this.prepareGame(plugin);
    }

    @NonNull
    public static CompletableFuture<Game> createAsync(
        @NonNull ParkourBeat plugin,
        @NonNull Player player,
        @NonNull UUID levelId,
        boolean preventWrongSpawn
    ) {
        CompletableFuture<Game> result = new CompletableFuture<>();
        LevelsManager levelsManager = plugin.get(LevelsManager.class);
        levelsManager.loadLevel(levelId, null).thenAccept(level -> {
            if (level == null) {
                result.complete(null);
                // TODO Отгружать мир
                return;
            }
            try {
                // TODO Проверять валидность точки спауна при загрузке мира и при установке новой точки
                // TODO Отключить данную проверку для уровней, прошедших модерацию
                if (!LocationUtils.isValidSpawnPoint(level.getSpawn(), level.getLevelSettings())) {
                    if (preventWrongSpawn) {
                    	LangOptions.level_prepare_spawninvalid_prevent.sendMsg(player);

                        if (level.getWorld().getPlayers().isEmpty()) {
                            levelsManager.unloadLevelAsync(levelId, false);
                        }

                        result.complete(null);
                        return;
                    } else {
                    	LangOptions.level_prepare_spawninvalid_notify.sendMsg(player);
                    }
                }

                result.complete(new Game(plugin, player, level));
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Unable to prepare game", e);
                result.complete(null);
                // TODO Отгружать мир
            }
        });
        return result;
    }

    private void prepareGame(@NonNull ParkourBeat plugin) {
        LevelSettings settings = this.level.getLevelSettings();

        ParticleController particleController = settings.getParticleController();

        if (!particleController.isLoaded()) {
            particleController.loadParticleLocations(
                settings.getWorldSettings().getWaypoints());
        }

        this.player.setGameMode(GameMode.ADVENTURE);

        this.setCurrentState(State.READY);

        MusicTrack musicTrack = settings.getGameSettings().getMusicTrack();
        if (musicTrack == null || musicTrack.isResourcepackCurrentlySet(this.player)) return;

        if (!musicTrack.isStillAvailable()) {
        	LangOptions.level_prepare_track_unavilable.sendMsg(player, new Placeholders("%track%", musicTrack.getName()));
            return;
        }

        this.setCurrentState(State.PREPARING);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            boolean startedSuccessfully = musicTrack.setResourcepackAsync(this.getPlugin(), this.player);
            if (startedSuccessfully) return;
            LangOptions.level_prepare_track_sendfail.sendMsg(player, new Placeholders("%track%", musicTrack.getName()));
            this.setCurrentState(State.READY);

        }, 20L);
    }

    @NonNull
    public ParkourBeat getPlugin() {
        return this.levelsManager.getPlugin();
    }

    public void start() {
        if (this.currentState != State.READY) {
            return;
        }

        this.setCurrentState(State.RUNNING);

        if (!this.player.isSprinting() || this.player.isSneaking()) {
            this.failLevel(LangOptions.level_play_title_pressrun.getComponent(player), null);
            return;
        }

        this.level.getLevelSettings().getParticleController().startSpawnParticles(this.player);

        MusicPlatform musicPlatform = this.musicTracksManager.getPlatform();
        if (this.musicMode == MusicMode.PIECES) {
            this.packetsAdapter.setWatchingPosition(this.player, true);
            musicPlatform.disableRepeatMode(this.player);
            this.musicTracksManager.setTrackPiecesSendingEnabled(this, true);
            this.tryToSendTrackPiece();
        } else if (this.musicMode == MusicMode.FULL_TRACK) {
            musicPlatform.disableRepeatMode(this.player);
            musicPlatform.startPlayingTrackFull(this.player);
        }

        Plugin plugin = this.levelsManager.getPlugin();
        for (Player onlinePlayer : this.player.getWorld().getPlayers()) {
            this.player.hidePlayer(plugin, onlinePlayer);
        }

        createBossBar();
    }

    public void tryToSendTrackPiece() {
        double distance = this.getPassedDistance(true);
        int trackSectionNumber = (int) Math.floor(distance / BLOCKS_PER_SECOND) + 1;
        if (trackSectionNumber <= this.lastTrackPieceNumber) return;
        this.lastTrackPieceNumber = trackSectionNumber;
        this.sendTrackPiece(trackSectionNumber);
    }

    private void sendTrackPiece(int trackSectionNumber) {
        this.musicTracksManager.getPlatform().startPlayingTrackPiece(this.player, trackSectionNumber);
    }

    public void failLevel(@Nullable Component reasonFirstLine, @Nullable Component reasonSecondLine) {
        TeleportUtils.teleportAsync(this.getPlugin(), this.player, this.level.getSpawn()).whenComplete((success, throwable) -> {
            try {
            	String progress = this.bossBar == null ? null : String.format("%.0f", this.bossBar.progress() * 100f);
                this.resetLevelGame(
                    reasonFirstLine,
                    reasonSecondLine != null
                        ? reasonSecondLine
                        : progress == null ? Component.empty() : LangOptions.level_play_progress.getComponent(player, new Placeholders("%value%", progress)),
                    false
                );
            } catch (Throwable t) {
                this.getPlugin().getLogger().log(java.util.logging.Level.SEVERE, "Unable to reset game", t);
            }
        });
    }

    public void completeLevel() {
        TeleportUtils.teleportAsync(this.getPlugin(), this.player, this.level.getSpawn()).whenComplete((success, throwable) -> {
            try {
                this.resetLevelGame(LangOptions.level_play_title_complete.getComponent(player), null, true);
            } catch (Throwable t) {
                this.getPlugin().getLogger().log(java.util.logging.Level.SEVERE, "Unable to reset game", t);
            }
        });
    }

    public void resetLevelGame(@Nullable Component reasonFirstLine, @Nullable Component reasonSecondLine, boolean levelComplete) {
        boolean switchState = this.currentState == State.RUNNING;
        this.resetRunningLevelGame(reasonFirstLine, reasonSecondLine, levelComplete);
        this.forceStopLevelGame();
        if (switchState) this.setCurrentState(State.READY);
    }

    private void resetRunningLevelGame(@Nullable Component reasonFirstLine, @Nullable Component reasonSecondLine, boolean levelComplete) {
        if (this.currentState != State.RUNNING) return;

        this.player.showTitle(Title.title(
            reasonFirstLine == null ? Component.empty() : reasonFirstLine,
            reasonSecondLine == null ? Component.empty() : reasonSecondLine,
            FINISH_REASON_TITLE_TIMES
        ));

        if (levelComplete) {
            this.player.playSound(this.player.getLocation(), Sound.ENTITY_SILVERFISH_DEATH, 1, 1);
            this.player.playSound(this.player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
        } else {
            this.player.playSound(this.player.getLocation(), Sound.ENTITY_SILVERFISH_DEATH, 1, 1);
        }

        this.gameMoveHandler.getAccuracyChecker().reset();
    }

    public void forceStopLevelGame() {
        this.player.setHealth(20);
        this.player.setGameMode(GameMode.ADVENTURE);

        if (this.musicMode == MusicMode.PIECES) {
            this.musicTracksManager.setTrackPiecesSendingEnabled(this, false);
            this.packetsAdapter.setWatchingPosition(this.player, false);
            this.musicTracksManager.getPlatform().stopPlayingTrackPiece(this.player, this.lastTrackPieceNumber);
            this.lastTrackPieceNumber = 0;
        } else if (this.musicMode == MusicMode.FULL_TRACK) {
            this.musicTracksManager.getPlatform().stopPlayingTrackFull(this.player);
        }

        this.level.getLevelSettings().getParticleController().stopSpawnParticlesForPlayer(this.player);

        Plugin plugin = this.getPlugin();
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            this.player.showPlayer(plugin, onlinePlayer);
        }

        removeBossBar();
    }

    public enum State {
        PREPARING,
        READY,
        RUNNING,
    }

    private void createBossBar() {
        removeBossBar();

        this.bossBar = BossBar.bossBar(Component.empty(), 0.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        this.player.showBossBar(this.bossBar);

        bossBarTask = Bukkit.getScheduler().runTaskTimer(getPlugin(), this::updateBossBar, 0L, 1L);
    }

    private void removeBossBar() {
        if (bossBarTask != null && !bossBarTask.isCancelled()) {
            bossBarTask.cancel();
            bossBarTask = null;
        }

        if (bossBar != null) {
            this.player.hideBossBar(this.bossBar);
            bossBar = null;
        }
    }

    private void updateBossBar() {
        float progress = this.getPassedProgress();
        this.bossBar.name(Component.text(String.format("%d%%", Math.round(progress * 100)), NamedTextColor.YELLOW));
        this.bossBar.progress(progress);
    }

    /**
     * @return Value between 0.0 and 1.0
     */
    private float getPassedProgress() {
        float passedProgress = (float) (this.getPassedDistance(false) / this.level.getLevelSettings().getTotalLevelDistance());
        if (passedProgress < 0) return 0;
        if (passedProgress > 1) return 1;
        return passedProgress;
    }

    /**
     * @param realtime If true position can be accessed asynchronously at any time
     * @return Distance in blocks from 0.0 to level distance
     */
    private double getPassedDistance(boolean realtime) {
        LevelSettings levelSettings = this.level.getLevelSettings();

        double playerPos;
        if (realtime) {
            playerPos = levelSettings.getDirectionChecker().getCoordinate(this.packetsAdapter.getPosition(this.player));
        } else {
            playerPos = levelSettings.getDirectionChecker().getCoordinate(this.player.getLocation());
        }
        double startPos = levelSettings.getStartPosition();

        double passedDistance = playerPos < startPos
            ? startPos - playerPos
            : playerPos - startPos;

        return Math.max(0, Math.min(levelSettings.getTotalLevelDistance(), passedDistance));
    }
}
