package ru.sortix.parkourbeat.boards;

import lombok.NonNull;
import org.bukkit.entity.Player;

import java.awt.Graphics2D;

public interface BoardRenderer {

    void draw(@NonNull Graphics2D g, @NonNull Board board, @NonNull Player player, @NonNull BoardSession session);

    /** Код того, на что сейчас наведён игрок: строка списка, кнопка или {@link BoardSession#NOTHING}. */
    int hover(@NonNull Board board, @NonNull Player player, @NonNull BoardSession session, int px, int py);

    /** @return true, если после нажатия борд нужно перерисовать */
    boolean click(@NonNull Board board, @NonNull Player player, @NonNull BoardSession session,
                  int px, int py, boolean right);
}
