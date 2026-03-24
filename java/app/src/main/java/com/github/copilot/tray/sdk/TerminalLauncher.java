package com.github.copilot.tray.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Opens a platform-appropriate terminal emulator to resume a Copilot CLI session.
 */
public class TerminalLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(TerminalLauncher.class);

    /**
     * Open a new terminal window running {@code copilot --resume sessionId}.
     */
    public void resumeSession(String sessionId) {
        var command = buildCommand("copilot --resume " + sessionId);
        LOG.info("Launching terminal for session {}: {}", sessionId, command);
        launch(command);
    }

    /**
     * Open a new terminal window running a fresh {@code copilot} session.
     */
    public void newSession() {
        var command = buildCommand("copilot");
        LOG.info("Launching new terminal session: {}", command);
        launch(command);
    }

    private void launch(List<String> command) {
        try {
            new ProcessBuilder(command)
                    .inheritIO()
                    .start();
        } catch (IOException e) {
            LOG.error("Failed to launch terminal: {}", command, e);
        }
    }

    private List<String> buildCommand(String shellCmd) {
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("mac")) {
            return buildMacCommand(shellCmd);
        } else if (os.contains("win")) {
            return buildWindowsCommand(shellCmd);
        } else {
            return buildLinuxCommand(shellCmd);
        }
    }

    private List<String> buildMacCommand(String shellCmd) {
        // Write a temporary .command file and use `open` to launch it
        // in the user's default terminal application (iTerm2, Terminal.app, etc.)
        try {
            var tmp = File.createTempFile("copilot-tray-", ".command");
            tmp.deleteOnExit();
            try (var writer = new java.io.FileWriter(tmp)) {
                writer.write("#!/bin/bash\n");
                writer.write(shellCmd + "\n");
            }
            tmp.setExecutable(true);
            return List.of("open", tmp.getAbsolutePath());
        } catch (IOException e) {
            LOG.error("Failed to create .command file", e);
            // Last-resort fallback
            return List.of("osascript",
                    "-e", "tell application \"Terminal\"",
                    "-e", "activate",
                    "-e", "do script \"" + shellCmd + "\"",
                    "-e", "end tell");
        }
    }

    private List<String> buildWindowsCommand(String shellCmd) {
        // Split into args for ProcessBuilder; prefer Windows Terminal, fall back to cmd
        String[] parts = shellCmd.split(" ");
        var args = new java.util.ArrayList<>(List.of("cmd", "/c", "start", "wt"));
        args.addAll(List.of(parts));
        return List.copyOf(args);
    }

    private List<String> buildLinuxCommand(String shellCmd) {
        return List.of("sh", "-c",
                "if command -v gnome-terminal > /dev/null 2>&1; then gnome-terminal -- bash -c '" + shellCmd + "; exec bash'; "
                        + "elif command -v konsole > /dev/null 2>&1; then konsole -e bash -c '" + shellCmd + "; exec bash'; "
                        + "elif command -v xterm > /dev/null 2>&1; then xterm -e '" + shellCmd + "'; "
                        + "else x-terminal-emulator -e '" + shellCmd + "'; fi");
    }
}
