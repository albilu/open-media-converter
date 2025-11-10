package org.omc.model;

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
     * Returns the display name when converting to JSON.
     * 
     * @return the display name
     */
    @JsonValue
    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Creates a ResizeMode from a display name string.
     * 
     * @param displayName the display name
     * @return the corresponding ResizeMode
     * @throws IllegalArgumentException if the display name is not recognized
     */
    @JsonCreator
    public static ResizeMode fromDisplayName(String displayName) {
        for (ResizeMode mode : values()) {
            if (mode.displayName.equals(displayName)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown resize mode: " + displayName);
    }
}
