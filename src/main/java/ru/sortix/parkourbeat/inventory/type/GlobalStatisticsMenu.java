package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.UIHeads;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.stats.ProfileSummary;
import ru.sortix.parkourbeat.stats.StatsFormat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GlobalStatisticsMenu extends PaginatedMenu<ParkourBeat, ProfileSummary> {
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int VIEWER_SLOT = 4;

    private final @NonNull Player viewer;
    private @NonNull StatisticsManager.SortKey sortKey = StatisticsManager.SortKey.PP;

    private List<ProfileSummary> currentOrder = new ArrayList<>();

    public GlobalStatisticsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer) {
        super(plugin, 6, lang, StatsFormat.text("&7Статистика игроков"), CONTENT_SLOTS);
        this.viewer = viewer;
        this.updateAllItems();
    }

    @Override
    @NonNull
    protected Collection<ProfileSummary> getAllItems() {
        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);

        for (Player online : Bukkit.getOnlinePlayers()) {
            statistics.getProfile(online.getUniqueId(), online.getName());
        }

        this.currentOrder = statistics.getLeaderboard(this.sortKey);
        return this.currentOrder;
    }

    @Override
    @NonNull
    protected ItemStack createItemDisplay(@NonNull ProfileSummary summary) {
        return buildHead(summary, this.rankOf(summary), false);
    }

    private int rankOf(@NonNull ProfileSummary summary) {
        if (!summary.hasStatistics()) return 0;
        int index = this.currentOrder.indexOf(summary);
        return index >= 0 ? index + 1 : 0;
    }

    @NonNull
    private ItemStack buildHead(@NonNull ProfileSummary summary, int position, boolean isViewerPlate) {
        ItemStack head = StatsFormat.playerHead(summary.getPlayerId(), summary.getPlayerName());
        return ItemUtils.modifyMeta(head, meta -> {
            meta.displayName(StatsFormat.text(
                StatsFormat.rankPrefix(position, summary.hasStatistics())
                    + " &f" + summary.getPlayerName()));

            List<Component> lore = new ArrayList<>();
            if (isViewerPlate) {
                lore.add(StatsFormat.text("&fВаше место в общем зачёте"));
            }
            lore.add(Component.empty());
            if (!summary.hasStatistics()) {
                lore.add(StatsFormat.text("&fПока нет ни одного результата"));
            }
            lore.add(StatsFormat.text("&7PP-рейтинг: &d" + StatsFormat.pp(summary.getPp())));
            lore.add(StatsFormat.text("&7Макс. комбо: &fx" + summary.getMaxCombo()));
            lore.add(StatsFormat.text("&7Очки: &f" + StatsFormat.number(summary.getTotalScore())));
            lore.add(StatsFormat.text("&7Точность: &f" + StatsFormat.percent(summary.getAverageAccuracy())));
            lore.add(StatsFormat.text("&7Сложнейший уровень: " + summary.getHardestDifficultyDisplay()));
            lore.add(StatsFormat.text("&7Пройдено уровней: &f" + summary.getCompletedLevelsCount()));
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&fНажмите для подробностей"));
            meta.lore(lore);
        });
    }

    @Override
    protected void onPageDisplayed() {
        ItemStack glass = ItemUtils.create(Material.BLACK_STAINED_GLASS_PANE,
            meta -> meta.displayName(Component.empty()));
        for (int slot = 0; slot < 54; slot++) {
            if (isContentSlot(slot)) continue;
            this.setItem(slot, glass, null);
        }

        this.drawViewerPlate();

        this.setItem(6, 2, ItemUtils.modifyMeta(UIHeads.SORT.clone(), meta -> {
            meta.displayName(StatsFormat.text("&eСортировка: " + this.sortKey.getDisplay()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            for (StatisticsManager.SortKey key : StatisticsManager.SortKey.values()) {
                lore.add(StatsFormat.text((key == this.sortKey ? "&f▶ " : "&7• ") + key.getDisplay()));
            }
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&fНажмите чтобы переключить"));
            meta.lore(lore);
        }), event -> {
            this.sortKey = this.sortKey.next();
            this.updateAllItems();
        });

        this.setPreviousPageItem(6, 4);
        this.setItem(6, 5, ItemUtils.create(Material.BARRIER, meta ->
            meta.displayName(StatsFormat.text("&cЗакрыть"))), event -> event.getPlayer().closeInventory());
        this.setNextPageItem(6, 6);

        if (this.currentOrder.isEmpty()) {
            this.setItem(22, ItemUtils.create(Material.BARRIER, meta ->
                meta.displayName(StatsFormat.text("&cПока нет игроков"))), null);
        }
    }

    private void drawViewerPlate() {
        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        ProfileSummary viewerSummary = null;
        for (ProfileSummary summary : this.currentOrder) {
            if (summary.getPlayerId().equals(this.viewer.getUniqueId())) {
                viewerSummary = summary;
                break;
            }
        }
        if (viewerSummary == null) {
            viewerSummary = statistics.summarize(
                statistics.getProfile(this.viewer.getUniqueId(), this.viewer.getName()));
        }

        final ProfileSummary finalSummary = viewerSummary;
        this.setItem(VIEWER_SLOT, buildHead(viewerSummary, this.rankOf(viewerSummary), true), event ->
            new PlayerStatisticsMenu(this.plugin, this.lang, this.viewer,
                Bukkit.getOfflinePlayer(finalSummary.getPlayerId())).open(this.viewer));
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull ProfileSummary summary) {
        new PlayerStatisticsMenu(this.plugin, this.lang, event.getPlayer(),
            Bukkit.getOfflinePlayer(summary.getPlayerId())).open(event.getPlayer());
    }

    private static boolean isContentSlot(int slot) {
        for (int contentSlot : CONTENT_SLOTS) {
            if (contentSlot == slot) return true;
        }
        return false;
    }
}
