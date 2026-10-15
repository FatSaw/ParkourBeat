package ru.sortix.parkourbeat.listeners;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import ru.sortix.parkourbeat.ParkourBeat;

import java.util.ArrayList;
import java.util.List;

// Source: https://github.com/kennytv/ExploitFixes
@RequiredArgsConstructor
public class FixesListener implements Listener {
    private final ParkourBeat plugin;

    private static final List<String> PB_COMMANDS = List.of(
        "convertdata", "create", "delete", "edit", "moderate", "toggle-physics-debug",
        "debugp", "debug-physics", "debugphysics", "play", "parkourbeat", "pb", "levels", "level", "lvl", "lvls",
        "menu", "меню", "pbmenu", "spawn", "lobby", "hub", "status", "statreset", "template", "test",
        "tptoworld", "updatetrack", "backtolerance", "lookangle", "maxlookangle", "look-angle",
        "backtol", "back-tolerance", "offautolook", "autolook", "auto-look", "join", "stat", "stats", "statistic",
        "top", "tops", "lvlstat", "lvlstats", "debugmode", "pbdebug", "bypassprivate"
    );

    @EventHandler
    private void on(AsyncTabCompleteEvent event) {
        if (event.getBuffer().length() > 256) {
            if (event.getSender() instanceof Player) {
                event.setCancelled(true);
                this.plugin.getServer().getScheduler().runTask(this.plugin, () -> ((Player) event.getSender())
                    .banPlayer("Использование стороннего ПО"));
            }
            return;
        }

        String buffer = event.getBuffer();
        if (buffer.startsWith("/") && !buffer.contains(" ")) {
            List<String> filtered = new ArrayList<>();
            for (String completion : event.getCompletions()) {
                if (completion.contains(":")) continue;
                String cmd = completion.startsWith("/") ? completion.substring(1).toLowerCase() : completion.toLowerCase();
                if (PB_COMMANDS.contains(cmd)) {
                    filtered.add(completion);
                }
            }
            event.setCompletions(filtered);
        } else {
            event.getCompletions().removeIf(completion -> completion.contains(":"));
        }
    }
}
