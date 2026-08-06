package ru.sortix.parkourbeat.listeners;

import lombok.NonNull;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class WorldEditGuardListener implements Listener {
    private static final String BYPASS_PERMISSION = "parkourbeat.worldedit.bypass";

    private final @NonNull ParkourBeat plugin;
    private final Map<UUID, Long> lastWarningAt = new HashMap<>();

    public WorldEditGuardListener(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;

        // Легчайший сканер 2-х прогруженных чанков вокруг каждого строителя каждые 2 секунды (0.05 мс)
        this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::scanLoadedChunksForOutsideEdits, 40L, 40L);
    }

    private void scanLoadedChunksForOutsideEdits() {
        ActivityManager activityManager = this.plugin.get(ActivityManager.class);

        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            UserActivity activity = activityManager.getActivity(player);
            if (!(activity instanceof EditActivity editActivity) || editActivity.isTesting()) continue;

            Level level = editActivity.getLevel();
            World world = player.getWorld();
            if (world != level.getWorld()) continue;

            Chunk playerChunk = player.getLocation().getChunk();
            int pX = playerChunk.getX();
            int pZ = playerChunk.getZ();

            boolean foundOutsideBlock = false;

            for (int cx = pX - 2; cx <= pX + 2 && !foundOutsideBlock; cx++) {
                for (int cz = pZ - 2; cz <= pZ + 2 && !foundOutsideBlock; cz++) {
                    if (!world.isChunkLoaded(cx, cz)) continue;
                    Chunk chunk = world.getChunkAt(cx, cz);

                    int minBlockX = cx << 4;
                    int minBlockZ = cz << 4;

                    for (int x = 0; x < 16 && !foundOutsideBlock; x += 4) {
                        for (int z = 0; z < 16 && !foundOutsideBlock; z += 4) {
                            int worldX = minBlockX + x;
                            int worldZ = minBlockZ + z;

                            if (level.isPositionInside(worldX, 64, worldZ)) continue;

                            for (int y = 0; y < 256; y += 8) {
                                Material type = chunk.getBlock(x, y, z).getType();
                                if (!type.isAir() && type != Material.STRUCTURE_VOID) {
                                    foundOutsideBlock = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (foundOutsideBlock) {
                this.notifyOutsideWorldEdit(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private void on(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS_PERMISSION)) return;

        if (isWorldEditCommand(event.getMessage())) {
            UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(player);
            if (activity instanceof EditActivity editActivity && !editActivity.isTesting()) {
                Location loc = player.getLocation();
                if (!editActivity.getLevel().isLocationInside(loc)) {
                    this.notifyOutsideWorldEdit(player);
                }
            }
        }

        if (!isGuardedCommand(event.getMessage())) return;

        if (!this.mayUseBuilderCommands(player)) {
            event.setCancelled(true);
            this.plugin.getServer().getScheduler().runTask(this.plugin, () ->
                LangOptions.worldedit_guard_denied.sendMsg(player));
        }
    }

    public void notifyOutsideWorldEdit(Player player) {
        long now = System.currentTimeMillis();
        Long last = this.lastWarningAt.get(player.getUniqueId());
        if (last != null && now - last < 3000L) return;
        this.lastWarningAt.put(player.getUniqueId(), now);

        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            player.sendMessage(Component.text(
                "Вы пытались поставить блоки с помощью WorldEdit за границей уровня. После перезахода они автоматически пропадут. Пишите //undo."
            ).color(NamedTextColor.RED));

            World world = player.getWorld();
            for (int i = 0; i < 3; i++) {
                this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                    if (player.isOnline()) {
                        world.strikeLightningEffect(player.getLocation());
                    }
                }, i * 3L);
            }
        });
    }

    private boolean mayUseBuilderCommands(@NonNull Player player) {
        UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(player);
        if (!(activity instanceof EditActivity editActivity)) return false;
        if (editActivity.isTesting()) return false;
        return player.getWorld() == editActivity.getLevel().getWorld();
    }

    private static boolean isGuardedCommand(@NonNull String rawMessage) {
        if (isWorldEditCommand(rawMessage)) return true;
        return isGiveCommand(rawMessage);
    }

    private static boolean isGiveCommand(@NonNull String rawMessage) {
        String message = rawMessage.toLowerCase(Locale.ROOT).trim();
        if (!message.startsWith("/")) return false;
        String withoutSlash = message.substring(1);

        int space = withoutSlash.indexOf(' ');
        String firstToken = space == -1 ? withoutSlash : withoutSlash.substring(0, space);

        int colon = firstToken.indexOf(':');
        if (colon != -1) firstToken = firstToken.substring(colon + 1);
        return firstToken.equals("give") || firstToken.equals("egive");
    }

    private static boolean isWorldEditCommand(@NonNull String rawMessage) {
        String message = rawMessage.toLowerCase(Locale.ROOT).trim();

        if (message.startsWith("//")) return true;
        if (!message.startsWith("/")) return false;
        String withoutSlash = message.substring(1);

        int space = withoutSlash.indexOf(' ');
        String firstToken = space == -1 ? withoutSlash : withoutSlash.substring(0, space);

        int colon = firstToken.indexOf(':');
        if (colon != -1) firstToken = firstToken.substring(colon + 1);

        return WORLDEDIT_COMMANDS.contains(firstToken);
    }

    private static final java.util.Set<String> WORLDEDIT_COMMANDS = java.util.Set.of(
        "worldedit", "we", "wand", "toggleeditwand", "sel", "desel", "pos1", "pos2", "hpos1", "hpos2",
        "chunk", "expand", "contract", "outset", "inset", "shift", "count", "distr", "size",
        "set", "replace", "overlay", "walls", "faces", "center", "line", "curve", "stack", "move",
        "smooth", "regen", "hollow", "forest", "flora", "deform",
        "copy", "cut", "paste", "rotate", "flip", "clearclipboard", "schematic", "schem",
        "undo", "redo", "clearhistory",
        "sphere", "hsphere", "cyl", "hcyl", "pyramid", "hpyramid", "generate", "gen",
        "drain", "fill", "fillr", "fixlava", "fixwater", "removeabove", "removebelow", "removenear",
        "replacenear", "snow", "thaw", "green", "ex", "extinguish", "butcher", "remove",
        "brush", "br", "mask", "gmask", "material", "mat", "range", "none", "naturalize",
        "up", "ascend", "descend", "ceil", "thru", "jumpto", "unstuck",
        "limit", "timeout", "fast", "perf", "update", "world", "watchdog"
    );
}
