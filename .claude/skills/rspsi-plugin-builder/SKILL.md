---
name: rspsi-plugin-builder
description: |
  Build or modify RSPSi map editor plugins that load RuneScape cache data accurately.
  Use when the user says "create plugin", "new plugin", "build plugin", "modify plugin",
  "update plugin", "fix plugin loader", "add loader", "port cache loading", "cache loader",
  "plugin for revision", "support revision", "OSRS plugin", "RS3 plugin", "RSPS plugin",
  "downgrade plugin", "upgrade plugin", or mentions loading cache data, decoding definitions,
  or cache formats for the map editor.
  Also use when the user references a RuneScape client revision and wants RSPSi to support it.
  Do NOT use for general Java coding, Gradle configuration unrelated to plugins, or
  editing the RSPSi Editor/Client core modules directly.
compatibility: project
metadata:
  version: "1.1.1"
---

# RSPSi Plugin Builder

Create or modify RSPSi plugins that load RuneScape cache data by matching exactly how a reference client decodes its cache.

## Setup

Before starting, verify the workspace:
1. Confirm we're in the RSPSi project root (look for `settings.gradle` with module includes)
2. Identify existing plugins under `Plugins/`
3. If the user provides a reference client path, verify it exists

Key project paths:
- Existing OSRS plugin (rev 236): `Plugins/OSRSPlugin/`
- Existing OSRS plugin (rev 218): `Plugins/OSRSPlugin-218/`
- Plugin build config: `Plugins/build.gradle`
- Module registration: `settings.gradle`
- Client loader base classes: `Client/src/main/java/com/jagex/cache/loader/`
- Definition classes: `Client/src/main/java/com/jagex/cache/def/`
- Cache type enum: `Client/src/main/java/com/rspsi/cache/CacheFileType.java`

## Instructions

### Step 1: Determine What the User Needs

Parse the user's request to identify:
- **Create new plugin**: User wants a fresh plugin for a specific RS revision/cache format
- **Modify existing plugin**: User wants to fix, update, or extend an existing plugin's loaders
- **Upgrade/downgrade plugin**: User wants to change the target revision (add/remove opcodes, adjust byte counts)
- **Add a loader**: User wants to add support for a new definition type to an existing plugin
- **Default (no clear intent)**: If the user just references a plugin or revision without a verb, assume they want to modify/fix the existing plugin for that revision. If no plugin exists yet, ask whether to create one.

Gather required information:
- **Target revision number** (e.g., 218, 194, 317, 667)
- **Reference client path** (source code of the RS client whose cache format to match) — if not provided and a reference client can't be found, ask the user for it before proceeding
- **Plugin name** (default: `OSRSPlugin-{revision}` or derive from context)
- **Which loaders** are needed (default: all standard loaders)

### Step 2: Study the Reference Client

This is the most critical step. The plugin must decode cache data exactly as the reference client does.

1. **Find the reference client's cache loading code.** Search for:
   - Definition classes (ObjectDefinition, UnderlayDefinition, OverlayDefinition, SequenceDefinition, etc.)
   - Loader classes that decode binary data (look for opcode-based switch/if chains)
   - Index/archive constants (which cache index holds which data type)
   - Config type IDs (archive IDs within the config index)

   **Fallback if loaders aren't found**: Search for alternative naming patterns (`ObjectComposition`, `LocType`, `SeqType`, `SpotAnimType`). Try searching for opcode switch patterns (`case 1:`, `opcode == 1`). If the reference client is deobfuscated RuneLite-style, look in `definitions/loaders/`. If still not found, ask the user to point to the specific loader files.

2. **For each definition type, extract:**
   - The complete opcode table (every opcode number and what data it reads)
   - Data types used per opcode — be precise about signed vs unsigned (byte vs ubyte, short vs ushort)
   - Post-decode processing (defaults, computed fields)
   - The cache index and archive where this data lives

