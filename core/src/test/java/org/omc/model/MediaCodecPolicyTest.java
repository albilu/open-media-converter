package org.omc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MediaCodecPolicy} container-aware codec selection. The
 * known-codec gate set and the per-container switch must stay consistent:
 * every codec the switch special-cases must pass the gate, otherwise the
 * special case is unreachable dead code.
 */
class MediaCodecPolicyTest {

    // ===== audioCodec: gate/switch consistency =====

    @Test
    void audioCodec_oggWithOpusAlias_mapsToLibopus() {
        // "opus" is a user-facing alias (AudioSettings accepts it, persisted
        // settings store it); the OGG branch must map it to "libopus".
        assertEquals("libopus", MediaCodecPolicy.audioCodec(FileFormat.OGG, "opus"));
    }

    @Test
    void audioCodec_oggWithLibopus_keepsLibopus() {
        assertEquals("libopus", MediaCodecPolicy.audioCodec(FileFormat.OGG, "libopus"));
    }

    @Test
    void audioCodec_oggWithKnownIncompatibleCodec_fallsBackToLibvorbis() {
        assertEquals("libvorbis", MediaCodecPolicy.audioCodec(FileFormat.OGG, "aac"));
    }

    @Test
    void audioCodec_opusAlias_followsContainerDefaults() {
        // Gate membership means container-default rewriting applies to the
        // alias in every container, not just OGG.
        assertEquals("libmp3lame", MediaCodecPolicy.audioCodec(FileFormat.MP3, "opus"));
        assertEquals("aac", MediaCodecPolicy.audioCodec(FileFormat.AAC, "opus"));
        assertEquals("pcm_s16le", MediaCodecPolicy.audioCodec(FileFormat.WAV, "opus"));
    }

    // ===== audioCodec: pass-through contract =====

    @Test
    void audioCodec_unknownCodec_passesThroughUnchanged() {
        assertEquals("ac3", MediaCodecPolicy.audioCodec(FileFormat.OGG, "ac3"));
    }

    @Test
    void audioCodec_nullFormatOrCodecOrCopy_returnsCodecUnchanged() {
        assertEquals("copy", MediaCodecPolicy.audioCodec(FileFormat.OGG, "copy"));
        assertEquals("aac", MediaCodecPolicy.audioCodec(null, "aac"));
        assertNull(MediaCodecPolicy.audioCodec(FileFormat.OGG, null));
    }

    // ===== videoCodec: container rewrites =====

    @Test
    void videoCodec_rewritesKnownIncompatibleCodecs() {
        assertEquals("libvpx-vp9", MediaCodecPolicy.videoCodec(FileFormat.WEBM, "libx264"));
        assertEquals("libx264", MediaCodecPolicy.videoCodec(FileFormat.MP4, "wmv2"));
        assertEquals("mpeg4", MediaCodecPolicy.videoCodec(FileFormat.AVI, "flv"));
        assertEquals("libx264", MediaCodecPolicy.videoCodec(FileFormat.MOV, "libvpx-vp9"));
    }

    @Test
    void videoCodec_keepsCompatibleCodec() {
        assertEquals("libx264", MediaCodecPolicy.videoCodec(FileFormat.MP4, "libx264"));
        assertEquals("mpeg4", MediaCodecPolicy.videoCodec(FileFormat.AVI, "mpeg4"));
    }

    // ===== idempotency: re-applying the policy must be a no-op =====
    // AudioSettings applies audioCodec in outputFormat() and again in build();
    // VideoSettings applies videoCodec in outputFormat() and withOutputFormat().
    // A second application must never flip an already-compatible codec.

    @Test
    void audioCodec_applyingTwice_equalsApplyingOnce() {
        String[] representativeCodecs = {
                "aac", "libmp3lame", "pcm_s16le", "flac", "libopus",
                "libvorbis", "alac", "opus", "copy", "ac3", null };
        for (FileFormat format : FileFormat.values()) {
            for (String codec : representativeCodecs) {
                String appliedOnce = MediaCodecPolicy.audioCodec(format, codec);
                assertEquals(appliedOnce, MediaCodecPolicy.audioCodec(format, appliedOnce),
                        () -> "audioCodec not idempotent for " + format + " x " + codec);
            }
        }
    }

    @Test
    void videoCodec_applyingTwice_equalsApplyingOnce() {
        String[] representativeCodecs = {
                "libx264", "libvpx-vp9", "wmv2", "flv", "mpeg4",
                "h264_nvenc", "hevc_nvenc", "libx265", null };
        for (FileFormat format : FileFormat.values()) {
            for (String codec : representativeCodecs) {
                String appliedOnce = MediaCodecPolicy.videoCodec(format, codec);
                assertEquals(appliedOnce, MediaCodecPolicy.videoCodec(format, appliedOnce),
                        () -> "videoCodec not idempotent for " + format + " x " + codec);
            }
        }
    }
}
