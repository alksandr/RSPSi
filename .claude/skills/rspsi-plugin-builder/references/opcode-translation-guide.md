# Opcode Translation Guide

This guide explains how to translate opcode-based definition decoders from a reference RuneScape client into RSPSi plugin loaders.

## General Pattern

Reference clients decode definitions using opcode-based loops:
```
while (true) {
    int opcode = read byte from buffer
    if (opcode == 0) break;  // end of definition
    switch (opcode) {
        case 1: ... break;
        case 2: ... break;
        // etc.
    }
}
```

RSPSi loaders follow the exact same pattern. The key is matching every opcode exactly.

## Object Definition Opcodes (OSRS Rev 218)

| Opcode | Data Read | RSPSi Field |
|---|---|---|
| 1 | ubyte count, then count*(ushort modelId + ubyte modelType) | modelIds[], modelTypes[] |
| 2 | string (null-terminated) | name |
| 5 | ubyte count, then count*ushort modelId (no types) | modelIds[] (types = null) |
| 14 | ubyte | width |
| 15 | ubyte | length |
| 17 | (no data) | solid = false, impenetrable = false |
| 18 | (no data) | impenetrable = false |
| 19 | ubyte | wallOrDoor (interactive type) |
| 21 | (no data) | contouredGround = true |
| 22 | (no data) | delayShading = true (mergeNormals) |
| 23 | (no data) | occludes = true |
| 24 | ushort (0xFFFF = -1) | animation |
| 27 | (no data) | interactType = 1 |
| 28 | ubyte | decorDisplacement |
| 29 | byte (signed) | ambientLighting |
| 30-34 | string (null-terminated) | interactions[opcode - 30] (5 actions, "Hidden" = null) |
| 39 | byte (signed) | lightDiffusion (ref client multiplies by 25, RSPSi stores raw) |
| 40 | ubyte count, then count*(ushort orig + ushort replacement) | originalColours[], replacementColours[] |
| 41 | ubyte count, then count*(ushort orig + ushort replacement) | retextureToFind[], textureToReplace[] |
| 61 | ushort | category (skip) |
| 62 | (no data) | inverted = true |
| 64 | (no data) | castsShadow = false |
| 65 | ushort | scaleX |
| 66 | ushort | scaleY |
| 67 | ushort | scaleZ |
| 68 | ushort | mapscene |
| 69 | ubyte | surroundings (blocking mask) |
| 70 | ushort | translateX |
| 71 | ushort | translateY |
| 72 | ushort | translateZ |
| 73 | (no data) | obstructsGround = true |
| 74 | (no data) | hollow = true |
| 75 | ubyte | supportItems |
| 77 | ushort varbit, ushort varp, ubyte count, (count+1)*ushort morphIds, last=-1 | morphisms[], varbit, varp |
| 78 | ushort + ubyte | ambientSoundId + ambientSoundDistance (skip, 3 bytes total) |
| 79 | ushort + ushort + ubyte + ubyte count + count*ushort | sound data (skip, 6 + count*2 bytes total) |
| 81 | ubyte | contouredGround value (skip, 1 byte) |
| 82 | ushort | areaId |
| 89 | (no data) | randomizeAnimStart = true |
| 92 | ushort varbit, ushort varp, ushort default, ubyte count, (count+1)*ushort morphIds | morphisms with default |
| 249 | ubyte count, then count*(ubyte isString, 24-bit key, string or int value) | custom params (skip) |

### Object opcodes added AFTER rev 218

These opcodes exist in later revisions but NOT in rev 218. If downgrading to 218, remove them:

| Opcode | Added In | Data Read | Notes |
|---|---|---|---|
| 60 | ~rev 220 | (no data, was ushort) | Minimap function — not used |
| 78 (extra byte) | ~rev 220 | +ubyte | ambientSoundRetain added (total becomes 4 bytes) |
| 79 (extra byte) | ~rev 220 | +ubyte before count | ambientSoundRetain added (total becomes 7 + count*2) |
| 90 | ~rev 220 | (no data) | Defer anim start |
| 91 | ~rev 225 | ubyte | Unknown |
| 93 | ~rev 225 | ubyte + ushort + ubyte + ushort | Unknown (6 bytes) |
| 95 | ~rev 225 | ubyte | Unknown |
| 96 | ~rev 225 | ubyte | Unknown |

