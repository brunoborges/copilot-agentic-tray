package com.github.copilot.tray.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Opens a platform-appropriate terminal emulator to resume a Copilot CLI session.
 */
public class TerminalLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(TerminalLauncher.class);

    /**
     * Open a new terminal window running `copilot /resume sessionId`.
     */
    public void resumeSession(String sessionId) {
        var command = buildCommand(sessionId);
        LOG.info("Launching terminal for session {}: {}", sessionId, command);
        try {
            new ProcessBuilder(command)
                    .inheritIO()
                    .start();
        } catch (IOException e) {
            LOG.error("Failed to launch terminal for session {}", sessionId, e);
        }
    }

    /**
     * Open a new terminal window running a fresh `copilot` session.
     */
    public void newSession() {
        var command = buildNewSessionCommand();
        LOG.info("Launching new terminal session: {}", command);
        try {
            new ProcessBuilder(command)
                    .inheritIO()
                    .start();
        } catch (IOException e) {
            LOG.error("Failed to launch new terminal session", e);
        }
    }

    private List<String> buildCommand(String sessionId) {
        String os = System.getProperty("os.name", "").toLowerCase();
        String resumeCmd = "copilot /resume " + sessionId;

        if (os.contains("mac")) {
            return List.of("open", "-a", "Terminal", "--args", "-e", resumeCmd);
        } else if (os.contains("win")) {
            return List.of("cmd", "/c", "start", "wt", "copilot", "/resume", sessionId);
        } else {
            // Linux: try common terminal emulators
            return List.of("sh", "-c",
                    "if command -v gnome-terminal > /dev/null 2>&1; then gnome-terminal -- bash -c '" + resumeCmd + "; exec bash'; "
                            + "elif command -v konsole > /dev/null 2>&1; then konsole -e bash -c '" + resumeCmd + "; exec bash'; "
                            + "elif command -v xterm > /dev/null 2>&1; then xterm -e '" + resumeCmd + "'; "
                            + "else x-terminal-emulator -e '" + resumeCmd + "'; fi");
        }
    }

    private List<String> buildNewSessionCommand() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return List.of("open", "-a", "Terminal", "--args", "-e", "copilot");
        } else if (os.contains("win")) {
            return List.of("cmd", "/c", "start", "wt", "copilot");
        } else {
            return List.of("sh", "-c",
                    "if command -v gnome-terminal > /dev/null 2>&1; then gnome-terminal -- bash -c 'copilot; exec bash'; "
                            + "elif command -v konsole > /dev/null 2>&1; then konsole -e bash -c 'copilot; exec bash'; "
                            + "elif command -v xterm > /dev/null 2>&1; then xterm -e 'copilot'; "
                            + "else x-terminal-emulator -e 'copilot'; fi");
        }
    }
}
