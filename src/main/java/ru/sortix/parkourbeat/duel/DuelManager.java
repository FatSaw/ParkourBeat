package ru.sortix.parkourbeat.duel;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.editor.type.EditTrackPointsItem;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.ParticleController;
import ru.sortix.parkourbeat.levels.Waypoint;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.stats.PlayerProfile;
import ru.sortix.parkourbeat.stats.RunResult;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ВСЯ ЖИЗНЬ ДУЭЛЬНОГО РЕЖИМА В ОДНОМ МЕНЕДЖЕРЕ.
 * <p>
 * Дуэльный уровень - это обычный уровень шириной в четыре чанка, у которого ДВА пути
 * из частиц: синий для первого игрока и красный для второго. У каждой трассы свой
 * старт и свой финиш - её первая и последняя точки. Готовых точек на новой карте нет
 * вовсе: обе линии строитель ведёт с нуля, куда считает нужным.
 * <p>
 * Игроки попадают на карту через {@code /duel}: первый ждёт соперника, второй
 * запускает забег для обоих.
 * <p>
 * Игроку показывается и судится ТОЛЬКО его собственный путь: прыжковые кольца чужой
 * стороны для него не существуют, поэтому «прыгать на чужой стороне» физически нечем -
 * там нет ни одного его триггера.
 */
public class DuelManager implements PluginManager, Listener {
    /**
     * Сколько уровней нужно пройти, чтобы получить право создавать дуэльные карты.
     */
    public static final int REQUIRED_COMPLETED_LEVELS = 7;

    /** Дуэльная карта всегда шириной в четыре чанка: двум полосам нужно место. */
    public static final int DUEL_CHUNK_WIDTH = 4;

    private final @NonNull ParkourBeat plugin;

    /**
     * Кто за какую сторону сейчас играет. Ключ - игрок, а не уровень: одновременно
     * игрок бежит ровно один забег.
     */
    private final Map<UUID, DuelSide> sides = new ConcurrentHashMap<>();

    /**
     * Очередь ожидания: уровень - игрок, который ждёт соперника.
     * <p>
     * Ждущий ровно один на уровень: как только приходит второй, обоих отправляют в
     * забег, и очередь снова пуста. Копить очередь длиннее незачем - дуэль на двоих.
     */
    private final Map<UUID, UUID> waiting = new ConcurrentHashMap<>();

    /**
     * Игроки, замороженные на старте до конца отсчёта.
     * <p>
     * Заморозка нужна не для красоты: дуэль - это гонка, и любая фора в полсекунды
     * решает исход. Пока идёт отсчёт, двигаться не может никто.
     */
    private final java.util.Set<UUID> frozen = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public DuelManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ==================== ПРОВЕРКИ РЕЖИМА ====================

