package org.omc.ui;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.gnome.gio.Menu;
import org.gnome.gio.MenuItem;
import org.gnome.gio.SimpleAction;
import org.gnome.glib.GLib;
import org.gnome.gtk.Application;
import org.gnome.gtk.ApplicationWindow;
import org.gnome.gtk.Box;
import org.gnome.gtk.Button;
import org.gnome.gtk.ColumnView;
import org.gnome.gtk.GestureClick;
import org.gnome.gtk.GtkBuilder;
import org.gnome.gtk.Label;
import org.gnome.gtk.MenuButton;
import org.gnome.gtk.Orientation;
import org.gnome.gtk.PopoverMenu;
import org.gnome.gtk.ProgressBar;
import org.gnome.gtk.Revealer;
import org.gnome.gtk.ScrolledWindow;
import org.gnome.gtk.Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.omc.controller.ApplicationWorkflowController;
import org.omc.exception.FileOperationException;
import org.omc.model.BatchProgress;
import org.omc.model.ConversionFile;
import org.omc.model.ConversionProgress;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionSettings;
import org.omc.model.ConversionStatus;
import org.omc.model.FileFormat;
import org.omc.model.FileListSortState;
import org.omc.model.FormatCategory;
import org.omc.model.SectionPreset;
import org.omc.model.WindowState;

/**
 * Main application window using GTK 4 via java-gi bindings.
 * Loads UI from main_window.ui and provides interface for file management and
 * conversion.
 * 
 * <p>
 * Requirements: REQ-001.1, REQ-102.1
 * </p>
 */
public class MainWindowJavaGi extends ApplicationWindow {

    private static final Logger logger = LoggerFactory.getLogger(MainWindowJavaGi.class);

    // Controller
    private final ApplicationWorkflowController controller;

    // UI Components from Builder
    private GtkBuilder builder;

    // Track window size
    private int currentWidth = 1000;
    private int currentHeight = 700;

    // Header Bar Widgets
    private Button addFilesButton;
    private Button addFolderButton;
    private Button settingsButton;
    private MenuButton menuButton;

    // File List View
    private ScrolledWindow fileListScrolledWindow;
    private ColumnView fileListColumnView;
    private FileListView fileListView;

    // Progress View
    private Revealer progressRevealer;
    private ProgressBar overallProgressBar;
    private Label statusLabel;
    private Label timeRemainingLabel;
    private Label conversionSpeedLabel;
    private Button pauseButton;
    private Button resumeButton;
    private Button cancelButton;
    private ProgressView progressView;

    // Action Bar Widgets
    private Button removeSelectedButton;
    private Button clearAllButton;
    private Button convertButton;
    private Label fileCountLabel;
    private Label totalSizeLabel;

    // Status Bar
    private Label statusBarLabel;

    // Batch completion tracking
    // Requirement REQ-004.2: Track overall batch progress
    private int totalFilesInBatch = 0;
    private int completedFilesInBatch = 0;
    private int successfulFilesInBatch = 0;
    private int failedFilesInBatch = 0;
    private int cancelledFilesInBatch = 0;
    private boolean batchCompleted = false;

    // Shutdown tracking
    // Prevents multiple confirmation dialogs during shutdown
    private boolean shutdownInProgress = false;

    /**
     * Constructs the main window and loads UI from XML.
     * 
     * @param app        the GTK application instance
     * @param controller the application workflow controller
     */
    public MainWindowJavaGi(Application app, ApplicationWorkflowController controller) {
        super(app);
        this.controller = controller;

        logger.info("Initializing MainWindowJavaGi");

        loadUI();
        setupWidgetReferences();
        setupComponents();
        setupWindowActions();
        connectSignals();
        setupWindowIcon();

        logger.info("MainWindowJavaGi initialization complete");
    }

    /**
     * Loads the UI definition from main_window.ui using GtkBuilder.
     */
    private void loadUI() {
        try {
            logger.debug("Loading UI from main_window.ui");

            // Load UI file from classpath as string
            InputStream inputStream = getClass().getResourceAsStream("/ui/main_window.ui");
            if (inputStream == null) {
                throw new RuntimeException("Could not find /ui/main_window.ui in classpath");
            }

            String uiXml = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            // Create builder and load from string
            builder = new GtkBuilder();
            builder.addFromString(uiXml, uiXml.length());

            // Get the main window from builder and copy its properties
            ApplicationWindow window = (ApplicationWindow) builder.getObject("mainWindow");

            // Copy window properties - use default size directly since GTK 4 doesn't expose
            // getDefaultWidth
            setTitle(window.getTitle());
            setDefaultSize(1000, 700); // Use hardcoded default matching UI file

            // Get the main content and set it as our child
            Widget content = window.getChild();
            if (content != null) {
                // Remove from old parent and add to this window
                window.setChild(null);
                setChild(content);
            }

            // Get the header bar and set it as our titlebar
            Widget titlebar = window.getTitlebar();
            if (titlebar != null) {
                window.setTitlebar(null);
                setTitlebar(titlebar);
            }

            logger.debug("UI loaded successfully");

        } catch (Exception e) {
            logger.error("Failed to load UI from main_window.ui", e);

            // Fallback: Create minimal UI programmatically
            setTitle("Open Media Converter");
            setDefaultSize(1000, 700);

            Box box = new Box(Orientation.VERTICAL, 0);
            Label errorLabel = new Label("Failed to load UI. Please check logs.");
            box.append(errorLabel);
            setChild(box);
        }
    }

    /**
     * Gets references to all widgets from the builder.
     */
    private void setupWidgetReferences() {
        try {
            // Header bar widgets
            addFilesButton = (Button) builder.getObject("addFilesButton");
            addFolderButton = (Button) builder.getObject("addFolderButton");
            settingsButton = (Button) builder.getObject("settingsButton");
            menuButton = (MenuButton) builder.getObject("menuButton");

            // File list view
            fileListScrolledWindow = (ScrolledWindow) builder.getObject("fileListScrolledWindow");
            fileListColumnView = (ColumnView) builder.getObject("fileListColumnView");

            // Progress view widgets
            progressRevealer = (Revealer) builder.getObject("progressRevealer");
            overallProgressBar = (ProgressBar) builder.getObject("overallProgressBar");
            statusLabel = (Label) builder.getObject("statusLabel");
            timeRemainingLabel = (Label) builder.getObject("timeRemainingLabel");
            conversionSpeedLabel = (Label) builder.getObject("conversionSpeedLabel");
            pauseButton = (Button) builder.getObject("pauseButton");
            resumeButton = (Button) builder.getObject("resumeButton");
            cancelButton = (Button) builder.getObject("cancelButton");

            // Action bar widgets
            removeSelectedButton = (Button) builder.getObject("removeSelectedButton");
            clearAllButton = (Button) builder.getObject("clearAllButton");
            convertButton = (Button) builder.getObject("convertButton");
            fileCountLabel = (Label) builder.getObject("fileCountLabel");
            totalSizeLabel = (Label) builder.getObject("totalSizeLabel");

            // Status bar
            statusBarLabel = (Label) builder.getObject("statusBarLabel");

            logger.debug("Widget references obtained successfully");

        } catch (Exception e) {
            logger.error("Failed to get widget references from builder", e);
            throw new RuntimeException("Failed to initialize UI components", e);
        }
    }

