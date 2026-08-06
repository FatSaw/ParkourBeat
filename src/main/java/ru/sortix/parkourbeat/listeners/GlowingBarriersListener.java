package ru.sortix.parkourbeat.listeners;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.GlowDirectionWand;
import ru.sortix.parkourbeat.item.GlowingBarrierItems;
import ru.sortix.parkourbeat.item.ItemsManager;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.GlowExtension;
import ru.sortix.parkourbeat.levels.settings.GlowingBarrier;
import ru.sortix.parkourbeat.levels.settings.WorldSettings;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.world.GlowingBarriersManager;

import javax.annotation.Nullable;

@RequiredArgsConstructor
public class GlowingBarriersListener implements Listener {
    private final @NonNull ParkourBeat plugin;

    @Nullable
    private EditActivity getEditActivity(@NonNull Player player) {
        UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(player);
        if (!(activity instanceof EditActivity editActivity)) return null;
        if (editActivity.isTesting()) return null;
        return editActivity;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickBlock(org.bukkit.event.inventory.InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        EditActivity activity = this.getEditActivity(player);
        if (activity == null) return;

        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType() != Material.BARRIER) return;

        org.bukkit.util.RayTraceResult rayTrace = player.rayTraceBlocks(10);
        if (rayTrace == null || rayTrace.getHitBlock() == null || rayTrace.getHitBlock().getType() != Material.BARRIER) return;

        Block targetBlock = rayTrace.getHitBlock();

        Level level = activity.getLevel();
        if (targetBlock.getWorld() != level.getWorld()) return;

        WorldSettings worldSettings = level.getLevelSettings().getWorldSettings();
        GlowingBarrier barrier = worldSettings.findGlowingBarrier(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
        if (barrier != null) {
            String lang = player.getLocale().toLowerCase();
            ItemStack glowingStack = GlowingBarrierItems.createGlowing(
                this.plugin,
                lang,
                barrier.getColor(),
                barrier.getMode(),
                cursor.getAmount() == 0 ? 1 : cursor.getAmount()
            );
            event.setCursor(glowingStack);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void on(@NonNull BlockPlaceEvent event) {
        Player player = event.getPlayer();
        EditActivity activity = this.getEditActivity(player);
        if (activity == null) return;

        Block block = event.getBlockPlaced();
        if (block.getType() != Material.BARRIER) return;
        if (block.getWorld() != activity.getLevel().getWorld()) return;

        ItemStack stack = event.getItemInHand();
        if (!GlowingBarrierItems.isGlowing(this.plugin, stack)) return;

        Level level = activity.getLevel();
        WorldSettings worldSettings = level.getLevelSettings().getWorldSettings();

        GlowingBarrier barrier = new GlowingBarrier(
            block.getX(), block.getY(), block.getZ(),
            GlowingBarrierItems.readColor(this.plugin, stack),
            GlowingBarrierItems.readMode(this.plugin, stack));

        if (!worldSettings.addGlowingBarrier(barrier)) {
            LangOptions.level_editor_glowbarrier_limit.sendMsg(player);
            return;
        }

        this.plugin.get(GlowingBarriersManager.class).refresh(level);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void on(@NonNull BlockBreakEvent event) {
        EditActivity activity = this.getEditActivity(event.getPlayer());
        if (activity == null) return;

        Block block = event.getBlock();
        Level level = activity.getLevel();
        if (block.getWorld() != level.getWorld()) return;

        WorldSettings worldSettings = level.getLevelSettings().getWorldSettings();
        if (!worldSettings.removeGlowingBarrier(block.getX(), block.getY(), block.getZ())) return;

        this.plugin.get(GlowingBarriersManager.class)
            .remove(level, GlowingBarrier.getPositionKey(block));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(@NonNull PlayerDropItemEvent event) {
        if (this.getEditActivity(event.getPlayer()) == null) return;

        ItemStack stack = event.getItemDrop().getItemStack();
        if (this.plugin.get(ItemsManager.class).isRegisteredItem(stack)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(false);
        event.getItemDrop().remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(@NonNull CreatureSpawnEvent event) {
        if (!event.isCancelled()) return;
        if (!this.plugin.get(GlowingBarriersManager.class).isOwnEntity(event.getEntity())) return;
        event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(@NonNull PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        EditActivity activity = this.getEditActivity(player);
        if (activity == null) return;

        if (!(event.getRightClicked() instanceof org.bukkit.entity.Shulker)) return;
        org.bukkit.entity.Shulker shulker = (org.bukkit.entity.Shulker) event.getRightClicked();

        if (!this.plugin.get(GlowingBarriersManager.class).isOwnEntity(shulker)) return;

        event.setCancelled(true);

        Location loc = shulker.getLocation();
        Level level = activity.getLevel();
        GlowingBarrier barrier = level.getLevelSettings().getWorldSettings().findGlowingBarrier(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (barrier == null) return;

        ItemStack stack = player.getInventory().getItemInMainHand();
        String lang = player.getLocale().toLowerCase();

        if (GlowDirectionWand.isWand(this.plugin, stack)) {
            GlowExtension ext = GlowDirectionWand.getExtension(this.plugin, stack);
            barrier.setExtension(ext);
            this.plugin.get(GlowingBarriersManager.class).refresh(level);
        } else if (player.isSneaking()) {
            float nextPeek = barrier.getPeek() + 0.1f;
            if (nextPeek > 1.05f) {
                nextPeek = 0f;
            }
            nextPeek = Math.round(nextPeek * 10.0f) / 10.0f;
            barrier.setPeek(nextPeek);
            this.plugin.get(GlowingBarriersManager.class).refresh(level);

            giveWandIfMissing(player, lang);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(@NonNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        EditActivity activity = this.getEditActivity(player);
        if (activity == null) return;

        ItemStack stack = player.getInventory().getItemInMainHand();
        String lang = player.getLocale().toLowerCase();

        if (GlowDirectionWand.isWand(this.plugin, stack)) {
            event.setCancelled(true);
            if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                GlowExtension ext = GlowDirectionWand.getExtension(this.plugin, stack);
                player.getInventory().setItemInMainHand(GlowDirectionWand.createWand(this.plugin, lang, ext.next()));
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
                Block block = event.getClickedBlock();
                if (block == null || block.getType() != Material.BARRIER) {
                    org.bukkit.util.RayTraceResult rt = player.rayTraceBlocks(10);
                    if (rt != null && rt.getHitBlock() != null) {
                        block = rt.getHitBlock();
                    }
                }

                if (block != null && block.getType() == Material.BARRIER) {
                    Level level = activity.getLevel();
                    if (block.getWorld() == level.getWorld()) {
                        GlowingBarrier barrier = level.getLevelSettings().getWorldSettings().findGlowingBarrier(block.getX(), block.getY(), block.getZ());
                        if (barrier != null) {
                            GlowExtension ext = GlowDirectionWand.getExtension(this.plugin, stack);
                            barrier.setExtension(ext);
                            this.plugin.get(GlowingBarriersManager.class).refresh(level);
                        }
                    }
                }
            }
            return;
        }

        if (event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY && event.useItemInHand() == org.bukkit.event.Event.Result.DENY) {
            return;
        }

        if (stack.getType() == Material.BARRIER) {
            if (player.isSneaking()) {
                if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    int amount = stack.getAmount();
                    if (GlowingBarrierItems.isGlowing(this.plugin, stack)) {
                        player.getInventory().setItemInMainHand(GlowingBarrierItems.createPlain(lang, amount));
                        LangOptions.level_editor_glowbarrier_plain.sendMsgActionbar(player);
                    } else {
                        player.getInventory().setItemInMainHand(GlowingBarrierItems.createGlowing(
                            this.plugin, lang, ru.sortix.parkourbeat.levels.settings.GlowColor.DEFAULT, ru.sortix.parkourbeat.levels.settings.GlowMode.DEFAULT, amount));
                        LangOptions.level_editor_glowbarrier_glowing.sendMsgActionbar(player);
                    }
                    return;
                }
            }
        }

        if (player.isSneaking() && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR)) {
            Block block = event.getClickedBlock();
            if (block == null || block.getType() != Material.BARRIER) {
                org.bukkit.util.RayTraceResult rt = player.rayTraceBlocks(10);
                if (rt != null && rt.getHitBlock() != null) {
                    block = rt.getHitBlock();
                }
            }

            if (block != null && block.getType() == Material.BARRIER) {
                Level level = activity.getLevel();
                if (block.getWorld() == level.getWorld()) {
                    GlowingBarrier barrier = level.getLevelSettings().getWorldSettings().findGlowingBarrier(block.getX(), block.getY(), block.getZ());
                    if (barrier != null) {
                        event.setCancelled(true);

                        float nextPeek = barrier.getPeek() + 0.1f;
                        if (nextPeek > 1.05f) {
                            nextPeek = 0f;
                        }
                        nextPeek = Math.round(nextPeek * 10.0f) / 10.0f;
                        barrier.setPeek(nextPeek);
                        this.plugin.get(GlowingBarriersManager.class).refresh(level);

                        giveWandIfMissing(player, lang);
                    }
                }
            }
        }
    }

    private void giveWandIfMissing(Player player, String lang) {
        boolean hasWand = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (GlowDirectionWand.isWand(this.plugin, item)) {
                hasWand = true;
                break;
            }
        }
        if (!hasWand) {
            player.getInventory().addItem(GlowDirectionWand.createWand(this.plugin, lang, GlowExtension.UP));
            player.showTitle(net.kyori.adventure.title.Title.title(
                LangOptions.level_editor_glowwand_title.getComponent(lang),
                LangOptions.level_editor_glowwand_subtitle.getComponent(lang),
                net.kyori.adventure.title.Title.Times.of(
                    java.time.Duration.ofMillis(200),
                    java.time.Duration.ofMillis(2000),
                    java.time.Duration.ofMillis(500)
                )
            ));
        }
    }
}
