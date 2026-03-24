# Copilot CLI Tray — Java Application

The Java implementation of Copilot CLI Tray, a cross-platform system tray application for monitoring and managing GitHub Copilot CLI sessions.

## Prerequisites

- **JDK 25** — install via [SDKMAN](https://sdkman.io/), [Adoptium](https://adoptium.net/), or your preferred distribution
- **Maven 3.9+** — install via `brew install maven`, `sdkman install maven`, or [download](https://maven.apache.org/download.cgi)
- **GitHub Copilot CLI** — installed and in `PATH` ([install instructions](https://docs.github.com/copilot/concepts/agents/about-copilot-cli))
- **Active Copilot subscription** — required for the SDK to connect

Verify your setup:

```bash
java -version    # should show 25.x
mvn -version     # should show 3.9+
copilot --version  # should be installed
```

## Build

```bash
cd java
mvn clean verify
```

This compiles all sources, runs unit tests, and produces the application JAR at `app/target/copilot-cli-tray-1.0.0-SNAPSHOT.jar`.

## Run Locally

### Option 1: JavaFX Maven Plugin (recommended for development)

```bash
cd java
mvn javafx:run -pl app
```

This launches the app with the correct module path and JavaFX configuration. The system tray icon will appear in your menu bar / taskbar.

### Option 2: Run the JAR directly

```bash
cd java
mvn clean package -pl app -DskipTests

java --module-path app/target/mods:app/target/copilot-cli-tray-1.0.0-SNAPSHOT.jar \
     --add-modules com.github.copilot.tray \
     -m com.github.copilot.tray/com.github.copilot.tray.Main
```

> **Note:** The `mvn package` step copies all dependencies to `app/target/mods/` via the maven-dependency-plugin.

### What happens on launch

1. A system tray icon appears (gray circle = idle, green = active sessions)
2. The app connects to your local Copilot CLI process via the SDK
3. It polls for active sessions every 5 seconds (configurable)
4. Right-click the tray icon to see sessions, usage, and actions
5. Click **Open Settings...** for the full JavaFX window with detailed views

## Run Tests

```bash
cd java
mvn test
```

Currently includes 7 unit tests covering `SessionManager` operations (add, archive, usage updates, subagent tracking, change listeners).

## Project Structure

```
java/
├── pom.xml                         # Parent POM (dependency versions)
├── app/
│   ├── pom.xml                     # App module (deps, plugins, profiles)
│   └── src/
│       ├── main/java/
│       │   ├── module-info.java    # JPMS module descriptor
│       │   └── com/github/copilot/tray/
│       │       ├── Main.java                    # JavaFX Application entry point
│       │       ├── TrayApplication.java         # Lifecycle wiring
│       │       ├── config/
│       │       │   ├── AppConfig.java           # Preferences model
│       │       │   └── ConfigStore.java         # JSON persistence
│       │       ├── notify/
│       │       │   └── Notifier.java            # OS notifications
│       │       ├── sdk/
│       │       │   ├── SdkBridge.java           # CopilotClient wrapper
│       │       │   ├── EventRouter.java         # SDK events → state changes
│       │       │   └── TerminalLauncher.java    # Open terminal per platform
│       │       ├── session/
│       │       │   ├── SessionManager.java      # Thread-safe session state
│       │       │   ├── SessionSnapshot.java     # Immutable session record
│       │       │   ├── SessionStatus.java       # ACTIVE/IDLE/BUSY/ARCHIVED/ERROR
│       │       │   ├── UsageSnapshot.java       # Token/context usage
│       │       │   ├── SubagentSnapshot.java    # Subagent tracking
│       │       │   └── SubagentStatus.java      # RUNNING/COMPLETED/FAILED
│       │       ├── tray/
│       │       │   ├── TrayManager.java         # AWT SystemTray + dynamic menu
│       │       │   └── TrayIconState.java       # Icon state enum
│       │       └── ui/
│       │           └── SettingsWindow.java       # JavaFX settings window
│       ├── main/resources/icons/                 # Tray icon PNGs
│       └── test/java/                            # Unit tests
└── IMPLEMENTATION.md                             # Detailed design document
```

## Configuration

Config is stored at a platform-specific location:

| OS      | Path                                              |
|---------|----------------------------------------------------|
| macOS   | `~/Library/Application Support/copilot-cli-tray/config.json` |
| Linux   | `~/.config/copilot-cli-tray/config.json`           |
| Windows | `%APPDATA%\copilot-cli-tray\config.json`           |

Settings can also be changed from the **Preferences** tab in the Settings window.

## Dependencies

| Dependency              | Version        | Purpose                    |
|------------------------|----------------|----------------------------|
| `copilot-sdk-java`     | 0.1.32-java.0  | Copilot CLI integration    |
| JavaFX (OpenJFX)       | 25.0.1          | Settings window UI         |
| Jackson Databind       | 2.19.0          | JSON config persistence    |
| SLF4J                  | 2.0.17          | Logging facade             |
| JUnit Jupiter          | 5.12.2          | Unit testing               |

## Troubleshooting

**"SystemTray is not supported"** — On some Linux desktops (Wayland), the AWT SystemTray may not be available. Install a tray extension for your desktop environment (e.g., `gnome-shell-extension-appindicator` for GNOME).

**SDK fails to connect** — Ensure `copilot` is in your `PATH` and you are logged in (`copilot` → `/login`). The SDK starts its own CLI server process.

**JavaFX module errors** — Make sure you're using JDK 25 (not an older version). The `module-info.java` requires JavaFX 25 modules.
