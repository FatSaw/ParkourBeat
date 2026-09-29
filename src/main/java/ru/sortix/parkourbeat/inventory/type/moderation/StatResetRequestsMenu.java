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
import ru.sortix.parkourbeat.utils.lang.Lang;

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
        super(plugin, 5, lang, Lang.item(lang, "inventory.statreset.title"), CONTENT_SLOTS);
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
            lore.add(Lang.item(this.lang, "inventory.statreset.entry.requested",
                "%date%", StatsFormat.dateTime(request.getRequestedAtMillis())));
            lore.add(Lang.item(this.lang, "inventory.statreset.entry.waiting",
                "%days%", String.valueOf(request.getAgeDays())));
            if (request.getAgeDays() >= StatResetRequestManager.REVIEW_DAYS) {
                lore.add(Lang.item(this.lang, "inventory.statreset.entry.overdue"));
            }
            lore.add(Component.empty());
            lore.addAll(Lang.lore(this.lang, "inventory.statreset.entry.warning"));
            lore.add(Component.empty());
            lore.addAll(Lang.lore(this.lang, "inventory.statreset.entry.actions"));
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
                m -> m.displayName(Lang.item(this.lang, "inventory.common.close"))),
            e -> e.getPlayer().closeInventory());
        this.setNextPageItem(5, 6);

        if (this.getAllItems().isEmpty()) {
            this.setItem(22, ItemUtils.create(Material.PAPER, m -> {
                m.displayName(Lang.item(this.lang, "inventory.statreset.empty.name"));
                m.lore(Lang.lore(this.lang, "inventory.statreset.empty.lore"));
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
            super(plugin, 3, lang, Lang.item(lang, approve
                ? "inventory.statreset.confirm.title_approve"
                : "inventory.statreset.confirm.title_deny"));
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
                m.displayName(Lang.item(this.lang, this.approve
                    ? "inventory.statreset.confirm.approve"
                    : "inventory.statreset.confirm.deny"));
                m.lore(Lang.lore(this.lang, this.approve
                        ? "inventory.statreset.confirm.lore_approve"
                        : "inventory.statreset.confirm.lore_deny",
                    "%player%", this.request.getPlayerName()));
            }), event -> {
                StatResetRequestManager manager = this.plugin.get(StatResetRequestManager.class);
                if (this.approve) {
                    manager.approve(this.request, this.moderator);
                    this.moderator.sendMessage(Lang.text(this.lang,
                        "inventory.statreset.done_approve",
                        "%player%", this.request.getPlayerName()));
                } else {
                    manager.reject(this.request, this.moderator);
                    this.moderator.sendMessage(Lang.text(this.lang,
                        "inventory.statreset.done_deny",
                        "%player%", this.request.getPlayerName()));
                }
                new StatResetRequestsMenu(this.plugin, this.lang, this.moderator).open(this.moderator);
            });

            this.setItem(15, ItemUtils.create(Material.RED_WOOL, m ->
                    m.displayName(Lang.item(this.lang, "inventory.common.back"))),
                event -> new StatResetRequestsMenu(this.plugin, this.lang, this.moderator)
                    .open(event.getPlayer()));
        }
    }
}