    /**
     * Sets up custom components with business logic integration.
     */
    private void setupComponents() {
        // Initialize FileListView component (Task 37: pass controller for output format
        // resolution)
        fileListView = new FileListView(fileListColumnView, controller);

        // Setup double-click listener (Task 54: Implement double-click handler)
        // Requirement REQ-FL-2.1: Double-click opens file details dialog
        fileListView.setDoubleClickListener(this::showFileDetailsDialog);

        // Setup sort change listener (Task 79: REQ-FL-4.5)
        // Save sort state when user changes column sorting
        fileListView.setSortChangeListener(sortState -> {
            logger.debug("Sort state changed: {}", sortState);
            controller.saveSortState(sortState);
        });

        // NOTE: Sort state restoration moved to restoreSortStateFromController()
        // which must be called AFTER controller.initialize() to ensure state is loaded

        // Initialize ProgressView component
        progressView = new ProgressView(
                progressRevealer,
                overallProgressBar,
                statusLabel,
                timeRemainingLabel,
                conversionSpeedLabel);

        // Initially hide progress view
        progressView.hide();

        // Initially disable progress control buttons (no conversion in progress)
        pauseButton.setSensitive(false);
        resumeButton.setSensitive(false);
        cancelButton.setSensitive(false);

        // Setup context menu for file list
        // Requirement REQ-3.3: Right-click context menu for preset application
        setupFileListContextMenu();

        logger.debug("Custom components initialized");
    }

    /**
     * Sets up window-level actions and keyboard shortcuts.
     * These are context-specific actions that apply only to this window.
     * 
     * Requirements: REQ-102.2 - Keyboard shortcuts (Delete and Ctrl+A)
     */
    private void setupWindowActions() {
        logger.debug("Setting up window-level actions");

        // Remove Selected action (Delete key)
        // Requirement REQ-102.2: Delete key removes selected items
        org.gnome.gio.SimpleAction removeAction = new org.gnome.gio.SimpleAction("remove-selected", null);
        removeAction.onActivate(param -> {
            logger.debug("Remove Selected action activated");
            triggerRemoveSelected();
        });
        addAction(removeAction);

        // Select All action (Ctrl+A)
        // Requirement REQ-102.2: Ctrl+A selects all items
        org.gnome.gio.SimpleAction selectAllAction = new org.gnome.gio.SimpleAction("select-all", null);
        selectAllAction.onActivate(param -> {
            logger.debug("Select All action activated");
            triggerSelectAll();
        });
        addAction(selectAllAction);

        // Register accelerators with the application
        // Note: Window actions use "win." prefix
        Application app = getApplication();
        if (app != null) {
            app.setAccelsForAction("win.remove-selected", new String[] { "Delete" });
            app.setAccelsForAction("win.select-all", new String[] { "<Primary>a" });
            logger.debug("Registered window action accelerators: Delete and Ctrl+A");
        } else {
            logger.warn("Application not available, cannot register accelerators");
        }
    }

    /**
     * Sets up the window icon for desktop integration.
     * Loads the application icon from resources.
     * 
     * Requirements: REQ-102.1 - Application icon for desktop integration
     */
    private void setupWindowIcon() {
        try {
            // Set the icon name - GTK will look for this in the icon theme paths
            // Our icons are in src/main/resources/icons/hicolor/
            setIconName("open-media-converter");
            logger.debug("Window icon set to 'open-media-converter'");
        } catch (Exception e) {
            logger.warn("Failed to set window icon", e);
        }
    }

    /**
     * Connects button signals to controller methods.
     * 
     * <p>
     * Requirements: REQ-001.1, REQ-001.2, REQ-002.1, REQ-002.2, REQ-004.2
     * </p>
     */
    private void connectSignals() {
        logger.debug("Connecting UI signals to controller");

        // Header bar buttons
        addFilesButton.onClicked(() -> handleAddFiles());
        addFolderButton.onClicked(() -> handleAddFolder());
        settingsButton.onClicked(() -> handleSettings());

        // Action bar buttons
        removeSelectedButton.onClicked(() -> handleRemoveSelected());
        clearAllButton.onClicked(() -> handleClearAll());
        convertButton.onClicked(() -> handleConvert());

        // Progress control buttons
        pauseButton.onClicked(() -> handlePause());
        resumeButton.onClicked(() -> handleResume());
        cancelButton.onClicked(() -> handleCancel());

        // Window close event
        onCloseRequest(() -> handleClose());

        logger.debug("Signal connections complete");
    }

    // ===== Context Menu Setup =====

    /**
     * Sets up right-click context menu for file list.
     * 
     * <p>
     * Requirements: REQ-3.3 - Context menu for preset application
     * Task 26: Add GestureClick for right mouse button
     * </p>
     */
    private void setupFileListContextMenu() {
        // Create GestureClick for right mouse button (button = 3)
        GestureClick gesture = new GestureClick();
        gesture.setButton(3); // Right mouse button

        // Connect to pressed signal to show context menu
        gesture.onPressed((nPress, x, y) -> {
            logger.debug("Right-click detected at ({}, {})", x, y);
            showContextMenu(x, y);
        });

        // Add gesture to the ColumnView widget
        fileListColumnView.addController(gesture);

        logger.debug("Context menu gesture controller added to file list");
    }

    /**
     * Shows the context menu at the specified coordinates.
     * 
     * <p>
     * Requirements: REQ-3.3 - Context menu with preset application options
     * Task 27: Implement "Apply Preset" submenu
     * Task 28: Implement "Clear Custom Settings" menu item
     * Task 59: Implement "Open File Location" menu item
     * </p>
     * 
     * @param x the x coordinate (relative to the parent widget)
     * @param y the y coordinate (relative to the parent widget)
     */
    private void showContextMenu(double x, double y) {
        // Get selected file IDs
        List<String> selectedIds = fileListView.getSelectedFileIds();

        if (selectedIds.isEmpty()) {
            logger.debug("No files selected, not showing context menu");
            return;
        }

        logger.debug("Showing context menu for {} selected file(s) at ({}, {})", selectedIds.size(), x, y);

        // Create menu model
        Menu menu = new Menu();

        // Add "Apply Preset" submenu (Task 27)
        buildApplyPresetSubmenu(menu, selectedIds);

        // Add "Clear Custom Settings" menu item (Task 28)
        buildClearCustomSettingsMenuItem(menu, selectedIds);

        // Add "Open File Location" menu item (Task 59) - only enabled for single
        // selection
        // Note: GTK automatically adds visual spacing between different action groups
        buildOpenFileLocationMenuItem(menu, selectedIds);

        // Create PopoverMenu and attach to file list
        PopoverMenu popoverMenu = PopoverMenu.fromModel(menu);
        popoverMenu.setParent(fileListColumnView);

        // Set the popover position to the click coordinates
        // Create a rectangle at the click position (1x1 pixel area)
        org.gnome.gdk.Rectangle rect = new org.gnome.gdk.Rectangle((int) x, (int) y, 1, 1);
        popoverMenu.setPointingTo(rect);

        // Show the popover
        popoverMenu.popup();

        logger.debug("Context menu displayed at position ({}, {})", x, y);
    }

