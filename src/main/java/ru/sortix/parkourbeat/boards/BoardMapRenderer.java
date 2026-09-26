package ru.sortix.parkourbeat.boards;

import lombok.NonNull;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/**
 * Одна плитка борда.
 * <p>
 * Рендерер контекстный: сервер вызывает его отдельно для каждого игрока, поэтому подсветка
 * строки под прицелом и список доступных уровней у всех свои, хотя карта на стене одна.
 */
public class BoardMapRenderer extends MapRenderer {

    private final @NonNull BoardsManager manager;
    private final @NonNull Board board;
    private final int tile;

    public BoardMapRenderer(@NonNull BoardsManager manager, @NonNull Board board, int tile) {
        super(true);
        this.manager = manager;
        this.board = board;
        this.tile = tile;
    }

    @Override
    public void render(@NonNull MapView view, @NonNull MapCanvas canvas, @NonNull Player player) {
        this.manager.paint(this.board, this.tile, canvas, player);
    }
}
