package ru.sortix.parkourbeat.utils.wonder;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.wonder.WonderAnchor;
import ru.sortix.parkourbeat.levels.wonder.WonderEffect;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Общая библиотека: строители выкладывают свои эффекты, остальные берут и лайкают. */
public class WonderLibrary implements PluginManager {

    @Getter
    public static final class Entry {
        private final @NonNull String id;
        private @NonNull String name;
        private @NonNull String author;
        private @NonNull UUID authorId;
        private long created;
        private final @NonNull Set<UUID> likes = new HashSet<>();
        private @NonNull String serializedEffect;

        Entry(@NonNull String id, @NonNull String name, @NonNull String author,
              @NonNull UUID authorId, long created, @NonNull String serializedEffect) {
            this.id = id;
            this.name = name;
            this.author = author;
            this.authorId = authorId;
            this.created = created;
            this.serializedEffect = serializedEffect;
        }

        public int getLikesAmount() {
            return this.likes.size();
        }

        public boolean isLikedBy(@NonNull UUID player) {
            return this.likes.contains(player);
        }

        @Nullable
        public WonderEffect toEffect(int startMillis) {
            WonderEffect effect = WonderEffect.deserialize(this.serializedEffect);
            if (effect == null) return null;
            int duration = effect.getDurationMillis();
            effect.setStartMillis(startMillis);
            effect.setEndMillis(startMillis + Math.max(500, duration));
            return effect;
        }
    }

    private final @NonNull ParkourBeat plugin;
    private final @NonNull File file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public WonderLibrary(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "wonder-library.yml");
        this.load();
    }

    @Override
    public void disable() {
        this.save();
        this.entries.clear();
    }

    private void load() {
        if (!this.file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection root = yaml.getConfigurationSection("entries");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            String serialized = section.getString("effect");
            if (serialized == null) continue;
            try {
                Entry entry = new Entry(
                    id,
                    section.getString("name", "Без названия"),
                    section.getString("author", "?"),
                    UUID.fromString(section.getString("author-id", UUID.randomUUID().toString())),
                    section.getLong("created"),
                    serialized);
                for (String like : section.getStringList("likes")) {
                    try {
                        entry.likes.add(UUID.fromString(like));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                this.entries.put(id, entry);
            } catch (RuntimeException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Entry entry : this.entries.values()) {
            String base = "entries." + entry.id + ".";
            yaml.set(base + "name", entry.name);
            yaml.set(base + "author", entry.author);
            yaml.set(base + "author-id", entry.authorId.toString());
            yaml.set(base + "created", entry.created);
            yaml.set(base + "effect", entry.serializedEffect);
            List<String> likes = new ArrayList<>();
            for (UUID like : entry.likes) likes.add(like.toString());
            yaml.set(base + "likes", likes);
        }
        try {
            yaml.save(this.file);
        } catch (Exception e) {
            this.plugin.getLogger().warning("Библиотека эффектов не сохранилась: " + e.getMessage());
        }
    }

    /** Сначала самые залайканные, при равенстве свежие сверху. */
    @NonNull
    public List<Entry> top() {
        List<Entry> list = new ArrayList<>(this.entries.values());
        list.sort((a, b) -> {
            int byLikes = Integer.compare(b.getLikesAmount(), a.getLikesAmount());
            return byLikes != 0 ? byLikes : Long.compare(b.created, a.created);
        });
        return list;
    }

    @NonNull
    public String publish(@NonNull Player author, @NonNull String name, @NonNull WonderEffect effect) {
        String id = Long.toString(System.currentTimeMillis(), 36) + "-" + author.getName().toLowerCase();
        Entry entry = new Entry(id, name, author.getName(), author.getUniqueId(),
            System.currentTimeMillis(), effect.copy().serialize());
        // Автор лайкает свою работу сразу: иначе свежая запись висит в самом низу топа
        entry.likes.add(author.getUniqueId());
        this.entries.put(id, entry);
        this.save();
        return id;
    }

    /** true — лайк поставлен, false — снят. */
    public boolean toggleLike(@NonNull Entry entry, @NonNull UUID player) {
        boolean added;
        if (entry.likes.contains(player)) {
            entry.likes.remove(player);
            added = false;
        } else {
            entry.likes.add(player);
            added = true;
        }
        this.save();
        return added;
    }

    public boolean delete(@NonNull Entry entry, @NonNull Player who) {
        boolean allowed = entry.authorId.equals(who.getUniqueId()) || who.hasPermission("parkourbeat.wonder.moderate");
        if (!allowed) return false;
        this.entries.remove(entry.id);
        this.save();
        return true;
    }

    public int amount() {
        return this.entries.size();
    }
}
