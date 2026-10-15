package ru.sortix.parkourbeat.twod;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * МОНЕТКИ 2D-УРОВНЯ.
 * <p>
 * Выглядят как лежащий дропнутый подсолнух: обычная сущность-предмет, только без
 * гравитации, без подбора и без исчезновения по таймеру. Именно поэтому монетка не
 * требует ни ресурспака, ни отдельной модели - она узнаётся с первого взгляда.
 * <p>
 * Координаты монеток живут в настройках уровня, а сущности - только в памяти: их
 * пересоздают при каждом запуске сервера, поэтому потерянных монеток в мире не бывает.
 */
public final class TwoDCoins {
    private TwoDCoins() {
    }

    public static final Material COIN_MATERIAL = Material.SUNFLOWER;
    public static final String COIN_ITEM_NAME = "&6&lМонетка";

    /** Живые сущности монеток по уровням. */
    private static final Map<UUID, List<Item>> SPAWNED = new HashMap<>();

    /**
     * Метка в данных предмета. По названию предметы не опознаём: название игрок может
     * переписать в наковальне, а на разных версиях сервера оно ещё и по-разному
     * сериализуется.
     */
    public static final NamespacedKey MARKER_KEY = NamespacedKey.fromString("parkourbeat:two_d_item");

    /** Предмет, которым строитель ставит монетки. */
    @NonNull
    public static ItemStack createBuilderItem() {
        return ItemUtils.fixItalic(ItemUtils.create(COIN_MATERIAL, meta -> {
            meta.displayName(PbText.item(COIN_ITEM_NAME));
            if (MARKER_KEY != null) {
                meta.getPersistentDataContainer().set(MARKER_KEY, PersistentDataType.STRING, "coin");
            }

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(PbText.item("&7ПКМ по блоку - поставить монетку"));
            lore.add(PbText.item("&7ЛКМ - убрать ближайшую"));
            lore.add(Component.empty());
            lore.add(PbText.item("&8Монетку собирает кубик на лету"));
            meta.lore(lore);
        }));
    }

    public static boolean isBuilderItem(@Nullable ItemStack stack) {
        return hasMarker(stack, COIN_MATERIAL, "coin");
    }

