package ru.sortix.parkourbeat.inventory.type;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.activity.type.PlayActivity;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.inventory.Heads;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.UIHeads;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.inventory.type.moderation.ModeratorConfirmationMenu;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.ModerationStatus;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.world.TeleportUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Predicate;

import ru.sortix.parkourbeat.utils.text.PbText;
public class LevelsListMenu extends PaginatedMenu<ParkourBeat, GameSettings> {
    private final @NonNull DisplayMode displayMode;
    private final @NonNull Player viewer;
    private final @NonNull UUID ownerId;
    private final boolean displayTechInfo;

    private SortMode sortMode = SortMode.DIFFICULTY_ASC;

    /** Показывать только 2D-уровни. */
    private boolean onlyTwoD = false;

    /**
     * Показывать только дуэльные карты. Включается из {@code /duel}: там список нужен
     * сразу отфильтрованным, обычные уровни в нём не нужны вовсе.
     */
    private boolean onlyDuel = false;

    public void setOnlyDuel(boolean onlyDuel) {
        this.onlyDuel = onlyDuel;
        this.updateAllItems();
    }

    public enum SortMode {
        DIFFICULTY_ASC("difficulty_asc"),
        DIFFICULTY_DESC("difficulty_desc"),
        POPULAR("popular"),
        DATE_NEWEST("date_newest");

        private final @NonNull String langKey;

        SortMode(@NonNull String langKey) {
            this.langKey = langKey;
        }

        @NonNull
        public String getDisplayName(String locale) {
            return Lang.raw(locale, "inventory.levellist.sort." + this.langKey);
        }

        public SortMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    public LevelsListMenu(@NonNull ParkourBeat plugin, String lang, @NonNull DisplayMode displayMode, @NonNull Player viewer, @NonNull UUID ownerId) {
        super(plugin, 6, lang, LangOptions.inventory_levellist_title.getComponent(lang), CONTENT_SLOTS);
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
            case MODERATION -> gs -> gs.getModerationStatus() != ModerationStatus.ON_MODERATION;
            case UNRANKED -> gs -> !gs.isPublicVisible();
            case RANKED -> gs -> !gs.isPublicVisible();
            case SELF -> gs -> !gs.canEdit(this.ownerId);
        };

        List<GameSettings> settings = new ArrayList<>(this.plugin.get(LevelsManager.class).getAvailableLevelsSettings());
        settings.removeIf(removeIf);
        if (this.onlyTwoD) settings.removeIf(gs -> !gs.getLevelMode().isTwoD());
        if (this.onlyDuel) settings.removeIf(gs -> !gs.getLevelMode().isDuel());

        settings.sort((a, b) -> {
            switch (this.sortMode) {
                case POPULAR:
                    // Популярность считаем по числу оценок: это единственная величина,
                    // которая растёт только от живых прохождений.
                    int popular = Integer.compare(b.getPlayerRatings().size(), a.getPlayerRatings().size());
                    if (popular != 0) return popular;
                    return Long.compare(b.getCreatedAtMills(), a.getCreatedAtMills());
                case DIFFICULTY_ASC:
                    if (a.getDifficulty() == LevelDifficulty.N_A && b.getDifficulty() != LevelDifficulty.N_A) return 1;
                    if (a.getDifficulty() != LevelDifficulty.N_A && b.getDifficulty() == LevelDifficulty.N_A) return -1;
                    int diffAsc = a.getDifficulty().compareTo(b.getDifficulty());
                    if (diffAsc != 0) return diffAsc;
                    return Long.compare(b.getCreatedAtMills(), a.getCreatedAtMills());
                case DIFFICULTY_DESC:
                    if (a.getDifficulty() == LevelDifficulty.N_A && b.getDifficulty() != LevelDifficulty.N_A) return 1;
                    if (a.getDifficulty() != LevelDifficulty.N_A && b.getDifficulty() == LevelDifficulty.N_A) return -1;
                    int diffDesc = b.getDifficulty().compareTo(a.getDifficulty());
                    if (diffDesc != 0) return diffDesc;
                    return Long.compare(b.getCreatedAtMills(), a.getCreatedAtMills());
                case DATE_NEWEST:
                    return Long.compare(b.getCreatedAtMills(), a.getCreatedAtMills());
                default:
                    return 0;
            }
        });
        return settings;
    }

