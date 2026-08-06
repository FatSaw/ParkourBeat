package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.Heads;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.UIHeads;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.stats.PlayerProfile;
import ru.sortix.parkourbeat.stats.RunResult;
import ru.sortix.parkourbeat.stats.StatsFormat;
import ru.sortix.parkourbeat.utils.TimeUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Уровни, пройденные игроком (п.6 ТЗ). Каждый — голова своей сложности, как в списке
 * уровней. Сортировка по умолчанию — по дате прохождения (свежие сверху),
 * с переключением на «по очкам».
 * <p>
 * Клик по уровню открывает топ этого уровня.
 */
public class PlayerLevelsMenu extends PaginatedMenu<ParkourBeat, RunResult> {
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    public enum SortMode {
        DATE("&bПо дате прохождения"),
        SCORE("&eПо очкам"),
        PP("&dПо PP");

        private final @NonNull String display;

        SortMode(@NonNull String display) {
            this.display = display;
        }

        @NonNull
        public String getDisplay() {
            return this.display;
        }

        @NonNull
        public SortMode next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    private final @NonNull Player viewer;
    private final @NonNull OfflinePlayer target;
    private @NonNull SortMode sortMode = SortMode.DATE;

    public PlayerLevelsMenu(@NonNull ParkourBeat plugin, String lang,
                            @NonNull Player viewer, @NonNull OfflinePlayer target) {
        super(plugin, 6, lang, StatsFormat.text("&7Уровни: &f" + StatsFormat.safeName(target.getName())),
            CONTENT_SLOTS);
        this.viewer = viewer;
        this.target = target;
        this.updateAllItems();
    }

    @Override
    @NonNull
    protected Collection<RunResult> getAllItems() {
        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        PlayerProfile profile = statistics.getProfile(this.target);

        List<RunResult> records = new ArrayList<>(profile.getAllRecords());
        Comparator<RunResult> comparator;
        switch (this.sortMode) {
            case SCORE:
                comparator = Comparator.comparingInt(RunResult::getScore).reversed();
                break;
            case PP:
                comparator = Comparator.comparingDouble(statistics::getRecordPP).reversed();
                break;
            case DATE:
            default:
                comparator = Comparator.comparingLong(RunResult::getTimestamp).reversed();
                break;
        }
        records.sort(comparator);
        return records;
    }

    @Override
    @NonNull
    protected ItemStack createItemDisplay(@NonNull RunResult record) {
        StatisticsManager statistics = this.plugin.get(StatisticsManager.class);
        GameSettings settings = statistics.getLevelSettings(record.getLevelId());
        LevelDifficulty currentDifficulty = settings == null ? null : settings.getDifficulty();
        boolean deleted = settings == null;

        // Голова — по АКТУАЛЬНОЙ сложности, как в списке уровней; если уровень удалён,
        // берём ту, что была на момент прохождения.
        LevelDifficulty headDifficulty = currentDifficulty != null ? currentDifficulty : record.getDifficulty();
        ItemStack head = Heads.getHeadByTextureData(headDifficulty.getHeadBase64(), true);

        return ItemUtils.modifyMeta(head, meta -> {
            String levelName = settings != null ? settings.getDisplayNameLegacy(false) : record.getLevelName();
            meta.displayName(StatsFormat.text("&f" + levelName + (deleted ? " &7(уровень удалён)" : "")));

            List<Component> lore = new ArrayList<>();
            lore.add(StatsFormat.text("&7Сложность: " + headDifficulty.getDisplayName()));
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&7Прогресс: &f" + StatsFormat.percentRounded(record.getProgressPercent())
                + (record.isCompleted() ? "" : " &7(не пройден)")));
            lore.add(StatsFormat.text("&7Время: &f" + TimeUtils.formatTimecode(record.getTimeMillis())));
            lore.add(StatsFormat.text("&7Точность: &f" + StatsFormat.percent(record.getAccuracy())
                + " &7(" + record.getGrade().getFormatted() + "&7)"));
            lore.add(StatsFormat.text("&7Комбо: &fx" + record.getMaxCombo()));
            lore.add(StatsFormat.text("&7Очков: &f" + StatsFormat.number(record.getScore())));
            lore.add(StatsFormat.text("&7Промахов: &f" + record.getMissCount()
                + (record.isFullCombo() ? " &7[&b&lFC&7]" : "")));
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&7Модификаторы: &f" + record.getModifiersDisplay()));

            if (deleted) {
                lore.add(StatsFormat.text("&fУровень удалён — в рейтинг не идёт"));
            } else if (currentDifficulty == LevelDifficulty.N_A) {
                lore.add(StatsFormat.text("&7&lUNRANKED"));
            } else {
                int position = statistics.getLevelTopPosition(record.getLevelId(), record.getPlayerId());
                int size = statistics.getLevelTopSize(record.getLevelId());
                lore.add(StatsFormat.text("&7Место в топе: " + StatsFormat.position(position)
                    + " &r&7из &f" + size));
                lore.add(StatsFormat.text("&7PP: &d" + StatsFormat.pp(statistics.getRecordPP(record))));
            }

            lore.add(StatsFormat.text("&7" + (record.isCompleted() ? "Пройден " : "Попытка ")
                + StatsFormat.dateTime(record.getTimestamp())));

            if (!deleted) {
                lore.add(Component.empty());
                lore.add(StatsFormat.text("&fНажмите, чтобы открыть топ уровня"));
            }
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

        this.setItem(6, 2, ItemUtils.modifyMeta(UIHeads.SORT.clone(), meta -> {
            meta.displayName(StatsFormat.text("&eСортировка: " + this.sortMode.getDisplay()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            for (SortMode mode : SortMode.values()) {
                lore.add(StatsFormat.text((mode == this.sortMode ? "&f▶ " : "&7• ") + mode.getDisplay()));
            }
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&fНажмите чтобы переключить"));
            meta.lore(lore);
        }), event -> {
            this.sortMode = this.sortMode.next();
            this.updateAllItems();
        });

        this.setPreviousPageItem(6, 4);
        this.setItem(6, 5, ItemUtils.modifyMeta(UIHeads.ARROW_LEFT.clone(), meta ->
                meta.displayName(StatsFormat.text("&7Назад"))),
            event -> new PlayerStatisticsMenu(this.plugin, this.lang, this.viewer, this.target).open(this.viewer));
        this.setNextPageItem(6, 6);

        if (this.getMaxPageNumber() == 1 && this.isEmptyList()) {
            this.setItem(22, ItemUtils.create(Material.BARRIER, meta ->
                meta.displayName(StatsFormat.text("&cНет пройденных уровней"))), null);
        }
    }

    private boolean isEmptyList() {
        return this.plugin.get(StatisticsManager.class).getProfile(this.target).getAllRecords().isEmpty();
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull RunResult record) {
        GameSettings settings = this.plugin.get(StatisticsManager.class).getLevelSettings(record.getLevelId());
        if (settings == null) return;
        new LevelTopMenu(this.plugin, this.lang, settings, event.getPlayer()).open(event.getPlayer());
    }

    private static boolean isContentSlot(int slot) {
        for (int contentSlot : CONTENT_SLOTS) {
            if (contentSlot == slot) return true;
        }
        return false;
    }
}