    /**
     * Builds the "Apply Preset" submenu dynamically based on available presets.
     * 
     * <p>
     * Requirements: REQ-3.3 - Dynamic preset submenu
     * Task 27: Implement "Apply Preset" submenu
     * </p>
     * 
     * @param menu        the parent menu
     * @param selectedIds the list of selected file IDs
     */
    private void buildApplyPresetSubmenu(Menu menu, List<String> selectedIds) {
        Menu presetSubmenu = new Menu();

        try {
            // Get available presets for selected files
            List<SectionPreset> availablePresets = controller.getAvailablePresetsForFiles(selectedIds);

            if (availablePresets.isEmpty()) {
                // No presets available - show disabled message
                MenuItem noPresetsItem = new MenuItem("No presets available", null);
                noPresetsItem.setActionAndTarget(null, null);
                presetSubmenu.appendItem(noPresetsItem);
            } else {
                // Add menu item for each available preset
                for (SectionPreset preset : availablePresets) {
                    String actionName = "win.apply-preset-" + sanitizeActionName(preset.name());

                    // Create action for this preset
                    SimpleAction action = new SimpleAction(actionName.substring(4), null); // Remove "win." prefix
                    action.onActivate(param -> {
                        logger.debug("Applying preset '{}' to {} files", preset.name(), selectedIds.size());
                        applyPresetToSelectedFiles(selectedIds, preset);
                    });
                    addAction(action);

                    // Add menu item
                    MenuItem presetItem = new MenuItem(preset.name(), actionName);
                    presetSubmenu.appendItem(presetItem);
                }
            }
        } catch (IllegalArgumentException e) {
            // Mixed selection or invalid selection - show error message
            logger.warn("Invalid selection for preset application: {}", e.getMessage());
            MenuItem errorItem = new MenuItem("Cannot apply to mixed file types", null);
            errorItem.setActionAndTarget(null, null);
            presetSubmenu.appendItem(errorItem);
        } catch (Exception e) {
            logger.error("Error building preset submenu", e);
            MenuItem errorItem = new MenuItem("Error loading presets", null);
            errorItem.setActionAndTarget(null, null);
            presetSubmenu.appendItem(errorItem);
        }

        // Add submenu to parent menu
        menu.appendSubmenu("Apply Preset", presetSubmenu);
    }

    /**
     * Builds the "Clear Custom Settings" menu item.
     * 
     * <p>
     * Requirements: REQ-3.3 - Clear custom settings option
     * Task 28: Implement "Clear Custom Settings" menu item
     * </p>
     * 
     * @param menu        the parent menu
     * @param selectedIds the list of selected file IDs
     */
    private void buildClearCustomSettingsMenuItem(Menu menu, List<String> selectedIds) {
        // Create action for clearing custom settings
        SimpleAction clearAction = new SimpleAction("clear-preset", null);
        clearAction.onActivate(param -> {
            logger.debug("Clearing custom settings from {} files", selectedIds.size());
            clearCustomSettingsFromFiles(selectedIds);
        });
        addAction(clearAction);

        // Add menu item
        MenuItem clearItem = new MenuItem("Clear Custom Settings", "win.clear-preset");
        menu.appendItem(clearItem);
    }

    /**
     * Builds the "Open File Location" menu item.
     * Only enabled when a single file is selected (REQ-FL-3.1).
     * 
     * <p>
     * Requirements: REQ-FL-3.1 - Context menu with open location option
     * Task 59: Implement "Open File Location" menu item
     * </p>
     * 
     * @param menu        the parent menu
     * @param selectedIds the list of selected file IDs
     */
    private void buildOpenFileLocationMenuItem(Menu menu, List<String> selectedIds) {
        // Create action for opening file location
        SimpleAction openLocationAction = new SimpleAction("open-file-location", null);

        // Only enable if exactly one file is selected
        if (selectedIds.size() == 1) {
            openLocationAction.onActivate(param -> {
                logger.debug("Opening file location for file: {}", selectedIds.get(0));
                handleOpenFileLocation(selectedIds.get(0));
            });
        } else {
            // Disable action for multiple selection
            openLocationAction.setEnabled(false);
        }

        addAction(openLocationAction);

        // Add menu item
        MenuItem openLocationItem = new MenuItem("Open File Location", "win.open-file-location");
        menu.appendItem(openLocationItem);
    }

    /**
     * Applies a preset to the selected files.
     * FIX: Defer error dialogs to avoid modal stacking issues with context menu
     * popover.
     * 
     * @param fileIds the list of file IDs
     * @param preset  the preset to apply
     */
    private void applyPresetToSelectedFiles(List<String> fileIds, SectionPreset preset) {
        try {
            controller.applyPresetToFiles(fileIds, preset);
            refreshFileList();
            showStatus(String.format("Applied preset '%s' to %d file(s)", preset.name(), fileIds.size()));
            logger.info("Applied preset '{}' to {} file(s)", preset.name(), fileIds.size());
        } catch (IllegalArgumentException e) {
            logger.error("Failed to apply preset: {}", e.getMessage());
            // Defer error dialog to next idle cycle to ensure context menu dismisses first
            GLib.idleAdd(0, () -> {
                showErrorDialog("Apply Preset Error", e.getMessage());
                return false;
            });
        } catch (Exception e) {
            logger.error("Unexpected error applying preset", e);
            // Defer error dialog to next idle cycle to ensure context menu dismisses first
            GLib.idleAdd(0, () -> {
                showErrorDialog("Apply Preset Error", "An unexpected error occurred: " + e.getMessage());
                return false;
            });
        }
    }

    /**
     * Clears custom settings from the selected files.
     * FIX: Defer error dialogs to avoid modal stacking issues with context menu
     * popover.
     * 
     * @param fileIds the list of file IDs
     */
    private void clearCustomSettingsFromFiles(List<String> fileIds) {
        try {
            controller.clearPresetFromFiles(fileIds);
            refreshFileList();
            showStatus(String.format("Cleared custom settings from %d file(s)", fileIds.size()));
            logger.info("Cleared custom settings from {} file(s)", fileIds.size());
        } catch (Exception e) {
            logger.error("Error clearing custom settings", e);
            // Defer error dialog to next idle cycle to ensure context menu dismisses first
            GLib.idleAdd(0, () -> {
                showErrorDialog("Clear Settings Error", "Failed to clear custom settings: " + e.getMessage());
                return false;
            });
        }
    }

    /**
     * Refreshes the file list display.
     * Updates the file list view with current files from the controller.
     * 
     * <p>
     * Requirements: REQ-3.3 - Refresh UI after preset operations
     * </p>
     */
    private void refreshFileList() {
        updateFileList();
    }