    @Override
    protected @NonNull ItemStack createItemDisplay(@NonNull GameSettings gameSettings) {
        return ItemUtils.modifyMeta(Heads.getHeadByTextureData(gameSettings.getDifficulty().getHeadBase64(), true), meta -> {
            String levelName = PbText.keepColors(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().serialize(gameSettings.getDisplayName()));
            meta.displayName(LangOptions.inventory_levellist_item_name.getComponent(lang,
                new Placeholders("%level%", levelName)
            ));

            List<net.kyori.adventure.text.Component> lore = new ArrayList<>(LangOptions.inventory_levellist_item_lore.getComponents(lang,
                new Placeholders("%id%", String.valueOf(gameSettings.getUniqueNumber())),
                new Placeholders("%access%", gameSettings.isPublicVisible() ? "PUBLIC" : "PRIVATE"),
                new Placeholders("%author%", gameSettings.getOwnerName()), // Только главный автор
                new Placeholders("%stars%", String.valueOf(gameSettings.getPlayerRatings().size())),
                new Placeholders("%difficulty%", gameSettings.getDifficulty().getDisplayName()),
                new Placeholders("%type%", typeValue(gameSettings)),
                new Placeholders("%date%", new SimpleDateFormat("yyyy.MM.dd").format(new Date(gameSettings.getCreatedAtMills())))
            ));

            if (gameSettings.isCustomTextures()) {
                lore.add(Lang.item(lang, "level.textures.custom"));
                if (gameSettings.getTextureVersionRange() != null) {
                    lore.add(Lang.item(lang, "level.textures.versions",
                        "%versions%", gameSettings.getTextureVersionRange().getLabel()));
                }
            }

            if (this.displayMode == DisplayMode.SELF) {
                lore.addAll(LangOptions.inventory_levellist_item_actions_self.getComponents(lang));
            }
            meta.lore(lore);
        });
    }

    @Override
    protected void onPageDisplayed() {
        // В режиме "только 2D" рамка жёлтая: видно с первого взгляда, что список отфильтрован.
        Material glassMaterial = this.onlyTwoD
            ? Material.YELLOW_STAINED_GLASS_PANE
            : Material.BLACK_STAINED_GLASS_PANE;
        ItemStack glass = ItemUtils.create(glassMaterial, m -> m.displayName(net.kyori.adventure.text.Component.empty()));
        for (int i = 0; i < 54; i++) {
            boolean isContent = false;
            for (int slot : CONTENT_SLOTS) if (i == slot) { isContent = true; break; }
            if (!isContent) this.setItem(i, glass, null);
        }

        this.setPreviousPageItem(6, 4);
        this.setItem(6, 5, ItemUtils.create(Material.BARRIER, m -> m.displayName(LangOptions.inventory_regularitems_close.getComponent(lang))), e -> e.getPlayer().closeInventory());
        this.setNextPageItem(6, 6);

        if (this.displayMode == DisplayMode.RANKED || this.displayMode == DisplayMode.UNRANKED) {
            this.updateSortButton();
            this.updateTwoDFilterButton();
            this.setItem(53, ItemUtils.create(Material.ENDER_PEARL, m -> m.displayName(
                LangOptions.inventory_edit_session_levels_name.getComponent(lang)
            )), e -> {
                e.getPlayer().closeInventory();
                e.getPlayer().performCommand("edit");
            });
        } else if (this.displayMode == DisplayMode.MODERATION) {
            this.setItem(8, ItemUtils.create(Material.WRITTEN_BOOK, m -> {
                m.displayName(LangOptions.inventory_levellist_feedback_name.getComponent(lang));
                m.lore(LangOptions.inventory_levellist_feedback_lore.getComponents(lang));
            }), e -> new RatingFeedbackMenu(plugin, lang).open(e.getPlayer()));

            // Вкладка с заявками на сброс статистики.
            int resetRequests = this.plugin.get(
                ru.sortix.parkourbeat.stats.StatResetRequestManager.class).getPendingCount();
            this.setItem(7, ItemUtils.create(
                resetRequests > 0 ? Material.REDSTONE_TORCH : Material.LEVER, m -> {
                    m.displayName(Lang.item(lang, resetRequests > 0
                            ? "inventory.levellist.statreset.name_pending"
                            : "inventory.levellist.statreset.name",
                        "%count%", String.valueOf(resetRequests)));
                    m.lore(Lang.lore(lang, resetRequests > 0
                            ? "inventory.levellist.statreset.lore_pending"
                            : "inventory.levellist.statreset.lore",
                        "%count%", String.valueOf(resetRequests)));
                }),
                e -> new ru.sortix.parkourbeat.inventory.type.moderation.StatResetRequestsMenu(
                    plugin, lang, e.getPlayer()).open(e.getPlayer()));
        } else if (this.displayMode == DisplayMode.SELF) {
            // Свои уровни сортируются так же, как общий список: набор режимов один.
            this.updateSortButton();
            this.updateTwoDFilterButton();
            this.setItem(45, ItemUtils.modifyMeta(UIHeads.ARROW_LEFT.clone(), m -> m.displayName(
                LangOptions.inventory_levellist_displaymode_self_backtoplay.getComponent(lang)
            )), e -> {
                e.getPlayer().closeInventory();
                e.getPlayer().performCommand("play");
            });
            this.setItem(53, ItemUtils.create(Material.WRITABLE_BOOK, m -> m.displayName(LangOptions.inventory_levellist_displaymode_self_createlevel.getComponent(lang))), e -> CreateLevelMenu.startCreating(plugin, viewer, lang));
        }
    }

