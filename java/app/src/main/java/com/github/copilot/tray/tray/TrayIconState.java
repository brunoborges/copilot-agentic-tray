package com.github.copilot.tray.tray;

/**
 * Represents the visual state of the system tray icon.
 */
public enum TrayIconState {
    /** No active sessions */
    IDLE("tray-idle.png", "GitHub Copilot Agentic Tray — No active sessions"),
    /** At least one session is active */
    ACTIVE("tray-active.png", "GitHub Copilot Agentic Tray — Sessions active"),
    /** At least one session is busy (processing a request) */
    BUSY("tray-busy.png", "GitHub Copilot Agentic Tray — Processing..."),
    /** Warning condition (context >80% full, or error) */
    WARNING("tray-warning.png", "GitHub Copilot Agentic Tray — Attention needed");

    private final String iconFilename;
    private final String tooltip;

    TrayIconState(String iconFilename, String tooltip) {
        this.iconFilename = iconFilename;
        this.tooltip = tooltip;
    }

    public String getIconFilename() { return iconFilename; }
    public String getTooltip() { return tooltip; }
}
