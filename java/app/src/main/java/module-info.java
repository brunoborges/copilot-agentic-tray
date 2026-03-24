module com.github.copilot.tray {
    requires com.github.copilot.sdk.java;

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;

    requires java.desktop;
    requires java.prefs;

    requires org.slf4j;

    opens com.github.copilot.tray.ui to javafx.fxml, javafx.graphics;
    opens com.github.copilot.tray.config to com.fasterxml.jackson.databind;
    opens com.github.copilot.tray.session to com.fasterxml.jackson.databind;
    opens com.github.copilot.tray to javafx.graphics;

    exports com.github.copilot.tray;
    exports com.github.copilot.tray.session;
    exports com.github.copilot.tray.sdk;
    exports com.github.copilot.tray.tray;
    exports com.github.copilot.tray.config;
    exports com.github.copilot.tray.notify;
    exports com.github.copilot.tray.ui;
}
