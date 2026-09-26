package ru.sortix.parkourbeat.player.music;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import lombok.NonNull;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.player.PlayerSettingsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Подстраховка для звука, который уходит мимо AMusicPlatform: клиентский режим AMusic
 * и Bukkit-платформа. Сами треки AMusic получают громкость в AMusicPlatform.AMusicUtils
 * и здесь намеренно пропускаются.
 */
public class MusicVolumeListener implements PluginManager {
    private final @NonNull ParkourBeat plugin;
    private PacketAdapter adapter = null;
    private boolean registered = false;
    private boolean loggedOnce = false;

    private static final String AMUSIC_SOUND_MARKER = "amusic.internal";

    public MusicVolumeListener(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.register();
    }

    /**
     * CUSTOM_SOUND_EFFECT существует не во всех версиях (в 1.19.3+ его слили с NAMED_SOUND_EFFECT).
     * Раньше оба типа передавались одним списком, и отсутствие одного роняло регистрацию целиком,
     * из-за чего перехватчик молча не работал. Теперь типы собираются по одному.
     */
    private void register() {
        try {
            Class.forName("com.comphenix.protocol.ProtocolLibrary");
        } catch (ClassNotFoundException e) {
            return;
        }

        List<PacketType> types = new ArrayList<>();
        this.addTypeIfExists(types, "NAMED_SOUND_EFFECT");
        this.addTypeIfExists(types, "CUSTOM_SOUND_EFFECT");
        if (types.isEmpty()) {
            this.plugin.getLogger().warning("Unable to hook music volume: no sound packet types available");
            return;
        }

        try {
            this.adapter = new PacketAdapter(this.plugin, types.toArray(new PacketType[0])) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    MusicVolumeListener.this.handle(event);
                }
            };
            ProtocolLibrary.getProtocolManager().addPacketListener(this.adapter);
            this.registered = true;
        } catch (Throwable t) {
            this.plugin.getLogger().log(Level.WARNING, "Unable to hook music volume", t);
        }
    }

    private void addTypeIfExists(@NonNull List<PacketType> types, @NonNull String fieldName) {
        try {
            Object value = PacketType.Play.Server.class.getField(fieldName).get(null);
            if (!(value instanceof PacketType type)) return;
            if (!type.isSupported()) return;
            types.add(type);
        } catch (Throwable ignored) {
        }
    }

    private void handle(@NonNull PacketEvent event) {
        try {
            Player player = event.getPlayer();
            if (player == null) return;

            float factor = this.plugin.get(PlayerSettingsManager.class)
                .getMusicVolumeFactor(player.getUniqueId());
            if (factor >= 0.999f) return;

            PacketContainer packet = event.getPacket();

            String soundName = this.readSoundName(packet);
            // Звуки AMusic уже уходят с нужной громкостью из AMusicPlatform.AMusicUtils,
            // второй раз множить нельзя, иначе получится квадрат множителя.
            if (soundName != null && soundName.contains(AMUSIC_SOUND_MARKER)) return;

            if (!this.isMusicSound(soundName, event.getPacketType())) return;

            Float volume = packet.getFloat().readSafely(0);
            if (volume == null || volume <= 0f) return;

            packet.getFloat().write(0, volume * factor);

            if (!this.loggedOnce) {
                this.loggedOnce = true;
                this.plugin.getLogger().info("Music volume hook active (packet "
                    + event.getPacketType().name() + ")");
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * AMusic шлёт трек как custom sound с ключом minecraft:amusic.internal.*,
     * и категория в этом пакете читается не всегда, поэтому смотрим ещё и имя.
     */
    private boolean isMusicSound(@javax.annotation.Nullable String name, @NonNull PacketType type) {
        if (name != null) {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("amusic") || lower.contains("music")
                || lower.contains("track") || lower.contains("record")) {
                return true;
            }
        }

        return type != PacketType.Play.Server.NAMED_SOUND_EFFECT;
    }

    @javax.annotation.Nullable
    private String readSoundName(@NonNull PacketContainer packet) {
        try {
            String value = packet.getStrings().readSafely(0);
            if (value != null) return value;
        } catch (Throwable ignored) {
        }
        try {
            Object key = packet.getMinecraftKeys().readSafely(0);
            if (key != null) return key.toString();
        } catch (Throwable ignored) {
        }
        try {
            Object sound = packet.getSoundEffects().readSafely(0);
            if (sound != null) return sound.toString();
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public void disable() {
        if (!this.registered || this.adapter == null) return;
        try {
            ProtocolLibrary.getProtocolManager().removePacketListener(this.adapter);
        } catch (Throwable ignored) {
        }
        this.registered = false;
    }
}
