package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.UIHeads;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.stats.RunResult;
import ru.sortix.parkourbeat.stats.StatsFormat;
import ru.sortix.parkourbeat.utils.TimeUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Топ прохождений конкретного уровня — кнопка «Статистика» в меню уровня (п.8 ТЗ).
 * <p>
 * Каждая строка — голова игрока, в лоре развёрнутые данные того прохождения.
 * Сверху отдельно выделен держатель глобального рекорда, внизу — строка
 * «Ваш результат» с местом смотрящего, даже если он вне топа.
 * <p>
 * Для N/A-уровня наверху вместо держателя рекорда висит плашка UNRANKED: сами
 * результаты показываем (п.0 ТЗ), но в рейтинг они не идут.
 */
public class LevelTopMenu extends PaginatedMenu<ParkourBeat, RunResult> {
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int HEADER_SLOT = 4;

    private final @NonNull GameSettings settings;
    private final @NonNull Player viewer;

    private List<RunResult> currentTop = new ArrayList<>();

    public LevelTopMenu(@NonNull ParkourBeat plugin, String lang,
                        @NonNull GameSettings settings, @NonNull Player viewer) {
        super(plugin, 6, lang,
            StatsFormat.text("&7Топ: &f" + settings.getDisplayNameLegacy(false)), CONTENT_SLOTS);
        this.settings = settings;
        this.viewer = viewer;
        this.updateAllItems();
    }

    @Override
    @NonNull
    protected Collection<RunResult> getAllItems() {
        this.currentTop = this.plugin.get(StatisticsManager.class).getLevelTop(this.settings.getUniqueId());
        return this.currentTop;
    }

    @Override
    @NonNull
    protected ItemStack createItemDisplay(@NonNull RunResult record) {
        int position = this.currentTop.indexOf(record) + 1;
        return this.buildEntry(record, position, false);
    }

