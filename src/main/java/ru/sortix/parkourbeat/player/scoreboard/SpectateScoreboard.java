package ru.sortix.parkourbeat.player.scoreboard;

import ru.sortix.parkourbeat.utils.lang.Lang;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.SpectateActivity;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.rating.Modifier;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Табло режима наблюдателя.
 * <p>
 * Оформление намеренно взято из игрового табло - те же строки LangOptions с юникод-иконками,
 * чтобы смотреть за чужим забегом было привычно. Отличие одно: цифры берутся из игры того,
 * за кем смотрят, а сверху добавлена строка с его ником.
 */
public class SpectateScoreboard implements ParkourBeatScoreboard {
    private final BasicScoreboard board;
    private final ParkourBeat plugin;
    private final Player viewer;
    private final SpectateActivity activity;

    public SpectateScoreboard(ParkourBeat plugin, Player viewer, SpectateActivity activity) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.activity = activity;

        this.board = new BasicScoreboard(plugin, viewer,
            Bukkit.getScoreboardManager().getNewScoreboard(), Component.text("\uE002"));

        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < 11; i++) lines.add(Component.empty());
        this.board.setLines(lines);
        this.board.show();
    }

    public SpectateActivity getActivity() {
        return this.activity;
    }

    @Override
    public void update() {
        String lang = PlayerLang.of(this.viewer);

        String mapName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacyAmpersand().serialize(this.activity.getLevel().getDisplayName());

        List<Component> lines = new ArrayList<>();
        lines.add(LangOptions.scoreboard_separator.getComponent(lang));

        UUID targetId = this.activity.getTargetPlayerId();
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);

        if (target == null) {
            lines.add(PbText.of(Lang.raw(PlayerLang.of(target), "auto.spectate_scoreboard.update.1")));
            lines.add(LangOptions.scoreboard_separator.getComponent(lang));
            lines.add(LangOptions.scoreboard_play_map.getComponent(lang,
                new Placeholders("%map%", mapName)));
            lines.add(LangOptions.scoreboard_separator.getComponent(lang));
            this.board.setLines(lines);
            return;
        }

        String rank = "";
        try {
            rank = this.plugin.get(ru.sortix.parkourbeat.rating.StatisticsManager.class)
                .getRankLabel(targetId) + " ";
        } catch (Exception ignored) {
        }
        lines.add(PbText.of(Lang.raw(PlayerLang.of(target), "auto.spectate_scoreboard.update.2") + rank + "&f" + target.getName()));
        lines.add(LangOptions.scoreboard_separator.getComponent(lang));

        Game game = this.activity.getTargetGame();
        boolean running = game != null && game.getCurrentState() == Game.State.RUNNING;

        if (game == null) {
            lines.add(PbText.of(Lang.raw(PlayerLang.of(target), "auto.spectate_scoreboard.update.3")));
        } else {
            // Прогресс и время имеют смысл только во время забега. До старта и после
            // провала они показывали остатки прошлой попытки и выглядели зависшими -
            // поэтому вне забега они просто обнуляются.
            boolean hideProgress = game.getLevel().getLevelSettings()
                .getGameSettings().isHideBossBar();

            if (!hideProgress) {
                lines.add(LangOptions.scoreboard_play_progress.getComponent(lang,
                    new Placeholders("%progress%", running
                        ? String.format(Locale.ROOT, "%.0f", game.getPassedProgress() * 100f)
                        : "0")));
            }

            lines.add(LangOptions.scoreboard_play_time.getComponent(lang,
                new Placeholders("%time%", running ? game.getSongTimecode() : "0:00")));

            String grade = game.getCurrentGrade().getFormatted();
            String modifiers = formatModifiers(game);
            if (modifiers != null) grade = grade + " &f| " + modifiers;

            lines.add(LangOptions.scoreboard_play_accuracy.getComponent(lang,
                new Placeholders("%accuracy%",
                    String.format(Locale.ROOT, "%.2f", game.getDisplayAccuracy())),
                new Placeholders("%grade%", grade)));

            lines.add(LangOptions.scoreboard_play_combo.getComponent(lang,
                new Placeholders("%combo%", String.valueOf(game.getRunTracker().getCombo()))));

            lines.add(LangOptions.scoreboard_play_score.getComponent(lang,
                new Placeholders("%score%", String.valueOf(game.getRunTracker().getScore()))));
        }

        lines.add(LangOptions.scoreboard_separator.getComponent(lang));
        lines.add(LangOptions.scoreboard_play_map.getComponent(lang,
            new Placeholders("%map%", mapName)));
        lines.add(LangOptions.scoreboard_play_ping.getComponent(lang,
            new Placeholders("%ping%", ru.sortix.parkourbeat.stats.StatsFormat.ping(
                this.plugin.get(ru.sortix.parkourbeat.player.PingManager.class).getPing(target)))));
        lines.add(LangOptions.scoreboard_separator.getComponent(lang));

        // Табло не переживает больше MAX_LINES строк: лучше срезать хвост,
        // чем словить исключение прямо в тике.
        while (lines.size() > BasicScoreboard.MAX_LINES) {
            lines.remove(lines.size() - 2);
        }

        this.board.setLines(lines);
    }

    /**
     * @return коды активных модификаторов через запятую или null, если их нет
     */
    private static String formatModifiers(Game game) {
        StringBuilder builder = new StringBuilder();
        for (Modifier modifier : game.getModifiers().getActive()) {
            if (builder.length() > 0) builder.append("&f, ");
            builder.append(modifier.getCode());
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    @Override
    public void hide() {
        this.board.hide();
    }
}
