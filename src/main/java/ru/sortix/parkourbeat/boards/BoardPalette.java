package ru.sortix.parkourbeat.boards;

import lombok.NonNull;
import org.bukkit.map.MapPalette;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Перевод картинки в цвета карты.
 * <p>
 * MapPalette.matchColor перебирает всю палитру на каждый пиксель, а полотно борда - это
 * сотни тысяч пикселей на каждую перерисовку. Здесь палитра один раз раскладывается в
 * таблицу на 32768 ячеек по RGB555, и дальше цвет берётся одним обращением в массив.
 */
public final class BoardPalette {

    private static byte[] lookup = null;

    private BoardPalette() {
    }

    @SuppressWarnings("deprecation")
    private static synchronized void build() {
        if (lookup != null) return;

        // В новых версиях API поле MapPalette.colors скрыто.
        // Создаем свой массив и заполняем через доступный метод MapPalette.getColor().
        // Формат карт в Minecraft подразумевает максимум 256 возможных цветов (от 0 до 255).
        Color[] palette = new Color[256];
        for (int i = 0; i < 256; i++) {
            try {
                palette[i] = MapPalette.getColor((byte) i);
            } catch (Exception e) {
                // Если цвет с таким ID еще не существует в текущей версии игры (IndexOutOfBounds),
                // оставляем его null, чтобы пропустить его ниже.
                palette[i] = null;
            }
        }

        int[] pr = new int[palette.length];
        int[] pg = new int[palette.length];
        int[] pb = new int[palette.length];
        boolean[] usable = new boolean[palette.length];
        for (int i = 0; i < palette.length; i++) {
            Color color = palette[i];
            if (i < 4 || color == null || color.getAlpha() < 128) continue;
            usable[i] = true;
            pr[i] = color.getRed();
            pg[i] = color.getGreen();
            pb[i] = color.getBlue();
        }

        byte[] table = new byte[1 << 15];
        for (int key = 0; key < table.length; key++) {
            int r = ((key >> 10) & 0x1F) << 3;
            int g = ((key >> 5) & 0x1F) << 3;
            int b = (key & 0x1F) << 3;

            int best = 0;
            long bestDistance = Long.MAX_VALUE;
            for (int i = 0; i < palette.length; i++) {
                if (!usable[i]) continue;
                long dr = r - pr[i];
                long dg = g - pg[i];
                long db = b - pb[i];
                long distance = dr * dr * 2L + dg * dg * 4L + db * db * 3L;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }
            table[key] = (byte) best;
        }
        lookup = table;
    }

    public static byte match(int rgb) {
        if (lookup == null) build();
        int key = (((rgb >> 19) & 0x1F) << 10) | (((rgb >> 11) & 0x1F) << 5) | ((rgb >> 3) & 0x1F);
        return lookup[key];
    }

    /** Полотно борда режется на плитки 128x128, каждая из них - одна карта. */
    @NonNull
    public static byte[][] slice(@NonNull BufferedImage image, int columns, int rows) {
        if (lookup == null) build();

        int width = columns * Board.TILE;
        int[] pixels = new int[width * rows * Board.TILE];
        image.getRGB(0, 0, width, rows * Board.TILE, pixels, 0, width);

        byte[][] tiles = new byte[columns * rows][];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                byte[] tile = new byte[Board.TILE * Board.TILE];
                int baseX = column * Board.TILE;
                int baseY = row * Board.TILE;
                for (int y = 0; y < Board.TILE; y++) {
                    int source = (baseY + y) * width + baseX;
                    int target = y * Board.TILE;
                    for (int x = 0; x < Board.TILE; x++) {
                        int rgb = pixels[source + x];
                        int key = (((rgb >> 19) & 0x1F) << 10)
                            | (((rgb >> 11) & 0x1F) << 5)
                            | ((rgb >> 3) & 0x1F);
                        tile[target + x] = lookup[key];
                    }
                }
                tiles[row * columns + column] = tile;
            }
        }
        return tiles;
    }
}
