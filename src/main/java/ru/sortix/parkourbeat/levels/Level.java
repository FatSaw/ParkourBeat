package ru.sortix.parkourbeat.levels;

import lombok.Getter;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.levels.settings.LevelSettings;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.world.Cuboid;

import java.util.UUID;

@Getter
public class Level {
    private final @NonNull LevelSettings levelSettings;
    private final @NonNull World world;
    private final @NonNull Cuboid cuboid;
    private boolean isEditing = false;

    public Level(@NonNull LevelSettings levelSettings, @NonNull World world) {
        this.levelSettings = levelSettings;
        this.world = world;
        DirectionChecker.Direction direction = this.levelSettings.getWorldSettings().getDirection();
        Cuboid configured = Settings.getLevelFixedEditableArea().get(direction);
        if (configured == null) {
            throw new IllegalArgumentException("Not fond config of direction " + direction);
        }
        // В конфиге записана область на один чанк. Широкий уровень занимает столько же
        // по длине, но шире по поперечной оси, поэтому растягиваем её здесь, а не
        // держим в конфиге отдельный блок на каждый размер.
        // 360-уровень - это КУБ из выбранных чанков, а не полоса: длина у него
        // ограничена так же, как ширина и высота, и продлевать её назад незачем.
        this.cuboid = this.levelSettings.getGameSettings().isDiggerLevel()
            ? diggerArea(configured, direction)
            : this.levelSettings.getGameSettings().isThreeSixtyLevel()
            ? threeSixtyArea(configured, this.levelSettings)
            : extendBack(
            widen(configured,
                this.levelSettings.getGameSettings().getWidthInBlocks(),
                this.levelSettings.getWorldSettings().getSpawn().toVector(),
                direction),
            direction);
        // Контроллер частиц обязан знать свой уровень СРАЗУ. Скрытие пути внутри порталов
        // решается на этапе сборки списка частиц, а раньше уровень проставлялся уже ПОСЛЕ
        // первой сборки — и путь сквозь порталы оставался видимым до первой правки портала.
        this.levelSettings.getParticleController().setColorCueLevel(this);
    }

    /**
     * Насколько область строительства продлевается НАЗАД, против хода уровня.
     * <p>
     * Задник уровня (то, что игрок видит за спиной на старте, и место под оформление
     * перед первым прыжком) раньше упирался в границу из конфига. Продление идёт
     * только назад, поэтому ни на длину самого уровня, ни на его ширину это не влияет.
     */
    public static final int EXTRA_BACK_LENGTH = 1024;

    @NonNull
    private static Cuboid extendBack(@NonNull Cuboid base,
                                     @NonNull DirectionChecker.Direction direction) {
        org.bukkit.util.Vector min = base.getMin();
        org.bukkit.util.Vector max = base.getMax();

        double extra = EXTRA_BACK_LENGTH;
        return switch (direction) {
            case POSITIVE_X -> new Cuboid(
                new org.bukkit.util.Vector(min.getX() - extra, min.getY(), min.getZ()),
                max.clone(), base.getWorld());
            case NEGATIVE_X -> new Cuboid(
                min.clone(),
                new org.bukkit.util.Vector(max.getX() + extra, max.getY(), max.getZ()),
                base.getWorld());
            case POSITIVE_Z -> new Cuboid(
                new org.bukkit.util.Vector(min.getX(), min.getY(), min.getZ() - extra),
                max.clone(), base.getWorld());
            case NEGATIVE_Z -> new Cuboid(
                min.clone(),
                new org.bukkit.util.Vector(max.getX(), max.getY(), max.getZ() + extra),
                base.getWorld());
        };
    }

    /**
     * ПЛОЩАДКА «КОПАТЕЛЯ» - ДЛИННЫЙ КОРИДОР.
     * <p>
     * Тоннель тянется на всю длину трека: три минуты на десяти блоках в секунду - это
     * почти две тысячи блоков. Обычная площадка длиной в один чанк обрывала бы стройку
     * на восемнадцатом метре, и построить карту было бы физически невозможно.
     * <p>
     * Поэтому область растягивается по ходу уровня во всю разрешённую длину и на сотню
     * блоков в стороны - тоннелю хватает с запасом на любое оформление вокруг.
     */
    public static final int DIGGER_LENGTH = 60000;
    public static final int DIGGER_HALF_WIDTH = 128;

