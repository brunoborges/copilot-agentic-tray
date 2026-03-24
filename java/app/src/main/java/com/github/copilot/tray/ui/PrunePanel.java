package com.github.copilot.tray.ui;

import com.github.copilot.tray.session.SessionPruner;
import com.github.copilot.tray.session.SessionPruner.PruneCandidate;
import com.github.copilot.tray.session.SessionPruner.PruneCategory;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * UI panel for scanning and pruning low-value sessions.
 * Shows a dry-run preview with metadata, then requires explicit confirmation.
 */
public class PrunePanel extends VBox {

    private final SessionPruner pruner;

    private final TableView<PruneCandidate> table = new TableView<>();
    private final CheckBox includeTrivialCb = new CheckBox("Include trivial sessions (≤5 messages)");
    private final Label statusLabel = new Label("Click 'Scan' to find pruneable sessions.");
    private final Label summaryLabel = new Label("");
    private final Button scanBtn = new Button("Scan for Pruneable Sessions");
    private final Button pruneBtn = new Button("Delete Selected Sessions");
    private final Button selectAllBtn = new Button("Select All");
    private final ProgressIndicator spinner = new ProgressIndicator();

    private List<PruneCandidate> candidates = List.of();

    public PrunePanel() {
        this(new SessionPruner());
    }