## Underlay Floor Opcodes

| Opcode | Data Read | RSPSi Field |
|---|---|---|
| 1 | 24-bit int (3 bytes) | rgb (color) |

## Overlay Floor Opcodes

| Opcode | Data Read | RSPSi Field |
|---|---|---|
| 1 | 24-bit int (3 bytes) | rgb (primary color) |
| 2 | ubyte | texture (texture ID) |
| 5 | (no data) | shadowed = false (hideUnderlay) |
| 7 | 24-bit int (3 bytes) | anotherRgb (secondary color) |

## Animation (Sequence) Opcodes (OSRS Rev 218)

| Opcode | Data Read | RSPSi Field |
|---|---|---|
| 1 | ushort frameCount, then frameCount*ushort durations, frameCount*ushort frameIds (low), frameCount*ushort frameIds (high combined) | primaryFrames[], durations[] |
| 2 | ushort | loopOffset |
| 3 | ubyte count, then count*ubyte | interleaveOrder[] (last entry = 9999999) |
| 4 | (no data) | stretches = true |
| 5 | ubyte | priority |
| 6 | ushort | playerOffhand |
| 7 | ushort | playerMainhand |
| 8 | ubyte | maximumLoops |
| 9 | ubyte | animatingPrecedence |
| 10 | ubyte | walkingPrecedence |
| 11 | ubyte | replayMode |
| 12 | ubyte count, then count*ushort (low) + count*ushort (high combined) | chatFrameIds (skip) |
| 13 | ubyte count, then count*24-bit int | frameSounds (skip, 1 + count*3 bytes) |
| 14 | int | animMayaID (skip, 4 bytes) |
| 15 | ushort count, then count*(ushort key + 24-bit int value) | animMayaFrameSounds (skip, 2 + count*5 bytes) |
| 16 | ushort + ushort | animMayaStart + animMayaEnd (skip, 4 bytes) |
| 17 | ubyte count, then count*ubyte (indices into boolean[256]) | animMayaMasks (skip) |

### Animation opcodes added AFTER rev 218

| Opcode | Added In | Data Read | Notes |
|---|---|---|---|
| 13 (format change) | ~rev 236 | skip(4) | Changed from variable-length to fixed 4 bytes |
| 14 (format change) | ~rev 236 | ushort count, skip(count*8) | Changed from int to variable-length array |
| 15 (format change) | ~rev 236 | skip(4) | Changed from variable-length to fixed 4 bytes |
| 16 (format change) | ~rev 236 | skip(1) | Changed from 2 ushorts (4 bytes) to 1 byte |
| 18 | ~rev 236 | string | Unknown (not in rev 218) |
| 19 | ~rev 236 | (no data) | Cross-world sounds (not in rev 218) |

## Graphic (SpotAnim) Opcodes

| Opcode | Data Read | RSPSi Field |
|---|---|---|
| 1 | ushort | model |
| 2 | ushort | animationId (resolves to Animation) |
| 4 | ushort | breadthScale |
| 5 | ushort | depthScale |
| 6 | ushort | orientation |
| 7 | ubyte | ambience |
| 8 | ubyte | modelShadow |
| 40 | ubyte count, then count*(ushort orig + ushort replacement) | originalColours[], replacementColours[] |
| 41 | ubyte count, then count*(ushort orig + ushort replacement) | texture replacements (skip in RSPSi) |

## Variable Bit Opcodes

| Opcode | Data Read | RSPSi Field |
|---|---|---|
| 1 | ushort settingId, ubyte lowBit, ubyte highBit | setting, low, high |

## RSArea Opcodes (Rev 218)

