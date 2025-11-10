// filepath: src/main/java/org/omc/model/ImageSettings.java

package org.omc.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Image-specific conversion settings.
 * <p>
 * This class encapsulates all settings related to image format conversion,
 * including quality, resolution, compression, resize mode, rotation, flip,
 * and output format.
 * </p>
 * 
 * <p>
 * Requirements:
 * </p>
 * <ul>
 * <li>REQ-006.3: Image format conversion with quality controls</li>
 * <li>REQ-2.4: Image output format selection</li>
 * <li>REQ-4.8: Image resize mode support</li>
 * <li>REQ-IMG-1.1: Image rotation support</li>
 * <li>REQ-IMG-2.1: Image flip support</li>
 * </ul>
 * 
 * @since 1.0
 */
public final class ImageSettings {

    private final int quality; // 0-100 for JPEG/WebP, -1 for lossless
    private final Resolution resolution; // null for original
    private final boolean maintainAspectRatio;
    private final int compressionLevel; // 0-9 for PNG, 0=no compression, 9=max compression
    private final ResizeMode resizeMode; // Requirement REQ-4.8: Resize algorithm
    private final ImageRotation rotation; // Requirement REQ-IMG-1.1: Image rotation
    private final ImageFlip flip; // Requirement REQ-IMG-2.1: Image flip
    private final FileFormat outputFormat; // Requirement REQ-2.4: Output format for image conversion

    /**
     * Creates a new ImageSettings instance.
     * 
     * @param quality             the image quality (0-100, or -1 for lossless)
     * @param resolution          the target resolution, or null for original
     * @param maintainAspectRatio whether to maintain aspect ratio during resize
     * @param compressionLevel    the PNG compression level (0-9)
     * @param resizeMode          the resize algorithm to use
     * @param rotation            the rotation to apply (REQ-IMG-1.1)
     * @param flip                the flip operation to apply (REQ-IMG-2.1)
     * @param outputFormat        the target output format (must be IMAGE category)
     */
    @JsonCreator
    private ImageSettings(
            @JsonProperty("quality") int quality,
            @JsonProperty("resolution") Resolution resolution,
            @JsonProperty("maintainAspectRatio") boolean maintainAspectRatio,
            @JsonProperty("compressionLevel") int compressionLevel,
            @JsonProperty("resizeMode") ResizeMode resizeMode,
            @JsonProperty("rotation") ImageRotation rotation,
            @JsonProperty("flip") ImageFlip flip,
            @JsonProperty("outputFormat") FileFormat outputFormat) {
        this.quality = quality;
        this.resolution = resolution;
        this.maintainAspectRatio = maintainAspectRatio;
        this.compressionLevel = compressionLevel;
        this.resizeMode = resizeMode;
        // Requirement REQ-IMG-1.1, REQ-IMG-2.1: Default to NONE for backward
        // compatibility
        this.rotation = rotation != null ? rotation : ImageRotation.NONE;
        this.flip = flip != null ? flip : ImageFlip.NONE;
        this.outputFormat = outputFormat;
    }

    /**
     * Returns the image quality setting.
     * 
     * @return quality value (0-100 for lossy formats, -1 for lossless)
     */
    @JsonProperty("quality")
    public int quality() {
        return quality;
    }

    /**
     * Returns the target resolution.
     * 
     * @return the resolution, or null to keep original
     */
    @JsonProperty("resolution")
    public Resolution resolution() {
        return resolution;
    }

    /**
     * Returns whether to maintain aspect ratio during resize.
     * 
     * @return true if aspect ratio should be maintained
     */
    @JsonProperty("maintainAspectRatio")
    public boolean maintainAspectRatio() {
        return maintainAspectRatio;
    }

    /**
     * Returns the PNG compression level.
     * 
     * @return compression level (0=none, 9=maximum)
     */
    @JsonProperty("compressionLevel")
    public int compressionLevel() {
        return compressionLevel;
    }

    /**
     * Returns the resize algorithm mode.
     * 
     * @return the resize mode
     */
    @JsonProperty("resizeMode")
    public ResizeMode resizeMode() {
        return resizeMode;
    }

    /**
     * Returns the rotation setting.
     * Requirement REQ-IMG-1.1: Image rotation support.
     * 
     * @return the rotation
     */
    @JsonProperty("rotation")
    public ImageRotation rotation() {
        return rotation;
    }

    /**
     * Returns the flip setting.
     * Requirement REQ-IMG-2.1: Image flip support.
     * 
     * @return the flip
     */
    @JsonProperty("flip")
    public ImageFlip flip() {
        return flip;
    }

    /**
     * Returns the target output format.
     * Requirement REQ-2.4: Image output format selection.
     * 
     * @return the output format (must be IMAGE category)
     */
    @JsonProperty("outputFormat")
    public FileFormat outputFormat() {
        return outputFormat;
    }

    /**
     * Validates image settings.
     * Requirement REQ-2.4: Validate output format is IMAGE category.
     * Requirement REQ-PDF-1.2: Accept PDF as valid image output format.
     * Requirement REQ-4.8: When resizeMode is NONE, ignore resolution requirements.
     * 
     * @return true if settings are valid
     */
    @JsonIgnore
    public boolean isValid() {
        // Quality validation (0-100 or -1 for lossless)
        if (quality != -1 && (quality < 0 || quality > 100)) {
            return false;
        }

        // Compression level validation (0-9)
        if (compressionLevel < 0 || compressionLevel > 9) {
            return false;
        }

        // Output format must support IMAGE category (primary or secondary)
        if (outputFormat == null || !outputFormat.supportsCategory(FormatCategory.IMAGE)) {
            return false;
        }

        // Resolution is validated in its constructor (width, height > 0)
        // When resizeMode is NONE, resolution requirements are ignored during
        // conversion

        return true;
    }

