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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * UI panel for scanning and pruning low-value sessions.
 * Shows a dry-run preview with metadata, then requires explicit confirmation.
 */
public class PrunePanel extends VBox {

    private final SessionPruner pruner;
    private final Consumer<String> resumeHandler;

    private final TableView<PruneCandidate> table = new TableView<>();
    private final CheckBox includeTrivialCb = new CheckBox("Include trivial sessions (≤5 messages)");
    private final Label statusLabel = new Label("Click 'Scan' to find pruneable sessions.");
    private final Label summaryLabel = new Label("");
    private final Button scanBtn = new Button("Scan for Pruneable Sessions");
    private final Button pruneBtn = new Button("Delete Selected");
    private final ProgressIndicator spinner = new ProgressIndicator();

    // Shift-click range selection: track last toggled row index
    private int lastClickedIndex = -1;

    // Selection buttons
    private final Button deselectAllBtn = new Button("Deselect All");
    private final Button selectEmptyBtn = new Button("Select Empty");
    private final Button selectAbandonedBtn = new Button("Select Abandoned");
    private final Button selectTrivialBtn = new Button("Select Trivial");
    private final Button selectAllBtn = new Button("Select All");
    private final Button infoBtn = new Button("ℹ Categories");

    // Per-row selection state keyed by sessionId
    private final Map<String, SimpleBooleanProperty> selectionMap = new LinkedHashMap<>();
    private List<PruneCandidate> candidates = List.of();

    public PrunePanel() {
        this(new SessionPruner(), id -> {});
    }

