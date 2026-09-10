package org.omc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Tests for the canonical output-format resolution helper on
 * {@link ConversionSettings}. The helper replaces triplicated resolution
 * switches in the UI (file list, file details dialog) with one core method.
 *
 * <p>
 * Requirements: REQ-FL-1.1 (output format resolution), REQ-2.1 (per-section
 * output formats), REQ-3.1 (per-file settings override).
 * </p>
 */
class ConversionSettingsResolveOutputFormatTest {

    private ConversionSettings fullSettings() {
        return ConversionSettings.builder()
                .videoSettings(VideoSettings.builder().outputFormat(FileFormat.WEBM).build())
                .audioSettings(AudioSettings.builder().outputFormat(FileFormat.FLAC).build())
                .imageSettings(ImageSettings.builder().outputFormat(FileFormat.WEBP).build())
                .documentSettings(DocumentSettings.builder().outputFormat(FileFormat.HTML).build())
                .build();
    }

    @Test
    void explicitSectionFormat_resolvedPerCategory() {
        ConversionSettings settings = fullSettings();

        assertEquals(FileFormat.WEBM, settings.resolveOutputFormat(FormatCategory.VIDEO));
        assertEquals(FileFormat.FLAC, settings.resolveOutputFormat(FormatCategory.AUDIO));
        assertEquals(FileFormat.WEBP, settings.resolveOutputFormat(FormatCategory.IMAGE));
        assertEquals(FileFormat.HTML, settings.resolveOutputFormat(FormatCategory.DOCUMENT));
    }

    @Test
    void missingSection_fallsBackToNull() {
        ConversionSettings empty = ConversionSettings.builder().build();

        assertNull(empty.resolveOutputFormat(FormatCategory.VIDEO));
        assertNull(empty.resolveOutputFormat(FormatCategory.AUDIO));
        assertNull(empty.resolveOutputFormat(FormatCategory.IMAGE));
        assertNull(empty.resolveOutputFormat(FormatCategory.DOCUMENT));
    }

    @Test
    void sectionBuiltWithBuilder_fallsBackToCategoryDefaultFormat() {
        // Section builders default their category format (Video -> MP4,
        // Audio -> MP3, Image -> PNG, Document -> PDF); resolution surfaces
        // those defaults exactly like the UI sites did before.
        ConversionSettings settings = ConversionSettings.builder()
                .videoSettings(VideoSettings.builder().codec("libx264").build())
                .audioSettings(AudioSettings.builder().build())
                .imageSettings(ImageSettings.builder().build())
                .documentSettings(DocumentSettings.builder().build())
                .build();

        assertEquals(FileFormat.MP4, settings.resolveOutputFormat(FormatCategory.VIDEO));
        assertEquals(FileFormat.MP3, settings.resolveOutputFormat(FormatCategory.AUDIO));
        assertEquals(FileFormat.PNG, settings.resolveOutputFormat(FormatCategory.IMAGE));
        assertEquals(FileFormat.PDF, settings.resolveOutputFormat(FormatCategory.DOCUMENT));
    }

    @Test
    void unknownCategory_resolvesToNull() {
        assertNull(fullSettings().resolveOutputFormat(FormatCategory.UNKNOWN));
    }

    @Test
    void perFileOverride_winsOverGlobalSectionFormat() {
        ConversionSettings settings = fullSettings();
        ConversionFile file = ConversionFile
                .create(Path.of("/test/video.mp4"), FileFormat.MP4, 1024L)
                .withSettingsOverride(FileSettingsOverride.forVideo("High Quality",
                        VideoSettings.builder().outputFormat(FileFormat.AVI).build()));

        assertEquals(FileFormat.AVI, settings.resolveOutputFormat(file),
                "The per-file override format must win over the global video format");
    }

    @Test
    void overrideBuiltWithBuilder_usesSectionDefault_notGlobalFormat() {
        ConversionSettings settings = fullSettings(); // global video format: WEBM
        // Override section built without an explicit format defaults to MP4;
        // the override shadows global settings, so WEBM must NOT be used.
        ConversionFile file = ConversionFile
                .create(Path.of("/test/video.mp4"), FileFormat.MP4, 1024L)
                .withSettingsOverride(FileSettingsOverride.forVideo("Preset only",
                        VideoSettings.builder().build()));

        assertEquals(FileFormat.MP4, settings.resolveOutputFormat(file),
                "An override resolves its own section default instead of the global format");
    }

    @Test
    void fileWithoutOverride_usesGlobalSectionFormat() {
        ConversionSettings settings = fullSettings();
        ConversionFile file = ConversionFile.create(Path.of("/test/video.mp4"), FileFormat.MP4, 1024L);

        assertEquals(FileFormat.WEBM, settings.resolveOutputFormat(file));
    }

    @Test
    void overrideForOtherCategory_sectionNotSet_resolvesToNull() {
        ConversionSettings settings = fullSettings();
        // Malformed override: audio section applied to a video file.
        ConversionFile file = ConversionFile
                .create(Path.of("/test/video.mp4"), FileFormat.MP4, 1024L)
                .withSettingsOverride(FileSettingsOverride.forAudio("Audio preset",
                        AudioSettings.builder().outputFormat(FileFormat.FLAC).build()));

        assertNull(settings.resolveOutputFormat(file),
                "Resolution is by the file's category; a mismatched override section resolves to null");
    }

    @Test
    void rejectsNullArguments() {
        ConversionSettings settings = fullSettings();

        assertThrows(NullPointerException.class, () -> settings.resolveOutputFormat((ConversionFile) null));
        assertThrows(NullPointerException.class, () -> settings.resolveOutputFormat((FormatCategory) null));
    }
}
