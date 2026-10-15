package ru.sortix.parkourbeat.boards;

import ru.sortix.parkourbeat.utils.lang.Lang;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import lombok.NonNull;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.type.PlayerStatisticsMenu;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.stats.ProfileSummary;
import ru.sortix.parkourbeat.stats.StatsFormat;

import javax.annotation.Nullable;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Locale;

public class TopBoardRenderer extends ListBoardRenderer<ProfileSummary> {

    public TopBoardRenderer(@NonNull ParkourBeat plugin, @NonNull BoardAssets assets) {
        super(plugin, assets);
    }

    @NonNull
    @Override
    protected String title(@NonNull Player player, @NonNull BoardSession session) {
        return Lang.raw(PlayerLang.of(player), "auto.top_board_renderer.title.1");
    }

    @Nullable
    @Override
    protected String headerIcon() {
        return "top";
    }

    @NonNull
    @Override
    protected List<ProfileSummary> items(@NonNull Player player, @NonNull BoardSession session) {
        return this.plugin.get(StatisticsManager.class).getLeaderboard(session.getSortKey());
    }

    @Override
    protected void drawItem(@NonNull Graphics2D g, @NonNull ProfileSummary item, int position,
                            int x, int y, int width, int height,
                            boolean hovered, @NonNull Player player, @NonNull BoardSession session
    ) {
        Font nameFont = this.assets.font(15, true);
        Font smallFont = this.assets.font(12, false);
        Font valueFont = this.assets.font(16, true);

        int left = x + PAD;
        int place = position + 1;
        String medal = place <= 3 ? "medal_" + place : null;
        if (medal != null && this.assets.icon(medal, 20) != null) {
            BoardTheme.icon(g, this.assets.icon(medal, 20), left, y + 6, 20);
        } else {
            Color color = place == 1 ? BoardTheme.ACCENT : place <= 3 ? BoardTheme.TEXT : BoardTheme.TEXT_DIM;
            BoardTheme.textCenter(g, "#" + place, left + 10, y + 21, color, this.assets.font(14, true));
        }
        left += 30;

        boolean self = item.getPlayerId().equals(player.getUniqueId());
        BoardTheme.icon(g, this.assets.icon("player", 20), left, y + 6, 20);
        left += 26;

        int rightArea = x + width - PAD;
        BoardTheme.text(g, BoardTheme.clip(g, item.getPlayerName(), nameFont, width / 3),
            left, y + 15, self ? BoardTheme.ACCENT : BoardTheme.TEXT, nameFont);
        BoardTheme.text(g, Lang.raw(PlayerLang.of(player), "auto.top_board_renderer.draw_item.1") + item.getCompletedLevelsCount()
                + Lang.raw(PlayerLang.of(player), "auto.top_board_renderer.draw_item.2") + StatsFormat.percent(item.getAverageAccuracy()),
            left, y + 28, BoardTheme.TEXT_DIM, smallFont);

        BoardTheme.textRight(g, this.value(item, session), rightArea, y + 17, BoardTheme.ACCENT, valueFont);
        BoardTheme.textRight(g, this.valueName(session), rightArea, y + 29, BoardTheme.TEXT_DIM, smallFont);
    }

    @NonNull
    private String value(@NonNull ProfileSummary summary, @NonNull BoardSession session) {
        switch (session.getSortKey()) {
            case SCORE: return StatsFormat.number(summary.getTotalScore());
            case ACCURACY: return StatsFormat.percent(summary.getAverageAccuracy());
            case LEVELS: return String.valueOf(summary.getCompletedLevelsCount());
            default: return StatsFormat.pp(summary.getPp());
        }
    }

    @NonNull
    /**
     * Экран висит в мире и его видят все сразу, поэтому язык у него один на всех и
     * не зависит от игрока. Меняется здесь - на код секции из lang.yml.
     */
    private static final String BOARD_LOCALE = "russian";

    private String valueName(@NonNull BoardSession session) {
        return ru.sortix.parkourbeat.utils.lang.Lang.raw(BOARD_LOCALE,
            "board.top.value." + session.getSortKey().name().toLowerCase(java.util.Locale.ROOT));
    }

    @NonNull
    @Override
    protected String sortLabel(@NonNull BoardSession session) {
        return BoardTheme.plain(session.getSortKey().getDisplay(BOARD_LOCALE));
    }

    @Override
    protected void nextSort(@NonNull BoardSession session) {
        session.setSortKey(session.getSortKey().next());
    }

    @Nullable
    @Override
    protected String actionLabel() {
        return "Моя статистика";
    }

    @Override
    protected void onItemClick(@NonNull Player player, @NonNull ProfileSummary item, boolean right) {
        new PlayerStatisticsMenu(this.plugin, PlayerLang.of(player),
            player, item.getPlayerId(), item.getPlayerName()).open(player);
    }

    @Override
    protected void onAction(@NonNull Player player, @NonNull BoardSession session) {
        new PlayerStatisticsMenu(this.plugin, PlayerLang.of(player),
            player, player.getUniqueId(), player.getName()).open(player);
    }

    @NonNull
    @Override
    protected String emptyText() {
        return "Ещё никто не прошёл ни одного уровня";
    }
}
