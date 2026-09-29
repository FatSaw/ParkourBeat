package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
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
import ru.sortix.parkourbeat.utils.lang.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerStatisticsMenu extends ParkourBeatInventory {
    private static final String CREEPER_HEAD =
        "621668ef7cb79dd9c22ce3d1f3f4cb6e2559893b6df4a469514e667c16aa4";
    private static final String ZOMBIE_HEAD =
        "56fc854bb84cf4b7697297973e02b79bc10698460b51a639c60e5e417734e11";

    private final @NonNull UUID targetId;
    private final @NonNull String targetName;

    public PlayerStatisticsMenu(@NonNull ParkourBeat plugin, String lang,
                                @NonNull Player viewer, @NonNull UUID targetId, @NonNull String targetName) {
        super(plugin, 3, lang, Lang.item(lang, "inventory.playerstats.title",
            "%player%", StatsFormat.safeName(targetName)));
        this.targetId = targetId;
        this.targetName = targetName;
        this.render(viewer);
    }

    private void render(@NonNull Player viewer) {
        this.drawBorders();

        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        PlayerProfile profile = statistics.getProfile(this.targetId, this.targetName);
        ProfileSummary summary = statistics.summarize(profile);
        int position = statistics.getDisplayRank(profile.getPlayerId());

        this.setItem(4, this.buildSummaryHead(summary, position), null);

        this.setItem(11, ItemUtils.modifyMeta(Heads.getHeadByHash(CREEPER_HEAD), meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.playerstats.levels.name"));
            meta.lore(Lang.lore(this.lang, "inventory.playerstats.levels.lore",
                "%count%", String.valueOf(profile.getAllRecords().size())));
        }), event -> new PlayerLevelsMenu(this.plugin, this.lang, viewer, this.targetId, this.targetName).open(viewer));

        this.setItem(15, ItemUtils.modifyMeta(Heads.getHeadByHash(ZOMBIE_HEAD), meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.playerstats.history.name"));
            meta.lore(Lang.lore(this.lang, "inventory.playerstats.history.lore"));
        }), event -> new PlayerHistoryMenu(this.plugin, this.lang, viewer, this.targetId, this.targetName).open(viewer));

        this.setItem(22, ItemUtils.modifyMeta(UIHeads.ARROW_LEFT.clone(), meta ->
                meta.displayName(Lang.item(this.lang, "inventory.common.back"))),
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

            meta.lore(Lang.lore(this.lang, "inventory.playerstats.summary",
                "%pp%", StatsFormat.pp(summary.getPp()),
                "%combo%", String.valueOf(summary.getMaxCombo()),
                "%score%", StatsFormat.number(summary.getTotalScore()),
                "%accuracy%", StatsFormat.percent(summary.getAverageAccuracy()),
                "%hardest%", summary.getHardestDifficultyDisplay(),
                "%levels%", String.valueOf(summary.getCompletedLevelsCount()),
                "%attempts%", StatsFormat.number(summary.getTotalAttempts()),
                "%grades%", gradesLine(summary)));
        });
    }

    @NonNull
    static String gradesLine(@NonNull ProfileSummary summary) {
        StringBuilder builder = new StringBuilder();
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
