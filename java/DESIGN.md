# UI Design System

This is the **authoritative reference** for all UI decisions. Every new window, panel, card, or component must follow these conventions.

---

## 1. Design Tokens

### Spacing Scale

All spacing values must come from this scale:

| Token | Value | Usage |
|---|---|---|
| `xs` | `2px` | Table cell internal padding |
| `sm` | `4px` | Tight spacing (icon gaps, badge padding) |
| `md` | `8px` | Standard gap between sibling elements (VBox/HBox spacing) |
| `lg` | `12px` | Gap between cards, grid column gap |
| `xl` | `20px` | Page-level content padding (all pages) |

**Gap** = space between children (`-fx-spacing` / VBox/HBox constructor arg).
**Padding** = space between container edge and children (`-fx-padding` / `setPadding()`).

### Font Sizes

| Size | Usage |
|---|---|
| `9px` | Timestamps, monospace metadata |
| `10px` | Legend labels, secondary text |
| `11px` | Table cells, tree items, detail keys |
| `12px` | Default body text, value labels |
| `13px` | Card titles, section headers |
| `14px` | Navigation labels, action buttons |
| `20px` | Hero text (About page app name) |

### Border Radius

| Radius | Usage |
|---|---|
| `3px` | Buttons, input fields |
| `4px` | Badges, small controls |
| `8px` | Cards (`.about-card`, `.sessions-card`) |
| `6px` | Scrollbar thumbs |

### Color Palette

#### Dark Theme

| Role | Color |
|---|---|
| Page background | `#1e1e2e` |
| Sidebar background | `#141424` |
| Card background | `#252538` |
| Card wrapper background | `#1e1e2e` |
| Card border (detail) | `#3c3c52` |
| Card border (wrapper) | `#2a2a3e` |
| Card title text | `#b0b0c8` |
| Key/label text | `#8888a0` |
| Value text | `#d0d0e0` |
| Primary accent | `#0078d4` |
| Hover accent | `#1a8ae8` |
| Badge background | `#3a5a8c` |
| Badge text | `#d0e8ff` |
| Table row hover | `#2a2d3e` |
| Table row selected | `#04395e` |
| Table grid lines | `#2a2a3e` |
| Danger/delete | `#f14c4c` |
| Warning | `#e8a855` |
| Success | `#4ec94e` |
| Search highlight | `#f9e64f` |

#### Light Theme

| Role | Color |
|---|---|
| Page background | `#f3f3f3` |
| Sidebar background | `#e8e8e8` |
| Card background | `#ffffff` |
| Card border (detail) | `#e0e0e0` |
| Card border (wrapper) | `#d0d0d8` |
| Card title text | `#333` |
| Key/label text | `#777` |
| Value text | `#1a1a1a` |
| Primary accent | `#0078d4` |
| Badge background | `#cce5ff` |
| Badge text | `#0055aa` |
| Table row hover | `#f0f0f5` |
| Table row selected | `#cce5ff` |
| Table grid lines | `#e8e8f0` |
| Danger/delete | `#d32f2f` |

---

## 2. CSS Class Reference

### Layout Classes

| Class | Properties | Where to use |
|---|---|---|
| `.content-padding` | `padding: 20px` | Top-level content container of every page |
| `.sessions-section` | `padding: 20px` | `rightBox` in Sessions split pane |
| `.action-bar` | Alignment, spacing | Bottom action bar in Sessions |
| `.left-panel` | Background, padding | Left sidebar within Sessions |
| `.activity-bar` | Fixed 72px, background, border | Main navigation sidebar |

### Card Classes

| Class | Properties | Where to use |
|---|---|---|
| `.about-card` | Background, border, 8px radius, 14px padding (Java) | Detail cards, settings sections, about sections, prune cards |
| `.sessions-card` | Background, border, 8px radius, 8px padding, shadow | Wrapper for tables, tiles pane, action bar |
| `.about-card-title` | Bold, 13px, themed color | Card header labels |

### Table Classes

| Class | Properties | Where to use |
|---|---|---|
| `.sessions-card-table` | Transparent bg, themed grid lines | Main sessions table |
| `.prune-card-table` | Transparent bg, themed grid lines | Prune category tables |
| `.no-header` | Hidden column header | Tables that don't need headers |

