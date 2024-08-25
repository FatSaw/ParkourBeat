package ru.sortix.parkourbeat.utils.shedule;

import lombok.NonNull;

import java.util.concurrent.Executor;

public class CurrentThreadExecutor implements Executor {
    @Override
    public void execute(@NonNull Runnable command) {
        command.run();
    }
}