    /**
     * Sanitizes a preset name to create a valid GTK action name.
     * Replaces spaces and special characters with hyphens.
     * 
     * @param name the preset name
     * @return sanitized action name
     */
    private String sanitizeActionName(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-");
    }

    // ===== Event Handlers =====

    /**
     * Handles Add Files button click.
     * Requirements: REQ-002.1, Task 43
     */
    private void handleAddFiles() {
        logger.debug("Add Files button clicked");
        showFileChooserDialog(selectedPaths -> {
            if (!selectedPaths.isEmpty()) {
                try {
                    List<java.nio.file.Path> paths = selectedPaths.stream()
                            .map(java.nio.file.Paths::get)
                            .collect(Collectors.toList());
                    controller.addFiles(paths);
                    updateFileList();
                    showStatus(selectedPaths.size() + " file(s) added");
                } catch (FileOperationException e) {
                    logger.error("Failed to add files", e);
                    showErrorDialog("Add Files Error", e.getMessage());
                }
            }
        });
    }

    /**
     * Handles Add Folder button click.
     * Requirements: REQ-002.1, Task 44
     */
    private void handleAddFolder() {
        logger.debug("Add Folder button clicked");
        showFolderChooserDialog(selectedPath -> {
            if (selectedPath != null && !selectedPath.isBlank()) {
                try {
                    java.nio.file.Path folderPath = java.nio.file.Paths.get(selectedPath);
                    List<ConversionFile> addedFiles = controller.addFilesFromFolder(folderPath, true);
                    updateFileList();
                    showStatus(addedFiles.size() + " file(s) added from folder");
                } catch (FileOperationException e) {
                    logger.error("Failed to add files from folder", e);
                    showErrorDialog("Add Folder Error", e.getMessage());
                }
            }
        });
    }

    /**
     * Handles Settings button click.
     */
    private void handleSettings() {
        logger.debug("Settings button clicked");

        try {
            // Get current settings from controller
            ConversionSettings currentSettings = controller.getCurrentSettings();

            // Create and show settings dialog
            SettingsDialogJavaGi settingsDialog = new SettingsDialogJavaGi(this, currentSettings,
                    controller.getSettingsManager());

            // Register callback to receive updated settings when user clicks Save
            // FIX: Defer error dialog to avoid modal stacking (settings dialog still open)
            settingsDialog.setOnSaveCallback(newSettings -> {
                logger.debug("Settings dialog saved, updating controller with new settings");
                try {
                    controller.updateSettings(newSettings);
                    logger.info("Settings successfully updated in controller");

                    // BUG FIX: Refresh file list to update Output Format column
                    // When section output format changes, the Output Format column needs to be
                    // refreshed
                    refreshFileList();
                    logger.debug("File list refreshed after settings update");
                } catch (Exception e) {
                    logger.error("Failed to update settings in controller", e);
                    // Defer error dialog to next idle cycle to ensure settings dialog closes first
                    GLib.idleAdd(0, () -> {
                        showErrorDialog("Settings Error", "Failed to save settings: " + e.getMessage());
                        return false;
                    });
                }
            });

            settingsDialog.showDialog();

            logger.debug("Settings dialog displayed");
        } catch (Exception e) {
            logger.error("Failed to show settings dialog", e);
            showErrorDialog("Settings Error", "Failed to open settings dialog: " + e.getMessage());
        }
    }

    /**
     * Handles Remove Selected button click.
     * Requirements: REQ-002.2, Task 45
     */
    private void handleRemoveSelected() {
        logger.debug("Remove Selected button clicked");
        List<String> selectedFileIds = fileListView.getSelectedFileIds();
        if (selectedFileIds.isEmpty()) {
            return;
        }

        // Check if conversions are in progress
        if (controller.isConversionInProgress()) {
            showConfirmDialog(
                    "Remove Files",
                    "Conversions are in progress. Are you sure you want to remove selected files?",
                    confirmed -> {
                        if (confirmed) {
                            removeFiles(selectedFileIds);
                        }
                    });
        } else {
            // No conversion in progress, confirm removal
            showConfirmDialog(
                    "Remove Files",
                    "Remove " + selectedFileIds.size() + " selected file(s)?",
                    confirmed -> {
                        if (confirmed) {
                            removeFiles(selectedFileIds);
                        }
                    });
        }
    }

    /**
     * Helper method to remove files from the list.
     * 
     * @param fileIds List of file IDs to remove
     */
    private void removeFiles(List<String> fileIds) {
        try {
            controller.handleRemoveFiles(fileIds);
            updateFileList();
            showStatus(fileIds.size() + " file(s) removed");
        } catch (FileOperationException e) {
            logger.error("Failed to remove files", e);
            showErrorDialog("Remove Files Error", e.getMessage());
        }
    }

    /**
     * Handles Clear All button click.
     */
    private void handleClearAll() {
        logger.debug("Clear All button clicked");
        controller.handleClearFiles();
        updateFileList();
    }

    /**
     * Handles Convert button click.
     */
    private void handleConvert() {
        logger.debug("Convert button clicked");

        // Initialize batch tracking counters
        // Requirement REQ-004.2: Track overall batch progress
        totalFilesInBatch = controller.getFileList().size();
        completedFilesInBatch = 0;
        successfulFilesInBatch = 0;
        failedFilesInBatch = 0;
        batchCompleted = false;

        logger.info("Starting conversion batch with {} files", totalFilesInBatch);

        // Show progress view and disable convert button
        showProgressView();
        convertButton.setSensitive(false);
        // Can Pause
        showPauseButton();

        controller.handleStartConversion();
        showStatus("Conversion Started");
    }

    /**
     * Handles Pause button click.
     */
    private void handlePause() {
        logger.debug("Pause button clicked");
        controller.handlePauseConversion();
        showResumeButton();
        showStatus("Conversion Paused");
    }

    /**
     * Handles Resume button click.
     */
    private void handleResume() {
        logger.debug("Resume button clicked");
        controller.handleResumeConversion();
        showPauseButton();
        showStatus("Conversion Resumed");
    }

    /**
     * Handles Cancel button click.
     * Requirements: REQ-004.2, Task 48
     */
    private void handleCancel() {
        logger.debug("Cancel button clicked");
        showConfirmDialog(
                "Cancel Conversion",
                "Are you sure you want to cancel all conversions?",
                confirmed -> {
                    if (confirmed) {
                        controller.handleCancelConversion();
                        showStatus("Conversions cancelled");
                    }
                });
    }

