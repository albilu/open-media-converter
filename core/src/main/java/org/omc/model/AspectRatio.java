// filepath: src/main/java/org/omc/model/AspectRatio.java

package org.omc.model;

/**
 * Supported video aspect ratios for conversion.
 * 
 * <p>
 * Aspect ratios define the proportional relationship between width and height
 * of video output. Each ratio can trigger FFmpeg filters to add letterboxing
 * (horizontal black bars) or pillarboxing (vertical black bars) to achieve
 * the target display aspect ratio.
 * </p>
 * 
 * <p>
 * Requirements: REQ-VID-2.1
 * </p>
 * 
 * @since 1.0.0
 */
public enum AspectRatio {
    /**
     * Keep original aspect ratio from source video.
     * No aspect ratio filters applied.
     */
    KEEP_ORIGINAL("Keep Original", null),

    /**
     * 16:9 widescreen aspect ratio (1.777:1).
     * Standard for HD video, YouTube, modern displays.
     */
    RATIO_16_9("16:9 (Widescreen)", 16.0 / 9.0),

    /**
     * 4:3 standard aspect ratio (1.333:1).
     * Classic TV and older video formats.
     */
    RATIO_4_3("4:3 (Standard)", 4.0 / 3.0),

    /**
     * 1:1 square aspect ratio.
     * Used for Instagram posts and square video formats.
     */
    RATIO_1_1("1:1 (Square)", 1.0),

    /**
     * 21:9 ultrawide aspect ratio (2.333:1).
     * Cinematic widescreen format.
     */
    RATIO_21_9("21:9 (Ultrawide)", 21.0 / 9.0),

    /**
     * 9:16 vertical aspect ratio (0.5625:1).
     * Used for mobile-first vertical video (Instagram Stories, TikTok).
     */
    RATIO_9_16("9:16 (Vertical)", 9.0 / 16.0),

    /**
     * 3:2 aspect ratio (1.5:1).
     * Common in photography and some video formats.
     */
    RATIO_3_2("3:2", 3.0 / 2.0),

    /**
     * 2.39:1 cinema aspect ratio.
     * Standard theatrical widescreen format.
     */
    RATIO_2_39_1("2.39:1 (Cinema)", 2.39);

    private final String displayName;
    private final Double ratio;

    /**
     * Constructs an AspectRatio enum constant.
     *
     * @param displayName Human-readable name for UI display
     * @param ratio       Numeric aspect ratio (width/height), or null for
     *                    KEEP_ORIGINAL
     */
    AspectRatio(String displayName, Double ratio) {
        this.displayName = displayName;
        this.ratio = ratio;
    }

    /**
     * Gets the display name for UI presentation.
     *
     * @return Human-readable aspect ratio name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the numeric aspect ratio.
     *
     * @return Ratio as width/height decimal, or null for KEEP_ORIGINAL
     */
    public Double getRatio() {
        return ratio;
    }

    /**
     * Checks if this aspect ratio preserves the original source aspect ratio.
     *
     * @return true if this is KEEP_ORIGINAL, false otherwise
     */
    public boolean isOriginal() {
        return this == KEEP_ORIGINAL;
    }
}
