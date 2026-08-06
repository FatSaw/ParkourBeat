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
import ru.sortix.parkourbeat.levels.settings.BossBarCue;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.util.ArrayList;
import java.util.Collection;

public class BossBarCuesMenu extends PaginatedMenu<ParkourBeat, BossBarCue> implements EditLevelMenu {
    private final @NonNull EditActivity activity;
    private final @NonNull Level level;

    public BossBarCuesMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 6, lang, LangOptions.inventory_editorbosscues_title.getComponent(lang), 0, 5 * 9);
        this.activity = activity;
        this.level = activity.getLevel();
        this.updateAllItems();
    }

    @NonNull
    private LightShowSettings getLightShow() {
        return this.level.getLightShow();
    }

    @Override
    protected @NonNull Collection<BossBarCue> getAllItems() {
        return new ArrayList<>(this.getLightShow().getBossBarCues());
    }

    @Override
    protected @NonNull ItemStack createItemDisplay(@NonNull BossBarCue cue) {
        return ItemUtils.create(cue.getColor().getIconMaterial(), meta -> {
            meta.displayName(LangOptions.inventory_editorbosscues_entry_name.getComponent(
                lang, new Placeholders("%time%", cue.getTimecode())));
            meta.lore(LangOptions.inventory_editorbosscues_entry_lore.getComponents(
                lang, new Placeholders("%color%", cue.getColor().getDisplayNameString(lang))));
        });
    }

    @Override
    protected void onPageDisplayed() {
        this.setNextPageItem(6, 3);
        this.setPreviousPageItem(6, 7);

        if (this.getLightShow().isBossBarCuesEmpty()) {
            this.setItem(1, 5, ItemUtils.create(Material.BARRIER, meta -> {
                meta.displayName(LangOptions.inventory_editorbosscues_empty_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorbosscues_empty_lore.getComponents(lang));
            }), null);
        }

        this.setItem(6, 1, ItemUtils.create(Material.WHITE_BANNER, meta -> {
            meta.displayName(LangOptions.inventory_editorbosscues_add_name.getComponent(lang));
            meta.lore(LangOptions.inventory_editorbosscues_add_lore.getComponents(lang));
        }), this::addCue);

        this.setItem(6, 5, RegularItems.closeInventory(lang), event -> event.getPlayer().closeInventory());

        this.setItem(6, 9, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(LangOptions.inventory_editorbosscues_back.getComponent(lang))
        ), event -> new LightShowMenu(this.plugin, lang, this.activity).open(event.getPlayer()));
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull BossBarCue cue) {
        Player player = event.getPlayer();
        if (event.isLeft()) {
            new BossBarCueMenu(this.plugin, this.lang, this.activity, cue).open(player);
            return;
        }
        if (!event.isShift()) return;

        if (this.activity.getSelectedElement() == cue) this.activity.setSelectedElement(null);
        if (!this.getLightShow().removeBossBarCue(cue)) return;
        player.sendMessage(LangOptions.inventory_editorbosscue_deleted.getComponent(
            lang, new Placeholders("%time%", cue.getTimecode())));
        this.activity.updateInventoriesOfAllEditors(BossBarCuesMenu.class, PaginatedMenu::updateAllItems);
    }

    private void addCue(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        if (this.getLightShow().getBossBarCuesAmount() >= LightShowSettings.MAX_CUES) {
            player.sendMessage(LangOptions.inventory_editorbosscues_add_limit.getComponent(lang));
            return;
        }

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editorbosscues_add_unavilable.getComponent(lang));
            return;
        }

        player.sendMessage(LangOptions.inventory_editorbosscues_add_request.getComponent(lang));
        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(LangOptions.inventory_editorbosscues_add_timeout.getComponent(lang));
                return;
            }

            int timeMillis = TimeUtils.parseTimecode(message);
            if (timeMillis < 0) {
                player.sendMessage(LangOptions.inventory_editorbosscues_add_invalid.getComponent(lang));
                return;
            }

            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                BossBarCue cue = new BossBarCue(
                    timeMillis, this.level.getLevelSettings().getGameSettings().getBossBarColor());

                if (!this.getLightShow().addBossBarCue(cue)) {
                    player.sendMessage(LangOptions.inventory_editorbosscues_add_limit.getComponent(lang));
                    return;
                }

                player.sendMessage(LangOptions.inventory_editorbosscues_add_success.getComponent(
                    lang, new Placeholders("%time%", cue.getTimecode())));

                this.activity.updateInventoriesOfAllEditors(BossBarCuesMenu.class, PaginatedMenu::updateAllItems);
                new BossBarCueMenu(this.plugin, this.lang, this.activity, cue).open(player);
            });
        });
    }
}
