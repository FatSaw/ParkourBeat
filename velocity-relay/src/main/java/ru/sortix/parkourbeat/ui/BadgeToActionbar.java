package ru.sortix.parkourbeat.ui;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.regex.Pattern;

/**
 * Плашки («ВЫ УПАЛИ», «НЕ ПЕРЕСТАВАЙ БЕЖАТЬ», «ЛИЧНЫЙ РЕКОРД»...) - в актионбар, а не титлом.
 * <p>
 * Титл рисуется в 4 раза крупнее обычного текста: при большом GUI плашка закрывала пол-экрана.
 * Плашки выводятся титлом из многих мест (игра, «Копатель», обучение), поэтому перехват -
 * на уровне пакета: если в титле плашка, она уходит отдельным пакетом в актионбар, а сам
 * титл - пустым (тогда подпись под ним, например «Прогресс: 9%», показывается как раньше).
 * Остальные титлы не трогаются. Выключить: {@link #ENABLED}.
 */
public final class BadgeToActionbar {
    private BadgeToActionbar() {
    }

    public static volatile boolean ENABLED = true;

    /** Символы плашек: левые половины E100.., правые E180.. (ru/en/uk). */
    private static final Pattern BADGE = Pattern.compile("[\\uE100-\\uE1AF]|\\\\u[eE]1[0-9a-fA-F]{2}");

    public static void register(@NonNull Plugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) return;
        try {
            ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.TITLE) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    if (ENABLED) redirect(event);
                }
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("[ParkourBeat] badge -> actionbar disabled: " + t);
        }
    }

    private static void redirect(@NonNull PacketEvent event) {
        try {
            PacketContainer packet = event.getPacket();
            EnumWrappers.TitleAction action = packet.getTitleActions().readSafely(0);
            if (action != EnumWrappers.TitleAction.TITLE) return;

            // Paper кладёт текст в своё Adventure-поле, а обычное поле может быть пустым - смотрим оба.
            StructureModifier<Component> adventure = packet.getModifier().withType(Component.class);
            Component adv = adventure.size() > 0 ? adventure.readSafely(0) : null;
            WrappedChatComponent chat = packet.getChatComponents().readSafely(0);
            String json = adv != null ? GsonComponentSerializer.gson().serialize(adv) : chat != null ? chat.getJson() : null;
            if (json == null || !BADGE.matcher(json).find()) return;

            PacketContainer bar = new PacketContainer(PacketType.Play.Server.TITLE);
            bar.getTitleActions().write(0, EnumWrappers.TitleAction.ACTIONBAR);
            bar.getChatComponents().write(0, WrappedChatComponent.fromJson(json));

            if (adv != null) adventure.write(0, Component.empty());
            packet.getChatComponents().writeSafely(0, WrappedChatComponent.fromText(""));

            ProtocolLibrary.getProtocolManager().sendServerPacket(event.getPlayer(), bar, false);
        } catch (Throwable ignored) {
            // в худшем случае плашка останется титлом, как раньше
        }
    }
}
