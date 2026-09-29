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
import ru.sortix.parkourbeat.player.PlayerSettingsManager;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.replay.ReplayManager;
import ru.sortix.parkourbeat.replay.ReplayStarter;
import ru.sortix.parkourbeat.stats.RunResult;
import ru.sortix.parkourbeat.stats.StatsFormat;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ru.sortix.parkourbeat.utils.text.PbText;

public class PlayerReplaysMenu extends ParkourBeatInventory {
    private static final int[] SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };
    private static final int LOADING_SLOT = 22;

    private final @NonNull UUID targetId;
    private final @NonNull String targetName;
    private final Player viewer;

    public PlayerReplaysMenu(@NonNull ParkourBeat plugin, String lang,
                             @NonNull Player viewer, @NonNull UUID targetId, @NonNull String targetName) {
        super(plugin, 5, lang, Lang.item(lang, "inventory.playerreplays.title",
            "%player%", StatsFormat.safeName(targetName)));
        this.targetId = targetId;
        this.targetName = targetName;
        this.viewer = viewer;
        this.render(viewer);
    }

    private void render(@NonNull Player viewer) {
        this.fillBorder();

        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);

        this.setItem(5, 5, ItemUtils.modifyMeta(UIHeads.ARROW_LEFT.clone(), meta ->
                meta.displayName(Lang.item(this.lang, "inventory.common.back"))),
            event -> new PlayerHistoryMenu(this.plugin, this.lang, viewer, this.targetId, this.targetName).open(viewer));

        PlayerSettingsManager settings = this.plugin.get(PlayerSettingsManager.class);
        if (!settings.canWatchReplays(viewer.getUniqueId(), this.targetId)) {
            boolean friendsOnly = settings.getReplayAccess(this.targetId)
                == PlayerSettingsManager.ReplayAccess.FRIENDS;
            this.setItem(LOADING_SLOT, ItemUtils.create(Material.BARRIER, meta -> {
                meta.displayName(Lang.item(this.lang, "inventory.playerreplays.hidden"));
                if (friendsOnly) {
                    meta.lore(Lang.lore(this.lang, "inventory.playerreplays.friendsonly"));
                }
            }), null);
            return;
        }

        this.setItem(LOADING_SLOT, ItemUtils.create(Material.CLOCK, meta ->
            meta.displayName(Lang.item(this.lang, "inventory.replays.loading"))), null);

        statistics.loadRecentRunsAsync(this.targetId,
            StatisticsManager.HISTORY_SIZE, this::display);
    }

    private void display(@NonNull List<RunResult> runs) {
        for (int slot : SLOTS) {
            this.setItem(slot, null, null);
        }

        ReplayManager replays = this.plugin.get(ReplayManager.class);
        PlayerSettingsManager settings = this.plugin.get(PlayerSettingsManager.class);

        List<RunResult> withReplay = new ArrayList<>();
        for (RunResult run : runs) {
            if (run.isCompleted() && replays.hasReplay(run.getRowId())) withReplay.add(run);
        }

        withReplay.sort((a, b) -> {
            boolean pinnedA = settings.isReplayPinned(this.targetId, a.getRowId());
            boolean pinnedB = settings.isReplayPinned(this.targetId, b.getRowId());
            if (pinnedA == pinnedB) return 0;
            return pinnedA ? -1 : 1;
        });

        if (withReplay.isEmpty()) {
            this.setItem(LOADING_SLOT, ItemUtils.create(Material.BARRIER, meta ->
                meta.displayName(Lang.item(this.lang, "inventory.replays.empty"))), null);
            return;
        }

        boolean own = this.viewer != null && this.viewer.getUniqueId().equals(this.targetId);

        int index = 0;
        for (RunResult run : withReplay) {
            if (index >= SLOTS.length) break;
            this.setItem(SLOTS[index++], this.buildItem(run, own), event -> {
                if (own && event.isShift() && !event.isLeft()) {
                    boolean pinned = settings.toggleReplayPin(this.targetId, run.getRowId());
                    settings.save();
                    event.getPlayer().sendMessage(Lang.text(this.lang, pinned
                        ? "inventory.playerreplays.pinned"
                        : "inventory.playerreplays.unpinned"));
                    this.render(event.getPlayer());
                    return;
                }
                ReplayStarter.start(this.plugin, event.getPlayer(), run);
            });
        }
    }

    @NonNull
    private ItemStack buildItem(@NonNull RunResult run, boolean own) {
        boolean pinned = this.plugin.get(PlayerSettingsManager.class)
            .isReplayPinned(this.targetId, run.getRowId());
        GameSettings settings = this.plugin.get(StatisticsManager.class).getLevelSettings(run.getLevelId());
        String levelName = PbText.keepColors(settings != null ? settings.getDisplayNameLegacy(false) : run.getLevelName());

        return ItemUtils.create(Material.LIME_STAINED_GLASS_PANE, meta -> {
            meta.displayName(StatsFormat.text((pinned ? "&e\u2605 &f" : "&f") + levelName));
            if (pinned) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            List<Component> lore = new ArrayList<>();
            lore.add(Lang.item(this.lang, "inventory.replays.entry.headline",
                "%date%", StatsFormat.relativeDateTime(run.getTimestamp()),
                "%grade%", run.getGrade().getFormatted(),
                "%accuracy%", StatsFormat.percent(run.getAccuracy())));
            lore.addAll(Lang.lore(this.lang, "inventory.replays.entry.lore",
                "%score%", StatsFormat.number(run.getScore()),
                "%combo%", String.valueOf(run.getMaxCombo()),
                "%time%", TimeUtils.formatTimecode(run.getTimeMillis())));
            if (run.isFullCombo()) lore.add(Lang.item(this.lang, "stats.entry.fullcombo"));
            lore.add(Component.empty());
            lore.add(Lang.item(this.lang, "stats.entry.watchreplay"));
            if (own) {
                lore.add(Lang.item(this.lang, pinned
                    ? "inventory.playerreplays.unpin_hint"
                    : "inventory.playerreplays.pin_hint"));
            }
            meta.lore(lore);
        });
    }
}
