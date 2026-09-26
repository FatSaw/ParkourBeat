package ru.sortix.parkourbeat.twod;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * САМ КУБИК.
 * <p>
 * На сервере 1.19.4+ это BlockDisplay: только он умеет крутиться вокруг своей оси,
 * а без вращения при прыжке кубик из Geometry Dash не читается совсем. Всё делается
 * через рефлексию, поэтому код собирается и работает на любой версии: если BlockDisplay
 * нет, кубик остаётся обычным falling_block, просто без кувырков.
 * <p>
 * Клиенты на старых версиях (те, кто заходит через ViaVersion) вращения не увидят,
 * но и ничего не сломают: сущность для них просто не отобразится нужным образом,
 * а игровая логика вращения вообще не касается.
 */
public class TwoDCubeEntity {

    private static Class<?> blockDisplayClass;
    private static boolean displayChecked = false;
    private static boolean displayUnavailable = false;

    private static Method setBlockMethod;
    private static Method setTransformationMethod;
    private static Method setInterpolationDurationMethod;
    private static Method setInterpolationDelayMethod;
    private static Constructor<?> transformationConstructor;
    private static Constructor<?> vector3fConstructor;
    private static Constructor<?> quaternionfConstructor;

    public enum Style {
        DISPLAY,
        HEAD,
        FALLING_BLOCK
    }

    private final @NonNull World world;
    private final @NonNull Entity entity;
    private final @NonNull Style style;

    private TwoDCubeEntity(@NonNull World world, @NonNull Entity entity, @NonNull Style style) {
        this.world = world;
        this.entity = entity;
        this.style = style;
    }

    @NonNull
    public Style getStyle() {
        return this.style;
    }

    /**
     * Может ли кубик ехать сам, без арморстенда-носителя.
     * <p>
     * falling_block - НЕ может, и это не вопрос удобства. Ванильный сервер рассылает
     * его позицию раз в 20 тиков (у этого типа сущностей такой интервал трекера),
     * поэтому своим ходом он движется рывками раз в секунду. Пассажир же получает
     * положение от носителя, а арморстенд обновляется каждые три тика и клиентом
     * сглаживается - отсюда и плавность.
     * <p>
     * Дисплей и арморстенд-с-блоком таких ограничений не имеют и едут сами.
     */
    public boolean isStandalone() {
        return this.style != Style.FALLING_BLOCK;
    }

    @NonNull
    public Entity getEntity() {
        return this.entity;
    }

    public boolean isDisplay() {
        return this.style == Style.DISPLAY;
    }

    public boolean canRotate() {
        return this.style == Style.DISPLAY || this.style == Style.HEAD;
    }

    public boolean isValid() {
        return this.entity.isValid();
    }

    public void remove() {
        this.entity.remove();
    }

    /**
     * Высота, на которую надо ставить сущность, чтобы низ кубика оказался на {@code bottomY}.
     * <p>
     * BlockDisplay рисует блок от своего угла, поэтому его мы центрируем сдвигом внутри
     * трансформации и ставим ровно в центр кубика; falling_block уже отрисован от низа.
     */
    public double toEntityY(double bottomY) {
        return switch (this.style) {
            case DISPLAY -> bottomY + 0.5D;
            // Блок рисуется на голове арморстенда, а не в его ногах.
            case HEAD -> bottomY + 0.5D + TwoDTuning.CUBE_HEAD_Y_OFFSET;
            case FALLING_BLOCK -> bottomY;
        };
    }

    @Nullable
    private static Class<?> displayClass() {
        if (!displayChecked) {
            displayChecked = true;
            try {
                blockDisplayClass = Class.forName("org.bukkit.entity.BlockDisplay");
                Class.forName("org.joml.Quaternionf");
                Class.forName("org.joml.Vector3f");
                Class.forName("org.bukkit.util.Transformation");
            } catch (Throwable t) {
                blockDisplayClass = null;
                displayUnavailable = true;
            }
        }
        return displayUnavailable ? null : blockDisplayClass;
    }

