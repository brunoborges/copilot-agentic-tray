package com.github.copilot.tray.session;

import java.time.Instant;

/**
 * Immutable snapshot of token/context usage for a session.
 */
public record UsageSnapshot(
        int currentTokens,
        int tokenLimit,
        int messagesCount
) {
    /**
     * Token usage as a percentage (0–100).
     */
    public double tokenUsagePercent() {
        if (tokenLimit <= 0) return 0.0;
        return (currentTokens * 100.0) / tokenLimit;
    }

    /**
     * An empty usage snapshot used as default.
     */
    public static final UsageSnapshot EMPTY = new UsageSnapshot(0, 0, 0);
}
