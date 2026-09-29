package ru.sortix.parkourbeat.commands;

import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.type.FriendsMenu;
import ru.sortix.parkourbeat.player.friends.FriendEntry;
import ru.sortix.parkourbeat.player.friends.FriendsManager;
import ru.sortix.parkourbeat.player.friends.PlayerFriends;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.UUID;

import static ru.sortix.parkourbeat.constant.PermissionConstants.COMMAND_PERMISSION;

/**
 * /friend - вся работа с друзьями из чата. Меню открывается по /friends или из меню сервера.
 */
@Command(name = "friend", aliases = {"friends", "f", "др"})
@RequiredArgsConstructor
public class CommandFriend {

    private final ParkourBeat plugin;

    @Execute
    @Permission(COMMAND_PERMISSION + "play")
    public void onMenu(@Context Player sender) {
        new FriendsMenu(this.plugin, PlayerLang.of(sender), sender).open(sender);
    }

    @Execute(name = "help")
    @Permission(COMMAND_PERMISSION + "play")
    public void onHelp(@Context Player sender) {
        sender.sendMessage(Component.empty());
        for (net.kyori.adventure.text.Component line
            : Lang.lore(sender, "command.friend.help")) {
            sender.sendMessage(line);
        }
        sender.sendMessage(Component.empty());
    }

    @Execute(name = "add")
    @Permission(COMMAND_PERMISSION + "play")
    public void onAdd(@Context Player sender, @Arg String targetName) {
        FriendsManager friends = this.friends();
        UUID targetId = friends.findPlayerIdByName(targetName);
        if (targetId == null) {
            sender.sendMessage(Lang.text(sender, "friends.request.notfound", "%player%", targetName));
            return;
        }

        switch (friends.sendRequest(sender, targetId)) {
            case OK -> {
                if (friends.areFriends(sender.getUniqueId(), targetId)) {
                    sender.sendMessage(Lang.text(sender, "command.friend.now_friends",
                        "%player%", friends.getKnownName(targetId)));
                } else {
                    sender.sendMessage(Lang.text(sender, "friends.request.sent",
                        "%player%", friends.getKnownName(targetId)));
                }
            }
            case SELF -> sender.sendMessage(Lang.text(sender, "friends.request.self"));
            case ALREADY_FRIENDS -> sender.sendMessage(Lang.text(sender, "friends.request.already_friends"));
            case ALREADY_REQUESTED -> sender.sendMessage(Lang.text(sender, "friends.request.already_sent"));
            case LIMIT_REACHED -> sender.sendMessage(Lang.text(sender, "command.friend.limit",
                "%limit%", String.valueOf(FriendsManager.MAX_FRIENDS)));
            case TARGET_LIMIT_REACHED -> sender.sendMessage(Lang.text(sender, "command.friend.target_limit"));
            default -> sender.sendMessage(Lang.text(sender, "friends.request.failed"));
        }
    }

    @Execute(name = "accept")
    @Permission(COMMAND_PERMISSION + "play")
    public void onAccept(@Context Player sender, @Arg String targetName) {
        FriendsManager friends = this.friends();
        UUID targetId = friends.findPlayerIdByName(targetName);
        if (targetId == null) {
            sender.sendMessage(Lang.text(sender, "friends.request.notfound", "%player%", targetName));
            return;
        }

        switch (friends.acceptRequest(sender, targetId)) {
            case OK -> {
            }
            case NO_REQUEST -> sender.sendMessage(Lang.text(sender, "command.friend.no_request"));
            case ALREADY_FRIENDS -> sender.sendMessage(Lang.text(sender, "friends.request.already_friends"));
            case LIMIT_REACHED -> sender.sendMessage(Lang.text(sender, "command.friend.limit_short"));
            default -> sender.sendMessage(Lang.text(sender, "command.friend.accept_failed"));
        }
    }

    @Execute(name = "deny")
    @Permission(COMMAND_PERMISSION + "play")
    public void onDeny(@Context Player sender, @Arg String targetName) {
        FriendsManager friends = this.friends();
        UUID targetId = friends.findPlayerIdByName(targetName);
        if (targetId == null || friends.denyRequest(sender, targetId) != FriendsManager.Result.OK) {
            sender.sendMessage(Lang.text(sender, "command.friend.no_request"));
            return;
        }
        sender.sendMessage(Lang.text(sender, "friends.request.denied"));
    }

    @Execute(name = "cancel")
    @Permission(COMMAND_PERMISSION + "play")
    public void onCancel(@Context Player sender, @Arg String targetName) {
        FriendsManager friends = this.friends();
        UUID targetId = friends.findPlayerIdByName(targetName);
        if (targetId == null || friends.cancelRequest(sender, targetId) != FriendsManager.Result.OK) {
            sender.sendMessage(Lang.text(sender, "command.friend.no_outgoing"));
            return;
        }
        sender.sendMessage(Lang.text(sender, "friends.request.cancelled"));
    }

    @Execute(name = "remove")
    @Permission(COMMAND_PERMISSION + "play")
    public void onRemove(@Context Player sender, @Arg String targetName) {
        FriendsManager friends = this.friends();
        UUID targetId = friends.findPlayerIdByName(targetName);
        if (targetId == null || friends.removeFriend(sender, targetId) != FriendsManager.Result.OK) {
            sender.sendMessage(Lang.text(sender, "command.friend.not_friend"));
            return;
        }
        sender.sendMessage(Lang.text(sender, "inventory.friend.remove.done",
            "%player%", friends.getKnownName(targetId)));
    }

    @Execute(name = "list")
    @Permission(COMMAND_PERMISSION + "play")
    public void onList(@Context Player sender) {
        FriendsManager friends = this.friends();
        PlayerFriends profile = friends.getProfile(sender);

        if (profile.getFriendsCount() == 0) {
            sender.sendMessage(Lang.text(sender, "command.friend.empty"));
            return;
        }

        sender.sendMessage(Component.empty());
        String lang = PlayerLang.of(sender);
        sender.sendMessage(Lang.text(lang, "friends.list.header",
            "%online%", String.valueOf(friends.getOnlineFriendsCount(sender.getUniqueId())),
            "%total%", String.valueOf(profile.getFriendsCount())));
        for (FriendEntry entry : profile.getAllFriends()) {
            sender.sendMessage(Lang.text(lang, "friends.list.entry",
                "%player%", entry.getPlayerName(),
                "%status%", friends.describeStatus(entry.getPlayerId(), lang)));
        }
        sender.sendMessage(Component.empty());
    }

    @Execute(name = "requests")
    @Permission(COMMAND_PERMISSION + "play")
    public void onRequests(@Context Player sender) {
        FriendsManager friends = this.friends();
        PlayerFriends profile = friends.getProfile(sender);

        if (profile.getIncoming().isEmpty() && profile.getOutgoing().isEmpty()) {
            sender.sendMessage(Lang.text(sender, "inventory.friendrequests.empty"));
            return;
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Lang.text(sender, "inventory.friendrequests.title"));
        for (UUID id : profile.getIncoming()) {
            sender.sendMessage(Lang.text(sender, "command.friend.incoming_entry",
                "%player%", friends.getKnownName(id)));
        }
        for (UUID id : profile.getOutgoing()) {
            sender.sendMessage(Lang.text(sender, "command.friend.outgoing_entry",
                "%player%", friends.getKnownName(id)));
        }
        sender.sendMessage(Component.empty());
    }

    private FriendsManager friends() {
        return this.plugin.get(FriendsManager.class);
    }
}
