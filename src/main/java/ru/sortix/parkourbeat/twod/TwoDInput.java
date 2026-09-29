package ru.sortix.parkourbeat.twod;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import lombok.NonNull;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.plugin.EventExecutor;
import ru.sortix.parkourbeat.ParkourBeat;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ЕДИНСТВЕННОЕ УПРАВЛЕНИЕ В 2D-РЕЖИМЕ - ПРОБЕЛ.
 * <p>
 * Игрок сидит на невидимом арморстенде, поэтому обычного прыжка у него нет и события
 * PlayerJumpEvent не будет никогда. Нажатие ловится двумя независимыми способами,
 * и достаточно, чтобы сработал любой:
 * <ol>
 *     <li>PlayerInputEvent - современный Paper (1.21.3+), самый честный источник;</li>
 *     <li>пакет управления транспортом через ProtocolLib - все версии до него.</li>
 * </ol>
 * Наружу отдаётся не «нажат ли пробел прямо сейчас», а момент последнего нажатия:
 * именно из него собирается буфер прыжка с поправкой на пинг.
 */
public class TwoDInput {

    private static final class State {
        private volatile long lastPressAt = 0L;
        private volatile boolean held = false;
        private volatile long lastHeldAt = 0L;
        /** До какого момента считаем зажатой левую кнопку мыши. */
        private volatile long clickHeldUntil = 0L;
    }

    /**
     * Сколько держится "зажатие" от одного щелчка мышью.
     * <p>
     * Клиент шлёт взмах рукой пачками, а не непрерывным состоянием, поэтому удержание
     * ЛКМ приходится достраивать по времени между взмахами.
     */
    private static final long CLICK_HOLD_MILLIS = 300L;

    private final @NonNull ParkourBeat plugin;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    private @Nullable PacketAdapter packetAdapter = null;
    private @Nullable Listener inputListener = null;
    private boolean paperInputAvailable = false;

    public TwoDInput(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.registerPaperInputEvent();
        this.registerPacketListener();

        if (!this.paperInputAvailable && this.packetAdapter == null) {
            plugin.getLogger().warning("2D: не удалось подключить ни один источник нажатий пробела!"
                + " Прыжок в 2D-режиме работать не будет.");
        }
    }

    // ==================== ИСТОЧНИК 1: PAPER ====================

    @SuppressWarnings("unchecked")
    private void registerPaperInputEvent() {
        try {
            Class<?> eventClass = Class.forName("org.bukkit.event.player.PlayerInputEvent");
            Method getInput = eventClass.getMethod("getInput");
            Class<?> inputClass = Class.forName("org.bukkit.Input");
            Method isJump = inputClass.getMethod("isJump");

            Listener listener = new Listener() {
            };
            EventExecutor executor = (ignored, event) -> {
                try {
                    if (!(event instanceof PlayerEvent playerEvent)) return;
                    Player player = playerEvent.getPlayer();
                    if (!this.states.containsKey(player.getUniqueId())) return;
                    Object input = getInput.invoke(event);
                    Object jump = isJump.invoke(input);
                    this.onJumpState(player, Boolean.TRUE.equals(jump));
                } catch (Throwable ignoredThrowable) {
                }
            };

            this.plugin.getServer().getPluginManager().registerEvent(
                (Class<? extends Event>) eventClass, listener, EventPriority.MONITOR, executor, this.plugin, true);

            this.inputListener = listener;
            this.paperInputAvailable = true;
        } catch (Throwable ignored) {
            this.paperInputAvailable = false;
        }
    }

    // ==================== ИСТОЧНИК 2: ПАКЕТЫ ====================

