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
- **Padding**: `14px` inside each card
- **Spacing**: `12px` gap between cards, `8px` between title and body
- **Border**: subtle contrast border (`#3c3c52` dark / `#e0e0e0` light)
- **Background**: slightly lighter than page background (`#252538` dark / `#ffffff` light)

### CSS Classes

| Class               | Purpose                                    |
|---------------------|--------------------------------------------|
| `.about-card`       | Card container (background, border, radius) |
| `.about-card-title` | Card section title (bold, 13px)            |
| `.about-key`        | Left column label (dimmed)                 |
| `.about-value`      | Right column value (bright)                |
| `.about-app-name`   | Hero title (20px, bold)                    |
| `.about-version-badge` | Pill badge for version string           |
| `.about-description`| Subtitle/description text                  |

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
- Card border: `#3c3c52`
- Card title: `#b0b0c8`
- Key text: `#8888a0`
- Value text: `#d0d0e0`
- Badge background: `#3a5a8c`
- Badge text: `#d0e8ff`

#### Light Theme
- Page background: `#f3f3f3`
- Card background: `#ffffff`
- Card border: `#e0e0e0`
- Card title: `#333`
- Key text: `#777`
- Value text: `#1a1a1a`
- Badge background: `#cce5ff`
- Badge text: `#0055aa`

## Sidebar Navigation

VS Code-style activity bar on the left:
- Fixed width `72px`, SVG icons + text labels
- `ToggleGroup` for mutual exclusion
- Top group (Sessions, Prune) + bottom group (Settings, About) with spacer

## Table Styling

- Row height: `28px` via `-fx-cell-size`
- Cell alignment: `CENTER-LEFT` for vertical centering
- Scrollbars: visible track + opaque thumb (no semi-transparent)
- Text color: inline styles via `selectedProperty()` listener for reliable theme behavior

## Tiles (TilesFX)

- Always use `TileBuilder.create()`, never constructors
- Set `.animated(false)` and `.textSize(Tile.TextSize.SMALLER)`
- Donut charts include legends below via `legendItem()` helper
- Fixed tile pane width (`480px`) with `setResizableWithParent(false)`
