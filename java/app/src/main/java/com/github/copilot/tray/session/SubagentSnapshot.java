package com.github.copilot.tray.session;

import java.time.Instant;

/**
 * Immutable snapshot of a subagent running within a session.
 */
public record SubagentSnapshot(
        String id,
        String description,
        SubagentStatus status,
        Instant startedAt
) {
    public SubagentSnapshot withStatus(SubagentStatus newStatus) {
        return new SubagentSnapshot(id, description, newStatus, startedAt);
    }
}
