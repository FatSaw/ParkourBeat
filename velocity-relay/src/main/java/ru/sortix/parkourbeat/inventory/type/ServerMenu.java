package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.ui.Glyphs;
import ru.sortix.parkourbeat.ui.Icons;
import ru.sortix.parkourbeat.ui.Icons.Button;
import ru.sortix.parkourbeat.ui.Icons.ButtonState;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ГЛАВНОЕ МЕНЮ.
 * <p>
 * Фон - картинка из РП (menu_main): большая плитка «Играть», две широкие плитки
 * «Мои уровни» / «Новый уровень», ряды иконок «Профиль» и «Прочее». Плитки
 * нарисованы на фоне, а в их слотах лежат прозрачные предметы - они дают
 * описание при наведении и ловят клик. Иконки - кнопки-предметы из РП.
 * <pre>
 *   [ ИГРАТЬ  4x2 ][ МОИ УРОВНИ   5x1 ]
 *   [            ][ НОВЫЙ УРОВЕНЬ 5x1 ]
 *   - - - - - - - - - - - - - - - - - -
 *   ПРОФИЛЬ  [стат][топ][записи][друзья][модиф][рейтинг]
 *   ПРОЧЕЕ   [настр][язык][музыка][обучение][как играть][команды]
 *   [X]                                              [AFK]
 * </pre>
 * Все тексты - те же ключи lang.yml, что и раньше.
 */
public class ServerMenu extends ParkourBeatInventory {
    public static final int TUTORIAL_LEVEL_ID = 267;

    private static final int[] PLAY_SLOTS = {0, 1, 2, 3, 9, 10, 11, 12};
    private static final int[] MINE_SLOTS = {4, 5, 6, 7, 8};
    private static final int[] NEW_SLOTS = {13, 14, 15, 16, 17};

