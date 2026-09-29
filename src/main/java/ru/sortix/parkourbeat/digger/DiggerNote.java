package ru.sortix.parkourbeat.digger;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;

/**
 * ОДНА НОТА - ОДНА РУДА В ТОННЕЛЕ.
 * <p>
 * Хранятся только координаты блока и тип руды. Таймкода в ноте НЕТ, и это главное
 * решение всего режима: время попадания считается из расстояния до старта и текущей
 * скорости ({@link DiggerTuning#noteMillis(double, double)}). Поменял BPM - вся карта
 * автоматически переехала на новую скорость, ничего не разъехалось и переразмечать
 * ничего не надо.
 * <p>
 * Источник правды - именно этот список в файле уровня, а не блок в мире. Руда,
 * поставленная из обычного инвентаря (декор), в список не попадает и не судится
 * вообще: ломай её сколько хочешь, очков не будет.
 * <p>
 * СОСТОЯНИЯ ЗАБЕГА ЗДЕСЬ НЕТ НАМЕРЕННО. Карта одна на всех, а забегов по ней
 * одновременно может идти сколько угодно, и флаг «сломана» на общем объекте означал бы,
 * что один игрок гасит ноты другому. Что уже разобрано, помнит сам забег.
 */
@Getter
public class DiggerNote {

    private final int x;
    private final int y;
    private final int z;

    @Setter
    private @NonNull DiggerOre ore;

    public DiggerNote(int x, int y, int z, @NonNull DiggerOre ore) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.ore = ore;
    }

    @NonNull
    public Vector toVector() {
        return new Vector(this.x, this.y, this.z);
    }

    @NonNull
    public Location toLocation(@NonNull World world) {
        return new Location(world, this.x, this.y, this.z);
    }

    /** Центр блока - отсюда бьют частицы и растёт луч. */
    @NonNull
    public Location center(@NonNull World world) {
        return new Location(world, this.x + 0.5D, this.y + 0.5D, this.z + 0.5D);
    }

    public boolean isAt(int x, int y, int z) {
        return this.x == x && this.y == y && this.z == z;
    }

    /** Ключ для быстрого поиска ноты по координатам блока. */
    public long key() {
        return key(this.x, this.y, this.z);
    }

    /**
     * Упаковка координат блока в long: 26 бит на X, 12 на Y, 26 на Z - ровно как
     * ванильный BlockPos. Нужна, чтобы искать ноту по сломанному блоку за O(1),
     * а не перебирать список из пары тысяч руд на каждый клик.
     */
    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    @NonNull
    public String serialize() {
        return this.x + ";" + this.y + ";" + this.z + ";" + this.ore.name();
    }

    @Nullable
    public static DiggerNote deserialize(@Nullable String raw) {
        if (raw == null) return null;
        String[] parts = raw.split(";");
        if (parts.length < 4) return null;
        DiggerOre ore = DiggerOre.byName(parts[3]);
        if (ore == null) return null;
        try {
            return new DiggerNote(
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim()),
                ore);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
