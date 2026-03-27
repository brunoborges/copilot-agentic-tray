package com.github.copilot.tray.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads basic git metadata from a working directory by parsing
 * .git/HEAD and .git/config directly — no git CLI dependency.
 */
public record GitInfo(String branch, String remoteUrl, String githubUrl) {

    private static final Pattern REMOTE_URL = Pattern.compile(
            "\\[remote \"origin\"\\].*?url\\s*=\\s*(.+)",
            Pattern.DOTALL);

    /**
     * Attempts to read git info from the given directory.
     * Returns empty if the directory is not a git repository.
     */
    public static Optional<GitInfo> from(String workingDirectory) {
        if (workingDirectory == null || workingDirectory.isBlank()) {
            return Optional.empty();
        }
        var dir = Path.of(workingDirectory);
        var gitDir = dir.resolve(".git");
        if (!Files.isDirectory(gitDir)) {
            return Optional.empty();
        }

        var branch = readBranch(gitDir);
        var remoteUrl = readOriginUrl(gitDir);
        var githubUrl = remoteUrl.flatMap(GitInfo::toGitHubUrl);

        if (branch.isEmpty() && remoteUrl.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new GitInfo(
                branch.orElse(null),
                remoteUrl.orElse(null),
                githubUrl.orElse(null)));
    }

    private static Optional<String> readBranch(Path gitDir) {
        var headFile = gitDir.resolve("HEAD");
        if (!Files.isRegularFile(headFile)) {
            return Optional.empty();
        }
        try {
            var content = Files.readString(headFile).trim();
            if (content.startsWith("ref: refs/heads/")) {
                return Optional.of(content.substring("ref: refs/heads/".length()));
            }
            // Detached HEAD — return short SHA
            if (content.length() >= 7) {
                return Optional.of(content.substring(0, 7) + " (detached)");
            }
        } catch (IOException ignored) {
        }
        return Optional.empty();
    }

    private static Optional<String> readOriginUrl(Path gitDir) {
        var configFile = gitDir.resolve("config");
        if (!Files.isRegularFile(configFile)) {
            return Optional.empty();
        }
        try {
            var lines = Files.readAllLines(configFile);
            boolean inOrigin = false;
            for (var line : lines) {
                var trimmed = line.trim();
                if (trimmed.equals("[remote \"origin\"]")) {
                    inOrigin = true;
                    continue;
                }
                if (inOrigin && trimmed.startsWith("[")) {
                    break; // next section
                }
                if (inOrigin && trimmed.startsWith("url =")) {
                    return Optional.of(trimmed.substring("url =".length()).trim());
                }
            }
        } catch (IOException ignored) {
        }
        return Optional.empty();
    }

    /**
     * Converts a git remote URL to a GitHub web URL, if applicable.
     * Handles SSH (git@github.com:owner/repo.git) and HTTPS
     * (https://github.com/owner/repo.git) formats.
     */
    public static Optional<String> toGitHubUrl(String remoteUrl) {
        if (remoteUrl == null) return Optional.empty();

        // SSH: git@github.com:owner/repo.git
        if (remoteUrl.startsWith("git@github.com:")) {
            var path = remoteUrl.substring("git@github.com:".length());
            path = stripGitSuffix(path);
            return Optional.of("https://github.com/" + path);
        }
        // HTTPS: https://github.com/owner/repo.git
        if (remoteUrl.contains("github.com/")) {
            var idx = remoteUrl.indexOf("github.com/");
            var path = remoteUrl.substring(idx + "github.com/".length());
            path = stripGitSuffix(path);
            return Optional.of("https://github.com/" + path);
        }
        return Optional.empty();
    }

    private static String stripGitSuffix(String path) {
        return path.endsWith(".git") ? path.substring(0, path.length() - 4) : path;
    }
}
