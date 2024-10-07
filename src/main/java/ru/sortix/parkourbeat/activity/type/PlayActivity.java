package ru.sortix.parkourbeat.activity.type;

import lombok.NonNull;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.potion.PotionEffect;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.game.movement.GameMoveHandler;
import ru.sortix.parkourbeat.item.ItemsManager;
import ru.sortix.parkourbeat.item.editor.type.TestGameItem;
import ru.sortix.parkourbeat.physics.CustomPhysicsManager;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayActivity extends UserActivity {

    private final @NonNull Game game;
    private final boolean isEditorGame;
    private final CustomPhysicsManager physicsManager;

    private PlayActivity(@NonNull Game game, boolean isEditorGame) {
        super(game.getPlugin(), game.getPlayer(), game.getLevel());
        this.game = game;
        this.isEditorGame = isEditorGame;
        this.physicsManager = this.plugin.get(CustomPhysicsManager.class);
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

        CompletableFuture<PlayActivity> result = new CompletableFuture<>();
        Game.createAsync(plugin, player, levelId, true).thenAccept(game -> {
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
        this.game.resetLevelGame(LangOptions.level_play_title_preparing.getComponent(player), null, false);

        this.player.setGameMode(GameMode.ADVENTURE);

        for (PotionEffect effect : this.player.getActivePotionEffects()) {
            this.player.removePotionEffect(effect.getType());
        }

        this.player.getInventory().clear();
        if (this.isEditorGame) {
            this.plugin.get(ItemsManager.class).putItem(this.player, TestGameItem.class);
        }
    }

    @Override
    public void on(@NonNull PlayerResourcePackStatusEvent event) {
        switch (event.getStatus()) {
            case ACCEPTED: {
            	LangOptions.level_play_resourcepackstatus_accepted.sendMsg(player);
                return;
            }
            case FAILED_DOWNLOAD: {
            	LangOptions.level_play_resourcepackstatus_failed.sendMsg(player);
                this.game.setCurrentState(Game.State.READY);
                return;
            }
            case DECLINED: {
            	LangOptions.level_play_resourcepackstatus_declined.sendMsg(player);
                this.game.setCurrentState(Game.State.READY);
                return;
            }
            case SUCCESSFULLY_LOADED: {
            	LangOptions.level_play_resourcepackstatus_success.sendMsg(player);
                this.game.setCurrentState(Game.State.READY);
                return;
            }
            default: {
                throw new IllegalArgumentException("Unknown status: " + event.getStatus());
            }
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
        }
    }

    @Override
    public void onTick() {
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
        this.game.forceStopLevelGame();
        this.game.setCurrentState(Game.State.PREPARING);
    }

}
