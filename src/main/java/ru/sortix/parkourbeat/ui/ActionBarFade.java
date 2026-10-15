package ru.sortix.parkourbeat.ui;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import ru.sortix.parkourbeat.rating.JumpResult;
import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Плашка попадания в актионбаре, которая гаснет так же, как раньше «+300» в сабтайтле:
 * держится 310 мс, затем за 135 мс плавно тает.
 * <p>
 * Время показа актионбара клиент держит сам (~3 с), поэтому затухание сделано
 * кадрами: в РП та же плашка лежит в 4 ступенях прозрачности (100/70/45/20%), они
 * отправляются по тику, а в конце - пустая строка. Новый прыжок перебивает старый.
 */
public final class ActionBarFade {
    private ActionBarFade() {
    }

    private static final int HOLD_TICKS = 6;   // ~310 мс
    private static final int FADE_STEPS = 3;   // ~135 мс

    private static final Map<UUID, BukkitRunnable> RUNNING = new ConcurrentHashMap<>();

    public static void rate(@NonNull Player player, @NonNull JumpResult result) {
        show(player, result, true, null);
    }

    /**
     * Актионбар по настройкам игрока: плашка оценки (если banner) и/или мелкие очки
     * (mini, например «+300» нашими цифрами). Плашка гаснет кадрами прозрачности,
     * очки - темнея цветом. Если показывать нечего - ничего не отправляется.
     */
    public static void show(@NonNull Player player, @NonNull JumpResult result, boolean banner,
                            @javax.annotation.Nullable String mini) {
        if (!banner && mini == null) return;
        String locale = PlayerLang.of(player);
        cancel(player);
        player.sendActionBar(frame(result, locale, 0, banner, mini));
        BukkitRunnable task = new BukkitRunnable() {
            private int tick = 0;

            @Override
            public void run() {
                this.tick++;
                if (!player.isOnline()) {
                    this.finish();
                    return;
                }
                int step = this.tick - HOLD_TICKS;
                if (step >= 1 && step <= FADE_STEPS) {
                    player.sendActionBar(frame(result, locale, step, banner, mini));
                } else if (step > FADE_STEPS) {
                    player.sendActionBar(Component.empty());
                    this.finish();
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
        task.runTaskTimer(JavaPlugin.getProvidingPlugin(ActionBarFade.class), 1L, 1L);
    }

    private static final String[] FADE_COLORS = {null, "&7", "&8", "&0"};

    @NonNull
    private static Component frame(@NonNull JumpResult result, String locale, int step, boolean banner,
                                   @javax.annotation.Nullable String mini) {
        Component out = banner ? Glyphs.glyph(Glyphs.rateBar(result, locale, step)) : Component.empty();
        if (mini != null) {
            String text = step == 0 ? mini : FADE_COLORS[Math.min(3, step)] + mini.replaceAll("(?i)[&\u00A7][0-9a-fk-or]", "");
            out = out.append(Component.text(banner ? " " : "")).append(ru.sortix.parkourbeat.utils.text.PbText.of(text));
        }
        return out;
    }

    /** Старый вариант: только плашка. Оставлен для совместимости. */
    private static void legacyRate(@NonNull Player player, @NonNull JumpResult result) {
        String locale = PlayerLang.of(player);
        cancel(player);
        player.sendActionBar(Glyphs.glyph(Glyphs.rateBar(result, locale, 0)));

        BukkitRunnable task = new BukkitRunnable() {
            private int tick = 0;

            @Override
            public void run() {
                this.tick++;
                if (!player.isOnline()) {
                    this.finish();
                    return;
                }
                int step = this.tick - HOLD_TICKS;
                if (step >= 1 && step <= FADE_STEPS) {
                    player.sendActionBar(Glyphs.glyph(Glyphs.rateBar(result, locale, step)));
                } else if (step > FADE_STEPS) {
                    player.sendActionBar(Component.empty());
                    this.finish();
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
        task.runTaskTimer(JavaPlugin.getProvidingPlugin(ActionBarFade.class), 1L, 1L);
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
}
