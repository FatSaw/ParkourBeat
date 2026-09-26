// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/item/editor/type/EditTrackPointsItem.java
package ru.sortix.parkourbeat.item.editor.type;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.item.editor.EditorItem;
import ru.sortix.parkourbeat.levels.DirectionChecker;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.Waypoint;
import ru.sortix.parkourbeat.levels.settings.WorldSettings;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import javax.annotation.Nullable;
import java.util.List;

public class EditTrackPointsItem extends EditorItem {
    public static final Color DEFAULT_PARTICLES_COLOR = Color.LIME;

    public static final double MIN_DISTANCE_BETWEEN_POINTS = 0.5;
    public static final double HEIGHT_CHANGE_VALUE = 0.5;
    public static final int REMOVE_POINT_DISTANCE = 1;
    public static final int INTERACT_BLOCK_DISTANCE = 5;

    public EditTrackPointsItem(@NonNull ParkourBeat plugin, String lang, int slot) {
        super(plugin, lang, slot, 0, ItemUtils.create(Material.BLAZE_ROD, (meta) -> {
            meta.displayName(LangOptions.item_editor_points_item_name.getComponent(lang));
            meta.lore(LangOptions.item_editor_points_item_lore.getComponents(lang));
        }));
    }

    public static void clearAllPoints(@NonNull Level level) {
        WorldSettings worldSettings = level.getLevelSettings().getWorldSettings();
        worldSettings.getWaypoints().clear();
        // Остаётся только старт: финиш - это последняя точка пути, а пути пока нет.
        worldSettings.addStartPoint(level.getWorld());
        worldSettings.updateBorders();
        level.getLevelSettings().recalculateWaypoints(level.getWorld());
        level.getLevelSettings().updateParticleLocations();

        // На дуэльной карте сбрасываются ОБЕ трассы: оставленная вторая половина
        // висела бы поверх нового пути, и понять, почему уровень не проходится,
        // было бы невозможно.
        if (ru.sortix.parkourbeat.duel.DuelManager.isDuel(level)) {
            java.util.List<Waypoint> second = worldSettings.getSecondWaypoints();
            second.clear();
            second.add(new Waypoint(
                worldSettings.getWaypoints().get(0).getLocation().clone(),
                0,
                ru.sortix.parkourbeat.duel.DuelSide.SECOND.getParticlesColor()));
            level.getLevelSettings().updateSecondParticleLocations();
        }
    }

    private static int findBestInsertionIndex(List<Waypoint> waypoints, Location newLoc) {
        if (waypoints.isEmpty()) return 0;
        if (waypoints.size() == 1) return 1;

        int bestIndex = 1;
        double minIncrease = Double.MAX_VALUE;

        for (int i = 0; i < waypoints.size() - 1; i++) {
            Location p1 = waypoints.get(i).getLocation();
            Location p2 = waypoints.get(i + 1).getLocation();

            double d1 = p1.distance(newLoc);
            double d2 = p2.distance(newLoc);
            double d12 = p1.distance(p2);

            double increase = (d1 + d2) - d12;
            if (increase < minIncrease) {
                minIncrease = increase;
                bestIndex = i + 1;
            }
        }

        Location first = waypoints.get(0).getLocation();
        Location last = waypoints.get(waypoints.size() - 1).getLocation();

        if (newLoc.distance(first) < minIncrease) {
            return 0;
        }
        if (newLoc.distance(last) < minIncrease) {
            return waypoints.size();
        }

        return bestIndex;
    }

