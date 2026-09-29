// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/player/scoreboard/ScoreboardManager.java
package ru.sortix.parkourbeat.player.scoreboard;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

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
import ru.sortix.parkourbeat.activity.type.ReplayActivity;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import ru.sortix.parkourbeat.utils.text.PbText;
public class ScoreboardManager implements PluginManager, Listener {
    private static final String RUNNING_MARK = "\u266B";
    private static final net.kyori.adventure.text.format.TextColor RUNNING_COLOR_A =
        net.kyori.adventure.text.format.TextColor.color(235, 107, 255);
    private static final net.kyori.adventure.text.format.TextColor RUNNING_COLOR_B =
        net.kyori.adventure.text.format.TextColor.color(232, 149, 245);
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
        int rank = this.plugin.get(ru.sortix.parkourbeat.rating.StatisticsManager.class).getDisplayRank(player.getUniqueId());
        if (player.getLevel() != rank) {
            player.setLevel(rank);
        }
        if (player.getExp() != 0.0f) {
            player.setExp(0.0f);
        }

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
            // У наблюдателя своё табло: показывать ему собственную статистику лобби,
            // пока он смотрит чужой забег, бессмысленно.
            level = spectateActivity.getLevel();
            this.updatePlayerTabList(player, level, false);
            this.plugin.get(ru.sortix.parkourbeat.inventory.LobbyItems.class).sync(player, false);

            ParkourBeatScoreboard current = this.scoreboards.get(player.getUniqueId());
            if (!(current instanceof SpectateScoreboard board)
                || board.getActivity() != spectateActivity) {
                if (current != null) current.hide();
                current = new SpectateScoreboard(this.plugin, player, spectateActivity);
                this.scoreboards.put(player.getUniqueId(), current);
            }
            current.update();
            return;
        } else if (activity instanceof ReplayActivity replayActivity) {
            level = replayActivity.getLevel();
            this.updatePlayerTabList(player, level, false);
            this.plugin.get(ru.sortix.parkourbeat.inventory.LobbyItems.class).sync(player, false);

            ParkourBeatScoreboard current = this.scoreboards.get(player.getUniqueId());
            if (!(current instanceof ReplayScoreboard)) {
                if (current != null) current.hide();
                current = new ReplayScoreboard(this.plugin, player, replayActivity.getRun());
                this.scoreboards.put(player.getUniqueId(), current);
            }
            // Табло реплея почти статично, но ранг игрока в топе меняется - обновляем.
            current.update();
            return;
        }

        // 2D-забег не поднимает состояние обычной игры: там свой цикл, а Game
        // остаётся в READY. Для таба это тоже "играет", иначе значок забега на
        // 2D-уровнях не появлялся вообще.
        boolean twoDRunning = level != null
            && ru.sortix.parkourbeat.twod.TwoDManager.isTwoD(level)
            && this.plugin.get(ru.sortix.parkourbeat.twod.TwoDManager.class).isPlaying(player);

        // ЗАЕЗД «КОПАТЕЛЯ» - ТОЖЕ ЗАБЕГ.
        //
        // У него, как и у 2D, свой цикл, а обычная Game остаётся в READY. Для всего,
        // что смотрит на состояние обычной игры, такой игрок числился стоящим в лобби:
        // ему каждый тик возвращали хотбар лобби поверх кирки, показывали лобби-табло
        // и не ставили значок забега в табе. Именно поэтому кирка «не выдавалась» -
        // выдавалась, и тут же затиралась обратно.
        boolean diggerRunning = level != null
            && ru.sortix.parkourbeat.digger.DiggerManager.isDigger(level)
            && this.plugin.get(ru.sortix.parkourbeat.digger.DiggerManager.class).isPlaying(player);

        this.updatePlayerTabList(player, level, shouldBePlay || twoDRunning || diggerRunning);

        boolean showLobbyItems = !shouldBePlay && !diggerRunning;
        if (activity instanceof EditActivity) {
            showLobbyItems = false;
        }
        this.plugin.get(ru.sortix.parkourbeat.inventory.LobbyItems.class)
            .sync(player, showLobbyItems);

        // 2D-забег: своё табло, потому что обычное живёт трекером попаданий,
        // а в 2D попаданий нет.
        ru.sortix.parkourbeat.twod.TwoDGame twoDGame = null;
        if (level != null && ru.sortix.parkourbeat.twod.TwoDManager.isTwoD(level)) {
            twoDGame = this.plugin.get(ru.sortix.parkourbeat.twod.TwoDManager.class).getGame(player);
            if (twoDGame != null && !twoDGame.isActive()) twoDGame = null;
        }

        ParkourBeatScoreboard current = this.scoreboards.get(player.getUniqueId());

        // ВО ВРЕМЯ ЗАЕЗДА «КОПАТЕЛЯ» ТАБЛО УБИРАЕТСЯ СОВСЕМ.
        //
        // Оно занимает справа ровно ту полосу, куда прилетают руды верхнего ряда, а
        // читать его на скорости всё равно некому: очки, комбо и точность и так в
        // боссбаре и в подписях попаданий. Прятать его из самого забега бессмысленно -
        // этот менеджер каждый тик поставил бы табло обратно.
        if (diggerRunning) {
            if (current != null) {
                current.hide();
                this.scoreboards.remove(player.getUniqueId());
            }
            return;
        }

        if (twoDGame != null) {
            if (!(current instanceof TwoDScoreboard board) || board.getGame() != twoDGame) {
                if (current != null) current.hide();
                current = new TwoDScoreboard(this.plugin, player, level, twoDGame);
                this.scoreboards.put(player.getUniqueId(), current);
            }
            current.update();
            return;
        }

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
        String lang = PlayerLang.of(player);

        String playerRank = this.plugin
            .get(ru.sortix.parkourbeat.rating.StatisticsManager.class)
            .getRankLabel(player.getUniqueId());
        int ping = this.plugin.get(ru.sortix.parkourbeat.player.PingManager.class).getPing(player);
        double tps = Math.round(Bukkit.getServer().getTPS()[0] * 10.0) / 10.0;

        boolean hideMap = this.plugin
            .get(ru.sortix.parkourbeat.player.PlayerSettingsManager.class)
            .isPlayingStatusHidden(player.getUniqueId());

        String mapName = level == null || hideMap
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
            .append(PbText.of(playerRank + " "))
            .append(net.kyori.adventure.text.Component.text(player.getName(), net.kyori.adventure.text.format.NamedTextColor.WHITE)
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        boolean statusHidden = this.plugin
            .get(ru.sortix.parkourbeat.player.PlayerSettingsManager.class)
            .isPlayingStatusHidden(player.getUniqueId());

        if (this.plugin.get(ru.sortix.parkourbeat.player.AfkManager.class).isAfk(player.getUniqueId())) {
            tabName = tabName.append(PbText.of(" &f[&7AFK&f]")
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }

        if (running && !statusHidden) {
            tabName = tabName.append(net.kyori.adventure.text.Component.text(" " + RUNNING_MARK)
                .color(currentBlinkColor())
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }

        player.playerListName(tabName);
    }

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