    public PrunePanel(SessionPruner pruner, Consumer<String> resumeHandler) {
        this.pruner = pruner;
        this.resumeHandler = resumeHandler;

        setSpacing(10);
        setPadding(new Insets(12));

        buildTable();

        // Scan controls
        includeTrivialCb.setSelected(true);
        scanBtn.setOnAction(e -> runScan());
        scanBtn.setStyle("-fx-font-weight: bold;");

        spinner.setVisible(false);
        spinner.setPrefSize(20, 20);

        infoBtn.setOnAction(e -> showCategoryInfo());

        var topRow = new HBox(10, scanBtn, includeTrivialCb, infoBtn, spinner);
        topRow.setAlignment(Pos.CENTER_LEFT);

        summaryLabel.setTextFill(Color.web("#888888"));
        summaryLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));

        statusLabel.setWrapText(true);
        statusLabel.setTextFill(Color.web("#cccccc"));

        // Selection controls
        deselectAllBtn.setOnAction(e -> setSelectionByCategory(null, false));
        selectAllBtn.setOnAction(e -> setSelectionByCategory(null, true));
        selectEmptyBtn.setOnAction(e -> { clearAllSelections(); setSelectionByCategory(PruneCategory.EMPTY, true); });
        selectAbandonedBtn.setOnAction(e -> { clearAllSelections(); setSelectionByCategory(PruneCategory.ABANDONED, true); });
        selectTrivialBtn.setOnAction(e -> { clearAllSelections(); setSelectionByCategory(PruneCategory.TRIVIAL, true); });

        var selectionRow = new HBox(8, deselectAllBtn, selectAllBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                selectEmptyBtn, selectAbandonedBtn, selectTrivialBtn);
        selectionRow.setAlignment(Pos.CENTER_LEFT);
        setSelectionControlsDisabled(true);

        // Delete button
        pruneBtn.setOnAction(e -> confirmAndPrune());
        pruneBtn.setStyle("-fx-text-fill: #cc3333; -fx-font-weight: bold;");
        pruneBtn.setDisable(true);

        var bottomRow = new HBox(10, pruneBtn, statusLabel);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        getChildren().addAll(topRow, summaryLabel, selectionRow, table, bottomRow);
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    @SuppressWarnings("unchecked")
    private void buildTable() {
        // Checkbox column
        var selectCol = new TableColumn<PruneCandidate, Boolean>("✓");
        selectCol.setCellValueFactory(cd -> getSelectionProperty(cd.getValue().sessionId()));
        selectCol.setCellFactory(col -> new CheckBoxCell());
        selectCol.setPrefWidth(35);
        selectCol.setSortable(false);

        // Session ID
        var idCol = new TableColumn<PruneCandidate, String>("Session ID");
        idCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().sessionId().substring(0, Math.min(8, cd.getValue().sessionId().length())) + "…"));
        idCol.setPrefWidth(80);
        idCol.setCellFactory(col -> {
            var cell = new TableCell<PruneCandidate, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                    if (!empty) {
                        // Show full ID on hover
                        var candidate = getTableRow().getItem();
                        if (candidate != null) {
                            setTooltip(new Tooltip(candidate.sessionId()));
                        }
                    }
                }
            };
            cell.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
            return cell;
        });

        // Category
        var categoryCol = new TableColumn<PruneCandidate, String>("Category");
        categoryCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().category().name()));
        categoryCol.setPrefWidth(90);
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
                        case EMPTY -> "-fx-text-fill: #ff6666; -fx-font-weight: bold;";
                        case ABANDONED -> "-fx-text-fill: #ffaa44; -fx-font-weight: bold;";
                        case TRIVIAL -> "-fx-text-fill: #aaaaaa;";
                    });
                }
            }
        });

        // First user message
        var titleCol = new TableColumn<PruneCandidate, String>("First Message");
        titleCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().firstUserMessage()));
        titleCol.setPrefWidth(220);

        // Age
        var ageCol = new TableColumn<PruneCandidate, String>("Age");
        ageCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().age()));
        ageCol.setPrefWidth(65);

        // Size
        var sizeCol = new TableColumn<PruneCandidate, String>("Size");
        sizeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().diskSizeFormatted()));
        sizeCol.setPrefWidth(65);

        // User / Assistant messages
        var userMsgCol = new TableColumn<PruneCandidate, String>("Usr");
        userMsgCol.setCellValueFactory(cd ->
                new SimpleStringProperty(String.valueOf(cd.getValue().userMessageCount())));
        userMsgCol.setPrefWidth(40);

        var assistMsgCol = new TableColumn<PruneCandidate, String>("Ast");
        assistMsgCol.setCellValueFactory(cd ->
                new SimpleStringProperty(String.valueOf(cd.getValue().assistantMessageCount())));
        assistMsgCol.setPrefWidth(40);

        // Working directory
        var dirCol = new TableColumn<PruneCandidate, String>("Directory");
        dirCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().workingDirectory()));
        dirCol.setPrefWidth(160);

        // Actions column
        var actionsCol = new TableColumn<PruneCandidate, Void>("Actions");
        actionsCol.setPrefWidth(140);
        actionsCol.setSortable(false);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button resumeBtn = new Button("Resume");
            private final Button deleteBtn = new Button("Delete");
            private final HBox box = new HBox(4, resumeBtn, deleteBtn);
            {
                resumeBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 6;");
                resumeBtn.setOnAction(e -> {
                    var item = getTableRow().getItem();
                    if (item != null) {
                        resumeHandler.accept(item.sessionId());
                    }
                });
                deleteBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 6; -fx-text-fill: #cc3333;");
                deleteBtn.setOnAction(e -> {
                    var item = getTableRow().getItem();
                    if (item != null) {
                        deleteSingleSession(item);
                    }
                });
                box.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        table.getColumns().addAll(selectCol, idCol, categoryCol, titleCol, ageCol,
                sizeCol, userMsgCol, assistMsgCol, dirCol, actionsCol);
        table.setPlaceholder(new Label("No pruneable sessions found. Click 'Scan' to search."));
    }

    // --- Selection management ---

    private SimpleBooleanProperty getSelectionProperty(String sessionId) {
        return selectionMap.computeIfAbsent(sessionId, id -> {
            var prop = new SimpleBooleanProperty(false);
            prop.addListener((obs, old, val) -> updatePruneButton());
            return prop;
        });
    }

    private void clearAllSelections() {
        selectionMap.values().forEach(p -> p.set(false));
    }

    private void setSelectionByCategory(PruneCategory category, boolean selected) {
        for (var candidate : candidates) {
            if (category == null || candidate.category() == category) {
                getSelectionProperty(candidate.sessionId()).set(selected);
            }
        }
    }

    private List<PruneCandidate> getSelectedCandidates() {
        return candidates.stream()
                .filter(c -> getSelectionProperty(c.sessionId()).get())
                .toList();
    }

    private void setSelectionControlsDisabled(boolean disabled) {
        deselectAllBtn.setDisable(disabled);
        selectAllBtn.setDisable(disabled);
        selectEmptyBtn.setDisable(disabled);
        selectAbandonedBtn.setDisable(disabled);
        selectTrivialBtn.setDisable(disabled);
    }

    private void updatePruneButton() {
        long selected = getSelectedCandidates().size();
        pruneBtn.setDisable(selected == 0);
        if (selected > 0) {
            long size = getSelectedCandidates().stream().mapToLong(PruneCandidate::diskSizeBytes).sum();
            pruneBtn.setText("Delete " + selected + " Selected (" + formatSize(size) + ")");
        } else {
            pruneBtn.setText("Delete Selected");
        }
    }

    // --- Scan ---

    private void runScan() {
        scanBtn.setDisable(true);
        spinner.setVisible(true);
        statusLabel.setText("Scanning sessions...");
        summaryLabel.setText("");
        pruneBtn.setDisable(true);
        setSelectionControlsDisabled(true);
        selectionMap.clear();

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

                        summaryLabel.setText("Found %d sessions — %s total  |  %d empty, %d abandoned, %d trivial"
                                .formatted(candidates.size(), formatSize(totalSize),
                                        emptyCount, abandonedCount, trivialCount));
                        statusLabel.setText("Use the selection buttons to pick sessions, then delete.");
                        setSelectionControlsDisabled(false);
                    }
                }));
    }

    // --- Prune ---

    private void deleteSingleSession(PruneCandidate candidate) {
        var alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete session '" + candidate.firstUserMessage() + "'?\n("
                        + candidate.diskSizeFormatted() + ")",
                ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.setTitle("Delete Session");
        alert.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                executePrune(List.of(candidate));
            }
        });
    }

    private void confirmAndPrune() {
        var selected = getSelectedCandidates();
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

                    // Remove deleted from table and selection map
                    result.deletedSessionIds().forEach(selectionMap::remove);
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
                    updatePruneButton();

                    if (!remaining.isEmpty()) {
                        setSelectionControlsDisabled(false);
                    }
                }));
    }

    // --- Info dialog ---

    private void showCategoryInfo() {
        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Prune Categories");
        alert.setHeaderText("How sessions are classified for pruning");
        alert.setContentText("""
                EMPTY (red)
                Sessions with no events.jsonl file, or no user messages at all. \
                These were likely created by accident or immediately abandoned.

                ABANDONED (orange)
                Sessions where the user sent a message but no assistant response \
                was ever generated. The session was started but never completed \
                its first exchange.

                TRIVIAL (gray)
                Sessions with ≤5 user messages and some assistant responses. \
                These are very short exchanges that typically hold little value \
                for future reference. This category is optional and can be \
                excluded using the checkbox.

                Sessions with more than 5 user messages and at least one \
                assistant response are considered valuable and never flagged.""");
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }

    // --- Utilities ---

    private static String formatSize(long bytes) {
        if (bytes >= 1_048_576) return String.format("%.1f MB", bytes / 1_048_576.0);
        if (bytes >= 1_024) return String.format("%.1f KB", bytes / 1_024.0);
        return bytes + " B";
    }

    /** CheckBox cell bound to the per-row selection property, with shift-click range support. */
    private class CheckBoxCell extends TableCell<PruneCandidate, Boolean> {
        private final CheckBox checkBox = new CheckBox();
        private String boundSessionId;
        private javafx.beans.value.ChangeListener<Boolean> externalListener;
        private boolean updating; // guard against feedback loops

        CheckBoxCell() {
            setAlignment(Pos.CENTER);

            // Intercept mouse clicks on the checkbox for shift-click range selection
            checkBox.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
                if (boundSessionId == null) return;
                int myIndex = getIndex();
                boolean newValue = !checkBox.isSelected(); // what the toggle will become

                if (event.isShiftDown() && lastClickedIndex >= 0 && lastClickedIndex != myIndex) {
                    // Shift-click: select/deselect the range
                    event.consume(); // prevent default toggle
                    int lo = Math.min(lastClickedIndex, myIndex);
                    int hi = Math.max(lastClickedIndex, myIndex);
                    var items = table.getItems();
                    for (int i = lo; i <= hi && i < items.size(); i++) {
                        getSelectionProperty(items.get(i).sessionId()).set(newValue);
                    }
                    lastClickedIndex = myIndex;
                } else {
                    // Normal click — let the default toggle happen
                    lastClickedIndex = myIndex;
                }
            });

            checkBox.selectedProperty().addListener((obs, old, val) -> {
                if (!updating && boundSessionId != null) {
                    getSelectionProperty(boundSessionId).set(val);
                }
            });
        }

        @Override
        protected void updateItem(Boolean item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
                unbind();
                return;
            }

            var candidate = getTableRow().getItem();
            var sessionId = candidate.sessionId();

            // Only rebind if the row changed
            if (!sessionId.equals(boundSessionId)) {
                unbind();
                boundSessionId = sessionId;
                var prop = getSelectionProperty(sessionId);

                updating = true;
                checkBox.setSelected(prop.get());
                updating = false;

                externalListener = (obs, old, val) -> {
                    updating = true;
                    checkBox.setSelected(val);
                    updating = false;
                };
                prop.addListener(externalListener);
            }
            setGraphic(checkBox);
        }

        private void unbind() {
            if (boundSessionId != null && externalListener != null) {
                var prop = selectionMap.get(boundSessionId);
                if (prop != null) {
                    prop.removeListener(externalListener);
                }
            }
            boundSessionId = null;
            externalListener = null;
        }
    }
}
