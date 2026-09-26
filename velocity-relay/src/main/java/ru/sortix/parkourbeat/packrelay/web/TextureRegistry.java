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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TextureRegistry {
    public static final class Entry {
        public String levelId;
        public String ownerUuid;
        public String ownerName;
        public String versionRange;
        public long sizeBytes;
        public int fileCount;
        public long createdAt;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Entry>>() {
    }.getType();

    private final Path file;
    private final Path temp;
    private final Path directory;
    private final Logger logger;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public TextureRegistry(Path dataDirectory, Logger logger) {
        this.directory = dataDirectory.resolve("textures");
        this.file = dataDirectory.resolve("textures.json");
        this.temp = dataDirectory.resolve("textures.json.tmp");
        this.logger = logger;

        try {
            Files.createDirectories(this.directory);
        } catch (Exception e) {
            logger.warn("Unable to create textures directory", e);
        }
        this.load();
    }

    public Path zipOf(String levelId) {
        return this.directory.resolve(levelId + ".zip");
    }

    public Entry get(String levelId) {
        return this.entries.get(levelId);
    }

    public void put(Entry entry) {
        this.entries.put(entry.levelId, entry);
        this.save();
    }

    public void remove(String levelId) {
        if (this.entries.remove(levelId) == null) return;
        try {
            Files.deleteIfExists(this.zipOf(levelId));
        } catch (Exception e) {
            this.logger.warn("Unable to delete texture pack of level {}", levelId, e);
        }
        this.save();
    }

    private void load() {
        try {
            if (!Files.isRegularFile(this.file)) return;
            try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
                Map<String, Entry> loaded = GSON.fromJson(reader, TYPE);
                if (loaded != null) this.entries.putAll(loaded);
            }
            this.logger.info("Loaded {} level texture pack(s)", this.entries.size());
        } catch (Exception e) {
            this.logger.warn("Unable to load texture registry", e);
        }
    }

    private synchronized void save() {
        try {
            try (Writer writer = Files.newBufferedWriter(this.temp, StandardCharsets.UTF_8)) {
                GSON.toJson(this.entries, TYPE, writer);
            }
            Files.move(this.temp, this.file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            this.logger.warn("Unable to save texture registry", e);
        }
    }
}