    public ServerMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer) {
        super(plugin, 6, lang, Glyphs.menuTitle(Glyphs.menuMain(lang),
            Icons.Icon.CUBE.component().append(Component.text(" "))
                .append(Lang.item(lang, "inventory.servermenu.title"))));
        this.render(viewer);
    }

    public static Component text(@NonNull String legacy) {
        return PbText.of(legacy).decoration(TextDecoration.ITALIC, false);
    }

    @NonNull
    private Component name(@NonNull Icons.Icon icon, @NonNull String key) {
        return icon.component().append(Component.text(" ")).append(Lang.item(this.lang, key));
    }

    @NonNull
    private ItemStack button(@NonNull Button button, @NonNull Icons.Icon icon, @NonNull String key) {
        return ItemUtils.modifyMeta(button.item(ButtonState.NORMAL), meta -> {
            meta.displayName(this.name(icon, key + ".name"));
            meta.lore(Lang.lore(this.lang, key + ".lore"));
        });
    }

    /** Плитка из нескольких слотов: везде один предмет и рамка вокруг всей плитки при наведении. */
    private void fill(int[] slots, int c0, int r0, int c1, int r1, @NonNull ItemStack item,
                      Consumer<ru.sortix.parkourbeat.inventory.event.ClickEvent> action) {
        ItemStack framed = Glyphs.withHover(item, c0, r0, c1, r1);
        for (int slot : slots) this.setItem(slot, framed, action);
    }

    private void render(@NonNull Player viewer) {
        // ---- большие плитки
        this.fill(PLAY_SLOTS, 0, 0, 3, 1, ItemUtils.modifyMeta(Icons.empty(), meta -> {
            meta.displayName(this.name(Icons.Icon.PLAY, "inventory.servermenu.play.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.play.lore"));
        }), event -> new LevelsListMenu(this.plugin, this.lang,
            LevelsListMenu.DisplayMode.RANKED, event.getPlayer(), event.getPlayer().getUniqueId()).open(event.getPlayer()));

        this.fill(MINE_SLOTS, 4, 0, 8, 0, ItemUtils.modifyMeta(Icons.empty(), meta -> {
            meta.displayName(this.name(Icons.Icon.PENCIL, "inventory.servermenu.mylevels.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.mylevels.lore"));
        }), event -> new LevelsListMenu(this.plugin, this.lang,
            LevelsListMenu.DisplayMode.SELF, event.getPlayer(), event.getPlayer().getUniqueId()).open(event.getPlayer()));

        boolean canCreate = this.plugin.get(StatisticsManager.class).getDisplayRank(viewer.getUniqueId()) > 0;
        this.fill(NEW_SLOTS, 4, 1, 8, 1, ItemUtils.modifyMeta(Icons.empty(), meta -> {
            meta.displayName(this.name(canCreate ? Icons.Icon.PLUS : Icons.Icon.LOCK, "inventory.servermenu.createlevel.name"));
            meta.lore(Lang.lore(this.lang, canCreate
                ? "inventory.servermenu.createlevel.lore"
                : "inventory.servermenu.createlevel.lore_locked"));
        }), event -> CreateLevelMenu.startCreating(this.plugin, event.getPlayer(), this.lang));

        // ---- ПРОФИЛЬ
        int r3 = 3 * 9 + 3;
        this.setItem(r3, this.button(Button.MM_MYSTATS, Icons.Icon.TROPHY, "inventory.servermenu.mystats"),
            event -> new PlayerStatisticsMenu(this.plugin, this.lang,
                event.getPlayer(), event.getPlayer().getUniqueId(), event.getPlayer().getName()).open(event.getPlayer()));
        this.setItem(r3 + 1, this.button(Button.MM_GLOBALSTATS, Icons.Icon.GLOBE, "inventory.servermenu.globalstats"),
            event -> new GlobalStatisticsMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));
        this.setItem(r3 + 2, this.button(Button.MM_REPLAYS, Icons.Icon.REC, "inventory.servermenu.records"),
            event -> new RecordsMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));
        this.setItem(r3 + 3, this.friendsItem(viewer),
            event -> new FriendsMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));
        this.setItem(r3 + 4, this.button(Button.MM_MODIFIERS, Icons.Icon.GEAR, "inventory.servermenu.modifiers"),
            event -> new ModifiersMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));
        this.setItem(r3 + 5, this.button(Button.MM_RATING, Icons.Icon.STAR, "inventory.servermenu.rating"), null);

        // ---- ПРОЧЕЕ
        int r4 = 4 * 9 + 3;
        this.setItem(r4, this.button(Button.MM_SETTINGS, Icons.Icon.GEAR, "inventory.servermenu.settings"),
            event -> new SettingsMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));
        this.setItem(r4 + 1, ItemUtils.modifyMeta(Button.MM_LANGUAGE.item(ButtonState.NORMAL), meta -> {
            meta.displayName(this.name(Icons.Icon.GLOBE, "inventory.servermenu.language.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.language.lore",
                "%language%", ru.sortix.parkourbeat.utils.lang.PlayerLang.displayName(this.lang)));
        }), event -> new LanguageMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));
        this.setItem(r4 + 2, this.button(Button.MM_MUSIC, Icons.Icon.MUSIC, "inventory.servermenu.music"), null);
        this.setItem(r4 + 3, this.button(Button.MM_TUTORIAL, Icons.Icon.BOOK, "inventory.servermenu.tutorial"),
            event -> this.startTutorial(event.getPlayer()));
        this.setItem(r4 + 4, this.button(Button.MM_HOWTO, Icons.Icon.INFO, "inventory.servermenu.howtoplay"), null);
        this.setItem(r4 + 5, this.button(Button.MM_COMMANDS, Icons.Icon.CHAT, "inventory.servermenu.commands"), null);

        // ---- низ
        this.setItem(5 * 9, ItemUtils.modifyMeta(Icons.cancel(), meta ->
                meta.displayName(ru.sortix.parkourbeat.utils.lang.LangOptions.inventory_regularitems_close.getComponent(this.lang))),
            event -> event.getPlayer().closeInventory());
        this.setItem(5 * 9 + 8, this.button(Button.MM_AFK, Icons.Icon.CLOCK, "inventory.servermenu.afk"),
            event -> new AfkMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));
    }

    @NonNull
    private ItemStack friendsItem(@NonNull Player viewer) {
        return ItemUtils.modifyMeta(Button.MM_FRIENDS.item(ButtonState.NORMAL), meta -> {
            int online = 0;
            int total = 0;
            int requests = 0;
            try {
                ru.sortix.parkourbeat.player.friends.FriendsManager friends =
                    this.plugin.get(ru.sortix.parkourbeat.player.friends.FriendsManager.class);
                online = friends.getOnlineFriendsCount(viewer.getUniqueId());
                total = friends.getProfile(viewer).getFriendsCount();
                requests = friends.getIncomingRequestsCount(viewer.getUniqueId());
            } catch (Exception ignored) {
            }

            meta.displayName(this.name(Icons.Icon.FRIEND, "inventory.servermenu.friends.name"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Lang.item(this.lang, "inventory.servermenu.friends.online",
                "%online%", String.valueOf(online),
                "%total%", String.valueOf(total)));
            if (requests > 0) {
                lore.add(Lang.item(this.lang, "inventory.servermenu.friends.requests",
                    "%requests%", String.valueOf(requests)));
            }
            lore.addAll(Lang.lore(this.lang, "inventory.servermenu.friends.lore"));
            meta.lore(lore);
        });
    }

    private void startTutorial(@NonNull Player player) {
        player.closeInventory();

        ru.sortix.parkourbeat.levels.settings.GameSettings tutorial = null;
        for (ru.sortix.parkourbeat.levels.settings.GameSettings settings
            : this.plugin.get(ru.sortix.parkourbeat.levels.LevelsManager.class).getAvailableLevelsSettings()) {
            if (settings.getUniqueNumber() != TUTORIAL_LEVEL_ID) continue;
            tutorial = settings;
            break;
        }

        if (tutorial == null) {
            player.sendMessage(Lang.text(this.lang, "inventory.servermenu.tutorial.unavailable"));
            return;
        }

        // Туториал включится сам при входе на этот уровень - отдельного запуска не нужно.
        LevelsListMenu.startPlaying(this.plugin, player, tutorial);
    }
}
