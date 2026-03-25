# GitHub Copilot Agentic Tray — Project Specification

## Overview

**GitHub Copilot Agentic Tray** is a cross-platform system tray application that provides real-time visibility and management of GitHub Copilot CLI sessions and remote coding agents. It gives developers instant access to session status, usage telemetry, model information, and common session actions — all from the system tray, without requiring a terminal window.

The application integrates with the [GitHub Copilot SDK for Java](https://github.com/github/copilot-sdk-java) to programmatically communicate with the Copilot CLI, and surfaces session data in an always-accessible tray menu.

---

## Goals

- Provide a non-intrusive system tray icon with a live status summary
- Allow users to see all active and archived Copilot CLI sessions at a glance
- Enable key session management actions (resume, cancel, delete) from the tray menu
- Display real-time telemetry: token usage, context window, model name, premium requests
- Offer an optional settings/detail window for advanced views
- Be fully cross-platform: **Linux (x86_64, arm64), macOS (x86_64, arm64), Windows (x86_64, arm64)**

---

## Non-Goals

- Replacing the Copilot CLI terminal UX
- Implementing a chat or code editing interface
- Proxying or intercepting Copilot API traffic directly (all communication goes through the SDK/CLI)

---

## Target Platforms

| OS      | Architectures         | Notes                          |
|---------|-----------------------|--------------------------------|
| macOS   | x86_64, arm64 (Apple Silicon) | Native menu bar icon via AWT/Dorkbox |
| Linux   | x86_64, arm64         | GTK or AppIndicator (via Dorkbox) |
| Windows | x86_64, arm64         | System tray via WinAPI (via Dorkbox) |

---

## Technology Stack

| Component         | Technology                          |
|-------------------|-------------------------------------|
| Language          | Java 21+                            |
| Build system      | Maven                               |
| Copilot SDK       | `com.github:copilot-sdk-java` (latest) |
| System tray       | [Dorkbox SystemTray](https://github.com/dorkbox/SystemTray) |
| Settings window   | JavaFX (OpenJFX) or Swing           |
| Distribution      | Self-contained native image (GraalVM) or fat JAR with JVM |
| Cross-platform binary | GraalVM Native Image (optional phase) |
| Icons             | SVG → PNG at multiple resolutions (16x16, 22x22, 32x32, 64x64) |

### Why Java?

The GitHub Copilot SDK for Java is the primary integration point. Java 21+ with GraalVM Native Image compilation can produce native binaries for all target platforms and architectures, satisfying the cross-platform requirement without bundling a JVM.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    GitHub Copilot Agentic Tray App                      │
│                                                              │
│  ┌─────────────┐    ┌──────────────────┐    ┌────────────┐  │
│  │  SystemTray │    │  Session Manager │    │ Settings   │  │
│  │  Component  │◄──►│  (State + Cache) │◄──►│ Window     │  │
│  └─────────────┘    └──────────────────┘    └────────────┘  │
│         │                    │                              │
│         │           ┌────────▼────────┐                    │
│         │           │   SDK Bridge    │                    │
│         │           │ CopilotClient   │                    │
│         │           └────────┬────────┘                    │
└─────────┼────────────────────┼────────────────────────────-┘
          │                    │ IPC (stdin/stdout/JSON)
          │            ┌───────▼──────┐
          │            │  Copilot CLI │
          │            │  (Process)   │
          │            └──────────────┘
          │
     User actions
```

### Components

#### 1. SystemTray Component
- Manages the system tray icon and dynamic tooltip
- Builds and rebuilds the tray context menu on demand
- Supports tray icon states: idle (no sessions), active (sessions running), warning (errors/limits)
- Listens to session state changes and refreshes menu accordingly

#### 2. Session Manager
- Polls or subscribes to Copilot SDK session events
- Maintains an in-memory list of:
  - Active sessions (currently running Copilot CLI processes)
  - Archived/completed sessions
  - Background tasks/subagents (from `/tasks`)
- Broadcasts state change events to UI components

#### 3. SDK Bridge
- Wraps `CopilotClient` from the SDK
- Manages lifecycle: start, connect, disconnect, restart
- Subscribes to SDK events:
  - `SessionCreatedEvent`
  - `SessionClosedEvent`
  - `AssistantMessageEvent`
  - `SessionUsageInfoEvent`
  - `PermissionRequestEvent`
  - Any agent/subagent events
- Translates SDK events into `SessionSnapshot` data objects consumed by the UI

#### 4. Settings Window
- A separate JavaFX/Swing window opened on demand
- Pages: Sessions, Usage, Models, Preferences, About

---

## Data Model

### SessionSnapshot
```java
record SessionSnapshot(
    String id,
    String name,
    SessionStatus status,
    String model,
    Instant createdAt,
    Instant lastActivityAt,
    String workingDirectory,
    UsageSnapshot usage,
    List<SubagentSnapshot> subagents
)
```

### SessionStatus (enum)
- `ACTIVE` — session is running
- `IDLE` — session is open but no current operation
- `BUSY` — session is processing a request
- `ARCHIVED` — session is complete or was closed
- `ERROR` — session encountered an error

### UsageSnapshot
```java
record UsageSnapshot(
    int currentTokens,
    int tokenLimit,
    double tokenUsagePercent,
    int messagesCount,
    int premiumRequestsUsed,
    int premiumRequestsLimit
)
```

### SubagentSnapshot
```java
record SubagentSnapshot(
    String id,
    String description,
    SubagentStatus status,
    Instant startedAt
)
```

---

## System Tray Menu Structure

The tray menu is rebuilt dynamically whenever session state changes.

```
┌─────────────────────────────────────────────┐
│  🤖 GitHub Copilot Agentic Tray                        │
│  ─────────────────────────────────────────  │
│  ▸ Active Sessions (N)                      │
│    ├─ 📝 my-feature [claude-sonnet-4.6]     │
│    │    ├─ Status: BUSY (42% context)       │
│    │    ├─ Resume in Terminal               │
│    │    ├─ Cancel Session                   │
│    │    └─ Delete Session                   │
│    └─ 📝 fix-bug [gpt-5.2]                 │
│         ├─ Status: IDLE (18% context)       │
│         ├─ Resume in Terminal               │
│         ├─ Cancel Session                   │
│         └─ Delete Session                   │
│  ─────────────────────────────────────────  │
│  ▸ Archived Sessions (M)                    │
│    ├─ 🗃 old-session-1                      │
│    │    └─ Delete                           │
│    └─ 🗃 old-session-2                      │
│         └─ Delete                           │
│  ─────────────────────────────────────────  │
│  ▸ Usage Summary                            │
│    ├─ Tokens: 12,345 / 100,000 (12%)        │
│    └─ Premium Requests: 47 used this month  │
│  ─────────────────────────────────────────  │
│  ⚙  Open Settings...                        │
│  🔄 New Session                             │
│  ─────────────────────────────────────────  │
│  ✕  Quit                                    │
└─────────────────────────────────────────────┘
```

### Tray Icon States

| State     | Icon Description                                       |
|-----------|--------------------------------------------------------|
| Idle      | Greyscale Copilot/robot icon                           |
| Active    | Full-color icon (at least one active session)          |
| Busy      | Animated or pulsing icon (at least one busy session)   |
| Warning   | Icon with warning badge (context >80% full, or error)  |
| Error     | Icon with red badge (SDK connection failed, CLI missing)|

---

## Settings Window

Accessible from the tray menu or taskbar. Contains tabbed views:

### Tab: Sessions
- Full list of all sessions (active + archived)
- Per-session details:
  - Session ID, name, model, working directory
  - Status, duration, last activity
  - Full context window visualization (token usage bar)
  - Message history count
  - Subagents (if any): list with status and description
- Actions: Resume, Cancel, Delete, View Log

### Tab: Usage
- Total tokens used across all sessions
- Per-session token breakdown (bar chart or table)
- Premium requests used this billing period (if accessible)
- Context window utilization per session
- Historical usage chart (if persisted locally)

### Tab: Models
- List of known/used models with their display names
- Which sessions are using each model
- Link to Copilot model documentation

### Tab: Preferences
- **CLI Path**: path to `copilot` binary (auto-detected by default)
- **Auto-Start**: launch tray app at system login
- **Poll Interval**: how often to refresh session state (default: 5s)
- **Notifications**: enable/disable OS notifications on session events
- **Context Warning Threshold**: % at which to show context warning (default: 80%)
- **Theme**: system default / light / dark
- **Log Level**: INFO / DEBUG

### Tab: About
- App version, license, GitHub link
- SDK version in use
- Copilot CLI version detected

---

## Session Actions

### Resume Session
- Focuses or opens a terminal and runs `copilot /resume <sessionId>`
- On macOS: opens Terminal.app or the user's preferred terminal
- On Linux: opens the default terminal emulator
- On Windows: opens Windows Terminal or PowerShell

### Cancel Session
- Calls the SDK to send a cancel signal to the session
- Prompts for confirmation if session is BUSY

### Delete Session
- Removes the session from the SDK / Copilot CLI's session list
- Prompts for confirmation
- If session is active, prompts to cancel first

### New Session
- Opens a new terminal window and launches `copilot` in the user's preferred directory

---

## Events & Telemetry

The SDK bridge subscribes to SDK events via `CopilotSession.on(EventClass, handler)` and updates `SessionSnapshot` accordingly. The full event taxonomy from `com.github.copilot.sdk.events`:

### Session Lifecycle Events

| Event Class                    | Data / Action                                               |
|-------------------------------|-------------------------------------------------------------|
| `SessionStartEvent`           | Session started — capture session id, model, workspace      |
| `SessionShutdownEvent`        | Session closed — update status to ARCHIVED                  |
| `SessionIdleEvent`            | Session idle — update status to IDLE                        |
| `SessionErrorEvent`           | Error occurred — update status to ERROR, notify user        |
| `SessionInfoEvent`            | General session info update                                 |
| `SessionResumeEvent`          | Session resumed from archived state                         |
| `SessionModeChangedEvent`     | Mode changed (interactive → plan, etc.)                     |
| `SessionModelChangeEvent`     | Model changed mid-session — update displayed model          |
| `SessionContextChangedEvent`  | Context/workspace changed                                   |
| `SessionHandoffEvent`         | Session handed off (e.g. `/delegate`)                       |

### Usage & Context Events

| Event Class                    | Data / Action                                               |
|-------------------------------|-------------------------------------------------------------|
| `SessionUsageInfoEvent`       | `currentTokens`, `tokenLimit`, `messagesLength`             |
| `AssistantUsageEvent`         | Per-turn token usage details                                |
| `SessionTruncationEvent`      | Context was truncated/compacted                             |
| `SessionCompactionStartEvent` | Compaction (`/compact`) started                             |
| `SessionCompactionCompleteEvent` | Compaction complete — usage metrics refresh              |

### Message & Tool Events

| Event Class                    | Data / Action                                               |
|-------------------------------|-------------------------------------------------------------|
| `AssistantMessageEvent`        | Full assistant response (for tooltip/notification)         |
| `AssistantMessageDeltaEvent`   | Streaming token delta                                       |
| `AssistantReasoningEvent`      | Reasoning/thinking content                                  |
| `AssistantTurnStartEvent`      | Assistant started a turn — set status to BUSY               |
| `AssistantTurnEndEvent`        | Assistant finished a turn — set status to IDLE              |
| `UserMessageEvent`             | User message submitted                                      |
| `ToolExecutionStartEvent`      | Tool/shell command started                                  |
| `ToolExecutionCompleteEvent`   | Tool/shell command completed                                |
| `PermissionRequestedEvent`     | Waiting for user permission — show badge/notification       |
| `PermissionCompletedEvent`     | Permission granted or denied                                |
| `ExternalToolRequestedEvent`   | External tool was requested                                 |
| `ExternalToolCompletedEvent`   | External tool completed                                     |
| `SkillInvokedEvent`            | A skill was invoked                                         |

### Subagent Events

| Event Class                | Data / Action                                               |
|---------------------------|-------------------------------------------------------------|
| `SubagentStartedEvent`    | Subagent started — add to session's subagent list           |
| `SubagentCompletedEvent`  | Subagent finished successfully                              |
| `SubagentFailedEvent`     | Subagent failed — notify user                               |
| `SubagentSelectedEvent`   | An agent was selected for the session                       |
| `SubagentDeselectedEvent` | Agent was deselected                                        |

### Plan Mode Events

| Event Class                    | Data / Action                    |
|-------------------------------|----------------------------------|
| `SessionPlanChangedEvent`      | Plan content changed             |
| `ExitPlanModeRequestedEvent`   | User requested to exit plan mode |
| `ExitPlanModeCompletedEvent`   | Plan mode exited                 |

### Usage Data from SDK

The `SessionUsageInfoEvent.getData()` provides a `SessionUsageInfoData` record with:
- `currentTokens()` — tokens currently in context (double)
- `tokenLimit()` — maximum context window size (double)
- `messagesLength()` — number of messages in session (double)

Token usage percentage is computed as: `(currentTokens / tokenLimit) * 100`

### CopilotClient API Used by the SDK Bridge

```java
// Connection
client.start()                          // Connect to running Copilot CLI
client.stop()                           // Graceful disconnect
client.getState()                       // ConnectionState enum
client.getStatus()                      // GetStatusResponse
client.getAuthStatus()                  // GetAuthStatusResponse
client.ping(message)                    // Health check

// Sessions
client.listSessions()                   // List<SessionMetadata>
client.listSessions(SessionListFilter)  // Filtered list
client.createSession(SessionConfig)     // CopilotSession
client.resumeSession(id, config)        // CopilotSession
client.deleteSession(id)                // void
client.getLastSessionId()               // String
client.getForegroundSessionId()         // String
client.setForegroundSessionId(id)       // void

// Models
client.listModels()                     // List<ModelInfo>

// Lifecycle hooks
client.onLifecycle(handler)             // SessionLifecycleHandler
```

### CopilotSession API Used by the SDK Bridge

```java
session.getSessionId()                  // String
session.getWorkspacePath()              // String
session.on(EventClass, handler)         // Subscribe to typed events
session.sendAndWait(MessageOptions)     // Send prompt, wait for response
session.abort()                         // Cancel current operation
session.setModel(model)                 // Change model mid-session
session.listAgents()                    // List<AgentInfo>
session.getCurrentAgent()               // AgentInfo
session.compact()                       // Trigger /compact
session.getMessages()                   // Full message history
session.close()                         // Close session
```

---

## Notifications (OS-Level)

Sent via Java `java.awt.Toolkit` / system notification APIs:

| SDK Event                          | Notification                                     |
|------------------------------------|--------------------------------------------------|
| `SessionStartEvent`                | "📝 Session 'name' started (model)"              |
| `SessionShutdownEvent`             | "✅ Session 'name' completed"                    |
| `SessionUsageInfoEvent` (>80%)     | "⚠️ Session 'name' context window is 83% full"  |
| `SessionErrorEvent`                | "❌ Session 'name' encountered an error"         |
| `PermissionRequestedEvent`         | "🔐 Session 'name' is waiting for permission"   |
| `SubagentFailedEvent`              | "❌ Subagent failed in session 'name'"           |
| `SessionCompactionCompleteEvent`   | "🗜 Session 'name' context was compacted"        |

Notifications can be disabled in Preferences.

---

## Configuration & Persistence

Configuration is stored in a platform-standard location:

| OS      | Path                                               |
|---------|----------------------------------------------------|
| macOS   | `~/Library/Application Support/copilot-agentic-tray/` |
| Linux   | `~/.config/copilot-agentic-tray/`                      |
| Windows | `%APPDATA%\copilot-agentic-tray\`                      |

Files:
- `config.json` — user preferences
- `sessions-cache.json` — cached session list for fast startup
- `usage-history.json` — historical token/request usage (last 30 days)

---

## Build & Distribution

### Fat JAR (Phase 1)
- Single executable JAR with all dependencies bundled
- Requires JRE 21+ installed on the machine
- Launched via `java -jar copilot-agentic-tray.jar`

### Native Binary (Phase 2, GraalVM)
- Compile with GraalVM Native Image for target platform/arch
- Produces a standalone binary with no JVM required
- Build matrix via GitHub Actions:

| Target        | Runner / Cross-compile    | Output Binary                          |
|---------------|---------------------------|----------------------------------------|
| Linux x86_64  | `ubuntu-latest`           | `copilot-agentic-tray-linux-x86_64`        |
| Linux arm64   | `ubuntu-24.04-arm`        | `copilot-agentic-tray-linux-arm64`         |
| macOS x86_64  | `macos-13`                | `copilot-agentic-tray-macos-x86_64`        |
| macOS arm64   | `macos-latest`            | `copilot-agentic-tray-macos-arm64`         |
| Windows x86_64| `windows-latest`          | `copilot-agentic-tray-windows-x86_64.exe`  |

### Auto-Update
- On startup, checks GitHub Releases for a newer version
- Notifies user via tray notification if an update is available
- Provides a direct link to download the latest release

---

## Security Considerations

- The app uses the user's existing `gh` / `GH_TOKEN` credentials already configured for the Copilot CLI
- No credentials are stored by the tray app itself
- The SDK communicates with the CLI process via local IPC; no outbound network calls are made by the tray app
- The Settings window does not expose or display tokens/credentials
- The app runs with the least necessary privileges (no root required)

---

## Accessibility

- All tray menu items have descriptive labels and keyboard shortcuts
- The Settings window is fully navigable via keyboard
- Icon states are distinguishable by both color and shape (for colorblind users)
- High-DPI / Retina display support via multi-resolution icons

---

## Implementation Phases

### Phase 1 — Core Tray + Active Sessions
- Project scaffold (Maven, Java 21)
- Dorkbox SystemTray integration
- SDK Bridge: connect to running Copilot CLI, receive session events
- Display active sessions in tray menu with basic status
- Resume / Cancel session actions
- Fat JAR distribution

### Phase 2 — Usage Telemetry + Settings Window
- `SessionUsageInfoEvent` integration (tokens, context window)
- Usage tab in Settings window (JavaFX)
- Context warning notifications
- Preferences tab (CLI path, poll interval, thresholds)
- Archived sessions list

### Phase 3 — Models + Advanced Features
- Model tracking per session
- Subagent visibility (from `/tasks` equivalent in SDK)
- OS notifications for all session lifecycle events
- Historical usage persistence and chart
- Auto-start on login

### Phase 4 — Native Binaries
- GraalVM Native Image compilation
- GitHub Actions cross-platform build matrix
- Release pipeline with native binaries per platform/arch
- Auto-update check

---

## Open Questions / Future Considerations

- **Premium requests**: The SDK's `SessionUsageInfoEvent` exposes token data; premium request quota may require a separate GitHub API call (`/user/copilot_billing`) — to be investigated
- **Multiple CLI processes**: If the user runs multiple `copilot` CLI instances, the SDK bridge may need to track multiple `CopilotClient` connections
- **Fleet mode**: Copilot CLI's `/fleet` command spawns parallel subagents; these should appear as child items under their parent session
- **Dark mode icon**: Provide both light and dark tray icons, and auto-select based on OS theme
- **Localization**: English-only for initial releases; i18n infrastructure to be added later
