package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.UIHeads;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ru.sortix.parkourbeat.utils.text.PbText;
public class LevelDetailsMenu extends ParkourBeatInventory {

    public LevelDetailsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull GameSettings settings, @NonNull Player player) {
        this(plugin, lang, settings, player, LevelsListMenu.DisplayMode.RANKED);
    }

    public LevelDetailsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull GameSettings settings,
                            @NonNull Player player, @NonNull LevelsListMenu.DisplayMode sourceMode) {
        super(plugin, 6, lang, ru.sortix.parkourbeat.ui.Glyphs.menuTitle(ru.sortix.parkourbeat.ui.Glyphs.menuDetails(lang),
            ru.sortix.parkourbeat.ui.Icons.Icon.MAP.component().append(net.kyori.adventure.text.Component.text(" "))
                .append(LangOptions.inventory_leveldetails_title.getComponent(lang,
                    new Placeholders("%level%", PbText.keepColors(settings.getDisplayNameLegacy(false)))))));


        // 1) статистика — открывает топ прохождений уровня (п.8 ТЗ)
        this.fillHover(TOP_SLOTS, 0, 3, 3, 3, ItemUtils.modifyMeta(ru.sortix.parkourbeat.ui.Icons.empty(), meta -> {
            meta.displayName(LangOptions.inventory_leveldetails_stats_name.getComponent(lang));

            List<Component> statsLore = new ArrayList<>(
                LangOptions.inventory_leveldetails_stats_lore.getComponents(lang));

            ru.sortix.parkourbeat.rating.StatisticsManager statistics =
                plugin.get(ru.sortix.parkourbeat.rating.StatisticsManager.class);
            java.util.List<ru.sortix.parkourbeat.stats.RunResult> top =
                statistics.getLevelTop(settings.getUniqueId());

            statsLore.add(Component.empty());
            if (settings.getDifficulty() == ru.sortix.parkourbeat.levels.LevelDifficulty.N_A) {
                statsLore.addAll(Lang.lore(lang, "inventory.leveldetails.unranked"));
                statsLore.add(Component.empty());
            }
            if (top.isEmpty()) {
                statsLore.add(Lang.item(lang, "inventory.leveldetails.noruns"));
            } else {
                for (int i = 0; i < Math.min(3, top.size()); i++) {
                    ru.sortix.parkourbeat.stats.RunResult entry = top.get(i);
                    statsLore.add(ru.sortix.parkourbeat.stats.StatsFormat.text(
                        ru.sortix.parkourbeat.stats.StatsFormat.rankPrefix(i + 1)
                            + " &e" + ru.sortix.parkourbeat.stats.StatsFormat.percentRounded(entry.getProgressPercent())
                            + " &7- &f" + entry.getPlayerName()
                            + " &7- " + entry.getGrade().getFormatted()
                            + Lang.raw(lang, "inventory.leveldetails.accuracy",
                            "%accuracy%", ru.sortix.parkourbeat.stats.StatsFormat.percent(entry.getAccuracy()))
                            + (entry.isFullCombo() ? " &7[&b&lFC&7]" : "")));
                }
                statsLore.add(Lang.item(lang, "inventory.leveldetails.total",
                    "%count%", String.valueOf(top.size())));
            }
            statsLore.add(Component.empty());
            statsLore.add(Lang.item(lang, "inventory.leveldetails.opentop"));

            meta.lore(statsLore);
        }), event -> new LevelTopMenu(plugin, lang, settings, player).open(player));

        // 2) играть
        this.fillHover(PLAY_SLOTS, 0, 0, 8, 1, ItemUtils.modifyMeta(ru.sortix.parkourbeat.ui.Icons.empty(), meta -> {
            String trackName = settings.getMusicTrack() != null ? settings.getMusicTrack().getName() : Lang.raw(lang, "level.track.none");

            List<Component> lore = new ArrayList<>(LangOptions.inventory_leveldetails_play_lore.getComponents(lang,
                new Placeholders("%track%", trackName),
                new Placeholders("%id%", String.valueOf(settings.getUniqueNumber())),
                new Placeholders("%stars%", String.valueOf(settings.getPlayerRatings().size())),
                new Placeholders("%difficulty%", settings.getDifficulty().getDisplayName()),
                new Placeholders("%type%", LevelsListMenu.typeValue(settings)),
                new Placeholders("%author%", settings.getOwnerName()),
                new Placeholders("%date%", new SimpleDateFormat("yyyy.MM.dd").format(new Date(settings.getCreatedAtMills())))
            ));

            // Вставляем каждого соредактора отдельной строкой в лор без переносов \n
            int insertIdx = -1;
            for (int i = 0; i < lore.size(); i++) {
                String plain = PlainComponentSerializer.plain().serialize(lore.get(i));
                if (plain.contains(settings.getOwnerName())) {
                    insertIdx = i + 1;
                    break;
                }
            }

            if (insertIdx != -1) {
                for (String coEditorName : settings.getCoEditors().values()) {
                    if (coEditorName != null && !coEditorName.isEmpty() && !coEditorName.equals(settings.getOwnerName())) {
                        lore.add(insertIdx++, PbText.of("&7 • &6" + coEditorName));
                    }
                }
            }

            if (settings.isHardcoreDifficulty()) {
                lore.add(Lang.item(lang, "level.hardness.line",
                    "%color%", ru.sortix.parkourbeat.levels.settings.GameSettings
                        .getDifficultyColorPrefix(settings.getDifficultyMultiplier()),
                    "%value%", ru.sortix.parkourbeat.levels.settings.GameSettings
                        .formatDifficultyColored(settings.getDifficultyMultiplier())));
                lore.add(Lang.item(lang, "level.hardness.pp",
                    "%multiplier%", formatDifficultyMultiplier(
                        ru.sortix.parkourbeat.stats.PPCalculator
                            .getHardnessMultiplier(settings.getDifficultyMultiplier()))));
            }

            if (settings.isCustomTextures()) {
                lore.add(Lang.item(lang, "level.textures.custom"));
                if (settings.getTextureVersionRange() != null) {
                    lore.add(Lang.item(lang, "level.textures.versions",
                        "%versions%", settings.getTextureVersionRange().getLabel()));
                }
            }

            meta.displayName(LangOptions.inventory_leveldetails_play_name.getComponent(lang, new Placeholders("%unique_name%", settings.getUniqueName() == null ? "#" + settings.getUniqueNumber() : settings.getUniqueName())));
            meta.lore(lore);
        }), event -> {
            event.getPlayer().closeInventory();
            LevelsListMenu.startPlaying(plugin, player, settings);
        });

        // 3) Оценка сложности
        this.fillHover(RATE_SLOTS, 5, 3, 8, 3, ItemUtils.modifyMeta(ru.sortix.parkourbeat.ui.Icons.empty(), meta -> {
            meta.displayName(LangOptions.inventory_leveldetails_rate_name.getComponent(lang));
            meta.lore(LangOptions.inventory_leveldetails_rate_lore.getComponents(lang, new Placeholders("%difficulty%", settings.getDifficulty().getDisplayName())));
        }), event -> {
            new LevelDifficultyMenu(plugin, lang, settings, player).open(player);
        });

        // Кнопка назад
        this.setItem(6, 1, ItemUtils.modifyMeta(ru.sortix.parkourbeat.ui.Icons.Button.ARROW_LEFT.item(ru.sortix.parkourbeat.ui.Icons.ButtonState.NORMAL), meta -> {
            meta.displayName(LangOptions.inventory_regularitems_previous.getComponent(lang));
        }), event -> {
            // Возвращаемся в тот список, из которого пришли, а не всегда в RANKED.
            new LevelsListMenu(plugin, lang, sourceMode, player, player.getUniqueId()).open(player);
        });
    }

    private void drawBorders() {
        org.bukkit.inventory.ItemStack glass = ItemUtils.create(Material.BLACK_STAINED_GLASS_PANE, meta -> meta.displayName(Component.empty()));
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                if (i != 49) {
                    this.setItem(i, glass, null);
                }
            }
        }
    }

    @NonNull
    private static String formatDifficultyMultiplier(double value) {
        String formatted = String.format(java.util.Locale.ROOT, "%.2f", value);
        while (formatted.endsWith("0")) formatted = formatted.substring(0, formatted.length() - 1);
        if (formatted.endsWith(".")) formatted = formatted.substring(0, formatted.length() - 1);
        return formatted;
    }

    // ---- большие плитки (фон menu_details в РП): «Играть» 9x2, «Топ» и «Оценить» 4x1
    private static final int[] PLAY_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int[] TOP_SLOTS = {27, 28, 29, 30};
    private static final int[] RATE_SLOTS = {32, 33, 34, 35};

    private void fillHover(int[] slots, int c0, int r0, int c1, int r1, org.bukkit.inventory.ItemStack item,
                           java.util.function.Consumer<ru.sortix.parkourbeat.inventory.event.ClickEvent> action) {
        org.bukkit.inventory.ItemStack framed = ru.sortix.parkourbeat.ui.Glyphs.withHover(item, c0, r0, c1, r1);
        for (int slot : slots) this.setItem(slot, framed, action);
    }
}
