package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

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
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;

/** Справочник прямо в игре: наведите на предмет и читайте, клик высыпает то же в чат. */
public class WonderHelpMenu extends ParkourBeatInventory implements EditLevelMenu {

    private final @NonNull EditActivity activity;

    /**
     * Страницы справочника: только иконка и идентификатор. Заголовок и текст лежат
     * в lang.yml под {@code wonder.help.<id>.title} и {@code .lore} - массив
     * статический и собирается при загрузке класса, когда языка ещё нет.
     */
    private static final String[] PAGES = {
        "CLOCK",
        "ARROW",
        "BLAZE_POWDER",
        "COMPASS",
        "BOOK",
        "REPEATER",
        "REDSTONE_TORCH",
        "PAPER",
    };

    public WonderHelpMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 4, lang, PbText.of(Lang.raw(lang, "auto.wonder_help_menu.wonder_help_menu.1")));
        this.activity = activity;
        this.updateItems();
    }

    public void updateItems() {
        this.clearInventory();

        for (int i = 0; i < PAGES.length && i < 8; i++) {
            String page = PAGES[i];
            String prefix = "wonder.help." + page.toLowerCase(java.util.Locale.ROOT);
            Material icon;
            try {
                icon = Material.valueOf(page);
            } catch (IllegalArgumentException e) {
                icon = Material.PAPER;
            }

            this.setItem(2, i + 1, ItemUtils.create(icon, meta -> {
                meta.displayName(PbText.of("&b" + Lang.raw(this.lang, prefix + ".title"))
                    .decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                for (String text : Lang.raw(this.lang, prefix + ".lore").split("\n")) {
                    lore.add(line("&7" + text));
                }
                lore.add(Component.empty());
                lore.add(line(Lang.raw(this.lang, "auto.wonder_help_menu.update_items.1")));
                meta.lore(lore);
            }), event -> {
                Player player = event.getPlayer();
                player.closeInventory();
                player.sendMessage(Component.empty());
                String lang = PlayerLang.of(player);
                player.sendMessage(PbText.of("&8✦ &b" + Lang.raw(lang, prefix + ".title")));
                for (String text : Lang.raw(lang, prefix + ".lore").split("\n")) {
                    player.sendMessage(PbText.of("&7" + text));
                }
                player.sendMessage(Component.empty());
            });
        }

        this.setItem(3, 5, ItemUtils.create(Material.WRITTEN_BOOK, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_help_menu.update_items.2")).decoration(TextDecoration.ITALIC, false));
            meta.lore(one(Lang.raw(this.lang, "auto.wonder_help_menu.update_items.3")));
        }), event -> {
            event.getPlayer().closeInventory();
            WonderManual.send(event.getPlayer());
        });

        this.fillBorder();

        this.setItem(4, 5, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_help_menu.update_items.4")).decoration(TextDecoration.ITALIC, false))
        ), event -> new WonderEffectsMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
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
