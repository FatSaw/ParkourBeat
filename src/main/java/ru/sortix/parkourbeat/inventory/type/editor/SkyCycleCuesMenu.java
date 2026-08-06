package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.SkyCycleCue;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.util.Collection;

public class SkyCycleCuesMenu extends LightShowElementsMenu<SkyCycleCue> {
    public SkyCycleCuesMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, lang, activity, LangOptions.inventory_editorcycles_title.getComponent(lang));
        this.updateAllItems();
    }

    @Override
    protected @NonNull Collection<SkyCycleCue> getElements() {
        return this.getLightShow().getSkyCycleCues();
    }

    @Override
    protected @NonNull ItemStack createEntry(@NonNull SkyCycleCue cue) {
        return ItemUtils.create(Material.CLOCK, meta -> {
            meta.displayName(LangOptions.inventory_editorcycles_entry_name.getComponent(
                lang, new Placeholders("%time%", cue.getStartTimecode())));
            meta.lore(LangOptions.inventory_editorcycles_entry_lore.getComponents(lang,
                new Placeholders("%start%", cue.getStartTimecode()),
                new Placeholders("%end%", cue.getEndTimecode()),
                new Placeholders("%cycle%", TimeUtils.formatSeconds(cue.getCycleMillis()))));
        });
    }

    @Override
    protected @NonNull SkyCycleCue createNew(int timeMillis) {
        return new SkyCycleCue(
            timeMillis,
            timeMillis + (SkyCycleCue.DEFAULT_CYCLE_MILLIS * 4),
            SkyCycleCue.DEFAULT_CYCLE_MILLIS);
    }

    @Override
    protected boolean addElement(@NonNull SkyCycleCue element) {
        return this.getLightShow().addSkyCycleCue(element);
    }

    @Override
    protected boolean removeElement(@NonNull SkyCycleCue element) {
        return this.getLightShow().removeSkyCycleCue(element);
    }

    @Override
    protected void openElementMenu(@NonNull Player player, @NonNull SkyCycleCue element) {
        new SkyCycleCueMenu(this.plugin, this.lang, this.activity, element).open(player);
    }

    @Override
    protected @NonNull Material addIconMaterial() {
        return Material.CLOCK;
    }
}
