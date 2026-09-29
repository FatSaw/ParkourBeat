package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.twod.TwoDEntityUtils;

import javax.annotation.Nullable;

/**
 * СИДЕНЬЕ, НА КОТОРОМ ЕДЕТ ИГРОК.
 * <p>
 * Ровно та же схема, что в 2D-режиме, и не из лени: она единственная работающая.
 * <ul>
 *     <li>Невидимая стойка брони с меткой - у неё нет ни хитбокса, ни физики, ни ИИ,
 *         и она не пытается упасть.</li>
 *     <li>Двигается НЕ через {@code teleport()}. Bukkit-овый телепорт ссаживает
 *         пассажира: именно поэтому вагонетка уезжала одна, а игрок оставался стоять
 *         на месте и дёргался. Позиция ставится напрямую через
 *         {@link TwoDEntityUtils#moveRaw}, и пассажир едет вместе с сиденьем.</li>
 *     <li>Живые сущности с выключенным ИИ не двигаются вообще - сервер пропускает им
 *         шаг движения. Поэтому никаких свиней и лошадей здесь нет.</li>
 * </ul>
 */
public final class DiggerCarrier {

    private DiggerCarrier() {
    }

    /**
     * Создать сиденье и посадить игрока.
     * <p>
     * Разворот выставляется ДО посадки: обычный телепорт ссаживает пассажира, и другого
     * момента задать угол наверняка просто нет. Иначе первые секунды забега игрок
     * смотрит куда попало и доворачивается руками.
     */
    @NonNull
    public static ArmorStand spawn(@NonNull World world, @NonNull Location at, @NonNull Player passenger) {
        Location seatAt = at.clone();
        seatAt.setY(seatAt.getY() + DiggerTuning.CARRIER_Y_OFFSET);

        ArmorStand seat = world.spawn(seatAt, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setGravity(false);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setCustomNameVisible(false);
            stand.setRemoveWhenFarAway(false);
            stand.setPersistent(false);
        });

        Location facing = seatAt.clone();
        facing.setYaw(at.getYaw());
        facing.setPitch(0.0f);
        try {
            passenger.teleport(facing);
        } catch (Throwable ignored) {
        }

        seat.addPassenger(passenger);
        return seat;
    }

    /**
     * Переставить сиденье вместе с пассажиром.
     *
     * @return false, если подвинуть не удалось - тогда забег стоит прервать
     */
    public static boolean move(@Nullable Entity seat, @NonNull Location to) {
        if (seat == null || !seat.isValid()) return false;
        return TwoDEntityUtils.moveRaw(seat,
            to.getX(), to.getY(), to.getZ(),
            seat.getLocation().getYaw(), seat.getLocation().getPitch());
    }

    public static void remove(@Nullable Entity seat) {
        if (seat == null) return;
        for (Entity passenger : seat.getPassengers()) {
            seat.removePassenger(passenger);
        }
        seat.remove();
    }
}
