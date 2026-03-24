package com.github.copilot.tray.ui;

import com.github.copilot.tray.session.SessionManager;
import com.github.copilot.tray.session.SessionSnapshot;
import com.github.copilot.tray.session.SessionStatus;
import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.Collection;

/**
 * Real-time usage dashboard built with Hansolo TilesFX.
 * Shows token usage, message count, model info, and session status
 * for a selected session, with an aggregate summary row.
 */
public class UsageDashboard extends VBox {

    private static final double TILE_SIZE = 200;
    private static final Color ACCENT = Tile.TileColor.BLUE.color;
    private static final Color BG = Color.web("#1a1a2e");

    private final SessionManager sessionManager;

    // Session selector
    private final ComboBox<String> sessionPicker = new ComboBox<>();

    // Per-session tiles
    private final Tile tokenGauge;
    private final Tile tokenCount;
    private final Tile messagesTile;
    private final Tile modelTile;
    private final Tile statusTile;

    // Aggregate summary tiles
    private final Tile totalSessionsTile;
    private final Tile totalTokensTile;
    private final Tile activeSessionsTile;

    public UsageDashboard(SessionManager sessionManager) {
        this.sessionManager = sessionManager;

        setSpacing(12);
        setPadding(new Insets(12));
        setStyle("-fx-background-color: #1a1a2e;");

        // --- Session picker ---
        var pickerRow = new HBox(10);
        pickerRow.setAlignment(Pos.CENTER_LEFT);
        var pickerLabel = new Label("Session:");
        pickerLabel.setTextFill(Color.WHITE);
        sessionPicker.setPrefWidth(400);
        sessionPicker.setOnAction(e -> onSessionSelected());
        pickerRow.getChildren().addAll(pickerLabel, sessionPicker);

        // --- Per-session tiles ---
        tokenGauge = TileBuilder.create()
                .skinType(Tile.SkinType.GAUGE)
                .prefSize(TILE_SIZE, TILE_SIZE)
                .title("Context Usage")
                .unit("%")
                .minValue(0)
                .maxValue(100)
                .value(0)
                .thresholdVisible(true)
                .threshold(80)
                .barColor(ACCENT)
                .thresholdColor(Tile.TileColor.RED.color)
                .animated(true)
                .build();

        tokenCount = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .prefSize(TILE_SIZE, TILE_SIZE)
                .title("Tokens Used")
                .description("of limit")
                .value(0)
                .decimals(0)
                .animated(true)
                .build();

        messagesTile = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .prefSize(TILE_SIZE, TILE_SIZE)
                .title("Messages")
                .value(0)
                .decimals(0)
                .animated(true)
                .build();

        modelTile = TileBuilder.create()
                .skinType(Tile.SkinType.TEXT)
                .prefSize(TILE_SIZE, TILE_SIZE)
                .title("Model")
                .description("—")
                .textVisible(true)
                .build();

        statusTile = TileBuilder.create()
                .skinType(Tile.SkinType.STATUS)
                .prefSize(TILE_SIZE, TILE_SIZE)
                .title("Status")
                .description("No session")
                .build();

        // --- Aggregate tiles ---
        totalSessionsTile = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .prefSize(TILE_SIZE, TILE_SIZE * 0.7)
                .title("Total Sessions")
                .value(0)
                .decimals(0)
                .animated(true)
                .build();

        totalTokensTile = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .prefSize(TILE_SIZE, TILE_SIZE * 0.7)
                .title("Total Tokens")
                .value(0)
                .decimals(0)
                .animated(true)
                .build();

        activeSessionsTile = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .prefSize(TILE_SIZE, TILE_SIZE * 0.7)
                .title("Active Sessions")
                .value(0)
                .decimals(0)
                .animated(true)
                .build();

        // Layout
        var sessionRow = new HBox(8, tokenGauge, tokenCount, messagesTile, modelTile, statusTile);
        sessionRow.setAlignment(Pos.CENTER);

        var summaryLabel = new Label("Aggregate");
        summaryLabel.setTextFill(Color.web("#aaaaaa"));
        summaryLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        var summaryRow = new HBox(8, totalSessionsTile, activeSessionsTile, totalTokensTile);
        summaryRow.setAlignment(Pos.CENTER);

        getChildren().addAll(pickerRow, sessionRow, summaryLabel, summaryRow);
        VBox.setVgrow(sessionRow, Priority.ALWAYS);
    }

    /**
     * Refresh all dashboard data. Call from the FX thread.
     */
    public void refresh(Collection<SessionSnapshot> sessions) {
        Platform.runLater(() -> {
            updatePicker(sessions);
            updateAggregate(sessions);
            onSessionSelected(); // re-apply selected session
        });
    }

    private void updatePicker(Collection<SessionSnapshot> sessions) {
        var selected = sessionPicker.getValue();
        var items = sessions.stream()
                .map(this::sessionLabel)
                .sorted()
                .toList();
        sessionPicker.setItems(FXCollections.observableArrayList(items));

        // Restore previous selection if still present
        if (selected != null && items.contains(selected)) {
            sessionPicker.setValue(selected);
        } else if (!items.isEmpty()) {
            sessionPicker.setValue(items.getFirst());
        }
    }

    private void onSessionSelected() {
        var label = sessionPicker.getValue();
        if (label == null) {
            clearSessionTiles();
            return;
        }
        var session = sessionManager.getSessions().stream()
                .filter(s -> sessionLabel(s).equals(label))
                .findFirst()
                .orElse(null);
        if (session == null) {
            clearSessionTiles();
            return;
        }
        updateSessionTiles(session);
    }

    private void updateSessionTiles(SessionSnapshot session) {
        var usage = session.usage();

        tokenGauge.setValue(usage.tokenUsagePercent());

        tokenCount.setValue(usage.currentTokens());
        tokenCount.setDescription("of " + formatNumber(usage.tokenLimit()));

        messagesTile.setValue(usage.messagesCount());

        modelTile.setDescription(session.model());

        statusTile.setDescription(session.status().name());
        statusTile.setActiveColor(statusColor(session.status()));

        var location = session.remote() ? " (Remote)" : " (Local)";
        statusTile.setText(session.status().name() + location);
    }

    private void clearSessionTiles() {
        tokenGauge.setValue(0);
        tokenCount.setValue(0);
        tokenCount.setDescription("of 0");
        messagesTile.setValue(0);
        modelTile.setDescription("—");
        statusTile.setDescription("No session");
        statusTile.setText("");
    }

    private void updateAggregate(Collection<SessionSnapshot> sessions) {
        totalSessionsTile.setValue(sessions.size());

        long active = sessions.stream()
                .filter(s -> s.status() != SessionStatus.ARCHIVED)
                .count();
        activeSessionsTile.setValue(active);

        int totalTokens = sessions.stream()
                .mapToInt(s -> s.usage().currentTokens())
                .sum();
        totalTokensTile.setValue(totalTokens);
    }

    private String sessionLabel(SessionSnapshot s) {
        var label = s.name();
        if (!"unknown".equals(s.model())) {
            label += " [" + s.model() + "]";
        }
        return label;
    }

    private static Color statusColor(SessionStatus status) {
        return switch (status) {
            case IDLE -> Tile.TileColor.GREEN.color;
            case BUSY -> Tile.TileColor.ORANGE.color;
            case ACTIVE -> Tile.TileColor.BLUE.color;
            case ERROR -> Tile.TileColor.RED.color;
            case ARCHIVED -> Color.GRAY;
        };
    }

    private static String formatNumber(int n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }
}
