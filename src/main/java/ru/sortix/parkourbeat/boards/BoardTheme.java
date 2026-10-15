package ru.sortix.parkourbeat.boards;

import lombok.NonNull;

import javax.annotation.Nullable;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public final class BoardTheme {

    public static final Color BACKGROUND = new Color(0x14141B);
    public static final Color PANEL = new Color(0x1D1D28);
    public static final Color ROW = new Color(0x22222E);
    public static final Color ROW_ALT = new Color(0x1A1A24);
    public static final Color ROW_HOVER = new Color(0x33334A);
    public static final Color BORDER = new Color(0x3A3A4E);

    public static final Color TEXT = new Color(0xF2F2F5);
    public static final Color TEXT_DIM = new Color(0x9A9AAB);

    public static final Color ACCENT = new Color(0xB86BFF);
    public static final Color BOARD_BORDER = new Color(0x3B2553);

    public static final Color GREEN = new Color(0x4CC24C);
    public static final Color GREEN_DARK = new Color(0x2E7D32);
    public static final Color RED = new Color(0xD84343);
    public static final Color BLUE = new Color(0x4A8FE0);

    public static Font customFallbackFont = null;
    private static final Map<BufferedImage, BufferedImage> silhouetteCache = new HashMap<>();

    private BoardTheme() {
    }

    public static void clearCache() {
        silhouetteCache.clear();
    }

    @NonNull
    public static Graphics2D prepare(@NonNull BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    public static void fill(@NonNull Graphics2D g, int x, int y, int width, int height, @NonNull Color color) {
        g.setColor(color);
        g.fillRect(x, y, width, height);
    }

    public static void pill(@NonNull Graphics2D g, int x, int y, int width, int height,
                            @NonNull Color color, @Nullable Color border
    ) {
        g.setColor(color);
        g.fillRoundRect(x, y, width, height, height / 2, height / 2);
        if (border != null) {
            g.setColor(border);
            g.drawRoundRect(x, y, width - 1, height - 1, height / 2, height / 2);
        }
    }

    public static void drawBoardBorder(Graphics2D g, int width, int height) {
        g.setColor(BOARD_BORDER);
        int thickness = 2;
        g.fillRect(0, 0, width, thickness);
        g.fillRect(0, height - thickness, width, thickness);
        g.fillRect(0, 0, thickness, height);
        g.fillRect(width - thickness, 0, thickness, height);
    }

    @Nullable
    private static Font getFallbackFont(Font base, int codePoint) {
        if (customFallbackFont != null && customFallbackFont.canDisplay(codePoint)) {
            return customFallbackFont.deriveFont(base.getStyle(), base.getSize());
        }
        Font f = new Font("SansSerif", base.getStyle(), base.getSize());
        if (f.canDisplay(codePoint)) return f;
        f = new Font("Segoe UI Symbol", base.getStyle(), base.getSize());
        if (f.canDisplay(codePoint)) return f;
        f = new Font("Dialog", base.getStyle(), base.getSize());
        if (f.canDisplay(codePoint)) return f;
        return null; // Возвращаем null, чтобы проигнорировать символ, а не рисовать квадрат
    }

    public static int getStringWidth(Graphics2D g, String value, Font font) {
        if (value == null || value.isEmpty()) return 0;
        int width = 0;
        Font currentFont = font;
        StringBuilder buffer = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            int codePoint = value.codePointAt(i);
            if (Character.isHighSurrogate(value.charAt(i))) i++;

            Font renderFont = font;
            if (!font.canDisplay(codePoint)) renderFont = getFallbackFont(font, codePoint);

            // Если символ ничем не отрисовать - пропускаем
            if (renderFont == null) continue;

            String s = new String(Character.toChars(codePoint));

            if (!renderFont.equals(currentFont)) {
                if (buffer.length() > 0) {
                    g.setFont(currentFont);
                    width += g.getFontMetrics().stringWidth(buffer.toString());
                    buffer.setLength(0);
                }
                currentFont = renderFont;
            }
            buffer.append(s);
        }
        if (buffer.length() > 0) {
            g.setFont(currentFont);
            width += g.getFontMetrics().stringWidth(buffer.toString());
        }
        return width;
    }

    public static void text(@NonNull Graphics2D g, @Nullable String value, int x, int baseline,
                            @NonNull Color color, @NonNull Font font
    ) {
        if (value == null || value.isEmpty()) return;
        g.setColor(color);
        Font currentFont = font;
        StringBuilder buffer = new StringBuilder();
        int currentX = x;

        for (int i = 0; i < value.length(); i++) {
            int codePoint = value.codePointAt(i);
            if (Character.isHighSurrogate(value.charAt(i))) i++;

            Font renderFont = font;
            if (!font.canDisplay(codePoint)) renderFont = getFallbackFont(font, codePoint);

            // Если символ ничем не отрисовать - пропускаем
            if (renderFont == null) continue;

            String s = new String(Character.toChars(codePoint));

            if (!renderFont.equals(currentFont)) {
                if (buffer.length() > 0) {
                    g.setFont(currentFont);
                    String toDraw = buffer.toString();
                    g.drawString(toDraw, currentX, baseline);
                    currentX += g.getFontMetrics().stringWidth(toDraw);
                    buffer.setLength(0);
                }
                currentFont = renderFont;
            }
            buffer.append(s);
        }
        if (buffer.length() > 0) {
            g.setFont(currentFont);
            g.drawString(buffer.toString(), currentX, baseline);
        }
    }

    public static void textRight(@NonNull Graphics2D g, @Nullable String value, int right, int baseline,
                                 @NonNull Color color, @NonNull Font font
    ) {
        if (value == null || value.isEmpty()) return;
        int width = getStringWidth(g, value, font);
        text(g, value, right - width, baseline, color, font);
    }

    public static void textCenter(@NonNull Graphics2D g, @Nullable String value, int centerX, int baseline,
                                  @NonNull Color color, @NonNull Font font
    ) {
        if (value == null || value.isEmpty()) return;
        int width = getStringWidth(g, value, font);
        text(g, value, centerX - width / 2, baseline, color, font);
    }

    public static void textOutlined(@NonNull Graphics2D g, @Nullable String value, int x, int baseline,
                                    @NonNull Color color, @NonNull Color outline, @NonNull Font font) {
        text(g, value, x - 1, baseline - 1, outline, font);
        text(g, value, x + 1, baseline - 1, outline, font);
        text(g, value, x - 1, baseline + 1, outline, font);
        text(g, value, x + 1, baseline + 1, outline, font);
        text(g, value, x - 1, baseline, outline, font);
        text(g, value, x + 1, baseline, outline, font);
        text(g, value, x, baseline - 1, outline, font);
        text(g, value, x, baseline + 1, outline, font);
        text(g, value, x, baseline, color, font);
    }

    public static void textCenterOutlined(@NonNull Graphics2D g, @Nullable String value, int centerX, int baseline,
                                          @NonNull Color color, @NonNull Color outline, @NonNull Font font) {
        if (value == null || value.isEmpty()) return;
        int width = getStringWidth(g, value, font);
        textOutlined(g, value, centerX - width / 2, baseline, color, outline, font);
    }

    public static void icon(@NonNull Graphics2D g, @Nullable BufferedImage image, int x, int y, int size) {
        if (image == null) return;
        g.drawImage(image, x, y, size, size, null);
    }

    public static void iconOutlined(@NonNull Graphics2D g, @Nullable BufferedImage image, int x, int y, int w, int h, @NonNull Color outline) {
        if (image == null) return;
        BufferedImage silhouette = silhouetteCache.computeIfAbsent(image, img -> {
            BufferedImage sil = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            int rgb = outline.getRGB();
            for (int ix = 0; ix < img.getWidth(); ix++) {
                for (int iy = 0; iy < img.getHeight(); iy++) {
                    if ((img.getRGB(ix, iy) >> 24) != 0x00) {
                        sil.setRGB(ix, iy, rgb);
                    }
                }
            }
            return sil;
        });

        g.drawImage(silhouette, x - 1, y, w, h, null);
        g.drawImage(silhouette, x + 1, y, w, h, null);
        g.drawImage(silhouette, x, y - 1, w, h, null);
        g.drawImage(silhouette, x, y + 1, w, h, null);
        g.drawImage(silhouette, x - 1, y - 1, w, h, null);
        g.drawImage(silhouette, x + 1, y + 1, w, h, null);
        g.drawImage(silhouette, x + 1, y - 1, w, h, null);
        g.drawImage(silhouette, x - 1, y + 1, w, h, null);
        g.drawImage(image, x, y, w, h, null);
    }

    @NonNull
    public static String clip(@NonNull Graphics2D g, @NonNull String value, @NonNull Font font, int maxWidth) {
        if (value == null || value.isEmpty()) return "";
        if (getStringWidth(g, value, font) <= maxWidth) return value;

        String tail = "...";
        int tailWidth = getStringWidth(g, tail, font);
        StringBuilder sb = new StringBuilder();
        int width = 0;

        for (int i = 0; i < value.length(); i++) {
            int codePoint = value.codePointAt(i);
            if (Character.isHighSurrogate(value.charAt(i))) i++;

            Font renderFont = font;
            if (!font.canDisplay(codePoint)) renderFont = getFallbackFont(font, codePoint);
            if (renderFont == null) continue;

            String s = new String(Character.toChars(codePoint));
            g.setFont(renderFont);

            int charWidth = g.getFontMetrics().stringWidth(s);
            if (width + charWidth + tailWidth > maxWidth) break;

            width += charWidth;
            sb.append(s);
        }
        return sb.append(tail).toString();
    }

    @NonNull
    public static String plain(@Nullable String value) {
        if (value == null) return "";
        String result = value.replace("<v>", "").replace("</v>", "");
        StringBuilder sb = new StringBuilder(result.length());

        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);

            if (c >= 0xFF01 && c <= 0xFF5E) {
                c = (char) (c - 0xFEE0);
            } else if (c == 0x3000) {
                c = ' ';
            }

            if ((c == '\u00A7' || c == '&') && i + 1 < result.length()) {
                char next = Character.toLowerCase(result.charAt(i + 1));
                if ((next >= '0' && next <= '9') || (next >= 'a' && next <= 'f') || "klmnorx".indexOf(next) >= 0) {
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }
}