    /**
     * Handles window close event.
     * Returns true to prevent window from closing (when showing confirmation
     * dialog),
     * or false to allow window to close.
     * Requirements: REQ-001.2, REQ-005.1, REQ-005.2, REQ-005.3, Task 49, Task 50
     */
    private boolean handleClose() {
        logger.info("Window close requested");

        // If shutdown is already in progress, allow the window to close
        if (shutdownInProgress) {
            logger.debug("Shutdown already in progress, allowing window to close");
            return false;
        }

        // Check if conversions are in progress
        if (controller.isConversionInProgress()) {
            showConfirmDialog(
                    "Close Application",
                    "Conversions are in progress. Are you sure you want to exit?\nAll active conversions will be cancelled.",
                    confirmed -> {
                        if (confirmed) {
                            shutdownInProgress = true;
                            // Shutdown in background thread to avoid blocking GTK main loop
                            // Then close window once shutdown is complete
                            new Thread(() -> {
                                try {
                                    logger.info("Shutting down controller and cancelling conversions...");
                                    controller.shutdown(true); // Force shutdown to cancel conversions
                                    logger.info("Controller shutdown complete, closing window");
                                    // Close window on GTK main thread
                                    GLib.idleAdd(0, () -> {
                                        close();
                                        return false; // Don't repeat
                                    });
                                } catch (Exception e) {
                                    logger.error("Error during shutdown", e);
                                    // Still try to close window
                                    GLib.idleAdd(0, () -> {
                                        close();
                                        return false;
                                    });
                                }
                            }, "shutdown-thread").start();
                        }
                    });
            // Return true to prevent window from closing until user responds to dialog
            return true;
        } else {
            // No conversions in progress, shutdown directly
            shutdownInProgress = true;
            controller.shutdown();
            // Return false to allow window to close
            return false;
        }
    }

    // ===== Public API for Controller and Actions =====

    /**
     * Triggers the Add Files action (called from keyboard shortcut or button).
     * Requirements: REQ-102.2 - Keyboard shortcuts
     */
    public void triggerAddFiles() {
        handleAddFiles();
    }

    /**
     * Triggers the Settings action (called from keyboard shortcut or button).
     * Requirements: REQ-102.2 - Keyboard shortcuts
     */
    public void triggerSettings() {
        handleSettings();
    }

    /**
     * Triggers the Convert action (called from keyboard shortcut or button).
     * Requirements: REQ-102.2 - Keyboard shortcuts
     */
    public void triggerConvert() {
        handleConvert();
    }

    /**
     * Triggers the Remove Selected action (called from keyboard shortcut or
     * button).
     * Requirements: REQ-102.2 - Keyboard shortcuts
     */
    public void triggerRemoveSelected() {
        handleRemoveSelected();
    }

    /**
     * Triggers the Select All action (called from keyboard shortcut or button).
     * Requirements: REQ-102.2 - Keyboard shortcuts
     */
    public void triggerSelectAll() {
        if (fileListView != null) {
            fileListView.selectAll();
        }
    }

    /**
     * Updates the file list display with current files from controller.
     * Thread-safe: Can be called from background threads.
     */
    public void updateFileList() {
        GLib.idleAdd(0, () -> {
            List<ConversionFile> files = controller.getFileList();
            fileListView.setFiles(files);

            // Update file count and size labels
            long totalSize = files.stream().mapToLong(ConversionFile::size).sum();
            fileCountLabel.setLabel(files.size() + " files selected");
            totalSizeLabel.setLabel(formatBytes(totalSize));

            // Enable/disable Convert and Clear All buttons based on file list
            boolean hasFiles = !files.isEmpty();
            convertButton.setSensitive(hasFiles);
            clearAllButton.setSensitive(hasFiles);

            return false; // Don't repeat
        });
    }

    /**
     * Updates the status of a single file in the list.
     * Thread-safe: Can be called from background threads.
     * 
     * @param fileId the file ID
     * @param file   the updated file information
     */
    public void updateFileStatus(String fileId, ConversionFile file) {
        GLib.idleAdd(0, () -> {
            fileListView.updateFile(fileId, file);
            return false;
        });
    }

    /**
     * Shows a status message in the status bar.
     * Thread-safe: Can be called from background threads.
     * 
     * @param message the message to display
     */
    public void showStatus(String message) {
        GLib.idleAdd(0, () -> {
            statusBarLabel.setLabel(message);
            logger.debug("Status: {}", message);
            return false;
        });
    }

    /**
     * Shows the progress view.
     */
    public void showProgressView() {
        GLib.idleAdd(0, () -> {
            progressView.show();

            // Disable file management buttons during conversion
            addFilesButton.setSensitive(false);
            addFolderButton.setSensitive(false);
            removeSelectedButton.setSensitive(false);
            clearAllButton.setSensitive(false);
            convertButton.setSensitive(false);

            // Enable progress control buttons during conversion
            pauseButton.setSensitive(true);
            resumeButton.setSensitive(true);
            cancelButton.setSensitive(true);

            return false;
        });
    }

    /**
     * Hides the progress view.
     */
    public void hideProgressView() {
        GLib.idleAdd(0, () -> {
            // progressView.hide();

            // Re-enable file management buttons
            addFilesButton.setSensitive(true);
            addFolderButton.setSensitive(true);
            removeSelectedButton.setSensitive(true);

            // Only re-enable Convert and Clear All if there are files
            boolean hasFiles = !controller.getFileList().isEmpty();
            clearAllButton.setSensitive(hasFiles);
            convertButton.setSensitive(hasFiles);

            // Disable progress control buttons when conversion is not in progress
            pauseButton.setSensitive(false);
            resumeButton.setSensitive(false);
            cancelButton.setSensitive(false);

            return false;
        });
    }

    /**
     * Updates the progress display.
     * Thread-safe: Can be called from background threads.
     * 
     * @param currentFile          the current file being converted (1-based)
     * @param totalFiles           the total number of files
     * @param overallProgress      the overall progress (0.0 to 1.0)
     * @param timeRemainingSeconds estimated time remaining in seconds
     * @param speed                conversion speed (e.g., "2.5 MB/s")
     */
    public void updateProgress(int currentFile, int totalFiles, double overallProgress,
            long timeRemainingSeconds, String speed) {
        GLib.idleAdd(0, () -> {
            progressView.updateProgress(currentFile, totalFiles, overallProgress,
                    timeRemainingSeconds, speed);
            return false;
        });
    }

    /**
     * Updates the progress for a specific file during conversion.
     * Thread-safe: Can be called from background threads.
     * 
     * Requirement REQ-004.2: Per-file progress updates
     * 
     * @param fileId   the file ID
     * @param progress the conversion progress for this file
     */
    public void updateFileProgress(String fileId, ConversionProgress progress) {
        GLib.idleAdd(0, () -> {
            // Update progress view with per-file progress
            progressView.updateFileProgress(fileId, progress);

            // Update file list view with updated file
            // Note: The controller has already updated the file with both progress and
            // status,
            // so we use the file as-is without further modification to preserve status
            // changes
            Optional<ConversionFile> fileOpt = controller.getFile(fileId);
            if (fileOpt.isPresent()) {
                fileListView.updateFile(fileId, fileOpt.get());
            }

            logger.debug("File progress updated: {} - {}%", fileId, progress.percentage());
            return false;
        });
    }

