package org.omc.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents a video or image resolution with width and height in pixels.
 * Immutable value object.
 * 
 * Requirements: REQ-003.2, REQ-006.1, REQ-006.3
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Resolution {
    private final int width;
    private final int height;

    /**
     * Creates a new Resolution.
     *
     * @param width  The width in pixels (must be positive)
     * @param height The height in pixels (must be positive)
     * @throws IllegalArgumentException if width or height is not positive
     */
    @JsonCreator
    public Resolution(
            @JsonProperty("width") int width,
            @JsonProperty("height") int height) {
        if (width <= 0) {
            throw new IllegalArgumentException("Width must be positive, got: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("Height must be positive, got: " + height);
        }
        this.width = width;
        this.height = height;
    }

    /**
     * Gets the width in pixels.
     *
     * @return The width
     */
    @JsonProperty("width")
    public int getWidth() {
        return width;
    }

    /**
     * Gets the height in pixels.
     *
     * @return The height
     */
    @JsonProperty("height")
    public int getHeight() {
        return height;
    }

    /**
     * Calculates the aspect ratio (width / height).
     *
     * @return The aspect ratio as a double
     */
    public double getAspectRatio() {
        return (double) width / height;
    }

    /**
     * Calculates the total number of pixels (width * height).
     *
     * @return The total pixel count
     */
    public long getPixelCount() {
        return (long) width * height;
    }

    /**
     * Checks if this resolution is in landscape orientation (width > height).
     *
     * @return true if landscape, false otherwise
     */
    public boolean isLandscape() {
        return width > height;
    }

    /**
     * Checks if this resolution is in portrait orientation (height > width).
     *
     * @return true if portrait, false otherwise
     */
    public boolean isPortrait() {
        return height > width;
    }

    /**
     * Checks if this resolution is square (width == height).
     *
     * @return true if square, false otherwise
     */
    public boolean isSquare() {
        return width == height;
    }

    /**
     * Scales this resolution by a factor while maintaining aspect ratio.
     *
     * @param scale The scaling factor (e.g., 0.5 for half size, 2.0 for double
     *              size)
     * @return A new Resolution with scaled dimensions
     * @throws IllegalArgumentException if scale is not positive
     */
    public Resolution scale(double scale) {
        if (scale <= 0) {
            throw new IllegalArgumentException("Scale must be positive, got: " + scale);
        }
        int newWidth = (int) Math.round(width * scale);
        int newHeight = (int) Math.round(height * scale);

        // Ensure at least 1x1
        newWidth = Math.max(1, newWidth);
        newHeight = Math.max(1, newHeight);

        return new Resolution(newWidth, newHeight);
    }

    /**
     * Creates a new resolution that fits within the given bounds while maintaining
     * aspect ratio.
     *
     * @param maxWidth  Maximum width
     * @param maxHeight Maximum height
     * @return A new Resolution that fits within bounds
     */
    public Resolution fitWithin(int maxWidth, int maxHeight) {
        double widthScale = (double) maxWidth / width;
        double heightScale = (double) maxHeight / height;
        double scale = Math.min(widthScale, heightScale);

        if (scale >= 1.0) {
            return this; // Already fits
        }

        return scale(scale);
    }

    /**
     * Parses a resolution string in format "WIDTHxHEIGHT" (e.g., "1920x1080").
     *
     * @param resolutionString The resolution string
     * @return The parsed Resolution
     * @throws IllegalArgumentException if the string format is invalid
     */
    public static Resolution parse(String resolutionString) {
        if (resolutionString == null || resolutionString.isBlank()) {
            throw new IllegalArgumentException("Resolution string cannot be null or empty");
        }

        String[] parts = resolutionString.trim().toLowerCase().split("x");
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Invalid resolution format. Expected WIDTHxHEIGHT, got: " + resolutionString);
        }

        try {
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            return new Resolution(width, height);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid resolution format. Width and height must be integers: " + resolutionString, e);
        }
    }

    /**
     * Common resolution presets.
     */
    public static final Resolution HD_720P = new Resolution(1280, 720);
    public static final Resolution FULL_HD_1080P = new Resolution(1920, 1080);
    public static final Resolution QHD_1440P = new Resolution(2560, 1440);
    public static final Resolution UHD_4K = new Resolution(3840, 2160);
    public static final Resolution SD_480P = new Resolution(640, 480);

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Resolution that = (Resolution) o;
        return width == that.width && height == that.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height);
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }
}
