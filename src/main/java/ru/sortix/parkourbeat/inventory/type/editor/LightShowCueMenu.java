package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.LightShowCue;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.levels.settings.LightShowSharpness;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.util.function.IntConsumer;

public class LightShowCueMenu extends ParkourBeatInventory implements EditLevelMenu {
    private final @NonNull EditActivity activity;
    private final @NonNull Level level;
    private final @NonNull LightShowCue cue;

    public LightShowCueMenu(@NonNull ParkourBeat plugin,
                            String lang,
                            @NonNull EditActivity activity,
                            @NonNull LightShowCue cue
    ) {
        super(plugin, 5, lang, LangOptions.inventory_editorcue_title.getComponent(lang));
        this.activity = activity;
        this.level = activity.getLevel();
        this.cue = cue;
        this.activity.setSelectedElement(cue);
        this.updateItems();
    }

    @NonNull
    private LightShowSettings getLightShow() {
        return this.level.getLightShow();
    }

    public void updateItems() {
        this.clearInventory();

        this.setItem(
            2,
            2,
            ItemUtils.create(this.cue.getSky().getIconMaterial(), meta -> {
                meta.displayName(LangOptions.inventory_editorcue_sky_name.getComponent(lang));
                java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>(
                    LangOptions.inventory_editorcue_sky_lore.getComponents(
                        lang, new Placeholders("%sky%", this.cue.getSky().getDisplayNameString(lang))));
                if (this.crossesNightVision()) {
                    lore.addAll(LangOptions.inventory_editorcue_sky_nvwarning.getComponents(lang));
                }
                meta.lore(lore);
            }),
            this::selectSky);

        this.setItem(
            2,
            4,
            ItemUtils.create(Material.CLOCK, meta -> {
                meta.displayName(LangOptions.inventory_editorcue_start_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorcue_start_lore.getComponents(
                    lang, new Placeholders("%time%", this.cue.getStartTimecode())));
            }),
            this::changeStart);

        this.setItem(
            2,
            6,
            ItemUtils.create(Material.COMPASS, meta -> {
                meta.displayName(LangOptions.inventory_editorcue_end_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorcue_end_lore.getComponents(lang,
                    new Placeholders("%time%", this.cue.getEndTimecode()),
                    new Placeholders("%duration%", TimeUtils.formatSeconds(this.cue.getDurationMillis()))));
            }),
            this::changeEnd);

        this.setItem(
            2,
            8,
            ItemUtils.create(Material.COMPARATOR, meta -> {
                meta.displayName(LangOptions.inventory_editorcue_sharpness_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorcue_sharpness_lore.getComponents(
                    lang, new Placeholders("%sharpness%", this.cue.getSharpness().getDisplayNameString(lang))));
            }),
            this::switchSharpness);

        this.setItem(
            4,
            3,
            ItemUtils.create(Material.BARRIER, meta -> {
                meta.displayName(LangOptions.inventory_editorcue_delete_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorcue_delete_lore.getComponents(lang));
            }),
            this::deleteCue);

        this.setItem(
            4,
            5,
            ItemUtils.create(Material.STICK, meta -> {
                meta.displayName(LangOptions.inventory_editorcue_wand_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorcue_wand_lore.getComponents(lang));
            }),
            event -> event.getPlayer().closeInventory());

        this.setItem(
            4,
            7,
            ItemUtils.create(Material.REDSTONE_TORCH, meta ->
                meta.displayName(LangOptions.inventory_editorcue_back.getComponent(lang))),
            event -> new LightShowCuesMenu(this.plugin, lang, this.activity).open(event.getPlayer()));
    }

