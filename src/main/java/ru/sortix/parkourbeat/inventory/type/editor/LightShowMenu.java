package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.LevelWeather;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.levels.BiomeApplier;
import ru.sortix.parkourbeat.levels.settings.LevelBiome;
import ru.sortix.parkourbeat.levels.settings.SkyType;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

public class LightShowMenu extends ParkourBeatInventory implements EditLevelMenu {
    private final @NonNull EditActivity activity;
    private final @NonNull Level level;

    public LightShowMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 6, lang, LangOptions.inventory_editorlightshow_title.getComponent(lang));
        this.activity = activity;
        this.level = activity.getLevel();
        this.updateItems();
    }

    @NonNull
    private LightShowSettings getLightShow() {
        return this.level.getLightShow();
    }

    public void updateItems() {
        this.clearInventory();

        SkyType baseSky = this.getLightShow().getBaseSky();
        this.setItem(
            2,
            3,
            ItemUtils.create(baseSky.getIconMaterial(), meta -> {
                meta.displayName(LangOptions.inventory_editorlightshow_basesky_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorlightshow_basesky_lore.getComponents(
                    lang, new Placeholders("%sky%", baseSky.getDisplayNameString(lang))));
            }),
            this::openBaseSkySelection);

        LevelWeather baseWeather = this.getLightShow().getBaseWeather();
        this.setItem(
            2,
            5,
            ItemUtils.create(baseWeather.getIconMaterial(), meta -> {
                meta.displayName(LangOptions.inventory_editorlightshow_baseweather_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorlightshow_baseweather_lore.getComponents(
                    lang, new Placeholders("%weather%", baseWeather.getDisplayNameString(lang))));
            }),
            this::switchBaseWeather);

        LevelBiome levelBiome = this.getLightShow().getLevelBiome();
        this.setItem(
            2,
            7,
            ItemUtils.create(levelBiome.getIconMaterial(), meta -> {
                meta.displayName(LangOptions.inventory_editorlightshow_levelbiome_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorlightshow_levelbiome_lore.getComponents(
                    lang, new Placeholders("%biome%", levelBiome.getDisplayNameString(lang))));
            }),
            this::openLevelBiomeSelection);

        this.setListItem(4, 2, Material.CLOCK,
            LangOptions.inventory_editorlightshow_cues_name,
            LangOptions.inventory_editorlightshow_cues_lore,
            this.getLightShow().getSkyCuesAmount(),
            player -> new LightShowCuesMenu(this.plugin, this.lang, this.activity).open(player));

        this.setListItem(4, 3, Material.WHITE_BANNER,
            LangOptions.inventory_editorlightshow_bosscues_name,
            LangOptions.inventory_editorlightshow_bosscues_lore,
            this.getLightShow().getBossBarCuesAmount(),
            player -> new BossBarCuesMenu(this.plugin, this.lang, this.activity).open(player));

        this.setListItem(4, 6, Material.REPEATER,
            LangOptions.inventory_editorlightshow_cycles_name,
            LangOptions.inventory_editorlightshow_cycles_lore,
            this.getLightShow().getSkyCycleCuesAmount(),
            player -> new SkyCycleCuesMenu(this.plugin, this.lang, this.activity).open(player));

        this.setListItem(4, 7, Material.GLOWSTONE_DUST,
            LangOptions.inventory_editorlightshow_flashes_name,
            LangOptions.inventory_editorlightshow_flashes_lore,
            this.getLightShow().getFlashCuesAmount(),
            player -> new FlashCuesMenu(this.plugin, this.lang, this.activity).open(player));

        this.setListItem(4, 8, Material.WATER_BUCKET,
            LangOptions.inventory_editorlightshow_weathers_name,
            LangOptions.inventory_editorlightshow_weathers_lore,
            this.getLightShow().getWeatherCuesAmount(),
            player -> new WeatherCuesMenu(this.plugin, this.lang, this.activity).open(player));

        this.setListItem(5, 5, Material.GRASS_BLOCK,
            LangOptions.inventory_editorlightshow_biomes_name,
            LangOptions.inventory_editorlightshow_biomes_lore,
            this.getLightShow().getBiomeZonesAmount(),
            player -> new BiomeZonesMenu(this.plugin, this.lang, this.activity).open(player));

        // Particle colors sit directly above the biome zones button.
        this.setListItem(4, 5, Material.REDSTONE,
            LangOptions.inventory_editorlightshow_pcolors_name,
            LangOptions.inventory_editorlightshow_pcolors_lore,
            this.getLightShow().getParticleColorCuesAmount(),
            player -> new ParticleColorsMenu(this.plugin, this.lang, this.activity).open(player));

        // Jump triggers sit right after the boss bar cues button (4,3).
        this.setListItem(4, 4, Material.RABBIT_FOOT,
            LangOptions.inventory_editorlightshow_jumps_name,
            LangOptions.inventory_editorlightshow_jumps_lore,
            this.getLightShow().getJumpZonesAmount(),
            player -> new JumpZonesMenu(this.plugin, this.lang, this.activity).open(player));

        // Win and loss completion effects together in the bottom-left corner.
        this.setItem(6, 1,
            ItemUtils.create(Material.LIME_TERRACOTTA, meta ->
                meta.displayName(LangOptions.inventory_editorlightshow_win_name.getComponent(lang))),
            event -> new CompletionParticlesMenu(this.plugin, this.lang, this.activity,
                CompletionParticlesMenu.Kind.WIN).open(event.getPlayer()));

        this.setItem(6, 2,
            ItemUtils.create(Material.RED_TERRACOTTA, meta ->
                meta.displayName(LangOptions.inventory_editorlightshow_lose_name.getComponent(lang))),
            event -> new CompletionParticlesMenu(this.plugin, this.lang, this.activity,
                CompletionParticlesMenu.Kind.LOSE).open(event.getPlayer()));

        this.setItem(
            6,
            5,
            ItemUtils.create(Material.REDSTONE_TORCH, meta ->
                meta.displayName(LangOptions.inventory_editorlightshow_back.getComponent(lang))),
            event -> new EditorMainMenu(this.plugin, lang, this.activity).open(event.getPlayer()));
    }

    private void setListItem(int row,
                             int column,
                             @NonNull Material material,
                             @NonNull LangOptions name,
                             @NonNull LangOptions lore,
                             int amount,
                             @NonNull java.util.function.Consumer<Player> opener
    ) {
        this.setItem(
            row,
            column,
            ItemUtils.create(material, meta -> {
                meta.displayName(name.getComponent(lang));
                meta.lore(lore.getComponents(lang, new Placeholders("%amount%", String.valueOf(amount))));
            }),
            event -> opener.accept(event.getPlayer()));
    }

    private void switchBaseWeather(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        LevelWeather weather = this.getLightShow().getBaseWeather().next();
        this.getLightShow().setBaseWeather(weather);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 1f);
        player.sendMessage(LangOptions.inventory_editorlightshow_weatherchanged.getComponent(
            lang, new Placeholders("%weather%", weather.getDisplayNameString(lang))));
        this.updateItems();
        this.activity.updateInventoriesOfAllEditors(LightShowMenu.class, LightShowMenu::updateItems);
    }

    private void openLevelBiomeSelection(@NonNull ClickEvent event) {
        new SelectBiomeMenu(
            this.plugin,
            this.lang,
            this.activity,
            this.getLightShow().getLevelBiome(),
            (player, biome) -> {
                this.getLightShow().setLevelBiome(biome);
                BiomeApplier.applyLevelWide(this.level, biome);
                player.sendMessage(LangOptions.inventory_editorlightshow_levelbiomeset.getComponent(
                    lang, new Placeholders("%biome%", biome.getDisplayNameString(lang))));
                new LightShowMenu(this.plugin, this.lang, this.activity).open(player);
            },
            player -> new LightShowMenu(this.plugin, this.lang, this.activity).open(player)
        ).open(event.getPlayer());
    }

    private void openBaseSkySelection(@NonNull ClickEvent event) {
        new SelectSkyMenu(
            this.plugin,
            this.lang,
            this.activity,
            this.getLightShow().getBaseSky(),
            (player, skyType) -> {
                this.getLightShow().setBaseSky(skyType);
                this.activity.applyBaseSkyToAllEditors();

                Placeholders namePlaceholder = new Placeholders("%name%", player.getName());
                Placeholders skyPlaceholder = new Placeholders("%sky%", skyType.getDisplayNameString(lang));
                for (Player editor : this.activity.getAllEditors()) {
                    editor.sendMessage(LangOptions.inventory_editorlightshow_skychanged.getComponent(
                        editor.getLocale().toLowerCase(), namePlaceholder, skyPlaceholder));
                }

                this.activity.updateInventoriesOfAllEditors(LightShowMenu.class, LightShowMenu::updateItems);
                new LightShowMenu(this.plugin, this.lang, this.activity).open(player);
            },
            player -> new LightShowMenu(this.plugin, this.lang, this.activity).open(player)
        ).open(event.getPlayer());
    }
}
