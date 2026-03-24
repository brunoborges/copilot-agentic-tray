package com.github.copilot.tray.session;

import java.time.Instant;
import java.util.List;

/**
 * Immutable snapshot of a Copilot CLI session's current state.
 * Created by SessionManager on each state change.
 */
public record SessionSnapshot(
        String id,
        String name,
        SessionStatus status,
        String model,
        Instant createdAt,
        Instant lastActivityAt,
        String workingDirectory,
        UsageSnapshot usage,
        List<SubagentSnapshot> subagents,
        boolean pendingPermission,
        boolean remote
) {
    /**
     * Creates a new snapshot for a freshly discovered/started session.
     */
    public static SessionSnapshot initial(String id, String model, String workingDirectory) {
        var now = Instant.now();
        return new SessionSnapshot(
                id,
                id, // name defaults to id
                SessionStatus.ACTIVE,
                model != null ? model : "unknown",
                now,
                now,
                workingDirectory != null ? workingDirectory : "",
                UsageSnapshot.EMPTY,
                List.of(),
                false,
                false
        );
    }

    public SessionSnapshot withStatus(SessionStatus newStatus) {
        return new SessionSnapshot(id, name, newStatus, model, createdAt,
                Instant.now(), workingDirectory, usage, subagents, pendingPermission, remote);
    }

    public SessionSnapshot withUsage(UsageSnapshot newUsage) {
        return new SessionSnapshot(id, name, status, model, createdAt,
                Instant.now(), workingDirectory, newUsage, subagents, pendingPermission, remote);
    }

    public SessionSnapshot withModel(String newModel) {
        return new SessionSnapshot(id, name, status, newModel, createdAt,
                Instant.now(), workingDirectory, usage, subagents, pendingPermission, remote);
    }

    public SessionSnapshot withSubagents(List<SubagentSnapshot> newSubagents) {
        return new SessionSnapshot(id, name, status, model, createdAt,
                Instant.now(), workingDirectory, usage, newSubagents, pendingPermission, remote);
    }

    public SessionSnapshot withPendingPermission(boolean pending) {
        return new SessionSnapshot(id, name, status, model, createdAt,
                Instant.now(), workingDirectory, usage, subagents, pending, remote);
    }

    public SessionSnapshot withName(String newName) {
        return new SessionSnapshot(id, newName, status, model, createdAt,
                lastActivityAt, workingDirectory, usage, subagents, pendingPermission, remote);
    }

    public SessionSnapshot withLastActivity(Instant time) {
        return new SessionSnapshot(id, name, status, model, createdAt,
                time, workingDirectory, usage, subagents, pendingPermission, remote);
    }

    public SessionSnapshot withRemote(boolean isRemote) {
        return new SessionSnapshot(id, name, status, model, createdAt,
                lastActivityAt, workingDirectory, usage, subagents, pendingPermission, isRemote);
    }
}
