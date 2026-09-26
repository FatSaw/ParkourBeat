package ru.sortix.parkourbeat.replay;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import lombok.NonNull;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.rating.StatisticsManager;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import ru.sortix.parkourbeat.utils.text.PbText;
/**
 * Пакетный NPC: видит его только зритель, на сервере сущности нет.
 * Так реплей не мешает игрокам на уровне и ничего не весит.
 */
public class ReplayNpc {
    private final @NonNull ParkourBeat plugin;
    private final @NonNull ProtocolManager manager;
    private final @NonNull Player viewer;
    private final int entityId;
    private final @NonNull UUID uuid;
    private final @NonNull WrappedGameProfile profile;
    private final @NonNull String rankPrefix;
    private boolean spawned = false;

    public ReplayNpc(@NonNull ParkourBeat plugin, @NonNull Player viewer, @NonNull String name, @NonNull UUID skinSource) {
        this.plugin = plugin;
        this.manager = ProtocolLibrary.getProtocolManager();
        this.viewer = viewer;
        this.entityId = ThreadLocalRandom.current().nextInt(200_000, 900_000);
        this.uuid = UUID.randomUUID();
        this.profile = new WrappedGameProfile(this.uuid,
            name.length() > 16 ? name.substring(0, 16) : name);
        this.copySkin(skinSource);

        this.rankPrefix = plugin.get(StatisticsManager.class).getRankLabel(skinSource);
    }

    private void copySkin(@NonNull UUID skinSource) {
        try {
            Player source = org.bukkit.Bukkit.getPlayer(skinSource);
            if (source == null) return;
            WrappedGameProfile from = WrappedGameProfile.fromPlayer(source);
            this.profile.getProperties().putAll(from.getProperties());
        } catch (Throwable ignored) {
        }
    }

    public int getEntityId() {
        return this.entityId;
    }

