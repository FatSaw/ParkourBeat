package ru.sortix.parkourbeat.listeners;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.levels.FallZoneRenderer;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LightShowPositions;
import ru.sortix.parkourbeat.levels.settings.FallZone;
import ru.sortix.parkourbeat.levels.settings.LightShowElement;
import ru.sortix.parkourbeat.utils.TimeUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import ru.sortix.parkourbeat.utils.text.PbText;
/**
 * Отдельная палочка для зон падения: у зоны кроме начала и конца есть ещё высота смерти,
 * а её удобнее не вводить числом, а просто навестись на нужный блок.
 */
@RequiredArgsConstructor
public class FallZoneWandListener implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final double RAY_TRACE_DISTANCE = 120.0D;
    private static final Material WAND_MATERIAL = Material.CARROT_ON_A_STICK;

    private final @NonNull ParkourBeat plugin;

    @NonNull
    private static NamespacedKey key(@NonNull ParkourBeat plugin) {
        return new NamespacedKey(plugin, "fall_zone_wand");
    }

    public static boolean isWand(@NonNull ParkourBeat plugin, @Nullable ItemStack stack) {
        if (stack == null || stack.getType() != WAND_MATERIAL) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(key(plugin), PersistentDataType.STRING);
    }

    @NonNull
    public static ItemStack createItem(@NonNull ParkourBeat plugin) {
        ItemStack stack = new ItemStack(WAND_MATERIAL);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(line("&6&lУдочка зон падения"));
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(line("&eПКМ &7- начало зоны"));
            lore.add(line("&eЛКМ &7- конец зоны"));
            lore.add(line("&eShift + клик &7- высота смерти по точке"));
            lore.add(Component.empty());
            lore.add(line("&7Наведитесь на блок и нажмите."));
            lore.add(line("&7Работает с зоной, открытой в меню."));
            meta.lore(lore);

            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, "1");
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Старая палочка выкидывается, чтобы их не накапливалось по всему инвентарю.
     */
    public static void give(@NonNull ParkourBeat plugin, @NonNull Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isWand(plugin, contents[slot])) player.getInventory().setItem(slot, null);
        }

        ItemStack stack = createItem(plugin);
        for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
    }

    private static Component line(@NonNull String legacy) {
        return PbText.of(legacy).decoration(TextDecoration.ITALIC, false);
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
        if (!(selected instanceof FallZone zone)) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.fall_zone_wand_listener.on.1")));
            return;
        }

        RayTraceResult result = player.rayTraceBlocks(RAY_TRACE_DISTANCE);
        Vector position = result == null ? null : result.getHitPosition();
        if (position == null) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.fall_zone_wand_listener.on.2")));
            return;
        }

        Level level = editActivity.getLevel();
        if (!level.isPositionInside(position.getX(), position.getY(), position.getZ())) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.fall_zone_wand_listener.on.3")));
            return;
        }

        if (player.isSneaking()) {
            zone.setDeathY(position.getBlockY());
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.fall_zone_wand_listener.on.4") + zone.getDeathY()));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1.4f);
            FallZoneRenderer.preview(this.plugin, player, level);
            return;
        }

        int timeMillis = LightShowPositions.toTimeMillis(level, position);
        if (rightClick) zone.setStartMillis(timeMillis);
        else zone.setEndMillis(timeMillis);
        level.getLightShow().sort();

        player.sendMessage(PbText.of((rightClick ? Lang.raw(PlayerLang.of(player), "auto.fall_zone_wand_listener.on.5") : Lang.raw(PlayerLang.of(player), "auto.fall_zone_wand_listener.on.6"))
            + TimeUtils.formatTimecode(timeMillis)
            + " &7(" + zone.getStartTimecode() + " - " + zone.getEndTimecode() + ")"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.8f);
    }
}
