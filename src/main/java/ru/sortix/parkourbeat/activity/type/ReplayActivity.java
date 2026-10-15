package ru.sortix.parkourbeat.activity.type;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.levels.settings.SkyType;
import ru.sortix.parkourbeat.rating.JumpResult;
import ru.sortix.parkourbeat.replay.ReplayData;
import ru.sortix.parkourbeat.replay.ReplayFrame;
import ru.sortix.parkourbeat.replay.ReplayJump;
import ru.sortix.parkourbeat.replay.ReplayNpc;
import ru.sortix.parkourbeat.stats.RunResult;

import ru.sortix.parkourbeat.utils.text.PbText;

/**
 * Просмотр записанного забега.
 * <p>
 * Зритель находится в режиме наблюдателя (GM 3) и с самого начала «вселён» в NPC —
 * ванильно, пакетом камеры. Дальше всё делает клиент: интерполяция, поворот головы,
 * плавность. Вылезти из NPC и залезть обратно можно как в обычном спектейторе — левой
 * кнопкой мыши по нему.
 * <p>
 * Раньше зритель был в SURVIVAL с креативным полётом и вечной невидимостью: он мог
 * упереться в блоки, получить урон и вообще существовал в мире как обычный игрок.
 */
public class ReplayActivity extends UserActivity {

    /**
     * Сколько ждём ресурспак, прежде чем начать реплей молча.
     * <p>
     * Двенадцати секунд не хватало: на живом сервере пак приезжал за 24 секунды, реплей
     * успевал стартовать без музыки и шёл в тишине до самого конца.
     */
    private static final int MAX_PACK_WAIT_TICKS = 20 * 90;
    /** Обратный отсчёт перед стартом, когда пак уже готов. */
    private static final int COUNTDOWN_TICKS = 60;

    private final @NonNull ReplayData replay;
    @Getter
    private final @NonNull RunResult run;
    @Getter
    private ReplayNpc npc = null;

    private int frameIndex = 0;
    private boolean finished = false;

    private boolean playing = false;
    private int ticksToStart = COUNTDOWN_TICKS;
    private int packWaitTicks = 0;

    /**
     * Пак трека уровня: пока он не приехал, музыки у клиента физически нет.
     * null - ответа ещё нет, TRUE/FALSE - приехал успешно / не приехал.
     */
    private volatile Boolean musicPackReady = null;
    private boolean musicStarted = false;


    public ReplayActivity(@NonNull ParkourBeat plugin, @NonNull Player player,
                          @NonNull Level level, @NonNull ReplayData replay, @NonNull RunResult run) {
        super(plugin, player, level);
        this.replay = replay;
        this.run = run;
    }

    /**
     * Вызывается стартером, когда ресурспак уровня доехал (или окончательно не доехал).
     */
    public void setMusicPackReady(boolean ready) {
        this.musicPackReady = ready;
        if (!this.player.isOnline()) return;

        if (!ready) {
            this.player.sendMessage(PbText.of("&cМузыка уровня не загрузилась, смотрим без звука"));
            return;
        }

        // Пак мог доехать уже после старта. Догонять песню перемоткой нечем, поэтому
        // включаем её только если реплей всё ещё в самом начале - иначе будет каша.
        if (this.playing && !this.finished && this.frameIndex < 20) {
            this.startMusic();
        }
    }

    /**
     * У уровня вообще нет трека - ждать нечего.
     */
    public void setNoMusicTrack() {
        this.musicPackReady = Boolean.FALSE;
    }

