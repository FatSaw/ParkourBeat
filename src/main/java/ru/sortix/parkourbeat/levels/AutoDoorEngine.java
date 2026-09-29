package ru.sortix.parkourbeat.levels;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.levels.settings.AutoDoor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Двери - общее состояние мира, а не персональный эффект, поэтому пересчёт идёт один раз
 * на уровень, а не на каждого игрока: иначе два человека рядом с одной дверью
 * перещёлкивали бы её друг у друга каждый тик.
 */
public final class AutoDoorEngine {
    private AutoDoorEngine() {
    }

    /**
     * @return true, если состояние двери изменилось
     */
    public static boolean tick(@NonNull ru.sortix.parkourbeat.ParkourBeat plugin,
                               @NonNull Level level,
                               @NonNull AutoDoor door) {
        if (!door.isEnabled()) return false;

        World world = level.getWorld();
        Block block = findOpenableBlock(world, door);
        if (block == null) return false;

        boolean anyoneNear = isAnyoneNear(plugin, world, door);
        boolean shouldBeOpen = door.isInverted() != anyoneNear;

        return setOpen(block, shouldBeOpen, door.isPlaySound());
    }

    private static final int MIN_LOOKAHEAD_PING_MILLIS = 60;
    private static final double MAX_LOOKAHEAD_SECONDS = 0.25D;
    private static final double MAX_LOOKAHEAD_BLOCKS = 2.0D;
    private static final double RADIUS_LOOKAHEAD_SHARE = 0.5D;

