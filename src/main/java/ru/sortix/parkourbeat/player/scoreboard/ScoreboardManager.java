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
    private final BukkitTask logoTask;
    /** Последний заголовок таба каждого игрока: чтобы менять в нём только кадр логотипа. */
    private final Map<UUID, TabHeader> tabHeaders = new java.util.concurrent.ConcurrentHashMap<>();
    private char lastTabLogo = 0;

    private static final class TabHeader {
        final String lang;
        final Placeholders[] placeholders;
        final Component footer;

        TabHeader(String lang, Placeholders[] placeholders, Component footer) {
            this.lang = lang;
            this.placeholders = placeholders;
            this.footer = footer;
        }

        Component header(char logo) {
            Placeholders[] all = new Placeholders[this.placeholders.length + 1];
            all[0] = new Placeholders("%logo%", String.valueOf(logo));
            System.arraycopy(this.placeholders, 0, all, 1, this.placeholders.length);
            // Отступ под логотипом берём из РУССКОГО шаблона: в английском его не было,
            // и баннер наезжал на строки. Логотип выше строк, нужны пустые строки под ним.
            String text = LangOptions.tablist_header.get(this.lang, all);
            if (text == null) return LangOptions.tablist_header.getComponent(this.lang, all);
            String logoText = String.valueOf(logo);
            int at = text.indexOf(logoText);
            if (at >= 0) {
                // отступ СВЕРХУ: столько же пустых строк перед логотипом, сколько в русском
                int before = at;
                while (before > 0 && text.charAt(before - 1) == '\n') before--;
                text = text.substring(0, before) + "\n".repeat(logoGapBefore()) + text.substring(at);
                at = text.indexOf(logoText);
                int end = at + logoText.length();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^((?:[&\u00A7][0-9a-fk-or])*)(\n*)").matcher(text.substring(end));
                if (m.find()) {
                    text = text.substring(0, end) + m.group(1) + "\n".repeat(logoGap()) + text.substring(end + m.end());
                }
            }
            return ru.sortix.parkourbeat.utils.text.PbText.of(text);
        }

        private static int cachedGapBefore = -1;

        private static int logoGapBefore() {
            if (cachedGapBefore >= 0) return cachedGapBefore;
            int gap = 1;
            try {
                String ru = LangOptions.tablist_header.get("ru_ru");
                int at = ru == null ? -1 : ru.indexOf("%logo%");
                if (at >= 0) {
                    int n = 0;
                    while (at - n - 1 >= 0 && ru.charAt(at - n - 1) == '\n') n++;
                    gap = n;
                }
            } catch (Throwable ignored) {
            }
            cachedGapBefore = gap;
            return gap;
        }

        private static int cachedGap = -1;

        private static int logoGap() {
            if (cachedGap >= 0) return cachedGap;
            int gap = 2;
            try {
                String ru = LangOptions.tablist_header.get("ru_ru");
                int at = ru == null ? -1 : ru.indexOf("%logo%");
                if (at >= 0) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^((?:[&\u00A7][0-9a-fk-or])*)(\n*)").matcher(ru.substring(at + 6));
                    if (m.find() && m.group(2).length() > 0) gap = m.group(2).length();
                }
            } catch (Throwable ignored) {
            }
            cachedGap = gap;
            return gap;
        }
    }

    public ScoreboardManager(ParkourBeat plugin) {
        this.plugin = plugin;
        this.activityManager = plugin.get(ActivityManager.class);
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Плашки «ВЫ УПАЛИ» и т.п. - в актионбар вместо огромного титла (ui/BadgeToActionbar).
        ru.sortix.parkourbeat.ui.BadgeToActionbar.register(plugin);
        // Ограничение фейерверков игроков (лаги от спама ракетами на элитрах).
        ru.sortix.parkourbeat.utils.FireworkLimiter.register(plugin);

        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updatePlayerScoreboard(player);
            }
        }, 5L, 5L);

        // Глянец по логотипу в табе и в табло: 40 кадров по тику, пакеты - только при смене кадра.
        this.logoTask = Bukkit.getScheduler().runTaskTimer(plugin, this::animateLogos, 1L, 1L);
    }

    private void animateLogos() {
        long now = System.currentTimeMillis();
        BasicScoreboard.animateLogos(ru.sortix.parkourbeat.ui.Glyphs.scoreLogo(now));

        char tab = ru.sortix.parkourbeat.ui.Glyphs.tabLogo(now);
        if (tab == this.lastTabLogo) return;
        this.lastTabLogo = tab;
        for (Player player : Bukkit.getOnlinePlayers()) {
            TabHeader cached = this.tabHeaders.get(player.getUniqueId());
            if (cached == null || !ru.sortix.parkourbeat.ui.HudSettings.logo(player)) continue;
            player.sendPlayerListHeaderAndFooter(cached.header(tab), cached.footer);
        }
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

        Component footer = LangOptions.tablist_footer.getComponent(lang);
        TabHeader tabHeader = new TabHeader(lang, new Placeholders[]{
            new Placeholders("%map%", mapName),
            new Placeholders("%rank%", playerRank),
            new Placeholders("%ping%", ru.sortix.parkourbeat.stats.StatsFormat.ping(ping)),
            new Placeholders("%tps%", String.valueOf(tps))
        }, footer);
        this.tabHeaders.put(player.getUniqueId(), tabHeader);
        Component header = tabHeader.header(ru.sortix.parkourbeat.ui.HudSettings.logo(player)
            ? ru.sortix.parkourbeat.ui.Glyphs.tabLogo(System.currentTimeMillis()) : ru.sortix.parkourbeat.ui.Glyphs.LOGO_TAB);

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

    // ================================================================ ВХОД / ВЫХОД В ЧАТЕ
    // Минималистично: иконка, ник и слово капителями - каждому на его языке.

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onJoinMessage(org.bukkit.event.player.PlayerJoinEvent event) {
        event.joinMessage(null);
        broadcastPresence(event.getPlayer(), true);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onQuitMessage(PlayerQuitEvent event) {
        event.quitMessage(null);
        broadcastPresence(event.getPlayer(), false);
    }

    private void broadcastPresence(Player subject, boolean joined) {
        for (Player receiver : Bukkit.getOnlinePlayers()) {
            if (!joined && receiver.getUniqueId().equals(subject.getUniqueId())) continue;
            int set = ru.sortix.parkourbeat.ui.Glyphs.localeSet(ru.sortix.parkourbeat.utils.lang.PlayerLang.of(receiver));
            String word = joined
                ? (set == 1 ? "joined" : set == 2 ? "\u0437\u0430\u0439\u0448\u043e\u0432" : "\u0437\u0430\u0448\u0451\u043b")
                : (set == 1 ? "left" : set == 2 ? "\u0432\u0438\u0439\u0448\u043e\u0432" : "\u0432\u044b\u0448\u0435\u043b");
            receiver.sendMessage(ru.sortix.parkourbeat.utils.text.PbText.of((joined ? "<i:plus> " : "<i:leave> ")
                + "<v>&f" + subject.getName() + "</v> &8" + ru.sortix.parkourbeat.ui.SmallCaps.convert(word)));
        }
    }

    // ================================================================ /customsounds
    // /customsounds - свои звуки у себя вкл/выкл; /customsounds all (оператор) - у всех сразу.
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onCustomSoundsCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().trim().toLowerCase(java.util.Locale.ROOT);
        if (!msg.equals("/customsounds") && !msg.startsWith("/customsounds ")) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        boolean all = msg.endsWith(" all") || msg.endsWith(" global");
        boolean en = ru.sortix.parkourbeat.ui.Glyphs.localeSet(ru.sortix.parkourbeat.utils.lang.PlayerLang.of(player)) == 1;
        if (all) {
            if (!player.isOp()) {
                player.sendMessage(ru.sortix.parkourbeat.utils.text.PbText.of("<i:lock> &c" + (en ? "Operators only"
                    : "\u0422\u043e\u043b\u044c\u043a\u043e \u0434\u043b\u044f \u043e\u043f\u0435\u0440\u0430\u0442\u043e\u0440\u043e\u0432")));
                return;
            }
            boolean now = !ru.sortix.parkourbeat.ui.UiSounds.globalEnabled();
            ru.sortix.parkourbeat.ui.UiSounds.setGlobal(now);
            player.sendMessage(ru.sortix.parkourbeat.utils.text.PbText.of("<i:music> &f" + (en ? "Custom sounds for everyone: "
                : "\u0421\u0432\u043e\u0438 \u0437\u0432\u0443\u043a\u0438 \u0443 \u0432\u0441\u0435\u0445: ") + (now ? "&aON" : "&cOFF")));
            return;
        }
        boolean now = !ru.sortix.parkourbeat.ui.UiSounds.personal(player);
        ru.sortix.parkourbeat.ui.UiSounds.setPersonal(player, now);
        player.sendMessage(ru.sortix.parkourbeat.utils.text.PbText.of("<i:music> &f" + (en ? "Custom sounds: "
            : "\u0421\u0432\u043e\u0438 \u0437\u0432\u0443\u043a\u0438: ") + (now ? "&aON" : "&cOFF")));
        ru.sortix.parkourbeat.ui.UiSounds.click(player);
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        this.tabHeaders.remove(event.getPlayer().getUniqueId());
        ru.sortix.parkourbeat.ui.LevelTransition.cancel(event.getPlayer());
        ParkourBeatScoreboard board = this.scoreboards.remove(event.getPlayer().getUniqueId());
        if (board != null) board.hide();
    }

    @Override
    public void disable() {
        this.task.cancel();
        this.logoTask.cancel();
        HandlerList.unregisterAll(this);
        for (ParkourBeatScoreboard board : this.scoreboards.values()) {
            board.hide();
        }
        this.scoreboards.clear();
    }
}
