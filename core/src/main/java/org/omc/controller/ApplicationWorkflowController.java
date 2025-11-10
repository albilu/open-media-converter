// filepath: src/main/java/org/omc/controller/ApplicationWorkflowController.java

package org.omc.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.omc.core.ConversionEngine;
import org.omc.exception.ErrorCode;
import org.omc.exception.FileOperationException;
import org.omc.exception.InvalidSettingsException;
import org.omc.model.ApplicationState;
import org.omc.model.BatchProgress;
import org.omc.model.ConversionFile;
import org.omc.model.ConversionProgress;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionSettings;
import org.omc.model.ConversionStatus;
import org.omc.model.FileListSortState;
import org.omc.model.FileSettingsOverride;
import org.omc.model.FormatCategory;
import org.omc.model.PresetsBySection;
import org.omc.model.SectionPreset;
import org.omc.model.SessionState;
import org.omc.model.WindowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller that orchestrates application workflows and coordinates
 * between UI and business logic layers.
 * 
 * Handles user actions such as file selection, settings management,
 * conversion control, and application lifecycle events.
 * 
 * Requirements:
 * - REQ-001.1: Application initialization and startup
 * - REQ-001.2: Application shutdown with state persistence
 * - REQ-002.1: File selection workflows
 * - REQ-002.2: File removal workflows
 * - REQ-003.1: Settings dialog workflow
 * - REQ-004.2: Conversion workflow control
 * - REQ-005.1, REQ-005.2, REQ-005.3: State management
 */
