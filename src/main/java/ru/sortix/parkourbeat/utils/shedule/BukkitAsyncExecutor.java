package ru.sortix.parkourbeat.utils.shedule;

import lombok.NonNull;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.concurrent.Executor;

public class BukkitAsyncExecutor implements Executor {
    private final Plugin plugin;
    private final BukkitScheduler scheduler;

    public BukkitAsyncExecutor(@NonNull Plugin plugin) {
        this.plugin = plugin;
        this.scheduler = plugin.getServer().getScheduler();
    }

    @Override
    public void execute(@NonNull Runnable command) {
        this.scheduler.runTaskAsynchronously(this.plugin, command);
    }
}
