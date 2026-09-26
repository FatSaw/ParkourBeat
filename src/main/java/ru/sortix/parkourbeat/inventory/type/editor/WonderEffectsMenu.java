package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;
import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.levels.wonder.WonderBridge;
import ru.sortix.parkourbeat.levels.wonder.WonderEffect;
import ru.sortix.parkourbeat.levels.wonder.WonderPreset;
import ru.sortix.parkourbeat.levels.wonder.WonderPresets;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WonderEffectsMenu extends LightShowElementsMenu<WonderEffect> {

    public WonderEffectsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, lang, activity, PbText.of(Lang.raw(lang, "auto.wonder_effects_menu.wonder_effects_menu.1")));
        this.plugin.get(ru.sortix.parkourbeat.utils.wonder.WonderStorage.class)
            .ensureLoaded(activity.getLevel());
        this.updateAllItems();
    }

    @Override
    protected @NonNull Collection<WonderEffect> getElements() {
        return this.getLightShow().getWonderEffects();
    }

    @Override
    protected @NonNull ItemStack createEntry(@NonNull WonderEffect effect) {
        WonderPreset preset = WonderPresets.byId(effect.getPresetId());
        Material icon = preset == null ? Material.NAME_TAG : preset.getIcon();

        return ItemUtils.create(icon, meta -> {
            meta.displayName(PbText.of("&d" + effect.getStartTimecode() + " &8· &f" + effect.getDisplayName(this.lang))
                .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.create_entry.1") + effect.getStartTimecode() + " &8— &f" + effect.getEndTimecode()
                + " &8(" + TimeUtils.formatSeconds(effect.getDurationMillis()) + Lang.raw(this.lang, "auto.wonder_effects_menu.create_entry.2")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.create_entry.3") + effect.getAnchor().getDisplay(this.lang)
                + (effect.getAnchor().name().equals("FOLLOW") ? "" : " &8· &f" + fmt(effect.getDistance()) + Lang.raw(this.lang, "auto.wonder_effects_menu.create_entry.4"))));
            if (effect.getScale() != 1.0D) lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.create_entry.5") + fmt(effect.getScale()) + "x"));

            int points = WonderBridge.estimatePoints(effect);
            if (points > 0) lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.create_entry.6") + points));

            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.create_entry.7")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.create_entry.8")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.create_entry.9")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.create_entry.10")));
            meta.lore(lore);
        });
    }

    protected void persist() {
        ru.sortix.parkourbeat.utils.wonder.WonderSave.now(this.plugin, this.activity.getLevel());
    }

    private static Component line(@NonNull String text) {
        return PbText.of(text).decoration(TextDecoration.ITALIC, false);
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    @Override
    protected @NonNull WonderEffect createNew(int timeMillis) {
        WonderPreset preset = WonderPresets.byId("text_fly");
        if (preset != null) return preset.toEffect(timeMillis);
        return new WonderEffect(timeMillis, timeMillis + WonderEffect.DEFAULT_DURATION_MILLIS,
            "", "text:ВПЕРЁД @ px:0.28", "in:fly int:20t face:player",
            ru.sortix.parkourbeat.levels.wonder.WonderAnchor.AHEAD);
    }

    @Override
    protected boolean addElement(@NonNull WonderEffect element) {
        return this.getLightShow().addWonderEffect(element);
    }

    @Override
    protected boolean removeElement(@NonNull WonderEffect element) {
        return this.getLightShow().removeWonderEffect(element);
    }

    @Override
    protected void openElementMenu(@NonNull Player player, @NonNull WonderEffect element) {
        new WonderEffectMenu(this.plugin, this.lang, this.activity, element).open(player);
    }

    @Override
    protected @NonNull Material addIconMaterial() {
        return Material.NETHER_STAR;
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull WonderEffect element) {
        if (event.isMiddle()) {
            this.duplicateHere(event.getPlayer(), element);
            return;
        }
        if (!event.isLeft() && !event.isShift()) {
            WonderPreview.show(this.plugin, event.getPlayer(), this.level, element,
                who -> new WonderEffectsMenu(this.plugin, this.lang, this.activity).open(who));
            return;
        }
        super.onClick(event, element);
    }

    private void duplicateHere(@NonNull Player player, @NonNull WonderEffect element) {
        int here = ru.sortix.parkourbeat.utils.wonder.WonderTimeline
            .millisAt(this.activity.getLevel(), player.getLocation(), 6.0D);

        WonderEffect copy = element.copy();
        int duration = element.getDurationMillis();
        if (here < 0) {
            player.sendMessage(PbText.of(Lang.raw(this.lang, "auto.wonder_effects_menu.duplicate_here.1")));
            here = 0;
        }
        copy.setStartMillis(here);
        copy.setEndMillis(here + duration);

        if (!this.getLightShow().addWonderEffect(copy)) {
            player.sendMessage(PbText.of(Lang.raw(this.lang, "auto.wonder_effects_menu.duplicate_here.2")));
            return;
        }
        this.getLightShow().sort();
        this.persist();
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.4f);
        player.sendMessage(PbText.of(Lang.raw(this.lang, "auto.wonder_effects_menu.duplicate_here.3") + copy.getStartTimecode()
            + " &8— &f" + copy.getEndTimecode()));
        this.updateAllItems();
    }

    @Override
    protected void onPageDisplayed() {
        super.onPageDisplayed();
        this.persist();

        this.setItem(6, 1, ItemUtils.create(Material.NETHER_STAR, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.1")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.2")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.3")));
            meta.lore(lore);
        }), event -> new WonderAddMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));

        this.setItem(6, 2, ItemUtils.create(Material.BOOKSHELF, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.4")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.5") + WonderPresets.amount()));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.6")));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.7")));
            meta.lore(lore);
        }), event -> {
            if (event.isLeft()) {
                new WonderPresetsMenu(this.plugin, this.lang, this.activity, null).open(event.getPlayer());
            } else {
                new WonderLibraryMenu(this.plugin, this.lang, this.activity, event.getPlayer()).open(event.getPlayer());
            }
        });

        this.setItem(6, 4, ItemUtils.create(Material.PRISMARINE_CRYSTALS, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.8")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.9")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.10")));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.11")));
            meta.lore(lore);
        }), event -> new WonderAiMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));

        this.setItem(6, 6, ItemUtils.create(Material.WRITTEN_BOOK, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.12")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.13")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.14")));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.15")));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            player.closeInventory();
            if (event.isLeft()) {
                WonderManual.send(player);
            } else {
                new WonderHelpMenu(this.plugin, this.lang, this.activity).open(player);
            }
        });

        this.setItem(6, 8, ItemUtils.create(Material.ARROW, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.16")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.17")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effects_menu.on_page_displayed.18")));
            meta.lore(lore);
        }), this::validateAll);
    }

    private void validateAll(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        LightShowSettings lightShow = this.getLightShow();

        if (!WonderBridge.isAvailable()) {
            player.sendMessage(PbText.of(Lang.raw(this.lang, "auto.wonder_effects_menu.validate_all.1")));
            return;
        }

        int broken = 0;
        int totalPoints = 0;
        for (WonderEffect effect : lightShow.getWonderEffects()) {
            String problem = WonderBridge.validate(effect);
            if (problem != null) {
                broken++;
                player.sendMessage(PbText.of(Lang.raw(this.lang, "auto.wonder_effects_menu.validate_all.2") + effect.getStartTimecode() + ": &f" + problem));
            } else {
                totalPoints += WonderBridge.estimatePoints(effect);
            }
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, broken == 0 ? 1.6f : 0.7f);
        player.sendMessage(PbText.of(Lang.raw(this.lang, "auto.wonder_effects_menu.validate_all.3") + lightShow.getWonderEffectsAmount()
            + Lang.raw(this.lang, "auto.wonder_effects_menu.validate_all.4") + totalPoints
            + Lang.raw(this.lang, "auto.wonder_effects_menu.validate_all.5") + (broken == 0 ? Lang.raw(this.lang, "auto.wonder_effects_menu.validate_all.6") : "&c" + broken)));
        player.sendMessage(PbText.of(Lang.raw(this.lang, "auto.wonder_effects_menu.validate_all.7") + WonderBridge.transport()));
    }
}
