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

import javax.annotation.Nullable;

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
    public static final int PLAY_SLOT = 4;
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
        PLAY,
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
        player.getInventory().setItem(STATS_SLOT, buildStats());
        player.getInventory().setItem(PLAY_SLOT, buildPlay());
        player.getInventory().setItem(MODIFIERS_SLOT, buildModifiers());
    }

    /**
     * Removes the three default items from the player's hotbar (used during an active run).
     */
    public void removeAll(@NonNull Player player) {
        for (int slot : new int[]{STATS_SLOT, PLAY_SLOT, MODIFIERS_SLOT}) {
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
            if (getKind(player.getInventory().getItem(STATS_SLOT)) != Kind.STATS) {
                player.getInventory().setItem(STATS_SLOT, buildStats());
            }
            if (getKind(player.getInventory().getItem(PLAY_SLOT)) != Kind.PLAY) {
                player.getInventory().setItem(PLAY_SLOT, buildPlay());
            }
            if (getKind(player.getInventory().getItem(MODIFIERS_SLOT)) != Kind.MODIFIERS) {
                player.getInventory().setItem(MODIFIERS_SLOT, buildModifiers());
            }
        } else {
            removeAll(player);
        }
    }

    @NonNull
    private ItemStack buildStats() {
        ItemStack item = ItemUtils.create(Material.EMERALD, meta -> {
            meta.displayName(name("&a&lСтатистика игроков"));
            meta.lore(java.util.Arrays.asList(
                Component.empty(),
                line("&7Достижения всех игроков сервера"),
                Component.empty(),
                line("&8ПКМ чтобы открыть")
            ));
        });
        return this.tag(item, Kind.STATS);
    }

    @NonNull
    private ItemStack buildPlay() {
        ItemStack item = ItemUtils.modifyMeta(UIHeads.PLAY.clone(), meta -> {
            meta.displayName(name("&b&lИграть"));
            meta.lore(java.util.Arrays.asList(
                Component.empty(),
                line("&7Список уровней"),
                Component.empty(),
                line("&8ПКМ чтобы открыть")
            ));
        });
        return this.tag(item, Kind.PLAY);
    }

    @NonNull
    private ItemStack buildModifiers() {
        ItemStack item = ItemUtils.create(Material.FIRE_CHARGE, meta -> {
            meta.displayName(name("&c&lМодификаторы"));
            meta.lore(java.util.Arrays.asList(
                Component.empty(),
                line("&7Выбор модификаторов для прохождения"),
                Component.empty(),
                line("&8ПКМ чтобы открыть")
            ));
        });
        return this.tag(item, Kind.MODIFIERS);
    }

    @NonNull
    private static Component name(@NonNull String legacy) {
        return LEGACY.deserialize(legacy)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    @NonNull
    private static Component line(@NonNull String legacy) {
        return LEGACY.deserialize(legacy)
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
