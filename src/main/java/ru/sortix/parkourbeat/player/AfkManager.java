package ru.sortix.parkourbeat.player;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import ru.sortix.parkourbeat.utils.text.PbText;
public class AfkManager implements PluginManager, Listener {
    public static final long TOGGLE_DELAY_MILLIS = 3_000L;
    public static final int[] AUTO_AFK_MINUTES = {0, 5, 15, 30};

    private final @NonNull ParkourBeat plugin;
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastMoveAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingSince = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pendingState = new ConcurrentHashMap<>();
    private final BukkitTask task;

    public AfkManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public boolean isAfk(@NonNull UUID playerId) {
        return this.afkPlayers.contains(playerId);
    }

    public boolean isPending(@NonNull UUID playerId) {
        return this.pendingSince.containsKey(playerId);
    }

    public void requestToggle(@NonNull Player player, boolean afk) {
        UUID id = player.getUniqueId();
        if (this.afkPlayers.contains(id) == afk) return;
        this.pendingSince.put(id, System.currentTimeMillis());
        this.pendingState.put(id, afk);
    }

    private void apply(@NonNull UUID id, boolean afk) {
        if (afk) this.afkPlayers.add(id);
        else this.afkPlayers.remove(id);

        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) return;
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer legacy =
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand();
        player.sendMessage(PbText.of(afk
            ? Lang.raw(PlayerLang.of(player), "auto.afk_manager.apply.1")
            : Lang.raw(PlayerLang.of(player), "auto.afk_manager.apply.2")));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        PlayerSettingsManager settings = this.plugin.get(PlayerSettingsManager.class);

