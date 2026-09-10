package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.omc.model.Resolution;

/**
 * Tests for the video resolution and frame-rate dropdown index mappings.
 *
 * <p>
 * The dropdown label list, the widget-to-model read path and the
 * model-to-widget load path must all be derived from ONE shared
 * source-of-truth array so that a displayed label always round-trips to the
 * same value. These tests verify the mapping helpers headlessly (pure static
 * seams, no GTK initialization), reproducing the defects where "8K" read back
 * as 480p and 30 fps loaded as "25 fps".
 * </p>
 */
@DisplayName("Video resolution and frame rate dropdown mappings")
class SettingsDialogVideoMappingTest {

    // ========== Resolution: index -> value ==========

    @Test
    @DisplayName("Resolution dropdown labels match the currently displayed list exactly")
    void testResolutionLabelsExact() {
        assertArrayEquals(new String[] {
                "Original",
                "8K (7680x4320)",
                "4K (3840x2160)",
                "1440p (2560x1440)",
                "1080p (1920x1080)",
                "720p (1280x720)",
                "480p (854x480)",
                "360p (640x360)",
                "Custom"
        }, SettingsDialogJavaGi.videoResolutionDropdownLabels());
    }

    @Test
    @DisplayName("Original (index 0) maps to null resolution")
    void testResolutionIndexZeroIsOriginal() {
        assertNull(SettingsDialogJavaGi.videoResolutionForDropdownIndex(0));
    }

    @Test
    @DisplayName("Every displayed preset index maps to the resolution its label shows")
    void testResolutionForEveryDisplayedIndex() {
        assertEquals(new Resolution(7680, 4320),
                SettingsDialogJavaGi.videoResolutionForDropdownIndex(1), "index 1 = 8K");
        assertEquals(new Resolution(3840, 2160),
                SettingsDialogJavaGi.videoResolutionForDropdownIndex(2), "index 2 = 4K");
        assertEquals(new Resolution(2560, 1440),
                SettingsDialogJavaGi.videoResolutionForDropdownIndex(3), "index 3 = 1440p");
        assertEquals(new Resolution(1920, 1080),
                SettingsDialogJavaGi.videoResolutionForDropdownIndex(4), "index 4 = 1080p");
        assertEquals(new Resolution(1280, 720),
                SettingsDialogJavaGi.videoResolutionForDropdownIndex(5), "index 5 = 720p");
        assertEquals(new Resolution(854, 480),
                SettingsDialogJavaGi.videoResolutionForDropdownIndex(6), "index 6 = 480p");
        assertEquals(new Resolution(640, 360),
                SettingsDialogJavaGi.videoResolutionForDropdownIndex(7), "index 7 = 360p");
    }

    @Test
    @DisplayName("Custom (last index) has no array value; manual WxH entry is preserved")
    void testResolutionCustomIndexHasNoPresetValue() {
        assertNull(SettingsDialogJavaGi.videoResolutionForDropdownIndex(8));
    }

    @Test
    @DisplayName("Out-of-range resolution indices map to null")
    void testResolutionOutOfRangeIndices() {
        assertNull(SettingsDialogJavaGi.videoResolutionForDropdownIndex(-1));
        assertNull(SettingsDialogJavaGi.videoResolutionForDropdownIndex(9));
        assertNull(SettingsDialogJavaGi.videoResolutionForDropdownIndex(100));
    }

    // ========== Resolution: value -> index (load path) ==========

    @Test
    @DisplayName("null resolution loads as Original (index 0)")
    void testResolutionNullLoadsAsOriginal() {
        assertEquals(0, SettingsDialogJavaGi.videoDropdownIndexForResolution(null));
    }

    @Test
    @DisplayName("Every preset resolution loads as its own label, not Custom")
    void testIndexForEveryPresetResolution() {
        assertEquals(1, SettingsDialogJavaGi.videoDropdownIndexForResolution(new Resolution(7680, 4320)), "8K");
        assertEquals(2, SettingsDialogJavaGi.videoDropdownIndexForResolution(new Resolution(3840, 2160)), "4K");
        assertEquals(3, SettingsDialogJavaGi.videoDropdownIndexForResolution(new Resolution(2560, 1440)), "1440p");
        assertEquals(4, SettingsDialogJavaGi.videoDropdownIndexForResolution(new Resolution(1920, 1080)), "1080p");
        assertEquals(5, SettingsDialogJavaGi.videoDropdownIndexForResolution(new Resolution(1280, 720)), "720p");
        assertEquals(6, SettingsDialogJavaGi.videoDropdownIndexForResolution(new Resolution(854, 480)), "480p");
        assertEquals(7, SettingsDialogJavaGi.videoDropdownIndexForResolution(new Resolution(640, 360)), "360p");
    }

    @Test
    @DisplayName("Non-preset resolution loads as Custom (manual WxH round-trip)")
    void testNonPresetResolutionLoadsAsCustom() {
        assertEquals(8, SettingsDialogJavaGi.videoDropdownIndexForResolution(new Resolution(1234, 567)));
    }

