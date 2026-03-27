# UI Design System

## Card-Based Layout

The application uses a **card-based layout** for all content sections. Cards provide visual grouping, hierarchy, and consistent spacing across all views.

### Card Structure

```
┌─────────────────────────────────┐
│  Card Title (bold, 13px)        │
│                                 │
│  Key        Value               │
│  Key        Value               │
│  Key        Value               │
└─────────────────────────────────┘
```

- **Rounded corners**: `8px` border radius on both background and border
- **Padding**: `16px` inside content panes and cards
- **Spacing**: `12px` gap between cards, `8px` between title and body, `8px` between major sections
- **Border**: subtle contrast border (`#3c3c52` dark / `#e0e0e0` light)
- **Background**: slightly lighter than page background (`#252538` dark / `#ffffff` light)
- **Drop shadow**: `dropshadow(gaussian, rgba(0,0,0,0.3), 6, 0, 0, 2)` dark / `rgba(0,0,0,0.08)` light

### Reusable CSS Classes

| Class | Purpose | Where |
|---|---|---|
| `.about-card` | Detail card container (background, border, radius) | Detail panes, About page |
| `.sessions-card` | Table/pane wrapper card (rounded, shadow, border) | Sessions table, tiles pane |
| `.prune-card-table` | Embedded table inside prune category cards | Prune cards |
| `.sessions-card-table` | Main sessions table (transparent bg, grid lines) | Sessions page |

### Standard Spacing Rules

All panes and containers follow these spacing rules via CSS or inline padding:

| Context | Padding | Example |
|---|---|---|
| Content pane (detail, tiles) | `16px` all sides | `detailPane`, `UsageTilesPane` |
| Card wrapper around tables | `2px` internal | `tableCard` (sessions table) |
| Outer section margin | `12px` horizontal | `topPane` in Sessions |
| Section spacing (vertical) | `8px` gap | Between aggregate row and table card |
| Card-to-card spacing | `8px` gap | Between detail pane and tiles pane |
| Prune panel padding | `12px` all sides | PrunePanel root |

> **Convention**: Always define spacing via CSS classes, not inline `setPadding()`/`setSpacing()`. When adding a new pane or section, apply an existing CSS class (e.g. `.sessions-card`, `.about-card`) rather than setting padding in Java code.

### Java Helper Methods

Cards are built via reusable helpers in `SettingsWindow`:

```java
// Create a card with a title and body node
VBox aboutCard(String title, Node body)

// Create a GridPane for key-value rows
GridPane aboutGrid()

// Add a key-value row (string value)
void aboutRow(GridPane grid, int row, String key, String value)

// Add a key-value row (Label value — for dynamic updates)
void aboutRow(GridPane grid, int row, String key, Label valueLabel)

// Create a styled value label
Label aboutValueLabel(String text)
```

### Color Palette

#### Dark Theme
- Page background: `#1e1e2e`
- Card background: `#252538`
- Card border: `#3c3c52` (detail cards) / `#2a2a3e` (table cards)
- Card title: `#b0b0c8`
- Key text: `#8888a0`
- Value text: `#d0d0e0`
- Badge background: `#3a5a8c`
- Badge text: `#d0e8ff`
- Table row hover: `#2a2d3e`
- Table row selected: `#04395e`
- Table grid lines: `#2a2a3e`

#### Light Theme
- Page background: `#f3f3f3`
- Card background: `#ffffff`
- Card border: `#e0e0e0` (detail cards) / `#d0d0d8` (table cards)
- Card title: `#333`
- Key text: `#777`
- Value text: `#1a1a1a`
- Badge background: `#cce5ff`
- Badge text: `#0055aa`
- Table row hover: `#f0f0f5`
- Table row selected: `#cce5ff`
- Table grid lines: `#e8e8f0`

## Sidebar Navigation

VS Code-style activity bar on the left:
- Fixed width `72px`, SVG icons + text labels
- `ToggleGroup` for mutual exclusion
- Top group (Sessions, Prune) + bottom group (Settings, About) with spacer

