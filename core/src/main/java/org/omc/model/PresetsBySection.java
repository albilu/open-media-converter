// filepath: src/main/java/org/omc/model/PresetsBySection.java
package org.omc.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Container for presets organized by format category.
 * 
 * <p>
 * This class replaces the old global preset list with a section-based
 * organization
 * where presets are grouped by their target format category (VIDEO, AUDIO,
 * IMAGE, DOCUMENT).
 * Each section maintains its own list of {@link SectionPreset} instances.
 * 
 * <p>
 * The class is immutable and provides defensive copies of all preset lists to
 * prevent
 * external modification. Use the factory method {@link #empty()} to create an
 * instance
 * with no presets, or use the constructor to create an instance from existing
 * preset lists.
 * 
 * <h2>Usage Example:</h2>
 * 
 * <pre>{@code
 * // Create empty preset container
 * PresetsBySection presets = PresetsBySection.empty();
 * 
 * // Create with existing presets
 * List<SectionPreset> videoPresets = List.of(
 *         SectionPreset.forVideo("High Quality", VideoSettings.builder().build()));
 * PresetsBySection presets = new PresetsBySection(videoPresets, null, null, null);
 * 
 * // Get presets for a specific category
 * List<SectionPreset> available = presets.getPresetsForCategory(FormatCategory.VIDEO);
 * }</pre>
 * 
 * <h2>JSON Serialization:</h2>
 * <p>
 * This class is designed for JSON serialization with Jackson. All fields are
 * annotated
 * with {@code @JsonProperty} and a {@code @JsonCreator} constructor is provided
 * for
 * deserialization.
 * 
 * @see SectionPreset
 * @see FormatCategory
 * 
 *      Requirements: REQ-2.7 (Preset storage and organization)
 */
public final class PresetsBySection {

    private final List<SectionPreset> videoPresets;
    private final List<SectionPreset> audioPresets;
    private final List<SectionPreset> imagePresets;
    private final List<SectionPreset> documentPresets;

    /**
     * Creates a new PresetsBySection container with the specified preset lists.
     * 
     * <p>
     * All list parameters accept null values, which will be converted to empty
     * lists.
     * Defensive copies are made of all non-null lists to ensure immutability.
     * 
     * @param videoPresets    list of video presets, or null for empty list
     * @param audioPresets    list of audio presets, or null for empty list
     * @param imagePresets    list of image presets, or null for empty list
     * @param documentPresets list of document presets, or null for empty list
     */
    @JsonCreator
    public PresetsBySection(
            @JsonProperty("videoPresets") List<SectionPreset> videoPresets,
            @JsonProperty("audioPresets") List<SectionPreset> audioPresets,
            @JsonProperty("imagePresets") List<SectionPreset> imagePresets,
            @JsonProperty("documentPresets") List<SectionPreset> documentPresets) {
        // Create defensive copies, converting null to empty lists
        this.videoPresets = videoPresets != null ? new ArrayList<>(videoPresets) : new ArrayList<>();
        this.audioPresets = audioPresets != null ? new ArrayList<>(audioPresets) : new ArrayList<>();
        this.imagePresets = imagePresets != null ? new ArrayList<>(imagePresets) : new ArrayList<>();
        this.documentPresets = documentPresets != null ? new ArrayList<>(documentPresets) : new ArrayList<>();
    }

    /**
     * Creates an empty PresetsBySection with no presets in any category.
     * 
     * @return a new PresetsBySection instance with all preset lists empty
     */
    public static PresetsBySection empty() {
        return new PresetsBySection(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Returns a defensive copy of the video presets list.
     * 
     * <p>
     * The returned list is a new instance and modifications will not affect
     * the internal state of this PresetsBySection.
     * 
     * @return a new list containing all video presets
     */
    @JsonProperty("videoPresets")
    public List<SectionPreset> videoPresets() {
        return new ArrayList<>(videoPresets);
    }

    /**
     * Returns a defensive copy of the audio presets list.
     * 
     * <p>
     * The returned list is a new instance and modifications will not affect
     * the internal state of this PresetsBySection.
     * 
     * @return a new list containing all audio presets
     */
    @JsonProperty("audioPresets")
    public List<SectionPreset> audioPresets() {
        return new ArrayList<>(audioPresets);
    }

    /**
     * Returns a defensive copy of the image presets list.
     * 
     * <p>
     * The returned list is a new instance and modifications will not affect
     * the internal state of this PresetsBySection.
     * 
     * @return a new list containing all image presets
     */
    @JsonProperty("imagePresets")
    public List<SectionPreset> imagePresets() {
        return new ArrayList<>(imagePresets);
    }

    /**
     * Returns a defensive copy of the document presets list.
     * 
     * <p>
     * The returned list is a new instance and modifications will not affect
     * the internal state of this PresetsBySection.
     * 
     * @return a new list containing all document presets
     */
    @JsonProperty("documentPresets")
    public List<SectionPreset> documentPresets() {
        return new ArrayList<>(documentPresets);
    }

    /**
     * Returns the list of presets for the specified format category.
     * 
     * <p>
     * This method provides a convenient way to retrieve presets without knowing
     * which specific getter method to call. The returned list is a defensive copy.
     * 
     * <p>
     * If the category is {@link FormatCategory#UNKNOWN}, an empty list is returned.
     * 
     * @param category the format category to get presets for
     * @return a new list containing all presets for the specified category, or
     *         empty list for UNKNOWN
     * @throws NullPointerException if category is null
     */
    public List<SectionPreset> getPresetsForCategory(FormatCategory category) {
        Objects.requireNonNull(category, "Category cannot be null");
        return switch (category) {
            case VIDEO -> videoPresets();
            case AUDIO -> audioPresets();
            case IMAGE -> imagePresets();
            case DOCUMENT -> documentPresets();
            case UNKNOWN -> new ArrayList<>();
        };
    }

    /**
     * Creates a new PresetsBySection with the video presets replaced.
     * 
     * <p>
     * This method provides an immutable update operation, returning a new instance
     * with the specified video presets while preserving all other preset lists.
     * 
     * @param newVideoPresets the new video presets list, or null for empty list
     * @return a new PresetsBySection with updated video presets
     */
    public PresetsBySection withVideoPresets(List<SectionPreset> newVideoPresets) {
        return new PresetsBySection(newVideoPresets, audioPresets, imagePresets, documentPresets);
    }

    /**
     * Creates a new PresetsBySection with the audio presets replaced.
     * 
     * <p>
     * This method provides an immutable update operation, returning a new instance
     * with the specified audio presets while preserving all other preset lists.
     * 
     * @param newAudioPresets the new audio presets list, or null for empty list
     * @return a new PresetsBySection with updated audio presets
     */
    public PresetsBySection withAudioPresets(List<SectionPreset> newAudioPresets) {
        return new PresetsBySection(videoPresets, newAudioPresets, imagePresets, documentPresets);
    }

    /**
     * Creates a new PresetsBySection with the image presets replaced.
     * 
     * <p>
     * This method provides an immutable update operation, returning a new instance
     * with the specified image presets while preserving all other preset lists.
     * 
     * @param newImagePresets the new image presets list, or null for empty list
     * @return a new PresetsBySection with updated image presets
     */
    public PresetsBySection withImagePresets(List<SectionPreset> newImagePresets) {
        return new PresetsBySection(videoPresets, audioPresets, newImagePresets, documentPresets);
    }

    /**
     * Creates a new PresetsBySection with the document presets replaced.
     * 
     * <p>
     * This method provides an immutable update operation, returning a new instance
     * with the specified document presets while preserving all other preset lists.
     * 
     * @param newDocumentPresets the new document presets list, or null for empty
     *                           list
     * @return a new PresetsBySection with updated document presets
     */
    public PresetsBySection withDocumentPresets(List<SectionPreset> newDocumentPresets) {
        return new PresetsBySection(videoPresets, audioPresets, imagePresets, newDocumentPresets);
    }

    /**
     * Returns the total number of presets across all categories.
     * 
     * @return the sum of all preset list sizes
     */
    public int totalPresetCount() {
        return videoPresets.size() + audioPresets.size() +
                imagePresets.size() + documentPresets.size();
    }

    /**
     * Validates all presets in this container.
     * 
     * <p>
     * This method checks that all presets across all categories are valid
     * according to their individual {@link SectionPreset#isValid()} method.
     * 
     * @return true if all presets are valid, false if any preset is invalid
     */
    @JsonIgnore
    public boolean isValid() {
        return videoPresets.stream().allMatch(SectionPreset::isValid) &&
                audioPresets.stream().allMatch(SectionPreset::isValid) &&
                imagePresets.stream().allMatch(SectionPreset::isValid) &&
                documentPresets.stream().allMatch(SectionPreset::isValid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PresetsBySection that = (PresetsBySection) o;
        return Objects.equals(videoPresets, that.videoPresets) &&
                Objects.equals(audioPresets, that.audioPresets) &&
                Objects.equals(imagePresets, that.imagePresets) &&
                Objects.equals(documentPresets, that.documentPresets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(videoPresets, audioPresets, imagePresets, documentPresets);
    }

    @Override
    public String toString() {
        return "PresetsBySection{" +
                "videoPresets=" + videoPresets.size() + " presets" +
                ", audioPresets=" + audioPresets.size() + " presets" +
                ", imagePresets=" + imagePresets.size() + " presets" +
                ", documentPresets=" + documentPresets.size() + " presets" +
                ", total=" + totalPresetCount() +
                '}';
    }
}
