package ru.sortix.parkourbeat.twod;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemsManager;
import ru.sortix.parkourbeat.item.editor.EditorItem;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ВСЯ ЖИЗНЬ 2D-РЕЖИМА В ОДНОМ МЕНЕДЖЕРЕ.
 * <p>
 * Он держит забеги, тикает их, ловит выход игрока, показывает строителю линию в
 * редакторе и запускает игру, когда игрок наступает на стартовый блок в начале линии.
 * Обычный 3D-режим об этом классе ничего не знает: все точки входа - это несколько
 * вызовов из PlayActivity/EditActivity, которые для 3D-уровней сразу возвращают false.
 */
public class TwoDManager implements PluginManager, Listener {

    /** Метка на всех служебных сущностях 2D-режима: по ней их подчищают при старте. */
    public static final String ENTITY_TAG = "pb_2d";

    /** Отдельная метка транспорта полёта: по ней его удобно найти и перерисовать. */
    public static final String FLIGHT_VEHICLE_TAG = "pb_2d_flight_vehicle";

    private final @NonNull ParkourBeat plugin;
    private final @NonNull TwoDInput input;
    private final @NonNull TwoDVisibility visibility;
    private final Map<UUID, TwoDGame> games = new HashMap<>();

    /**
     * Игроки, которых уже несёт к старту. Без этого списка каждый шаг по стартовой
     * зоне запускал ещё один забег: телепорт асинхронный, и до появления игры в
     * games успевает прилететь десяток событий движения.
     */
    private final java.util.Set<UUID> starting = new java.util.HashSet<>();
    private final BukkitTask tickTask;

    public TwoDManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.input = new TwoDInput(plugin);
        this.visibility = new TwoDVisibility(plugin);

