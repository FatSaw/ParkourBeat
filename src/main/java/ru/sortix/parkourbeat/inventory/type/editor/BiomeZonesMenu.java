package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.BiomeApplier;
import ru.sortix.parkourbeat.levels.settings.BiomeZone;
import ru.sortix.parkourbeat.levels.settings.LevelBiome;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.util.Collection;

public class BiomeZonesMenu extends LightShowElementsMenu<BiomeZone> {
    public BiomeZonesMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, lang, activity, LangOptions.inventory_editorbiomes_title.getComponent(lang));
        this.updateAllItems();
    }

    @Override
    protected @NonNull Collection<BiomeZone> getElements() {
        return this.getLightShow().getBiomeZones();
    }

    @Override
    protected @NonNull ItemStack createEntry(@NonNull BiomeZone zone) {
        return ItemUtils.create(zone.getBiome().getIconMaterial(), meta -> {
            meta.displayName(LangOptions.inventory_editorbiomes_entry_name.getComponent(
                lang, new Placeholders("%time%", zone.getStartTimecode())));
            meta.lore(LangOptions.inventory_editorbiomes_entry_lore.getComponents(lang,
                new Placeholders("%biome%", zone.getBiome().getDisplayNameString(lang)),
                new Placeholders("%start%", zone.getStartTimecode()),
                new Placeholders("%end%", zone.getEndTimecode())));
        });
    }

    @Override
    protected @NonNull BiomeZone createNew(int timeMillis) {
        return new BiomeZone(timeMillis, timeMillis + BiomeZone.DEFAULT_LENGTH_MILLIS,
            LevelBiome.SNOWY, true, ru.sortix.parkourbeat.levels.settings.ZoneSkyTime.DEFAULT);
    }

    @Override
    protected boolean addElement(@NonNull BiomeZone element) {
        if (!this.getLightShow().addBiomeZone(element)) return false;
        BiomeApplier.apply(this.level, element);
        return true;
    }

    @Override
    protected boolean removeElement(@NonNull BiomeZone element) {
        BiomeApplier.reset(this.level, element);
        return this.getLightShow().removeBiomeZone(element);
    }

    @Override
    protected void openElementMenu(@NonNull Player player, @NonNull BiomeZone element) {
        new BiomeZoneMenu(this.plugin, this.lang, this.activity, element).open(player);
    }

    @Override
    protected @NonNull Material addIconMaterial() {
        return Material.GRASS_BLOCK;
    }
}
