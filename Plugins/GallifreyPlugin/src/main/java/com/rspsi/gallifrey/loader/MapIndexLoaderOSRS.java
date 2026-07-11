package com.rspsi.gallifrey.loader;

import com.displee.cache.index.Index;
import com.displee.cache.index.archive.Archive;
import com.jagex.cache.loader.map.MapIndexLoader;
import com.jagex.cache.loader.map.MapType;
import com.jagex.io.Buffer;

public class MapIndexLoaderOSRS extends MapIndexLoader {

	private Index mapIndex;

	/** GallifreyClient maps are keyed by name ("m{x}_{z}"/"l{x}_{z}"), not by region hash. */
	public void init(Index mapIndex) {
		this.mapIndex = mapIndex;
	}

	@Override
	public void init(Archive archive) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void init(Buffer buffer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getFileId(int hash, MapType type) {
		if (mapIndex == null) {
			return -1;
		}
		// Resolve the map name to its displee archive id (-1 if the region has no map).
		return mapIndex.archiveId(getGroupName(hash, type));
	}

	@Override
	public String getGroupName(int hash, MapType type) {
		int x = hash >> 8;
		int z = hash & 0xFF;

		String prefix = type == MapType.LANDSCAPE ? "m" : "l";
		return prefix + x + "_" + z;
	}

	@Override
	public boolean landscapePresent(int id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean objectPresent(int id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public byte[] encode() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void set(int regionX, int regionY, int landscapeId, int objectsId) {
		// no-op
	}

}
