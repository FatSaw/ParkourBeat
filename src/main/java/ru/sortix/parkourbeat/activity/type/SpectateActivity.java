package ru.sortix.parkourbeat.activity.type;

import lombok.NonNull;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.world.TeleportUtils;

public class SpectateActivity extends UserActivity {
    public SpectateActivity(@NonNull ParkourBeat plugin, @NonNull Player player, @NonNull Level level) {
        super(plugin, player, level);
    }

    @Override
    public void startActivity() {
        this.player.setGameMode(GameMode.SPECTATOR);
        LangOptions.level_spectate_success.sendMsg(player, new Placeholders("%level%", ((TextComponent)this.level.getDisplayName()).content()));
    }

    @Override
    public void on(@NonNull PlayerResourcePackStatusEvent event) {
    }

    @Override
    public void on(@NonNull PlayerMoveEvent event) {
    }

    @Override
    public void onTick() {
    }

    @Override
    public void on(@NonNull PlayerToggleSprintEvent event) {
    }

    @Override
    public void on(@NonNull PlayerToggleSneakEvent event) {
    }

    @Override
    public int getFallHeight() {
        return this.getFallHeight(false);
    }

    @Override
    public void onPlayerFall() {
        TeleportUtils.teleportAsync(this.getPlugin(), this.player, this.level.getSpawn());
    }

    @Override
    public void endActivity() {
        this.player.setGameMode(GameMode.ADVENTURE);
    }
}