    /**
     * Вставить точку в путь.
     *
     * @param primary true - это основной путь уровня, у которого пересчитываются границы
     *                (старт, финиш, нижняя высота). Второй путь дуэльного уровня границ
     *                не задаёт: старт и финиш у обеих трасс общие.
     */
    public static boolean insertPoint(
        @NonNull List<Waypoint> waypoints,
        @NonNull Waypoint newWaypoint,
        @NonNull Player player,
        @NonNull Level level,
        boolean primary) {

        // Точка позади старта делает уровень непроходимым: игрок стартует уже "после"
        // неё, путь начинается за спиной и первый же шаг считается движением назад.
        if (!waypoints.isEmpty()) {
            ru.sortix.parkourbeat.levels.DirectionChecker checker =
                level.getLevelSettings().getDirectionChecker();
            double startCoord = checker.getCoordinate(waypoints.get(0).getLocation());
            double newCoord = checker.getCoordinate(newWaypoint.getLocation());

            boolean behindStart = checker.isNegative()
                ? newCoord > startCoord
                : newCoord < startCoord;

            if (behindStart) {
                player.sendMessage(ru.sortix.parkourbeat.utils.text.PbText.of(
                    Lang.raw(PlayerLang.of(player), "auto.edit_track_points_item.insert_waypoint_in_order.1")));
                return false;
            }
        }

        int index = findBestInsertionIndex(waypoints, newWaypoint.getLocation());

        // Точка НИКОГДА не встаёт перед стартом.
        //
        // findBestInsertionIndex сравнивает расстояние до крайней точки с "удлинением"
        // маршрута, и стоит поставить точку чуть в стороне, как она оказывается ближе
        // к старту, чем стоит вставка в середину, - тогда она вставала нулевой и САМА
        // становилась стартом. Со стороны это выглядело так, будто старт прыгает по
        // уровню сам по себе. Раньше на новом уровне была всего одна точка, и заметить
        // это было негде; с появлением пары старт-финиш вылезло сразу.
        //
        // Продлевать маршрут за финиш по-прежнему можно: это осмысленное действие,
        // в отличие от бега до старта.
        // ...но только если точки вообще есть. На пустом пути (дуэльная карта начинается
        // без единой точки) вставка "после старта" означала бы вставку в индекс 1 в
        // список нулевой длины - ровно то, что валилось IndexOutOfBounds.
        if (index == 0 && !waypoints.isEmpty()) index = 1;
        if (index > waypoints.size()) index = waypoints.size();
        if (index < 0) index = 0;

        for (int i = Math.max(0, index - 1); i <= Math.min(waypoints.size() - 1, index); i++) {
            Waypoint waypoint = waypoints.get(i);
            if (waypoint.getLocation().distance(newWaypoint.getLocation()) < MIN_DISTANCE_BETWEEN_POINTS) {
                return false;
            }
        }

        waypoints.add(index, newWaypoint);
        if (primary) refreshWaypoints(level);

        LangOptions.item_editor_points_added.sendMsgActionbar(player);
        return true;
    }

    public static boolean removePoint(
        @NonNull List<Waypoint> waypoints,
        @NonNull Location particleLoc,
        @NonNull Player player,
        @NonNull Level level,
        boolean primary) {

        int bestIndex = -1;
        double minDistance = REMOVE_POINT_DISTANCE;

        for (int i = 0; i < waypoints.size(); i++) {
            double dist = waypoints.get(i).getLocation().distance(particleLoc);
            if (dist < minDistance) {
                minDistance = dist;
                bestIndex = i;
            }
        }

        if (bestIndex != -1) {
            // СТАРТ НЕ УДАЛЯЕТСЯ КИРКОЙ.
            //
            // Нулевая точка - это начало уровня, а не часть пути. Убери её - и стартом
            // молча станет следующая точка, то есть начало уровня уедет на середину
            // трассы. Перенести старт можно кнопкой в меню редактора, над "Точкой спавна".
            if (bestIndex == 0) {
                player.sendMessage(ru.sortix.parkourbeat.utils.text.PbText.of(
                    Lang.raw(PlayerLang.of(player), "auto.edit_track_points_item.remove_waypoint_if_close_enough.1")));
                return false;
            }

            // Минимум - одна точка (старт). Пары точек больше не требуется: финиш
            // теперь не отдельная сущность, а просто последняя точка пути.
            if (waypoints.size() <= 1) {
                LangOptions.item_editor_points_minimumtwo.sendMsgActionbar(player);
                return false;
            }
            waypoints.remove(bestIndex);
            if (primary) refreshWaypoints(level);
            LangOptions.item_editor_points_removed.sendMsgActionbar(player);
            return true;
        }
        return false;
    }

    public static boolean adjustHeight(
        boolean increase,
        @NonNull List<Waypoint> waypoints,
        @NonNull Player player,
        @NonNull EditActivity activity) {
        Waypoint startSegment = getLookingSegment(player, waypoints);

        if (startSegment == null) return false;

        if (increase) {
            activity.setCurrentHeight(
                Math.min(255 - startSegment.getLocation().getY(), startSegment.getHeight() + HEIGHT_CHANGE_VALUE));
        } else {
            activity.setCurrentHeight(Math.max(0, startSegment.getHeight() - HEIGHT_CHANGE_VALUE));
        }
        startSegment.setHeight(activity.getCurrentHeight());
        return true;
    }

