package ru.sortix.parkourbeat.listeners;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.activity.type.PlayActivity;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.utils.ChatLinks;
import ru.sortix.parkourbeat.world.TeleportUtils;

import java.util.function.Consumer;

public final class GamesListener implements Listener {
    private final ParkourBeat plugin;
    private final ActivityManager activityManager;
    private final Consumer<Player> onPlayerTeleportToLobby = player -> {
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(5.0F);
        player.setExhaustion(0.0F);
        player.setFireTicks(-40);
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        org.bukkit.plugin.Plugin pl = org.bukkit.Bukkit.getPluginManager().getPlugin("ParkourBeat");
        if (pl instanceof ParkourBeat) {
            ((ParkourBeat) pl).get(ru.sortix.parkourbeat.inventory.LobbyItems.class).giveAll(player);
        }
    };
    private final ChatRenderer.ViewerUnaware viewerUnaware = new ChatRenderer.ViewerUnaware() {
        @Override
        public @NonNull Component render(@NonNull Player source,
                                         @NonNull Component sourceDisplayName,
                                         @NonNull Component message
        ) {
            Component rank = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand()
                .deserialize(GamesListener.this.plugin
                    .get(ru.sortix.parkourbeat.rating.StatisticsManager.class)
                    .getRankLabel(source.getUniqueId()));
            TextColor nameColor =
                source.hasPermission(PermissionConstants.COLORED_CHAT) ? NamedTextColor.RED : NamedTextColor.WHITE;
            Component renderedMessage = ChatLinks.makeLinksClickable(message).color(NamedTextColor.WHITE);
            // Корень намеренно пустой: если бы мы дописывали к самому рангу,
            // ник стал бы его дочерним элементом и унаследовал цвет с жирностью.
            return Component.empty()
                .append(rank)
                .append(Component.text(" ", NamedTextColor.WHITE))
                .append(sourceDisplayName.color(nameColor)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
                .append(Component.text(" -> ", NamedTextColor.WHITE))
                .append(renderedMessage);
        }
    };

    public GamesListener(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.activityManager = plugin.get(ActivityManager.class);

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            TeleportUtils.teleportAsync(plugin, player, Settings.getLobbySpawn());
            if (this.activityManager.getActivity(player) == null) {
                this.onPlayerTeleportToLobby.accept(player);
            }
        }
    }

    @EventHandler
    private void on(PlayerTeleportEvent event) {
        World from = event.getFrom().getWorld();
        World to = event.getTo().getWorld();
        if (from == to) return;

        UserActivity oldActivity = this.activityManager.getActivity(event.getPlayer());
        if (oldActivity == null) {
            event.getPlayer().sendMessage("Текущая активность не найдена");
        } else if (oldActivity.getLevel().getWorld() == from) {
            event.getPlayer().sendMessage("Завершаем старую активность (" + from.getName() + ")");
        } else if (oldActivity.getLevel().getWorld() == to) {
            event.getPlayer().sendMessage("Запускаем новую активность (" + to.getName() + ")");
        } else {
            event.getPlayer()
                .sendMessage("Миры " + from.getName() + " и " + to.getName() + " не относятся к активностям");
        }
    }

    @EventHandler
    private void on(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        this.onPlayerTeleportToLobby.accept(player);
        ru.sortix.parkourbeat.player.music.MusicTracksManager musicManager =
            this.plugin.get(ru.sortix.parkourbeat.player.music.MusicTracksManager.class);

        ru.sortix.parkourbeat.player.music.MusicTrack lobbyBasePack =
            new ru.sortix.parkourbeat.player.music.MusicTrack(
                musicManager.getPlatform(),
                "ParkourBeatCore",
                "ParkourBeatCore",
                false
            );
        musicManager.getPlatform().setResourcepackTrack(player, lobbyBasePack, success -> {
            if (!success) {
                this.plugin.getLogger().warning("Не удалось отправить базовый ресурс-пак игроку " + player.getName());
            } else {
                this.plugin.getLogger().info("Команда на базовый ресурс-пак успешно отправлена игроку " + player.getName());
            }
        });
    }

    @EventHandler
    private void on(PlayerSpawnLocationEvent event) {
        event.setSpawnLocation(Settings.getLobbySpawn());
    }

    @EventHandler
    private void on(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (this.isLobby(player.getWorld())) {
            event.setRespawnLocation(Settings.getLobbySpawn());
        } else {
            this.doActivityAction(player, activity -> {
                event.setRespawnLocation(activity.getLevel().getSpawn());
                activity.startActivity();
            });
        }
    }

    @EventHandler
    private void on(PlayerQuitEvent event) {
        this.doActivityAction(event.getPlayer(), UserActivity::endActivity);
    }