## Table Styling

All tables follow these conventions:

### Layout
- Resize policy: `CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS`
- Row height: `28px` via `-fx-cell-size`
- Cell alignment: `CENTER-LEFT`
- Horizontal scrollbar: **always hidden** via `> .virtual-flow > .scroll-bar:horizontal` with `max-height: 0`

### Grid Lines
- Cell top border only: `1px` in theme grid-line color
- Column header right border (except last): `1px` divider
- Column header background: transparent

### Action Menu Column (⋮)
- Fixed width: `50px` (min/max/pref)
- `MenuButton` with `\u22EE` text, 28×28px, `prune-small-btn` style class
- Arrow button hidden via CSS (`.prune-small-btn .arrow-button { max-width: 0 }`)
- Standard menu items: Resume, View Events, Copy ID, Delete (with separator before Delete)

### Scrollbars (vertical)
- Use child combinator: `.my-table > .virtual-flow > .scroll-bar:vertical`
- Thumb: rounded (`background-radius: 6`), themed color, `2px` inset
- Track: subtle background matching card
- Arrow buttons: hidden (`pref-height: 0`)
- Width: `12px`

### Selection
- Text color: inline styles via `selectedProperty()` listener for reliable theme behavior
- Multi-select enabled via `SelectionMode.MULTIPLE`

## View Events Window

### Virtualized ListView
- Uses `ListView<ParsedEvent>` with custom `EventCell` for virtualization
- Cell caching: `cache(true)` + `CacheHint.SPEED`
- Async loading with spinner placeholder

### Search & Navigation
- Bottom toolbar with role navigation (◀/▶ User, ◀/▶ Assistant)
- Search field with match counter, ◀/▶ to cycle matches
- Search counts all occurrences per cell (not just one per matching event)
- Highlighted matches use `TextFlow` with `Label`-wrapped `Text` nodes (yellow background `#f9e64f`, bold black text)
- Falls back to plain `Label` when no search is active

## Tiles (TilesFX)

- Always use `TileBuilder.create()`, never constructors
- Set `.animated(false)` and `.textSize(Tile.TextSize.SMALLER)`
- Donut charts include legends below via `legendItem()` helper
- Fixed tile pane width (`480px`) with fixed min/max width on wrapper card
- Padding: `16px` (matching detail pane)

## Cross-View Sync

- **SessionManager** is the single source of truth. All mutations trigger `notifyListeners()`.
- When deleting from **any** view, call `sessionManager.removeSession()` so all listeners refresh.
- **PrunePanel** resets `hasScanned` on SessionManager changes → auto-rescans on next tab visit.
- **SettingsWindow** refreshes table via `onSessionChange()` callback.

## Window Management

All popup/viewer windows must follow these conventions:

### Keyboard Shortcuts

- **Cmd+W** (macOS) / **Ctrl+W** (Windows/Linux) must close every window. Use `SHORTCUT_DOWN` modifier:
  ```java
  scene.getAccelerators().put(
      new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN),
      () -> stage.close());
  ```
- **Alt+F4** is handled natively by JavaFX on all platforms — no code needed.

### Singleton Windows

Never open duplicate windows for the same session. Each viewer class maintains a `static Map<String, Viewer> OPEN_VIEWERS`:

- **Open**: check the map first → if found, `show()` + `toFront()` and return. Otherwise, create, register, and show.
- **Close**: register `stage.setOnHidden(e -> OPEN_VIEWERS.remove(key))` to clean up the map.
- Callers use a `static showViewer(...)` factory method instead of `new Viewer(...).show()`.

| Window | Singleton Key | Close shortcut |
|---|---|---|
| `SettingsWindow` | single instance (field `stage`) | Cmd+W hides |
| `SessionEventsViewer` | `sessionId` | Cmd+W closes |
| `SessionCheckpointViewer` | `sessionId` | Cmd+W closes |
| `SessionEventLogWindow` | per-instance (attach-specific) | Cmd+W closes |
| `PrunePanel` category popup | `categoryStage` field | Cmd+W closes |
