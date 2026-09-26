package ru.sortix.parkourbeat.listeners;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.player.PlayerSettingsManager;

import ru.sortix.parkourbeat.utils.text.PbText;
public class PrivateLevelGuardListener implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final String BYPASS_PERMISSION = "parkourbeat.command.bypassprivate";

    private final @NonNull ParkourBeat plugin;

    public PrivateLevelGuardListener(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void on(@NonNull PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        World to = event.getTo().getWorld();
        if (to == null || to == event.getFrom().getWorld()) return;

        Level level = this.plugin.get(LevelsManager.class).getLoadedLevel(to);
        if (level == null) return;

        GameSettings settings = level.getLevelSettings().getGameSettings();
        if (settings.isPublicVisible()) return;

        Player player = event.getPlayer();
        // Именно isAccessibleForPlaying, а не canEdit: последний знает только про владельца,
        // соредакторов и друзей с правом СТРОИТЬ. Друг, которому владелец открыл доступ на
        // приватные уровни, под canEdit не подходил - и упирался в "вход закрыт", хотя
        // выдача права на стройку тот же телепорт почему-то чинила.
        if (settings.isAccessibleForPlaying(player, false)) return;

        PlayerSettingsManager playerSettings = this.plugin.get(PlayerSettingsManager.class);

        if (!player.hasPermission(BYPASS_PERMISSION)) {
            event.setCancelled(true);
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.private_level_guard_listener.on.1")));
            return;
        }

        if (playerSettings.consumePrivateBypass(player.getUniqueId())) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.private_level_guard_listener.on.2")));
            return;
        }

        event.setCancelled(true);
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.private_level_guard_listener.on.3")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.private_level_guard_listener.on.4")));
    }
}