    @EventHandler
    private void on(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (this.isNotInLobbyOrLevel(player)) return;
        } else {
            Level level = this.plugin.get(LevelsManager.class).getLoadedLevel(event.getEntity().getWorld());
            if (level == null || level.isEditing()) return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    private void on(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (this.isNotInLobbyOrLevel(player)) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void on(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (this.isNotInLobbyOrLevel(player)) return;

        event.setKeepInventory(true);
        event.getDrops().clear();
        player.spigot().respawn();

        this.doActivityAction(player, UserActivity::startActivity);
    }

    @EventHandler
    private void on(FoodLevelChangeEvent event) {
        if (this.isNotInLobbyOrLevel((Player) event.getEntity())) return;
        if (event.getFoodLevel() != 20) {
            event.setFoodLevel(20);
        }
    }

    @EventHandler
    private void on(PlayerDropItemEvent event) {
        if (this.isNotInLobbyOrLevel(event.getPlayer())) return;
        UserActivity activity = this.activityManager.getActivity(event.getPlayer());
        if (activity instanceof EditActivity && !((EditActivity) activity).isTesting()) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void onActivityEvent(PlayerMoveEvent event) {
        this.doActivityAction(event.getPlayer(), activity -> activity.on(event));
    }

    @EventHandler
    private void onActivityEvent(PlayerToggleSprintEvent event) {
        this.doActivityAction(event.getPlayer(), activity -> activity.on(event));
    }

    @EventHandler
    private void onActivityEvent(PlayerToggleSneakEvent event) {
        this.doActivityAction(event.getPlayer(), activity -> activity.on(event));
    }

    @EventHandler
    private void on(PlayerArmorStandManipulateEvent event) {
        this.cancelIfCantModify(
            event, event.getPlayer(), event.getRightClicked().getLocation());
    }

    @EventHandler
    private void on(PlayerInteractAtEntityEvent event) {
        this.cancelIfCantModify(
            event, event.getPlayer(), event.getRightClicked().getLocation());
    }

    @EventHandler
    private void on(BlockPlaceEvent event) {
        this.cancelIfCantModify(event, event.getPlayer(), event.getBlock().getLocation());
    }

    @EventHandler
    private void on(BlockBreakEvent event) {
        this.cancelIfCantModify(event, event.getPlayer(), event.getBlock().getLocation());
    }

    @EventHandler
    private void on(VehicleDamageEvent event) {
        if (event.getAttacker() instanceof Player) {
            Player player = (Player) event.getAttacker();
            this.cancelIfCantModify(
                event, player, event.getVehicle().getLocation());
        }
    }

    @EventHandler
    private void on(VehicleDestroyEvent event) {
        if (event.getAttacker() instanceof Player) {
            Player player = (Player) event.getAttacker();
            this.cancelIfCantModify(
                event, player, event.getVehicle().getLocation());
        }
    }

    @EventHandler
    private void on(VehicleEntityCollisionEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            this.cancelIfCantModify(
                event, player, event.getVehicle().getLocation());
        }
    }

    @EventHandler
    private void on(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player) {
            Player player = (Player) event.getEntered();
            this.cancelIfCantModify(
                event, player, event.getVehicle().getLocation());
        }
    }

    private void cancelIfCantModify(@NonNull Cancellable event, @NonNull Player player, @NonNull Location location) {
        if (this.isPlayerCanModify(player, location)) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void on(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UserActivity activity = this.activityManager.getActivity(player);
        if (activity instanceof PlayActivity) {
            PlayActivity playActivity = (PlayActivity) activity;
            playActivity.onPracticeInteract(event);
            if (event.isCancelled()) return;
        }

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (this.isPlayerCanModify(player, block.getLocation())) return;
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    private boolean isPlayerCanModify(@NonNull Player player, @NonNull Location location) {
        UserActivity activity = this.activityManager.getActivity(player);
        if (activity == null) {
            if (this.isLobby(location.getWorld())) {
                return player.hasPermission(PermissionConstants.EDIT_LOBBY);
            } else {
                return true;
            }
        }
        if (!(activity instanceof EditActivity) || ((EditActivity) activity).isTesting()) return false;
        return activity.getLevel().isLocationInside(location);
    }

    @EventHandler
    private void on1(PlayerMoveEvent event) {
        double yPos = event.getTo().getY();
        if (event.getFrom().getY() <= yPos) return;

        Player player = event.getPlayer();
        UserActivity activity = this.activityManager.getActivity(player);
        if (activity != null) {
            if (yPos > activity.getFallHeight()) return;
            activity.onPlayerFall();
        } else if (this.isLobby(player.getWorld())) {
            if (yPos > 0) return;
            TeleportUtils.teleportAsync(this.plugin, player, player.getWorld().getSpawnLocation());
        }
    }

    private void doActivityAction(@NonNull Player player, @NonNull Consumer<UserActivity> activityConsumer) {
        UserActivity activity = this.activityManager.getActivity(player);
        if (activity == null) return;
        if (activity.isValidWorld(player.getWorld())) {
            activityConsumer.accept(activity);
            return;
        }
        this.plugin.getLogger().severe("Detected wrong activity world of player " + player.getName() + ". "
            + "Expected: " + activity.getLevel().getWorld().getName() + ". "
            + "Got: " + player.getLocation().getWorld().getName()
        );
        this.activityManager.switchActivity(player, null, null);
        player.sendMessage("Произошла техническая ошибка, приносим свои извинения");
    }

    private boolean isLobby(@NonNull World world) {
        return world == Settings.getLobbySpawn().getWorld();
    }

    private boolean isNotInLobbyOrLevel(@NonNull Player player) {
        return this.activityManager.getActivity(player) == null && !this.isLobby(player.getWorld());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private void on(AsyncChatEvent event) {
        String plainText = net.kyori.adventure.text.serializer.plain.PlainComponentSerializer.plain().serialize(event.message());
        if (ru.sortix.parkourbeat.utils.StringUtils.containsCustomFont(plainText)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(net.kyori.adventure.text.Component.text("MrBeast, this is you ?", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        event.renderer(ChatRenderer.viewerUnaware(this.viewerUnaware));
    }

    @EventHandler
    private void on(ChunkUnloadEvent event) {
        Level level = this.plugin.get(LevelsManager.class).getLoadedLevel(event.getChunk().getWorld());
        if (level == null) return;

        if (!level.isChunkInside(event.getChunk())) {
            event.setSaveChunk(false);
        }
    }
}
