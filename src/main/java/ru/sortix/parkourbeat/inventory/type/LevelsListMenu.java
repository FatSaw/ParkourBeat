package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.activity.type.PlayActivity;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.RegularItems;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.inventory.type.moderation.CancelModerationRequestMenu;
import ru.sortix.parkourbeat.inventory.type.moderation.ModeratorConfirmationMenu;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.ModerationStatus;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.world.TeleportUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Predicate;

public class LevelsListMenu extends PaginatedMenu<ParkourBeat, GameSettings> {
    private final @NonNull DisplayMode displayMode;
    private final @NonNull Player viewer;
    private final @NonNull UUID ownerId;
    private final boolean displayTechInfo;

    public LevelsListMenu(@NonNull ParkourBeat plugin, String lang,
                          @NonNull DisplayMode displayMode,
                          @NonNull Player viewer,
                          @NonNull UUID ownerId
    ) {
        super(plugin, 6, lang, LangOptions.inventory_levellist_title.getComponent(lang), 0, 5 * 9);
        this.displayMode = displayMode;
        this.viewer = viewer;
        this.ownerId = ownerId;
        this.displayTechInfo = viewer.hasPermission(PermissionConstants.VIEW_TECH_LEVELS_INFO);
        this.updateAllItems();
    }

    @Override
    @NonNull
    protected Collection<GameSettings> getAllItems() {
        Predicate<GameSettings> removeIf = switch (this.displayMode) {
            case MODERATION -> gameSettings -> {
                boolean allowIf = gameSettings.getModerationStatus() == ModerationStatus.ON_MODERATION;
                return !allowIf;
            };
            case UNRANKED -> gameSettings -> {
                boolean allowIf = gameSettings.isPublicVisible() && gameSettings.getModerationStatus() != ModerationStatus.MODERATED;
                return !allowIf;
            };
            case RANKED -> gameSettings -> {
                boolean allowIf = gameSettings.isPublicVisible() && gameSettings.getModerationStatus() == ModerationStatus.MODERATED;
                return !allowIf;
            };
            case SELF -> gameSettings -> {
                boolean allowIf = gameSettings.isOwner(this.ownerId);
                return !allowIf;
            };
        };

        List<GameSettings> settings =
            new ArrayList<>(this.plugin.get(LevelsManager.class).getAvailableLevelsSettings());
        settings.removeIf(removeIf);
        settings.sort(Comparator.comparingLong(GameSettings::getUniqueNumber));
        return settings;
    }

    public static void startPlaying(
        @NonNull ParkourBeat plugin, @NonNull Player player, @NonNull GameSettings settings) {
        Level level = plugin.get(LevelsManager.class).getLoadedLevel(settings.getUniqueId());
        String lang = player.getLocale().toLowerCase();
        if (level != null && level.isEditing()) {
            player.sendMessage(LangOptions.level_play_unavilable.getComponent(lang));
            return;
        }

        UserActivity previousActivity = plugin.get(ActivityManager.class).getActivity(player);
        if (previousActivity instanceof PlayActivity && previousActivity.getLevel() == level) {
            player.sendMessage(LangOptions.level_play_alreadyinworld.getComponent(lang));
            return;
        }

        PlayActivity.createAsync(plugin, player, settings.getUniqueId(), false).thenAccept(playActivity -> {
            if (playActivity == null) {
                player.sendMessage(LangOptions.level_play_failload.getComponent(lang));
                return;
            }

            plugin.get(ActivityManager.class).switchActivity(player, playActivity, playActivity.getLevel().getSpawn())
                .thenAccept(success -> {
                    if (!success) {
                        player.sendMessage(LangOptions.level_play_failteleport.getComponent(lang));
                    }
                });
        });
    }

    public static void startSpectating(
        @NonNull ParkourBeat plugin, @NonNull Player player, @NonNull GameSettings settings) {
        plugin.get(LevelsManager.class)
            .loadLevel(settings.getUniqueId(), settings)
            .thenAccept(level -> {
                if (level == null) {
                    player.sendMessage(LangOptions.level_spectate_failload.getComponent(player.getLocale().toLowerCase()));
                    return;
                }
                if (level.getWorld() == player.getWorld()) {
                    player.sendMessage(LangOptions.level_spectate_alreadyinworld.getComponent(player.getLocale().toLowerCase()));
                    return;
                }
                TeleportUtils.teleportAsync(plugin, player, level.getSpawn());
            });
    }

    public static void startEditing(@NonNull ParkourBeat plugin,
                                    @NonNull Player player,
                                    @NonNull GameSettings settings
    ) {
        startEditing(plugin, player, settings, true);
    }

