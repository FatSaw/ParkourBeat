package ru.sortix.parkourbeat.activity;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class EditorSessionsManager implements PluginManager {
    private static final long FLUSH_PERIOD_TICKS = 40L;
    private static final long AUTOSAVE_PERIOD_TICKS = 20L * 30L;
    private static final long EXPIRY_MILLIS = 1000L * 60L * 60L * 24L * 45L;
    private static final int MAX_SLOTS = 54;

    @Getter
    public static final class Session {
        private final @NonNull ItemStack[] contents;
        private final int heldSlot;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final boolean hasLocation;
        private final boolean active;
        private final long updatedAt;

        private Session(@NonNull ItemStack[] contents, int heldSlot,
                        double x, double y, double z, float yaw, float pitch,
                        boolean hasLocation, boolean active, long updatedAt) {
            this.contents = contents;
            this.heldSlot = heldSlot;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.hasLocation = hasLocation;
            this.active = active;
            this.updatedAt = updatedAt;
        }

        @Nullable
        public Location toLocation(@NonNull World world) {
            if (!this.hasLocation) return null;
            return new Location(world, this.x, this.y, this.z, this.yaw, this.pitch);
        }
    }

    private final @NonNull ParkourBeat plugin;
    private final @NonNull File file;
    private final @NonNull File tempFile;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final @NonNull BukkitTask flushTask;
    private final @NonNull BukkitTask autoSaveTask;
    private volatile boolean dirty = false;

    public EditorSessionsManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "editor_sessions.yml");
        this.tempFile = new File(plugin.getDataFolder(), "editor_sessions.yml.tmp");
        this.load();

        this.flushTask = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, this::flushIfDirty, FLUSH_PERIOD_TICKS, FLUSH_PERIOD_TICKS);
        this.autoSaveTask = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, this::autoSaveAll, AUTOSAVE_PERIOD_TICKS, AUTOSAVE_PERIOD_TICKS);

        plugin.getServer().getScheduler().runTaskLater(plugin, this::restoreActiveEditors, 40L);
    }

    @NonNull
    private static String key(@NonNull UUID levelId, @NonNull UUID playerId) {
        return levelId + "/" + playerId;
    }

    private void load() {
        if (!this.file.isFile()) {
            if (this.tempFile.isFile()) {
                try {
                    Files.move(this.tempFile.toPath(), this.file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    this.plugin.getLogger().log(Level.WARNING,
                        "Unable to recover editor sessions from temp file", e);
                }
            }
            if (!this.file.isFile()) return;
        }

        long now = System.currentTimeMillis();
        int expired = 0;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(this.file);
            ConfigurationSection root = config.getConfigurationSection("sessions");
            if (root == null) return;

            for (String key : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) continue;

                long updatedAt = section.getLong("updated", 0L);
                if (updatedAt > 0L && now - updatedAt > EXPIRY_MILLIS) {
                    expired++;
                    continue;
                }

                ItemStack[] contents = decode(section.getString("inventory"));
                if (contents == null) continue;

                Session session = new Session(
                    contents,
                    section.getInt("held", 0),
                    section.getDouble("x"),
                    section.getDouble("y"),
                    section.getDouble("z"),
                    (float) section.getDouble("yaw"),
                    (float) section.getDouble("pitch"),
                    section.getBoolean("has_location", false),
                    section.getBoolean("active", false),
                    updatedAt);

                this.sessions.put(key.replace(':', '/'), session);
            }
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Unable to load editor sessions", e);
            return;
        }

        this.plugin.getLogger().info("Загружено сессий редактора: " + this.sessions.size()
            + (expired > 0 ? " (просрочено и отброшено: " + expired + ")" : ""));
        if (expired > 0) this.dirty = true;
    }

    @Nullable
    private static String encode(@NonNull ItemStack[] contents) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
                out.writeInt(contents.length);
                for (ItemStack item : contents) {
                    out.writeObject(item);
                }
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static ItemStack[] decode(@Nullable String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                int length = in.readInt();
                if (length < 0 || length > MAX_SLOTS) return null;
                ItemStack[] contents = new ItemStack[length];
                for (int i = 0; i < length; i++) {
                    Object value = in.readObject();
                    contents[i] = value instanceof ItemStack ? (ItemStack) value : null;
                }
                return contents;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    public void snapshot(@NonNull UUID levelId,
                         @NonNull UUID playerId,
                         @NonNull ItemStack[] contents,
                         int heldSlot,
                         @Nullable Location location,
                         boolean active
    ) {
        ItemStack[] copy = new ItemStack[Math.min(contents.length, MAX_SLOTS)];
        for (int i = 0; i < copy.length; i++) {
            ItemStack item = contents[i];
            copy[i] = item == null ? null : item.clone();
        }

        Session session = new Session(
            copy,
            Math.max(0, Math.min(8, heldSlot)),
            location == null ? 0 : location.getX(),
            location == null ? 0 : location.getY(),
            location == null ? 0 : location.getZ(),
            location == null ? 0 : location.getYaw(),
            location == null ? 0 : location.getPitch(),
            location != null,
            active,
            System.currentTimeMillis());

        this.sessions.put(key(levelId, playerId), session);
        this.dirty = true;
    }

    @Nullable
    public Session get(@NonNull UUID levelId, @NonNull UUID playerId) {
        return this.sessions.get(key(levelId, playerId));
    }

    public void remove(@NonNull UUID levelId, @NonNull UUID playerId) {
        if (this.sessions.remove(key(levelId, playerId)) != null) this.dirty = true;
    }

    public void removeLevel(@NonNull UUID levelId) {
        String prefix = levelId + "/";
        boolean removed = this.sessions.keySet().removeIf(key -> key.startsWith(prefix));
        if (removed) this.dirty = true;
    }

    private void autoSaveAll() {
        ActivityManager activityManager;
        try {
            activityManager = this.plugin.get(ActivityManager.class);
        } catch (Exception e) {
            return;
        }

        for (UserActivity activity : activityManager.getAllActivities()) {
            if (!(activity instanceof EditActivity)) continue;
            try {
                ((EditActivity) activity).saveEditorSession(true);
            } catch (Exception e) {
                this.plugin.getLogger().log(Level.WARNING, "Unable to autosave editor session", e);
            }
        }
    }

    private void restoreActiveEditors() {
        LevelsManager levelsManager;
        ActivityManager activityManager;
        try {
            levelsManager = this.plugin.get(LevelsManager.class);
            activityManager = this.plugin.get(ActivityManager.class);
        } catch (Exception e) {
            return;
        }

        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();

            for (Map.Entry<String, Session> entry : this.sessions.entrySet()) {
                if (!entry.getKey().endsWith("/" + playerId)) continue;
                if (!entry.getValue().active) continue;

                UUID levelId;
                try {
                    levelId = UUID.fromString(entry.getKey().substring(0, entry.getKey().indexOf('/')));
                } catch (Exception e) {
                    continue;
                }

                this.markInactive(levelId, playerId);
                this.reopenEditor(levelsManager, activityManager, player, levelId);
                break;
            }
        }
    }

    private void markInactive(@NonNull UUID levelId, @NonNull UUID playerId) {
        Session session = this.sessions.get(key(levelId, playerId));
        if (session == null || !session.active) return;

        this.sessions.put(key(levelId, playerId), new Session(
            session.contents, session.heldSlot,
            session.x, session.y, session.z, session.yaw, session.pitch,
            session.hasLocation, false, session.updatedAt));
        this.dirty = true;
    }

    private void reopenEditor(@NonNull LevelsManager levelsManager,
                              @NonNull ActivityManager activityManager,
                              @NonNull Player player,
                              @NonNull UUID levelId
    ) {
        GameSettings settings = levelsManager.getAvailableLevelSettings(levelId);
        if (settings == null || !settings.canEdit(player.getUniqueId())) return;

        levelsManager.loadLevel(levelId, settings).thenAccept(level -> {
            if (level == null) return;
            if (!player.isOnline()) return;
            if (activityManager.getActivity(player) instanceof EditActivity) return;

            EditActivity.createAsync(this.plugin, player, level).thenAccept(editActivity -> {
                if (editActivity == null || !player.isOnline()) return;
                activityManager.switchActivity(player, editActivity, level.getSpawn());
            });
        });
    }

    private void flushIfDirty() {
        if (!this.dirty) return;
        this.flush(true);
    }

    public void flush(boolean async) {
        this.dirty = false;

        String content;
        try {
            YamlConfiguration config = new YamlConfiguration();
            ConfigurationSection root = config.createSection("sessions");
            for (Map.Entry<String, Session> entry : this.sessions.entrySet()) {
                Session session = entry.getValue();
                String encoded = encode(session.contents);
                if (encoded == null) continue;

                ConfigurationSection section = root.createSection(entry.getKey().replace('/', ':'));
                section.set("inventory", encoded);
                section.set("held", session.heldSlot);
                section.set("has_location", session.hasLocation);
                section.set("active", session.active);
                section.set("x", session.x);
                section.set("y", session.y);
                section.set("z", session.z);
                section.set("yaw", session.yaw);
                section.set("pitch", session.pitch);
                section.set("updated", session.updatedAt);
            }
            content = config.saveToString();
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Unable to serialize editor sessions", e);
            this.dirty = true;
            return;
        }

        if (async) {
            try {
                this.plugin.getServer().getScheduler()
                    .runTaskAsynchronously(this.plugin, () -> this.write(content));
                return;
            } catch (Throwable ignored) {
            }
        }
        this.write(content);
    }

    private synchronized void write(@NonNull String content) {
        try {
            File parent = this.file.getParentFile();
            if (parent != null) parent.mkdirs();

            Path temp = this.tempFile.toPath();
            Files.write(temp, content.getBytes(StandardCharsets.UTF_8));

            try {
                Files.move(temp, this.file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFailed) {
                Files.move(temp, this.file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Unable to save editor sessions", e);
            this.dirty = true;
        }
    }

    @Override
    public void disable() {
        if (!this.flushTask.isCancelled()) this.flushTask.cancel();
        if (!this.autoSaveTask.isCancelled()) this.autoSaveTask.cancel();
        this.flush(false);
    }
}
