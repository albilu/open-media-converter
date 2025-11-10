// filepath: src/main/java/org/omc/model/VideoMetadata.java

package org.omc.model;

import java.time.Duration;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata for video files.
 * Requirement REQ-002.2: Video file metadata including duration, resolution,
 * codec, frame rate, and bitrate.
 */
public final class VideoMetadata implements MediaMetadata {

    private final Duration duration; // Video duration
    private final int width; // Video width in pixels
    private final int height; // Video height in pixels
    private final String videoCodec; // Video codec (e.g., "h264", "hevc", "vp9")
    private final String audioCodec; // Audio codec (e.g., "aac", "mp3", "opus")
    private final double frameRate; // Frame rate (fps)
    private final long videoBitrate; // Video bitrate in bps
    private final long audioBitrate; // Audio bitrate in bps

    @JsonCreator
    public VideoMetadata(
            @JsonProperty("duration") Duration duration,
            @JsonProperty("width") int width,
            @JsonProperty("height") int height,
            @JsonProperty("videoCodec") String videoCodec,
            @JsonProperty("audioCodec") String audioCodec,
            @JsonProperty("frameRate") double frameRate,
            @JsonProperty("videoBitrate") long videoBitrate,
            @JsonProperty("audioBitrate") long audioBitrate) {
        this.duration = duration;
        this.width = width;
        this.height = height;
        this.videoCodec = videoCodec;
        this.audioCodec = audioCodec;
        this.frameRate = frameRate;
        this.videoBitrate = videoBitrate;
        this.audioBitrate = audioBitrate;
    }

    /**
     * Creates a VideoMetadata builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    @JsonIgnore
    public FormatCategory getCategory() {
        return FormatCategory.VIDEO;
    }

    @Override
    @JsonIgnore
    public boolean isValid() {
        return duration != null && !duration.isNegative() && !duration.isZero()
                && width > 0 && height > 0
                && videoCodec != null && !videoCodec.isBlank()
                && (audioCodec == null || !audioCodec.isBlank())
                && frameRate > 0
                && videoBitrate >= 0
                && audioBitrate >= 0;
    }

    @Override
    @JsonIgnore
    public String getSummary() {
        return String.format("%dx%d, %s, %.2f fps, %s",
                width, height, videoCodec, frameRate,
                formatDuration(duration));
    }

    private String formatDuration(Duration duration) {
        if (duration == null)
            return "unknown";
        long seconds = duration.getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%d:%02d", minutes, secs);
        }
    }

    public Duration getDuration() {
        return duration;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getVideoCodec() {
        return videoCodec;
    }

    public String getAudioCodec() {
        return audioCodec;
    }

    public double getFrameRate() {
        return frameRate;
    }

    public long getVideoBitrate() {
        return videoBitrate;
    }

    public long getAudioBitrate() {
        return audioBitrate;
    }

    /**
     * Gets the video resolution as "widthxheight" (e.g., "1920x1080").
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
     * Checks if this is HD quality (height >= 720).
     */
    @JsonIgnore
    public boolean isHD() {
        return height >= 720;
    }

    /**
     * Checks if this is Full HD quality (height >= 1080).
     */
    @JsonIgnore
    public boolean isFullHD() {
        return height >= 1080;
    }

    /**
     * Checks if this is 4K quality (height >= 2160).
     */
    @JsonIgnore
    public boolean is4K() {
        return height >= 2160;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        VideoMetadata that = (VideoMetadata) o;
        return width == that.width &&
                height == that.height &&
                Double.compare(that.frameRate, frameRate) == 0 &&
                videoBitrate == that.videoBitrate &&
                audioBitrate == that.audioBitrate &&
                Objects.equals(duration, that.duration) &&
                Objects.equals(videoCodec, that.videoCodec) &&
                Objects.equals(audioCodec, that.audioCodec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(duration, width, height, videoCodec, audioCodec, frameRate, videoBitrate, audioBitrate);
    }

    @Override
    public String toString() {
        return "VideoMetadata{" +
                "duration=" + duration +
                ", resolution=" + width + "x" + height +
                ", videoCodec='" + videoCodec + '\'' +
                ", audioCodec='" + audioCodec + '\'' +
                ", frameRate=" + frameRate +
                ", videoBitrate=" + videoBitrate +
                ", audioBitrate=" + audioBitrate +
                '}';
    }

    /**
     * Builder for VideoMetadata.
     */
    public static class Builder {
        private Duration duration;
        private int width;
        private int height;
        private String videoCodec;
        private String audioCodec;
        private double frameRate;
        private long videoBitrate;
        private long audioBitrate;

        private Builder() {
        }

        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Builder videoCodec(String videoCodec) {
            this.videoCodec = videoCodec;
            return this;
        }

        public Builder audioCodec(String audioCodec) {
            this.audioCodec = audioCodec;
            return this;
        }

        public Builder frameRate(double frameRate) {
            this.frameRate = frameRate;
            return this;
        }

        public Builder videoBitrate(long videoBitrate) {
            this.videoBitrate = videoBitrate;
            return this;
        }

        public Builder audioBitrate(long audioBitrate) {
            this.audioBitrate = audioBitrate;
            return this;
        }

        public VideoMetadata build() {
            return new VideoMetadata(duration, width, height, videoCodec, audioCodec,
                    frameRate, videoBitrate, audioBitrate);
        }
    }
}