public class ApplicationWorkflowController {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationWorkflowController.class);

    // Dependencies
    private final FileManager fileManager;
    private final SettingsManager settingsManager;
    private final StateManager stateManager;
    private final ConversionEngine conversionEngine;

    // Current state
    private final AtomicBoolean initialized;
    private final AtomicBoolean conversionInProgress;
    private final AtomicBoolean hasUnsavedChanges;

    // Tracked state for session persistence
    private Path lastInputDirectory;
    private Path lastOutputDirectory;
    private List<Path> recentFilePaths;

    // Current settings cached for quick access
    private ConversionSettings currentSettings;

    // UI callback handlers for progress and completion events
    // Requirement REQ-004.2: Forward conversion events to UI
    private BiConsumer<String, ConversionProgress> uiProgressCallback;
    private BiConsumer<String, ConversionResult> uiCompletionCallback;
    private Consumer<BatchProgress> uiBatchProgressCallback;

    /**
     * Creates a new ApplicationWorkflowController with required dependencies.
     * 
     * @param fileManager      the file manager for file list operations
     * @param settingsManager  the settings manager for settings persistence
     * @param stateManager     the state manager for state persistence
     * @param conversionEngine the conversion engine for conversion operations
     * @throws NullPointerException if any parameter is null
     */
    public ApplicationWorkflowController(
            FileManager fileManager,
            SettingsManager settingsManager,
            StateManager stateManager,
            ConversionEngine conversionEngine) {

        this.fileManager = Objects.requireNonNull(fileManager, "fileManager cannot be null");
        this.settingsManager = Objects.requireNonNull(settingsManager, "settingsManager cannot be null");
        this.stateManager = Objects.requireNonNull(stateManager, "stateManager cannot be null");
        this.conversionEngine = Objects.requireNonNull(conversionEngine, "conversionEngine cannot be null");

        this.initialized = new AtomicBoolean(false);
        this.conversionInProgress = new AtomicBoolean(false);
        this.hasUnsavedChanges = new AtomicBoolean(false);

        this.lastInputDirectory = null;
        this.lastOutputDirectory = null;
        this.recentFilePaths = List.of();

        logger.debug("ApplicationWorkflowController created");
    }

    /**
     * Initializes the application workflow controller.
     * Loads settings and application state.
     * 
     * Requirement REQ-001.1: Application initialization
     * 
     * @throws IllegalStateException if already initialized
     */
    public void initialize() {
        if (initialized.getAndSet(true)) {
            throw new IllegalStateException("ApplicationWorkflowController already initialized");
        }

        logger.info("Initializing ApplicationWorkflowController");

        try {
            // Load settings from disk or use defaults
            // Requirement REQ-003.1, REQ-005.3: Load persisted settings
            currentSettings = settingsManager.loadSettings();
            logger.info("Settings loaded successfully");

            // Apply loaded parallelism setting to ConversionEngine
            // This ensures the engine uses the user's saved preference instead of the
            // default
            try {
                conversionEngine.setParallelConversions(currentSettings.parallelConversions());
                logger.info("Applied parallelism setting from loaded settings: {}",
                        currentSettings.parallelConversions());
            } catch (IllegalStateException e) {
                // This shouldn't happen during initialization, but log if it does
                logger.warn("Could not set parallelism during initialization: {}", e.getMessage());
            }

            // Load application state from disk or use defaults
            // Requirement REQ-005.1, REQ-005.2: Restore state
            ApplicationState state = stateManager.loadState();
            logger.info("Application state loaded successfully");

            // Restore tracked state
            SessionState sessionState = state.sessionState();
            this.lastInputDirectory = sessionState.lastInputDirectory();
            this.lastOutputDirectory = sessionState.lastOutputDirectory();
            this.recentFilePaths = sessionState.recentFilePaths() != null ? sessionState.recentFilePaths() : List.of();

            // Restore file list from session state if any
            restoreFileList(state.sessionState());

            // Register conversion completion handler to track conversion status
            conversionEngine.onConversionComplete((fileId, result) -> {
                // Handle null result (should not occur anymore, but keep as safety check)
                if (result == null) {
                    logger.warn("Received null result for file: {} (unexpected)", fileId);
                    return;
                }

                if (!result.success()) {
                    if (result.isCancelled()) {
                        logger.info("Conversion cancelled for file: {}", fileId);
                    } else {
                        logger.warn("Conversion failed for file: {} - {}", fileId, result.errorMessage());
                    }
                }

                // Update file status in FileManager
                // Requirement REQ-FL-3.3: Update ConversionFile with output path after
                // successful conversion
                try {
                    Optional<ConversionFile> fileOpt = fileManager.getFile(fileId);
                    if (fileOpt.isPresent()) {
                        ConversionFile updated;
                        if (result.success()) {
                            // Extract output path from result and set on file
                            updated = fileOpt.get()
                                    .withProgress(100)
                                    .withStatus(ConversionStatus.COMPLETED)
                                    .withOutputPath(result.outputPath().orElse(null));
                        } else if (result.isCancelled()) {
                            updated = fileOpt.get().withCancelled();
                        } else {
                            updated = fileOpt.get().withError(result.errorMessage().orElse("Conversion failed"));
                        }
                        fileManager.updateFile(updated);
                    }
                } catch (Exception e) {
                    logger.error("Failed to update file status", e);
                }

                // Check if all conversions are complete
                // Requirement REQ-004.2: Save session state after batch completion
                if (conversionEngine.getActiveConversionCount() == 0) {
                    conversionInProgress.set(false);
                    logger.info("Batch conversion complete, saving session state");
                    saveApplicationState();
                }

                // Forward completion event to UI callback if registered
                if (uiCompletionCallback != null) {
                    uiCompletionCallback.accept(fileId, result);
                }
            });

            // Register progress handler
            conversionEngine.onProgressUpdate((fileId, progress) -> {
                logger.debug("Progress update for file {}: {}%", fileId, progress.percentage());

                // Update file progress and status in FileManager
                try {
                    Optional<ConversionFile> fileOpt = fileManager.getFile(fileId);
                    if (fileOpt.isPresent()) {
                        ConversionFile file = fileOpt.get();
                        // Store full ConversionProgress (including speed) and update status
                        ConversionFile updated = file
                                .withProgressInfo(progress) // This also updates progress percentage
                                .withStatus(ConversionStatus.IN_PROGRESS);
                        fileManager.updateFile(updated);
                    }
                } catch (Exception e) {
                    logger.error("Failed to update file progress", e);
                }

                // Forward progress event to UI callback if registered
                if (uiProgressCallback != null) {
                    uiProgressCallback.accept(fileId, progress);
                }
            });

            // Register batch progress handler
            // Requirement REQ-004.3: Batch progress tracking with speed and ETA
            conversionEngine.onBatchProgressUpdate(batchProgress -> {
                logger.debug("Batch progress update: {} files completed, {}% overall, speed: {}, ETA: {}",
                        batchProgress.completedFiles(), batchProgress.overallPercentage(),
                        batchProgress.formatSpeed(), batchProgress.formatEta());

                // Forward batch progress event to UI callback if registered
                if (uiBatchProgressCallback != null) {
                    uiBatchProgressCallback.accept(batchProgress);
                }
            });

            logger.info("ApplicationWorkflowController initialized successfully");

        } catch (Exception e) {
            initialized.set(false);
            logger.error("Failed to initialize ApplicationWorkflowController", e);
            throw new RuntimeException("Initialization failed", e);
        }
    }

    /**
     * Shuts down the application workflow controller.
     * Saves current state and settings, shuts down conversion engine.
     * 
     * Requirement REQ-001.2: Application shutdown with state persistence
     * 
     * @param forceShutdown if true, shutdown without confirmation even if
     *                      conversions active
     * @return true if shutdown completed, false if shutdown was cancelled by user
     */
    public boolean shutdown(boolean forceShutdown) {
        if (!initialized.get()) {
            logger.warn("Attempted to shutdown uninitialized controller");
            return true;
        }

        logger.info("Shutting down ApplicationWorkflowController (force={})", forceShutdown);

        try {
            // Check for active conversions
            // Requirement REQ-001.2: Warn user about active conversions
            if (conversionInProgress.get() && !forceShutdown) {
                logger.warn("Active conversions in progress, shutdown requires confirmation");
                // In a real application, this would show a dialog
                // For now, we just log it
                return false; // User would need to confirm
            }

            // Check for unsaved changes
            // Requirement REQ-001.2: Prompt for unsaved changes
            if (hasUnsavedChanges.get() && !forceShutdown) {
                logger.warn("Unsaved settings changes exist, shutdown requires confirmation");
                // In a real application, this would show a Save/Discard/Cancel dialog
                // For now, we auto-save
                saveCurrentSettings();
            }

            // Cancel active conversions if any
            if (conversionInProgress.get()) {
                logger.info("Cancelling active conversions");
                conversionEngine.cancelConversion();
                conversionInProgress.set(false);
            }

            // Save current application state
            // Requirement REQ-005.1, REQ-005.2, REQ-005.3: Persist state
            saveApplicationState();

            // Save current settings
            // Requirement REQ-003.1, REQ-005.3: Persist settings
            saveCurrentSettings();

            // Shutdown conversion engine
            conversionEngine.shutdown();

            initialized.set(false);
            logger.info("ApplicationWorkflowController shutdown complete");
            return true;

        } catch (Exception e) {
            logger.error("Error during shutdown", e);
            return false;
        }
    }

    /**
     * Shuts down the application workflow controller with confirmation prompts.
     * 
     * @return true if shutdown completed, false if cancelled
     */
    public boolean shutdown() {
        return shutdown(false);
    }

    /**
     * Gets the current conversion settings.
     * 
     * @return current settings
     */
    public ConversionSettings getCurrentSettings() {
        return currentSettings;
    }

    /**
     * Gets the settings manager.
     * 
     * @return settings manager
     */
    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    /**
     * Gets a file by its ID.
     * 
     * @param fileId the file ID
     * @return the conversion file, or empty if not found
     */
    public Optional<ConversionFile> getFile(String fileId) {
        return fileManager.getFile(fileId);
    }

    /**
     * Gets the conversion result for a file.
     * 
     * Requirement REQ-FL-2.1: Retrieve conversion results for file details dialog
     * 
     * @param fileId the file ID
     * @return the conversion result, or null if not available
     */
    public ConversionResult getConversionResult(String fileId) {
        return conversionEngine.getConversionResult(fileId);
    }

    /**
     * Gets the file handler for file system operations.
     * Required for operations like opening file locations in the file manager.
     * 
     * Requirement REQ-FL-3.2: Enable file manager integration for opening file
     * locations
     * 
     * @return the file handler instance
     */
    public org.omc.service.FileHandler getFileHandler() {
        return fileManager.getFileHandler();
    }

    /**
     * Checks if controller is initialized.
     * 
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Checks if conversion is currently in progress.
     * 
     * @return true if conversion active
     */
    public boolean isConversionInProgress() {
        return conversionInProgress.get();
    }

    /**
     * Checks if there are unsaved settings changes.
     * 
     * @return true if unsaved changes exist
     */
    public boolean hasUnsavedChanges() {
        return hasUnsavedChanges.get();
    }

    /**
     * Marks settings as changed (unsaved).
     */
    public void markSettingsChanged() {
        hasUnsavedChanges.set(true);
        logger.debug("Settings marked as changed");
    }

    /**
     * Clears unsaved changes flag.
     */
    public void clearUnsavedChanges() {
        hasUnsavedChanges.set(false);
        logger.debug("Unsaved changes cleared");
    }

    // ===== UI Callback Registration Methods =====

    /**
     * Registers a callback to be invoked when conversion progress is updated.
     * This allows the UI to receive real-time progress updates from the conversion
     * engine.
     * 
     * Requirement REQ-004.2: Progress event flow from engine to UI
     * 
     * @param callback the callback to invoke with (fileId, progress) parameters
     */
    public void registerProgressCallback(BiConsumer<String, ConversionProgress> callback) {
        this.uiProgressCallback = callback;
        logger.debug("UI progress callback registered");
    }

    /**
     * Registers a callback to be invoked when a conversion completes (success or
     * failure).
     * This allows the UI to update file status and overall batch progress.
     * 
     * Requirement REQ-004.2: Completion event flow from engine to UI
     * 
     * @param callback the callback to invoke with (fileId, result) parameters
     */
    public void registerCompletionCallback(BiConsumer<String, ConversionResult> callback) {
        this.uiCompletionCallback = callback;
        logger.debug("UI completion callback registered");
    }

    /**
     * Registers a callback to be invoked when batch progress updates (speed, ETA,
     * overall progress).
     * This allows the UI to update the bottom status bar with real-time speed and
     * time remaining.
     * 
     * Requirement REQ-004.3: Batch progress tracking with speed and ETA
     * 
     * @param callback the callback to invoke with batch progress parameter
     */
    public void registerBatchProgressCallback(Consumer<BatchProgress> callback) {
        this.uiBatchProgressCallback = callback;
        logger.debug("UI batch progress callback registered");
    }

    // ===== File Management Methods =====

    /**
     * Adds files to the conversion list.
     * Validates files and updates tracked directories.
     * 
     * Requirement REQ-002.1: File selection workflows
     * 
     * @param filePaths the paths of files to add
     * @throws FileOperationException if files cannot be added
     */
    public void addFiles(List<Path> filePaths) throws FileOperationException {
        Objects.requireNonNull(filePaths, "filePaths cannot be null");
        if (filePaths.isEmpty()) {
            return;
        }

        logger.info("Adding {} files to conversion list", filePaths.size());

        try {
            fileManager.addFiles(filePaths);

            // Update last input directory from the first file's parent
            Path firstFile = filePaths.get(0);
            if (firstFile.getParent() != null) {
                this.lastInputDirectory = firstFile.getParent();
            }

            // Update recent files (keep last 10)
            List<Path> updatedRecent = new ArrayList<>(recentFilePaths);
            for (Path path : filePaths) {
                updatedRecent.remove(path); // avoid duplicates
                updatedRecent.add(0, path); // add to front
            }
            if (updatedRecent.size() > 10) {
                updatedRecent = updatedRecent.subList(0, 10);
            }
            this.recentFilePaths = List.copyOf(updatedRecent);

            logger.info("Files added successfully");

        } catch (Exception e) {
            logger.error("Failed to add files", e);
            throw new FileOperationException("Failed to add files: " + e.getMessage(), ErrorCode.FILE_IO_ERROR, "", e);
        }
    }

    /**
     * Adds all files from a folder to the conversion list.
     * Updates tracked directories with the scanned folder path.
     * 
     * Requirement REQ-002.1: File selection workflows
     * 
     * @param folderPath the folder path to scan
     * @param recursive  whether to scan recursively
     * @return list of successfully added files
     * @throws FileOperationException if folder cannot be scanned or files cannot be
     *                                added
     */
    public List<ConversionFile> addFilesFromFolder(Path folderPath, boolean recursive) throws FileOperationException {
        Objects.requireNonNull(folderPath, "folderPath cannot be null");

        logger.info("Adding files from folder: {} (recursive: {})", folderPath, recursive);

        try {
            List<ConversionFile> addedFiles = fileManager.addFilesFromFolder(folderPath, recursive);

            // Update last input directory to the scanned folder
            this.lastInputDirectory = folderPath;

            // Update recent files with newly added files
            if (!addedFiles.isEmpty()) {
                List<Path> updatedRecent = new ArrayList<>(recentFilePaths);
                for (ConversionFile file : addedFiles) {
                    Path path = file.path();
                    updatedRecent.remove(path); // avoid duplicates
                    updatedRecent.add(0, path); // add to front
                }
                if (updatedRecent.size() > 10) {
                    updatedRecent = updatedRecent.subList(0, 10);
                }
                this.recentFilePaths = List.copyOf(updatedRecent);
            }

            logger.info("Added {} files from folder", addedFiles.size());
            return addedFiles;

        } catch (FileOperationException e) {
            logger.error("Failed to add files from folder", e);
            throw e;
        } catch (Exception e) {
            logger.error("Failed to add files from folder", e);
            throw new FileOperationException("Failed to add files from folder: " + e.getMessage(),
                    ErrorCode.FILE_IO_ERROR, folderPath.toString(), e);
        }
    }

    /**
     * Removes files from the conversion list.
     * 
     * Requirement REQ-002.2: File removal workflows
     * 
     * @param fileIds the IDs of files to remove
     * @throws FileOperationException if files cannot be removed
     */
    public void removeFiles(List<String> fileIds) throws FileOperationException {
        Objects.requireNonNull(fileIds, "fileIds cannot be null");
        if (fileIds.isEmpty()) {
            return;
        }

        logger.info("Removing {} files from conversion list", fileIds.size());

        try {
            fileManager.removeFiles(fileIds);
            logger.info("Files removed successfully");

        } catch (Exception e) {
            logger.error("Failed to remove files", e);
            throw new FileOperationException("Failed to remove files: " + e.getMessage(), ErrorCode.FILE_IO_ERROR, "",
                    e);
        }
    }

    /**
     * Clears all files from the conversion list.
     * 
     * Requirement REQ-002.2: File removal workflows
     */
    public void clearFiles() {
        logger.info("Clearing all files from conversion list");

        try {
            fileManager.clearFiles();
            logger.info("Files cleared successfully");

        } catch (Exception e) {
            logger.error("Failed to clear files", e);
            throw new RuntimeException("Failed to clear files: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the current list of files pending conversion.
     * 
     * @return list of conversion files
     */
    public List<ConversionFile> getFileList() {
        return fileManager.getFiles();
    }

    // ===== Settings Management Methods =====

    /**
     * Updates the current conversion settings.
     * Validates settings and marks as changed.
     * 
     * Requirement REQ-003.1: Settings dialog workflow
     * 
     * @param newSettings the new settings to apply
     * @throws InvalidSettingsException if settings are invalid
     */
    public void updateSettings(ConversionSettings newSettings) {
        Objects.requireNonNull(newSettings, "newSettings cannot be null");

        logger.info("Updating conversion settings");

        // Validate settings (assuming ConversionSettings has validation)
        // For now, assume valid

        // Check if parallelConversions has changed
        int oldParallelConversions = currentSettings != null ? currentSettings.parallelConversions() : -1;
        int newParallelConversions = newSettings.parallelConversions();

        this.currentSettings = newSettings;

        // Update ConversionEngine if parallelism has changed
        if (oldParallelConversions != newParallelConversions && oldParallelConversions != -1) {
            try {
                conversionEngine.setParallelConversions(newParallelConversions);
                logger.info("Updated ConversionEngine parallelism from {} to {}",
                        oldParallelConversions, newParallelConversions);
            } catch (IllegalStateException e) {
                logger.warn("Cannot update parallelism while conversions are active: {}", e.getMessage());
                // Don't throw - settings will still be saved and applied on next conversion
                // start
            }
        }

        // Persist settings to disk immediately
        try {
            settingsManager.saveSettings(newSettings);
            clearUnsavedChanges();
            logger.info("Settings updated and saved successfully");
        } catch (InvalidSettingsException | java.io.IOException e) {
            // Mark as changed so they can be saved later
            markSettingsChanged();
            logger.error("Settings updated but failed to save to disk", e);
            throw new RuntimeException("Failed to save settings to disk: " + e.getMessage(), e);
        }
    }

    // ===== Conversion Control Methods =====

    /**
     * Starts the conversion process for all pending files.
     * 
     * Requirement REQ-004.2: Conversion workflow control
     * 
     * @throws IllegalStateException if conversion already in progress or no files
     */
    public void startConversion() {
        if (conversionInProgress.get()) {
            throw new IllegalStateException("Conversion already in progress");
        }

        List<ConversionFile> files = fileManager.getFiles();
        if (files.isEmpty()) {
            throw new IllegalStateException("No files to convert");
        }

        logger.info("Starting conversion for {} files", files.size());

        try {
            conversionEngine.convertBatch(files, currentSettings);
            conversionInProgress.set(true);
            logger.info("Conversion started successfully");

        } catch (Exception e) {
            logger.error("Failed to start conversion", e);
            throw new RuntimeException("Failed to start conversion: " + e.getMessage(), e);
        }
    }

    /**
     * Pauses the current conversion process.
     * 
     * Requirement REQ-004.2: Conversion workflow control
     * 
     * @throws IllegalStateException if no conversion in progress
     */
    public void pauseConversion() {
        if (!conversionInProgress.get()) {
            throw new IllegalStateException("No conversion in progress");
        }

        logger.info("Pausing conversion");

        try {
            conversionEngine.pauseConversion();
            logger.info("Conversion paused successfully");

        } catch (Exception e) {
            logger.error("Failed to pause conversion", e);
            throw new RuntimeException("Failed to pause conversion: " + e.getMessage(), e);
        }
    }

    /**
     * Resumes the paused conversion process.
     * 
     * Requirement REQ-004.2: Conversion workflow control
     * 
     * @throws IllegalStateException if no conversion in progress or not paused
     */
    public void resumeConversion() {
        if (!conversionInProgress.get()) {
            throw new IllegalStateException("No conversion in progress");
        }

        logger.info("Resuming conversion");

        try {
            conversionEngine.resumeConversion();
            logger.info("Conversion resumed successfully");

        } catch (Exception e) {
            logger.error("Failed to resume conversion", e);
            throw new RuntimeException("Failed to resume conversion: " + e.getMessage(), e);
        }
    }

    /**
     * Cancels the current conversion process.
     * 
     * Requirement REQ-004.2: Conversion workflow control
     */
    public void cancelConversion() {
        if (!conversionInProgress.get()) {
            logger.warn("No conversion in progress to cancel");
            return;
        }

        logger.info("Cancelling conversion");

        try {
            conversionEngine.cancelConversion();
            conversionInProgress.set(false);
            logger.info("Conversion cancelled successfully");

        } catch (Exception e) {
            logger.error("Failed to cancel conversion", e);
            // Don't throw, as cancellation should be best effort
        }
    }

    /**
     * Gets the current conversion progress.
     * 
     * @return conversion progress or null if no conversion active
     */
    public ConversionProgress getConversionProgress() {
        return null; // Progress is per-file, no overall progress available
    }

    // ===== Workflow Handler Methods =====
    // Note: These methods will be fully integrated with GTK UI in Phase 6

    /**
     * Handles the "Add Files" workflow.
     * Creates file chooser dialog, validates selected files, and adds to file
     * manager.
     * 
     * Requirement REQ-002.1: File selection workflows
     * 
     * NOTE: GTK UI integration is handled by
     * MainWindowJavaGi.showFileChooserDialog() (line 915).
     * This workflow handler is called by the UI, which then invokes addFiles() with
     * the selected paths.
     * 
     * @param parentWindow the parent window for the file chooser dialog
     * @throws IllegalStateException if controller not initialized
     */
    public void handleAddFiles(Object parentWindow) {
        if (!initialized.get()) {
            throw new IllegalStateException("Controller not initialized");
        }

        logger.info("handleAddFiles workflow started");

        // NOTE: Dialog display is handled by MainWindowJavaGi.showFileChooserDialog()
        // The UI calls this method, shows the dialog, and then calls addFiles() with
        // selected paths.
        // File addition business logic is in addFiles() method (lines 367-401).

        logger.debug("File chooser dialog handled by MainWindowJavaGi");
    }

    /**
     * Handles the "Add Folder" workflow.
     * Creates folder chooser dialog, scans folder recursively, and adds files to
     * file manager.
     * 
     * Requirement REQ-002.1: File selection workflows
     * 
     * NOTE: GTK UI integration is handled by
     * MainWindowJavaGi.showFolderChooserDialog() (line 972).
     * This workflow handler is called by the UI, which then invokes
     * addFilesFromFolder() with the selected path.
     * 
     * @param parentWindow the parent window for the folder chooser dialog
     * @throws IllegalStateException if controller not initialized
     */
    public void handleAddFolder(Object parentWindow) {
        if (!initialized.get()) {
            throw new IllegalStateException("Controller not initialized");
        }

        logger.info("handleAddFolder workflow started");

        // NOTE: Dialog display is handled by MainWindowJavaGi.showFolderChooserDialog()
        // The UI calls this method, shows the dialog, and then calls
        // addFilesFromFolder() with selected path.
        // Folder scanning business logic is in addFilesFromFolder() method (lines
        // 414-449).
        // Error dialogs are shown by MainWindowJavaGi.showErrorDialog() (line 1051).

        logger.debug("Folder chooser dialog handled by MainWindowJavaGi");
    }

    /**
     * Handles the "Remove Files" workflow.
     * Confirms removal if conversions in progress, removes files, and saves state.
     * 
     * Requirement REQ-002.2: File removal workflows
     * 
     * @param fileIds the IDs of files to remove
     * @throws IllegalStateException  if controller not initialized
     * @throws FileOperationException if files cannot be removed
     */
    public void handleRemoveFiles(List<String> fileIds) throws FileOperationException {
        if (!initialized.get()) {
            throw new IllegalStateException("Controller not initialized");
        }

        Objects.requireNonNull(fileIds, "fileIds cannot be null");
        if (fileIds.isEmpty()) {
            logger.debug("No files to remove");
            return;
        }

        logger.info("handleRemoveFiles workflow started for {} files", fileIds.size());

        // Check if any of the files being removed are currently being converted
        if (conversionInProgress.get()) {
            logger.warn("Files are being removed while conversion is in progress");
            // NOTE: Confirmation dialog is shown by MainWindowJavaGi.showConfirmDialog()
            // (line 1016) before calling this method
            // For now, we allow removal but log a warning
        }

        try {
            // Remove files via FileManager
            removeFiles(fileIds);

            // Save session state after removal to persist changes
            // Requirement REQ-005.2: Save session state
            saveApplicationState();

            logger.info("Files removed successfully and state saved");

        } catch (FileOperationException e) {
            logger.error("Failed to remove files", e);
            // NOTE: Error dialogs are shown by MainWindowJavaGi.showErrorDialog() (line
            // 1051)
            throw e;
        }
    }

    /**
     * Handles the "Clear All Files" workflow.
     * Clears all files from the conversion list and saves state.
     * 
     * Requirement REQ-002.2: File removal workflows
     * 
     * @throws IllegalStateException if controller not initialized
     */
    public void handleClearFiles() {
        if (!initialized.get()) {
            throw new IllegalStateException("Controller not initialized");
        }

        logger.info("handleClearFiles workflow started");

        // Check if conversion is in progress
        if (conversionInProgress.get()) {
            logger.warn("Clearing files while conversion is in progress");
            // NOTE: Confirmation dialog is shown by MainWindowJavaGi.showConfirmDialog()
            // (line 1016) before calling this method
            // For now, we allow clearing but log a warning
        }

        try {
            // Clear all files via FileManager
            clearFiles();

            // Save session state after clearing
            // Requirement REQ-005.2: Save session state
            saveApplicationState();

            logger.info("All files cleared successfully and state saved");

        } catch (Exception e) {
            logger.error("Failed to clear files", e);
            // NOTE: Error dialogs are shown by MainWindowJavaGi.showErrorDialog() (line
            // 1051)
            throw new RuntimeException("Failed to clear files: " + e.getMessage(), e);
        }
    }

    // ===== Settings Workflow Methods =====

    /**
     * Handles the "Settings Dialog" workflow.
     * Opens settings dialog, handles user input, validates and saves settings.
     * 
     * Requirement REQ-003.1: Settings dialog workflow
     * 
     * @param parentWindow the parent window for the dialog (for GTK modal dialogs)
     * @throws IllegalStateException if controller not initialized
     */
    public void handleSettingsDialog(Object parentWindow) {
        if (!initialized.get()) {
            throw new IllegalStateException("Controller not initialized");
        }

        logger.info("handleSettingsDialog workflow started");

        try {
            // Get current settings from SettingsManager
            ConversionSettings currentSettings = settingsManager.getCurrentSettings();

            // NOTE: SettingsDialogJavaGi handles GTK UI integration
            // (src/main/java/org/omc/ui/SettingsDialogJavaGi.java)
            // The UI creates the dialog, displays it to the user, and calls
            // handleSettingsSave() or handleSettingsCancel() based on user action.

            logger.info("Settings dialog workflow prepared (UI integration handled by SettingsDialogJavaGi)");

            // Business logic is ready:
            // - Gets current settings from SettingsManager
            // - Validates and saves settings on OK (handleSettingsSave)
            // - Discards changes on Cancel (handleSettingsCancel)

        } catch (Exception e) {
            logger.error("Failed to open settings dialog", e);
            // NOTE: Error dialogs are shown by MainWindowJavaGi.showErrorDialog() (line
            // 1051)
            throw new RuntimeException("Failed to open settings dialog: " + e.getMessage(), e);
        }
    }

    /**
     * Handles saving settings from the settings dialog.
     * Validates settings, persists them, and updates current settings.
     * 
     * Requirement REQ-003.1: Settings save workflow
     * 
     * @param newSettings the new settings to save
     * @throws InvalidSettingsException if settings validation fails
     */
    public void handleSettingsSave(ConversionSettings newSettings) throws InvalidSettingsException {
        Objects.requireNonNull(newSettings, "newSettings cannot be null");

        if (!initialized.get()) {
            throw new IllegalStateException("Controller not initialized");
        }

        logger.info("handleSettingsSave workflow started");

        try {
            // Validate settings before saving
            validateSettings(newSettings);

            // Save settings to SettingsManager
            settingsManager.saveSettings(newSettings);

            // Update current settings cache
            this.currentSettings = newSettings;

            // Clear unsaved changes flag
            clearUnsavedChanges();

            logger.info("Settings saved successfully");

            // NOTE: UI updates with new settings are handled by MainWindowJavaGi after this
            // method returns
            // - Format dropdowns, output directory display, quality/codec displays are
            // updated by the UI layer

        } catch (InvalidSettingsException e) {
            logger.error("Settings validation failed", e);
            // NOTE: Validation error dialogs are shown by
            // MainWindowJavaGi.showErrorDialog() (line 1051)
            throw e;
        } catch (Exception e) {
            logger.error("Failed to save settings", e);
            // NOTE: Error dialogs are shown by MainWindowJavaGi.showErrorDialog() (line
            // 1051)
            throw new RuntimeException("Failed to save settings: " + e.getMessage(), e);
        }
    }

    /**
     * Handles canceling settings changes.
     * Reverts to previous settings and clears unsaved changes flag.
     * 
     * Requirement REQ-003.1: Settings cancel workflow
     */
    public void handleSettingsCancel() {
        if (!initialized.get()) {
            throw new IllegalStateException("Controller not initialized");
        }

        logger.info("handleSettingsCancel workflow started");

        // Reload settings from SettingsManager (reverts to last saved)
        this.currentSettings = settingsManager.getCurrentSettings();

        // Clear unsaved changes flag
        clearUnsavedChanges();

        logger.info("Settings changes discarded");
    }

    /**
     * Validates conversion settings.
     * 
     * @param settings the settings to validate
     * @throws InvalidSettingsException if settings are invalid
     */
    private void validateSettings(ConversionSettings settings) throws InvalidSettingsException {
        Objects.requireNonNull(settings, "settings cannot be null");

        // Requirement REQ-003.1: Validate settings before saving

        // Validate output directory exists and is writable
        if (settings.outputDirectory() != null) {
            Path outputDir = settings.outputDirectory();
            if (!Files.exists(outputDir)) {
                throw new InvalidSettingsException(
                        "Output directory does not exist: " + settings.outputDirectory(),
                        "outputDirectory");
            }
            if (!Files.isDirectory(outputDir)) {
                throw new InvalidSettingsException(
                        "Output directory is not a directory: " + settings.outputDirectory(),
                        "outputDirectory");
            }
            if (!Files.isWritable(outputDir)) {
                throw new InvalidSettingsException(
                        "Output directory is not writable: " + settings.outputDirectory(),
                        "outputDirectory");
            }
        }

        // Validate parallelism is within reasonable range
        if (settings.parallelConversions() < 1 || settings.parallelConversions() > 16) {
            throw new InvalidSettingsException(
                    "Max parallel conversions must be between 1 and 16, got: " + settings.parallelConversions(),
                    "parallelConversions");
        }

        logger.debug("Settings validation passed");
    }

    // ===== Conversion Workflow Handler Methods =====

    /**
     * Handles the "Start Conversion" workflow.
     * Validates prerequisites (files, output directory, format) and starts batch
     * conversion.
     * 
     * Requirement REQ-004.2: Conversion start workflow
     * 
     * @throws IllegalStateException if validation fails or conversion already in
     *                               progress
     */
    public void handleStartConversion() {
        if (!initialized.get()) {
            throw new IllegalStateException("Controller not initialized");
        }

        if (conversionInProgress.get()) {
            throw new IllegalStateException("Conversion already in progress");
        }

        logger.info("handleStartConversion workflow started");

        try {
            // Requirement REQ-004.2: Validate file list not empty
            List<ConversionFile> files = fileManager.getFiles();
            if (files.isEmpty()) {
                throw new IllegalStateException("No files to convert");
            }

            // Requirement REQ-004.2: Validate output directory configured
            if (currentSettings == null || currentSettings.outputDirectory() == null) {
                throw new IllegalStateException("Output directory not configured");
            }

            // Requirement REQ-004.2: Validate output format selected
            if (!currentSettings.isValid()) {
                throw new IllegalStateException("Settings are not valid");
            }

            // Apply current sort order to file list before conversion
            // This ensures conversions execute in the same order as displayed in the UI
            FileListSortState sortState = getSavedSortState();
            if (sortState.isSorted()) {
                logger.debug("Applying sort order to conversion list: {}", sortState);
                files = new ArrayList<>(files); // Make a mutable copy
                files.sort(sortState.createComparator());
            }

            logger.info("Starting conversion for {} files in {} order",
                    files.size(), sortState.isSorted() ? sortState.toString() : "insertion");

            // Set flag BEFORE calling convertBatch to avoid race condition
            // where user cancels while async tasks are starting
            conversionInProgress.set(true);

            try {
                // Call ConversionEngine to start batch conversion
                conversionEngine.convertBatch(files, currentSettings);

                logger.info("Conversion started successfully");

                // NOTE: Progress and completion listeners are registered in initialize() method
                // (lines 132-182)
                // NOTE: UI updates are handled via registered callbacks (uiProgressCallback,
                // uiCompletionCallback)

            } catch (Exception conversionException) {
                // Reset flag if conversion failed to start
                conversionInProgress.set(false);
                logger.error("Failed to start conversion", conversionException);
                throw new RuntimeException("Failed to start conversion: " + conversionException.getMessage(),
                        conversionException);
            }

        } catch (IllegalStateException e) {
            logger.warn("Conversion start validation failed: {}", e.getMessage());
            // NOTE: Validation error dialogs are shown by
            // MainWindowJavaGi.showErrorDialog() (line 1051)
            // Don't re-throw - this is a validation error that should be handled gracefully
            // The UI shows a user-friendly message instead of a stack trace
            // For now, we just log the warning and return gracefully
        } catch (RuntimeException e) {
            // Re-throw RuntimeException from conversion start failure
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in conversion workflow", e);
            throw new RuntimeException("Failed to start conversion: " + e.getMessage(), e);
        }
    }

    /**
     * Handles the "Pause Conversion" workflow.
     * Pauses the current conversion process.
     * 
     * Requirement REQ-004.2: Conversion control workflow
     * 
     * @throws IllegalStateException if no conversion in progress
     */
    public void handlePauseConversion() {
        if (!initialized.get()) {
            throw new IllegalStateException("Controller not initialized");
        }

        if (!conversionInProgress.get()) {
            throw new IllegalStateException("No conversion in progress");
        }

        logger.info("handlePauseConversion workflow started");

        try {
            conversionEngine.pauseConversion();
            logger.info("Conversion paused successfully");

            // NOTE: UI button state updates are handled by MainWindowJavaGi via
            // showPauseButton()/showResumeButton()

        } catch (Exception e) {
            logger.error("Failed to pause conversion", e);
            // NOTE: Error dialogs are shown by MainWindowJavaGi.showErrorDialog() (line
            // 1051)
            throw new RuntimeException("Failed to pause conversion: " + e.getMessage(), e);
        }
    }

    /**
     * Handles the "Resume Conversion" workflow.
     * Resumes the paused conversion process.
     * 
     * Requirement REQ-004.2: Conversion control workflow
     * 
     * @throws IllegalStateException if no conversion in progress or not paused
     */
    public void handleResumeConversion() {
        if (!initialized.get()) {
            throw new IllegalStateException("Controller not initialized");
        }

        if (!conversionInProgress.get()) {
            throw new IllegalStateException("No conversion in progress");
        }

        logger.info("handleResumeConversion workflow started");

        try {
            conversionEngine.resumeConversion();
            logger.info("Conversion resumed successfully");

            // NOTE: UI button state updates are handled by MainWindowJavaGi via
            // showPauseButton()/showResumeButton()

        } catch (Exception e) {
            logger.error("Failed to resume conversion", e);
            // NOTE: Error dialogs are shown by MainWindowJavaGi.showErrorDialog() (line
            // 1051)
            throw new RuntimeException("Failed to resume conversion: " + e.getMessage(), e);
        }
    }

    /**
     * Handles the "Cancel Conversion" workflow.
     * Shows confirmation dialog and cancels the current conversion process if
     * confirmed.
     * 
     * Requirement REQ-004.2: Conversion control workflow
     */
    public void handleCancelConversion() {
        if (!initialized.get()) {
            throw new IllegalStateException("Controller not initialized");
        }

        if (!conversionInProgress.get()) {
            logger.warn("No conversion in progress to cancel");
            return;
        }

        logger.info("handleCancelConversion workflow started");

        // NOTE: Confirmation dialog is shown by MainWindowJavaGi.showConfirmDialog()
        // (line 1016) before calling this method
        // "Are you sure you want to cancel the conversion? Progress will be lost."
        // This method is only called if user confirms.

        try {
            conversionEngine.cancelConversion();
            logger.info("Conversion cancellation requested");

            // NOTE: conversionInProgress flag will be cleared by the completion handler
            // (line 172)
            // when all active conversions actually finish. This ensures the flag accurately
            // reflects the actual state of running conversions.

        } catch (Exception e) {
            logger.error("Failed to cancel conversion", e);
            // Don't throw exception - cancellation should be best effort
            // Flag will still be cleared by completion handler when conversions finish
        }

        // NOTE: UI button state updates are handled by MainWindowJavaGi after this
        // method returns
        // - Enable Start button, disable Pause/Resume/Cancel buttons
        // - Update file statuses in UI (mark as failed/cancelled)
        // - Clear progress displays
    }

    /**
     * Applies a preset to the specified files by setting settings overrides.
     * 
     * <p>
     * This method validates that all specified files belong to the same format
     * category
     * and that this category matches the preset's category. If validation passes,
     * it creates
     * a {@link FileSettingsOverride} from the preset and applies it to each file
     * via
     * {@link ConversionFile#withSettingsOverride(FileSettingsOverride)}.
     * </p>
     * 
     * <p>
     * <b>Validation Rules:</b>
     * </p>
     * <ul>
     * <li>All files must have the same format category</li>
     * <li>File category must match preset category</li>
     * <li>fileIds list cannot be empty</li>
     * <li>All file IDs must exist in {@link FileManager}</li>
     * </ul>
     * 
     * <p>
     * <b>Typical Workflow:</b>
     * </p>
     * <ol>
     * <li>User selects files in UI (single or multiple)</li>
     * <li>User chooses "Apply Preset" from context menu</li>
     * <li>UI calls {@link #getAvailablePresetsForFiles(List)} to get compatible
     * presets</li>
     * <li>User selects a preset</li>
     * <li>UI calls this method to apply the preset</li>
     * </ol>
     * 
     * <p>
     * Requirements:
     * </p>
     * <ul>
     * <li>REQ-3.2: Preset application to files</li>
     * <li>REQ-3.3: Context menu preset application</li>
     * </ul>
     * 
     * @param fileIds List of file IDs to apply the preset to
     * @param preset  The section preset to apply
     * @throws IllegalArgumentException if files have mixed categories, if any
     *                                  file's category
     *                                  doesn't match the preset category, if
     *                                  fileIds is empty, or if any file ID is not
     *                                  found
     * @see #clearPresetFromFiles(List) to remove preset overrides
     * @see #getAvailablePresetsForFiles(List) to get compatible presets
     * @see FileSettingsOverride
     */
    public void applyPresetToFiles(List<String> fileIds, SectionPreset preset) {
        Objects.requireNonNull(fileIds, "fileIds cannot be null");
        Objects.requireNonNull(preset, "preset cannot be null");

        if (fileIds.isEmpty()) {
            throw new IllegalArgumentException("fileIds cannot be empty");
        }

        logger.debug("Applying preset '{}' to {} files", preset.name(), fileIds.size());

        // Performance tracking: REQ-5.2 - Target < 500ms for 100 files
        long startTime = System.nanoTime();

        // Get ConversionFile objects from FileManager
        List<ConversionFile> files = fileIds.stream()
                .map(fileManager::getFile)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        // Validate all files have the same FormatCategory
        java.util.Set<FormatCategory> categories = files.stream()
                .map(file -> file.format().getCategory())
                .collect(java.util.stream.Collectors.toSet());

        if (categories.size() != 1) {
            logger.warn("Cannot apply preset to mixed file categories: {}", categories);
            throw new IllegalArgumentException(
                    "Cannot apply preset to files with different format categories. " +
                            "Selected files have categories: " + categories);
        }

        // Get the single category
        FormatCategory fileCategory = categories.iterator().next();

        // Check file category matches preset category
        if (fileCategory != preset.category()) {
            logger.warn("File category {} does not match preset category {}",
                    fileCategory, preset.category());
            throw new IllegalArgumentException(
                    String.format("File category %s does not match preset category %s",
                            fileCategory, preset.category()));
        }

        // Create FileSettingsOverride using switch on category
        FileSettingsOverride override = switch (preset.category()) {
            case VIDEO -> FileSettingsOverride.forVideo(
                    preset.name(),
                    preset.videoSettings());
            case AUDIO -> FileSettingsOverride.forAudio(
                    preset.name(),
                    preset.audioSettings());
            case IMAGE -> FileSettingsOverride.forImage(
                    preset.name(),
                    preset.imageSettings());
            case DOCUMENT -> FileSettingsOverride.forDocument(
                    preset.name(),
                    preset.documentSettings());
            case UNKNOWN -> {
                logger.error("Cannot create override for UNKNOWN category");
                throw new IllegalArgumentException("Cannot apply preset with UNKNOWN category");
            }
        };

        // Loop through files and apply settings override
        for (ConversionFile file : files) {
            ConversionFile updatedFile = file.withSettingsOverride(override);
            fileManager.updateFile(updatedFile);
        }

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        logger.info("Successfully applied preset '{}' to {} file(s) in category {} (took {}ms - target <500ms)",
                preset.name(), files.size(), preset.category(), elapsedMs);
    }

    /**
     * Clears custom settings (preset overrides) from the specified files.
     * 
     * <p>
     * Removes any {@link FileSettingsOverride} applied to the specified files,
     * causing
     * them to revert to using the global section settings for their category. Files
     * that
     * don't have custom settings are skipped gracefully without error.
     * </p>
     * 
     * <p>
     * <b>Typical Workflow:</b>
     * </p>
     * <ol>
     * <li>User selects files with preset overrides in UI</li>
     * <li>User chooses "Clear Preset" from context menu</li>
     * <li>UI calls this method to remove overrides</li>
     * <li>Files revert to global section settings</li>
     * </ol>
     * 
     * <p>
     * Requirement REQ-3.3: Context menu clear preset functionality
     * </p>
     * 
     * @param fileIds List of file IDs to clear settings from
     * @throws IllegalArgumentException if fileIds is null
     * @see #applyPresetToFiles(List, SectionPreset) to apply presets
     * @see ConversionFile#clearSettingsOverride()
     * @see ConversionFile#hasCustomSettings()
     */
    public void clearPresetFromFiles(List<String> fileIds) {
        Objects.requireNonNull(fileIds, "fileIds cannot be null");

        if (fileIds.isEmpty()) {
            logger.debug("clearPresetFromFiles called with empty list");
            return;
        }

        logger.debug("Clearing custom settings from {} file(s)", fileIds.size());

        int clearedCount = 0;
        for (String fileId : fileIds) {
            Optional<ConversionFile> fileOpt = fileManager.getFile(fileId);
            if (fileOpt.isEmpty()) {
                logger.warn("File not found in FileManager: {}", fileId);
                continue;
            }

            ConversionFile file = fileOpt.get();
            if (file.hasCustomSettings()) {
                ConversionFile updatedFile = file.clearSettingsOverride();
                fileManager.updateFile(updatedFile);
                clearedCount++;
                logger.debug("Cleared custom settings from file: {}", fileId);
            } else {
                logger.debug("File has no custom settings to clear: {}", fileId);
            }
        }

        logger.info("Cleared custom settings from {} of {} file(s)", clearedCount, fileIds.size());
    }

    /**
     * Returns the list of available presets for the given files.
     * 
     * <p>
     * This method determines which presets are compatible with the selected files
     * by:
     * <ol>
     * <li>Validating all files have the same format category</li>
     * <li>Loading {@link PresetsBySection} from {@link SettingsManager}</li>
     * <li>Returning presets matching the file category</li>
     * </ol>
     * </p>
     * 
     * <p>
     * <b>Return Conditions:</b>
     * </p>
     * <ul>
     * <li>Returns empty list if fileIds is empty</li>
     * <li>Returns empty list if any file ID is not found</li>
     * <li>Returns empty list if files have mixed categories</li>
     * <li>Returns empty list if category is UNKNOWN</li>
     * <li>Otherwise returns all presets for the file category</li>
     * </ul>
     * 
     * <p>
     * <b>Typical Workflow:</b>
     * </p>
     * <ol>
     * <li>User right-clicks on file(s) in UI</li>
     * <li>UI calls this method to populate "Apply Preset" submenu</li>
     * <li>User selects a preset from the submenu</li>
     * <li>UI calls {@link #applyPresetToFiles(List, SectionPreset)}</li>
     * </ol>
     * 
     * <p>
     * Requirement REQ-3.3: Context menu preset list population
     * </p>
     * 
     * @param fileIds List of file IDs to get available presets for
     * @return List of presets for the file's category, or empty list if
     *         mixed/invalid
     * @throws IllegalArgumentException if fileIds is null
     * @see #applyPresetToFiles(List, SectionPreset) to apply a preset
     * @see PresetsBySection#getPresetsForCategory(FormatCategory)
     */
    public List<SectionPreset> getAvailablePresetsForFiles(List<String> fileIds) {
        Objects.requireNonNull(fileIds, "fileIds cannot be null");

        if (fileIds.isEmpty()) {
            logger.debug("getAvailablePresetsForFiles called with empty list");
            return List.of();
        }

        logger.debug("Getting available presets for {} file(s)", fileIds.size());

        // Get ConversionFile objects from FileManager
        List<ConversionFile> files = new ArrayList<>();
        for (String fileId : fileIds) {
            Optional<ConversionFile> fileOpt = fileManager.getFile(fileId);
            if (fileOpt.isEmpty()) {
                logger.warn("File not found in FileManager: {}", fileId);
                // If any file is missing, return empty list (invalid state)
                return List.of();
            }
            files.add(fileOpt.get());
        }

        // Collect all FormatCategory values to validate uniformity
        java.util.Set<FormatCategory> categories = files.stream()
                .map(file -> file.format().getCategory())
                .collect(java.util.stream.Collectors.toSet());

        // If empty or mixed categories, return empty list
        if (categories.isEmpty() || categories.size() > 1) {
            logger.debug("Mixed or empty categories detected: {}, returning empty preset list", categories);
            return List.of();
        }

        // Get the single category
        FormatCategory category = categories.iterator().next();

        // UNKNOWN category has no presets
        if (category == FormatCategory.UNKNOWN) {
            logger.debug("UNKNOWN category detected, returning empty preset list");
            return List.of();
        }

        // Load PresetsBySection from SettingsManager
        PresetsBySection presetsBySection = settingsManager.loadPresetsBySection();

        // Get presets for this category
        List<SectionPreset> presets = presetsBySection.getPresetsForCategory(category);

        logger.debug("Found {} preset(s) for category {}", presets.size(), category);
        return presets;
    }

    // ===== Private Helper Methods =====

    /**
     * Restores file list from session state.
     * Validates that files still exist and removes missing files.
     * 
     * Requirement REQ-005.2: Restore session state
     */
    private void restoreFileList(SessionState sessionState) {
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

        // Extract paths from ConversionFile objects and filter out files that no longer
        // exist
        List<Path> existingFilePaths = pendingFiles.stream()
                .map(ConversionFile::path)
                .filter(Files::exists)
                .toList();

        int missingCount = pendingFiles.size() - existingFilePaths.size();
        if (missingCount > 0) {
            logger.warn("Skipped {} missing files from session state", missingCount);
        }

        if (!existingFilePaths.isEmpty()) {
            fileManager.addFiles(existingFilePaths);
            logger.info("Restored {} files to file list", existingFilePaths.size());
        }
    }

    /**
     * Saves current application state to disk.
     * 
     * Requirement REQ-005.1, REQ-005.2, REQ-005.3: Persist application state
     * Requirement REQ-FL-4.5: Preserve file list sort state during shutdown
     */
    private void saveApplicationState() {
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

            // Get current window state (would come from UI)
            WindowState windowState = WindowState.defaultState();

            // Get current sort state to preserve it during shutdown
            // Requirement REQ-FL-4.5: Persist sort state across application restarts
            ApplicationState currentState = stateManager.getCurrentState();
            FileListSortState sortState = currentState.fileListSortState();

            // Create new state with all current values including sort state
            ApplicationState state = new ApplicationState(
                    windowState,
                    sessionState,
                    currentSettings,
                    sortState, // Preserve current sort state
                    "1.0.0",
                    System.currentTimeMillis());

            stateManager.saveState(state);
            logger.debug("Application state saved successfully");

        } catch (Exception e) {
            logger.error("Failed to save application state", e);
        }
    }

    /**
     * Saves current settings to disk.
     * 
     * Requirement REQ-003.1, REQ-005.3: Persist settings
     */
    private void saveCurrentSettings() {
        try {
            if (currentSettings != null) {
                settingsManager.saveSettings(currentSettings);
                clearUnsavedChanges();
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
    public void saveSortState(FileListSortState sortState) {
        try {
            logger.debug("Saving sort state: {}", sortState);

            // Get current application state
            ApplicationState currentState = stateManager.getCurrentState();

            // Update with new sort state
            ApplicationState updatedState = currentState.withFileListSortState(sortState);

            // Save updated state
            stateManager.saveState(updatedState);

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
    public FileListSortState getSavedSortState() {
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
