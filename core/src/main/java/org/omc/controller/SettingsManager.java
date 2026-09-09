package org.omc.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.omc.core.ConfigurationManager;
import org.omc.core.ValidationEngine;
import org.omc.exception.InvalidSettingsException;
import org.omc.model.AudioSettings;
import org.omc.model.ConversionSettings;
import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;
import org.omc.model.FormatCategory;
import org.omc.model.ImageSettings;
import org.omc.model.PresetsBySection;
import org.omc.model.SectionPreset;
import org.omc.model.SettingsPreset;
import org.omc.model.ValidationResult;
import org.omc.model.VideoSettings;
import org.omc.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages application settings and preset persistence and retrieval.
 * 
 * <p>
 * <b>Settings Management:</b>
 * </p>
 * <p>
 * Handles loading, saving, validation, and defaults for conversion settings.
 * Implements atomic writes with temporary files to prevent corruption and
 * provides
 * automatic backup and recovery for corrupted settings files.
 * </p>
 * 
 * <p>
 * <b>Preset Management:</b>
 * </p>
 * <p>
 * Manages section-based presets organized by format category (video, audio,
 * image,
 * document). Handles automatic migration from old preset format to new
 * {@link PresetsBySection}
 * structure with backup of old files.
 * </p>
 * 
 * <p>
 * <b>Backward Compatibility:</b>
 * </p>
 * <p>
 * Automatically migrates settings and presets from older formats:
 * <ul>
 * <li>Old global {@code outputFormat} → Section-based format settings</li>
 * <li>Old {@code List<SettingsPreset>} → {@link PresetsBySection}
 * structure</li>
 * <li>Creates timestamped backups of old files during migration</li>
 * </ul>
 * </p>
 * 
 * <p>
 * Requirements:
 * </p>
 * <ul>
 * <li>REQ-003.1: Settings management and persistence</li>
 * <li>REQ-002.6: Preset creation and management</li>
 * <li>REQ-002.7: Section-based preset organization</li>
 * <li>REQ-005.3: Atomic file operations for data integrity</li>
 * <li>REQ-5.1: Backward compatibility with old formats</li>
 * </ul>
 * 
 * @see ConversionSettings
 * @see PresetsBySection
 * @see SectionPreset
 */
public class SettingsManager {
    private static final Logger logger = LoggerFactory.getLogger(SettingsManager.class);

    private static final String BACKUP_SUFFIX = ".backup";
    private static final String TEMP_SUFFIX = ".tmp";

    private final ConfigurationManager configurationManager;
    private final ValidationEngine validationEngine;
    private final AtomicReference<ConversionSettings> currentSettings;

    /**
     * Creates a new SettingsManager.
     *
     * @param configurationManager Configuration manager for paths
     * @param validationEngine     Validation engine for settings validation
     */
    public SettingsManager(ConfigurationManager configurationManager, ValidationEngine validationEngine) {
        this.configurationManager = Objects.requireNonNull(configurationManager, "configurationManager cannot be null");
        this.validationEngine = Objects.requireNonNull(validationEngine, "validationEngine cannot be null");
        this.currentSettings = new AtomicReference<>();
        logger.debug("SettingsManager initialized");
    }

    /**
     * Loads settings from disk.
     * If the settings file doesn't exist or is corrupted, returns default settings.
     * Corrupted files are backed up with timestamp.
     * 
     * Requirement REQ-005.3: Settings persistence
     *
     * @return Loaded or default settings
     */
    public ConversionSettings loadSettings() {
        Path settingsPath = configurationManager.getSettingsFilePath();
        logger.info("Loading settings from: {}", settingsPath);

        // If settings file doesn't exist, use defaults
        if (!Files.exists(settingsPath)) {
            logger.info("Settings file does not exist, using defaults");
            ConversionSettings defaults = createDefaultSettings();
            currentSettings.set(defaults);
            return defaults;
        }

        try {
            // Read settings from file
            ConversionSettings settings = JsonUtils.readJsonFile(
                    settingsPath.toFile(),
                    ConversionSettings.class);

            // Validate loaded settings
            if (settings == null) {
                logger.warn("Settings file is empty or null, using defaults");
                backupCorruptedSettings(settingsPath);
                ConversionSettings defaults = createDefaultSettings();
                currentSettings.set(defaults);
                return defaults;
            }

            // Validate settings using basic validation
            if (!settings.isValid()) {
                logger.warn("Loaded settings are invalid, using defaults");
                backupCorruptedSettings(settingsPath);
                ConversionSettings defaults = createDefaultSettings();
                currentSettings.set(defaults);
                return defaults;
            }

            // Additional validation using ValidationEngine
            ValidationResult validationResult = validationEngine.validateSettings(settings);
            if (validationResult.isFailure()) {
                logger.warn("Settings validation failed: {}", validationResult.getErrors());
                backupCorruptedSettings(settingsPath);
                ConversionSettings defaults = createDefaultSettings();
                currentSettings.set(defaults);
                return defaults;
            }

            if (validationResult.hasWarnings()) {
                logger.warn("Settings loaded with warnings: {}", validationResult.getWarnings());
            }

            currentSettings.set(settings);
            logger.info("Settings loaded successfully");
            return settings;

        } catch (IOException e) {
            logger.error("Error reading settings file: {}", settingsPath, e);
            backupCorruptedSettings(settingsPath);
            ConversionSettings defaults = createDefaultSettings();
            currentSettings.set(defaults);
            return defaults;
        }
    }

