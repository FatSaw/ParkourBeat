package ru.sortix.parkourbeat.game.movement;

import lombok.Getter;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.levels.settings.LevelSettings;
import ru.sortix.parkourbeat.rating.Modifier;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import javax.annotation.Nullable;
import java.time.Duration;

public class GameMoveHandler {
    private static final boolean DISPLAY_DEBUG_FAIL_REASONS = false;
    public static double MAX_LOOK_ANGLE = 100.0D;
    public static double BACKWARD_TOLERANCE = 0.0D;

    public static void setMaxLookAngleAndSave(@NonNull ru.sortix.parkourbeat.ParkourBeat plugin, double degrees) {
        MAX_LOOK_ANGLE = degrees;
        plugin.getConfig().set("max_look_angle", degrees);
        plugin.saveConfig();
    }

    public static void setBackwardToleranceAndSave(@NonNull ru.sortix.parkourbeat.ParkourBeat plugin, double blocks) {
        BACKWARD_TOLERANCE = blocks;
        plugin.getConfig().set("backward_tolerance", blocks);
        plugin.saveConfig();
    }

    private static final Title.Times DAMAGE_REASON_TITLE_TIMES
        = Title.Times.of(Duration.ZERO, Duration.ofMillis(250), Duration.ofMillis(250));

    private static final int NOT_SPRINT_DAMAGE_PER_PERIOD = 1;
    private static final int NOT_SPRINT_DAMAGE_PERIOD_TICKS = 1;

    private final @NonNull Game game;
    // Не final: на дуэльной карте концы трассы зависят от стороны игрока, а сторона
    // становится известна уже после создания забега.
    private @NonNull Location startWaypoint;
    private @NonNull Location finishWaypoint;
    private @NonNull Vector startToFinishVector;

    @Getter
    private @NonNull MovementAccuracyChecker accuracyChecker;

    private BukkitTask task;

    public GameMoveHandler(@NonNull Game game) {
        this.game = game;

        LevelSettings settings = game.getLevel().getLevelSettings();
        // Точность меряется по пути ИГРОКА: на дуэльной карте у каждой стороны своя
        // трасса, и судить второго игрока по чужой линии значило бы обнулять ему
        // точность за то, что он бежит там, где ему и положено.
        this.accuracyChecker = new MovementAccuracyChecker(
            ru.sortix.parkourbeat.duel.DuelManager.waypointsFor(
                game.getPlugin(), game.getLevel(), game.getPlayer()),
            settings.getDirectionChecker(),
            settings.getGameSettings().getDifficultyMultiplier());

        this.startWaypoint = settings.getStartWaypointLoc();
        this.finishWaypoint = settings.getFinishWaypointLoc();
        this.startToFinishVector = this.finishWaypoint.toVector().subtract(this.startWaypoint.toVector());
    }

    /**
     * Пересчитать старт и финиш под сторону игрока.
     * <p>
     * На дуэльной карте у каждого свой старт и свой финиш - концы его собственной
     * трассы. На обычном уровне значения те же, что и были.
     */
    private void rebuildStartAndFinish() {
        this.startWaypoint = ru.sortix.parkourbeat.duel.DuelManager.startFor(
            this.game.getPlugin(), this.game.getLevel(), this.game.getPlayer());
        this.finishWaypoint = ru.sortix.parkourbeat.duel.DuelManager.finishFor(
            this.game.getPlugin(), this.game.getLevel(), this.game.getPlayer());
        this.startToFinishVector =
            this.finishWaypoint.toVector().subtract(this.startWaypoint.toVector());
    }

    /**
     * Пересобрать проверку точности под путь игрока.
     * <p>
     * Забег создаётся раньше, чем игрок получает сторону дуэли (сначала грузится мир и
     * собирается {@link Game}, и только потом стартует активность), поэтому на дуэльной
     * карте проверку нужно пересобрать, когда сторона уже известна. На обычном уровне
     * вызов ничего не меняет: путь там один.
     */
    public void rebuildAccuracyChecker() {
        LevelSettings settings = this.game.getLevel().getLevelSettings();
        this.accuracyChecker = new MovementAccuracyChecker(
            ru.sortix.parkourbeat.duel.DuelManager.waypointsFor(
                this.game.getPlugin(), this.game.getLevel(), this.game.getPlayer()),
            settings.getDirectionChecker(),
            settings.getGameSettings().getDifficultyMultiplier());

        this.rebuildStartAndFinish();
    }

