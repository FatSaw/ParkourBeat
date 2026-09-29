package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.twod.TwoDBanners;
import ru.sortix.parkourbeat.twod.TwoDCoins;
import ru.sortix.parkourbeat.twod.TwoDGeometry;
import ru.sortix.parkourbeat.twod.TwoDLevelSettings;
import ru.sortix.parkourbeat.twod.TwoDManager;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;

/**
 * Меню 2D-уровня: спавн кубика, длина уровня, баннеры переходов и монетки.
 * На обычных 3D-уровнях кнопка в это меню просто не появляется.
 */
public class TwoDSettingsMenu extends ParkourBeatInventory implements EditLevelMenu {

    private final @NonNull EditActivity activity;
    private final @NonNull Level level;

    public TwoDSettingsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 3, lang, PbText.of(Lang.raw(lang, "auto.two_d_settings_menu.two_d_settings_menu.1")));
        this.activity = activity;
        this.level = activity.getLevel();
        this.render();
    }

    @NonNull
    private TwoDLevelSettings settings() {
        return this.level.getLevelSettings().getGameSettings().getTwoDSettings();
    }

    private void render() {
        this.clearInventory();

        TwoDLevelSettings settings = this.settings();

        // --- Спавн кубика ---
        Location cubeSpawn = settings.getCubeSpawn(this.level.getWorld());
        this.setItem(2, 2, ItemUtils.create(Material.REPEATING_COMMAND_BLOCK, meta -> {
            meta.displayName(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.1")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.2")));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.3")));
            lore.add(Component.empty());
            lore.add(PbText.item(cubeSpawn == null
                ? Lang.raw(this.lang, "auto.two_d_settings_menu.render.4")
                : String.format(java.util.Locale.ROOT, Lang.raw(this.lang, "auto.two_d_settings_menu.render.5"),
                cubeSpawn.getX(), cubeSpawn.getY(), cubeSpawn.getZ())));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.6")));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.7")));
            meta.lore(lore);
        }), this::changeCubeSpawn);

        // --- Длина уровня ---
        this.setItem(2, 3, ItemUtils.create(Material.STRING, meta -> {
            meta.displayName(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.8")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.9")));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.10")));
            lore.add(Component.empty());
            lore.add(PbText.item(String.format(java.util.Locale.ROOT,
                Lang.raw(this.lang, "auto.two_d_settings_menu.render.11"), settings.getLineLength())));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.12")));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.13")));
            meta.lore(lore);
        }), this::changeLineLength);

        // --- Баннеры ---
        this.setItem(2, 4, ItemUtils.create(Material.LIGHT_BLUE_BANNER, meta -> {
            meta.displayName(PbText.item(TwoDBanners.Type.FLY.title));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(PbText.item(TwoDBanners.Type.FLY.description));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.14")));
            lore.add(Component.empty());
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.15")));
            meta.lore(lore);
        }), event -> new TwoDBannerColorMenu(this.plugin, this.lang, this.activity, TwoDBanners.Type.FLY)
            .open(event.getPlayer()));

        this.setItem(2, 5, ItemUtils.create(Material.LIME_BANNER, meta -> {
            meta.displayName(PbText.item(TwoDBanners.Type.PARKOUR.title));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(PbText.item(TwoDBanners.Type.PARKOUR.description));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.16")));
            lore.add(Component.empty());
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.17")));
            meta.lore(lore);
        }), event -> new TwoDBannerColorMenu(this.plugin, this.lang, this.activity, TwoDBanners.Type.PARKOUR)
            .open(event.getPlayer()));

        // --- Монетки ---
        this.setItem(2, 6, ItemUtils.create(TwoDCoins.COIN_MATERIAL, meta -> {
            meta.displayName(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.18")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.19")));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.20")));
            lore.add(Component.empty());
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.21") + settings.getCoinsAmount()
                + "&8/&f" + TwoDLevelSettings.MAX_COINS));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.22")));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.23")));
            meta.lore(lore);
        }), this::coinsAction);

        // --- Шипы ---
        this.setItem(2, 9, ItemUtils.create(ru.sortix.parkourbeat.twod.TwoDItems.SPIKE_WAND_MATERIAL, meta -> {
            meta.displayName(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.24")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.25")));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.26")));
            lore.add(Component.empty());
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.27") + settings.getSpikesAmount()));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.28")));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.29")));
            meta.lore(lore);
        }), this::spikesAction);

        // --- Скорость ---
        this.setItem(2, 7, ItemUtils.create(Material.SUGAR, meta -> {
            meta.displayName(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.30")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.31")));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.32")));
            lore.add(Component.empty());
            lore.add(PbText.item(String.format(java.util.Locale.ROOT,
                Lang.raw(this.lang, "auto.two_d_settings_menu.render.33"), settings.resolveSpeed(),
                settings.hasOwnSpeed() ? Lang.raw(this.lang, "auto.two_d_settings_menu.render.34") : Lang.raw(this.lang, "auto.two_d_settings_menu.render.35"))));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.36")));
            lore.add(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.37")));
            meta.lore(lore);
        }), this::changeSpeed);

        this.setItem(3, 9, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
                meta.displayName(PbText.item(Lang.raw(this.lang, "auto.two_d_settings_menu.render.38")))),
            event -> new EditorMainMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }

    private void changeCubeSpawn(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        TwoDLevelSettings settings = this.settings();

        if (!event.isLeft()) {
            settings.setCubeSpawn(null);
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.change_cube_spawn.1")));
        } else {
            Location at = player.getLocation();
            if (at.getWorld() != this.level.getWorld()) {
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.change_cube_spawn.2")));
                return;
            }
            settings.setCubeSpawn(at);
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.change_cube_spawn.3")));
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.change_cube_spawn.4")));
        }
        this.render();
        TwoDGeometry.resolveCubeSpawn(this.level);
    }

    private void changeLineLength(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.change_line_length.1")));
            return;
        }

        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.change_line_length.2")
            + (int) TwoDLevelSettings.MIN_LINE_LENGTH + "&e-&f"
            + (int) TwoDLevelSettings.MAX_LINE_LENGTH + "&e):"));

        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.change_line_length.3")));
                return;
            }
            double value;
            try {
                value = Double.parseDouble(message.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.change_line_length.4")));
                return;
            }
            this.settings().setLineLength(value);
            this.plugin.getServer().getScheduler().runTask(this.plugin, () ->
                new TwoDSettingsMenu(this.plugin, this.lang, this.activity).open(player));
        });
    }

    private void changeSpeed(@NonNull ClickEvent event) {
        Player player = event.getPlayer();

        if (!event.isLeft()) {
            this.settings().setSpeed(0.0D);
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.change_speed.1")));
            this.render();
            return;
        }

        player.closeInventory();
        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.change_speed.2")));
            return;
        }

        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.change_speed.3")
            + (int) TwoDLevelSettings.MIN_SPEED + "&e-&f"
            + (int) TwoDLevelSettings.MAX_SPEED + "&e):"));

        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.change_speed.4")));
                return;
            }
            double value;
            try {
                value = Double.parseDouble(message.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.change_speed.5")));
                return;
            }
            this.settings().setSpeed(value);
            this.plugin.getServer().getScheduler().runTask(this.plugin, () ->
                new TwoDSettingsMenu(this.plugin, this.lang, this.activity).open(player));
        });
    }

    private void spikesAction(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        TwoDLevelSettings settings = this.settings();

        if (!event.isLeft()) {
            settings.clearSpikes();
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.spikes_action.1")));
            this.render();
            return;
        }

        player.getInventory().addItem(ru.sortix.parkourbeat.twod.TwoDItems.createSpikeWand());
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.spikes_action.2")));
        player.closeInventory();
    }

    private void coinsAction(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        TwoDLevelSettings settings = this.settings();

        if (!event.isLeft()) {
            settings.clearCoins();
            TwoDCoins.despawn(this.level);
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.coins_action.1")));
            this.render();
            return;
        }

        player.getInventory().addItem(TwoDCoins.createBuilderItem());
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.two_d_settings_menu.coins_action.2")));
        player.closeInventory();
    }
}