    private static boolean isAnyoneNear(@NonNull ru.sortix.parkourbeat.ParkourBeat plugin,
                                        @NonNull World world,
                                        @NonNull AutoDoor door) {
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;

            Location location = player.getLocation();
            if (door.isInRadius(location.getX(), location.getY(), location.getZ())) return true;
            if (isPredictedInside(plugin, player, door, location)) return true;
        }
        return false;
    }

    /**
     * Между тем, как сервер увидел игрока, и тем, как до клиента доедет открытая дверь,
     * проходит целый круг задержки. На большом пинге игрок успевает добежать до створки
     * раньше картинки. Поэтому дополнительно проверяется точка, где игрок окажется
     * через это время.
     *
     * Упреждение считается не в абсолютных блоках, а с оглядкой на радиус самой двери:
     * для радиуса 1.5 прибавка в целый блок означала бы срабатывание почти вдвое дальше,
     * а этого никто не просил. Поэтому прибавка не превышает половины радиуса.
     *
     * Проверка только добавляет "рядом", но никогда не убирает: игрок, убегающий от двери,
     * держит её открытой по своей настоящей позиции, и створка не захлопнется ему в спину.
     */
    private static boolean isPredictedInside(@NonNull ru.sortix.parkourbeat.ParkourBeat plugin,
                                             @NonNull Player player,
                                             @NonNull AutoDoor door,
                                             @NonNull Location location) {
        double lead = lookaheadSeconds(plugin, player);
        if (lead <= 0.0D) return false;

        org.bukkit.util.Vector velocity = player.getVelocity();
        double dx = velocity.getX() * 20.0D * lead;
        double dz = velocity.getZ() * 20.0D * lead;

        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.05D) return false;

        double limit = Math.min(door.getRadius() * RADIUS_LOOKAHEAD_SHARE, MAX_LOOKAHEAD_BLOCKS);
        if (distance > limit) {
            double scale = limit / distance;
            dx *= scale;
            dz *= scale;
        }

        return door.isInRadius(location.getX() + dx, location.getY(), location.getZ() + dz);
    }

    private static double lookaheadSeconds(@NonNull ru.sortix.parkourbeat.ParkourBeat plugin,
                                           @NonNull Player player) {
        int ping;
        try {
            ping = plugin.get(ru.sortix.parkourbeat.player.PingManager.class).getPing(player);
        } catch (Exception e) {
            return 0.0D;
        }

        if (ping <= MIN_LOOKAHEAD_PING_MILLIS) return 0.0D;
        return Math.min((ping - MIN_LOOKAHEAD_PING_MILLIS) / 1000.0D, MAX_LOOKAHEAD_SECONDS);
    }

    /**
     * Строитель мог кликнуть по верхней половине двери, а состояние хранится в обеих.
     * Ищем открываемый блок в самой точке, потом на блок ниже и на блок выше.
     */
    @Nullable
    public static Block findOpenableBlock(@NonNull World world, @NonNull AutoDoor door) {
        // Без этой проверки getBlockAt() принудительно подгрузил бы чанк, и двери
        // держали бы куски уровня в памяти просто потому, что мы их опрашиваем.
        if (!world.isChunkLoaded(door.getBlockX() >> 4, door.getBlockZ() >> 4)) return null;

        Block exact = world.getBlockAt(door.getBlockX(), door.getBlockY(), door.getBlockZ());
        if (isOpenable(exact)) return normalizeDoor(exact);

        Block below = exact.getRelative(BlockFace.DOWN);
        if (isOpenable(below)) return normalizeDoor(below);

        Block above = exact.getRelative(BlockFace.UP);
        if (isOpenable(above)) return normalizeDoor(above);

        return null;
    }

    public static boolean isOpenable(@Nullable Block block) {
        return block != null && block.getBlockData() instanceof Openable;
    }

    /**
     * У двери состояние дублируется в обеих половинах, но верхняя половина не хранит
     * поворот - работать удобнее всегда с нижней.
     */
    @NonNull
    private static Block normalizeDoor(@NonNull Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Door && ((Door) data).getHalf() == org.bukkit.block.data.Bisected.Half.TOP) {
            Block below = block.getRelative(BlockFace.DOWN);
            if (below.getBlockData() instanceof Door) return below;
        }
        return block;
    }

    /**
     * @return true, если состояние действительно поменялось
     */
    public static boolean setOpen(@NonNull Block block, boolean open, boolean withSound) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Openable openable)) return false;
        if (openable.isOpen() == open) return false;

        applyOpen(block, openable, open);

        if (data instanceof Door) {
            applyToOtherHalf(block, open);
            Block partner = findDoublePartner(block);
            if (partner != null) {
                BlockData partnerData = partner.getBlockData();
                if (partnerData instanceof Openable partnerOpenable && partnerOpenable.isOpen() != open) {
                    applyOpen(partner, partnerOpenable, open);
                    applyToOtherHalf(partner, open);
                }
            }
        }

        if (withSound) {
            Location soundLocation = block.getLocation().add(0.5D, 0.5D, 0.5D);
            block.getWorld().playSound(soundLocation, soundOf(block.getType(), open), 0.9f, 1.0f);
        }
        return true;
    }

    private static void applyOpen(@NonNull Block block, @NonNull Openable openable, boolean open) {
        openable.setOpen(open);
        // Без физики: обновление соседей заставило бы дверь пересчитать опору и,
        // например, сломаться, если под ней декоративный блок без коллизии.
        block.setBlockData(openable, false);
    }

    private static void applyToOtherHalf(@NonNull Block doorBlock, boolean open) {
        BlockData data = doorBlock.getBlockData();
        if (!(data instanceof Door door)) return;

        BlockFace direction = door.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM
            ? BlockFace.UP : BlockFace.DOWN;
        Block other = doorBlock.getRelative(direction);
        BlockData otherData = other.getBlockData();
        if (!(otherData instanceof Door otherDoor)) return;
        if (otherDoor.isOpen() == open) return;

        otherDoor.setOpen(open);
        other.setBlockData(otherDoor, false);
    }

    /**
     * Двустворчатая дверь - это два отдельных блока с разными петлями. Открывать их
     * порознь выглядит поломанным, поэтому ищем соседа с тем же поворотом и другой петлёй.
     */
    @Nullable
    private static Block findDoublePartner(@NonNull Block block) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Door door)) return null;

        BlockFace facing = door.getFacing();
        BlockFace left = rotateLeft(facing);
        if (left == null) return null;

        for (BlockFace side : new BlockFace[]{left, left.getOppositeFace()}) {
            Block neighbour = block.getRelative(side);
            BlockData neighbourData = neighbour.getBlockData();
            if (!(neighbourData instanceof Door neighbourDoor)) continue;
            if (neighbourDoor.getFacing() != facing) continue;
            if (neighbourDoor.getHinge() == door.getHinge()) continue;
            return neighbour;
        }
        return null;
    }

    @Nullable
    private static BlockFace rotateLeft(@NonNull BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> null;
        };
    }

    @NonNull
    private static Sound soundOf(@NonNull Material material, boolean open) {
        String name = material.name();
        if (material == Material.IRON_DOOR) {
            return open ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE;
        }
        if (material == Material.IRON_TRAPDOOR) {
            return open ? Sound.BLOCK_IRON_TRAPDOOR_OPEN : Sound.BLOCK_IRON_TRAPDOOR_CLOSE;
        }
        if (name.endsWith("_TRAPDOOR")) {
            return open ? Sound.BLOCK_WOODEN_TRAPDOOR_OPEN : Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE;
        }
        if (name.endsWith("_FENCE_GATE")) {
            return open ? Sound.BLOCK_FENCE_GATE_OPEN : Sound.BLOCK_FENCE_GATE_CLOSE;
        }
        return open ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE;
    }

    /**
     * Приводит все двери уровня в закрытое состояние: чтобы забег всегда начинался одинаково,
     * а не с тем, что осталось от предыдущего игрока.
     */
    public static void resetAll(@NonNull Level level) {
        World world = level.getWorld();
        List<AutoDoor> doors = level.getLightShow().getAutoDoors();
        for (AutoDoor door : doors) {
            Block block = findOpenableBlock(world, door);
            if (block == null) continue;
            setOpen(block, door.isInverted(), false);
        }
    }

    @NonNull
    public static String describeType(@NonNull World world, @NonNull AutoDoor door) {
        Block block = findOpenableBlock(world, door);
        if (block == null) return "блок не найден";

        String name = block.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return name;
    }
}
