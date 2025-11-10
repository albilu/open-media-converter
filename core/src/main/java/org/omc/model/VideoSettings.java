// filepath: src/main/java/org/omc/model/VideoSettings.java

package org.omc.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Video-specific conversion settings.
 * <p>
 * This class encapsulates all settings related to video format conversion,
 * including codec, bitrate, resolution, frame rate, preset, CRF, aspect ratio,
 * and output format.
 * </p>
 * 
 * <p>
 * Requirements:
 * </p>
 * <ul>
 * <li>REQ-006.1: Video format conversion with quality controls</li>
 * <li>REQ-2.2: Video output format selection</li>
 * <li>REQ-4.1: Video format conversion support</li>
 * <li>REQ-VID-1.1: Support GPU-accelerated codecs (h264_nvenc, hevc_nvenc)</li>
 * <li>REQ-VID-2.1: Support aspect ratio adjustment</li>
 * </ul>
 * 
 * @since 1.0
 */
public final class VideoSettings {

    private final String codec;
    private final int bitrate; // in kbps
    private final Resolution resolution;
    private final int frameRate; // -1 for original
    private final String preset; // FFmpeg preset: ultrafast, superfast, veryfast, faster, fast, medium, slow,
                                 // slower, veryslow
    private final int crf; // Constant Rate Factor (0-51, 23 is default, lower is better quality)
    private final AspectRatio aspectRatio; // Requirement REQ-VID-2.1: Aspect ratio adjustment
    private final FileFormat outputFormat; // Requirement REQ-2.2: Output format for video conversion

    @JsonCreator
    private VideoSettings(
            @JsonProperty("codec") String codec,
            @JsonProperty("bitrate") int bitrate,
            @JsonProperty("resolution") Resolution resolution,
            @JsonProperty("frameRate") int frameRate,
            @JsonProperty("preset") String preset,
            @JsonProperty("crf") int crf,
            @JsonProperty("aspectRatio") AspectRatio aspectRatio,
            @JsonProperty("outputFormat") FileFormat outputFormat) {
        this.codec = codec;
        this.bitrate = bitrate;
        this.resolution = resolution;
        this.frameRate = frameRate;
        this.preset = preset;
        this.crf = crf;
        // Requirement REQ-VID-2.1: Default to KEEP_ORIGINAL for backward compatibility
        this.aspectRatio = aspectRatio != null ? aspectRatio : AspectRatio.KEEP_ORIGINAL;
        this.outputFormat = outputFormat;
    }

    /**
     * Returns the video codec.
     *
     * @return the codec string
     */
    @JsonProperty("codec")
    public String codec() {
        return codec;
    }

    /**
     * Returns the bitrate in kbps.
     *
     * @return the bitrate
     */
    @JsonProperty("bitrate")
    public int bitrate() {
        return bitrate;
    }

    /**
     * Returns the target resolution.
     *
     * @return the resolution, or null for original
     */
    @JsonProperty("resolution")
    public Resolution resolution() {
        return resolution;
    }

    /**
     * Returns the frame rate, or -1 for original.
     *
     * @return the frame rate
     */
    @JsonProperty("frameRate")
    public int frameRate() {
        return frameRate;
    }

    /**
     * Returns the FFmpeg preset.
     *
     * @return the preset string
     */
    @JsonProperty("preset")
    public String preset() {
        return preset;
    }

    /**
     * Returns the Constant Rate Factor.
     *
     * @return the CRF value
     */
    @JsonProperty("crf")
    public int crf() {
        return crf;
    }

    /**
     * Returns the aspect ratio setting.
     * Requirement REQ-VID-2.1: Aspect ratio adjustment support.
     *
     * @return the aspect ratio
     */
    @JsonProperty("aspectRatio")
    public AspectRatio aspectRatio() {
        return aspectRatio;
    }

    /**
     * Returns the output file format.
     * Requirement REQ-2.2: Video output format selection.
     *
     * @return the output format
     */
    @JsonProperty("outputFormat")
    public FileFormat outputFormat() {
        return outputFormat;
    }

    /**
     * Validates video settings.
     * Requirement REQ-2.2: Validate output format is VIDEO category.
     * Requirement REQ-VID-1.1: Accept GPU-accelerated codecs.
     * 
     * @return true if settings are valid
     */
    @JsonIgnore
    public boolean isValid() {
        // Codec validation - accept standard and GPU codecs (Requirement REQ-VID-1.1)
        if (codec == null || codec.trim().isEmpty()) {
            return false;
        }
        if (!isValidCodec(codec)) {
            return false;
        }

        // Output format validation (Requirement REQ-2.2)
        if (outputFormat == null || !outputFormat.supportsCategory(FormatCategory.VIDEO)) {
            return false;
        }

        // Bitrate validation
        if (bitrate < 500 || bitrate > 50000) {
            return false;
        }

        // Frame rate validation (-1 for original, or 1-120 fps)
        if (frameRate != -1 && (frameRate < 1 || frameRate > 120)) {
            return false;
        }

        // CRF validation (0-51)
        if (crf < 0 || crf > 51) {
            return false;
        }

        // Preset validation
        if (preset != null && !isValidPreset(preset)) {
            return false;
        }

        // Aspect ratio validation
        if (aspectRatio == null) {
            return false;
        }

        // Resolution is validated in its constructor (width, height > 0)
        // No additional validation needed here

        return true;
    }

