package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
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
import ru.sortix.parkourbeat.world.TeleportUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Predicate;

public class LevelsListMenu extends PaginatedMenu<ParkourBeat, GameSettings> {
    private static final SimpleDateFormat LEVEL_CREATION_DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy");
    private final @NonNull DisplayMode displayMode;
    private final @NonNull Player viewer;
    private final @NonNull UUID ownerId;
    private final boolean displayTechInfo;

    public LevelsListMenu(@NonNull ParkourBeat plugin,
                          @NonNull DisplayMode displayMode,
                          @NonNull Player viewer,
                          @NonNull UUID ownerId
    ) {
        super(plugin, 6, Component.text("Уровни"), 0, 5 * 9);
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
        if (level != null && level.isEditing()) {
            player.sendMessage("Данный уровень недоступен для игры, т.к. он сейчас редактируется");
            return;
        }

        UserActivity previousActivity = plugin.get(ActivityManager.class).getActivity(player);
        if (previousActivity instanceof PlayActivity && previousActivity.getLevel() == level) {
            player.sendMessage("Вы уже на этом уровне!");
            return;
        }

        PlayActivity.createAsync(plugin, player, settings.getUniqueId(), false).thenAccept(playActivity -> {
            if (playActivity == null) {
                player.sendMessage("Не удалось подготовить игру");
                return;
            }

            plugin.get(ActivityManager.class).switchActivity(player, playActivity, playActivity.getLevel().getSpawn())
                .thenAccept(success -> {
                    if (!success) {
                        player.sendMessage("Не удалось телепортировать вас на уровень");
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
                    player.sendMessage("Не удалось загрузить данные уровня");
                    return;
                }
                if (level.getWorld() == player.getWorld()) {
                    player.sendMessage("Вы уже в этом мире");
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
        if (allowModerationMenu
            && settings.getModerationStatus() == ModerationStatus.ON_MODERATION
            && player.hasPermission(PermissionConstants.MODERATE_LEVELS)
        ) {
            new ModeratorConfirmationMenu(plugin, settings, player).open(player);
            return;
        }

        if (!settings.isOwner(player, true, true)) {
            player.sendMessage("Вы не являетесь владельцем этого уровня");
            return;
        }

        switch (settings.getModerationStatus()) {
            case MODERATED -> {
                player.sendMessage("Уровень прошёл модерацию, редактирование недоступно");
                return;
            }
            case ON_MODERATION -> {
                if (!player.hasPermission(PermissionConstants.EDIT_OTHERS_LEVELS_ON_MODERATION)) {
                    new CancelModerationRequestMenu(plugin, settings).open(player);
                    return;
                }
            }
        }

        plugin.get(LevelsManager.class)
            .loadLevel(settings.getUniqueId(), settings)
            .thenAccept(level -> {
                if (level == null) {
                    player.sendMessage("Не удалось загрузить данные уровня");
                    return;
                }

                if (level.isEditing()) {
                    player.sendMessage("Данный уровень уже редактируется");
                    return;
                }

                ActivityManager activityManager = plugin.get(ActivityManager.class);

                Collection<Player> playersOnLevel = activityManager.getPlayersOnTheLevel(level);
                playersOnLevel.removeIf(player1 -> settings.isOwner(player1, true, false));

                if (!playersOnLevel.isEmpty()) {
                    player.sendMessage("Нельзя редактировать уровень, на котором находятся игроки");
                    return;
                }

                EditActivity.createAsync(plugin, player, level).thenAccept(editActivity -> {
                    if (editActivity == null) {
                        player.sendMessage("Не удалось запустить редактор уровня");
                        return;
                    }
                    activityManager.switchActivity(player, editActivity, level.getSpawn()).thenAccept(success -> {
                        if (success) return;
                        player.sendMessage("Не удалось телепортироваться в мир уровня");
                    });
                });
            });
    }

    @Override
    protected @NonNull ItemStack createItemDisplay(@NonNull GameSettings gameSettings) {
        return ItemUtils.modifyMeta(new ItemStack(Material.PAPER), meta -> {
            meta.displayName(gameSettings.getDisplayName());

            List<Component> lore = new ArrayList<>();
            if (this.displayTechInfo) {
                lore.add(Component.text("UUID: " + gameSettings.getUniqueId(), NamedTextColor.GRAY));
                lore.add(Component.text("Статус модерации: " + gameSettings.getModerationStatus().getDisplayName(), NamedTextColor.GRAY));
            }
            if (this.displayMode == DisplayMode.SELF || this.displayTechInfo) {
                lore.add(Component.text("Доступ: " + (gameSettings.isPublicVisible() ? "Публичный" : "Приватный"),
                    this.displayMode == DisplayMode.SELF ? NamedTextColor.YELLOW : NamedTextColor.GRAY));
            }
            if (gameSettings.getUniqueName() == null) {
                lore.add(Component.text("Номер для команд: " + gameSettings.getUniqueNumber(), NamedTextColor.YELLOW));
            } else {
                lore.add(Component.text("Название для команд: " + gameSettings.getUniqueName(), NamedTextColor.YELLOW));
            }
            lore.add(Component.text("Создатель: " + gameSettings.getOwnerName(), NamedTextColor.YELLOW));
            if (this.displayTechInfo) {
                lore.add(Component.text("UUID создателя: " + gameSettings.getOwnerId(), NamedTextColor.GRAY));
            }
            lore.add(Component.text("Дата создания: "
                + LEVEL_CREATION_DATE_FORMAT.format(new Date(gameSettings.getCreatedAtMills())), NamedTextColor.YELLOW));
            lore.add(Component.text("Трек: "
                + (gameSettings.getMusicTrack() == null
                ? "отсутствует"
                : gameSettings.getMusicTrack().getName()), NamedTextColor.YELLOW));
            lore.add(Component.text("ЛКМ, чтобы играть", NamedTextColor.GOLD));
            lore.add(Component.text("ПКМ, чтобы наблюдать", NamedTextColor.GOLD));

            if (gameSettings.getModerationStatus() == ModerationStatus.ON_MODERATION
                && this.viewer.hasPermission(PermissionConstants.MODERATE_LEVELS)
            ) {
                lore.add(Component.text("Шифт + ЛКМ, чтобы модерировать", NamedTextColor.GOLD));
            } else if (gameSettings.isOwner(this.viewer, true, false)) {
                lore.add(Component.text("Шифт + ЛКМ, чтобы редактировать", NamedTextColor.GOLD));
            }

            if (this.displayTechInfo) {
                lore.add(Component.text("Шифт + ПКМ, чтобы скопировать UUID", NamedTextColor.GRAY));
            }
            meta.lore(lore);
        });
    }

    @Override
    protected void onPageDisplayed() {
        this.setNextPageItem(6, 3);
        this.setItem(
            6, 5, RegularItems.closeInventory(), event -> event.getPlayer().closeInventory());
        this.setPreviousPageItem(6, 7);
        if (this.displayMode == DisplayMode.SELF) {
            this.setItem(
                6,
                1,
                ItemUtils.create(
                    Material.WRITABLE_BOOK, meta -> meta.displayName(Component.text("Создать уровень", NamedTextColor.GOLD))),
                event -> new CreateLevelMenu(this.plugin).open(event.getPlayer()));
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
                meta.displayName(Component.text(mode.displayName, selectedMode ? NamedTextColor.YELLOW : NamedTextColor.GOLD));
                meta.addItemFlags(ItemFlag.values());
                if (selectedMode) {
                    meta.addEnchant(Enchantment.ARROW_INFINITE, 1, true);
                }
            }),
            event -> {
                Player player = event.getPlayer();
                new LevelsListMenu(this.plugin, mode, this.viewer, this.ownerId).open(player);
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
                    this.viewer.sendMessage(Component.text("> Скопировать UUID уровня <", NamedTextColor.YELLOW)
                        .hoverEvent(HoverEvent.showText(Component.text("Нажмите для копирования", NamedTextColor.GOLD)))
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(settings.getUniqueId().toString()))
                    );
                }
            } else {
                startSpectating(this.plugin, event.getPlayer(), settings);
            }
        }
    }

    @RequiredArgsConstructor
    public enum DisplayMode {
        MODERATION("На модерации", Material.TNT),
        UNRANKED("Пользовательские уровни", Material.BOOKSHELF),
        RANKED("Рейтинговые уровни", Material.BOOK),
        SELF("Собственные уровни", Material.WRITABLE_BOOK);

        private final String displayName;
        private final Material iconMaterial;
    }
}
