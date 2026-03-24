package com.github.copilot.tray.ui;

import com.github.copilot.tray.config.AppConfig;
import com.github.copilot.tray.config.ConfigStore;
import com.github.copilot.tray.session.SessionManager;
import com.github.copilot.tray.session.SessionSnapshot;
import com.github.copilot.tray.session.SessionStatus;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.Collection;

/**
 * JavaFX settings window launched from the system tray.
 * Uses programmatic UI construction (no FXML) for simplicity in v1.
 */
public class SettingsWindow {

    private final SessionManager sessionManager;
    private final ConfigStore configStore;
    private Stage stage;

    // Session tab controls
    private ListView<String> sessionListView;
    private Label detailLabel;

    // Preferences tab controls
    private TextField cliPathField;
    private Spinner<Integer> pollIntervalSpinner;
    private Spinner<Integer> warningThresholdSpinner;
    private CheckBox notificationsCheckBox;
    private CheckBox autoStartCheckBox;

    public SettingsWindow(SessionManager sessionManager, ConfigStore configStore) {
        this.sessionManager = sessionManager;
        this.configStore = configStore;
    }

    /**
     * Show (or bring to front) the settings window.
     * Must be called on the JavaFX Application Thread.
     */
    public void show() {
        Platform.runLater(() -> {
            if (stage == null) {
                stage = createStage();
            }
            refreshSessionList(sessionManager.getSessions());
            stage.show();
            stage.toFront();
        });
    }

    /**
     * Refresh session data in the settings window.
     */
    public void onSessionChange(Collection<SessionSnapshot> sessions) {
        if (stage != null && stage.isShowing()) {
            Platform.runLater(() -> refreshSessionList(sessions));
        }
    }

    private Stage createStage() {
        var tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        tabPane.getTabs().addAll(
                createSessionsTab(),
                createUsageTab(),
                createPreferencesTab(),
                createAboutTab()
        );

        var scene = new Scene(tabPane, 700, 500);
        var s = new Stage();
        s.setTitle("Copilot CLI Tray — Settings");
        s.setScene(scene);
        s.setOnCloseRequest(e -> {
            e.consume();
            s.hide(); // Hide, don't close (Platform.setImplicitExit(false))
        });
        return s;
    }

    // --- Sessions Tab ---

    private Tab createSessionsTab() {
        sessionListView = new ListView<>();
        sessionListView.setPrefWidth(250);
        sessionListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> showSessionDetail(selected));

        detailLabel = new Label("Select a session to view details.");
        detailLabel.setWrapText(true);
        detailLabel.setPadding(new Insets(10));

        var scrollDetail = new ScrollPane(detailLabel);
        scrollDetail.setFitToWidth(true);
        scrollDetail.setFitToHeight(true);

        var split = new SplitPane(sessionListView, scrollDetail);
        split.setDividerPositions(0.35);

