package ru.sortix.parkourbeat.activity;

import lombok.NonNull;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public interface ActivityPacketsAdapter {
    void setWatchingPosition(@NonNull Player player, boolean watching);

    boolean isWatchingPosition(@NonNull Player player);

    @NonNull
    Vector getPosition(@NonNull Player player);
}
