package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.LevelWeather;
import ru.sortix.parkourbeat.levels.settings.WeatherCue;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.util.Collection;

public class WeatherCuesMenu extends LightShowElementsMenu<WeatherCue> {
    public WeatherCuesMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, lang, activity, LangOptions.inventory_editorweathers_title.getComponent(lang));
        this.updateAllItems();
    }

    @Override
    protected @NonNull Collection<WeatherCue> getElements() {
        return this.getLightShow().getWeatherCues();
    }

    @Override
    protected @NonNull ItemStack createEntry(@NonNull WeatherCue cue) {
        return ItemUtils.create(cue.getWeather().getIconMaterial(), meta -> {
            meta.displayName(LangOptions.inventory_editorweathers_entry_name.getComponent(
                lang, new Placeholders("%time%", cue.getTimecode())));
            meta.lore(LangOptions.inventory_editorweathers_entry_lore.getComponents(
                lang, new Placeholders("%weather%", cue.getWeather().getDisplayNameString(lang))));
        });
    }

    @Override
    protected @NonNull WeatherCue createNew(int timeMillis) {
        return new WeatherCue(timeMillis, LevelWeather.RAIN);
    }

    @Override
    protected boolean addElement(@NonNull WeatherCue element) {
        return this.getLightShow().addWeatherCue(element);
    }

    @Override
    protected boolean removeElement(@NonNull WeatherCue element) {
        return this.getLightShow().removeWeatherCue(element);
    }

    @Override
    protected void openElementMenu(@NonNull Player player, @NonNull WeatherCue element) {
        new WeatherCueMenu(this.plugin, this.lang, this.activity, element).open(player);
    }

    @Override
    protected @NonNull Material addIconMaterial() {
        return Material.WATER_BUCKET;
    }
}
