package ru.sortix.parkourbeat.twod;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Техническая обвязка 2D-режима: подвинуть сущность с пассажиром, развернуть игроку
 * камеру, спрятать сущность лично у одного игрока.
 * <p>
 * Всё делается через рефлексию и с фолбэками: версия сервера тут не важна, а падать
 * из-за отсутствующего метода 2D-режим не имеет права.
 */
public final class TwoDEntityUtils {
    private TwoDEntityUtils() {
    }

    // ==================== ПЕРЕМЕЩЕНИЕ ТРАНСПОРТА ====================

    /**
     * Bukkit-овый teleport() отказывается двигать сущность, на которой кто-то едет,
     * поэтому здесь дёргается NMS-метод напрямую. Имён у него по версиям несколько:
     * setLocation (Spigot-маппинги), absMoveTo/moveTo (Mojang-маппинги).
     */
    private static final String[] MOVE_METHODS = {"setLocation", "absMoveTo", "moveTo"};

    private static Method handleMethod;
    private static Method moveMethod;
    private static boolean nmsMoveUnavailable = false;

    public static boolean moveRaw(@NonNull Entity entity, double x, double y, double z, float yaw, float pitch) {
        if (!nmsMoveUnavailable) {
            try {
                Method handle = handleMethod;
                if (handle == null || !handle.getDeclaringClass().isInstance(entity)) {
                    handle = entity.getClass().getMethod("getHandle");
                    handleMethod = handle;
                }
                Object nmsEntity = handle.invoke(entity);

                Method move = moveMethod;
                if (move == null || !move.getDeclaringClass().isInstance(nmsEntity)) {
                    move = findMoveMethod(nmsEntity);
                    moveMethod = move;
                }
                if (move != null) {
                    move.invoke(nmsEntity, x, y, z, yaw, pitch);
                    return true;
                }
                nmsMoveUnavailable = true;
            } catch (Throwable throwable) {
                nmsMoveUnavailable = true;
            }
        }

        // Фолбэк: обычный телепорт. Менее плавно, зато работает всегда.
        try {
            entity.teleport(new Location(entity.getWorld(), x, y, z, yaw, pitch));
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Nullable
    private static Method findMoveMethod(@NonNull Object nmsEntity) {
        for (String name : MOVE_METHODS) {
            try {
                return nmsEntity.getClass().getMethod(name,
                    double.class, double.class, double.class, float.class, float.class);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    // ==================== ФИКСАЦИЯ ВЗГЛЯДА ====================

    /** Каким способом в последний раз получилось развернуть игрока. Для диагностики. */
    private static volatile String lastRotationMethod = "нет данных";

    @NonNull
    public static String getLastRotationMethod() {
        return lastRotationMethod;
    }

    /**
     * Забыть, какие способы разворота уже признаны нерабочими, и попробовать их заново.
     * <p>
     * Нужно для диагностики: иначе один неудачный запуск навсегда вычёркивает способ
     * до перезагрузки плагина, и проверить починку на живом сервере невозможно.
     */
    public static void resetRotationMethods() {
        lookAtPacketUnavailable = false;
        nmsRotationUnavailable = false;
        protocolRotationUnavailable = false;
        paperRotationUnavailable = false;
        lookAtUnavailable = false;
        setRotationUnavailable = false;
        lastRotationMethod = "нет данных";
    }

    /**
     * ЖЁСТКАЯ ФИКСАЦИЯ ВЗГЛЯДА.
     * <p>
     * Способы перебираются от самого надёжного к самому капризному. Первый же
     * сработавший запоминается, дальше используется только он.
     * <p>
     * Первым идёт пакет "посмотреть на точку" - единственный, который работает,
     * когда игрок едет на транспорте. Все остальные способы разворачивают в этом
     * случае не игрока, а его транспорт.
     */
    public static void lockRotation(@NonNull Player player,
                                    double targetX, double targetY, double targetZ,
                                    float yaw, float pitch) {
        if (sendLookAtPacket(player, targetX, targetY, targetZ)) return;
        if (sendRotationNms(player, yaw, pitch)) return;
        if (sendRotationProtocolLib(player, yaw, pitch)) return;
        if (paperRotate(player, yaw, pitch)) return;

        if (!lookAtUnavailable) {
            lookAt(player, targetX, targetY, targetZ);
            if (!lookAtUnavailable) {
                lastRotationMethod = "Paper lookAt";
                return;
            }
        }
        setRotationRaw(player, yaw, pitch);
    }

    // ---------- способ 0: пакет "посмотреть на точку" ----------

    private static boolean lookAtPacketUnavailable = false;
    private static Constructor<?> lookAtConstructor;
    private static Object eyesAnchorNms;

    /**
     * ЕДИНСТВЕННЫЙ РАЗВОРОТ, КОТОРЫЙ РАБОТАЕТ ПОД ПАССАЖИРОМ.
     * <p>
     * Пакет позиции клиент применяет к КОРНЕВОМУ ТРАНСПОРТУ игрока: сидя на
     * арморстенде, игрок получает разворот не себе, а стенду - камера остаётся на
     * месте. Отсюда и загадка "стоя команда работает, в игре нет".
     * <p>
     * Пакет "посмотреть на точку" устроен иначе: клиент вызывает у самого игрока
     * поворот на переданную точку, транспорт в этом не участвует вообще. Поэтому
     * фиксация работает и на арморстенде, и игрок остаётся обычным игроком - со
     * своим игровым режимом, эффектами и чтением пробела через пакет транспорта.
     */
    public static boolean sendLookAtPacket(@NonNull Player player, double x, double y, double z) {
        if (lookAtPacketUnavailable) return false;
        try {
            if (lookAtConstructor == null) {
                Class<?> packetClass = findClass(
                    nmsName("PacketPlayOutLookAt"),
                    "net.minecraft.network.protocol.game.PacketPlayOutLookAt",
                    "net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket");
                if (packetClass == null) throw new IllegalStateException(Lang.raw(PlayerLang.of(player), "auto.two_d_entity_utils.send_look_at_packet.1"));

                Constructor<?> found = null;
                Object anchor = null;

                for (Constructor<?> constructor : packetClass.getConstructors()) {
                    Class<?>[] types = constructor.getParameterTypes();
                    if (types.length != 4) continue;
                    if (!types[0].isEnum()) continue;
                    if (types[1] != double.class || types[2] != double.class || types[3] != double.class) continue;

                    anchor = eyesConstant(types[0]);
                    if (anchor == null) continue;

                    found = constructor;
                    break;
                }
                if (found == null) throw new IllegalStateException(Lang.raw(PlayerLang.of(player), "auto.two_d_entity_utils.send_look_at_packet.2"));

                lookAtConstructor = found;
                eyesAnchorNms = anchor;
            }

            Object packet = lookAtConstructor.newInstance(eyesAnchorNms, x, y, z);
            if (!sendNmsPacket(player, packet)) throw new IllegalStateException(Lang.raw(PlayerLang.of(player), "auto.two_d_entity_utils.send_look_at_packet.3"));

            lastRotationMethod = "LookAt";
            return true;
        } catch (Throwable throwable) {
            lookAtPacketUnavailable = true;
            org.bukkit.Bukkit.getLogger().warning(
                "[ParkourBeat] 2D: разворот пакетом взгляда недоступен: " + throwable);
            return false;
        }
    }

    /**
     * Точка привязки взгляда. Нужна та, что считает от глаз: от ног картинка уезжает
     * вниз тем сильнее, чем ближе цель.
     */
    @Nullable
    private static Object eyesConstant(@NonNull Class<?> anchorEnum) {
        Object[] constants = anchorEnum.getEnumConstants();
        if (constants == null || constants.length == 0) return null;

        for (Object constant : constants) {
            if (String.valueOf(constant).toUpperCase(java.util.Locale.ROOT).contains("EYE")) return constant;
        }
        // Имена в обфусцированной сборке могут не сохраниться: там EYES идёт второй.
        return constants.length > 1 ? constants[1] : constants[0];
    }

    @Nullable
    private static String nmsName(@NonNull String simpleName) {
        try {
            String packageName = org.bukkit.Bukkit.getServer().getClass().getPackage().getName();
            String[] parts = packageName.split("\\.");
            if (parts.length >= 4) return "net.minecraft.server." + parts[3] + "." + simpleName;
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Nullable
    private static Class<?> findClass(@Nullable String... names) {
        for (String name : names) {
            if (name == null) continue;
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * Отправить готовый NMS-пакет игроку. Соединение и метод отправки ищутся один раз
     * и переиспользуются всеми способами разворота.
     */
    private static boolean sendNmsPacket(@NonNull Player player, @NonNull Object packet) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);

            if (nmsConnectionField == null) {
                Field field = findConnectionField(handle.getClass());
                if (field == null) return false;
                field.setAccessible(true);
                nmsConnectionField = field;
            }
            Object connection = nmsConnectionField.get(handle);
            if (connection == null) return false;

            if (nmsSendMethod == null) {
                Method method = findSendMethod(connection.getClass(), packet.getClass());
                if (method == null) return false;
                method.setAccessible(true);
                nmsSendMethod = method;
            }
            nmsSendMethod.invoke(connection, packet);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    // ---------- псевдонимы ----------
    //
    // Способы разворота за время отладки успели поменять имена, и снаружи их зовут
    // и так, и так. Держим оба названия: это дешевле, чем ловить несовпадение имён
    // при каждой пересборке.

    public static boolean sendNmsRotation(@NonNull Player player, float yaw, float pitch) {
        return sendRotationNms(player, yaw, pitch);
    }

    public static boolean sendRotationPacket(@NonNull Player player, float yaw, float pitch) {
        return sendRotationProtocolLib(player, yaw, pitch);
    }

    // ---------- способ 1: серверный пакет, собранный руками ----------

    private static boolean nmsRotationUnavailable = false;
    private static Constructor<?> nmsPositionConstructor;
    private static Object nmsRelativeFlags;
    private static Field nmsConnectionField;
    private static Method nmsSendMethod;
    private static boolean nmsHasBooleanTail = false;

    /**
     * САМЫЙ ПРЯМОЙ СПОСОБ.
     * <p>
     * Никаких обёрток: берётся настоящий класс пакета позиции, его собственный енум
     * относительных координат и соединение игрока. Координаты нулевые и помечены
     * относительными, углы абсолютные - игрок стоит на месте, но смотрит туда, куда
     * сказано, и не слезает с камеры.
     */
    public static boolean sendRotationNms(@NonNull Player player, float yaw, float pitch) {
        if (nmsRotationUnavailable) return false;
        try {
            if (nmsPositionConstructor == null) {
                Class<?> packetClass = findPositionPacketClass();
                if (packetClass == null) throw new IllegalStateException(Lang.raw(PlayerLang.of(player), "auto.two_d_entity_utils.send_rotation_nms.1"));

                Class<?> flagEnum = findFlagEnum(packetClass);
                if (flagEnum == null) throw new IllegalStateException(Lang.raw(PlayerLang.of(player), "auto.two_d_entity_utils.send_rotation_nms.2") + packetClass);

                Set<Object> flags = relativeConstants(flagEnum);
                if (flags == null) throw new IllegalStateException(Lang.raw(PlayerLang.of(player), "auto.two_d_entity_utils.send_rotation_nms.3"));
                nmsRelativeFlags = flags;

                Constructor<?> found = null;
                for (Constructor<?> constructor : packetClass.getConstructors()) {
                    Class<?>[] types = constructor.getParameterTypes();
                    if (types.length < 7) continue;
                    if (types[0] != double.class || types[1] != double.class || types[2] != double.class) continue;
                    if (types[3] != float.class || types[4] != float.class) continue;
                    if (!Set.class.isAssignableFrom(types[5])) continue;
                    if (types[6] != int.class) continue;

                    found = constructor;
                    nmsHasBooleanTail = types.length == 8 && types[7] == boolean.class;
                    break;
                }
                if (found == null) throw new IllegalStateException(Lang.raw(PlayerLang.of(player), "auto.two_d_entity_utils.send_rotation_nms.4"));
                nmsPositionConstructor = found;
            }

            Object packet = nmsHasBooleanTail
                ? nmsPositionConstructor.newInstance(0.0D, 0.0D, 0.0D, yaw, pitch, nmsRelativeFlags, 0, false)
                : nmsPositionConstructor.newInstance(0.0D, 0.0D, 0.0D, yaw, pitch, nmsRelativeFlags, 0);

            if (!sendNmsPacket(player, packet)) {
                throw new IllegalStateException(Lang.raw(PlayerLang.of(player), "auto.two_d_entity_utils.send_rotation_nms.5"));
            }

            lastRotationMethod = "NMS";
            return true;
        } catch (Throwable throwable) {
            nmsRotationUnavailable = true;
            org.bukkit.Bukkit.getLogger().warning(
                "[ParkourBeat] 2D: прямой разворот через NMS недоступен: " + throwable);
            return false;
        }
    }

    @Nullable
    private static Class<?> findPositionPacketClass() {
        List<String> names = new ArrayList<>();
        try {
            String packageName = org.bukkit.Bukkit.getServer().getClass().getPackage().getName();
            String[] parts = packageName.split("\\.");
            if (parts.length >= 4) names.add("net.minecraft.server." + parts[3] + ".PacketPlayOutPosition");
        } catch (Throwable ignored) {
        }
        names.add("net.minecraft.network.protocol.game.PacketPlayOutPosition");
        names.add("net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket");

        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static Field findConnectionField(@NonNull Class<?> handleClass) {
        Class<?> current = handleClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                String typeName = field.getType().getName();
                if (typeName.contains("PlayerConnection") || typeName.contains("ServerGamePacketListener")) {
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    @Nullable
    private static Method findSendMethod(@NonNull Class<?> connectionClass, @NonNull Class<?> packetClass) {
        for (Method method : connectionClass.getMethods()) {
            if (method.getParameterCount() != 1) continue;
            if (!method.getParameterTypes()[0].isAssignableFrom(packetClass)) continue;

            String name = method.getName();
            if (name.equals("sendPacket") || name.equals("send") || name.equals("a")) return method;
        }
        return null;
    }

    // ---------- способ 2: тот же пакет, но через ProtocolLib ----------

    private static boolean protocolRotationUnavailable = false;

    @SuppressWarnings("unchecked")
    public static boolean sendRotationProtocolLib(@NonNull Player player, float yaw, float pitch) {
        if (protocolRotationUnavailable) return false;
        try {
            com.comphenix.protocol.ProtocolManager manager =
                com.comphenix.protocol.ProtocolLibrary.getProtocolManager();

            com.comphenix.protocol.events.PacketContainer packet =
                manager.createPacket(com.comphenix.protocol.PacketType.Play.Server.POSITION);

            packet.getDoubles().write(0, 0.0D).write(1, 0.0D).write(2, 0.0D);
            packet.getFloat().write(0, yaw).write(1, pitch);
            try {
                packet.getIntegers().writeSafely(0, 0);
            } catch (Throwable ignored) {
            }

            Class<?> flagEnum = findFlagEnum(packet.getHandle().getClass());
            Set<Object> flags = flagEnum == null ? null : relativeConstants(flagEnum);
            if (flags == null) throw new IllegalStateException(Lang.raw(PlayerLang.of(player), "auto.two_d_entity_utils.send_rotation_protocol_lib.1"));
            packet.getSpecificModifier(Set.class).write(0, flags);

            manager.sendServerPacket(player, packet);
            lastRotationMethod = "ProtocolLib";
            return true;
        } catch (Throwable throwable) {
            protocolRotationUnavailable = true;
            org.bukkit.Bukkit.getLogger().warning(
                "[ParkourBeat] 2D: разворот через ProtocolLib недоступен: " + throwable);
            return false;
        }
    }

    // ---------- способ 3: Paper-овский телепорт с флагами (1.19.4+) ----------

    private static Method teleportWithFlagsMethod;
    private static Object[] rotationFlags;
    private static boolean paperRotationUnavailable = false;

    public static boolean paperRotate(@NonNull Player player, float yaw, float pitch) {
        if (paperRotationUnavailable) return false;
        try {
            if (teleportWithFlagsMethod == null) {
                Class<?> flagClass = Class.forName("io.papermc.paper.entity.TeleportFlag");
                Class<?> relativeClass = Class.forName("io.papermc.paper.entity.TeleportFlag$Relative");
                Class<?> stateClass = Class.forName("io.papermc.paper.entity.TeleportFlag$EntityState");

                Object emptyArray = java.lang.reflect.Array.newInstance(flagClass, 0);
                Method method = Entity.class.getMethod("teleport",
                    Location.class,
                    org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.class,
                    emptyArray.getClass());

                Object[] flags = (Object[]) java.lang.reflect.Array.newInstance(flagClass, 4);
                flags[0] = enumValue(stateClass, "RETAIN_VEHICLE");
                flags[1] = enumValue(relativeClass, "X");
                flags[2] = enumValue(relativeClass, "Y");
                flags[3] = enumValue(relativeClass, "Z");
                for (Object flag : flags) {
                    if (flag == null) throw new IllegalStateException(Lang.raw(PlayerLang.of(player), "auto.two_d_entity_utils.paper_rotate.1"));
                }

                rotationFlags = flags;
                teleportWithFlagsMethod = method;
            }

            Location delta = new Location(player.getWorld(), 0.0D, 0.0D, 0.0D, yaw, pitch);
            teleportWithFlagsMethod.invoke(player, delta,
                org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN, rotationFlags);

            lastRotationMethod = "Paper teleport";
            return true;
        } catch (Throwable throwable) {
            paperRotationUnavailable = true;
            org.bukkit.Bukkit.getLogger().warning(
                "[ParkourBeat] 2D: разворот через телепорт недоступен: " + throwable);
            return false;
        }
    }

    // ---------- способ 4: клиентский lookAt ----------

    private static Method lookAtMethod;
    private static Object eyesAnchor;
    private static boolean lookAtUnavailable = false;

    public static void lookAt(@NonNull Player player, double x, double y, double z) {
        if (lookAtUnavailable) return;
        try {
            if (lookAtMethod == null) {
                Class<?> anchorClass = Class.forName("io.papermc.paper.entity.LookAnchor");
                Object eyes = enumValue(anchorClass, "EYES");
                if (eyes == null) {
                    lookAtUnavailable = true;
                    return;
                }
                eyesAnchor = eyes;
                lookAtMethod = Player.class.getMethod("lookAt",
                    double.class, double.class, double.class, anchorClass);
            }
            lookAtMethod.invoke(player, x, y, z, eyesAnchor);
        } catch (Throwable throwable) {
            lookAtUnavailable = true;
        }
    }

    // ---------- способ 5: прямая установка угла ----------

    private static Method setRotationMethod;
    private static boolean setRotationUnavailable = false;

    private static void setRotationRaw(@NonNull Player player, float yaw, float pitch) {
        if (setRotationUnavailable) return;
        try {
            if (setRotationMethod == null) {
                setRotationMethod = Player.class.getMethod("setRotation", float.class, float.class);
            }
            setRotationMethod.invoke(player, yaw, pitch);
            lastRotationMethod = "setRotation";
        } catch (Throwable throwable) {
            setRotationUnavailable = true;
        }
    }

    // ---------- общий поиск енума флагов ----------

    /**
     * Енум относительных координат берётся из самого пакета: у обёрток он на разных
     * версиях называется по-разному, а то и отсутствует, а в пакете есть всегда.
     */
    @Nullable
    private static Class<?> findFlagEnum(@NonNull Class<?> packetClass) {
        for (Class<?> inner : packetClass.getDeclaredClasses()) {
            if (isFlagEnum(inner)) return inner;
        }
        for (Field field : packetClass.getDeclaredFields()) {
            if (!Set.class.isAssignableFrom(field.getType())) continue;

            Type generic = field.getGenericType();
            if (!(generic instanceof ParameterizedType parameterized)) continue;

            Type[] arguments = parameterized.getActualTypeArguments();
            if (arguments.length != 1) continue;
            if (!(arguments[0] instanceof Class<?> argumentClass)) continue;
            if (isFlagEnum(argumentClass)) return argumentClass;
        }
        return null;
    }

    private static boolean isFlagEnum(@Nullable Class<?> candidate) {
        if (candidate == null || !candidate.isEnum()) return false;
        return relativeConstants(candidate) != null;
    }

    @Nullable
    private static Set<Object> relativeConstants(@NonNull Class<?> enumClass) {
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null) return null;

        Set<Object> flags = new HashSet<>();
        for (Object constant : constants) {
            String name = String.valueOf(constant);
            // Относительными делаем только координаты: углы обязаны быть абсолютными,
            // иначе поворот превратится в добавку к текущему и камеру закрутит.
            if (name.equals("X") || name.equals("Y") || name.equals("Z")) flags.add(constant);
        }
        return flags.size() == 3 ? flags : null;
    }

    @Nullable
    private static Object enumValue(@NonNull Class<?> enumClass, @NonNull String name) {
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null) return null;
        for (Object constant : constants) {
            if (name.equals(String.valueOf(constant))) return constant;
        }
        return null;
    }

    // ==================== СКРЫТИЕ СУЩНОСТЕЙ ====================

    private static Method hideMethod;
    private static Method showMethod;
    private static boolean visibilityUnavailable = false;

    /**
     * Спрятать сущность лично у одного игрока. Нужно для собранных монеток: у всех
     * остальных они обязаны остаться на месте.
     */
    public static void hideEntity(@NonNull Plugin plugin, @NonNull Player player, @NonNull Entity entity) {
        if (visibilityUnavailable) return;
        try {
            if (hideMethod == null) {
                hideMethod = Player.class.getMethod("hideEntity", Plugin.class, Entity.class);
            }
            hideMethod.invoke(player, plugin, entity);
        } catch (Throwable throwable) {
            visibilityUnavailable = true;
        }
    }

    public static void showEntity(@NonNull Plugin plugin, @NonNull Player player, @NonNull Entity entity) {
        if (visibilityUnavailable) return;
        try {
            if (showMethod == null) {
                showMethod = Player.class.getMethod("showEntity", Plugin.class, Entity.class);
            }
            showMethod.invoke(player, plugin, entity);
        } catch (Throwable ignored) {
        }
    }

    // ==================== ПРОЧЕЕ ====================

    /** Направление взгляда по горизонтали в градусах (yaw), собранное из вектора. */
    public static float yawOf(@NonNull Vector direction) {
        double x = direction.getX();
        double z = direction.getZ();
        if (x == 0.0D && z == 0.0D) return 0f;
        return (float) Math.toDegrees(Math.atan2(-x, z));
    }
}
