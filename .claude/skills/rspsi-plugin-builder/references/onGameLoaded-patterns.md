# onGameLoaded Patterns

This reference shows the standard patterns for initializing loaders in the `onGameLoaded(Client client)` method, based on existing working plugins.

## Standard OSRS Pattern (File-Per-Definition Archives)

Modern OSRS revisions (175+) store each definition as a separate file within an archive. The loader iterates archive files and decodes each one.

The RSPSi cache API uses `client.getCache().getFile(CacheFileType.XXX)` to get cache indices, and `index.archive(id)` to get archives within an index. Archives are auto-decompressed by the displee cache library.

```java
@Override
public void onGameLoaded(Client client) throws Exception {
    // Initialize frame loader with animation index size
    frameLoader.init(2500);

    // Get the config index (index 2) — contains objects, floors, sequences, etc.
    Index configIndex = client.getCache().getFile(CacheFileType.CONFIG);

    // Initialize floor loaders from config archives
    floorLoader.initUnderlays(configIndex.archive(1));   // UNDERLAY
    floorLoader.initOverlays(configIndex.archive(4));    // OVERLAY

    // Initialize other config-based loaders
    objLoader.init(configIndex.archive(6));              // OBJECTS
    animDefLoader.init(configIndex.archive(12));         // SEQUENCES
    graphicLoader.init(configIndex.archive(13));         // SPOTANIM
    varbitLoader.init(configIndex.archive(14));          // VARBIT
    areaLoader.init(configIndex.archive(35));            // AREAS

    // Post-init: rename objects with area data
    objLoader.renameMapFunctions(areaLoader);

    // Frame base (skeleton) uses skeleton index directly
    Index skeletonIndex = client.getCache().getFile(CacheFileType.SKELETON);
    skeletonLoader.init(skeletonIndex);

    // Map index uses map index directly (scans for m{x}_{y} and l{x}_{y} archives)
    Index mapIndex = client.getCache().getFile(CacheFileType.MAP);
    mapIndexLoader.init(mapIndex);

    // Textures need both texture index and sprite index
    Index textureIndex = client.getCache().getFile(CacheFileType.TEXTURE);
    Index spriteIndex = client.getCache().getFile(CacheFileType.SPRITE);
    textureLoader.init(textureIndex.archive(0), spriteIndex);
}
```

**Important**: This pattern uses the displee cache library API (`com.displee.cache`), NOT the runelite API. Key differences:
- `client.getCache().getFile(CacheFileType.CONFIG)` — gets an Index by type enum
- `configIndex.archive(id)` — gets an Archive by ID (auto-decompresses)
- `archive.files()` — iterates files within an archive
- `archive.file(id)` — gets a specific file by ID

## Legacy Pattern (Single Data File with Index)

Older revisions (317, 377, etc.) pack all definitions into a single data file with a separate index file:

```java
@Override
public void onGameLoaded(Client client) throws Exception {
    Archive config = /* load config archive */;

    // Read data + index files
    byte[] objData = config.readFile("loc.dat");
    byte[] objIndex = config.readFile("loc.idx");
    objLoader.init(objData, objIndex);

    byte[] floorData = config.readFile("flo.dat");
    floorLoader.init(floorData);

    byte[] seqData = config.readFile("seq.dat");
    animLoader.init(seqData);

    // etc.
}
```

## Key Points

1. **Archive decompression is automatic** in the displee cache library — no need to call `readArchive()` separately
2. **Archive IDs are revision-specific** — verify against the reference client's ConfigType/IndexType constants
3. **Frame loader size** should be initialized before other loaders (typically 2500 for OSRS)
4. **Texture loading** requires both the texture definition archive AND the sprite index for pixel data
5. **Map index** can be loaded from an archive file OR by iterating the map index directly (scanning for "m{x}_{y}" / "l{x}_{y}" archive names)
6. **Post-init steps** like `renameMapFunctions()` depend on other loaders being initialized first — order matters
7. **CacheFileType enum** maps to cache indices: CONFIG=2, MODEL=7, ANIMATION=0, MAP=5, TEXTURE=9, SKELETON=1, SPRITE=8
