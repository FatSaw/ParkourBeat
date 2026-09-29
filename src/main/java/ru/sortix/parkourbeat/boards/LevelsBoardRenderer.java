package ru.sortix.parkourbeat.boards;

import ru.sortix.parkourbeat.utils.lang.Lang;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import lombok.NonNull;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.type.LevelDetailsMenu;
import ru.sortix.parkourbeat.inventory.type.LevelTopMenu;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.rating.StatisticsManager;

import javax.annotation.Nullable;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class LevelsBoardRenderer extends ListBoardRenderer<GameSettings> {

    private static final String[] SORT_NAMES = {
        "Сначала популярные", "Сначала сложные", "Сначала лёгкие", "Сначала новые"};

    public LevelsBoardRenderer(@NonNull ParkourBeat plugin, @NonNull BoardAssets assets) {
        super(plugin, assets);
    }

    @NonNull
    @Override
    protected String title(@NonNull Player player, @NonNull BoardSession session) {
        return Lang.raw(PlayerLang.of(player), "auto.levels_board_renderer.title.1");
    }

    @Nullable
    @Override
    protected String headerIcon() {
        return "levels";
    }

    @NonNull
    @Override
    protected List<GameSettings> items(@NonNull Player player, @NonNull BoardSession session) {
        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        List<GameSettings> levels = new ArrayList<>();

        for (GameSettings settings : this.plugin.get(LevelsManager.class).getAvailableLevelsSettings()) {
            if (settings.isPublicVisible()) {
                levels.add(settings);
            }
        }

        Comparator<GameSettings> comparator;
        switch (session.getLevelSort()) {
            case 1: // Сначала сложные
                comparator = (a, b) -> {
                    if (a.getDifficulty() == LevelDifficulty.N_A && b.getDifficulty() != LevelDifficulty.N_A) return 1;
                    if (a.getDifficulty() != LevelDifficulty.N_A && b.getDifficulty() == LevelDifficulty.N_A) return -1;

                    int diff = b.getDifficulty().compareTo(a.getDifficulty());
                    if (diff != 0) return diff;

                    int mult = Double.compare(b.getDifficultyMultiplier(), a.getDifficultyMultiplier());
                    if (mult != 0) return mult;

                    return Long.compare(b.getCreatedAtMills(), a.getCreatedAtMills());
                };
                break;
            case 2: // Сначала лёгкие
                comparator = (a, b) -> {
                    // Уровни без сложности всегда в конце: их сложность неизвестна,
                    // и в начале списка "лёгких" им не место.
                    if (a.getDifficulty() == LevelDifficulty.N_A && b.getDifficulty() != LevelDifficulty.N_A) return 1;
                    if (a.getDifficulty() != LevelDifficulty.N_A && b.getDifficulty() == LevelDifficulty.N_A) return -1;

                    int diff = a.getDifficulty().compareTo(b.getDifficulty());
                    if (diff != 0) return diff;

                    int mult = Double.compare(a.getDifficultyMultiplier(), b.getDifficultyMultiplier());
                    if (mult != 0) return mult;

                    return Long.compare(b.getCreatedAtMills(), a.getCreatedAtMills());
                };
                break;
            case 3: // Сначала новые
                comparator = Comparator.comparingLong((GameSettings s) -> s.getCreatedAtMills()).reversed();
                break;
            default: // Сначала популярные: чем больше прохождений, тем выше
                comparator = Comparator.comparingInt(
                        (GameSettings s) -> statistics.getLevelTopSize(s.getUniqueId())).reversed()
                    .thenComparing(Comparator.comparingLong(GameSettings::getCreatedAtMills).reversed());
                break;
        }
        levels.sort(comparator);
        return levels;
    }

    @Override
    protected void drawItem(@NonNull Graphics2D g, @NonNull GameSettings item, int position,
                            int x, int y, int width, int height,
                            boolean hovered, @NonNull Player player, @NonNull BoardSession session
    ) {
        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);

        Font nameFont = this.assets.font(15, true);
        Font smallFont = this.assets.font(12, false);

        int left = x + PAD;
        LevelDifficulty difficulty = statistics.getCurrentDifficulty(item.getUniqueId());
        String iconName = "diff_" + difficulty.name().toLowerCase(Locale.ROOT);

        if (this.assets.icon(iconName, 20) != null) {
            BoardTheme.icon(g, this.assets.icon(iconName, 20), left, y + 6, 20);
        } else {
            BoardTheme.icon(g, this.assets.icon("level", 20), left, y + 6, 20);
        }
        left += 26;

        // ФИКС: подбираем цвета сложности
        Color diffColor = switch (difficulty) {
            case EASY -> new Color(0x55FF55);        // Зеленый
            case HARD -> new Color(0xFFFF55);        // Желтый
            case EXPERT -> new Color(0xFF5555);      // Красный
            case EXPERT_PLUS -> new Color(0xFF55FF); // Розовый/Пурпурный
            default -> new Color(0xAAAAAA);          // Серый (N/A)
        };

        int rightArea = x + width - PAD;
        int playWidth = 88;
        int nameLimit = rightArea - playWidth - 150 - left;

        String name = BoardTheme.plain(item.getDisplayNameLegacy(false));
        BoardTheme.text(g, BoardTheme.clip(g, name, nameFont, Math.max(40, nameLimit)),
            left, y + 15, BoardTheme.TEXT, nameFont);
        BoardTheme.text(g, BoardTheme.clip(g, Lang.raw(PlayerLang.of(player), "auto.levels_board_renderer.draw_item.1") + item.getOwnerName(), smallFont, Math.max(40, nameLimit)),
            left, y + 28, BoardTheme.TEXT_DIM, smallFont);

        int statsRight = rightArea - playWidth - 12;
        BoardTheme.textRight(g, BoardTheme.plain(difficulty.getDisplayName()), statsRight, y + 15,
            diffColor, this.assets.font(13, true));
        BoardTheme.textRight(g, Lang.raw(PlayerLang.of(player), "auto.levels_board_renderer.draw_item.2") + statistics.getLevelTopSize(item.getUniqueId()),
            statsRight, y + 28, BoardTheme.TEXT_DIM, smallFont);

        BoardTheme.pill(g, rightArea - playWidth, y + 5, playWidth, height - 10,
            hovered ? BoardTheme.GREEN : BoardTheme.GREEN_DARK, BoardTheme.BORDER);
        BoardTheme.textCenter(g, Lang.raw(PlayerLang.of(player), "auto.levels_board_renderer.draw_item.3"), rightArea - playWidth / 2, y + height / 2 + 4,
            BoardTheme.TEXT, this.assets.font(13, true));
    }

    @NonNull
    @Override
    protected String sortLabel(@NonNull BoardSession session) {
        return SORT_NAMES[session.getLevelSort() % SORT_NAMES.length];
    }

    @Override
    protected void nextSort(@NonNull BoardSession session) {
        session.setLevelSort((session.getLevelSort() + 1) % SORT_NAMES.length);
    }

    @Nullable
    @Override
    protected String actionLabel() {
        return "Случайный уровень";
    }

    @Override
    protected void onItemClick(@NonNull Player player, @NonNull GameSettings item, boolean right) {
        String lang = PlayerLang.of(player);
        if (right) {
            new LevelTopMenu(this.plugin, lang, item, player).open(player);
        } else {
            new LevelDetailsMenu(this.plugin, lang, item, player).open(player);
        }
    }

    @Override
    protected void onAction(@NonNull Player player, @NonNull BoardSession session) {
        List<GameSettings> levels = this.items(player, session);
        if (levels.isEmpty()) return;
        GameSettings settings = levels.get((int) (Math.random() * levels.size()));
        new LevelDetailsMenu(this.plugin, PlayerLang.of(player), settings, player).open(player);
    }

    @NonNull
    @Override
    protected String emptyText() {
        return "Ни одного доступного уровня";
    }
}