    public static void startEditing(@NonNull ParkourBeat plugin,
                                    @NonNull Player player,
                                    @NonNull GameSettings settings,
                                    boolean allowModerationMenu
    ) {
    	String lang = player.getLocale().toLowerCase();
        if (allowModerationMenu
            && settings.getModerationStatus() == ModerationStatus.ON_MODERATION
            && player.hasPermission(PermissionConstants.MODERATE_LEVELS)
        ) {
            new ModeratorConfirmationMenu(plugin, lang, settings, player).open(player);
            return;
        }

        if (!settings.isOwner(player, true, true)) {
            player.sendMessage(LangOptions.level_editor_cantedit_notowner.getComponent(lang));
            return;
        }

        switch (settings.getModerationStatus()) {
            case MODERATED -> {
                player.sendMessage(LangOptions.level_editor_cantedit_moderated.getComponent(lang));
                return;
            }
            case ON_MODERATION -> {
                if (!player.hasPermission(PermissionConstants.EDIT_OTHERS_LEVELS_ON_MODERATION)) {
                    new CancelModerationRequestMenu(plugin, lang, settings).open(player);
                    return;
                }
            }
        }

        plugin.get(LevelsManager.class)
            .loadLevel(settings.getUniqueId(), settings)
            .thenAccept(level -> {
                if (level == null) {
                    player.sendMessage(LangOptions.level_editor_cantedit_failload.getComponent(lang));
                    return;
                }

                if (level.isEditing()) {
                    player.sendMessage(LangOptions.level_editor_cantedit_editingnow.getComponent(lang));
                    return;
                }

                ActivityManager activityManager = plugin.get(ActivityManager.class);

                Collection<Player> playersOnLevel = activityManager.getPlayersOnTheLevel(level);
                playersOnLevel.removeIf(player1 -> settings.isOwner(player1, true, false));

                if (!playersOnLevel.isEmpty()) {
                    player.sendMessage(LangOptions.level_editor_cantedit_playersonlevel.getComponent(lang));
                    return;
                }

                EditActivity.createAsync(plugin, player, level).thenAccept(editActivity -> {
                    if (editActivity == null) {
                        player.sendMessage(LangOptions.level_editor_cantedit_failstart.getComponent(lang));
                        return;
                    }
                    activityManager.switchActivity(player, editActivity, level.getSpawn()).thenAccept(success -> {
                        if (success) return;
                        player.sendMessage(LangOptions.level_editor_cantedit_failteleport.getComponent(lang));
                    });
                });
            });
    }

    @Override
    protected @NonNull ItemStack createItemDisplay(@NonNull GameSettings gameSettings) {
        return ItemUtils.modifyMeta(new ItemStack(Material.PAPER), meta -> {
        	meta.displayName(LangOptions.inventory_levellist_selectlevel_name.getComponent(lang, new Placeholders("%level%", ((TextComponent)gameSettings.getDisplayName()).content())));

            List<Component> lore = new ArrayList<>();
            if (this.displayTechInfo) {
            	lore.addAll(LangOptions.inventory_levellist_selectlevel_lore_uuid.getComponents(lang, new Placeholders("%uuid%", gameSettings.getUniqueId().toString())));
            	switch(gameSettings.getModerationStatus()) {
            	case NOT_MODERATED:
                	lore.addAll(LangOptions.inventory_levellist_selectlevel_lore_moderation_notmoderated.getComponents(lang));
                	break;
            	case ON_MODERATION:
                	lore.addAll(LangOptions.inventory_levellist_selectlevel_lore_moderation_onmoderation.getComponents(lang));
                	break;
                case MODERATED:
                    lore.addAll(LangOptions.inventory_levellist_selectlevel_lore_moderation_moderated.getComponents(lang));
                    break;
            	}
            }
            lore.addAll((this.displayMode == DisplayMode.SELF ? gameSettings.isPublicVisible() ? LangOptions.inventory_levellist_selectlevel_lore_displaymode_self_public : LangOptions.inventory_levellist_selectlevel_lore_displaymode_self_private : gameSettings.isPublicVisible() ? LangOptions.inventory_levellist_selectlevel_lore_displaymode_techinfo_public : LangOptions.inventory_levellist_selectlevel_lore_displaymode_techinfo_private).getComponents(lang));
            
            lore.addAll(gameSettings.getUniqueName() == null ? LangOptions.inventory_levellist_selectlevel_lore_uniqueid_number.getComponents(lang, new Placeholders("%id%", Integer.valueOf(gameSettings.getUniqueNumber()).toString())) : LangOptions.inventory_levellist_selectlevel_lore_uniqueid_name.getComponents(lang, new Placeholders("%name%", gameSettings.getUniqueName())));
            
            lore.addAll(LangOptions.inventory_levellist_selectlevel_lore_creator_name.getComponents(lang, new Placeholders("%name%", gameSettings.getOwnerName())));
            
            if (this.displayTechInfo) {
            	lore.addAll(LangOptions.inventory_levellist_selectlevel_lore_creator_uuid.getComponents(lang, new Placeholders("%uuid%", gameSettings.getOwnerId().toString())));
            }
            lore.addAll(LangOptions.inventory_levellist_selectlevel_lore_creationtime_time.getComponents(lang, new Placeholders("%time%", new SimpleDateFormat(LangOptions.inventory_levellist_selectlevel_lore_creationtime_format.get(lang)).format(new Date(gameSettings.getCreatedAtMills())))));
            lore.addAll(gameSettings.getMusicTrack() == null ? LangOptions.inventory_levellist_selectlevel_lore_track_no.getComponents(lang) : LangOptions.inventory_levellist_selectlevel_lore_track_is.getComponents(lang, new Placeholders("%track%", gameSettings.getMusicTrack().getName())));

            lore.addAll(LangOptions.inventory_levellist_selectlevel_lore_actions_default.getComponents(lang));

            if (gameSettings.getModerationStatus() == ModerationStatus.ON_MODERATION
                && this.viewer.hasPermission(PermissionConstants.MODERATE_LEVELS)
            ) {
            	lore.addAll(LangOptions.inventory_levellist_selectlevel_lore_actions_moderator.getComponents(lang));
            } else if (gameSettings.isOwner(this.viewer, true, false)) {
            	lore.addAll(LangOptions.inventory_levellist_selectlevel_lore_actions_owner.getComponents(lang));
            }

            if (this.displayTechInfo) {
            	lore.addAll(LangOptions.inventory_levellist_selectlevel_lore_actions_tech.getComponents(lang));
            }
            meta.lore(lore);
        });
    }

