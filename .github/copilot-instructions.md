# GitHub Copilot Agentic Tray — Copilot Instructions

## Build, Test & Run

All commands run from the `java/` directory. Always use `./mvnw` (Maven wrapper) instead of `mvn`.

```bash
# Build and run tests (ALWAYS do this, not just compile)
./mvnw clean verify

# Run locally (recommended for development)
./mvnw javafx:run -pl app

# Run tests only
./mvnw test

# Run a single test class
./mvnw test -pl app -Dtest=SessionManagerTest

# Build without tests (produces JAR + deps in app/target/mods/)
./mvnw clean package -pl app -DskipTests
```

CI runs `mvn -B clean verify` from `java/` across a matrix of macOS, Linux, and Windows (x86_64 + arm64).

## Architecture

```
System Tray (AWT)  ←→  TrayManager  ←→  SessionManager  ←→  SdkBridge (CopilotClient)
                                              ↑                      ↓
                              SessionDiskReader              EventRouter
                          (~/.copilot/session-state/)    (routes SDK events)
                                    ↕
                             SettingsWindow (JavaFX)
```

- **`TrayApplication`** — wires all components together at startup; owns the lifecycle
- **`SessionManager`** — single source of truth for session state; thread-safe; all mutations go through it
- **`SdkBridge`** — wraps `CopilotClient` from `copilot-sdk-java`; polls session list, attaches event listeners per session
- **`EventRouter`** — translates raw SDK events into `SessionManager` mutations using pattern-matching `switch`
- **`SessionDiskReader`** — reads `~/.copilot/session-state/` directly for sessions the SDK doesn't expose (token accuracy, offline access, checkpoints)
- **`TrayManager`** — owns AWT `SystemTray` and rebuilds the `PopupMenu` dynamically from `SessionManager` state
- **`SettingsWindow`** — JavaFX dashboard window (single instance, shown/hidden); opened from tray menu

## Key Conventions

### JPMS (Java Platform Module System)
The project is a JPMS module (`module com.github.copilot.tray`). When adding new packages, update `module-info.java` with the appropriate `requires`, `exports`, and `opens` directives. Jackson requires `opens` for packages it deserializes; JavaFX FXML requires `opens` for controller packages.

### Immutable `SessionSnapshot` records
`SessionSnapshot` is a Java `record` — treat it as immutable. All state changes produce a new instance via `with*` methods (e.g., `withStatus(...)`, `withUsage(...)`). Never mutate session state directly; always go through `SessionManager`.

### Thread safety
`SessionManager` uses `ConcurrentHashMap` for sessions and `CopyOnWriteArrayList` for listeners. SDK events arrive on background threads; UI updates must be dispatched to the JavaFX thread via `Platform.runLater(...)`.

### Logging
Use SLF4J: `private static final Logger LOG = LoggerFactory.getLogger(ClassName.class);`. The runtime binding is `slf4j-jdk14`.

### Maven multi-module layout
`java/pom.xml` is the parent POM managing all dependency versions in `<dependencyManagement>`. `java/app/pom.xml` declares dependencies without versions. Platform-specific installer types (`dmg`, `deb`, `msi`) are set via Maven profiles that activate automatically based on OS.

### Config persistence
`AppConfig` is a Jackson-serialized POJO stored at a platform-specific path. `ConfigStore` handles read/write. Add new config fields to `AppConfig` and open the package in `module-info.java` if needed.

### TilesFX tiles
All tiles are created exclusively via `TileBuilder.create()` — never via constructors. Always set `.animated(false)` and `.textSize(Tile.TextSize.SMALLER)` on every tile. Update live data by mutating the tile or its `ChartData` in place (e.g., `tile.setValue(...)`, `tile.setDescription(...)`, `chartData.setValue(...)`) — don't rebuild tiles on each update. Colors are defined as class-level `Color.web("#hex")` constants. The breakdown tiles use `SkinType.PERCENTAGE`, summary number tiles use `SkinType.NUMBER`, and the context window uses `SkinType.GAUGE`.

### TerminalLauncher — platform detection
OS is detected via `System.getProperty("os.name", "").toLowerCase()`. Each platform has its own strategy:
- **macOS**: writes a temp `.command` shell script to a temp file, marks it executable, and launches via `open <file>`. Falls back to `osascript` + Terminal if file creation fails.
- **Windows**: uses `cmd /c start wt [--startingDirectory <dir>]` (Windows Terminal).
- **Linux**: tries terminal emulators in order — `gnome-terminal`, `konsole`, `xterm`, `x-terminal-emulator` — using a single `sh -c` command with `command -v` guards.

Shell paths passed into commands are always escaped via `escapeShell()` (single-quote wrapping with internal `'` escaped as `'\''`).

### UI Design System
**Before making any UI changes, read [`java/DESIGN.md`](../java/DESIGN.md) in full.** It is the authoritative reference for all visual decisions — design tokens, color palette, CSS classes, component patterns, page layouts, window management, and the new-UI checklist. Do not guess at spacing, colors, or layout conventions; they are all specified there.

Key points summarized here for quick reference:

### CSS-Driven Spacing
All padding and spacing must use CSS classes — never inline `setPadding()` for layout-level spacing.

| Class | Purpose |
|---|---|
| `.content-padding` | 20px all sides — every page's top-level content container |
| `.sessions-section` | 20px all sides — rightBox in Sessions page |
| `.sessions-card` | 8px padding, 8px border-radius, border, shadow — card wrapper |
| `.sessions-detail-pane` | 0px padding, 8px spacing — detail pane |
| `.about-card` | Detail card container with background, border, radius |

### Window Management
- **Cmd+W / Ctrl+W** must close every popup window (use `SHORTCUT_DOWN` modifier via scene accelerators).
- **Singleton windows**: viewer windows use a `static Map<String, Viewer> OPEN_VIEWERS` to prevent duplicates. Callers use `static showViewer(...)` factory methods.
- **Cleanup**: register `stage.setOnHidden(e -> OPEN_VIEWERS.remove(key))`.

### Cross-View Sync
- **SessionManager** is the single source of truth. All mutations trigger `notifyListeners()`.
- When deleting from any view, call `sessionManager.removeSession()` so all listeners refresh.
- **PrunePanel** resets `hasScanned` on SessionManager changes → auto-rescans on next tab visit.
