package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.Lang;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

import java.util.ArrayList;
import java.util.List;

import ru.sortix.parkourbeat.utils.text.PbText;
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

        // На 2D-уровне прыжки не судятся и путь из частиц не рисуется.
        // Базовое небо, погода и биом остаются: они относятся ко всему уровню.
        if (ru.sortix.parkourbeat.twod.TwoDManager.isTwoD(this.level)) {
            this.setItem(4, 4, null, null); // триггеры прыжка
            this.setItem(4, 5, null, null); // цвета частиц
        }

        // На «Копателе» ровно та же история: пути нет, прыжков нет. Небо, вспышки,
        // ламповые стены и боссбар остаются - они к пути не привязаны и работают там
        // даже лучше, чем на обычном уровне.
        if (ru.sortix.parkourbeat.digger.DiggerManager.isDigger(this.level)) {
            this.setItem(4, 4, null, null); // триггеры прыжка
            this.setItem(4, 5, null, null); // цвета частиц
        }

        // Зоны цветов частиц перекрашивают путь по ходу уровня. На дуэльной карте цвет
        // пути означает сторону, а на 360-уровне пути нет вовсе - в обоих случаях
        // настройка бессмысленна.
        if (ru.sortix.parkourbeat.duel.DuelManager.isDuel(this.level)
            || ru.sortix.parkourbeat.threesixty.ThreeSixtyManager.isThreeSixty(this.level)) {
            this.setItem(4, 5, null, null); // зоны цветов частиц
        }

        // На карте «Копателя» появляется своя кнопка: кирка в руке игрока может
        // меняться по ходу трека. Для остальных режимов кьюс бессмысленен - там
        // в руке вообще ничего нет.
        if (ru.sortix.parkourbeat.digger.DiggerManager.isDigger(this.level)) {
            this.setItem(5, 3, ru.sortix.parkourbeat.item.ItemUtils.create(
                org.bukkit.Material.NETHERITE_PICKAXE, meta -> {
                    meta.displayName(ru.sortix.parkourbeat.utils.text.PbText.item("&5Кирки по таймкоду"));
                    java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                    lore.add(ru.sortix.parkourbeat.utils.text.PbText.item(
                        "&7Кирка в руке на отрезке трека."));
                    lore.add(ru.sortix.parkourbeat.utils.text.PbText.item(
                        "&7Всего кьюсов: &f" + this.getLightShow().getPickaxeCues().size()));
                    meta.lore(lore);
                }),
                event -> new PickaxeCuesMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
        }

        this.setWonderEffectsItem();
        this.setLampShowItem();

        this.setPortalsItem();
        this.setAutoDoorsItem();


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

    /**
     * Чудоэффекты стоят ровно в центре меню (3,5): третий ряд был пуст, а центральный слот
     * симметричен сам по себе и попадает точно между погодой сверху и цветами частиц снизу.
     * Оформление — как у "Порталов" и "Автодверей", чтобы кнопка не выбивалась из ряда.
     */
    private void setWonderEffectsItem() {
        int amount = this.getLightShow().getWonderEffectsAmount();
        boolean ready = ru.sortix.parkourbeat.levels.wonder.WonderBridge.isAvailable();

        String[] loreLines = {
            Lang.raw(this.lang, "auto.light_show_menu.set_wonder_effects_item.1"),
            Lang.raw(this.lang, "auto.light_show_menu.set_wonder_effects_item.2"),
            "",
            Lang.raw(this.lang, "auto.light_show_menu.set_wonder_effects_item.3") + amount,
            ready ? "" : Lang.raw(this.lang, "auto.light_show_menu.set_wonder_effects_item.4")
        };

        this.setItem(3, 5, ItemUtils.create(Material.NETHER_STAR, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.light_show_menu.set_wonder_effects_item.5"))
                .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                if (line.isEmpty()) {
                    lore.add(Component.empty());
                } else {
                    lore.add(PbText.of(line).decoration(TextDecoration.ITALIC, false));
                }
            }
            meta.lore(lore);
        }), event -> new WonderEffectsMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }

    /** Ламповое шоу стоит рядом с чудоэффектами, в том же ряду. */
    private void setLampShowItem() {
        int amount = this.getLightShow().getLampWallsAmount();

        this.setItem(3, 3, ItemUtils.create(Material.REDSTONE_LAMP, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.light_show_menu.set_lamp_show_item.1"))
                .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(PbText.of(Lang.raw(this.lang, "auto.light_show_menu.set_lamp_show_item.2"))
                .decoration(TextDecoration.ITALIC, false));
            lore.add(PbText.of(Lang.raw(this.lang, "auto.light_show_menu.set_lamp_show_item.3"))
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(PbText.of(Lang.raw(this.lang, "auto.light_show_menu.set_lamp_show_item.4") + amount)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        }), event -> new LampWallsMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }

    private void setPortalsItem() {
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

        String[] loreLines = {
            Lang.raw(this.lang, "auto.light_show_menu.set_portals_item.1"),
            Lang.raw(this.lang, "auto.light_show_menu.set_portals_item.2"),
            "",
            Lang.raw(this.lang, "auto.light_show_menu.set_portals_item.3") + this.getLightShow().getPortalsAmount()
        };

        this.setItem(5, 4, ItemUtils.create(Material.PURPLE_STAINED_GLASS, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.light_show_menu.set_portals_item.4"))
                .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(line.isEmpty()
                    ? Component.empty()
                    : PbText.of(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }), event -> new PortalsMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }

    private void setAutoDoorsItem() {
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

        String[] loreLines = {
            Lang.raw(this.lang, "auto.light_show_menu.set_auto_doors_item.1"),
            Lang.raw(this.lang, "auto.light_show_menu.set_auto_doors_item.2"),
            "",
            Lang.raw(this.lang, "auto.light_show_menu.set_auto_doors_item.3") + this.getLightShow().getAutoDoorsAmount()
        };

        this.setItem(5, 6, ItemUtils.create(Material.OAK_DOOR, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.light_show_menu.set_auto_doors_item.4"))
                .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(line.isEmpty()
                    ? Component.empty()
                    : PbText.of(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }), event -> new AutoDoorsMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
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
                // Подсказка про путь из частиц - только там, где этот путь есть.
                // На карте «Копателя» её текст просто врёт: трассы из точек у режима нет,
                // а биом теперь красится по самой дальней руде.
                if (!ru.sortix.parkourbeat.digger.DiggerManager.isDigger(this.level)) {
                    this.sendBiomeRangeHint(player);
                }
                new LightShowMenu(this.plugin, this.lang, this.activity).open(player);
            },
            player -> new LightShowMenu(this.plugin, this.lang, this.activity).open(player)
        ).open(event.getPlayer());
    }

    /**
     * Биом на весь уровень красится по пути из частиц, а не по всей редактируемой зоне:
     * зона тянется на десятки тысяч блоков, и красить её целиком нельзя. Пока путь
     * не доделан, биом обрывается там же, где обрывается путь - и со стороны это выглядит
     * так, будто "на весь уровень" не работает. Поэтому говорим об этом прямо.
     */
    private void sendBiomeRangeHint(@NonNull Player player) {
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
        player.sendMessage(PbText.of(
            Lang.raw(PlayerLang.of(player), "auto.light_show_menu.send_biome_range_hint.1")));
        player.sendMessage(PbText.of(
            Lang.raw(PlayerLang.of(player), "auto.light_show_menu.send_biome_range_hint.2")));
        player.sendMessage(PbText.of(
            Lang.raw(PlayerLang.of(player), "auto.light_show_menu.send_biome_range_hint.3")));
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
                        PlayerLang.of(editor), namePlaceholder, skyPlaceholder));
                }

                this.activity.updateInventoriesOfAllEditors(LightShowMenu.class, LightShowMenu::updateItems);
                new LightShowMenu(this.plugin, this.lang, this.activity).open(player);
            },
            player -> new LightShowMenu(this.plugin, this.lang, this.activity).open(player)
        ).open(event.getPlayer());
    }
}
