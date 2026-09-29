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
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.wonder.WonderCategory;
import ru.sortix.parkourbeat.levels.wonder.WonderEffect;
import ru.sortix.parkourbeat.levels.wonder.WonderPreset;
import ru.sortix.parkourbeat.levels.wonder.WonderPresets;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Библиотека готовых эффектов: вкладки категорий сверху, сами эффекты в середине,
 * низ забит стеклом с единственной кнопкой возврата — как в остальных меню плагина.
 * <p>
 * Если меню открыто из настройки эффекта, выбор заменяет эффект на месте, не создавая новый.
 */
public class WonderPresetsMenu extends ParkourBeatInventory implements EditLevelMenu {

    private final @NonNull EditActivity activity;
    private final @NonNull Level level;
    private final @Nullable WonderEffect target;
    private final boolean autoTime;
    private WonderCategory category = null;
    private int page = 0;

    public WonderPresetsMenu(@NonNull ParkourBeat plugin,
                             String lang,
                             @NonNull EditActivity activity,
                             @Nullable WonderEffect target
    ) {
        this(plugin, lang, activity, target, null, false);
    }

    public WonderPresetsMenu(@NonNull ParkourBeat plugin,
                             String lang,
                             @NonNull EditActivity activity,
                             @Nullable WonderEffect target,
                             @Nullable WonderCategory startCategory,
                             boolean autoTime
    ) {
        super(plugin, 6, lang, PbText.of(Lang.raw(lang, "auto.wonder_presets_menu.wonder_presets_menu.1")));
        this.activity = activity;
        this.level = activity.getLevel();
        this.target = target;
        this.autoTime = autoTime;
        if (startCategory != null) this.category = startCategory;
        if (target != null) {
            WonderPreset current = WonderPresets.byId(target.getPresetId());
            if (current != null) this.category = current.getCategory();
        }
        this.updateItems();
    }

    public void updateItems() {
        this.clearInventory();

        WonderCategory[] categories = WonderCategory.values();
        for (int i = 0; i < categories.length && i < 9; i++) {
            WonderCategory tab = categories[i];
            boolean selected = tab == this.category;
            this.setItem(1, i + 1, ItemUtils.create(selected ? tab.getIcon() : Material.GRAY_STAINED_GLASS_PANE, meta -> {
                meta.displayName(PbText.of((selected ? "&f▸ " : "&8") + tab.getDisplay(this.lang))
                    .decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(line("&7" + tab.getHint(this.lang)));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.1") + WonderPresets.byCategory(tab).size()));
                meta.lore(lore);
            }), event -> {
                this.category = tab;
                this.page = 0;
                event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
                this.updateItems();
            });
        }

        boolean allTab = this.category == null;
        this.setItem(1, 9, ItemUtils.create(allTab ? Material.CHEST : Material.GRAY_STAINED_GLASS_PANE, meta -> {
            meta.displayName(PbText.of((allTab ? Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.2") : Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.3")))
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.4") + WonderPresets.amount() + Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.5")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.6")));
            meta.lore(lore);
        }), event -> {
            this.category = null;
            this.page = 0;
            this.updateItems();
        });

        List<WonderPreset> presets = this.category == null
            ? WonderPresets.all()
            : WonderPresets.byCategory(this.category);

        int perPage = 28;
        int from = this.page * perPage;
        int slot = 0;
        for (int index = from; index < presets.size() && slot < perPage; index++) {
            WonderPreset preset = presets.get(index);
            int row = 2 + (slot / 7);
            int column = 2 + (slot % 7);
            slot++;

            this.setItem(row, column, ItemUtils.create(preset.getIcon(), meta -> {
                meta.displayName(PbText.of("&f" + preset.getDisplay(this.lang)).decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(line("&7" + preset.getHint(this.lang)));
                lore.add(Component.empty());
                lore.add(line(Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.7") + (preset.getDurationMillis() / 1000.0D) + Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.8")));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.9") + preset.getAnchor().getDisplay(this.lang)));
                lore.add(Component.empty());
                lore.add(line(Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.10") + (this.target == null ? Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.11") : Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.12"))));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.13")));
                meta.lore(lore);
            }), event -> this.choose(event, preset));
        }

        if (this.page > 0) {
            this.setItem(6, 3, ItemUtils.create(Material.ARROW, meta ->
                meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.14")).decoration(TextDecoration.ITALIC, false))
            ), event -> {
                this.page--;
                this.updateItems();
            });
        }
        final int shown = from + slot;
        final int total = presets.size();
        if (total > from + perPage) {
            this.setItem(6, 7, ItemUtils.create(Material.ARROW, meta -> {
                meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.15")).decoration(TextDecoration.ITALIC, false));
                meta.lore(one(Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.16") + shown + Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.17") + total));
            }), event -> {
                this.page++;
                this.updateItems();
            });
        }

        this.fillBorder();

        this.setItem(6, 5, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_presets_menu.update_items.18")).decoration(TextDecoration.ITALIC, false))
        ), event -> {
            if (this.target != null) {
                new WonderEffectMenu(this.plugin, this.lang, this.activity, this.target).open(event.getPlayer());
            } else if (this.autoTime) {
                new WonderAddMenu(this.plugin, this.lang, this.activity).open(event.getPlayer());
            } else {
                new WonderEffectsMenu(this.plugin, this.lang, this.activity).open(event.getPlayer());
            }
        });
    }

    private void choose(@NonNull ClickEvent event, @NonNull WonderPreset preset) {
        Player player = event.getPlayer();

        if (!event.isLeft()) {
            // Смотрим «в воздухе»: на таймлайн ничего не попадает
            WonderEffect sample = preset.toEffect(suggestStart());
            WonderPreview.show(this.plugin, player, this.level, sample,
                who -> new WonderPresetsMenu(this.plugin, this.lang, this.activity,
                    this.target, this.category, this.autoTime).open(who));
            return;
        }

        if (this.target != null) {
            this.target.setPresetId(preset.getId());
            this.target.setSpec(preset.getSpec());
            this.target.setParams(preset.getParams());
            this.target.setAnchor(preset.getAnchor());
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_presets_menu.choose.1") + preset.getDisplay(this.lang)));
            new WonderEffectMenu(this.plugin, this.lang, this.activity, this.target).open(player);
            return;
        }

        int start = suggestStart();
        if (this.autoTime && start == 0) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_presets_menu.choose.2")));
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_presets_menu.choose.3")));
        }
        WonderEffect effect = preset.toEffect(start);
        if (!this.level.getLightShow().addWonderEffect(effect)) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_presets_menu.choose.4")));
            return;
        }
        this.level.getLightShow().sort();
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
        new WonderEffectMenu(this.plugin, this.lang, this.activity, effect).open(player);
    }

    /**
     * Куда ставить новый эффект.
     * <p>
     * В режиме добавления берём то место трассы, где стоит строитель: он и так подошёл
     * туда, где хочет эффект. Иначе ставим после последнего, чтобы таймлайн не слипался.
     */
    private int suggestStart() {
        if (this.autoTime) {
            Player player = null;
            for (Player online : this.plugin.getServer().getOnlinePlayers()) {
                if (online.getWorld().equals(this.level.getWorld())) {
                    player = online;
                    break;
                }
            }
            if (player != null) {
                int here = ru.sortix.parkourbeat.utils.wonder.WonderTimeline
                    .millisAt(this.level, player.getLocation(), 6.0D);
                if (here >= 0) return here;
            }
            return 0;
        }
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