    @Override
    public void startActivity() {
        this.level.getLightShow().getBaseSky().apply(this.player);
        ru.sortix.parkourbeat.levels.ParticleController particleController =
            this.level.getLevelSettings().getParticleController();
        this.level.applyViewDistances();
        if (!particleController.isLoaded()) {
            particleController.loadParticleLocations(
                this.level.getLevelSettings().getWorldSettings().getWaypoints());
        }
        particleController.startSpawnParticles(this.player);

        // НА ДУЭЛЬНОЙ КАРТЕ В РЕПЛЕЕ ВИДНЫ ОБЕ ТРАССЫ.
        //
        // В записи забега сторона не сохранена, а показывать одну синюю линию, пока
        // игрок в записи бежит по красной, - значит показывать, что он бежал мимо пути.
        // Поэтому рисуются обе: какая из них его, видно по тому, где он бежит.
        if (ru.sortix.parkourbeat.duel.DuelManager.isDuel(this.level)) {
            this.plugin.get(ru.sortix.parkourbeat.duel.DuelManager.class)
                .showBothPaths(this.level, this.player);
        }

        if (this.replay.getFrames().isEmpty()) {
            this.player.sendMessage(PbText.of("&cРеплей пуст"));
            this.finished = true;
            return;
        }

        // Наблюдатель: не мешает игрокам на уровне, не падает и не получает урона.
        // Невидимость и креативный полёт при этом больше не нужны.
        this.player.setGameMode(GameMode.SPECTATOR);

        Location start = this.replay.getFrames().get(0).toLocation(this.level.getWorld());

        this.npc = new ReplayNpc(this.plugin, this.player, this.replay.getPlayerName(), this.replay.getPlayerId());
        this.npc.spawn(start);

        // Вселяемся сразу, как и просили: реплей начинается от первого лица.
        this.npc.attachCamera();
        // Вылез из NPC (в ваниле это Shift) - можно вернуться кликом по нему.
        this.npc.listenForClicks(() -> {
            if (this.npc != null) this.npc.attachCamera();
        });

        this.player.sendMessage(PbText.of("&aРеплей игрока &f" + this.replay.getPlayerName()));
    }

    public Location getCurrentLocation() {
        if (this.replay.getFrames().isEmpty()) return this.player.getLocation();
        int index = Math.max(0, Math.min(this.frameIndex, this.replay.getFrames().size() - 1));
        return this.replay.getFrames().get(index).toLocation(this.level.getWorld());
    }

    /**
     * Подтягивает НЕВИДИМОГО зрителя за NPC.
     * <p>
     * Камера у клиента уже сидит на NPC, но чанки сервер грузит вокруг самого игрока: если
     * его не двигать, дальняя часть трассы приедет пустотой. Поэтому телепортируем зрителя
     * следом — на картинке это не видно, он смотрит глазами NPC.
     */
    private void keepViewerNearNpc(@NonNull Location location) {
        Location current = this.player.getLocation();
        if (current.getWorld() == location.getWorld()
            && current.distanceSquared(location) < FOLLOW_DISTANCE_SQUARED) {
            return;
        }
        this.player.teleport(location);
    }

    /**
     * Дальше этого расстояния зритель отстаёт настолько, что чанки перестают грузиться.
     */
    private static final double FOLLOW_DISTANCE_SQUARED = 24 * 24;

    @Override
    public void onTick() {
        if (this.finished || this.npc == null) return;
        if (!this.player.isOnline()) return;

        if (!this.playing) {
            // Сначала дожидаемся ресурспака: старт без него означает немой реплей,
            // а титул "Загрузка..." для того и висит.
            if (this.musicPackReady == null && this.packWaitTicks++ < MAX_PACK_WAIT_TICKS) {
                this.showLoadingTitle();
                return;
            }

            if (this.ticksToStart > 0) {
                this.showLoadingTitle();
                this.ticksToStart--;
                // Пока идёт отсчёт, держим зрителя на стартовом кадре.
                this.keepViewerNearNpc(this.replay.getFrames().get(0).toLocation(this.level.getWorld()));
                return;
            }

            this.playing = true;
            if (Boolean.TRUE.equals(this.musicPackReady)) this.startMusic();
        }

        java.util.List<ReplayFrame> frames = this.replay.getFrames();
        if (this.frameIndex >= frames.size()) {
            this.onReplayEnd();
            return;
        }

        ReplayFrame frame = frames.get(this.frameIndex);
        Location loc = frame.toLocation(this.level.getWorld());
        this.npc.teleport(loc);
        this.npc.setSneaking(frame.isSneaking());
        this.keepViewerNearNpc(loc);

        if (frame.isSwinging()) {
            this.npc.swingArm();
        }

        for (ReplayJump jump : this.replay.getJumps()) {
            if (jump.getFrameIndex() == this.frameIndex) {
                JumpResult result = jump.getResult();
                net.kyori.adventure.text.Component points;
                if (result == JumpResult.MISS) {
                    points = PbText.of("&7MISS");
                } else {
                    points = PbText.of(result.formatPoints());
                }
                this.player.showTitle(net.kyori.adventure.title.Title.title(
                    net.kyori.adventure.text.Component.empty(),
                    points,
                    net.kyori.adventure.title.Title.Times.of(
                        java.time.Duration.ZERO, java.time.Duration.ofMillis(150), java.time.Duration.ofMillis(100)
                    )
                ));
            }
        }

        this.frameIndex++;
    }

