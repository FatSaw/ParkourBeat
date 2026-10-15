// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/commands/CommandSpawnTools.java
package ru.sortix.parkourbeat.commands;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.world.SpawnToolsManager;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.Map;

@Command(name = "spawntools")
@Permission("parkourbeat.command.spawntools")
public class CommandSpawnTools {

    private final ParkourBeat plugin;

    public CommandSpawnTools(ParkourBeat plugin) {
        this.plugin = plugin;
    }

    @Execute
    public void onHelp(@Context Player player) {
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_help.1")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_help.2")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_help.3")));
        player.sendMessage(PbText.of("&e/spawntools remove <x> <y> <z>"));
        player.sendMessage(PbText.of("&e/spawntools list"));
        player.sendMessage(PbText.of(""));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_help.4")));
        player.sendMessage(PbText.of("&e/spawntools parkour-start <id>"));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_help.5")));
        player.sendMessage(PbText.of(""));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_help.6")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_help.7")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_help.8")));
        player.sendMessage(PbText.of(""));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_help.9")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_help.10")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_help.11")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_help.12")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_help.13")));
    }

    @Execute(name = "push")
    public void onPush(@Context Player player,
                       @Arg double strength,
                       @Arg double speed,
                       @Arg String particleData,
                       @Arg String xStr,
                       @Arg String yStr,
                       @Arg String zStr) {

        SpawnToolsManager manager = plugin.get(SpawnToolsManager.class);
        Particle particle;
        int amount;
        try {
            String[] parts = particleData.split("\\.");
            particle = Particle.valueOf(parts[0].toUpperCase());
            amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
        } catch (Exception e) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_push.1")));
            return;
        }

        Location loc = parseLoc(player, xStr, yStr, zStr);
        manager.addPad(loc, strength, speed, particle, amount);
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_push.2") + formatLoc(loc)));
    }

    @Execute(name = "remove")
    public void onRemove(@Context Player player, @Arg String xStr, @Arg String yStr, @Arg String zStr) {
        SpawnToolsManager manager = plugin.get(SpawnToolsManager.class);
        Location loc = parseLoc(player, xStr, yStr, zStr);
        if (manager.removePad(loc)) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_remove.1")));
        } else {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_remove.2")));
        }
    }

    @Execute(name = "list")
    public void onList(@Context Player player) {
        SpawnToolsManager manager = plugin.get(SpawnToolsManager.class);
        Map<String, SpawnToolsManager.LaunchPad> pads = manager.getAllPads();
        if (pads.isEmpty()) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_list.1")));
            return;
        }
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_list.2") + pads.size() + "&a):"));
        for (Map.Entry<String, SpawnToolsManager.LaunchPad> entry : pads.entrySet()) {
            player.sendMessage(PbText.of("&8- &f" + entry.getKey().replace("_", " ")
                + Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_list.3") + entry.getValue().strength()));
        }
    }

    @Execute(name = "parkour-start")
    public void onParkourStart(@Context Player player, @Arg String id) {
        plugin.get(SpawnToolsManager.class).setParkourStart(id, player.getLocation().getBlock().getLocation());
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_parkour_start.1") + id + Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_parkour_start.2")));
    }

    @Execute(name = "parkour-finish")
    public void onParkourFinish(@Context Player player, @Arg String id, @Arg String particleData) {
        Particle particle;
        int amount;
        try {
            String[] parts = particleData.split("\\.");
            particle = Particle.valueOf(parts[0].toUpperCase());
            amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 15;
        } catch (Exception e) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_parkour_finish.1")));
            return;
        }

        plugin.get(SpawnToolsManager.class).setParkourFinish(id, player.getLocation().getBlock().getLocation(), particle, amount);
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_parkour_finish.2") + id + Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_parkour_finish.3")));
    }

    @Execute(name = "move-a")
    public void onMoveA(@Context Player player, @Arg String id) {
        plugin.get(SpawnToolsManager.class).setMoveBlockA(id, player.getLocation().getBlock().getLocation());
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_move_a.1") + id + Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_move_a.2")));
    }

    @Execute(name = "move-b")
    public void onMoveB(@Context Player player, @Arg String id, @Arg double speed, @Arg String particleData) {
        Particle particle;
        int amount;
        try {
            String[] parts = particleData.split("\\.");
            particle = Particle.valueOf(parts[0].toUpperCase());
            amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
        } catch (Exception e) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_move_b.1")));
            return;
        }

        plugin.get(SpawnToolsManager.class).setMoveBlockB(id, player.getLocation().getBlock().getLocation(), speed, particle, amount);
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_move_b.2") + id + Lang.raw(PlayerLang.of(player), "auto.command_spawn_tools.on_move_b.3") + speed + ")"));
    }

    @Execute(name = "npc")
    public void onNpc(@Context Player player, @Arg String[] args) {
        plugin.get(SpawnToolsManager.class).handleNpcCommand(player, args);
    }

    private Location parseLoc(Player player, String xStr, String yStr, String zStr) {
        return new Location(player.getWorld(),
            parseCoord(xStr, player.getLocation().getX()),
            parseCoord(yStr, player.getLocation().getY()),
            parseCoord(zStr, player.getLocation().getZ())
        );
    }

    private double parseCoord(String arg, double current) {
        if (arg.startsWith("~")) {
            if (arg.length() == 1) return current;
            try { return current + Double.parseDouble(arg.substring(1)); }
            catch (NumberFormatException e) { return current; }
        }
        try { return Double.parseDouble(arg); }
        catch (NumberFormatException e) { return current; }
    }

    private String formatLoc(Location loc) {
        return loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
    }
}
