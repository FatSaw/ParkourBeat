package ru.sortix.parkourbeat.levels;


import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import ru.sortix.parkourbeat.levels.settings.Portal;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Точки пути внутри портала прячутся и не считаются.
 * Удалять их нельзя: строитель мог собрать там готовый участок и просто
 * проверяет портал, а вернув портал назад он получит путь обратно.
 * <p>
 * ГРАНИЦЫ СКРЫТИЯ — РОВНО СЕРЕДИНА РАМКИ, ни блоком раньше, ни блоком позже.
 * <p>
 * У входного портала путь виден до его середины, у выходного — начинается с середины.
 * Благодаря этому у игрока с обеих сторон остаётся половина рамки видимого пути, и в
 * портал можно нормально запрыгивать и из него выпрыгивать.
 * <p>
 * Никаких поправок на размер рамки, хитбокс или запас по краям здесь нет и быть не
 * должно: от них скрытие расползалось за пределы рамки, и на уменьшенных порталах путь
 * пропадал заметно раньше и дальше самого портала.
 */
@UtilityClass
public class PortalPathFilter {
    public boolean isHidden(@Nullable Level level, @Nullable Location location) {
        if (level == null || location == null) return false;

        List<Portal> portals = level.getLightShow().getPortals();
        if (portals.isEmpty()) return false;

        DirectionChecker checker;
        try {
            checker = level.getLevelSettings().getDirectionChecker();
        } catch (Exception e) {
            return false;
        }
        if (checker == null) return false;

        double coordinate = checker.getCoordinate(location);

        for (Portal portal : portals) {
            if (!portal.isEnabled()) continue;

            // Центр рамки — это и есть точка реза. Позиция стороны портала хранится
            // как центр, поэтому ничего дополнительно считать не нужно.
            double entryMiddle = checker.getCoordinate(
                portal.getEntry().getPosition().toLocation(level.getWorld()));
            double exitMiddle = checker.getCoordinate(
                portal.getExit().getPosition().toLocation(level.getWorld()));

            double min = Math.min(entryMiddle, exitMiddle);
            double max = Math.max(entryMiddle, exitMiddle);
            if (min >= max) continue;

            if (coordinate > min && coordinate < max) return true;
        }
        return false;
    }
}