    /**
     * Создать кубик. Сначала пробуем BlockDisplay, при любой осечке откатываемся
     * на falling_block: без кубика уровень не играется вообще, а без вращения играется.
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public static TwoDCubeEntity spawn(@NonNull World world, @NonNull Location location,
                                       @NonNull BlockData blockData) {
        String requested = TwoDTuning.CUBE_STYLE == null
            ? "AUTO" : TwoDTuning.CUBE_STYLE.trim().toUpperCase(java.util.Locale.ROOT);

        if (requested.equals("HEAD")) {
            TwoDCubeEntity head = spawnHead(world, location, blockData);
            if (head != null) return head;
        }

        Class<?> displayClass = requested.equals("FALLING_BLOCK") || requested.equals("HEAD")
            ? null : displayClass();
        if (displayClass != null && TwoDTuning.CUBE_ROTATION) {
            try {
                Location at = location.clone();
                at.setY(at.getY() + 0.5D);

                Entity spawned = world.spawn(at, (Class<? extends Entity>) displayClass);

                if (setBlockMethod == null) {
                    setBlockMethod = displayClass.getMethod("setBlock", BlockData.class);
                }
                setBlockMethod.invoke(spawned, blockData);

                // Дисплей ездит сам по себе, без арморстенда: клиент сам сглаживает
                // его перемещение между телепортами, и это выходит ровнее, чем
                // тащить его пассажиром.
                try {
                    spawned.getClass().getMethod("setTeleportDuration", int.class)
                        .invoke(spawned, 2);
                } catch (Throwable ignored) {
                }
                try {
                    spawned.getClass().getMethod("setViewRange", float.class)
                        .invoke(spawned, 4.0f);
                } catch (Throwable ignored) {
                }

                TwoDCubeEntity result = new TwoDCubeEntity(world, spawned, Style.DISPLAY);
                result.applyRotation(0.0D, new Vector(0, 0, 1), 0);
                return result;
            } catch (Throwable t) {
                displayUnavailable = true;
                org.bukkit.Bukkit.getLogger().warning(
                    "[ParkourBeat] 2D: BlockDisplay недоступен, кубик будет без вращения: " + t);
            }
        }

        org.bukkit.entity.FallingBlock fallingBlock = world.spawnFallingBlock(location, blockData);
        fallingBlock.setGravity(false);
        fallingBlock.setDropItem(false);
        fallingBlock.setHurtEntities(false);
        fallingBlock.setTicksLived(1);
        return new TwoDCubeEntity(world, fallingBlock, Style.FALLING_BLOCK);
    }

    /**
     * КУБИК НА ГОЛОВЕ АРМОРСТЕНДА.
     * <p>
     * На версиях без BlockDisplay это единственный способ вращать блок: поза головы
     * арморстенда крутится как угодно и работает начиная с 1.8. Расплата - размер:
     * блок на голове рисуется примерно в 0.625 блока, поэтому под этот стиль имеет
     * смысл уменьшить и хитбокс кубика (cube_half).
     */
    @Nullable
    private static TwoDCubeEntity spawnHead(@NonNull World world, @NonNull Location location,
                                            @NonNull BlockData blockData) {
        try {
            Location at = location.clone();
            at.setY(at.getY() + 0.5D + TwoDTuning.CUBE_HEAD_Y_OFFSET);

            org.bukkit.entity.ArmorStand stand =
                world.spawn(at, org.bukkit.entity.ArmorStand.class, entity -> {
                    entity.setVisible(false);
                    entity.setMarker(true);
                    entity.setBasePlate(false);
                    entity.setArms(false);
                    entity.setGravity(false);
                    entity.setSilent(true);
                    entity.setInvulnerable(true);
                    entity.setRemoveWhenFarAway(false);
                    try {
                        entity.getEquipment().setHelmet(
                            new org.bukkit.inventory.ItemStack(blockData.getMaterial()));
                    } catch (Throwable ignored) {
                    }
                });

            return new TwoDCubeEntity(world, stand, Style.HEAD);
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning(
                "[ParkourBeat] 2D: не удалось создать кубик на арморстенде: " + t);
            return null;
        }
    }

    public void keepAlive() {
        if (this.style != Style.FALLING_BLOCK) return;
        try {
            ((org.bukkit.entity.FallingBlock) this.entity).setTicksLived(1);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Повернуть кубик на заданный угол вокруг оси, смотрящей в камеру.
     *
     * @param angle    угол в радианах
     * @param axis     ось вращения (вектор в сторону камеры)
     * @param interpolationTicks за сколько тиков клиент доводит поворот; 0 значит мгновенно
     */
    public void applyRotation(double angle, @NonNull Vector axis, int interpolationTicks) {
        if (this.style == Style.HEAD) {
            try {
                // Поза головы крутится вокруг оси взгляда арморстенда, а он у нас
                // всегда развёрнут на камеру - то есть это ровно тот кувырок, что нужен.
                ((org.bukkit.entity.ArmorStand) this.entity)
                    .setHeadPose(new org.bukkit.util.EulerAngle(0.0D, 0.0D, angle));
            } catch (Throwable ignored) {
            }
            return;
        }
        if (this.style != Style.DISPLAY) return;

        try {
            Class<?> displayClass = this.entity.getClass();

            if (quaternionfConstructor == null) {
                Class<?> quaternionClass = Class.forName("org.joml.Quaternionf");
                quaternionfConstructor = quaternionClass.getConstructor(
                    float.class, float.class, float.class, float.class);

                Class<?> vectorClass = Class.forName("org.joml.Vector3f");
                vector3fConstructor = vectorClass.getConstructor(float.class, float.class, float.class);

                Class<?> transformationClass = Class.forName("org.bukkit.util.Transformation");
                transformationConstructor = transformationClass.getConstructor(
                    vectorClass, quaternionClass, vectorClass, quaternionClass);
            }

            double half = angle / 2.0D;
            double sin = Math.sin(half);
            Vector unit = axis.clone().normalize();

            Object rotation = quaternionfConstructor.newInstance(
                (float) (unit.getX() * sin),
                (float) (unit.getY() * sin),
                (float) (unit.getZ() * sin),
                (float) Math.cos(half));
            Object identity = quaternionfConstructor.newInstance(0f, 0f, 0f, 1f);

            // Сдвиг на полблока в каждую сторону: только так блок крутится вокруг
            // собственного центра, а не вокруг своего угла.
            Object translation = vector3fConstructor.newInstance(-0.5f, -0.5f, -0.5f);
            Object scale = vector3fConstructor.newInstance(1f, 1f, 1f);

            Object transformation = transformationConstructor.newInstance(
                translation, rotation, scale, identity);

            if (setTransformationMethod == null) {
                setTransformationMethod = displayClass.getMethod("setTransformation",
                    Class.forName("org.bukkit.util.Transformation"));
                setInterpolationDurationMethod = displayClass.getMethod("setInterpolationDuration", int.class);
                setInterpolationDelayMethod = displayClass.getMethod("setInterpolationDelay", int.class);
            }

            setInterpolationDelayMethod.invoke(this.entity, 0);
            setInterpolationDurationMethod.invoke(this.entity, Math.max(0, interpolationTicks));
            setTransformationMethod.invoke(this.entity, transformation);
        } catch (Throwable ignored) {
        }
    }
}
