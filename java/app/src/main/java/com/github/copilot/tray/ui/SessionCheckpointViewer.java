package com.github.copilot.tray.ui;

import com.github.copilot.tray.session.SessionDiskReader;
import com.github.copilot.tray.session.SessionDiskReader.CheckpointEntry;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Read-only viewer for a session's checkpoints.
 * Parses checkpoint content into structured sections (overview, history, work_done,
 * technical_details, important_files, next_steps) and renders each as a styled card.
 */
public class SessionCheckpointViewer {

    private static final Logger LOG = LoggerFactory.getLogger(SessionCheckpointViewer.class);

    private static final java.util.Map<String, SessionCheckpointViewer> OPEN_VIEWERS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final Pattern SECTION_PATTERN =
            Pattern.compile("<(overview|history|work_done|technical_details|important_files|next_steps|checkpoint_title)>(.*?)</\\1>",
                    Pattern.DOTALL);

    private static final Map<String, SectionMeta> SECTION_META = Map.of(
            "overview", new SectionMeta("📋", "Overview", "checkpoint-section-overview"),
            "history", new SectionMeta("📝", "History", "checkpoint-section-history"),
            "work_done", new SectionMeta("✅", "Work Done", "checkpoint-section-work-done"),
            "technical_details", new SectionMeta("🔧", "Technical Details", "checkpoint-section-technical"),
            "important_files", new SectionMeta("📁", "Important Files", "checkpoint-section-files"),
            "next_steps", new SectionMeta("🔜", "Next Steps", "checkpoint-section-next-steps")
    );

    private record SectionMeta(String icon, String label, String cssClass) {}

    /** A parsed file entry from the important_files section. */
    record FileEntry(String path, List<String> descriptions) {}

    private final Stage stage;
    private final VBox sectionsContainer;

