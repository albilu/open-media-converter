/* filepath: src/main/java/org/omc/model/ImageFlip.java */
package org.omc.model;

/**
 * Enumeration of image flip options for image transformations.
 * <p>
 * Provides flip operations for mirroring images horizontally, vertically, or
 * both.
 * NONE option represents no flip applied to the image.
 * </p>
 * <p>
 * This enum is used by ImageSettings to specify flip transformations
 * that will be applied during image conversion via ImageMagick.
 * </p>
 *
 * @see ImageSettings
 * @since 1.0.0
 * 
 *        Requirements coverage:
 *        - REQ-IMG-2.1: Image flip options (None, Horizontal, Vertical, Both)
 */
public enum ImageFlip {

    /**
     * No flip - keep original orientation.
     */
    NONE("None", false, false),

    /**
     * Flip horizontally (mirror left-to-right).
     * Creates a mirror image along the vertical axis.
     */
    HORIZONTAL("Horizontal", true, false),

    /**
     * Flip vertically (mirror top-to-bottom).
     * Creates a mirror image along the horizontal axis.
     */
    VERTICAL("Vertical", false, true),

    /**
     * Flip both horizontally and vertically.
     * Equivalent to a 180-degree rotation.
     */
    BOTH("Both", true, true);

    private final String displayName;
    private final boolean flipHorizontal;
    private final boolean flipVertical;

    /**
     * Constructs an ImageFlip enum constant.
     *
     * @param displayName    the human-readable name for UI display
     * @param flipHorizontal whether to flip horizontally (left-to-right mirror)
     * @param flipVertical   whether to flip vertically (top-to-bottom mirror)
     */
    ImageFlip(String displayName, boolean flipHorizontal, boolean flipVertical) {
        this.displayName = displayName;
        this.flipHorizontal = flipHorizontal;
        this.flipVertical = flipVertical;
    }

    /**
     * Gets the display name for UI presentation.
     *
     * @return the human-readable flip operation name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if horizontal flip is enabled.
     *
     * @return true if image should be flipped horizontally
     */
    public boolean isFlipHorizontal() {
        return flipHorizontal;
    }

    /**
     * Checks if vertical flip is enabled.
     *
     * @return true if image should be flipped vertically
     */
    public boolean isFlipVertical() {
        return flipVertical;
    }

    /**
     * Checks if this flip represents no transformation.
     *
     * @return true if this is NONE, false otherwise
     */
    public boolean isNone() {
        return this == NONE;
    }
}