    @NonNull
    private static Cuboid diggerArea(@NonNull Cuboid base,
                                     @NonNull DirectionChecker.Direction direction) {
        org.bukkit.util.Vector min = base.getMin();
        org.bukkit.util.Vector max = base.getMax();

        double minY = 0.0D;
        double maxY = 255.0D;

        return switch (direction) {
            case POSITIVE_X -> new Cuboid(
                new org.bukkit.util.Vector(min.getX() - EXTRA_BACK_LENGTH, minY, min.getZ() - DIGGER_HALF_WIDTH),
                new org.bukkit.util.Vector(max.getX() + DIGGER_LENGTH, maxY, max.getZ() + DIGGER_HALF_WIDTH),
                base.getWorld());
            case NEGATIVE_X -> new Cuboid(
                new org.bukkit.util.Vector(min.getX() - DIGGER_LENGTH, minY, min.getZ() - DIGGER_HALF_WIDTH),
                new org.bukkit.util.Vector(max.getX() + EXTRA_BACK_LENGTH, maxY, max.getZ() + DIGGER_HALF_WIDTH),
                base.getWorld());
            case POSITIVE_Z -> new Cuboid(
                new org.bukkit.util.Vector(min.getX() - DIGGER_HALF_WIDTH, minY, min.getZ() - EXTRA_BACK_LENGTH),
                new org.bukkit.util.Vector(max.getX() + DIGGER_HALF_WIDTH, maxY, max.getZ() + DIGGER_LENGTH),
                base.getWorld());
            case NEGATIVE_Z -> new Cuboid(
                new org.bukkit.util.Vector(min.getX() - DIGGER_HALF_WIDTH, minY, min.getZ() - DIGGER_LENGTH),
                new org.bukkit.util.Vector(max.getX() + DIGGER_HALF_WIDTH, maxY, max.getZ() + EXTRA_BACK_LENGTH),
                base.getWorld());
        };
    }

    /**
     * ПЛОЩАДКА 360-УРОВНЯ - ЭТО РОВНО ВЫБРАННЫЕ ЧАНКИ.
     * <p>
     * Выбран один чанк - строить можно ровно в этом чанке: те же 16 блоков по ширине
     * и те же 16 по высоте, что видно в клиенте по границам чанка (F3+G). Поэтому
     * область считается не «от спавна плюс-минус половина», а по чанковой сетке: берётся
     * чанк, в котором стоит спавн, и его секция по высоте. Иначе граница проходила бы
     * посреди чанка и совпасть с тем, что видит строитель, не могла бы в принципе.
     * <p>
     * Ограничены ВСЕ ТРИ оси. Выбрано два чанка - значит куб 2x2x2 чанка вокруг спавна,
     * и ни вперёд, ни назад за него не выйти: 360-уровень - это коробка, а не полоса.
     */
    @NonNull
    private static Cuboid threeSixtyArea(@NonNull Cuboid base, @NonNull LevelSettings settings) {
        org.bukkit.util.Vector min = base.getMin();
        org.bukkit.util.Vector max = base.getMax();

        int chunks = Math.max(1, settings.getGameSettings().getChunkWidth());
        org.bukkit.Location spawn = settings.getWorldSettings().getSpawn();

        // При размере больше одного чанка лишние чанки добавляются симметрично,
        // с приоритетом «назад и вниз» - чтобы спавн не оказался у самого края.
        int firstChunkX = (spawn.getBlockX() >> 4) - (chunks - 1) / 2;
        double minX = firstChunkX * 16.0D;
        double maxX = minX + chunks * 16.0D - 1;

        int firstChunkZ = (spawn.getBlockZ() >> 4) - (chunks - 1) / 2;
        double minZ = firstChunkZ * 16.0D;
        double maxZ = minZ + chunks * 16.0D - 1;

        // Убираем ограничение по высоте: 360-уровню лучше быть вертикальным столбом,
        // чтобы можно было строить высокие башни и глубокие шахты во всю высоту мира.
        double minY = min.getY();
        double maxY = max.getY();

        return new Cuboid(
            new org.bukkit.util.Vector(minX, minY, minZ),
            new org.bukkit.util.Vector(maxX, maxY, maxZ),
            base.getWorld());
    }

    /**
     * ПЛОЩАДКА ЦЕНТРИРУЕТСЯ ПО ТОЧКЕ СПАВНА.
     * <p>
     * Раньше широкая площадка растягивалась от полосы, записанной в конфиге (Z 0..15),
     * поровну в обе стороны - и получалась область Z -24..39. Но широкий шаблон
     * построен от нуля вправо, на все четыре чанка (Z 0..63), поэтому его постройка
     * оказывалась прижата к правому краю: слева 24 блока пустого места, справа
     * не хватало 24 блоков уже построенного. Это и была «кривая граница».
     * <p>
     * Теперь отсчёт идёт от спавна: в какую бы точку ни поставили старт, площадка
     * ложится вокруг неё симметрично. Для узкого уровня результат тот же, что и
     * раньше (спавн стоит в середине полосы 0..15), поэтому существующие уровни
     * ничего не замечают.
     *
     * @param widthInBlocks желаемая ширина по оси Z в блоках
     * @param spawn         точка спавна уровня - центр площадки
     * @param direction     ход уровня: от него зависит, где у площадки правая сторона
     */
    @NonNull
    private static Cuboid widen(@NonNull Cuboid base, int widthInBlocks,
                                @NonNull org.bukkit.util.Vector spawn,
                                @NonNull DirectionChecker.Direction direction) {
        org.bukkit.util.Vector min = base.getMin();
        org.bukkit.util.Vector max = base.getMax();

        int currentWidth = (int) (max.getZ() - min.getZ()) + 1;
        if (widthInBlocks <= currentWidth) return base;

        // Ширина чётная, поэтому идеально «по центру блока» её не поделить: слева
        // от блока спавна оказывается на один блок больше, чем справа. Половину
        // блока туда-сюда не заметит никто, а вот 24 блока перекоса - заметили.
        int spawnZ = (int) Math.floor(spawn.getZ());
        int half = widthInBlocks / 2;

        // Ширина чётная, поэтому ровно посередине блок спавна не встаёт: с одной
        // стороны от него всегда на блок больше. Сдвигаем площадку на этот блок ВПРАВО
        // по ходу уровня - тогда лишний блок оказывается слева, где он и нужен.
        int shift = new DirectionChecker(direction).isNegative() ? -1 : 1;

        double minZ = spawnZ - half + shift;
        double maxZ = minZ + widthInBlocks - 1;

        return new Cuboid(
            new org.bukkit.util.Vector(min.getX(), min.getY(), minZ),
            new org.bukkit.util.Vector(max.getX(), max.getY(), maxZ),
            base.getWorld());
    }