    private static void refreshWaypoints(@NonNull Level level) {
        WorldSettings worldSettings = level.getLevelSettings().getWorldSettings();
        if (worldSettings.getWaypoints().isEmpty()) return;

        Vector oldStart = worldSettings.getStartWaypoint();
        Vector oldFinish = worldSettings.getFinishWaypoint();

        worldSettings.updateBorders();

        if (!oldStart.equals(worldSettings.getStartWaypoint())
            || !oldFinish.equals(worldSettings.getFinishWaypoint())) {
            level.getLevelSettings().recalculateWaypoints(level.getWorld());
        }
    }

    private static Waypoint getLookingSegment(@NonNull Player player, @NonNull List<Waypoint> waypoints) {

        for (int i = 0; i < waypoints.size() - 1; i++) {
            Waypoint startSegment = waypoints.get(i);
            Waypoint endSegment = waypoints.get(i + 1);

            if (isLookingAt(
                player,
                startSegment.getLocation().toVector(),
                endSegment.getLocation().toVector())) {
                return startSegment;
            }
        }
        return null;
    }

    public static boolean isLookingAt(@NonNull Player player, @NonNull Vector block1, @NonNull Vector block2) {
        Vector toBlock1 = block1.subtract(player.getEyeLocation().toVector()).setY(0);
        Vector toBlock2 = block2.subtract(player.getEyeLocation().toVector()).setY(0);

        Vector playerDirection = player.getEyeLocation().getDirection().setY(0);

        Vector cross1 = playerDirection.getCrossProduct(toBlock1);
        Vector cross2 = playerDirection.getCrossProduct(toBlock2);

        double dot = cross1.dot(cross2);

        boolean sameHalfPlane = playerDirection.dot(toBlock1) > 0 && playerDirection.dot(toBlock2) > 0;

        return dot < 0 && sameHalfPlane;
    }

    @Nullable
    public static Location getInteractionPoint(@NonNull PlayerInteractEvent event) {
        Location interactionPoint = event.getInteractionPoint();
        if (interactionPoint != null) return interactionPoint;

        Player player = event.getPlayer();
        World world = player.getWorld();
        Location eyeLocation = player.getEyeLocation();
        RayTraceResult rayTrace =
            world.rayTraceBlocks(eyeLocation, eyeLocation.getDirection(), INTERACT_BLOCK_DISTANCE);
        if (rayTrace != null) {
            interactionPoint = rayTrace.getHitPosition().toLocation(world);
        }
        return interactionPoint;
    }

    @Override
    public void onUse(@NonNull PlayerInteractEvent event, @NonNull EditActivity activity) {
        Player player = event.getPlayer();
        Level level = activity.getLevel();

        // НА 2D-УРОВНЕ ПАЛОЧКА ТЯНЕТ ЛИНИЮ, А НЕ СТАВИТ ТОЧКИ.
        // Путь из частиц там не нужен: длина уровня и позиция финиша заданы линией.
        if (ru.sortix.parkourbeat.twod.TwoDManager.isTwoD(level)) {
            this.plugin.get(ru.sortix.parkourbeat.twod.TwoDManager.class).handleWand(event, activity);
            return;
        }

        boolean left;
        switch (event.getAction()) {
            case LEFT_CLICK_BLOCK:
            case LEFT_CLICK_AIR: {
                left = true;
                break;
            }
            case RIGHT_CLICK_BLOCK:
            case RIGHT_CLICK_AIR: {
                left = false;
                break;
            }
            default: {
                return;
            }
        }

        boolean isChanged = false;
        WorldSettings worldSettings = level.getLevelSettings().getWorldSettings();
        List<Waypoint> waypoints = worldSettings.getWaypoints();

        if (player.isSneaking()) {
            if (adjustHeight(left, waypoints, player, activity)) {
                isChanged = true;
            }
        } else {
            Location interactionPoint = getInteractionPoint(event);
            if (interactionPoint == null) {
                return;
            }

            if (left) {
                Waypoint newWaypoint =
                    new Waypoint(interactionPoint, activity.getCurrentHeight(), activity.getCurrentColor(), activity.getCurrentJumpColor());
                if (insertPoint(waypoints, newWaypoint, player, level, true)) {
                    isChanged = true;
                }
            } else {
                if (removePoint(waypoints, interactionPoint, player, level, true)) {
                    isChanged = true;
                }
            }
        }

        if (isChanged) {
            level.getLevelSettings().updateParticleLocations();
        }
    }
}
