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
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.levels.AutoDoorEngine;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.AutoDoor;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import ru.sortix.parkourbeat.utils.text.PbText;
/**
 * Инструмент строителя: тыкаешь в саму дверь, а не вводишь координаты руками.
 * Радиус тоже крутится прямо по двери, чтобы не бегать в меню после каждого шага.
 */
@RequiredArgsConstructor
public class AutoDoorWandListener implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final Material WAND_MATERIAL = Material.TRIPWIRE_HOOK;
    private static final double RAY_TRACE_DISTANCE = 8.0D;

    private final @NonNull ParkourBeat plugin;

    @NonNull
    private static NamespacedKey key(@NonNull ParkourBeat plugin) {
        return new NamespacedKey(plugin, "auto_door_wand");
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
            meta.displayName(line("&6&lПалочка автодверей"));
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.setUnbreakable(true);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(line("&eПКМ &7по двери - привязать / выбрать"));
            lore.add(line("&eЛКМ &7по двери - отвязать"));
            lore.add(line("&eShift + ПКМ &7- радиус больше"));
            lore.add(line("&eShift + ЛКМ &7- радиус меньше"));
            lore.add(Component.empty());
            lore.add(line("&7Работает с дверьми, люками"));
            lore.add(line("&7и калитками любого материала."));
            lore.add(line("&8Радиус видно кольцом из частиц."));
            meta.lore(lore);

            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, "1");
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static void give(@NonNull ParkourBeat plugin, @NonNull Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isWand(plugin, contents[slot])) player.getInventory().setItem(slot, null);
        }
        for (ItemStack leftover : player.getInventory().addItem(createItem(plugin)).values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
    }

    private static Component line(@NonNull String legacy) {
        return PbText.of(legacy).decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void on(@NonNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) return;

        Player player = event.getPlayer();
        if (!isWand(this.plugin, player.getInventory().getItemInMainHand())) return;

        // Иначе ПКМ палочкой сам откроет дверь, а ЛКМ начнёт её ломать.
        event.setCancelled(true);

        UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(player);
        if (!(activity instanceof EditActivity editActivity) || editActivity.isTesting()) return;

        Level level = editActivity.getLevel();
        Block block = this.findDoorBlock(player, event.getClickedBlock());
        if (block == null) {
            player.sendActionBar(PbText.of(Lang.raw(PlayerLang.of(player), "auto.auto_door_wand_listener.on.1")));
            return;
        }

        if (!level.isPositionInside(block.getX() + 0.5D, block.getY(), block.getZ() + 0.5D)) {
            player.sendActionBar(PbText.of(Lang.raw(PlayerLang.of(player), "auto.auto_door_wand_listener.on.2")));
            return;
        }

        LightShowSettings lightShow = level.getLightShow();
        AutoDoor existing = lightShow.findAutoDoorAt(block.getX(), block.getY(), block.getZ());

        if (player.isSneaking()) {
            this.changeRadius(player, editActivity, existing, right);
            return;
        }

        if (right) {
            this.bindOrSelect(player, editActivity, lightShow, block, existing);
        } else {
            this.unbind(player, editActivity, lightShow, existing);
        }
    }

    private void bindOrSelect(@NonNull Player player,
                              @NonNull EditActivity activity,
                              @NonNull LightShowSettings lightShow,
                              @NonNull Block block,
                              @Nullable AutoDoor existing
    ) {
        if (existing != null) {
            activity.setSelectedAutoDoor(existing);
            player.sendActionBar(PbText.of(
                Lang.raw(PlayerLang.of(player), "auto.auto_door_wand_listener.bind_or_select.1") + existing.formatRadius() + Lang.raw(PlayerLang.of(player), "auto.auto_door_wand_listener.bind_or_select.2")));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.6f);
            return;
        }

        AutoDoor door = new AutoDoor(block.getX(), block.getY(), block.getZ());
        if (!lightShow.addAutoDoor(door)) {
            player.sendActionBar(PbText.of(Lang.raw(PlayerLang.of(player), "auto.auto_door_wand_listener.bind_or_select.3")));
            return;
        }

        activity.setSelectedAutoDoor(door);
        player.sendActionBar(PbText.of(
            Lang.raw(PlayerLang.of(player), "auto.auto_door_wand_listener.bind_or_select.4") + door.formatRadius() + Lang.raw(PlayerLang.of(player), "auto.auto_door_wand_listener.bind_or_select.5")));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.8f);
    }

    private void unbind(@NonNull Player player,
                        @NonNull EditActivity activity,
                        @NonNull LightShowSettings lightShow,
                        @Nullable AutoDoor existing
    ) {
        if (existing == null) {
            player.sendActionBar(PbText.of(Lang.raw(PlayerLang.of(player), "auto.auto_door_wand_listener.unbind.1")));
            return;
        }
        if (activity.getSelectedAutoDoor() == existing) activity.setSelectedAutoDoor(null);
        lightShow.removeAutoDoor(existing);
        player.sendActionBar(PbText.of(Lang.raw(PlayerLang.of(player), "auto.auto_door_wand_listener.unbind.2")));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
    }

    /**
     * Радиус меняется у той двери, на которую смотришь. Если она не привязана - у выбранной,
     * чтобы можно было подкрутить радиус, отойдя и глядя на кольцо со стороны.
     */
    private void changeRadius(@NonNull Player player,
                              @NonNull EditActivity activity,
                              @Nullable AutoDoor pointed,
                              boolean increase
    ) {
        AutoDoor door = pointed != null ? pointed : activity.getSelectedAutoDoor();
        if (door == null) {
            player.sendActionBar(PbText.of(Lang.raw(PlayerLang.of(player), "auto.auto_door_wand_listener.change_radius.1")));
            return;
        }

        double before = door.getRadius();
        door.setRadius(before + (increase ? AutoDoor.RADIUS_STEP : -AutoDoor.RADIUS_STEP));
        activity.setSelectedAutoDoor(door);

        if (door.getRadius() == before) {
            player.sendActionBar(PbText.of(Lang.raw(PlayerLang.of(player), "auto.auto_door_wand_listener.change_radius.2") + door.formatRadius() + Lang.raw(PlayerLang.of(player), "auto.auto_door_wand_listener.change_radius.3")));
            return;
        }

        player.sendActionBar(PbText.of(Lang.raw(PlayerLang.of(player), "auto.auto_door_wand_listener.change_radius.4") + door.formatRadius() + Lang.raw(PlayerLang.of(player), "auto.auto_door_wand_listener.change_radius.5")));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f,
            increase ? 1.9f : 0.9f);
    }

    /**
     * Сначала блок под прицелом, потом обычный клик: по нижней половине двери попасть
     * лучом проще, чем по верхней, но кликать строитель может куда угодно.
     */
    @Nullable
    private Block findDoorBlock(@NonNull Player player, @Nullable Block clicked) {
        if (AutoDoorEngine.isOpenable(clicked)) return clicked;

        RayTraceResult result = player.rayTraceBlocks(RAY_TRACE_DISTANCE);
        Block traced = result == null ? null : result.getHitBlock();
        if (AutoDoorEngine.isOpenable(traced)) return traced;

        if (clicked != null) {
            Block below = clicked.getRelative(org.bukkit.block.BlockFace.DOWN);
            if (AutoDoorEngine.isOpenable(below)) return below;
            Block above = clicked.getRelative(org.bukkit.block.BlockFace.UP);
            if (AutoDoorEngine.isOpenable(above)) return above;
        }
        return null;
    }
}