    /**
     * Validates codec name.
     * Requirement REQ-VID-1.1: Accept GPU-accelerated codecs (h264_nvenc,
     * hevc_nvenc, mpeg4).
     * Accepts any non-null, non-blank codec string for flexibility.
     * 
     * @param codec the codec name
     * @return true if codec is valid (non-null and non-blank)
     */
    private static boolean isValidCodec(String codec) {
        return codec != null && !codec.isBlank();
    }

    private static boolean isValidPreset(String preset) {
        return preset.equals("ultrafast") ||
                preset.equals("superfast") ||
                preset.equals("veryfast") ||
                preset.equals("faster") ||
                preset.equals("fast") ||
                preset.equals("medium") ||
                preset.equals("slow") ||
                preset.equals("slower") ||
                preset.equals("veryslow");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String codec = "libx264"; // H.264 default
        private int bitrate = 5000; // 5 Mbps default
        private Resolution resolution; // null means original
        private int frameRate = -1; // -1 means original
        private String preset = "medium";
        private int crf = 23; // Default CRF
        private AspectRatio aspectRatio = AspectRatio.KEEP_ORIGINAL; // Requirement REQ-VID-2.1: Default aspect ratio
        private FileFormat outputFormat = FileFormat.MP4; // Requirement REQ-2.2: Default to MP4

        /**
         * Sets the video codec.
         *
         * @param codec the codec string
         * @return this builder
         */
        public Builder codec(String codec) {
            this.codec = codec;
            return this;
        }

        /**
         * Sets the bitrate in kbps.
         *
         * @param bitrate the bitrate
         * @return this builder
         */
        public Builder bitrate(int bitrate) {
            this.bitrate = bitrate;
            return this;
        }

        /**
         * Sets the resolution.
         *
         * @param resolution the resolution, or null for original
         * @return this builder
         */
        public Builder resolution(Resolution resolution) {
            this.resolution = resolution;
            return this;
        }

        /**
         * Sets the frame rate.
         *
         * @param frameRate the frame rate, or -1 for original
         * @return this builder
         */
        public Builder frameRate(int frameRate) {
            this.frameRate = frameRate;
            return this;
        }

        /**
         * Sets the FFmpeg preset.
         *
         * @param preset the preset string
         * @return this builder
         */
        public Builder preset(String preset) {
            this.preset = preset;
            return this;
        }

        /**
         * Sets the Constant Rate Factor.
         *
         * @param crf the CRF value
         * @return this builder
         */
        public Builder crf(int crf) {
            this.crf = crf;
            return this;
        }

        /**
         * Sets the aspect ratio.
         * Requirement REQ-VID-2.1: Aspect ratio adjustment support.
         *
         * @param aspectRatio the aspect ratio
         * @return this builder
         */
        public Builder aspectRatio(AspectRatio aspectRatio) {
            this.aspectRatio = aspectRatio;
            return this;
        }

        /**
         * Sets the output format.
         *
         * @param outputFormat the file format
         * @return this builder
         */
        public Builder outputFormat(FileFormat outputFormat) {
            this.outputFormat = outputFormat;
            return this;
        }

        /**
         * Builds the VideoSettings instance.
         *
         * @return a new VideoSettings
         * @throws IllegalArgumentException if the settings are invalid
         */
        public VideoSettings build() {
            validate();
            return new VideoSettings(codec, bitrate, resolution, frameRate, preset, crf, aspectRatio, outputFormat);
        }

        private void validate() {
            if (codec == null || codec.trim().isEmpty()) {
                throw new IllegalArgumentException("Codec must not be null or empty");
            }
            if (!VideoSettings.isValidCodec(codec)) {
                throw new IllegalArgumentException("Invalid codec: " + codec);
            }
            if (outputFormat == null || !outputFormat.supportsCategory(FormatCategory.VIDEO)) {
                throw new IllegalArgumentException("Output format must be VIDEO category");
            }
            if (bitrate < 500 || bitrate > 50000) {
                throw new IllegalArgumentException("Bitrate must be between 500 and 50000 kbps");
            }
            if (frameRate != -1 && (frameRate < 1 || frameRate > 120)) {
                throw new IllegalArgumentException("Frame rate must be -1 or between 1 and 120");
            }
            if (crf < 0 || crf > 51) {
                throw new IllegalArgumentException("CRF must be between 0 and 51");
            }
            if (preset != null && !VideoSettings.isValidPreset(preset)) {
                throw new IllegalArgumentException("Invalid preset: " + preset);
            }
            if (aspectRatio == null) {
                throw new IllegalArgumentException("Aspect ratio must not be null");
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        VideoSettings that = (VideoSettings) o;
        return bitrate == that.bitrate &&
                frameRate == that.frameRate &&
                crf == that.crf &&
                Objects.equals(codec, that.codec) &&
                Objects.equals(resolution, that.resolution) &&
                Objects.equals(preset, that.preset) &&
                aspectRatio == that.aspectRatio &&
                Objects.equals(outputFormat, that.outputFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codec, bitrate, resolution, frameRate, preset, crf, aspectRatio, outputFormat);
    }

    @Override
    public String toString() {
        return "VideoSettings{" +
                "codec='" + codec + '\'' +
                ", bitrate=" + bitrate +
                ", resolution=" + resolution +
                ", frameRate=" + frameRate +
                ", preset='" + preset + '\'' +
                ", crf=" + crf +
                ", aspectRatio=" + aspectRatio +
                ", outputFormat=" + outputFormat +
                '}';
    }
}