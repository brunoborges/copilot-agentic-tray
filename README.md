# GitHub Copilot Agentic Tray

> 🤖 A cross-platform system tray app to track and manage GitHub Copilot CLI sessions and remote coding agents.

[![Platform: Linux | macOS | Windows](https://img.shields.io/badge/platform-Linux%20%7C%20macOS%20%7C%20Windows-lightgrey)](#)
[![Java 25](https://img.shields.io/badge/Java-25-blue?logo=openjdk)](https://openjdk.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Overview

**GitHub Copilot Agentic Tray** lives in your system tray and gives you real-time visibility into all your [GitHub Copilot CLI](https://docs.github.com/copilot/concepts/agents/about-copilot-cli) sessions and remote coding agents — without needing to keep a terminal window open.

Built on the [GitHub Copilot SDK for Java](https://github.com/github/copilot-sdk-java), it connects to the Copilot CLI process running on your machine and surfaces session data, usage telemetry, and key management actions directly in the tray menu.

---

## Features

- **Session overview** — see all active and archived Copilot CLI sessions at a glance
- **Live telemetry** — token count, context window usage %, message count per session
- **Model visibility** — see which AI model each session is using (Claude, GPT, etc.)
- **Subagent tracking** — monitor parallel subagents spawned via `/fleet` mode
- **Session actions** — Resume, Cancel, Delete sessions from the tray menu
- **OS notifications** — get notified when context window fills up, errors occur, or permissions are requested
- **Dashboard window** — detailed view with usage tiles, donut charts, session details, and preferences
- **Session pruning** — clean up old/empty sessions to reclaim disk space
- **Cross-platform** — Linux, macOS, Windows on both x86_64 and arm64

---

## Prerequisites

- **JDK 25** (EA) — download from [jdk.java.net/25](https://jdk.java.net/25/) or install via [SDKMAN](https://sdkman.io/)
- **Maven 3.9+**
- **GitHub Copilot CLI** installed and in `PATH`
- **Active GitHub Copilot subscription**

---

## Getting Started

### Clone the repository

```bash
git clone https://github.com/brunoborges/copilot-agentic-tray.git
cd copilot-agentic-tray
```

### Build

```bash
cd java
mvn clean install
```

### Run

```bash
cd java
mvn -pl app javafx:run
```

The app will start in the system tray. Look for the Copilot icon in your menu bar (macOS), system tray (Linux), or notification area (Windows).

### Run Tests

```bash
cd java
mvn test
```

---

## How It Works

GitHub Copilot Agentic Tray uses the [GitHub Copilot SDK for Java](https://github.com/github/copilot-sdk-java) to connect to your locally-running Copilot CLI process via its JSON-RPC interface. It subscribes to session lifecycle events, usage info events, and subagent events to keep the tray menu up-to-date in real time.

```
System Tray  ←→  Session Manager  ←→  SDK Bridge (CopilotClient)  ←→  copilot (CLI process)
     ↕                                                                    ↕
Dashboard UI                                                    Session Disk Reader
(JavaFX)                                                     (~/.copilot/session-state/)
```

Key SDK integrations:
- `CopilotClient.listSessions()` — enumerate active and archived sessions
- `CopilotClient.listModels()` — display available AI models
- `SessionUsageInfoEvent` — live token/context window telemetry
- `SessionStartEvent` / `SessionShutdownEvent` — session lifecycle
- `SubagentStartedEvent` / `SubagentCompletedEvent` — agent tracking
- `PermissionRequestedEvent` — permission request notifications

Sessions are also discovered directly from disk (`~/.copilot/session-state/`) to show sessions the SDK server may not know about.

---

## Tray Menu

```
🤖 GitHub Copilot Agentic Tray
────────────────────────────────────
🟢 CLI Connected
▸ Active Sessions (2)
  ├─ my-feature [claude-sonnet-4.6]
  │    ├─ Status: BUSY (42% context)
  │    └─ Resume in Terminal
  └─ fix-bug [gpt-5.2]
       └─ Status: IDLE (18% context)
────────────────────────────────────
⚙  Open Dashboard...
🔄 New Session
────────────────────────────────────
✕  Quit
```

---

## Dashboard

The Dashboard window provides:

- **Sessions tab** — directory-first layout with session table, detail pane, usage tiles (donut chart, gauges, context breakdown), and actions (Resume, Rename, Delete)
- **Prune tab** — scan and clean up old/empty session directories
- **Preferences tab** — configure CLI path, poll interval, warning thresholds, notifications
- **About tab** — version info and links

---

## Supported Platforms

| OS      | x86_64 | arm64 |
|---------|--------|-------|
| macOS   | ✅     | ✅    |
| Linux   | ✅     | ✅    |
| Windows | ✅     | ✅    |

---

## Technology Stack

| Component       | Technology                                         |
|-----------------|--------------------------------------------------  |
| Language        | Java 25                                            |
| Build           | Maven                                              |
| UI Framework    | JavaFX 25 (OpenJFX) + TilesFX                     |
| Copilot SDK     | `com.github:copilot-sdk-java:0.1.32-java.0`       |
| System Tray     | `java.awt.SystemTray`                              |
| CI/CD           | GitHub Actions (multi-platform build matrix)       |

---

## Project Structure

```
copilot-agentic-tray/
├── SPECIFICATION.md           # Full design specification
├── README.md
├── LICENSE
└── java/
    ├── IMPLEMENTATION.md      # Implementation details
    ├── pom.xml                # Parent POM
    └── app/
        ├── pom.xml            # App module POM
        └── src/main/java/com/github/copilot/tray/
            ├── Main.java              # JavaFX entry point
            ├── TrayApplication.java   # App wiring & lifecycle
            ├── config/                # Configuration store
            ├── sdk/                   # SDK bridge & terminal launcher
            ├── session/               # Session manager, disk reader, pruner
            ├── tray/                  # System tray manager
            └── ui/                    # Dashboard, usage tiles, prune panel
```

---

## Contributing

Contributions are welcome! Please open an issue to discuss what you'd like to add or change.

## License

MIT — see [LICENSE](LICENSE) for details.
