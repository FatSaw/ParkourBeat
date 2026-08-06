package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.FlashCue;
import ru.sortix.parkourbeat.levels.settings.FlashSpeed;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.util.Collection;

public class FlashCuesMenu extends LightShowElementsMenu<FlashCue> {
    public FlashCuesMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, lang, activity, LangOptions.inventory_editorflashes_title.getComponent(lang));
        this.updateAllItems();
    }

    @Override
    protected @NonNull Collection<FlashCue> getElements() {
        return this.getLightShow().getFlashCues();
    }

    @Override
    protected @NonNull ItemStack createEntry(@NonNull FlashCue cue) {
        return ItemUtils.create(Material.GLOWSTONE_DUST, meta -> {
            meta.displayName(LangOptions.inventory_editorflashes_entry_name.getComponent(
                lang, new Placeholders("%time%", cue.getStartTimecode())));
            meta.lore(LangOptions.inventory_editorflashes_entry_lore.getComponents(lang,
                new Placeholders("%start%", cue.getStartTimecode()),
                new Placeholders("%end%", cue.getEndTimecode()),
                new Placeholders("%speed%", cue.getSpeed().getDisplayNameString(lang))));
        });
    }

    @Override
    protected @NonNull FlashCue createNew(int timeMillis) {
        return new FlashCue(timeMillis, timeMillis + FlashCue.DEFAULT_DURATION_MILLIS, FlashSpeed.DEFAULT);
    }

    @Override
    protected boolean addElement(@NonNull FlashCue element) {
        return this.getLightShow().addFlashCue(element);
    }

    @Override
    protected boolean removeElement(@NonNull FlashCue element) {
        return this.getLightShow().removeFlashCue(element);
    }

    @Override
    protected void openElementMenu(@NonNull Player player, @NonNull FlashCue element) {
        new FlashCueMenu(this.plugin, this.lang, this.activity, element).open(player);
    }

    @Override
    protected @NonNull Material addIconMaterial() {
        return Material.GLOWSTONE_DUST;
    }
}
