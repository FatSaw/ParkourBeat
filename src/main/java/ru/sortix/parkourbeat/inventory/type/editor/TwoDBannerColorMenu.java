package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.twod.TwoDBanners;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;

/**
 * Выбор цвета баннера-перехода. Цвет чисто декоративный: тип перехода зашит в узор,
 * поэтому строитель может красить баннеры под оформление уровня как угодно.
 */
public class TwoDBannerColorMenu extends ParkourBeatInventory implements EditLevelMenu {

    private static final String[] COLOR_NAMES = {
        "Белый", "Оранжевый", "Пурпурный", "Голубой",
        "Жёлтый", "Лаймовый", "Розовый", "Серый",
        "Светло-серый", "Бирюзовый", "Фиолетовый", "Синий",
        "Коричневый", "Зелёный", "Красный", "Чёрный"
    };

    private static final DyeColor[] COLORS = {
        DyeColor.WHITE, DyeColor.ORANGE, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE,
        DyeColor.YELLOW, DyeColor.LIME, DyeColor.PINK, DyeColor.GRAY,
        DyeColor.LIGHT_GRAY, DyeColor.CYAN, DyeColor.PURPLE, DyeColor.BLUE,
        DyeColor.BROWN, DyeColor.GREEN, DyeColor.RED, DyeColor.BLACK
    };

    private final @NonNull EditActivity activity;
    private final TwoDBanners.@NonNull Type type;

    public TwoDBannerColorMenu(@NonNull ParkourBeat plugin, String lang,
                               @NonNull EditActivity activity, TwoDBanners.@NonNull Type type) {
        super(plugin, 4, lang, PbText.of(type.title));
        this.activity = activity;
        this.type = type;

        for (int index = 0; index < COLORS.length; index++) {
            DyeColor color = COLORS[index];
            String name = COLOR_NAMES[index];

            int row = 1 + index / 8;
            int column = 1 + index % 8;

            this.setItem(row, column, TwoDBanners.createItem(type, color), event -> {
                Player player = event.getPlayer();
                player.getInventory().addItem(TwoDBanners.createItem(type, color));
                player.sendMessage(PbText.of(Lang.raw(lang, "auto.two_d_banner_color_menu.two_d_banner_color_menu.1") + name));
                player.closeInventory();
            });
        }

        this.setItem(4, 9, ItemUtils.create(Material.REDSTONE_TORCH, meta -> {
            meta.displayName(PbText.item(Lang.raw(lang, "auto.two_d_banner_color_menu.two_d_banner_color_menu.2")));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(PbText.item(Lang.raw(lang, "auto.two_d_banner_color_menu.two_d_banner_color_menu.3")));
            meta.lore(lore);
        }), event -> new TwoDSettingsMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }
}
