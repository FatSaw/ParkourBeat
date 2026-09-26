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
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.wonder.WonderEffect;
import ru.sortix.parkourbeat.utils.text.PbText;
import ru.sortix.parkourbeat.utils.wonder.WonderLibrary;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Топ общей библиотеки: самые залайканные сверху, с автором и датой. */
public class WonderLibraryMenu extends ParkourBeatInventory implements EditLevelMenu {

    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd.MM.yyyy");

    private final @NonNull EditActivity activity;
    private final @NonNull Level level;
    private final @NonNull java.util.UUID viewer;
    private int page = 0;

    public WonderLibraryMenu(@NonNull ParkourBeat plugin, String lang,
                             @NonNull EditActivity activity, @NonNull Player viewer) {
        super(plugin, 6, lang, PbText.of(Lang.raw(PlayerLang.of(viewer), "auto.wonder_library_menu.wonder_library_menu.1")));
        this.activity = activity;
        this.level = activity.getLevel();
        this.viewer = viewer.getUniqueId();
        this.updateItems();
    }

    public void updateItems() {
        this.clearInventory();
        WonderLibrary library = this.plugin.get(WonderLibrary.class);
        List<WonderLibrary.Entry> entries = library.top();

        int perPage = 28;
        int from = this.page * perPage;
        int slot = 0;

        for (int i = from; i < entries.size() && slot < perPage; i++, slot++) {
            WonderLibrary.Entry entry = entries.get(i);
            int place = i + 1;
            int row = 2 + (slot / 7);
            int column = 2 + (slot % 7);

            Material icon = place == 1 ? Material.GOLD_INGOT
                : place == 2 ? Material.IRON_INGOT
                : place == 3 ? Material.BRICK
                : Material.PAPER;

            this.setItem(row, column, ItemUtils.create(icon, meta -> {
                meta.displayName(PbText.of("&f" + place + ". " + entry.getName())
                    .decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(line(Lang.raw(this.lang, "auto.wonder_library_menu.update_items.1") + entry.getAuthor()));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_library_menu.update_items.2") + DATE.format(new Date(entry.getCreated()))));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_library_menu.update_items.3") + entry.getLikesAmount()
                    + (entry.isLikedBy(this.viewer) ? Lang.raw(this.lang, "auto.wonder_library_menu.update_items.4") : "")));
                lore.add(Component.empty());
                lore.add(line(Lang.raw(this.lang, "auto.wonder_library_menu.update_items.5")));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_library_menu.update_items.6")));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_library_menu.update_items.7")));
                if (entry.getAuthorId().equals(this.viewer)) {
                    lore.add(line(Lang.raw(this.lang, "auto.wonder_library_menu.update_items.8")));
                }
                meta.lore(lore);
            }), event -> this.onEntryClick(event.getPlayer(), entry, event.isLeft(), event.isShift()));
        }

        if (entries.isEmpty()) {
            this.setItem(3, 5, ItemUtils.create(Material.BARRIER, meta -> {
                meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_library_menu.update_items.9")).decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(line(Lang.raw(this.lang, "auto.wonder_library_menu.update_items.10")));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_library_menu.update_items.11")));
                meta.lore(lore);
            }), null);
        }

        if (this.page > 0) {
            this.setItem(6, 3, ItemUtils.create(Material.ARROW, meta ->
                meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_library_menu.update_items.12")).decoration(TextDecoration.ITALIC, false))
            ), event -> {
                this.page--;
                this.updateItems();
            });
        }
        if (entries.size() > from + perPage) {
            this.setItem(6, 7, ItemUtils.create(Material.ARROW, meta ->
                meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_library_menu.update_items.13")).decoration(TextDecoration.ITALIC, false))
            ), event -> {
                this.page++;
                this.updateItems();
            });
        }

        this.fillBorder();

        this.setItem(6, 5, ItemUtils.create(Material.REDSTONE_TORCH, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_library_menu.update_items.14")).decoration(TextDecoration.ITALIC, false));
            meta.lore(one(Lang.raw(this.lang, "auto.wonder_library_menu.update_items.15") + library.amount()));
        }), event -> new WonderEffectsMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }

    private void onEntryClick(@NonNull Player player, @NonNull WonderLibrary.Entry entry,
                              boolean left, boolean shift) {
        WonderLibrary library = this.plugin.get(WonderLibrary.class);

        if (shift && left) {
            boolean liked = library.toggleLike(entry, player.getUniqueId());
            player.playSound(player.getLocation(),
                liked ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.UI_BUTTON_CLICK, 0.6f, liked ? 1.6f : 0.8f);
            this.updateItems();
            return;
        }

        if (shift) {
            if (library.delete(entry, player)) {
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_library_menu.on_entry_click.1")));
                this.updateItems();
            } else {
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_library_menu.on_entry_click.2")));
            }
            return;
        }

        WonderEffect effect = entry.toEffect(this.suggestStart());
        if (effect == null) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_library_menu.on_entry_click.3")));
            return;
        }

        if (!left) {
            WonderPreview.show(this.plugin, player, this.level, effect,
                who -> new WonderLibraryMenu(this.plugin, this.lang, this.activity, who).open(who));
            return;
        }

        if (!this.level.getLightShow().addWonderEffect(effect)) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_library_menu.on_entry_click.4")));
            return;
        }
        this.level.getLightShow().sort();
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
        new WonderEffectMenu(this.plugin, this.lang, this.activity, effect).open(player);
    }

    private int suggestStart() {
        int last = 0;
        for (WonderEffect effect : this.level.getLightShow().getWonderEffects()) {
            last = Math.max(last, effect.getEndMillis());
        }
        return last + 1000;
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
