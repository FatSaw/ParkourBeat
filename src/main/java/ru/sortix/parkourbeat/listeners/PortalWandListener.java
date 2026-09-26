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
import org.bukkit.block.BlockFace;
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
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.Portal;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import ru.sortix.parkourbeat.utils.text.PbText;
@RequiredArgsConstructor
public class PortalWandListener implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final double RAY_TRACE_DISTANCE = 120.0D;
    private static final Material WAND_MATERIAL = Material.FISHING_ROD;

    private final @NonNull ParkourBeat plugin;

    @NonNull
    private static NamespacedKey key(@NonNull ParkourBeat plugin) {
        return new NamespacedKey(plugin, "portal_wand");
    }

    public static boolean isWand(@NonNull ParkourBeat plugin, @Nullable ItemStack stack) {
        if (stack == null || stack.getType() != WAND_MATERIAL) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(key(plugin), PersistentDataType.STRING);
    }

    public static void give(@NonNull ParkourBeat plugin, @NonNull Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isWand(plugin, contents[slot])) player.getInventory().setItem(slot, null);
        }

        ItemStack stack = new ItemStack(WAND_MATERIAL);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(line(Lang.raw(PlayerLang.of(player), "auto.portal_wand_listener.give.1")));
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(line(Lang.raw(PlayerLang.of(player), "auto.portal_wand_listener.give.2")));
            lore.add(line(Lang.raw(PlayerLang.of(player), "auto.portal_wand_listener.give.3")));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(PlayerLang.of(player), "auto.portal_wand_listener.give.4")));
            lore.add(line(Lang.raw(PlayerLang.of(player), "auto.portal_wand_listener.give.5")));
            lore.add(line(Lang.raw(PlayerLang.of(player), "auto.portal_wand_listener.give.6")));
            meta.lore(lore);

            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, "1");
            stack.setItemMeta(meta);
        }

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

        Portal portal = editActivity.getSelectedPortal();
        if (portal == null) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.portal_wand_listener.on.1")));
            return;
        }

        RayTraceResult result = player.rayTraceBlocks(RAY_TRACE_DISTANCE);
        if (result == null || result.getHitPosition() == null) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.portal_wand_listener.on.2")));
            return;
        }

        Portal.Side side = leftClick ? portal.getEntry() : portal.getExit();
        Portal.Facing facing = facingOf(result.getHitBlockFace());
        Vector position = placeOnSurface(result, facing, side.getSize());

        Level level = editActivity.getLevel();
        if (!level.isPositionInside(position.getX(), position.getY(), position.getZ())) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.portal_wand_listener.on.3")));
            return;
        }

        side.setPosition(position);
        side.setFacing(facing);
        level.getLevelSettings().updateParticleLocations();

        player.sendMessage(PbText.of(leftClick
            ? Lang.raw(PlayerLang.of(player), "auto.portal_wand_listener.on.4") + side.format() + " &7(" + facing.getDisplayName() + ")"
            : Lang.raw(PlayerLang.of(player), "auto.portal_wand_listener.on.5") + side.format() + " &7(" + facing.getDisplayName() + ")"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.8f);
    }

    @NonNull
    private static Portal.Facing facingOf(@Nullable BlockFace face) {
        if (face == null) return Portal.Facing.WALL_Z;
        return switch (face) {
            case UP, DOWN -> Portal.Facing.FLOOR;
            case EAST, WEST -> Portal.Facing.WALL_X;
            default -> Portal.Facing.WALL_Z;
        };
    }

    /**
     * Точку сдвигаем от поверхности наружу, иначе рамка окажется внутри блока
     * и игрок при переходе застрянет в стене.
     */
    @NonNull
    private static Vector placeOnSurface(@NonNull RayTraceResult result,
                                         @NonNull Portal.Facing facing, double size) {
        Vector position = result.getHitPosition().clone();
        BlockFace face = result.getHitBlockFace();
        if (face == null) return position;

        double offset = 0.55D;
        position.add(new Vector(
            face.getModX() * offset,
            face.getModY() * offset,
            face.getModZ() * offset));

        if (facing != Portal.Facing.FLOOR && face.getModY() == 0) {
            position.setY(position.getY() + (size / 2.0D) - 0.5D);
        }
        return position;
    }
}
