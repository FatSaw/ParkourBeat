package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * ЭФФЕКТЫ РАЗРУШЕНИЯ.
 * <p>
 * Базовый слой: осколки блока, цветное кольцо, луч вверх и звук. Ванильное
 * разрушение сыплет скупую горстку частиц, поэтому осколков здесь намеренно в разы
 * больше - множитель в {@link DiggerTuning#CRACK_MULTIPLIER}.
 * <p>
 * ВСЁ РИСУЕТСЯ АДРЕСНО. По одному тоннелю одновременно едут разные люди, и чужие
 * искры в кадре - это не «атмосферно», а мешанина, в которой не видно собственных нот.
 * Поэтому ни одной частицы через мир: только {@code player.spawnParticle}.
 */
public final class DiggerEffects {

    private DiggerEffects() {
    }

    /** Сколько частиц реально сыпать с учётом общей плотности. */
    private static int count(double base) {
        return Math.max(1, (int) Math.round(base * DiggerTuning.PARTICLE_DENSITY));
    }

    /**
     * Попадание по руде.
     *
     * @param multiplier текущий множитель комбо - от него растёт масштаб залпа
     */
    public static void hit(@NonNull Player player,
                           @NonNull Location center,
                           @NonNull DiggerOre ore,
                           @NonNull DiggerHit hit,
                           int multiplier,
                           @javax.annotation.Nullable String customSound,
                           float customPitch
    ) {
        // ТОЛЬКО ЗВУК. Ни лучей, ни цветной пыли: на плотном треке любые частицы на
        // каждое попадание закрывают собой следующие руды. Всё оформление разрушения -
        // дело строителя: он поставит свои вспышки и цвета на нужный таймкод в
        // световом шоу, где им и место.
        if (customSound != null) {
            player.playSound(center, customSound, 1.0f, customPitch);
        } else {
            player.playSound(center, ore.getSound(), 1.0f, ore.getPitch());
        }
    }

    /** Луч END_ROD вверх - самая заметная часть попадания на записи. */
    public static void beam(@NonNull Player player, @NonNull Location from, double height) {
        if (height <= 0.0D) return;
        int steps = count(height * 4);
        for (int i = 0; i < steps; i++) {
            double y = height * i / (double) steps;
            player.spawnParticle(Particle.END_ROD,
                from.clone().add(0.0D, y, 0.0D), 1, 0.02D, 0.0D, 0.02D, 0.0D);
        }
    }

    /** Промах по ноте: только звук, тихо. */
    public static void miss(@NonNull Player player, @NonNull Location center) {
        player.playSound(center, DiggerHit.MISS.getSound(), 0.3f, DiggerHit.MISS.getPitch());
    }

    /**
     * Удар в пустоту.
     * <p>
     * Молча. Комбо и так рвётся, это видно по панели, а звук на каждый лишний взмах
     * превращается в трещотку поверх музыки - ради которой сюда и приходят.
     */
    public static void wasted(@NonNull Player player) {
    }
}
