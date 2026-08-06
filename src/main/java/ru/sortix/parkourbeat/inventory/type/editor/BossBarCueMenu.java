package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.BossBarCue;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

public class BossBarCueMenu extends ParkourBeatInventory implements EditLevelMenu {
    private final @NonNull EditActivity activity;
    private final @NonNull Level level;
    private final @NonNull BossBarCue cue;

    public BossBarCueMenu(@NonNull ParkourBeat plugin,
                          String lang,
                          @NonNull EditActivity activity,
                          @NonNull BossBarCue cue
    ) {
        super(plugin, 5, lang, LangOptions.inventory_editorbosscue_title.getComponent(lang));
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
            3,
            ItemUtils.create(this.cue.getColor().getIconMaterial(), meta -> {
                meta.displayName(LangOptions.inventory_editorbosscue_color_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorbosscue_color_lore.getComponents(
                    lang, new Placeholders("%color%", this.cue.getColor().getDisplayNameString(lang))));
            }),
            this::selectColor);

        this.setItem(
            2,
            7,
            ItemUtils.create(Material.CLOCK, meta -> {
                meta.displayName(LangOptions.inventory_editorbosscue_time_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorbosscue_time_lore.getComponents(
                    lang, new Placeholders("%time%", this.cue.getTimecode())));
            }),
            this::changeTimecode);

        this.setItem(
            4,
            3,
            ItemUtils.create(Material.BARRIER, meta -> {
                meta.displayName(LangOptions.inventory_editorbosscue_delete_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorbosscue_delete_lore.getComponents(lang));
            }),
            this::deleteCue);

        this.setItem(
            4,
            7,
            ItemUtils.create(Material.REDSTONE_TORCH, meta ->
                meta.displayName(LangOptions.inventory_editorbosscue_back.getComponent(lang))),
            event -> new BossBarCuesMenu(this.plugin, lang, this.activity).open(event.getPlayer()));
    }

    private void selectColor(@NonNull ClickEvent event) {
        new SelectBossBarColorMenu(
            this.plugin,
            this.lang,
            this.activity,
            this.cue.getColor(),
            (player, barColor) -> {
                this.cue.setColor(barColor);
                this.refreshEditors();
                new BossBarCueMenu(this.plugin, this.lang, this.activity, this.cue).open(player);
            },
            player -> new BossBarCueMenu(this.plugin, this.lang, this.activity, this.cue).open(player)
        ).open(event.getPlayer());
    }

    private void changeTimecode(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editorbosscue_unavilable.getComponent(lang));
            return;
        }

        player.sendMessage(LangOptions.inventory_editorbosscue_time_request.getComponent(lang));
        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(LangOptions.inventory_editorbosscue_time_timeout.getComponent(lang));
                return;
            }

            int timeMillis = TimeUtils.parseTimecode(message);
            if (timeMillis < 0) {
                player.sendMessage(LangOptions.inventory_editorbosscue_time_invalid.getComponent(lang));
                return;
            }

            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                this.cue.setTimeMillis(timeMillis);
                this.getLightShow().sort();

                player.sendMessage(LangOptions.inventory_editorbosscue_time_success.getComponent(
                    lang, new Placeholders("%time%", this.cue.getTimecode())));

                this.refreshEditors();
                new BossBarCueMenu(this.plugin, this.lang, this.activity, this.cue).open(player);
            });
        });
    }

    private void deleteCue(@NonNull ClickEvent event) {
        Player player = event.getPlayer();

        if (this.activity.getSelectedElement() == this.cue) this.activity.setSelectedElement(null);
        if (this.getLightShow().removeBossBarCue(this.cue)) {
            player.sendMessage(LangOptions.inventory_editorbosscue_deleted.getComponent(
                lang, new Placeholders("%time%", this.cue.getTimecode())));
        }

        this.refreshEditors();
        new BossBarCuesMenu(this.plugin, this.lang, this.activity).open(player);
    }

    private void refreshEditors() {
        this.activity.updateInventoriesOfAllEditors(BossBarCuesMenu.class, PaginatedMenu::updateAllItems);
        this.activity.updateInventoriesOfAllEditors(BossBarCueMenu.class, BossBarCueMenu::updateItems);
        this.activity.updateInventoriesOfAllEditors(LightShowMenu.class, LightShowMenu::updateItems);
    }
}
