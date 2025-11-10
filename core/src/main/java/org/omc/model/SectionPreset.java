// filepath: src/main/java/org/omc/model/SectionPreset.java

package org.omc.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a named preset for a specific format category section.
 * 
 * <p>
 * This class replaces the legacy SettingsPreset model by storing
 * section-specific
 * settings (VideoSettings, AudioSettings, ImageSettings, or DocumentSettings)
 * instead
 * of global ConversionSettings with an output format.
 * </p>
 * 
 * <p>
 * Only one section's settings should be non-null based on the preset's
 * category.
 * The category field explicitly indicates which type of preset this is.
 * </p>
 * 
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * // Create a video preset
 * VideoSettings settings = VideoSettings.builder()
 *         .outputFormat(FileFormat.MP4)
 *         .codec("libx264")
 *         .bitrate(5000)
 *         .build();
 * SectionPreset preset = SectionPreset.forVideo(
 *         "High Quality 1080p",
 *         "1080p video with high bitrate",
 *         settings,
 *         false);
 * 
 * // Check validity
 * if (preset.isValid()) {
 *     settingsManager.addSectionPreset(preset);
 * }
 * </pre>
 * 
 * <p>
 * <strong>Requirements:</strong> REQ-2.6 (Preset management), REQ-2.7 (Preset
 * storage)
 * </p>
 * 
 * @see VideoSettings
 * @see AudioSettings
 * @see ImageSettings
 * @see DocumentSettings
 * @see PresetsBySection
 */
public final class SectionPreset {

    private final String name;
    private final String description;
    private final FormatCategory category;
    private final VideoSettings videoSettings;
    private final AudioSettings audioSettings;
    private final ImageSettings imageSettings;
    private final DocumentSettings documentSettings;
    private final boolean builtIn;
    private final long createdAt;