        var tab = new Tab("Sessions", split);
        return tab;
    }

    private void refreshSessionList(Collection<SessionSnapshot> sessions) {
        if (sessionListView == null) return;
        var items = sessions.stream()
                .map(s -> s.name() + " [" + s.status() + "]")
                .sorted()
                .toList();
        sessionListView.setItems(FXCollections.observableArrayList(items));
    }

    private void showSessionDetail(String selectedLabel) {
        if (selectedLabel == null) {
            detailLabel.setText("Select a session to view details.");
            return;
        }
        // Find matching session
        var session = sessionManager.getSessions().stream()
                .filter(s -> selectedLabel.startsWith(s.name()))
                .findFirst()
                .orElse(null);

        if (session == null) {
            detailLabel.setText("Session not found.");
            return;
        }

        var sb = new StringBuilder();
        sb.append("ID: ").append(session.id()).append("\n");
        sb.append("Name: ").append(session.name()).append("\n");
        sb.append("Model: ").append(session.model()).append("\n");
        sb.append("Status: ").append(session.status()).append("\n");
        sb.append("Working Directory: ").append(session.workingDirectory()).append("\n");
        sb.append("Created: ").append(session.createdAt()).append("\n");
        sb.append("Last Activity: ").append(session.lastActivityAt()).append("\n\n");

        sb.append("--- Usage ---\n");
        sb.append("Tokens: ").append(session.usage().currentTokens())
                .append(" / ").append(session.usage().tokenLimit())
                .append(" (").append((int) session.usage().tokenUsagePercent()).append("%)\n");
        sb.append("Messages: ").append(session.usage().messagesCount()).append("\n\n");

        if (!session.subagents().isEmpty()) {
            sb.append("--- Subagents ---\n");
            for (var sub : session.subagents()) {
                sb.append("  ").append(sub.id())
                        .append(" [").append(sub.status()).append("] ")
                        .append(sub.description()).append("\n");
            }
        }

        if (session.pendingPermission()) {
            sb.append("\n⚠ Permission request pending\n");
        }

        detailLabel.setText(sb.toString());
    }

    // --- Usage Tab ---

    private Tab createUsageTab() {
        var content = new VBox(10);
        content.setPadding(new Insets(15));
        content.getChildren().add(new Label("Usage metrics are updated in real-time from active sessions."));

        // Placeholder — will be enriched in later phases
        var table = new TextArea();
        table.setEditable(false);
        table.setPrefRowCount(20);
        content.getChildren().add(table);
        VBox.setVgrow(table, Priority.ALWAYS);

        // Populate on tab select
        var tab = new Tab("Usage", content);
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) {
                var sb = new StringBuilder();
                sb.append(String.format("%-30s %-10s %-15s %-10s%n", "Session", "Status", "Tokens", "Usage%"));
                sb.append("-".repeat(70)).append("\n");
                for (var session : sessionManager.getSessions()) {
                    sb.append(String.format("%-30s %-10s %6d/%-6d %5.1f%%%n",
                            truncate(session.name(), 30),
                            session.status(),
                            session.usage().currentTokens(),
                            session.usage().tokenLimit(),
                            session.usage().tokenUsagePercent()));
                }
                table.setText(sb.toString());
            }
        });
        return tab;
    }

    // --- Preferences Tab ---

    private Tab createPreferencesTab() {
        var config = configStore.getConfig();

        var grid = new GridPane();
        grid.setPadding(new Insets(15));
        grid.setHgap(10);
        grid.setVgap(10);

        int row = 0;

        grid.add(new Label("Copilot CLI Path:"), 0, row);
        cliPathField = new TextField(config.getCliPath());
        cliPathField.setPromptText("Auto-detect (leave empty)");
        cliPathField.setPrefWidth(350);
        grid.add(cliPathField, 1, row++);

        grid.add(new Label("Poll Interval (seconds):"), 0, row);
        pollIntervalSpinner = new Spinner<>(1, 60, config.getPollIntervalSeconds());
        grid.add(pollIntervalSpinner, 1, row++);

        grid.add(new Label("Context Warning Threshold (%):"), 0, row);
        warningThresholdSpinner = new Spinner<>(50, 100, config.getContextWarningThreshold());
        grid.add(warningThresholdSpinner, 1, row++);

        grid.add(new Label("Enable Notifications:"), 0, row);
        notificationsCheckBox = new CheckBox();
        notificationsCheckBox.setSelected(config.isNotificationsEnabled());
        grid.add(notificationsCheckBox, 1, row++);

        grid.add(new Label("Auto-Start on Login:"), 0, row);
        autoStartCheckBox = new CheckBox();
        autoStartCheckBox.setSelected(config.isAutoStart());
        grid.add(autoStartCheckBox, 1, row++);

        var saveButton = new Button("Save");
        saveButton.setOnAction(e -> savePreferences());
        grid.add(saveButton, 1, row);

        return new Tab("Preferences", grid);
    }

    private void savePreferences() {
        var config = configStore.getConfig();
        config.setCliPath(cliPathField.getText().trim());
        config.setPollIntervalSeconds(pollIntervalSpinner.getValue());
        config.setContextWarningThreshold(warningThresholdSpinner.getValue());
        config.setNotificationsEnabled(notificationsCheckBox.isSelected());
        config.setAutoStart(autoStartCheckBox.isSelected());
        configStore.save();
    }

    // --- About Tab ---

    private Tab createAboutTab() {
        var content = new VBox(10);
        content.setPadding(new Insets(15));

        content.getChildren().addAll(
                new Label("Copilot CLI Tray"),
                new Label("Version: 1.0.0-SNAPSHOT"),
                new Label("License: MIT"),
                new Separator(),
                new Label("A cross-platform system tray application to track and manage"),
                new Label("GitHub Copilot CLI sessions and remote coding agents."),
                new Separator(),
                new Label("SDK: copilot-sdk-java"),
                new Label("JDK: " + System.getProperty("java.version")),
                new Label("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch")),
                new Separator(),
                createHyperlink("GitHub", "https://github.com/brunoborges/copilot-cli-tray")
        );

        return new Tab("About", content);
    }

    private Hyperlink createHyperlink(String text, String url) {
        var link = new Hyperlink(text + ": " + url);
        link.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            } catch (Exception ex) {
                // ignore
            }
        });
        return link;
    }

    private static String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }
}
