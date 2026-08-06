package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
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
import ru.sortix.parkourbeat.levels.settings.LightShowCue;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.levels.settings.LightShowSharpness;
import ru.sortix.parkourbeat.levels.settings.SkyType;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.util.ArrayList;
import java.util.Collection;

public class LightShowCuesMenu extends PaginatedMenu<ParkourBeat, LightShowCue> implements EditLevelMenu {
    private final @NonNull EditActivity activity;
    private final @NonNull Level level;

    public LightShowCuesMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 6, lang, LangOptions.inventory_editorcues_title.getComponent(lang), 0, 5 * 9);
        this.activity = activity;
        this.level = activity.getLevel();
        this.updateAllItems();
    }

    @NonNull
    private LightShowSettings getLightShow() {
        return this.level.getLightShow();
    }

    @Override
    protected @NonNull Collection<LightShowCue> getAllItems() {
        return new ArrayList<>(this.getLightShow().getSkyCues());
    }

    @Override
    protected @NonNull ItemStack createItemDisplay(@NonNull LightShowCue cue) {
        SkyType sky = cue.getSky();
        LightShowSharpness sharpness = cue.getSharpness();
        boolean selected = this.activity.getSelectedElement() == cue;
        boolean crossesNightVision =
            this.getLightShow().getSkyBefore(cue).isNightVision() != sky.isNightVision();
        return ItemUtils.create(sky.getIconMaterial(), meta -> {
            meta.displayName(LangOptions.inventory_editorcues_entry_name.getComponent(
                lang, new Placeholders("%time%", cue.getStartTimecode())));
            java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>(
                LangOptions.inventory_editorcues_entry_lore.getComponents(lang,
                    new Placeholders("%sky%", sky.getDisplayNameString(lang)),
                    new Placeholders("%start%", cue.getStartTimecode()),
                    new Placeholders("%end%", cue.getEndTimecode()),
                    new Placeholders("%sharpness%", sharpness.getDisplayNameString(lang))));
            if (crossesNightVision) {
                lore.addAll(LangOptions.inventory_editorcues_entry_nvwarning.getComponents(lang));
            }
            if (selected) {
                lore.addAll(LangOptions.inventory_editorcues_entry_selected.getComponents(lang));
            }
            meta.lore(lore);
        });
    }

    @Override
    protected void onPageDisplayed() {
        this.setNextPageItem(6, 3);
        this.setPreviousPageItem(6, 7);

        if (this.getLightShow().isSkyCuesEmpty()) {
            this.setItem(1, 5, ItemUtils.create(Material.BARRIER, meta -> {
                meta.displayName(LangOptions.inventory_editorcues_empty_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorcues_empty_lore.getComponents(lang));
            }), null);
        }

        this.setItem(6, 1, ItemUtils.create(Material.CLOCK, meta -> {
            meta.displayName(LangOptions.inventory_editorcues_add_name.getComponent(lang));
            meta.lore(LangOptions.inventory_editorcues_add_lore.getComponents(lang));
        }), this::addCue);

        this.setItem(6, 5, RegularItems.closeInventory(lang), event -> event.getPlayer().closeInventory());

        this.setItem(6, 9, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(LangOptions.inventory_editorcues_back.getComponent(lang))
        ), event -> new LightShowMenu(this.plugin, lang, this.activity).open(event.getPlayer()));
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull LightShowCue cue) {
        Player player = event.getPlayer();
        if (event.isLeft()) {
            new LightShowCueMenu(this.plugin, this.lang, this.activity, cue).open(player);
            return;
        }
        if (!event.isShift()) return;

        if (!this.getLightShow().removeSkyCue(cue)) return;
        if (this.activity.getSelectedElement() == cue) this.activity.setSelectedElement(null);
        player.sendMessage(LangOptions.inventory_editorcue_deleted.getComponent(
            lang, new Placeholders("%time%", cue.getStartTimecode())));
        this.activity.updateInventoriesOfAllEditors(LightShowCuesMenu.class, PaginatedMenu::updateAllItems);
    }

    private void addCue(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        if (this.getLightShow().getSkyCuesAmount() >= LightShowSettings.MAX_CUES) {
            player.sendMessage(LangOptions.inventory_editorcues_add_limit.getComponent(lang));
            return;
        }

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editorcues_add_unavilable.getComponent(lang));
            return;
        }

        player.sendMessage(LangOptions.inventory_editorcues_add_request.getComponent(lang));
        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(LangOptions.inventory_editorcues_add_timeout.getComponent(lang));
                return;
            }

            int timeMillis = TimeUtils.parseTimecode(message);
            if (timeMillis < 0) {
                player.sendMessage(LangOptions.inventory_editorcues_add_invalid.getComponent(lang));
                return;
            }

            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.finishAddingCue(player, timeMillis));
        });
    }

    private void finishAddingCue(@NonNull Player player, int timeMillis) {
        LightShowSettings lightShow = this.getLightShow();

        LightShowCue cue = new LightShowCue(
            timeMillis,
            timeMillis + LightShowCue.DEFAULT_DURATION_MILLIS,
            lightShow.getBaseSky(),
            LightShowSharpness.DEFAULT
        );

        if (!lightShow.addSkyCue(cue)) {
            player.sendMessage(LangOptions.inventory_editorcues_add_limit.getComponent(lang));
            return;
        }

        player.sendMessage(LangOptions.inventory_editorcues_add_success.getComponent(
            lang, new Placeholders("%time%", cue.getStartTimecode())));

        this.activity.updateInventoriesOfAllEditors(LightShowCuesMenu.class, PaginatedMenu::updateAllItems);

        new LightShowCueMenu(this.plugin, this.lang, this.activity, cue).open(player);
    }
}