### Detail / Content Classes

| Class | Properties | Where to use |
|---|---|---|
| `.sessions-detail-pane` | `padding: 0`, `spacing: 8` | Sessions detail pane (cards provide own padding) |
| `.usage-tiles-pane` | Background color | Tiles pane wrapper |
| `.about-key` | Muted color, right-aligned | Key labels in GridPane rows |
| `.about-value` | Bright color | Value labels in GridPane rows |

### Navigation Classes

| Class | Properties | Where to use |
|---|---|---|
| `.nav-button` | Full-width, icon+label, toggle | Activity bar buttons |
| `.nav-icon` | 18px, centered | Navigation button icons |
| `.nav-label` | Themed text | Navigation button text |

> **Rule**: Always define spacing via CSS classes. Never use inline `setPadding()` for layout-level spacing. Internal component padding (e.g., `aboutCard` body padding) set in Java helpers is acceptable.

---

## 3. Component Patterns

### Cards

The **card** is the fundamental UI primitive. Everything is a card.

```java
// Standard card with title + GridPane body
var grid = aboutGrid();
aboutRow(grid, 0, "Key", "Value");
aboutRow(grid, 1, "Key", dynamicLabel);
var card = aboutCard("Section Title", grid);
```

Java helpers in `SettingsWindow`:
- `aboutCard(String title, Node body)` — VBox with `.about-card`, 14px padding, 8px gap
- `aboutGrid()` — GridPane with 12px hgap, 10px vgap
- `aboutRow(grid, row, key, value)` — key-value row
- `aboutValueLabel(text)` — styled value Label

### Tables

```
┌─────────────────────────────────────────────┐
│  Col1    Col2    Col3    Col4         ⋮     │  ← header (28px)
├─────────────────────────────────────────────┤
│  val     val     val     val         [⋮]   │  ← data rows
└─────────────────────────────────────────────┘
```

- Resize policy: `CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS`
- Row height: `28px` via `-fx-cell-size`
- Horizontal scrollbar: hidden (`max-height: 0`)
- Multi-select: `SelectionMode.MULTIPLE`
- Action column: fixed `50px`, `MenuButton("⋮")` 28×28px

### Tiles (TilesFX)

```java
TileBuilder.create()
    .skinType(SkinType.PERCENTAGE)  // or NUMBER, GAUGE
    .prefSize(TILE_W, TILE_H)
    .animated(false)
    .textSize(Tile.TextSize.SMALLER)
    .build();
```

- Never use constructors — always `TileBuilder.create()`
- Always `.animated(false)` and `.textSize(Tile.TextSize.SMALLER)`
- Update via mutation (`tile.setValue(...)`, `chartData.setValue(...)`)
- Colors as class-level `Color.web("#hex")` constants

### Buttons

| Style | Class / Pattern | Usage |
|---|---|---|
| Primary action | Default button styling | New Session, Resume, Save |
| Danger | `.delete-btn` | Delete buttons |
| Small utility | `.prune-small-btn` | ⋮ menu buttons |
| Hyperlink | `.prune-card-link` | Select all/none |

### Scrollbars

Use child combinator selectors for virtualized controls:
```css
.my-list > .virtual-flow > .scroll-bar:vertical { ... }
.my-table > .virtual-flow > .scroll-bar:horizontal { max-height: 0; }
```
- Thumb: rounded (radius 6), themed color, 2px inset
- Track: subtle background
- Arrow buttons: hidden (`pref-height: 0`)
- Width: `12px`

---

## 4. Page Layouts

### Sessions Page

```
SplitPane (20% | 80%)
├─ leftBox .left-panel
│  ├─ toggleBar ── [Local] [Remote]
│  └─ directoryList (TreeView)
│
└─ rightBox .sessions-section [pad: 20]
   ├─ topPane .sessions-card [pad: 8, clipped]       ↕ grows
   │  ├─ aggregateRow
   │  └─ sessionTable .sessions-card-table
   ├─ bottomPane (HBox, gap=8)                        fixed 375px
   │  ├─ detailPane .sessions-detail-pane [pad: 0, gap: 8]
   │  └─ usageTilesPane .sessions-card [pad: 8]       fixed 480px
   └─ actionBar .action-bar .sessions-card [pad: 8]
```

