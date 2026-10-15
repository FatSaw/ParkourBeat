package ru.sortix.parkourbeat.levels.wonder;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import org.bukkit.entity.Player;
import ru.lightshow.api.ShowHandle;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Проигрывает чудоэффекты одного игрока по времени песни.
 * <p>
 * Эффект стартует ровно на своём таймкоде и живёт до конца окна. Перемотка назад
 * (рестарт с чекпоинта, откат) обнаруживается по времени, идущему вспять: всё
 * запущенное гасится, и таймлайн проигрывается заново.
 */
public class WonderRunner {

    private final @NonNull Player player;
    private final ru.sortix.parkourbeat.levels.Level level;
    private final @NonNull List<WonderEffect> effects;
    private final Map<WonderEffect, ShowHandle> running = new IdentityHashMap<>();

    private long lastTime = Long.MIN_VALUE;
    private boolean warnedAboutMissingPlugin = false;

    public WonderRunner(@NonNull Player player,
                        ru.sortix.parkourbeat.levels.Level level,
                        @NonNull List<WonderEffect> effects) {
        this.player = player;
        this.level = level;
        this.effects = new ArrayList<>(effects);
        this.effects.sort((a, b) -> Integer.compare(a.getStartMillis(), b.getStartMillis()));
    }

    public boolean isEmpty() {
        return this.effects.isEmpty();
    }

    public void tick(long songTimeMillis) {
        this.tick(songTimeMillis, true);
    }

    public void tick(long songTimeMillis, boolean running) {
        if (this.effects.isEmpty()) return;

        // На спавне время стоит на нуле, и эффект с 00:00 висел бы вечно
        if (!running) {
            if (!this.running.isEmpty()) this.stopAll();
            this.lastTime = Long.MIN_VALUE;
            return;
        }

        if (!WonderBridge.isAvailable()) {
            if (!this.warnedAboutMissingPlugin) {
                this.warnedAboutMissingPlugin = true;
                this.player.sendMessage(ru.sortix.parkourbeat.utils.text.PbText.of(
                    Lang.raw(PlayerLang.of(this.player), "auto.wonder_runner.tick.1")));
            }
            return;
        }

        if (songTimeMillis + 250L < this.lastTime) this.stopAll();
        this.lastTime = songTimeMillis;

        for (WonderEffect effect : this.effects) {
            boolean shouldRun = effect.isActive(songTimeMillis);
            ShowHandle handle = this.running.get(effect);

            if (shouldRun && handle == null) {
                int leftMillis = effect.getEndMillis() - (int) songTimeMillis;
                int ticks = Math.max(1, leftMillis / 50);
                org.bukkit.Location onTrack = this.level == null ? null
                    : ru.sortix.parkourbeat.utils.wonder.WonderTimeline
                        .locationAt(this.level, effect.getStartMillis());
                ShowHandle started = WonderBridge.play(this.player, effect, ticks, onTrack);
                if (started != null) this.running.put(effect, started);
                continue;
            }

            if (!shouldRun && handle != null) {
                handle.stop();
                this.running.remove(effect);
            }
        }

        this.running.entrySet().removeIf(entry -> !entry.getValue().isAlive());
    }

    public void stopAll() {
        for (ShowHandle handle : this.running.values()) {
            try {
                handle.stop();
            } catch (Throwable ignored) {
            }
        }
        this.running.clear();
    }

    public void shutdown() {
        this.stopAll();
        this.lastTime = Long.MIN_VALUE;
    }
}
