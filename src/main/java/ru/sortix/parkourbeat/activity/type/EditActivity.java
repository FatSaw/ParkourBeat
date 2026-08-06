package ru.sortix.parkourbeat.activity.type;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.inventory.type.editor.EditLevelMenu;
import ru.sortix.parkourbeat.item.ItemsManager;
import ru.sortix.parkourbeat.item.editor.EditorItem;
import ru.sortix.parkourbeat.item.editor.type.EditTrackPointsItem;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.LightShowPositions;
import ru.sortix.parkourbeat.levels.LightShowRunner;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.levels.settings.GlowMode;
import ru.sortix.parkourbeat.levels.settings.LevelBossBarColor;
import ru.sortix.parkourbeat.levels.settings.LightShowElement;
import ru.sortix.parkourbeat.levels.settings.SkyType;
import ru.sortix.parkourbeat.physics.CustomPhysicsManager;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.world.TeleportUtils;
import ru.sortix.parkourbeat.worldedit.WorldEditAccessManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class EditActivity extends UserActivity {
    @Getter
    @Setter
    private @NonNull Color currentColor = EditTrackPointsItem.DEFAULT_PARTICLES_COLOR;
    @Getter
    @Setter
    private @Nullable Color currentJumpColor = null;
    @Getter
    @Setter
    private double currentHeight = 0;
    @Getter
    @Setter
    private boolean infiniteTesting = true;
    @Getter
    @Setter
    private @Nullable LightShowElement selectedElement = null;
    @Getter
    @Setter
    private @NonNull GlowMode glowMode = GlowMode.DEFAULT;
    @Getter
    private boolean previewEnabled = true;
    private @Nullable LightShowRunner previewRunner = null;
    private @Nullable LevelBossBarColor previewBarColor = null;
    private @Nullable PlayActivity testingActivity = null;
    private @Nullable Location creativePosition = null;
    private @Nullable ItemStack[] creativeInventoryContents = null;
    private final CustomPhysicsManager physicsManager;

    private static final Particle.DustOptions START_MARKER_DUST = new Particle.DustOptions(Color.GREEN, 5.0f);
    private static final Particle.DustOptions FINISH_MARKER_DUST = new Particle.DustOptions(Color.RED, 5.0f);

    private EditActivity(@NonNull ParkourBeat plugin, @NonNull Player player, @NonNull Level level) {
        super(plugin, player, level);
        LangOptions.level_editor_success_start.sendMsg(player, new Placeholders("%level%", ((TextComponent)this.level.getDisplayName()).content()));
        this.level.getLevelSettings().updateParticleLocations();
        this.level.applyViewDistances();
        this.level.setEditing(true);
        this.physicsManager = plugin.get(CustomPhysicsManager.class);

        Placeholders namePlaceholder = new Placeholders("%name%", player.getName());
        for (Player editor : this.getAllEditors()) {
            if (editor == player) continue;
            LangOptions.level_editor_coeditor_joined.sendMsg(editor, namePlaceholder);
        }
    }

    @Nullable
    public PlayActivity getTestingActivity() {
        return this.testingActivity;
    }

    @NonNull
    public static CompletableFuture<EditActivity> createAsync(@NonNull ParkourBeat plugin,
                                                              @NonNull Player player,
                                                              @NonNull Level level
    ) {
        UserActivity activity = plugin.get(ActivityManager.class).getActivity(player);
        if (activity instanceof EditActivity
            && activity.getLevel().getUniqueId().equals(level.getUniqueId())) {
            return CompletableFuture.completedFuture((EditActivity) activity);
        }

        if (!level.isLevelAccessibleForEditing(player, true, true)) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<EditActivity> result = new CompletableFuture<>();
        Game.createAsync(plugin, player, level.getUniqueId(), false).thenAccept(game -> {
            if (game == null) {
                result.complete(null);
                return;
            }

            result.complete(new EditActivity(plugin, player, level));
        });
        return result;
    }

    @Override
    public void startActivity() {
        physicsManager.addPlayer(player, level);
        if (this.testingActivity != null) {
            this.testingActivity.startActivity();
        } else {
            this.player.setGameMode(GameMode.CREATIVE);
            this.player.setFlying(true);

            this.player.getInventory().clear();
            this.plugin.get(ItemsManager.class).putAllItems(this.player, EditorItem.class);

            this.level.getLevelSettings().getParticleController().startSpawnParticles(this.player);

            this.startPreview();

            int blockLimit = this.isOwner() || this.getGameSettings().isTrusted(this.player.getUniqueId()) ? 90000 : 5000;
            this.plugin.get(WorldEditAccessManager.class).grant(this.player, blockLimit);
        }
    }

    public void setPreviewEnabled(boolean previewEnabled) {
        if (this.previewEnabled == previewEnabled) return;
        this.previewEnabled = previewEnabled;
        if (this.isTesting()) return;
        if (previewEnabled) {
            this.startPreview();
        } else {
            this.stopPreview();
            SkyType.reset(this.player);
        }
    }

    private void startPreview() {
        this.stopPreview();
        if (!this.previewEnabled) return;
        this.previewRunner = new LightShowRunner(
            this.plugin, this.player, this.level.getLightShow(), barColor -> this.previewBarColor = barColor);
        this.previewRunner.startShow();
    }

    private void stopPreview() {
        if (this.previewRunner == null) return;
        this.previewRunner.shutdown();
        this.previewRunner = null;
        this.previewBarColor = null;
    }

    private void tickPreview() {
        LightShowRunner runner = this.previewRunner;
        if (runner == null) return;
        if (this.player.getWorld() != this.level.getWorld()) return;

        boolean onLevel = LightShowPositions.getSignedDistance(this.level, this.player.getLocation()) >= 0.0D;
        int timeMillis = LightShowPositions.toTimeMillis(this.level, this.player.getLocation());
        try {
            runner.tick(onLevel ? timeMillis : -1L);
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Unable to tick lightshow preview of player " + this.player.getName(), e);
            this.stopPreview();
            return;
        }

        if (onLevel) this.player.sendActionBar(this.buildPreviewActionBar(timeMillis));
    }

    @NonNull
    private net.kyori.adventure.text.Component buildPreviewActionBar(int timeMillis) {
        LevelBossBarColor barColor = this.previewBarColor != null
            ? this.previewBarColor
            : this.getGameSettings().getBossBarColor();

        double total = this.level.getLevelSettings().getTotalLevelDistance();
        double passed = Math.abs(this.level.getLevelSettings().getDirectionChecker()
            .getCoordinate(this.player.getLocation()) - this.level.getLevelSettings().getStartPosition());
        double fraction = total <= 0 ? 0 : Math.max(0, Math.min(1, passed / total));
        String percent = String.format(java.util.Locale.ROOT, "%.2f", fraction * 100);
        int m = Math.max(0, timeMillis / 60000);
        int s = Math.max(0, (timeMillis / 1000) % 60);
        int ms = Math.max(0, timeMillis % 1000);
        String preciseTimecode = String.format(java.util.Locale.ROOT, "%02d:%02d.%03d", m, s, ms);

        return net.kyori.adventure.text.Component.text(percent + "%")
            .color(barColor.getTextColor())
            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)
            .append(net.kyori.adventure.text.Component.text(" - ")
                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false))
            .append(net.kyori.adventure.text.Component.text(preciseTimecode)
                .color(barColor.getTextColor())
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
    }

    public void applyBaseSky() {
        this.startPreview();
    }

    public void applyBaseSkyToAllEditors() {
        ActivityManager activityManager = this.plugin.get(ActivityManager.class);
        for (Player editor : this.getAllEditors()) {
            if (!(activityManager.getActivity(editor) instanceof EditActivity editActivity)) continue;
            if (editActivity.isTesting()) continue;
            editActivity.applyBaseSky();
        }
    }

    @Override
    public void on(@NonNull PlayerMoveEvent event) {
        if (this.testingActivity != null) this.testingActivity.on(event);
    }

    @Override
    public void onTick() {
        if (this.testingActivity != null) {
            this.testingActivity.onTick();
            return;
        }

        this.tickPreview();
        this.renderEditorMarkers();
    }

    private void renderEditorMarkers() {
        if (this.player.getWorld() != this.level.getWorld()) return;

        Location startLoc = this.level.getLevelSettings().getStartWaypointLoc().clone().add(0, 1.5, 0);
        Location finishLoc = this.level.getLevelSettings().getFinishWaypointLoc().clone().add(0, 1.5, 0);

        this.player.spawnParticle(Particle.REDSTONE, startLoc, 1, 0, 0, 0, 0, START_MARKER_DUST);
        this.player.spawnParticle(Particle.REDSTONE, finishLoc, 1, 0, 0, 0, 0, FINISH_MARKER_DUST);
    }

    @Override
    public void on(@NonNull PlayerToggleSprintEvent event) {
        if (this.testingActivity != null) this.testingActivity.on(event);
    }

    @Override
    public void on(@NonNull PlayerToggleSneakEvent event) {
        if (this.testingActivity != null) this.testingActivity.on(event);
    }

    @Override
    public int getFallHeight() {
        return this.getFallHeight(this.testingActivity == null);
    }

    @Override
    public void onPlayerFall() {
        if (this.testingActivity != null) {
            this.testingActivity.onPlayerFall();
        } else {
            TeleportUtils.teleportAsync(this.getPlugin(), this.player, this.level.getSpawn());
        }
    }

    @Override
    public void endActivity() {
        this.plugin.get(WorldEditAccessManager.class).revoke(this.player);
        physicsManager.purgePlayer(player);
        if (this.testingActivity != null) this.testingActivity.endActivity();

        this.player.setGameMode(GameMode.ADVENTURE);
        this.player.getInventory().clear();

        this.stopPreview();
        SkyType.reset(this.player);

        this.level.getLevelSettings().getParticleController().stopSpawnParticlesForPlayer(this.player);

        LangOptions.level_editor_success_stop.sendMsg(player, new Placeholders("%level%", ((TextComponent)this.level.getDisplayName()).content()));

        Collection<Player> remainingEditors = this.getOtherEditors();

        Placeholders namePlaceholder = new Placeholders("%name%", this.player.getName());
        for (Player editor : remainingEditors) {
            LangOptions.level_editor_coeditor_left.sendMsg(editor, namePlaceholder);
        }

        if (!remainingEditors.isEmpty()) {
            return;
        }

        this.level.getLevelSettings().getParticleController().stopSpawnParticles();

        if (this.level.isEditing()) {
            this.level.setEditing(false);
            this.plugin.get(LevelsManager.class).saveLevelSettingsAndBlocks(this.level);
        }
    }

    public void startTesting() {
        if (this.testingActivity != null) throw new IllegalArgumentException("Testing already started");

        this.plugin.get(WorldEditAccessManager.class).revoke(this.player);

        PlayActivity.createAsync(this.plugin, this.player, this.level.getUniqueId(), true)
            .thenAccept(playActivity -> {
                if (playActivity == null) {
                    LangOptions.level_editor_test_fail_start.sendMsg(player);
                    return;
                }

                this.creativePosition = this.player.getLocation();
                TeleportUtils.teleportAsync(this.plugin, this.player, this.level.getSpawn()).thenAccept(success -> {
                    if (!success) {
                        LangOptions.level_editor_test_fail_start.sendMsg(player);
                        return;
                    }

                    this.creativeInventoryContents = this.player.getInventory().getContents();
                    this.player.getInventory().clear();

                    this.level.getLevelSettings().getParticleController().stopSpawnParticlesForPlayer(this.player);
                    this.stopPreview();

                    this.testingActivity = playActivity;
                    this.testingActivity.getGame().setAllowEndlessRun(this.infiniteTesting);
                    this.testingActivity.startActivity();

                    LangOptions.level_editor_test_success_start.sendMsgActionbar(player);
                });
            });
    }

    public void endTesting() {
        if (this.testingActivity == null) throw new IllegalArgumentException("Testing not started");

        Game testingGame = this.testingActivity.getGame();
        String timecode = testingGame.getSongTimecode();
        String coordinate = String.format(java.util.Locale.ROOT, "%.2f",
            this.level.getLevelSettings().getDirectionChecker().getCoordinate(this.player.getLocation()));

        TeleportUtils.teleportAsync(
            this.plugin,
            this.player,
            this.creativePosition == null ? this.level.getSpawn() : this.creativePosition
        ).thenAccept(success -> {
            this.creativePosition = null;

            if (!success) {
                LangOptions.level_editor_test_fail_stop.sendMsg(player);
                return;
            }

            this.testingActivity.endActivity();
            this.testingActivity = null;
            this.startActivity();

            this.player.getInventory().setContents(this.creativeInventoryContents);
            this.creativeInventoryContents = null;

            LangOptions.level_editor_test_success_stop.sendMsgActionbar(player);
            LangOptions.level_editor_test_success_stoptime.sendMsg(player,
                new Placeholders("%time%", timecode),
                new Placeholders("%coord%", coordinate));
        });
    }

    public boolean isTesting() {
        return this.testingActivity != null;
    }

    public boolean isOwner() {
        return this.getGameSettings().isOwner(this.player.getUniqueId());
    }

    @NonNull
    public GameSettings getGameSettings() {
        return this.level.getLevelSettings().getGameSettings();
    }

    @NonNull
    public Collection<Player> getAllEditors() {
        List<Player> result = new ArrayList<>();
        ActivityManager activityManager = this.plugin.get(ActivityManager.class);
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (!(activityManager.getActivity(player) instanceof EditActivity editActivity)) continue;
            if (editActivity.getLevel() != this.level) continue;
            result.add(player);
        }
        return result;
    }

    @NonNull
    public Collection<Player> getOtherEditors() {
        List<Player> result = new ArrayList<>();
        for (Player editor : this.getAllEditors()) {
            if (editor == this.player) continue;
            result.add(editor);
        }
        return result;
    }

    public <T extends EditLevelMenu> void updateInventoriesOfAllEditors(@NonNull Class<T> menuClass,
                                                                        @NonNull Consumer<T> updater
    ) {
        for (Player editor : this.getAllEditors()) {
            InventoryHolder holder = editor.getOpenInventory().getTopInventory().getHolder();
            if (holder == null) continue;
            if (!menuClass.isAssignableFrom(holder.getClass())) continue;
            updater.accept(menuClass.cast(holder));
        }
    }
}