        this.removeStrayEntities();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickGames, 1L, 1L);
    }

    private void removeStrayEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                try {
                    if (entity.getScoreboardTags().contains(ENTITY_TAG)) entity.remove();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ==================== ПРОВЕРКИ РЕЖИМА ====================

    /**
     * Прогресс забега игрока по 2D-уровню, 0..1.
     * <p>
     * Возвращает 0, если игрок сейчас не в забеге: до старта пройдено ровно ничего.
     */
    public float getProgress(@NonNull Player player) {
        TwoDGame game = this.getGame(player);
        return game == null ? 0f : game.getPassedProgress();
    }

    /** Кому видны служебные сущности забега. */
    @NonNull
    public TwoDVisibility getVisibility() {
        return this.visibility;
    }

    public static boolean isTwoD(@Nullable Level level) {
        if (level == null) return false;
        try {
            return level.getLevelSettings().getGameSettings().getLevelMode().isTwoD();
        } catch (Throwable t) {
            return false;
        }
    }

    @Nullable
    public TwoDGame getGame(@NonNull Player player) {
        return this.games.get(player.getUniqueId());
    }

    public boolean isPlaying(@NonNull Player player) {
        if (this.starting.contains(player.getUniqueId())) return true;
        TwoDGame game = this.getGame(player);
        return game != null && game.isActive();
    }

    // ==================== ЗАПУСК И ОСТАНОВКА ====================

    /**
     * Запуск идёт в два шага: сначала игрока переносим к месту старта и только потом,
     * следующим тиком, собираем сцену. Иначе при запуске с другого конца уровня клиент
     * не успевает получить арморстенд камеры и игрок остаётся стоять где стоял.
     */
    public boolean startGame(@NonNull Player player, @NonNull Level level, boolean editorTest) {
        if (this.starting.contains(player.getUniqueId())) return true;
        this.stopGame(player, false);
        TwoDCoins.refresh(this.plugin, level, true);

        this.starting.add(player.getUniqueId());

        // Запоминаем, где игрок стоял ДО переноса к старту: именно сюда его вернём.
        Location returnLocation = editorTest ? player.getLocation().clone() : level.getSpawn();

        Location cameraStart = TwoDGeometry.cameraStart(level);
        ru.sortix.parkourbeat.world.TeleportUtils
            .teleportAsync(this.plugin, player, cameraStart)
            .thenAccept(success -> this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                this.starting.remove(player.getUniqueId());
                if (!player.isOnline()) return;
                if (this.games.containsKey(player.getUniqueId())) return;

                TwoDGame game = new TwoDGame(this.plugin, player, level, this.input,
                    editorTest, returnLocation);
                if (!game.start()) return;
                this.games.put(player.getUniqueId(), game);
            }, 2L));
        return true;
    }

    public void stopGame(@NonNull Player player, boolean teleportBack) {
        this.starting.remove(player.getUniqueId());
        TwoDGame game = this.games.remove(player.getUniqueId());
        if (game == null) return;
        try {
            game.stop(teleportBack);
        } catch (Throwable t) {
            this.plugin.getLogger().warning("2D: ошибка при завершении забега: " + t);
        }
    }

    private void tickGames() {
        if (this.games.isEmpty()) return;

        for (UUID playerId : new java.util.ArrayList<>(this.games.keySet())) {
            TwoDGame game = this.games.get(playerId);
            if (game == null) continue;
            try {
                game.tick();
            } catch (Throwable t) {
                this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "2D: ошибка в тике забега", t);
                game.stop(true);
            }
            if (!game.isActive()) {
                this.games.remove(playerId);
                this.onGameFinished(playerId, game);
            }
        }
    }

    /**
     * Забег закончился сам (SHIFT, выход из мира, потеря сцены) - возвращаем строителю
     * его инструменты, если это был тестовый прогон.
     */
    private void onGameFinished(@NonNull UUID playerId, @NonNull TwoDGame game) {
        // Инвентарь строителя возвращает сам забег: он его и забирал, вместе с
        // блоками. Трогать инвентарь ещё раз отсюда нельзя, иначе постройки пропадут.
    }

    /**
     * Выдать строителю инструменты 2D-уровня поверх обычных: палочка пути там нужна
     * не для точек, а для длины уровня.
     */
    public void giveEditorItems(@NonNull Player player) {
        try {
            player.getInventory().setItem(TwoDItems.LINE_WAND_SLOT, TwoDItems.createLineWand());
        } catch (Throwable ignored) {
        }
    }

    // ==================== ТЕСТОВЫЙ ЗАБЕГ В РЕДАКТОРЕ ====================

    /**
     * Диамант в редакторе на 2D-уровне запускает и останавливает свой, 2D-тест.
     */
    public void toggleEditorTest(@NonNull EditActivity activity) {
        Player player = activity.getPlayer();
        if (this.isPlaying(player)) {
            this.stopGame(player, true);
            return;
        }

        // Никаких сообщений на входе в тест: игрок и так видит, что уровень поехал.
        if (!this.startGame(player, activity.getLevel(), true)) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_manager.toggle_editor_test.1")));
        }
    }

    // ==================== АВТОСТАРТ С БЛОКА ====================

    /**
     * Игрок наступил на блок, с которого начинается линия - это и есть старт.
     *
     * @return true, если ход обработан 2D-режимом и обычную логику запускать не надо
     */
    public boolean handlePlayMove(@NonNull Player player, @NonNull Level level) {
        if (this.isPlaying(player)) return true;

        Location spawn = TwoDGeometry.resolveCubeSpawn(level);
        Location at = player.getLocation();
        if (at.getWorld() != spawn.getWorld()) return true;

        double dx = at.getX() - spawn.getX();
        double dz = at.getZ() - spawn.getZ();
        double dy = Math.abs(at.getY() - spawn.getY());

        if (dy > TwoDTuning.START_HEIGHT_TOLERANCE) return true;
        if (dx * dx + dz * dz > TwoDTuning.START_RADIUS * TwoDTuning.START_RADIUS) return true;

        this.startGame(player, level, false);
        return true;
    }

    // ==================== ЛИНИЯ В РЕДАКТОРЕ ====================

    private int editorTick = 0;

    /**
     * Подсветка линии кубика строителю. Вызывается из тика редактора.
     */
    public void tickEditorLine(@NonNull Player player, @NonNull Level level) {
        this.editorTick++;
        if (this.editorTick % 40 == 0) TwoDCoins.keepAlive(level);
        if (this.editorTick % Math.max(1, TwoDTuning.LINE_PERIOD_TICKS) != 0) return;
        if (this.isPlaying(player)) return;

        World world = level.getWorld();
        if (player.getWorld() != world) return;

        Location spawn = TwoDGeometry.resolveCubeSpawn(level);
        Vector forward = TwoDGeometry.forwardVector(
            level.getLevelSettings().getDirectionChecker().direction());
        Vector side = TwoDGeometry.sideVector(forward);

        double dx = player.getLocation().getX() - spawn.getX();
        double dz = player.getLocation().getZ() - spawn.getZ();
        double projected = dx * forward.getX() + dz * forward.getZ();

        double length;
        try {
            length = level.getLevelSettings().getGameSettings().getTwoDSettings().getLineLength();
        } catch (Throwable t) {
            length = TwoDLevelSettings.DEFAULT_LINE_LENGTH;
        }

        // ЛИНИЯ ИДЁТ ТАМ, ГДЕ ЕДЕТ КАМЕРА, А НЕ ТАМ, ГДЕ КУБИК.
        // Строителю нужно видеть именно ту полосу, вдоль которой в игре поедет
        // арморстенд с игроком: у самого кубика линия сливалась с уровнем.
        double sideOffset = TwoDTuning.CAMERA_DISTANCE + TwoDTuning.LINE_SIDE_OFFSET;

        TwoDLine.render(player, world, spawn.toVector(), forward, side,
            length, projected, spawn.getY(), sideOffset);
        TwoDLine.renderFallPreview(player, world, spawn.toVector(), forward, side,
            length, projected, spawn.getY(), sideOffset);

        // Флаги полёта дымят и в редакторе: строитель должен видеть их так же,
        // как увидит игрок.
        double from = Math.max(0.0D, projected - TwoDTuning.LINE_BEHIND);
        TwoDBannerFx.render(player, world, spawn.toVector(), forward, side,
            from, from + TwoDTuning.LINE_VIEW_DISTANCE, spawn.getY());

        this.renderSpikes(player, level);
    }

    // ==================== ПАЛОЧКА ЧАСТИЦ: ДЛИНА УРОВНЯ ====================

    /**
     * На 2D-уровне палочка частиц не расставляет точки пути, а тянет линию - то есть
     * двигает финиш. Линию можно вести дальше построенного паркура: уровень просто
     * ещё не доделан, и это нормальное рабочее состояние.
     *
     * @return true, если действие обработано и обычную логику палочки запускать не надо
     */
    public boolean handleWand(@NonNull PlayerInteractEvent event, @NonNull EditActivity activity) {
        Player player = event.getPlayer();
        Level level = activity.getLevel();
        if (!isTwoD(level)) return false;

        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) return true;

        TwoDLevelSettings settings = level.getLevelSettings().getGameSettings().getTwoDSettings();
        double current = settings.getLineLength();
        double updated;

        if (player.isSneaking() && right) {
            // Точная установка: длина ровно до блока, по которому кликнули.
            Location target = event.getInteractionPoint();
            if (target == null && event.getClickedBlock() != null) {
                target = event.getClickedBlock().getLocation().add(0.5D, 0.5D, 0.5D);
            }
            if (target == null) {
                updated = current;
            } else {
                Location spawn = TwoDGeometry.resolveCubeSpawn(level);
                Vector forward = TwoDGeometry.forwardVector(
                    level.getLevelSettings().getDirectionChecker().direction());
                double dx = target.getX() - spawn.getX();
                double dz = target.getZ() - spawn.getZ();
                updated = dx * forward.getX() + dz * forward.getZ();
            }
        } else if (left) {
            updated = current + (player.isSneaking() ? 16.0D : 4.0D);
        } else {
            updated = current - (player.isSneaking() ? 16.0D : 4.0D);
        }

        settings.setLineLength(updated);
        ru.sortix.parkourbeat.utils.text.ActionBarPriority.notice(player,
            PbText.of(String.format(java.util.Locale.ROOT,
                Lang.raw(PlayerLang.of(player), "auto.two_d_manager.handle_wand.1"), settings.getLineLength())));
        return true;
    }

    // ==================== МОНЕТКИ ====================

    private boolean handleCoinItem(@NonNull PlayerInteractEvent event, @NonNull EditActivity activity) {
        Player player = event.getPlayer();
        Level level = activity.getLevel();

        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) return false;

        TwoDLevelSettings settings = level.getLevelSettings().getGameSettings().getTwoDSettings();

        Location target = event.getInteractionPoint();
        if (target == null && event.getClickedBlock() != null) {
            target = event.getClickedBlock().getLocation().add(0.5D, 1.0D, 0.5D);
        }
        if (target == null) {
            target = player.getLocation();
        }

        if (right) {
            if (settings.addCoin(target)) {
                TwoDCoins.refresh(this.plugin, level);
                ru.sortix.parkourbeat.utils.text.ActionBarPriority.notice(player,
                    PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_manager.handle_coin_item.1")
                        + settings.getCoinsAmount() + ")"));
                player.playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.6f);
            } else {
                ru.sortix.parkourbeat.utils.text.ActionBarPriority.notice(player,
                    PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_manager.handle_coin_item.2")));
            }
        } else {
            if (settings.removeCoinNear(target, 2.0D)) {
                TwoDCoins.refresh(this.plugin, level);
                ru.sortix.parkourbeat.utils.text.ActionBarPriority.notice(player,
                    PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_manager.handle_coin_item.3")
                        + settings.getCoinsAmount() + ")"));
            } else {
                ru.sortix.parkourbeat.utils.text.ActionBarPriority.notice(player,
                    PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_manager.handle_coin_item.4")));
            }
        }
        return true;
    }

    /**
     * ПОДСВЕТКА ШИПОВ ДЛЯ СТРОИТЕЛЯ.
     * <p>
     * Чёрная дымка по верхней грани блока: шип должен быть заметен, но не должен
     * притворяться частью оформления уровня. Видит её только строитель.
     */
    private void renderSpikes(@NonNull Player player, @NonNull Level level) {
        java.util.Set<Vector> spikes;
        try {
            spikes = level.getLevelSettings().getGameSettings().getTwoDSettings().getSpikes();
        } catch (Throwable t) {
            return;
        }
        if (spikes.isEmpty()) return;

        Particle.DustOptions dust = new Particle.DustOptions(org.bukkit.Color.fromRGB(20, 20, 20), 1.2f);
        double viewSquared = TwoDTuning.LINE_VIEW_DISTANCE * TwoDTuning.LINE_VIEW_DISTANCE;
        Location at = player.getLocation();

        for (Vector spike : spikes) {
            double dx = spike.getX() + 0.5D - at.getX();
            double dz = spike.getZ() + 0.5D - at.getZ();
            if (dx * dx + dz * dz > viewSquared) continue;

            try {
                for (int i = 0; i < 4; i++) {
                    double ox = (i == 0 || i == 3) ? 0.15D : 0.85D;
                    double oz = (i < 2) ? 0.15D : 0.85D;

                    player.spawnParticle(Particle.REDSTONE,
                        new Location(level.getWorld(),
                            spike.getBlockX() + ox,
                            spike.getBlockY() + 1.05D,
                            spike.getBlockZ() + oz),
                        1, 0.0D, 0.0D, 0.0D, 0.0D, dust);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // ==================== СОБЫТИЯ ====================

    /**
     * Пометка блока шипом.
     * <p>
     * Точек тут нет намеренно: шип - это весь блок. В оригинале он убивает касанием
     * с любой стороны, и половинчатая разметка внутри блока только запутала бы.
     */
    private void handleSpikeWand(@NonNull PlayerInteractEvent event, @NonNull EditActivity activity) {
        Player player = event.getPlayer();
        org.bukkit.block.Block block = event.getClickedBlock();
        if (block == null) {
            ru.sortix.parkourbeat.utils.text.ActionBarPriority.notice(player,
                PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_manager.handle_spike_wand.1")));
            return;
        }

        TwoDLevelSettings settings = activity.getLevel().getLevelSettings()
            .getGameSettings().getTwoDSettings();

        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_BLOCK;

        if (left) {
            if (settings.addSpike(block.getLocation())) {
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_LAND, 0.4f, 1.8f);
                ru.sortix.parkourbeat.utils.text.ActionBarPriority.notice(player,
                    PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_manager.handle_spike_wand.2") + settings.getSpikesAmount() + ")"));
            } else {
                ru.sortix.parkourbeat.utils.text.ActionBarPriority.notice(player,
                    PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_manager.handle_spike_wand.3")));
            }
            return;
        }

        if (settings.removeSpike(block.getLocation())) {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.2f);
            ru.sortix.parkourbeat.utils.text.ActionBarPriority.notice(player,
                PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_manager.handle_spike_wand.4") + settings.getSpikesAmount() + ")"));
        } else {
            ru.sortix.parkourbeat.utils.text.ActionBarPriority.notice(player,
                PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_manager.handle_spike_wand.5")));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void on(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (this.isPlaying(player)) {
            Action action = event.getAction();
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                this.input.onClick(player);
            }
            event.setCancelled(true);
            return;
        }
        boolean coin = TwoDCoins.isBuilderItem(event.getItem());
        boolean wand = !coin && TwoDItems.isLineWand(event.getItem());
        boolean spike = !coin && !wand && TwoDItems.isSpikeWand(event.getItem());
        if (!coin && !wand && !spike) return;

        UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(player);
        if (!(activity instanceof EditActivity editActivity)) return;
        if (!isTwoD(editActivity.getLevel())) return;

        event.setCancelled(true);
        if (coin) {
            this.handleCoinItem(event, editActivity);
        } else if (spike) {
            this.handleSpikeWand(event, editActivity);
        } else {
            this.handleWand(event, editActivity);
        }
    }

    /**
     * ЛКМ это второй прыжок.
     * <p>
     * В оригинале прыгать можно и кликом, и пробелом, поэтому взмах рукой во время
     * забега ведёт ровно туда же, куда и пробел.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    private void on(org.bukkit.event.player.PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        if (!this.isPlaying(player)) return;
        this.input.onClick(player);
    }

    /**
     * SHIFT заканчивает забег в любом режиме - и в игре, и в тесте у строителя.
     * В режиме наблюдателя клиент по SHIFT сам отцепляется от камеры, но событие
     * приходит раньше, и лучше закончить забег ровно в этот момент.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    private void on(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (this.getGame(player) == null) return;
        this.stopGame(player, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void on(PlayerQuitEvent event) {
        this.stopGame(event.getPlayer(), false);
        this.input.untrack(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private void on(EntityDamageEvent event) {
        try {
            if (event.getEntity().getScoreboardTags().contains(ENTITY_TAG)) {
                event.setCancelled(true);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void disable() {
        try {
            this.tickTask.cancel();
        } catch (Throwable ignored) {
        }
        for (UUID playerId : new java.util.ArrayList<>(this.games.keySet())) {
            TwoDGame game = this.games.remove(playerId);
            if (game == null) continue;
            try {
                game.stop(true);
            } catch (Throwable ignored) {
            }
        }
        this.input.disable();
        this.visibility.disable();
        TwoDCoins.despawnAll();
        HandlerList.unregisterAll(this);
        this.removeStrayEntities();
    }
}
