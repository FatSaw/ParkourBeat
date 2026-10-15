package ru.sortix.parkourbeat.inventory;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class HeadCache implements PluginManager, Listener {
    private static final long RESOLVE_COOLDOWN_MILLIS = 10L * 60L * 1000L;

    private final @NonNull ParkourBeat plugin;
    private final @NonNull File file;
    private final Map<UUID, String> textures = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> items = new ConcurrentHashMap<>();
    private final Map<UUID, Long> resolveAttempts = new ConcurrentHashMap<>();
    private final Set<UUID> resolving = ConcurrentHashMap.newKeySet();
    private volatile boolean dirty = false;

    public HeadCache(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "head_cache.yml");
        this.load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        for (Player online : plugin.getServer().getOnlinePlayers()) {
            this.capture(online);
        }

        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!this.dirty) return;
            this.dirty = false;
            this.save();
        }, 20L * 30L, 20L * 30L);
    }

    private void load() {
        if (!this.file.isFile()) return;
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(this.file);
            for (String key : config.getKeys(false)) {
                String value = config.getString(key);
                if (value == null || value.isEmpty()) continue;
                try {
                    this.textures.put(UUID.fromString(key), value);
                } catch (IllegalArgumentException ignored) {
                }
            }
            this.plugin.getLogger().info("Кэш голов загружен: " + this.textures.size());
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Unable to load head cache", e);
        }
    }

    private synchronized void save() {
        try {
            YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<UUID, String> entry : this.textures.entrySet()) {
                config.set(entry.getKey().toString(), entry.getValue());
            }
            File parent = this.file.getParentFile();
            if (parent != null) parent.mkdirs();
            config.save(this.file);
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Unable to save head cache", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NonNull PlayerJoinEvent event) {
        this.capture(event.getPlayer());
    }

    @EventHandler
    public void onQuit(@NonNull PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        this.items.remove(uuid);
        this.textures.remove(uuid);
        this.resolveAttempts.remove(uuid);
    }

    private void capture(@NonNull Player player) {
        try {
            String texture = extractTexture(player.getPlayerProfile());
            if (texture == null) return;
            UUID uuid = player.getUniqueId();
            if (texture.equals(this.textures.get(uuid))) return;
            this.textures.put(uuid, texture);
            this.items.remove(uuid);
            this.dirty = true;
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    private static String extractTexture(@Nullable PlayerProfile profile) {
        if (profile == null) return null;
        for (ProfileProperty property : profile.getProperties()) {
            if (property.getName().equals("textures")) return property.getValue();
        }
        return null;
    }

    @NonNull
    public ItemStack getHead(@NonNull UUID playerId, @Nullable String playerName) {
        ItemStack cached = this.items.get(playerId);
        if (cached != null) return cached.clone();

        String texture = this.textures.get(playerId);
        if (texture != null) {
            try {
                ItemStack head = Heads.getHeadByTextureData(texture, true);
                this.items.put(playerId, head);
                return head.clone();
            } catch (Throwable ignored) {
            }
        }

        this.scheduleResolve(playerId, playerName);
        return Heads.getHeadWithoutSkin();
    }

    private void scheduleResolve(@NonNull UUID playerId, @Nullable String playerName) {
        if (playerName == null || playerName.isEmpty() || playerName.length() > 16) return;

        long now = System.currentTimeMillis();
        Long lastAttempt = this.resolveAttempts.get(playerId);
        if (lastAttempt != null && now - lastAttempt < RESOLVE_COOLDOWN_MILLIS) return;
        if (!this.resolving.add(playerId)) return;
        this.resolveAttempts.put(playerId, now);

        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                PlayerProfile profile = Bukkit.createProfile(playerId, playerName);
                profile.complete(true);
                String texture = extractTexture(profile);
                if (texture == null) return;
                this.textures.put(playerId, texture);
                this.items.remove(playerId);
                this.dirty = true;
            } catch (Throwable ignored) {
            } finally {
                this.resolving.remove(playerId);
            }
        });
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        if (this.dirty) this.save();
        this.items.clear();
        this.textures.clear();
    }
}
