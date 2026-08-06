package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.LightShowElement;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.util.function.IntConsumer;

/**
 * Shared editor for one lightshow element: its start, its end when it has one, the wand hint,
 * delete and back. Subclasses only add whatever makes their type special.
 */
public abstract class LightShowElementMenu<E extends LightShowElement>
    extends ParkourBeatInventory implements EditLevelMenu {

    protected final @NonNull EditActivity activity;
    protected final @NonNull Level level;
    protected final @NonNull E element;

    protected LightShowElementMenu(@NonNull ParkourBeat plugin,
                                   String lang,
                                   @NonNull EditActivity activity,
                                   @NonNull E element,
                                   @NonNull Component title
    ) {
        super(plugin, 5, lang, title);
        this.activity = activity;
        this.level = activity.getLevel();
        this.element = element;
        this.activity.setSelectedElement(element);
    }

    @NonNull
    protected LightShowSettings getLightShow() {
        return this.level.getLightShow();
    }

    protected abstract void addSpecificItems();

    protected abstract boolean removeElement();

    protected abstract void openListMenu(@NonNull Player player);

    protected abstract void reopen(@NonNull Player player);

    /**
     * Hook for elements that also have to touch the world, like biome zones.
     */
    protected void onTimecodeAboutToChange() {
    }

    protected void onTimecodeChanged() {
    }

    public void updateItems() {
        this.clearInventory();

        this.addSpecificItems();

        this.setItem(
            3,
            3,
            ItemUtils.create(Material.CLOCK, meta -> {
                meta.displayName(LangOptions.inventory_editorelement_start_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorelement_start_lore.getComponents(
                    lang, new Placeholders("%time%", TimeUtils.formatTimecode(this.element.getStartMillis()))));
            }),
            event -> this.requestTimecode(event.getPlayer(), true));

        if (this.element.hasEnd()) {
            this.setItem(
                3,
                7,
                ItemUtils.create(Material.COMPASS, meta -> {
                    meta.displayName(LangOptions.inventory_editorelement_end_name.getComponent(lang));
                    meta.lore(LangOptions.inventory_editorelement_end_lore.getComponents(lang,
                        new Placeholders("%time%", TimeUtils.formatTimecode(this.element.getEndMillis())),
                        new Placeholders("%duration%", TimeUtils.formatSeconds(
                            this.element.getEndMillis() - this.element.getStartMillis()))));
                }),
                event -> this.requestTimecode(event.getPlayer(), false));
        }

        this.setItem(
            5,
            3,
            ItemUtils.create(Material.BARRIER, meta -> {
                meta.displayName(LangOptions.inventory_editorelement_delete_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorelement_delete_lore.getComponents(lang));
            }),
            this::deleteElement);

        this.setItem(
            5,
            5,
            ItemUtils.create(Material.STICK, meta -> {
                meta.displayName(LangOptions.inventory_editorelement_wand_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorelement_wand_lore.getComponents(lang));
            }),
            event -> {
                Player wandTaker = event.getPlayer();
                ru.sortix.parkourbeat.listeners.LightShowWandListener.give(this.plugin, wandTaker, this.lang);
                wandTaker.sendMessage(LangOptions.level_editor_wand_given.getComponent(lang));
                wandTaker.closeInventory();
            });

        this.setItem(
            5,
            7,
            ItemUtils.create(Material.REDSTONE_TORCH, meta ->
                meta.displayName(LangOptions.inventory_editorelement_back.getComponent(lang))),
            event -> this.openListMenu(event.getPlayer()));
    }

    private void deleteElement(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        if (this.activity.getSelectedElement() == this.element) this.activity.setSelectedElement(null);
        if (this.removeElement()) {
            player.sendMessage(LangOptions.inventory_editorelement_deleted.getComponent(
                lang, new Placeholders("%time%", this.element.getTimecode())));
            this.activity.applyBaseSky();
        }
        this.openListMenu(player);
    }

    protected void requestTimecode(@NonNull Player player, boolean start) {
        player.closeInventory();

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editorelement_unavilable.getComponent(lang));
            return;
        }

        player.sendMessage((start
            ? LangOptions.inventory_editorelement_start_request
            : LangOptions.inventory_editorelement_end_request).getComponent(lang));

        this.requestValue(player, message -> {
            int timeMillis = TimeUtils.parseTimecode(message);
            if (timeMillis < 0) return -1;
            return timeMillis;
        }, timeMillis -> {
            this.onTimecodeAboutToChange();
            if (start) this.element.setStartMillis(timeMillis);
            else this.element.setEndMillis(timeMillis);
            this.getLightShow().sort();
            this.onTimecodeChanged();
            player.sendMessage((start
                ? LangOptions.inventory_editorelement_startset
                : LangOptions.inventory_editorelement_endset).getComponent(
                lang, new Placeholders("%time%", TimeUtils.formatTimecode(timeMillis))));
        });
    }

    protected void requestValue(@NonNull Player player,
                                @NonNull java.util.function.ToIntFunction<String> parser,
                                @NonNull IntConsumer consumer
    ) {
        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(LangOptions.inventory_editorelement_timeout.getComponent(lang));
                return;
            }
            int value = parser.applyAsInt(message);
            if (value < 0) {
                player.sendMessage(LangOptions.inventory_editorelement_invalid.getComponent(lang));
                return;
            }
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                consumer.accept(value);
                this.reopen(player);
            });
        });
    }
}
