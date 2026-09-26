package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.wonder.WonderEffect;
import ru.sortix.parkourbeat.utils.text.PbText;
import ru.sortix.parkourbeat.utils.wonder.WonderSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Холст 9 на 4: строитель рисует фигуру мышью, и она становится эффектом.
 * Никакой математики знать не нужно.
 */
public class WonderDrawMenu extends ParkourBeatInventory implements EditLevelMenu {

    private static final int WIDTH = 9;
    private static final int HEIGHT = 4;
    private static final String[] SIZES = {"0.2", "0.3", "0.4", "0.5", "0.7", "1.0"};

    private final @NonNull EditActivity activity;
    private final @NonNull WonderEffect effect;
    private final boolean[] canvas = new boolean[WIDTH * HEIGHT];
    private int sizeIndex = 2;

    public WonderDrawMenu(@NonNull ParkourBeat plugin, String lang,
                          @NonNull EditActivity activity, @NonNull WonderEffect effect) {
        super(plugin, 6, lang, PbText.of(Lang.raw(lang, "auto.wonder_draw_menu.wonder_draw_menu.1")));
        this.activity = activity;
        this.effect = effect;
        this.load();
        this.updateItems();
    }

    /** Если эффект уже был рисунком, подхватываем его, чтобы можно было доправить. */
    private void load() {
        String spec = this.effect.getSpec().trim();
        if (!spec.toLowerCase(Locale.ROOT).startsWith("pix:")) return;

        String body = spec.substring(4);
        int at = body.indexOf('@');
        if (at >= 0) body = body.substring(0, at);

        String[] rows = body.trim().split("/");
        for (int y = 0; y < Math.min(HEIGHT, rows.length); y++) {
            String row = rows[y];
            for (int x = 0; x < Math.min(WIDTH, row.length()); x++) {
                this.canvas[y * WIDTH + x] = row.charAt(x) != '0';
            }
        }
        String size = WonderSpec.get(this.effect.getSpec(), "px");
        if (size != null) {
            for (int i = 0; i < SIZES.length; i++) if (SIZES[i].equals(size)) this.sizeIndex = i;
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
                lit ? Material.WHITE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE, meta -> {
                    meta.displayName(PbText.of(lit ? "&f✦" : "&8·").decoration(TextDecoration.ITALIC, false));
                    if (!lit) {
                        meta.lore(one(Lang.raw(this.lang, "auto.wonder_draw_menu.update_items.1")));
                    } else {
                        meta.lore(one(Lang.raw(this.lang, "auto.wonder_draw_menu.update_items.2")));
                    }
                }), event -> {
                this.canvas[index] = event.isLeft();
                this.updateItems();
            });
        }

        String particle = WonderSpec.get(this.effect.getSpec(), "particle");
        this.setItem(5, 2, ItemUtils.create(Material.BLAZE_POWDER, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_draw_menu.update_items.3") + (particle == null ? "end_rod" : particle))
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(one(Lang.raw(this.lang, "auto.wonder_draw_menu.update_items.4")));
        }), event -> {
            this.save(false);
            new WonderParticlesMenu(this.plugin, this.lang, this.activity, this.effect).open(event.getPlayer());
        });

        this.setItem(5, 4, ItemUtils.create(Material.STICK, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_draw_menu.update_items.5") + SIZES[this.sizeIndex] + Lang.raw(this.lang, "auto.wonder_draw_menu.update_items.6"))
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_draw_menu.update_items.7")
                + fmt(WIDTH * Double.parseDouble(SIZES[this.sizeIndex])) + Lang.raw(this.lang, "auto.wonder_draw_menu.update_items.8")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_draw_menu.update_items.9")));
            meta.lore(lore);
        }), event -> {
            this.sizeIndex = event.isLeft()
                ? Math.min(SIZES.length - 1, this.sizeIndex + 1)
                : Math.max(0, this.sizeIndex - 1);
            this.updateItems();
        });

        this.setItem(5, 6, ItemUtils.create(Material.BARRIER, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_draw_menu.update_items.10")).decoration(TextDecoration.ITALIC, false));
            meta.lore(one(Lang.raw(this.lang, "auto.wonder_draw_menu.update_items.11")));
        }), event -> {
            java.util.Arrays.fill(this.canvas, false);
            this.updateItems();
        });

        this.setItem(5, 8, ItemUtils.create(Material.ENDER_EYE, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_draw_menu.update_items.12")).decoration(TextDecoration.ITALIC, false));
            meta.lore(one(Lang.raw(this.lang, "auto.wonder_draw_menu.update_items.13")));
        }), event -> {
            this.save(false);
            WonderPreview.show(this.plugin, event.getPlayer(), this.activity.getLevel(), this.effect,
                who -> new WonderDrawMenu(this.plugin, this.lang, this.activity, this.effect).open(who));
        });

        this.fillBorder();

        this.setItem(6, 5, ItemUtils.create(Material.LIME_CONCRETE, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_draw_menu.update_items.14")).decoration(TextDecoration.ITALIC, false));
            meta.lore(one(Lang.raw(this.lang, "auto.wonder_draw_menu.update_items.15") + this.lit()));
        }), event -> {
            this.save(true);
            new WonderEffectMenu(this.plugin, this.lang, this.activity, this.effect).open(event.getPlayer());
        });
    }

    private int lit() {
        int amount = 0;
        for (boolean cell : this.canvas) if (cell) amount++;
        return amount;
    }

    private void save(boolean notify) {
        StringBuilder rows = new StringBuilder();
        for (int y = 0; y < HEIGHT; y++) {
            if (y > 0) rows.append('/');
            for (int x = 0; x < WIDTH; x++) rows.append(this.canvas[y * WIDTH + x] ? '1' : '0');
        }

        String particle = WonderSpec.get(this.effect.getSpec(), "particle");
        String spec = "pix:" + rows + " @ px:" + SIZES[this.sizeIndex] + " refresh:12"
            + (particle == null ? "" : " particle:" + particle);

        this.effect.setSpec(spec);
        this.effect.setPresetId("");
        if (notify) {
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            });
        }
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
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
