package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.Heads;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.UIHeads;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.rating.AccuracyGrade;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.stats.PlayerProfile;
import ru.sortix.parkourbeat.stats.ProfileSummary;
import ru.sortix.parkourbeat.stats.StatsFormat;

import java.util.ArrayList;
import java.util.List;

public class PlayerStatisticsMenu extends ParkourBeatInventory {
    private static final String CREEPER_HEAD =
        "621668ef7cb79dd9c22ce3d1f3f4cb6e2559893b6df4a469514e667c16aa4";
    private static final String ZOMBIE_HEAD =
        "56fc854bb84cf4b7697297973e02b79bc10698460b51a639c60e5e417734e11";

    private final @NonNull OfflinePlayer target;

    public PlayerStatisticsMenu(@NonNull ParkourBeat plugin, String lang,
                                @NonNull Player viewer, @NonNull OfflinePlayer target) {
        super(plugin, 3, lang, StatsFormat.text("&7Статистика: &f" + StatsFormat.safeName(target.getName())));
        this.target = target;
        this.render(viewer);
    }

    private void render(@NonNull Player viewer) {
        this.drawBorders();

        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        PlayerProfile profile = statistics.getProfile(this.target);
        ProfileSummary summary = statistics.summarize(profile);
        int position = statistics.getDisplayRank(profile.getPlayerId());

        this.setItem(4, this.buildSummaryHead(summary, position), null);

        this.setItem(11, ItemUtils.modifyMeta(Heads.getHeadByHash(CREEPER_HEAD), meta -> {
            meta.displayName(StatsFormat.text("&aУровни"));
            List<Component> lore = new ArrayList<>();
            lore.add(StatsFormat.text("&7Уровни, пройденные игроком"));
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&7Всего записей: &f" + profile.getAllRecords().size()));
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&fНажмите чтобы открыть"));
            meta.lore(lore);
        }), event -> new PlayerLevelsMenu(this.plugin, this.lang, viewer, this.target).open(viewer));

        this.setItem(15, ItemUtils.modifyMeta(Heads.getHeadByHash(ZOMBIE_HEAD), meta -> {
            meta.displayName(StatsFormat.text("&2История"));
            List<Component> lore = new ArrayList<>();
            lore.add(StatsFormat.text("&7Карточка аккаунта и лента"));
            lore.add(StatsFormat.text("&7последних попыток"));
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&fНажмите чтобы открыть"));
            meta.lore(lore);
        }), event -> new PlayerHistoryMenu(this.plugin, this.lang, viewer, this.target).open(viewer));

        this.setItem(22, ItemUtils.modifyMeta(UIHeads.ARROW_LEFT.clone(), meta ->
                meta.displayName(StatsFormat.text("&7Назад"))),
            event -> new GlobalStatisticsMenu(this.plugin, this.lang, viewer).open(viewer));
    }

    @NonNull
    private ItemStack buildSummaryHead(@NonNull ProfileSummary summary, int position) {
        ItemStack head = StatsFormat.playerHead(summary.getPlayerId(), summary.getPlayerName());
        return ItemUtils.modifyMeta(head, meta -> {
            meta.displayName(StatsFormat.text("&f" + summary.getPlayerName()
                + (position > 0
                ? " &7(" + StatsFormat.position(position, summary.hasStatistics()) + "&r&7)"
                : "")));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&7PP-рейтинг: &d" + StatsFormat.pp(summary.getPp())));
            lore.add(StatsFormat.text("&7Макс. комбо: &fx" + summary.getMaxCombo()));
            lore.add(StatsFormat.text("&7Очки: &f" + StatsFormat.number(summary.getTotalScore())));
            lore.add(StatsFormat.text("&7Точность: &f" + StatsFormat.percent(summary.getAverageAccuracy())));
            lore.add(StatsFormat.text("&7Сложнейший уровень: " + summary.getHardestDifficultyDisplay()));
            lore.add(StatsFormat.text("&7Пройдено уровней: &f" + summary.getCompletedLevelsCount()));
            lore.add(StatsFormat.text("&7Всего попыток: &f" + StatsFormat.number(summary.getTotalAttempts())));
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&7Оценки: " + gradesLine(summary)));
            meta.lore(lore);
        });
    }

    @NonNull
    static String gradesLine(@NonNull ProfileSummary summary) {
        StringBuilder builder = new StringBuilder();
        // Оценок стало 7 (добавились B и C) — пустые не показываем,
        // иначе строка лора не влезает.
        for (AccuracyGrade grade : AccuracyGrade.values()) {
            int count = summary.getGradeCount(grade);
            if (count <= 0 && grade != AccuracyGrade.SS && grade != AccuracyGrade.S
                && grade != AccuracyGrade.A) continue;
            if (builder.length() > 0) builder.append(" &7| ");
            builder.append(grade.getFormatted()).append(" &f").append(count);
        }
        return builder.toString();
    }

    private void drawBorders() {
        ItemStack glass = ItemUtils.create(Material.BLACK_STAINED_GLASS_PANE,
            meta -> meta.displayName(Component.empty()));
        for (int i = 0; i < 27; i++) {
            if (i == 4 || i == 11 || i == 15 || i == 22) continue;
            this.setItem(i, glass, null);
        }
    }
}
