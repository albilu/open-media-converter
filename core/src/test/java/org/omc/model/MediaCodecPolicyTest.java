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
}
