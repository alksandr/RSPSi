package com.rspsi.gallifrey.loader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import lombok.extern.slf4j.Slf4j;

/**
 * Raw reader for the GallifreyClient cache's config archives.
 *
 * <p>The GallifreyClient cache is a RuneLite-style repack whose large config archives
 * (objects=6, npcs=9, items=10, params=34) declare a non-standard index-255 reference
 * table: 6 bytes per file (standard OSRS is 2) and, in the objects archive, 17 duplicate
 * file ids. displee (all versions) stores an archive's files in a {@code SortedMap} keyed
 * by file id, so the duplicate ids collapse, its file count under-counts, and its
 * per-file split-size trailer offset is computed wrong -> a negative array size ->
 * {@code NegativeArraySizeException}. RuneLite reads the same cache because it stores files
 * positionally and tolerates the duplicates.
 *
 * <p>This class re-reads such an archive straight from the {@code main_file_cache.*} files,
 * splitting files positionally exactly like RuneLite. Duplicate ids resolve
 * last-occurrence-wins (matching RuneLite's slot-order overwrite), so the returned map is
 * safe to feed to the definition loaders. Only used for archive 6 (objects); the other
 * broken archives are never read by the editor.
 */
@Slf4j
public final class GallifreyRawCache {

	private static final int SECTOR_SIZE = 520;

	private GallifreyRawCache() {
	}

	/**
	 * Reads a config archive (index 2) positionally, returning fileId -> raw file bytes.
	 * Duplicate file ids resolve last-occurrence-wins.
	 */
	public static Map<Integer, byte[]> readConfigArchive(String cacheDir, int archiveId) {
		try {
			byte[] dat = readAll(new File(cacheDir, "main_file_cache.dat2"));
			byte[] idx255 = readAll(new File(cacheDir, "main_file_cache.idx255"));
			byte[] idx2 = readAll(new File(cacheDir, "main_file_cache.idx2"));

			int[] fileIds = readReferenceFileIds(dat, idx255, archiveId);
			byte[] archiveData = decompress(readContainer(dat, idx2, archiveId));
			byte[][] files = splitFiles(archiveData, fileIds.length);

			// Slot order, last-occurrence-wins on duplicate ids (matches RuneLite).
			Map<Integer, byte[]> result = new LinkedHashMap<>(fileIds.length);
			for (int slot = 0; slot < fileIds.length; slot++) {
				result.put(fileIds[slot], files[slot]);
			}
			log.info("Raw-read config archive {}: {} slots, {} unique ids", archiveId, fileIds.length, result.size());
			return result;
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to raw-read config archive " + archiveId + " from " + cacheDir, ex);
		}
	}

	/** Parses the index-255 reference table and returns the file ids (in slot order) for one archive. */
	private static int[] readReferenceFileIds(byte[] dat, byte[] idx255, int archiveId) throws Exception {
		byte[] table = decompress(readContainer(dat, idx255, 2));
		int[] p = {0};

		int protocol = u8(table, p);
		if (protocol >= 6) {
			i32(table, p); // revision
		}
		int flags = u8(table, p);
		boolean named = (flags & 0x1) != 0;
		boolean digest = (flags & 0x2) != 0;
		boolean sized = (flags & 0x4) != 0;
		boolean hashed = (flags & 0x8) != 0;

		int numArchives = protocol >= 7 ? smart(table, p) : u16(table, p);
		int[] archiveIds = new int[numArchives];
		int acc = 0;
		for (int i = 0; i < numArchives; i++) {
			acc += protocol >= 7 ? smart(table, p) : u16(table, p);
			archiveIds[i] = acc;
		}
		if (named) {
			for (int i = 0; i < numArchives; i++) {
				i32(table, p); // archive name hash
			}
		}
		for (int i = 0; i < numArchives; i++) {
			i32(table, p); // crc
		}
		if (hashed) {
			for (int i = 0; i < numArchives; i++) {
				i32(table, p);
			}
		}
		if (digest) {
			for (int i = 0; i < numArchives; i++) {
				p[0] += 64; // whirlpool
			}
		}
		if (sized) {
			for (int i = 0; i < numArchives; i++) {
				i32(table, p); // compressed
				i32(table, p); // uncompressed
			}
		}
		for (int i = 0; i < numArchives; i++) {
			i32(table, p); // version
		}
		int[] numFiles = new int[numArchives];
		for (int i = 0; i < numArchives; i++) {
			numFiles[i] = protocol >= 7 ? smart(table, p) : u16(table, p);
		}
		// File id deltas (block). We only need the requested archive's block; skip the rest.
		int[] wanted = null;
		for (int i = 0; i < numArchives; i++) {
			int nf = numFiles[i];
			int fid = 0;
			int[] ids = archiveIds[i] == archiveId ? new int[nf] : null;
			for (int j = 0; j < nf; j++) {
				fid += protocol >= 7 ? smart(table, p) : u16(table, p);
				if (ids != null) {
					ids[j] = fid;
				}
			}
			if (ids != null) {
				wanted = ids;
			}
		}
		if (wanted == null) {
			throw new IllegalStateException("Archive " + archiveId + " not present in reference table");
		}
		return wanted;
	}