    /**
     * Saves settings to disk using atomic write operation.
     * Writes to temporary file first, then renames to prevent corruption.
     * 
     * Requirement REQ-005.3: Atomic settings persistence
     *
     * @param settings Settings to save
     * @throws InvalidSettingsException if settings are invalid
     * @throws IOException              if save operation fails
     */
    public synchronized void saveSettings(ConversionSettings settings) throws InvalidSettingsException, IOException {
        Objects.requireNonNull(settings, "settings cannot be null");
        logger.debug("Saving settings");

        // Validate settings before saving
        ValidationResult validationResult = validationEngine.validateSettings(settings);
        if (validationResult.isFailure()) {
            String errorMessage = "Cannot save invalid settings: " +
                    String.join(", ", validationResult.getErrors());
            logger.error(errorMessage);
            throw new InvalidSettingsException(errorMessage, "settings");
        }

        if (validationResult.hasWarnings()) {
            logger.warn("Saving settings with warnings: {}", validationResult.getWarnings());
        }

        Path settingsPath = configurationManager.getSettingsFilePath();
        Path tempPath = Path.of(settingsPath.toString() + TEMP_SUFFIX);

        try {
            // Write to temporary file
            JsonUtils.writeJsonFile(settings, tempPath.toFile());
            logger.debug("Settings written to temporary file: {}", tempPath);

            // Atomically rename temporary file to final location
            Files.move(tempPath, settingsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            logger.info("Settings saved successfully to: {}", settingsPath);

            // Update current settings
            currentSettings.set(settings);

        } catch (IOException e) {
            logger.error("Error saving settings to: {}", settingsPath, e);

            // Clean up temporary file if it exists
            try {
                if (Files.exists(tempPath)) {
                    Files.delete(tempPath);
                }
            } catch (IOException cleanupError) {
                logger.warn("Failed to delete temporary settings file: {}", tempPath, cleanupError);
            }

            throw e;
        }
    }

    /**
     * Gets the current settings.
     * If settings have not been loaded, loads them first.
     * 
     * Requirement REQ-003.1: Settings access
     *
     * @return Current settings
     */
    public ConversionSettings getCurrentSettings() {
        ConversionSettings current = currentSettings.get();
        if (current == null) {
            current = loadSettings();
        }
        return current;
    }

    /**
     * Updates the current settings with validation.
     * Validates settings before updating and saving.
     * 
     * Requirement REQ-003.1: Settings management
     *
     * @param settings New settings
     * @throws InvalidSettingsException if settings are invalid
     * @throws IOException              if save operation fails
     */
    public void updateSettings(ConversionSettings settings) throws InvalidSettingsException, IOException {
        Objects.requireNonNull(settings, "settings cannot be null");
        logger.debug("Updating settings");

        // Save settings (includes validation)
        saveSettings(settings);

        // Update current settings reference
        currentSettings.set(settings);
        logger.info("Settings updated successfully");
    }

    /**
     * Resets settings to factory defaults.
     * Creates default settings and saves them to disk.
     * 
     * Requirement REQ-003.1: Settings reset
     *
     * @throws IOException if save operation fails
     */
    public void resetToDefaults() throws IOException {
        logger.info("Resetting settings to defaults");

        ConversionSettings defaults = createDefaultSettings();

        try {
            saveSettings(defaults);
            currentSettings.set(defaults);
            logger.info("Settings reset to defaults successfully");
        } catch (InvalidSettingsException e) {
            // This should never happen with default settings
            logger.error("Default settings are invalid - this is a bug", e);
            throw new IllegalStateException("Default settings are invalid", e);
        }
    }

    /**
     * Creates default settings with sensible values.
     * Ensures the default output directory exists.
     * 
     * Requirement REQ-003.1: Default settings
     *
     * @return Default settings
     */
    public static ConversionSettings createDefaultSettings() {
        // Use user's home directory as default output directory
        Path defaultOutputDir = Path.of(System.getProperty("user.home"), "Converted");

        try {
            Files.createDirectories(defaultOutputDir);
        } catch (IOException e) {
            // If we can't create the default directory, use a fallback in temp
            defaultOutputDir = Path.of(System.getProperty("java.io.tmpdir"), "Converted");
            try {
                Files.createDirectories(defaultOutputDir);
            } catch (IOException ex) {
                // Last resort: use home directory directly
                defaultOutputDir = Path.of(System.getProperty("user.home"));
            }
        }

        return ConversionSettings.builder()
                .outputFormat(FileFormat.MP4) // Default to MP4 for video conversions
                .outputDirectory(defaultOutputDir)
                .overwriteExisting(false)
                .createSubdirectory(false)
                .parallelConversions(4)
                .build();
    }

    /**
     * Backs up a corrupted settings file.
     * Renames the file with .backup suffix and timestamp.
     * 
     * Requirement REQ-005.3: Corrupted file handling
     *
     * @param settingsPath Path to corrupted settings file
     */
    private void backupCorruptedSettings(Path settingsPath) {
        try {
            if (Files.exists(settingsPath)) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSSSSSSSS"));
                Path backupPath = Path.of(settingsPath.toString() + BACKUP_SUFFIX + "_" + timestamp);

                Files.move(settingsPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                logger.warn("Corrupted settings file backed up to: {}", backupPath);
            }
        } catch (IOException e) {
            logger.error("Failed to backup corrupted settings file: {}", settingsPath, e);
        }
    }

    /**
     * Checks if settings file exists.
     *
     * @return true if settings file exists
     */
    public boolean settingsFileExists() {
        return Files.exists(configurationManager.getSettingsFilePath());
    }

    /**
     * Gets the settings file path.
     *
     * @return Settings file path
     */
    public Path getSettingsFilePath() {
        return configurationManager.getSettingsFilePath();
    }

    // ========== Preset Management ==========
    // Requirement REQ-003.2: Format presets

    /**
     * Gets all available presets (built-in and custom).
     * Loads custom presets from presets.json and combines with built-in presets.
     *
     * @return List of all presets
     */
    public List<SettingsPreset> getPresets() {
        logger.debug("Loading presets");

        List<SettingsPreset> allPresets = new ArrayList<>();

        // Add built-in presets first
        allPresets.addAll(createBuiltInPresets());

        // Load custom presets from file
        Path presetsPath = configurationManager.getConfigDirectory().resolve("presets.json");
        if (Files.exists(presetsPath)) {
            try {
                PresetContainer container = JsonUtils.readJsonFile(presetsPath.toFile(), PresetContainer.class);
                if (container != null && container.presets != null) {
                    allPresets.addAll(container.presets);
                    logger.debug("Loaded {} custom presets", container.presets.size());
                }
            } catch (IOException e) {
                logger.warn("Failed to load custom presets, using built-in only", e);
            }
        }

        logger.info("Loaded {} total presets ({} built-in)", allPresets.size(), createBuiltInPresets().size());
        return allPresets;
    }

    /**
     * Gets a preset by name.
     *
     * @param name Preset name
     * @return Preset if found
     * @throws IllegalArgumentException if preset not found
     */
    public SettingsPreset getPreset(String name) {
        Objects.requireNonNull(name, "name cannot be null");

        return getPresets().stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Preset not found: " + name));
    }

    /**
     * Saves a custom preset.
     * Built-in presets cannot be overwritten.
     *
     * @param preset Preset to save
     * @throws IOException              if save operation fails
     * @throws IllegalArgumentException if trying to overwrite a built-in preset
     */
    public void savePreset(SettingsPreset preset) throws IOException {
        Objects.requireNonNull(preset, "preset cannot be null");
        logger.debug("Saving preset: {}", preset.name());

        // Validate preset
        if (!preset.isValid()) {
            throw new IllegalArgumentException("Invalid preset: settings are not valid");
        }

        // Check if trying to overwrite built-in preset
        if (preset.builtIn()) {
            throw new IllegalArgumentException("Cannot save built-in preset: " + preset.name());
        }

        // Read-merge-write cycle is guarded so concurrent saves cannot interleave
        // (consistent with the savePresetsAtomic / savePresetsBySection monitors)
        synchronized (this) {
            // Load existing custom presets, preserving unknown keys (e.g. section presets)
            Path presetsPath = configurationManager.getConfigDirectory().resolve("presets.json");
            List<SettingsPreset> customPresets = new ArrayList<>();
            ObjectNode existingRoot = null;

            if (Files.exists(presetsPath)) {
                try {
                    JsonNode tree = JsonUtils.getObjectMapper().readTree(presetsPath.toFile());
                    JsonNode presetsNode = tree != null && tree.isObject() ? tree.get("presets") : tree;
                    if (tree != null && tree.isObject()) {
                        existingRoot = (ObjectNode) tree;
                    }
                    if (presetsNode != null && presetsNode.isArray()) {
                        customPresets.addAll(JsonUtils.getObjectMapper().convertValue(
                                presetsNode, new TypeReference<List<SettingsPreset>>() {
                                }));
                    }
                } catch (IOException | IllegalArgumentException e) {
                    logger.warn("Failed to load existing presets, backing up before overwrite", e);
                    if (!backupPresetsFile(presetsPath)) {
                        throw new IOException(
                                "Failed to back up existing presets file; aborting save to avoid data loss", e);
                    }
                }
            }

            // Check for built-in preset name conflict
            List<String> builtInNames = createBuiltInPresets().stream()
                    .map(SettingsPreset::name)
                    .toList();
            if (builtInNames.contains(preset.name())) {
                throw new IllegalArgumentException("Preset name conflicts with built-in preset: " + preset.name());
            }

            // Remove existing preset with same name and add new one
            customPresets.removeIf(p -> p.name().equals(preset.name()));
            customPresets.add(preset);

            // Save to file, preserving coexisting section-based presets
            if (existingRoot != null) {
                existingRoot.set("presets", JsonUtils.getObjectMapper().valueToTree(customPresets));
                savePresetsAtomic(existingRoot, presetsPath);
            } else {
                PresetContainer container = new PresetContainer(customPresets);
                savePresetsAtomic(container, presetsPath);
            }
        }

        logger.info("Preset saved successfully: {}", preset.name());
    }

    /**
     * Deletes a custom preset.
     * Built-in presets cannot be deleted.
     *
     * @param name Preset name
     * @throws IOException              if save operation fails
     * @throws IllegalArgumentException if preset is built-in or not found
     */
    public void deletePreset(String name) throws IOException {
        Objects.requireNonNull(name, "name cannot be null");
        logger.debug("Deleting preset: {}", name);

        // Check if it's a built-in preset
        List<String> builtInNames = createBuiltInPresets().stream()
                .map(SettingsPreset::name)
                .toList();
        if (builtInNames.contains(name)) {
            throw new IllegalArgumentException("Cannot delete built-in preset: " + name);
        }

        // Load existing custom presets
        Path presetsPath = configurationManager.getConfigDirectory().resolve("presets.json");
        if (!Files.exists(presetsPath)) {
            throw new IllegalArgumentException("Preset not found: " + name);
        }

        List<SettingsPreset> customPresets = new ArrayList<>();
        try {
            PresetContainer container = JsonUtils.readJsonFile(presetsPath.toFile(), PresetContainer.class);
            if (container != null && container.presets != null) {
                customPresets.addAll(container.presets);
            }
        } catch (IOException e) {
            throw new IOException("Failed to load presets file", e);
        }

        // Remove the preset
        boolean removed = customPresets.removeIf(p -> p.name().equals(name));
        if (!removed) {
            throw new IllegalArgumentException("Preset not found: " + name);
        }

        // Save updated list
        PresetContainer container = new PresetContainer(customPresets);
        savePresetsAtomic(container, presetsPath);

        logger.info("Preset deleted successfully: {}", name);
    }

    /**
     * Creates built-in presets for common use cases.
     * Requirement REQ-003.2: Built-in presets
     *
     * @return List of built-in presets
     */
    private List<SettingsPreset> createBuiltInPresets() {
        Path defaultOutputDir = Path.of(System.getProperty("user.home"), "Converted");

        List<SettingsPreset> presets = new ArrayList<>();

        // High Quality preset
        presets.add(SettingsPreset.createBuiltInPreset(
                "High Quality",
                "Best quality output with minimal compression. Larger file sizes.",
                ConversionSettings.builder()
                        .outputFormat(FileFormat.MP4)
                        .outputDirectory(defaultOutputDir)
                        .overwriteExisting(false)
                        .createSubdirectory(false)
                        .parallelConversions(2) // Lower parallelism for high quality
                        .build()));

        // Balanced preset
        presets.add(SettingsPreset.createBuiltInPreset(
                "Balanced",
                "Good balance between quality and file size. Recommended for most uses.",
                ConversionSettings.builder()
                        .outputFormat(FileFormat.MP4)
                        .outputDirectory(defaultOutputDir)
                        .overwriteExisting(false)
                        .createSubdirectory(false)
                        .parallelConversions(4)
                        .build()));

        // Small Size preset
        presets.add(SettingsPreset.createBuiltInPreset(
                "Small Size",
                "Optimized for small file sizes. Lower quality but good compression.",
                ConversionSettings.builder()
                        .outputFormat(FileFormat.MP4)
                        .outputDirectory(defaultOutputDir)
                        .overwriteExisting(false)
                        .createSubdirectory(false)
                        .parallelConversions(4)
                        .build()));

        // Web Optimized preset
        presets.add(SettingsPreset.createBuiltInPreset(
                "Web Optimized",
                "Optimized for web streaming and fast loading. H.264/AAC codec.",
                ConversionSettings.builder()
                        .outputFormat(FileFormat.MP4)
                        .outputDirectory(defaultOutputDir)
                        .overwriteExisting(false)
                        .createSubdirectory(false)
                        .parallelConversions(4)
                        .build()));

        return presets;
    }

    /**
     * Saves presets atomically using temporary file and rename.
     * Synchronized because the temporary file name is fixed.
     *
     * @param value       presets value to save (container or JSON tree)
     * @param presetsPath Target file path
     * @throws IOException if save operation fails
     */
    private synchronized void savePresetsAtomic(Object value, Path presetsPath) throws IOException {
        // Write to temporary file first
        Path tempPath = Path.of(presetsPath.toString() + TEMP_SUFFIX);

        try {
            JsonUtils.writeJsonFile(value, tempPath.toFile());

            // Atomic rename
            Files.move(tempPath, presetsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            // Clean up temp file if it exists
            if (Files.exists(tempPath)) {
                try {
                    Files.delete(tempPath);
                } catch (IOException cleanupEx) {
                    logger.warn("Failed to delete temp presets file: {}", tempPath, cleanupEx);
                }
            }
            throw e;
        }
    }

    /**
     * Container class for JSON serialization of presets.
     * Tolerates unknown properties so old-API reads of new-format
     * {@link PresetsBySection} files do not fail.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PresetContainer {
        public List<SettingsPreset> presets;

        public PresetContainer() {
            this.presets = new ArrayList<>();
        }

        public PresetContainer(List<SettingsPreset> presets) {
            this.presets = new ArrayList<>(presets);
        }
    }

    // ========== Section-Based Preset Management (New Format) ==========
    // Requirement REQ-2.7: Section-based preset organization

    /**
     * Loads presets organized by section from disk.
     * 
     * <p>
     * This method attempts to load presets in the new {@link PresetsBySection}
     * format.
     * If the file doesn't exist or loading fails, it attempts automatic migration
     * from
     * the old {@code List<SettingsPreset>} format. Migration creates a timestamped
     * backup
     * of the old file before converting to the new structure.
     * </p>
     * 
     * <p>
     * <b>Migration Process:</b>
     * </p>
     * <ol>
     * <li>Try loading as {@link PresetsBySection}</li>
     * <li>If fails, load as old {@code List<SettingsPreset>} format</li>
     * <li>Categorize old presets by output format category</li>
     * <li>Create backup of old file with timestamp</li>
     * <li>Convert to {@link PresetsBySection} and save in new format</li>
     * </ol>
     * 
     * <p>
     * Requirements:
     * </p>
     * <ul>
     * <li>REQ-2.7: Preset loading with automatic migration</li>
     * <li>REQ-5.1: Backward compatibility with old format</li>
     * </ul>
     * 
     * @return Presets organized by section, or empty if none exist
     * @see #migrateOldPresetsFormat() for migration implementation
     * @see #addSectionPreset(SectionPreset) to add new presets
     * @see PresetsBySection
     */
    public PresetsBySection loadPresetsBySection() {
        Path presetsPath = configurationManager.getConfigDirectory().resolve("presets.json");

        logger.debug("Loading presets by section from: {}", presetsPath);

        // If presets file doesn't exist, return empty
        if (!Files.exists(presetsPath)) {
            logger.info("Presets file does not exist, returning empty");
            return PresetsBySection.empty();
        }

        try {
            // Try loading as new PresetsBySection format
            PresetsBySection presets = JsonUtils.readJsonFile(
                    presetsPath.toFile(),
                    PresetsBySection.class);

            if (presets != null) {
                logger.info("Successfully loaded presets by section");
                return presets;
            } else {
                logger.warn("Presets file returned null, attempting migration");
                return migrateOldPresetsFormat();
            }

        } catch (IOException e) {
            // Failed to load as new format, try migration from old format
            logger.info("Failed to load as PresetsBySection format, attempting migration: {}", e.getMessage());
            return migrateOldPresetsFormat();
        }
    }

    /**
     * Migrates presets from old {@code List<SettingsPreset>} format to new
     * {@link PresetsBySection} format.
     * 
     * <p>
     * This method handles the conversion of legacy preset files to the new
     * section-based structure. It accepts both legacy shapes: bare
     * {@code List<SettingsPreset>} arrays and {@code {"presets":[...]}}
     * containers. Section presets coexisting in the same file are preserved.
     * Each old preset is categorized by its output format's category and
     * converted to corresponding {@link SectionPreset} instances. Invalid or
     * malformed presets are skipped with warnings logged.
     * </p>
     * 
     * <p>
     * <b>Migration Steps:</b>
     * </p>
     * <ol>
     * <li>Create backup of the old file with timestamp suffix
     * (.old.TIMESTAMP.bak)</li>
     * <li>Parse defensively: bare array or legacy container</li>
     * <li>Validate each preset and extract output format category</li>
     * <li>Create {@link SectionPreset} for each valid preset based on category</li>
     * <li>Save new {@link PresetsBySection} structure atomically</li>
     * </ol>
     * 
     * <p>
     * <b>Error Handling:</b>
     * </p>
     * <ul>
     * <li>Invalid presets are skipped with warnings</li>
     * <li>Presets without output format or category are skipped</li>
     * <li>If the backup attempt fails, migration is aborted before any
     * destructive rewrite (fail-closed)</li>
     * <li>Returns empty {@link PresetsBySection} if migration fails completely</li>
     * </ul>
     * 
     * <p>
     * Requirements:
     * </p>
     * <ul>
     * <li>REQ-2.7: Preset format migration</li>
     * <li>REQ-5.1: Backward compatibility with old format</li>
     * </ul>
     * 
     * @return Migrated presets organized by section, or empty on failure
     * @see #loadPresetsBySection() for the main entry point
     * @see #savePresetsBySection(PresetsBySection) for atomic save operation
     */
    private PresetsBySection migrateOldPresetsFormat() {
        Path presetsPath = configurationManager.getConfigDirectory().resolve("presets.json");

        logger.info("Starting migration from old preset format at: {}", presetsPath);

        // Backup before parsing so unreadable content is never lost.
        // Fail closed: never rewrite the file when the backup attempt failed.
        if (!backupPresetsFile(presetsPath)) {
            logger.error("Aborting preset migration: backup failed for {}", presetsPath);
            return PresetsBySection.empty();
        }

        try {
            // Parse defensively: accept bare [...] arrays and {"presets":[...]} containers
            JsonNode root = JsonUtils.getObjectMapper().readTree(presetsPath.toFile());
            JsonNode legacyNode = root != null && root.isObject() ? root.get("presets") : root;

            List<SettingsPreset> oldPresets = List.of();
            if (legacyNode != null && legacyNode.isArray()) {
                mapLegacyGlobalOutputFormats(legacyNode);
                oldPresets = JsonUtils.getObjectMapper().convertValue(
                        legacyNode, new TypeReference<List<SettingsPreset>>() {
                        });
            }

            // Preserve section presets coexisting with legacy data (mixed files)
            PresetsBySection existingSections = root != null && root.isObject()
                    ? readExistingSections(root)
                    : PresetsBySection.empty();

            if (oldPresets.isEmpty() && existingSections.totalPresetCount() == 0) {
                logger.warn("No recognizable presets found to migrate");
                return PresetsBySection.empty();
            }

            logger.info("Loaded {} old presets for migration", oldPresets.size());

            // Categorize presets by output format category
            List<SectionPreset> videoPresets = new ArrayList<>(existingSections.videoPresets());
            List<SectionPreset> audioPresets = new ArrayList<>(existingSections.audioPresets());
            List<SectionPreset> imagePresets = new ArrayList<>(existingSections.imagePresets());
            List<SectionPreset> documentPresets = new ArrayList<>(existingSections.documentPresets());

            for (SettingsPreset oldPreset : oldPresets) {
                if (!oldPreset.isValid()) {
                    logger.warn("Skipping invalid old preset: {}", oldPreset.name());
                    continue;
                }

                ConversionSettings settings = oldPreset.settings();
                FileFormat outputFormat = settings.outputFormat();

                if (outputFormat == null) {
                    logger.warn("Skipping preset '{}' - no output format", oldPreset.name());
                    continue;
                }

                FormatCategory category = outputFormat.getCategory();

                // Create SectionPreset based on category
                try {
                    SectionPreset sectionPreset = switch (category) {
                        case VIDEO -> {
                            VideoSettings videoSettings = settings.videoSettings();
                            if (videoSettings == null) {
                                logger.warn("Skipping preset '{}' - VIDEO category but no videoSettings",
                                        oldPreset.name());
                                yield null;
                            }
                            yield SectionPreset.forVideo(
                                    oldPreset.name(),
                                    oldPreset.description(),
                                    videoSettings,
                                    oldPreset.builtIn());
                        }
                        case AUDIO -> {
                            AudioSettings audioSettings = settings.audioSettings();
                            if (audioSettings == null) {
                                logger.warn("Skipping preset '{}' - AUDIO category but no audioSettings",
                                        oldPreset.name());
                                yield null;
                            }
                            yield SectionPreset.forAudio(
                                    oldPreset.name(),
                                    oldPreset.description(),
                                    audioSettings,
                                    oldPreset.builtIn());
                        }
                        case IMAGE -> {
                            ImageSettings imageSettings = settings.imageSettings();
                            if (imageSettings == null) {
                                logger.warn("Skipping preset '{}' - IMAGE category but no imageSettings",
                                        oldPreset.name());
                                yield null;
                            }
                            yield SectionPreset.forImage(
                                    oldPreset.name(),
                                    oldPreset.description(),
                                    imageSettings,
                                    oldPreset.builtIn());
                        }
                        case DOCUMENT -> {
                            DocumentSettings documentSettings = settings.documentSettings();
                            if (documentSettings == null) {
                                logger.warn("Skipping preset '{}' - DOCUMENT category but no documentSettings",
                                        oldPreset.name());
                                yield null;
                            }
                            yield SectionPreset.forDocument(
                                    oldPreset.name(),
                                    oldPreset.description(),
                                    documentSettings,
                                    oldPreset.builtIn());
                        }
                        case UNKNOWN -> {
                            logger.warn("Skipping preset '{}' - UNKNOWN category", oldPreset.name());
                            yield null;
                        }
                    };

                    if (sectionPreset != null) {
                        switch (category) {
                            case VIDEO -> videoPresets.add(sectionPreset);
                            case AUDIO -> audioPresets.add(sectionPreset);
                            case IMAGE -> imagePresets.add(sectionPreset);
                            case DOCUMENT -> documentPresets.add(sectionPreset);
                            default -> {
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    logger.error("Error migrating preset '{}': {}", oldPreset.name(), e.getMessage(), e);
                }
            }

            // Create new PresetsBySection
            PresetsBySection newPresets = new PresetsBySection(
                    videoPresets,
                    audioPresets,
                    imagePresets,
                    documentPresets);

            // Save in new format
            savePresetsBySection(newPresets);

            logger.info("Successfully migrated {} presets: {} video, {} audio, {} image, {} document",
                    oldPresets.size(), videoPresets.size(), audioPresets.size(),
                    imagePresets.size(), documentPresets.size());

            return newPresets;

        } catch (IOException | IllegalArgumentException e) {
            logger.error("Failed to migrate old presets format", e);
            return PresetsBySection.empty();
        }
    }

    /**
     * Maps the legacy global {@code outputFormat} field onto the matching
     * section settings before binding, so presets written by versions prior to
     * the section-based architecture survive migration.
     *
     * <p>
     * For each legacy preset whose {@code settings} node carries a textual
     * {@code outputFormat} but lacks the corresponding section settings, a
     * default-valid section settings object with that format is injected.
     * Existing section settings are never overwritten. Presets with unknown
     * formats are left untouched (they are skipped later by validation).
     * </p>
     *
     * @param legacyPresets array node of legacy {@link SettingsPreset} objects
     */
    private void mapLegacyGlobalOutputFormats(JsonNode legacyPresets) {
        for (JsonNode presetNode : legacyPresets) {
            JsonNode settingsNode = presetNode.get("settings");
            if (!(settingsNode instanceof ObjectNode settingsObject)) {
                continue;
            }

            JsonNode formatNode = settingsObject.get("outputFormat");
            if (formatNode == null || !formatNode.isTextual()) {
                continue;
            }

            FileFormat legacyFormat;
            try {
                legacyFormat = FileFormat.valueOf(formatNode.asText());
            } catch (IllegalArgumentException e) {
                logger.warn("Cannot map legacy global outputFormat '{}': unknown format",
                        formatNode.asText());
                continue;
            }

            JsonNode sectionSettings;
            switch (legacyFormat.getCategory()) {
                case VIDEO -> sectionSettings = JsonUtils.getObjectMapper().valueToTree(
                        VideoSettings.builder().outputFormat(legacyFormat).build());
                case AUDIO -> sectionSettings = JsonUtils.getObjectMapper().valueToTree(
                        AudioSettings.builder().outputFormat(legacyFormat).build());
                case IMAGE -> sectionSettings = JsonUtils.getObjectMapper().valueToTree(
                        ImageSettings.builder().outputFormat(legacyFormat).build());
                case DOCUMENT -> sectionSettings = JsonUtils.getObjectMapper().valueToTree(
                        DocumentSettings.builder().outputFormat(legacyFormat).build());
                default -> {
                    continue;
                }
            }

            String sectionField = sectionFieldName(legacyFormat.getCategory());
            JsonNode existingSection = settingsObject.get(sectionField);
            if (existingSection == null || existingSection.isNull()) {
                settingsObject.set(sectionField, sectionSettings);
                logger.debug("Mapped legacy global outputFormat '{}' to {} for migration",
                        legacyFormat, sectionField);
            }
        }
    }

    /**
     * Gets the JSON field name of the section settings for a category.
     *
     * @param category the format category
     * @return the settings field name (e.g. {@code videoSettings})
     */
    private String sectionFieldName(FormatCategory category) {
        return switch (category) {
            case VIDEO -> "videoSettings";
            case AUDIO -> "audioSettings";
            case IMAGE -> "imageSettings";
            case DOCUMENT -> "documentSettings";
            default -> "settings";
        };
    }

    /**
     * Backs up a presets file with a timestamped .old.TIMESTAMP.bak suffix.
     *
     * @param presetsPath path to the presets file
     * @return true if a backup was created or there is nothing to back up,
     *         false if the backup attempt failed
     */
    private boolean backupPresetsFile(Path presetsPath) {
        if (!Files.exists(presetsPath)) {
            return true;
        }
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSSSSSSSS"));
            Path backupPath = Path.of(presetsPath.toString() + ".old." + timestamp + ".bak");
            Files.copy(presetsPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Backed up old presets to: {}", backupPath);
            return true;
        } catch (IOException e) {
            logger.error("Failed to backup old presets file: {}", presetsPath, e);
            return false;
        }
    }

    /**
     * Extracts per-category section presets from a JSON object node.
     * Unreadable sections are skipped with warnings.
     *
     * @param root object node possibly containing section preset arrays
     * @return presets organized by section, empty where absent or unreadable
     */
    private PresetsBySection readExistingSections(JsonNode root) {
        return new PresetsBySection(
                readSectionPresetList(root.get("videoPresets")),
                readSectionPresetList(root.get("audioPresets")),
                readSectionPresetList(root.get("imagePresets")),
                readSectionPresetList(root.get("documentPresets")));
    }

    /**
     * Converts a JSON array node into a list of section presets.
     *
     * @param node array node, or null/non-array for an empty list
     * @return converted list, or empty list if the node is unreadable
     */
    private List<SectionPreset> readSectionPresetList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return new ArrayList<>();
        }
        try {
            return JsonUtils.getObjectMapper().convertValue(
                    node, new TypeReference<List<SectionPreset>>() {
                    });
        } catch (IllegalArgumentException e) {
            logger.warn("Skipping unreadable section presets during migration: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Saves presets by section to disk.
     * 
     * <p>
     * Uses atomic write operation with temp file and rename to prevent corruption.
     * Pretty-prints JSON for human readability.
     * </p>
     * 
     * <p>
     * Requirement REQ-2.7: Preset storage and persistence
     * </p>
     * <p>
     * Requirement REQ-5.3: Atomic write for data integrity
     * </p>
     * 
     * @param presets The presets to save
     * @throws IOException if write operation fails
     */
    private synchronized void savePresetsBySection(PresetsBySection presets) throws IOException {
        Path configDir = configurationManager.getConfigDirectory();
        Path presetsPath = configDir.resolve("presets.json");
        Path tempPath = configDir.resolve("presets.json.tmp");

        logger.debug("Saving presets to: {}", presetsPath);

        try {
            // Write to temporary file first
            JsonUtils.writeJsonFile(presets, tempPath.toFile());

            // Atomic move to final location
            Files.move(tempPath, presetsPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            logger.info("Successfully saved presets by section to: {}", presetsPath);

        } catch (IOException e) {
            // Clean up temp file if it exists
            if (Files.exists(tempPath)) {
                try {
                    Files.delete(tempPath);
                } catch (IOException cleanupError) {
                    logger.warn("Failed to delete temp presets file: {}", tempPath, cleanupError);
                }
            }
            throw e;
        }
    }

    /**
     * Adds a new section preset to the preset collection.
     * 
     * <p>
     * Validates that no preset with the same name (case-insensitive) already exists
     * in the same category. The updated preset collection is saved atomically to
     * disk.
     * </p>
     * 
     * <p>
     * <b>Thread Safety:</b> This method is not thread-safe. If concurrent access is
     * needed, external synchronization should be applied by the caller.
     * </p>
     * 
     * <p>
     * Requirement 2.6: Preset creation and management
     * </p>
     * 
     * @param preset the section preset to add
     * @throws IllegalArgumentException if a preset with the same name already
     *                                  exists in the category,
     *                                  or if the preset category is UNKNOWN
     * @throws IOException              if saving the updated presets fails
     * @see #deleteSectionPreset(String, FormatCategory) to remove presets
     * @see #loadPresetsBySection() to retrieve all presets
     */
    public void addSectionPreset(SectionPreset preset) throws IOException {
        Objects.requireNonNull(preset, "preset cannot be null");

        // Validate category is not UNKNOWN
        if (preset.category() == FormatCategory.UNKNOWN) {
            throw new IllegalArgumentException("Cannot add preset with UNKNOWN category");
        }

        logger.debug("Adding section preset: {} (category: {})", preset.name(), preset.category());

        // Load current presets
        PresetsBySection currentPresets = loadPresetsBySection();

        // Get list for the preset's category
        List<SectionPreset> categoryPresets = new ArrayList<>(
                currentPresets.getPresetsForCategory(preset.category()));

        // Check for duplicate name (case-insensitive)
        boolean duplicateExists = categoryPresets.stream()
                .anyMatch(existing -> existing.name().equalsIgnoreCase(preset.name()));

        if (duplicateExists) {
            logger.warn("Preset with name '{}' already exists in category {}",
                    preset.name(), preset.category());
            throw new IllegalArgumentException(
                    String.format("A preset named '%s' already exists in the %s category",
                            preset.name(), preset.category().name().toLowerCase()));
        }

        // Add preset to list
        categoryPresets.add(preset);

        // Create updated PresetsBySection
        PresetsBySection updatedPresets = replacePresetsForCategory(
                currentPresets, preset.category(), categoryPresets);

        // Save to disk
        savePresetsBySection(updatedPresets);

        logger.info("Successfully added preset '{}' to {} category",
                preset.name(), preset.category());
    }

    /**
     * Deletes a section preset from the preset collection.
     * 
     * <p>
     * Removes the preset with the specified name (case-insensitive match) from
     * the specified category. If the preset is not found, this method logs a
     * warning
     * and returns gracefully without throwing an exception. The updated collection
     * is
     * saved atomically to disk.
     * </p>
     * 
     * <p>
     * <b>Thread Safety:</b> This method is not thread-safe. If concurrent access is
     * needed, external synchronization should be applied by the caller.
     * </p>
     * 
     * <p>
     * Requirement 2.6: Preset deletion
     * </p>
     * 
     * @param name     the name of the preset to delete (case-insensitive)
     * @param category the category containing the preset
     * @throws IllegalArgumentException if category is UNKNOWN
     * @throws IOException              if saving the updated presets fails
     * @see #addSectionPreset(SectionPreset) to add presets
     * @see #loadPresetsBySection() to retrieve all presets
     */
    public void deleteSectionPreset(String name, FormatCategory category) throws IOException {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(category, "category cannot be null");

        // Validate category is not UNKNOWN
        if (category == FormatCategory.UNKNOWN) {
            throw new IllegalArgumentException("Cannot delete preset from UNKNOWN category");
        }

        logger.debug("Deleting section preset: {} from category: {}", name, category);

        // Load current presets
        PresetsBySection currentPresets = loadPresetsBySection();

        // Get list for the category
        List<SectionPreset> categoryPresets = new ArrayList<>(
                currentPresets.getPresetsForCategory(category));

        // Filter out the preset to delete (case-insensitive)
        List<SectionPreset> filteredPresets = categoryPresets.stream()
                .filter(preset -> !preset.name().equalsIgnoreCase(name))
                .toList();

        // Check if any preset was actually removed
        if (filteredPresets.size() == categoryPresets.size()) {
            logger.warn("Preset '{}' not found in category {}", name, category);
            // Don't throw exception, just log warning and return
            return;
        }

        // Create updated PresetsBySection
        PresetsBySection updatedPresets = replacePresetsForCategory(
                currentPresets, category, filteredPresets);

        // Save to disk
        savePresetsBySection(updatedPresets);

        logger.info("Successfully deleted preset '{}' from {} category", name, category);
    }

    /**
     * Creates a new PresetsBySection with the specified category's preset list
     * replaced.
     * All other category lists remain unchanged.
     * 
     * @param presets    the current PresetsBySection
     * @param category   the category to replace
     * @param newPresets the new list of presets for the category
     * @return a new PresetsBySection with the updated category list
     */
    private PresetsBySection replacePresetsForCategory(
            PresetsBySection presets,
            FormatCategory category,
            List<SectionPreset> newPresets) {

        return switch (category) {
            case VIDEO -> new PresetsBySection(
                    newPresets,
                    presets.audioPresets(),
                    presets.imagePresets(),
                    presets.documentPresets());
            case AUDIO -> new PresetsBySection(
                    presets.videoPresets(),
                    newPresets,
                    presets.imagePresets(),
                    presets.documentPresets());
            case IMAGE -> new PresetsBySection(
                    presets.videoPresets(),
                    presets.audioPresets(),
                    newPresets,
                    presets.documentPresets());
            case DOCUMENT -> new PresetsBySection(
                    presets.videoPresets(),
                    presets.audioPresets(),
                    presets.imagePresets(),
                    newPresets);
            case UNKNOWN -> {
                logger.warn("Cannot replace presets for UNKNOWN category");
                yield presets; // Return unchanged
            }
        };
    }
}
