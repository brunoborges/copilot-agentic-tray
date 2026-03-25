package com.github.copilot.tray.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads/writes AppConfig from/to the platform-specific config directory.
 */
public class ConfigStore {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigStore.class);
    private static final String APP_DIR_NAME = "copilot-agentic-tray";
    private static final String CONFIG_FILE = "config.json";

    private final ObjectMapper mapper;
    private final Path configDir;
    private AppConfig config;

    public ConfigStore() {
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.configDir = resolveConfigDir();
        this.config = new AppConfig();
    }

    /**
     * Load config from disk. If the file doesn't exist, use defaults.
     */
    public AppConfig load() {
        var configFile = configDir.resolve(CONFIG_FILE);
        if (Files.exists(configFile)) {
            try {
                config = mapper.readValue(configFile.toFile(), AppConfig.class);
                LOG.info("Loaded config from {}", configFile);
            } catch (IOException e) {
                LOG.warn("Failed to read config from {}, using defaults", configFile, e);
                config = new AppConfig();
            }
        } else {
            LOG.info("No config file at {}, using defaults", configFile);
        }
        return config;
    }

    /**
     * Save current config to disk.
     */
    public void save() {
        try {
            Files.createDirectories(configDir);
            mapper.writeValue(configDir.resolve(CONFIG_FILE).toFile(), config);
            LOG.info("Saved config to {}", configDir.resolve(CONFIG_FILE));
        } catch (IOException e) {
            LOG.error("Failed to save config", e);
        }
    }

    public AppConfig getConfig() {
        return config;
    }

    public Path getConfigDir() {
        return configDir;
    }

    private static Path resolveConfigDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return Path.of(System.getProperty("user.home"),
                    "Library", "Application Support", APP_DIR_NAME);
        } else if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                return Path.of(appData, APP_DIR_NAME);
            }
            return Path.of(System.getProperty("user.home"), "AppData", "Roaming", APP_DIR_NAME);
        } else {
            // Linux / other Unix
            String xdgConfig = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfig != null && !xdgConfig.isBlank()) {
                return Path.of(xdgConfig, APP_DIR_NAME);
            }
            return Path.of(System.getProperty("user.home"), ".config", APP_DIR_NAME);
        }
    }
}