| Opcode | Data Read | RSPSi Field |
|---|---|---|
| 1 | smart int (bigSmart2) | spriteId |
| 2 | smart int (bigSmart2) | anInt1967 |
| 3 | string (null-terminated) | name |
| 4 | 24-bit int | anInt1959 |
| 5 | 24-bit int | (skip) |
| 6 | ubyte | anInt1968 |
| 7 | ubyte (flags) | various boolean flags |
| 8 | ubyte | (skip) |
| 10-14 | string (null-terminated) | aStringArray1969[opcode - 10] |
| 15 | complex: ubyte size, size*2 shorts, int, ubyte size2, size2 ints, size bytes | coordinate/polygon data |
| 16 | (no data) | no-op |
| 17 | string (null-terminated) | aString1970 |
| 18 | smart int (bigSmart2) | (skip) |
| 19 | ushort | anInt1980 |
| 21 | int | (skip) |
| 22 | int | (skip) |
| 23 | ubyte + ubyte + ubyte | (skip, 3 bytes) |
| 24 | short + short | (skip, 4 bytes) |
| 25 | smart int (bigSmart2) | (skip) |
| 28 | ubyte | (skip) |
| 29 | ubyte | (skip) |
| 30 | ubyte | (skip) |

Other opcodes vary significantly between revisions — always check the reference client.

## Texture Definition Format (NOT Opcode-Based)

Unlike all other definitions, textures use a sequential field format, not opcodes:

| Offset | Data Read | Field | Notes |
|---|---|---|---|
| 0 | ushort | field1777 | Average color — **NOT the sprite ID** |
| 2 | byte | field1778 | Boolean flag |
| 3 | ubyte | count | Number of sprite file IDs |
| 4 | count * ushort | fileIds[] | **Actual sprite file IDs** — use fileIds[0] to load the sprite |
| 4 + count*2 | (count-1) * ubyte | field1780[] | Blending mode (only if count > 1) |
| ... | (count-1) * ubyte | field1781[] | Blending mode 2 (only if count > 1) |
| ... | count * int | field1786[] | Color multipliers |
| ... | ubyte | animationDirection | |
| ... | ubyte | animationSpeed | |

**CRITICAL BUG WARNING**: The first ushort is the average color, NOT the sprite ID. A common mistake is reading it as the sprite ID — this causes `NullPointerException` when `spriteIndex.archive(wrongId)` returns null. Always skip to `fileIds[0]` for the actual sprite.

Always null-check `spriteIndex.archive(spriteId)` before accessing `.file(0).getData()`.

## Common Pitfalls

1. **Short vs UShort**: Reference clients often use `readUnsignedShort()` where the value could be interpreted as signed. Use `readUShort()` in RSPSi by default. For opcodes 70-72 (translate offsets), the reference client uses unsigned shorts despite these being spatial offsets.

2. **Smart integers**: Some newer revisions use smart-encoded integers (variable length: 1 or 2 bytes). Check if the reference client uses `readSmart()`, `readUnsignedSmart()`, or `readBigSmart2()`.

3. **String encoding**: OSRS uses CP1252 null-terminated strings. Use RSPSi's `buffer.readOSRSString()` (null-terminated, byte 0x00). Do NOT use `buffer.readString()` which uses newline termination (byte 0x0A) — this is for older 317-era formats only.

4. **24-bit color**: Colors are stored as 3 separate bytes (R, G, B) combined into `(r << 16) | (g << 8) | b`, or read as a single `readUTriByte()` / `readMedium()`.

5. **Frame ID encoding**: Frame IDs pack file ID and frame index: `fullId = (fileId << 16) | frameIndex`. When reading, the low word is read first, then the high word is combined.

6. **Morphism opcode 77 vs 92**: Opcode 77 has no default morph ID (last entry = -1). Opcode 92 adds a default morph ID (ushort) read between varp and count. Both store varbit + varp + morph ID array.

7. **Post-decode defaults**: After all opcodes are read, some fields need computed defaults:
   - wallOrDoor/interactive auto-detection from model presence and interactions
   - Animation precedence defaults based on interleave presence
   - Impenetrable cleared when hollow is set
   - supportItems derived from solid/interactType

8. **Interactions array size**: Rev 218 uses 5 actions (opcodes 30-34). Later revisions expand to 30-38 (9 actions). Reuse the existing interactions array rather than creating a new one per opcode hit.

9. **Opcode byte counts change between revisions**: Opcodes 78 and 79 gained an extra byte (~rev 220) for `ambientSoundRetain`. Always verify exact byte counts against the reference client for your target revision.
