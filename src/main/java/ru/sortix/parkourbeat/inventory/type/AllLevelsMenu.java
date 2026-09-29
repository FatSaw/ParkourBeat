package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.Heads;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.ModerationStatus;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * ПРИВАТНЫЕ УРОВНИ СЕРВЕРА - ДЛЯ АДМИНИСТРАЦИИ.
 * <p>
 * Обычные списки показывают только опубликованные уровни: приватная стройка чужого
 * игрока в них не попадает никогда. Здесь ровно наоборот - только приватное, то есть
 * то, чего больше нигде не видно: построил человек уровень и не отправил.
 * <p>
 * Клик по уровню открывает редактор с правами хозяина (см. {@code EditActivity.isOwner}):
 * можно и переименовать, и опубликовать, и удалить.
 */
public class AllLevelsMenu extends PaginatedMenu<ParkourBeat, GameSettings> {
    /** Две строки уровней: восемнадцать штук на страницу. */
    private static final int[] CONTENT_SLOTS = {
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26
    };

    /** Порядок в списке. Обе сортировки - по времени, просто в разные стороны. */
    private enum Sort {
        NEWEST("&fПо времени &7(сначала новые)"),
        OLDEST("&fПо времени &7(сначала старые)");

        private final String displayName;

        Sort(String displayName) {
            this.displayName = displayName;
        }

        Sort next() {
            return this == NEWEST ? OLDEST : NEWEST;
        }
    }

    private Sort sort = Sort.NEWEST;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy");

    public AllLevelsMenu(@NonNull ParkourBeat plugin, String lang) {
        super(plugin, 4, lang, PbText.of("&8Приватные уровни"), CONTENT_SLOTS);
        this.updateAllItems();
    }

    @Override
    @NonNull
    protected Collection<GameSettings> getAllItems() {
        List<GameSettings> settings =
            new ArrayList<>(this.plugin.get(LevelsManager.class).getAvailableLevelsSettings());

        // ТОЛЬКО ПРИВАТНЫЕ. Опубликованные и так видны в обычном списке уровней, а сюда
        // заходят ровно за тем, чего в нём нет: за чужой неопубликованной стройкой.
        settings.removeIf(GameSettings::isPublicVisible);

        Comparator<GameSettings> byDate = Comparator.comparingLong(GameSettings::getCreatedAtMills);
        settings.sort(this.sort == Sort.NEWEST ? byDate.reversed() : byDate);
        return settings;
    }

    @Override
    @NonNull
    protected ItemStack createItemDisplay(@NonNull GameSettings settings) {
        return ItemUtils.modifyMeta(
            Heads.getHeadByTextureData(settings.getDifficulty().getHeadBase64(), true), meta -> {
                String name = PbText.keepColors(net.kyori.adventure.text.serializer.legacy
                    .LegacyComponentSerializer.legacyAmpersand().serialize(settings.getDisplayName()));
                meta.displayName(PbText.item("&f" + name + " &8(#" + settings.getUniqueNumber() + ")"));

                List<Component> lore = new ArrayList<>();
                lore.add(PbText.item("&7Автор: &f" + settings.getOwnerName()));
                lore.add(PbText.item("&7Создан: &f" + DATE_FORMAT.format(settings.getCreatedAtMills())));
                lore.add(PbText.item("&7Режим: &f" + settings.getLevelMode().getShortName()
                    + " &8| &7" + settings.getChunkWidth() + " ч."));
                lore.add(PbText.item("&7Модерация: " + statusName(settings.getModerationStatus())));
                lore.add(PbText.item("&8Нажмите, чтобы открыть в редакторе"));
                meta.lore(lore);
            });
    }

    @NonNull
    private static String statusName(@NonNull ModerationStatus status) {
        return switch (status) {
            case ON_MODERATION -> "&eпроходит модерацию";
            case MODERATED -> "&aпройдена";
            default -> "&7черновик, не отправлялся";
        };
    }

    @Override
    protected void onPageDisplayed() {
        this.setPreviousPageItem(4, 3);
        this.setNextPageItem(4, 7);

        this.setItem(4, 5, ItemUtils.create(Material.CLOCK, meta -> {
            meta.displayName(PbText.item("&8◆ &6Сортировка"));
            meta.lore(List.of(
                PbText.item("&e" + this.sort.displayName),
                PbText.item("&8Нажмите, чтобы перевернуть порядок")
            ));
        }), event -> {
            this.sort = this.sort.next();
            this.updateAllItems();
        });

        this.setItem(4, 9, ItemUtils.create(Material.BARRIER,
            meta -> meta.displayName(PbText.item("&cЗакрыть"))),
            event -> event.getPlayer().closeInventory());
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull GameSettings settings) {
        Player player = event.getPlayer();
        player.closeInventory();

        // РАЗОВЫЙ ПРОПУСК НА ПРИВАТНЫЙ УРОВЕНЬ ВЫДАЁТСЯ САМ.
        //
        // Охрана приватных уровней требует /bypassprivate перед каждым телепортом - и
        // правильно делает: случайно провалиться в чужую стройку нельзя. Но здесь весь
        // список и состоит из приватных уровней, и клик по уровню - это уже осознанное
        // решение туда пойти. Требовать после него ещё и команду в чат бессмысленно.
        this.plugin.get(ru.sortix.parkourbeat.player.PlayerSettingsManager.class)
            .grantPrivateBypass(player.getUniqueId());

        // false - меню модерации админу тут не нужно: он пришёл смотреть стройку,
        // а не выносить вердикт.
        LevelsListMenu.startEditing(this.plugin, player, settings, false);
    }
}
