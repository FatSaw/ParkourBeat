package ru.sortix.parkourbeat.player.scoreboard;

import ru.sortix.parkourbeat.utils.lang.Lang;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.stats.RunResult;
import ru.sortix.parkourbeat.stats.StatsFormat;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Табло просмотра реплея.
 * <p>
 * Оформление то же, что в игре и в наблюдении: строки берутся из LangOptions с их
 * юникод-иконками. Раньше здесь были свои самодельные строки, и одно и то же табло
 * выглядело по-разному в трёх режимах.
 * <p>
 * Данные забега неизменны, но ранг игрока в топе живёт своей жизнью, поэтому строка с
 * ником обновляется каждый тик - раньше она бралась один раз при открытии и показывала
 * ранг на момент записи, что путало.
 */
public class ReplayScoreboard implements ParkourBeatScoreboard {
    private final BasicScoreboard board;
    private final ParkourBeat plugin;
    private final Player viewer;
    private final RunResult run;

    public ReplayScoreboard(ParkourBeat plugin, Player viewer, RunResult run) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.run = run;

        this.board = new BasicScoreboard(plugin, viewer,
            Bukkit.getScoreboardManager().getNewScoreboard(), Component.text("\uE002"));

        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < 11; i++) lines.add(Component.empty());
        this.board.setLines(lines);
        this.board.show();
        this.update();
    }

    @Override
    public void update() {
        String lang = PlayerLang.of(this.viewer);

        String rank = "";
        try {
            rank = this.plugin.get(ru.sortix.parkourbeat.rating.StatisticsManager.class)
                .getRankLabel(this.run.getPlayerId()) + " ";
        } catch (Exception ignored) {
        }

        ru.sortix.parkourbeat.levels.settings.GameSettings settings = null;
        try {
            settings = this.plugin.get(ru.sortix.parkourbeat.rating.StatisticsManager.class)
                .getLevelSettings(this.run.getLevelId());
        } catch (Exception ignored) {
        }
        // Без keepColors: шаблон scoreboard_play_map в lang.yml уже оборачивает %map%
        // в <v>, и второй тег внутри выводился игроку как текст.
        String mapName = settings != null
            ? settings.getDisplayNameLegacy(false) : this.run.getLevelName();

        List<Component> lines = new ArrayList<>();
        lines.add(LangOptions.scoreboard_separator.getComponent(lang));
        lines.add(PbText.of(Lang.raw(PlayerLang.of(this.viewer), "auto.replay_scoreboard.update.1") + rank + "&f" + this.run.getPlayerName()));
        lines.add(LangOptions.scoreboard_separator.getComponent(lang));

        String grade = this.run.getGrade().getFormatted();
        if (!this.run.getModifiers().isEmpty()) {
            grade = grade + " &f| " + this.run.getModifiersCodes();
        }

        lines.add(LangOptions.scoreboard_play_time.getComponent(lang,
            new Placeholders("%time%", TimeUtils.formatTimecode(this.run.getTimeMillis()))));
        lines.add(LangOptions.scoreboard_play_accuracy.getComponent(lang,
            new Placeholders("%accuracy%",
                String.format(Locale.ROOT, "%.2f", this.run.getAccuracy())),
            new Placeholders("%grade%", grade)));
        lines.add(LangOptions.scoreboard_play_combo.getComponent(lang,
            new Placeholders("%combo%", String.valueOf(this.run.getMaxCombo()))));
        lines.add(LangOptions.scoreboard_play_score.getComponent(lang,
            new Placeholders("%score%", String.valueOf(this.run.getScore()))));

        lines.add(LangOptions.scoreboard_separator.getComponent(lang));
        lines.add(LangOptions.scoreboard_play_map.getComponent(lang,
            new Placeholders("%map%", mapName)));
        lines.add(PbText.of(Lang.raw(PlayerLang.of(this.viewer), "auto.replay_scoreboard.update.2")
            + StatsFormat.relativeDateTime(this.run.getTimestamp())));
        lines.add(LangOptions.scoreboard_separator.getComponent(lang));

        while (lines.size() > BasicScoreboard.MAX_LINES) {
            lines.remove(lines.size() - 2);
        }

        this.board.setLines(lines);
    }

    @Override
    public void hide() {
        this.board.hide();
    }
}
