package ru.sortix.parkourbeat.player.scoreboard;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.activity.type.PlayActivity;
import ru.sortix.parkourbeat.activity.type.SpectateActivity;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScoreboardManager implements PluginManager, Listener {
    /** Значок «сейчас бежит», который дописывается к нику в табе. */
    private static final String RUNNING_MARK = "\u266B";
    /**
     * Два оттенка, между которыми мерцает значок. RGB поддерживается начиная
     * с 1.16, так что цвета берутся точные, а не ближайшие из палитры §-кодов.
     */
    private static final net.kyori.adventure.text.format.TextColor RUNNING_COLOR_A =
        net.kyori.adventure.text.format.TextColor.color(235, 107, 255);
    private static final net.kyori.adventure.text.format.TextColor RUNNING_COLOR_B =
        net.kyori.adventure.text.format.TextColor.color(232, 149, 245);
    /**
     * Длительность одного кадра мерцания. Таб обновляется раз в 5 тиков (250 мс),
     * поэтому меньшее значение просто не будет видно — быстрее уже некуда.
     */
    private static final long BLINK_FRAME_MILLIS = 250L;

    private final ParkourBeat plugin;
    private final ActivityManager activityManager;
    private final Map<UUID, ParkourBeatScoreboard> scoreboards = new HashMap<>();
    private final BukkitTask task;

    public ScoreboardManager(ParkourBeat plugin) {
        this.plugin = plugin;
        this.activityManager = plugin.get(ActivityManager.class);
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);

        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updatePlayerScoreboard(player);
            }
        }, 5L, 5L);
    }

    private void updatePlayerScoreboard(Player player) {
        UserActivity activity = this.activityManager.getActivity(player);

        boolean shouldBePlay = false;
        Game game = null;
        ru.sortix.parkourbeat.levels.Level level = null;

        if (activity instanceof PlayActivity playActivity) {
            game = playActivity.getGame();
            level = playActivity.getLevel();
            if (game.getCurrentState() == Game.State.RUNNING) {
                shouldBePlay = true;
            }
        } else if (activity instanceof EditActivity editActivity) {
            level = editActivity.getLevel();
            if (editActivity.isTesting()) {
                PlayActivity testActivity = editActivity.getTestingActivity();
                if (testActivity != null) {
                    game = testActivity.getGame();
                    if (game.getCurrentState() == Game.State.RUNNING) {
                        shouldBePlay = true;
                    }
                }
            }
        } else if (activity instanceof SpectateActivity spectateActivity) {
            level = spectateActivity.getLevel();
        }

        this.updatePlayerTabList(player, level, shouldBePlay);

        // Предметы хотбара выдаем на спавне И на уровне ДО начала бега (!shouldBePlay).
        // Во время забега (RUNNING) и в редакторе скрываем их.
        boolean showLobbyItems = !shouldBePlay;
        if (activity instanceof EditActivity) {
            showLobbyItems = false;
        }
        this.plugin.get(ru.sortix.parkourbeat.inventory.LobbyItems.class)
            .sync(player, showLobbyItems);

        ParkourBeatScoreboard current = this.scoreboards.get(player.getUniqueId());

        if (shouldBePlay) {
            if (!(current instanceof PlayScoreboard)) {
                if (current != null) current.hide();
                current = new PlayScoreboard(this.plugin, player, game);
                this.scoreboards.put(player.getUniqueId(), current);
            }
        } else {
            if (!(current instanceof IdleScoreboard)) {
                if (current != null) current.hide();
                current = new IdleScoreboard(this.plugin, player, level);
                this.scoreboards.put(player.getUniqueId(), current);
            } else {
                if (((IdleScoreboard) current).getLevel() != level) {
                    current.hide();
                    current = new IdleScoreboard(this.plugin, player, level);
                    this.scoreboards.put(player.getUniqueId(), current);
                }
            }
        }

        current.update();
    }

    private void updatePlayerTabList(Player player, ru.sortix.parkourbeat.levels.Level level, boolean running) {
        String lang = player.getLocale().toLowerCase();

        String playerRank = this.plugin
            .get(ru.sortix.parkourbeat.rating.StatisticsManager.class)
            .getRankLabel(player.getUniqueId());
        int ping = this.plugin.get(ru.sortix.parkourbeat.player.PingManager.class).getPing(player);
        double tps = Math.round(Bukkit.getServer().getTPS()[0] * 10.0) / 10.0;

        String mapName = level == null
            ? LangOptions.scoreboard_idle_mapnone.get(lang)
            : net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacyAmpersand().serialize(level.getDisplayName());

        Component header = LangOptions.tablist_header.getComponent(lang,
            new Placeholders("%logo%", "\uE001"),
            new Placeholders("%map%", mapName),
            new Placeholders("%rank%", playerRank),
            new Placeholders("%ping%", ru.sortix.parkourbeat.stats.StatsFormat.ping(ping)),
            new Placeholders("%tps%", String.valueOf(tps))
        );

        Component footer = LangOptions.tablist_footer.getComponent(lang);

        player.sendPlayerListHeaderAndFooter(header, footer);
        net.kyori.adventure.text.Component tabName = net.kyori.adventure.text.Component.empty()
            .append(net.kyori.adventure.text.serializer.legacy
                .LegacyComponentSerializer.legacyAmpersand().deserialize(playerRank + " "))
            .append(net.kyori.adventure.text.Component.text(player.getName(), net.kyori.adventure.text.format.NamedTextColor.WHITE)
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        // Значок появляется, только пока игрок реально бежит уровень.
        // Просто стоять на уровне недостаточно — сюда приходит state RUNNING.
        if (running) {
            tabName = tabName.append(net.kyori.adventure.text.Component.text(" " + RUNNING_MARK)
                .color(currentBlinkColor())
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }

        player.playerListName(tabName);
    }

    /**
     * Кадр мерцания считается от системного времени, а не от счётчика тиков:
     * так значок мигает одинаково у всех игроков и не сбивается, когда кто-то
     * заходит или выходит.
     */
    private static net.kyori.adventure.text.format.TextColor currentBlinkColor() {
        boolean even = (System.currentTimeMillis() / BLINK_FRAME_MILLIS) % 2L == 0L;
        return even ? RUNNING_COLOR_A : RUNNING_COLOR_B;
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        ParkourBeatScoreboard board = this.scoreboards.remove(event.getPlayer().getUniqueId());
        if (board != null) board.hide();
    }

    @Override
    public void disable() {
        this.task.cancel();
        HandlerList.unregisterAll(this);
        for (ParkourBeatScoreboard board : this.scoreboards.values()) {
            board.hide();
        }
        this.scoreboards.clear();
    }
}
