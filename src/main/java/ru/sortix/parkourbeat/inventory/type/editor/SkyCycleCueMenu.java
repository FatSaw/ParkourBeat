package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.SkyCycleCue;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

public class SkyCycleCueMenu extends LightShowElementMenu<SkyCycleCue> {
    public SkyCycleCueMenu(@NonNull ParkourBeat plugin,
                           String lang,
                           @NonNull EditActivity activity,
                           @NonNull SkyCycleCue cue
    ) {
        super(plugin, lang, activity, cue, LangOptions.inventory_editorcycle_title.getComponent(lang));
        this.updateItems();
    }

    @Override
    protected void addSpecificItems() {
        this.setItem(
            2,
            5,
            ItemUtils.create(Material.REPEATER, meta -> {
                meta.displayName(LangOptions.inventory_editorcycle_speed_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorcycle_speed_lore.getComponents(
                    lang, new Placeholders("%cycle%", TimeUtils.formatSeconds(this.element.getCycleMillis()))));
            }),
            event -> this.changeCycle(event.getPlayer()));
    }

    private void changeCycle(@NonNull Player player) {
        player.closeInventory();
        player.sendMessage(LangOptions.inventory_editorcycle_speed_request.getComponent(lang));
        this.requestValue(player, message -> {
            int millis = TimeUtils.parseMillis(message);
            if (millis < SkyCycleCue.MIN_CYCLE_MILLIS || millis > SkyCycleCue.MAX_CYCLE_MILLIS) return -1;
            return millis;
        }, cycleMillis -> {
            this.element.setCycleMillis(cycleMillis);
            player.sendMessage(LangOptions.inventory_editorcycle_speed_success.getComponent(
                lang, new Placeholders("%cycle%", TimeUtils.formatSeconds(this.element.getCycleMillis()))));
        });
    }

    @Override
    protected boolean removeElement() {
        return this.getLightShow().removeSkyCycleCue(this.element);
    }

    @Override
    protected void openListMenu(@NonNull Player player) {
        new SkyCycleCuesMenu(this.plugin, this.lang, this.activity).open(player);
    }

    @Override
    protected void reopen(@NonNull Player player) {
        new SkyCycleCueMenu(this.plugin, this.lang, this.activity, this.element).open(player);
    }
}
