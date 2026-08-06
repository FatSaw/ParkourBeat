package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.BiomeApplier;
import org.bukkit.Material;
import org.bukkit.Sound;
import ru.sortix.parkourbeat.levels.settings.BiomeZone;
import ru.sortix.parkourbeat.levels.settings.ZoneSkyTime;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

public class BiomeZoneMenu extends LightShowElementMenu<BiomeZone> {
    public BiomeZoneMenu(@NonNull ParkourBeat plugin,
                         String lang,
                         @NonNull EditActivity activity,
                         @NonNull BiomeZone zone
    ) {
        super(plugin, lang, activity, zone, LangOptions.inventory_editorbiome_title.getComponent(lang));
        this.updateItems();
    }

    @Override
    protected void addSpecificItems() {
        this.setItem(
            2,
            5,
            ItemUtils.create(this.element.getBiome().getIconMaterial(), meta -> {
                meta.displayName(LangOptions.inventory_editorbiome_type_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorbiome_type_lore.getComponents(
                    lang, new Placeholders("%biome%", this.element.getBiome().getDisplayNameString(lang))));
            }),
            event -> new SelectBiomeMenu(
                this.plugin,
                this.lang,
                this.activity,
                this.element.getBiome(),
                (player, biome) -> {
                    BiomeApplier.reset(this.level, this.element);
                    this.element.setBiome(biome);
                    BiomeApplier.apply(this.level, this.element);
                    player.sendMessage(LangOptions.inventory_editorbiome_applied.getComponent(lang));
                    this.reopen(player);
                },
                this::reopen
            ).open(event.getPlayer()));

        this.addRainItem();
        this.addSkyTimeItem();
    }

    private void addRainItem() {
        boolean forceRain = this.element.isForceRain();
        this.setItem(
            2,
            3,
            ItemUtils.create(forceRain ? Material.WATER_BUCKET : Material.BUCKET, meta -> {
                meta.displayName(LangOptions.inventory_editorbiome_rain_name.getComponent(lang));
                meta.lore((forceRain
                    ? LangOptions.inventory_editorbiome_rain_lore_on
                    : LangOptions.inventory_editorbiome_rain_lore_off).getComponents(lang));
            }),
            event -> {
                Player player = event.getPlayer();
                this.element.setForceRain(!this.element.isForceRain());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 1f);
                this.updateItems();
            });
    }

    private void addSkyTimeItem() {
        ZoneSkyTime skyTime = this.element.getSkyTime();
        this.setItem(
            2,
            7,
            ItemUtils.create(skyTime.getIconMaterial(), meta -> {
                meta.displayName(LangOptions.inventory_editorbiome_daytime_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorbiome_daytime_lore.getComponents(
                    lang, new Placeholders("%time%", skyTime.getDisplayNameString(lang))));
            }),
            event -> {
                Player player = event.getPlayer();
                this.element.setSkyTime(this.element.getSkyTime().next());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 1f);
                this.updateItems();
            });
    }

    @Override
    protected void onTimecodeAboutToChange() {
        BiomeApplier.reset(this.level, this.element);
    }

    @Override
    protected void onTimecodeChanged() {
        BiomeApplier.apply(this.level, this.element);
    }

    @Override
    protected boolean removeElement() {
        BiomeApplier.reset(this.level, this.element);
        return this.getLightShow().removeBiomeZone(this.element);
    }

    @Override
    protected void openListMenu(@NonNull Player player) {
        new BiomeZonesMenu(this.plugin, this.lang, this.activity).open(player);
    }

    @Override
    protected void reopen(@NonNull Player player) {
        new BiomeZoneMenu(this.plugin, this.lang, this.activity, this.element).open(player);
    }
}
