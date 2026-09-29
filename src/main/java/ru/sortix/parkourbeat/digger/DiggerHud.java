package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.utils.text.Theme;

/**
 * ВЕСЬ ИНТЕРФЕЙС ЗАБЕГА - ДВЕ ПАНЕЛИ В МИРЕ.
 * <p>
 * Ни боссбара, ни экшнбара, ни скорборда: всё это лепится к краям экрана, а в
 * ритм-игре глаз держится центра и по краям попросту не смотрит.
 * <p>
 * СЛЕВА, сверху вниз: подпись «КОМБО», само число, черта, счёт, точность и ранг.
 * Комбо стоит выше счёта потому, что рвётся оно мгновенно, а счёт растёт плавно:
 * на резкое изменение глаз реагирует, на плавное нет.
 * <p>
 * СПРАВА: множитель и время трека.
 */
public class DiggerHud {

    /** КОМБО / число / черта / счёт / точность / ранг. */
    private static final int LEFT_LINES = 6;

    /** Множитель / время. */
    private static final int RIGHT_LINES = 2;

    private final @NonNull Player player;
    private final boolean axisX;
    private final int sign;

    private final @NonNull DiggerHologram left;
    private final @NonNull DiggerHologram right;

    public DiggerHud(@NonNull World world, @NonNull Player player, boolean axisX, int sign) {
        this.player = player;
        this.axisX = axisX;
        this.sign = sign;

        // Вектор, направленный из плоскости панели прямо навстречу игроку
        Vector punchDir = axisX ? new Vector(-sign, 0, 0) : new Vector(0, 0, -sign);

        this.left = new DiggerHologram(world, player.getUniqueId(), this.offset(true, 0.0D), punchDir, LEFT_LINES);
        // Правая панель висит ниже: её верхняя строка - множитель, а он должен
        // оказаться на одной высоте с кольцом, а не над ним.
        this.right = new DiggerHologram(world, player.getUniqueId(), this.offset(false, -0.5D), punchDir, RIGHT_LINES);
    }

    /**
     * Смещение панели относительно носителя.
     * <p>
     * Вперёд - чтобы панель не оказалась за спиной и не пропала из кадра. Вбок - чтобы
     * не закрывала руды. Вверх - на уровень глаз, а не под ноги.
     */
    @NonNull
    private Vector offset(boolean isLeft, double extraHeight) {
        double forward = DiggerTuning.HOLOGRAM_FORWARD;
        double side = DiggerTuning.HOLOGRAM_SIDE * (isLeft ? -1 : 1) * this.sign;
        double height = DiggerTuning.HOLOGRAM_HEIGHT + extraHeight;

        return this.axisX
            ? new Vector(forward * this.sign, height, side)
            : new Vector(side, height, forward * this.sign);
    }

    public void spawn(@NonNull Location anchor) {
        if (!DiggerTuning.HOLOGRAMS) return;
        this.left.spawn(anchor);
        this.right.spawn(anchor);
    }

    /** Переставить обе панели вслед за сиденьем. */
    public void move(@NonNull Location anchor) {
        if (!DiggerTuning.HOLOGRAMS) return;
        this.left.move(anchor);
        this.right.move(anchor);
    }

    /**
     * Обновить содержимое панелей.
     *
     * @param lastHit прибавка за последний удар или пустая строка
     * @param millis  время трека
     * @param total   длительность карты
     */
    public void update(@NonNull DiggerScore score,
                       @NonNull String lastHit,
                       long millis,
                       long total
    ) {
        if (!DiggerTuning.HOLOGRAMS) return;

        this.left.setLine(0, Theme.V_GRAY + "КОМБО");
        this.left.setLine(1, Theme.V_WHITE + "&l" + score.getCombo());
        this.left.setLine(2, Theme.V_DARK_GRAY + "&m        ");
        this.left.setLine(3, Theme.V_WHITE + formatScore(score.getScore()));
        this.left.setLine(4, Theme.V_GRAY + String.format("%.1f", score.accuracy()) + " %");
        this.left.setLine(5, score.grade().getFormatted()
            + (lastHit.isEmpty() ? "" : "   " + lastHit));

        this.right.setLine(0, Theme.V_WHITE + "&lx" + score.multiplier());
        this.right.setLine(1, Theme.V_DARK_GRAY + formatTime(millis)
            + " " + Theme.V_GRAY + "| " + Theme.V_DARK_GRAY + formatTime(total));
    }

    /** Выдвигает только число комбо (строка с индексом 1 на левой панели) */
    public void punchCombo() {
        this.left.punch(1);
    }

    /** Выдвигает только множитель x1-x8 (строка с индексом 0 на правой панели) */
    public void punchMultiplier() {
        this.right.punch(0);
    }

    public void remove() {
        this.left.remove();
        this.right.remove();
    }

    // ==================== ФОРМАТ ====================

    /** Счёт с пробелом между тысячами: 2 325 читается с одного взгляда, 2325 - нет. */
    @NonNull
    private static String formatScore(long score) {
        String raw = Long.toString(score);
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (int i = raw.length() - 1; i >= 0; i--) {
            result.append(raw.charAt(i));
            if (++count % 3 == 0 && i > 0) result.append(' ');
        }
        return result.reverse().toString();
    }

    @NonNull
    private static String formatTime(long millis) {
        if (millis < 0L) millis = 0L;
        long seconds = millis / 1000L;
        return (seconds / 60L) + ":" + String.format("%02d", seconds % 60L);
    }

}
