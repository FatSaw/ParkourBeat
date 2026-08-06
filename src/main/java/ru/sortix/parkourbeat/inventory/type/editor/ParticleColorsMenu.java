package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.ParticleColorCue;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.util.Collection;

public class ParticleColorsMenu extends LightShowElementsMenu<ParticleColorCue> {
    public ParticleColorsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, lang, activity, LangOptions.inventory_editorpcolors_title.getComponent(lang));
        this.updateAllItems();
    }

    @Override
    protected @NonNull Collection<ParticleColorCue> getElements() {
        return this.getLightShow().getParticleColorCues();
    }

    @Override
    protected @NonNull ItemStack createEntry(@NonNull ParticleColorCue cue) {
        return ItemUtils.create(Material.REDSTONE, meta -> {
            meta.displayName(LangOptions.inventory_editorpcolors_entry_name.getComponent(
                lang, new Placeholders("%time%", cue.getStartTimecode())));
            meta.lore(LangOptions.inventory_editorpcolors_entry_lore.getComponents(lang,
                new Placeholders("%color%", cue.getHexColor()),
                new Placeholders("%start%", cue.getStartTimecode()),
                new Placeholders("%end%", cue.getEndTimecode())));
        });
    }

    @Override
    protected @NonNull ParticleColorCue createNew(int timeMillis) {
        return new ParticleColorCue(timeMillis, timeMillis + ParticleColorCue.DEFAULT_LENGTH_MILLIS,
            ParticleColorCue.DEFAULT_COLOR);
    }

    @Override
    protected boolean addElement(@NonNull ParticleColorCue element) {
        boolean added = this.getLightShow().addParticleColorCue(element);
        if (added) this.level.refreshParticleColorCues();
        return added;
    }

    @Override
    protected boolean removeElement(@NonNull ParticleColorCue element) {
        boolean removed = this.getLightShow().removeParticleColorCue(element);
        if (removed) this.level.refreshParticleColorCues();
        return removed;
    }

    @Override
    protected void openElementMenu(@NonNull Player player, @NonNull ParticleColorCue element) {
        new ParticleColorMenu(this.plugin, this.lang, this.activity, element).open(player);
    }

    @Override
    protected @NonNull Material addIconMaterial() {
        return Material.REDSTONE;
    }
}
