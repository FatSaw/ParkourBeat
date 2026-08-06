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
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LevelDetailsMenu extends ParkourBeatInventory {

    public LevelDetailsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull GameSettings settings, @NonNull Player player) {
        this(plugin, lang, settings, player, LevelsListMenu.DisplayMode.RANKED);
    }

    public LevelDetailsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull GameSettings settings,
                            @NonNull Player player, @NonNull LevelsListMenu.DisplayMode sourceMode) {
        super(plugin, 6, lang, LangOptions.inventory_leveldetails_title.getComponent(lang,
            new Placeholders("%level%", settings.getDisplayNameLegacy(false))));

        this.drawBorders();

        // 1) статистика — открывает топ прохождений уровня (п.8 ТЗ)
        this.setItem(3, 3, ItemUtils.create(Material.DIAMOND, meta -> {
            meta.displayName(LangOptions.inventory_leveldetails_stats_name.getComponent(lang));

            List<Component> statsLore = new ArrayList<>(
                LangOptions.inventory_leveldetails_stats_lore.getComponents(lang));

            ru.sortix.parkourbeat.rating.StatisticsManager statistics =
                plugin.get(ru.sortix.parkourbeat.rating.StatisticsManager.class);
            java.util.List<ru.sortix.parkourbeat.stats.RunResult> top =
                statistics.getLevelTop(settings.getUniqueId());

            statsLore.add(Component.empty());
            if (settings.getDifficulty() == ru.sortix.parkourbeat.levels.LevelDifficulty.N_A) {
                statsLore.add(ru.sortix.parkourbeat.stats.StatsFormat.text("&7&lUNRANKED"));
                statsLore.add(ru.sortix.parkourbeat.stats.StatsFormat.text("&7Результаты не идут в рейтинг"));
                statsLore.add(Component.empty());
            }
            if (top.isEmpty()) {
                statsLore.add(ru.sortix.parkourbeat.stats.StatsFormat.text("&fПрохождений пока нет"));
            } else {
                for (int i = 0; i < Math.min(3, top.size()); i++) {
                    ru.sortix.parkourbeat.stats.RunResult entry = top.get(i);
                    statsLore.add(ru.sortix.parkourbeat.stats.StatsFormat.text(
                        ru.sortix.parkourbeat.stats.StatsFormat.rankPrefix(i + 1)
                            + " &e" + ru.sortix.parkourbeat.stats.StatsFormat.percentRounded(entry.getProgressPercent())
                            + " &7- &f" + entry.getPlayerName()
                            + " &7- " + entry.getGrade().getFormatted()
                            + "&7, точность - " + ru.sortix.parkourbeat.stats.StatsFormat.percent(entry.getAccuracy())
                            + (entry.isFullCombo() ? " &7[&b&lFC&7]" : "")));
                }
                statsLore.add(ru.sortix.parkourbeat.stats.StatsFormat.text(
                    "&fВсего результатов: &e" + top.size()));
            }
            statsLore.add(Component.empty());
            statsLore.add(ru.sortix.parkourbeat.stats.StatsFormat.text("&fНажмите, чтобы открыть полный топ"));

            meta.lore(statsLore);
        }), event -> new LevelTopMenu(plugin, lang, settings, player).open(player));

        // 2) играть
        this.setItem(3, 5, ItemUtils.modifyMeta(UIHeads.PLAY.clone(), meta -> {
            String trackName = settings.getMusicTrack() != null ? settings.getMusicTrack().getName() : "Отсутствует";

            List<Component> lore = new ArrayList<>(LangOptions.inventory_leveldetails_play_lore.getComponents(lang,
                new Placeholders("%track%", trackName),
                new Placeholders("%id%", String.valueOf(settings.getUniqueNumber())),
                new Placeholders("%stars%", String.valueOf(settings.getPlayerRatings().size())),
                new Placeholders("%difficulty%", settings.getDifficulty().getDisplayName()),
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
                        lore.add(insertIdx++, LegacyComponentSerializer.legacyAmpersand().deserialize("&7 • &6" + coEditorName));
                    }
                }
            }

            meta.displayName(LangOptions.inventory_leveldetails_play_name.getComponent(lang, new Placeholders("%unique_name%", settings.getUniqueName() == null ? "#" + settings.getUniqueNumber() : settings.getUniqueName())));
            meta.lore(lore);
        }), event -> {
            event.getPlayer().closeInventory();
            LevelsListMenu.startPlaying(plugin, player, settings);
        });

        // 3) Оценка сложности
        this.setItem(3, 7, ItemUtils.create(Material.NETHER_STAR, meta -> {
            meta.displayName(LangOptions.inventory_leveldetails_rate_name.getComponent(lang));
            meta.lore(LangOptions.inventory_leveldetails_rate_lore.getComponents(lang, new Placeholders("%difficulty%", settings.getDifficulty().getDisplayName())));
        }), event -> {
            new LevelDifficultyMenu(plugin, lang, settings, player).open(player);
        });

        // Кнопка назад
        this.setItem(6, 5, ItemUtils.modifyMeta(UIHeads.ARROW_LEFT.clone(), meta -> {
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
}