    @Override
    protected void onPageDisplayed() {
        this.setNextPageItem(6, 3);
        this.setItem(
            6, 5, RegularItems.closeInventory(lang), event -> event.getPlayer().closeInventory());
        this.setPreviousPageItem(6, 7);
        if (this.displayMode == DisplayMode.SELF) {
            this.setItem(
                6,
                1,
                ItemUtils.create(
                    Material.WRITABLE_BOOK, meta -> meta.displayName(LangOptions.inventory_levellist_displaymode_self_createlevel.getComponent(lang))),
                event -> new CreateLevelMenu(this.plugin, lang).open(event.getPlayer()));
        }
        if (this.viewer.hasPermission(PermissionConstants.MODERATE_LEVELS)) {
            this.setDisplayModeItem(DisplayMode.MODERATION, 6, 6);
        }
        this.setDisplayModeItem(DisplayMode.UNRANKED, 6, 7);
        this.setDisplayModeItem(DisplayMode.RANKED, 6, 8);
        this.setDisplayModeItem(DisplayMode.SELF, 6, 9);
    }

    private void setDisplayModeItem(@NonNull DisplayMode mode,
                                    @SuppressWarnings("SameParameterValue") int row, int column
    ) {
        boolean selectedMode = mode == this.displayMode;
        this.setItem(
            row,
            column,
            ItemUtils.create(mode.iconMaterial, meta -> {
            	meta.displayName((selectedMode ? mode.selectedDisplayName : mode.unselectedDisplayName).getComponent(lang));
                meta.addItemFlags(ItemFlag.values());
                if (selectedMode) {
                    meta.addEnchant(Enchantment.ARROW_INFINITE, 1, true);
                }
            }),
            event -> {
                Player player = event.getPlayer();
                new LevelsListMenu(this.plugin, lang, mode, this.viewer, this.ownerId).open(player);
            });
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull GameSettings settings) {
        if (event.isLeft()) {
            if (event.isShift()) {
                startEditing(this.plugin, event.getPlayer(), settings);
            } else {
                startPlaying(this.plugin, event.getPlayer(), settings);
            }
        } else {
            if (event.isShift()) {
                if (this.displayTechInfo) {
                    event.getPlayer().closeInventory();
                    this.viewer.sendMessage(LangOptions.inventory_levellist_copyleveluuid.getComponent(lang, new Placeholders("%uuid%", settings.getUniqueId().toString())));
                }
            } else {
                startSpectating(this.plugin, event.getPlayer(), settings);
            }
        }
    }

    @RequiredArgsConstructor
    public enum DisplayMode {
        MODERATION(LangOptions.inventory_levellist_displaymode_moderation_selected, LangOptions.inventory_levellist_displaymode_moderation_unselected, Material.TNT),
        UNRANKED(LangOptions.inventory_levellist_displaymode_unranked_selected, LangOptions.inventory_levellist_displaymode_unranked_unselected, Material.BOOKSHELF),
        RANKED(LangOptions.inventory_levellist_displaymode_ranked_selected, LangOptions.inventory_levellist_displaymode_ranked_unselected, Material.BOOK),
        SELF(LangOptions.inventory_levellist_displaymode_self_selected, LangOptions.inventory_levellist_displaymode_self_unselected, Material.WRITABLE_BOOK);

        private final LangOptions selectedDisplayName, unselectedDisplayName;
        private final Material iconMaterial;
    }
}
