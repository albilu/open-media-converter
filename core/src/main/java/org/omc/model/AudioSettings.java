// filepath: src/main/java/org/omc/model/AudioSettings.java

package org.omc.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Audio-specific conversion settings.
 * <p>
 * This class encapsulates all settings related to audio format conversion,
 * including codec, bitrate, sample rate, channels, quality, and output format.
 * Supports "copy" codec for stream copying without re-encoding.
 * </p>
 * 
 * <p>
 * Requirements:
 * </p>
 * <ul>
 * <li>REQ-006.2: Audio format conversion with quality controls</li>
 * <li>REQ-2.3: Audio output format selection</li>
 * <li>REQ-4.5: Audio format conversion support</li>
 * <li>REQ-AUD-1.1: Support audio stream copy without re-encoding</li>
 * </ul>
 * 
 * @since 1.0
 */
public final class AudioSettings {

    private final String codec;
    private final int bitrate; // in kbps
    private final int sampleRate; // in Hz, -1 for original
    private final int channels; // -1 for original, 1=mono, 2=stereo, 6=5.1
    private final int quality; // codec-specific quality (0-9, lower is better for MP3/Vorbis)
    private final FileFormat outputFormat; // Requirement REQ-2.3: Output format for audio conversion

    @JsonCreator
    private AudioSettings(
            @JsonProperty("codec") String codec,
            @JsonProperty("bitrate") int bitrate,
            @JsonProperty("sampleRate") int sampleRate,
            @JsonProperty("channels") int channels,
            @JsonProperty("quality") int quality,
            @JsonProperty("outputFormat") FileFormat outputFormat) {
        this.codec = codec;
        this.bitrate = bitrate;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.quality = quality;
        this.outputFormat = outputFormat;
    }

    /**
     * Returns the audio codec.
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
     * Returns the sample rate in Hz, or -1 for original.
     *
     * @return the sample rate
     */
    @JsonProperty("sampleRate")
    public int sampleRate() {
        return sampleRate;
    }

    /**
     * Returns the number of channels, or -1 for original.
     *
     * @return the channels
     */
    @JsonProperty("channels")
    public int channels() {
        return channels;
    }

    /**
     * Returns the quality setting (0-9, lower is better).
     *
     * @return the quality
     */
    @JsonProperty("quality")
    public int quality() {
        return quality;
    }

    /**
     * Returns the output file format.
     *
     * @return the output format
     */
    @JsonProperty("outputFormat")
    public FileFormat outputFormat() {
        return outputFormat;
    }

    /**
     * Validates audio settings.
     * Requirement REQ-2.3: Validate output format is AUDIO category.
     * Requirement REQ-AUD-1.1: Accept "copy" codec for stream copying.
     * 
     * @return true if settings are valid
     */
    @JsonIgnore
    public boolean isValid() {
        // Codec validation (Requirement REQ-AUD-1.1)
        if (codec == null || codec.trim().isEmpty()) {
            return false;
        }
        if (!isValidCodec(codec)) {
            return false;
        }

        // Output format validation (Requirement REQ-2.3)
        if (outputFormat == null || outputFormat.getCategory() != FormatCategory.AUDIO) {
            return false;
        }

        // Bitrate validation (64-320 kbps)
        if (bitrate < 64 || bitrate > 320) {
            return false;
        }

        // Sample rate validation (-1 for original, or valid rates)
        if (sampleRate != -1 && !isValidSampleRate(sampleRate)) {
            return false;
        }

        // Channels validation (-1 for original, or 1, 2, 6)
        if (channels != -1 && channels != 1 && channels != 2 && channels != 6) {
            return false;
        }

        // Quality validation (0-9)
        if (quality < 0 || quality > 9) {
            return false;
        }

        return true;
    }

    /**
     * Validates codec name.
     * Requirement REQ-AUD-1.1: Accept "copy" codec for audio stream copying.
     * Accepts any non-null, non-blank codec string for flexibility.
     * 
     * @param codec the codec name
     * @return true if codec is valid (non-null and non-blank)
     */
    private static boolean isValidCodec(String codec) {
        return codec != null && !codec.isBlank();
    }

    private static boolean isValidSampleRate(int rate) {
        return rate == 8000 ||
                rate == 11025 ||
                rate == 16000 ||
                rate == 22050 ||
                rate == 32000 ||
                rate == 44100 ||
                rate == 48000 ||
                rate == 88200 ||
                rate == 96000 ||
                rate == 176400 ||
                rate == 192000;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String codec = "aac"; // AAC default
        private int bitrate = 192; // 192 kbps default
        private int sampleRate = -1; // -1 means original
        private int channels = -1; // -1 means original
        private int quality = 5; // Middle quality
        private FileFormat outputFormat = FileFormat.MP3; // Requirement REQ-2.3: Default to MP3

        /**
         * Sets the audio codec.
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
         * Sets the sample rate in Hz, or -1 for original.
         *
         * @param sampleRate the sample rate
         * @return this builder
         */
        public Builder sampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        /**
         * Sets the number of channels, or -1 for original.
         *
         * @param channels the channels
         * @return this builder
         */
        public Builder channels(int channels) {
            this.channels = channels;
            return this;
        }

        /**
         * Sets the quality setting (0-9).
         *
         * @param quality the quality
         * @return this builder
         */
        public Builder quality(int quality) {
            this.quality = quality;
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
         * Builds the AudioSettings instance.
         *
         * @return a new AudioSettings
         * @throws IllegalArgumentException if the settings are invalid
         */
        public AudioSettings build() {
            validate();
            return new AudioSettings(codec, bitrate, sampleRate, channels, quality, outputFormat);
        }

        private void validate() {
            if (codec == null || codec.trim().isEmpty()) {
                throw new IllegalArgumentException("Codec must not be null or empty");
            }
            if (!AudioSettings.isValidCodec(codec)) {
                throw new IllegalArgumentException("Invalid codec: " + codec);
            }
            if (outputFormat == null || outputFormat.getCategory() != FormatCategory.AUDIO) {
                throw new IllegalArgumentException("Output format must be AUDIO category");
            }
            if (bitrate < 64 || bitrate > 320) {
                throw new IllegalArgumentException("Bitrate must be between 64 and 320 kbps");
            }
            if (sampleRate != -1 && !AudioSettings.isValidSampleRate(sampleRate)) {
                throw new IllegalArgumentException("Invalid sample rate: " + sampleRate);
            }
            if (channels != -1 && channels != 1 && channels != 2 && channels != 6) {
                throw new IllegalArgumentException("Channels must be -1, 1, 2, or 6");
            }
            if (quality < 0 || quality > 9) {
                throw new IllegalArgumentException("Quality must be between 0 and 9");
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AudioSettings that = (AudioSettings) o;
        return bitrate == that.bitrate &&
                sampleRate == that.sampleRate &&
                channels == that.channels &&
                quality == that.quality &&
                Objects.equals(codec, that.codec) &&
                Objects.equals(outputFormat, that.outputFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codec, bitrate, sampleRate, channels, quality, outputFormat);
    }

    @Override
    public String toString() {
        return "AudioSettings{" +
                "codec='" + codec + '\'' +
                ", bitrate=" + bitrate +
                ", sampleRate=" + sampleRate +
                ", channels=" + channels +
                ", quality=" + quality +
                ", outputFormat=" + outputFormat +
                '}';
    }
}
