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
import ru.sortix.parkourbeat.player.friends.FriendEntry;
import ru.sortix.parkourbeat.player.friends.FriendsManager;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.replay.ReplayManager;
import ru.sortix.parkourbeat.replay.ReplayStarter;
import ru.sortix.parkourbeat.stats.RunResult;
import ru.sortix.parkourbeat.stats.StatsFormat;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Лучшие реплеи сервера.
 * <p>
 * Показывает не «свежие», а именно лучшие забеги: очки, точность, свежесть - в этом
 * порядке. Записи игроков, закрывших свои реплеи, сюда не попадают: настройка приватности
 * должна работать и в общем списке, иначе смысла в ней нет.
 */
public class AllReplaysMenu extends ParkourBeatInventory {
    private static final int[] SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };
    private static final int LOADING_SLOT = 22;
    /** Берём с запасом: часть забегов отсеется приватностью и отсутствием записи. */
    private static final int QUERY_LIMIT = 300;

    private final @NonNull Player viewer;
    private final boolean friendsOnly;

    public AllReplaysMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer,
                          boolean friendsOnly) {
        super(plugin, 5, lang, Lang.item(lang, friendsOnly
            ? "inventory.allreplays.title_friends"
            : "inventory.allreplays.title"));
        this.viewer = viewer;
        this.friendsOnly = friendsOnly;
        this.render();
    }

    private void render() {
        this.fillBorder();

        this.setItem(5, 5, ItemUtils.modifyMeta(UIHeads.ARROW_LEFT.clone(), meta ->
                meta.displayName(Lang.item(this.lang, "inventory.common.back"))),
            event -> new ServerMenu(this.plugin, this.lang, this.viewer).open(this.viewer));

        if (!this.friendsOnly) {
            this.setItem(5, 4, ItemUtils.modifyMeta(
                StatsFormat.playerHead(this.viewer.getUniqueId(), this.viewer.getName()), meta -> {
                    meta.displayName(Lang.item(this.lang, "inventory.allreplays.friends.name"));
                    meta.lore(Lang.lore(this.lang, "inventory.allreplays.friends.lore"));
                }), event -> new AllReplaysMenu(this.plugin, this.lang, this.viewer, true)
                .open(this.viewer));
        } else {
            this.setItem(5, 4, ItemUtils.create(Material.NETHER_STAR, meta -> {
                meta.displayName(Lang.item(this.lang, "inventory.allreplays.all.name"));
                meta.lore(Lang.lore(this.lang, "inventory.allreplays.all.lore"));
            }), event -> new AllReplaysMenu(this.plugin, this.lang, this.viewer, false)
                .open(this.viewer));
        }

        this.setItem(LOADING_SLOT, ItemUtils.create(Material.CLOCK, meta ->
            meta.displayName(Lang.item(this.lang, "inventory.replays.loading"))), null);

        this.plugin.get(StatisticsManager.class).loadBestRunsAsync(QUERY_LIMIT, this::display);
    }

    private void display(@NonNull List<RunResult> runs) {
        for (int slot : SLOTS) {
            this.setItem(slot, null, null);
        }

        ReplayManager replays = this.plugin.get(ReplayManager.class);
        PlayerSettingsManager settings = this.plugin.get(PlayerSettingsManager.class);

        Set<UUID> friendIds = new HashSet<>();
        if (this.friendsOnly) {
            for (FriendEntry entry : this.plugin.get(FriendsManager.class)
                .getFriends(this.viewer.getUniqueId())) {
                friendIds.add(entry.getPlayerId());
            }
        }

        List<RunResult> visible = new ArrayList<>();
        for (RunResult run : runs) {
            if (visible.size() >= SLOTS.length) break;
            if (!replays.hasReplay(run.getRowId())) continue;
            if (this.friendsOnly && !friendIds.contains(run.getPlayerId())) continue;
            if (!settings.canWatchReplays(this.viewer.getUniqueId(), run.getPlayerId())) continue;
            visible.add(run);
        }

        if (visible.isEmpty()) {
            this.setItem(LOADING_SLOT, ItemUtils.create(Material.BARRIER, meta ->
                meta.displayName(Lang.item(this.lang, this.friendsOnly
                    ? "inventory.allreplays.empty_friends"
                    : "inventory.replays.empty"))), null);
            return;
        }

        int index = 0;
        for (RunResult run : visible) {
            int place = index + 1;
            this.setItem(SLOTS[index++], this.buildItem(run, place),
                event -> ReplayStarter.start(this.plugin, event.getPlayer(), run));
        }
    }

    @NonNull
    private ItemStack buildItem(@NonNull RunResult run, int place) {
        GameSettings settings = this.plugin.get(StatisticsManager.class)
            .getLevelSettings(run.getLevelId());
        String levelName = PbText.keepColors(settings != null
            ? settings.getDisplayNameLegacy(false) : run.getLevelName());

        return ItemUtils.modifyMeta(
            StatsFormat.playerHead(run.getPlayerId(), run.getPlayerName()), meta -> {
                meta.displayName(StatsFormat.text(medal(place) + " &f" + run.getPlayerName()));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.addAll(Lang.lore(this.lang, "inventory.allreplays.entry",
                    "%level%", levelName,
                    "%difficulty%", run.getDifficulty().getDisplayName(),
                    "%score%", StatsFormat.number(run.getScore()),
                    "%grade%", run.getGrade().getFormatted(),
                    "%accuracy%", StatsFormat.percent(run.getAccuracy()),
                    "%combo%", String.valueOf(run.getMaxCombo()),
                    "%time%", TimeUtils.formatTimecode(run.getTimeMillis())));
                if (run.isFullCombo()) lore.add(Lang.item(this.lang, "stats.entry.fullcombo"));
                lore.add(Lang.item(this.lang, "inventory.allreplays.entry_date",
                    "%date%", StatsFormat.relativeDateTime(run.getTimestamp())));
                lore.add(Component.empty());
                lore.add(Lang.item(this.lang, "stats.entry.watchreplay"));
                meta.lore(lore);

                // Тройку призёров подсвечиваем, как закреплённые записи в профиле.
                if (place <= 3) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                }
            });
    }

    @NonNull
    private static String medal(int place) {
        return switch (place) {
            case 1 -> "&6&l#1";
            case 2 -> "&7&l#2";
            case 3 -> "&c&l#3";
            default -> "&8#" + place;
        };
    }
}
