package ru.sortix.parkourbeat.listeners;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import lombok.NonNull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.InventoryUtils;
import ru.sortix.parkourbeat.inventory.LobbyItems;
import ru.sortix.parkourbeat.inventory.type.GlobalStatisticsMenu;
import ru.sortix.parkourbeat.inventory.type.LevelsListMenu;
import ru.sortix.parkourbeat.inventory.type.ModifiersMenu;

/**
 * Routes right-clicks on the three default lobby items (emerald → statistics,
 * PLAY head → level list, fireball → modifiers) to their menus.
 */
public final class LobbyItemsListener implements Listener {
    private final @NonNull ParkourBeat plugin;
    private final @NonNull LobbyItems lobbyItems;

    public LobbyItemsListener(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.lobbyItems = plugin.get(LobbyItems.class);
    }

    @EventHandler
    private void on(@NonNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR
            && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (InventoryUtils.isInventoryOpen(player)) return;

        ItemStack item = event.getItem();
        LobbyItems.Kind kind = this.lobbyItems.getKind(item);
        if (kind == null) return;

        event.setCancelled(true);
        String lang = PlayerLang.of(player);
        switch (kind) {
            case STATS:
                new GlobalStatisticsMenu(this.plugin, lang, player).open(player);
                break;
            case PLAY:
                new LevelsListMenu(this.plugin, lang, LevelsListMenu.DisplayMode.RANKED,
                    player, player.getUniqueId()).open(player);
                break;
            case MENU:
                new ru.sortix.parkourbeat.inventory.type.ServerMenu(this.plugin, lang, player).open(player);
                break;
            case RECORDS:
                new ru.sortix.parkourbeat.inventory.type.RecordsMenu(this.plugin, lang, player).open(player);
                break;
            case MODIFIERS:
                new ModifiersMenu(this.plugin, lang, player).open(player);
                break;
        }
    }
}
