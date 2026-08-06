package ru.sortix.parkourbeat.worldedit;

import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lets level builders use WorldEdit / FastAsyncWorldEdit, but only on their own level and only
 * while actually building (edit mode, not a test run, not the lobby).
 *
 * <p>Rather than intercept the dozens of WorldEdit commands one by one, access is granted through
 * a temporary {@link PermissionAttachment}: while a builder is editing their own level we attach
 * {@code worldedit.*} plus the WorldEdit/FAWE per-player block-limit permission that matches their
 * role (full for the owner and trusted co-editors, the smaller default for other co-editors), and
 * we remove the attachment the moment they stop building. Every command then works automatically
 * and the block limit is enforced natively by WorldEdit/FAWE, which both read these permission
 * nodes. If WorldEdit is not installed the whole thing is a no-op.
 */
public class WorldEditAccessManager implements PluginManager {
    private final @NonNull ParkourBeat plugin;
    private final boolean worldEditPresent;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public WorldEditAccessManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.worldEditPresent = Bukkit.getPluginManager().getPlugin("WorldEdit") != null
            || Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
        if (!this.worldEditPresent) {
            plugin.getLogger().info("WorldEdit/FAWE not found; builder WorldEdit access disabled.");
        }
    }

    /**
     * Grants scoped builder commands to the player: EssentialsX {@code /give} always, and (if
     * WorldEdit/FAWE is installed) WorldEdit access capped at the given block limit. Safe to call
     * every time the editing context is (re)entered; a previous grant is replaced.
     */
    public void grant(@NonNull Player player, int blockLimit) {
        this.revoke(player);

        PermissionAttachment attachment = player.addAttachment(this.plugin);

        // EssentialsX /give, so builders can pull the blocks they need for the build. item-all
        // allows any item id; the guard listener still confines the command to edit mode. This
        // does not depend on WorldEdit being installed.
        attachment.setPermission("essentials.give", true);
        attachment.setPermission("essentials.give.item-all", true);

        if (this.worldEditPresent && blockLimit > 0) {
            // Command access.
            attachment.setPermission("worldedit.*", true);
            attachment.setPermission("fawe.worldedit.*", true);
            // Per-player block-change limit. Both WorldEdit and FAWE honour a numeric limit node;
            // granting the exact number caps how many blocks a single operation may change.
            attachment.setPermission("worldedit.limit." + blockLimit, true);
            attachment.setPermission("fawe.limit." + blockLimit, true);

            // LuckPerms-friendly specific nodes fallback. 
            // In case LuckPerms improperly unpacks worldedit.* to Bukkit.
            String[] wePerms = {
                "worldedit.wand", "worldedit.wand.toggle", "worldedit.selection.pos", "worldedit.selection.hpos",
                "worldedit.selection.expand", "worldedit.selection.contract", "worldedit.selection.shift",
                "worldedit.selection.outset", "worldedit.selection.inset", "worldedit.selection.size",
                "worldedit.region.set", "worldedit.region.replace", "worldedit.region.walls", "worldedit.region.faces",
                "worldedit.region.smooth", "worldedit.region.stack", "worldedit.region.move", "worldedit.region.center",
                "worldedit.clipboard.copy", "worldedit.clipboard.cut", "worldedit.clipboard.paste",
                "worldedit.clipboard.rotate", "worldedit.clipboard.flip", "worldedit.clipboard.clear",
                "worldedit.history.undo", "worldedit.history.redo", "worldedit.history.clear",
                "worldedit.generation.cylinder", "worldedit.generation.sphere", "worldedit.generation.forest",
                "worldedit.generation.pumpkins", "worldedit.generation.pyramid", "worldedit.brush.options.material",
                "worldedit.brush.sphere", "worldedit.brush.cylinder", "worldedit.brush.clipboard", "worldedit.brush.smooth",
                "worldedit.navigation.jumpto", "worldedit.navigation.thru", "worldedit.navigation.up"
            };
            for (String perm : wePerms) {
                attachment.setPermission(perm, true);
            }
        }

        this.attachments.put(player.getUniqueId(), attachment);
    }

    /** Removes any WorldEdit access previously granted to the player. */
    public void revoke(@NonNull Player player) {
        PermissionAttachment attachment = this.attachments.remove(player.getUniqueId());
        if (attachment == null) return;
        try {
            player.removeAttachment(attachment);
        } catch (IllegalArgumentException ignored) {
            // Attachment already gone (e.g. player relogged); nothing to do.
        }
    }

    @Override
    public void disable() {
        for (Map.Entry<UUID, PermissionAttachment> entry : this.attachments.entrySet()) {
            Player player = this.plugin.getServer().getPlayer(entry.getKey());
            if (player == null) continue;
            try {
                player.removeAttachment(entry.getValue());
            } catch (IllegalArgumentException ignored) {
            }
        }
        this.attachments.clear();
    }
}