    public void onPreparingState(@NonNull PlayerMoveEvent event) {
        event.setCancelled(true);
    }

    public void onReadyState(@NonNull Player player) {
        LevelSettings settings = this.game.getLevel().getLevelSettings();

        // НА 360-УРОВНЕ ЗАБЕГ НАЧИНАЕТСЯ С ПЕРВОГО ЖЕ ДВИЖЕНИЯ.
        //
        // Стартовой черты там нет: игрок появляется на платформе и бежит куда хочет.
        // Отсюда же и музыка - она включается ровно вместе с забегом, то есть как
        // только игрок сдвинулся, и глохнет при падении, потому что забег начинается
        // заново и снова ждёт первого движения.
        boolean started = settings.getGameSettings().isThreeSixtyLevel()
            || settings.getDirectionChecker().isCorrectDirection(this.startWaypoint, player.getLocation());

        if (started) {
            this.game.start();
            if ((this.task == null || this.task.isCancelled()) && !player.isSprinting()) {
                if (!this.game.hasModifier(Modifier.PRACTICE) && !Game.isInWater(player)) {
                    this.startDamageTask(player,
                        LangOptions.level_play_title_notsprinting.getComponent(player), null,
                        LangOptions.level_play_title_death.getComponent(player), null
                    );
                }
            }
        }
    }

    /**
     * ФИНИШ 360-УРОВНЯ - ЭТО БЛОК, А НЕ ШАР ВОКРУГ НЕГО.
     * <p>
     * Раньше стояла сфера радиусом два блока, и она срабатывала откуда угодно - в том
     * числе снизу, когда игрок пробегал под финишем. Теперь по горизонтали нужно быть
     * над самим блоком (полблока плюс ширина игрока), а по высоте - стоять на нём или
     * внутри него. Снизу финиш не засчитывается вовсе.
     */
    private static final double THREE_SIXTY_FINISH_HORIZONTAL = 0.9D;

    /** Насколько ниже блока финиша ещё считается «на нём» - только погрешность шага. */
    private static final double THREE_SIXTY_FINISH_BELOW = 0.1D;

    /** Насколько выше блока финиша ещё считается «на нём»: стоя сверху и в прыжке с него. */
    private static final double THREE_SIXTY_FINISH_ABOVE = 2.0D;

    public void onRunningState(@NonNull Player player, @NonNull Location from, @NonNull Location to) {
        LevelSettings settings = this.game.getLevel().getLevelSettings();

        if (settings.getGameSettings().isThreeSixtyLevel()) {
            if (this.isThreeSixtyFinishReached(player)) {
                this.game.completeLevel();
                return;
            }
            // Ни угол взгляда, ни «движение назад» на 360-уровне не судятся: там бегают
            // по стенам и потолку, разворачиваются на месте и возвращаются по своей же
            // трассе - это нормальная игра, а не ошибка.
            if (!this.game.isRespawnGrace()) this.accuracyChecker.onPlayerLocationChange(to);
            return;
        }

        if (settings.getDirectionChecker().isCorrectDirection(this.finishWaypoint, player.getLocation())) {
            boolean isShortTestLevel = this.game.isDisplayTimecode() && this.game.getLevel().getLevelSettings().getWorldSettings().getWaypoints().size() < 4;

            if (!this.game.isAllowEndlessRun() && !isShortTestLevel) {
                this.game.completeLevel();
                return;
            }
        }
        // Игрока откатывает на чекпоинт: телепорт назад — это по определению движение
        // против направления уровня, судить его нельзя, иначе откат зациклится.
        if (this.game.isRespawnGrace()) return;

        double angle = getLeftOrRightRotationAngle(player);
        if (angle > MAX_LOOK_ANGLE) {
            if (DISPLAY_DEBUG_FAIL_REASONS) {
                this.game.failLevel(LangOptions.level_play_title_wrongangle.getComponent(player, new Placeholders("%angle%", Double.valueOf(angle).toString())), null);
            } else {
                this.game.failLevel(LangOptions.level_play_title_moveback.getComponent(player), null);
            }
            return;
        }
        double fromCoord = settings.getDirectionChecker().getCoordinate(from);
        double toCoord = settings.getDirectionChecker().getCoordinate(to);

        double backwardAmount = settings.getDirectionChecker().isNegative()
            ? toCoord - fromCoord
            : fromCoord - toCoord;

        double backwardTolerance = player.isOnGround() ? BACKWARD_TOLERANCE : BACKWARD_TOLERANCE + 0.75D;

        if (backwardAmount > backwardTolerance) {
            if (DISPLAY_DEBUG_FAIL_REASONS) {
                this.game.failLevel(LangOptions.level_play_title_wrongdirection.getComponent(player, new Placeholders("%direction%", fromCoord + " -> " + toCoord)), null);
            } else {
                this.game.failLevel(LangOptions.level_play_title_moveback.getComponent(player), null);
            }
            return;
        }
        this.accuracyChecker.onPlayerLocationChange(to);
    }

