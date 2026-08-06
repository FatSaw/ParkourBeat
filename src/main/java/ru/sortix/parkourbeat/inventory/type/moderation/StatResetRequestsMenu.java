package ru.sortix.parkourbeat.inventory.type.moderation;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.stats.StatResetRequest;
import ru.sortix.parkourbeat.stats.StatResetRequestManager;
import ru.sortix.parkourbeat.stats.StatsFormat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Вкладка {@code /moder}: заявки игроков на сброс статистики.
 * ЛКМ — одобрить, ПКМ — отклонить. Оба действия требуют подтверждения.
 */
public class StatResetRequestsMenu extends PaginatedMenu<ParkourBeat, StatResetRequest> {

    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };

    private final @NonNull Player viewer;

    public StatResetRequestsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer) {
        super(plugin, 5, lang, StatsFormat.text("&cЗапросы на сброс статистики"), CONTENT_SLOTS);
        this.viewer = viewer;
        this.updateAllItems();
    }

    @Override
    @NonNull
    protected Collection<StatResetRequest> getAllItems() {
        return new ArrayList<>(this.plugin.get(StatResetRequestManager.class).getPending());
    }

    @Override
    protected @NonNull ItemStack createItemDisplay(@NonNull StatResetRequest request) {
        return ItemUtils.modifyMeta(StatsFormat.playerHead(request.getPlayerId(), request.getPlayerName()), meta -> {
            meta.displayName(StatsFormat.text("&e" + request.getPlayerName()));

            List<Component> lore = new ArrayList<>();
            lore.add(StatsFormat.text("&7Запрошено: &f" + StatsFormat.dateTime(request.getRequestedAtMillis())));
            lore.add(StatsFormat.text("&7Ждёт: &f" + request.getAgeDays() + " дн."));
            if (request.getAgeDays() >= StatResetRequestManager.REVIEW_DAYS) {
                lore.add(StatsFormat.text("&c⚠ Обещанный срок рассмотрения истёк"));
            }
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&7Будет удалено: профиль, все рекорды,"));
            lore.add(StatsFormat.text("&7вся история, PP и время игры."));
            lore.add(Component.empty());
            lore.add(StatsFormat.text("&aЛКМ &7— одобрить сброс"));
            lore.add(StatsFormat.text("&cПКМ &7— отклонить"));
            meta.lore(lore);
        });
    }

    @Override
    protected void onPageDisplayed() {
        ItemStack glass = ItemUtils.create(Material.BLACK_STAINED_GLASS_PANE,
            m -> m.displayName(Component.empty()));
        for (int i = 0; i < 45; i++) {
            boolean content = false;
            for (int slot : CONTENT_SLOTS) if (i == slot) { content = true; break; }
            if (!content) this.setItem(i, glass, null);
        }

        this.setPreviousPageItem(5, 4);
        this.setItem(5, 5, ItemUtils.create(Material.BARRIER,
                m -> m.displayName(StatsFormat.text("&cЗакрыть"))),
            e -> e.getPlayer().closeInventory());
        this.setNextPageItem(5, 6);

        if (this.getAllItems().isEmpty()) {
            this.setItem(22, ItemUtils.create(Material.PAPER, m -> {
                m.displayName(StatsFormat.text("&7Заявок нет"));
                m.lore(List.of(StatsFormat.text("&8Здесь появятся запросы игроков на /statreset")));
            }), null);
        }
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull StatResetRequest request) {
        Player moderator = event.getPlayer();
        boolean approve = event.isLeft();
        new StatResetConfirmMenu(this.plugin, this.lang, request, approve, moderator).open(moderator);
    }

    /** Простое подтверждение поверх списка. */
    public static class StatResetConfirmMenu
        extends ru.sortix.parkourbeat.inventory.PluginInventory<ParkourBeat> {

        private final @NonNull StatResetRequest request;
        private final boolean approve;
        private final @NonNull Player moderator;

        public StatResetConfirmMenu(@NonNull ParkourBeat plugin, String lang,
                                    @NonNull StatResetRequest request, boolean approve,
                                    @NonNull Player moderator) {
            super(plugin, 3, lang, StatsFormat.text(approve
                ? "&cОдобрить сброс статистики?"
                : "&eОтклонить запрос?"));
            this.request = request;
            this.approve = approve;
            this.moderator = moderator;
            this.draw();
        }

        private void draw() {
            ItemStack glass = ItemUtils.create(Material.BLACK_STAINED_GLASS_PANE,
                m -> m.displayName(Component.empty()));
            for (int i = 0; i < 27; i++) this.setItem(i, glass, null);

            this.setItem(13, StatsFormat.playerHead(this.request.getPlayerId(), this.request.getPlayerName()), null);

            this.setItem(11, ItemUtils.create(Material.LIME_WOOL, m -> {
                m.displayName(StatsFormat.text(this.approve ? "&a&lОдобрить" : "&a&lОтклонить запрос"));
                m.lore(List.of(StatsFormat.text(this.approve
                    ? "&cСтатистика " + this.request.getPlayerName() + " будет стёрта навсегда"
                    : "&7Игрок получит уведомление об отказе")));
            }), event -> {
                StatResetRequestManager manager = this.plugin.get(StatResetRequestManager.class);
                if (this.approve) {
                    manager.approve(this.request, this.moderator);
                    this.moderator.sendMessage(StatsFormat.text(
                        "&aСтатистика игрока " + this.request.getPlayerName() + " сброшена."));
                } else {
                    manager.reject(this.request, this.moderator);
                    this.moderator.sendMessage(StatsFormat.text(
                        "&eЗапрос игрока " + this.request.getPlayerName() + " отклонён."));
                }
                new StatResetRequestsMenu(this.plugin, this.lang, this.moderator).open(this.moderator);
            });

            this.setItem(15, ItemUtils.create(Material.RED_WOOL, m ->
                    m.displayName(StatsFormat.text("&c&lНазад"))),
                event -> new StatResetRequestsMenu(this.plugin, this.lang, this.moderator)
                    .open(event.getPlayer()));
        }
    }
}
