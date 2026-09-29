// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/listeners/GamesListener.java
package ru.sortix.parkourbeat.listeners;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

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
import ru.sortix.parkourbeat.player.DebugModeManager;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.activity.type.PlayActivity;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.utils.ChatLinks;
import ru.sortix.parkourbeat.world.TeleportUtils;

import java.util.function.Consumer;

import ru.sortix.parkourbeat.utils.text.PbText;
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
        player.setExp(0.0F);
        ru.sortix.parkourbeat.levels.settings.SkyType.reset(player);
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
            Component rank = PbText.of(GamesListener.this.plugin
                    .get(ru.sortix.parkourbeat.rating.StatisticsManager.class)
                    .getRankLabel(source.getUniqueId()));
            TextColor nameColor =
                source.hasPermission(PermissionConstants.COLORED_CHAT) ? NamedTextColor.RED : NamedTextColor.WHITE;
            Component renderedMessage = ChatLinks.makeLinksClickable(message).color(NamedTextColor.WHITE);
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

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onExpChange(PlayerExpChangeEvent event) {
        event.setAmount(0);
    }

    @EventHandler
    private void on(PlayerTeleportEvent event) {
        World from = event.getFrom().getWorld();
        World to = event.getTo().getWorld();
        if (from == to) return;

        DebugModeManager debug = this.plugin.get(DebugModeManager.class);
        if (!debug.isEnabled(event.getPlayer())) return;

        UserActivity oldActivity = this.activityManager.getActivity(event.getPlayer());
        if (oldActivity == null) {
            debug.send(event.getPlayer(), Lang.raw(PlayerLang.of(event.getPlayer()), "auto.games_listener.on.1"));
        } else if (oldActivity.getLevel().getWorld() == from) {
            debug.send(event.getPlayer(), Lang.raw(PlayerLang.of(event.getPlayer()), "auto.games_listener.on.2") + from.getName() + ")");
        } else if (oldActivity.getLevel().getWorld() == to) {
            debug.send(event.getPlayer(), Lang.raw(PlayerLang.of(event.getPlayer()), "auto.games_listener.on.3") + to.getName() + ")");
        } else {
            debug.send(event.getPlayer(),
                Lang.raw(PlayerLang.of(event.getPlayer()), "auto.games_listener.on.4") + from.getName() + Lang.raw(PlayerLang.of(event.getPlayer()), "auto.games_listener.on.5") + to.getName() + Lang.raw(PlayerLang.of(event.getPlayer()), "auto.games_listener.on.6"));
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
    private void onActivityEvent(com.destroystokyo.paper.event.player.PlayerJumpEvent event) {
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
        if (!event.isCancelled()) this.markWorldChanged(event.getBlock().getWorld());
    }

    @EventHandler
    private void on(BlockBreakEvent event) {
        this.cancelIfCantModify(event, event.getPlayer(), event.getBlock().getLocation());
        if (!event.isCancelled()) this.markWorldChanged(event.getBlock().getWorld());
    }

    /**
     * Автосохранение трогает мир только если в нём реально что-то поменяли.
     * Без этой пометки world.save() каждые 15 секунд гонял бы все загруженные чанки впустую.
     */
    private void markWorldChanged(@NonNull World world) {
        this.plugin.get(LevelsManager.class).markWorldChanged(world);
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

    @EventHandler
    private void on(PlayerAnimationEvent event) {
        if (event.getAnimationType() == org.bukkit.event.player.PlayerAnimationType.ARM_SWING) {
            this.plugin.get(ru.sortix.parkourbeat.replay.ReplayManager.class).recordSwing(event.getPlayer());
        }
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

    /**
     * Подсказка строителю о том, почему зона падения его не убивает.
     * <p>
     * Была статической константой. Текст зависит от языка игрока, а статика
     * собирается при загрузке класса, когда языка ещё нет, - поэтому сообщение
     * собирается на месте.
     */
    @NonNull
    private static net.kyori.adventure.text.Component missingPathMessage(@NonNull Player player) {
        return net.kyori.adventure.text.Component.text(
            Lang.raw(PlayerLang.of(player), "editor.missingpath"),
            net.kyori.adventure.text.format.NamedTextColor.AQUA);
    }

    private static final long MISSING_PATH_COOLDOWN_MILLIS = 300_000L;
    private final java.util.Map<java.util.UUID, Long> missingPathNotices =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Уровень со своими текстурами собран под конкретный диапазон версий: на другом клиенте
     * пак либо не применится, либо применится криво. Проще не пускать, чем показывать кашу.
     */
    public static boolean canJoinLevel(@NonNull Player player,
                                       @NonNull ru.sortix.parkourbeat.levels.settings.GameSettings settings) {
        if (!settings.isCustomTextures()) return true;

        ru.sortix.parkourbeat.levels.TextureVersionRange range = settings.getTextureVersionRange();
        if (range == null) return true;

        return range.accepts(player);
    }

    private void notifyMissingPath(@NonNull Player player) {
        long now = System.currentTimeMillis();
        Long last = this.missingPathNotices.get(player.getUniqueId());
        if (last != null && now - last < MISSING_PATH_COOLDOWN_MILLIS) return;

        this.missingPathNotices.put(player.getUniqueId(), now);
        player.sendMessage(missingPathMessage(player));
    }

    /**
     * Автомаркеры: во время теста каждый прыжок оставляет точку. Так строителю не нужно
     * успевать кликать - он просто пробегает уровень так, как задумал.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void on(com.destroystokyo.paper.event.player.PlayerJumpEvent event) {
        Player player = event.getPlayer();
        UserActivity activity = this.activityManager.getActivity(player);
        if (!(activity instanceof EditActivity)) return;

        EditActivity editActivity = (EditActivity) activity;
        if (!editActivity.isTesting() || !editActivity.isAutoJumpMarkers()) return;

        ru.sortix.parkourbeat.levels.settings.HelperMarker marker =
            new ru.sortix.parkourbeat.levels.settings.HelperMarker(
                player.getLocation().toVector(),
                ru.sortix.parkourbeat.levels.settings.HelperMarker.Kind.LEFT);

        if (!editActivity.getLevel().getLightShow().addHelperMarker(marker)) return;

        player.sendActionBar(PbText.of(Lang.raw(PlayerLang.of(player), "auto.games_listener.on.7")
                + editActivity.getLevel().getLightShow().getHelperMarkers().size() + ")"));
    }

    @EventHandler
    private void on1(PlayerMoveEvent event) {
        double yPos = event.getTo().getY();
        if (event.getFrom().getY() <= yPos) return;

        Player player = event.getPlayer();
        UserActivity activity = this.activityManager.getActivity(player);
        if (activity != null) {
            if (yPos > activity.getFallHeight()) return;
            if (activity.isOutsidePathSpan()) {
                this.notifyMissingPath(player);
                return;
            }
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
        this.plugin.get(DebugModeManager.class).send(player,
            Lang.raw(PlayerLang.of(player), "auto.games_listener.do_activity_action.1"));
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
