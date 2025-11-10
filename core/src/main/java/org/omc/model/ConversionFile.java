// filepath: src/main/java/org/omc/model/ConversionFile.java

package org.omc.model;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

import org.omc.controller.ApplicationWorkflowController;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a file in the conversion queue with optional per-file settings
 * overrides.
 * 
 * <p>
 * <b>Per-File Settings Override Model:</b>
 * </p>
 * <p>
 * Each file in the queue can optionally have custom settings that override the
 * global
 * section settings for that file's category. This allows applying presets or
 * custom
 * configuration to individual files or selections without affecting the global
 * settings.
 * </p>
 * 
 * <p>
 * <b>Usage Example:</b>
 * </p>
 * 
 * <pre>{@code
 * // Apply custom settings to a file
 * FileSettingsOverride override = FileSettingsOverride.forVideo(
 *         "High Quality",
 *         videoSettings);
 * ConversionFile customFile = file.withSettingsOverride(override);
 * 
 * // Check if file has custom settings
 * if (customFile.hasCustomSettings()) {
 *     System.out.println("Using preset: " + customFile.settingsOverride().presetName());
 * }
 * 
 * // Clear custom settings to use global defaults
 * ConversionFile defaultFile = customFile.clearSettingsOverride();
 * }</pre>
 * 
 * <p>
 * <b>Immutability:</b>
 * </p>
 * <p>
 * This class is immutable. All modification methods ({@code withStatus()},
 * {@code withProgress()}, {@code withSettingsOverride()}, etc.) return new
 * instances
 * rather than modifying the existing object.
 * </p>
 * 
 * <p>
 * Requirements:
 * </p>
 * <ul>
 * <li>REQ-002.2: File list management with status tracking</li>
 * <li>REQ-3.1: Per-file settings override capability</li>
 * <li>REQ-5.1: Backward compatibility (via @JsonIgnoreProperties)</li>
 * </ul>
 * 
 * @see FileSettingsOverride
 * @see ConversionStatus
 * @see ApplicationWorkflowController#applyPresetToFiles(List, SectionPreset)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ConversionFile {

    private final String id;
    private final Path path;
    private final FileFormat format;
    private final long size; // in bytes
    private final Object metadata; // VideoMetadata, AudioMetadata, ImageMetadata, or DocumentMetadata
    private final FileSettingsOverride settingsOverride; // Requirement REQ-3.1: Per-file settings override
    private final Path outputPath; // Requirement REQ-FL-3.3: Output file path after successful conversion
    private ConversionStatus status;
    private int progress; // 0-100
    private String errorMessage;
    private ConversionProgress progressInfo; // Full progress info including speed (not persisted)

    @JsonCreator
    private ConversionFile(
            @JsonProperty("id") String id,
            @JsonProperty("path") Path path,
            @JsonProperty("format") FileFormat format,
            @JsonProperty("size") long size,
            @JsonProperty("metadata") Object metadata,
            @JsonProperty("settingsOverride") FileSettingsOverride settingsOverride,
            @JsonProperty("outputPath") Path outputPath,
            @JsonProperty("status") ConversionStatus status,
            @JsonProperty("progress") int progress,
            @JsonProperty("errorMessage") String errorMessage) {
        this.id = id;
        this.path = path;
        this.format = format;
        this.size = size;
        this.metadata = metadata;
        this.settingsOverride = settingsOverride;
        this.outputPath = outputPath;
        this.status = status;
        this.progress = progress;
        this.errorMessage = errorMessage;
        this.progressInfo = null; // Not persisted, set at runtime
    }

    /**
     * Creates a new ConversionFile with pending status and no settings override.
     * The outputPath is initially null and will be set after successful conversion.
     */
    public static ConversionFile create(Path path, FileFormat format, long size) {
        return new ConversionFile(
                UUID.randomUUID().toString(),
                path,
                format,
                size,
                null,
                null, // No settings override by default
                null, // No output path yet
                ConversionStatus.PENDING,
                0,
                null);
    }

    @JsonProperty("id")
    public String id() {
        return id;
    }

    @JsonProperty("path")
    public Path path() {
        return path;
    }

    @JsonProperty("format")
    public FileFormat format() {
        return format;
    }

    @JsonProperty("size")
    public long size() {
        return size;
    }

    @JsonProperty("metadata")
    public Object metadata() {
        return metadata;
    }

    @JsonProperty("status")
    public ConversionStatus status() {
        return status;
    }

    @JsonProperty("progress")
    public int progress() {
        return progress;
    }

    @JsonProperty("errorMessage")
    public String errorMessage() {
        return errorMessage;
    }

    /**
     * Returns the full progress information including speed.
     * This is not persisted and is only available during active conversions.
     * 
     * @return ConversionProgress or null if not set
     */
    @JsonIgnore
    public ConversionProgress progressInfo() {
        return progressInfo;
    }

    /**
     * Returns the settings override for this file, if any.
     * 
     * @return Settings override or null if using global section settings
     */
    @JsonProperty("settingsOverride")
    public FileSettingsOverride settingsOverride() {
        return settingsOverride;
    }

    /**
     * Returns the output file path after successful conversion.
     * 
     * <p>
     * This field is populated when a conversion completes successfully and contains
     * the path to the converted output file. It remains null for pending,
     * in-progress,
     * failed, or cancelled conversions.
     * </p>
     * 
     * <p>
     * Requirement REQ-FL-3.3: Output path tracking for "Open File Location" feature
     * </p>
     * 
     * @return Optional containing the output path, or empty if not yet converted
     */
    public java.util.Optional<Path> outputPath() {
        return java.util.Optional.ofNullable(outputPath);
    }

    /**
     * For JSON serialization - returns raw Path.
     * Use {@link #outputPath()} for public API access.
     */
    @JsonProperty("outputPath")
    Path getOutputPathForJson() {
        return outputPath;
    }

    /**
     * Checks if this file has custom settings that override the global section
     * settings.
     * This is a computed property derived from whether settingsOverride is
     * non-null.
     * 
     * @return true if the file has custom settings, false otherwise
     */
    @JsonIgnore
    public boolean hasCustomSettings() {
        return settingsOverride != null;
    }

    /**
     * Creates a copy with updated status.
     */
    public ConversionFile withStatus(ConversionStatus status) {
        ConversionFile copy = new ConversionFile(id, path, format, size, metadata, settingsOverride, outputPath, status,
                progress, errorMessage);
        copy.progressInfo = this.progressInfo; // Preserve progress info
        return copy;
    }

    /**
     * Creates a copy with updated progress.
     */
    public ConversionFile withProgress(int progress) {
        ConversionFile copy = new ConversionFile(id, path, format, size, metadata, settingsOverride, outputPath, status,
                progress, errorMessage);
        copy.progressInfo = this.progressInfo; // Preserve progress info
        return copy;
    }

    /**
     * Creates a copy with updated progress information.
     * This stores the full ConversionProgress including speed.
     * 
     * @param progressInfo the progress information to store
     * @return A new ConversionFile with updated progress info
     */
    public ConversionFile withProgressInfo(ConversionProgress progressInfo) {
        ConversionFile copy = new ConversionFile(id, path, format, size, metadata, settingsOverride, outputPath, status,
                progressInfo != null ? progressInfo.percentage() : progress, errorMessage);
        copy.progressInfo = progressInfo;
        return copy;
    }

    /**
     * Creates a copy with updated error message.
     */
    public ConversionFile withError(String errorMessage) {
        ConversionFile copy = new ConversionFile(id, path, format, size, metadata, settingsOverride, outputPath,
                ConversionStatus.FAILED, progress, errorMessage);
        copy.progressInfo = this.progressInfo; // Preserve progress info
        return copy;
    }

    /**
     * Creates a copy with cancelled status.
     */
    public ConversionFile withCancelled() {
        ConversionFile copy = new ConversionFile(id, path, format, size, metadata, settingsOverride, outputPath,
                ConversionStatus.CANCELLED, progress, "Conversion cancelled by user");
        copy.progressInfo = this.progressInfo; // Preserve progress info
        return copy;
    }

    /**
     * Creates a copy with updated metadata.
     */
    public ConversionFile withMetadata(Object metadata) {
        ConversionFile copy = new ConversionFile(id, path, format, size, metadata, settingsOverride, outputPath, status,
                progress, errorMessage);
        copy.progressInfo = this.progressInfo; // Preserve progress info
        return copy;
    }

    /**
     * Creates a copy with the specified settings override.
     * 
     * <p>
     * This method applies custom settings to this file that will override the
     * global
     * section settings during conversion. This is the primary mechanism for
     * applying
     * presets or custom configuration to individual files.
     * </p>
     * 
     * <p>
     * Requirement REQ-3.1: Per-file settings override application
     * </p>
     * 
     * @param override Settings override to apply (can be null to clear)
     * @return A new ConversionFile instance with the override applied
     * @see #clearSettingsOverride() to remove custom settings
     * @see #hasCustomSettings() to check if file has overrides
     */
    public ConversionFile withSettingsOverride(FileSettingsOverride override) {
        ConversionFile copy = new ConversionFile(id, path, format, size, metadata, override, outputPath, status,
                progress, errorMessage);
        copy.progressInfo = this.progressInfo; // Preserve progress info
        return copy;
    }

    /**
     * Creates a copy with settings override removed.
     * 
     * <p>
     * After clearing, the file will use the global section settings for its
     * category
     * during conversion. This is useful when removing a preset from a file or
     * reverting
     * to default settings.
     * </p>
     * 
     * <p>
     * Requirement REQ-3.3: Clear preset functionality
     * </p>
     * 
     * @return A new ConversionFile instance without custom settings
     * @see #withSettingsOverride(FileSettingsOverride) to apply custom settings
     * @see ApplicationWorkflowController#clearPresetFromFiles(List)
     */
    public ConversionFile clearSettingsOverride() {
        ConversionFile copy = new ConversionFile(id, path, format, size, metadata, null, outputPath, status, progress,
                errorMessage);
        copy.progressInfo = this.progressInfo; // Preserve progress info
        return copy;
    }

    /**
     * Creates a copy with the specified output path.
     * 
     * <p>
     * This method is typically called by the conversion engine after a successful
     * conversion to record where the output file was written. The output path is
     * used
     * by the "Open File Location" feature to show the converted file in the file
     * manager.
     * </p>
     * 
     * <p>
     * All other fields including progressInfo are preserved in the copy.
     * </p>
     * 
     * <p>
     * Requirement REQ-FL-3.3: Output path tracking
     * </p>
     * 
     * @param outputPath The path to the converted output file (can be null to
     *                   clear)
     * @return A new ConversionFile instance with the output path set
     * @see #outputPath() to retrieve the output path
     */
    public ConversionFile withOutputPath(Path outputPath) {
        ConversionFile copy = new ConversionFile(id, path, format, size, metadata, settingsOverride, outputPath, status,
                progress, errorMessage);
        copy.progressInfo = this.progressInfo; // Preserve progress info
        return copy;
    }

    /**
     * Gets the filename without path.
     */
    public String fileName() {
        return path.getFileName().toString();
    }

    /**
     * Checks if the file is in a terminal state (completed, failed, or cancelled).
     */
    public boolean isTerminal() {
        return status == ConversionStatus.COMPLETED ||
                status == ConversionStatus.FAILED ||
                status == ConversionStatus.CANCELLED;
    }

    /**
     * Checks if the file is currently being processed.
     */
    public boolean isInProgress() {
        return status == ConversionStatus.IN_PROGRESS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ConversionFile that = (ConversionFile) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ConversionFile{");
        sb.append("id='").append(id).append('\'');
        sb.append(", path=").append(path);
        sb.append(", format=").append(format);
        sb.append(", size=").append(size);
        sb.append(", status=").append(status);
        sb.append(", progress=").append(progress);
        sb.append(", hasCustomSettings=").append(hasCustomSettings());
        if (settingsOverride != null) {
            sb.append(", settingsOverride=").append(settingsOverride);
        }
        if (outputPath != null) {
            sb.append(", outputPath=").append(outputPath);
        }
        if (errorMessage != null) {
            sb.append(", errorMessage='").append(errorMessage).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }
}
