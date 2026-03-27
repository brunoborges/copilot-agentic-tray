package com.github.copilot.tray;

import com.github.copilot.tray.session.GitInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GitInfoTest {

    @TempDir
    Path tempDir;

    @Test
    void nonGitDirectoryReturnsEmpty() {
        assertTrue(GitInfo.from(tempDir.toString()).isEmpty());
    }

    @Test
    void nullOrBlankReturnsEmpty() {
        assertTrue(GitInfo.from(null).isEmpty());
        assertTrue(GitInfo.from("").isEmpty());
        assertTrue(GitInfo.from("  ").isEmpty());
    }

    @Test
    void readsBranchFromHead() throws IOException {
        var gitDir = tempDir.resolve(".git");
        Files.createDirectory(gitDir);
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/feature/my-branch\n");

        var info = GitInfo.from(tempDir.toString());
        assertTrue(info.isPresent());
        assertEquals("feature/my-branch", info.get().branch());
    }

    @Test
    void detachedHeadShowsShortSha() throws IOException {
        var gitDir = tempDir.resolve(".git");
        Files.createDirectory(gitDir);
        Files.writeString(gitDir.resolve("HEAD"), "abc1234def5678901234567890abcdef12345678\n");

        var info = GitInfo.from(tempDir.toString());
        assertTrue(info.isPresent());
        assertEquals("abc1234 (detached)", info.get().branch());
    }

    @Test
    void readsOriginUrlFromConfig() throws IOException {
        var gitDir = tempDir.resolve(".git");
        Files.createDirectory(gitDir);
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main\n");
        Files.writeString(gitDir.resolve("config"), """
                [core]
                    repositoryformatversion = 0
                [remote "origin"]
                    url = git@github.com:user/repo.git
                    fetch = +refs/heads/*:refs/remotes/origin/*
                [branch "main"]
                    remote = origin
                """);

        var info = GitInfo.from(tempDir.toString());
        assertTrue(info.isPresent());
        assertEquals("main", info.get().branch());
        assertEquals("git@github.com:user/repo.git", info.get().remoteUrl());
        assertEquals("https://github.com/user/repo", info.get().githubUrl());
    }

    @Test
    void httpsGitHubUrl() throws IOException {
        var gitDir = tempDir.resolve(".git");
        Files.createDirectory(gitDir);
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main\n");
        Files.writeString(gitDir.resolve("config"), """
                [remote "origin"]
                    url = https://github.com/org/project.git
                """);

        var info = GitInfo.from(tempDir.toString());
        assertTrue(info.isPresent());
        assertEquals("https://github.com/org/project", info.get().githubUrl());
    }

    @Test
    void nonGitHubRemoteHasNoGithubUrl() throws IOException {
        var gitDir = tempDir.resolve(".git");
        Files.createDirectory(gitDir);
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main\n");
        Files.writeString(gitDir.resolve("config"), """
                [remote "origin"]
                    url = git@gitlab.com:user/repo.git
                """);

        var info = GitInfo.from(tempDir.toString());
        assertTrue(info.isPresent());
        assertEquals("git@gitlab.com:user/repo.git", info.get().remoteUrl());
        assertNull(info.get().githubUrl());
    }

    @Test
    void toGitHubUrlHandlesVariousFormats() {
        assertEquals("https://github.com/a/b",
                GitInfo.toGitHubUrl("git@github.com:a/b.git").orElse(null));
        assertEquals("https://github.com/a/b",
                GitInfo.toGitHubUrl("git@github.com:a/b").orElse(null));
        assertEquals("https://github.com/a/b",
                GitInfo.toGitHubUrl("https://github.com/a/b.git").orElse(null));
        assertEquals("https://github.com/a/b",
                GitInfo.toGitHubUrl("https://github.com/a/b").orElse(null));
        assertTrue(GitInfo.toGitHubUrl("git@bitbucket.org:a/b.git").isEmpty());
        assertTrue(GitInfo.toGitHubUrl(null).isEmpty());
    }
}