    /**
     * Private constructor for JSON deserialization.
     * Use static factory methods to create instances.
     * 
     * @param name             Preset name (required, non-null)
     * @param description      Optional description of the preset
     * @param category         Format category (required, non-null)
     * @param videoSettings    Video-specific settings (non-null only for VIDEO
     *                         category)
     * @param audioSettings    Audio-specific settings (non-null only for AUDIO
     *                         category)
     * @param imageSettings    Image-specific settings (non-null only for IMAGE
     *                         category)
     * @param documentSettings Document-specific settings (non-null only for
     *                         DOCUMENT category)
     * @param builtIn          True if this is a system-provided preset (cannot be
     *                         deleted)
     * @param createdAt        Timestamp when preset was created (milliseconds since
     *                         epoch)
     */
    @JsonCreator
    private SectionPreset(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("category") FormatCategory category,
            @JsonProperty("videoSettings") VideoSettings videoSettings,
            @JsonProperty("audioSettings") AudioSettings audioSettings,
            @JsonProperty("imageSettings") ImageSettings imageSettings,
            @JsonProperty("documentSettings") DocumentSettings documentSettings,
            @JsonProperty("builtIn") boolean builtIn,
            @JsonProperty("createdAt") long createdAt) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.description = description;
        this.category = Objects.requireNonNull(category, "category cannot be null");
        this.videoSettings = videoSettings;
        this.audioSettings = audioSettings;
        this.imageSettings = imageSettings;
        this.documentSettings = documentSettings;
        this.builtIn = builtIn;
        this.createdAt = createdAt;
    }

    /**
     * Creates a video preset.
     * 
     * @param name        Preset name (required)
     * @param description Optional description
     * @param settings    Video-specific settings (required, non-null)
     * @param builtIn     True if this is a system preset
     * @return A new SectionPreset instance for video
     * @throws NullPointerException if settings is null
     */
    public static SectionPreset forVideo(String name, String description, VideoSettings settings, boolean builtIn) {
        Objects.requireNonNull(settings, "videoSettings cannot be null");
        return new SectionPreset(
                name,
                description,
                FormatCategory.VIDEO,
                settings,
                null,
                null,
                null,
                builtIn,
                System.currentTimeMillis());
    }

    /**
     * Creates an audio preset.
     * 
     * @param name        Preset name (required)
     * @param description Optional description
     * @param settings    Audio-specific settings (required, non-null)
     * @param builtIn     True if this is a system preset
     * @return A new SectionPreset instance for audio
     * @throws NullPointerException if settings is null
     */
    public static SectionPreset forAudio(String name, String description, AudioSettings settings, boolean builtIn) {
        Objects.requireNonNull(settings, "audioSettings cannot be null");
        return new SectionPreset(
                name,
                description,
                FormatCategory.AUDIO,
                null,
                settings,
                null,
                null,
                builtIn,
                System.currentTimeMillis());
    }

    /**
     * Creates an image preset.
     * 
     * @param name        Preset name (required)
     * @param description Optional description
     * @param settings    Image-specific settings (required, non-null)
     * @param builtIn     True if this is a system preset
     * @return A new SectionPreset instance for image
     * @throws NullPointerException if settings is null
     */
    public static SectionPreset forImage(String name, String description, ImageSettings settings, boolean builtIn) {
        Objects.requireNonNull(settings, "imageSettings cannot be null");
        return new SectionPreset(
                name,
                description,
                FormatCategory.IMAGE,
                null,
                null,
                settings,
                null,
                builtIn,
                System.currentTimeMillis());
    }

    /**
     * Creates a document preset.
     * 
     * @param name        Preset name (required)
     * @param description Optional description
     * @param settings    Document-specific settings (required, non-null)
     * @param builtIn     True if this is a system preset
     * @return A new SectionPreset instance for document
     * @throws NullPointerException if settings is null
     */
    public static SectionPreset forDocument(String name, String description, DocumentSettings settings,
            boolean builtIn) {
        Objects.requireNonNull(settings, "documentSettings cannot be null");
        return new SectionPreset(
                name,
                description,
                FormatCategory.DOCUMENT,
                null,
                null,
                null,
                settings,
                builtIn,
                System.currentTimeMillis());
    }

    /**
     * Returns the preset name.
     * 
     * @return Preset name (never null)
     */
    @JsonProperty("name")
    public String name() {
        return name;
    }

    /**
     * Returns the preset description.
     * 
     * @return Description or null if not provided
     */
    @JsonProperty("description")
    public String description() {
        return description;
    }

    /**
     * Returns the format category this preset applies to.
     * 
     * @return Format category (never null)
     */
    @JsonProperty("category")
    public FormatCategory category() {
        return category;
    }

    /**
     * Returns video settings if this is a video preset.
     * 
     * @return Video settings or null if not a video preset
     */
    @JsonProperty("videoSettings")
    public VideoSettings videoSettings() {
        return videoSettings;
    }

    /**
     * Returns audio settings if this is an audio preset.
     * 
     * @return Audio settings or null if not an audio preset
     */
    @JsonProperty("audioSettings")
    public AudioSettings audioSettings() {
        return audioSettings;
    }

    /**
     * Returns image settings if this is an image preset.
     * 
     * @return Image settings or null if not an image preset
     */
    @JsonProperty("imageSettings")
    public ImageSettings imageSettings() {
        return imageSettings;
    }

    /**
     * Returns document settings if this is a document preset.
     * 
     * @return Document settings or null if not a document preset
     */
    @JsonProperty("documentSettings")
    public DocumentSettings documentSettings() {
        return documentSettings;
    }

    /**
     * Checks if this is a built-in (system-provided) preset.
     * Built-in presets cannot be deleted by users.
     * 
     * @return true if this is a built-in preset
     */
    @JsonProperty("builtIn")
    public boolean builtIn() {
        return builtIn;
    }

    /**
     * Returns the timestamp when this preset was created.
     * 
     * @return Creation timestamp in milliseconds since epoch
     */
    @JsonProperty("createdAt")
    public long createdAt() {
        return createdAt;
    }

    /**
     * Validates that this preset has valid data.
     * A valid preset must have:
     * <ul>
     * <li>Non-empty name</li>
     * <li>Non-null category</li>
     * <li>Appropriate section settings non-null for the category</li>
     * </ul>
     * 
     * @return true if the preset is valid, false otherwise
     */
    @JsonIgnore
    public boolean isValid() {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        if (category == null) {
            return false;
        }
        return switch (category) {
            case VIDEO -> videoSettings != null;
            case AUDIO -> audioSettings != null;
            case IMAGE -> imageSettings != null;
            case DOCUMENT -> documentSettings != null;
            case UNKNOWN -> false;
        };
    }

    /**
     * Creates a copy of this preset with new video settings.
     * 
     * @param settings New video settings
     * @return A new SectionPreset instance with updated settings
     * @throws IllegalStateException if this is not a video preset
     * @throws NullPointerException  if settings is null
     */
    public SectionPreset withVideoSettings(VideoSettings settings) {
        if (category != FormatCategory.VIDEO) {
            throw new IllegalStateException("Cannot set video settings on " + category + " preset");
        }
        Objects.requireNonNull(settings, "videoSettings cannot be null");
        return new SectionPreset(name, description, category, settings, null, null, null, builtIn, createdAt);
    }

    /**
     * Creates a copy of this preset with new audio settings.
     * 
     * @param settings New audio settings
     * @return A new SectionPreset instance with updated settings
     * @throws IllegalStateException if this is not an audio preset
     * @throws NullPointerException  if settings is null
     */
    public SectionPreset withAudioSettings(AudioSettings settings) {
        if (category != FormatCategory.AUDIO) {
            throw new IllegalStateException("Cannot set audio settings on " + category + " preset");
        }
        Objects.requireNonNull(settings, "audioSettings cannot be null");
        return new SectionPreset(name, description, category, null, settings, null, null, builtIn, createdAt);
    }

    /**
     * Creates a copy of this preset with new image settings.
     * 
     * @param settings New image settings
     * @return A new SectionPreset instance with updated settings
     * @throws IllegalStateException if this is not an image preset
     * @throws NullPointerException  if settings is null
     */
    public SectionPreset withImageSettings(ImageSettings settings) {
        if (category != FormatCategory.IMAGE) {
            throw new IllegalStateException("Cannot set image settings on " + category + " preset");
        }
        Objects.requireNonNull(settings, "imageSettings cannot be null");
        return new SectionPreset(name, description, category, null, null, settings, null, builtIn, createdAt);
    }

    /**
     * Creates a copy of this preset with new document settings.
     * 
     * @param settings New document settings
     * @return A new SectionPreset instance with updated settings
     * @throws IllegalStateException if this is not a document preset
     * @throws NullPointerException  if settings is null
     */
    public SectionPreset withDocumentSettings(DocumentSettings settings) {
        if (category != FormatCategory.DOCUMENT) {
            throw new IllegalStateException("Cannot set document settings on " + category + " preset");
        }
        Objects.requireNonNull(settings, "documentSettings cannot be null");
        return new SectionPreset(name, description, category, null, null, null, settings, builtIn, createdAt);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        SectionPreset other = (SectionPreset) obj;
        return builtIn == other.builtIn
                && createdAt == other.createdAt
                && Objects.equals(name, other.name)
                && Objects.equals(description, other.description)
                && category == other.category
                && Objects.equals(videoSettings, other.videoSettings)
                && Objects.equals(audioSettings, other.audioSettings)
                && Objects.equals(imageSettings, other.imageSettings)
                && Objects.equals(documentSettings, other.documentSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, category, videoSettings, audioSettings,
                imageSettings, documentSettings, builtIn, createdAt);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SectionPreset{");
        sb.append("name='").append(name).append('\'');
        sb.append(", category=").append(category);
        if (description != null) {
            sb.append(", description='").append(description).append('\'');
        }
        sb.append(", builtIn=").append(builtIn);
        sb.append(", createdAt=").append(createdAt);

        if (videoSettings != null) {
            sb.append(", videoSettings=").append(videoSettings);
        }
        if (audioSettings != null) {
            sb.append(", audioSettings=").append(audioSettings);
        }
        if (imageSettings != null) {
            sb.append(", imageSettings=").append(imageSettings);
        }
        if (documentSettings != null) {
            sb.append(", documentSettings=").append(documentSettings);
        }

        sb.append('}');
        return sb.toString();
    }
}
