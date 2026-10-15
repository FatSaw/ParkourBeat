package ru.sortix.parkourbeat.player.scoreboard;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.twod.TwoDGame;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;

/**
 * ТАБЛО 2D-ЗАБЕГА.
 * <p>
 * Обычное сюда не годится: оно показывает комбо, очки и точность из трекера попаданий,
 * а в 2D попаданий нет вообще. Поэтому строки другие, но каркас и оформление те же -
 * игрок не должен видеть чужеродное окно.
 */
public class TwoDScoreboard implements ParkourBeatScoreboard {

    private final BasicScoreboard board;
    private final Plugin plugin;
    private final Player player;
    private final Level level;
    private final TwoDGame game;

    public TwoDScoreboard(Plugin plugin, Player player, Level level, TwoDGame game) {
        this.plugin = plugin;
        this.player = player;
        this.level = level;
        this.game = game;

        this.board = new BasicScoreboard(plugin, player,
            Bukkit.getScoreboardManager().getNewScoreboard(), Component.text("\uE002"));

        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < 11; i++) lines.add(Component.empty());
        this.board.setLines(lines);
        this.board.show();
    }

    public TwoDGame getGame() {
        return this.game;
    }

    @Override
    public void update() {
        String lang = PlayerLang.of(this.player);

        float progress = this.game.getPassedProgress() * 100f;
        int attempt = this.game.getAttempt();
        int deaths = Math.max(0, attempt - 1);

        ru.sortix.parkourbeat.levels.settings.GameSettings settings =
            this.level.getLevelSettings().getGameSettings();

        int totalCoins = settings.getTwoDSettings().getCoinsAmount();
        int coins = this.game.getCoinsCollected();

        double accuracy = TwoDGame.attemptAccuracy(deaths, this.game.getMissedCoins());
        ru.sortix.parkourbeat.rating.AccuracyGrade grade =
            ru.sortix.parkourbeat.rating.AccuracyGrade.byAccuracy(accuracy);

        String timecode = ru.sortix.parkourbeat.utils.TimeUtils.formatTimecode(
            this.game.getAttemptMillis());

        double tps = Math.round(Bukkit.getServer().getTPS()[0] * 10.0) / 10.0;
        int ping = 0;
        try {
            ping = ((ru.sortix.parkourbeat.ParkourBeat) this.plugin)
                .get(ru.sortix.parkourbeat.player.PingManager.class).getPing(this.player);
        } catch (Throwable ignored) {
        }

        String mapName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacyAmpersand().serialize(this.level.getDisplayName());

        boolean hideProgress = settings.isHideBossBar();

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
            new Placeholders("%grade%", grade.getFormatted())));

        // Монетки занимают место комбо: комбо в 2D нечему считать, а монетки
        // меняются по ходу забега ровно так же.
        lines.add(LangOptions.scoreboard_play_coins.getComponent(lang,
            new Placeholders("%coins%", String.valueOf(coins)),
            new Placeholders("%total%", String.valueOf(totalCoins))));

        lines.add(LangOptions.scoreboard_play_attempt.getComponent(lang,
            new Placeholders("%attempt%", String.valueOf(attempt))));

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
