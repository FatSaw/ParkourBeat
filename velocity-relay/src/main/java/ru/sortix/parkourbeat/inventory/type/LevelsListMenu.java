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
import ru.sortix.parkourbeat.ui.Icons;

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
    /** Какие уровни показывать: вкладки режимов сверху. По умолчанию - обычные 3D. */
    private ru.sortix.parkourbeat.twod.LevelMode modeFilter = ru.sortix.parkourbeat.twod.LevelMode.THREE_D;

    /**
     * Показывать только дуэльные карты. Включается из {@code /duel}: там список нужен
     * сразу отфильтрованным, обычные уровни в нём не нужны вовсе.
     */
    private boolean onlyDuel = false;

    public void setOnlyDuel(boolean onlyDuel) {
        this.onlyDuel = onlyDuel;
        if (onlyDuel) this.modeFilter = ru.sortix.parkourbeat.twod.LevelMode.DUEL;
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

    /** Витрина 7x3 в центре окна (см. фон menu_levels в РП). */
    private static final int[] CONTENT_SLOTS = {
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35
    };

    /** Фильтр по сложностям: пусто - показываются все. */
    private final java.util.EnumSet<LevelDifficulty> difficultyFilter = java.util.EnumSet.noneOf(LevelDifficulty.class);

    private static final LevelDifficulty[] FILTER_DIFFICULTIES = {LevelDifficulty.EASY, LevelDifficulty.NORMAL,
        LevelDifficulty.HARD, LevelDifficulty.EXPERT, LevelDifficulty.EXPERT_PLUS};

    @NonNull
    private static net.kyori.adventure.text.Component title(String lang) {
        return ru.sortix.parkourbeat.ui.Glyphs.menuTitle(ru.sortix.parkourbeat.ui.Glyphs.menuLevels(lang),
            ru.sortix.parkourbeat.ui.Icons.Icon.SEARCH.component()
                .append(net.kyori.adventure.text.Component.text(" "))
                .append(LangOptions.inventory_levellist_title.getComponent(lang)));
    }

    @NonNull
    private String t(@NonNull String ruText, @NonNull String enText, @NonNull String ukText) {
        int set = ru.sortix.parkourbeat.ui.Glyphs.localeSet(this.lang);
        return set == 1 ? enText : set == 2 ? ukText : ruText;
    }

    public LevelsListMenu(@NonNull ParkourBeat plugin, String lang, @NonNull DisplayMode displayMode, @NonNull Player viewer, @NonNull UUID ownerId) {
        super(plugin, 6, lang, title(lang), CONTENT_SLOTS);
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
        // Вкладка режима (3D / 2D / Копатель / Дуэль / 360). Мои уровни и модерация - все режимы.
        if (this.displayMode == DisplayMode.RANKED || this.displayMode == DisplayMode.UNRANKED) {
            ru.sortix.parkourbeat.twod.LevelMode wanted = this.modeFilter;
            settings.removeIf(gs -> gs.getLevelMode() != wanted);
        }
        if (!this.difficultyFilter.isEmpty()) settings.removeIf(gs -> !this.difficultyFilter.contains(gs.getDifficulty()));

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
        // КАРТОЧКА УРОВНЯ: кубик сложности, а в описании - автор, плашка сложности,
        // тип, звёзды, сколько человек прошло и что делают кнопки мыши.
        return ItemUtils.modifyMeta(gameSettings.getDifficulty().createIcon(), meta -> {
            String levelName = PbText.keepColors(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().serialize(gameSettings.getDisplayName()));
            meta.displayName(LangOptions.inventory_levellist_item_name.getComponent(lang,
                new Placeholders("%level%", levelName)
            ));

            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7" + ru.sortix.parkourbeat.ui.SmallCaps.convert(this.t("от ", "by ", "від "))
                + "<v>&f" + gameSettings.getOwnerName() + "</v>"));
            lore.add(ru.sortix.parkourbeat.ui.Glyphs.glyph(ru.sortix.parkourbeat.ui.Glyphs.banner(gameSettings.getDifficulty(), lang))
                .append(PbText.item("  " + typeValue(gameSettings))));

            int votes = gameSettings.getStarRatings().size();
            int players = this.plugin.get(ru.sortix.parkourbeat.rating.StatisticsManager.class)
                .getLevelTopSize(gameSettings.getUniqueId());
            lore.add(ru.sortix.parkourbeat.ui.Glyphs.glyph(ru.sortix.parkourbeat.ui.Glyphs.stars(gameSettings.getAverageStars()))
                .append(PbText.item(votes == 0
                    ? " &8" + this.t("нет оценок", "no ratings", "немає оцінок")
                    : " &7" + String.format(Locale.ROOT, "%.1f", gameSettings.getAverageStars()) + " &8(" + votes + ")")));
            lore.add(PbText.item("<i:user> &7" + players + "   <i:clock> &7"
                + new SimpleDateFormat("dd.MM.yyyy").format(new Date(gameSettings.getCreatedAtMills()))));
            if (this.displayTechInfo) {
                lore.add(PbText.item("&8#" + gameSettings.getUniqueNumber() + " \u00B7 "
                    + (gameSettings.isPublicVisible() ? "PUBLIC" : "PRIVATE")));
            }

            if (gameSettings.isCustomTextures()) {
                lore.add(Lang.item(lang, "level.textures.custom"));
                if (gameSettings.getTextureVersionRange() != null) {
                    lore.add(Lang.item(lang, "level.textures.versions",
                        "%versions%", gameSettings.getTextureVersionRange().getLabel()));
                }
            }

            lore.add(net.kyori.adventure.text.Component.empty());
            if (this.displayMode == DisplayMode.SELF) {
                lore.addAll(LangOptions.inventory_levellist_item_actions_self.getComponents(lang));
            } else if (this.displayMode == DisplayMode.MODERATION) {
                lore.add(PbText.item("<i:lmb> &7" + this.t("Модерация", "Moderate", "Модерація")));
            } else {
                lore.add(PbText.item("<i:lmb> &7" + this.t("Играть", "Play", "Грати")));
                lore.add(PbText.item("<i:rmb> &7" + this.t("Подробнее", "Details", "Детальніше")));
            }
            meta.lore(lore);
        });
    }

    @Override
    protected void onPageDisplayed() {
        // Раскладка под фон menu_levels (РП):
        //   [X] .. [3D][2D][КОП][ДУЭЛЬ][360] .. [кнопка]
        //   витрина 9x3
        //   [+] ....... [<][стр][>]
        //   [сортировка x4][сложность x5]
        this.setItem(0, ItemUtils.modifyMeta(Icons.cancel(),
            m -> m.displayName(LangOptions.inventory_regularitems_close.getComponent(lang))), e -> e.getPlayer().closeInventory());

        int page = this.getCurrentPageNumber();
        int pages = this.getMaxPageNumber();
        this.setItem(4 * 9 + 3, ItemUtils.modifyMeta(Icons.Button.ARROW_LEFT.item(page > 1 ? Icons.ButtonState.NORMAL : Icons.ButtonState.DISABLED),
                m -> m.displayName(PbText.item("<i:arrow_left> &f" + this.t("Назад", "Previous", "Назад")))),
            page > 1 ? e -> {
                ru.sortix.parkourbeat.ui.UiSounds.page(e.getPlayer());
                this.showPage(page - 1);
            } : null);
        ItemStack pageItem = ItemUtils.modifyMeta(Icons.Button.PAGE.item(Icons.ButtonState.SELECTED), m -> {
            m.displayName(PbText.item("<i:book> &f" + this.t("Страница ", "Page ", "Сторінка ") + page + " / " + pages));
            m.lore(List.of(PbText.item("&7" + this.t("Уровней: ", "Levels: ", "Рівнів: ") + this.getTotalItemsCount())));
        });
        pageItem.setAmount(Math.max(1, Math.min(64, page)));
        this.setItem(4 * 9 + 4, pageItem, null);
        this.setItem(4 * 9 + 5, ItemUtils.modifyMeta(Icons.Button.ARROW_RIGHT.item(page < pages ? Icons.ButtonState.NORMAL : Icons.ButtonState.DISABLED),
                m -> m.displayName(PbText.item("<i:arrow_right> &f" + this.t("Дальше", "Next", "Далі")))),
            page < pages ? e -> {
                ru.sortix.parkourbeat.ui.UiSounds.page(e.getPlayer());
                this.showPage(page + 1);
            } : null);

        if (this.displayMode != DisplayMode.MODERATION) {
            this.updateSortButton();
            this.updateDifficultyFilter();
        }
        if (this.displayMode == DisplayMode.RANKED || this.displayMode == DisplayMode.UNRANKED) {
            this.updateModeTabs();
        }

        if (this.displayMode == DisplayMode.RANKED || this.displayMode == DisplayMode.UNRANKED) {
            this.setItem(8, ItemUtils.modifyMeta(Icons.Button.PENCIL.item(Icons.ButtonState.NORMAL), m -> m.displayName(
                LangOptions.inventory_edit_session_levels_name.getComponent(lang)
            )), e -> {
                e.getPlayer().closeInventory();
                e.getPlayer().performCommand("edit");
            });
            this.setItem(4 * 9 + 8, ItemUtils.modifyMeta(Icons.Button.PLUS.item(Icons.ButtonState.NORMAL), m -> m.displayName(
                LangOptions.inventory_levellist_displaymode_self_createlevel.getComponent(lang))),
                e -> CreateLevelMenu.startCreating(plugin, viewer, lang));
        } else if (this.displayMode == DisplayMode.MODERATION) {
            this.setItem(8, ItemUtils.modifyMeta(Icons.Button.BOOK.item(Icons.ButtonState.NORMAL), m -> {
                m.displayName(LangOptions.inventory_levellist_feedback_name.getComponent(lang));
                m.lore(LangOptions.inventory_levellist_feedback_lore.getComponents(lang));
            }), e -> new RatingFeedbackMenu(plugin, lang).open(e.getPlayer()));

            int resetRequests = this.plugin.get(
                ru.sortix.parkourbeat.stats.StatResetRequestManager.class).getPendingCount();
            this.setItem(4 * 9 + 8, ItemUtils.modifyMeta(Icons.Button.WARN.item(
                resetRequests > 0 ? Icons.ButtonState.SELECTED : Icons.ButtonState.NORMAL), m -> {
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
            this.setItem(8, ItemUtils.modifyMeta(Icons.Button.ARROW_LEFT.item(Icons.ButtonState.NORMAL), m -> m.displayName(
                LangOptions.inventory_levellist_displaymode_self_backtoplay.getComponent(lang)
            )), e -> {
                e.getPlayer().closeInventory();
                e.getPlayer().performCommand("play");
            });
            this.setItem(4 * 9 + 8, ItemUtils.modifyMeta(Icons.Button.PLUS.item(Icons.ButtonState.NORMAL),
                m -> m.displayName(LangOptions.inventory_levellist_displaymode_self_createlevel.getComponent(lang))),
                e -> CreateLevelMenu.startCreating(plugin, viewer, lang));
        }
    }

    /** Вкладки режимов: 3D (по умолчанию), 2D, Копатель, Дуэль, 360. */
    private void updateModeTabs() {
        ru.sortix.parkourbeat.twod.LevelMode[] modes = {
            ru.sortix.parkourbeat.twod.LevelMode.THREE_D, ru.sortix.parkourbeat.twod.LevelMode.TWO_D,
            ru.sortix.parkourbeat.twod.LevelMode.DIGGER, ru.sortix.parkourbeat.twod.LevelMode.DUEL,
            ru.sortix.parkourbeat.twod.LevelMode.THREE_SIXTY};
        Icons.Button[] icons = {Icons.Button.MODE_3D, Icons.Button.MODE_2D, Icons.Button.MODE_DIGGER,
            Icons.Button.MODE_DUEL, Icons.Button.MODE_360};
        for (int i = 0; i < modes.length; i++) {
            ru.sortix.parkourbeat.twod.LevelMode mode = modes[i];
            boolean on = mode == this.modeFilter;
            this.setItem(2 + i, ItemUtils.modifyMeta(icons[i].item(on ? Icons.ButtonState.SELECTED : Icons.ButtonState.NORMAL), m -> {
                m.displayName(PbText.item((on ? "<i:check> " : "") + "&f" + mode.getDisplayName().replaceAll("(?i)[&\u00A7][0-9a-fk-or]", "")));
                m.lore(List.of(PbText.item(on
                    ? "&a" + this.t("Показываются", "Shown", "Показуються")
                    : "<i:lmb> &7" + this.t("Показать эти уровни", "Show these levels", "Показати ці рівні"))));
            }), on ? null : e -> {
                this.modeFilter = mode;
                this.click(e.getPlayer());
                this.updateAllItems();
            });
        }
    }

    /** Фильтр сложностей: нажатие включает/выключает; ничего не выбрано - видны все. */
    private void updateDifficultyFilter() {
        for (int i = 0; i < FILTER_DIFFICULTIES.length; i++) {
            LevelDifficulty diff = FILTER_DIFFICULTIES[i];
            boolean on = this.difficultyFilter.contains(diff);
            this.setItem(5 * 9 + 4 + i, ItemUtils.modifyMeta(Icons.Button.difficulty(diff).item(
                on ? Icons.ButtonState.SELECTED : Icons.ButtonState.NORMAL), m -> {
                m.displayName(ru.sortix.parkourbeat.ui.Glyphs.glyph(ru.sortix.parkourbeat.ui.Glyphs.banner(diff, lang)));
                m.lore(List.of(PbText.item(on
                    ? "<i:check> &a" + this.t("Показываются", "Shown", "Показуються")
                    : "<i:lmb> &7" + this.t("Показать только их", "Show only these", "Показати лише їх"))));
            }), e -> {
                if (!this.difficultyFilter.remove(diff)) this.difficultyFilter.add(diff);
                this.click(e.getPlayer());
                this.updateAllItems();
            });
        }
    }

    private void click(@NonNull Player player) {
        ru.sortix.parkourbeat.ui.UiSounds.click(player);
    }

    private void updateSortButton() {
        // Четыре вкладки вместо одного переключателя по кругу: видно все варианты сразу.
        SortMode[] modes = SortMode.values();
        Icons.Button[] icons = {Icons.Button.SORT_ASC, Icons.Button.SORT_DESC, Icons.Button.SORT_POPULAR, Icons.Button.SORT_NEW};
        for (int i = 0; i < modes.length && i < icons.length; i++) {
            SortMode mode = modes[i];
            boolean on = mode == this.sortMode;
            this.setItem(5 * 9 + i, ItemUtils.modifyMeta(icons[i].item(on ? Icons.ButtonState.SELECTED : Icons.ButtonState.NORMAL), m -> {
                m.displayName(PbText.item((on ? "<i:check> " : "") + "&f" + mode.getDisplayName(this.lang)));
                m.lore(List.of(PbText.item(on
                    ? "&a" + this.t("Выбрано", "Selected", "Обрано")
                    : "<i:lmb> &7" + this.t("Сортировать так", "Sort this way", "Сортувати так"))));
            }), on ? null : e -> {
                this.sortMode = mode;
                this.click(e.getPlayer());
                this.updateAllItems();
            });
        }
    }

    /**
     * Переключатель "только 2D".
     * <p>
     * Кубик из Geometry Dash включает фильтр, столб пурпура возвращает весь список:
     * по предмету сразу видно, в каком состоянии список сейчас.
     */
    private void updateTwoDFilterButton() {
        // заменён вкладками режимов (updateModeTabs)
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
            // Как у HollowCube: ЛКМ - сразу играть, ПКМ (и Shift) - подробнее.
            if (event.isLeft() && !event.isShift()) {
                player.closeInventory();
                startPlaying(plugin, player, settings);
            } else {
                new LevelDetailsMenu(plugin, lang, settings, player, this.displayMode).open(player);
            }
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
        // Полноэкранный переход: экран закрывается кубиками (у каждой сложности свой
        // рисунок), пока он закрыт - создаётся забег и идёт телепорт, потом открывается.
        ru.sortix.parkourbeat.levels.LevelDifficulty difficulty = settings.getDifficulty();
        ru.sortix.parkourbeat.ui.LevelTransition.play(plugin, player, difficulty,
            ru.sortix.parkourbeat.ui.LevelTransition.subtitle(player, difficulty, settings.getDisplayName()),
            () -> {
                java.util.concurrent.CompletableFuture<Object> done = new java.util.concurrent.CompletableFuture<>();
                PlayActivity.createAsync(plugin, player, settings.getUniqueId(), false).thenAccept(playActivity -> {
                    if (playActivity == null) {
                        player.sendMessage(LangOptions.level_play_failload.getComponent(lang));
                        done.complete(null);
                        return;
                    }
                    plugin.get(ActivityManager.class).switchActivity(player, playActivity, playActivity.getLevel().getSpawn())
                        .thenAccept(success -> {
                            if (!success) player.sendMessage(LangOptions.level_play_failteleport.getComponent(lang));
                            done.complete(null);
                        });
                }).exceptionally(t -> {
                    done.complete(null);
                    return null;
                });
                return done;
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