Remote mode: `detailPane` full-width (no tiles), no aggregate row.

### Prune Page

```
VBox .content-padding [pad: 20]
├─ Scan controls
├─ Category cards (VBox, gap=10)
│  └─ each: .about-card [pad: 12 6 6 12]
│     ├─ Header (title + select links)
│     └─ TableView .prune-card-table .no-header
└─ Action bar
```

### Settings Page

```
ScrollPane
└─ VBox(12) .content-padding [pad: 20]
   ├─ Appearance card (.about-card)
   ├─ GitHub Tools card (.about-card)
   ├─ Monitoring card (.about-card)
   ├─ Behavior card (.about-card)
   └─ Save button
```

### About Page

```
ScrollPane
└─ VBox(12) .content-padding [pad: 20]
   ├─ Hero box (name + badge + description)
   ├─ Build Information (.about-card)
   ├─ Runtime Environment (.about-card)
   ├─ Copilot CLI Status (.about-card)
   └─ Links (.about-card)
```

### Viewer Windows

All viewer windows follow this pattern:

```
VBox .content-padding [pad: 20]
├─ Header label (.events-viewer-header)
├─ Stats label (.events-viewer-stats)
└─ Content area (grows)                    ↕ Priority.ALWAYS
```

| Window | Content | Size |
|---|---|---|
| Events Viewer | `ListView` + search toolbar | 800×600 |
| Checkpoint Viewer | `HBox(listCard, contentCard)` | 900×600 |
| Event Log | `TextArea` (live stream) | 700×500 |
| Prune Category Info | `ScrollPane(VBox of cards)` | 480×520 |

---

## 5. Window Management

### Keyboard Shortcuts

Every window must close with **Cmd+W** / **Ctrl+W**:

```java
scene.getAccelerators().put(
    new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN),
    () -> stage.close());
```

Alt+F4 is handled natively by JavaFX.

### Singleton Pattern

Never open duplicate windows for the same entity:

```java
private static final Map<String, MyViewer> OPEN_VIEWERS = new ConcurrentHashMap<>();

public static void showViewer(String key, ...) {
    var existing = OPEN_VIEWERS.get(key);
    if (existing != null) { existing.stage.show(); existing.stage.toFront(); return; }
    var viewer = new MyViewer(key, ...);
    OPEN_VIEWERS.put(key, viewer);
    viewer.stage.show(); viewer.stage.toFront();
}
// In constructor: stage.setOnHidden(e -> OPEN_VIEWERS.remove(key));
```

| Window | Singleton Key |
|---|---|
| `SettingsWindow` | Single field |
| `SessionEventsViewer` | `sessionId` |
| `SessionCheckpointViewer` | `sessionId` |
| `SessionEventLogWindow` | Per-instance |
| `PrunePanel` popup | `categoryStage` field |

---

## 6. Theming

`ThemeManager` resolves `"dark"` / `"light"` / `"system"`:
- `dark` → `/css/dashboard-dark.css`
- `light` → `/css/dashboard-light.css`
- `system` → OS detection (macOS: `AppleInterfaceStyle`, Windows: registry)

Every new `Scene` must be registered: `themeManager.register(scene);`
Dialogs: `themeManager.register(dialog.getDialogPane().getScene());`

---

## 7. Cross-View Synchronization

- **SessionManager** is the single source of truth. All mutations trigger `notifyListeners()`.
- Deleting from any view → call `sessionManager.removeSession()`.
- **PrunePanel** resets `hasScanned` on changes → auto-rescans on next visit.
- **SettingsWindow** refreshes table via `onSessionChange()`.

---

## 8. Checklist for New UI

When adding a new panel, window, or card:

- [ ] Use `.content-padding` (20px) on the top-level content container
- [ ] Wrap content sections in `.about-card` or `.sessions-card`
- [ ] Use `aboutCard()`, `aboutGrid()`, `aboutRow()` for key-value displays
- [ ] Use colors from the palette — never invent new colors
- [ ] Use spacing values from the token scale only
- [ ] Register the scene with `ThemeManager`
- [ ] Add **Cmd+W** close accelerator
- [ ] Implement singleton pattern if the window is session-specific
- [ ] Use `Platform.runLater()` for all UI updates from background threads
- [ ] Load data on virtual threads with spinner placeholder
