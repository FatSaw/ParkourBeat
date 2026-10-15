package ru.sortix.parkourbeat.threesixty;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.item.editor.type.EditTrackPointsItem;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.Waypoint;
import ru.sortix.parkourbeat.levels.settings.WorldSettings;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 360-УРОВНИ.
 * <p>
 * Труба квадратного сечения вокруг спавна: строить можно во все стороны - по стенам,
 * по потолку и под платформой. Пути из частиц здесь нет вообще: забег начинается сам,
 * как только игрок попал на уровень, а из точек уровню нужен только финиш.
 * <p>
 * Точки старта и финиша при этом всё равно живут в настройках - по ним считаются
 * направление уровня, границы и момент завершения забега. Просто они не рисуются:
 * см. {@code LevelSettings.updateParticleLocations()}.
 */
public class ThreeSixtyManager implements PluginManager, Listener {
    /** Метка палочки финиша в данных предмета. */
    public static final NamespacedKey MARKER_KEY = NamespacedKey.fromString("parkourbeat:three_sixty_finish");

    /** Слот палочки в хотбаре редактора - тот же, где на обычном уровне палочка пути. */
    public static final int WAND_SLOT = 2;

    /**
     * Как часто подсвечивается финиш. Пять тиков - глазу непрерывно, серверу незаметно.
     */
    private static final long FINISH_HIGHLIGHT_PERIOD_TICKS = 5L;

    /** Дистанция, с которой видно подсветку (13.5 блока в квадрате). */
    private static final double FINISH_HIGHLIGHT_DISTANCE_SQUARED = 182.25D;

    /**
     * Тот же фиолетовый градиент, что у подсветки в spawn-tools: чтобы «важная точка,
     * на которую надо встать» выглядела на сервере всегда одинаково.
     */
    private static final org.bukkit.Color[] PURPLE_GRADIENT = new org.bukkit.Color[]{
        org.bukkit.Color.fromRGB(0x8A, 0x2B, 0xE2),
        org.bukkit.Color.fromRGB(0x94, 0x00, 0xD3),
        org.bukkit.Color.fromRGB(0x99, 0x32, 0xCC),
        org.bukkit.Color.fromRGB(0xBA, 0x55, 0xD3),
        org.bukkit.Color.fromRGB(0x93, 0x70, 0xDB),
        org.bukkit.Color.fromRGB(0xDA, 0x70, 0xD6),
        org.bukkit.Color.fromRGB(0xDD, 0xA0, 0xDD)
    };

    private final @NonNull ParkourBeat plugin;
    private org.bukkit.scheduler.BukkitTask highlightTask;

