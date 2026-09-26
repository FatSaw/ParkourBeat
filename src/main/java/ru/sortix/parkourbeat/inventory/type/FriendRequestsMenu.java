package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.player.friends.FriendsManager;
import ru.sortix.parkourbeat.player.friends.PlayerFriends;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.stats.StatsFormat;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Входящие и отправленные заявки в друзья.
 */
public class FriendRequestsMenu extends ParkourBeatInventory {
    private static final int[] INCOMING_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] OUTGOING_SLOTS = {28, 29, 30, 31, 32, 33, 34};

    private final @NonNull Player viewer;

    public FriendRequestsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer) {
        super(plugin, 5, lang, Lang.item(lang, "inventory.friendrequests.title"));
        this.viewer = viewer;
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

        this.setItem(2, 1, ItemUtils.create(Material.LIME_STAINED_GLASS_PANE, meta ->
            meta.displayName(Lang.item(this.lang, "inventory.friendrequests.incoming"))), null);
        this.setItem(4, 1, ItemUtils.create(Material.YELLOW_STAINED_GLASS_PANE, meta ->
            meta.displayName(Lang.item(this.lang, "inventory.friendrequests.outgoing"))), null);

        List<UUID> incoming = new ArrayList<>(profile.getIncoming());
        for (int i = 0; i < INCOMING_SLOTS.length && i < incoming.size(); i++) {
            UUID senderId = incoming.get(i);
            String name = friends.getKnownName(senderId);
            this.setItem(INCOMING_SLOTS[i], ItemUtils.modifyMeta(
                StatsFormat.playerHead(senderId, name), meta -> {
                    meta.displayName(Lang.item(this.lang, "inventory.friendrequests.entry.name",
                        "%player%", name));
                    meta.lore(Lang.lore(this.lang, "inventory.friendrequests.entry.lore_incoming"));
                }), event -> {
                if (event.isLeft()) {
                    friends.acceptRequest(this.viewer, senderId);
                } else {
                    friends.denyRequest(this.viewer, senderId);
                    this.viewer.sendMessage(Lang.text(this.lang, "friends.request.denied"));
                }
                this.render();
            });
        }

        List<UUID> outgoing = new ArrayList<>(profile.getOutgoing());
        for (int i = 0; i < OUTGOING_SLOTS.length && i < outgoing.size(); i++) {
            UUID targetId = outgoing.get(i);
            String name = friends.getKnownName(targetId);
            this.setItem(OUTGOING_SLOTS[i], ItemUtils.modifyMeta(
                StatsFormat.playerHead(targetId, name), meta -> {
                    meta.displayName(Lang.item(this.lang, "inventory.friendrequests.entry.name",
                        "%player%", name));
                    meta.lore(Lang.lore(this.lang, "inventory.friendrequests.entry.lore_outgoing"));
                }), event -> {
                friends.cancelRequest(this.viewer, targetId);
                this.viewer.sendMessage(Lang.text(this.lang, "friends.request.cancelled"));
                this.render();
            });
        }

        if (incoming.isEmpty() && outgoing.isEmpty()) {
            this.setItem(22, ItemUtils.create(Material.BARRIER, meta ->
                meta.displayName(Lang.item(this.lang, "inventory.friendrequests.empty"))), null);
        }

        this.setItem(5, 4, ItemUtils.create(Material.NAME_TAG, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.friendrequests.add.name"));
            meta.lore(Lang.lore(this.lang, "inventory.friendrequests.add.lore"));
        }), event -> this.requestNickname());

        this.setItem(5, 5, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
                meta.displayName(Lang.item(this.lang, "inventory.common.back"))),
            event -> new FriendsMenu(this.plugin, this.lang, this.viewer).open(this.viewer));
    }

    private void requestNickname() {
        this.viewer.closeInventory();

        PlayersInputManager input = this.plugin.get(PlayersInputManager.class);
        if (input.isInputRequested(this.viewer)) {
            this.viewer.sendMessage(Lang.text(this.lang, "input.busy"));
            return;
        }

        this.viewer.sendMessage(Lang.text(this.lang, "friends.request.ask"));
        input.requestChatInput(this.viewer, 20 * 30).thenAccept(name -> {
            if (name == null) {
                this.viewer.sendMessage(Lang.text(this.lang, "input.timeout"));
                return;
            }
            if (!this.viewer.isOnline()) return;

            FriendsManager friends = this.friends();
            UUID targetId = friends.findPlayerIdByName(name.trim());
            if (targetId == null) {
                this.viewer.sendMessage(Lang.text(this.lang, "friends.request.notfound",
                    "%player%", name));
                return;
            }

            switch (friends.sendRequest(this.viewer, targetId)) {
                case OK -> this.viewer.sendMessage(Lang.text(this.lang, "friends.request.sent",
                    "%player%", friends.getKnownName(targetId)));
                case SELF -> this.viewer.sendMessage(Lang.text(this.lang, "friends.request.self"));
                case ALREADY_FRIENDS -> this.viewer.sendMessage(
                    Lang.text(this.lang, "friends.request.already_friends"));
                case ALREADY_REQUESTED -> this.viewer.sendMessage(
                    Lang.text(this.lang, "friends.request.already_sent"));
                default -> this.viewer.sendMessage(Lang.text(this.lang, "friends.request.failed"));
            }
        });
    }
}
