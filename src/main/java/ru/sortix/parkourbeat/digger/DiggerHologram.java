package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.twod.TwoDEntityUtils;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;

/**
 * ПАНЕЛЬ ИЗ СТРОК, ЛЕТЯЩАЯ РЯДОМ С ИГРОКОМ.
 * <p>
 * На 1.16.5 голограмма - это стойка брони с видимым именем, по одной на строку.
 * Текстовых дисплеев здесь нет, поэтому строки складываются стопкой, а расстояние
 * между ними подбирается вручную.
 * <p>
 * ГЛАВНОЕ - ПЛАВНОСТЬ. Панель нельзя телепортировать каждый тик: клиент почти не
 * сглаживает такие перемещения, и текст начинает дрожать тем сильнее, чем быстрее
 * летит игрок. Поэтому строки едут ТОЧНО ТАК ЖЕ, КАК НОСИТЕЛЬ: им задаётся та же
 * скорость и та же редкая коррекция позиции. Обе сущности дрейфуют одинаково, и
 * панель стоит относительно камеры неподвижно, хотя обе летят через мир.
 * <p>
 * Имя переписывается только когда текст реально изменился: смена имени - это
 * отдельный пакет метаданных на каждую строку, и слать его каждый тик незачем.
 * <p>
 * Панель ЛИЧНАЯ. Стойка брони - обычная сущность, её видит весь сервер, поэтому
 * пакеты о ней перехватываются и не выпускаются никому, кроме владельца: на турнире
 * иначе каждый видел бы вокруг себя тридцать чужих счётчиков.
 */
public class DiggerHologram {

    private final @NonNull World world;
    /** Кому видна панель. Всем остальным её пакеты не выпускаются вовсе. */
    private final @NonNull java.util.UUID owner;
    private final List<ArmorStand> lines = new ArrayList<>();
    private final List<String> texts = new ArrayList<>();

    /** Смещение панели относительно носителя: вперёд, вбок, вверх. */
    private final @NonNull Vector offset;
    private final @NonNull Vector punchDirection;
    private final long[] linePunchTimes;

    /** Длительность подскока одной строки. */
    private static final long PUNCH_MILLIS = 140L;

    public DiggerHologram(@NonNull World world,
                          @NonNull java.util.UUID owner,
                          @NonNull Vector offset,
                          @NonNull Vector punchDirection,
                          int lineCount
    ) {
        this.world = world;
        this.owner = owner;
        this.offset = offset.clone();
        this.punchDirection = punchDirection.clone();
        this.linePunchTimes = new long[lineCount];
        for (int i = 0; i < lineCount; i++) {
            this.texts.add("");
        }
    }

    /** Создать строки. Первая строка - верхняя, дальше вниз. */
    public void spawn(@NonNull Location anchor) {
        this.remove();
        for (int i = 0; i < this.texts.size(); i++) {
            Location at = this.lineLocation(anchor, i);
            ArmorStand stand = (ArmorStand) this.world.spawnEntity(at, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
            stand.setCollidable(false);
            stand.setCustomNameVisible(false);

            // Панель личная: чужим игрокам пакеты об этой стойке не уйдут.
            DiggerEntityHider.own(stand.getEntityId(), this.owner);
            DiggerEntityHider.drive(stand.getEntityId(), this.owner);
            this.lines.add(stand);
        }
    }

    @NonNull
    private Location lineLocation(@NonNull Location anchor, int index) {
        Location result = anchor.clone().add(this.offset);
        result.setY(result.getY() - index * DiggerTuning.HOLOGRAM_LINE_GAP);
        result.setYaw(0.0f);
        result.setPitch(0.0f);
        return result;
    }

    /**
     * Задать текст. Пустая строка гасит нить целиком, а не оставляет пустое имя:
     * невидимая подпись всё равно занимает место в стопке и раздвигает соседей.
     */
    public void setLine(int index, @NonNull String text) {
        if (index < 0 || index >= this.texts.size()) return;
        if (this.texts.get(index).equals(text)) return;
        this.texts.set(index, text);

        if (index >= this.lines.size()) return;
        ArmorStand stand = this.lines.get(index);
        if (stand == null || stand.isDead()) return;

        if (text.isEmpty()) {
            stand.setCustomNameVisible(false);
            stand.customName(null);
        } else {
            stand.customName(PbText.of(text));
            stand.setCustomNameVisible(true);
        }
    }

    /**
     * Переставить панель. Вызывается каждый тик вместе с сиденьем.
     * <p>
     * Через тот же прямой вызов, что и сиденье: обычный телепорт стойки брони каждый
     * тик даёт заметное дрожание текста, а прямая установка позиции уходит клиенту
     * обычным пакетом движения и сглаживается им самим.
     */
    public void move(@NonNull Location anchor) {
        // РЫВОК СТРОК: ЧИСТО ПОЛОЖЕНИЕ, БЕЗ ЕДИНОГО ИЗМЕНЕНИЯ ТЕКСТА.
        //
        // Сам текст при ударе не меняется вообще - ни цвет, ни жирность, ни пробелы.
        // Строка просто подскакивает и оседает обратно. Так это и выглядит в ритм-играх:
        // панель дёргается, а числа на ней остаются теми же самыми, и глаз ловит именно
        // движение, а не мигание другого начертания.
        long now = System.currentTimeMillis();

        for (int i = 0; i < this.lines.size(); i++) {
            ArmorStand stand = this.lines.get(i);
            if (stand == null || !stand.isValid()) continue;

            Location at = this.lineLocation(anchor, i);

            long since = now - this.linePunchTimes[i];
            if (since >= 0L && since < PUNCH_MILLIS) {
                // Полсинусоиды: подскок ВПЕРЁД НА ИГРОКА и мягкое возвращение
                double punch = DiggerTuning.HOLOGRAM_PUNCH_DISTANCE * Math.sin(Math.PI * since / (double) PUNCH_MILLIS);
                at.add(this.punchDirection.getX() * punch,
                    this.punchDirection.getY() * punch,
                    this.punchDirection.getZ() * punch);
            }

            TwoDEntityUtils.moveRaw(stand, at.getX(), at.getY(), at.getZ(), 0.0f, 0.0f);

            // Панель едет вместе с камерой, и её позиция тоже уходит каждый такт:
            // иначе камера плывёт гладко, а текст рядом с ней дёргается раз в три такта.
            org.bukkit.entity.Player viewer = org.bukkit.Bukkit.getPlayer(this.owner);
            if (viewer != null) {
                DiggerEntityHider.pushPosition(viewer, stand.getEntityId(),
                    at.getX(), at.getY(), at.getZ(), 0.0f, 0.0f);
            }
        }
    }

    /** Отметить удар по конкретной строке. */
    public void punch(int lineIndex) {
        if (lineIndex >= 0 && lineIndex < this.linePunchTimes.length) {
            this.linePunchTimes[lineIndex] = System.currentTimeMillis();
        }
    }

    public void remove() {
        for (ArmorStand stand : this.lines) {
            if (stand == null) continue;
            DiggerEntityHider.forget(stand.getEntityId());
            if (!stand.isDead()) stand.remove();
        }
        this.lines.clear();
    }
}