    public ThreeSixtyManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        this.highlightTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin, this::highlightFinishes, FINISH_HIGHLIGHT_PERIOD_TICKS, FINISH_HIGHLIGHT_PERIOD_TICKS);
    }

    /**
     * Подсвечивает блок финиша всем, кто на 360-уровне: и строителю, и игроку.
     * <p>
     * Пути из частиц там нет, а значит и понять, куда бежать, иначе неоткуда - финиш
     * обязан быть видно издалека.
     */
    private void highlightFinishes() {
        java.util.Set<org.bukkit.World> done = new java.util.HashSet<>();

        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            org.bukkit.World world = player.getWorld();
            if (!done.add(world)) continue;

            Level level = this.plugin.get(LevelsManager.class).getLoadedLevel(world);
            if (!isThreeSixty(level)) continue;

            List<Waypoint> waypoints = level.getLevelSettings().getWorldSettings().getWaypoints();
            if (waypoints.size() < 2) continue;

            Location finish = waypoints.get(waypoints.size() - 1).getLocation();
            boolean near = false;
            for (Player nearby : world.getPlayers()) {
                if (nearby.getLocation().distanceSquared(finish) <= FINISH_HIGHLIGHT_DISTANCE_SQUARED) {
                    near = true;
                    break;
                }
            }
            if (!near) continue;

            Location top = finish.clone().add(0.0D, 0.7D, 0.0D);
            world.spawnParticle(org.bukkit.Particle.SPELL_WITCH, top, 2, 0.15, 0.15, 0.15, 0.0);
            for (int i = 0; i < 7; i++) {
                org.bukkit.Color color = PURPLE_GRADIENT[
                    java.util.concurrent.ThreadLocalRandom.current().nextInt(PURPLE_GRADIENT.length)];
                world.spawnParticle(org.bukkit.Particle.REDSTONE, top, 1, 0.15, 0.15, 0.15, 0.0,
                    new org.bukkit.Particle.DustOptions(color, 1.30f));
            }
        }
    }

    public static boolean isThreeSixty(@Nullable Level level) {
        if (level == null) return false;
        try {
            return level.getLevelSettings().getGameSettings().isThreeSixtyLevel();
        } catch (Throwable t) {
            return false;
        }
    }

    @NonNull
    public static ItemStack createFinishWand() {
        return ItemUtils.fixItalic(ItemUtils.create(Material.BLAZE_ROD, meta -> {
            meta.displayName(PbText.item("&6&lФиниш"));
            if (MARKER_KEY != null) {
                meta.getPersistentDataContainer().set(MARKER_KEY, PersistentDataType.STRING, "finish");
            }
            meta.lore(List.of(PbText.item("&7ПКМ - поставить старт по блоку")));
        }));
    }

    public static boolean isFinishWand(@Nullable ItemStack stack) {
        if (stack == null || MARKER_KEY == null || stack.getType() != Material.BLAZE_ROD) return false;
        try {
            org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
            if (meta == null) return false;
            return meta.getPersistentDataContainer().has(MARKER_KEY, PersistentDataType.STRING);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Выдаёт палочку финиша вместо палочки пути: путей на 360-уровне не бывает.
     */
    public void giveEditorItems(@NonNull Player player) {
        try {
            player.getInventory().setItem(WAND_SLOT, createFinishWand());
        } catch (Throwable ignored) {
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void on(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) return;
        if (!isFinishWand(event.getItem())) return;

        Player player = event.getPlayer();
        UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(player);
        if (!(activity instanceof EditActivity editActivity) || editActivity.isTesting()) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        Level level = editActivity.getLevel();
        if (!isThreeSixty(level)) {
            player.sendMessage(PbText.of("&cЭта палочка работает только на 360-уровнях."));
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        Block block = event.getClickedBlock();
        if (block == null) {
            player.sendMessage(PbText.of("&cНаведитесь на блок."));
            return;
        }

        this.setFinish(player, level, block);
    }

    /**
     * Финиш встаёт над указанным блоком.
     * <p>
     * Точек у 360-уровня ровно две: невидимый старт на спавне и финиш. Держать их
     * списком, а не отдельными полями, нужно затем, чтобы уровнем занимался всё тот же
     * код, что и обычными: границы, направление и завершение забега считаются по нему.
     */
    private void setFinish(@NonNull Player player, @NonNull Level level, @NonNull Block block) {
        WorldSettings worldSettings = level.getLevelSettings().getWorldSettings();
        List<Waypoint> waypoints = worldSettings.getWaypoints();

        // Точек у 360-уровня при создании нет вовсе, и это нормально: старт там не
        // ставят. Служебная нулевая точка появляется на спавне вместе с первым финишем -
        // по ней считаются границы и прогресс. Игрок её не видит: частиц на 360 нет,
        // маркер старта в редакторе не рисуется.
        if (waypoints.isEmpty()) {
            waypoints.add(new Waypoint(
                worldSettings.getSpawn().clone(), 0, EditTrackPointsItem.DEFAULT_PARTICLES_COLOR));
        }

        // Ровно тот блок, по которому кликнули: финиш на 360 - это блок, а не
        // плоскость поперёк уровня.
        Location finish = block.getLocation().add(0.5D, 0.5D, 0.5D);

        // Никаких проверок «впереди старта»: 360-уровень - это куб, бегут в нём во все
        // стороны, и финиш ставится ровно туда, куда кликнул строитель.
        if (!level.isLocationInside(finish)) {
            player.sendMessage(PbText.of("&cФиниш должен быть внутри границ уровня."));
            return;
        }

        Waypoint waypoint = new Waypoint(finish, 0, EditTrackPointsItem.DEFAULT_PARTICLES_COLOR);
        if (waypoints.size() < 2) {
            waypoints.add(waypoint);
        } else {
            // Финиш всегда один: старые точки в списке 360-уровня появиться не должны,
            // но если уровень достался от другого режима - подчищаем хвост.
            while (waypoints.size() > 2) waypoints.remove(waypoints.size() - 1);
            waypoints.set(1, waypoint);
        }

        worldSettings.updateBorders();
        level.getLevelSettings().recalculateWaypoints(level.getWorld());
        level.getLevelSettings().updateParticleLocations();
        this.plugin.get(LevelsManager.class).markWorldChanged(level.getUniqueId());

        player.sendMessage(PbText.of("&aФиниш установлен: &f"
            + block.getX() + " " + block.getY() + " " + block.getZ()));
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        if (this.highlightTask != null) {
            this.highlightTask.cancel();
            this.highlightTask = null;
        }
    }
}
