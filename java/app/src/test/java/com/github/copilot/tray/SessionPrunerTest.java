package com.github.copilot.tray;

import com.github.copilot.tray.session.SessionPruner;
import com.github.copilot.tray.session.SessionPruner.PruneCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SessionPrunerTest {

    @TempDir
    Path tempDir;

    private SessionPruner pruner;

    @BeforeEach
    void setUp() {
        pruner = new SessionPruner(tempDir);
    }

    @Test
    void emptyDirReturnsNoCandidates() {
        var candidates = pruner.scan();
        assertTrue(candidates.isEmpty());
    }

    @Test
    void sessionWithNoEventsIsEmpty() throws IOException {
        var sessionDir = tempDir.resolve("session-1");
        Files.createDirectory(sessionDir);
        Files.writeString(sessionDir.resolve("workspace.yaml"),
                "id: session-1\ncwd: /tmp\ncreated_at: 2026-01-01T00:00:00Z\nupdated_at: 2026-01-01T00:00:00Z\n");

        var candidates = pruner.scan();
        assertEquals(1, candidates.size());
        assertEquals(PruneCategory.EMPTY, candidates.getFirst().category());
        assertEquals("session-1", candidates.getFirst().sessionId());
    }

    @Test
    void sessionWithEventsButNoUserMessagesIsEmpty() throws IOException {
        var sessionDir = tempDir.resolve("session-2");
        Files.createDirectory(sessionDir);
        Files.writeString(sessionDir.resolve("events.jsonl"),
                """
                {"type":"session.start","id":"e1"}
                {"type":"session.model_change","id":"e2"}
                """);
        Files.writeString(sessionDir.resolve("workspace.yaml"),
                "id: session-2\ncwd: /tmp\ncreated_at: 2026-01-01T00:00:00Z\nupdated_at: 2026-01-01T00:00:00Z\n");

        var candidates = pruner.scan();
        assertEquals(1, candidates.size());
        assertEquals(PruneCategory.EMPTY, candidates.getFirst().category());
    }

    @Test
    void sessionWithUserMessageButNoAssistantIsAbandoned() throws IOException {
        var sessionDir = tempDir.resolve("session-3");
        Files.createDirectory(sessionDir);
        Files.writeString(sessionDir.resolve("events.jsonl"),
                """
                {"type":"session.start","id":"e1"}
                {"type":"user.message","data":{"content":"hello world"},"id":"e2"}
                """);
        Files.writeString(sessionDir.resolve("workspace.yaml"),
                "id: session-3\ncwd: /home/user\ncreated_at: 2026-01-15T12:00:00Z\nupdated_at: 2026-01-15T12:05:00Z\n");

        var candidates = pruner.scan();
        assertEquals(1, candidates.size());
        assertEquals(PruneCategory.ABANDONED, candidates.getFirst().category());
        assertEquals("hello world", candidates.getFirst().firstUserMessage());
        assertEquals("/home/user", candidates.getFirst().workingDirectory());
    }

    @Test
    void trivialSessionWithFewMessages() throws IOException {
        var sessionDir = tempDir.resolve("session-4");
        Files.createDirectory(sessionDir);
        Files.writeString(sessionDir.resolve("events.jsonl"),
                """
                {"type":"session.start","id":"e1"}
                {"type":"user.message","data":{"content":"fix the bug"},"id":"e2"}
                {"type":"assistant.message","data":{"content":"done"},"id":"e3"}
                {"type":"user.message","data":{"content":"thanks"},"id":"e4"}
                {"type":"assistant.message","data":{"content":"welcome"},"id":"e5"}
                """);
        Files.writeString(sessionDir.resolve("workspace.yaml"),
                "id: session-4\ncwd: /tmp\ncreated_at: 2026-02-01T00:00:00Z\nupdated_at: 2026-02-01T01:00:00Z\n");

        var candidates = pruner.scan(true);
        assertEquals(1, candidates.size());
        assertEquals(PruneCategory.TRIVIAL, candidates.getFirst().category());
        assertEquals(2, candidates.getFirst().userMessageCount());
        assertEquals(2, candidates.getFirst().assistantMessageCount());
    }

    @Test
    void trivialExcludedWhenFlagIsFalse() throws IOException {
        var sessionDir = tempDir.resolve("session-5");
        Files.createDirectory(sessionDir);
        Files.writeString(sessionDir.resolve("events.jsonl"),
                """
                {"type":"user.message","data":{"content":"hi"},"id":"e1"}
                {"type":"assistant.message","data":{"content":"hello"},"id":"e2"}
                """);
        Files.writeString(sessionDir.resolve("workspace.yaml"),
                "id: session-5\ncwd: /tmp\ncreated_at: 2026-01-01T00:00:00Z\nupdated_at: 2026-01-01T00:00:00Z\n");

        var candidates = pruner.scan(false);
        assertTrue(candidates.isEmpty());
    }

    @Test
    void longSessionIsNotPrunable() throws IOException {
        var sessionDir = tempDir.resolve("session-6");
        Files.createDirectory(sessionDir);
        var sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append("{\"type\":\"user.message\",\"data\":{\"content\":\"msg " + i + "\"},\"id\":\"u" + i + "\"}\n");
            sb.append("{\"type\":\"assistant.message\",\"data\":{\"content\":\"reply " + i + "\"},\"id\":\"a" + i + "\"}\n");
        }
        Files.writeString(sessionDir.resolve("events.jsonl"), sb.toString());
        Files.writeString(sessionDir.resolve("workspace.yaml"),
                "id: session-6\ncwd: /tmp\ncreated_at: 2026-01-01T00:00:00Z\nupdated_at: 2026-01-01T00:00:00Z\n");

        var candidates = pruner.scan(true);
        assertTrue(candidates.isEmpty());
    }

    @Test
    void deleteRemovesSessionFromDisk() throws IOException {
        var sessionDir = tempDir.resolve("session-del");
        Files.createDirectory(sessionDir);
        Files.writeString(sessionDir.resolve("workspace.yaml"),
                "id: session-del\ncwd: /tmp\ncreated_at: 2026-01-01T00:00:00Z\nupdated_at: 2026-01-01T00:00:00Z\n");
        Files.writeString(sessionDir.resolve("some-file.txt"), "data");

        var candidates = pruner.scan();
        assertEquals(1, candidates.size());

        var result = pruner.delete(candidates);
        assertEquals(1, result.deletedCount());
        assertTrue(result.totalBytesFreed() > 0);
        assertFalse(Files.exists(sessionDir));
    }

    @Test
    void mixedSessionsAreSortedByCategory() throws IOException {
        // Empty session
        var empty = tempDir.resolve("s-empty");
        Files.createDirectory(empty);
        Files.writeString(empty.resolve("workspace.yaml"),
                "id: s-empty\ncwd: /tmp\ncreated_at: 2026-01-01T00:00:00Z\nupdated_at: 2026-01-01T00:00:00Z\n");

        // Abandoned session
        var abandoned = tempDir.resolve("s-abandoned");
        Files.createDirectory(abandoned);
        Files.writeString(abandoned.resolve("events.jsonl"),
                "{\"type\":\"user.message\",\"data\":{\"content\":\"help\"},\"id\":\"e1\"}\n");
        Files.writeString(abandoned.resolve("workspace.yaml"),
                "id: s-abandoned\ncwd: /tmp\ncreated_at: 2026-01-02T00:00:00Z\nupdated_at: 2026-01-02T00:00:00Z\n");

        // Trivial session
        var trivial = tempDir.resolve("s-trivial");
        Files.createDirectory(trivial);
        Files.writeString(trivial.resolve("events.jsonl"),
                "{\"type\":\"user.message\",\"data\":{\"content\":\"hi\"},\"id\":\"e1\"}\n"
                + "{\"type\":\"assistant.message\",\"data\":{\"content\":\"hey\"},\"id\":\"e2\"}\n");
        Files.writeString(trivial.resolve("workspace.yaml"),
                "id: s-trivial\ncwd: /tmp\ncreated_at: 2026-01-03T00:00:00Z\nupdated_at: 2026-01-03T00:00:00Z\n");

        var candidates = pruner.scan(true);
        assertEquals(3, candidates.size());
        assertEquals(PruneCategory.EMPTY, candidates.get(0).category());
        assertEquals(PruneCategory.ABANDONED, candidates.get(1).category());
        assertEquals(PruneCategory.TRIVIAL, candidates.get(2).category());
    }

    @Test
    void diskSizeAndAgeFormatting() throws IOException {
        var sessionDir = tempDir.resolve("session-fmt");
        Files.createDirectory(sessionDir);
        Files.writeString(sessionDir.resolve("workspace.yaml"),
                "id: session-fmt\ncwd: /tmp\ncreated_at: 2026-01-01T00:00:00Z\nupdated_at: 2026-01-01T00:00:00Z\n");
        // Write some content so disk size > 0
        Files.writeString(sessionDir.resolve("padding.txt"), "x".repeat(2048));

        var candidates = pruner.scan();
        assertEquals(1, candidates.size());
        var c = candidates.getFirst();
        assertTrue(c.diskSizeBytes() > 0);
        assertFalse(c.diskSizeFormatted().isEmpty());
        assertTrue(c.age().endsWith("ago"));
    }
}