    /**
     * Updates a file with the conversion result (success or failure).
     * Thread-safe: Can be called from background threads.
     * 
     * Requirement REQ-004.2: Per-file completion updates and batch completion
     * notification
     * 
     * @param fileId the file ID
     * @param result the conversion result
     */
    public void updateFileResult(String fileId, ConversionResult result) {
        GLib.idleAdd(0, () -> {
            // Get the updated file from controller (status should be updated by controller)
            Optional<ConversionFile> fileOpt = controller.getFile(fileId);
            if (fileOpt.isPresent()) {
                ConversionFile file = fileOpt.get();
                // Update file list view
                fileListView.updateFile(fileId, file);

                // Track completion for batch progress
                completedFilesInBatch++;
                if (result.success()) {
                    successfulFilesInBatch++;
                    logger.info("Conversion completed successfully: {} ({}/{} files)",
                            fileId, completedFilesInBatch, totalFilesInBatch);
                    showStatus("Conversion completed: " + result.outputPath().map(Path::toString).orElse(""));
                } else if (result.isCancelled()) {
                    cancelledFilesInBatch++;
                    logger.info("Conversion cancelled: {} ({}/{} files)",
                            fileId, completedFilesInBatch, totalFilesInBatch);
                    showStatus("Conversion cancelled: " + fileId);
                } else {
                    failedFilesInBatch++;
                    logger.error("Conversion failed: {} - {} ({}/{} files)",
                            fileId, result.errorMessage().orElse("Unknown error"),
                            completedFilesInBatch, totalFilesInBatch);
                    showStatus("Conversion failed: " + result.errorMessage().orElse("Unknown error"));
                }

                // Note: Overall progress bar, speed, and time remaining are now updated
                // automatically via the batch progress listener (see updateBatchProgress
                // method)

                // Check if batch is complete
                if (!batchCompleted && completedFilesInBatch >= totalFilesInBatch && totalFilesInBatch > 0) {
                    batchCompleted = true;
                    onBatchComplete();
                }
            } else {
                logger.warn("File not found for result update: {}", fileId);
            }

            return false;
        });
    }

    /**
     * Updates the batch progress display (speed and time remaining).
     * Called by ApplicationWorkflowController when batch progress is updated.
     * 
     * Requirement REQ-004.3: Batch progress tracking with speed and ETA
     * 
     * @param batchProgress the current batch progress
     */
    public void updateBatchProgress(BatchProgress batchProgress) {
        GLib.idleAdd(0, () -> {
            progressView.updateOverallProgress(batchProgress);
            return false;
        });
    }

    /**
     * Called when all files in the batch have completed conversion.
     * Shows notification, saves session state, and resets UI state.
     * 
     * Requirement REQ-004.2: Batch completion notification and state saving
     */
    private void onBatchComplete() {
        logger.info("Batch conversion complete: {} successful, {} failed, {} cancelled out of {} total files",
                successfulFilesInBatch, failedFilesInBatch, cancelledFilesInBatch, totalFilesInBatch);

        // Show completion notification
        String message;
        if (cancelledFilesInBatch == totalFilesInBatch) {
            // All files were cancelled
            message = "All conversions cancelled";
        } else if (failedFilesInBatch == 0 && cancelledFilesInBatch == 0) {
            message = String.format("All %d files converted successfully!", totalFilesInBatch);
        } else if (successfulFilesInBatch == 0) {
            message = String.format("All %d files failed to convert.", totalFilesInBatch);
        } else {
            // Mixed results
            StringBuilder sb = new StringBuilder("Conversion complete: ");
            sb.append(String.format("%d successful", successfulFilesInBatch));
            if (failedFilesInBatch > 0) {
                sb.append(String.format(", %d failed", failedFilesInBatch));
            }
            if (cancelledFilesInBatch > 0) {
                sb.append(String.format(", %d cancelled", cancelledFilesInBatch));
            }
            sb.append(String.format(" out of %d files.", totalFilesInBatch));
            message = sb.toString();
        }

        showStatus(message);

        // Show GTK notification (application-level notification)
        // Requirement REQ-004.2: Show notification on batch completion
        showCompletionNotification(message);

        // Reset batch tracking counters
        totalFilesInBatch = 0;
        completedFilesInBatch = 0;
        successfulFilesInBatch = 0;
        failedFilesInBatch = 0;
        cancelledFilesInBatch = 0;

        // Update UI state - hide progress, enable convert button
        hideProgressView();
        convertButton.setSensitive(true);

        // Let controller know conversion is complete (it will save session state)
        // The controller already handles this via its completion listener
        logger.debug("Batch completion notification sent to user");
    }

    /**
     * Shows a desktop notification for batch completion.
     * 
     * @param message the notification message
     */
    private void showCompletionNotification(String message) {
        try {
            // Use notify-send command to show desktop notification
            Process process = Runtime.getRuntime()
                    .exec(new String[] { "notify-send", "Open Media Converter", message,
                            "--icon=open-media-converter" });
            logger.info("Desktop notification sent via notify-send: {}", message);
        } catch (Exception e) {
            logger.error("Failed to show desktop notification", e);
        }
    }

    /**
     * Shows the pause button and hides the resume button.
     */
    private void showPauseButton() {
        GLib.idleAdd(0, () -> {
            pauseButton.setVisible(true);
            resumeButton.setVisible(false);
            return false;
        });
    }

    /**
     * Shows the resume button and hides the pause button.
     */
    private void showResumeButton() {
        GLib.idleAdd(0, () -> {
            pauseButton.setVisible(false);
            resumeButton.setVisible(true);
            return false;
        });
    }

    // ===== Window State Management =====

    /**
     * Saves the current window state.
     * 
     * @return the captured window state
     */
    public WindowState saveState() {
        // GTK 4 doesn't provide getDefaultWidth/Height after window is created
        // Store the last set values
        int width = currentWidth;
        int height = currentHeight;

        // Note: GTK 4 doesn't provide get_position() easily
        // We'll store 0,0 for now and handle positioning differently
        int x = 0;
        int y = 0;

        boolean maximized = isMaximized();
        boolean fullscreen = isFullscreen();

        logger.debug("Saving window state: {}x{} maximized={} fullscreen={}",
                width, height, maximized, fullscreen);

        return new WindowState(width, height, x, y, maximized, fullscreen);
    }

    /**
     * Restores window state from saved state.
     * 
     * @param state the window state to restore
     */
    public void restoreState(WindowState state) {
        if (state == null) {
            logger.debug("No window state to restore, using defaults");
            return;
        }

        logger.debug("Restoring window state: {}x{} maximized={} fullscreen={}",
                state.width(), state.height(),
                state.maximized(), state.fullscreen());

        // Restore window size
        if (state.width() > 0 && state.height() > 0) {
            currentWidth = state.width();
            currentHeight = state.height();
            setDefaultSize(state.width(), state.height());
        }

        // Restore maximized state
        if (state.maximized()) {
            maximize();
        }

        // Restore fullscreen state
        if (state.fullscreen()) {
            fullscreen();
        }
    }

    // ===== Utility Methods =====

