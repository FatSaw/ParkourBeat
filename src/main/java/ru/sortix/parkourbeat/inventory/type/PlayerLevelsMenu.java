package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.Heads;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.UIHeads;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.stats.PlayerProfile;
import ru.sortix.parkourbeat.stats.RunResult;
import ru.sortix.parkourbeat.stats.StatsFormat;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.TimeUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import ru.sortix.parkourbeat.utils.text.PbText;

public class PlayerLevelsMenu extends PaginatedMenu<ParkourBeat, RunResult> {
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    public enum SortMode {
        DATE("date"),
        SCORE("score"),
        PP("pp");

        private final @NonNull String langKey;

        SortMode(@NonNull String langKey) {
            this.langKey = langKey;
        }

        @NonNull
        public String getDisplay(String locale) {
            return Lang.raw(locale, "inventory.playerlevels.sort." + this.langKey);
        }

        @NonNull
        public SortMode next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    private final @NonNull Player viewer;
    private final @NonNull UUID targetId;
    private final @NonNull String targetName;
    private @NonNull SortMode sortMode = SortMode.DATE;

    public PlayerLevelsMenu(@NonNull ParkourBeat plugin, String lang,
                            @NonNull Player viewer, @NonNull UUID targetId, @NonNull String targetName) {
        super(plugin, 6, lang, Lang.item(lang, "inventory.playerlevels.title",
                "%player%", StatsFormat.safeName(targetName)),
            CONTENT_SLOTS);
        this.viewer = viewer;
        this.targetId = targetId;
        this.targetName = targetName;
        this.updateAllItems();
    }

    @Override
    @NonNull
    protected Collection<RunResult> getAllItems() {
        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        PlayerProfile profile = statistics.getProfile(this.targetId, this.targetName);

        List<RunResult> records = new ArrayList<>(profile.getAllRecords());
        Comparator<RunResult> comparator;
        switch (this.sortMode) {
            case SCORE:
                comparator = Comparator.comparingInt(RunResult::getScore).reversed();
                break;
            case PP:
                comparator = Comparator.comparingDouble(statistics::getRecordPP).reversed();
                break;
            case DATE:
            default:
                comparator = Comparator.comparingLong(RunResult::getTimestamp).reversed();
                break;
        }
        records.sort(comparator);
        return records;
    }

    @Override
    @NonNull
    protected ItemStack createItemDisplay(@NonNull RunResult record) {
        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        GameSettings settings = statistics.getLevelSettings(record.getLevelId());
        LevelDifficulty currentDifficulty = settings == null ? null : settings.getDifficulty();
        boolean deleted = settings == null;

        LevelDifficulty headDifficulty = currentDifficulty != null ? currentDifficulty : record.getDifficulty();
        ItemStack head = Heads.getHeadByTextureData(headDifficulty.getHeadBase64(), true);

        return ItemUtils.modifyMeta(head, meta -> {
            String levelName = PbText.keepColors(settings != null ? settings.getDisplayNameLegacy(false) : record.getLevelName());
            meta.displayName(Lang.item(this.lang, deleted
                    ? "stats.entry.level_deleted"
                    : "stats.entry.level",
                "%level%", levelName));

            List<Component> lore = new ArrayList<>(
                Lang.lore(this.lang, "inventory.playerlevels.entry",
                    "%difficulty%", headDifficulty.getDisplayName(),
                    "%progress%", StatsFormat.percentRounded(record.getProgressPercent())
                        + (record.isCompleted() ? "" : Lang.raw(this.lang, "stats.entry.notcompleted")),
                    "%time%", TimeUtils.formatTimecode(record.getTimeMillis()),
                    "%accuracy%", StatsFormat.percent(record.getAccuracy()),
                    "%grade%", record.getGrade().getFormatted(),
                    "%combo%", String.valueOf(record.getMaxCombo()),
                    "%score%", StatsFormat.number(record.getScore()),
                    "%miss%", record.getMissCount()
                        + (record.isFullCombo() ? " &7[&b&lFC&7]" : ""),
                    "%modifiers%", record.getModifiersDisplay()));

            if (deleted) {
                lore.add(Lang.item(this.lang, "inventory.playerlevels.deleted"));
            } else if (currentDifficulty == LevelDifficulty.N_A) {
                lore.add(Lang.item(this.lang, "inventory.playerlevels.unranked"));
            } else {
                int position = statistics.getLevelTopPosition(record.getLevelId(), record.getPlayerId());
                int size = statistics.getLevelTopSize(record.getLevelId());
                lore.add(Lang.item(this.lang, "inventory.playerlevels.position",
                    "%position%", StatsFormat.position(position),
                    "%total%", String.valueOf(size)));
                lore.add(Lang.item(this.lang, "stats.entry.pp",
                    "%pp%", StatsFormat.pp(statistics.getRecordPP(record))));
            }

            lore.add(Lang.item(this.lang, record.isCompleted()
                    ? "stats.entry.completed"
                    : "stats.entry.attempt",
                "%date%", StatsFormat.dateTime(record.getTimestamp())));

            if (!deleted) {
                lore.add(Component.empty());
                lore.add(Lang.item(this.lang, "inventory.playerlevels.opentop"));
            }
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

        this.setItem(6, 2, ItemUtils.modifyMeta(UIHeads.SORT.clone(), meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.globalstats.sort.name",
                "%sort%", this.sortMode.getDisplay(this.lang)));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            for (SortMode mode : SortMode.values()) {
                lore.add(Lang.item(this.lang, mode == this.sortMode
                        ? "inventory.globalstats.sort.entry_selected"
                        : "inventory.globalstats.sort.entry",
                    "%sort%", mode.getDisplay(this.lang)));
            }
            lore.add(Component.empty());
            lore.add(Lang.item(this.lang, "inventory.common.toggle"));
            meta.lore(lore);
        }), event -> {
            this.sortMode = this.sortMode.next();
            this.updateAllItems();
        });

        this.setPreviousPageItem(6, 4);
        this.setItem(6, 5, ItemUtils.modifyMeta(UIHeads.ARROW_LEFT.clone(), meta ->
                meta.displayName(Lang.item(this.lang, "inventory.common.back"))),
            event -> new PlayerStatisticsMenu(this.plugin, this.lang, this.viewer, this.targetId, this.targetName).open(this.viewer));
        this.setNextPageItem(6, 6);

        if (this.getMaxPageNumber() == 1 && this.isEmptyList()) {
            this.setItem(22, ItemUtils.create(Material.BARRIER, meta ->
                meta.displayName(Lang.item(this.lang, "inventory.playerlevels.empty"))), null);
        }
    }

    private boolean isEmptyList() {
        return this.plugin.get(StatisticsManager.class).getProfile(this.targetId, this.targetName).getAllRecords().isEmpty();
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull RunResult record) {
        GameSettings settings = this.plugin.get(StatisticsManager.class).getLevelSettings(record.getLevelId());
        if (settings == null) return;
        new LevelTopMenu(this.plugin, this.lang, settings, event.getPlayer()).open(event.getPlayer());
    }

    private static boolean isContentSlot(int slot) {
        for (int contentSlot : CONTENT_SLOTS) {
            if (contentSlot == slot) return true;
        }
        return false;
    }
}
