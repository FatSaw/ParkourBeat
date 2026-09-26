package ru.sortix.parkourbeat.tutorial;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.EntityEffect;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.PlayActivity;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.lang.PlayerLang;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ТУТОРИАЛ ПО ОБЫЧНЫМ (3D) УРОВНЯМ.
 * <p>
 * Проходит НА САМОМ УРОВНЕ-ТУТОРИАЛЕ, а не в лобби. Учить бегу, стоя на месте,
 * бессмысленно: вся игра - это непрерывный бег с зажатым бегом и прыжки только по
 * меткам. Значит и учить надо ровно этому и ровно там.
 * <p>
 * Работает туториал не на уговорах, а на последствиях. Обычный игрок читает
 * "не прыгай просто так" и всё равно прыгает, потому что это ничего ему не стоит.
 * Здесь стоит: промах бьёт по здоровью, гасит экран и ОТКАТЫВАЕТ забег. Отпустил
 * бег - предупреждение, отпустил ещё раз - тоже откат. Через два-три раза правило
 * усваивается само, без единого лишнего слова.
 */
public class TutorialManager implements PluginManager, Listener {

    /** Сколько промахов туториал прощает, прежде чем откатить забег. */
    private static final int MISS_LIMIT = 1;
    /** Сколько раз можно отпустить бег до отката. */
    private static final int SPRINT_LIMIT = 2;

    private static final Title.Times PUNISH_TIMES =
        Title.Times.of(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(250));
    /**
     * Титлы облёта висят долго и намеренно: фраза должна успеть быть прочитанной
     * человеком, который видит игру первый раз, а не тем, кто её написал.
     */
    private static final Title.Times BRIEF_TIMES =
        Title.Times.of(Duration.ofMillis(400), Duration.ofMillis(4200), Duration.ofMillis(600));

    private static class Session {
        /** Уровень, на котором идёт туториал. Вне его сессия не действует вообще. */
        private java.util.UUID levelId;
        private boolean cutscene = false;
        /** Удалось ли усадить игрока на камеру. */
        private boolean attached = false;
        /** Сколько тиков ждём, пока догрузится музыка. */
        private int waitTicks = 0;
        private boolean waitingForMusic = false;
        private int cutsceneTick = 0;
        private org.bukkit.entity.ArmorStand camera;
        private org.bukkit.GameMode savedGameMode;
        /** Дальность прорисовки частиц до облёта, чтобы вернуть её обратно. */
        private double savedViewDistance = -1.0D;

        private int misses = 0;
        private int sprintDrops = 0;
        private int restarts = 0;
        private long lastPunishAt = 0L;
        private boolean briefed = false;
    }

    private final @NonNull ParkourBeat plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    private final org.bukkit.scheduler.BukkitTask tickTask;

    public TutorialManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        if (this.sessions.isEmpty()) return;

        for (UUID playerId : new java.util.ArrayList<>(this.sessions.keySet())) {
            Session session = this.sessions.get(playerId);
            if (session == null) continue;
            if (!session.cutscene && !session.waitingForMusic) continue;

            Player player = this.plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                this.sessions.remove(playerId);
                continue;
            }

