package org.omc.core;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.omc.controller.ApplicationWorkflowController;
import org.omc.controller.FileManager;
import org.omc.controller.SettingsManager;
import org.omc.controller.StateManager;
import org.omc.model.ToolConfiguration;
import org.omc.service.FFmpegService;
import org.omc.service.FileHandler;
import org.omc.service.ImageMagickService;
import org.omc.service.LibreOfficeService;
import org.omc.service.PandocService;
import org.omc.service.ToolDiscovery;

/**
 * Factory class for creating and wiring application dependencies.
 * Provides centralized dependency injection for the application.
 * 
 * <p>
 * This factory creates all core services, managers, and controllers
 * in the correct initialization order, ensuring all dependencies are
 * properly injected.
 * </p>
 * 
 * Requirements:
 * - REQ-001.1: Application initialization and dependency management
 * - All component requirements (delegates to created components)
 */
public class DependencyFactory {
    private static final Logger logger = LoggerFactory.getLogger(DependencyFactory.class);

    // Default configuration
    private static final int DEFAULT_PARALLEL_CONVERSIONS = 4;

    // Singleton instances (created once)
    private ConfigurationManager configManager;
    private FileHandler fileHandler;
    private ValidationEngine validationEngine;
    private ProgressEngine progressEngine;
    private ToolManager toolManager;
    private ConversionEngine conversionEngine;
    private FileManager fileManager;
    private SettingsManager settingsManager;
    private StateManager stateManager;
    private ApplicationWorkflowController controller;

    // Custom configuration directory (optional)
    private final Path customConfigDirectory;

    /**
     * Creates a new dependency factory with default configuration.
     */
    public DependencyFactory() {
        this(null);
    }

    /**
     * Creates a new dependency factory with custom config directory.
     * 
     * @param customConfigDirectory the custom config directory path, or null for
     *                              default
     */
    public DependencyFactory(Path customConfigDirectory) {
        this.customConfigDirectory = customConfigDirectory;
        logger.debug("DependencyFactory created with custom config dir: {}", customConfigDirectory);
    }

