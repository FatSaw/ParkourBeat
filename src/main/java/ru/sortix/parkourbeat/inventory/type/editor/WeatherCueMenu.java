package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.LevelWeather;
import ru.sortix.parkourbeat.levels.settings.WeatherCue;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

public class WeatherCueMenu extends LightShowElementMenu<WeatherCue> {
    public WeatherCueMenu(@NonNull ParkourBeat plugin,
                          String lang,
                          @NonNull EditActivity activity,
                          @NonNull WeatherCue cue
    ) {
        super(plugin, lang, activity, cue, LangOptions.inventory_editorweather_title.getComponent(lang));
        this.updateItems();
    }

    @Override
    protected void addSpecificItems() {
        this.setItem(
            2,
            5,
            ItemUtils.create(this.element.getWeather().getIconMaterial(), meta -> {
                meta.displayName(LangOptions.inventory_editorweather_type_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorweather_type_lore.getComponents(
                    lang, new Placeholders("%weather%", this.element.getWeather().getDisplayNameString(lang))));
            }),
            event -> {
                Player player = event.getPlayer();
                LevelWeather weather = this.element.getWeather().next();
                this.element.setWeather(weather);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 1f);
                player.sendMessage(LangOptions.inventory_editorweather_type_changed.getComponent(
                    lang, new Placeholders("%weather%", weather.getDisplayNameString(lang))));
                this.updateItems();
            });
    }

    @Override
    protected boolean removeElement() {
        return this.getLightShow().removeWeatherCue(this.element);
    }

    @Override
    protected void openListMenu(@NonNull Player player) {
        new WeatherCuesMenu(this.plugin, this.lang, this.activity).open(player);
    }

    @Override
    protected void reopen(@NonNull Player player) {
        new WeatherCueMenu(this.plugin, this.lang, this.activity, this.element).open(player);
    }
}
