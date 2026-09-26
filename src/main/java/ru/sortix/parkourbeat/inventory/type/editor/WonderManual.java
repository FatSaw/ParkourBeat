package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.utils.text.PbText;

/**
 * Ссылка на инструкцию для внешних моделей.
 * <p>
 * Отдаётся кликабельной строкой: строитель нажимает, копирует адрес, вставляет
 * в любую модель — и получает готовые команды /pbllmeffects.
 */
public final class WonderManual {

    public static final String URL =
        "https://raw.githubusercontent.com/XaviersDev/Lightshow-Plugin/main/PARKOURBEAT-EFFECTS-LLM.md";

    private WonderManual() {
    }

    /** Полный список команд прямо в чат: строители про них просто не знали. */
    public static void sendCommands(@NonNull Player player) {
        player.sendMessage(Component.empty());
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_manual.send_commands.1")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_manual.send_commands.2")));
        player.sendMessage(PbText.of("&8» &f/pbllmeffects preset 01:20 01:24 stars_fall"));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_manual.send_commands.3")));
        player.sendMessage(PbText.of("&8» &f/pbllmeffects add 00:10 00:14 x=4*cos(t);y=4*sin(t) @ steps:150"));
        player.sendMessage(PbText.of("&8» &f/pbllmeffects edit 2 dist:18 height:7 scale:1.5"));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_manual.send_commands.4")));
        player.sendMessage(Component.empty());
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_manual.send_commands.5")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_manual.send_commands.6")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_manual.send_commands.7")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_manual.send_commands.8")));
        player.sendMessage(Component.empty());
    }

    public static void send(@NonNull Player player) {
        player.sendMessage(Component.empty());
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_manual.send.1")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_manual.send.2")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_manual.send.3")));
        player.sendMessage(Component.empty());

        player.sendMessage(PbText.of("&8» &b" + URL)
            .clickEvent(ClickEvent.openUrl(URL))
            .hoverEvent(HoverEvent.showText(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_manual.send.4")))));

        player.sendMessage(Component.empty());
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_manual.send.5")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_manual.send.6")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_manual.send.7")));
        player.sendMessage(Component.empty());
    }
}