    /**
     * Formats bytes into human-readable format.
     * 
     * @param bytes the number of bytes
     * @return formatted string (e.g., "1.5 MB")
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    // ===== Dialog Helper Methods =====

    /**
     * Shows a file chooser dialog for selecting multiple files to convert.
     * Uses GTK 4's FileDialog API (replaces deprecated FileChooserDialog).
     * Requirements: REQ-001, Task 36, Task 37
     * 
     * @param callback Callback to receive list of selected file paths
     */
    @SuppressWarnings("deprecation")
    private void showFileChooserDialog(java.util.function.Consumer<List<String>> callback) {
        GLib.idleAdd(0, () -> {
            try {
                // Use no-arg constructor to avoid varargs FFI crash
                // The varargs constructor FileChooserDialog(title, parent, action, btn1, resp1,
                // btn2, resp2)
                // causes SIGSEGV in java-gi FFI layer due to improper varargs handling
                org.gnome.gtk.FileChooserDialog dialog = new org.gnome.gtk.FileChooserDialog();

                // Set properties after construction
                dialog.setTitle("Select Files to Convert");
                dialog.setTransientFor(this);
                dialog.setAction(org.gnome.gtk.FileChooserAction.OPEN);
                dialog.setModal(true);
                dialog.setSelectMultiple(true);

                // Add buttons separately (safer than varargs constructor)
                dialog.addButton("_Cancel", org.gnome.gtk.ResponseType.CANCEL.getValue());
                dialog.addButton("_Open", org.gnome.gtk.ResponseType.ACCEPT.getValue());

                addFileFilters(dialog);
                dialog.show();

                dialog.onResponse(responseId -> {
                    List<String> selectedPaths = new java.util.ArrayList<>();
                    if (responseId == org.gnome.gtk.ResponseType.ACCEPT.getValue()) {
                        var files = dialog.getFiles();
                        if (files != null) {
                            for (int i = 0; i < files.getNItems(); i++) {
                                var file = (org.gnome.gio.File) files.getItem(i);
                                if (file != null) {
                                    String path = file.getPath();
                                    if (path != null && !path.isBlank()) {
                                        selectedPaths.add(path);
                                    }
                                }
                            }
                        }
                    }
                    dialog.destroy();
                    callback.accept(selectedPaths);
                });

            } catch (Exception e) {
                logger.error("Error showing file chooser dialog", e);
                callback.accept(List.of());
            }
            return false;
        });
    }

    /**
     * Shows a folder chooser dialog for selecting an output directory.
     * Uses FileChooserDialog temporarily until FileDialog API is stable.
     * Requirements: REQ-001, Task 36, Task 37
     * 
     * @param callback Callback to receive the selected folder path (null if
     *                 cancelled)
     */
    @SuppressWarnings("deprecation")
    private void showFolderChooserDialog(java.util.function.Consumer<String> callback) {
        GLib.idleAdd(0, () -> {
            try {
                // Use no-arg constructor to avoid varargs FFI crash
                org.gnome.gtk.FileChooserDialog dialog = new org.gnome.gtk.FileChooserDialog();

                // Set properties after construction
                dialog.setTitle("Select Output Folder");
                dialog.setTransientFor(this);
                dialog.setAction(org.gnome.gtk.FileChooserAction.SELECT_FOLDER);
                dialog.setModal(true);

                // Add buttons separately (safer than varargs constructor)
                dialog.addButton("_Cancel", org.gnome.gtk.ResponseType.CANCEL.getValue());
                dialog.addButton("_Select", org.gnome.gtk.ResponseType.ACCEPT.getValue());

                dialog.show();

                dialog.onResponse(responseId -> {
                    String selectedPath = null;
                    if (responseId == org.gnome.gtk.ResponseType.ACCEPT.getValue()) {
                        var file = dialog.getFile();
                        if (file != null) {
                            selectedPath = file.getPath();
                        }
                    }
                    dialog.destroy();
                    callback.accept(selectedPath);
                });

            } catch (Exception e) {
                logger.error("Error showing folder chooser dialog", e);
                callback.accept(null);
            }
            return false;
        });
    }

    /**
     * Shows a confirmation dialog with Yes/No buttons.
     * Requirements: Task 45, Task 48, Task 49, Task 50
     * 
     * @param title    Dialog title
     * @param message  Dialog message
     * @param callback Callback to receive user response (true=Yes, false=No/Cancel)
     */
    @SuppressWarnings("deprecation")
    private void showConfirmDialog(String title, String message, java.util.function.Consumer<Boolean> callback) {
        GLib.idleAdd(0, () -> {
            try {
                org.gnome.gtk.MessageDialog dialog = new org.gnome.gtk.MessageDialog(
                        this,
                        org.gnome.gtk.DialogFlags.MODAL,
                        org.gnome.gtk.MessageType.QUESTION,
                        org.gnome.gtk.ButtonsType.YES_NO,
                        message);

                dialog.setTitle(title);
                dialog.show();

                dialog.onResponse(responseId -> {
                    boolean confirmed = (responseId == org.gnome.gtk.ResponseType.YES.getValue());
                    dialog.destroy();
                    callback.accept(confirmed);
                });
            } catch (Exception e) {
                logger.error("Error showing confirm dialog", e);
                callback.accept(false);
            }
            return false;
        });
    }

    /**
     * Shows an error dialog with the given title and message.
     * Uses the new ErrorDialog helper for consistent error UI.
     * Requirements: REQ-007.1
     *
     * @param title   The dialog title
     * @param message The error message
     */
    private void showErrorDialog(String title, String message) {
        ErrorDialog.showError(this, title, message);
    }

    /**
     * Shows the file details dialog for a given file ID.
     * 
     * Requirement REQ-FL-2.1: Display detailed file information and conversion
     * results
     * Task 55: Implement showFileDetailsDialog handler
     * 
     * @param fileId The file ID to show details for
     */
    private void showFileDetailsDialog(String fileId) {
        logger.debug("Opening file details dialog for file: {}", fileId);

        // Get the conversion file from the controller
        Optional<ConversionFile> fileOptional = controller.getFile(fileId);
        if (fileOptional.isEmpty()) {
            logger.warn("File not found for ID: {}", fileId);
            showErrorDialog("File Not Found", "Unable to find file details for the selected file.");
            return;
        }

        ConversionFile file = fileOptional.get();

        // Get the conversion result (may be null if not yet converted)
        ConversionResult result = controller.getConversionResult(fileId);

        // Create and show the dialog
        FileDetailsDialog dialog = new FileDetailsDialog(this);
        dialog.show(file, result);

        logger.debug("File details dialog displayed for: {}", file.path().getFileName());
    }