    @Test
    @DisplayName("Resolution index round-trips for every entry in the shared array")
    void testResolutionRoundTripForEachEntry() {
        String[] labels = SettingsDialogJavaGi.videoResolutionDropdownLabels();
        for (int index = 1; index <= labels.length - 2; index++) {
            Resolution value = SettingsDialogJavaGi.videoResolutionForDropdownIndex(index);
            assertEquals(index, SettingsDialogJavaGi.videoDropdownIndexForResolution(value),
                    "round-trip failed for label: " + labels[index]);
        }
    }

    @Test
    @DisplayName("Each resolution label embeds the dimensions of its mapped resolution")
    void testResolutionLabelsAgreeWithValues() {
        String[] labels = SettingsDialogJavaGi.videoResolutionDropdownLabels();
        for (int index = 1; index <= labels.length - 2; index++) {
            Resolution value = SettingsDialogJavaGi.videoResolutionForDropdownIndex(index);
            assertEquals(value.getWidth() + "x" + value.getHeight(),
                    labels[index].replaceAll("^.* \\(([0-9]+x[0-9]+)\\)$", "$1"),
                    "label/value mismatch at index " + index);
        }
    }

    // ========== Frame rate: index -> value ==========

    @Test
    @DisplayName("Frame rate dropdown labels match the currently displayed list exactly")
    void testFrameRateLabelsExact() {
        assertArrayEquals(new String[] {
                "Original",
                "24 fps",
                "25 fps",
                "30 fps",
                "50 fps",
                "60 fps",
                "120 fps"
        }, SettingsDialogJavaGi.videoFrameRateDropdownLabels());
    }

    @Test
    @DisplayName("Every displayed frame rate index maps to the fps its label shows")
    void testFrameRateForEveryDisplayedIndex() {
        assertEquals(-1, SettingsDialogJavaGi.videoFrameRateForDropdownIndex(0), "index 0 = Original");
        assertEquals(24, SettingsDialogJavaGi.videoFrameRateForDropdownIndex(1), "index 1 = 24 fps");
        assertEquals(25, SettingsDialogJavaGi.videoFrameRateForDropdownIndex(2), "index 2 = 25 fps");
        assertEquals(30, SettingsDialogJavaGi.videoFrameRateForDropdownIndex(3), "index 3 = 30 fps");
        assertEquals(50, SettingsDialogJavaGi.videoFrameRateForDropdownIndex(4), "index 4 = 50 fps");
        assertEquals(60, SettingsDialogJavaGi.videoFrameRateForDropdownIndex(5), "index 5 = 60 fps");
        assertEquals(120, SettingsDialogJavaGi.videoFrameRateForDropdownIndex(6), "index 6 = 120 fps");
    }

    @Test
    @DisplayName("Out-of-range frame rate indices map to Original (-1)")
    void testFrameRateOutOfRangeIndices() {
        assertEquals(-1, SettingsDialogJavaGi.videoFrameRateForDropdownIndex(-1));
        assertEquals(-1, SettingsDialogJavaGi.videoFrameRateForDropdownIndex(7));
        assertEquals(-1, SettingsDialogJavaGi.videoFrameRateForDropdownIndex(99));
    }

    // ========== Frame rate: value -> index (load path) ==========

    @Test
    @DisplayName("Original (-1) and unknown frame rates load as Original (index 0)")
    void testFrameRateOriginalAndUnknownLoadAsOriginal() {
        assertEquals(0, SettingsDialogJavaGi.videoDropdownIndexForFrameRate(-1));
        assertEquals(0, SettingsDialogJavaGi.videoDropdownIndexForFrameRate(0));
        assertEquals(0, SettingsDialogJavaGi.videoDropdownIndexForFrameRate(48));
    }

    @Test
    @DisplayName("Every listed frame rate loads as its own label")
    void testIndexForEveryListedFrameRate() {
        assertEquals(1, SettingsDialogJavaGi.videoDropdownIndexForFrameRate(24));
        assertEquals(2, SettingsDialogJavaGi.videoDropdownIndexForFrameRate(25));
        assertEquals(3, SettingsDialogJavaGi.videoDropdownIndexForFrameRate(30));
        assertEquals(4, SettingsDialogJavaGi.videoDropdownIndexForFrameRate(50));
        assertEquals(5, SettingsDialogJavaGi.videoDropdownIndexForFrameRate(60));
        assertEquals(6, SettingsDialogJavaGi.videoDropdownIndexForFrameRate(120));
    }

    @Test
    @DisplayName("Frame rate index round-trips for all 7 dropdown entries")
    void testFrameRateRoundTripForAllEntries() {
        String[] labels = SettingsDialogJavaGi.videoFrameRateDropdownLabels();
        for (int index = 0; index < labels.length; index++) {
            int value = SettingsDialogJavaGi.videoFrameRateForDropdownIndex(index);
            assertEquals(index, SettingsDialogJavaGi.videoDropdownIndexForFrameRate(value),
                    "round-trip failed for label: " + labels[index]);
        }
    }
}