    public void setEditing(boolean isEditing) {
        this.isEditing = isEditing;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Level)) return false;
        return ((Level) other).getUniqueId().equals(this.getUniqueId());
    }

    @Override
    public int hashCode() {
        return this.getUniqueId().hashCode();
    }

    @NonNull
    public Component getDisplayName() {
        return this.levelSettings.getGameSettings().getDisplayName();
    }

    @NonNull
    public UUID getUniqueId() {
        return this.levelSettings.getGameSettings().getUniqueId();
    }

    @NonNull
    public Location getSpawn() {
        return this.levelSettings.getWorldSettings().getSpawn();
    }

    /**
     * Pushes the view distances of this level into the runtime pieces that use them.
     */
    public void applyViewDistances() {
        this.levelSettings.getParticleController()
            .setViewDistance(this.levelSettings.getWorldSettings().getParticleViewDistance());

        this.levelSettings.getParticleController().setColorCueLevel(this);
        this.levelSettings.getParticleController()
            .setColorCues(this.getLightShow().getParticleColorCues());
    }

    public void refreshParticleColorCues() {
        this.levelSettings.getParticleController().setColorCueLevel(this);
        this.levelSettings.getParticleController()
            .setColorCues(this.getLightShow().getParticleColorCues());
    }

    @NonNull
    public LightShowSettings getLightShow() {
        return this.levelSettings.getWorldSettings().getLightShow();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isLevelAccessibleForPlaying(@NonNull Player player, boolean bypassForAdmins, boolean sendMessages) {
        GameSettings settings = this.levelSettings.getGameSettings();
        if (settings.isAccessibleForPlaying(player, bypassForAdmins)) return true;
        if (sendMessages) LangOptions.level_play_noaccess.sendMsg(player);
        return false;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isLevelAccessibleForEditing(@NonNull Player player, boolean bypassForAdmins, boolean sendMessages) {
        GameSettings settings = this.levelSettings.getGameSettings();
        if (!settings.canEdit(player, bypassForAdmins, false)) {
            if (sendMessages) LangOptions.level_editor_cantedit_notowner.sendMsg(player);
            return false;
        }
        if (settings.getModerationStatus() == ModerationStatus.ON_MODERATION
            && !player.hasPermission(PermissionConstants.EDIT_OTHERS_LEVELS_ON_MODERATION)
        ) {
            if (sendMessages) LangOptions.level_editor_cantedit_onmoderation.sendMsg(player);
            return false;
        }
        if (sendMessages) settings.canEdit(player, bypassForAdmins, true); // send bypass message
        return true;
    }

    public boolean isLocationInside(@NonNull Location location) {
        if (location.getWorld() != this.world) return false;
        return this.cuboid.isInside(location);
    }

    public boolean isPositionInside(double x, double y, double z) {
        return this.cuboid.isInside(x, y, z);
    }

    /**
     * Лежит ли столбец блоков в границах уровня, БЕЗ проверки высоты.
     * <p>
     * Высоту здесь спрашивать нельзя. У 360-уровня площадка ограничена и по вертикали,
     * и проверка «на нулевой высоте» отвечала бы «снаружи» для всего уровня целиком -
     * а от этого ответа зависит, какие чанки сохранять и где строителю можно работать.
     */
    public boolean isColumnInside(double x, double z) {
        return this.cuboid.isInside(x, this.cuboid.getMin().getY(), z);
    }

    @SuppressWarnings({"PointlessBitwiseExpression", "OctalInteger", "RedundantIfStatement"})
    public boolean isChunkInside(@NonNull Chunk chunk) {
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        if (this.isColumnInside((chunkX << 4) | 00, (chunkZ << 4) | 00)) return true;
        if (this.isColumnInside((chunkX << 4) | 15, (chunkZ << 4) | 00)) return true;
        if (this.isColumnInside((chunkX << 4) | 00, (chunkZ << 4) | 15)) return true;
        if (this.isColumnInside((chunkX << 4) | 15, (chunkZ << 4) | 15)) return true;

        return false;
    }
}
