package org.omc.controller;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.omc.model.ConversionFile;
import org.omc.model.FileSettingsOverride;
import org.omc.model.FormatCategory;
import org.omc.model.PresetsBySection;
import org.omc.model.SectionPreset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Preset workflow concern extracted from {@link ApplicationWorkflowController}
 * (audit: god-object decomposition).
 *
 * <p>
 * Owns the preset-application workflow: listing compatible presets for a
 * selection, applying a preset as a per-file {@link FileSettingsOverride}, and
 * clearing overrides. Stateless besides its two manager collaborators, which
 * makes it safe to call from any thread the controller's other concerns use.
 * </p>
 *
 * <p>
 * Requirements: REQ-3.2 (preset application), REQ-3.3 (context menu preset
 * application), REQ-5.2 (apply &lt; 500ms for 100 files).
 * </p>
 */
class PresetWorkflowHandler {

    private static final Logger logger = LoggerFactory.getLogger(PresetWorkflowHandler.class);

    private final FileManager fileManager;
    private final SettingsManager settingsManager;

    PresetWorkflowHandler(FileManager fileManager, SettingsManager settingsManager) {
        this.fileManager = Objects.requireNonNull(fileManager, "fileManager cannot be null");
        this.settingsManager = Objects.requireNonNull(settingsManager, "settingsManager cannot be null");
    }

    /**
     * Applies a section preset to the specified files as per-file overrides.
     *
     * <p>
     * Validation rules: fileIds non-empty; every ID resolves in the
     * FileManager; all files share one format category; that category matches
     * the preset's category. On success each file is replaced via
     * {@link ConversionFile#withSettingsOverride(FileSettingsOverride)}.
     * </p>
     *
     * @param fileIds List of file IDs to apply the preset to
     * @param preset  The section preset to apply
     * @throws IllegalArgumentException if files have mixed categories, a file's
     *                                  category doesn't match the preset
     *                                  category, fileIds is empty, or a file ID
     *                                  is not found
     */
    void applyPresetToFiles(List<String> fileIds, SectionPreset preset) {
        Objects.requireNonNull(fileIds, "fileIds cannot be null");
        Objects.requireNonNull(preset, "preset cannot be null");

        if (fileIds.isEmpty()) {
            throw new IllegalArgumentException("fileIds cannot be empty");
        }

        logger.debug("Applying preset '{}' to {} files", preset.name(), fileIds.size());

        // Performance tracking: REQ-5.2 - Target < 500ms for 100 files
        long startTime = System.nanoTime();

        // Resolve every ID strictly: a stale ID must fail loudly rather than
        // silently applying the preset to only the resolvable files
        List<ConversionFile> files = new java.util.ArrayList<>(fileIds.size());
        List<String> missingIds = new java.util.ArrayList<>();
        for (String fileId : fileIds) {
            Optional<ConversionFile> fileOpt = fileManager.getFile(fileId);
            if (fileOpt.isEmpty()) {
                missingIds.add(fileId);
            } else {
                files.add(fileOpt.get());
            }
        }
        if (!missingIds.isEmpty()) {
            logger.warn("Cannot apply preset: file ID(s) not found: {}", missingIds);
            throw new IllegalArgumentException("File ID(s) not found: " + missingIds);
        }

        // Validate all files have the same FormatCategory
        java.util.Set<FormatCategory> categories = files.stream()
                .map(file -> file.format().getCategory())
                .collect(Collectors.toSet());

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
     * Clears custom settings (preset overrides) from the specified files so
     * they revert to the global section settings for their category.
     *
     * @param fileIds List of file IDs to clear overrides from
     */
    void clearPresetFromFiles(List<String> fileIds) {
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
     * Returns presets compatible with the given selection.
     *
     * <p>
     * Return conditions: empty list if fileIds is empty, any ID is not found,
     * files have mixed categories, or the category is UNKNOWN.
     * </p>
     *
     * @param fileIds List of file IDs to get available presets for
     * @return List of presets for the file's category, or empty list if
     *         mixed/invalid
     */
    List<SectionPreset> getAvailablePresetsForFiles(List<String> fileIds) {
        Objects.requireNonNull(fileIds, "fileIds cannot be null");

        if (fileIds.isEmpty()) {
            logger.debug("getAvailablePresetsForFiles called with empty list");
            return List.of();
        }

        logger.debug("Getting available presets for {} file(s)", fileIds.size());

        // Get ConversionFile objects from FileManager
        List<ConversionFile> files = new java.util.ArrayList<>();
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
                .collect(Collectors.toSet());

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

        List<SectionPreset> presets = presetsBySection.getPresetsForCategory(category);
        logger.debug("Found {} presets for category {}", presets.size(), category);
        return presets;
    }
}
