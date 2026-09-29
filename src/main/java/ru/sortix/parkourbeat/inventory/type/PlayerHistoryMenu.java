package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.UIHeads;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.stats.PlayerProfile;
import ru.sortix.parkourbeat.stats.ProfileSummary;
import ru.sortix.parkourbeat.stats.RunResult;
import ru.sortix.parkourbeat.stats.StatsFormat;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ru.sortix.parkourbeat.utils.text.Theme;
import ru.sortix.parkourbeat.utils.text.PbText;

public class PlayerHistoryMenu extends ParkourBeatInventory {
    private static final int[] HISTORY_SLOTS = {
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int CARD_SLOT = 4;
    private static final int LOADING_SLOT = 31;

    private final @NonNull UUID targetId;
    private final @NonNull String targetName;

    public PlayerHistoryMenu(@NonNull ParkourBeat plugin, String lang,
                             @NonNull Player viewer, @NonNull UUID targetId, @NonNull String targetName) {
        super(plugin, 6, lang, Lang.item(lang, "inventory.playerhistory.title",
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

        this.setItem(CARD_SLOT, this.buildAccountCard(summary), null);

        this.setItem(6, 5, ItemUtils.modifyMeta(UIHeads.ARROW_LEFT.clone(), meta ->
                meta.displayName(Lang.item(this.lang, "inventory.common.back"))),
            event -> new PlayerStatisticsMenu(this.plugin, this.lang, viewer, this.targetId, this.targetName).open(viewer));

        this.setItem(LOADING_SLOT, ItemUtils.create(Material.CLOCK, meta ->
            meta.displayName(Lang.item(this.lang, "inventory.playerhistory.loading"))), null);

        this.setItem(6, 9, ItemUtils.create(Material.REDSTONE, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.playerhistory.replays.name"));
            meta.lore(Lang.lore(this.lang, "inventory.playerhistory.replays.lore",
                "%player%", StatsFormat.safeName(this.targetName)));
        }), event -> new PlayerReplaysMenu(this.plugin, this.lang, viewer, this.targetId, this.targetName).open(viewer));

        this.setItem(5, 9, ItemUtils.create(Material.NETHER_STAR, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.records.all.name"));
            meta.lore(Lang.lore(this.lang, "inventory.records.all.lore"));
            meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }), event -> new AllReplaysMenu(this.plugin, this.lang, viewer, false).open(viewer));

        statistics.loadRecentRunsAsync(this.targetId, StatisticsManager.HISTORY_SIZE,
            this::displayHistory);
    }

    private void displayHistory(@NonNull List<RunResult> runs) {
        for (int slot : HISTORY_SLOTS) {
            this.setItem(slot, null, null);
        }

        if (runs.isEmpty()) {
            this.setItem(LOADING_SLOT, ItemUtils.create(Material.BARRIER, meta ->
                meta.displayName(Lang.item(this.lang, "inventory.playerhistory.empty"))), null);
            return;
        }

        ru.sortix.parkourbeat.replay.ReplayManager replays =
            this.plugin.get(ru.sortix.parkourbeat.replay.ReplayManager.class);

        int index = 0;
        for (RunResult run : runs) {
            if (index >= HISTORY_SLOTS.length) break;
            boolean watchable = this.canWatch(run, replays);
            this.setItem(HISTORY_SLOTS[index++], this.buildRunItem(run, watchable),
                watchable ? event -> ru.sortix.parkourbeat.replay.ReplayStarter
                    .start(this.plugin, event.getPlayer(), run) : null);
        }
    }

    @NonNull
    private boolean canWatch(@NonNull RunResult run,
                             @NonNull ru.sortix.parkourbeat.replay.ReplayManager replays) {
        if (!run.isCompleted()) return false;
        if (!replays.hasReplay(run.getRowId())) return false;
        return !this.plugin.get(ru.sortix.parkourbeat.player.PlayerSettingsManager.class)
            .areReplaysHidden(run.getPlayerId());
    }

    @NonNull
    private ItemStack buildRunItem(@NonNull RunResult run, boolean watchable) {
        Material material = run.isCompleted()
            ? Material.LIME_STAINED_GLASS_PANE
            : Material.RED_STAINED_GLASS_PANE;

        GameSettings settings = this.plugin.get(StatisticsManager.class).getLevelSettings(run.getLevelId());
        String levelName = PbText.keepColors(settings != null ? settings.getDisplayNameLegacy(false) : run.getLevelName());

        return ItemUtils.create(material, meta -> {
            meta.displayName(Lang.item(this.lang, settings == null
                    ? "stats.entry.level_deleted"
                    : "stats.entry.level",
                "%level%", levelName));

            List<Component> lore = new ArrayList<>();
            lore.add(Lang.item(this.lang, "stats.entry.headline",
                "%date%", StatsFormat.relativeDateTime(run.getTimestamp()),
                "%progress%", StatsFormat.percentRounded(run.getProgressPercent()),
                "%grade%", run.getGrade().getFormatted(),
                "%accuracy%", StatsFormat.percent(run.getAccuracy())));
            lore.add(Component.empty());
            lore.add(Lang.item(this.lang, "stats.entry.difficulty",
                "%difficulty%", run.getDifficulty().getDisplayName()));
            lore.addAll(Lang.lore(this.lang, "stats.entry.details",
                "%score%", StatsFormat.number(run.getScore()),
                "%rawscore%", StatsFormat.number(run.getRawScore()),
                "%combo%", String.valueOf(run.getMaxCombo()),
                "%c300%", Theme.V_AQUA + run.getCount300(),
                "%c100%", Theme.V_YELLOW + run.getCount100(),
                "%c50%", Theme.V_RED + run.getCount50(),
                "%miss%", String.valueOf(run.getMissCount()),
                "%time%", TimeUtils.formatTimecode(run.getTimeMillis()),
                "%modifiers%", run.getModifiersDisplay(),
                "%multiplier%", String.format(java.util.Locale.ROOT, "%.2f", run.getMultiplier())));
            if (run.isFullCombo()) lore.add(Lang.item(this.lang, "stats.entry.fullcombo"));
            if (watchable) {
                lore.add(Component.empty());
                lore.add(Lang.item(this.lang, "stats.entry.watchreplay"));
            }
            meta.lore(lore);
        });
    }

    @NonNull
    private ItemStack buildAccountCard(@NonNull ProfileSummary summary) {
        return ItemUtils.create(Material.BOOK, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.playerhistory.card.name"));
            meta.lore(Lang.lore(this.lang, "inventory.playerhistory.card.lore",
                "%created%", StatsFormat.date(summary.getFirstJoinAtMillis()),
                "%playtime%", StatsFormat.duration(summary.getPlaytimeMillis()),
                "%ownlevels%", String.valueOf(summary.getOwnLevelsCount()),
                "%levels%", String.valueOf(summary.getCompletedLevelsCount()),
                "%attempts%", StatsFormat.number(summary.getTotalAttempts()),
                "%grades%", PlayerStatisticsMenu.gradesLine(summary)));
        });
    }

    private void drawBorders() {
        ItemStack glass = ItemUtils.create(Material.BLACK_STAINED_GLASS_PANE,
            meta -> meta.displayName(Component.empty()));
        for (int slot = 0; slot < 54; slot++) {
            if (slot == CARD_SLOT) continue;
            if (isHistorySlot(slot)) continue;
            this.setItem(slot, glass, null);
        }
    }

    private static boolean isHistorySlot(int slot) {
        for (int historySlot : HISTORY_SLOTS) {
            if (historySlot == slot) return true;
        }
        return false;
    }
}