	/** Reads and reassembles an archive's raw (still-compressed) container from the dat2/idx sectors. */
	private static byte[] readContainer(byte[] dat, byte[] idx, int archiveId) {
		int o = archiveId * 6;
		int size = ((idx[o] & 0xff) << 16) | ((idx[o + 1] & 0xff) << 8) | (idx[o + 2] & 0xff);
		int sector = ((idx[o + 3] & 0xff) << 16) | ((idx[o + 4] & 0xff) << 8) | (idx[o + 5] & 0xff);
		byte[] out = new byte[size];
		int off = 0;
		int chunk = 0;
		boolean extended = archiveId > 0xffff;
		int header = extended ? 10 : 8;
		while (off < size) {
			int pos = sector * SECTOR_SIZE;
			int next;
			if (extended) {
				next = ((dat[pos + 6] & 0xff) << 16) | ((dat[pos + 7] & 0xff) << 8) | (dat[pos + 8] & 0xff);
			} else {
				next = ((dat[pos + 4] & 0xff) << 16) | ((dat[pos + 5] & 0xff) << 8) | (dat[pos + 6] & 0xff);
			}
			int len = Math.min(SECTOR_SIZE - header, size - off);
			System.arraycopy(dat, pos + header, out, off, len);
			off += len;
			sector = next;
		}
		return out;
	}

	/** Decompresses a container (compression byte + length header). Supports NONE and GZIP. */
	private static byte[] decompress(byte[] container) throws Exception {
		int compression = container[0] & 0xff;
		int compressedLen = ((container[1] & 0xff) << 24) | ((container[2] & 0xff) << 16)
				| ((container[3] & 0xff) << 8) | (container[4] & 0xff);
		if (compression == 0) { // none
			byte[] out = new byte[compressedLen];
			System.arraycopy(container, 5, out, 0, compressedLen);
			return out;
		}
		if (compression == 2) { // gzip
			byte[] payload = new byte[compressedLen];
			System.arraycopy(container, 9, payload, 0, compressedLen); // skip 4-byte decompressed length
			try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(payload))) {
				ByteArrayOutputStream bo = new ByteArrayOutputStream();
				byte[] buf = new byte[1 << 16];
				int r;
				while ((r = in.read(buf)) > 0) {
					bo.write(buf, 0, r);
				}
				return bo.toByteArray();
			}
		}
		throw new IllegalStateException("Unsupported compression " + compression + " in config archive");
	}

	/** Splits a multi-file archive into its positional files using the trailing size table. */
	private static byte[][] splitFiles(byte[] data, int numFiles) {
		if (numFiles == 1) {
			return new byte[][]{data};
		}
		int chunks = data[data.length - 1] & 0xff;
		int[] sizes = new int[numFiles];
		int p = data.length - 1 - chunks * numFiles * 4;
		for (int c = 0; c < chunks; c++) {
			int accum = 0;
			for (int i = 0; i < numFiles; i++) {
				int delta = ((data[p] & 0xff) << 24) | ((data[p + 1] & 0xff) << 16)
						| ((data[p + 2] & 0xff) << 8) | (data[p + 3] & 0xff);
				p += 4;
				accum += delta;
				sizes[i] += accum;
			}
		}
		byte[][] files = new byte[numFiles][];
		int off = 0;
		for (int i = 0; i < numFiles; i++) {
			files[i] = new byte[sizes[i]];
			System.arraycopy(data, off, files[i], 0, sizes[i]);
			off += sizes[i];
		}
		return files;
	}

	private static byte[] readAll(File f) throws Exception {
		return Files.readAllBytes(f.toPath());
	}

	private static int u8(byte[] b, int[] p) {
		return b[p[0]++] & 0xff;
	}

	private static int u16(byte[] b, int[] p) {
		int v = ((b[p[0]] & 0xff) << 8) | (b[p[0] + 1] & 0xff);
		p[0] += 2;
		return v;
	}

	private static int i32(byte[] b, int[] p) {
		int v = ((b[p[0]] & 0xff) << 24) | ((b[p[0] + 1] & 0xff) << 16)
				| ((b[p[0] + 2] & 0xff) << 8) | (b[p[0] + 3] & 0xff);
		p[0] += 4;
		return v;
	}

	/** OSRS "smart" (1-or-2 byte unsigned) used by protocol >= 7 reference tables. */
	private static int smart(byte[] b, int[] p) {
		int peek = b[p[0]] & 0xff;
		return peek < 128 ? u8(b, p) : u16(b, p) - 0x8000;
	}
}
