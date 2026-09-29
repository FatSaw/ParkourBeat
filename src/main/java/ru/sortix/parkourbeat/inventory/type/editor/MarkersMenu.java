package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.RegularItems;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.world.TeleportUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MarkersMenu extends PaginatedMenu<ParkourBeat, ru.sortix.parkourbeat.levels.settings.HelperMarker> implements EditLevelMenu {
    private final @NonNull EditActivity activity;
    private final @NonNull Level level;

    public MarkersMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 6, lang, FallZonesMenu.text(Lang.raw(lang, "auto.markers_menu.markers_menu.1")), 0, 5 * 9);
        this.activity = activity;
        this.level = activity.getLevel();
        this.updateAllItems();
    }

    @Override
    protected @NonNull Collection<ru.sortix.parkourbeat.levels.settings.HelperMarker> getAllItems() {
        return new ArrayList<>(this.level.getLightShow().getHelperMarkers());
    }

    @Override
    protected @NonNull ItemStack createItemDisplay(
        @NonNull ru.sortix.parkourbeat.levels.settings.HelperMarker marker
    ) {
        boolean right = marker.getKind()
            == ru.sortix.parkourbeat.levels.settings.HelperMarker.Kind.RIGHT;

        return ItemUtils.create(right ? Material.REDSTONE_BLOCK : Material.SLIME_BLOCK, meta -> {
            meta.displayName(FallZonesMenu.text((right ? "&c" : "&a") + marker.format()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.markers_menu.create_item_display.1")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.markers_menu.create_item_display.2")));
            meta.lore(lore);
        });
    }

    @Override
    protected void onClick(@NonNull ClickEvent event,
                           @NonNull ru.sortix.parkourbeat.levels.settings.HelperMarker marker) {
        Player player = event.getPlayer();

        if (!event.isLeft()) {
            this.level.getLightShow().removeHelperMarker(marker);
            player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.markers_menu.on_click.1")));
            this.updateAllItems();
            return;
        }

        player.closeInventory();

        Location target = marker.getPosition().toLocation(this.level.getWorld());
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());

        TeleportUtils.teleportAsync(this.plugin, player, target);
        player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.markers_menu.on_click.2") + marker.format()));
    }

    @Override
    protected void onPageDisplayed() {
        this.setNextPageItem(6, 3);
        this.setPreviousPageItem(6, 7);

        if (this.level.getLightShow().getHelperMarkers().isEmpty()) {
            this.setItem(1, 5, ItemUtils.create(Material.BARRIER, meta -> {
                meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.markers_menu.on_page_displayed.1")));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.markers_menu.on_page_displayed.2")));
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.markers_menu.on_page_displayed.3")));
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.markers_menu.on_page_displayed.4")));
                meta.lore(lore);
            }), null);
        }

        this.setItem(6, 1, ItemUtils.create(Material.LAVA_BUCKET, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.markers_menu.on_page_displayed.5")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.markers_menu.on_page_displayed.6")
                + this.level.getLightShow().getHelperMarkers().size()));
            meta.lore(lore);
        }), event -> {
            this.level.getLightShow().clearHelperMarkers();
            event.getPlayer().sendMessage(FallZonesMenu.text(Lang.raw(this.lang, "auto.markers_menu.on_page_displayed.7")));
            this.updateAllItems();
        });

        // На «Копателе» прыжков нет вообще: маркеры там ставятся вручную нотным блоком
        // из инвентаря тестирования, и переключатель автопрыжков только путает.
        boolean digger = ru.sortix.parkourbeat.digger.DiggerManager.isDigger(this.activity.getLevel());

        boolean auto = this.activity.isAutoJumpMarkers();
        if (digger) this.setItem(6, 2, null, null);
        else this.setItem(6, 2, ItemUtils.create(
            auto ? Material.LIME_DYE : Material.GRAY_DYE, meta -> {
                meta.displayName(FallZonesMenu.text(auto
                    ? Lang.raw(this.lang, "auto.markers_menu.on_page_displayed.8")
                    : Lang.raw(this.lang, "auto.markers_menu.on_page_displayed.9")));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.markers_menu.on_page_displayed.10")));
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.markers_menu.on_page_displayed.11")));
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.markers_menu.on_page_displayed.12")));
                meta.lore(lore);
            }), event -> {
            this.activity.setAutoJumpMarkers(!this.activity.isAutoJumpMarkers());
            event.getPlayer().sendMessage(FallZonesMenu.text(this.activity.isAutoJumpMarkers()
                ? Lang.raw(this.lang, "auto.markers_menu.on_page_displayed.13")
                : Lang.raw(this.lang, "auto.markers_menu.on_page_displayed.14")));
            this.updateAllItems();
        });

        this.setItem(6, 5, RegularItems.closeInventory(this.lang),
            event -> event.getPlayer().closeInventory());

        this.setItem(6, 9, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.markers_menu.on_page_displayed.15")))
        ), event -> new EditorMainMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }
}
