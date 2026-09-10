package org.omc.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents image resize mode options for image conversions.
 * 
 * <p>
 * Requirement REQ-4.8: Image resize mode dropdown population with scaling
 * algorithms.
 * 
 * <p>
 * This enum provides various scaling algorithms that can be used when resizing
 * images,
 * from simple aspect ratio preservation to advanced interpolation algorithms.
 */
public enum ResizeMode {
    /**
     * No resizing - preserve original dimensions.
     */
    NONE("None"),

    /**
     * Fit within bounds while maintaining aspect ratio.
     */
    FIT("Fit (maintain aspect)"),

    /**
     * Fill bounds by cropping to maintain aspect ratio.
     */
    FILL("Fill (crop)"),

    /**
     * Stretch to fill bounds without maintaining aspect ratio.
     */
    STRETCH("Stretch"),

    /**
     * Lanczos resampling algorithm (high quality, slower).
     */
    LANCZOS("Lanczos"),

    /**
     * Bicubic interpolation algorithm (good quality, moderate speed).
     */
    BICUBIC("Bicubic"),

    /**
     * Bilinear interpolation algorithm (decent quality, fast).
     */
    BILINEAR("Bilinear"),

    /**
     * Nearest neighbor algorithm (low quality, very fast).
     */
    NEAREST_NEIGHBOR("Nearest Neighbor");

    /**
     * Makes the silent unknown-value coercion observable in logs (relevant to
     * the SettingsManager salvage path).
     */
    private static final Logger LOG = LoggerFactory.getLogger(ResizeMode.class);

    private final String displayName;

    /**
     * Constructs a ResizeMode with the given display name.
     * 
     * @param displayName the human-readable display name
     */
    ResizeMode(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the human-readable display name for this resize mode.
     * 
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the value used for JSON persistence: the enum name.
     *
     * <p>
     * Persistence uses the stable enum name (like every other enum in this
     * package) so rewording display labels can never break settings or preset
     * deserialization. Use {@link #getDisplayName()} for UI labels.
     * </p>
     *
     * @return the enum name (e.g. "FIT")
     */
    @JsonValue
    public String getJsonName() {
        return name();
    }

    /**
     * Returns the human-readable display name.
     *
     * @return the display name
     */
    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Resolves a ResizeMode from a persisted or legacy value.
     *
     * <p>
     * Resolution order:
     * </p>
     * <ol>
     * <li>exact enum name match (e.g. "FIT") — current persistence format</li>
     * <li>legacy display name match (e.g. "Fit (maintain aspect)") — files
     * written by older versions</li>
     * <li>otherwise {@link #FIT} — deterministic safe default so a corrupt or
     * unrecognized value can never break settings/preset deserialization</li>
     * </ol>
     *
     * @param value the enum name or legacy display name; null passes through
     *              as null so callers (e.g. ImageSettings) can apply their own
     *              absent-value defaults
     * @return the corresponding ResizeMode, or null if the input is null
     */
    @JsonCreator
    public static ResizeMode fromDisplayName(String value) {
        if (value == null) {
            return null;
        }
        for (ResizeMode mode : values()) {
            if (mode.name().equals(value)) {
                return mode;
            }
        }
        for (ResizeMode mode : values()) {
            if (mode.displayName.equals(value)) {
                return mode;
            }
        }
        LOG.warn("Unknown ResizeMode value '{}'; falling back to FIT", value);
        return FIT;
    }
}
