package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.lightshow.api.ShowHandle;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.wonder.WonderAnchor;
import ru.sortix.parkourbeat.levels.wonder.WonderBridge;
import ru.sortix.parkourbeat.levels.wonder.WonderEffect;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.text.PbText;
import ru.sortix.parkourbeat.utils.wonder.WonderTimeline;
import ru.sortix.parkourbeat.world.TeleportUtils;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Предпросмотр эффекта прямо на таймлайне.
 * <p>
 * Строителя переносит в ту точку трассы, где эффект встретит бегущего, и показывает всё
 * оттуда: только так видно, попадает ли эффект в паркур и не сливается ли с дорогой.
 * Пока показ идёт, в сабтитрах висит подсказка, а левый клик возвращает в то же меню,
 * из которого предпросмотр запускали.
 */
public final class WonderPreview {

    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private WonderPreview() {
    }

    private static final class Session {
        ShowHandle handle;
        BukkitTask task;
        Consumer<Player> back;
        Location returnTo;
    }

    public static boolean isActive(@NonNull Player player) {
        return ACTIVE.containsKey(player.getUniqueId());
    }

    /**
     * @param back что открыть, когда строитель щёлкнет левой кнопкой
     */
    public static void show(@NonNull ParkourBeat plugin,
                            @NonNull Player player,
                            @NonNull Level level,
                            @NonNull WonderEffect effect,
                            @Nullable Consumer<Player> back
    ) {
        stop(plugin, player, false);

        if (!WonderBridge.isAvailable()) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_preview.show.1")));
            return;
        }
        String problem = WonderBridge.validate(effect);
        if (problem != null) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_preview.show.2") + problem));
            return;
        }

        player.closeInventory();

        Session session = new Session();
        session.back = back;
        session.returnTo = player.getLocation().clone();
        ACTIVE.put(player.getUniqueId(), session);

        // Показу нельзя жить меньше трёх секунд: короткую вспышку не успеть даже разглядеть,
        // а отсечение по расстоянию для предпросмотра отключаем совсем, иначе после телепорта
        // первые кадры уходят в пустоту.
        WonderEffect shown = effect.copy();
        if (shown.getDurationMillis() < 3000) {
            shown.setEndMillis(shown.getStartMillis() + 3000);
        }
        shown.setParams((shown.getParams() + " cull:0 view:512").trim());

        Location onTrack = WonderTimeline.locationAt(level, effect.getStartMillis());
        if (onTrack == null || onTrack.getWorld() == null) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_preview.show.3")));
            begin(plugin, player, shown, effect, session, null);
            return;
        }

        Location viewpoint = onTrack.clone().add(0, 1.0D, 0);
        if (effect.getAnchor() != WonderAnchor.FOLLOW) {
            viewpoint.subtract(viewpoint.getDirection().clone().multiply(2.0D));
        }

        // Раньше шоу запускалось сразу после вызова телепорта, а он асинхронный:
        // частицы рождались, пока строитель ещё стоял за сотни блоков, и он не видел ничего.
        TeleportUtils.teleportAsync(plugin, player, viewpoint).thenAccept(success ->
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || !ACTIVE.containsKey(player.getUniqueId())) return;
                begin(plugin, player, shown, effect, session, onTrack);
            }, 5L));
    }

    private static void begin(@NonNull ParkourBeat plugin,
                              @NonNull Player player,
                              @NonNull WonderEffect shown,
                              @NonNull WonderEffect original,
                              @NonNull Session session,
                              @Nullable Location onTrack
    ) {
        int ticks = Math.max(60, shown.getDurationMillis() / 50);
        ShowHandle handle = WonderBridge.play(player, shown, ticks, onTrack);
        if (handle == null) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_preview.begin.1")));
            stop(plugin, player, true);
            return;
        }
        session.handle = handle;

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.7f);
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_preview.begin.2") + original.getStartTimecode()
            + " &8· &7" + TimeUtils.formatSeconds(original.getDurationMillis()) + Lang.raw(PlayerLang.of(player), "auto.wonder_preview.begin.3")
            + Lang.raw(PlayerLang.of(player), "auto.wonder_preview.begin.4") + handle.points()));

        // Подсказка держится всё время показа: титры сами гаснут, поэтому шлём их заново
        session.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Session current = ACTIVE.get(player.getUniqueId());
            if (current == null || !player.isOnline()) return;
            if (current.handle != null && !current.handle.isAlive()) {
                stop(plugin, player, true);
                return;
            }
            player.sendTitle(" ", Lang.raw(PlayerLang.of(player), "auto.wonder_preview.begin.5"), 0, 45, 10);
        }, 1L, 20L);

    }

    /** Левый клик во время показа. true — клик проглочен предпросмотром. */
    public static boolean handleLeftClick(@NonNull ParkourBeat plugin, @NonNull Player player) {
        if (!ACTIVE.containsKey(player.getUniqueId())) return false;
        stop(plugin, player, true);
        return true;
    }

    public static void stop(@NonNull ParkourBeat plugin, @NonNull Player player, boolean reopen) {
        Session session = ACTIVE.remove(player.getUniqueId());
        if (session == null) return;

        if (session.task != null) session.task.cancel();
        if (session.handle != null) {
            try {
                session.handle.stop();
            } catch (Throwable ignored) {
            }
        }
        player.sendTitle(" ", " ", 0, 1, 0);

        if (session.returnTo != null) TeleportUtils.teleportAsync(plugin, player, session.returnTo);
        if (reopen && session.back != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) session.back.accept(player);
            }, 3L);
        }
    }
}