    public static boolean isDuel(@Nullable Level level) {
        if (level == null) return false;
        try {
            return level.getLevelSettings().getGameSettings().getLevelMode().isDuel();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Сколько уровней игрок прошёл до конца. Считаются рекорды: один уровень - один раз,
     * сколько бы забегов по нему ни было.
     */
    public static int getCompletedLevels(@NonNull ParkourBeat plugin, @NonNull Player player) {
        try {
            PlayerProfile profile = plugin.get(StatisticsManager.class).getProfile(player);
            int completed = 0;
            for (RunResult record : profile.getAllRecords()) {
                if (record.isCompleted()) completed++;
            }
            return completed;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static boolean canCreateDuelLevels(@NonNull ParkourBeat plugin, @NonNull Player player) {
        return getCompletedLevels(plugin, player) >= REQUIRED_COMPLETED_LEVELS;
    }

    // ==================== СТОРОНЫ ====================

    /**
     * Сторона, за которую играет игрок. По умолчанию - первая: на обычном уровне
     * сторона одна, и весь код может спрашивать её не задумываясь.
     */
    @NonNull
    public DuelSide getSide(@NonNull Player player) {
        DuelSide side = this.sides.get(player.getUniqueId());
        return side == null ? DuelSide.FIRST : side;
    }

    public void setSide(@NonNull Player player, @NonNull DuelSide side) {
        this.sides.put(player.getUniqueId(), side);
    }

    /**
     * Игрок уже в дуэли - сторона ему выдана.
     * <p>
     * По этому же признаку отличается заход на карту через очередь от попытки зайти
     * на неё в одиночку из обычного списка уровней.
     */
    public boolean isInDuel(@NonNull Player player) {
        return this.sides.containsKey(player.getUniqueId());
    }

    public void release(@NonNull Player player) {
        this.sides.remove(player.getUniqueId());
        this.frozen.remove(player.getUniqueId());
    }

    /**
     * Посадить игрока на свободную сторону уровня.
     * <p>
     * Уже выбранную сторону не трогаем: её мог явно задать строитель перед тестовым
     * забегом. Если свободных сторон нет (оба места заняты), игрок получает вторую -
     * бежать вдвоём по одной полосе всё равно можно, просто это уже не дуэль.
     */
    @NonNull
    public DuelSide assign(@NonNull Player player, @NonNull Level level) {
        DuelSide existing = this.sides.get(player.getUniqueId());
        if (existing != null) return existing;

        boolean firstTaken = false;
        for (Player other : level.getWorld().getPlayers()) {
            if (other == player) continue;
            DuelSide otherSide = this.sides.get(other.getUniqueId());
            if (otherSide == DuelSide.FIRST) firstTaken = true;
        }

        DuelSide side = firstTaken ? DuelSide.SECOND : DuelSide.FIRST;
        this.sides.put(player.getUniqueId(), side);
        return side;
    }

    // ==================== ПУТИ И ЧАСТИЦЫ ====================

    /**
     * Путь, по которому судится конкретный игрок.
     * <p>
     * На обычном уровне это единственный путь уровня. На дуэльном - путь его стороны;
     * если строитель вторую трассу так и не построил, игрок бежит по первой, иначе
     * уровень был бы для него пустым.
     */
    @NonNull
    public static List<Waypoint> waypointsFor(@NonNull ParkourBeat plugin,
                                              @NonNull Level level,
                                              @NonNull Player player) {
        if (!isDuel(level)) {
            return level.getLevelSettings().getWorldSettings().getWaypoints();
        }
        DuelSide side = plugin.get(DuelManager.class).getSide(player);
        return level.getLevelSettings().getWorldSettings().getWaypointsOrPrimary(side);
    }

    /**
     * Контроллер частиц, который показывает игроку его собственный путь.
     */
    @NonNull
    public static ParticleController particlesFor(@NonNull ParkourBeat plugin,
                                                  @NonNull Level level,
                                                  @NonNull Player player) {
        if (isDuel(level)
            && plugin.get(DuelManager.class).getSide(player) == DuelSide.SECOND
            && !level.getLevelSettings().getWorldSettings().getSecondWaypoints().isEmpty()) {
            ParticleController second = level.getLevelSettings().getSecondParticleController();
            // Контроллер могли ещё ни разу не собрать (никто не заходил в редактор
            // после загрузки уровня). Несобранный он только ругается в консоль.
            if (!second.isLoaded()) level.getLevelSettings().updateSecondParticleLocations();
            return second;
        }
        return level.getLevelSettings().getParticleController();
    }

    /**
     * Показать строителю ОБА пути сразу: он ведёт их вдвоём и должен видеть, где они
     * расходятся, а где идут вплотную.
     */
    public void showBothPaths(@NonNull Level level, @NonNull Player player) {
        if (!isDuel(level)) return;
        level.getLevelSettings().updateSecondParticleLocations();
        level.getLevelSettings().getSecondParticleController().startSpawnParticles(player);
    }

    public void hideBothPaths(@NonNull Level level, @NonNull Player player) {
        if (!isDuel(level)) return;
        level.getLevelSettings().getSecondParticleController().stopSpawnParticlesForPlayer(player);
    }

    /**
     * Старт стороны: первая точка её трассы.
     */
    @NonNull
    public static Location startFor(@NonNull ParkourBeat plugin,
                                    @NonNull Level level,
                                    @NonNull Player player) {
        List<Waypoint> waypoints = waypointsFor(plugin, level, player);
        if (waypoints.isEmpty()) return level.getLevelSettings().getStartWaypointLoc();
        return waypoints.get(0).getLocation();
    }

    /**
     * Финиш стороны: последняя точка её трассы.
     * <p>
     * У каждого игрока дуэли свой финиш - тот, до которого ведёт его собственная
     * трасса. Общий финиш означал бы, что один из двоих обязан сойти со своей полосы.
     */
    @NonNull
    public static Location finishFor(@NonNull ParkourBeat plugin,
                                     @NonNull Level level,
                                     @NonNull Player player) {
        List<Waypoint> waypoints = waypointsFor(plugin, level, player);
        if (waypoints.size() < 2) return level.getLevelSettings().getFinishWaypointLoc();
        return waypoints.get(waypoints.size() - 1).getLocation();
    }

    // ==================== ОЧЕРЕДЬ /duel ====================

    /**
     * Встать в очередь на дуэль по конкретной карте.
     * <p>
     * Если соперник уже ждёт - забег начинается сразу для обоих. Если нет - игрок
     * остаётся ждать и может выйти повторной командой.
     */
    public void joinQueue(@NonNull Player player, @NonNull GameSettings settings) {
        if (!settings.getLevelMode().isDuel()) {
            player.sendMessage(PbText.of("&cЭто не дуэльная карта."));
            return;
        }
        if (!settings.isAccessibleForPlaying(player, true)) {
            player.sendMessage(PbText.of("&cЭта карта вам недоступна."));
            return;
        }

        UUID levelId = settings.getUniqueId();
        UUID opponentId = this.waiting.get(levelId);

        // Повторная команда на той же карте - выход из очереди.
        if (player.getUniqueId().equals(opponentId)) {
            this.leaveQueue(player);
            player.sendMessage(PbText.of("&7Вы вышли из очереди на дуэль."));
            return;
        }

        // На двух картах одновременно не ждут.
        this.leaveQueue(player);

        Player opponent = opponentId == null ? null : this.plugin.getServer().getPlayer(opponentId);
        if (opponent == null || !opponent.isOnline()) {
            this.waiting.put(levelId, player.getUniqueId());
            player.sendMessage(PbText.of("&eОжидание соперника на карте "
                + settings.getDisplayNameLegacy(false) + "&e..."));
            player.sendMessage(PbText.of("&7Повторите команду, чтобы выйти из очереди."));
            return;
        }

        this.waiting.remove(levelId);
        this.startDuel(opponent, player, settings);
    }

    public void leaveQueue(@NonNull Player player) {
        this.waiting.values().remove(player.getUniqueId());
    }

    /**
     * Запуск дуэли: тот, кто ждал, бежит за первую сторону, пришедший - за вторую.
     * <p>
     * Стороны раздаются ЗДЕСЬ, до захода на уровень: по стороне собираются прыжковые
     * кольца, старт, финиш и проверка точности, а всё это считается в момент входа.
     */
    private void startDuel(@NonNull Player first, @NonNull Player second, @NonNull GameSettings settings) {
        this.setSide(first, DuelSide.FIRST);
        this.setSide(second, DuelSide.SECOND);

        // Морозим СРАЗУ, ещё до загрузки уровня: иначе тот, кого закинуло первым,
        // успеет сделать несколько шагов, пока второй грузится.
        this.frozen.add(first.getUniqueId());
        this.frozen.add(second.getUniqueId());

        for (Player player : new Player[]{first, second}) {
            Player rival = player == first ? second : first;
            player.sendMessage(PbText.of("&aДуэль! Соперник: &f" + rival.getName()));
            player.sendMessage(PbText.of("&7Вы бежите за " + this.getSide(player).getColoredName()));
            ru.sortix.parkourbeat.inventory.type.LevelsListMenu
                .startPlaying(this.plugin, player, settings);
        }

        this.awaitBothAndCountdown(first, second, settings.getUniqueId(), 0);
    }

    /**
     * Ждёт, пока ОБА игрока окажутся на уровне, и только тогда запускает отсчёт.
     * <p>
     * Загрузка мира и телепорт занимают разное время у разных игроков, и начинать
     * отсчёт по факту команды нельзя: один услышал бы «ПОЕХАЛИ» ещё в лобби.
     *
     * @param attempt номер попытки; после 200 (10 секунд) ожидание бросается
     */
    private void awaitBothAndCountdown(@NonNull Player first, @NonNull Player second,
                                       @NonNull UUID levelId, int attempt) {
        this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            if (!first.isOnline() || !second.isOnline()) {
                this.cancelDuel(first, second, "&cСоперник вышел. Дуэль отменена.");
                return;
            }

            if (!this.isOnLevel(first, levelId) || !this.isOnLevel(second, levelId)) {
                if (attempt >= 200) {
                    this.cancelDuel(first, second, "&cНе удалось начать дуэль: соперник не зашёл на карту.");
                    return;
                }
                this.awaitBothAndCountdown(first, second, levelId, attempt + 1);
                return;
            }

            this.placeOnOwnStart(first);
            this.placeOnOwnStart(second);
            this.runCountdown(first, second, 3);
        }, 5L);
    }

    private boolean isOnLevel(@NonNull Player player, @NonNull UUID levelId) {
        ru.sortix.parkourbeat.activity.UserActivity activity =
            this.plugin.get(ActivityManager.class).getActivity(player);
        if (!(activity instanceof ru.sortix.parkourbeat.activity.type.PlayActivity)) return false;
        return activity.getLevel() != null && levelId.equals(activity.getLevel().getUniqueId());
    }

    /**
     * Ставит игрока на первую точку ЕГО трассы.
     * <p>
     * Старты у сторон разные, а спавн у уровня один - без этого оба стояли бы на одной
     * точке и бежали бы первые метры друг сквозь друга.
     */
    private void placeOnOwnStart(@NonNull Player player) {
        ru.sortix.parkourbeat.activity.UserActivity activity =
            this.plugin.get(ActivityManager.class).getActivity(player);
        Level level = activity == null ? null : activity.getLevel();
        if (level == null) return;

        List<Waypoint> path = level.getLevelSettings().getWorldSettings()
            .getWaypointsOrPrimary(this.getSide(player));
        if (path.isEmpty()) return;

        Location start = path.get(0).getLocation().clone();
        start.setWorld(level.getWorld());
        // Направление взгляда оставляем то, с которым игрока уже развернуло по трассе.
        start.setYaw(player.getLocation().getYaw());
        start.setPitch(player.getLocation().getPitch());

        ru.sortix.parkourbeat.world.TeleportUtils.teleportAsync(this.plugin, player, start);
    }

    /**
     * 3, 2, 1, ПОЕХАЛИ.
     */
    private void runCountdown(@NonNull Player first, @NonNull Player second, int left) {
        if (!first.isOnline() || !second.isOnline()) {
            this.cancelDuel(first, second, "&cСоперник вышел. Дуэль отменена.");
            return;
        }

        for (Player player : new Player[]{first, second}) {
            if (left > 0) {
                this.showBigTitle(player, "&e&l" + left, "&7Приготовьтесь...");
                player.playSound(player.getLocation(),
                    org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, left == 1 ? 1.5f : 1.0f);
            } else {
                this.showBigTitle(player, "&a&lПОЕХАЛИ!", "&f" + this.getSide(player).getColoredName());
                player.playSound(player.getLocation(),
                    org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
            }
        }

        if (left > 0) {
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin,
                () -> this.runCountdown(first, second, left - 1), 20L);
            return;
        }

        // Размораживаем ОБОИХ одним тиком: любая разница здесь - это фора.
        this.frozen.remove(first.getUniqueId());
        this.frozen.remove(second.getUniqueId());
    }

    private void showBigTitle(@NonNull Player player, @NonNull String title, @NonNull String subtitle) {
        player.showTitle(net.kyori.adventure.title.Title.title(
            PbText.of(title),
            PbText.of(subtitle),
            net.kyori.adventure.title.Title.Times.of(
                java.time.Duration.ZERO,
                java.time.Duration.ofMillis(900),
                java.time.Duration.ofMillis(200))));
    }

    private void cancelDuel(@NonNull Player first, @NonNull Player second, @NonNull String reason) {
        for (Player player : new Player[]{first, second}) {
            this.frozen.remove(player.getUniqueId());
            if (player.isOnline()) player.sendMessage(PbText.of(reason));
        }
    }

    /**
     * Идёт ли у игрока отсчёт перед дуэлью. Пока идёт - он не двигается.
     */
    public boolean isFrozen(@NonNull Player player) {
        return this.frozen.contains(player.getUniqueId());
    }

    // ==================== ИНСТРУМЕНТЫ СТРОИТЕЛЯ ====================
    // ==================== ИНСТРУМЕНТЫ СТРОИТЕЛЯ ====================

    /**
     * Выдаёт две палочки пути вместо одной обычной. Обычную при этом убираем: на
     * дуэльном уровне она вела бы белый путь молча, не показывая, какую сторону трогает.
     */
    public void giveEditorItems(@NonNull Player player) {
        try {
            for (DuelSide side : DuelSide.values()) {
                player.getInventory().setItem(side.getWandSlot(), DuelItems.createWand(side));
            }
        } catch (Throwable ignored) {
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void on(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) return;

        ItemStack item = event.getItem();
        DuelSide side = DuelItems.sideOf(item);
        if (side == null) return;

        Player player = event.getPlayer();
        UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(player);
        if (!(activity instanceof EditActivity editActivity) || editActivity.isTesting()) {
            // Палочка вне редактора - просто предмет, но махать ей уровнем нельзя.
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        Level level = editActivity.getLevel();
        if (!isDuel(level)) {
            player.sendMessage(PbText.of("&cЭти палочки работают только на дуэльных уровнях."));
            return;
        }

        this.handleWand(event, editActivity, side);
    }

    /**
     * Одна палочка - одна сторона. Всё остальное поведение (левый клик ставит точку,
     * правый убирает, SHIFT меняет высоту прыжка) намеренно совпадает с обычной
     * палочкой пути: переучиваться строителю не нужно.
     */
    private void handleWand(@NonNull PlayerInteractEvent event,
                            @NonNull EditActivity activity,
                            @NonNull DuelSide side) {
        Player player = event.getPlayer();
        Level level = activity.getLevel();

        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) return;

        List<Waypoint> waypoints = level.getLevelSettings().getWorldSettings().getWaypoints(side);
        boolean primary = side.isFirst();
        boolean changed = false;

        if (player.isSneaking()) {
            changed = EditTrackPointsItem.adjustHeight(left, waypoints, player, activity);
        } else {
            Location interactionPoint = EditTrackPointsItem.getInteractionPoint(event);
            if (interactionPoint == null) return;

            if (left) {
                Waypoint waypoint = new Waypoint(interactionPoint, activity.getCurrentHeight(),
                    side.getParticlesColor(), activity.getCurrentJumpColor());

                waypoints.add(bestInsertIndex(waypoints, interactionPoint), waypoint);
                changed = true;
            } else {
                changed = EditTrackPointsItem.removePoint(waypoints, interactionPoint, player, level, primary);
            }
        }

        if (!changed) return;

        if (primary) {
            level.getLevelSettings().updateParticleLocations();
        } else {
            level.getLevelSettings().updateSecondParticleLocations();
        }
        this.plugin.get(ru.sortix.parkourbeat.levels.LevelsManager.class)
            .markWorldChanged(level.getUniqueId());
    }

    /**
     * КУДА ВСТАВИТЬ НОВУЮ ТОЧКУ ТРАССЫ.
     * <p>
     * Правило одно: точка встаёт туда, где ломает линию меньше всего. Для каждого
     * отрезка считается удлинение маршрута (насколько станет длиннее путь, если пустить
     * его через новую точку), а для концов - просто расстояние до них. Побеждает
     * наименьшее.
     * <p>
     * Общий подбор из обычной палочки здесь не годился: он сравнивает расстояние до
     * КРАЙНЕЙ точки с удлинением в середине, и на дуэльной карте, где две трассы идут
     * рядом и обе начинаются с нуля, постоянно ошибался - линия складывалась наискосок
     * и переворачивалась. Простая вставка в конец тоже неверна: точку, поставленную
     * перед прыжком в середине трассы, уносило в хвост, и путь возвращался к ней
     * от финиша.
     */
    static int bestInsertIndex(@NonNull List<Waypoint> waypoints, @NonNull Location point) {
        if (waypoints.isEmpty()) return 0;
        if (waypoints.size() == 1) return 1;

        double bestCost = Double.MAX_VALUE;
        int bestIndex = waypoints.size();

        for (int i = 0; i < waypoints.size() - 1; i++) {
            Location a = waypoints.get(i).getLocation();
            Location b = waypoints.get(i + 1).getLocation();
            double cost = distance(a, point) + distance(point, b) - distance(a, b);
            if (cost < bestCost) {
                bestCost = cost;
                bestIndex = i + 1;
            }
        }

        // Продлить трассу с конца.
        double tail = distance(waypoints.get(waypoints.size() - 1).getLocation(), point);
        if (tail < bestCost) {
            bestCost = tail;
            bestIndex = waypoints.size();
        }

        // ...или с начала: у дуэльной трассы старт - её же первая точка, и отодвинуть
        // его назад - обычное дело.
        double head = distance(waypoints.get(0).getLocation(), point);
        if (head < bestCost) {
            bestIndex = 0;
        }

        return bestIndex;
    }

    private static double distance(@NonNull Location a, @NonNull Location b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @EventHandler
    private void on(PlayerQuitEvent event) {
        this.release(event.getPlayer());
        this.leaveQueue(event.getPlayer());
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        this.sides.clear();
        this.waiting.clear();
        this.frozen.clear();
    }
}
