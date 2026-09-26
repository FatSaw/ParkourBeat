package ru.sortix.parkourbeat.boards;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.rating.StatisticsManager;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class LogoBoardRenderer implements BoardRenderer {

    private static final int BUTTON = 0;

    private final @NonNull ParkourBeat plugin;
    private final @NonNull BoardAssets assets;

    public LogoBoardRenderer(@NonNull ParkourBeat plugin, @NonNull BoardAssets assets) {
        this.plugin = plugin;
        this.assets = assets;
    }

    @Override
    public void draw(@NonNull Graphics2D g, @NonNull Board board, @NonNull Player player,
                     @NonNull BoardSession session) {
        int width = board.pixelWidth();
        int height = board.pixelHeight();

        BoardTheme.fill(g, 0, 0, width, height, BoardTheme.BACKGROUND);
        BufferedImage background = this.assets.icon("background");
        if (background != null) g.drawImage(background, 0, 0, width, height, null);

        BufferedImage logo = this.assets.icon("logo");
        int logoW = 0, logoH = 0;
        int logoX = width / 2;
        int logoY = height / 2;

        if (logo != null) {
            int maxWidth = width - 240;
            int maxHeight = height - 120;
            double scale = Math.min((double) maxWidth / logo.getWidth(), (double) maxHeight / logo.getHeight());
            logoW = (int) (logo.getWidth() * scale);
            logoH = (int) (logo.getHeight() * scale);

            logoX = (width - logoW) / 2;
            logoY = (height - logoH) / 2 - 25;

            BoardTheme.iconOutlined(g, logo, logoX, logoY, logoW, logoH, BoardTheme.ACCENT);
        } else {
            g.setFont(this.assets.font(Math.max(24, width / 10), true));
            logoW = g.getFontMetrics().stringWidth("ParkourBeat");
            logoH = 40;
            logoX = (width - logoW) / 2;
            logoY = (height - logoH) / 2 - 25;
            BoardTheme.textCenterOutlined(g, "ParkourBeat", width / 2, logoY + 30, BoardTheme.TEXT, BoardTheme.ACCENT, g.getFont());
        }

        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);

        int levelsCount = 0;
        for (GameSettings gs : this.plugin.get(LevelsManager.class).getAvailableLevelsSettings()) {
            if (gs.isPublicVisible()) {
                levelsCount++;
            }
        }

        int topCount = statistics.getRankedPlayersCount();
        int onlineCount = Bukkit.getOnlinePlayers().size();

        Font statFont = this.assets.font(20, true);

        int leftCX = logoX / 2;
        int leftCY = (logoY + logoH / 2) - 58;
        drawStat(g, "levels", levelsCount + Lang.raw(PlayerLang.of(player), "auto.logo_board_renderer.draw.1"), leftCX, leftCY, statFont);

        int rightCX = logoX + logoW + (width - (logoX + logoW)) / 2;
        int rightCY = (logoY + logoH / 2) - 58;
        drawStat(g, "top", topCount + Lang.raw(PlayerLang.of(player), "auto.logo_board_renderer.draw.2"), rightCX, rightCY, statFont);

        int bottomCX = width / 2;
        int bottomCY = logoY + logoH + (height - (logoY + logoH)) / 2 - 36;
        drawStat(g, "online", onlineCount + Lang.raw(PlayerLang.of(player), "auto.logo_board_renderer.draw.3"), bottomCX, bottomCY, statFont);

        Font ipFont = this.assets.font(20, false);
        BoardTheme.textCenterOutlined(g, "parkourbeat.com", width / 2, height - 20, BoardTheme.TEXT, BoardTheme.ACCENT, ipFont);

        BoardTheme.drawBoardBorder(g, width, height);
    }

    private void drawStat(Graphics2D g, String iconName, String text, int cx, int cy, Font font) {
        BufferedImage icon = this.assets.icon(iconName, 32);

        if (icon != null) {
            BoardTheme.iconOutlined(g, icon, cx - 16, cy - 24, 32, 32, BoardTheme.ACCENT);
        }
        BoardTheme.textCenterOutlined(g, text, cx, cy + 24, BoardTheme.TEXT, BoardTheme.ACCENT, font);
    }

    @Override
    public int hover(@NonNull Board board, @NonNull Player player, @NonNull BoardSession session, int px, int py) {
        return BUTTON;
    }

    @Override
    public boolean click(@NonNull Board board, @NonNull Player player, @NonNull BoardSession session,
                         int px, int py, boolean right) {
        Bukkit.dispatchCommand(player, right ? "top" : "menu");
        return false;
    }
}