3. **Map reference client concepts to RSPSi equivalents:**

   | Reference Client Concept | RSPSi Equivalent |
   |---|---|
   | ObjectDefinition / ObjectComposition | `com.jagex.cache.def.ObjectDefinition` |
   | UnderlayDefinition | `com.jagex.cache.def.Floor` (FloorType.UNDERLAY) |
   | OverlayDefinition | `com.jagex.cache.def.Floor` (FloorType.OVERLAY) |
   | TextureDefinition / TextureProvider | `com.jagex.draw.textures.Texture` / `SpriteTexture` |
   | SequenceDefinition | `com.jagex.cache.anim.Animation` |
   | SpotAnimDefinition | `com.jagex.cache.anim.Graphic` |
   | VarbitDefinition | `com.jagex.cache.config.VariableBits` |
   | MapDefinition / RegionLoader | Map index data |
   | Frame / FrameMap | `com.jagex.cache.anim.Frame` / `com.jagex.cache.anim.FrameBase` |
   | RSArea / AreaDefinition | `com.jagex.cache.def.RSArea` |

4. **Identify cache index structure.** Standard OSRS indices (via `CacheFileType` enum):
   - Index 0: ANIMATION (frame data)
   - Index 1: SKELETON (frame bases)
   - Index 2: CONFIG (objects, floors, sequences, spotanims, varbits, areas, etc.)
   - Index 5: MAP (terrain + locations)
   - Index 8: SPRITE
   - Index 9: TEXTURE

   Config archive IDs within index 2:
   - Archive 1: UNDERLAY floors
   - Archive 4: OVERLAY floors
   - Archive 6: OBJECTS
   - Archive 12: SEQUENCES (animations)
   - Archive 13: SPOTANIM (graphics)
   - Archive 14: VARBIT
   - Archive 35: AREAS (RSArea)

### Step 2b: Compare Revisions (for Upgrade/Downgrade)

When converting a plugin between revisions, systematically compare every opcode:

1. **Read both the current plugin's decode methods AND the reference client's loaders**
2. **For each loader, compare opcode by opcode:**
   - Opcodes present in one but not the other → add or remove
   - Same opcode but different byte counts → format changed between revisions
   - Same opcode but different data types (signed vs unsigned) → adjust read calls
3. **Pay special attention to:**
   - Sound-related opcodes (78, 79) — byte counts often change between revisions
   - Opcodes above 89 — these are frequently added in newer revisions
   - Animation opcodes 13-17 — formats changed significantly around rev 236
   - Interactions/actions range — rev 218 uses 30-34 (5 actions), later revisions use 30-38

See `references/opcode-translation-guide.md` for a complete table with revision-specific notes.

### Step 3: Create or Modify the Plugin

#### For a New Plugin

1. **Copy an existing plugin** (use `Plugins/OSRSPlugin-218/` as the best starting base)
2. **Register in settings.gradle:** Add `include 'Plugins:{PluginName}'`
3. **Implement the main plugin class** (implements `ClientPlugin`)
4. **Implement each loader class** by translating the reference client's decode logic

#### For Each Loader

Follow this pattern to translate reference client code into RSPSi loader code:

**a) Object Definition Loader** (`extends ObjectDefinitionLoader`):
- Read from config index, archive 6 (objects)
- Translate every opcode from the reference client's ObjectDefinition/ObjectLoader
- Map decoded fields to `ObjectDefinition` setters
- Implement morphism support for varbit/varp opcodes (77/92)
- Implement `renameMapFunctions()` if RSArea loader is present

**b) Floor Definition Loader** (`extends FloorDefinitionLoader`):
- Two separate init methods: `initOverlays(Archive)` and `initUnderlays(Archive)`
- Underlay: usually just opcode 1 = RGB color
- Overlay: opcodes for color, texture ID, hide-underlay flag, secondary color
- Call `floor.generateHsl()` after decoding each floor

**c) Map Index Loader** (`extends MapIndexLoader`):
- Iterate the map index to find "m{x}_{y}" and "l{x}_{y}" archives
- Store parallel arrays: mapHashes[], landscapes[], objects[], groupName[]

**d) Texture Loader** (`extends TextureLoader`):
- Read texture definitions from texture index archive 0
- **Texture definition format** (NOT opcode-based, sequential fields):
  1. `ushort` — average color (field1777, skip)
  2. `byte` — boolean flag (field1778, skip)
  3. `ubyte` — count of sprite file IDs
  4. `count * ushort` — sprite file IDs (use first one to load the sprite)
  5. Additional blending/animation data follows (skip for basic loading)
