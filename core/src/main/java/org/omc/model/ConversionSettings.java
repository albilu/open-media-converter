// filepath: src/main/java/org/omc/model/ConversionSettings.java

package org.omc.model;

import java.nio.file.Path;
import java.util.Objects;

import org.omc.controller.SettingsManager;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Core conversion settings for output configuration.
 * 
 * <p>
 * <b>Section-Based Output Format Architecture:</b>
 * </p>
 * <p>
 * This class implements a section-based format selection model where each media
 * category (VIDEO, AUDIO, IMAGE, DOCUMENT) has its own dedicated settings
 * object
 * containing format and quality parameters. This architecture replaced the
 * older
 * global {@code outputFormat} field to support independent format choices per
 * category.
 * </p>
 * 
 * <p>
 * <b>Migration from Legacy Format:</b>
 * </p>
 * <p>
 * Prior versions used a single global {@code outputFormat} field. This has been
 * replaced by category-specific settings ({@link VideoSettings},
 * {@link AudioSettings},
 * {@link ImageSettings}, {@link DocumentSettings}). The {@link SettingsManager}
 * handles
 * automatic migration of old settings files to the new structure.
 * </p>
 * 
 * <p>
 * <b>Usage Example:</b>
 * </p>
 * 
 * <pre>{@code
 * ConversionSettings settings = ConversionSettings.builder()
 *         .outputDirectory(Paths.get("/output"))
 *         .videoSettings(VideoSettings.builder()
 *                 .outputFormat(FileFormat.MP4)
 *                 .codec("h264")
 *                 .build())
 *         .audioSettings(AudioSettings.builder()
 *                 .outputFormat(FileFormat.MP3)
 *                 .bitrate(192)
 *                 .build())
 *         .parallelConversions(4)
 *         .build();
 * }</pre>
 * 
 * <p>
 * Requirements:
 * </p>
 * <ul>
 * <li>REQ-003.2: Conversion settings with builder pattern</li>
 * <li>REQ-2.1: Per-section output format support</li>
 * <li>REQ-5.1: Backward compatibility with old settings format</li>
 * <li>REQ-GEN-1.1: Delete original file after successful conversion</li>
 * </ul>
 * 
 * @see VideoSettings
 * @see AudioSettings
 * @see ImageSettings
 * @see DocumentSettings
 * @see SettingsManager#loadSettings()
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ConversionSettings {

    private final Path outputDirectory;
    private final boolean overwriteExisting;
    private final boolean createSubdirectory;
    private final int parallelConversions;
    private final boolean deleteOriginalFile; // Requirement REQ-GEN-1.1: Delete original after conversion
    private final VideoSettings videoSettings;
    private final AudioSettings audioSettings;
    private final ImageSettings imageSettings;
    private final DocumentSettings documentSettings;

    @JsonCreator
    private ConversionSettings(
            @JsonProperty("outputDirectory") Path outputDirectory,
            @JsonProperty("overwriteExisting") boolean overwriteExisting,
            @JsonProperty("createSubdirectory") boolean createSubdirectory,
            @JsonProperty("parallelConversions") int parallelConversions,
            @JsonProperty("deleteOriginalFile") boolean deleteOriginalFile,
            @JsonProperty("videoSettings") VideoSettings videoSettings,
            @JsonProperty("audioSettings") AudioSettings audioSettings,
            @JsonProperty("imageSettings") ImageSettings imageSettings,
            @JsonProperty("documentSettings") DocumentSettings documentSettings) {
        this.outputDirectory = outputDirectory;
        this.overwriteExisting = overwriteExisting;
        this.createSubdirectory = createSubdirectory;
        this.parallelConversions = parallelConversions;
        this.deleteOriginalFile = deleteOriginalFile;
        this.videoSettings = videoSettings;
        this.audioSettings = audioSettings;
        this.imageSettings = imageSettings;
        this.documentSettings = documentSettings;
    }

    @JsonProperty("outputDirectory")
    public Path outputDirectory() {
        return outputDirectory;
    }

    @JsonProperty("overwriteExisting")
    public boolean overwriteExisting() {
        return overwriteExisting;
    }

    @JsonProperty("createSubdirectory")
    public boolean createSubdirectory() {
        return createSubdirectory;
    }

    @JsonProperty("parallelConversions")
    public int parallelConversions() {
        return parallelConversions;
    }

    @JsonProperty("deleteOriginalFile")
    public boolean deleteOriginalFile() {
        return deleteOriginalFile;
    }

    @JsonProperty("videoSettings")
    public VideoSettings videoSettings() {
        return videoSettings;
    }

    @JsonProperty("audioSettings")
    public AudioSettings audioSettings() {
        return audioSettings;
    }

    @JsonProperty("imageSettings")
    public ImageSettings imageSettings() {
        return imageSettings;
    }

    @JsonProperty("documentSettings")
    public DocumentSettings documentSettings() {
        return documentSettings;
    }

    /**
     * Returns the output format for the specified category.
     * 
     * <p>
     * This method provides category-specific format lookup by delegating to the
     * appropriate section settings object. If no settings are configured for the
     * requested category, returns null.
     * </p>
     * 
     * <p>
     * Requirement REQ-2.1: Section-based output format retrieval
     * </p>
     * 
     * @param category the format category (VIDEO, AUDIO, IMAGE, DOCUMENT)
     * @return the output format for the category, or null if not set
     * @see #outputFormat() for retrieving the primary output format
     */
    public FileFormat outputFormat(FormatCategory category) {
        return switch (category) {
            case VIDEO -> videoSettings != null ? videoSettings.outputFormat() : null;
            case AUDIO -> audioSettings != null ? audioSettings.outputFormat() : null;
            case IMAGE -> imageSettings != null ? imageSettings.outputFormat() : null;
            case DOCUMENT -> documentSettings != null ? documentSettings.outputFormat() : null;
            default -> null;
        };
    }

    /**
     * Returns the primary output format (first non-null from settings).
     * 
     * @return the output format, or null if none set
     */
    public FileFormat outputFormat() {
        if (videoSettings != null && videoSettings.outputFormat() != null)
            return videoSettings.outputFormat();
        if (audioSettings != null && audioSettings.outputFormat() != null)
            return audioSettings.outputFormat();
        if (imageSettings != null && imageSettings.outputFormat() != null)
            return imageSettings.outputFormat();
        if (documentSettings != null && documentSettings.outputFormat() != null)
            return documentSettings.outputFormat();
        return null;
    }

    /**
     * Validates these settings.
     * Checks that output directory exists and is writable, parallel conversions are
     * within bounds,
     * at least one output format is configured, and format-specific settings are
     * valid.
     * 
     * @return true if settings are valid, false otherwise
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isValid() {
        if (outputDirectory == null || !outputDirectory.toFile().exists()) {
            return false;
        }

        if (!outputDirectory.toFile().canWrite()) {
            return false;
        }

        if (parallelConversions < 1 || parallelConversions > 16) {
            return false;
        }

        // Requirement REQ-004.2: At least one output format must be configured
        if (videoSettings == null && audioSettings == null &&
                imageSettings == null && documentSettings == null) {
            return false;
        }

        // Validate format-specific settings
        if (videoSettings != null && !videoSettings.isValid()) {
            return false;
        }
        if (audioSettings != null && !audioSettings.isValid()) {
            return false;
        }
        if (imageSettings != null && !imageSettings.isValid()) {
            return false;
        }
        if (documentSettings != null && !documentSettings.isValid()) {
            return false;
        }

        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Path outputDirectory;
        private boolean overwriteExisting = false;
        private boolean createSubdirectory = false;
        private int parallelConversions = 4;
        private boolean deleteOriginalFile = false;
        private VideoSettings videoSettings;
        private AudioSettings audioSettings;
        private ImageSettings imageSettings;
        private DocumentSettings documentSettings;

        /**
         * Sets the output directory.
         * 
         * @param outputDirectory the directory where converted files will be saved
         * @return this builder
         */
        public Builder outputDirectory(Path outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        /**
         * Sets whether to overwrite existing files.
         * 
         * @param overwriteExisting true to overwrite existing files, false otherwise
         * @return this builder
         */
        public Builder overwriteExisting(boolean overwriteExisting) {
            this.overwriteExisting = overwriteExisting;
            return this;
        }

        /**
         * Sets whether to create a subdirectory for output.
         * 
         * @param createSubdirectory true to create a subdirectory, false otherwise
         * @return this builder
         */
        public Builder createSubdirectory(boolean createSubdirectory) {
            this.createSubdirectory = createSubdirectory;
            return this;
        }

        /**
         * Sets the number of parallel conversions.
         * 
         * @param parallelConversions the number of parallel conversions (1-16)
         * @return this builder
         */
        public Builder parallelConversions(int parallelConversions) {
            this.parallelConversions = parallelConversions;
            return this;
        }

        /**
         * Sets whether to delete the original file after successful conversion.
         * 
         * @param deleteOriginalFile true to delete original files after successful
         *                           conversion, false otherwise
         * @return this builder
         */
        public Builder deleteOriginalFile(boolean deleteOriginalFile) {
            this.deleteOriginalFile = deleteOriginalFile;
            return this;
        }

        /**
         * Sets the video-specific settings.
         * 
         * @param videoSettings the video settings
         * @return this builder
         */
        public Builder videoSettings(VideoSettings videoSettings) {
            this.videoSettings = videoSettings;
            return this;
        }

        /**
         * Sets the audio-specific settings.
         * 
         * @param audioSettings the audio settings
         * @return this builder
         */
        public Builder audioSettings(AudioSettings audioSettings) {
            this.audioSettings = audioSettings;
            return this;
        }

        /**
         * Sets the image-specific settings.
         * 
         * @param imageSettings the image settings
         * @return this builder
         */
        public Builder imageSettings(ImageSettings imageSettings) {
            this.imageSettings = imageSettings;
            return this;
        }

        /**
         * Sets the document-specific settings.
         * 
         * @param documentSettings the document settings
         * @return this builder
         */
        public Builder documentSettings(DocumentSettings documentSettings) {
            this.documentSettings = documentSettings;
            return this;
        }

        /**
         * Sets the output format for the appropriate category.
         * 
         * @param outputFormat the output format
         * @return this builder
         */
        public Builder outputFormat(FileFormat outputFormat) {
            FormatCategory category = outputFormat.getCategory();
            switch (category) {
                case VIDEO -> {
                    if (videoSettings == null) {
                        videoSettings = VideoSettings.builder().outputFormat(outputFormat).build();
                    } else {
                        videoSettings = VideoSettings.builder()
                                .codec(videoSettings.codec())
                                .bitrate(videoSettings.bitrate())
                                .resolution(videoSettings.resolution())
                                .frameRate(videoSettings.frameRate())
                                .preset(videoSettings.preset())
                                .crf(videoSettings.crf())
                                .aspectRatio(videoSettings.aspectRatio())
                                .outputFormat(outputFormat)
                                .build();
                    }
                }
                case AUDIO -> {
                    if (audioSettings == null) {
                        audioSettings = AudioSettings.builder().outputFormat(outputFormat).build();
                    } else {
                        audioSettings = AudioSettings.builder()
                                .codec(audioSettings.codec())
                                .bitrate(audioSettings.bitrate())
                                .sampleRate(audioSettings.sampleRate())
                                .channels(audioSettings.channels())
                                .quality(audioSettings.quality())
                                .outputFormat(outputFormat)
                                .build();
                    }
                }
                case IMAGE -> {
                    if (imageSettings == null) {
                        imageSettings = ImageSettings.builder().outputFormat(outputFormat).build();
                    } else {
                        imageSettings = ImageSettings.builder()
                                .quality(imageSettings.quality())
                                .maintainAspectRatio(imageSettings.maintainAspectRatio())
                                .compressionLevel(imageSettings.compressionLevel())
                                .rotation(imageSettings.rotation())
                                .flip(imageSettings.flip())
                                .outputFormat(outputFormat)
                                .build();
                    }
                }
                case DOCUMENT -> {
                    if (documentSettings == null) {
                        documentSettings = DocumentSettings.builder().outputFormat(outputFormat).build();
                    } else {
                        // Assuming DocumentSettings has similar builder
                        documentSettings = DocumentSettings.builder()
                                .outputFormat(outputFormat)
                                .build();
                    }
                }
            }
            return this;
        }

        /**
         * Builds the ConversionSettings instance.
         * 
         * @return a new ConversionSettings instance
         */
        public ConversionSettings build() {
            return new ConversionSettings(
                    outputDirectory,
                    overwriteExisting,
                    createSubdirectory,
                    parallelConversions,
                    deleteOriginalFile,
                    videoSettings,
                    audioSettings,
                    imageSettings,
                    documentSettings);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ConversionSettings that = (ConversionSettings) o;
        return overwriteExisting == that.overwriteExisting &&
                createSubdirectory == that.createSubdirectory &&
                parallelConversions == that.parallelConversions &&
                deleteOriginalFile == that.deleteOriginalFile &&
                Objects.equals(outputDirectory, that.outputDirectory) &&
                Objects.equals(videoSettings, that.videoSettings) &&
                Objects.equals(audioSettings, that.audioSettings) &&
                Objects.equals(imageSettings, that.imageSettings) &&
                Objects.equals(documentSettings, that.documentSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(outputDirectory, overwriteExisting,
                createSubdirectory, parallelConversions, deleteOriginalFile, videoSettings,
                audioSettings, imageSettings, documentSettings);
    }

    @Override
    public String toString() {
        return "ConversionSettings{" +
                "outputDirectory=" + outputDirectory +
                ", overwriteExisting=" + overwriteExisting +
                ", createSubdirectory=" + createSubdirectory +
                ", parallelConversions=" + parallelConversions +
                ", deleteOriginalFile=" + deleteOriginalFile +
                ", videoSettings=" + videoSettings +
                ", audioSettings=" + audioSettings +
                ", imageSettings=" + imageSettings +
                ", documentSettings=" + documentSettings +
                '}';
    }
}
