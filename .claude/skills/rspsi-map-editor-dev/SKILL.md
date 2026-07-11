---
name: rspsi-map-editor-dev
description: |
  Go-to guide for developing and debugging the RSPSi map editor CODEBASE itself (the
  JavaFX app — Client engine, Editor UI, plugin loading, cache detection, rendering),
  NOT the game client/server it edits caches for. Use whenever working inside the RSPSi
  repo: adding cache-revision or plugin support, debugging why a cache won't load or a
  map won't render, the editor hangs on "Loading...", a plugin fails at onGameLoaded,
  wiring editor tooling, or probing a cache to find out what format it is. Also use when
  someone says "the editor won't load the map", "cache format", "plugin not loading",
  "NPE on game load", "debug the running editor", or names a RuneScape revision they
  want RSPSi to open. For writing/porting the loader opcode logic inside a plugin, defer
  to the rspsi-plugin-builder skill; this skill covers the surrounding architecture,
  build/run/deploy flow, format detection, and runtime observability.
compatibility: project
metadata:
  version: "1.0.0"
---

# RSPSi map editor development

RSPSi is a JavaFX desktop app that loads RuneScape cache files, renders terrain/objects
to a canvas, and lets you edit maps. This skill is for hacking on **the editor itself**.
When a change spans cache loading, rendering, the plugin system, or the run/deploy loop,
start here — it maps the terrain so you fix the root cause instead of the nearest symptom.

## First, orient

- Confirm you're in the repo root: `settings.gradle` listing module includes, a
  `Client/`, `Editor/`, and `Plugins/` dir. Read `CLAUDE.md` for the current snapshot.
- **Java 21, Gradle 8.14.3 (wrapper included). There is no test suite** — you cannot
  lean on tests to tell you a change works. You verify by *running the editor* and
  watching its runtime output (see "Debug the running editor" below). Budget for that.

## Module architecture (and why the boundaries matter)

```
Client/   core engine: cache load, rendering, scene graph, definitions, loader base classes
Editor/   JavaFX UI: launcher, main window, controls, FXML  (depends on Client)
Plugins/  cache-format support         (depends on Client only, NEVER on Editor)
```

The dependency arrows are one-way on purpose: **Client has no dependency on Editor**, and
plugins depend only on Client. So engine/loader code lives in Client, UI in Editor, and
revision-specific cache parsing in a plugin. If you find yourself reaching from Client
into Editor, or from a plugin into Editor, you're about to break the layering — stop and
put the code on the right side. Keep cache/render logic in Client so every plugin and the
UI can share it.

## The plugin system

Two `ServiceLoader`-discovered interfaces (grep `implements ClientPlugin` /
`implements ApplicationPlugin`):

- **`ClientPlugin`** — cache-format support. Two-phase: `initializePlugin()` constructs
  the revision-specific loaders and installs them as the singletons the engine reads
  (`ObjectDefinitionLoader.instance = ...`, likewise `FloorDefinitionLoader`,
  `MapIndexLoader`, `TextureLoader`, `AnimationDefinitionLoader`, `FrameLoader`, etc.);
  then `onGameLoaded(client)` pulls archives out of the loaded cache and calls each
  loader's `init(...)`. **Only ONE ClientPlugin is active at a time.**
- **`ApplicationPlugin`** — UI extension, single `initialize(MainWindow)`.

Supporting a new revision = subclass the abstract loaders and register them in a
`ClientPlugin`. The abstract-loader base classes live under
`Client/src/main/java/com/jagex/cache/loader/`. For the actual opcode/byte-level decode
work inside those subclasses, **use the `rspsi-plugin-builder` skill** — it owns the
opcode tables, buffer-read mapping, and per-loader patterns. This skill stops at the
architecture and wiring.

## Build, run, deploy — and the deploy gotcha

- Build everything: `./gradlew build`. Single module: `./gradlew Client:build`.
- Run the editor: `./gradlew Editor:run` (launches `LauncherWindow`). There's also a
  `run-editor.bat` at the repo root.
- **The plugin deploy dance (real, repeatedly-bites-people gotcha):** the gradle
  `buildAndMove` task copies a plugin jar to `Editor/plugins/inactive/`, but the editor
  loads plugins from `Editor/plugins/active/`. After building a plugin you MUST copy its
  jar from `inactive/` to `active/` or the editor runs the old one (or none) and you
  debug a ghost. See project memory [[plugin-test-deploy-active]] for the exact commands.

