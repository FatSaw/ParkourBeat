package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.UIHeads;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.player.friends.FriendEntry;
import ru.sortix.parkourbeat.player.friends.FriendsManager;
import ru.sortix.parkourbeat.player.friends.PlayerFriends;
import ru.sortix.parkourbeat.stats.StatsFormat;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;

/**
 * Список друзей: кто в сети, чем занят, быстрый переход к другу и общие права.
 */
public class FriendsMenu extends ParkourBeatInventory {
    private static final int[] SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };

    private final @NonNull Player viewer;
    private int page;

    public FriendsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer) {
        this(plugin, lang, viewer, 0);
    }

    public FriendsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer, int page) {
        super(plugin, 6, lang, Lang.item(lang, "inventory.friends.title"));
        this.viewer = viewer;
        this.page = page;
        this.render();
    }

    private FriendsManager friends() {
        return this.plugin.get(FriendsManager.class);
    }

    private void render() {
        this.clearInventory();
        this.fillBorder();

        FriendsManager friends = this.friends();
        PlayerFriends profile = friends.getProfile(this.viewer);

        List<FriendEntry> all = new ArrayList<>(profile.getAllFriends());
        // Онлайн - наверх: список нужен в первую очередь чтобы найти, к кому пойти.
        all.sort((a, b) -> {
            boolean onlineA = this.plugin.getServer().getPlayer(a.getPlayerId()) != null;
            boolean onlineB = this.plugin.getServer().getPlayer(b.getPlayerId()) != null;
            if (onlineA != onlineB) return onlineA ? -1 : 1;
            return a.getPlayerName().compareToIgnoreCase(b.getPlayerName());
        });

        int maxPage = Math.max(0, (all.size() - 1) / SLOTS.length);
        if (this.page > maxPage) this.page = maxPage;
        if (this.page < 0) this.page = 0;

        if (all.isEmpty()) {
            this.setItem(23, ItemUtils.create(Material.BARRIER, meta -> {
                meta.displayName(Lang.item(this.lang, "inventory.friends.empty.name"));
                meta.lore(Lang.lore(this.lang, "inventory.friends.empty.lore"));
            }), null);
        }

        int first = this.page * SLOTS.length;
        for (int i = 0; i < SLOTS.length; i++) {
            int index = first + i;
            if (index >= all.size()) break;
            FriendEntry entry = all.get(index);
            this.setItem(SLOTS[i], this.createFriendItem(entry),
                event -> this.onFriendClick(event.getPlayer(), entry, event.isLeft()));
        }

        if (this.page > 0) {
            this.setItem(6, 3, ItemUtils.modifyMeta(UIHeads.ARROW_LEFT.clone(), meta ->
                meta.displayName(Lang.item(this.lang, "inventory.common.previous"))), event -> {
                this.page--;
                this.render();
            });
        }
        if (this.page < maxPage) {
            this.setItem(6, 7, ItemUtils.modifyMeta(UIHeads.ARROW_RIGHT.clone(), meta ->
                meta.displayName(Lang.item(this.lang, "inventory.common.next"))), event -> {
                this.page++;
                this.render();
            });
        }

        this.renderRequestsItem(profile);
        this.renderAccessItems(profile);

        this.setItem(6, 5, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
                meta.displayName(Lang.item(this.lang, "inventory.common.back"))),
            event -> new ServerMenu(this.plugin, this.lang, this.viewer).open(this.viewer));
    }

    @NonNull
    private ItemStack createFriendItem(@NonNull FriendEntry entry) {
        FriendsManager friends = this.friends();
        String status = friends.describeStatus(entry.getPlayerId(), this.lang);
        boolean online = this.plugin.getServer().getPlayer(entry.getPlayerId()) != null;

        PlayerFriends profile = friends.getProfile(this.viewer);

        return ItemUtils.modifyMeta(
            StatsFormat.playerHead(entry.getPlayerId(), entry.getPlayerName()), meta -> {
                meta.displayName(Lang.item(this.lang, online
                        ? "inventory.friends.entry.name_online"
                        : "inventory.friends.entry.name_offline",
                    "%player%", entry.getPlayerName()));

                List<Component> lore = new ArrayList<>(
                    Lang.lore(this.lang, "inventory.friends.entry.lore",
                        "%status%", status,
                        "%since%", formatDate(entry.getFriendsSinceMillis()),
                        "%private%", yesNo(this.lang,
                            profile.getPrivateLevelsAccess().allows(entry.isPrivateAccess())),
                        "%build%", yesNo(this.lang,
                            profile.getBuildAccess().allows(entry.isBuildAccess()))));
                lore.add(Lang.item(this.lang, online
                    ? "inventory.friends.entry.click_online"
                    : "inventory.friends.entry.click_offline"));
                lore.add(Lang.item(this.lang, "inventory.friends.entry.click_manage"));
                meta.lore(lore);
            });
    }

    private void onFriendClick(@NonNull Player player, @NonNull FriendEntry entry, boolean left) {
        if (!left) {
            new FriendMenu(this.plugin, this.lang, this.viewer, entry.getPlayerId()).open(player);
            return;
        }
        FriendMenu.teleportToFriend(this.plugin, player, entry.getPlayerId());
    }

    private void renderRequestsItem(@NonNull PlayerFriends profile) {
        int incoming = profile.getIncoming().size();
        this.setItem(6, 2, ItemUtils.create(
            incoming > 0 ? Material.WRITABLE_BOOK : Material.BOOK, meta -> {
                meta.displayName(Lang.item(this.lang, "inventory.friends.requests.name"));
                meta.lore(Lang.lore(this.lang, "inventory.friends.requests.lore",
                    "%incoming%", String.valueOf(incoming),
                    "%outgoing%", String.valueOf(profile.getOutgoing().size())));
            }), event -> new FriendRequestsMenu(this.plugin, this.lang, this.viewer).open(this.viewer));
    }

    private void renderAccessItems(@NonNull PlayerFriends profile) {
        this.setItem(6, 4, ItemUtils.create(Material.IRON_DOOR, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.friends.privatelevels.name"));
            meta.lore(Lang.lore(this.lang, "inventory.friends.privatelevels.lore",
                "%mode%", profile.getPrivateLevelsAccess().getDisplay(this.lang),
                "%description%", profile.getPrivateLevelsAccess().getDescription(this.lang)));
        }), event -> {
            profile.setPrivateLevelsAccess(profile.getPrivateLevelsAccess().next());
            this.friends().save();
            this.render();
        });

        this.setItem(6, 6, ItemUtils.create(Material.GOLDEN_PICKAXE, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.friends.build.name"));
            meta.lore(Lang.lore(this.lang, "inventory.friends.build.lore",
                "%mode%", profile.getBuildAccess().getDisplay(this.lang),
                "%description%", profile.getBuildAccess().getDescription(this.lang)));
        }), event -> {
            profile.setBuildAccess(profile.getBuildAccess().next());
            this.friends().save();
            this.render();
        });

        this.setItem(6, 8, ItemUtils.create(
            profile.isJoinNotifications() ? Material.LIME_DYE : Material.GRAY_DYE, meta -> {
                meta.displayName(Lang.item(this.lang, "inventory.friends.notifications.name"));
                List<Component> lore = new ArrayList<>(
                    Lang.lore(this.lang, "inventory.friends.notifications.lore"));
                lore.add(Lang.item(this.lang, profile.isJoinNotifications()
                    ? "inventory.friends.notifications.on"
                    : "inventory.friends.notifications.off"));
                meta.lore(lore);
            }), event -> {
            profile.setJoinNotifications(!profile.isJoinNotifications());
            this.friends().save();
            this.render();
        });
    }

    @NonNull
    static String yesNo(String locale, boolean value) {
        return Lang.raw(locale, value ? "common.yes" : "common.no");
    }

    @NonNull
    static String formatDate(long millis) {
        return new java.text.SimpleDateFormat("dd.MM.yyyy").format(new java.util.Date(millis));
    }

    @NonNull
    public Player getViewer() {
        return this.viewer;
    }
}