    private void showLoadingTitle() {
        this.player.showTitle(net.kyori.adventure.title.Title.title(
            PbText.of("&a&lЗагрузка..."),
            net.kyori.adventure.text.Component.empty(),
            net.kyori.adventure.title.Title.Times.of(
                java.time.Duration.ZERO, java.time.Duration.ofMillis(200), java.time.Duration.ZERO
            )
        ));
    }

    private void onReplayEnd() {
        this.finished = true;
        this.player.sendMessage(PbText.of("&aРеплей закончен"));

        this.stopMusic();

        this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            if (!this.player.isOnline()) return;
            this.plugin.get(ru.sortix.parkourbeat.activity.ActivityManager.class)
                .switchActivity(this.player, null,
                    ru.sortix.parkourbeat.data.Settings.getLobbySpawn());
        }, 40L);
    }

    private void startMusic() {
        if (this.musicStarted) return;
        GameSettings settings = this.level.getLevelSettings().getGameSettings();
        if (settings.getMusicTrack() == null) return;
        try {
            ru.sortix.parkourbeat.player.music.platform.MusicPlatform platform =
                this.plugin.get(ru.sortix.parkourbeat.player.music.MusicTracksManager.class).getPlatform();
            platform.disableRepeatMode(this.player);
            platform.startPlayingTrackFull(this.player);
            this.musicStarted = true;
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Unable to start replay music for " + this.player.getName(), e);
        }
    }

    private void stopMusic() {
        this.musicStarted = false;
        GameSettings settings = this.level.getLevelSettings().getGameSettings();
        if (settings.getMusicTrack() == null) return;
        try {
            this.plugin.get(ru.sortix.parkourbeat.player.music.MusicTracksManager.class)
                .getPlatform().stopPlayingTrackFull(this.player);
        } catch (Exception ignored) {
        }
    }

    public void restart() {
        this.frameIndex = 0;
        this.finished = false;
        this.playing = false;
        this.ticksToStart = COUNTDOWN_TICKS;

        this.stopMusic();

        Location loc = this.replay.getFrames().get(0).toLocation(this.level.getWorld());
        this.player.teleport(loc);
    }

    @Override
    public void on(@NonNull PlayerMoveEvent event) {
    }

    @Override
    public void on(@NonNull PlayerToggleSprintEvent event) {
    }

    /**
     * Выход из NPC.
     * <p>
     * В ваниле наблюдателя из сущности выкидывает сервер, увидев приседание. Наш NPC
     * существует только в пакетах, поэтому сервер об этом не знает и камера намертво
     * висела на нём - выселиться было нечем. Повторяем ванильное поведение руками;
     * вернуться обратно можно кликом по NPC, как и в обычном спектейторе.
     */
    @Override
    public void on(@NonNull PlayerToggleSneakEvent event) {
        if (!event.isSneaking() || this.npc == null) return;
        this.npc.resetCamera();
    }

    @Override
    public int getFallHeight() {
        return this.getFallHeight(false);
    }

    @Override
    public void onPlayerFall() {
        this.player.teleport(this.getCurrentLocation());
    }

    @Override
    public void endActivity() {
        this.finished = true;
        if (this.npc != null) {
            // despawn() сам вернёт камеру зрителю и снимет слушатель кликов.
            this.npc.despawn();
            this.npc = null;
        }

        this.stopMusic();

        this.level.getLevelSettings().getParticleController().stopSpawnParticlesForPlayer(this.player);
        this.plugin.get(ru.sortix.parkourbeat.duel.DuelManager.class)
            .hideBothPaths(this.level, this.player);
        this.player.setGameMode(GameMode.ADVENTURE);
        this.player.setAllowFlight(false);
        this.player.setFlying(false);
        this.player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        SkyType.reset(this.player);
    }
}