## Entry points (where execution starts)

- `LauncherWindow.main` → plugin/cache selector UI; installs the runtime-observability
  handlers early (console tee + FX uncaught handler).
- `MainWindow.start` → the editor proper; loads the cache, initializes `Client`, renders.
- `Client.initialize` → creates the singleton rendering engine.
- `ClientPluginLoader.loadPlugins` → scans `Editor/plugins/active/*.jar`.

Trace a "won't load" bug forward from `MainWindow.start` → cache construction → plugin
`onGameLoaded`; trace a "renders wrong" bug from `Client.drawGameImage` and the scene graph.

## Cache format detection (the #1 "won't load" root cause)

Cache format is auto-detected in `Cache.java`'s constructor via a chain of predicates —
`is317()`, then an OSRS check (grep `isCacheNewOSRS`), then `isRS3()`. **Each branch
assigns the archive fields** (`configArchive`, `modelArchive`, `mapArchive`,
`skeletonArchive`, `spriteIndex`, `textureIndex`, …). `getFile(CacheFileType.CONFIG)`
just returns `configArchive`.

The failure mode to recognize: if a cache matches **none** of the branches, every archive
field stays `null`, and the first plugin line that does `configIndex.archive(n)` in
`onGameLoaded` throws an NPE — which surfaces as the editor stuck on "Loading…", map never
appears. The NPE is the symptom; the missing detection branch is the cause.

Fix discipline:
1. **Widen the right predicate**, don't null-guard the plugin. One correct branch fixes
   every loader that shares `configIndex`; a guard in one plugin line leaves the rest
   broken.
2. **Add a loud-fail `else`** in the constructor (`throw new IllegalStateException("Unrecognized cache format at " + path)`)
   so the next undetected cache fails with a clear message in the log instead of a
   downstream NPE.
3. **Validate the format by decoding real cache bytes — never trust a revision number,
   a folder name, or a merge/commit label.** Displee-library assumptions can silently
   mismatch a cache. See [[validate-cache-format-empirically]] and the specific
   [[gallifrey-cache-displee-incompat]] for a case where the label lied.

To inspect a cache *outside* the editor (confirm indices/archives before touching
detection code), use the standalone displee/disio/kotlin jars from the gradle
`modules-2` cache — recipe in [[standalone-cache-probe-recipe]].

## Debug the running editor — look, don't guess

The editor writes runtime feedback to `~/.rspsi/logs/` on every run, via the `AiFeedback`
helper (wired in at `LauncherWindow.start` for the console/exception capture and
`Client.drawGameImage` for the frame/state capture):

- `editor.log` — tee of stdout+stderr + uncaught exceptions, fresh each run. **Tail it**
  to see the "Loaded cache in X format!" line, plugin errors, and stack traces.
- `frame.png` — the latest rendered frame, ~2s throttle. **Read it** to actually SEE the
  render — blank canvas, broken tiles, missing textures, wrong camera — instead of
  inferring from code.
- `status.json` — `loadState`, `fps`, memory, camera, region, tool, `lastError`.

When debugging anything about the *running* app, tail the log / Read the png / cat the
json BEFORE theorizing from source. This is the fastest way to tell "did the cache even
load" from "did it load but render wrong." This observability is a concrete instance of
the generic **`ai-runtime-feedback`** skill — read that skill for the design (console tee
not hijack, end-of-render snapshot off-thread, atomic writes, deterministic path) if you
need to extend or repair the channel. Instance notes: [[ai-runtime-feedback-instance]].

## Related skills and memory

- **`rspsi-plugin-builder`** — deep loader/opcode work when adding or fixing a plugin's
  cache decoding. Hand off to it once architecture/wiring is settled.
- **`ai-runtime-feedback`** — the generic runtime-observability pattern the `~/.rspsi/logs`
  channel implements.
- Volatile, per-cache specifics live in project memory, not here — reference them:
  [[plugin-test-deploy-active]], [[validate-cache-format-empirically]],
  [[gallifrey-cache-displee-incompat]], [[standalone-cache-probe-recipe]],
  [[ai-runtime-feedback-instance]].