    /**
     * Строка топа ровно в духе ТЗ:
     * <pre>&amp;6#1 &amp;e100% &amp;8- &amp;7iMirAtorG &amp;8- &amp;e&amp;lSS&amp;8, точность - 99.98% &amp;8[&amp;b&amp;lFC&amp;8]</pre>
     */
    @NonNull
    private ItemStack buildEntry(@NonNull RunResult record, int position, boolean isViewerPlate) {
        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        ItemStack head = StatsFormat.playerHead(record.getPlayerId(), record.getPlayerName());

        return ItemUtils.modifyMeta(head, meta -> {
            meta.displayName(StatsFormat.text(
                StatsFormat.rankPrefix(position)
                    + " &e" + StatsFormat.percentRounded(record.getProgressPercent())
                    + " &7- &f" + record.getPlayerName()
                    + " &7- " + record.getGrade().getFormatted()
                    + "&7, точность - " + StatsFormat.percent(record.getAccuracy())
                    + (record.isFullCombo() ? " &7[&b&lFC&7]" : "")));

            List<Component> lore = new ArrayList<>();
            if (isViewerPlate) {
                lore.add(StatsFormat.text("&fВаш результат на этом уровне"));
            }
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&7Очков: &f" + StatsFormat.number(record.getScore())
                + " &7(без множителя: " + StatsFormat.number(record.getRawScore()) + ")"));
            lore.add(StatsFormat.text("&7Комбо: &fx" + record.getMaxCombo()));
            lore.add(StatsFormat.text("&7+300: &b" + record.getCount300()
                + " &7+100: &e" + record.getCount100()
                + " &7+50: &c" + record.getCount50()
                + " &7промахов: &f" + record.getMissCount()));
            lore.add(StatsFormat.text("&7Время: &f" + TimeUtils.formatTimecode(record.getTimeMillis())));
            lore.add(StatsFormat.text("&7Модификаторы: &f" + record.getModifiersDisplay()
                + " &7(x" + String.format(java.util.Locale.ROOT, "%.2f", record.getMultiplier()) + ")"));
            if (this.isRanked()) {
                lore.add(StatsFormat.text("&7PP: &d" + StatsFormat.pp(statistics.getRecordPP(record))));
            }
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&7" + (record.isCompleted() ? "Пройден " : "Попытка ")
                + StatsFormat.dateTime(record.getTimestamp())));
            meta.lore(lore);
        });
    }

    @Override
    protected void onPageDisplayed() {
        ItemStack glass = ItemUtils.create(Material.BLACK_STAINED_GLASS_PANE,
            meta -> meta.displayName(Component.empty()));
        for (int slot = 0; slot < 54; slot++) {
            if (isContentSlot(slot)) continue;
            this.setItem(slot, glass, null);
        }

        this.drawHeader();
        this.drawViewerResult();

        this.setPreviousPageItem(6, 4);
        this.setItem(6, 5, ItemUtils.modifyMeta(UIHeads.ARROW_LEFT.clone(), meta ->
                meta.displayName(StatsFormat.text("&7Назад"))),
            event -> new LevelDetailsMenu(this.plugin, this.lang, this.settings, this.viewer).open(this.viewer));
        this.setNextPageItem(6, 6);

        if (this.currentTop.isEmpty()) {
            this.setItem(22, ItemUtils.create(Material.BARRIER, meta -> {
                meta.displayName(StatsFormat.text("&cЭтот уровень ещё никто не проходил"));
                meta.lore(java.util.Collections.singletonList(
                    StatsFormat.text("&fСтаньте первым!")));
            }), null);
        }
    }

    /** Держатель глобального рекорда — крупно и со звёздочкой. Для N/A — плашка UNRANKED. */
    private void drawHeader() {
        if (!this.isRanked()) {
            this.setItem(HEADER_SLOT, ItemUtils.create(Material.GRAY_DYE, meta -> {
                meta.displayName(StatsFormat.text("&7&lUNRANKED"));
                List<Component> lore = new ArrayList<>();
                lore.add(StatsFormat.text("&7Этот уровень не прошёл модерацию,"));
                lore.add(StatsFormat.text("&7результаты не идут в рейтинг."));
                meta.lore(lore);
            }), null);
            return;
        }

        RunResult globalRecord = this.plugin.get(StatisticsManager.class)
            .getGlobalRecord(this.settings.getUniqueId());
        if (globalRecord == null) {
            this.setItem(HEADER_SLOT, ItemUtils.create(Material.FIREWORK_STAR, meta -> {
                meta.displayName(StatsFormat.text("&e&lРЕКОРД УРОВНЯ"));
                meta.lore(java.util.Collections.singletonList(
                    StatsFormat.text("&fРекорда ещё нет — он может быть вашим")));
            }), null);
            return;
        }

        this.setItem(HEADER_SLOT, ItemUtils.create(Material.FIREWORK_STAR, meta -> {
            meta.displayName(StatsFormat.text("&e&l★ РЕКОРД УРОВНЯ &7- &f" + globalRecord.getPlayerName()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&7Очков: &e" + StatsFormat.number(globalRecord.getScore())));
            lore.add(StatsFormat.text("&7Точность: &f" + StatsFormat.percent(globalRecord.getAccuracy())
                + " &7(" + globalRecord.getGrade().getFormatted() + "&7)"));
            lore.add(StatsFormat.text("&7Комбо: &fx" + globalRecord.getMaxCombo()
                + (globalRecord.isFullCombo() ? " &7[&b&lFC&7]" : "")));
            lore.add(StatsFormat.text("&7Время: &f" + TimeUtils.formatTimecode(globalRecord.getTimeMillis())));
            lore.add(StatsFormat.text("&7Модификаторы: &f" + globalRecord.getModifiersDisplay()));
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&7Установлен " + StatsFormat.dateTime(globalRecord.getTimestamp())));
            meta.lore(lore);
        }), null);
    }

    /** «Ваш результат» — всегда, даже если смотрящий вне топа. */
    private void drawViewerResult() {
        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        RunResult record = statistics.getRecord(this.viewer.getUniqueId(), this.settings.getUniqueId());

        if (record == null) {
            this.setItem(46, ItemUtils.create(Material.PAPER, meta -> {
                meta.displayName(StatsFormat.text("&7Ваш результат: &7отсутствует"));
                meta.lore(java.util.Collections.singletonList(
                    StatsFormat.text("&fВы ещё не проходили этот уровень")));
            }), null);
            return;
        }

        int position = statistics.getLevelTopPosition(this.settings.getUniqueId(), this.viewer.getUniqueId());
        this.setItem(46, this.buildEntry(record, position, true), null);
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull RunResult record) {
        new PlayerStatisticsMenu(this.plugin, this.lang, event.getPlayer(),
            Bukkit.getOfflinePlayer(record.getPlayerId())).open(event.getPlayer());
    }

    private boolean isRanked() {
        LevelDifficulty difficulty = this.settings.getDifficulty();
        return difficulty != null && difficulty != LevelDifficulty.N_A;
    }

    private static boolean isContentSlot(int slot) {
        for (int contentSlot : CONTENT_SLOTS) {
            if (contentSlot == slot) return true;
        }
        return false;
    }
}
