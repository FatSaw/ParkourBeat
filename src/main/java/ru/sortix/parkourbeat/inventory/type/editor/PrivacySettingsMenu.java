package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.ModerationStatus;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.world.TeleportUtils;

import java.util.List;

public class PrivacySettingsMenu extends ParkourBeatInventory implements EditLevelMenu {
    private final EditActivity activity;
    private final Level level;

    protected PrivacySettingsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 5, lang, LangOptions.inventory_editorprivacy_title.getComponent(lang));
        this.activity = activity;
        this.level = activity.getLevel();
        this.updateItems();
    }

    private void updateItems() {
        this.clearInventory();

        this.updatePublicVisibilityItem();

        this.updateLevelNameItem();

        this.updateModerationStatusItem();

        this.setItem(
            4,
            5,
            ItemUtils.create(Material.REDSTONE_TORCH, (meta) -> {
                meta.displayName(LangOptions.inventory_editorprivacy_back.getComponent(lang));
            }),
            this::back);
    }

    private void updatePublicVisibilityItem() {
        boolean publicVisible = this.level.getLevelSettings().getGameSettings().isPublicVisible();
        
        List<Component> lore = (publicVisible ? LangOptions.inventory_editorprivacy_visibility_lore_public : LangOptions.inventory_editorprivacy_visibility_lore_private).getComponents(lang);
        this.setItem(
            2,
            3,
            ItemUtils.create(publicVisible ? Material.REDSTONE_LAMP : Material.BARRIER, (meta) -> {
            	meta.displayName(LangOptions.inventory_editorprivacy_visibility_name.getComponent(lang));
                meta.lore(lore);
            }),
            this::switchPublicVisibility);
    }

    private void switchPublicVisibility(@NonNull ClickEvent event) {
        GameSettings settings = this.level.getLevelSettings().getGameSettings();

        if (settings.getModerationStatus() == ModerationStatus.MODERATED) {
        	event.getPlayer().sendMessage((settings.isPublicVisible() ? LangOptions.inventory_editorprivacy_visibility_cantchange_moderated : LangOptions.inventory_editorprivacy_visibility_cantchange_blocked).getComponent(lang));
            event.getPlayer().closeInventory();
            return;
        }

        boolean publicVisibleNow = !settings.isPublicVisible();
        settings.setPublicVisible(publicVisibleNow);

        this.activity.updateInventoriesOfAllEditors(PrivacySettingsMenu.class,
            PrivacySettingsMenu::updatePublicVisibilityItem);
        Placeholders namePlaceholder = new Placeholders("%name%", event.getPlayer().getName());
        for (Player editor : this.activity.getAllEditors()) {
            editor.sendMessage((publicVisibleNow ? LangOptions.inventory_editorprivacy_visibility_changedto_public : LangOptions.inventory_editorprivacy_visibility_changedto_private).getComponent(editor, namePlaceholder));
        }
    }

    private void updateLevelNameItem() {
        this.setItem(
            2,
            5,
            ItemUtils.create(Material.WRITABLE_BOOK, (meta) -> {
            	meta.displayName(LangOptions.inventory_editorprivacy_rename_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorprivacy_rename_lore.getComponents(lang, new Placeholders("%level%", ((TextComponent)this.level.getDisplayName()).content())));
            }),
            this::renameLevel);
    }

    private void renameLevel(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editorprivacy_rename_unavilable.getComponent(lang));
            return;
        }

        player.sendMessage(LangOptions.inventory_editorprivacy_rename_timetochange.getComponent(lang));
        manager.requestChatInput(player, 20 * 30).thenAccept(newNameLegacy -> {
            if (newNameLegacy == null) {
                player.sendMessage(LangOptions.inventory_editorprivacy_rename_timeout.getComponent(lang));
                return;
            }

            Component newName = LegacyComponentSerializer.legacyAmpersand().deserialize(newNameLegacy);

            int nameLength = PlainComponentSerializer.plain().serialize(newName).length();

            if (nameLength < 3) {
                player.sendMessage(LangOptions.inventory_editorprivacy_rename_length_min.getComponent(lang));
                return;
            }

            if (nameLength > 30) {
                player.sendMessage(LangOptions.inventory_editorprivacy_rename_length_max.getComponent(lang));
                return;
            }

            Component oldName;
            oldName = this.level.getDisplayName();
            this.level.getLevelSettings().getGameSettings().setDisplayName(newName);
            newName = this.level.getDisplayName();
            player.sendMessage(LangOptions.inventory_editorprivacy_rename_changed.getComponent(lang, new Placeholders("%before%", ((TextComponent)oldName).content()), new Placeholders("%after%", ((TextComponent)newName).content())));

            this.activity.updateInventoriesOfAllEditors(PrivacySettingsMenu.class,
                PrivacySettingsMenu::updateLevelNameItem);
        });
    }

    private void updateModerationStatusItem() {
        List<Component> lore;
        Material material = switch (this.level.getLevelSettings().getGameSettings().getModerationStatus()) {
            case NOT_MODERATED -> {
            	lore = LangOptions.inventory_editorprivacy_moderation_lore_notmoderated.getComponents(lang);
                yield Material.PAPER;
            }
            case ON_MODERATION -> {
            	lore = LangOptions.inventory_editorprivacy_moderation_lore_onmoderation.getComponents(lang);
                yield Material.PLAYER_HEAD;
            }
            case MODERATED -> {
            	lore = LangOptions.inventory_editorprivacy_moderation_lore_moderated.getComponents(lang);
                yield Material.BOOKSHELF;
            }
        };
        this.setItem(
            2,
            7,
            ItemUtils.create(material, (meta) -> {
            	meta.displayName(LangOptions.inventory_editorprivacy_moderation_name.getComponent(lang));
                meta.lore(lore);
            }),
            this::switchModerationStatus);
    }

    private void switchModerationStatus(@NonNull ClickEvent event) {
        GameSettings settings = this.level.getLevelSettings().getGameSettings();

        switch (settings.getModerationStatus()) {
            case NOT_MODERATED -> {
                settings.setModerationStatus(ModerationStatus.ON_MODERATION);
                boolean changeVisibility = !settings.isPublicVisible();
                if (changeVisibility) {
                    settings.setPublicVisible(true);
                }
                Placeholders nameplaceholder = new Placeholders("%name%", event.getPlayer().getName());
                for (Player editor : this.activity.getAllEditors()) {
                    TeleportUtils.teleportAsync(this.plugin, editor, Settings.getLobbySpawn()).whenComplete((success, throwable) -> {
                        if (success) {
                        	editor.sendMessage((changeVisibility ? LangOptions.inventory_editorprivacy_moderation_requested_visibilitychanged : LangOptions.inventory_editorprivacy_moderation_requested_visibilitynotchanged).getComponent(editor, nameplaceholder));
                        }
                    });
                }
            }
            case ON_MODERATION -> {
                settings.setModerationStatus(ModerationStatus.NOT_MODERATED);
                this.activity.updateInventoriesOfAllEditors(PrivacySettingsMenu.class,
                    PrivacySettingsMenu::updateModerationStatusItem);
            }
            case MODERATED -> {
                event.getPlayer().sendMessage(LangOptions.inventory_editorprivacy_moderation_moderated.getComponent(lang));
                event.getPlayer().closeInventory();
            }
        }
    }

    private void back(@NonNull ClickEvent event) {
        new EditorMainMenu(this.plugin, lang, this.activity).open(event.getPlayer());
    }
}
