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
 * <p>
 * <b>PACKET</b> (основной режим на нашем 1.16.5): настоящий BlockDisplay только у клиента
 * игрока, через ViaVersion ({@link ru.sortix.parkourbeat.ui.PacketBlockDisplay}). Вращение
 * вокруг центра и перемещение сглаживает сам клиент - кубик кувыркается как в Geometry Dash.
 * На сервере остаётся лишь невидимый маркер-арморстенд: к нему привязана вся прежняя
 * логика (метки, владелец, удаление), а видимый кубик - пакетный.
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
        PACKET,
        HEAD,
        FALLING_BLOCK
    }

    private final @NonNull World world;
    private final @NonNull Entity entity;
    private final @NonNull Style style;
    /** Видимый кубик в режиме PACKET (у остальных стилей - null). */
    private @Nullable ru.sortix.parkourbeat.ui.PacketBlockDisplay packet;
    /** Лодка полёта (только у клиента), едет пассажиром НА кубике. */
    private @Nullable ru.sortix.parkourbeat.ui.PacketBoat flightBoat;
    /** В полёте точка дисплея опущена до лодки, а модель кубика поднята на столько же. */
    private float flightOffset;

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
        return this.style == Style.DISPLAY || this.style == Style.PACKET;
    }

    public boolean canRotate() {
        return this.style == Style.DISPLAY || this.style == Style.HEAD || this.style == Style.PACKET;
    }

    public boolean isValid() {
        return this.entity.isValid();
    }

    public void remove() {
        this.stopFlight();
        if (this.packet != null) {
            this.packet.hide();
            this.packet = null;
        }
        this.entity.remove();
    }

    /**
     * Сущность передвинута на (x, y, z) - для PACKET переносим и видимый кубик.
     * y - высота сущности (центр кубика, см. {@link #toEntityY}).
     */
    public void moved(double x, double y, double z) {
        if (this.packet != null) this.packet.teleport(x, y - this.flightOffset, z);
    }

    /** Кубик - пакетный BlockDisplay (полёт для него - пакетная лодка). */
    public boolean isPacket() {
        return this.style == Style.PACKET && this.packet != null;
    }

    public boolean isFlying() {
        return this.flightBoat != null;
    }

    /**
     * ПОЛЁТ: лодка у клиента едет ПАССАЖИРОМ НА КУБИКЕ.
     * <p>
     * Наоборот (кубик в лодке) нельзя: лодка разворачивает пассажира к своему курсу
     * (clampRotation), и кубик отворачивался от камеры и крутился вокруг чужой оси -
     * «смотрит назад» и «колбасит» при нажатии. Носитель же свой поворот сохраняет.
     * Точка дисплея опускается до лодки, а модель кубика поднимается на ту же высоту -
     * оба сдвига плавные за 2 тика, поэтому на глаз кубик не дёргается.
     *
     * @param boatYaw    курс лодки - по ходу уровня, чтобы камера видела её сбоку
     * @param drop       насколько точка лодки ниже центра кубика из физики
     * @param cubeOffset насколько модель кубика выше точки лодки (drop + приподнятие на дно лодки)
     */
    public boolean startFlight(@NonNull org.bukkit.entity.Player owner, double x, double boatY, double z,
                               float boatYaw, float drop, float cubeOffset) {
        if (this.packet == null) return false;
        this.stopFlight();
        ru.sortix.parkourbeat.ui.PacketBoat boat = ru.sortix.parkourbeat.ui.PacketBoat.show(owner, x, boatY, z, boatYaw);
        if (boat == null) return false;
        this.flightBoat = boat;
        this.flightOffset = drop;
        this.packet.carry(boat.getEntityId());
        this.packet.setOffsetY(cubeOffset, 2);
        this.packet.teleport(x, boatY, z);
        return true;
    }

    /** Лодка едет сама вместе с кубиком - двигать её отдельно не нужно. */
    public void moveFlight(double x, double boatY, double z, float yaw) {
    }

    public void stopFlight() {
        if (this.flightBoat == null) return;
        if (this.packet != null) {
            this.packet.dropCarried();
            this.packet.setOffsetY(0F, 2);
        }
        this.flightOffset = 0F;
        this.flightBoat.hide();
        this.flightBoat = null;
    }

    /**
     * Высота, на которую надо ставить сущность, чтобы низ кубика оказался на {@code bottomY}.
     * <p>
     * BlockDisplay рисует блок от своего угла, поэтому его мы центрируем сдвигом внутри
     * трансформации и ставим ровно в центр кубика; falling_block уже отрисован от низа.
     */
    public double toEntityY(double bottomY) {
        return switch (this.style) {
            case DISPLAY, PACKET -> bottomY + 0.5D;
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
    public static TwoDCubeEntity spawn(@NonNull World world, @NonNull Location location,
                                       @NonNull BlockData blockData) {
        return spawn(world, location, blockData, null);
    }

    /**
     * То же, но с владельцем: на сервере без BlockDisplay (наш 1.16.5) кубик рисуется
     * пакетным BlockDisplay только у него. Стиль по умолчанию (AUTO): настоящий
     * BlockDisplay сервера -> пакетный BlockDisplay -> falling_block.
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public static TwoDCubeEntity spawn(@NonNull World world, @NonNull Location location,
                                       @NonNull BlockData blockData, @Nullable org.bukkit.entity.Player owner) {
        String requested = TwoDTuning.CUBE_STYLE == null
            ? "AUTO" : TwoDTuning.CUBE_STYLE.trim().toUpperCase(java.util.Locale.ROOT);

        boolean wantsPacket = requested.equals("PACKET")
            || ((requested.equals("AUTO") || requested.equals("DISPLAY")) && displayClass() == null);
        if (wantsPacket && owner != null && TwoDTuning.CUBE_ROTATION
            && ru.sortix.parkourbeat.ui.PacketBlockDisplay.canShowTo(owner)) {
            TwoDCubeEntity packetCube = spawnPacket(world, location, blockData, owner);
            if (packetCube != null) return packetCube;
        }

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
     * ПАКЕТНЫЙ КУБИК: невидимый маркер на сервере + BlockDisplay у клиента владельца.
     */
    @Nullable
    private static TwoDCubeEntity spawnPacket(@NonNull World world, @NonNull Location location,
                                              @NonNull BlockData blockData, @NonNull org.bukkit.entity.Player owner) {
        org.bukkit.entity.ArmorStand marker = null;
        try {
            Location at = location.clone();
            at.setY(at.getY() + 0.5D);
            marker = world.spawn(at, org.bukkit.entity.ArmorStand.class, entity -> {
                entity.setVisible(false);
                entity.setMarker(true);
                entity.setSmall(true);
                entity.setBasePlate(false);
                entity.setArms(false);
                entity.setGravity(false);
                entity.setSilent(true);
                entity.setInvulnerable(true);
                entity.setRemoveWhenFarAway(false);
            });
            ru.sortix.parkourbeat.ui.PacketBlockDisplay display = ru.sortix.parkourbeat.ui.PacketBlockDisplay.show(
                owner, blockData, at.getX(), at.getY(), at.getZ(), 2);
            if (display == null) {
                marker.remove();
                return null;
            }
            TwoDCubeEntity result = new TwoDCubeEntity(world, marker, Style.PACKET);
            result.packet = display;
            return result;
        } catch (Throwable t) {
            if (marker != null) marker.remove();
            org.bukkit.Bukkit.getLogger().warning("[ParkourBeat] 2D: пакетный BlockDisplay недоступен: " + t);
            return null;
        }
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
        if (this.style == Style.PACKET) {
            if (this.packet != null) {
                this.packet.rotate(angle, axis.getX(), axis.getY(), axis.getZ(), Math.max(0, interpolationTicks));
            }
            return;
        }
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
