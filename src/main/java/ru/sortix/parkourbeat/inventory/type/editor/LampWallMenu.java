package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.lamps.LampAnimation;
import ru.sortix.parkourbeat.levels.lamps.LampEngine;
import ru.sortix.parkourbeat.levels.lamps.LampWall;
import ru.sortix.parkourbeat.listeners.LampWandListener;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Настройка одной ламповой стены. */
public class LampWallMenu extends LightShowElementMenu<LampWall> {

    public LampWallMenu(@NonNull ParkourBeat plugin, String lang,
                        @NonNull EditActivity activity, @NonNull LampWall wall) {
        super(plugin, lang, activity, wall, PbText.of(Lang.raw(lang, "auto.lamp_wall_menu.lamp_wall_menu.1")));
        this.updateItems();
    }

    @Override
    protected void addSpecificItems() {
        // Палочка сразу знает, какую стену правим: отдельная кнопка «применить» не нужна
        for (Player viewer : this.plugin.getServer().getOnlinePlayers()) {
            if (viewer.getOpenInventory() != null) LampWandListener.setEditing(viewer, this.element);
        }
        LampAnimation animation = this.element.getAnimation();

        this.setItem(1, 5, ItemUtils.create(animation.getIcon(), meta -> {
            meta.displayName(PbText.of("&e" + animation.getDisplay(this.lang)).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line("&7" + animation.getHint(this.lang)));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.1") + this.element.getColumns() + Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.2") + this.element.getRows()));
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.3")
                + LampEngine.countLamps(this.activity.getLevel().getWorld(), this.element)));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.4")));
            meta.lore(lore);
        }), event -> {
            this.element.setAnimation(this.element.getAnimation().next());
            this.click(event.getPlayer());
            this.reopen(event.getPlayer());
        });

        this.setItem(2, 3, ItemUtils.create(Material.GOLDEN_PICKAXE, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.5")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.6")));
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.7")));
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.8") + this.element.getX1() + " " + this.element.getY1() + " " + this.element.getZ1()
                + Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.9") + this.element.getX2() + " " + this.element.getY2() + " " + this.element.getZ2()));
            meta.lore(lore);
        }), this::takeSelection);

        this.setItem(2, 5, ItemUtils.create(
            this.element.isInverted() ? Material.BLACK_CONCRETE : Material.WHITE_CONCRETE, meta -> {
                meta.displayName(PbText.of(this.element.isInverted() ? Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.10") : Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.11"))
                    .decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.12")));
                lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.13")));
                meta.lore(lore);
            }), event -> {
            this.element.setInverted(!this.element.isInverted());
            this.click(event.getPlayer());
            this.reopen(event.getPlayer());
        });

        this.setItem(2, 7, ItemUtils.create(Material.PAINTING, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.14")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.15")));
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.16")));
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.17")));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.18")));
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.19") + (this.element.getPattern() == null ? Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.20") : Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.21"))));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.22")));
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.23")));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            if (!event.isLeft()) {
                new LampPatternMenu(this.plugin, this.lang, this.activity, this.element).open(player);
                return;
            }
            LampWandListener.setEditing(player, this.element);
            boolean on = LampWandListener.togglePaintMode(player);
            player.closeInventory();
            player.getInventory().addItem(LampWandListener.createWand());
            player.sendMessage(PbText.of(on
                ? Lang.raw(PlayerLang.of(player), "auto.lamp_wall_menu.add_specific_items.24")
                : Lang.raw(PlayerLang.of(player), "auto.lamp_wall_menu.add_specific_items.25")));
            if (on) {
                this.element.setAnimation(ru.sortix.parkourbeat.levels.lamps.LampAnimation.PATTERN);
                org.bukkit.World world = this.activity.getLevel().getWorld();
                if (world != null) LampEngine.showPattern(world, this.element);
            }
        });

        this.setItem(3, 5, ItemUtils.create(Material.ENDER_EYE, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.26")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.27")));
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.28")));
            meta.lore(lore);
        }), this::preview);

        double speed = this.element.getSpeed();
        this.setItem(4, 5, ItemUtils.create(Material.CLOCK, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.29") + fmt(speed) + "x")
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.30")));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.lamp_wall_menu.add_specific_items.31")));
            meta.lore(lore);
        }), event -> {
            double step = event.isShift() ? 1.0D : 0.25D;
            this.element.setSpeed(Math.max(0.05D, speed + (event.isLeft() ? step : -step)));
            this.click(event.getPlayer());
            this.reopen(event.getPlayer());
        });
    }

    private void takeSelection(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        int[] selection = LampWandListener.selection(player);
        if (selection == null) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.lamp_wall_menu.take_selection.1")));
            return;
        }
        this.element.setCorners(selection[0], selection[1], selection[2],
            selection[3], selection[4], selection[5]);
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.lamp_wall_menu.take_selection.2")
            + this.element.getColumns() + Lang.raw(PlayerLang.of(player), "auto.lamp_wall_menu.take_selection.3") + this.element.getRows()));
        this.click(player);
        this.reopen(player);
    }

    /** Крутит анимацию в мире и сама убирает за собой. */
    private void preview(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        org.bukkit.World world = this.activity.getLevel().getWorld();
        if (world == null) return;

        int lamps = LampEngine.countLamps(world, this.element);
        if (lamps == 0) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.lamp_wall_menu.preview.1")));
            return;
        }
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.lamp_wall_menu.preview.2") + lamps));

        final LampWall wall = this.element;
        final int total = Math.max(20, wall.getDurationMillis() / 50);
        final int[] tick = {0};
        this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, task -> {
            if (tick[0] > total || !player.isOnline()) {
                LampEngine.reset(world, wall);
                task.cancel();
                if (player.isOnline()) {
                    new LampWallMenu(this.plugin, this.lang, this.activity, wall).open(player);
                }
                return;
            }
            double phase = (double) tick[0] / total * Math.max(0.05D, wall.getSpeed());
            LampEngine.apply(world, wall, phase);
            tick[0]++;
        }, 1L, 1L);
    }

    private void click(@NonNull Player player) {
        this.persist();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.5f);
    }

    /** Любая правка сразу уходит на диск: перезагрузка плагина больше ничего не теряет. */
    protected void persist() {
        ru.sortix.parkourbeat.utils.wonder.WonderSave.now(this.plugin, this.activity.getLevel());
    }

    private static Component line(@NonNull String text) {
        return PbText.of(text).decoration(TextDecoration.ITALIC, false);
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value)
            : String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    protected boolean removeElement() {
        return this.getLightShow().removeLampWall(this.element);
    }

    @Override
    protected void openListMenu(@NonNull Player player) {
        new LampWallsMenu(this.plugin, this.lang, this.activity).open(player);
    }

    @Override
    protected void reopen(@NonNull Player player) {
        new LampWallMenu(this.plugin, this.lang, this.activity, this.element).open(player);
    }
}