- **CRITICAL**: The first ushort is NOT the sprite ID — it's a color field. The actual sprite IDs come after the boolean and count byte.
- Load sprite data from sprite index using the decoded file ID
- Always null-check `spriteIndex.archive(spriteId)` — missing sprites should be skipped, not crash
- Generate pixel arrays with 4 brightness levels (128x128 = 16384 pixels per level)

**e) Animation Definition Loader** (`extends AnimationDefinitionLoader`):
- Read from config index, archive 12 (sequences)
- Decode frame IDs, durations, loop offset, interleave order, priority
- Handle dummy frame creation when frameCount is 0

**f) Frame Loader** (`extends FrameLoader`):
- Initialize with size (typically 2500)
- Lazy-load frames via `Client.getProvider().requestFile()`
- Decode transformation data (X/Y/Z coordinates, types)

**g) Frame Base Loader** (`extends FrameBaseLoader`):
- Read from skeleton index (index 1)
- Decode transformation types and vertex groups

**h) Graphic Loader** (`extends GraphicLoader`):
- Read from config index, archive 13 (spotanims)
- Decode model ID, animation ID, scaling, orientation, lighting

**i) Variable Bit Loader** (`extends VariableBitLoader`):
- Read from config index, archive 14 (varbits)
- Decode setting ID, low bit, high bit

**j) RSArea Loader** (`extends RSAreaLoader`):
- Read from config index, archive 35 (areas)
- Decode sprite ID, name, and other area properties

### Step 4: Wire Up onGameLoaded

See `references/onGameLoaded-patterns.md` for the complete pattern using the displee cache library API.

Key API calls:
```java
Index configIndex = client.getCache().getFile(CacheFileType.CONFIG);
floorLoader.initUnderlays(configIndex.archive(1));
floorLoader.initOverlays(configIndex.archive(4));
objLoader.init(configIndex.archive(6));
// etc.
```

### Step 5: Build and Test

1. Build the plugin: `./gradlew "Plugins:{PluginName}:buildAndMove"`
2. If build errors occur, fix them based on compiler output
3. Verify the JAR was created in `Editor/plugins/inactive/`
4. If adding to settings.gradle, ensure the include line is present

### Step 6: Validate Opcode Accuracy

After generating loader code, cross-reference every opcode against the reference client to ensure:
- Same opcode numbers map to the same data types and fields
- Signed vs unsigned read methods match (`readShort()` vs `readUShort()`)
- Read order within each opcode matches exactly
- Byte counts per opcode match exactly (especially sound opcodes 78, 79)
- Default/post-decode values match
- No opcodes are missing or added incorrectly

## Key Translation Rules

When converting reference client code to RSPSi plugin loaders:

1. **Buffer reading methods mapping:**
   - Reference `readUnsignedByte()` / `readByte()` → RSPSi `buffer.readUByte()` or `buffer.readByte()`
   - Reference `readUnsignedShort()` / `readShort()` → RSPSi `buffer.readUShort()` or `buffer.readShort()`
   - Reference `readInt()` → RSPSi `buffer.readInt()`
   - Reference `readString()` / `readStringCp1252NullTerminated()` → RSPSi `buffer.readOSRSString()` (null-terminated CP1252, byte 0x00). **WARNING**: `buffer.readString()` uses newline termination (byte 0x0A) and is NOT correct for OSRS cache data — only use for 317-era formats.
   - Reference `readMedium()` / `read24BitInt()` → RSPSi `buffer.readUTriByte()`
   - Reference `readSmart()` / `readUnsignedSmart()` → RSPSi `buffer.readSmart()`
   - Reference `readBigSmart2()` → RSPSi `ByteBufferUtils.getSmartInt(buffer)` (for ByteBuffer-based loaders)

2. **Use RSPSi definition setters**, not direct field access. ObjectDefinition uses Lombok @Getter/@Setter.

3. **Archive vs file-per-definition:** OSRS revisions 175+ store each definition as a separate file in an archive. Older revisions pack all definitions into a single data file with an index file.

4. **Opcode 0 always means end-of-definition** in opcode-based formats.

5. **Unknown/skipped opcodes:** When the reference client has opcodes the RSPSi definition class doesn't support, skip the bytes but still read them to keep the buffer position correct.

