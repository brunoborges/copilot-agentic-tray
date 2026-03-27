package com.github.copilot.tray.ui;

import com.github.copilot.tray.session.SessionDiskReader;
import com.github.copilot.tray.session.SessionDiskReader.CheckpointEntry;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Read-only viewer for a session's checkpoints.
 * Shows a list of checkpoints on the left and their content on the right.
 */
public class SessionCheckpointViewer {

    private static final Logger LOG = LoggerFactory.getLogger(SessionCheckpointViewer.class);

    private final Stage stage;

    public SessionCheckpointViewer(String sessionId, String sessionName,
                                   ThemeManager themeManager, Stage owner) {
        stage = new Stage();
        stage.setTitle("Checkpoints — " + sessionName);
        stage.initOwner(owner);

        // Left: checkpoint list
        var listView = new ListView<CheckpointEntry>();
        listView.setPrefWidth(300);
        listView.setMinWidth(200);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CheckpointEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.number() + ". " + item.title());
                }
            }
        });

        // Right: content viewer
        var contentArea = new TextArea();
        contentArea.setEditable(false);
        contentArea.setWrapText(true);
        contentArea.getStyleClass().add("checkpoint-content");
        HBox.setHgrow(contentArea, Priority.ALWAYS);

        var contentPlaceholder = new Label("Select a checkpoint to view its content.");
        contentPlaceholder.setStyle("-fx-text-fill: #888; -fx-font-style: italic;");

        // Wire selection
        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null && !selected.content().isEmpty()) {
                contentArea.setText(selected.content());
                contentArea.positionCaret(0);
            } else {
                contentArea.clear();
            }
        });

        var header = new Label("Session: " + sessionName + "  (" + sessionId + ")");
        header.getStyleClass().add("events-viewer-header");
        header.setPadding(new Insets(8));

        var statsLabel = new Label("Loading checkpoints…");
        statsLabel.getStyleClass().add("events-viewer-stats");
        statsLabel.setPadding(new Insets(0, 8, 4, 8));

        var splitPane = new SplitPane(listView, contentArea);
        splitPane.setDividerPositions(0.30);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        var root = new VBox(header, statsLabel, splitPane);
        root.getStyleClass().add("events-viewer-root");

        var scene = new Scene(root, 900, 600);
        themeManager.register(scene);
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

    public void show() {
        stage.show();
        stage.toFront();
    }
}
