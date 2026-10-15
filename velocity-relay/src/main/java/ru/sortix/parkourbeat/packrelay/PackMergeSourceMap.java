package ru.sortix.parkourbeat.packrelay;

import java.nio.file.Path;
import java.util.HashMap;

import me.bomb.amusic.resourcepack.PackMergeEntry;
import me.bomb.amusic.resourcepack.PackMergeEntryFile;
import me.bomb.amusic.resourcepack.PackMergeSource;

public final class PackMergeSourceMap implements PackMergeSource {

    private final Path defaultPath;
    private volatile PackMergeEntry defaultentry;
    private final int maxmergeresourcepacksize; //MAX: 0x0FA00000 - 250MiB
    private final HashMap<String, Path> map;
    private final String joinPlaylist;

    public PackMergeSourceMap(Path defaultPath, int maxmergeresourcepacksize, String joinPlaylist) {
        this.defaultPath = defaultPath;
        this.defaultentry = new PackMergeEntryFile(defaultPath, maxmergeresourcepacksize);
        this.maxmergeresourcepacksize = maxmergeresourcepacksize;
        this.joinPlaylist = joinPlaylist;
        this.map = new HashMap<String, Path>();
    }

    public PackMergeSourceMap(PackMergeEntry defaultentry, int maxmergeresourcepacksize) {
        this.defaultPath = null;
        this.defaultentry = defaultentry;
        this.maxmergeresourcepacksize = maxmergeresourcepacksize;
        this.joinPlaylist = null;
        this.map = new HashMap<String, Path>();
    }

    public void reloadDefault() {
        if (this.defaultPath != null) {
            this.defaultentry = new PackMergeEntryFile(this.defaultPath, this.maxmergeresourcepacksize);
        }
    }

    public Path put(String resourcepackid, Path mergeresourcepack) {
        return this.map.put(resourcepackid, mergeresourcepack);
    }

    public Path remove(String resourcepackid) {
        return this.map.remove(resourcepackid);
    }

    @Override
    public PackMergeEntry get(String id) {
        // 1. Кастомные текстуры конкретного уровня (если назначены через редактор)
        final Path path;
        if (id != null && (path = this.map.get(id)) != null) {
            return new PackMergeEntryFile(path, maxmergeresourcepacksize);
        }

        // 2. Базовый resourcepack.zip вшиваем ТОЛЬКО во входной плейлист (при логине)
        if (id != null) {
            if ((this.joinPlaylist != null && id.equalsIgnoreCase(this.joinPlaylist))
                || id.equalsIgnoreCase("ParkourBeatCore")
                || id.equalsIgnoreCase("core")) {
                return this.defaultentry;
            }
        }

        // 3. Во все остальные треки НЕ вшиваем ничего (чистый звук)
        return null;
    }

}
