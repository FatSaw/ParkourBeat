package ru.sortix.parkourbeat.player.scoreboard;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.util.ArrayList;
import java.util.List;

public class PlayScoreboard implements ParkourBeatScoreboard {
    private final BasicScoreboard board;
    private final Player player;
    private final Game game;

    public PlayScoreboard(Plugin plugin, Player player, Game game) {
        this.player = player;
        this.game = game;

        this.board = new BasicScoreboard(plugin, player, Bukkit.getScoreboardManager().getNewScoreboard(), Component.text("\uE002"));

        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < 11; i++) lines.add(Component.empty());
        this.board.setLines(lines);
        this.board.show();
    }

    @Override
    public void update() {
        String lang = this.player.getLocale().toLowerCase();

        ru.sortix.parkourbeat.rating.RunTracker run = this.game.getRunTracker();
        int currentCombo = run.getCombo();
        int currentScore = run.getScore();

        float progress = this.game.getPassedProgress() * 100f;
        String timecode = this.game.getSongTimecode();
        double accuracy = this.game.getDisplayAccuracy();

        ru.sortix.parkourbeat.rating.AccuracyGrade grade = this.game.getCurrentGrade();
        String formattedGrade = grade.getFormatted();

        // PRACTICE appends " | PC" to the accuracy line, per the TZ.
        if (this.game.hasModifier(ru.sortix.parkourbeat.rating.Modifier.PRACTICE)) {
            formattedGrade = formattedGrade + " &f| " + ru.sortix.parkourbeat.rating.Modifier.PRACTICE.getCode();
        }

        double tps = Math.round(Bukkit.getServer().getTPS()[0] * 10.0) / 10.0;
        int ping = this.game.getPlugin().get(ru.sortix.parkourbeat.player.PingManager.class).getPing(this.player);

        String mapName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacyAmpersand().serialize(this.game.getLevel().getDisplayName());

        boolean hideProgress = this.game.getLevel().getLevelSettings().getGameSettings().isHideBossBar();

        List<Component> lines = new ArrayList<>();
        lines.add(LangOptions.scoreboard_separator.getComponent(lang));

        if (!hideProgress) {
            lines.add(LangOptions.scoreboard_play_progress.getComponent(lang,
                new Placeholders("%progress%", String.format(java.util.Locale.ROOT, "%.0f", progress))));
        }

        lines.add(LangOptions.scoreboard_play_time.getComponent(lang,
            new Placeholders("%time%", timecode)));

        lines.add(LangOptions.scoreboard_play_accuracy.getComponent(lang,
            new Placeholders("%accuracy%", String.format(java.util.Locale.ROOT, "%.2f", accuracy)),
            new Placeholders("%grade%", formattedGrade)));

        lines.add(LangOptions.scoreboard_play_combo.getComponent(lang,
            new Placeholders("%combo%", String.valueOf(currentCombo))));

        lines.add(LangOptions.scoreboard_play_score.getComponent(lang,
            new Placeholders("%score%", String.valueOf(currentScore))));

        lines.add(LangOptions.scoreboard_separator.getComponent(lang));

        lines.add(LangOptions.scoreboard_play_map.getComponent(lang,
            new Placeholders("%map%", mapName)));

        lines.add(LangOptions.scoreboard_play_ping.getComponent(lang,
            new Placeholders("%ping%", ru.sortix.parkourbeat.stats.StatsFormat.ping(ping))));

        lines.add(LangOptions.scoreboard_play_tps.getComponent(lang,
            new Placeholders("%tps%", String.valueOf(tps))));

        lines.add(LangOptions.scoreboard_separator.getComponent(lang));

        this.board.setLines(lines);
    }

    @Override
    public void hide() {
        this.board.hide();
    }
}
