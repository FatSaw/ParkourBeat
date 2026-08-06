package ru.sortix.parkourbeat.player;

import lombok.NonNull;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Player pushing is resolved server side through the main scoreboard, so a single team with
 * {@link Team.Option#COLLISION_RULE} set to {@link Team.OptionStatus#NEVER} is enough.
 * Players are put into that team while they have an activity, which means while they are
 * on a level, and taken out of it as soon as they return to the lobby.
 */
public class PlayersCollisionManager implements PluginManager {
    private static final String TEAM_NAME = "pb_no_collision";

    private final @NonNull ParkourBeat plugin;
    private @Nullable Team team;

    public PlayersCollisionManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.team = this.createTeam();
    }

    @Nullable
    private Team createTeam() {
        ScoreboardManager scoreboardManager = this.plugin.getServer().getScoreboardManager();
        if (scoreboardManager == null) {
            this.plugin.getLogger().severe("Unable to disable players collisions: scoreboard manager not available");
            return null;
        }

        Scoreboard scoreboard = scoreboardManager.getMainScoreboard();
        Team existingTeam = scoreboard.getTeam(TEAM_NAME);
        if (existingTeam != null) {
            try {
                existingTeam.unregister();
            } catch (IllegalStateException ignored) {
            }
        }

        try {
            Team newTeam = scoreboard.registerNewTeam(TEAM_NAME);
            newTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            newTeam.setCanSeeFriendlyInvisibles(false);
            newTeam.setAllowFriendlyFire(true);
            return newTeam;
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Unable to create players collision team", e);
            return null;
        }
    }

    public void setCollisionsDisabled(@NonNull Player player, boolean disabled) {
        if (this.team == null) return;
        String entry = player.getName();
        try {
            if (disabled) {
                if (!this.team.hasEntry(entry)) this.team.addEntry(entry);
            } else {
                if (this.team.hasEntry(entry)) this.team.removeEntry(entry);
            }
        } catch (IllegalStateException e) {
            this.team = this.createTeam();
        }
        for (Player p : this.plugin.getServer().getOnlinePlayers()) {
            Scoreboard board = p.getScoreboard();
            if (board != null && board != this.plugin.getServer().getScoreboardManager().getMainScoreboard()) {
                Team t = board.getTeam(TEAM_NAME);
                if (t != null) {
                    if (disabled) {
                        if (!t.hasEntry(entry)) t.addEntry(entry);
                    } else {
                        if (t.hasEntry(entry)) t.removeEntry(entry);
                    }
                }
            }
        }
    }

    @Override
    public void disable() {
        if (this.team == null) return;
        try {
            this.team.unregister();
        } catch (IllegalStateException ignored) {
        }
        this.team = null;
    }
}
