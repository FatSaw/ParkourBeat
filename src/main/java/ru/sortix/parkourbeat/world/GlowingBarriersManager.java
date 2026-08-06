package ru.sortix.parkourbeat.world;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GlowColor;
import ru.sortix.parkourbeat.levels.settings.GlowMode;
import ru.sortix.parkourbeat.levels.settings.GlowingBarrier;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GlowingBarriersManager implements PluginManager {
    private static final int ENSURE_PERIOD_TICKS = 40;
    private static final int ANIMATE_PERIOD_TICKS = 1;
    private static final int BLINK_HALF_PERIOD = 3;
    private static final int RGB_SLOW_STEPS = 8;
    private static final int RGB_FAST_STEPS = 2;
    private static final int INVISIBILITY_DURATION_TICKS = 1_000_000;
    private static final double DEFAULT_GLOW_DISTANCE = 3.0D;

    private final @NonNull ParkourBeat plugin;
    private final @NonNull NamespacedKey MARKER_KEY;
    private final Map<GlowColor, Team> teams = new EnumMap<>(GlowColor.class);
    private final Map<UUID, Map<String, GlowEntity>> spawned = new HashMap<>();

    private final BukkitTask ensureTask;
    private final BukkitTask animateTask;
    private int animationTick = 0;

    public GlowingBarriersManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.MARKER_KEY = new NamespacedKey(plugin, "glow_barrier");
        this.createTeams();

        this.ensureTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin, this::ensureAll, ENSURE_PERIOD_TICKS, ENSURE_PERIOD_TICKS);
        this.animateTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin, this::animate, ANIMATE_PERIOD_TICKS, ANIMATE_PERIOD_TICKS);
    }

    private void createTeams() {
        ScoreboardManager scoreboardManager = this.plugin.getServer().getScoreboardManager();
        if (scoreboardManager == null) {
            this.plugin.getLogger().severe("Unable to create glow teams: scoreboard manager not available");
            return;
        }
        Scoreboard scoreboard = scoreboardManager.getMainScoreboard();

        for (GlowColor color : GlowColor.values()) {
            Team team = scoreboard.getTeam(color.getTeamName());
            if (team != null) {
                try {
                    team.unregister();
                } catch (IllegalStateException ignored) {
                }
            }
            try {
                Team newTeam = scoreboard.registerNewTeam(color.getTeamName());
                newTeam.setColor(color.getChatColor());
                newTeam.setCanSeeFriendlyInvisibles(false);
                this.teams.put(color, newTeam);
            } catch (Exception e) {
                this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Unable to create glow team " + color.getTeamName(), e);
            }
        }
    }

    public void refresh(@NonNull Level level) {
        this.ensureLevel(level);
    }

    public void remove(@NonNull Level level, @NonNull String positionKey) {
        Map<String, GlowEntity> byPosition = this.spawned.get(level.getWorld().getUID());
        if (byPosition == null) return;
        GlowEntity glowEntity = byPosition.remove(positionKey);
        if (glowEntity != null) glowEntity.despawn();
    }

    private void ensureAll() {
        LevelsManager levelsManager = this.plugin.get(LevelsManager.class);

        for (World world : this.plugin.getServer().getWorlds()) {
            Level level = levelsManager.getLoadedLevel(world);
            if (level == null) {
                this.despawnWorld(world.getUID());
                continue;
            }
            try {
                this.ensureLevel(level);
            } catch (Exception e) {
                this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Unable to update glowing barriers of level " + level.getUniqueId(), e);
            }
        }

        Iterator<Map.Entry<UUID, Map<String, GlowEntity>>> iterator = this.spawned.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Map<String, GlowEntity>> entry = iterator.next();
            if (this.plugin.getServer().getWorld(entry.getKey()) != null) continue;
            for (GlowEntity glowEntity : entry.getValue().values()) glowEntity.despawn();
            iterator.remove();
        }
    }

    private void ensureLevel(@NonNull Level level) {
        World world = level.getWorld();
        List<GlowingBarrier> barriers = level.getLevelSettings().getWorldSettings().getGlowingBarriers();

        Map<String, GlowEntity> byPosition =
            this.spawned.computeIfAbsent(world.getUID(), key -> new HashMap<>());

        List<String> alive = new ArrayList<>(barriers.size());

        for (GlowingBarrier barrier : barriers) {
            String key = barrier.getPositionKey();

            if (!world.isChunkLoaded(barrier.getX() >> 4, barrier.getZ() >> 4)) {
                GlowEntity stale = byPosition.remove(key);
                if (stale != null) stale.despawn();
                continue;
            }

            alive.add(key);

            GlowEntity glowEntity = byPosition.get(key);
            if (glowEntity != null && glowEntity.isValid()) {
                glowEntity.barrier = barrier;
                if (glowEntity.entity.getAttachedFace() != barrier.getExtension().attachedFace) {
                    glowEntity.entity.setAttachedFace(barrier.getExtension().attachedFace);
                }
                float expectedPeek = barrier.getPeek();
                if (Math.abs(glowEntity.entity.getPeek() - expectedPeek) > 0.01f) {
                    glowEntity.entity.setPeek(expectedPeek);
                }
                continue;
            }

            GlowEntity spawnedEntity = this.spawn(world, barrier);
            if (spawnedEntity != null) byPosition.put(key, spawnedEntity);
        }

        byPosition.entrySet().removeIf(entry -> {
            if (alive.contains(entry.getKey())) return false;
            entry.getValue().despawn();
            return true;
        });
    }

    private boolean shouldGlow(@NonNull Level level, @NonNull GlowingBarrier barrier) {
        double distance = level.getLevelSettings().getWorldSettings().getGlowViewDistance();
        if (distance <= 0.0D) distance = DEFAULT_GLOW_DISTANCE;
        double squared = distance * distance;

        Location barrierLocation = barrier.toLocation(level.getWorld());

        for (Player player : level.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(barrierLocation) > squared) continue;
            return true;
        }
        return false;
    }

    @Nullable
    private GlowEntity spawn(@NonNull World world, @NonNull GlowingBarrier barrier) {
        Location location = barrier.toLocation(world);
        try {
            Shulker shulker = world.spawn(location, Shulker.class, entity -> {
                entity.setAI(false);
                entity.setPeek(barrier.getPeek());
                entity.setSilent(true);
                entity.setInvulnerable(true);
                entity.setPersistent(false);
                entity.setRemoveWhenFarAway(false);
                entity.setCollidable(false);
                entity.setGravity(false);
                entity.setGlowing(false);
                entity.setAttachedFace(barrier.getExtension().attachedFace);
                entity.addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY, INVISIBILITY_DURATION_TICKS, 0, false, false, false));
                entity.getPersistentDataContainer().set(
                    MARKER_KEY, PersistentDataType.STRING, barrier.getPositionKey());
            });

            if (!shulker.isValid()) {
                return null;
            }

            GlowEntity glowEntity = new GlowEntity(shulker, barrier);
            this.applyColor(glowEntity, barrier.getColor());
            return glowEntity;
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Unable to spawn glowing barrier at " + barrier.getPositionKey(), e);
            return null;
        }
    }

    public boolean isOwnEntity(@NonNull org.bukkit.entity.Entity entity) {
        return entity.getPersistentDataContainer().has(MARKER_KEY, PersistentDataType.STRING);
    }

    private void applyColor(@NonNull GlowEntity glowEntity, @NonNull GlowColor color) {
        if (glowEntity.appliedColor == color) return;

        String entry = glowEntity.entity.getUniqueId().toString();
        Team mainTeam = this.teams.get(color);
        if (mainTeam != null) {
            try { mainTeam.addEntry(entry); } catch (IllegalStateException ignored) {}
        }
        for (org.bukkit.entity.Player p : this.plugin.getServer().getOnlinePlayers()) {
            Scoreboard board = p.getScoreboard();
            if (board != null && board != this.plugin.getServer().getScoreboardManager().getMainScoreboard()) {
                Team team = board.getTeam(color.getTeamName());
                if (team != null) team.addEntry(entry);
            }
        }

        glowEntity.appliedColor = color;
    }

    private void animate() {
        this.animationTick++;

        LevelsManager levelsManager = this.plugin.get(LevelsManager.class);

        for (Map.Entry<UUID, Map<String, GlowEntity>> worldEntry : this.spawned.entrySet()) {
            World world = this.plugin.getServer().getWorld(worldEntry.getKey());
            if (world == null) continue;
            Level level = levelsManager.getLoadedLevel(world);
            if (level == null) continue;

            for (GlowEntity glowEntity : worldEntry.getValue().values()) {
                if (!glowEntity.isValid()) continue;

                if (glowEntity.entity.getAttachedFace() != glowEntity.barrier.getExtension().attachedFace) {
                    glowEntity.entity.setAttachedFace(glowEntity.barrier.getExtension().attachedFace);
                }

                float expectedPeek = glowEntity.barrier.getPeek();
                if (Math.abs(glowEntity.entity.getPeek() - expectedPeek) > 0.01f) {
                    glowEntity.entity.setPeek(expectedPeek);
                }

                GlowMode mode = glowEntity.barrier.getMode();

                boolean glowActiveNow;
                if (mode == GlowMode.BLINK) {
                    glowActiveNow = (this.animationTick % (BLINK_HALF_PERIOD * 2)) < BLINK_HALF_PERIOD;
                    this.applyColor(glowEntity, glowEntity.barrier.getColor());
                } else if (!mode.usesRainbow()) {
                    glowActiveNow = true;
                    this.applyColor(glowEntity, glowEntity.barrier.getColor());
                } else {
                    glowActiveNow = true;
                    int step = this.animationTick
                        / (mode == GlowMode.RGB_FAST ? RGB_FAST_STEPS : RGB_SLOW_STEPS);
                    this.applyColor(glowEntity, GlowColor.RAINBOW[Math.floorMod(step, GlowColor.RAINBOW.length)]);
                }

                this.updatePerPlayerGlow(level, glowEntity, glowActiveNow);
            }
        }
    }

    private void updatePerPlayerGlow(@NonNull Level level, @NonNull GlowEntity glowEntity, boolean glowActiveNow) {
        double distance = level.getLevelSettings().getWorldSettings().getGlowViewDistance();
        if (distance <= 0.0D) distance = DEFAULT_GLOW_DISTANCE;
        double squared = distance * distance;

        Location barrierLocation = glowEntity.barrier.toLocation(level.getWorld());

        for (Player player : level.getWorld().getPlayers()) {
            boolean inRange = glowActiveNow
                && player.getLocation().distanceSquared(barrierLocation) <= squared;

            Boolean previous = glowEntity.perPlayerGlow.get(player.getUniqueId());

            // The server keeps the shulker's own glow flag at false and resends full entity
            // metadata to nearby players whenever the entity changes (peek, attached face,
            // regular tracker updates). Each of those packets overwrites our per-player glow
            // back to off. A one-shot "send only when the state flips" therefore silently
            // loses the glow — most visibly for the builder, who flies right next to the
            // barriers and so receives those metadata packets constantly.
            //
            // So while the barrier should glow for this player we refresh it every tick; the
            // set is tiny because only barriers within the (small) view radius qualify. When
            // it should be off we still send once, on the flip, and then stay quiet.
            if (inRange) {
                glowEntity.perPlayerGlow.put(player.getUniqueId(), true);
                if (PerPlayerGlowSender.isAvailable()) {
                    PerPlayerGlowSender.sendGlow(player, glowEntity.entity, true);
                }
            } else {
                if (previous != null && !previous) continue;
                glowEntity.perPlayerGlow.put(player.getUniqueId(), false);
                if (PerPlayerGlowSender.isAvailable()) {
                    PerPlayerGlowSender.sendGlow(player, glowEntity.entity, false);
                }
            }
        }
    }

    private void setGlowing(@NonNull GlowEntity glowEntity, boolean glowing) {
        if (glowEntity.appliedGlowing == glowing) return;
        glowEntity.entity.setGlowing(glowing);
        glowEntity.appliedGlowing = glowing;
    }

    private void despawnWorld(@NonNull UUID worldId) {
        Map<String, GlowEntity> byPosition = this.spawned.remove(worldId);
        if (byPosition == null) return;
        for (GlowEntity glowEntity : byPosition.values()) glowEntity.despawn();
    }

    @Override
    public void disable() {
        this.ensureTask.cancel();
        this.animateTask.cancel();

        for (Map<String, GlowEntity> byPosition : this.spawned.values()) {
            for (GlowEntity glowEntity : byPosition.values()) glowEntity.despawn();
        }
        this.spawned.clear();

        for (Team team : this.teams.values()) {
            try {
                team.unregister();
            } catch (IllegalStateException ignored) {
            }
        }
        this.teams.clear();
    }

    private static final class GlowEntity {
        private final @NonNull Shulker entity;
        private @NonNull GlowingBarrier barrier;
        private @Nullable GlowColor appliedColor = null;
        private boolean appliedGlowing = false;
        private final Map<UUID, Boolean> perPlayerGlow = new HashMap<>();

        private GlowEntity(@NonNull Shulker entity, @NonNull GlowingBarrier barrier) {
            this.entity = entity;
            this.barrier = barrier;
        }

        private boolean isValid() {
            return this.entity.isValid();
        }

        private void despawn() {
            if (this.entity.isDead()) return;
            this.entity.remove();
        }
    }
}