    /**
     * Handles the "Open File Location" context menu action.
     * Opens the system file manager and navigates to the file's location.
     * 
     * <p>
     * Requirements:
     * - REQ-FL-3.1: Context menu integration
     * - REQ-FL-3.2: Open file location in system file manager
     * - REQ-FL-3.3: Navigate to output path for completed files
     * 
     * Task 61: Implement handleOpenFileLocation handler
     * </p>
     * 
     * @param fileId The ID of the file to open in file manager
     */
    private void handleOpenFileLocation(String fileId) {
        logger.debug("Opening file location for file: {}", fileId);

        try {
            // Get the conversion file from the controller
            Optional<ConversionFile> fileOptional = controller.getFile(fileId);
            if (fileOptional.isEmpty()) {
                logger.warn("File not found for ID: {}", fileId);
                showErrorDialog("File Not Found", "Unable to find the selected file.");
                return;
            }

            ConversionFile file = fileOptional.get();

            // Determine which path to open based on file status
            Path pathToOpen = determinePathToOpen(file);

            // Get FileHandler from controller and open the file manager
            org.omc.service.FileHandler fileHandler = controller.getFileHandler();
            fileHandler.openInFileManager(pathToOpen);

            logger.info("Opened file location: {}", pathToOpen);
            showStatus("Opened file location: " + pathToOpen.getParent().getFileName());

        } catch (java.nio.file.AccessDeniedException e) {
            // Task 68: Handle permission denied error
            logger.error("Permission denied accessing file location", e);
            showErrorDialog(
                    "Permission Denied",
                    "You do not have permission to access this location.\n\nPath: " + e.getFile());
        } catch (java.io.FileNotFoundException e) {
            // Task 66: Handle file not found error
            logger.error("File not found when opening location", e);
            showErrorDialog(
                    "File Not Found",
                    "The file could not be found. It may have been moved or deleted.\n\n" + e.getMessage());
        } catch (java.io.IOException e) {
            // Task 67: Handle file manager not available error
            logger.error("Failed to open file manager", e);
            showErrorDialog(
                    "Cannot Open File Manager",
                    "Could not open the file manager. Please ensure xdg-open or a file manager " +
                            "(Nautilus, Dolphin, Thunar, Nemo, PCManFM) is installed.\n\nError: " + e.getMessage());
        } catch (Exception e) {
            // Catch-all for unexpected errors
            logger.error("Unexpected error opening file location", e);
            showErrorDialog(
                    "Error",
                    "An unexpected error occurred while trying to open the file location.\n\n" + e.getMessage());
        }
    }

    /**
     * Determines which path to open in the file manager based on file status.
     * 
     * <p>
     * Logic (Task 62):
     * - PENDING, IN_PROGRESS: Open source path
     * - COMPLETED: Open output path (fallback to source if output not available)
     * - FAILED, CANCELLED: Open source path
     * </p>
     * 
     * <p>
     * Requirements:
     * - REQ-FL-3.2: Navigate to source for pending/in-progress/failed files
     * - REQ-FL-3.3: Navigate to output for completed files
     * </p>
     * 
     * @param file The conversion file
     * @return The path to open in the file manager
     * @throws java.io.FileNotFoundException if neither source nor output path
     *                                       exists
     */
    private Path determinePathToOpen(ConversionFile file) throws java.io.FileNotFoundException {
        Path pathToOpen;

        switch (file.status()) {
            case COMPLETED:
                // For completed files, prefer output path
                pathToOpen = file.outputPath().orElse(file.path());
                logger.debug("Determined path for COMPLETED file: {} (output: {})",
                        pathToOpen, file.outputPath().isPresent());
                break;

            case PENDING:
            case IN_PROGRESS:
            case FAILED:
            case CANCELLED:
            default:
                // For all other statuses, use source path
                pathToOpen = file.path();
                logger.debug("Determined path for {} file: {}", file.status(), pathToOpen);
                break;
        }

        // Verify the path exists
        if (!java.nio.file.Files.exists(pathToOpen)) {
            // If output path doesn't exist but source does, fall back to source
            if (file.status() == ConversionStatus.COMPLETED &&
                    file.outputPath().isPresent() &&
                    java.nio.file.Files.exists(file.path())) {
                logger.warn("Output path does not exist, falling back to source: {}", file.path());
                pathToOpen = file.path();
            } else {
                throw new java.io.FileNotFoundException(
                        "File does not exist: " + pathToOpen);
            }
        }

        return pathToOpen;
    }

    /**
     * Adds file filters for all supported formats to a file chooser dialog.
     * Requirements: REQ-006.1, REQ-006.2, REQ-006.3, REQ-006.4
     * 
     * @param dialog The file chooser dialog
     */
    @SuppressWarnings("deprecation")
    private void addFileFilters(org.gnome.gtk.FileChooserDialog dialog) {
        // "All Supported Files" filter
        org.gnome.gtk.FileFilter allFilter = new org.gnome.gtk.FileFilter();
        allFilter.setName("All Supported Files");
        for (FileFormat format : FileFormat.values()) {
            if (format != FileFormat.UNKNOWN && format.isValidInput()) {
                for (String ext : format.getExtensions()) {
                    allFilter.addPattern("*." + ext);
                }
            }
        }
        dialog.addFilter(allFilter);

        // Category-specific filters
        addCategoryFilter(dialog, FormatCategory.VIDEO, "Video Files");
        addCategoryFilter(dialog, FormatCategory.AUDIO, "Audio Files");
        addCategoryFilter(dialog, FormatCategory.IMAGE, "Image Files");
        addCategoryFilter(dialog, FormatCategory.DOCUMENT, "Document Files");

        // "All Files" filter
        org.gnome.gtk.FileFilter allFilesFilter = new org.gnome.gtk.FileFilter();
        allFilesFilter.setName("All Files");
        allFilesFilter.addPattern("*");
        dialog.addFilter(allFilesFilter);
    }

    /**
     * Adds a file filter for a specific format category.
     * 
     * @param dialog   The file chooser dialog
     * @param category The format category
     * @param name     The filter display name
     */
    @SuppressWarnings("deprecation")
    private void addCategoryFilter(org.gnome.gtk.FileChooserDialog dialog, FormatCategory category, String name) {
        FileFormat[] formats = FileFormat.getFormatsByCategory(category);
        if (formats.length == 0) {
            return;
        }

        org.gnome.gtk.FileFilter filter = new org.gnome.gtk.FileFilter();
        filter.setName(name);
        for (FileFormat format : formats) {
            for (String ext : format.getExtensions()) {
                filter.addPattern("*." + ext);
            }
        }
        dialog.addFilter(filter);
    }

    /**
     * Restores the file list sort state from the controller's saved state.
     * This method must be called AFTER controller.initialize() to ensure
     * the application state has been loaded from disk.
     * 
     * Requirement REQ-FL-4.5: Restore sort state on application startup
     * 
     * @see ApplicationWorkflowController#initialize()
     * @see ApplicationWorkflowController#getSavedSortState()
     */
    public void restoreSortStateFromController() {
        FileListSortState savedSortState = controller.getSavedSortState();
        if (savedSortState != null && savedSortState.isSorted()) {
            logger.debug("Restoring saved sort state: {}", savedSortState);
            fileListView.restoreSortState(savedSortState);
        } else {
            logger.debug("No sort state to restore (null or unsorted)");
        }
    }
}