    private void selectSky(@NonNull ClickEvent event) {
        new SelectSkyMenu(
            this.plugin,
            this.lang,
            this.activity,
            this.cue.getSky(),
            (player, skyType) -> {
                this.cue.setSky(skyType);
                if (this.getLightShow().getSkyBefore(this.cue).isNightVision() != skyType.isNightVision()) {
                    player.sendMessage(LangOptions.inventory_editorcue_sky_nvchanged.getComponent(lang));
                }
                this.refreshEditors();
                new LightShowCueMenu(this.plugin, this.lang, this.activity, this.cue).open(player);
            },
            player -> new LightShowCueMenu(this.plugin, this.lang, this.activity, this.cue).open(player)
        ).open(event.getPlayer());
    }

    private void changeStart(@NonNull ClickEvent event) {
        this.requestTimecode(
            event.getPlayer(),
            LangOptions.inventory_editorcue_start_request,
            LangOptions.inventory_editorcue_start_timeout,
            LangOptions.inventory_editorcue_start_invalid,
            timeMillis -> {
                this.cue.setStartMillis(timeMillis);
                this.getLightShow().sort();
                event.getPlayer().sendMessage(LangOptions.inventory_editorcue_start_success.getComponent(
                    lang, new Placeholders("%time%", this.cue.getStartTimecode())));
            });
    }

    private void changeEnd(@NonNull ClickEvent event) {
        this.requestTimecode(
            event.getPlayer(),
            LangOptions.inventory_editorcue_end_request,
            LangOptions.inventory_editorcue_end_timeout,
            LangOptions.inventory_editorcue_end_invalid,
            timeMillis -> {
                this.cue.setEndMillis(timeMillis);
                event.getPlayer().sendMessage(LangOptions.inventory_editorcue_end_success.getComponent(
                    lang, new Placeholders("%time%", this.cue.getEndTimecode())));
            });
    }

    private void requestTimecode(@NonNull Player player,
                                 @NonNull LangOptions request,
                                 @NonNull LangOptions timeout,
                                 @NonNull LangOptions invalid,
                                 @NonNull IntConsumer consumer
    ) {
        player.closeInventory();

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editorcue_unavilable.getComponent(lang));
            return;
        }

        player.sendMessage(request.getComponent(lang));
        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(timeout.getComponent(lang));
                return;
            }

            int timeMillis = TimeUtils.parseTimecode(message);
            if (timeMillis < 0) {
                player.sendMessage(invalid.getComponent(lang));
                return;
            }

            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                consumer.accept(timeMillis);
                this.refreshEditors();
                new LightShowCueMenu(this.plugin, this.lang, this.activity, this.cue).open(player);
            });
        });
    }

    private void switchSharpness(@NonNull ClickEvent event) {
        Player player = event.getPlayer();

        LightShowSharpness sharpness = this.cue.getSharpness().next();
        this.cue.setSharpness(sharpness);

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 1f);
        player.sendMessage(LangOptions.inventory_editorcue_sharpness_changed.getComponent(
            lang, new Placeholders("%sharpness%", sharpness.getDisplayNameString(lang))));

        this.updateItems();
        this.refreshEditors();
    }

    private void deleteCue(@NonNull ClickEvent event) {
        Player player = event.getPlayer();

        if (this.getLightShow().removeSkyCue(this.cue)) {
            player.sendMessage(LangOptions.inventory_editorcue_deleted.getComponent(
                lang, new Placeholders("%time%", this.cue.getStartTimecode())));
        }
        if (this.activity.getSelectedElement() == this.cue) this.activity.setSelectedElement(null);

        this.refreshEditors();
        new LightShowCuesMenu(this.plugin, this.lang, this.activity).open(player);
    }

    private boolean crossesNightVision() {
        return this.getLightShow().getSkyBefore(this.cue).isNightVision() != this.cue.getSky().isNightVision();
    }

    private void refreshEditors() {
        this.activity.updateInventoriesOfAllEditors(LightShowCuesMenu.class, PaginatedMenu::updateAllItems);
        this.activity.updateInventoriesOfAllEditors(LightShowCueMenu.class, LightShowCueMenu::updateItems);
        this.activity.updateInventoriesOfAllEditors(LightShowMenu.class, LightShowMenu::updateItems);
    }
}
