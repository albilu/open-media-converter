package org.omc.model;

import org.omc.model.ImageSettings;
import org.omc.model.FormatCategory;
import org.omc.model.PresetsBySection;
import org.omc.model.DocumentSettings;
import org.omc.model.VideoSettings;
import org.omc.model.AudioSettings;
import org.omc.model.SectionPreset;
import org.omc.model.FileFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive unit tests for PresetsBySection class.
 * Tests cover factory methods, constructors, getters, immutable updates,
 * equality, JSON serialization, and defensive copying.
 */
class PresetsBySectionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Helper methods to create test presets
    private SectionPreset createVideoPreset(String name) {
        VideoSettings settings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .codec("libx264")
                .build();
        return SectionPreset.forVideo(name, "Video preset", settings, false);
    }

    private SectionPreset createAudioPreset(String name) {
        AudioSettings settings = AudioSettings.builder()
                .outputFormat(FileFormat.MP3)
                .codec("libmp3lame")
                .build();
        return SectionPreset.forAudio(name, "Audio preset", settings, false);
    }

    private SectionPreset createImagePreset(String name) {
        ImageSettings settings = ImageSettings.builder()
                .outputFormat(FileFormat.PNG)
                .quality(90)
                .build();
        return SectionPreset.forImage(name, "Image preset", settings, false);
    }

    private SectionPreset createDocumentPreset(String name) {
        DocumentSettings settings = DocumentSettings.builder()
                .outputFormat(FileFormat.PDF)
                .build();
        return SectionPreset.forDocument(name, "Document preset", settings, false);
    }

    // Test empty() static factory method
    @Test
    void testEmpty_ReturnsInstanceWithEmptyLists() {
        PresetsBySection presets = PresetsBySection.empty();

        assertNotNull(presets);
        assertTrue(presets.videoPresets().isEmpty());
        assertTrue(presets.audioPresets().isEmpty());
        assertTrue(presets.imagePresets().isEmpty());
        assertTrue(presets.documentPresets().isEmpty());
        assertEquals(0, presets.totalPresetCount());
    }

    // Test constructor with all combinations
    @Test
    void testConstructor_AllNullLists_CreatesEmptyLists() {
        PresetsBySection presets = new PresetsBySection(null, null, null, null);

        assertTrue(presets.videoPresets().isEmpty());
        assertTrue(presets.audioPresets().isEmpty());
        assertTrue(presets.imagePresets().isEmpty());
        assertTrue(presets.documentPresets().isEmpty());
    }

    @Test
    void testConstructor_AllNonNullLists_CreatesDefensiveCopies() {
        List<SectionPreset> videoList = List.of(createVideoPreset("V1"), createVideoPreset("V2"));
        List<SectionPreset> audioList = List.of(createAudioPreset("A1"));
        List<SectionPreset> imageList = List.of(createImagePreset("I1"), createImagePreset("I2"),
                createImagePreset("I3"));
        List<SectionPreset> documentList = List.of(createDocumentPreset("D1"));

        PresetsBySection presets = new PresetsBySection(videoList, audioList, imageList, documentList);

        assertEquals(2, presets.videoPresets().size());
        assertEquals(1, presets.audioPresets().size());
        assertEquals(3, presets.imagePresets().size());
        assertEquals(1, presets.documentPresets().size());
    }

    @Test
    void testConstructor_MixedNullAndNonNullLists_HandlesCorrectly() {
        List<SectionPreset> videoList = List.of(createVideoPreset("V1"));
        List<SectionPreset> audioList = null;
        List<SectionPreset> imageList = List.of(createImagePreset("I1"));
        List<SectionPreset> documentList = null;

        PresetsBySection presets = new PresetsBySection(videoList, audioList, imageList, documentList);

        assertEquals(1, presets.videoPresets().size());
        assertTrue(presets.audioPresets().isEmpty());
        assertEquals(1, presets.imagePresets().size());
        assertTrue(presets.documentPresets().isEmpty());
    }

    // Test all individual getter methods return defensive copies
    @Test
    void testVideoPresets_ReturnsDefensiveCopy() {
        List<SectionPreset> originalList = new ArrayList<>(List.of(createVideoPreset("V1")));
        PresetsBySection presets = new PresetsBySection(originalList, null, null, null);

        List<SectionPreset> returnedList = presets.videoPresets();
        returnedList.add(createVideoPreset("V2"));

        assertEquals(1, presets.videoPresets().size());
        assertEquals(2, returnedList.size());
    }

    @Test
    void testAudioPresets_ReturnsDefensiveCopy() {
        List<SectionPreset> originalList = new ArrayList<>(List.of(createAudioPreset("A1")));
        PresetsBySection presets = new PresetsBySection(null, originalList, null, null);

        List<SectionPreset> returnedList = presets.audioPresets();
        returnedList.add(createAudioPreset("A2"));

        assertEquals(1, presets.audioPresets().size());
        assertEquals(2, returnedList.size());
    }

    @Test
    void testImagePresets_ReturnsDefensiveCopy() {
        List<SectionPreset> originalList = new ArrayList<>(List.of(createImagePreset("I1")));
        PresetsBySection presets = new PresetsBySection(null, null, originalList, null);

        List<SectionPreset> returnedList = presets.imagePresets();
        returnedList.add(createImagePreset("I2"));

        assertEquals(1, presets.imagePresets().size());
        assertEquals(2, returnedList.size());
    }

    @Test
    void testDocumentPresets_ReturnsDefensiveCopy() {
        List<SectionPreset> originalList = new ArrayList<>(List.of(createDocumentPreset("D1")));
        PresetsBySection presets = new PresetsBySection(null, null, null, originalList);

        List<SectionPreset> returnedList = presets.documentPresets();
        returnedList.add(createDocumentPreset("D2"));

        assertEquals(1, presets.documentPresets().size());
        assertEquals(2, returnedList.size());
    }

    // Test getPresetsForCategory() for all FormatCategory values
    @Test
    void testGetPresetsForCategory_Video_ReturnsVideoPresets() {
        List<SectionPreset> videoList = List.of(createVideoPreset("V1"));
        PresetsBySection presets = new PresetsBySection(videoList, null, null, null);

        List<SectionPreset> result = presets.getPresetsForCategory(FormatCategory.VIDEO);

        assertEquals(1, result.size());
        assertEquals("V1", result.get(0).name());
    }

    @Test
    void testGetPresetsForCategory_Audio_ReturnsAudioPresets() {
        List<SectionPreset> audioList = List.of(createAudioPreset("A1"));
        PresetsBySection presets = new PresetsBySection(null, audioList, null, null);

        List<SectionPreset> result = presets.getPresetsForCategory(FormatCategory.AUDIO);

        assertEquals(1, result.size());
        assertEquals("A1", result.get(0).name());
    }

    @Test
    void testGetPresetsForCategory_Image_ReturnsImagePresets() {
        List<SectionPreset> imageList = List.of(createImagePreset("I1"));
        PresetsBySection presets = new PresetsBySection(null, null, imageList, null);

        List<SectionPreset> result = presets.getPresetsForCategory(FormatCategory.IMAGE);

        assertEquals(1, result.size());
        assertEquals("I1", result.get(0).name());
    }

    @Test
    void testGetPresetsForCategory_Document_ReturnsDocumentPresets() {
        List<SectionPreset> documentList = List.of(createDocumentPreset("D1"));
        PresetsBySection presets = new PresetsBySection(null, null, null, documentList);

        List<SectionPreset> result = presets.getPresetsForCategory(FormatCategory.DOCUMENT);

        assertEquals(1, result.size());
        assertEquals("D1", result.get(0).name());
    }

    @Test
    void testGetPresetsForCategory_Unknown_ReturnsEmptyList() {
        List<SectionPreset> videoList = List.of(createVideoPreset("V1"));
        PresetsBySection presets = new PresetsBySection(videoList, null, null, null);

        List<SectionPreset> result = presets.getPresetsForCategory(FormatCategory.UNKNOWN);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetPresetsForCategory_NullCategory_ThrowsNullPointerException() {
        PresetsBySection presets = PresetsBySection.empty();

        assertThrows(NullPointerException.class, () -> presets.getPresetsForCategory(null));
    }

    // Test immutable update methods
    @Test
    void testWithVideoPresets_UpdatesVideoPresets() {
        PresetsBySection original = PresetsBySection.empty();
        List<SectionPreset> newVideoPresets = List.of(createVideoPreset("V1"));

        PresetsBySection updated = original.withVideoPresets(newVideoPresets);

        assertTrue(original.videoPresets().isEmpty());
        assertEquals(1, updated.videoPresets().size());
        assertTrue(updated.audioPresets().isEmpty());
        assertTrue(updated.imagePresets().isEmpty());
        assertTrue(updated.documentPresets().isEmpty());
    }

    @Test
    void testWithAudioPresets_UpdatesAudioPresets() {
        PresetsBySection original = PresetsBySection.empty();
        List<SectionPreset> newAudioPresets = List.of(createAudioPreset("A1"));

        PresetsBySection updated = original.withAudioPresets(newAudioPresets);

        assertTrue(original.audioPresets().isEmpty());
        assertEquals(1, updated.audioPresets().size());
        assertTrue(updated.videoPresets().isEmpty());
        assertTrue(updated.imagePresets().isEmpty());
        assertTrue(updated.documentPresets().isEmpty());
    }

    @Test
    void testWithImagePresets_UpdatesImagePresets() {
        PresetsBySection original = PresetsBySection.empty();
        List<SectionPreset> newImagePresets = List.of(createImagePreset("I1"));

        PresetsBySection updated = original.withImagePresets(newImagePresets);

        assertTrue(original.imagePresets().isEmpty());
        assertEquals(1, updated.imagePresets().size());
        assertTrue(updated.videoPresets().isEmpty());
        assertTrue(updated.audioPresets().isEmpty());
        assertTrue(updated.documentPresets().isEmpty());
    }

    @Test
    void testWithDocumentPresets_UpdatesDocumentPresets() {
        PresetsBySection original = PresetsBySection.empty();
        List<SectionPreset> newDocumentPresets = List.of(createDocumentPreset("D1"));

        PresetsBySection updated = original.withDocumentPresets(newDocumentPresets);

        assertTrue(original.documentPresets().isEmpty());
        assertEquals(1, updated.documentPresets().size());
        assertTrue(updated.videoPresets().isEmpty());
        assertTrue(updated.audioPresets().isEmpty());
        assertTrue(updated.imagePresets().isEmpty());
    }

    // Test totalPresetCount() with various scenarios
    @Test
    void testTotalPresetCount_AllEmpty_ReturnsZero() {
        PresetsBySection presets = PresetsBySection.empty();

        assertEquals(0, presets.totalPresetCount());
    }

    @Test
    void testTotalPresetCount_MixedCounts_ReturnsSum() {
        List<SectionPreset> videoList = List.of(createVideoPreset("V1"), createVideoPreset("V2"));
        List<SectionPreset> audioList = List.of(createAudioPreset("A1"));
        List<SectionPreset> imageList = List.of(createImagePreset("I1"), createImagePreset("I2"),
                createImagePreset("I3"));
        List<SectionPreset> documentList = List.of(createDocumentPreset("D1"), createDocumentPreset("D2"));

        PresetsBySection presets = new PresetsBySection(videoList, audioList, imageList, documentList);

        assertEquals(8, presets.totalPresetCount());
    }

    // Test equals(), hashCode(), toString()
    @Test
    void testEquals_SameInstance_ReturnsTrue() {
        PresetsBySection presets = PresetsBySection.empty();

        assertEquals(presets, presets);
    }

    @Test
    void testEquals_EqualInstances_ReturnsTrue() {
        List<SectionPreset> videoList = List.of(createVideoPreset("V1"));
        PresetsBySection presets1 = new PresetsBySection(videoList, null, null, null);
        PresetsBySection presets2 = new PresetsBySection(videoList, null, null, null);

        assertEquals(presets1, presets2);
    }

    @Test
    void testEquals_DifferentInstances_ReturnsFalse() {
        PresetsBySection presets1 = PresetsBySection.empty();
        List<SectionPreset> videoList = List.of(createVideoPreset("V1"));
        PresetsBySection presets2 = new PresetsBySection(videoList, null, null, null);

        assertNotEquals(presets1, presets2);
    }

    @Test
    void testHashCode_EqualInstances_SameHashCode() {
        List<SectionPreset> videoList = List.of(createVideoPreset("V1"));
        PresetsBySection presets1 = new PresetsBySection(videoList, null, null, null);
        PresetsBySection presets2 = new PresetsBySection(videoList, null, null, null);

        assertEquals(presets1.hashCode(), presets2.hashCode());
    }

    @Test
    void testToString_IncludesAllCounts() {
        List<SectionPreset> videoList = List.of(createVideoPreset("V1"), createVideoPreset("V2"));
        List<SectionPreset> audioList = List.of(createAudioPreset("A1"));
        PresetsBySection presets = new PresetsBySection(videoList, audioList, null, null);

        String toString = presets.toString();

        assertTrue(toString.contains("videoPresets=2 presets"));
        assertTrue(toString.contains("audioPresets=1 presets"));
        assertTrue(toString.contains("imagePresets=0 presets"));
        assertTrue(toString.contains("documentPresets=0 presets"));
        assertTrue(toString.contains("total=3"));
    }

    // Test JSON serialization/deserialization with Jackson
    @Test
    void testJsonSerialization_RoundTrip_Succeeds() throws Exception {
        List<SectionPreset> videoList = List.of(createVideoPreset("V1"));
        List<SectionPreset> audioList = List.of(createAudioPreset("A1"));
        PresetsBySection original = new PresetsBySection(videoList, audioList, null, null);

        String json = objectMapper.writeValueAsString(original);
        PresetsBySection deserialized = objectMapper.readValue(json, PresetsBySection.class);

        assertEquals(original, deserialized);
    }

    // Test that modifying returned lists doesn't affect internal state (defensive
    // copies)
    @Test
    void testDefensiveCopy_GetPresetsForCategory_Video_ModifyReturnedList_DoesNotAffectOriginal() {
        List<SectionPreset> videoList = new ArrayList<>(List.of(createVideoPreset("V1")));
        PresetsBySection presets = new PresetsBySection(videoList, null, null, null);

        List<SectionPreset> returnedList = presets.getPresetsForCategory(FormatCategory.VIDEO);
        returnedList.add(createVideoPreset("V2"));

        assertEquals(1, presets.getPresetsForCategory(FormatCategory.VIDEO).size());
        assertEquals(2, returnedList.size());
    }

    @Test
    void testDefensiveCopy_GetPresetsForCategory_Audio_ModifyReturnedList_DoesNotAffectOriginal() {
        List<SectionPreset> audioList = new ArrayList<>(List.of(createAudioPreset("A1")));
        PresetsBySection presets = new PresetsBySection(null, audioList, null, null);

        List<SectionPreset> returnedList = presets.getPresetsForCategory(FormatCategory.AUDIO);
        returnedList.add(createAudioPreset("A2"));

        assertEquals(1, presets.getPresetsForCategory(FormatCategory.AUDIO).size());
        assertEquals(2, returnedList.size());
    }

    @Test
    void testDefensiveCopy_GetPresetsForCategory_Image_ModifyReturnedList_DoesNotAffectOriginal() {
        List<SectionPreset> imageList = new ArrayList<>(List.of(createImagePreset("I1")));
        PresetsBySection presets = new PresetsBySection(null, null, imageList, null);

        List<SectionPreset> returnedList = presets.getPresetsForCategory(FormatCategory.IMAGE);
        returnedList.add(createImagePreset("I2"));

        assertEquals(1, presets.getPresetsForCategory(FormatCategory.IMAGE).size());
        assertEquals(2, returnedList.size());
    }

    @Test
    void testDefensiveCopy_GetPresetsForCategory_Document_ModifyReturnedList_DoesNotAffectOriginal() {
        List<SectionPreset> documentList = new ArrayList<>(List.of(createDocumentPreset("D1")));
        PresetsBySection presets = new PresetsBySection(null, null, null, documentList);

        List<SectionPreset> returnedList = presets.getPresetsForCategory(FormatCategory.DOCUMENT);
        returnedList.add(createDocumentPreset("D2"));

        assertEquals(1, presets.getPresetsForCategory(FormatCategory.DOCUMENT).size());
        assertEquals(2, returnedList.size());
    }
}