package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.player.PlayerSettingsManager;
import ru.sortix.parkourbeat.player.friends.FriendAccess;
import ru.sortix.parkourbeat.player.friends.FriendEntry;
import ru.sortix.parkourbeat.player.friends.FriendsManager;
import ru.sortix.parkourbeat.player.friends.PlayerFriends;
import ru.sortix.parkourbeat.stats.StatsFormat;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FriendMenu extends ParkourBeatInventory {
    private final @NonNull Player viewer;
    private final @NonNull UUID friendId;

    public FriendMenu(@NonNull ParkourBeat plugin, String lang,
                      @NonNull Player viewer, @NonNull UUID friendId) {
        super(plugin, 5, lang, Lang.item(lang, "inventory.friend.title"));
        this.viewer = viewer;
        this.friendId = friendId;
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
        FriendEntry entry = profile.getFriend(this.friendId);

        if (entry == null) {
            this.setItem(22, ItemUtils.create(Material.BARRIER, meta ->
                meta.displayName(Lang.item(this.lang, "inventory.friend.gone"))), null);
            this.setItem(5, 5, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
                    meta.displayName(Lang.item(this.lang, "inventory.common.back"))),
                event -> new FriendsMenu(this.plugin, this.lang, this.viewer).open(this.viewer));
            return;
        }

        boolean online = this.plugin.getServer().getPlayer(this.friendId) != null;

        this.setItem(2, 5, ItemUtils.modifyMeta(
            StatsFormat.playerHead(this.friendId, entry.getPlayerName()), meta -> {
                meta.displayName(Lang.item(this.lang, "inventory.friend.head.name",
                    "%player%", entry.getPlayerName()));
                List<Component> lore = new ArrayList<>(
                    Lang.lore(this.lang, "inventory.friend.head.lore",
                        "%status%", friends.describeStatus(this.friendId, this.lang),
                        "%since%", FriendsMenu.formatDate(entry.getFriendsSinceMillis())));
                lore.add(Lang.item(this.lang, online
                    ? "inventory.friend.head.click_online"
                    : "inventory.friend.head.click_offline"));
                meta.lore(lore);
            }), event -> teleportToFriend(this.plugin, this.viewer, this.friendId));

        boolean privatePerFriend = profile.getPrivateLevelsAccess() == FriendAccess.SELECTED;
        this.setItem(3, 3, ItemUtils.create(
            entry.isPrivateAccess() ? Material.LIME_DYE : Material.GRAY_DYE, meta -> {
                boolean effective = profile.getPrivateLevelsAccess()
                    .allows(entry.isPrivateAccess());
                meta.displayName(Lang.item(this.lang, "inventory.friend.private.name"));
                List<Component> lore = new ArrayList<>(
                    Lang.lore(this.lang, "inventory.friend.private.lore",
                        "%state%", allowedText(this.lang, effective),
                        "%flag%", flagText(this.lang, entry.isPrivateAccess())));
                if (!privatePerFriend) {
                    lore.add(Lang.item(this.lang, profile.getPrivateLevelsAccess() == FriendAccess.ALL
                            ? "inventory.friend.commonmode_all"
                            : "inventory.friend.commonmode",
                        "%mode%", profile.getPrivateLevelsAccess().getDisplay(this.lang)));
                }
                lore.add(Component.empty());
                lore.add(Lang.item(this.lang, "inventory.common.toggle"));
                meta.lore(lore);
            }), event -> {
            entry.setPrivateAccess(!entry.isPrivateAccess());
            if (entry.isPrivateAccess() && profile.getPrivateLevelsAccess() == FriendAccess.NONE) {
                profile.setPrivateLevelsAccess(FriendAccess.SELECTED);
                this.viewer.sendMessage(Lang.text(this.lang, "inventory.friend.private.switched"));
            }
            friends.save();
            this.render();
        });

        this.setItem(3, 4, ItemUtils.create(
            entry.isBuildAccess() ? Material.LIME_DYE : Material.GRAY_DYE, meta -> {
                boolean effectiveBuild = profile.getBuildAccess().allows(entry.isBuildAccess());
                meta.displayName(Lang.item(this.lang, "inventory.friend.build.name"));
                List<Component> lore = new ArrayList<>(
                    Lang.lore(this.lang, "inventory.friend.build.lore",
                        "%state%", allowedText(this.lang, effectiveBuild),
                        "%flag%", flagText(this.lang, entry.isBuildAccess())));
                if (profile.getBuildAccess() != FriendAccess.SELECTED) {
                    lore.add(Lang.item(this.lang, profile.getBuildAccess() == FriendAccess.ALL
                            ? "inventory.friend.commonmode_all"
                            : "inventory.friend.commonmode",
                        "%mode%", profile.getBuildAccess().getDisplay(this.lang)));
                }
                lore.add(Component.empty());
                lore.add(Lang.item(this.lang, "inventory.common.toggle"));
                meta.lore(lore);
            }), event -> {
            entry.setBuildAccess(!entry.isBuildAccess());
            if (entry.isBuildAccess() && profile.getBuildAccess() == FriendAccess.NONE) {
                profile.setBuildAccess(FriendAccess.SELECTED);
                this.viewer.sendMessage(Lang.text(this.lang, "inventory.friend.build.switched"));
            }
            friends.save();
            this.render();
        });

        PlayerSettingsManager settings = this.plugin.get(PlayerSettingsManager.class);
        this.setItem(3, 6, ItemUtils.create(
            entry.isTeleportAccess() ? Material.ENDER_PEARL : Material.ENDER_EYE, meta -> {
                meta.displayName(Lang.item(this.lang, "inventory.friend.teleport.name"));
                List<Component> lore = new ArrayList<>(
                    Lang.lore(this.lang, "inventory.friend.teleport.lore",
                        "%state%", allowedText(this.lang, entry.isTeleportAccess()),
                        "%mode%", settings.getTeleportAccess(this.viewer.getUniqueId())
                            .getDisplay(this.lang)));
                lore.add(Component.empty());
                lore.add(Lang.item(this.lang, "inventory.common.toggle"));
                meta.lore(lore);
            }), event -> {
            entry.setTeleportAccess(!entry.isTeleportAccess());
            friends.save();
            this.render();
        });

        this.setItem(3, 7, ItemUtils.create(
            entry.isJoinNotifications() ? Material.LIME_DYE : Material.GRAY_DYE, meta -> {
                meta.displayName(Lang.item(this.lang, "inventory.friend.notifications.name"));
                List<Component> lore = new ArrayList<>(
                    Lang.lore(this.lang, "inventory.friend.notifications.lore"));
                lore.add(Lang.item(this.lang, entry.isJoinNotifications()
                    ? "inventory.friends.notifications.on"
                    : "inventory.friends.notifications.off"));
                meta.lore(lore);
            }), event -> {
            entry.setJoinNotifications(!entry.isJoinNotifications());
            friends.save();
            this.render();
        });

        this.setItem(3, 5, ItemUtils.create(Material.LIME_STAINED_GLASS_PANE, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.friend.replays.name"));
            List<Component> lore = new ArrayList<>(
                Lang.lore(this.lang, "inventory.friend.replays.lore"));
            lore.add(Lang.item(this.lang, this.plugin.get(PlayerSettingsManager.class)
                .canWatchReplays(this.viewer.getUniqueId(), this.friendId)
                ? "inventory.friend.replays.allowed"
                : "inventory.friend.replays.denied"));
            meta.lore(lore);
        }), event -> new PlayerReplaysMenu(this.plugin, this.lang, this.viewer,
            this.friendId, entry.getPlayerName()).open(this.viewer));

        this.setItem(4, 5, ItemUtils.create(Material.BARRIER, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.friend.remove.name"));
            meta.lore(Lang.lore(this.lang, "inventory.friend.remove.lore"));
        }), event -> {
            if (!event.isShift() || !event.isLeft()) {
                event.getPlayer().sendMessage(Lang.text(this.lang, "inventory.friend.remove.confirm"));
                return;
            }
            friends.removeFriend(this.viewer, this.friendId);
            this.viewer.sendMessage(Lang.text(this.lang, "inventory.friend.remove.done",
                "%player%", entry.getPlayerName()));
            new FriendsMenu(this.plugin, this.lang, this.viewer).open(this.viewer);
        });

        this.setItem(5, 5, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
                meta.displayName(Lang.item(this.lang, "inventory.common.back"))),
            event -> new FriendsMenu(this.plugin, this.lang, this.viewer).open(this.viewer));
    }

    @NonNull
    private static String allowedText(String locale, boolean allowed) {
        return Lang.raw(locale, allowed ? "common.allowed" : "common.denied");
    }

    @NonNull
    private static String flagText(String locale, boolean enabled) {
        return Lang.raw(locale, enabled ? "common.on" : "common.off");
    }

    public static void teleportToFriend(@NonNull ParkourBeat plugin, @NonNull Player viewer,
                                        @NonNull UUID friendId) {
        Player friend = plugin.getServer().getPlayer(friendId);
        if (friend == null || !friend.isOnline()) {
            viewer.sendMessage(Lang.text(viewer, "friends.teleport.offline"));
            return;
        }

        if (!plugin.get(PlayerSettingsManager.class)
            .canTeleportTo(viewer.getUniqueId(), friendId)) {
            viewer.sendMessage(Lang.text(viewer, "friends.teleport.denied"));
            return;
        }

        viewer.closeInventory();

        UserActivity activity = plugin.get(ActivityManager.class).getActivity(friend);
        if (activity == null) {
            ru.sortix.parkourbeat.world.TeleportUtils.teleportAsync(plugin, viewer,
                friend.getLocation());
            viewer.sendMessage(Lang.text(viewer, "friends.teleport.done",
                "%player%", friend.getName()));
            return;
        }

        GameSettings settings = activity.getLevel().getLevelSettings().getGameSettings();
        LevelsListMenu.startSpectating(plugin, viewer, settings);
    }
}
