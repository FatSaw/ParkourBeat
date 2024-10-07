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
import ru.sortix.parkourbeat.levels.settings.LevelSettings;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.player.music.MusicTrack;
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
        this.setItem(
            2,
            2,
            ItemUtils.create(Material.FIREWORK_STAR, (meta) -> {
            	
                meta.displayName(LangOptions.inventory_editormain_particlecolor_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_particlecolor_lore.getComponents(lang));
            }),
            this::selectParticlesColor);
        this.setItem(
            2,
            4,
            ItemUtils.modifyMeta(SelectSongMenu.NOTE_HEAD.clone(), meta -> {
            	meta.displayName(LangOptions.inventory_editormain_selectsong_name.getComponent(lang));
                MusicTrack musicTrack = activity.getLevel()
                    .getLevelSettings()
                    .getGameSettings()
                    .getMusicTrack();
                meta.lore(musicTrack == null ? LangOptions.inventory_editormain_selectsong_notracklore.getComponents(lang) : LangOptions.inventory_editormain_selectsong_lore.getComponents(lang, new Placeholders("%track%", musicTrack.getName())));
                
            }),
            this::selectLevelSong);
        this.setItem(
            2,
            6,
            ItemUtils.create(Material.ENDER_PEARL, (meta) -> {
            	meta.displayName(LangOptions.inventory_editormain_spawnpoint_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_spawnpoint_lore.getComponents(lang));
            }),
            this::setSpawnPoint);
        this.setItem(
            2,
            8,
            ItemUtils.create(Material.WRITABLE_BOOK, (meta) -> {
            	meta.displayName(LangOptions.inventory_editormain_privacy_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_privacy_lore.getComponents(lang));
            }),
            this::openPrivacySettings);
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
        this.setItem(
            4,
            7,
            ItemUtils.create(Material.BARRIER, (meta) -> {
            	meta.displayName(LangOptions.inventory_editormain_delete_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_delete_lore.getComponents(lang));
            }),
            this::deleteLevel);
        if (false) this.setItem(
            5,
            5,
            ItemUtils.create(Material.SLIME_BLOCK, (meta) -> {
            	meta.displayName(LangOptions.inventory_editormain_physic_name.getComponent(lang));
                meta.lore((activity.getLevel().getLevelSettings().getGameSettings().isCustomPhysicsEnabled() ? LangOptions.inventory_editormain_physic_lore_turnoff : LangOptions.inventory_editormain_physic_lore_turnon).getComponents(lang));
            }),
            this::switchCustomBlockPhysics);
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

        for(Component component : LangOptions.inventory_editormain_particlecolor_timetochange.getComponents(lang)) {
        	player.sendMessage(component);
        }

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

    private void selectLevelSong(@NonNull ClickEvent event) {
        Player player = event.getPlayer();

        new SelectSongMenu(this.plugin, lang, this.activity).open(player);
    }

    private void setSpawnPoint(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        LevelSettings levelSettings = this.level.getLevelSettings();
        Location playerLocation = player.getLocation();

        if (!LocationUtils.isValidSpawnPoint(playerLocation, levelSettings)) {
            player.sendMessage(LangOptions.inventory_editormain_spawnpoint_fail.getComponent(lang));
            return;
        }

        levelSettings.getWorldSettings().setSpawn(playerLocation);

        player.sendMessage(LangOptions.inventory_editormain_spawnpoint_success.getComponent(lang));
    }

    private void openPrivacySettings(@NonNull ClickEvent event) {
        new PrivacySettingsMenu(this.plugin, this.lang, this.activity).open(event.getPlayer());
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

        CommandDelete.deleteLevel(
            this.plugin, player, this.level.getLevelSettings().getGameSettings());
    }

    private void switchCustomBlockPhysics(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        GameSettings settings = this.level.getLevelSettings().getGameSettings();
        boolean inverted = !settings.isCustomPhysicsEnabled();
        settings.setCustomPhysicsEnabled(inverted);

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 1f);
        player.sendMessage((inverted ? LangOptions.inventory_editormain_physic_turn_on : LangOptions.inventory_editormain_physic_turn_off).getComponent(lang));
    }
}