    private void updateSortButton() {
        String sortName = this.sortMode.getDisplayName(this.lang);

        this.setItem(0, ItemUtils.modifyMeta(UIHeads.SORT.clone(), m -> {
            m.displayName(LangOptions.inventory_levellist_sort_name.getComponent(lang));
            m.lore(LangOptions.inventory_levellist_sort_lore.getComponents(lang, new Placeholders("%sort%", sortName)));
        }), e -> {
            this.sortMode = this.sortMode.next();
            this.updateAllItems();
        });
    }

    /**
     * Переключатель "только 2D".
     * <p>
     * Кубик из Geometry Dash включает фильтр, столб пурпура возвращает весь список:
     * по предмету сразу видно, в каком состоянии список сейчас.
     */
    private void updateTwoDFilterButton() {
        Material material = this.onlyTwoD ? Material.PURPUR_PILLAR : Material.REPEATING_COMMAND_BLOCK;

        this.setItem(8, ItemUtils.create(material, m -> {
            m.displayName(Lang.item(lang, this.onlyTwoD
                ? "inventory.levellist.twod.name_on"
                : "inventory.levellist.twod.name_off"));
            m.lore(Lang.lore(lang, this.onlyTwoD
                ? "inventory.levellist.twod.lore_on"
                : "inventory.levellist.twod.lore_off"));
        }), e -> {
            this.onlyTwoD = !this.onlyTwoD;
            this.updateAllItems();
        });
    }

    /**
     * Значение строки «Тип» для описания уровня.
     * <p>
     * Раньше эта строка вставлялась в готовый лор поиском слова «Сложность» по тексту -
     * на любом языке, кроме русского, вставка бы промахнулась. Теперь порядок строк
     * задан в lang.yml через плейсхолдер {@code %type%}, а здесь остаётся только значение.
     * <p>
     * Выделено только ЗНАЧЕНИЕ, и только у 2D: подчёркивать стоит то, что необычно.
     */
    @NonNull
    public static String typeValue(@NonNull GameSettings settings) {
        // Подчёркивается всё, что не обычный 3D: именно это игроку и нужно заметить
        // в списке, потому что играется такой уровень иначе.
        return switch (settings.getLevelMode()) {
            case TWO_D -> ru.sortix.parkourbeat.utils.text.Theme.V_YELLOW + "&n2D";
            case DUEL -> ru.sortix.parkourbeat.utils.text.Theme.V_GOLD + "&nДуэль";
            case THREE_SIXTY -> ru.sortix.parkourbeat.utils.text.Theme.V_GREEN + "&n360";
            case DIGGER -> ru.sortix.parkourbeat.utils.text.Theme.V_DARK_PURPLE + "&nКопатель";
            default -> ru.sortix.parkourbeat.utils.text.Theme.V_AQUA + "3D";
        };
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull GameSettings settings) {
        Player player = event.getPlayer();
        if (this.displayMode == DisplayMode.RANKED || this.displayMode == DisplayMode.UNRANKED) {
            new LevelDetailsMenu(plugin, lang, settings, player, this.displayMode).open(player);
        } else if (this.displayMode == DisplayMode.MODERATION) {
            new ModeratorConfirmationMenu(plugin, lang, settings, player).open(player);
        } else {
            // Свои уровни: обычный ЛКМ открывает меню уровня, а не бросает сразу в игру.
            // Быстрые действия остались на модификаторах клика.
            if (event.isLeft() && !event.isShift()) {
                new LevelDetailsMenu(plugin, lang, settings, player, this.displayMode).open(player);
                return;
            }
            player.closeInventory();
            if (event.isLeft()) {
                startEditing(plugin, player, settings);
            } else {
                startSpectating(plugin, player, settings);
            }
        }
    }

