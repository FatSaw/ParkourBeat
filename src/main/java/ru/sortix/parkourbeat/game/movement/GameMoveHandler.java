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
import ru.sortix.parkourbeat.levels.settings.WorldSettings;
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
    private final @NonNull Location startWaypoint;
    private final @NonNull Location finishWaypoint;
    private final @NonNull Vector startToFinishVector;

    @Getter
    private final @NonNull MovementAccuracyChecker accuracyChecker;

    private BukkitTask task;

    public GameMoveHandler(@NonNull Game game) {
        this.game = game;

        LevelSettings settings = game.getLevel().getLevelSettings();
        WorldSettings worldSettings = settings.getWorldSettings();
        this.accuracyChecker = new MovementAccuracyChecker(
            worldSettings.getWaypoints(), settings.getDirectionChecker());

        this.startWaypoint = settings.getStartWaypointLoc();
        this.finishWaypoint = settings.getFinishWaypointLoc();
        this.startToFinishVector = this.finishWaypoint.toVector().subtract(this.startWaypoint.toVector());
    }

    public void onPreparingState(@NonNull PlayerMoveEvent event) {
        event.setCancelled(true);
    }

    public void onReadyState(@NonNull Player player) {
        LevelSettings settings = this.game.getLevel().getLevelSettings();
        if (settings.getDirectionChecker().isCorrectDirection(this.startWaypoint, player.getLocation())) {
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

    public void onRunningState(@NonNull Player player, @NonNull Location from, @NonNull Location to) {
        LevelSettings settings = this.game.getLevel().getLevelSettings();
        if (settings.getDirectionChecker().isCorrectDirection(this.finishWaypoint, player.getLocation())) {
            boolean isShortTestLevel = this.game.isDisplayTimecode() && this.game.getLevel().getLevelSettings().getWorldSettings().getWaypoints().size() < 4;

            if (!this.game.isAllowEndlessRun() && !isShortTestLevel) {
                this.game.completeLevel();
                return;
            }
        }
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
        if (!event.isSprinting()) {
            // Отпустил Ctrl — комбо обнуляется. Промах при этом не засчитывается:
            // максимальное комбо и точность остаются нетронутыми.
            this.game.getRunTracker().resetCombo();

            if (!this.game.hasModifier(Modifier.PRACTICE) && !Game.isInWater(player)) {
                this.startDamageTask(player,
                    LangOptions.level_play_title_notsprinting.getComponent(player), null,
                    LangOptions.level_play_title_death.getComponent(player), null
                );
            }
        } else {
            if (this.task != null) {
                this.task.cancel();
            }
        }
    }

    private double getLeftOrRightRotationAngle(@NonNull Player player) {
        Vector playerVector = player.getLocation().getDirection();
        return Math.toDegrees(playerVector.angle(this.startToFinishVector));
    }

    @SuppressWarnings("SameParameterValue")
    private void startDamageTask(@NonNull Player player,
                                 @Nullable Component warnReasonFirstLine, @Nullable Component warnReasonSecondLine,
                                 @Nullable Component failReasonFirstLine, @Nullable Component failReasonSecondLine
    ) {
        if (Game.isInWater(player)) return;

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
                int damage = NOT_SPRINT_DAMAGE_PER_PERIOD;
                if (game.hasModifier(ru.sortix.parkourbeat.rating.Modifier.HARD)) {
                    damage *= 2;
                }
                if (player.getHealth() <= damage) {
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
        }.runTaskTimer(this.game.getPlugin(), 0, 2);
    }
}
