// filepath: src/main/java/org/omc/model/FileSettingsOverride.java
package org.omc.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stores per-file settings overrides.
 * 
 * <p>
 * This class allows individual files to have custom conversion settings that
 * override the global section settings. Only one section's settings should be
 * non-null
 * based on the file's format category.
 * </p>
 * 
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * // Apply custom video settings to a video file
 * VideoSettings customSettings = VideoSettings.builder()
 *         .outputFormat(FileFormat.MP4)
 *         .bitrate(5000)
 *         .build();
 * FileSettingsOverride override = FileSettingsOverride.forVideo("High Quality", customSettings);
 * ConversionFile file = existingFile.withSettingsOverride(override);
 * </pre>
 * 
 * <p>
 * <strong>Requirements:</strong> REQ-3.1 - Per-file settings override
 * capability
 * </p>
 * 
 * @see ConversionFile#withSettingsOverride(FileSettingsOverride)
 * @see VideoSettings
 * @see AudioSettings
 * @see ImageSettings
 * @see DocumentSettings
 */
public final class FileSettingsOverride {

    private final String presetName;
    private final VideoSettings videoSettings;
    private final AudioSettings audioSettings;
    private final ImageSettings imageSettings;
    private final DocumentSettings documentSettings;

    /**
     * Private constructor for JSON deserialization.
     * Use static factory methods to create instances.
     * 
     * @param presetName       Name of the preset applied (for display purposes)
     * @param videoSettings    Video-specific settings (non-null only for video
     *                         files)
     * @param audioSettings    Audio-specific settings (non-null only for audio
     *                         files)
     * @param imageSettings    Image-specific settings (non-null only for image
     *                         files)
     * @param documentSettings Document-specific settings (non-null only for
     *                         document files)
     */
    @JsonCreator
    private FileSettingsOverride(
            @JsonProperty("presetName") String presetName,
            @JsonProperty("videoSettings") VideoSettings videoSettings,
            @JsonProperty("audioSettings") AudioSettings audioSettings,
            @JsonProperty("imageSettings") ImageSettings imageSettings,
            @JsonProperty("documentSettings") DocumentSettings documentSettings) {
        this.presetName = presetName;
        this.videoSettings = videoSettings;
        this.audioSettings = audioSettings;
        this.imageSettings = imageSettings;
        this.documentSettings = documentSettings;
    }

    /**
     * Creates a video settings override.
     * 
     * @param presetName Name of the preset (for display)
     * @param settings   Video-specific settings
     * @return A new FileSettingsOverride instance for video files
     * @throws NullPointerException if settings is null
     */
    public static FileSettingsOverride forVideo(String presetName, VideoSettings settings) {
        Objects.requireNonNull(settings, "videoSettings cannot be null");
        return new FileSettingsOverride(presetName, settings, null, null, null);
    }

    /**
     * Creates an audio settings override.
     * 
     * @param presetName Name of the preset (for display)
     * @param settings   Audio-specific settings
     * @return A new FileSettingsOverride instance for audio files
     * @throws NullPointerException if settings is null
     */
    public static FileSettingsOverride forAudio(String presetName, AudioSettings settings) {
        Objects.requireNonNull(settings, "audioSettings cannot be null");
        return new FileSettingsOverride(presetName, null, settings, null, null);
    }

    /**
     * Creates an image settings override.
     * 
     * @param presetName Name of the preset (for display)
     * @param settings   Image-specific settings
     * @return A new FileSettingsOverride instance for image files
     * @throws NullPointerException if settings is null
     */
    public static FileSettingsOverride forImage(String presetName, ImageSettings settings) {
        Objects.requireNonNull(settings, "imageSettings cannot be null");
        return new FileSettingsOverride(presetName, null, null, settings, null);
    }

    /**
     * Creates a document settings override.
     * 
     * @param presetName Name of the preset (for display)
     * @param settings   Document-specific settings
     * @return A new FileSettingsOverride instance for document files
     * @throws NullPointerException if settings is null
     */
    public static FileSettingsOverride forDocument(String presetName, DocumentSettings settings) {
        Objects.requireNonNull(settings, "documentSettings cannot be null");
        return new FileSettingsOverride(presetName, null, null, null, settings);
    }

    /**
     * Returns the name of the preset applied.
     * 
     * @return Preset name, may be null if no name was provided
     */
    @JsonProperty("presetName")
    public String presetName() {
        return presetName;
    }

    /**
     * Returns video settings if this override is for a video file.
     * 
     * @return Video settings, or null if this is not a video override
     */
    @JsonProperty("videoSettings")
    public VideoSettings videoSettings() {
        return videoSettings;
    }

    /**
     * Returns audio settings if this override is for an audio file.
     * 
     * @return Audio settings, or null if this is not an audio override
     */
    @JsonProperty("audioSettings")
    public AudioSettings audioSettings() {
        return audioSettings;
    }

    /**
     * Returns image settings if this override is for an image file.
     * 
     * @return Image settings, or null if this is not an image override
     */
    @JsonProperty("imageSettings")
    public ImageSettings imageSettings() {
        return imageSettings;
    }

    /**
     * Returns document settings if this override is for a document file.
     * 
     * @return Document settings, or null if this is not a document override
     */
    @JsonProperty("documentSettings")
    public DocumentSettings documentSettings() {
        return documentSettings;
    }

    /**
     * Determines the format category of this override based on which settings are
     * non-null.
     * 
     * @return The category of this override (VIDEO, AUDIO, IMAGE, DOCUMENT, or
     *         UNKNOWN)
     */
    @JsonIgnore
    public FormatCategory getCategory() {
        if (videoSettings != null) {
            return FormatCategory.VIDEO;
        }
        if (audioSettings != null) {
            return FormatCategory.AUDIO;
        }
        if (imageSettings != null) {
            return FormatCategory.IMAGE;
        }
        if (documentSettings != null) {
            return FormatCategory.DOCUMENT;
        }
        return FormatCategory.UNKNOWN;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        FileSettingsOverride other = (FileSettingsOverride) obj;
        return Objects.equals(presetName, other.presetName)
                && Objects.equals(videoSettings, other.videoSettings)
                && Objects.equals(audioSettings, other.audioSettings)
                && Objects.equals(imageSettings, other.imageSettings)
                && Objects.equals(documentSettings, other.documentSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(presetName, videoSettings, audioSettings, imageSettings, documentSettings);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FileSettingsOverride{");
        sb.append("presetName='").append(presetName).append('\'');
        sb.append(", category=").append(getCategory());
        sb.append(", videoSettings=").append(videoSettings);
        sb.append(", audioSettings=").append(audioSettings);
        sb.append(", imageSettings=").append(imageSettings);
        sb.append(", documentSettings=").append(documentSettings);
        sb.append('}');
        return sb.toString();
    }
}