6. **Interactions array**: Reuse the existing array (`definition.getInteractions()`) rather than creating a new array for each opcode. Initialize once if null with the correct size (5 for rev 218, up to 9 for later revisions).

## Examples

### Example 1: Create a new plugin for revision 220
```
User: "Create a plugin for OSRS revision 220 using the client at S:\RSPS\OpenOSRS\rev220"
```
Actions: Read reference client's cache loaders at given path, copy OSRSPlugin-218 as base, modify all loaders to match revision 220 opcodes, register in settings.gradle, build.

### Example 2: Downgrade plugin to earlier revision
```
User: "Downgrade this rev 236 plugin to rev 218 using the IdleScape client as reference"
```
Actions: Read both the plugin's loaders AND the reference client's loaders. Compare opcode by opcode. Remove opcodes not in 218. Adjust byte counts for opcodes that changed format. Build and verify.

### Example 3: Fix object loading in existing plugin
```
User: "Objects aren't loading correctly in my plugin, fix the object loader"
```
Actions: Read the plugin's ObjectDefinitionLoader, compare opcodes against reference client, identify missing/incorrect opcodes, fix the decode method.

### Example 4: Add RSArea loader to a plugin
```
User: "Add area/minimap support to OSRSPlugin-218"
```
Actions: Read reference client's area definition, create RSAreaLoaderOSRS class, wire it into the plugin's initializePlugin() and onGameLoaded(), rebuild.

### Example 5: Ambiguous input (default behavior)
```
User: "OSRSPlugin-218 revision 218"
```
Actions: No verb specified — default to inspecting the existing plugin. Check if OSRSPlugin-218 exists, read its loaders, and ask the user what they want to do (fix a bug, add a loader, change revision target).

## Troubleshooting

**Build fails with "cannot find symbol"**
- Check that the plugin's module is included in `settings.gradle`
- Plugin inherits dependencies from `Plugins/build.gradle` (`implementation project(':Editor')`)
- Verify import statements match RSPSi's package structure (com.jagex.cache.*)

**Cache loads but definitions are wrong/corrupted**
- Opcode mismatch between reference client and plugin loader — re-examine every opcode
- Buffer read order incorrect — ensure bytes are read in exact same sequence
- Wrong archive ID used in onGameLoaded — verify config type IDs match revision
- Signed/unsigned mismatch — check if reference uses `readUnsignedShort()` vs `readShort()`
- Byte count mismatch — especially sound opcodes 78/79 which changed between revisions

**Plugin not discovered at runtime**
- Check META-INF/services file is generated (run `serviceLoaderBuild` task)
- Verify the plugin JAR is in `Editor/plugins/active/` (buildAndMove copies to `inactive/`)
- Check settings.gradle includes the plugin module

**NullPointerException in TextureLoaderOSRS**
- The texture definition format is NOT opcode-based — it reads sequential fields
- The FIRST ushort is NOT the sprite ID — it's an average color field. Must skip ushort + byte + ubyte count before reading the actual sprite file IDs
- Always null-check `spriteIndex.archive(spriteId)` before calling `.file(0).getData()`

**Textures appear black or missing**
- Sprite index vs texture index confusion — textures reference sprites by ID
- Brightness calculation mismatch — check pixel generation logic

**ArrayIndexOutOfBoundsException in MapRegion.loadTerrain / Buffer.readUnsignedShort**
- This is almost always an **XTEA key mismatch**, not a plugin issue
- Map regions are XTEA-encrypted. Wrong or missing keys produce garbage data, causing the terrain parser to read past the buffer end
- XTEA keys are revision-specific — keys from rev 236 won't work for a rev 218 cache
- Fix: provide the correct XTEA key JSON file matching the target cache revision
- The terrain tile format itself (`readUnsignedShort` attribute loop) is the same across OSRS revisions — if keys are correct, the format parses fine
- This is a Client core issue (`Cache.java`, `MapRegion.java`, `XTEAManager.java`), not a plugin loader issue

**Map regions don't load**
- Map index format may differ between revisions — check if it uses archive-based or file-based lookup
- Region naming convention: "m{x}_{y}" for terrain, "l{x}_{y}" for locations
- Ensure XTEA keys are loaded and match the cache revision
