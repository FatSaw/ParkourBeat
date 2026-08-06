package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.UIHeads;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.stats.PlayerProfile;
import ru.sortix.parkourbeat.stats.ProfileSummary;
import ru.sortix.parkourbeat.stats.RunResult;
import ru.sortix.parkourbeat.stats.StatsFormat;
import ru.sortix.parkourbeat.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * «История» игрока (п.6 ТЗ): сверху карточка аккаунта, снизу — лента последних
 * {@link StatisticsManager#HISTORY_SIZE} попыток.
 * <p>
 * Лента живёт в таблице {@code runs} и в памяти не держится, поэтому читается
 * асинхронно: меню открывается сразу с плашкой «Загрузка…», а предметы
 * досыпаются, когда база ответит.
 */
public class PlayerHistoryMenu extends ParkourBeatInventory {
    private static final int[] HISTORY_SLOTS = {
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int CARD_SLOT = 4;
    private static final int LOADING_SLOT = 31;

    private final @NonNull OfflinePlayer target;

    public PlayerHistoryMenu(@NonNull ParkourBeat plugin, String lang,
                             @NonNull Player viewer, @NonNull OfflinePlayer target) {
        super(plugin, 6, lang, StatsFormat.text("&7История: &f" + StatsFormat.safeName(target.getName())));
        this.target = target;
        this.render(viewer);
    }

    private void render(@NonNull Player viewer) {
        this.drawBorders();

        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        PlayerProfile profile = statistics.getProfile(this.target);
        ProfileSummary summary = statistics.summarize(profile);

        this.setItem(CARD_SLOT, this.buildAccountCard(summary), null);

        this.setItem(6, 5, ItemUtils.modifyMeta(UIHeads.ARROW_LEFT.clone(), meta ->
                meta.displayName(StatsFormat.text("&7Назад"))),
            event -> new PlayerStatisticsMenu(this.plugin, this.lang, viewer, this.target).open(viewer));

        this.setItem(LOADING_SLOT, ItemUtils.create(Material.CLOCK, meta ->
            meta.displayName(StatsFormat.text("&7Загрузка истории…"))), null);

        statistics.loadRecentRunsAsync(profile.getPlayerId(), StatisticsManager.HISTORY_SIZE,
            this::displayHistory);
    }

    /** Вызывается уже в основном потоке, когда база отдала ленту. */
    private void displayHistory(@NonNull List<RunResult> runs) {
        for (int slot : HISTORY_SLOTS) {
            this.setItem(slot, null, null);
        }

        if (runs.isEmpty()) {
            this.setItem(LOADING_SLOT, ItemUtils.create(Material.BARRIER, meta ->
                meta.displayName(StatsFormat.text("&cПопыток пока нет"))), null);
            return;
        }

        int index = 0;
        for (RunResult run : runs) {
            if (index >= HISTORY_SLOTS.length) break;
            this.setItem(HISTORY_SLOTS[index++], this.buildRunItem(run), null);
        }
    }

    @NonNull
    private ItemStack buildRunItem(@NonNull RunResult run) {
        // Успешные — зелёные, провальные — красные. Прогресс виден с одного взгляда.
        Material material = run.isCompleted()
            ? Material.LIME_STAINED_GLASS_PANE
            : Material.RED_STAINED_GLASS_PANE;

        GameSettings settings = this.plugin.get(StatisticsManager.class).getLevelSettings(run.getLevelId());
        String levelName = settings != null ? settings.getDisplayNameLegacy(false) : run.getLevelName();

        return ItemUtils.create(material, meta -> {
            meta.displayName(StatsFormat.text("&f" + levelName
                + (settings == null ? " &7(уровень удалён)" : "")));

            List<Component> lore = new ArrayList<>();
            lore.add(StatsFormat.text("&7" + StatsFormat.relativeDateTime(run.getTimestamp())
                + " &7- &e" + StatsFormat.percentRounded(run.getProgressPercent())
                + " &7- " + run.getGrade().getFormatted()
                + " &7- &7точность " + StatsFormat.percent(run.getAccuracy())));
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&7Сложность: " + run.getDifficulty().getDisplayName()));
            lore.add(StatsFormat.text("&7Очков: &f" + StatsFormat.number(run.getScore())
                + " &7(без множителя: " + StatsFormat.number(run.getRawScore()) + ")"));
            lore.add(StatsFormat.text("&7Комбо: &fx" + run.getMaxCombo()));
            lore.add(StatsFormat.text("&7+300: &b" + run.getCount300()
                + " &7+100: &e" + run.getCount100()
                + " &7+50: &c" + run.getCount50()
                + " &7промахов: &f" + run.getMissCount()));
            lore.add(StatsFormat.text("&7Время: &f" + TimeUtils.formatTimecode(run.getTimeMillis())));
            lore.add(StatsFormat.text("&7Модификаторы: &f" + run.getModifiersDisplay()
                + " &7(x" + String.format(java.util.Locale.ROOT, "%.2f", run.getMultiplier()) + ")"));
            if (run.isFullCombo()) lore.add(StatsFormat.text("&b&lFULL COMBO"));
            meta.lore(lore);
        });
    }

    @NonNull
    private ItemStack buildAccountCard(@NonNull ProfileSummary summary) {
        return ItemUtils.create(Material.BOOK, meta -> {
            meta.displayName(StatsFormat.text("&2Карточка аккаунта"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&7Дата создания: &f" + StatsFormat.date(summary.getFirstJoinAtMillis())));
            lore.add(StatsFormat.text("&7Время на ParkourBeat: &f"
                + StatsFormat.duration(summary.getPlaytimeMillis())));
            lore.add(StatsFormat.text("&7Своих уровней: &f" + summary.getOwnLevelsCount()));
            lore.add(StatsFormat.text("&7Пройдено уровней: &f" + summary.getCompletedLevelsCount()));
            lore.add(StatsFormat.text("&7Всего попыток: &f" + StatsFormat.number(summary.getTotalAttempts())));
            lore.add(StatsFormat.text("&7Оценки: " + PlayerStatisticsMenu.gradesLine(summary)));
            meta.lore(lore);
        });
    }

    private void drawBorders() {
        ItemStack glass = ItemUtils.create(Material.BLACK_STAINED_GLASS_PANE,
            meta -> meta.displayName(Component.empty()));
        for (int slot = 0; slot < 54; slot++) {
            if (slot == CARD_SLOT) continue;
            if (isHistorySlot(slot)) continue;
            this.setItem(slot, glass, null);
        }
    }

    private static boolean isHistorySlot(int slot) {
        for (int historySlot : HISTORY_SLOTS) {
            if (historySlot == slot) return true;
        }
        return false;
    }
}
