package ru.sortix.parkourbeat.packrelay.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TrackRegistry {
    public static final class Entry {
        public String trackId;
        public String ownerUuid;
        public String ownerName;
        public String author;
        public String title;
        public long sizeBytes;
        public long createdAt;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Entry>>() {
    }.getType();

    private final Path file;
    private final Path temp;
    private final Logger logger;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public TrackRegistry(Path directory, Logger logger) {
        this.file = directory.resolve("tracks.json");
        this.temp = directory.resolve("tracks.json.tmp");
        this.logger = logger;
        this.load();
    }

    private void load() {
        try {
            if (!Files.isRegularFile(this.file)) return;
            try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
                Map<String, Entry> loaded = GSON.fromJson(reader, TYPE);
                if (loaded != null) this.entries.putAll(loaded);
            }
            this.logger.info("Loaded {} owned track(s)", this.entries.size());
        } catch (Exception e) {
            this.logger.warn("Unable to load track registry", e);
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(this.file.getParent());
            try (Writer writer = Files.newBufferedWriter(this.temp, StandardCharsets.UTF_8)) {
                GSON.toJson(this.entries, TYPE, writer);
            }
            Files.move(this.temp, this.file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            this.logger.warn("Unable to save track registry", e);
        }
    }

    public void put(Entry entry) {
        this.entries.put(entry.trackId, entry);
        this.save();
    }

    public void remove(String trackId) {
        if (this.entries.remove(trackId) != null) this.save();
    }

    public Entry get(String trackId) {
        return this.entries.get(trackId);
    }

    public List<Entry> ownedBy(UUID owner) {
        String id = owner.toString();
        List<Entry> result = new ArrayList<>();
        for (Entry entry : this.entries.values()) {
            if (id.equals(entry.ownerUuid)) result.add(entry);
        }
        return result;
    }

    public int countOwnedBy(UUID owner) {
        return this.ownedBy(owner).size();
    }
}
