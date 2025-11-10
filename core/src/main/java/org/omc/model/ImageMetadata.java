// filepath: src/main/java/org/omc/model/ImageMetadata.java

package org.omc.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata for image files.
 * Requirement REQ-002.2: Image file metadata including width, height, and color
 * space.
 */
public final class ImageMetadata implements MediaMetadata {

    private final int width; // Image width in pixels
    private final int height; // Image height in pixels
    private final String colorSpace; // Color space (e.g., "RGB", "CMYK", "Grayscale")
    private final int bitDepth; // Bit depth per channel (e.g., 8, 16, 24)
    private final boolean hasAlpha; // Whether image has alpha channel

    @JsonCreator
    public ImageMetadata(
            @JsonProperty("width") int width,
            @JsonProperty("height") int height,
            @JsonProperty("colorSpace") String colorSpace,
            @JsonProperty("bitDepth") int bitDepth,
            @JsonProperty("hasAlpha") boolean hasAlpha) {
        this.width = width;
        this.height = height;
        this.colorSpace = colorSpace;
        this.bitDepth = bitDepth;
        this.hasAlpha = hasAlpha;
    }

    /**
     * Creates an ImageMetadata builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    @JsonIgnore
    public FormatCategory getCategory() {
        return FormatCategory.IMAGE;
    }

    @Override
    @JsonIgnore
    public boolean isValid() {
        return width > 0 && height > 0
                && colorSpace != null && !colorSpace.isBlank()
                && bitDepth > 0;
    }

    @Override
    @JsonIgnore
    public String getSummary() {
        return String.format("%dx%d, %s, %d-bit%s",
                width, height, colorSpace, bitDepth,
                hasAlpha ? ", alpha" : "");
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getColorSpace() {
        return colorSpace;
    }

    public int getBitDepth() {
        return bitDepth;
    }

    public boolean hasAlpha() {
        return hasAlpha;
    }

    /**
     * Gets the image resolution as "widthxheight" (e.g., "1920x1080").
     */
    @JsonIgnore
    public String getResolution() {
        return width + "x" + height;
    }

    /**
     * Gets the aspect ratio (width / height).
     */
    @JsonIgnore
    public double getAspectRatio() {
        return height > 0 ? (double) width / height : 0.0;
    }

    /**
     * Gets the total number of pixels (width * height).
     */
    @JsonIgnore
    public long getPixelCount() {
        return (long) width * height;
    }

    /**
     * Gets the megapixel count.
     */
    @JsonIgnore
    public double getMegapixels() {
        return getPixelCount() / 1_000_000.0;
    }

    /**
     * Checks if this is a high-resolution image (> 5 megapixels).
     */
    @JsonIgnore
    public boolean isHighResolution() {
        return getMegapixels() > 5.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ImageMetadata that = (ImageMetadata) o;
        return width == that.width &&
                height == that.height &&
                bitDepth == that.bitDepth &&
                hasAlpha == that.hasAlpha &&
                Objects.equals(colorSpace, that.colorSpace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height, colorSpace, bitDepth, hasAlpha);
    }

    @Override
    public String toString() {
        return "ImageMetadata{" +
                "width=" + width +
                ", height=" + height +
                ", colorSpace='" + colorSpace + '\'' +
                ", bitDepth=" + bitDepth +
                ", hasAlpha=" + hasAlpha +
                '}';
    }

    /**
     * Builder for ImageMetadata.
     */
    public static class Builder {
        private int width;
        private int height;
        private String colorSpace;
        private int bitDepth;
        private boolean hasAlpha;

        private Builder() {
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Builder colorSpace(String colorSpace) {
            this.colorSpace = colorSpace;
            return this;
        }

        public Builder bitDepth(int bitDepth) {
            this.bitDepth = bitDepth;
            return this;
        }

        public Builder hasAlpha(boolean hasAlpha) {
            this.hasAlpha = hasAlpha;
            return this;
        }

        public ImageMetadata build() {
            return new ImageMetadata(width, height, colorSpace, bitDepth, hasAlpha);
        }
    }
}
