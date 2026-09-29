package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.lamps.LampEngine;
import ru.sortix.parkourbeat.levels.lamps.LampWall;
import ru.sortix.parkourbeat.listeners.LampWandListener;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Список ламповых стен уровня. */
public class LampWallsMenu extends LightShowElementsMenu<LampWall> {

    public LampWallsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, lang, activity, PbText.of(Lang.raw(lang, "auto.lamp_walls_menu.lamp_walls_menu.1")));
        this.plugin.get(ru.sortix.parkourbeat.utils.wonder.WonderStorage.class)
            .ensureLoaded(activity.getLevel());
        this.updateAllItems();
    }

    @Override
    protected @NonNull Collection<LampWall> getElements() {
        return this.getLightShow().getLampWalls();
    }

    @Override
    protected @NonNull ItemStack createEntry(@NonNull LampWall wall) {
        return ItemUtils.create(wall.getAnimation().getIcon(), meta -> {
            meta.displayName(PbText.of("&e" + wall.getStartTimecode() + " &8· &f" + wall.getAnimation().getDisplay(this.lang))
                .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.lamp_walls_menu.create_entry.1") + wall.getStartTimecode() + " &8— &f" + wall.getEndTimecode()
                + " &8(" + TimeUtils.formatSeconds(wall.getDurationMillis()) + Lang.raw(this.lang, "auto.lamp_walls_menu.create_entry.2")));
            lore.add(line(Lang.raw(this.lang, "auto.lamp_walls_menu.create_entry.3") + wall.getColumns() + Lang.raw(this.lang, "auto.lamp_walls_menu.create_entry.4") + wall.getRows()));
            lore.add(line(Lang.raw(this.lang, "auto.lamp_walls_menu.create_entry.5")
                + LampEngine.countLamps(this.activity.getLevel().getWorld(), wall)));
            if (wall.isInverted()) lore.add(line(Lang.raw(this.lang, "auto.lamp_walls_menu.create_entry.6")));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.lamp_walls_menu.create_entry.7")));
            lore.add(line(Lang.raw(this.lang, "auto.lamp_walls_menu.create_entry.8")));
            meta.lore(lore);
        });
    }

    /** Любая правка сразу уходит на диск: перезагрузка плагина больше ничего не теряет. */
    protected void persist() {
        ru.sortix.parkourbeat.utils.wonder.WonderSave.now(this.plugin, this.activity.getLevel());
    }

    private static Component line(@NonNull String text) {
        return PbText.of(text).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected @NonNull LampWall createNew(int timeMillis) {
        // Заготовка вокруг строителя: дальше он поправит углы палочкой
        Player any = this.activity.getPlayer();
        int x = any == null ? 0 : any.getLocation().getBlockX();
        int y = any == null ? 64 : any.getLocation().getBlockY();
        int z = any == null ? 0 : any.getLocation().getBlockZ();
        return new LampWall(timeMillis, timeMillis + LampWall.DEFAULT_DURATION_MILLIS,
            x - 4, y, z, x + 4, y + 4, z);
    }

    @Override
    protected boolean addElement(@NonNull LampWall element) {
        return this.getLightShow().addLampWall(element);
    }

    @Override
    protected boolean removeElement(@NonNull LampWall element) {
        return this.getLightShow().removeLampWall(element);
    }

    @Override
    protected void openElementMenu(@NonNull Player player, @NonNull LampWall element) {
        new LampWallMenu(this.plugin, this.lang, this.activity, element).open(player);
    }

    @Override
    protected @NonNull Material addIconMaterial() {
        return Material.REDSTONE_LAMP;
    }

    @Override
    protected void onPageDisplayed() {
        super.onPageDisplayed();

        this.setItem(6, 1, ItemUtils.create(Material.REDSTONE_LAMP, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.lamp_walls_menu.on_page_displayed.1")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.lamp_walls_menu.on_page_displayed.2")));
            lore.add(line(Lang.raw(this.lang, "auto.lamp_walls_menu.on_page_displayed.3")));
            meta.lore(lore);
        }), this::createWallHere);
        this.persist();

        this.setItem(6, 2, ItemUtils.create(Material.REDSTONE_LAMP, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.lamp_walls_menu.on_page_displayed.4")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.lamp_walls_menu.on_page_displayed.5")));
            lore.add(line(Lang.raw(this.lang, "auto.lamp_walls_menu.on_page_displayed.6")));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.lamp_walls_menu.on_page_displayed.7")));
            meta.lore(lore);
        }), this::giveLamps);

        this.setItem(6, 8, ItemUtils.create(Material.STICK, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.lamp_walls_menu.on_page_displayed.8")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.lamp_walls_menu.on_page_displayed.9")));
            lore.add(line(Lang.raw(this.lang, "auto.lamp_walls_menu.on_page_displayed.10")));
            lore.add(line(Lang.raw(this.lang, "auto.lamp_walls_menu.on_page_displayed.11")));
            meta.lore(lore);
        }), this::giveWand);
    }

    /** Создаём стену на месте строителя и сразу даём инструмент, чтобы не искать его в меню. */
    private void createWallHere(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        int here = ru.sortix.parkourbeat.utils.wonder.WonderTimeline
            .millisAt(this.activity.getLevel(), player.getLocation(), 6.0D);
        if (here < 0) here = 0;

        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();
        LampWall wall = new LampWall(here, here + LampWall.DEFAULT_DURATION_MILLIS,
            x - 4, y, z, x + 4, y + 4, z);

        if (!this.getLightShow().addLampWall(wall)) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.lamp_walls_menu.create_wall_here.1")));
            return;
        }
        this.getLightShow().sort();
        this.persist();

        player.getInventory().addItem(LampWandListener.createWand());
        LampWandListener.setEditing(player, wall);
        player.sendTitle(" ", Lang.raw(PlayerLang.of(player), "auto.lamp_walls_menu.create_wall_here.2"), 0, 60, 15);
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.lamp_walls_menu.create_wall_here.3")));

        new LampWallMenu(this.plugin, this.lang, this.activity, wall).open(player);
    }

    private void giveLamps(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.getInventory().addItem(new ItemStack(Material.REDSTONE_LAMP, 64));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.lamp_walls_menu.give_lamps.1")));
    }

    private void giveWand(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.getInventory().addItem(LampWandListener.createWand());
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.lamp_walls_menu.give_wand.1")));
    }
}
