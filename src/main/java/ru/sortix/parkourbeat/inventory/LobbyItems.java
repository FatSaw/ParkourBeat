package ru.sortix.parkourbeat.inventory;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import javax.annotation.Nullable;

import ru.sortix.parkourbeat.utils.text.PbText;
/**
 * The default lobby/level hotbar items and the logic for identifying and placing them.
 * <ul>
 *   <li>slot 1 (index 0) — emerald: global player statistics</li>
 *   <li>slot 5 (index 4) — PLAY head: opens the /play menu on right-click</li>
 *   <li>slot 9 (index 8) — fireball: opens the modifiers menu</li>
 * </ul>
 * Each item is tagged with a persistent key so clicks can be routed regardless of the
 * player's locale or any display-name changes.
 */
public final class LobbyItems implements PluginManager {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public static final int STATS_SLOT = 0;
    public static final int MENU_SLOT = 3;
    public static final int PLAY_SLOT = 4;
    public static final int RECORDS_SLOT = 5;
    public static final int MODIFIERS_SLOT = 8;

    private final @NonNull NamespacedKey key;

    public LobbyItems(@NonNull ParkourBeat plugin) {
        this.key = new NamespacedKey(plugin, "lobby_item");
    }

    /**
     * Identifier stored on each lobby item; also the switch used by the listener.
     */
    public enum Kind {
        STATS,
        MENU,
        PLAY,
        RECORDS,
        MODIFIERS
    }

    @Nullable
    public Kind getKind(@Nullable ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        String raw = stack.getItemMeta().getPersistentDataContainer()
            .get(this.key, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return Kind.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Places all three default items into the player's hotbar.
     */
    public void giveAll(@NonNull Player player) {
        String lang = PlayerLang.of(player);
        player.getInventory().setItem(STATS_SLOT, buildStats(lang));
        player.getInventory().setItem(MENU_SLOT, buildMenu(lang));
        player.getInventory().setItem(PLAY_SLOT, buildPlay(lang));
        player.getInventory().setItem(RECORDS_SLOT, buildRecords(lang));
        player.getInventory().setItem(MODIFIERS_SLOT, buildModifiers(lang));
    }

    /**
     * Пересобирает уже выданные предметы - например, после смены языка.
     * <p>
     * Именно уже выданные: {@link #sync} сверяет только вид предмета и на смену языка
     * не реагирует, а выдавать предметы заново нельзя - во время забега их у игрока
     * нет намеренно, и они бы вернулись прямо посреди трассы.
     */
    public void refresh(@NonNull Player player) {
        if (getKind(player.getInventory().getItem(MENU_SLOT)) == null
            && getKind(player.getInventory().getItem(PLAY_SLOT)) == null) {
            return;
        }
        this.giveAll(player);
    }

    /**
     * Removes the three default items from the player's hotbar (used during an active run).
     */
    public void removeAll(@NonNull Player player) {
        for (int slot : new int[]{STATS_SLOT, MENU_SLOT, PLAY_SLOT, RECORDS_SLOT, MODIFIERS_SLOT}) {
            if (getKind(player.getInventory().getItem(slot)) != null) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    /**
     * Ensures the hotbar matches the desired state without rewriting items that are already
     * correct (so an open menu's cursor never jitters).
     *
     * @param shouldHave true in the lobby and on a level while not running; false mid-run
     */
    public void sync(@NonNull Player player, boolean shouldHave) {
        if (shouldHave) {
            String lang = PlayerLang.of(player);
            if (getKind(player.getInventory().getItem(STATS_SLOT)) != Kind.STATS) {
                player.getInventory().setItem(STATS_SLOT, buildStats(lang));
            }
            if (getKind(player.getInventory().getItem(MENU_SLOT)) != Kind.MENU) {
                player.getInventory().setItem(MENU_SLOT, buildMenu(lang));
            }
            if (getKind(player.getInventory().getItem(PLAY_SLOT)) != Kind.PLAY) {
                player.getInventory().setItem(PLAY_SLOT, buildPlay(lang));
            }
            if (getKind(player.getInventory().getItem(RECORDS_SLOT)) != Kind.RECORDS) {
                player.getInventory().setItem(RECORDS_SLOT, buildRecords(lang));
            }
            if (getKind(player.getInventory().getItem(MODIFIERS_SLOT)) != Kind.MODIFIERS) {
                player.getInventory().setItem(MODIFIERS_SLOT, buildModifiers(lang));
            }
        } else {
            removeAll(player);
        }
    }

    @NonNull
    private ItemStack buildStats(String lang) {
        ItemStack item = ItemUtils.create(Material.EMERALD, meta -> {
            meta.displayName(Lang.item(lang, "item.lobby.stats.name"));
            meta.lore(Lang.lore(lang, "item.lobby.stats.lore"));
        });
        return this.tag(item, Kind.STATS);
    }

    @NonNull
    private ItemStack buildPlay(String lang) {
        ItemStack item = ItemUtils.modifyMeta(UIHeads.PLAY.clone(), meta -> {
            meta.displayName(Lang.item(lang, "item.lobby.play.name"));
            meta.lore(Lang.lore(lang, "item.lobby.play.lore"));
        });
        return this.tag(item, Kind.PLAY);
    }

    @NonNull
    private ItemStack buildMenu(String lang) {
        ItemStack item = ItemUtils.modifyMeta(UIHeads.MENU.clone(), meta -> {
            meta.displayName(Lang.item(lang, "item.lobby.menu.name"));
            meta.lore(Lang.lore(lang, "item.lobby.menu.lore"));
        });
        return this.tag(item, Kind.MENU);
    }

    @NonNull
    private ItemStack buildRecords(String lang) {
        ItemStack item = ItemUtils.modifyMeta(UIHeads.RECORDS.clone(), meta -> {
            meta.displayName(Lang.item(lang, "item.lobby.records.name"));
            meta.lore(Lang.lore(lang, "item.lobby.records.lore"));
        });
        return this.tag(item, Kind.RECORDS);
    }

    @NonNull
    private ItemStack buildModifiers(String lang) {
        ItemStack item = ItemUtils.create(Material.FIRE_CHARGE, meta -> {
            meta.displayName(Lang.item(lang, "item.lobby.modifiers.name"));
            meta.lore(Lang.lore(lang, "item.lobby.modifiers.lore"));
        });
        return this.tag(item, Kind.MODIFIERS);
    }

    @NonNull
    private static Component name(@NonNull String legacy) {
        return PbText.of(legacy)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    @NonNull
    private static Component line(@NonNull String legacy) {
        return PbText.of(legacy)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    @NonNull
    private ItemStack tag(@NonNull ItemStack stack, @NonNull Kind kind) {
        return ItemUtils.modifyMeta(stack, meta ->
            meta.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, kind.name()));
    }

    @Override
    public void disable() {
    }
}