    public void spawn(@NonNull Location location) {
        if (this.spawned) return;
        this.spawned = true; // Сразу ставим флаг

        // Откладываем всю цепочку спавна NPC на 25 тиков,
        // чтобы асинхронный телепорт игрока был ГАРАНТИРОВАННО завершён,
        // а мир был сменен и прогружен в клиенте.
        this.plugin.getServer().getScheduler().runTaskLaterAsynchronously(this.plugin, () -> {
            if (!this.viewer.isOnline()) return;

            try {
                // 1. ADD_PLAYER - добавляем игрока в таб, чтобы клиент скачал его скин
                PacketContainer info = this.manager.createPacket(PacketType.Play.Server.PLAYER_INFO);
                info.getPlayerInfoAction().write(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER);
                info.getPlayerInfoDataLists().write(0, Collections.singletonList(new PlayerInfoData(
                    this.profile, 0, EnumWrappers.NativeGameMode.SURVIVAL,
                    WrappedChatComponent.fromText(this.profile.getName()))));
                this.manager.sendServerPacket(this.viewer, info);

                // 2. NAMED_ENTITY_SPAWN - спавним теперь по актуальным координатам загруженного мира
                PacketContainer spawn = this.manager.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
                spawn.getIntegers().write(0, this.entityId);
                spawn.getUUIDs().write(0, this.uuid);
                spawn.getDoubles()
                    .write(0, location.getX())
                    .write(1, location.getY())
                    .write(2, location.getZ());
                spawn.getBytes()
                    .write(0, toAngle(location.getYaw()))
                    .write(1, toAngle(location.getPitch()));
                this.manager.sendServerPacket(this.viewer, spawn);

                this.sendSkinLayers();

                // 3. Выдача ранга в Scoreboard (Делается СТРОГО в основном потоке из-за Bukkit API)
                this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                    if (!this.viewer.isOnline()) return;
                    Scoreboard board = this.viewer.getScoreboard();
                    Team team = board.getTeam("pb_replay_npc");
                    if (team == null) {
                        team = board.registerNewTeam("pb_replay_npc");
                    }
                    team.prefix(PbText.of(this.rankPrefix + " "));
                    if (!team.hasEntry(this.profile.getName())) {
                        team.addEntry(this.profile.getName());
                    }
                });

                // 4. Только теперь через 10 тиков аккуратно убираем муляж из Tab-а (чтобы не мозолил глаза)
                // Модельку уже зафорсило, и она больше не сбежит!
                this.plugin.getServer().getScheduler().runTaskLaterAsynchronously(this.plugin, () -> {
                    if (!this.viewer.isOnline()) return;
                    try {
                        PacketContainer removeInfo = this.manager.createPacket(PacketType.Play.Server.PLAYER_INFO);
                        removeInfo.getPlayerInfoAction().write(0, EnumWrappers.PlayerInfoAction.REMOVE_PLAYER);
                        removeInfo.getPlayerInfoDataLists().write(0, Collections.singletonList(new PlayerInfoData(
                            this.profile, 0, EnumWrappers.NativeGameMode.SURVIVAL,
                            WrappedChatComponent.fromText(this.profile.getName()))));
                        this.manager.sendServerPacket(this.viewer, removeInfo);
                    } catch (Throwable ignored) {
                    }
                }, 10L);

            } catch (Throwable e) {
                // Если с протоколом или парсингом какая-то дичь — наконец будет stacktrace вместо игнора!
                e.printStackTrace();
            }
        }, 25L); // Задержка в 1,25с отлично маскируется вашим Loading... титулом (он у вас на 60 тиков настроен).
    }

    /**
     * Индекс поля «включённые части скина» в метаданных игрока.
     * <p>
     * У этого поля НЕТ постоянного номера: с каждой версией перед ним добавлялись новые
     * поля сущности. Зашитая 16 верна только для 1.15-1.16, а на 1.17+ она попадает уже
     * в другое поле - поэтому второй слой скина (шляпа, куртка, рукава) у NPC реплея
     * просто не включался, и модель выглядела «голой».
     */
    private static final int SKIN_LAYERS_INDEX = resolveSkinLayersIndex();

    private static int resolveSkinLayersIndex() {
        try {
            String version = org.bukkit.Bukkit.getBukkitVersion(); // например "1.20.4-R0.1-SNAPSHOT"
            String[] parts = version.split("-")[0].split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

            if (major > 1) return 17;
            if (minor >= 17) return 17;
            if (minor >= 15) return 16;
            if (minor >= 14) return 15;
            return 13;
        } catch (Throwable t) {
            // Современные сервера встречаются намного чаще старых.
            return 17;
        }
    }

    private void sendSkinLayers() {
        // 0x7F - все части второго слоя разом (шляпа, куртка, рукава, штанины).
        this.sendMetadata(null, (byte) 0x7F);
    }

    /**
     * Собирает и шлёт пакет метаданных.
     * <p>
     * Список наблюдаемых значений строится вручную, без {@code new WrappedDataWatcher()}.
     * Пустой конструктор обёртки лезет за конструктором ванильного DataWatcher, которому
     * нужна живая сущность; у пакетного NPC её нет, и на 1.16.5 это падало ещё до отправки
     * пакета с NullPointerException прямо внутри ProtocolLib. Готовому списку
     * WrappedWatchableObject сущность не нужна вообще.
     *
     * @param entityFlags флаги сущности (индекс 0) или null, если их слать не надо
     */
    private void sendMetadata(@Nullable Byte entityFlags, byte skinLayers) {
        try {
            PacketContainer metadata = this.manager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            metadata.getIntegers().write(0, this.entityId);

            java.util.List<WrappedWatchableObject> values = new java.util.ArrayList<>(2);
            if (entityFlags != null) {
                values.add(new WrappedWatchableObject(
                    new WrappedDataWatcher.WrappedDataWatcherObject(
                        0, WrappedDataWatcher.Registry.get(Byte.class)),
                    entityFlags));
            }
            values.add(new WrappedWatchableObject(
                new WrappedDataWatcher.WrappedDataWatcherObject(
                    SKIN_LAYERS_INDEX, WrappedDataWatcher.Registry.get(Byte.class)),
                skinLayers));

            metadata.getWatchableCollectionModifier().write(0, values);
            this.manager.sendServerPacket(this.viewer, metadata);
        } catch (Throwable t) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Unable to send replay NPC metadata", t);
        }
    }

    /**
     * Слои скина сбрасываются вместе с любой другой отправкой метаданных (например,
     * приседанием), поэтому их приходится присылать повторно вместе с флагами сущности.
     */
    private void sendEntityFlags(boolean sneaking) {
        this.sendMetadata((byte) (sneaking ? 0x02 : 0x00), (byte) 0x7F);
    }

    public void teleport(@NonNull Location location) {
        if (!this.spawned) return;
        try {
            PacketContainer packet = this.manager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
            packet.getIntegers().write(0, this.entityId);
            packet.getDoubles()
                .write(0, location.getX())
                .write(1, location.getY())
                .write(2, location.getZ());
            packet.getBytes()
                .write(0, toAngle(location.getYaw()))
                .write(1, toAngle(location.getPitch()));
            packet.getBooleans().write(0, false);
            this.manager.sendServerPacket(this.viewer, packet);

            PacketContainer head = this.manager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
            head.getIntegers().write(0, this.entityId);
            head.getBytes().write(0, toAngle(location.getYaw()));
            this.manager.sendServerPacket(this.viewer, head);
        } catch (Throwable ignored) {
        }
    }

    private boolean lastSneaking = false;

    public void setSneaking(boolean sneaking) {
        if (!this.spawned) return;
        // Метаданные шлём только на смену состояния: раньше пакет уходил каждый тик,
        // то есть двадцать лишних пакетов в секунду на каждого зрителя.
        if (sneaking == this.lastSneaking) return;
        this.lastSneaking = sneaking;
        this.sendEntityFlags(sneaking);
    }

    public void swingArm() {
        if (!this.spawned) return;
        try {
            PacketContainer animation = this.manager.createPacket(PacketType.Play.Server.ANIMATION);
            animation.getIntegers().write(0, this.entityId);
            animation.getIntegers().write(1, 0);
            this.manager.sendServerPacket(this.viewer, animation);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Привязывает камеру клиента к NPC - ровно тем же пакетом, которым ванильный сервер
     * пересаживает наблюдателя на сущность.
     * <p>
     * setSpectatorTarget() здесь бесполезен: серверной сущности не существует, прицепляться
     * не к чему. А клиенту всё равно - он знает entity id из пакета спавна и честно рисует
     * мир его глазами, со всей ванильной интерполяцией и без единого телепорта зрителя.
     */
    public void attachCamera() {
        if (!this.spawned) return;
        this.sendCamera(this.entityId);
    }

    /**
     * Возвращает камеру самому зрителю.
     */
    public void resetCamera() {
        this.sendCamera(this.viewer.getEntityId());
    }

    private void sendCamera(int targetEntityId) {
        try {
            PacketContainer camera = this.manager.createPacket(PacketType.Play.Server.CAMERA);
            camera.getIntegers().write(0, targetEntityId);
            this.manager.sendServerPacket(this.viewer, camera);
        } catch (Throwable t) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Unable to move replay camera", t);
        }
    }

    /**
     * Слушает клики зрителя по NPC. Своей сущности на сервере нет, поэтому обычный
     * PlayerInteractEntityEvent не срабатывает - ловим пакет напрямую.
     *
     * @param onClick вызывается в главном потоке, когда зритель кликнул по NPC
     */
    public void listenForClicks(@NonNull Runnable onClick) {
        if (this.clickListener != null) return;

        this.clickListener = new com.comphenix.protocol.events.PacketAdapter(
            this.plugin, com.comphenix.protocol.events.ListenerPriority.NORMAL,
            PacketType.Play.Client.USE_ENTITY) {
            @Override
            public void onPacketReceiving(com.comphenix.protocol.events.PacketEvent event) {
                if (event.getPlayer() != ReplayNpc.this.viewer) return;
                try {
                    if (event.getPacket().getIntegers().read(0) != ReplayNpc.this.entityId) return;
                } catch (Throwable t) {
                    return;
                }
                // Пакет приходит в сетевом потоке, а трогать игрока можно только в главном.
                ReplayNpc.this.plugin.getServer().getScheduler()
                    .runTask(ReplayNpc.this.plugin, onClick);
            }
        };
        this.manager.addPacketListener(this.clickListener);
    }

    private com.comphenix.protocol.events.PacketListener clickListener = null;

    public void despawn() {
        if (this.clickListener != null) {
            this.manager.removePacketListener(this.clickListener);
            this.clickListener = null;
        }
        if (!this.spawned) return;
        this.spawned = false;
        this.resetCamera();
        try {
            PacketContainer destroy = this.manager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            try {
                destroy.getIntLists().write(0, Collections.singletonList(this.entityId));
            } catch (Throwable t) {
                destroy.getIntegerArrays().write(0, new int[]{this.entityId});
            }
            this.manager.sendServerPacket(this.viewer, destroy);

            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                if (!this.viewer.isOnline()) return;
                Scoreboard board = this.viewer.getScoreboard();
                Team team = board.getTeam("pb_replay_npc");
                if (team != null) {
                    team.removeEntry(this.profile.getName());
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static byte toAngle(float value) {
        return (byte) (value * 256.0F / 360.0F);
    }
}
