// filepath: src/main/java/org/omc/controller/StateManager.java

package org.omc.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.omc.core.ConfigurationManager;
import org.omc.model.ApplicationState;
import org.omc.model.SessionState;
import org.omc.model.WindowState;
import org.omc.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages application state persistence and retrieval.
 * Handles window state, session state, and overall application state.
 * 
 * Implements atomic writes to prevent corruption and provides automatic
 * backup and recovery for corrupted state files.
 * 
 * Requirements: REQ-005.1, REQ-005.2, REQ-005.3
 */
public class StateManager {
    private static final Logger logger = LoggerFactory.getLogger(StateManager.class);

    private static final String STATE_FILE = "state.json";
    private static final String BACKUP_SUFFIX = ".backup";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String CURRENT_VERSION = "1.0.0";

    private final ConfigurationManager configurationManager;
    private final AtomicReference<ApplicationState> currentState;

    /**
     * Creates a new StateManager.
     *
     * @param configurationManager Configuration manager for paths
     */
    public StateManager(ConfigurationManager configurationManager) {
        this.configurationManager = Objects.requireNonNull(configurationManager, "configurationManager cannot be null");
        this.currentState = new AtomicReference<>(ApplicationState.defaultState());
        logger.debug("StateManager initialized");
    }

    /**
     * Loads application state from disk.
     * If the state file doesn't exist or is corrupted, returns default state.
     * Corrupted files are backed up with timestamp.
     * 
     * Requirement REQ-005.3: State persistence
     *
     * @return Loaded or default state
     */
    public ApplicationState loadState() {
        Path statePath = getStatePath();
        logger.info("Loading application state from: {}", statePath);

        // If state file doesn't exist, use defaults
        if (!Files.exists(statePath)) {
            logger.info("State file does not exist, using defaults");
            ApplicationState defaults = ApplicationState.defaultState();
            currentState.set(defaults);
            return defaults;
        }

        try {
            // Read state from file
            ApplicationState state = JsonUtils.readJsonFile(
                    statePath.toFile(),
                    ApplicationState.class);

            // Validate loaded state
            if (state == null) {
                logger.warn("State file is empty or null, using defaults");
                backupCorruptedState(statePath);
                ApplicationState defaults = ApplicationState.defaultState();
                currentState.set(defaults);
                return defaults;
            }

            // Validate and clean state
            ApplicationState validatedState = state.validated();

            // Check if state needs migration
            if (state.needsMigration(CURRENT_VERSION)) {
                logger.info("State requires migration from version {} to {}",
                        state.version(), CURRENT_VERSION);
                validatedState = migrateState(validatedState);
            }

            currentState.set(validatedState);
            logger.info("Application state loaded successfully");
            return validatedState;

        } catch (IOException e) {
            logger.error("Error reading state file: {}", statePath, e);
            backupCorruptedState(statePath);
            ApplicationState defaults = ApplicationState.defaultState();
            currentState.set(defaults);
            return defaults;
        }
    }

