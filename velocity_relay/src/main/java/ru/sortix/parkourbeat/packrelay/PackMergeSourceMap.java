package ru.sortix.parkourbeat.packrelay;

import java.nio.file.Path;
import java.util.HashMap;

import me.bomb.amusic.resourcepack.PackMergeEntry;
import me.bomb.amusic.resourcepack.PackMergeEntryFile;
import me.bomb.amusic.resourcepack.PackMergeSource;

public final class PackMergeSourceMap implements PackMergeSource {
	
	private final PackMergeEntry defaultentry;
	private final int maxmergeresourcepacksize; //MAX: 0x0FA00000 - 250MiB
	private final HashMap<String, Path> map;
	
	public PackMergeSourceMap(PackMergeEntry defaultentry, int maxmergeresourcepacksize) {
		this.defaultentry = defaultentry;
		this.maxmergeresourcepacksize = maxmergeresourcepacksize;
		map = new HashMap<String, Path>();
	}
	
	public Path put(String resourcepackid, Path mergeresourcepack) {
		return this.map.put(resourcepackid, mergeresourcepack);
	}
	
	public Path remove(String resourcepackid) {
		return this.map.remove(resourcepackid);
	}
	
	@Override
	public PackMergeEntry get(String id) {
		final Path path;
		if(id == null || (path = this.map.get(id)) == null) {
			return this.defaultentry;
		}
		return new PackMergeEntryFile(path, maxmergeresourcepacksize);
	}

}
