package ru.sortix.parkourbeat.ui;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;

/**
 * Иконки в строках табло.
 * <p>
 * Строки приходят из lang.yml с маркером «◆» в начале - он заменяется на иконку,
 * подходящую по смыслу (пинг, TPS, карта, точность...). Если маркера в строке нет,
 * иконка просто ставится перед ней. Тексты в lang.yml менять не нужно.
 */
public final class ScoreIcons {
    private ScoreIcons() {
    }

    private static final String MARKER = "\u25C6"; // ◆

    @NonNull
    public static Component mark(@NonNull Icons.Icon icon, @NonNull Component line) {
        Component replaced = line.replaceText(TextReplacementConfig.builder()
            .matchLiteral(MARKER)
            .once()
            .replacement(icon.component())
            .build());
        if (!replaced.equals(line)) return replaced;
        return icon.component().append(Component.text(" ")).append(line);
    }
}