            try {
                if (session.waitingForMusic) {
                    this.tickWaitForMusic(player, session);
                    continue;
                }
                this.tickCutscene(player, session);
            } catch (Throwable t) {
                this.plugin.getLogger().warning("Туториал: ошибка облёта: " + t);
                this.endCutscene(player, session, null);
            }
        }
    }

    /**
     * Идёт ли туториал ПРЯМО СЕЙЧАС И ЗДЕСЬ.
     * <p>
     * Проверяется не только наличие сессии, но и то, что игрок находится именно на
     * уровне-туториале. Без этой проверки наказания за промах уезжали на все
     * остальные уровни: сессия оставалась висеть после выхода с туториала.
     */
    public boolean isActive(@NonNull Player player) {
        Session session = this.sessions.get(player.getUniqueId());
        if (session == null || session.levelId == null) return false;

        UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(player);
        if (!(activity instanceof PlayActivity playActivity)) return false;

        return session.levelId.equals(playActivity.getLevel().getUniqueId());
    }

    /** Идёт ли вступительный облёт: во время него игрок не управляет собой. */
    public boolean isCutscene(@NonNull Player player) {
        Session session = this.sessions.get(player.getUniqueId());
        return session != null && session.cutscene;
    }

    // ==================== ЗАПУСК ====================

    /**
     * Игрок зашёл на уровень. Туториал начинается САМ и только на своём уровне -
     * отдельной команды больше нет: тот, кто до неё додумается, в туториале и не
     * нуждается.
     */
    public void onLevelEnter(@NonNull Player player, @NonNull ru.sortix.parkourbeat.levels.Level level) {
        if (!this.isTutorialLevel(level)) return;

        Session session = new Session();
        session.levelId = level.getUniqueId();
        // Облёт начинаем НЕ СРАЗУ: сначала клиент грузит музыку и показывает свой
        // заголовок. Наши титлы поверх него просто не видны, поэтому ждём.
        session.waitingForMusic = true;
        this.sessions.put(player.getUniqueId(), session);
    }

    /** Игрок ушёл с уровня - сессия закрывается немедленно. */
    public void onLevelLeave(@NonNull Player player) {
        Session session = this.sessions.remove(player.getUniqueId());
        if (session == null) return;
        this.endCutscene(player, session, null);
    }

    private boolean isTutorialLevel(@NonNull ru.sortix.parkourbeat.levels.Level level) {
        GameSettings tutorial = this.findTutorialLevel();
        return tutorial != null && tutorial.getUniqueId().equals(level.getUniqueId());
    }

    public void stop(@NonNull Player player, boolean quiet) {
        Session session = this.sessions.remove(player.getUniqueId());
        if (session == null || !player.isOnline()) return;

        this.endCutscene(player, session, null);
        try {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        } catch (Throwable ignored) {
        }
    }

    /** Дольше этого не ждём: музыка может не загрузиться вообще. */
    private static final int MUSIC_WAIT_TIMEOUT_TICKS = 400;
    /** Пауза после исчезновения чужого заголовка, чтобы титлы не наложились. */
    private static final int MUSIC_WAIT_GRACE_TICKS = 20;

    /**
     * Ждём, пока клиент догрузит музыку.
     * <p>
     * Пока висит заголовок "Загрузка музыки...", наши титлы просто не видны:
     * заголовок один на экран, и побеждает последний показанный. Раньше первые
     * фразы облёта уходили в никуда именно поэтому.
     */
    private void tickWaitForMusic(@NonNull Player player, @NonNull Session session) {
        session.waitTicks++;

        UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(player);
        if (!(activity instanceof PlayActivity playActivity)) {
            this.sessions.remove(player.getUniqueId());
            return;
        }

        boolean loading = this.isMusicLoading(player);
        if (loading) {
            // Пока грузится - отсчёт паузы начинаем заново.
            session.waitTicks = 0;
        }

        boolean waitedEnough = !loading && session.waitTicks >= MUSIC_WAIT_GRACE_TICKS;
        boolean timedOut = session.waitTicks >= MUSIC_WAIT_TIMEOUT_TICKS;
        if (!waitedEnough && !timedOut) return;

        session.waitingForMusic = false;
        session.waitTicks = 0;
        this.startCutscene(player, session, playActivity.getLevel());
    }

    private boolean isMusicLoading(@NonNull Player player) {
        try {
            ru.sortix.parkourbeat.player.music.platform.MusicPlatform platform =
                this.plugin.get(ru.sortix.parkourbeat.player.music.MusicTracksManager.class).getPlatform();

            if (platform instanceof ru.sortix.parkourbeat.player.music.platform.AMusicPlatform aMusic) {
                return aMusic.getDispatcher().isPending(player.getUniqueId());
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // ==================== ВСТУПИТЕЛЬНЫЙ ОБЛЁТ ====================

    /**
     * Длительность облёта, тиков. Тридцать шесть секунд - это МНОГО по ощущениям
     * разработчика и ровно столько, сколько нужно, чтобы человек, видящий игру
     * впервые, успел прочитать шесть коротких фраз и посмотреть на трассу.
     */
    private static final int DEFAULT_CUTSCENE_TICKS = 800;
    private static final double DEFAULT_CUTSCENE_HEIGHT = 5.0D;
    /** Скорость облёта, блоков за тик. Медленно и намеренно. */
    private static final double DEFAULT_CUTSCENE_SPEED = 0.25D;

    /** Через сколько тиков после начала пробуем усадить игрока на камеру. */
    private static final int CAMERA_ATTACH_DELAY = 5;
    /** Сколько тиков пытаемся, прежде чем перейти на запасной способ. */
    private static final int CAMERA_ATTACH_TIMEOUT = 30;

    private int cutsceneTicks() {
        return Math.max(60, this.plugin.getConfig().getInt("tutorial.cutscene_ticks", DEFAULT_CUTSCENE_TICKS));
    }

    private double cutsceneHeight() {
        return this.plugin.getConfig().getDouble("tutorial.cutscene_height", DEFAULT_CUTSCENE_HEIGHT);
    }

    private double cutsceneSpeed() {
        return this.plugin.getConfig().getDouble("tutorial.cutscene_speed", DEFAULT_CUTSCENE_SPEED);
    }

    /** Дальность прорисовки частиц на время облёта. */
    private double cutsceneViewDistance() {
        return this.plugin.getConfig().getDouble("tutorial.cutscene_view_distance", 45.0D);
    }

    private double cutscenePitch() {
        return this.plugin.getConfig().getDouble("tutorial.cutscene_pitch", 20.0D);
    }

    /**
     * Куда смотрит камера. Считается по направлению уровня, но с поправкой из
     * конфига: если на вашей карте взгляд всё же уводит вбок, правится
     * {@code tutorial.cutscene_yaw_offset} без пересборки.
     */
    private float cutsceneYaw(@NonNull org.bukkit.util.Vector forward) {
        float yaw = ru.sortix.parkourbeat.twod.TwoDEntityUtils.yawOf(forward);
        return yaw + (float) this.plugin.getConfig().getDouble("tutorial.cutscene_yaw_offset", 0.0D);
    }

    private void startCutscene(@NonNull Player player, @NonNull Session session,
                               @NonNull ru.sortix.parkourbeat.levels.Level level) {
        try {
            org.bukkit.Location start = level.getLevelSettings().getStartWaypointLoc();
            if (start == null) start = level.getSpawn();

            org.bukkit.Location cameraStart = start.clone().add(0.0D, this.cutsceneHeight(), 0.0D);

            // Угол задаём СРАЗУ при создании стенда.
            // Привязанная камера берёт поворот у сущности, а обновления поворота
            // арморстенда доезжают до клиента не мгновенно - если не выставить угол
            // при спавне, первые секунды игрок смотрит туда, куда смотрел стенд
            // при рождении, то есть в стену.
            org.bukkit.util.Vector startForward = ru.sortix.parkourbeat.twod.TwoDGeometry.forwardVector(
                level.getLevelSettings().getDirectionChecker().direction());
            cameraStart.setYaw(this.cutsceneYaw(startForward));
            cameraStart.setPitch((float) this.cutscenePitch());

            session.camera = level.getWorld().spawn(cameraStart, org.bukkit.entity.ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setSilent(true);
                stand.setInvulnerable(true);
                stand.setRemoveWhenFarAway(false);
            });

            session.savedGameMode = player.getGameMode();
            session.cutscene = true;
            session.cutsceneTick = 0;
            session.attached = false;

            // Камера ведётся через режим наблюдателя: игрок физически не может ни
            // уйти, ни начать забег раньше времени.
            //
            // Привязка делается НЕ СРАЗУ: только что созданного арморстенда клиент
            // ещё не знает, и setSpectatorTarget по нему молча не срабатывает -
            // именно поэтому игрока просто таскало за камерой вместо привязки.
            // Ждём несколько тиков и пробуем, пока не получится.
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            player.teleport(cameraStart);

            // ЧАСТИЦЫ ДОЛЖНО БЫТЬ ВИДНО.
            //
            // Главное: трасса вообще не рисуется до старта забега - её включает
            // Game.start(), то есть уже после облёта. Поэтому на облёте не было
            // видно ничего, и дальность прорисовки была тут ни при чём.
            // Включаем показ вручную и выключаем в конце, чтобы обычный порядок
            // (включение при старте забега) не сломался.
            //
            // Заодно поднимаем дальность: камера летит выше и быстрее игрока, а
            // штатных семи с половиной блоков с высоты не хватает.
            try {
                ru.sortix.parkourbeat.levels.settings.WorldSettings worldSettings =
                    level.getLevelSettings().getWorldSettings();
                session.savedViewDistance = worldSettings.getParticleViewDistance();

                level.getLevelSettings().getParticleController()
                    .setViewDistance(this.cutsceneViewDistance());
                level.getLevelSettings().getParticleController()
                    .startSpawnParticles(player);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            this.plugin.getLogger().warning("Туториал: облёт не удался: " + t);
            session.cutscene = false;
            this.endCutscene(player, session, level);
        }
    }

    private void tickCutscene(@NonNull Player player, @NonNull Session session) {
        UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(player);
        if (!(activity instanceof PlayActivity playActivity)) {
            this.endCutscene(player, session, null);
            return;
        }

        ru.sortix.parkourbeat.levels.Level level = playActivity.getLevel();
        session.cutsceneTick++;

        // ВОЗВРАЩАЕМ КАМЕРУ КАЖДЫЙ ТИК.
        //
        // По SHIFT клиент сам отцепляется от наблюдаемой сущности - это ванильное
        // поведение, отключить его нельзя. Поэтому мы просто цепляем обратно:
        // пропустить облёт не выйдет, максимум мигнёт картинка.
        this.attachCamera(player, session);

        this.showCutsceneText(player, session.cutsceneTick);

        try {
            org.bukkit.util.Vector forward = ru.sortix.parkourbeat.twod.TwoDGeometry.forwardVector(
                level.getLevelSettings().getDirectionChecker().direction());

            org.bukkit.Location at = session.camera.getLocation();
            double speed = this.cutsceneSpeed();
            double x = at.getX() + forward.getX() * speed;
            double z = at.getZ() + forward.getZ() * speed;

            // Смотрим вперёд и слегка вниз: видно и трассу под собой, и то, что дальше.
            float yaw = this.cutsceneYaw(forward);
            ru.sortix.parkourbeat.twod.TwoDEntityUtils.moveRaw(
                session.camera, x, at.getY(), z, yaw, (float) this.cutscenePitch());

            // Запасной способ, если привязка так и не случилась: просто ведём
            // игрока за камерой телепортом. Менее гладко, но лучше, чем ничего.
            if (!session.attached && session.cutsceneTick > CAMERA_ATTACH_TIMEOUT) {
                org.bukkit.Location target = session.camera.getLocation();
                target.setYaw(yaw);
                target.setPitch((float) this.cutscenePitch());
                player.teleport(target);
            }
        } catch (Throwable ignored) {
        }

        if (session.cutsceneTick >= this.cutsceneTicks()) {
            this.endCutscene(player, session, level);
        }
    }

    /**
     * Усадить игрока на камеру. Пытаемся каждый тик, пока не выйдет: клиенту нужно
     * время, чтобы узнать о новой сущности.
     */
    private void attachCamera(@NonNull Player player, @NonNull Session session) {
        if (session.cutsceneTick < CAMERA_ATTACH_DELAY) return;
        if (session.camera == null || !session.camera.isValid()) return;

        try {
            if (session.camera.equals(player.getSpectatorTarget())) {
                session.attached = true;
                return;
            }
            player.setSpectatorTarget(session.camera);
            session.attached = session.camera.equals(player.getSpectatorTarget());
        } catch (Throwable ignored) {
        }
    }

    private void showCutsceneText(@NonNull Player player, int tick) {
        // Раз в сто тиков, то есть раз в пять секунд. Игрок видит игру впервые:
        // ему нужно и прочитать фразу, и посмотреть, о чём она.
        switch (tick) {
            case 20 -> this.step(player, "intro", Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f);
            case 140 -> this.step(player, "line", Sound.BLOCK_NOTE_BLOCK_HARP, 1.4f);
            case 260 -> this.step(player, "rings", Sound.BLOCK_NOTE_BLOCK_PLING, 1.6f);
            case 380 -> this.step(player, "nojump", Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f);
            case 490 -> this.step(player, "sprint", Sound.BLOCK_NOTE_BLOCK_HARP, 1.4f);
            case 600 -> this.step(player, "particles", Sound.BLOCK_NOTE_BLOCK_HARP, 1.0f);
            case 700 -> this.step(player, "go", Sound.BLOCK_NOTE_BLOCK_PLING, 1.8f);

            default -> {
            }
        }
    }

    /**
     * Один шаг облёта: титл, звук и, если нужно, строка в чат для чтения без спешки.
     * <p>
     * Тексты лежат в lang.yml под {@code tutorial.step.<шаг>}. Строка в чат
     * необязательна: если ключа {@code .chat} у шага нет, Lang возвращает сам ключ -
     * значит, шаг ограничивается титлом.
     */
    private void step(@NonNull Player player, @NonNull String step,
                      @NonNull Sound sound, float pitch) {
        String lang = PlayerLang.of(player);
        String prefix = "tutorial.step." + step;

        this.title(player, Lang.raw(lang, prefix + ".title"), Lang.raw(lang, prefix + ".subtitle"));
        player.playSound(player.getLocation(), sound, 1f, pitch);

        String chatKey = prefix + ".chat";
        String chatLine = Lang.raw(lang, chatKey);
        if (chatLine.equals(chatKey)) return;
        player.sendMessage(Component.empty());
        player.sendMessage(PbText.of("  " + chatLine));
    }

    private void endCutscene(@NonNull Player player, @NonNull Session session,
                             @Nullable ru.sortix.parkourbeat.levels.Level level) {
        session.cutscene = false;

        // Возвращаем всё как было: дальность частиц и сам факт показа. Трассу
        // снова включит забег, когда игрок побежит - как и на любом другом уровне.
        if (level != null && session.savedViewDistance > 0) {
            try {
                level.getLevelSettings().getParticleController()
                    .setViewDistance(session.savedViewDistance);
                level.getLevelSettings().getParticleController()
                    .stopSpawnParticlesForPlayer(player);
            } catch (Throwable ignored) {
            }
        }
        session.savedViewDistance = -1.0D;

        try {
            if (session.camera != null) {
                if (session.camera.equals(player.getSpectatorTarget())) {
                    player.setSpectatorTarget(null);
                }
                session.camera.remove();
                session.camera = null;
            }
        } catch (Throwable ignored) {
        }

        if (!player.isOnline()) return;

        try {
            player.setGameMode(session.savedGameMode == null
                ? org.bukkit.GameMode.ADVENTURE : session.savedGameMode);

            if (level != null) player.teleport(level.getSpawn());
        } catch (Throwable ignored) {
        }
    }

    private void title(@NonNull Player player, @NonNull String first, @NonNull String second) {
        player.showTitle(Title.title(PbText.of(first), PbText.of(second), BRIEF_TIMES));
    }

    // ==================== ПОСЛЕДСТВИЯ ====================

    /**
     * Промах во время туториала.
     * <p>
     * Вызывается из {@link Game#registerJump}. Здесь и происходит то, чего не хватало:
     * промах не просто пишется в статистику, он ОЩУЩАЕТСЯ.
     */
    public void onMiss(@NonNull Player player, @NonNull Game game) {
        Session session = this.sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!this.canPunish(session)) return;

        session.misses++;
        this.hurt(player);

        String lang = PlayerLang.of(player);
        player.showTitle(Title.title(
            Lang.text(lang, "tutorial.punish.jump.title"),
            Lang.text(lang, "tutorial.punish.jump.subtitle"), PUNISH_TIMES));

        player.sendMessage(Component.empty());
        for (Component line : Lang.lore(lang, "tutorial.punish.jump.chat")) {
            player.sendMessage(line);
        }
        player.sendMessage(Component.empty());

        if (session.misses >= MISS_LIMIT) {
            this.restartRun(player, session, "tutorial.restart.miss", "tutorial.restart.subtitle");
        }
    }

    /**
     * Игрок отпустил бег во время забега.
     * <p>
     * Это вторая половина основы игры: уровень проходится на постоянном беге, а не
     * перебежками. Первый раз предупреждаем, дальше откатываем.
     */
    private void onSprintDropped(@NonNull Player player) {
        Session session = this.sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!this.canPunish(session)) return;

        session.sprintDrops++;

        if (session.sprintDrops < SPRINT_LIMIT) {
            player.showTitle(Title.title(
                Lang.text(player, "tutorial.punish.sprint.title"),
                Lang.text(player, "tutorial.punish.sprint.subtitle"), PUNISH_TIMES));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
            return;
        }

        this.hurt(player);
        this.restartRun(player, session, "tutorial.restart.sprint", "tutorial.restart.subtitle");
    }

    /** Больно, коротко и наглядно: удар, тряска экрана и провал в темноту. */
    private void hurt(@NonNull Player player) {
        try {
            player.playEffect(EntityEffect.HURT);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.9f);
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_HURT, 0.7f, 0.7f);
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS, 16, 0, false, false, false));
        } catch (Throwable ignored) {
        }
    }

    private boolean canPunish(@NonNull Session session) {
        // Один откат за раз: иначе за время телепорта прилетит ещё десяток промахов
        // и игрок получит подряд пять наказаний за одну ошибку.
        long now = System.currentTimeMillis();
        if (now - session.lastPunishAt < 1500L) return false;
        session.lastPunishAt = now;
        return true;
    }

    /**
     * @param firstLineKey  ключ lang.yml для первой строки титла
     * @param secondLineKey ключ lang.yml для второй строки
     */
    private void restartRun(@NonNull Player player, @NonNull Session session,
                            @NonNull String firstLineKey, @NonNull String secondLineKey) {
        session.restarts++;
        session.misses = 0;
        session.sprintDrops = 0;

        UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(player);
        if (!(activity instanceof PlayActivity playActivity)) return;

        try {
            playActivity.getGame().failLevel(
                Lang.text(player, firstLineKey), Lang.text(player, secondLineKey));
        } catch (Throwable t) {
            this.plugin.getLogger().warning("Туториал: не удалось перезапустить забег: " + t);
        }
    }

    // ==================== СОБЫТИЯ ====================

    @EventHandler(priority = EventPriority.MONITOR)
    private void on(PlayerToggleSprintEvent event) {
        if (event.isSprinting()) return;

        Player player = event.getPlayer();
        if (!this.isActive(player)) return;

        UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(player);
        if (!(activity instanceof PlayActivity playActivity)) return;
        if (playActivity.getGame().getCurrentState() != Game.State.RUNNING) return;

        this.onSprintDropped(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void on(PlayerQuitEvent event) {
        this.sessions.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Уровень пройден. Вызывается из {@link Game} по завершении.
     */
    public void onLevelCompleted(@NonNull Player player) {
        Session session = this.sessions.remove(player.getUniqueId());
        if (session == null) return;

        String lang = PlayerLang.of(player);
        player.showTitle(Title.title(
            Lang.text(lang, "tutorial.finish.title"),
            session.restarts == 0
                ? Lang.text(lang, "tutorial.finish.clean")
                : Lang.text(lang, "tutorial.finish.restarts",
                "%count%", String.valueOf(session.restarts)), BRIEF_TIMES));

        player.sendMessage(Component.empty());
        for (Component line : Lang.lore(lang, "tutorial.finish.chat")) {
            player.sendMessage(line);
        }
        player.sendMessage(Component.empty());
    }

    /**
     * Уровень-туториал.
     * <p>
     * По умолчанию берётся тот же, что и в главном меню сервера
     * ({@link ru.sortix.parkourbeat.inventory.type.ServerMenu#TUTORIAL_LEVEL_ID}) -
     * держать его номер в двух местах незачем. Настройка {@code tutorial.level}
     * в config.yml нужна только чтобы переопределить его, не трогая код.
     */
    @Nullable
    private GameSettings findTutorialLevel() {
        LevelsManager levels = this.plugin.get(LevelsManager.class);

        try {
            String levelId = this.plugin.getConfig().getString("tutorial.level");
            if (levelId != null && !levelId.isBlank()) {
                GameSettings configured = levels.findLevel(levelId);
                if (configured != null) return configured;
            }
        } catch (Throwable ignored) {
        }

        try {
            return levels.findLevel(String.valueOf(
                ru.sortix.parkourbeat.inventory.type.ServerMenu.TUTORIAL_LEVEL_ID));
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void disable() {
        try {
            this.tickTask.cancel();
        } catch (Throwable ignored) {
        }
        for (UUID playerId : new java.util.ArrayList<>(this.sessions.keySet())) {
            Player player = this.plugin.getServer().getPlayer(playerId);
            if (player != null) this.stop(player, true);
        }
        this.sessions.clear();
        HandlerList.unregisterAll(this);
    }
}
