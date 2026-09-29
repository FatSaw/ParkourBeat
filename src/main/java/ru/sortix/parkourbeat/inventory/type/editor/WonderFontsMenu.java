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
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.wonder.WonderEffect;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.utils.text.PbText;
import ru.sortix.parkourbeat.utils.wonder.WonderFonts;
import ru.sortix.parkourbeat.utils.wonder.WonderSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Выбор шрифта. Все шрифты в одном списке и выглядят одинаково: встроенные просто идут первыми.
 * Каталог загрузки вынесен на отдельную страницу, чтобы не мешался в общем ряду.
 */
public class WonderFontsMenu extends ParkourBeatInventory implements EditLevelMenu {

    private static final int PER_PAGE = 21;

    private final @NonNull EditActivity activity;
    private final @NonNull WonderEffect effect;
    private int page = 0;
    private boolean catalogue = false;

    public WonderFontsMenu(@NonNull ParkourBeat plugin, String lang,
                           @NonNull EditActivity activity, @NonNull WonderEffect effect) {
        super(plugin, 6, lang, PbText.of(Lang.raw(lang, "auto.wonder_fonts_menu.wonder_fonts_menu.1")));
        this.activity = activity;
        this.effect = effect;
        this.updateItems();
    }

    public void updateItems() {
        this.clearInventory();
        WonderFonts fonts = this.plugin.get(WonderFonts.class);

        String current = WonderSpec.get(this.effect.getSpec(), "font");
        if (current == null) current = "pixel";

        List<String> list = new ArrayList<>();
        if (this.catalogue) {
            for (String[] entry : WonderFonts.CATALOGUE) {
                if (!fonts.isCustom(entry[0])) list.add(entry[0]);
            }
        } else {
            list.addAll(fonts.builtInFamilies());
            list.addAll(fonts.customFamilies());
        }

        int from = this.page * PER_PAGE;
        int shown = 0;
        for (int i = from; i < list.size() && shown < PER_PAGE; i++, shown++) {
            String family = list.get(i);
            int row = 2 + (shown / 7);
            int column = 2 + (shown % 7);
            if (this.catalogue) this.catalogueItem(row, column, family);
            else this.fontItem(row, column, family, current);
        }

        if (this.page > 0) {
            this.setItem(5, 3, ItemUtils.create(Material.ARROW, meta ->
                meta.displayName(PbText.of("&eНазад на страницу").decoration(TextDecoration.ITALIC, false))
            ), event -> {
                this.page--;
                this.updateItems();
            });
        }
        if (list.size() > from + PER_PAGE) {
            final int total = list.size();
            final int upTo = from + shown;
            this.setItem(5, 7, ItemUtils.create(Material.ARROW, meta -> {
                meta.displayName(PbText.of("&eДальше").decoration(TextDecoration.ITALIC, false));
                meta.lore(one("&8Показано &f" + upTo + " &8из &f" + total));
            }), event -> {
                this.page++;
                this.updateItems();
            });
        }

        this.setItem(5, 5, ItemUtils.create(this.catalogue ? Material.CHEST : Material.BOOK, meta -> {
            meta.displayName(PbText.of(this.catalogue ? "&eМои шрифты" : "&bСкачать шрифт")
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            if (this.catalogue) {
                lore.add(line("&7Вернуться к тем, что уже стоят"));
            } else {
                lore.add(line("&7Готовый список: рукописные, плакатные,"));
                lore.add(line("&7пиксельные, с кириллицей и без."));
                lore.add(line("&7Или пришлите свою прямую ссылку."));
            }
            meta.lore(lore);
        }), event -> {
            this.catalogue = !this.catalogue;
            this.page = 0;
            this.updateItems();
        });

        if (this.catalogue) {
            this.setItem(5, 8, ItemUtils.create(Material.LEAD, meta -> {
                meta.displayName(PbText.of("&bСвоя ссылка").decoration(TextDecoration.ITALIC, false));
                meta.lore(one("&7Прямая ссылка на ttf или otf"));
            }), event -> this.requestUrl(event.getPlayer()));
        }

        this.fillBorder();

        this.setItem(6, 5, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(PbText.of("&eНазад").decoration(TextDecoration.ITALIC, false))
        ), event -> new WonderEffectMenu(this.plugin, this.lang, this.activity, this.effect)
            .open(event.getPlayer()));
    }

    private void fontItem(int row, int column, @NonNull String family, @NonNull String current) {
        boolean selected = family.equalsIgnoreCase(current);
        this.setItem(row, column, ItemUtils.create(selected ? Material.LIME_DYE : Material.PAPER, meta -> {
            meta.displayName(PbText.of((selected ? "&a▸ " : "&f") + family.replace('_', ' '))
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_fonts_menu.font_item.1")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_fonts_menu.font_item.2")));
            meta.lore(lore);
        }), event -> {
            if (!event.isLeft()) {
                this.previewFont(event.getPlayer(), family);
                return;
            }
            this.effect.setSpec(WonderSpec.set(this.effect.getSpec(), "font", family));
            Player player = event.getPlayer();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.5f);
            new WonderEffectMenu(this.plugin, this.lang, this.activity, this.effect).open(player);
        });
    }

    private void catalogueItem(int row, int column, @NonNull String family) {
        String url = null, hint = "";
        for (String[] entry : WonderFonts.CATALOGUE) {
            if (entry[0].equals(family)) {
                url = entry[1];
                hint = entry[2];
                break;
            }
        }
        if (url == null) return;
        final String link = url;
        final String note = hint;

        this.setItem(row, column, ItemUtils.create(Material.PAPER, meta -> {
            meta.displayName(PbText.of("&b" + family).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line("&7" + note));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.wonder_fonts_menu.catalogue_item.1")));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            player.closeInventory();
            this.plugin.get(WonderFonts.class).download(player, link, name -> {
                this.effect.setSpec(WonderSpec.set(this.effect.getSpec(), "font", name));
                this.catalogue = false;
                this.page = 0;
                new WonderFontsMenu(this.plugin, this.lang, this.activity, this.effect).open(player);
            });
        });
    }

    /** Показывает надпись этим шрифтом на трассе, ничего не меняя в самом эффекте. */
    private void previewFont(@NonNull Player player, @NonNull String family) {
        WonderEffect sample = this.effect.copy();
        sample.setSpec(WonderSpec.set(sample.getSpec(), "font", family));

        String words = WonderSpec.words(sample.getSpec());
        if (words == null || words.trim().isEmpty()) {
            sample.setSpec(WonderSpec.withWords(sample.getSpec(), family.replace('_', ' ')));
        }
        if (sample.getDurationMillis() < 4000) {
            sample.setEndMillis(sample.getStartMillis() + 4000);
        }

        WonderPreview.show(this.plugin, player, this.activity.getLevel(), sample,
            who -> new WonderFontsMenu(this.plugin, this.lang, this.activity, this.effect).open(who));
    }

    private void requestUrl(@NonNull Player player) {
        player.closeInventory();
        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) return;

        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_fonts_menu.request_url.1")));
        manager.requestChatInput(player, 20 * 60).thenAccept(message -> {
            if (message == null) return;
            this.plugin.getServer().getScheduler().runTask(this.plugin, () ->
                this.plugin.get(WonderFonts.class).download(player, message.trim(), family -> {
                    this.effect.setSpec(WonderSpec.set(this.effect.getSpec(), "font", family));
                    this.catalogue = false;
                    new WonderFontsMenu(this.plugin, this.lang, this.activity, this.effect).open(player);
                }));
        });
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