    /**
     * Опознаём предмет по метке в его данных, а не по названию: название игрок может
     * переписать в наковальне, да и сериализуется оно на разных версиях по-разному.
     */
    static boolean hasMarker(@Nullable ItemStack stack, @NonNull Material material, @NonNull String value) {
        if (stack == null || stack.getType() != material) return false;
        if (MARKER_KEY == null) return false;
        try {
            org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
            if (meta == null) return false;
            String stored = meta.getPersistentDataContainer().get(MARKER_KEY, PersistentDataType.STRING);
            return value.equals(stored);
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================== СУЩНОСТИ ====================

    /**
     * Пересоздать монетки уровня в мире. Вызывается при входе в редактор, старте
     * забега и при каждой правке списка.
     */
    public static void refresh(@NonNull ParkourBeat plugin, @NonNull Level level) {
        refresh(plugin, level, false);
    }

    /**
     * @param glowing подсветить монетки жёлтым. Включается для строителя в редакторе:
     *                ему важно видеть, где они расставлены. В обычной игре подсветки
     *                нет - монетку игрок должен замечать сам.
     */
    public static void refresh(@NonNull ParkourBeat plugin, @NonNull Level level, boolean glowing) {
        despawn(level);

        World world = level.getWorld();
        List<Vector> points;
        try {
            points = level.getLevelSettings().getGameSettings().getTwoDSettings().getCoins();
        } catch (Throwable t) {
            return;
        }
        if (points.isEmpty()) return;

        List<Item> spawned = new ArrayList<>();
        for (Vector point : points) {
            try {
                Location location = new Location(world, point.getX(), point.getY(), point.getZ());
                ItemStack stack = new ItemStack(COIN_MATERIAL);
                Item item = world.dropItem(location, stack);
                item.setGravity(false);
                item.setVelocity(new Vector());
                item.setPickupDelay(Integer.MAX_VALUE);
                item.setInvulnerable(true);
                item.setSilent(true);
                item.setPersistent(false);
                try {
                    // Paper-only: на Spigot метода нет, а подбирать монетку всё равно некому.
                    item.setCanMobPickup(false);
                } catch (Throwable ignored) {
                }
                item.setTicksLived(1);
                item.addScoreboardTag(TwoDManager.ENTITY_TAG);

                if (glowing) {
                    item.setGlowing(true);
                    applyGlowTeam(item);
                }
                spawned.add(item);
            } catch (Throwable t) {
                plugin.getLogger().warning("2D: не удалось поставить монетку: " + t);
            }
        }
        SPAWNED.put(level.getUniqueId(), spawned);
    }

    /**
     * Предметы в Minecraft исчезают через пять минут - обновляем им возраст,
     * иначе монетки пропадут прямо посреди уровня.
     */
    public static void keepAlive(@NonNull Level level) {
        List<Item> items = SPAWNED.get(level.getUniqueId());
        if (items == null) return;
        for (Item item : items) {
            try {
                if (item.isValid()) item.setTicksLived(1);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Команда подсветки: цвет свечения в Minecraft задаётся только через неё. */
    private static final String TEAM_NAME = "pb2d_coins";

    /**
     * ЖЁЛТОЕ СВЕЧЕНИЕ.
     * <p>
     * Цвет обводки берётся из команды, в которой состоит сущность, но смотрит игрок
     * на СВОЙ скорборд, а не на главный. У плагина свои борды, поэтому команду
     * приходится заводить на каждом скорборде, который сейчас кому-то показан -
     * иначе монетка светится белым.
     */
    private static void applyGlowTeam(@NonNull Item item) {
        String entry = item.getUniqueId().toString();

        java.util.Set<org.bukkit.scoreboard.Scoreboard> boards = new java.util.HashSet<>();
        try {
            boards.add(org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard());
        } catch (Throwable ignored) {
        }
        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            try {
                boards.add(player.getScoreboard());
            } catch (Throwable ignored) {
            }
        }

        for (org.bukkit.scoreboard.Scoreboard board : boards) {
            try {
                org.bukkit.scoreboard.Team team = board.getTeam(TEAM_NAME);
                if (team == null) team = board.registerNewTeam(TEAM_NAME);
                team.setColor(org.bukkit.ChatColor.YELLOW);
                if (!team.hasEntry(entry)) team.addEntry(entry);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Включить или выключить подсветку уже расставленных монеток.
     */
    /**
     * Привязать монетки к команде подсветки на ТЕКУЩЕМ скорборде игрока.
     * <p>
     * Цвет свечения берётся из команды на том скорборде, который игрок видит прямо
     * сейчас. А во время забега ему показывают новый, только что созданный борд, где
     * нашей команды нет - поэтому монетки и светились белым. Синхронизируем.
     */
    public static void syncGlowTeam(@NonNull org.bukkit.entity.Player player, @NonNull Level level) {
        List<Item> items = SPAWNED.get(level.getUniqueId());
        if (items == null || items.isEmpty()) return;

        try {
            org.bukkit.scoreboard.Scoreboard board = player.getScoreboard();
            org.bukkit.scoreboard.Team team = board.getTeam(TEAM_NAME);
            if (team == null) team = board.registerNewTeam(TEAM_NAME);
            team.setColor(org.bukkit.ChatColor.YELLOW);

            for (Item item : items) {
                if (!item.isValid()) continue;
                String entry = item.getUniqueId().toString();
                if (!team.hasEntry(entry)) team.addEntry(entry);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void setGlowing(@NonNull Level level, boolean glowing) {
        List<Item> items = SPAWNED.get(level.getUniqueId());
        if (items == null) return;

        for (Item item : items) {
            try {
                if (!item.isValid()) continue;
                item.setGlowing(glowing);
                if (glowing) applyGlowTeam(item);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void despawn(@NonNull Level level) {
        List<Item> items = SPAWNED.remove(level.getUniqueId());
        if (items == null) return;
        for (Item item : items) {
            try {
                item.remove();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void despawnAll() {
        for (UUID levelId : new ArrayList<>(SPAWNED.keySet())) {
            List<Item> items = SPAWNED.remove(levelId);
            if (items == null) continue;
            for (Item item : items) {
                try {
                    item.remove();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Сущность монетки по её порядковому номеру - нужна, чтобы спрятать собранную
     * монетку лично у собравшего.
     */
    @Nullable
    public static Entity getEntity(@NonNull Level level, int index) {
        List<Item> items = SPAWNED.get(level.getUniqueId());
        if (items == null || index < 0 || index >= items.size()) return null;
        Item item = items.get(index);
        return item != null && item.isValid() ? item : null;
    }
}