    public PrunePanel(SessionPruner pruner) {
        this.pruner = pruner;

        setSpacing(10);
        setPadding(new Insets(12));

        buildTable();

        // Controls
        includeTrivialCb.setSelected(true);

        scanBtn.setOnAction(e -> runScan());
        scanBtn.setStyle("-fx-font-weight: bold;");

        pruneBtn.setOnAction(e -> confirmAndPrune());
        pruneBtn.setStyle("-fx-text-fill: #cc3333; -fx-font-weight: bold;");
        pruneBtn.setDisable(true);

        selectAllBtn.setOnAction(e -> toggleSelectAll());
        selectAllBtn.setDisable(true);

        spinner.setVisible(false);
        spinner.setPrefSize(20, 20);

        var topRow = new HBox(10, scanBtn, includeTrivialCb, spinner);
        topRow.setAlignment(Pos.CENTER_LEFT);

        summaryLabel.setTextFill(Color.web("#888888"));
        summaryLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));

        statusLabel.setWrapText(true);
        statusLabel.setTextFill(Color.web("#cccccc"));

        var bottomRow = new HBox(10, selectAllBtn, pruneBtn, statusLabel);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        getChildren().addAll(topRow, summaryLabel, table, bottomRow);
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    @SuppressWarnings("unchecked")
    private void buildTable() {
        // Selection checkboxes
        var selectCol = new TableColumn<PruneCandidate, Boolean>("✓");
        selectCol.setCellValueFactory(cd -> {
            var prop = new SimpleBooleanProperty(true);
            prop.addListener((obs, old, val) -> updatePruneButton());
            return prop;
        });
        selectCol.setCellFactory(col -> new CheckBoxTableCell());
        selectCol.setPrefWidth(35);

        // Category
        var categoryCol = new TableColumn<PruneCandidate, String>("Category");
        categoryCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().category().name()));
        categoryCol.setPrefWidth(100);
        categoryCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle(switch (PruneCategory.valueOf(item)) {
                        case EMPTY -> "-fx-text-fill: #ff6666;";
                        case ABANDONED -> "-fx-text-fill: #ffaa44;";
                        case TRIVIAL -> "-fx-text-fill: #aaaaaa;";
                    });
                }
            }
        });

        // First user message (title)
        var titleCol = new TableColumn<PruneCandidate, String>("First Message");
        titleCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().firstUserMessage()));
        titleCol.setPrefWidth(250);

        // Age
        var ageCol = new TableColumn<PruneCandidate, String>("Age");
        ageCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().age()));
        ageCol.setPrefWidth(70);

        // Size
        var sizeCol = new TableColumn<PruneCandidate, String>("Size");
        sizeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().diskSizeFormatted()));
        sizeCol.setPrefWidth(70);

        // User messages
        var userMsgCol = new TableColumn<PruneCandidate, String>("User Msgs");
        userMsgCol.setCellValueFactory(cd ->
                new SimpleStringProperty(String.valueOf(cd.getValue().userMessageCount())));
        userMsgCol.setPrefWidth(70);

        // Assistant messages
        var assistMsgCol = new TableColumn<PruneCandidate, String>("Asst Msgs");
        assistMsgCol.setCellValueFactory(cd ->
                new SimpleStringProperty(String.valueOf(cd.getValue().assistantMessageCount())));
        assistMsgCol.setPrefWidth(70);

        // Working directory
        var dirCol = new TableColumn<PruneCandidate, String>("Directory");
        dirCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().workingDirectory()));
        dirCol.setPrefWidth(200);

        table.getColumns().addAll(selectCol, categoryCol, titleCol, ageCol, sizeCol,
                userMsgCol, assistMsgCol, dirCol);
        table.setPlaceholder(new Label("No pruneable sessions found. Click 'Scan' to search."));
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    private void runScan() {
        scanBtn.setDisable(true);
        spinner.setVisible(true);
        statusLabel.setText("Scanning sessions...");
        summaryLabel.setText("");
        pruneBtn.setDisable(true);
        selectAllBtn.setDisable(true);

        boolean includeTrivial = includeTrivialCb.isSelected();

        CompletableFuture.supplyAsync(() -> pruner.scan(includeTrivial))
                .thenAccept(results -> Platform.runLater(() -> {
                    candidates = results;
                    table.setItems(FXCollections.observableArrayList(candidates));
                    scanBtn.setDisable(false);
                    spinner.setVisible(false);

                    if (candidates.isEmpty()) {
                        statusLabel.setText("No pruneable sessions found.");
                    } else {
                        long totalSize = candidates.stream()
                                .mapToLong(PruneCandidate::diskSizeBytes).sum();
                        long emptyCount = candidates.stream()
                                .filter(c -> c.category() == PruneCategory.EMPTY).count();
                        long abandonedCount = candidates.stream()
                                .filter(c -> c.category() == PruneCategory.ABANDONED).count();
                        long trivialCount = candidates.stream()
                                .filter(c -> c.category() == PruneCategory.TRIVIAL).count();

                        summaryLabel.setText("Found %d pruneable sessions — %s total disk space  |  %d empty, %d abandoned, %d trivial"
                                .formatted(candidates.size(), formatSize(totalSize),
                                        emptyCount, abandonedCount, trivialCount));
                        statusLabel.setText("Review the sessions below, then click 'Delete Selected' to prune.");
                        pruneBtn.setDisable(false);
                        selectAllBtn.setDisable(false);
                    }
                }));
    }

    private void confirmAndPrune() {
        var selected = candidates.stream().toList(); // all are selected by default via checkbox

        if (selected.isEmpty()) {
            statusLabel.setText("No sessions selected.");
            return;
        }

        long totalSize = selected.stream().mapToLong(PruneCandidate::diskSizeBytes).sum();

        var alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Prune");
        alert.setHeaderText("Delete " + selected.size() + " sessions?");
        alert.setContentText("This will permanently delete " + selected.size()
                + " session(s) and free approximately " + formatSize(totalSize)
                + " of disk space.\n\nThis action cannot be undone.");
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

        alert.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                executePrune(selected);
            }
        });
    }

    private void executePrune(List<PruneCandidate> toDelete) {
        pruneBtn.setDisable(true);
        scanBtn.setDisable(true);
        spinner.setVisible(true);
        statusLabel.setText("Deleting sessions...");

        CompletableFuture.supplyAsync(() -> pruner.delete(toDelete))
                .thenAccept(result -> Platform.runLater(() -> {
                    spinner.setVisible(false);
                    scanBtn.setDisable(false);

                    // Remove deleted from the table
                    var remaining = candidates.stream()
                            .filter(c -> !result.deletedSessionIds().contains(c.sessionId()))
                            .toList();
                    candidates = remaining;
                    table.setItems(FXCollections.observableArrayList(remaining));

                    var sb = new StringBuilder();
                    sb.append("Deleted ").append(result.deletedCount()).append(" sessions, freed ")
                            .append(result.totalBytesFreedFormatted()).append(".");
                    if (!result.failedSessionIds().isEmpty()) {
                        sb.append("  ⚠ ").append(result.failedSessionIds().size()).append(" failed.");
                    }
                    statusLabel.setText(sb.toString());
                    summaryLabel.setText("");

                    if (!remaining.isEmpty()) {
                        pruneBtn.setDisable(false);
                    }
                }));
    }

    private void toggleSelectAll() {
        // Simple toggle: if table has items, select/deselect all
        if (table.getSelectionModel().getSelectedItems().size() == table.getItems().size()) {
            table.getSelectionModel().clearSelection();
        } else {
            table.getSelectionModel().selectAll();
        }
    }

    private void updatePruneButton() {
        pruneBtn.setDisable(candidates.isEmpty());
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1_048_576) return String.format("%.1f MB", bytes / 1_048_576.0);
        if (bytes >= 1_024) return String.format("%.1f KB", bytes / 1_024.0);
        return bytes + " B";
    }

    /** Custom CheckBox cell for the selection column. */
    private static class CheckBoxTableCell extends TableCell<PruneCandidate, Boolean> {
        private final CheckBox checkBox = new CheckBox();

        CheckBoxTableCell() {
            checkBox.setSelected(true);
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(Boolean item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
            } else {
                setGraphic(checkBox);
            }
        }
    }
}
