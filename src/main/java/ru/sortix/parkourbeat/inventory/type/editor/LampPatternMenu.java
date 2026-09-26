package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.lamps.LampAnimation;
import ru.sortix.parkourbeat.levels.lamps.LampWall;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;

/**
 * Рисование узора для стены.
 * <p>
 * Холст 9 на 4, а растягивается он на всю сетку стены: строителю не нужно попадать
 * в каждую лампу, достаточно нарисовать сердечко один раз.
 */
public class LampPatternMenu extends ParkourBeatInventory implements EditLevelMenu {

    private static final int WIDTH = 9;
    private static final int HEIGHT = 4;

    private final @NonNull EditActivity activity;
    private final @NonNull LampWall wall;
    private final boolean[] canvas = new boolean[WIDTH * HEIGHT];
    private int symmetry = 0;

    public LampPatternMenu(@NonNull ParkourBeat plugin, String lang,
                           @NonNull EditActivity activity, @NonNull LampWall wall) {
        super(plugin, 6, lang, PbText.of(Lang.raw(lang, "auto.lamp_pattern_menu.lamp_pattern_menu.1")));
        this.activity = activity;
        this.wall = wall;
        this.load();
        this.updateItems();
    }

    private void load() {
        String pattern = this.wall.getPattern();
        if (pattern == null) return;
        String[] rows = pattern.split("/");
        for (int y = 0; y < Math.min(HEIGHT, rows.length); y++) {
            String row = rows[y];
            for (int x = 0; x < Math.min(WIDTH, row.length()); x++) {
                this.canvas[y * WIDTH + x] = row.charAt(x) != '0';
            }
        }
    }

    public void updateItems() {
        this.clearInventory();

        for (int i = 0; i < WIDTH * HEIGHT; i++) {
            int row = 1 + (i / WIDTH);
            int column = 1 + (i % WIDTH);
            boolean lit = this.canvas[i];
            int index = i;

            this.setItem(row, column, ItemUtils.create(
                lit ? Material.REDSTONE_LAMP : Material.BLACK_STAINED_GLASS_PANE, meta -> {
                    meta.displayName(PbText.of(lit ? Lang.raw(this.lang, "auto.lamp_pattern_menu.update_items.1") : Lang.raw(this.lang, "auto.lamp_pattern_menu.update_items.2"))
                        .decoration(TextDecoration.ITALIC, false));
                    meta.lore(one(lit ? Lang.raw(this.lang, "auto.lamp_pattern_menu.update_items.3") : Lang.raw(this.lang, "auto.lamp_pattern_menu.update_items.4")));
                }), event -> {
                this.paint(index, event.isLeft());
                this.updateItems();
            });
        }

        String[] modes = {Lang.raw(this.lang, "auto.lamp_pattern_menu.update_items.5"), Lang.raw(this.lang, "auto.lamp_pattern_menu.update_items.6"), Lang.raw(this.lang, "auto.lamp_pattern_menu.update_items.7"), Lang.raw(this.lang, "auto.lamp_pattern_menu.update_items.8")};
        this.setItem(5, 3, ItemUtils.create(Material.ITEM_FRAME, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.lamp_pattern_menu.update_items.9") + modes[this.symmetry])
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.lamp_pattern_menu.update_items.10")));
            lore.add(line(Lang.raw(this.lang, "auto.lamp_pattern_menu.update_items.11")));
            meta.lore(lore);
        }), event -> {
            this.symmetry = (this.symmetry + 1) % modes.length;
            this.updateItems();
        });

        this.setItem(5, 5, ItemUtils.create(Material.BARRIER, meta ->
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.lamp_pattern_menu.update_items.12")).decoration(TextDecoration.ITALIC, false))
        ), event -> {
            java.util.Arrays.fill(this.canvas, false);
            this.updateItems();
        });

        this.setItem(5, 7, ItemUtils.create(Material.REDSTONE, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.lamp_pattern_menu.update_items.13")).decoration(TextDecoration.ITALIC, false));
            meta.lore(one(Lang.raw(this.lang, "auto.lamp_pattern_menu.update_items.14")));
        }), event -> {
            this.heart();
            this.updateItems();
        });

        this.fillBorder();

        this.setItem(6, 5, ItemUtils.create(Material.LIME_CONCRETE, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.lamp_pattern_menu.update_items.15")).decoration(TextDecoration.ITALIC, false));
            meta.lore(one(Lang.raw(this.lang, "auto.lamp_pattern_menu.update_items.16")));
        }), event -> {
            this.save();
            this.wall.setAnimation(LampAnimation.PATTERN);
            new LampWallMenu(this.plugin, this.lang, this.activity, this.wall).open(event.getPlayer());
        });
    }

    private void paint(int index, boolean lit) {
        int x = index % WIDTH, y = index / WIDTH;
        this.set(x, y, lit);
        if (this.symmetry == 1 || this.symmetry == 3) this.set(WIDTH - 1 - x, y, lit);
        if (this.symmetry == 2 || this.symmetry == 3) this.set(x, HEIGHT - 1 - y, lit);
        if (this.symmetry == 3) this.set(WIDTH - 1 - x, HEIGHT - 1 - y, lit);
    }

    private void set(int x, int y, boolean lit) {
        if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT) return;
        this.canvas[y * WIDTH + x] = lit;
    }

    private void heart() {
        String[] rows = {"011000110", "111101111", "011111110", "000111000"};
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                this.canvas[y * WIDTH + x] = rows[y].charAt(x) != '0';
            }
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < HEIGHT; y++) {
            if (y > 0) sb.append('/');
            for (int x = 0; x < WIDTH; x++) sb.append(this.canvas[y * WIDTH + x] ? '1' : '0');
        }
        this.wall.setPattern(sb.toString());
    }

    private static Component line(@NonNull String text) {
        return PbText.of(text).decoration(TextDecoration.ITALIC, false);
    }

    private static List<Component> one(@NonNull String text) {
        List<Component> lore = new ArrayList<>();
        lore.add(line(text));
        return lore;
    }
}
