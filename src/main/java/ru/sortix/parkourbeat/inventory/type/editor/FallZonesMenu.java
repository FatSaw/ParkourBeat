package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;
import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.FallZoneRenderer;
import ru.sortix.parkourbeat.levels.settings.FallZone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import ru.sortix.parkourbeat.utils.text.PbText;

public class FallZonesMenu extends LightShowElementsMenu<FallZone> {
    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    public FallZonesMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, lang, activity, text(Lang.raw(lang, "auto.fall_zones_menu.fall_zones_menu.1")));
        this.updateAllItems();
    }

    static Component text(@NonNull String legacy) {
        return PbText.of(legacy).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected @NonNull Collection<FallZone> getElements() {
        return this.getLightShow().getFallZones();
    }

    @Override
    protected @NonNull ItemStack createEntry(@NonNull FallZone zone) {
        return ItemUtils.create(zone.isEnabled() ? Material.RED_CONCRETE : Material.GRAY_CONCRETE, meta -> {
            meta.displayName(text("&cЗона падения &f" + zone.getStartTimecode()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(text("&7Начало: &f" + zone.getStartTimecode()));
            lore.add(text("&7Конец: &f" + zone.getEndTimecode()));
            lore.add(text("&7Высота смерти: &fY " + zone.getDeathY()));
            lore.add(text("&7Состояние: " + (zone.isEnabled() ? "&aвключена" : "&cвыключена")));
            lore.add(Component.empty());
            lore.add(text("&8ЛКМ - настроить"));
            lore.add(text("&8Shift + ПКМ - удалить"));
            meta.lore(lore);
        });
    }

    @Override
    protected @NonNull FallZone createNew(int timeMillis) {
        int defaultY = FallZoneRenderer.getDefaultDeathY(this.level);
        return new FallZone(timeMillis, timeMillis + FallZone.DEFAULT_LENGTH_MILLIS, defaultY);
    }

    @Override
    protected boolean addElement(@NonNull FallZone element) {
        return this.getLightShow().addFallZone(element);
    }

    @Override
    protected boolean removeElement(@NonNull FallZone element) {
        return this.getLightShow().removeFallZone(element);
    }

    @Override
    protected void openElementMenu(@NonNull Player player, @NonNull FallZone element) {
        new FallZoneMenu(this.plugin, this.lang, this.activity, element).open(player);
    }

    @Override
    protected @NonNull Material addIconMaterial() {
        return Material.RED_CONCRETE;
    }

    @Override
    protected void onPageDisplayed() {
        super.onPageDisplayed();

        int defaultY = FallZoneRenderer.getDefaultDeathY(this.level);
        boolean hasCustom = this.level.getLightShow().getDefaultDeathY() != null;
        this.setItem(6, 2, ItemUtils.create(Material.BEDROCK, meta -> {
            meta.displayName(text(Lang.raw(this.lang, "auto.fall_zones_menu.on_page_displayed.1")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(text(Lang.raw(this.lang, "auto.fall_zones_menu.on_page_displayed.2")));
            lore.add(text(Lang.raw(this.lang, "auto.fall_zones_menu.on_page_displayed.3") + defaultY + (hasCustom ? " &8(своя)" : " &8(авто)")));
            lore.add(Component.empty());
            lore.add(text("&eЛКМ &7- задать вручную"));
            lore.add(text("&eПКМ &7- сбросить на авто"));
            lore.add(Component.empty());
            lore.add(text(Lang.raw(this.lang, "auto.fall_zones_menu.on_page_displayed.4")));
            lore.add(text(Lang.raw(this.lang, "auto.fall_zones_menu.on_page_displayed.5")));
            lore.add(text(Lang.raw(this.lang, "auto.fall_zones_menu.on_page_displayed.6")));
            lore.add(text(Lang.raw(this.lang, "auto.fall_zones_menu.on_page_displayed.7")));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            if (!event.isLeft()) {
                this.level.getLightShow().setDefaultDeathY(null);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                this.updateAllItems();
                return;
            }
            player.closeInventory();
            ru.sortix.parkourbeat.player.input.PlayersInputManager manager = this.plugin.get(ru.sortix.parkourbeat.player.input.PlayersInputManager.class);
            if (manager.isInputRequested(player)) return;
            player.sendMessage(ru.sortix.parkourbeat.utils.text.PbText.of("&eВведите базовую высоту смерти в чат (целое число):"));
            manager.requestChatInput(player, 20 * 60).thenAccept(message -> {
                if (message == null) return;
                try {
                    int y = Integer.parseInt(message.trim());
                    this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                        this.level.getLightShow().setDefaultDeathY(y);
                        this.open(player);
                    });
                } catch (NumberFormatException e) {
                    player.sendMessage(ru.sortix.parkourbeat.utils.text.PbText.of("&cНеверное число!"));
                }
            });
        });

        this.setItem(6, 4, ItemUtils.create(Material.SPECTRAL_ARROW, meta -> {
            meta.displayName(text(Lang.raw(this.lang, "auto.fall_zones_menu.on_page_displayed.8")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(text(Lang.raw(this.lang, "auto.fall_zones_menu.on_page_displayed.9")));
            lore.add(text(Lang.raw(this.lang, "auto.fall_zones_menu.on_page_displayed.10")));
            lore.add(text(Lang.raw(this.lang, "auto.fall_zones_menu.on_page_displayed.11")));
            lore.add(Component.empty());
            lore.add(text(Lang.raw(this.lang, "auto.fall_zones_menu.on_page_displayed.12")));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            player.closeInventory();
            FallZoneRenderer.preview(this.plugin, player, this.level);
            player.sendMessage(text(Lang.raw(this.lang, "auto.fall_zones_menu.on_page_displayed.13")));
        });
    }
}
