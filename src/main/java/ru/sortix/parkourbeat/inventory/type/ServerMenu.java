package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.RegularItems;
import ru.sortix.parkourbeat.inventory.UIHeads;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.utils.lang.Lang;

import java.util.ArrayList;
import java.util.List;

import ru.sortix.parkourbeat.utils.text.PbText;
public class ServerMenu extends ParkourBeatInventory {
    /** Номер уровня-туториала. Публичный: этот же уровень использует и сам туториал. */
    public static final int TUTORIAL_LEVEL_ID = 267;

    public ServerMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer) {
        super(plugin, 6, lang, Lang.item(lang, "inventory.servermenu.title"));
        this.render(viewer);
    }

    /**
     * Разбор строки для предметов меню.
     * <p>
     * Остаётся публичным: этим помощником пользуется добрая половина меню плагина,
     * и трогать их все ради одного переезда строк смысла нет.
     */
    public static Component text(@NonNull String legacy) {
        return PbText.of(legacy).decoration(TextDecoration.ITALIC, false);
    }

    private void render(@NonNull Player viewer) {
        this.fillBorder();

        this.setItem(2, 2, ItemUtils.modifyMeta(UIHeads.PLAY.clone(), meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.servermenu.play.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.play.lore"));
        }), event -> new LevelsListMenu(this.plugin, this.lang,
            LevelsListMenu.DisplayMode.RANKED, event.getPlayer(), event.getPlayer().getUniqueId()).open(event.getPlayer()));

        this.setItem(2, 3, ItemUtils.create(Material.WRITABLE_BOOK, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.servermenu.mylevels.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.mylevels.lore"));
        }), event -> new LevelsListMenu(this.plugin, this.lang,
            LevelsListMenu.DisplayMode.SELF, event.getPlayer(), event.getPlayer().getUniqueId()).open(event.getPlayer()));

        this.setItem(2, 4, ItemUtils.create(Material.EMERALD, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.servermenu.globalstats.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.globalstats.lore"));
        }), event -> new GlobalStatisticsMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));

        this.setItem(2, 5, ItemUtils.create(Material.PLAYER_HEAD, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.servermenu.mystats.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.mystats.lore"));
        }), event -> new PlayerStatisticsMenu(this.plugin, this.lang,
            event.getPlayer(), event.getPlayer().getUniqueId(), event.getPlayer().getName()).open(event.getPlayer()));

        this.setItem(2, 6, ItemUtils.create(Material.FIRE_CHARGE, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.servermenu.modifiers.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.modifiers.lore"));
        }), event -> new ModifiersMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));

        this.setItem(2, 7, ItemUtils.modifyMeta(UIHeads.RECORDS.clone(), meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.servermenu.records.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.records.lore"));
        }), event -> new RecordsMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));

        this.setItem(2, 8, ItemUtils.create(Material.COMPARATOR, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.servermenu.settings.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.settings.lore"));
        }), event -> new SettingsMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));

        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        boolean canCreate = statistics.getDisplayRank(viewer.getUniqueId()) > 0;
        this.setItem(4, 2, ItemUtils.create(canCreate ? Material.GRASS_BLOCK : Material.BARRIER, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.servermenu.createlevel.name"));
            meta.lore(Lang.lore(this.lang, canCreate
                ? "inventory.servermenu.createlevel.lore"
                : "inventory.servermenu.createlevel.lore_locked"));
        }), event -> CreateLevelMenu.startCreating(this.plugin, event.getPlayer(), this.lang));

        // Прямо под «Записями»: язык - не потайная настройка, его должно быть
        // видно с главного экрана.
        this.setItem(3, 7, ItemUtils.modifyMeta(UIHeads.LANGUAGE.clone(), meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.servermenu.language.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.language.lore",
                "%language%", ru.sortix.parkourbeat.utils.lang.PlayerLang.displayName(this.lang)));
        }), event -> new LanguageMenu(this.plugin, this.lang, event.getPlayer())
            .open(event.getPlayer()));

        this.setItem(3, 3, ItemUtils.modifyMeta(
            ru.sortix.parkourbeat.stats.StatsFormat.playerHead(viewer.getUniqueId(), viewer.getName()),
            meta -> {
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

                meta.displayName(Lang.item(this.lang, "inventory.servermenu.friends.name"));
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
            }), event -> new FriendsMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));

        this.setItem(4, 4, ItemUtils.create(Material.GOLDEN_APPLE, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.servermenu.howtoplay.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.howtoplay.lore"));
        }), null);

        this.setItem(3, 5, ItemUtils.create(Material.SNOWBALL, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.servermenu.tutorial.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.tutorial.lore"));
        }), event -> {
            Player player = event.getPlayer();
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

            // Туториал включится сам при входе на этот уровень - отдельного
            // запуска не нужно.
            LevelsListMenu.startPlaying(this.plugin, player, tutorial);
        });

        this.setItem(4, 5, ItemUtils.create(Material.NOTE_BLOCK, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.servermenu.music.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.music.lore"));
        }), null);

        this.setItem(4, 6, ItemUtils.create(Material.EXPERIENCE_BOTTLE, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.servermenu.rating.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.rating.lore"));
        }), null);

        this.setItem(4, 8, ItemUtils.create(Material.PAPER, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.servermenu.commands.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.commands.lore"));
        }), null);

        this.setItem(5, 5, ItemUtils.create(Material.WHITE_BED, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.servermenu.afk.name"));
            meta.lore(Lang.lore(this.lang, "inventory.servermenu.afk.lore"));
        }), event -> new AfkMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));

        this.setItem(6, 5, RegularItems.closeInventory(this.lang),
            event -> event.getPlayer().closeInventory());
    }
}