    /**
     * Gets the default data directory (~/.local/share/open-media-converter).
     *
     * @return The default data directory path
     */
    private static Path getDefaultDataDirectory() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".local", "share", "open-media-converter");
    }

    /**
     * Gets the default cache directory (~/.cache/open-media-converter).
     *
     * @return The default cache directory path
     */
    private static Path getDefaultCacheDirectory() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".cache", "open-media-converter");
    }

    /**
     * Initializes all application dependencies in the correct order.
     * This method should be called once during application startup.
     * 
     * @return the fully initialized ApplicationWorkflowController
     * @throws IllegalStateException if dependencies have already been initialized
     */
    public ApplicationWorkflowController createApplicationController() {
        logger.info("Initializing application dependencies");

        if (controller != null) {
            throw new IllegalStateException("Dependencies already initialized");
        }

        try {
            // Phase 1: Create foundation services
            createFoundationServices();

            // Phase 2: Create tool services
            createToolServices();

            // Phase 3: Create business logic components
            createBusinessLogicComponents();

            // Phase 4: Create controllers
            createControllers();

            logger.info("Application dependencies initialized successfully");
            return controller;

        } catch (Exception e) {
            logger.error("Failed to initialize application dependencies", e);
            throw new RuntimeException("Dependency initialization failed", e);
        }
    }

    /**
     * Phase 1: Creates foundation services that other components depend on.
     * Order: ConfigurationManager → FileHandler → ValidationEngine → ProgressEngine
     */
    private void createFoundationServices() {
        logger.debug("Creating foundation services");

        // ConfigurationManager - no dependencies
        Path configDir = customConfigDirectory != null ? customConfigDirectory
                : Paths.get(System.getProperty("user.home"), ".config", "open-media-converter");
        Path dataDir = getDefaultDataDirectory();
        Path cacheDir = getDefaultCacheDirectory();
        configManager = new ConfigurationManager(configDir, dataDir, cacheDir);
        logger.debug("Created ConfigurationManager with config dir: {}", configDir);

        // FileHandler - depends on ConfigurationManager
        fileHandler = new FileHandler(configManager);
        logger.debug("Created FileHandler");

        // ValidationEngine - depends on FileHandler
        validationEngine = new ValidationEngine(fileHandler);
        logger.debug("Created ValidationEngine");

        // ProgressEngine - no dependencies
        progressEngine = new ProgressEngine();
        logger.debug("Created ProgressEngine");
    }

    /**
     * Phase 2: Creates tool services (FFmpeg, Pandoc, LibreOffice, ImageMagick).
     * Discovers tool paths and creates service instances.
     * 
     * Requirement REQ-DEP-1: Dependency injection for all services
     */
    private void createToolServices() {
        logger.debug("Creating tool services");

        // Tool discovery - depends on ConfigurationManager
        ToolDiscovery toolDiscovery = new ToolDiscovery(configManager);
        ToolConfiguration toolConfig = toolDiscovery.discoverTools();
        logger.debug("Tool discovery complete");

        // Create tool services with discovered paths
        FFmpegService ffmpegService = new FFmpegService(
                toolConfig.getFfmpegPath(),
                toolConfig.getFfprobePath());
        logger.debug("Created FFmpegService with path: {}", toolConfig.getFfmpegPath());

        PandocService pandocService = new PandocService(toolConfig.getPandocPath());
        logger.debug("Created PandocService with path: {}", toolConfig.getPandocPath());

        LibreOfficeService libreOfficeService = new LibreOfficeService(
                toolConfig.getLibreOfficePath());
        logger.debug("Created LibreOfficeService with path: {}", toolConfig.getLibreOfficePath());

        // Create ImageMagickService if convert binary was found
        ImageMagickService imageMagickService = null;
        if (toolConfig.getConvertPath() != null) {
            imageMagickService = new ImageMagickService(toolConfig.getConvertPath());
            logger.info("ImageMagickService initialized with convert: {}", toolConfig.getConvertPath());
        } else {
            logger.warn("ImageMagickService not initialized - convert binary not found");
        }

        // Create ToolManager with all tool services
        toolManager = new ToolManager(ffmpegService, pandocService, libreOfficeService, imageMagickService);
        logger.debug("Created ToolManager");
    }

    /**
     * Phase 3: Creates business logic components.
     * Creates ConversionEngine with all required dependencies.
     * Uses default parallelism initially - will be updated when settings are
     * loaded.
     */
    private void createBusinessLogicComponents() {
        logger.debug("Creating business logic components");

        // ConversionEngine - depends on ToolManager, ValidationEngine, ProgressEngine,
        // FileHandler
        // Use DEFAULT_PARALLEL_CONVERSIONS initially;
        // ApplicationWorkflowController.initialize()
        // will update this when it loads settings and calls setParallelConversions()
        conversionEngine = new ConversionEngine(
                toolManager,
                validationEngine,
                progressEngine,
                fileHandler,
                DEFAULT_PARALLEL_CONVERSIONS);
        logger.debug("Created ConversionEngine with {} parallel conversions (will be updated from settings)",
                DEFAULT_PARALLEL_CONVERSIONS);
    }

    /**
     * Phase 4: Creates controller layer components.
     * Creates FileManager, SettingsManager, StateManager, and
     * ApplicationWorkflowController.
     */
    private void createControllers() {
        logger.debug("Creating controllers");

        // FileManager - depends on FileHandler, ValidationEngine
        fileManager = new FileManager(fileHandler, validationEngine);
        logger.debug("Created FileManager");

        // SettingsManager - depends on ConfigurationManager, ValidationEngine
        settingsManager = new SettingsManager(configManager, validationEngine);
        logger.debug("Created SettingsManager");

        // StateManager - depends on ConfigurationManager
        stateManager = new StateManager(configManager);
        logger.debug("Created StateManager");

        // ApplicationWorkflowController - depends on all managers and ConversionEngine
        controller = new ApplicationWorkflowController(
                fileManager,
                settingsManager,
                stateManager,
                conversionEngine);
        logger.debug("Created ApplicationWorkflowController");
    }

    /**
     * Gets the ConfigurationManager instance.
     * 
     * @return the configuration manager
     * @throws IllegalStateException if dependencies not yet initialized
     */
    public ConfigurationManager getConfigurationManager() {
        ensureInitialized();
        return configManager;
    }

    /**
     * Gets the FileHandler instance.
     * 
     * @return the file handler
     * @throws IllegalStateException if dependencies not yet initialized
     */
    public FileHandler getFileHandler() {
        ensureInitialized();
        return fileHandler;
    }

    /**
     * Gets the ValidationEngine instance.
     * 
     * @return the validation engine
     * @throws IllegalStateException if dependencies not yet initialized
     */
    public ValidationEngine getValidationEngine() {
        ensureInitialized();
        return validationEngine;
    }

    /**
     * Gets the ProgressEngine instance.
     * 
     * @return the progress engine
     * @throws IllegalStateException if dependencies not yet initialized
     */
    public ProgressEngine getProgressEngine() {
        ensureInitialized();
        return progressEngine;
    }

    /**
     * Gets the ToolManager instance.
     * 
     * @return the tool manager
     * @throws IllegalStateException if dependencies not yet initialized
     */
    public ToolManager getToolManager() {
        ensureInitialized();
        return toolManager;
    }

    /**
     * Gets the ConversionEngine instance.
     * 
     * @return the conversion engine
     * @throws IllegalStateException if dependencies not yet initialized
     */
    public ConversionEngine getConversionEngine() {
        ensureInitialized();
        return conversionEngine;
    }

    /**
     * Gets the FileManager instance.
     * 
     * @return the file manager
     * @throws IllegalStateException if dependencies not yet initialized
     */
    public FileManager getFileManager() {
        ensureInitialized();
        return fileManager;
    }

    /**
     * Gets the SettingsManager instance.
     * 
     * @return the settings manager
     * @throws IllegalStateException if dependencies not yet initialized
     */
    public SettingsManager getSettingsManager() {
        ensureInitialized();
        return settingsManager;
    }

    /**
     * Gets the StateManager instance.
     * 
     * @return the state manager
     * @throws IllegalStateException if dependencies not yet initialized
     */
    public StateManager getStateManager() {
        ensureInitialized();
        return stateManager;
    }

    /**
     * Gets the ApplicationWorkflowController instance.
     * 
     * @return the application workflow controller
     * @throws IllegalStateException if dependencies not yet initialized
     */
    public ApplicationWorkflowController getApplicationWorkflowController() {
        ensureInitialized();
        return controller;
    }

    /**
     * Ensures that dependencies have been initialized.
     * 
     * @throws IllegalStateException if dependencies not yet initialized
     */
    private void ensureInitialized() {
        if (controller == null) {
            throw new IllegalStateException(
                    "Dependencies not initialized. Call createApplicationController() first.");
        }
    }

    /**
     * Shuts down all managed components.
     * Should be called during application shutdown.
     */
    public void shutdown() {
        logger.info("Shutting down dependency factory");

        // Shutdown in reverse order of initialization
        if (conversionEngine != null) {
            try {
                conversionEngine.shutdown();
                logger.debug("ConversionEngine shutdown complete");
            } catch (Exception e) {
                logger.error("Error shutting down ConversionEngine", e);
            }
        }

        // Clear references
        controller = null;
        stateManager = null;
        settingsManager = null;
        fileManager = null;
        conversionEngine = null;
        toolManager = null;
        progressEngine = null;
        validationEngine = null;
        fileHandler = null;
        configManager = null;

        logger.info("Dependency factory shutdown complete");
    }
}