    public SessionCheckpointViewer(String sessionId, String sessionName,
                                   ThemeManager themeManager, Stage owner) {
        stage = new Stage();
        stage.setTitle("Checkpoints — " + sessionName);
        stage.initOwner(owner);

        // Left: checkpoint list in a card
        var listView = new ListView<CheckpointEntry>();
        listView.setPrefWidth(280);
        listView.setMinWidth(200);
        listView.getStyleClass().add("checkpoint-list");
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CheckpointEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    var numLabel = new Label("#" + item.number());
                    numLabel.getStyleClass().add("checkpoint-list-number");
                    numLabel.setMinWidth(30);

                    var titleLabel = new Label(item.title());
                    titleLabel.getStyleClass().add("checkpoint-list-title");
                    titleLabel.setWrapText(true);

                    var row = new HBox(6, numLabel, titleLabel);
                    row.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(row);
                    setText(null);
                }
            }
        });
        VBox.setVgrow(listView, Priority.ALWAYS);
        var listCard = new VBox(listView);
        listCard.getStyleClass().add("sessions-card");

        // Right: scrollable section cards
        sectionsContainer = new VBox(10);
        sectionsContainer.setPadding(new Insets(4));

        var scrollPane = new ScrollPane(sectionsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("checkpoint-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        var contentCard = new VBox(scrollPane);
        contentCard.getStyleClass().add("sessions-card");
        HBox.setHgrow(contentCard, Priority.ALWAYS);

        // Wire selection → parse and render sections
        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null && !selected.content().isEmpty()) {
                renderCheckpoint(selected);
            } else {
                sectionsContainer.getChildren().clear();
            }
        });

        var header = new Label("Session: " + sessionName + "  (" + sessionId + ")");
        header.getStyleClass().add("events-viewer-header");
        header.setPadding(new Insets(8));

        var statsLabel = new Label("Loading checkpoints…");
        statsLabel.getStyleClass().add("events-viewer-stats");
        statsLabel.setPadding(new Insets(0, 8, 4, 8));

        var contentPane = new HBox(8, listCard, contentCard);
        VBox.setVgrow(contentPane, Priority.ALWAYS);

        var root = new VBox(8, header, statsLabel, contentPane);
        root.getStyleClass().addAll("events-viewer-root", "content-padding");

        var scene = new Scene(root, 1000, 700);
        themeManager.register(scene);
        scene.getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(
                        javafx.scene.input.KeyCode.W,
                        javafx.scene.input.KeyCombination.SHORTCUT_DOWN),
                () -> stage.close());
        stage.setOnHidden(e -> OPEN_VIEWERS.remove(sessionId));
        stage.setScene(scene);

        // Load checkpoints in background
        Thread.ofVirtual().start(() -> {
            var checkpoints = SessionDiskReader.readCheckpoints(sessionId);
            Platform.runLater(() -> {
                if (checkpoints.isEmpty()) {
                    statsLabel.setText("No checkpoints found.");
                    listView.setPlaceholder(new Label("No checkpoints available."));
                } else {
                    statsLabel.setText(checkpoints.size() + " checkpoint"
                            + (checkpoints.size() == 1 ? "" : "s"));
                    listView.getItems().setAll(checkpoints);
                    listView.getSelectionModel().selectFirst();
                }
            });
        });
    }

    // ── Section parsing ──────────────────────────────────────────────

    private void renderCheckpoint(CheckpointEntry entry) {
        sectionsContainer.getChildren().clear();
        var sections = parseSections(entry.content());

        for (var section : sections.entrySet()) {
            var meta = SECTION_META.get(section.getKey());
            if (meta == null) continue;

            Node card;
            if ("important_files".equals(section.getKey())) {
                card = buildFilesSection(meta, section.getValue());
            } else {
                card = buildSection(meta, section.getValue());
            }
            sectionsContainer.getChildren().add(card);
        }

        // If no recognized sections were parsed, show raw content as fallback
        if (sectionsContainer.getChildren().isEmpty()) {
            var fallback = new Label(entry.content());
            fallback.setWrapText(true);
            fallback.getStyleClass().add("checkpoint-content-text");
            var card = new VBox(8, fallback);
            card.getStyleClass().addAll("checkpoint-section-card", "checkpoint-section-overview");
            card.setPadding(new Insets(10));
            sectionsContainer.getChildren().add(card);
        }
    }

    static Map<String, String> parseSections(String content) {
        var sections = new LinkedHashMap<String, String>();
        var matcher = SECTION_PATTERN.matcher(content);
        while (matcher.find()) {
            var tag = matcher.group(1);
            var body = matcher.group(2).strip();
            if (!"checkpoint_title".equals(tag) && !body.isEmpty()) {
                sections.put(tag, body);
            }
        }
        return sections;
    }

    // ── Card builders ────────────────────────────────────────────────

    private Node buildSection(SectionMeta meta, String content) {
        var roleLabel = new Label(meta.icon() + "  " + meta.label());
        roleLabel.getStyleClass().add("checkpoint-section-header");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var headerRow = new HBox(8, roleLabel, spacer);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        var contentLabel = new Label(content);
        contentLabel.setWrapText(true);
        contentLabel.getStyleClass().add("checkpoint-content-text");

        var card = new VBox(6, headerRow, contentLabel);
        card.getStyleClass().addAll("checkpoint-section-card", meta.cssClass());
        card.setPadding(new Insets(10));
        return card;
    }

    private Node buildFilesSection(SectionMeta meta, String content) {
        var roleLabel = new Label(meta.icon() + "  " + meta.label());
        roleLabel.getStyleClass().add("checkpoint-section-header");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var headerRow = new HBox(8, roleLabel, spacer);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        var card = new VBox(6, headerRow);
        card.getStyleClass().addAll("checkpoint-section-card", meta.cssClass());
        card.setPadding(new Insets(10));

        var fileEntries = parseFileEntries(content);
        for (var fileEntry : fileEntries) {
            card.getChildren().add(buildFileEntry(fileEntry));
        }

        if (fileEntries.isEmpty()) {
            var fallback = new Label(content);
            fallback.setWrapText(true);
            fallback.getStyleClass().add("checkpoint-content-text");
            card.getChildren().add(fallback);
        }

        return card;
    }

    private Node buildFileEntry(FileEntry entry) {
        var pathLabel = new Label(entry.path());
        pathLabel.getStyleClass().add("checkpoint-file-path");
        pathLabel.setWrapText(true);

        // Copy path to clipboard on click
        var copyBtn = new Button("📋");
        copyBtn.getStyleClass().add("checkpoint-file-copy");
        copyBtn.setTooltip(new Tooltip("Copy path"));
        copyBtn.setOnAction(e -> {
            var cc = new ClipboardContent();
            cc.putString(entry.path());
            Clipboard.getSystemClipboard().setContent(cc);
            copyBtn.setText("✓");
            // Reset after brief delay
            Thread.ofVirtual().start(() -> {
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> copyBtn.setText("📋"));
            });
        });

        var pathRow = new HBox(6, pathLabel, copyBtn);
        pathRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(pathLabel, Priority.ALWAYS);

        var fileCard = new VBox(3, pathRow);
        fileCard.getStyleClass().add("checkpoint-file-entry");
        fileCard.setPadding(new Insets(6, 8, 6, 8));

        for (var desc : entry.descriptions()) {
            var descLabel = new Label("  • " + desc);
            descLabel.setWrapText(true);
            descLabel.getStyleClass().add("checkpoint-file-desc");
            fileCard.getChildren().add(descLabel);
        }

        return fileCard;
    }

    // ── important_files parser ───────────────────────────────────────

    static List<FileEntry> parseFileEntries(String content) {
        var entries = new ArrayList<FileEntry>();
        String currentPath = null;
        var descriptions = new ArrayList<String>();

        for (var line : content.split("\n")) {
            // Top-level file entry: "- `path/to/file`" or "- path/to/file"
            if (line.matches("^- `[^`]+`.*") || line.matches("^- [^ ].*\\.[a-zA-Z]+.*")) {
                // Save previous entry
                if (currentPath != null) {
                    entries.add(new FileEntry(currentPath, List.copyOf(descriptions)));
                }
                // Extract path (strip backticks and leading "- ")
                var pathPart = line.substring(2).strip();
                if (pathPart.startsWith("`") && pathPart.contains("`")) {
                    currentPath = pathPart.substring(1, pathPart.indexOf('`', 1));
                } else {
                    currentPath = pathPart;
                }
                descriptions = new ArrayList<>();
            } else if (currentPath != null && line.matches("^  +- .*")) {
                // Sub-bullet description
                descriptions.add(line.strip().substring(2).strip());
            }
        }
        // Save last entry
        if (currentPath != null) {
            entries.add(new FileEntry(currentPath, List.copyOf(descriptions)));
        }
        return entries;
    }

    // ── Singleton viewer ─────────────────────────────────────────────

    /** Show or refocus an existing viewer for the given session. */
    public static void showViewer(String sessionId, String sessionName,
                                  ThemeManager themeManager, Stage owner) {
        var existing = OPEN_VIEWERS.get(sessionId);
        if (existing != null) {
            existing.stage.show();
            existing.stage.toFront();
            return;
        }
        var viewer = new SessionCheckpointViewer(sessionId, sessionName, themeManager, owner);
        OPEN_VIEWERS.put(sessionId, viewer);
        viewer.stage.show();
        viewer.stage.toFront();
    }

    public void show() {
        stage.show();
        stage.toFront();
    }
}