    public void onRunningState(@NonNull PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        // На паузе после отката игрок стоит: отпущенный Ctrl тут не ошибка. Проверка
        // бега повторится в момент, когда забег тронется, - см. onCheckpointResume.
        if (this.game.isCheckpointPaused()) return;
        if (!event.isSprinting()) {
            // Отпустил Ctrl — комбо обнуляется. Промах при этом не засчитывается:
            // максимальное комбо и точность остаются нетронутыми.
            //
            // В ВОДЕ КОМБО НЕ СБРАСЫВАЕТСЯ. Клиент сам снимает спринт при входе в воду
            // и включает плавание, игрок Ctrl не отпускал. Урон за это мы уже не давали,
            // а комбо всё равно обнулялось — это и был баг.
            // Комбо тоже не рушим, если уровень вообще не наказывает за отпущенный бег:
            // иначе наказание просто сменило бы форму - с урона на потерю комбо.
            if (!Game.isInWater(player) && this.isSprintDamageEnabled()) {
                this.game.getRunTracker().resetCombo();
            }

            if (!this.game.hasModifier(Modifier.PRACTICE) && !Game.isInWater(player)) {
                this.startDamageTask(player,
                    LangOptions.level_play_title_notsprinting.getComponent(player), null,
                    LangOptions.level_play_title_death.getComponent(player), null
                );
            }
        } else {
            // Побежал снова — наказание снимается. Поле обязательно обнуляем: иначе
            // отменённая задача остаётся висеть, и следующий startDamageTask
            // запустит вторую параллельно с ней.
            this.stopDamageTask();
        }
    }

    /**
     * Забег тронулся после паузы на чекпоинте. Если игрок так и не зажал Ctrl, он
     * получает то же, что на обычном старте: урон пойдёт, как только кончится грейс.
     */
    public void onCheckpointResume(@NonNull Player player) {
        if (player.isSprinting()) return;
        if (this.task != null && !this.task.isCancelled()) return;
        if (this.game.hasModifier(Modifier.PRACTICE) || Game.isInWater(player)) return;
        this.startDamageTask(player,
            LangOptions.level_play_title_notsprinting.getComponent(player), null,
            LangOptions.level_play_title_death.getComponent(player), null
        );
    }

    private double getLeftOrRightRotationAngle(@NonNull Player player) {
        Vector playerVector = player.getLocation().getDirection();
        return Math.toDegrees(playerVector.angle(this.startToFinishVector));
    }

    @SuppressWarnings("SameParameterValue")
    private long teleportGraceUntil = 0L;

