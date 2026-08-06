package ru.sortix.parkourbeat.activity.type;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.game.movement.GameMoveHandler;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.item.ItemsManager;
import ru.sortix.parkourbeat.item.editor.type.TestGameItem;
import ru.sortix.parkourbeat.levels.DirectionChecker;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.Waypoint;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.physics.CustomPhysicsManager;
import ru.sortix.parkourbeat.rating.JumpResult;
import ru.sortix.parkourbeat.rating.JumpTriggerEvaluator;
import ru.sortix.parkourbeat.rating.Modifier;
import ru.sortix.parkourbeat.rating.ModifierSet;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.world.TeleportUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayActivity extends UserActivity {

    @Getter
    private final @NonNull Game game;
    private final boolean isEditorGame;
    private final CustomPhysicsManager physicsManager;

    private static final long JUMP_COOLDOWN_MILLIS = 120L;
    private boolean jumping = false;
    private long lastJumpAt = 0L;
    private final java.util.Random jumpRandom = new java.util.Random();

    @Getter
    private Location lastPlayerJumpLocation = null;

    private final List<Waypoint> triggerWaypoints = new ArrayList<>();
    private double[] triggerDistances = new double[0];
    private int nextTriggerIndex = 0;

    private PlayActivity(@NonNull Game game, boolean isEditorGame) {
        super(game.getPlugin(), game.getPlayer(), game.getLevel());
        this.game = game;
        this.isEditorGame = isEditorGame;
        this.game.setDisplayTimecode(isEditorGame);
        this.physicsManager = this.plugin.get(CustomPhysicsManager.class);

        if (!isEditorGame && game.getLevel().getLevelSettings().getGameSettings().getMusicTrack() != null) {
            game.getPlayer().sendMessage(LangOptions.level_play_music_notice.getComponent(game.getPlayer()));
        }
    }

    @NonNull
    public static CompletableFuture<PlayActivity> createAsync(@NonNull ParkourBeat plugin,
                                                              @NonNull Player player,
                                                              @NonNull UUID levelId,
                                                              boolean isEditorGame
    ) {
        UserActivity activity = plugin.get(ActivityManager.class).getActivity(player);
        if (activity instanceof PlayActivity
            && activity.getLevel().getUniqueId().equals(levelId)
            && ((PlayActivity) activity).isEditorGame == isEditorGame
        ) {
            return CompletableFuture.completedFuture((PlayActivity) activity);
        }

        GameSettings targetSettings = plugin.get(LevelsManager.class).getAvailableLevelSettings(levelId);
        if (targetSettings != null && !targetSettings.isAccessibleForPlaying(player, true)) {
            LangOptions.level_play_noaccess.sendMsg(player);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<PlayActivity> result = new CompletableFuture<>();
        ModifierSet modifiers = isEditorGame
            ? new ModifierSet()
            : plugin.get(ru.sortix.parkourbeat.rating.StatisticsManager.class)
            .getSelectedModifiers(player.getUniqueId());

        Game.createAsync(plugin, player, levelId, true, modifiers).thenAccept(game -> {
            if (game == null) {
                result.complete(null);
                return;
            }

            if (!game.getLevel().isLevelAccessibleForPlaying(player, true, true)) {
                result.complete(null);
                return;
            }

            result.complete(new PlayActivity(game, isEditorGame));
        });
        return result;
    }

    @Override
    public void startActivity() {
        physicsManager.addPlayer(player, level);
        // Перечитываем модификаторы ДО всего остального: иначе после выключения
        // PRACTICE хотбар, полёт и счётчики остались бы от прошлого забега.
        this.game.refreshModifiers();
        this.game.resetLevelGame(LangOptions.level_play_title_preparing.getComponent(player), null, false);
        this.game.resetRunProgress();

        this.player.setGameMode(GameMode.ADVENTURE);

        for (PotionEffect effect : this.player.getActivePotionEffects()) {
            this.player.removePotionEffect(effect.getType());
        }

        this.player.getInventory().clear();
        if (this.isEditorGame) {
            this.plugin.get(ItemsManager.class).putItem(this.player, TestGameItem.class);
            this.player.setFlying(false);
            this.player.setAllowFlight(false);
        } else if (!this.isEditorGame && this.game.hasModifier(Modifier.PRACTICE)) {
            this.setupPracticeHotbar();
        }

        if (!this.isEditorGame && this.game.hasModifier(Modifier.PRACTICE)) {
            this.player.setAllowFlight(true);
            this.player.setFlying(true);
        } else {
            this.player.setFlying(false);
            this.player.setAllowFlight(false);
        }

        this.game.onEnterLevel();
        this.buildTriggerDistances();
        this.lastPlayerJumpLocation = null;

        // Камеру доворачиваем на следующий тик — к этому моменту игрок уже
        // гарантированно стоит на точке спавна уровня.
        this.plugin.getServer().getScheduler().runTask(this.plugin, this.game::applyAutoLook);
    }

    private void setupPracticeHotbar() {
        ItemStack stopItem = ItemUtils.create(Material.DIAMOND, meta -> {
            meta.displayName(net.kyori.adventure.text.Component.text("Закончить практику")
                .color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        });
        this.player.getInventory().setItem(0, stopItem);
    }

    private void buildTriggerDistances() {
        ru.sortix.parkourbeat.levels.settings.LevelSettings settings = this.getLevel().getLevelSettings();
        DirectionChecker checker = settings.getDirectionChecker();
        double startPos = settings.getStartPosition();

        this.triggerWaypoints.clear();
        List<Waypoint> list = new ArrayList<>();
        for (Waypoint waypoint : settings.getWorldSettings().getWaypoints()) {
            if (waypoint.getHeight() <= 0) continue;
            list.add(waypoint);
        }

        list.sort((w1, w2) -> {
            double c1 = Math.abs(checker.getCoordinate(w1.getLocation()) - startPos);
            double c2 = Math.abs(checker.getCoordinate(w2.getLocation()) - startPos);
            return Double.compare(c1, c2);
        });

        this.triggerWaypoints.addAll(list);
        this.triggerDistances = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            double coord = checker.getCoordinate(list.get(i).getLocation());
            this.triggerDistances[i] = Math.abs(coord - startPos);
        }
        this.resetTriggerIndexToPosition(0.0D);
    }

    public void resetTriggerIndexToPosition(double playerDistance) {
        this.nextTriggerIndex = 0;
        while (this.nextTriggerIndex < this.triggerDistances.length) {
            if (this.triggerDistances[this.nextTriggerIndex] >= playerDistance - JumpTriggerEvaluator.FRONT_OK_RADIUS) {
                break;
            }
            this.nextTriggerIndex++;
        }
    }

    @Override
    public void on(@NonNull PlayerMoveEvent event) {
        Game.State state = this.game.getCurrentState();
        GameMoveHandler gameMoveHandler = this.game.getGameMoveHandler();

        if (state == Game.State.PREPARING) {
            gameMoveHandler.onPreparingState(event);
        } else if (state == Game.State.READY) {
            gameMoveHandler.onReadyState(this.player);
        } else if (state == Game.State.RUNNING) {
            gameMoveHandler.onRunningState(this.player, event.getFrom(), event.getTo());
            this.detectJump(event);
            this.evaluateTriggers();
        }
    }

    private void detectJump(@NonNull PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        boolean movingUp = to.getY() > from.getY();
        boolean onGround = this.player.isOnGround();

        if (onGround) {
            this.jumping = false;
            return;
        }
        if (this.jumping || !movingUp) return;

        long now = System.currentTimeMillis();
        if (now - this.lastJumpAt < JUMP_COOLDOWN_MILLIS) return;
        this.lastJumpAt = now;
        this.jumping = true;

        this.lastPlayerJumpLocation = this.player.getLocation().clone();

        DirectionChecker checker = this.getLevel().getLevelSettings().getDirectionChecker();
        Location playerLoc = this.player.getLocation();
        double playerCoord = checker.getCoordinate(playerLoc);

        int bestIdx = -1;
        double minEffectiveDelta = Double.MAX_VALUE;
        double bestSignedDelta = 0.0D;

        for (int i = this.nextTriggerIndex; i < this.triggerWaypoints.size(); i++) {
            Waypoint waypoint = this.triggerWaypoints.get(i);
            Location wLoc = waypoint.getLocation();

            double sideDist;
            if (checker.direction() == DirectionChecker.Direction.POSITIVE_X || checker.direction() == DirectionChecker.Direction.NEGATIVE_X) {
                sideDist = Math.abs(playerLoc.getZ() - wLoc.getZ());
            } else {
                sideDist = Math.abs(playerLoc.getX() - wLoc.getX());
            }
            double yDist = Math.abs(playerLoc.getY() - wLoc.getY());

            if (yDist > JumpTriggerEvaluator.MAX_Y_DISTANCE) {
                continue;
            }

            double wCoord = checker.getCoordinate(wLoc);
            double signedDelta = checker.isNegative() ? wCoord - playerCoord : playerCoord - wCoord;

            double effectiveDelta = Math.hypot(signedDelta, sideDist);

            if (effectiveDelta < minEffectiveDelta) {
                minEffectiveDelta = effectiveDelta;
                bestSignedDelta = signedDelta;
                bestIdx = i;
            }

            if (signedDelta < -JumpTriggerEvaluator.FRONT_OK_RADIUS) {
                break;
            }
        }

        if (bestIdx != -1 && minEffectiveDelta <= JumpTriggerEvaluator.FRONT_OK_RADIUS) {
            double evaluationDelta = minSignedDeltaSign(bestSignedDelta) * minEffectiveDelta;
            JumpResult result = JumpTriggerEvaluator.evaluate(evaluationDelta);

            if (result != JumpResult.MISS) {
                this.game.registerJump(result);
                this.nextTriggerIndex = bestIdx + 1;
            } else {
                this.handleMissOrSpecialCases();
            }
        } else {
            this.handleMissOrSpecialCases();
        }

        this.fireJumpEffect();
    }

    private double minSignedDeltaSign(double signedDelta) {
        return signedDelta < 0 ? -1.0D : 1.0D;
    }

    private void handleMissOrSpecialCases() {
        // Получаем текущий хитбокс игрока
        org.bukkit.util.BoundingBox pBox = this.player.getBoundingBox();

        // Создаем хитбокс прямо над головой игрока (на высоту прыжка ~0.8 блока).
        // Чуть-чуть сужаем его по краям (на 0.05), чтобы игрок не "цеплял" головой
        // стены, когда просто трется о них сбоку.
        org.bukkit.util.BoundingBox headBox = new org.bukkit.util.BoundingBox(
            pBox.getMinX() + 0.05,
            pBox.getMaxY(),
            pBox.getMinZ() + 0.05,
            pBox.getMaxX() - 0.05,
            pBox.getMaxY() + 0.8,
            pBox.getMaxZ() - 0.05
        );

        // Проверяем, пересекается ли область над головой с какими-либо непроходимыми блоками
        boolean isHeadHitter = ru.sortix.parkourbeat.world.BoundingBoxUtils.isBoundingBoxOverlapsWithAnyBlock(
            this.player.getWorld(),
            headBox,
            true, // Игнорировать блоки, хитбоксы которых фактически не пересекаются
            true  // Игнорировать проходимые блоки (воздух, таблички, нити, вода)
        );

        Location feet = this.player.getLocation();
        Block feetBlock = feet.getBlock();
        Block belowBlock = feet.clone().subtract(0, 0.5, 0).getBlock();

        boolean isSpecialBounce = feetBlock.getType() == Material.SLIME_BLOCK || belowBlock.getType() == Material.SLIME_BLOCK
            || isBed(feetBlock.getType()) || isBed(belowBlock.getType());

        if (!isHeadHitter && !isSpecialBounce) {
            this.game.registerJump(JumpResult.MISS);
            if (!this.isEditorGame) {
                this.game.applyDamage(2.0D);
            }
        }
    }

    private static boolean isBed(@NonNull Material material) {
        return org.bukkit.Tag.BEDS.isTagged(material) || material.name().endsWith("_BED");
    }

    private void evaluateTriggers() {
        if (this.game.getCurrentState() != Game.State.RUNNING) return;
        if (this.nextTriggerIndex >= this.triggerWaypoints.size()) return;

        DirectionChecker checker = this.getLevel().getLevelSettings().getDirectionChecker();
        double playerCoord = checker.getCoordinate(this.player.getLocation());

        while (this.nextTriggerIndex < this.triggerWaypoints.size()) {
            Waypoint waypoint = this.triggerWaypoints.get(this.nextTriggerIndex);
            double wCoord = checker.getCoordinate(waypoint.getLocation());
            double signedDelta = checker.isNegative() ? wCoord - playerCoord : playerCoord - wCoord;

            if (JumpTriggerEvaluator.isPassedUnjumped(signedDelta)) {
                this.game.registerJump(JumpResult.MISS);
                if (!this.isEditorGame) {
                    this.game.applyDamage(2.0D);
                }
                this.nextTriggerIndex++;
            } else {
                break;
            }
        }
    }

    private void fireJumpEffect() {
        ru.sortix.parkourbeat.levels.settings.LightShowSettings lightShow = this.getLevel().getLightShow();
        long songTimeMillis = this.game.getSongTimeMillis();

        ru.sortix.parkourbeat.levels.settings.JumpZone active = null;
        for (ru.sortix.parkourbeat.levels.settings.JumpZone zone : lightShow.getJumpZones()) {
            if (zone.contains(songTimeMillis)) {
                active = zone;
                break;
            }
        }
        if (active == null) active = lightShow.getDefaultJumpTrigger();
        if (active == null) return;

        if (active.getEffects().contains(ru.sortix.parkourbeat.levels.settings.JumpEffect.SOUND)) {
            ru.sortix.parkourbeat.world.JumpEffectSender.play(this.plugin, this.player,
                ru.sortix.parkourbeat.levels.settings.JumpEffect.SOUND, active.getSoundKey());
        }

        ru.sortix.parkourbeat.levels.settings.JumpEffect effect = active.nextEffect(this.jumpRandom);
        if (effect == null || effect == ru.sortix.parkourbeat.levels.settings.JumpEffect.SOUND) return;

        if (effect == ru.sortix.parkourbeat.levels.settings.JumpEffect.TIME_PUSH) {
            ru.sortix.parkourbeat.levels.LightShowRunner runner = this.game.getLightShowRunner();
            if (runner != null) runner.addTimePush(300L);
            return;
        }
        ru.sortix.parkourbeat.world.JumpEffectSender.play(this.plugin, this.player, effect, active.getSoundKey());
    }

    public void onPracticeInteract(PlayerInteractEvent event) {
        if (!this.isEditorGame && this.game.hasModifier(Modifier.PRACTICE)
            && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            ItemStack item = event.getItem();
            if (item != null && item.getType() == Material.DIAMOND) {
                event.setCancelled(true);
                this.player.getInventory().setItem(0, null);
                this.game.forceStopLevelGame();
                this.game.setCurrentState(Game.State.READY);
                TeleportUtils.teleportAsync(this.plugin, this.player, this.getLevel().getSpawn());
            }
        }
    }

    @Override
    public void onTick() {
        this.evaluateTriggers();

        if (!this.isEditorGame && this.game.getCurrentState() == Game.State.RUNNING && this.game.hasModifier(Modifier.PRACTICE)) {
            ItemStack slot0 = this.player.getInventory().getItem(0);
            if (slot0 == null || slot0.getType() != Material.DIAMOND) {
                this.setupPracticeHotbar();
            }
        }
    }

    @Override
    public void on(@NonNull PlayerToggleSprintEvent event) {
        if (this.game.getCurrentState() == Game.State.RUNNING) {
            this.game.getGameMoveHandler().onRunningState(event);
        }
    }

    @Override
    public void on(@NonNull PlayerToggleSneakEvent event) {
        if (event.isSneaking() && this.game.getCurrentState() == Game.State.RUNNING) {
            if (!this.isEditorGame && this.game.hasModifier(Modifier.PRACTICE)) {
                return;
            }
            this.game.failLevel(LangOptions.level_play_title_stopped.getComponent(player), null);
        }
    }

    @Override
    public int getFallHeight() {
        return this.getFallHeight(false);
    }

    @Override
    public void onPlayerFall() {
        this.game.failLevel(LangOptions.level_play_title_fall.getComponent(player), null);
    }

    @Override
    public void endActivity() {
        physicsManager.purgePlayer(player);
        this.player.setFlying(false);
        this.player.setAllowFlight(false);
        this.game.forceStopLevelGame();
        this.game.setCurrentState(Game.State.PREPARING);
        this.game.shutdown();
    }
}
