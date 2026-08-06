package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
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
import ru.sortix.parkourbeat.levels.settings.LightShowElement;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Shared list behaviour for every kind of lightshow element: paginate, add by timecode,
 * open one for editing, delete with shift.
 */
public abstract class LightShowElementsMenu<E extends LightShowElement>
    extends PaginatedMenu<ParkourBeat, E> implements EditLevelMenu {

    protected final @NonNull EditActivity activity;
    protected final @NonNull Level level;

    protected LightShowElementsMenu(@NonNull ParkourBeat plugin,
                                    String lang,
                                    @NonNull EditActivity activity,
                                    @NonNull Component title
    ) {
        super(plugin, 6, lang, title, 0, 5 * 9);
        this.activity = activity;
        this.level = activity.getLevel();
    }

    @NonNull
    protected LightShowSettings getLightShow() {
        return this.level.getLightShow();
    }

    @NonNull
    protected abstract Collection<E> getElements();

    @NonNull
    protected abstract ItemStack createEntry(@NonNull E element);

    @NonNull
    protected abstract E createNew(int timeMillis);

    protected abstract boolean addElement(@NonNull E element);

    protected abstract boolean removeElement(@NonNull E element);

    protected abstract void openElementMenu(@NonNull Player player, @NonNull E element);

    @NonNull
    protected abstract Material addIconMaterial();

    @Override
    protected @NonNull Collection<E> getAllItems() {
        return new ArrayList<>(this.getElements());
    }

    @Override
    protected @NonNull ItemStack createItemDisplay(@NonNull E element) {
        return this.createEntry(element);
    }

    @Override
    protected void onPageDisplayed() {
        this.setNextPageItem(6, 3);
        this.setPreviousPageItem(6, 7);

        if (this.getElements().isEmpty()) {
            this.setItem(1, 5, ItemUtils.create(Material.BARRIER, meta -> {
                meta.displayName(LangOptions.inventory_editorelement_empty_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorelement_empty_lore.getComponents(lang));
            }), null);
        }

        this.setItem(6, 1, ItemUtils.create(this.addIconMaterial(), meta -> {
            meta.displayName(LangOptions.inventory_editorelement_add_name.getComponent(lang));
            meta.lore(LangOptions.inventory_editorelement_add_lore.getComponents(lang));
        }), this::addByTimecode);

        this.setItem(6, 5, RegularItems.closeInventory(lang), event -> event.getPlayer().closeInventory());

        this.setItem(6, 9, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(LangOptions.inventory_editorelement_back.getComponent(lang))
        ), event -> new LightShowMenu(this.plugin, lang, this.activity).open(event.getPlayer()));
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull E element) {
        Player player = event.getPlayer();
        if (event.isLeft()) {
            this.openElementMenu(player, element);
            return;
        }
        if (!event.isShift()) return;

        if (this.activity.getSelectedElement() == element) this.activity.setSelectedElement(null);
        if (!this.removeElement(element)) return;

        player.sendMessage(LangOptions.inventory_editorelement_deleted.getComponent(
            lang, new Placeholders("%time%", element.getTimecode())));
        this.updateAllItems();
    }

    private void addByTimecode(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        if (this.getElements().size() >= LightShowSettings.MAX_CUES) {
            player.sendMessage(LangOptions.inventory_editorelement_limit.getComponent(lang));
            return;
        }

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editorelement_unavilable.getComponent(lang));
            return;
        }

        player.sendMessage(LangOptions.inventory_editorelement_start_request.getComponent(lang));
        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(LangOptions.inventory_editorelement_timeout.getComponent(lang));
                return;
            }

            int timeMillis = TimeUtils.parseTimecode(message);
            if (timeMillis < 0) {
                player.sendMessage(LangOptions.inventory_editorelement_invalid.getComponent(lang));
                return;
            }

            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                E element = this.createNew(timeMillis);
                if (!this.addElement(element)) {
                    player.sendMessage(LangOptions.inventory_editorelement_limit.getComponent(lang));
                    return;
                }
                player.sendMessage(LangOptions.inventory_editorelement_added.getComponent(
                    lang, new Placeholders("%time%", element.getTimecode())));
                this.openElementMenu(player, element);
            });
        });
    }

    @Nullable
    protected static <T> T firstOrNull(@NonNull Collection<T> collection) {
        for (T element : collection) return element;
        return null;
    }
}