    /**
     * Телепорт сбрасывает состояние спринта на клиенте, и сервер получает "перестал бежать"
     * уже на выходе из портала. Игрок при этом Ctrl не отпускал. Поэтому сразу после
     * телепорта наказание за отпущенный бег временно не включается.
     */
    public void applyTeleportGrace(long millis) {
        this.teleportGraceUntil = System.currentTimeMillis() + Math.max(0L, millis);
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    /**
     * Снять наказание за отпущенный бег. Вызывается и извне, и самой задачей, когда та
     * поняла, что игрок на самом деле бежит.
     */
    public void stopDamageTask() {
        if (this.task == null) return;
        try {
            this.task.cancel();
        } catch (Exception ignored) {
        }
        this.task = null;
    }

    /**
     * Наказывает ли этот уровень за отпущенный Ctrl.
     */
    public boolean isSprintDamageEnabled() {
        try {
            return this.game.getLevel().getLevelSettings().getGameSettings().isSprintDamage();
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Дошёл ли игрок до блока финиша 360-уровня.
     * <p>
     * Финиша может не быть вовсе - строитель ещё не поставил его палочкой. Тогда
     * забег просто не заканчивается, и это честнее, чем финишировать где попало.
     */
    public boolean isThreeSixtyFinishReached(@NonNull Player player) {
        java.util.List<ru.sortix.parkourbeat.levels.Waypoint> waypoints =
            this.game.getLevel().getLevelSettings().getWorldSettings().getWaypoints();
        if (waypoints.size() < 2) return false;

        Location finish = waypoints.get(waypoints.size() - 1).getLocation();
        Location at = player.getLocation();
        if (finish.getWorld() != null && at.getWorld() != null
            && !finish.getWorld().equals(at.getWorld())) return false;

        double dx = at.getX() - finish.getX();
        double dz = at.getZ() - finish.getZ();
        if ((dx * dx) + (dz * dz) > THREE_SIXTY_FINISH_HORIZONTAL * THREE_SIXTY_FINISH_HORIZONTAL) {
            return false;
        }

        // Финиш хранится в центре блока, ноги игрока - на его верхней грани, то есть
        // на полблока выше центра. Отсюда и границы: чуть-чуть вниз на погрешность
        // шага и два блока вверх.
        double dy = at.getY() - (finish.getY() - 0.5D);
        return dy >= -THREE_SIXTY_FINISH_BELOW && dy <= THREE_SIXTY_FINISH_ABOVE;
    }

    private boolean isTeleportGrace() {
        return System.currentTimeMillis() < this.teleportGraceUntil;
    }

    /**
     * Период тика урона за отпущенный бег. На поднятой сложности уровня бьёт чаще:
     * при множителе 9 — каждый тик вместо каждого второго.
     */
    private long getDamageTaskPeriodTicks() {
        double period = 2.0D / this.game.getDamageRateScale();
        return (long) Math.max(1L, Math.round(period));
    }

    private void startDamageTask(@NonNull Player player,
                                 @Nullable Component warnReasonFirstLine, @Nullable Component warnReasonSecondLine,
                                 @Nullable Component failReasonFirstLine, @Nullable Component failReasonSecondLine
    ) {
        // Урон за отпущенный бег выключен настройкой уровня (по умолчанию так на
        // 360-уровнях: там клиент сам снимает спринт на каждом развороте).
        if (!this.isSprintDamageEnabled()) return;
        if (Game.isInWater(player)) return;
        if (this.isTeleportGrace()) return;

        // Две задачи одновременно — это два титла и двойной урон. Старую всегда гасим.
        this.stopDamageTask();

        player.playEffect(EntityEffect.HURT);
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_HURT, 1, 1);

        this.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || game.getCurrentState() != Game.State.RUNNING || Game.isInWater(player)) {
                    this.cancel();
                    return;
                }

                boolean levelFailed;
                double damage = NOT_SPRINT_DAMAGE_PER_PERIOD;
                if (game.hasModifier(ru.sortix.parkourbeat.rating.Modifier.SUDDEN_DEATH)) {
                    damage *= 2;
                }
                // Урон, который будет реально нанесён с учётом сложности уровня
                // (applyDamage сам домножает на getDamageScale, здесь считаем то же самое,
                // чтобы корректно понять, хватит ли игроку здоровья).
                double effectiveDamage = damage * game.getDamageScale();
                if (player.getHealth() <= effectiveDamage) {
                    levelFailed = true;
                    game.failLevel(failReasonFirstLine, failReasonSecondLine);
                } else {
                    levelFailed = false;
                    player.showTitle(Title.title(
                        warnReasonFirstLine == null ? Component.empty() : warnReasonFirstLine,
                        warnReasonSecondLine == null ? Component.empty() : warnReasonSecondLine,
                        DAMAGE_REASON_TITLE_TIMES
                    ));
                    if (player.getNoDamageTicks() <= 0) {
                        game.applyDamage(damage);
                        player.setNoDamageTicks(NOT_SPRINT_DAMAGE_PERIOD_TICKS);
                    }
                }

                if (levelFailed) {
                    this.cancel();
                }
            }
        }.runTaskTimer(this.game.getPlugin(), 0, this.getDamageTaskPeriodTicks());
    }
}
