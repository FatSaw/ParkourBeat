package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.UIHeads;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.stats.RunResult;
import ru.sortix.parkourbeat.stats.StatsFormat;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.TimeUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import ru.sortix.parkourbeat.utils.text.Theme;
import ru.sortix.parkourbeat.utils.text.PbText;

public class LevelTopMenu extends PaginatedMenu<ParkourBeat, RunResult> {
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int HEADER_SLOT = 4;

    private final @NonNull GameSettings settings;
    private final @NonNull Player viewer;

    private List<RunResult> currentTop = new ArrayList<>();

    public LevelTopMenu(@NonNull ParkourBeat plugin, String lang,
                        @NonNull GameSettings settings, @NonNull Player viewer) {
        super(plugin, 6, lang,
            Lang.item(lang, "inventory.leveltop.title",
                "%level%", PbText.keepColors(settings.getDisplayNameLegacy(false))), CONTENT_SLOTS);
        this.settings = settings;
        this.viewer = viewer;
        this.updateAllItems();
    }

    @Override
    @NonNull
    protected Collection<RunResult> getAllItems() {
        this.currentTop = this.plugin.get(StatisticsManager.class).getLevelTop(this.settings.getUniqueId());
        return this.currentTop;
    }

    @Override
    @NonNull
    protected ItemStack createItemDisplay(@NonNull RunResult record) {
        int position = this.currentTop.indexOf(record) + 1;
        return this.buildEntry(record, position, false);
    }

    @NonNull
    private ItemStack buildEntry(@NonNull RunResult record, int position, boolean isViewerPlate) {
        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        ItemStack head = StatsFormat.playerHead(record.getPlayerId(), record.getPlayerName());

        return ItemUtils.modifyMeta(head, meta -> {
            meta.displayName(StatsFormat.text(
                StatsFormat.rankPrefix(position)
                    + " &e" + StatsFormat.percentRounded(record.getProgressPercent())
                    + " &7- &f" + record.getPlayerName()
                    + " &7- " + record.getGrade().getFormatted()
                    + Lang.raw(this.lang, "stats.entry.accuracy",
                    "%accuracy%", StatsFormat.percent(record.getAccuracy()))
                    + (record.isFullCombo() ? " &7[&b&lFC&7]" : "")));

            List<Component> lore = new ArrayList<>();
            if (isViewerPlate) {
                lore.add(Lang.item(this.lang, "inventory.leveltop.yourresult"));
            }
            lore.add(Component.empty());
            lore.addAll(Lang.lore(this.lang, "stats.entry.details",
                "%score%", StatsFormat.number(record.getScore()),
                "%rawscore%", StatsFormat.number(record.getRawScore()),
                "%combo%", String.valueOf(record.getMaxCombo()),
                "%c300%", Theme.V_AQUA + record.getCount300(),
                "%c100%", Theme.V_YELLOW + record.getCount100(),
                "%c50%", Theme.V_RED + record.getCount50(),
                "%miss%", String.valueOf(record.getMissCount()),
                "%time%", TimeUtils.formatTimecode(record.getTimeMillis()),
                "%modifiers%", record.getModifiersDisplay(),
                "%multiplier%", String.format(java.util.Locale.ROOT, "%.2f", record.getMultiplier())));
            if (this.isRanked()) {
                lore.add(Lang.item(this.lang, "stats.entry.pp",
                    "%pp%", StatsFormat.pp(statistics.getRecordPP(record))));
            }
            lore.add(Component.empty());
            lore.add(Lang.item(this.lang, record.isCompleted()
                    ? "stats.entry.completed"
                    : "stats.entry.attempt",
                "%date%", StatsFormat.dateTime(record.getTimestamp())));
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

        this.drawHeader();
        this.drawViewerResult();

        this.setPreviousPageItem(6, 4);
        this.setItem(6, 5, ItemUtils.modifyMeta(UIHeads.ARROW_LEFT.clone(), meta ->
                meta.displayName(Lang.item(this.lang, "inventory.common.back"))),
            event -> new LevelDetailsMenu(this.plugin, this.lang, this.settings, this.viewer).open(this.viewer));
        this.setNextPageItem(6, 6);

        if (this.currentTop.isEmpty()) {
            this.setItem(22, ItemUtils.create(Material.BARRIER, meta -> {
                meta.displayName(Lang.item(this.lang, "inventory.leveltop.empty.name"));
                meta.lore(Lang.lore(this.lang, "inventory.leveltop.empty.lore"));
            }), null);
        }
    }

    private void drawHeader() {
        if (!this.isRanked()) {
            this.setItem(HEADER_SLOT, ItemUtils.create(Material.GRAY_DYE, meta -> {
                meta.displayName(Lang.item(this.lang, "inventory.leveltop.unranked.name"));
                meta.lore(Lang.lore(this.lang, "inventory.leveltop.unranked.lore"));
            }), null);
            return;
        }

        RunResult globalRecord = this.plugin.get(StatisticsManager.class)
            .getGlobalRecord(this.settings.getUniqueId());
        if (globalRecord == null) {
            this.setItem(HEADER_SLOT, ItemUtils.create(Material.FIREWORK_STAR, meta -> {
                meta.displayName(Lang.item(this.lang, "inventory.leveltop.norecord.name"));
                meta.lore(Lang.lore(this.lang, "inventory.leveltop.norecord.lore"));
            }), null);
            return;
        }

        this.setItem(HEADER_SLOT, ItemUtils.create(Material.FIREWORK_STAR, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.leveltop.record.name",
                "%player%", globalRecord.getPlayerName()));
            meta.lore(Lang.lore(this.lang, "inventory.leveltop.record.lore",
                "%score%", StatsFormat.number(globalRecord.getScore()),
                "%accuracy%", StatsFormat.percent(globalRecord.getAccuracy()),
                "%grade%", globalRecord.getGrade().getFormatted(),
                "%combo%", globalRecord.getMaxCombo()
                    + (globalRecord.isFullCombo() ? " &7[&b&lFC&7]" : ""),
                "%time%", TimeUtils.formatTimecode(globalRecord.getTimeMillis()),
                "%modifiers%", globalRecord.getModifiersDisplay(),
                "%date%", StatsFormat.dateTime(globalRecord.getTimestamp())));
        }), null);
    }

    private void drawViewerResult() {
        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        RunResult record = statistics.getRecord(this.viewer.getUniqueId(), this.settings.getUniqueId());

        if (record == null) {
            this.setItem(46, ItemUtils.create(Material.PAPER, meta -> {
                meta.displayName(Lang.item(this.lang, "inventory.leveltop.noresult.name"));
                meta.lore(Lang.lore(this.lang, "inventory.leveltop.noresult.lore"));
            }), null);
            return;
        }

        int position = statistics.getLevelTopPosition(this.settings.getUniqueId(), this.viewer.getUniqueId());
        this.setItem(46, this.buildEntry(record, position, true), null);
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull RunResult record) {
        new PlayerStatisticsMenu(this.plugin, this.lang, event.getPlayer(),
            record.getPlayerId(), record.getPlayerName()).open(event.getPlayer());
    }

    private boolean isRanked() {
        LevelDifficulty difficulty = this.settings.getDifficulty();
        return difficulty != null && difficulty != LevelDifficulty.N_A;
    }

    private static boolean isContentSlot(int slot) {
        for (int contentSlot : CONTENT_SLOTS) {
            if (contentSlot == slot) return true;
        }
        return false;
    }
}