    public static void startPlaying(@NonNull ParkourBeat plugin, @NonNull Player player, @NonNull GameSettings settings) {
        String lang = PlayerLang.of(player);

        // НА ДУЭЛЬНУЮ КАРТУ ОДНОМУ НЕ ЗАЙТИ.
        //
        // Дуэль - это забег против соперника; в одиночку половина карты (чужая трасса)
        // просто не имеет смысла. Вход только через очередь, которая и раздаёт стороны.
        if (settings.getLevelMode().isDuel()
            && !plugin.get(ru.sortix.parkourbeat.duel.DuelManager.class).isInDuel(player)) {
            plugin.get(ru.sortix.parkourbeat.duel.DuelManager.class).joinQueue(player, settings);
            return;
        }
        if (!settings.isAccessibleForPlaying(player, true)) {
            player.sendMessage(LangOptions.level_play_noaccess.getComponent(lang));
            return;
        }
        if (!ru.sortix.parkourbeat.listeners.GamesListener.canJoinLevel(player, settings)) {
            player.sendMessage(Lang.text(player, "level.textures.wrongversion",
                "%versions%", settings.getTextureVersionRange().getLabel()));
            return;
        }
        Level level = plugin.get(LevelsManager.class).getLoadedLevel(settings.getUniqueId());
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
                    if (!success) player.sendMessage(LangOptions.level_play_failteleport.getComponent(lang));
                });
        });
    }

    public static void startSpectating(@NonNull ParkourBeat plugin, @NonNull Player player, @NonNull GameSettings settings) {
        String lang = PlayerLang.of(player);
        if (!settings.isAccessibleForPlaying(player, true)) {
            player.sendMessage(LangOptions.level_spectate_noaccess.getComponent(lang));
            return;
        }
        plugin.get(LevelsManager.class).loadLevel(settings.getUniqueId(), settings).thenAccept(level -> {
            if (level == null) {
                player.sendMessage(LangOptions.level_spectate_failload.getComponent(lang));
                return;
            }
            if (level.getWorld() == player.getWorld()) {
                player.sendMessage(LangOptions.level_spectate_alreadyinworld.getComponent(lang));
                return;
            }
            TeleportUtils.teleportAsync(plugin, player, level.getSpawn());
        });
    }

    public static void startEditing(@NonNull ParkourBeat plugin, @NonNull Player player, @NonNull GameSettings settings) {
        startEditing(plugin, player, settings, true);
    }

    public static void startEditing(@NonNull ParkourBeat plugin, @NonNull Player player, @NonNull GameSettings settings, boolean allowModerationMenu) {
        String lang = PlayerLang.of(player);

        if (allowModerationMenu && player.hasPermission(PermissionConstants.MODERATE_LEVELS)
            && (settings.getModerationStatus() == ModerationStatus.MODERATED || settings.getModerationStatus() == ModerationStatus.ON_MODERATION)) {
            new ModeratorConfirmationMenu(plugin, lang, settings, player).open(player);
            return;
        }

        if (!settings.canEdit(player, true, true)) {
            player.sendMessage(LangOptions.level_editor_cantedit_notowner.getComponent(lang));
            return;
        }

        if (settings.getModerationStatus() == ModerationStatus.MODERATED) {
            player.sendMessage(LangOptions.level_editor_cantedit_moderated.getComponent(lang));
            return;
        }

        if (settings.getModerationStatus() == ModerationStatus.ON_MODERATION) {
            settings.setModerationStatus(ModerationStatus.NOT_MODERATED);
            settings.setDifficulty(LevelDifficulty.N_A);
            plugin.get(LevelsManager.class).saveGameSettings(settings);
            LangOptions.level_editor_unmoderated_by_edit.sendMsg(player);
        }

        plugin.get(LevelsManager.class).loadLevel(settings.getUniqueId(), settings).thenAccept(level -> {
            if (level == null) {
                player.sendMessage(LangOptions.level_editor_cantedit_failload.getComponent(lang));
                return;
            }
            ActivityManager activityManager = plugin.get(ActivityManager.class);
            Collection<Player> playersOnLevel = activityManager.getPlayersOnTheLevel(level);
            playersOnLevel.removeIf(p -> settings.canEdit(p, true, false));
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
                    if (!success) player.sendMessage(LangOptions.level_editor_cantedit_failteleport.getComponent(lang));
                });
            });
        });
    }

    @RequiredArgsConstructor
    public enum DisplayMode { MODERATION, UNRANKED, RANKED, SELF }
}
