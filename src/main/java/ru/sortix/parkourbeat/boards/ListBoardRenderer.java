package ru.sortix.parkourbeat.boards;

import lombok.NonNull;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;

import javax.annotation.Nullable;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ListBoardRenderer<T> implements BoardRenderer {

    protected static final int HEADER = 34;
    protected static final int FOOTER = 36;
    protected static final int ROW = 34;
    protected static final int PAD = 10;

    public static final int BTN_PREV = 1000;
    public static final int BTN_NEXT = 1001;
    public static final int BTN_SORT = 1002;
    public static final int BTN_ACTION = 1003;

    protected final @NonNull ParkourBeat plugin;
    protected final @NonNull BoardAssets assets;

    private static final long CACHE_MILLIS = 1500L;

    private final Map<String, Cached> cache = new HashMap<>();

    protected ListBoardRenderer(@NonNull ParkourBeat plugin, @NonNull BoardAssets assets) {
        this.plugin = plugin;
        this.assets = assets;
    }

    private final class Cached {
        private final List<T> items;
        private final long expiresAt;
        private final int stamp;

        private Cached(List<T> items, long expiresAt, int stamp) {
            this.items = items;
            this.expiresAt = expiresAt;
            this.stamp = stamp;
        }
    }

    @NonNull
    protected final List<T> cached(@NonNull Board board, @NonNull Player player, @NonNull BoardSession session) {
        String key = player.getUniqueId() + "/" + board.getId();
        int stamp = session.getLevelSort() * 31 + session.getSortKey().ordinal();
        long now = System.currentTimeMillis();

        Cached entry = this.cache.get(key);
        if (entry != null && entry.expiresAt > now && entry.stamp == stamp) return entry.items;

        List<T> items = this.items(player, session);
        this.cache.put(key, new Cached(items, now + CACHE_MILLIS, stamp));
        return items;
    }

    @NonNull
    protected abstract String title(@NonNull Player player, @NonNull BoardSession session);

    @Nullable
    protected abstract String headerIcon();

    @NonNull
    protected abstract List<T> items(@NonNull Player player, @NonNull BoardSession session);

    protected abstract void drawItem(@NonNull Graphics2D g, @NonNull T item, int position,
                                     int x, int y, int width, int height,
                                     boolean hovered, @NonNull Player player, @NonNull BoardSession session);

    @NonNull
    protected abstract String sortLabel(@NonNull BoardSession session);

    protected abstract void nextSort(@NonNull BoardSession session);

    @Nullable
    protected abstract String actionLabel();

    protected abstract void onItemClick(@NonNull Player player, @NonNull T item, boolean right);

    protected abstract void onAction(@NonNull Player player, @NonNull BoardSession session);

    @NonNull
    protected String emptyText() {
        return "Пока пусто";
    }

    protected int rowsPerPage(@NonNull Board board) {
        return Math.max(1, (board.pixelHeight() - HEADER - FOOTER) / ROW);
    }

    private List<int[]> buttons(@NonNull Board board) {
        int width = board.pixelWidth();
        int height = board.pixelHeight();
        int y = height - FOOTER + 4;
        int h = FOOTER - 10;

        List<int[]> result = new ArrayList<>();
        result.add(new int[]{BTN_PREV, PAD, y, 34, h});
        result.add(new int[]{BTN_NEXT, PAD + 38, y, 34, h});
        result.add(new int[]{BTN_SORT, PAD + 80, y, Math.min(200, width / 3), h});
        if (this.actionLabel() != null) {
            int actionWidth = Math.min(210, width / 3);
            result.add(new int[]{BTN_ACTION, width - PAD - actionWidth, y, actionWidth, h});
        }
        return result;
    }

    @Override
    public void draw(@NonNull Graphics2D g, @NonNull Board board, @NonNull Player player,
                     @NonNull BoardSession session
    ) {
        int width = board.pixelWidth();
        int height = board.pixelHeight();

        BoardTheme.fill(g, 0, 0, width, height, BoardTheme.BACKGROUND);
        BoardTheme.fill(g, 0, 0, width, HEADER, BoardTheme.PANEL);
        BoardTheme.fill(g, 0, HEADER - 2, width, 2, BoardTheme.ACCENT);
        BoardTheme.fill(g, 0, height - FOOTER, width, FOOTER, BoardTheme.PANEL);

        Font titleFont = this.assets.font(18, true);
        Font smallFont = this.assets.font(13, false);
        Font rowFont = this.assets.font(15, false);

        String iconName = this.headerIcon();
        int titleX = PAD;
        if (iconName != null && this.assets.icon(iconName, 20) != null) {
            BoardTheme.icon(g, this.assets.icon(iconName, 20), PAD, 7, 20);
            titleX = PAD + 26;
        }
        BoardTheme.text(g, this.title(player, session), titleX, 23, BoardTheme.ACCENT, titleFont);

        List<T> items = this.cached(board, player, session);
        int perPage = this.rowsPerPage(board);
        int pages = Math.max(1, (items.size() + perPage - 1) / perPage);
        if (session.getPage() >= pages) session.setPage(pages - 1);
        if (session.getPage() < 0) session.setPage(0);

        int from = session.getPage() * perPage;

        if (items.isEmpty()) {
            BoardTheme.textCenter(g, this.emptyText(), width / 2, HEADER + 40, BoardTheme.TEXT_DIM, rowFont);
        }

        for (int i = 0; i < perPage; i++) {
            int index = from + i;
            if (index >= items.size()) break;

            int y = HEADER + i * ROW;
            boolean hovered = session.getHover() == i;
            Color background = hovered ? BoardTheme.ROW_HOVER : (i % 2 == 0 ? BoardTheme.ROW : BoardTheme.ROW_ALT);
            BoardTheme.fill(g, 0, y, width, ROW - 2, background);
            if (hovered) BoardTheme.fill(g, 0, y, 3, ROW - 2, BoardTheme.ACCENT);

            this.drawItem(g, items.get(index), index, 0, y, width, ROW - 2, hovered, player, session);
        }

        this.drawFooter(g, board, session);
        BoardTheme.drawBoardBorder(g, width, height);
    }

    private void drawFooter(@NonNull Graphics2D g, @NonNull Board board, @NonNull BoardSession session) {
        Font font = this.assets.font(14, true);

        for (int[] button : this.buttons(board)) {
            int code = button[0];
            int x = button[1];
            int y = button[2];
            int w = button[3];
            int h = button[4];
            boolean hovered = session.getHover() == code;

            Color fill;
            Color textColor = BoardTheme.TEXT;
            String label;
            switch (code) {
                case BTN_PREV:
                    fill = hovered ? BoardTheme.BORDER : BoardTheme.ROW;
                    label = "<";
                    break;
                case BTN_NEXT:
                    fill = hovered ? BoardTheme.BORDER : BoardTheme.ROW;
                    label = ">";
                    break;
                case BTN_SORT:
                    fill = hovered ? BoardTheme.BORDER : BoardTheme.ROW;
                    label = this.sortLabel(session);
                    break;
                default:
                    fill = hovered ? BoardTheme.GREEN : BoardTheme.GREEN_DARK;
                    textColor = Color.WHITE;
                    label = this.actionLabel();
                    break;
            }

            BoardTheme.pill(g, x, y, w, h, fill, BoardTheme.BORDER);
            BoardTheme.textCenter(g, BoardTheme.clip(g, label == null ? "" : label, font, w - 12),
                x + w / 2, y + h / 2 + 5, textColor, font);
        }
    }

    @Override
    public int hover(@NonNull Board board, @NonNull Player player, @NonNull BoardSession session, int px, int py) {
        for (int[] button : this.buttons(board)) {
            if (px >= button[1] && px < button[1] + button[3] && py >= button[2] && py < button[2] + button[4]) {
                return button[0];
            }
        }
        if (py < HEADER || py >= board.pixelHeight() - FOOTER) return BoardSession.NOTHING;

        int row = (py - HEADER) / ROW;
        if (row < 0 || row >= this.rowsPerPage(board)) return BoardSession.NOTHING;

        int index = session.getPage() * this.rowsPerPage(board) + row;
        return index < this.cached(board, player, session).size() ? row : BoardSession.NOTHING;
    }

    @Override
    public boolean click(@NonNull Board board, @NonNull Player player, @NonNull BoardSession session,
                         int px, int py, boolean right
    ) {
        int code = this.hover(board, player, session, px, py);
        if (code == BoardSession.NOTHING) return false;

        switch (code) {
            case BTN_PREV:
                if (session.getPage() <= 0) return false;
                session.setPage(session.getPage() - 1);
                return true;
            case BTN_NEXT: {
                List<T> items = this.cached(board, player, session);
                int pages = Math.max(1, (items.size() + this.rowsPerPage(board) - 1) / this.rowsPerPage(board));
                if (session.getPage() >= pages - 1) return false;
                session.setPage(session.getPage() + 1);
                return true;
            }
            case BTN_SORT:
                this.nextSort(session);
                session.setPage(0);
                return true;
            case BTN_ACTION:
                this.onAction(player, session);
                return false;
            default:
                break;
        }

        List<T> items = this.cached(board, player, session);
        int index = session.getPage() * this.rowsPerPage(board) + code;
        if (index < 0 || index >= items.size()) return false;
        this.onItemClick(player, items.get(index), right);
        return false;
    }
}
