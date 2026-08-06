package ru.sortix.parkourbeat.player.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.ModerationStatus;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class IdleScoreboard implements ParkourBeatScoreboard {
    private final BasicScoreboard board;
    private final Player player;
    private final Level level;
    private final ParkourBeat plugin;

    public IdleScoreboard(ParkourBeat plugin, Player player, @Nullable Level level) {
        this.plugin = plugin;
        this.player = player;
        this.level = level;

        this.board = new BasicScoreboard(plugin, player, Bukkit.getScoreboardManager().getNewScoreboard(), Component.text("\uE002"));

        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < 11; i++) lines.add(Component.empty());
        this.board.setLines(lines);
        this.board.show();
    }

    @Nullable
    public Level getLevel() {
        return this.level;
    }

    @Override
    public void update() {
        String lang = this.player.getLocale().toLowerCase();

        ru.sortix.parkourbeat.rating.StatisticsManager statistics =
            this.plugin.get(ru.sortix.parkourbeat.rating.StatisticsManager.class);
        ru.sortix.parkourbeat.stats.ProfileSummary summary = statistics.summarize(
            statistics.getProfile(this.player.getUniqueId(), this.player.getName()));

        long totalScore = summary.getTotalScore();
        double avgAccuracy = summary.getAverageAccuracy();
        int maxCombo = summary.getMaxCombo();
        String playerRank = statistics.getRankLabel(this.player.getUniqueId());

        double tps = Math.round(Bukkit.getServer().getTPS()[0] * 10.0) / 10.0;
        int ping = this.plugin.get(ru.sortix.parkourbeat.player.PingManager.class).getPing(this.player);

        String mapName = this.level == null
            ? LangOptions.scoreboard_idle_mapnone.get(lang)
            : LegacyComponentSerializer.legacyAmpersand().serialize(this.level.getDisplayName());

        List<Component> lines = new ArrayList<>();
        lines.add(LangOptions.scoreboard_separator.getComponent(lang));

        lines.add(LangOptions.scoreboard_idle_nickname.getComponent(lang,
            new Placeholders("%rank%", playerRank),
            new Placeholders("%name%", this.player.getName())));

        lines.add(LangOptions.scoreboard_idle_score.getComponent(lang,
            new Placeholders("%score%", String.valueOf(totalScore))));

        lines.add(LangOptions.scoreboard_idle_accuracy.getComponent(lang,
            new Placeholders("%accuracy%", String.format(java.util.Locale.ROOT, "%.2f", avgAccuracy))));

        lines.add(LangOptions.scoreboard_idle_maxcombo.getComponent(lang,
            new Placeholders("%combo%", String.valueOf(maxCombo))));

        lines.add(LangOptions.scoreboard_idle_map.getComponent(lang,
            new Placeholders("%map%", mapName)));
        if (this.player.hasPermission(PermissionConstants.MODERATE_LEVELS)) {
            long pendingCount = this.plugin.get(LevelsManager.class).getAvailableLevelsSettings().stream()
                .filter(gs -> gs.getModerationStatus() == ModerationStatus.ON_MODERATION)
                .count();

            lines.add(LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&fПроверить уровней: &e" + pendingCount + " &7(/moder)"));
        }

        lines.add(LangOptions.scoreboard_separator.getComponent(lang));

        lines.add(LangOptions.scoreboard_idle_ping.getComponent(lang,
            new Placeholders("%ping%", ru.sortix.parkourbeat.stats.StatsFormat.ping(ping))));

        lines.add(LangOptions.scoreboard_idle_tps.getComponent(lang,
            new Placeholders("%tps%", String.valueOf(tps))));

        lines.add(LangOptions.scoreboard_idle_ip.getComponent(lang));
        lines.add(LangOptions.scoreboard_separator.getComponent(lang));

        this.board.setLines(lines);
    }

    @Override
    public void hide() {
        this.board.hide();
    }
}