    private void registerPacketListener() {
        List<PacketType> types = new ArrayList<>();
        addPacketType(types, "STEER_VEHICLE");
        addPacketType(types, "PLAYER_INPUT");
        // Повороты головы в 2D запрещены: гасим их прямо на входе, иначе клиент
        // и сервер начинают спорить об угле и картинка дёргается.
        addPacketType(types, "LOOK");
        addPacketType(types, "POSITION_LOOK");

        // ЛКМ ловим ПАКЕТАМИ, а не событиями.
        //
        // В режиме наблюдателя сервер не порождает событий взмаха и удара - он их
        // отбрасывает раньше, - но сам пакет от клиента приходит всегда. Для 2D это
        // единственный способ дать управление, когда игрок не сидит на транспорте.
        addPacketType(types, "ARM_ANIMATION");
        addPacketType(types, "BLOCK_DIG");
        if (types.isEmpty()) return;

        try {
            PacketAdapter adapter = new PacketAdapter(this.plugin, types.toArray(new PacketType[0])) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    try {
                        Player player = event.getPlayer();
                        if (player == null) return;
                        if (!TwoDInput.this.states.containsKey(player.getUniqueId())) return;

                        String typeName = event.getPacketType().name();
                        if (typeName.contains("LOOK")) {
                            TwoDInput.this.rewriteLook(player, event);
                            return;
                        }
                        if (typeName.contains("ARM_ANIMATION") || typeName.contains("BLOCK_DIG")) {
                            TwoDInput.this.onClick(player);
                            return;
                        }

                        Boolean jump = readJump(event.getPacket());
                        if (jump == null) return;
                        TwoDInput.this.onJumpState(player, jump);
                    } catch (Throwable ignored) {
                    }
                }
            };
            ProtocolLibrary.getProtocolManager().addPacketListener(adapter);
            this.packetAdapter = adapter;
        } catch (Throwable t) {
            this.plugin.getLogger().warning("2D: не удалось подключить пакетный слушатель пробела: " + t);
        }
    }

    private static void addPacketType(@NonNull List<PacketType> types, @NonNull String name) {
        try {
            Field field = PacketType.Play.Client.class.getField(name);
            Object value = field.get(null);
            if (value instanceof PacketType type && !types.contains(type)) types.add(type);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Достать флаг прыжка из пакета управления.
     * <p>
     * До 1.21.2 это был простой пакет с двумя булями (прыжок и слезание), после -
     * один компактный рекорд со всеми клавишами сразу. Разбираем оба вида.
     */
    @Nullable
    private static Boolean readJump(@NonNull PacketContainer packet) {
        try {
            StructureModifier<Boolean> booleans = packet.getBooleans();
            int size = booleans.size();
            if (size == 2 || size == 1) return booleans.read(0);
            if (size >= 7) return booleans.read(4);
        } catch (Throwable ignored) {
        }

        try {
            Object handle = packet.getHandle();
            for (Field field : handle.getClass().getDeclaredFields()) {
                Class<?> fieldType = field.getType();
                if (fieldType.isPrimitive() || fieldType == String.class) continue;
                field.setAccessible(true);
                Object value = field.get(handle);
                if (value == null) continue;
                Boolean jump = readJumpFromRecord(value);
                if (jump != null) return jump;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Nullable
    private static Boolean readJumpFromRecord(@NonNull Object value) {
        try {
            Class<?> valueClass = value.getClass();
            if (!valueClass.isRecord()) return null;

            RecordComponent[] components = valueClass.getRecordComponents();
            // Сначала пробуем по имени - оно есть, если сервер не обфусцирован.
            for (RecordComponent component : components) {
                if (component.getType() != boolean.class) continue;
                if (!component.getName().toLowerCase(java.util.Locale.ROOT).contains("jump")) continue;
                return (Boolean) component.getAccessor().invoke(value);
            }
            // Иначе по позиции: forward, backward, left, right, JUMP, shift, sprint.
            int index = 0;
            for (RecordComponent component : components) {
                if (component.getType() != boolean.class) continue;
                if (index == 4) return (Boolean) component.getAccessor().invoke(value);
                index++;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ==================== СОСТОЯНИЕ ====================

    private void onJumpState(@NonNull Player player, boolean jump) {
        State state = this.states.get(player.getUniqueId());
        if (state == null) return;

        long now = System.currentTimeMillis();
        if (jump) {
            // Нас интересует именно фронт нажатия: в полёте игрок «тапает» пробел,
            // и каждое новое нажатие обязано давать отдельный толчок.
            if (!state.held) state.lastPressAt = now;
            state.lastHeldAt = now;
        }
        state.held = jump;
    }

    private final java.util.Set<UUID> lockedRotation =
        java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** Начать слушать этого игрока. Без этого пакеты от него просто игнорируются. */
    public void track(@NonNull Player player) {
        this.states.put(player.getUniqueId(), new State());
    }

    public void untrack(@NonNull Player player) {
        this.states.remove(player.getUniqueId());
        this.lockedRotation.remove(player.getUniqueId());
        this.lockedAngles.remove(player.getUniqueId());
        this.rotationDirty.remove(player.getUniqueId());
    }

    private final Map<UUID, float[]> lockedAngles = new ConcurrentHashMap<>();

    /** Игроки, чей клиент отвернулся от заданного угла и требует поправки. */
    private final java.util.Set<UUID> rotationDirty =
        java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Клиент отвернулся от заданного угла?
     * <p>
     * Разворот шлём только по этому признаку: постоянная отправка каждый тик заставляет
     * камеру мелко дрожать, потому что клиент и сервер спорят об угле буквально всё время.
     *
     * @return true один раз на каждое отклонение
     */
    public boolean consumeRotationDirty(@NonNull Player player) {
        return this.rotationDirty.remove(player.getUniqueId());
    }

    public void markRotationDirty(@NonNull Player player) {
        this.rotationDirty.add(player.getUniqueId());
    }

    /** Запретить или разрешить игроку крутить головой. */
    public void setRotationLocked(@NonNull Player player, boolean locked) {
        if (locked) {
            this.lockedRotation.add(player.getUniqueId());
        } else {
            this.lockedRotation.remove(player.getUniqueId());
            this.lockedAngles.remove(player.getUniqueId());
            this.rotationDirty.remove(player.getUniqueId());
        }
    }

    private static float wrapDegrees(float degrees) {
        float result = degrees % 360f;
        if (result >= 180f) result -= 360f;
        if (result < -180f) result += 360f;
        return result;
    }

    /** Углы, которыми подменяются присланные клиентом. */
    public void setLockedAngles(@NonNull Player player, float yaw, float pitch) {
        this.lockedAngles.put(player.getUniqueId(), new float[]{yaw, pitch});
    }

    /**
     * ПОДМЕНА ПОВОРОТА НА ВХОДЕ.
     * <p>
     * Отменять пакет бесполезно: сервер тогда просто не узнает о повороте, а клиент
     * всё равно крутит камеру у себя. Поэтому пакет не отменяется, а переписывается -
     * в нём остаются наши углы. Сервер видит игрока строго в 2D-плоскости, и любой
     * следующий ответ сервера (в том числе наш пакет позиции) возвращает камеру на
     * место, не борясь с самим собой.
     */
    private void rewriteLook(@NonNull Player player,
                             @NonNull com.comphenix.protocol.events.PacketEvent event) {
        float[] angles = this.lockedAngles.get(player.getUniqueId());
        if (angles == null) return;
        if (!this.lockedRotation.contains(player.getUniqueId())) return;

        try {
            com.comphenix.protocol.reflect.StructureModifier<Float> floats =
                event.getPacket().getFloat();
            if (floats.size() < 2) return;

            // Клиент прислал свой угол: если он разошёлся с нашим, значит игрок
            // дёрнул мышкой и его надо вернуть на место.
            Float clientYaw = floats.read(0);
            Float clientPitch = floats.read(1);
            if (clientYaw != null && clientPitch != null) {
                boolean turned = Math.abs(wrapDegrees(clientYaw - angles[0])) > 0.35f
                    || Math.abs(clientPitch - angles[1]) > 0.35f;
                if (turned) this.rotationDirty.add(player.getUniqueId());
            }

            // Пакет мог прийти уже прочитанным другим слушателем - работаем с копией.
            com.comphenix.protocol.events.PacketContainer packet = event.getPacket();
            packet.getFloat().write(0, angles[0]).write(1, angles[1]);
            event.setPacket(packet);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Нажатие левой кнопкой мыши. В оригинале прыжок висит и на пробеле, и на клике,
     * поэтому оба источника ведут в одно и то же место.
     */
    public void onClick(@NonNull Player player) {
        State state = this.states.get(player.getUniqueId());
        if (state == null) return;

        long now = System.currentTimeMillis();
        // Новый щелчок это новое нажатие: серия кликов должна давать серию прыжков.
        if (now >= state.clickHeldUntil) state.lastPressAt = now;
        state.clickHeldUntil = now + CLICK_HOLD_MILLIS;
    }

    public long getLastPressAt(@NonNull Player player) {
        State state = this.states.get(player.getUniqueId());
        return state == null ? 0L : state.lastPressAt;
    }

    /**
     * Нажатие использовано: чтобы одно и то же нажатие не сработало дважды.
     */
    public void consumePress(@NonNull Player player) {
        State state = this.states.get(player.getUniqueId());
        if (state != null) state.lastPressAt = 0L;
    }

    public boolean isHeld(@NonNull Player player) {
        State state = this.states.get(player.getUniqueId());
        if (state == null) return false;
        if (System.currentTimeMillis() < state.clickHeldUntil) return true;
        // Клиент присылает состояние клавиш регулярно, но если поток прервался -
        // считаем, что пробел отпущен, иначе кораблик залипнет в наборе высоты.
        if (!state.held) return false;
        return System.currentTimeMillis() - state.lastHeldAt <= 400L;
    }

    public void disable() {
        this.states.clear();
        this.lockedRotation.clear();
        this.lockedAngles.clear();
        this.rotationDirty.clear();
        if (this.packetAdapter != null) {
            try {
                ProtocolLibrary.getProtocolManager().removePacketListener(this.packetAdapter);
            } catch (Throwable ignored) {
            }
            this.packetAdapter = null;
        }
        if (this.inputListener != null) {
            try {
                HandlerList.unregisterAll(this.inputListener);
            } catch (Throwable ignored) {
            }
            this.inputListener = null;
        }
    }
}
