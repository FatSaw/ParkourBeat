package ru.sortix.parkourbeat.ui;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Переход при заходе на уровень.
 * <p>
 * В титл кладётся один огромный символ из РП (512x256 пикселей шрифта, в титле это
 * ещё x4), который перекрывает весь экран. Кадры меняются каждый тик:
 * <pre>
 *   закрытие (кубики волной) -> экран закрыт, в это время телепорт -> открытие
 * </pre>
 * Поверх, в сабтайтле, - куб сложности, её плашка и название уровня. Сабтайтл
 * рисуется после титла, поэтому он виден над закрытым экраном.
 * <p>
 * У каждой сложности свой рисунок волны, у N/A - серое рассыпание.
 */
public final class LevelTransition {
    private LevelTransition() {
    }

    /** Минимум, сколько экран держится закрытым: успеть прочитать сабтайтл. */
    private static final int HOLD_MIN_TICKS = 12;
    /** Страховка: если уровень грузится бесконечно, открываем экран всё равно. */
    private static final int HOLD_MAX_TICKS = 200;
    /** После телепорта ещё пара тиков, чтобы клиент успел дорисовать чанки. */
    private static final int SETTLE_TICKS = 4;

    private static final Map<UUID, BukkitRunnable> RUNNING = new ConcurrentHashMap<>();

    public static boolean isRunning(@NonNull Player player) {
        return RUNNING.containsKey(player.getUniqueId());
    }

    public static void cancel(@NonNull Player player) {
        BukkitRunnable old = RUNNING.remove(player.getUniqueId());
        if (old != null) {
            try {
                old.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    /** Сабтайтл по умолчанию: куб, плашка сложности и название уровня. */
    @NonNull
    public static Component subtitle(@NonNull Player player, @Nullable LevelDifficulty difficulty,
                                     @Nullable Component levelName) {
        LevelDifficulty diff = difficulty == null ? LevelDifficulty.N_A : difficulty;
        String locale = PlayerLang.of(player);
        Component result = Glyphs.glyph(Glyphs.cubeBig(diff) + " " + Glyphs.banner(diff, locale));
        if (levelName != null) {
            result = result.append(Component.text("  ")).append(levelName.colorIfAbsent(NamedTextColor.WHITE));
        }
        return result;
    }

    /**
     * @param action то, что делается, пока экран закрыт (создание забега + телепорт).
     *               Вызывается ровно один раз, в основном потоке.
     */
    public static void play(@NonNull Plugin plugin,
                            @NonNull Player player,
                            @Nullable LevelDifficulty difficulty,
                            @NonNull Component subtitle,
                            @NonNull Supplier<CompletableFuture<?>> action
    ) {
        cancel(player);
        player.closeInventory();

        // Игрок выключил переходы в меню «Интерфейс» - сразу на уровень.
        if (!HudSettings.transitions(player)) {
            try {
                action.get();
            } catch (Throwable t) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Level transition action failed", t);
            }
            return;
        }

        Glyphs.Transition style = Glyphs.Transition.of(difficulty);

        BukkitRunnable task = new BukkitRunnable() {
            private int phase = 0; // 0 - закрытие, 1 - закрыто, 2 - открытие
            private int frame = 0;
            private int hold = 0;
            private int settle = -1;
            private CompletableFuture<?> future;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    this.finish();
                    return;
                }

                if (this.phase == 0) {
                    show(player, style.in(this.frame), Component.empty(), 3, 0);
                    if (this.frame == 0) {
                        UiSounds.play(player, UiSounds.Kind.TRANSITION_IN, 0.6F);
                    }
                    this.frame++;
                    if (this.frame >= style.inFrames) {
                        this.phase = 1;
                        this.startAction();
                    }
                    return;
                }

                if (this.phase == 1) {
                    if (this.hold % 4 == 0) show(player, style.full(), subtitle, 10, 0);
                    this.hold++;
                    if (this.settle < 0 && (this.future == null || this.future.isDone())) this.settle = 0;
                    if (this.settle >= 0) this.settle++;
                    boolean ready = this.settle >= SETTLE_TICKS && this.hold >= HOLD_MIN_TICKS;
                    if (ready || this.hold >= HOLD_MAX_TICKS) {
                        this.phase = 2;
                        this.frame = 0;
                        UiSounds.play(player, UiSounds.Kind.TRANSITION_OUT, 0.6F);
                    }
                    return;
                }

                if (this.frame < style.outFrames) {
                    boolean last = this.frame == style.outFrames - 1;
                    show(player, style.out(this.frame), this.frame < 2 ? subtitle : Component.empty(),
                        last ? 1 : 3, last ? 3 : 0);
                    this.frame++;
                } else {
                    this.finish();
                }
            }

            private void startAction() {
                try {
                    this.future = action.get();
                } catch (Throwable t) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Level transition action failed for " + player.getName(), t);
                    this.future = null;
                }
            }

            private void finish() {
                RUNNING.remove(player.getUniqueId(), this);
                try {
                    this.cancel();
                } catch (IllegalStateException ignored) {
                }
            }
        };

        RUNNING.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 1L);
    }

    private static void show(@NonNull Player player, char frame, @NonNull Component subtitle,
                             int stayTicks, int fadeOutTicks) {
        player.showTitle(Title.title(
            Glyphs.glyph(frame),
            subtitle,
            Title.Times.of(Duration.ZERO, Duration.ofMillis(stayTicks * 50L), Duration.ofMillis(fadeOutTicks * 50L))
        ));
    }
}