    /**
     * Saves application state to persistent storage (state.json).
     * Writes to temporary file first, then renames to prevent corruption.
     * Method is synchronized to prevent concurrent writes.
     * 
     * Requirement REQ-005.3: State persistence with atomic writes
     *
     * @param state Application state to save
     * @throws IOException if save operation fails
     */
    public synchronized void saveState(ApplicationState state) throws IOException {
        Objects.requireNonNull(state, "state cannot be null");
        logger.debug("Saving application state");

        Path statePath = getStatePath();
        Path tempPath = Path.of(statePath.toString() + TEMP_SUFFIX);

        try {
            // Ensure config directory exists
            Files.createDirectories(statePath.getParent());

            // Update version
            ApplicationState stateWithVersion = state.withVersion(CURRENT_VERSION);

            // Write to temporary file first
            JsonUtils.writeJsonFile(stateWithVersion, tempPath.toFile());

            // Atomic rename to target file
            Files.move(tempPath, statePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            // Update current state
            currentState.set(stateWithVersion);

            // log state fileListSortState object for debugging
            logger.debug("State fileListSortState object: {}", stateWithVersion.toString());

            logger.info("Application state saved successfully to: {}", statePath);

        } catch (IOException e) {
            logger.error("Failed to save application state to: {}", statePath, e);

            // Clean up temp file if it exists
            if (Files.exists(tempPath)) {
                try {
                    Files.delete(tempPath);
                } catch (IOException cleanupEx) {
                    logger.warn("Failed to delete temp state file: {}", tempPath, cleanupEx);
                }
            }

            throw e;
        }
    }

    /**
     * Loads window state from current application state.
     * 
     * Requirement REQ-005.1: Window state restoration
     *
     * @return Current window state
     */
    public WindowState loadWindowState() {
        ApplicationState state = currentState.get();
        if (state == null || state.windowState() == null) {
            logger.debug("No window state available, using default");
            return WindowState.defaultState();
        }

        WindowState windowState = state.windowState();
        if (!windowState.isValid()) {
            logger.warn("Window state is invalid, using default");
            return WindowState.defaultState();
        }

        logger.debug("Loaded window state: {}x{} at ({}, {})",
                windowState.width(), windowState.height(), windowState.x(), windowState.y());
        return windowState;
    }

    /**
     * Saves window state.
     * Updates current state and persists to disk.
     * 
     * Requirement REQ-005.1: Window state persistence
     *
     * @param windowState Window state to save
     * @throws IOException if save operation fails
     */
    public void saveWindowState(WindowState windowState) throws IOException {
        Objects.requireNonNull(windowState, "windowState cannot be null");
        logger.debug("Saving window state");

        if (!windowState.isValid()) {
            logger.warn("Window state is invalid, not saving");
            throw new IllegalArgumentException("Invalid window state");
        }

        ApplicationState state = currentState.get();
        ApplicationState updatedState = state.withWindowState(windowState);
        saveState(updatedState);

        logger.info("Window state saved successfully");
    }

    /**
     * Loads session state from current application state.
     * 
     * Requirement REQ-005.2: Session state restoration
     *
     * @return Current session state
     */
    public SessionState loadSessionState() {
        ApplicationState state = currentState.get();
        if (state == null || state.sessionState() == null) {
            logger.debug("No session state available, using empty");
            return SessionState.empty();
        }

        SessionState sessionState = state.sessionState().validated();
        logger.debug("Loaded session state with {} recent files and {} pending files",
                sessionState.recentFilePaths().size(), sessionState.pendingFiles().size());
        return sessionState;
    }

    /**
     * Saves session state.
     * Updates current state and persists to disk.
     * 
     * Requirement REQ-005.2: Session state persistence
     *
     * @param sessionState Session state to save
     * @throws IOException if save operation fails
     */
    public void saveSessionState(SessionState sessionState) throws IOException {
        Objects.requireNonNull(sessionState, "sessionState cannot be null");
        logger.debug("Saving session state");

        ApplicationState state = currentState.get();
        ApplicationState updatedState = state.withSessionState(sessionState);
        saveState(updatedState);

        logger.info("Session state saved successfully");
    }

    /**
     * Gets the current application state.
     *
     * @return Current application state
     */
    public ApplicationState getCurrentState() {
        return currentState.get();
    }

    /**
     * Resets state to defaults.
     * Creates default state and saves it to disk.
     *
     * @throws IOException if save operation fails
     */
    public void resetToDefaults() throws IOException {
        logger.info("Resetting state to defaults");

        ApplicationState defaults = ApplicationState.defaultState();
        saveState(defaults);

        logger.info("State reset to defaults successfully");
    }

    /**
     * Migrates state from old version to current version.
     * 
     * Requirement REQ-005.3: State version migration support
     *
     * @param state State to migrate
     * @return Migrated state
     */
    private ApplicationState migrateState(ApplicationState state) {
        // For now, just update version
        // In future, add version-specific migration logic here
        logger.info("Migrating state to version {}", CURRENT_VERSION);
        return state.withVersion(CURRENT_VERSION);
    }

    /**
     * Backs up a corrupted state file.
     * Renames the file with .backup suffix and timestamp.
     * 
     * Requirement REQ-005.3: Corrupted file handling
     *
     * @param statePath Path to corrupted state file
     */
    private void backupCorruptedState(Path statePath) {
        try {
            if (Files.exists(statePath)) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                Path backupPath = Path.of(statePath.toString() + BACKUP_SUFFIX + "_" + timestamp);

                Files.move(statePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                logger.warn("Corrupted state file backed up to: {}", backupPath);
            }
        } catch (IOException e) {
            logger.error("Failed to backup corrupted state file: {}", statePath, e);
        }
    }

    /**
     * Gets the state file path.
     *
     * @return State file path
     */
    private Path getStatePath() {
        return configurationManager.getConfigDirectory().resolve(STATE_FILE);
    }

    /**
     * Checks if state file exists.
     *
     * @return true if state file exists
     */
    public boolean stateFileExists() {
        return Files.exists(getStatePath());
    }

    /**
     * Gets the state file path for external use.
     *
     * @return State file path
     */
    public Path getStateFilePath() {
        return getStatePath();
    }
}
