package com.github.copilot.tray.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointParserTest {

    @Test
    void parseSections_extractsAllSections() {
        var content = """
                <overview>
                This is the overview.
                </overview>
                
                <history>
                1. First thing
                2. Second thing
                </history>
                
                <work_done>
                Files created:
                - file.java
                </work_done>
                
                <technical_details>
                Some technical detail.
                </technical_details>
                
                <important_files>
                - `src/Main.java`
                  - Entry point
                  - 100 lines
                </important_files>
                
                <next_steps>
                - Do more work
                </next_steps>
                
                <checkpoint_title>My checkpoint</checkpoint_title>
                """;

        var sections = SessionCheckpointViewer.parseSections(content);

        assertEquals(6, sections.size());
        assertTrue(sections.containsKey("overview"));
        assertTrue(sections.containsKey("history"));
        assertTrue(sections.containsKey("work_done"));
        assertTrue(sections.containsKey("technical_details"));
        assertTrue(sections.containsKey("important_files"));
        assertTrue(sections.containsKey("next_steps"));

        // checkpoint_title should be excluded
        assertFalse(sections.containsKey("checkpoint_title"));

        assertTrue(sections.get("overview").contains("This is the overview"));
        assertTrue(sections.get("history").contains("1. First thing"));
    }

    @Test
    void parseSections_returnsEmptyForNoSections() {
        var sections = SessionCheckpointViewer.parseSections("Just raw text, no sections.");
        assertTrue(sections.isEmpty());
    }

    @Test
    void parseFileEntries_extractsPathsAndDescriptions() {
        var content = """
                - `java/app/src/Main.java`
                  - Entry point for the application
                  - 200 lines
                
                - `java/app/src/Config.java`
                  - Configuration handling
                """;

        var entries = SessionCheckpointViewer.parseFileEntries(content);

        assertEquals(2, entries.size());

        assertEquals("java/app/src/Main.java", entries.get(0).path());
        assertEquals(2, entries.get(0).descriptions().size());
        assertEquals("Entry point for the application", entries.get(0).descriptions().get(0));
        assertEquals("200 lines", entries.get(0).descriptions().get(1));

        assertEquals("java/app/src/Config.java", entries.get(1).path());
        assertEquals(1, entries.get(1).descriptions().size());
    }

    @Test
    void parseFileEntries_handlesNoBackticks() {
        var content = """
                - src/main/App.java
                  - The main app
                """;

        var entries = SessionCheckpointViewer.parseFileEntries(content);
        assertEquals(1, entries.size());
        assertEquals("src/main/App.java", entries.get(0).path());
    }
}
