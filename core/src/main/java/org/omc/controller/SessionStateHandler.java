package org.omc.controller;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.omc.exception.InvalidSettingsException;
import org.omc.model.ApplicationState;
import org.omc.model.ConversionFile;
import org.omc.model.ConversionSettings;
import org.omc.model.FileListSortState;
import org.omc.model.SessionState;
import org.omc.model.WindowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Session-state persistence concern extracted from
 * {@link ApplicationWorkflowController} (audit: god-object decomposition).
 *
 * <p>
 * Owns saving/restoring the persisted application session: window state,
 * sort state, pending file list, and the current settings snapshot. The
 * controller passes its live session values at call time, so this handler
 * holds no session state of its own except the window-state capture written
 * by the UI thread.
 * </p>
 *
 * <p>
 * Requirements: REQ-003.1/REQ-005.1/REQ-005.2/REQ-005.3 (persist settings and
 * session state), REQ-FL-4.5 (preserve sort state across restarts).
 * </p>
 */
class SessionStateHandler {

    private static final Logger logger = LoggerFactory.getLogger(SessionStateHandler.class);

    private final StateManager stateManager;
    private final FileManager fileManager;
    private final SettingsManager settingsManager;

    /** Window state captured on the GTK thread for background persistence. */
    private volatile WindowState windowState = WindowState.defaultState();

    SessionStateHandler(StateManager stateManager, FileManager fileManager,
            SettingsManager settingsManager) {
        this.stateManager = Objects.requireNonNull(stateManager, "stateManager cannot be null");
        this.fileManager = Objects.requireNonNull(fileManager, "fileManager cannot be null");
        this.settingsManager = Objects.requireNonNull(settingsManager, "settingsManager cannot be null");
    }

    /** Returns the window geometry and desktop state restored at startup. */
    WindowState getWindowState() {
        return windowState;
    }

    /**
     * Captures window state on the GTK thread for subsequent background persistence.
     *
     * @param state current window geometry and desktop state
     */
    void updateWindowState(WindowState state) {
        windowState = Objects.requireNonNull(state, "state");
    }

    /**
     * Restores the persisted window state (startup path).
     *
     * @param state the loaded application state, or null
     */
    void restoreWindowState(ApplicationState state) {
        if (state != null && state.windowState() != null) {
            windowState = state.windowState();
        }
    }

    /**
     * Restores file list from session state.
     * Validates that files still exist and removes missing files.
     *
     * Requirement REQ-005.2: Restore session state
     */
    void restoreFileList(SessionState sessionState) {
        if (sessionState == null || sessionState.pendingFiles() == null) {
            logger.debug("No files to restore from session state");
            return;
        }

        List<ConversionFile> pendingFiles = sessionState.pendingFiles();
        if (pendingFiles.isEmpty()) {
            logger.debug("No pending files in session state");
            return;
        }

        logger.info("Restoring {} files from session state", pendingFiles.size());

        fileManager.restoreFiles(pendingFiles);
    }

    /**
     * Saves current application state to disk from the controller's live
     * session values.
     *
     * Requirement REQ-005.1, REQ-005.2, REQ-005.3: Persist application state
     * Requirement REQ-FL-4.5: Preserve file list sort state during shutdown
     *
     * @param recentFilePaths    recently opened file paths
     * @param lastInputDirectory last browsed input directory (nullable)
     * @param lastOutputDirectory last browsed output directory (nullable)
     * @param currentSettings    current conversion settings snapshot
     */
    void saveApplicationState(List<Path> recentFilePaths, Path lastInputDirectory,
            Path lastOutputDirectory, ConversionSettings currentSettings) {
        try {
            // Build session state from current file list
            List<ConversionFile> pendingFiles = fileManager.getFiles();

            SessionState sessionState = new SessionState(
                    recentFilePaths,
                    lastInputDirectory,
                    lastOutputDirectory,
                    pendingFiles,
                    null // lastUsedPreset
            );

            // Merge into the current state atomically: the sort state read
            // here must not race (and silently discard or be discarded by) a
            // concurrent save such as a user sort change on the GTK thread.
            // Requirement REQ-FL-4.5: Preserve current sort state
            stateManager.updateState(currentState -> new ApplicationState(
                    windowState,
                    sessionState,
                    currentSettings,
                    currentState.fileListSortState(),
                    ApplicationState.CURRENT_STATE_VERSION,
                    System.currentTimeMillis()));

            logger.debug("Application state saved successfully");

        } catch (Exception e) {
            logger.error("Failed to save application state", e);
        }
    }

    /**
     * Saves current settings to disk.
     *
     * Requirement REQ-003.1, REQ-005.3: Persist settings
     *
     * @param currentSettings the settings snapshot to persist
     * @param onSaved         invoked after a successful save (clears the
     *                        unsaved-changes marker in the controller)
     */
    void saveCurrentSettings(ConversionSettings currentSettings, Runnable onSaved) {
        try {
            if (currentSettings != null) {
                settingsManager.saveSettings(currentSettings);
                onSaved.run();
                logger.debug("Settings saved successfully");
            }
        } catch (InvalidSettingsException | java.io.IOException e) {
            logger.error("Failed to save settings", e);
        }
    }

    /**
     * Saves file list sort state to application state.
     * Task 80: REQ-FL-4.5 - Persist sort state when user changes column sorting.
     *
     * @param sortState The new sort state to save
     */
    void saveSortState(FileListSortState sortState) {
        try {
            logger.debug("Saving sort state: {}", sortState);

            // Read-modify-write must be atomic: a concurrent save (e.g.
            // batch-completion saveApplicationState on a worker thread) must
            // not silently discard this sort update or vice versa
            stateManager.updateState(currentState -> currentState.withFileListSortState(sortState));

            logger.debug("Sort state saved successfully");
        } catch (Exception e) {
            logger.error("Failed to save sort state", e);
        }
    }

    /**
     * Gets the saved file list sort state from application state.
     * Task 82: REQ-FL-4.5 - Retrieve saved sort state for restoration on startup.
     *
     * @return The saved sort state, or FileListSortState.unsorted() if none saved
     */
    FileListSortState getSavedSortState() {
        try {
            ApplicationState currentState = stateManager.getCurrentState();
            FileListSortState sortState = currentState.fileListSortState();
            return sortState != null ? sortState : FileListSortState.unsorted();
        } catch (Exception e) {
            logger.error("Failed to retrieve saved sort state", e);
            return FileListSortState.unsorted();
        }
    }
}
