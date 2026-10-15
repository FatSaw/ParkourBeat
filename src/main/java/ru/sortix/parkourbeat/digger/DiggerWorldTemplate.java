package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import ru.sortix.parkourbeat.levels.DirectionChecker;
import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * СОЗДАНИЕ ПУСТОГО МИРА ПОД «КОПАТЕЛЯ».
 * <p>
 * Шаблона у режима нет и не будет: тоннель строитель тянет сам, и любой заранее
 * построенный кусок пришлось бы сносить руками. Поэтому мир создаётся пустым, и в
 * нём кладётся ровно один барьер на {@link DiggerTuning#SPAWN_Y}, чтобы строителю
 * было куда встать и от чего начать.
 * <p>
 * Камень, а не барьер: барьер в творческом режиме виден контуром и торчит в кадре,
 * а строителю нужна обычная опора, от которой он начнёт вести тоннель.
 */
public final class DiggerWorldTemplate {

    private DiggerWorldTemplate() {
    }

    /** Координаты той самой единственной точки опоры. */
    public static final int SPAWN_X = 0;
    public static final int SPAWN_Z = 0;

    /**
     * На сколько блоков стартовая линия уходит ВПЕРЁД от блока спавна.
     * <p>
     * Уровень требует, чтобы точка появления была позади начала трассы по ходу
     * движения. Пока линия проходила ровно через блок спавна, проверка честно говорила,
     * что точка задана неверно, и её приходилось переставлять руками на блок назад.
     * <p>
     * Двигаем при этом ЛИНИЮ, а не спавн: спавн должен остаться на единственном блоке
     * площадки, иначе игрок появится в воздухе рядом с ним.
     */
    public static final int START_AHEAD_BLOCKS = 1;

    /**
     * Разложить мир под новый уровень: один блок камня, спавн над ним, вечный полдень
     * и никакой погоды.
     *
     * @return точка, куда ставить строителя
     */
    @NonNull
    public static Location prepare(@NonNull World world) {
        return prepare(world, DiggerTuning.SPAWN_Y);
    }

    /**
     * То же самое, но на заданной высоте.
     * <p>
     * Высота стала ВЫБОРОМ СТРОИТЕЛЯ, а не одним числом на весь сервер: от неё зависит,
     * увидит ли игрок полное небо или его тёмную нижнюю половину (см. {@link
     * ru.sortix.parkourbeat.levels.SkyMode}). Поэтому она приезжает сюда параметром, а
     * {@code spawn_y} из настроек остаётся значением по умолчанию для полного неба.
     */
    @NonNull
    public static Location prepare(@NonNull World world, int y) {

        // ОДИН БЛОК, как и было. Площадка 3x3 проблему не решала - дело было не в
        // опоре под ногами, а в том, ГДЕ стоит сама точка, - а лишний камень строителю
        // потом мешает: его приходится выламывать перед началом тоннеля.
        Block platform = world.getBlockAt(SPAWN_X, y, SPAWN_Z);
        platform.setType(Material.STONE, false);

        world.setSpawnLocation(SPAWN_X, y + 1, SPAWN_Z);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);

        // Правила ставятся ПО ИМЕНИ, а не через константы GameRule: часть из них
        // (например fallDamage) появилась только в 1.17, и прямая ссылка на константу
        // уронила бы класс на 1.16.5 ещё до первой строки.
        rule(world, "doDaylightCycle", false);
        rule(world, "doWeatherCycle", false);
        rule(world, "doMobSpawning", false);
        rule(world, "doTileDrops", false);
        rule(world, "doEntityDrops", false);
        rule(world, "doFireTick", false);
        rule(world, "fallDamage", false);
        rule(world, "announceAdvancements", false);

        return builderSpawn(world, null, y);
    }

    /** Поставить игровое правило, если эта версия сервера его знает. */
    @SuppressWarnings("unchecked")
    private static void rule(@NonNull World world, @NonNull String name, boolean value) {
        GameRule<Boolean> rule = (GameRule<Boolean>) GameRule.getByName(name);
        if (rule == null) return;
        try {
            world.setGameRule(rule, value);
        } catch (Throwable ignored) {
        }
    }

    /** Точка спавна строителя: ровно на барьере, лицом вдоль будущего тоннеля. */
    @NonNull
    public static Location builderSpawn(@NonNull World world) {
        return builderSpawn(world, null);
    }

    /**
     * Точка появления строителя.
     * <p>
     * ВЗГЛЯД ВДОЛЬ ТОННЕЛЯ, а не в фиксированную сторону. Раньше здесь стоял поворот на
     * юг, и на карте, идущей вдоль X, строитель появлялся ЛИЦОМ В СТЕНУ - развёрнутый
     * на девяносто градусов от собственной трассы. Ошибка мелкая, но заметная сразу и
     * каждый раз: первое, что делает человек после создания карты, - разворачивается.
     *
     * @param checker направление уровня; null - взять юг, как было
     */
    public static Location builderSpawn(@NonNull World world,
                                        @javax.annotation.Nullable DirectionChecker checker
    ) {
        return builderSpawn(world, checker, DiggerTuning.SPAWN_Y);
    }

    /** Точка появления строителя на заданной высоте площадки. */
    public static Location builderSpawn(@NonNull World world,
                                        @javax.annotation.Nullable DirectionChecker checker,
                                        int y
    ) {
        Location location = new Location(world,
            SPAWN_X + 0.5D, y + 1.0D, SPAWN_Z + 0.5D);
        location.setYaw(yawOf(checker));
        location.setPitch(0.0f);
        return location;
    }

    /** Поворот, при котором игрок смотрит вдоль уровня. */
    private static float yawOf(@javax.annotation.Nullable DirectionChecker checker) {
        if (checker == null) return 0.0f;
        switch (checker.direction()) {
            case POSITIVE_X:
                return -90.0f;
            case NEGATIVE_X:
                return 90.0f;
            case NEGATIVE_Z:
                return 180.0f;
            default:
                return 0.0f;
        }
    }

    /**
     * Стоит ли опора на месте. Строитель мог снести её в процессе - тогда при
     * следующем входе в редактор кладём обратно, чтобы человек не падал в пустоту.
     */
    public static boolean hasPlatform(@NonNull World world) {
        return hasPlatform(world, DiggerTuning.SPAWN_Y);
    }

    public static boolean hasPlatform(@NonNull World world, int y) {
        return !world.getBlockAt(SPAWN_X, y, SPAWN_Z).getType().isAir();
    }

    /**
     * Вернуть опору на место.
     * <p>
     * ВЫСОТА БЕРЁТСЯ У САМОГО УРОВНЯ, а не из общих настроек. Пока она бралась из
     * {@code spawn_y}, карта с половиной неба получала при каждом входе в редактор
     * лишний камень на сто двадцатой высоте - в сотне блоков над тоннелем, где он
     * никому не нужен, - а под ногами строителя по-прежнему могло быть пусто.
     */
    public static void restorePlatform(@NonNull World world, int y) {
        if (hasPlatform(world, y)) return;
        world.getBlockAt(SPAWN_X, y, SPAWN_Z).setType(Material.STONE, false);
    }

    /**
     * Опора ПОД ТОЧКОЙ СПАВНА УРОВНЯ.
     * <p>
     * Блок в нуле координат помогает ровно до тех пор, пока спавн стоит там же. Уровень
     * же ставит свою точку старта туда, куда её положил шаблон, и строитель появлялся
     * рядом с пустотой, а камень оставался где-то в стороне. Поэтому опора кладётся
     * там, где игрок реально появится.
     */
    public static void ensurePlatformUnder(@NonNull Location spawn) {
        World world = spawn.getWorld();
        if (world == null) return;

        int x = spawn.getBlockX();
        int y = spawn.getBlockY() - 1;
        int z = spawn.getBlockZ();
        if (y < 0) return;

        if (!world.getBlockAt(x, y, z).getType().isAir()) return;
        world.getBlockAt(x, y, z).setType(Material.STONE, false);
    }
}
