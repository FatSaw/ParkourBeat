package ru.sortix.parkourbeat.levels.lamps;

import lombok.NonNull;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Проигрывает ламповые стены по времени песни.
 * <p>
 * Лампы это блоки мира, поэтому в отличие от частиц шоу видят все, кто рядом.
 * Стены, которые уже отыграли, гасятся один раз, а не каждый тик.
 */
public class LampRunner {

    private final @NonNull World world;
    private final @NonNull List<LampWall> walls;
    private final Set<LampWall> active = new HashSet<>();

    private long lastTime = Long.MIN_VALUE;

    public LampRunner(@NonNull World world, @NonNull List<LampWall> walls) {
        this.world = world;
        this.walls = new ArrayList<>(walls);
        this.walls.sort((a, b) -> Integer.compare(a.getStartMillis(), b.getStartMillis()));
    }

    public boolean isEmpty() {
        return this.walls.isEmpty();
    }

    public void tick(long songTimeMillis, boolean running) {
        if (this.walls.isEmpty()) return;

        if (!running) {
            if (!this.active.isEmpty()) this.resetAll();
            this.lastTime = Long.MIN_VALUE;
            return;
        }
        if (songTimeMillis + 250L < this.lastTime) this.resetAll();
        this.lastTime = songTimeMillis;

        for (LampWall wall : this.walls) {
            boolean shouldRun = wall.isActive(songTimeMillis);

            if (shouldRun) {
                int duration = Math.max(1, wall.getDurationMillis());
                double progress = (songTimeMillis - wall.getStartMillis()) / (double) duration;
                double phase = progress * Math.max(0.05D, wall.getSpeed());
                // Повтор: узор гоняется по кругу, а не растягивается на всю длительность
                if (wall.isLoop()) phase = phase % 1.0D;
                LampEngine.apply(this.world, wall, phase, progress);
                this.active.add(wall);
                continue;
            }

            if (this.active.remove(wall)) LampEngine.reset(this.world, wall);
        }
    }

    public void resetAll() {
        for (LampWall wall : this.active) LampEngine.reset(this.world, wall);
        this.active.clear();
    }

    public void shutdown() {
        this.resetAll();
        this.lastTime = Long.MIN_VALUE;
    }
}
