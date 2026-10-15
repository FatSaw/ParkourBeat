package ru.sortix.parkourbeat.boards;

import lombok.NonNull;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BoardAssets {

    private final @NonNull Plugin plugin;
    private final @NonNull File folder;
    private final @NonNull File iconsFolder;

    private final Map<String, BufferedImage> icons = new HashMap<>();
    private final Map<String, BufferedImage> scaled = new HashMap<>();
    private @Nullable Font baseFont = null;

    public BoardAssets(@NonNull Plugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "boards");
        this.iconsFolder = new File(this.folder, "icons");
        if (!this.iconsFolder.exists() && !this.iconsFolder.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку " + this.iconsFolder.getPath());
        }
        this.reload();
    }

    public void reload() {
        BoardTheme.clearCache();
        this.icons.clear();
        this.scaled.clear();
        this.baseFont = null;

        File[] files = this.iconsFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".png")) continue;
                try {
                    BufferedImage image = ImageIO.read(file);
                    if (image != null) this.icons.put(name.substring(0, name.length() - 4), image);
                } catch (Throwable t) {
                    this.plugin.getLogger().warning("Иконка " + file.getName() + " не прочиталась: " + t.getMessage());
                }
            }
        }

        File fontFile = new File(this.folder, "font.ttf");
        if (fontFile.isFile()) {
            try {
                this.baseFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);
            } catch (Throwable t) {
                this.plugin.getLogger().warning("Шрифт бордов не подошёл: " + t.getMessage());
            }
        }

        File fallbackFile = new File(this.folder, "fallback.ttf");
        if (fallbackFile.isFile()) {
            try {
                BoardTheme.customFallbackFont = Font.createFont(Font.TRUETYPE_FONT, fallbackFile);
            } catch (Throwable t) {
                this.plugin.getLogger().warning("Запасной шрифт бордов не подошёл: " + t.getMessage());
            }
        } else {
            BoardTheme.customFallbackFont = null;
        }

        this.plugin.getLogger().info("Борды: иконок " + this.icons.size()
            + ", шрифт " + (this.baseFont == null ? "системный" : this.baseFont.getFontName(Locale.ROOT)));
    }

    @Nullable
    public BufferedImage icon(@NonNull String name) {
        return this.icons.get(name.toLowerCase(Locale.ROOT));
    }

    @Nullable
    public BufferedImage icon(@NonNull String name, int size) {
        String key = name.toLowerCase(Locale.ROOT) + "@" + size;
        BufferedImage cached = this.scaled.get(key);
        if (cached != null) return cached;

        BufferedImage source = this.icon(name);
        if (source == null) return null;
        if (source.getWidth() == size && source.getHeight() == size) {
            this.scaled.put(key, source);
            return source;
        }

        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        this.scaled.put(key, result);
        return result;
    }

    @NonNull
    public Font font(int size, boolean bold) {
        Font font = this.baseFont;
        if (font == null) return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size);
        return font.deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size);
    }

    @NonNull
    public File getFolder() {
        return this.folder;
    }
}
