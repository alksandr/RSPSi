# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RSPSi is a JavaFX-based map editor for RuneScape Private Servers (RSPS). It loads RuneScape cache files, renders 3D terrain/objects onto a canvas, and provides editing tools. It supports multiple cache revisions (317, OSRS, RS3) via a plugin system.

## Build Commands

```bash
# Build all modules
./gradlew build

# Build shadow JAR (fat JAR with all dependencies)
./gradlew shadowJar

# Run the editor (launches LauncherWindow)
./gradlew Editor:run

# Build a single module
./gradlew Client:build
./gradlew Editor:build
./gradlew Plugins:OSRSPlugin:build

# Generate ServiceLoader metadata for plugins
./gradlew Plugins:OSRSPlugin:serviceLoaderBuild

# Create runtime image / installer
./gradlew Editor:runtime
./gradlew Editor:jpackage
```

- **Java 21** required (sourceCompatibility/targetCompatibility)
- **Gradle 8.14.3** (wrapper included)
- **No test suite** exists in this project

## Module Architecture

```
RSPSiSuite (root)
├── Client/     → Core engine: cache loading, rendering, scene graph, definitions
├── Editor/     → JavaFX UI: launcher, main editor window, controls, FXML layouts
└── Plugins/
    └── OSRSPlugin/  → OSRS cache format loaders (the only plugin currently)
```

- **Client** has no dependency on Editor. Editor depends on Client.
- **Plugins** depend on Client (for base loader classes) but not on Editor.

## Plugin System

Two plugin interfaces, both discovered via Java `ServiceLoader`:

1. **`ClientPlugin`** (`com.rspsi.plugins.core`) — Cache format support. Registered loaders as singletons in `initializePlugin()`, then `onGameLoaded(client)` initializes with cache data. Plugin JARs go in `Editor/plugins/active/`.

2. **`ApplicationPlugin`** (`com.rspsi.plugins.ui`) — UI extensions. Single `initialize(MainWindow)` method.

The Gradle `serviceloader` plugin auto-generates `META-INF/services/` entries. Only one ClientPlugin can be active at a time.

### Creating a New Plugin

Implement version-specific subclasses of the abstract loaders (`ObjectDefinitionLoader`, `FloorDefinitionLoader`, `MapIndexLoader`, `TextureLoader`, etc.), then register them as singletons in your `ClientPlugin.initializePlugin()`. See `OSRSPlugin.java` and its `loader/` package for the pattern.

## Key Architectural Patterns

### Definition Loaders (Abstract Factory + Singleton)
Each loader type (`ObjectDefinitionLoader`, `FloorDefinitionLoader`, etc.) has an abstract base in Client with a static `instance` field. Plugins replace these instances at startup to provide revision-specific parsing.

### Cache Architecture
`Cache.java` auto-detects format (317/OSRS/RS3) based on cache structure. Cache indices: 0=ANIMATIONS, 1=SKELETONS, 2=CONFIGS, 5=MAPS, 7=MODELS, 8=SPRITES, 9=TEXTURES. The `CacheFileType` enum maps these.

### Async Resource Loading
`ResourceProvider` loads heavy assets (models, textures, frames) off-thread, publishing `ResourceResponse` events via GreenRobot EventBus (`ThreadMode.ASYNC`). Subscribers: `Chunk`, `BasicChunk`, `Client`, `MeshLoader`.

### Rendering Pipeline
`Client.java` (singleton) renders the scene graph to a `BufferedImage`, converted via `SwingFXUtils` to display in a JavaFX `FXCanvas`.

### Settings
JSON-based persistence in `~/.rspsi/settings.json`. `Config.java` uses JavaFX observable properties for UI binding.

## Entry Points

- **`LauncherWindow.main()`** — Application entry. Shows plugin/cache selector UI.
- **`MainWindow.start(Stage)`** — Main editor. Loads cache, initializes Client, renders scene.
- **`Client.initialize(w, h)`** — Creates singleton rendering engine.
- **`ClientPluginLoader.loadPlugins()`** — Scans `Editor/plugins/active/*.jar` for plugins.

## Key Dependencies

- `com.displee:rs-cache-library:7.3.0` — RuneScape cache file parsing
- `org.openjfx:javafx:17` — UI framework
- `controlsfx:11.2.2`, `jfoenix:9.0.10` — JavaFX UI components
- `org.greenrobot:eventbus-java:3.3.1` — Async event system
- `org.quartz-scheduler:quartz:2.5.0` — Auto-save scheduling
- `io.freefair.lombok` — Annotation processing (getters/setters/builders)

## XTEA Keys

Map region decryption uses XTEA keys managed by `XTEAManager`. Keys are stored as JSON and required for loading encrypted map regions.
