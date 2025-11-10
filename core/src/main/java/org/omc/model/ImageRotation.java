/* filepath: src/main/java/org/omc/model/ImageRotation.java */
package org.omc.model;

/**
 * Enumeration of image rotation options for image transformations.
 * <p>
 * Provides rotation angles for clockwise and counter-clockwise rotations.
 * NONE option represents no rotation applied to the image.
 * </p>
 * <p>
 * This enum is used by ImageSettings to specify rotation transformations
 * that will be applied during image conversion via ImageMagick.
 * </p>
 *
 * @see ImageSettings
 * @since 1.0.0
 * 
 *        Requirements coverage:
 *        - REQ-IMG-1.1: Image rotation options (None, 90° CW, 180°, 90° CCW)
 */
public enum ImageRotation {

    /**
     * No rotation - keep original orientation.
     * Degrees value is null to indicate no transformation.
     */
    NONE("None", null),

    /**
     * Rotate 90 degrees clockwise.
     * Converts portrait to landscape (or vice versa).
     */
    CLOCKWISE_90("90° Clockwise", 90),

    /**
     * Rotate 180 degrees (upside down).
     * Inverts the image vertically and horizontally.
     */
    ROTATE_180("180°", 180),

    /**
     * Rotate 90 degrees counter-clockwise (270 degrees clockwise).
     * Converts landscape to portrait (or vice versa).
     */
    COUNTER_CLOCKWISE_90("90° Counter-Clockwise", 270);

    private final String displayName;
    private final Integer degrees;

    /**
     * Constructs an ImageRotation enum constant.
     *
     * @param displayName the human-readable name for UI display
     * @param degrees     the rotation angle in degrees (null for NONE)
     */
    ImageRotation(String displayName, Integer degrees) {
        this.displayName = displayName;
        this.degrees = degrees;
    }

    /**
     * Gets the display name for UI presentation.
     *
     * @return the human-readable rotation name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the rotation angle in degrees.
     *
     * @return the rotation angle, or null if no rotation (NONE)
     */
    public Integer getDegrees() {
        return degrees;
    }

    /**
     * Checks if this rotation represents no transformation.
     *
     * @return true if this is NONE, false otherwise
     */
    public boolean isNone() {
        return this == NONE;
    }
}
