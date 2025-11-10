// filepath: src/main/java/org/omc/model/AudioMetadata.java

package org.omc.model;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata for audio files.
 * Requirement REQ-002.2: Audio file metadata including duration, codec,
 * bitrate, sample rate, and channels.
 */
public final class AudioMetadata implements MediaMetadata {

    private final Duration duration; // Audio duration
    private final String codec; // Audio codec (e.g., "aac", "mp3", "opus", "flac")
    private final long bitrate; // Bitrate in bps
    private final int sampleRate; // Sample rate in Hz (e.g., 44100, 48000)
    private final int channels; // Number of audio channels (1 = mono, 2 = stereo, 6 = 5.1)

    @JsonCreator
    public AudioMetadata(
            @JsonProperty("duration") Duration duration,
            @JsonProperty("codec") String codec,
            @JsonProperty("bitrate") long bitrate,
            @JsonProperty("sampleRate") int sampleRate,
            @JsonProperty("channels") int channels) {
        this.duration = duration;
        this.codec = codec;
        this.bitrate = bitrate;
        this.sampleRate = sampleRate;
        this.channels = channels;
    }

    /**
     * Creates an AudioMetadata builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    @JsonIgnore
    public FormatCategory getCategory() {
        return FormatCategory.AUDIO;
    }

    @Override
    @JsonIgnore
    public boolean isValid() {
        return duration != null && !duration.isNegative() && !duration.isZero()
                && codec != null && !codec.isBlank()
                && bitrate >= 0
                && sampleRate > 0
                && channels > 0;
    }

    @Override
    @JsonIgnore
    public String getSummary() {
        return String.format(Locale.US, "%s, %d kbps, %.1f kHz, %s, %s",
                codec,
                bitrate / 1000,
                sampleRate / 1000.0,
                getChannelDescription(),
                formatDuration(duration));
    }

    private String formatDuration(Duration duration) {
        if (duration == null)
            return "unknown";
        long seconds = duration.getSeconds();
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    /**
     * Gets a human-readable description of the channel configuration.
     */
    @JsonIgnore
    public String getChannelDescription() {
        return switch (channels) {
            case 1 -> "mono";
            case 2 -> "stereo";
            case 6 -> "5.1";
            case 8 -> "7.1";
            default -> channels + " channels";
        };
    }

    public Duration getDuration() {
        return duration;
    }

    public String getCodec() {
        return codec;
    }

    public long getBitrate() {
        return bitrate;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    /**
     * Checks if this is high-quality audio (bitrate >= 256 kbps or lossless codec).
     */
    @JsonIgnore
    public boolean isHighQuality() {
        return bitrate >= 256_000 || isLossless();
    }

    /**
     * Checks if this uses a lossless codec.
     */
    @JsonIgnore
    public boolean isLossless() {
        return codec != null && (codec.equalsIgnoreCase("flac") ||
                codec.equalsIgnoreCase("alac") ||
                codec.equalsIgnoreCase("wav") ||
                codec.equalsIgnoreCase("pcm"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AudioMetadata that = (AudioMetadata) o;
        return bitrate == that.bitrate &&
                sampleRate == that.sampleRate &&
                channels == that.channels &&
                Objects.equals(duration, that.duration) &&
                Objects.equals(codec, that.codec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(duration, codec, bitrate, sampleRate, channels);
    }

    @Override
    public String toString() {
        return "AudioMetadata{" +
                "duration=" + duration +
                ", codec='" + codec + '\'' +
                ", bitrate=" + bitrate +
                ", sampleRate=" + sampleRate +
                ", channels=" + channels +
                '}';
    }

    /**
     * Builder for AudioMetadata.
     */
    public static class Builder {
        private Duration duration;
        private String codec;
        private long bitrate;
        private int sampleRate;
        private int channels;

        private Builder() {
        }

        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder codec(String codec) {
            this.codec = codec;
            return this;
        }

        public Builder bitrate(long bitrate) {
            this.bitrate = bitrate;
            return this;
        }

        public Builder sampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        public Builder channels(int channels) {
            this.channels = channels;
            return this;
        }

        public AudioMetadata build() {
            return new AudioMetadata(duration, codec, bitrate, sampleRate, channels);
        }
    }
}
