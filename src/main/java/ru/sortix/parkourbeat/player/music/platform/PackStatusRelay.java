package ru.sortix.parkourbeat.player.music.platform;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.UUID;

@RequiredArgsConstructor
public final class PackStatusRelay implements PluginMessageListener {
    private final @NonNull MusicPackDispatcher dispatcher;

    @Override
    public void onPluginMessageReceived(@NonNull String channel, @NonNull Player player, byte[] message) {
        if (!MusicPackDispatcher.RELAY_CHANNEL.equals(channel)) return;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            long most = in.readLong();
            long least = in.readLong();
            String status = in.readUTF();
            this.dispatcher.onRelayStatus(new UUID(most, least), status);
        } catch (Throwable ignored) {
        }
    }
}
