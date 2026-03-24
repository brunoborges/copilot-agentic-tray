# Copilot CLI Tray

> 🤖 A cross-platform system tray app to track and manage GitHub Copilot CLI sessions and remote coding agents.

[![Status: Specification](https://img.shields.io/badge/status-specification-blue)](SPECIFICATION.md)
[![Platform: Linux | macOS | Windows](https://img.shields.io/badge/platform-Linux%20%7C%20macOS%20%7C%20Windows-lightgrey)](#)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue?logo=openjdk)](https://openjdk.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Overview

**Copilot CLI Tray** lives in your system tray and gives you real-time visibility into all your [GitHub Copilot CLI](https://docs.github.com/copilot/concepts/agents/about-copilot-cli) sessions and remote coding agents — without needing to keep a terminal window open.

Built on the [GitHub Copilot SDK for Java](https://github.com/github/copilot-sdk-java), it connects to the Copilot CLI process running on your machine and surfaces session data, usage telemetry, and key management actions directly in the tray menu.

---

## Features

- **Session overview** — see all active and archived Copilot CLI sessions at a glance
- **Live telemetry** — token count, context window usage %, message count per session
- **Model visibility** — see which AI model each session is using (Claude, GPT, etc.)
- **Subagent tracking** — monitor parallel subagents spawned via `/fleet` mode
- **Session actions** — Resume, Cancel, Delete sessions from the tray menu
- **OS notifications** — get notified when context window fills up, errors occur, or permissions are requested
- **Settings window** — detailed view with usage history, preferences, and model info
- **Cross-platform** — Linux, macOS, Windows on both x86_64 and arm64

---

## System Requirements

- **Java 21+** (or use the native binary — no JVM required)
- **GitHub Copilot CLI** installed and in `PATH` (version 0.0.411+ recommended)
- **Active GitHub Copilot subscription**

---

## Quick Start

> 🚧 This project is currently in the **specification phase**. Implementation is coming soon.

Once released, you'll be able to:

```bash
# Download the native binary for your platform from GitHub Releases
# e.g., on macOS arm64:
curl -LO https://github.com/brunoborges/copilot-cli-tray/releases/latest/download/copilot-cli-tray-macos-arm64
chmod +x copilot-cli-tray-macos-arm64
./copilot-cli-tray-macos-arm64
```

Or run from the fat JAR:

```bash
java -jar copilot-cli-tray.jar
```

---

## How It Works

Copilot CLI Tray uses the [GitHub Copilot SDK for Java](https://github.com/github/copilot-sdk-java) to connect to your locally-running Copilot CLI process via its JSON-RPC interface. It subscribes to session lifecycle events, usage info events, and subagent events to keep the tray menu up-to-date in real time.

```
System Tray Menu  ←→  Session Manager  ←→  SDK Bridge (CopilotClient)  ←→  copilot (CLI process)
```

Key SDK integrations:
- `CopilotClient.listSessions()` — enumerate active and archived sessions
- `CopilotClient.listModels()` — display available AI models
- `SessionUsageInfoEvent` — live token/context window telemetry
- `SessionStartEvent` / `SessionShutdownEvent` — session lifecycle
- `SubagentStartedEvent` / `SubagentCompletedEvent` — agent tracking
- `PermissionRequestedEvent` — permission request notifications

---

## Tray Menu

```
🤖 Copilot CLI Tray
────────────────────────────────────
▸ Active Sessions (2)
  ├─ 📝 my-feature [claude-sonnet-4.6]
  │    ├─ Status: BUSY (42% context)
  │    ├─ Resume in Terminal
  │    ├─ Cancel Session
  │    └─ Delete Session
  └─ 📝 fix-bug [gpt-5.2]
       ├─ Status: IDLE (18% context)
       ├─ Resume in Terminal
       └─ Cancel Session
────────────────────────────────────
▸ Archived Sessions (3)
▸ Usage Summary
  ├─ Tokens: 12,345 / 100,000 (12%)
  └─ Premium Requests: 47 used
────────────────────────────────────
⚙  Open Settings...
🔄 New Session
────────────────────────────────────
✕  Quit
```

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
|-----------------|----------------------------------------------------|
| Language        | Java 21+                                           |
| Build           | Maven                                              |
| Copilot SDK     | `com.github:copilot-sdk-java`                      |
| System Tray     | [Dorkbox SystemTray](https://github.com/dorkbox/SystemTray) |
| Settings UI     | JavaFX (OpenJFX)                                   |
| Native binaries | GraalVM Native Image                               |
| CI/CD           | GitHub Actions (multi-platform build matrix)       |

---

## Project Status

| Phase   | Description                            | Status      |
|---------|----------------------------------------|-------------|
| Phase 1 | Core tray + active session display     | 📋 Planned  |
| Phase 2 | Usage telemetry + settings window      | 📋 Planned  |
| Phase 3 | Models, subagents, notifications       | 📋 Planned  |
| Phase 4 | GraalVM native binaries + auto-update  | 📋 Planned  |

See [SPECIFICATION.md](SPECIFICATION.md) for the full design.

---

## Contributing

Contributions are welcome! Please open an issue to discuss what you'd like to add or change.

## License

MIT — see [LICENSE](LICENSE) for details.