    /**
     * Creates a new Builder for ImageSettings.
     * 
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating ImageSettings instances.
     */
    public static class Builder {
        private int quality = 0; // 0 means not set (use tool defaults)
        private Resolution resolution; // null means original
        private boolean maintainAspectRatio = true;
        private int compressionLevel = 0; // 0 means not set (no compression)
        private ResizeMode resizeMode = ResizeMode.NONE;
        private ImageRotation rotation = ImageRotation.NONE; // Requirement REQ-IMG-1.1: Default rotation
        private ImageFlip flip = ImageFlip.NONE; // Requirement REQ-IMG-2.1: Default flip
        private FileFormat outputFormat = FileFormat.PNG;

        private void validate() {
            if (quality != -1 && (quality < 0 || quality > 100)) {
                throw new IllegalArgumentException("Quality must be -1 or between 0 and 100");
            }
            if (compressionLevel < 0 || compressionLevel > 9) {
                throw new IllegalArgumentException("Compression level must be between 0 and 9");
            }
            // Requirement REQ-PDF-1.2: Accept formats that support IMAGE category
            // (including PDF)
            if (outputFormat == null || !outputFormat.supportsCategory(FormatCategory.IMAGE)) {
                throw new IllegalArgumentException("Output format must support IMAGE category, got: " + outputFormat);
            }
        }

        /**
         * Sets the image quality.
         * 
         * @param quality the quality value (0-100, or -1 for lossless)
         * @return this builder
         */
        public Builder quality(int quality) {
            this.quality = quality;
            return this;
        }

        /**
         * Sets the target resolution.
         * 
         * @param resolution the resolution, or null for original
         * @return this builder
         */
        public Builder resolution(Resolution resolution) {
            this.resolution = resolution;
            return this;
        }

        /**
         * Sets whether to maintain aspect ratio during resize.
         * 
         * @param maintainAspectRatio true to maintain aspect ratio
         * @return this builder
         */
        public Builder maintainAspectRatio(boolean maintainAspectRatio) {
            this.maintainAspectRatio = maintainAspectRatio;
            return this;
        }

        /**
         * Sets the PNG compression level.
         * 
         * @param compressionLevel the compression level (0-9)
         * @return this builder
         */
        public Builder compressionLevel(int compressionLevel) {
            this.compressionLevel = compressionLevel;
            return this;
        }

        /**
         * Sets the resize algorithm mode.
         * Requirement REQ-4.8: Image resize mode support.
         * 
         * @param resizeMode the resize mode
         * @return this builder
         */
        public Builder resizeMode(ResizeMode resizeMode) {
            this.resizeMode = resizeMode;
            return this;
        }

        /**
         * Sets the rotation.
         * Requirement REQ-IMG-1.1: Image rotation support.
         * 
         * @param rotation the rotation
         * @return this builder
         */
        public Builder rotation(ImageRotation rotation) {
            this.rotation = rotation;
            return this;
        }

        /**
         * Sets the flip operation.
         * Requirement REQ-IMG-2.1: Image flip support.
         * 
         * @param flip the flip operation
         * @return this builder
         */
        public Builder flip(ImageFlip flip) {
            this.flip = flip;
            return this;
        }

        /**
         * Sets the output format.
         * Requirement REQ-2.4: Image output format selection.
         * 
         * @param outputFormat the output format (must be IMAGE category)
         * @return this builder
         */
        public Builder outputFormat(FileFormat outputFormat) {
            this.outputFormat = outputFormat;
            return this;
        }

        /**
         * Builds the ImageSettings instance.
         * Validates the image settings.
         *
         * @return a new ImageSettings instance
         * @throws IllegalArgumentException if the settings are invalid
         */
        public ImageSettings build() {
            validate();
            return new ImageSettings(quality, resolution, maintainAspectRatio,
                    compressionLevel, resizeMode, rotation, flip, outputFormat);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ImageSettings that = (ImageSettings) o;
        return quality == that.quality &&
                maintainAspectRatio == that.maintainAspectRatio &&
                compressionLevel == that.compressionLevel &&
                Objects.equals(resolution, that.resolution) &&
                resizeMode == that.resizeMode &&
                rotation == that.rotation &&
                flip == that.flip &&
                outputFormat == that.outputFormat;
    }

    @Override
    public int hashCode() {
        return Objects.hash(quality, resolution, maintainAspectRatio,
                compressionLevel, resizeMode, rotation, flip, outputFormat);
    }

    @Override
    public String toString() {
        return "ImageSettings{" +
                "quality=" + quality +
                ", resolution=" + resolution +
                ", maintainAspectRatio=" + maintainAspectRatio +
                ", compressionLevel=" + compressionLevel +
                ", resizeMode=" + resizeMode +
                ", rotation=" + rotation +
                ", flip=" + flip +
                ", outputFormat=" + outputFormat +
                '}';
    }
}
