package ru.sortix.parkourbeat.listeners;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.levels.BiomeApplier;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LightShowPositions;
import ru.sortix.parkourbeat.levels.settings.BiomeZone;
import ru.sortix.parkourbeat.levels.settings.LightShowElement;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import javax.annotation.Nullable;

/**
 * The wand is handed out from the settings of a single element and always points at that
 * element, so there is nothing to switch and no reason for it to sit in the hotbar.
 */
@RequiredArgsConstructor
public class LightShowWandListener implements Listener {
    private static final double RAY_TRACE_DISTANCE = 120.0D;

    private final @NonNull ParkourBeat plugin;

    @NonNull
    private static NamespacedKey key(@NonNull ParkourBeat plugin) {
        return new NamespacedKey(plugin, "lightshow_wand");
    }

    public static boolean isWand(@NonNull ParkourBeat plugin, @Nullable ItemStack stack) {
        if (stack == null || stack.getType() != Material.STICK) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(key(plugin), PersistentDataType.STRING);
    }

    /**
     * Any wand the player already had is dropped, so there is never more than one.
     */
    public static void give(@NonNull ParkourBeat plugin, @NonNull Player player, String lang) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isWand(plugin, contents[slot])) player.getInventory().setItem(slot, null);
        }

        ItemStack stack = new ItemStack(Material.STICK);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(LangOptions.item_editor_lightshowwand_name.getComponent(lang));
            meta.lore(LangOptions.item_editor_lightshowwand_lore.getComponents(lang));
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, "1");
            stack.setItemMeta(meta);
        }

        for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void on(@NonNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (!rightClick && !leftClick) return;

        Player player = event.getPlayer();
        if (!isWand(this.plugin, player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);

        UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(player);
        if (!(activity instanceof EditActivity editActivity) || editActivity.isTesting()) return;

        LightShowElement selected = editActivity.getSelectedElement();
        if (selected == null) {
            LangOptions.level_editor_wand_noselection.sendMsg(player);
            return;
        }

        Level level = editActivity.getLevel();
        Vector position = this.findAimedPosition(player);
        if (position == null) {
            LangOptions.level_editor_wand_nothingaimed.sendMsg(player);
            return;
        }
        if (!level.isPositionInside(position.getX(), position.getY(), position.getZ())) {
            LangOptions.level_editor_wand_outside.sendMsg(player);
            return;
        }

        int timeMillis = LightShowPositions.toTimeMillis(level, position);
        Placeholders timePlaceholder = new Placeholders("%time%", TimeUtils.formatTimecode(timeMillis));

        boolean start = rightClick || !selected.hasEnd();

        if (selected instanceof BiomeZone zone) {
            BiomeApplier.reset(level, zone);
            if (start) zone.setStartMillis(timeMillis);
            else zone.setEndMillis(timeMillis);
            BiomeApplier.apply(level, zone);
        } else if (start) {
            selected.setStartMillis(timeMillis);
        } else {
            selected.setEndMillis(timeMillis);
        }

        level.getLightShow().sort();
        (start
            ? LangOptions.level_editor_wand_startset
            : LangOptions.level_editor_wand_endset).sendMsg(player, timePlaceholder);
    }

    @Nullable
    private Vector findAimedPosition(@NonNull Player player) {
        RayTraceResult result = player.rayTraceBlocks(RAY_TRACE_DISTANCE);
        return result == null ? null : result.getHitPosition();
    }
}
