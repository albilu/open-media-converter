package org.omc.model;

/** Selects encoders supported by the requested media container. */
public final class MediaCodecPolicy {
    private MediaCodecPolicy() { }

    /**
     * Keeps a compatible video encoder, otherwise selects the container default.
     * @param format target container
     * @param codec requested encoder
     * @return an encoder suitable for the container
     */
    public static String videoCodec(FileFormat format, String codec) {
        if (format == null || codec == null) return codec;
        return switch (format) {
            case WEBM -> "libvpx-vp9";
            case WMV -> "wmv2";
            case FLV -> "flv";
            case AVI -> codec.equals("mpeg4") || codec.equals("libx264") ? codec : "mpeg4";
            case MP4 -> codec.equals("wmv2") || codec.equals("flv") ? "libx264" : codec;
            case MOV -> codec.equals("libvpx-vp9") || codec.equals("wmv2") || codec.equals("flv") ? "libx264" : codec;
            default -> codec;
        };
    }

    /**
     * Keeps a compatible audio encoder, otherwise selects the container default.
     * Stream copy is retained because compatibility depends on the source stream.
     * @param format target container
     * @param codec requested encoder
     * @return an encoder suitable for the container
     */
    public static String audioCodec(FileFormat format, String codec) {
        if (format == null || codec == null || codec.equals("copy")) return codec;
        // "opus" is the user-facing alias of the libopus encoder (accepted by
        // AudioSettings and persisted in settings); it must pass the gate so
        // the per-container switch can map it like any other known codec.
        if (!java.util.Set.of("aac", "libmp3lame", "pcm_s16le", "flac", "libopus", "libvorbis", "alac", "opus").contains(codec)) {
            return codec;
        }
        return switch (format) {
            case MP3 -> "libmp3lame";
            case AAC -> "aac";
            case M4A -> codec.equals("alac") ? codec : "aac";
            case WAV -> "pcm_s16le";
            case FLAC -> "flac";
            case OGG -> codec.equals("libopus") || codec.equals("opus") ? "libopus" : "libvorbis";
            default -> codec;
        };
    }
}