        for (Map.Entry<UUID, Long> entry : this.pendingSince.entrySet()) {
            if (now - entry.getValue() < TOGGLE_DELAY_MILLIS) continue;
            UUID id = entry.getKey();
            Boolean target = this.pendingState.remove(id);
            this.pendingSince.remove(id);
            if (target != null) this.apply(id, target);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (this.afkPlayers.contains(id) || this.isPending(id)) continue;

            int minutes = settings.getAutoAfkMinutes(id);
            if (minutes <= 0) continue;

            // ИДЁТ ЗАБЕГ - ЗНАЧИТ ЧЕЛОВЕК ЗА КЛАВИАТУРОЙ.
            //
            // Бездействие здесь определяется одним-единственным признаком: сдвинулся ли
            // игрок сам. В «Копателе» он этого не делает НИКОГДА - он пассажир носителя,
            // а двигает носителя сервер. Пассажира при этом никуда не «двигают» в том
            // смысле, который понимает PlayerMoveEvent, поэтому для счётчика бездействия
            // игрок всю карту стоит на месте. Через выставленные пять минут его уводило
            // в АФК прямо посреди заезда.
            //
            // Тот же разговор про 2D: там управление тоже не сводится к перемещению тела.
            //
            // Поэтому во время забега счётчик просто сбрасывается. Не «не проверяем», а
            // именно сбрасываем: иначе сразу после финиша человек ушёл бы в АФК по
            // времени, накопленному за время игры.
            if (this.isBusyPlaying(player)) {
                this.lastMoveAt.put(id, now);
                continue;
            }

            Long last = this.lastMoveAt.get(id);
            if (last == null) {
                this.lastMoveAt.put(id, now);
                continue;
            }
            if (now - last < minutes * 60_000L) continue;
            this.requestToggle(player, true);
        }
    }

    /**
     * Занят ли игрок забегом прямо сейчас.
     * <p>
     * Проверяются все три режима, а не только «Копатель»: 2D работает так же, а обычный
     * паркур хоть и двигает игрока сам, но на отсчёте перед стартом и на паузе он тоже
     * стоит неподвижно, и уходить в АФК оттуда ему незачем.
     */
    private boolean isBusyPlaying(@NonNull Player player) {
        try {
            if (this.plugin.get(ru.sortix.parkourbeat.digger.DiggerManager.class)
                .isPlaying(player)) return true;
            if (this.plugin.get(ru.sortix.parkourbeat.twod.TwoDManager.class)
                .isPlaying(player)) return true;

            ru.sortix.parkourbeat.activity.UserActivity activity =
                this.plugin.get(ru.sortix.parkourbeat.activity.ActivityManager.class)
                    .getActivity(player);

            // Обычный забег: игрок на уровне, даже если стоит на отсчёте.
            //
            // Редактор сюда НЕ входит намеренно. Строитель действительно может отойти
            // от клавиатуры, оставив открытым редактор, и держать его онлайн вечно -
            // ровно то, от чего автоафк и придуман. А вот забег конечен сам по себе:
            // он закончится через три минуты и счётчик пойдёт снова.
            return activity instanceof ru.sortix.parkourbeat.activity.type.PlayActivity;
        } catch (Throwable ignored) {
            // Любой менеджер может быть ещё не поднят - тогда просто считаем, что не занят.
            return false;
        }
    }

    @EventHandler
    private void on(@NonNull PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getX() == event.getTo().getX()
            && event.getFrom().getY() == event.getTo().getY()
            && event.getFrom().getZ() == event.getTo().getZ()) return;

        this.markActive(event.getPlayer());
    }

    /**
     * ЛОМАНИЕ БЛОКА - ТОЖЕ ДЕЙСТВИЕ.
     * <p>
     * В «Копателе» это ЕДИНСТВЕННОЕ, что игрок делает своими руками: он не ходит и не
     * поворачивается корпусом, он только бьёт по рудам. Странно было бы считать
     * бездействием то, ради чего он вообще зашёл.
     * <p>
     * Полезно и вне забега: строитель, который час выкладывает тоннель, тоже не
     * обязан подпрыгивать раз в пять минут, чтобы его не увели в АФК.
     */
    @EventHandler(ignoreCancelled = true)
    private void on(@NonNull org.bukkit.event.block.BlockBreakEvent event) {
        this.markActive(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    private void on(@NonNull org.bukkit.event.block.BlockPlaceEvent event) {
        this.markActive(event.getPlayer());
    }

    /** Отметить действие: счётчик бездействия сбрасывается, из АФК выводим. */
    private void markActive(@NonNull Player player) {
        UUID id = player.getUniqueId();
        this.lastMoveAt.put(id, System.currentTimeMillis());

        if (this.afkPlayers.contains(id) && !Boolean.FALSE.equals(this.pendingState.get(id))) {
            this.requestToggle(player, false);
        }
    }

    /** Из АФК по чату выходим сразу: ждать 3 секунды тут бессмысленно. */
    public void wakeImmediately(@NonNull Player player) {
        UUID id = player.getUniqueId();
        this.lastMoveAt.put(id, System.currentTimeMillis());

        boolean wasPending = Boolean.TRUE.equals(this.pendingState.get(id));
        if (wasPending) {
            this.pendingSince.remove(id);
            this.pendingState.remove(id);
        }
        if (!this.afkPlayers.contains(id)) return;

        this.pendingSince.remove(id);
        this.pendingState.remove(id);
        this.apply(id, false);
    }

    @EventHandler(ignoreCancelled = true)
    private void on(@NonNull AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this.plugin, () -> this.wakeImmediately(player));
    }

    @EventHandler(ignoreCancelled = true)
    private void on(@NonNull PlayerCommandPreprocessEvent event) {
        this.wakeImmediately(event.getPlayer());
    }

    @EventHandler
    private void on(@NonNull PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        this.afkPlayers.remove(id);
        this.lastMoveAt.remove(id);
        this.pendingSince.remove(id);
        this.pendingState.remove(id);
    }

    @Override
    public void disable() {
        if (this.task != null) this.task.cancel();
        HandlerList.unregisterAll(this);
        this.afkPlayers.clear();
    }
}
