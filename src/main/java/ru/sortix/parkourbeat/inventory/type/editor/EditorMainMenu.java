package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.commands.CommandDelete;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.item.editor.type.EditTrackPointsItem;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.levels.settings.LevelBossBarColor;
import ru.sortix.parkourbeat.levels.settings.LevelSettings;
import ru.sortix.parkourbeat.levels.settings.WorldSettings;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.player.music.MusicTrack;
import ru.sortix.parkourbeat.utils.ChatColorPalette;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.world.LocationUtils;
import ru.sortix.parkourbeat.world.TeleportUtils;

public class EditorMainMenu extends ParkourBeatInventory implements EditLevelMenu {
    private final EditActivity activity;
    private final Level level;

    public EditorMainMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 5, lang, LangOptions.inventory_editormain_title.getComponent(lang));
        this.activity = activity;
        this.level = activity.getLevel();

        boolean isOwner = activity.isOwner();

        this.setItem(
            2,
            1,
            ItemUtils.create(Material.FIREWORK_STAR, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_particlecolor_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_particlecolor_lore.getComponents(lang));
            }),
            this::selectParticlesColor);
        this.setItem(
            2,
            2,
            ItemUtils.create(Material.FIRE_CHARGE, (meta) -> {
                meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize("&6Цвет прыжков")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer L =
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand();
                lore.add(net.kyori.adventure.text.Component.empty());
                lore.add(L.deserialize("&7Цвет круга частиц триггера прыжка")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                lore.add(L.deserialize("&7Применяется к новым точкам трека")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                lore.add(net.kyori.adventure.text.Component.empty());
                Color jc = this.activity.getCurrentJumpColor();
                lore.add(L.deserialize(jc == null
                        ? "&8Текущий: &7инверсия цвета пути"
                        : "&8Текущий: &f#" + String.format("%06X", jc.asRGB()))
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                lore.add(L.deserialize("&8ЛКМ - ввести HEX")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                lore.add(L.deserialize("&8ПКМ - сбросить (инверсия)")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                meta.lore(lore);
            }),
            this::selectJumpColor);
        this.setItem(
            2,
            3,
            ItemUtils.modifyMeta(SelectSongMenu.NOTE_HEAD.clone(), meta -> {
                meta.displayName(LangOptions.inventory_editormain_selectsong_name.getComponent(lang));
                MusicTrack musicTrack = activity.getLevel()
                    .getLevelSettings()
                    .getGameSettings()
                    .getMusicTrack();

                if (musicTrack == null) {
                    meta.lore(LangOptions.inventory_editormain_selectsong_notracklore.getComponents(lang));
                } else {
                    meta.lore(LangOptions.inventory_editormain_selectsong_lore.getComponents(lang,
                        new Placeholders("%track%", musicTrack.getName())));
                }
            }),
            this::selectLevelSong);
        this.setItem(
            2,
            5,
            ItemUtils.create(Material.ENDER_PEARL, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_spawnpoint_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_spawnpoint_lore.getComponents(lang));
            }),
            this::setSpawnPoint);
        this.setItem(
            2,
            7,
            ItemUtils.create(Material.BEACON, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_lightshow_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_lightshow_lore.getComponents(lang));
            }),
            this::openLightShowSettings);
        this.updateBossBarColorItem();
        this.updateBorderPushItem();

        if (isOwner) {
            this.setItem(
                3,
                3,
                ItemUtils.create(Material.WRITABLE_BOOK, (meta) -> {
                    meta.displayName(LangOptions.inventory_editormain_privacy_name.getComponent(lang));
                    meta.lore(LangOptions.inventory_editormain_privacy_lore.getComponents(lang));
                }),
                this::openPrivacySettings);
            this.setItem(
                3,
                7,
                ItemUtils.create(Material.PLAYER_HEAD, (meta) -> {
                    meta.displayName(LangOptions.inventory_editormain_coeditors_name.getComponent(lang));
                    meta.lore(LangOptions.inventory_editormain_coeditors_lore.getComponents(lang));
                }),
                this::openCoEditorsSettings);
        }

        this.setItem(
            3,
            5,
            ItemUtils.create(Material.REDSTONE_BLOCK, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_glow_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_glow_lore.getComponents(lang));
            }),
            event -> new GlowingBarriersMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
        this.setItem(
            4,
            3,
            ItemUtils.create(Material.NETHER_STAR, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_resetpoints_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_resetpoints_lore.getComponents(lang));
            }),
            this::resetAllTrackPoints);
        this.setItem(
            4,
            5,
            ItemUtils.create(Material.REDSTONE_TORCH, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_exit_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_exit_lore.getComponents(lang));
            }),
            this::leaveEditor);

        if (isOwner) {
            this.setItem(
                4,
                7,
                ItemUtils.create(Material.BARRIER, (meta) -> {
                    meta.displayName(LangOptions.inventory_editormain_delete_name.getComponent(lang));
                    meta.lore(LangOptions.inventory_editormain_delete_lore.getComponents(lang));
                }),
                this::deleteLevel);
        }

        boolean previewEnabled = activity.isPreviewEnabled();
        this.setItem(
            5,
            5,
            ItemUtils.create(previewEnabled ? Material.ENDER_EYE : Material.ENDER_PEARL, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_preview_name.getComponent(lang));
                meta.lore((previewEnabled
                    ? LangOptions.inventory_editormain_preview_lore_on
                    : LangOptions.inventory_editormain_preview_lore_off).getComponents(lang));
            }),
            event -> {
                Player player = event.getPlayer();
                this.activity.setPreviewEnabled(!this.activity.isPreviewEnabled());
                player.sendMessage((this.activity.isPreviewEnabled()
                    ? LangOptions.inventory_editormain_preview_turnedon
                    : LangOptions.inventory_editormain_preview_turnedoff).getComponent(lang));
                new EditorMainMenu(this.plugin, lang, this.activity).open(player);
            });

        boolean infiniteRunEnabled = activity.isInfiniteTesting();
        this.setItem(
            5,
            4,
            ItemUtils.create(infiniteRunEnabled ? Material.GOLDEN_BOOTS : Material.LEATHER_BOOTS, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_infiniterun_name.getComponent(lang));
                meta.lore((infiniteRunEnabled
                    ? LangOptions.inventory_editormain_infiniterun_lore_on
                    : LangOptions.inventory_editormain_infiniterun_lore_off).getComponents(lang));
            }),
            event -> {
                Player player = event.getPlayer();
                this.activity.setInfiniteTesting(!this.activity.isInfiniteTesting());
                player.sendMessage((this.activity.isInfiniteTesting()
                    ? LangOptions.inventory_editormain_infiniterun_turnedon
                    : LangOptions.inventory_editormain_infiniterun_turnedoff).getComponent(lang));
                new EditorMainMenu(this.plugin, lang, this.activity).open(player);
            });

        this.setItem(
            5,
            3,
            ItemUtils.create(Material.STRING, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_particledistance_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_particledistance_lore.getComponents(lang,
                    new Placeholders("%distance%", String.format(java.util.Locale.ROOT, "%.1f",
                        this.level.getLevelSettings().getWorldSettings().getParticleViewDistance()))));
            }),
            this::changeParticleDistance);
    }

    private void leaveEditor(@NonNull ClickEvent event) {
        Player player = event.getPlayer();

        TeleportUtils.teleportAsync(this.plugin, player, Settings.getLobbySpawn()).thenAccept(success -> {
            if (success) return;
            player.sendMessage(LangOptions.inventory_editormain_exit_canceled.getComponent(lang));
        });
    }

    private void selectParticlesColor(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editormain_particlecolor_unavilable.getComponent(lang));
            return;
        }

        ChatColorPalette.sendPalette(player);

        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(LangOptions.inventory_editormain_particlecolor_timeout.getComponent(lang));
                return;
            }

            String hex = message.startsWith("#") ? message.substring(1) : message;
            Color color;
            try {
                color = Color.fromRGB(Integer.valueOf(hex, 16));
            } catch (IllegalArgumentException e) {
                player.sendMessage(LangOptions.inventory_editormain_particlecolor_invalidhex.getComponent(lang));
                return;
            }
            this.activity.setCurrentColor(color);
            for(Component component : LangOptions.inventory_editormain_particlecolor_selectedcolor.getComponents(lang, new Placeholders("%color%", hex))) {
                player.sendMessage(component);
            }
        });
    }

    private void selectJumpColor(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        if (!event.isLeft()) {
            this.activity.setCurrentJumpColor(null);
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize("&aЦвет прыжков сброшен (инверсия цвета пути)"));
            return;
        }

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editormain_particlecolor_unavilable.getComponent(lang));
            return;
        }

        ChatColorPalette.sendPalette(player);

        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(LangOptions.inventory_editormain_particlecolor_timeout.getComponent(lang));
                return;
            }
            String hex = message.startsWith("#") ? message.substring(1) : message;
            Color color;
            try {
                color = Color.fromRGB(Integer.valueOf(hex, 16));
            } catch (IllegalArgumentException e) {
                player.sendMessage(LangOptions.inventory_editormain_particlecolor_invalidhex.getComponent(lang));
                return;
            }
            this.activity.setCurrentJumpColor(color);
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize("&aЦвет прыжков установлен: &f#" + hex.toUpperCase()));
        });
    }

    private void selectLevelSong(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        new SelectSongMenu(this.plugin, lang, this.activity).open(player);
    }

    private void setSpawnPoint(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        LevelSettings levelSettings = this.level.getLevelSettings();
        Location playerLocation = player.getLocation().clone();

        if (!LocationUtils.isValidSpawnPoint(playerLocation, levelSettings)) {
            player.sendMessage(LangOptions.inventory_editormain_spawnpoint_fail.getComponent(lang));
            return;
        }

        playerLocation.setPitch(0f);
        switch (levelSettings.getDirectionChecker().direction()) {
            case POSITIVE_X: playerLocation.setYaw(-90f); break;
            case NEGATIVE_X: playerLocation.setYaw(90f); break;
            case POSITIVE_Z: playerLocation.setYaw(0f); break;
            case NEGATIVE_Z: playerLocation.setYaw(180f); break;
        }

        levelSettings.getWorldSettings().setSpawn(playerLocation);
        player.teleport(playerLocation);

        player.sendMessage(LangOptions.inventory_editormain_spawnpoint_success.getComponent(lang));
    }

    private void openLightShowSettings(@NonNull ClickEvent event) {
        new LightShowMenu(this.plugin, this.lang, this.activity).open(event.getPlayer());
    }

    private void updateBorderPushItem() {
        double strength = this.getGameSettings().getBorderPushStrength();
        this.setItem(
            3,
            1,
            ItemUtils.create(strength > 0 ? org.bukkit.Material.SLIME_BALL : org.bukkit.Material.GRAY_DYE, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_borderpush_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_borderpush_lore.getComponents(
                    lang, new Placeholders("%value%", String.format("%.2f", strength))));
            }),
            this::requestBorderPush);
    }

    private void requestBorderPush(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        PlayersInputManager manager =
            this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editormain_borderpush_unavailable.getComponent(lang));
            return;
        }

        player.sendMessage(LangOptions.inventory_editormain_borderpush_request.getComponent(lang));
        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(LangOptions.inventory_editormain_borderpush_timeout.getComponent(lang));
                return;
            }
            double value;
            try {
                value = Double.parseDouble(message.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                player.sendMessage(LangOptions.inventory_editormain_borderpush_invalid.getComponent(lang));
                return;
            }
            if (value < 0) value = 0;
            if (value > 5) value = 5;
            this.getGameSettings().setBorderPushStrength(value);
            new EditorMainMenu(this.plugin, this.lang, this.activity).open(player);
        });
    }

    private void updateBossBarColorItem() {
        LevelBossBarColor barColor = this.getGameSettings().getBossBarColor();
        boolean hidden = this.getGameSettings().isHideBossBar();
        this.setItem(
            2,
            9,
            ItemUtils.create(hidden ? org.bukkit.Material.BARRIER : barColor.getIconMaterial(), (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_bossbar_name.getComponent(lang));
                meta.lore(concatLore(
                    LangOptions.inventory_editormain_bossbar_lore.getComponents(
                        lang, new Placeholders("%color%", barColor.getDisplayNameString(lang))),
                    (hidden
                        ? LangOptions.inventory_editormain_bossbar_hidden
                        : LangOptions.inventory_editormain_bossbar_shown).getComponents(lang)));
            }),
            event -> {
                if (event.isLeft()) {
                    this.openBossBarColorSelection(event);
                } else {
                    this.getGameSettings().setHideBossBar(!this.getGameSettings().isHideBossBar());
                    Player player = event.getPlayer();
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                    this.updateBossBarColorItem();
                }
            });
    }

    private static java.util.List<net.kyori.adventure.text.Component> concatLore(
        java.util.List<net.kyori.adventure.text.Component> a,
        java.util.List<net.kyori.adventure.text.Component> b) {
        java.util.List<net.kyori.adventure.text.Component> result = new java.util.ArrayList<>(a);
        result.add(net.kyori.adventure.text.Component.empty());
        result.addAll(b);
        return result;
    }

    @NonNull
    private GameSettings getGameSettings() {
        return this.level.getLevelSettings().getGameSettings();
    }

    private void openBossBarColorSelection(@NonNull ClickEvent event) {
        new SelectBossBarColorMenu(
            this.plugin,
            this.lang,
            this.activity,
            this.getGameSettings().getBossBarColor(),
            (player, barColor) -> {
                this.getGameSettings().setBossBarColor(barColor);

                Placeholders namePlaceholder = new Placeholders("%name%", player.getName());
                Placeholders colorPlaceholder = new Placeholders("%color%", barColor.getDisplayNameString(lang));
                for (Player editor : this.activity.getAllEditors()) {
                    editor.sendMessage(LangOptions.inventory_editormain_bossbarchanged.getComponent(
                        editor.getLocale().toLowerCase(), namePlaceholder, colorPlaceholder));
                }

                new EditorMainMenu(this.plugin, this.lang, this.activity).open(player);
            },
            player -> new EditorMainMenu(this.plugin, this.lang, this.activity).open(player)
        ).open(event.getPlayer());
    }

    private void openPrivacySettings(@NonNull ClickEvent event) {
        new PrivacySettingsMenu(this.plugin, this.lang, this.activity).open(event.getPlayer());
    }

    private void openCoEditorsSettings(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        if (!this.level.getLevelSettings().getGameSettings().isOwner(player.getUniqueId())) {
            player.sendMessage(LangOptions.inventory_editorcoeditors_notowner.getComponent(lang));
            player.closeInventory();
            return;
        }
        new CoEditorsMenu(this.plugin, this.lang, this.activity).open(player);
    }

    private void resetAllTrackPoints(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        EditTrackPointsItem.clearAllPoints(this.level);
        player.sendMessage(LangOptions.inventory_editormain_resetpoints_reset.getComponent(lang));
    }

    private void deleteLevel(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        GameSettings settings = this.level.getLevelSettings().getGameSettings();
        if (!settings.isOwner(player, true, true)) {
            player.sendMessage(LangOptions.level_editor_delete_notowner.getComponent(lang));
            return;
        }

        CommandDelete.deleteLevel(this.plugin, player, settings);
    }

    private void changeParticleDistance(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editormain_particledistance_unavilable.getComponent(lang));
            return;
        }

        player.sendMessage(LangOptions.inventory_editormain_particledistance_request.getComponent(lang));
        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(LangOptions.inventory_editormain_particledistance_timeout.getComponent(lang));
                return;
            }

            double distance;
            try {
                distance = Double.parseDouble(message.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                player.sendMessage(LangOptions.inventory_editormain_particledistance_invalid.getComponent(lang));
                return;
            }
            if (distance < WorldSettings.MIN_VIEW_DISTANCE || distance > WorldSettings.MAX_VIEW_DISTANCE) {
                player.sendMessage(LangOptions.inventory_editormain_particledistance_invalid.getComponent(lang));
                return;
            }

            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                this.level.getLevelSettings().getWorldSettings().setParticleViewDistance(distance);
                this.level.applyViewDistances();
                player.sendMessage(LangOptions.inventory_editormain_particledistance_success.getComponent(lang,
                    new Placeholders("%distance%", String.format(java.util.Locale.ROOT, "%.1f",
                        this.level.getLevelSettings().getWorldSettings().getParticleViewDistance()))));
                new EditorMainMenu(this.plugin, this.lang, this.activity).open(player);
            });
        });
    }
}
