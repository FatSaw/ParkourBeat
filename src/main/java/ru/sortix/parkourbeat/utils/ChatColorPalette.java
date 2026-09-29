package ru.sortix.parkourbeat.utils;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.Locale;

public class ChatColorPalette {

    public static void sendPalette(Player player) {
        Component title = Component.text(Lang.raw(PlayerLang.of(player), "auto.chat_color_palette.send_palette.1"))
            .color(NamedTextColor.YELLOW)
            .append(Component.text(Lang.raw(PlayerLang.of(player), "auto.chat_color_palette.send_palette.2"))
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.suggestCommand(Lang.raw(PlayerLang.of(player), "auto.chat_color_palette.send_palette.3")))
                .hoverEvent(HoverEvent.showText(Component.text(Lang.raw(PlayerLang.of(player), "auto.chat_color_palette.send_palette.4")).color(NamedTextColor.GRAY))));

        player.sendMessage(title);

        int cols = 36;
        int rows = 8;

        for (int r = 0; r < rows; r++) {
            Component line = Component.empty();
            float saturation = 1.0f - (r * 0.10f);
            float brightness = r < 4 ? 1.0f : 1.0f - ((r - 3) * 0.15f);

            for (int c = 0; c < cols; c++) {
                float hue = c / (float) cols;
                Color color = Color.getHSBColor(hue, saturation, brightness);
                String hex = String.format(Locale.ROOT, "#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());

                Component square = Component.text("█")
                    .color(TextColor.color(color.getRed(), color.getGreen(), color.getBlue()))
                    .clickEvent(ClickEvent.suggestCommand(hex))
                    .hoverEvent(HoverEvent.showText(
                        Component.text(Lang.raw(PlayerLang.of(player), "auto.chat_color_palette.send_palette.5") + hex + " ")
                            .color(NamedTextColor.WHITE)
                            .append(Component.text("█").color(TextColor.color(color.getRed(), color.getGreen(), color.getBlue())))
                            .append(Component.text(Lang.raw(PlayerLang.of(player), "auto.chat_color_palette.send_palette.6")).color(NamedTextColor.RED))
                    ));

                line = line.append(square);
            }
            player.sendMessage(line);
        }

        Component grayLine = Component.empty();
        float mid = (cols - 1) / 2.0f;
        for (int c = 0; c < cols; c++) {
            float distFromCenter = Math.abs(c - mid) / mid;
            float brightness = Math.max(0.0f, Math.min(1.0f, 1.0f - distFromCenter));
            Color color = Color.getHSBColor(0.0f, 0.0f, brightness);
            String hex = String.format(Locale.ROOT, "#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());

            Component square = Component.text("█")
                .color(TextColor.color(color.getRed(), color.getGreen(), color.getBlue()))
                .clickEvent(ClickEvent.suggestCommand(hex))
                .hoverEvent(HoverEvent.showText(
                    Component.text(Lang.raw(PlayerLang.of(player), "auto.chat_color_palette.send_palette.7") + hex + " ")
                        .color(NamedTextColor.WHITE)
                        .append(Component.text("█").color(TextColor.color(color.getRed(), color.getGreen(), color.getBlue())))
                        .append(Component.text(Lang.raw(PlayerLang.of(player), "auto.chat_color_palette.send_palette.8")).color(NamedTextColor.RED))
                ));

            grayLine = grayLine.append(square);
        }
        player.sendMessage(grayLine);
    }
}
