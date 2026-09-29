package ru.sortix.parkourbeat.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import lombok.NonNull;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.sortix.parkourbeat.ParkourBeat;

import java.util.HashSet;
import java.util.Set;

/**
 * Звуковой пинг тому, чей ник упомянули в чате.
 * <p>
 * Ник ищется по целым словам, поэтому "AlliSighs", "@AlliSighs" и "привет, allisighs!"
 * сработают, а "AlliSighsXX" - нет. Регистр не важен.
 */
public class ChatMentionListener implements Listener {
    private static final Sound PING_SOUND = Sound.BLOCK_NOTE_BLOCK_PLING;
    private static final float PING_VOLUME = 1.0f;
    private static final float PING_PITCH = 1.1f;

    /** Количество звуков и пауза между ними. Тик - это ровно 50 мс. */
    private static final int PING_COUNT = 2;
    private static final long PING_INTERVAL_TICKS = 1L;

    /** Ники в Minecraft состоят только из букв, цифр и подчёркиваний. */
    private static final String NAME_SEPARATORS = "[^A-Za-z0-9_]+";

    private final @NonNull ParkourBeat plugin;

    public ChatMentionListener(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void on(AsyncChatEvent event) {
        String text;
        try {
            text = PlainComponentSerializer.plain().serialize(event.message());
        } catch (Exception e) {
            return;
        }
        if (text.isEmpty()) return;

        Set<Player> mentioned = this.findMentioned(text, event.getPlayer());
        if (mentioned.isEmpty()) return;

        // Событие чата приходит в асинхронном потоке, а звук - это работа с игроком.
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            for (Player player : mentioned) {
                this.ping(player, PING_COUNT);
            }
        });
    }

    @NonNull
    private Set<Player> findMentioned(@NonNull String text, @NonNull Player author) {
        Set<String> words = new HashSet<>();
        for (String word : text.split(NAME_SEPARATORS)) {
            if (word.isEmpty()) continue;
            words.add(word.toLowerCase(java.util.Locale.ROOT));
        }
        if (words.isEmpty()) return java.util.Collections.emptySet();

        Set<Player> mentioned = new HashSet<>();
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            // Упоминание самого себя пингует зря: человек и так читает свой текст.
            if (player.equals(author)) continue;
            if (!words.contains(player.getName().toLowerCase(java.util.Locale.ROOT))) continue;
            mentioned.add(player);
        }
        return mentioned;
    }

    /**
     * Играет пинг и планирует следующий, пока они не кончатся.
     */
    private void ping(@NonNull Player player, int remaining) {
        if (remaining <= 0) return;
        if (!player.isOnline()) return;

        player.playSound(player.getLocation(), PING_SOUND, SoundCategory.MASTER, PING_VOLUME, PING_PITCH);

        if (remaining <= 1) return;
        this.plugin.getServer().getScheduler().runTaskLater(this.plugin,
            () -> this.ping(player, remaining - 1), PING_INTERVAL_TICKS);
    }
}
