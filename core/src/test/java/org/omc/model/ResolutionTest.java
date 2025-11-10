package org.omc.model;

import org.omc.model.Resolution;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Resolution}.
 * 
 * Tests:
 * - Constructor validation
 * - Getters (width, height, aspect ratio, pixel count)
 * - Orientation detection (landscape, portrait, square)
 * - Scaling operations
 * - Fit within bounds
 * - String parsing
 * - Common presets
 * - JSON serialization
 * - equals/hashCode/toString
 */
@DisplayName("Resolution Tests")
class ResolutionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ===========================
    // Constructor Tests
    // ===========================

    @Test
    @DisplayName("Constructor creates valid resolution")
    void constructor_WithValidDimensions_CreatesResolution() {
        Resolution resolution = new Resolution(1920, 1080);

        assertEquals(1920, resolution.getWidth());
        assertEquals(1080, resolution.getHeight());
    }

    @Test
    @DisplayName("Constructor rejects zero width")
    void constructor_WithZeroWidth_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Resolution(0, 1080));
        assertTrue(exception.getMessage().contains("Width must be positive"));
    }

    @Test
    @DisplayName("Constructor rejects negative width")
    void constructor_WithNegativeWidth_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Resolution(-1920, 1080));
        assertTrue(exception.getMessage().contains("Width must be positive"));
    }

    @Test
    @DisplayName("Constructor rejects zero height")
    void constructor_WithZeroHeight_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Resolution(1920, 0));
        assertTrue(exception.getMessage().contains("Height must be positive"));
    }

    @Test
    @DisplayName("Constructor rejects negative height")
    void constructor_WithNegativeHeight_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Resolution(1920, -1080));
        assertTrue(exception.getMessage().contains("Height must be positive"));
    }

    @Test
    @DisplayName("Constructor accepts minimum valid dimensions (1x1)")
    void constructor_WithMinimumDimensions_CreatesResolution() {
        Resolution resolution = new Resolution(1, 1);

        assertEquals(1, resolution.getWidth());
        assertEquals(1, resolution.getHeight());
    }

    // ===========================
    // Getter Tests
    // ===========================

    @Test
    @DisplayName("getAspectRatio calculates correct ratio for 16:9")
    void getAspectRatio_For16by9_ReturnsCorrectRatio() {
        Resolution resolution = new Resolution(1920, 1080);

        double aspectRatio = resolution.getAspectRatio();

        assertEquals(16.0 / 9.0, aspectRatio, 0.001);
    }

    @Test
    @DisplayName("getAspectRatio calculates correct ratio for 4:3")
    void getAspectRatio_For4by3_ReturnsCorrectRatio() {
        Resolution resolution = new Resolution(640, 480);

        double aspectRatio = resolution.getAspectRatio();

        assertEquals(4.0 / 3.0, aspectRatio, 0.001);
    }

    @Test
    @DisplayName("getAspectRatio returns 1.0 for square")
    void getAspectRatio_ForSquare_ReturnsOne() {
        Resolution resolution = new Resolution(1024, 1024);

        double aspectRatio = resolution.getAspectRatio();

        assertEquals(1.0, aspectRatio, 0.001);
    }

    @Test
    @DisplayName("getPixelCount calculates correct total pixels")
    void getPixelCount_CalculatesTotalPixels() {
        Resolution resolution = new Resolution(1920, 1080);

        long pixelCount = resolution.getPixelCount();

        assertEquals(2073600L, pixelCount);
    }

    @Test
    @DisplayName("getPixelCount handles large resolutions without overflow")
    void getPixelCount_ForLargeResolution_NoOverflow() {
        Resolution resolution = new Resolution(7680, 4320); // 8K

        long pixelCount = resolution.getPixelCount();

        assertEquals(33177600L, pixelCount);
    }

    // ===========================
    // Orientation Tests
    // ===========================

    @Test
    @DisplayName("isLandscape returns true for landscape orientation")
    void isLandscape_ForLandscapeResolution_ReturnsTrue() {
        Resolution resolution = new Resolution(1920, 1080);

        assertTrue(resolution.isLandscape());
        assertFalse(resolution.isPortrait());
        assertFalse(resolution.isSquare());
    }

    @Test
    @DisplayName("isPortrait returns true for portrait orientation")
    void isPortrait_ForPortraitResolution_ReturnsTrue() {
        Resolution resolution = new Resolution(1080, 1920);

        assertTrue(resolution.isPortrait());
        assertFalse(resolution.isLandscape());
        assertFalse(resolution.isSquare());
    }

    @Test
    @DisplayName("isSquare returns true for square resolution")
    void isSquare_ForSquareResolution_ReturnsTrue() {
        Resolution resolution = new Resolution(1024, 1024);

        assertTrue(resolution.isSquare());
        assertFalse(resolution.isLandscape());
        assertFalse(resolution.isPortrait());
    }

    // ===========================
    // Scaling Tests
    // ===========================

    @Test
    @DisplayName("scale doubles resolution with factor 2.0")
    void scale_WithFactorTwo_DoublesResolution() {
        Resolution original = new Resolution(1920, 1080);

        Resolution scaled = original.scale(2.0);

        assertEquals(3840, scaled.getWidth());
        assertEquals(2160, scaled.getHeight());
    }

    @Test
    @DisplayName("scale halves resolution with factor 0.5")
    void scale_WithFactorHalf_HalvesResolution() {
        Resolution original = new Resolution(1920, 1080);

        Resolution scaled = original.scale(0.5);

        assertEquals(960, scaled.getWidth());
        assertEquals(540, scaled.getHeight());
    }

    @Test
    @DisplayName("scale with factor 1.0 returns equivalent resolution")
    void scale_WithFactorOne_ReturnsSameDimensions() {
        Resolution original = new Resolution(1920, 1080);

        Resolution scaled = original.scale(1.0);

        assertEquals(1920, scaled.getWidth());
        assertEquals(1080, scaled.getHeight());
    }

    @Test
    @DisplayName("scale ensures minimum 1x1 for very small factors")
    void scale_WithVerySmallFactor_ReturnsMinimumOnePixel() {
        Resolution original = new Resolution(1920, 1080);

        Resolution scaled = original.scale(0.0001);

        assertEquals(1, scaled.getWidth());
        assertEquals(1, scaled.getHeight());
    }

    @Test
    @DisplayName("scale rejects zero factor")
    void scale_WithZeroFactor_ThrowsException() {
        Resolution resolution = new Resolution(1920, 1080);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> resolution.scale(0));
        assertTrue(exception.getMessage().contains("Scale must be positive"));
    }

    @Test
    @DisplayName("scale rejects negative factor")
    void scale_WithNegativeFactor_ThrowsException() {
        Resolution resolution = new Resolution(1920, 1080);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> resolution.scale(-1.5));
        assertTrue(exception.getMessage().contains("Scale must be positive"));
    }

    // ===========================
    // Fit Within Tests
    // ===========================

    @Test
    @DisplayName("fitWithin scales down when larger than bounds")
    void fitWithin_WhenLargerThanBounds_ScalesDown() {
        Resolution original = new Resolution(1920, 1080);

        Resolution fitted = original.fitWithin(1280, 720);

        assertEquals(1280, fitted.getWidth());
        assertEquals(720, fitted.getHeight());
    }

    @Test
    @DisplayName("fitWithin maintains aspect ratio when width is limiting")
    void fitWithin_WhenWidthLimiting_MaintainsAspectRatio() {
        Resolution original = new Resolution(1920, 1080);

        Resolution fitted = original.fitWithin(1000, 2000);

        assertEquals(1000, fitted.getWidth());
        assertEquals(563, fitted.getHeight()); // Maintains 16:9 aspect ratio (rounds to 563)
    }

    @Test
    @DisplayName("fitWithin maintains aspect ratio when height is limiting")
    void fitWithin_WhenHeightLimiting_MaintainsAspectRatio() {
        Resolution original = new Resolution(1920, 1080);

        Resolution fitted = original.fitWithin(2000, 500);

        assertEquals(889, fitted.getWidth()); // Maintains 16:9 aspect ratio
        assertEquals(500, fitted.getHeight());
    }

    @Test
    @DisplayName("fitWithin returns same instance when already fits")
    void fitWithin_WhenAlreadyFits_ReturnsSameInstance() {
        Resolution original = new Resolution(1280, 720);

        Resolution fitted = original.fitWithin(1920, 1080);

        assertSame(original, fitted);
    }

    @Test
    @DisplayName("fitWithin returns same instance when exactly matches bounds")
    void fitWithin_WhenExactlyMatchesBounds_ReturnsSameInstance() {
        Resolution original = new Resolution(1920, 1080);

        Resolution fitted = original.fitWithin(1920, 1080);

        assertSame(original, fitted);
    }

    // ===========================
    // Parse Tests
    // ===========================

    @Test
    @DisplayName("parse creates resolution from valid string")
    void parse_WithValidString_CreatesResolution() {
        Resolution resolution = Resolution.parse("1920x1080");

        assertEquals(1920, resolution.getWidth());
        assertEquals(1080, resolution.getHeight());
    }

    @Test
    @DisplayName("parse handles uppercase X separator")
    void parse_WithUppercaseX_CreatesResolution() {
        Resolution resolution = Resolution.parse("1920X1080");

        assertEquals(1920, resolution.getWidth());
        assertEquals(1080, resolution.getHeight());
    }

    @Test
    @DisplayName("parse handles whitespace")
    void parse_WithWhitespace_CreatesResolution() {
        Resolution resolution = Resolution.parse("  1920 x 1080  ");

        assertEquals(1920, resolution.getWidth());
        assertEquals(1080, resolution.getHeight());
    }

    @Test
    @DisplayName("parse rejects null string")
    void parse_WithNull_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Resolution.parse(null));
        assertTrue(exception.getMessage().contains("cannot be null or empty"));
    }

    @Test
    @DisplayName("parse rejects empty string")
    void parse_WithEmptyString_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Resolution.parse(""));
        assertTrue(exception.getMessage().contains("cannot be null or empty"));
    }

    @Test
    @DisplayName("parse rejects blank string")
    void parse_WithBlankString_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Resolution.parse("   "));
        assertTrue(exception.getMessage().contains("cannot be null or empty"));
    }

    @Test
    @DisplayName("parse rejects invalid format without separator")
    void parse_WithoutSeparator_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Resolution.parse("1920-1080"));
        assertTrue(exception.getMessage().contains("Invalid resolution format"));
    }

    @Test
    @DisplayName("parse rejects non-numeric width")
    void parse_WithNonNumericWidth_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Resolution.parse("ABCxDEF"));
        assertTrue(exception.getMessage().contains("must be integers"));
    }

    @Test
    @DisplayName("parse rejects negative dimensions")
    void parse_WithNegativeDimensions_ThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> Resolution.parse("-1920x1080"));
    }

    // ===========================
    // Preset Constants Tests
    // ===========================

    @Test
    @DisplayName("HD_720P preset has correct dimensions")
    void preset_HD720P_HasCorrectDimensions() {
        assertEquals(1280, Resolution.HD_720P.getWidth());
        assertEquals(720, Resolution.HD_720P.getHeight());
    }

    @Test
    @DisplayName("FULL_HD_1080P preset has correct dimensions")
    void preset_FullHD1080P_HasCorrectDimensions() {
        assertEquals(1920, Resolution.FULL_HD_1080P.getWidth());
        assertEquals(1080, Resolution.FULL_HD_1080P.getHeight());
    }

    @Test
    @DisplayName("QHD_1440P preset has correct dimensions")
    void preset_QHD1440P_HasCorrectDimensions() {
        assertEquals(2560, Resolution.QHD_1440P.getWidth());
        assertEquals(1440, Resolution.QHD_1440P.getHeight());
    }

    @Test
    @DisplayName("UHD_4K preset has correct dimensions")
    void preset_UHD4K_HasCorrectDimensions() {
        assertEquals(3840, Resolution.UHD_4K.getWidth());
        assertEquals(2160, Resolution.UHD_4K.getHeight());
    }

    @Test
    @DisplayName("SD_480P preset has correct dimensions")
    void preset_SD480P_HasCorrectDimensions() {
        assertEquals(640, Resolution.SD_480P.getWidth());
        assertEquals(480, Resolution.SD_480P.getHeight());
    }

    // ===========================
    // JSON Serialization Tests
    // ===========================

    @Test
    @DisplayName("Serializes to JSON correctly")
    void serialize_ToJson_ProducesCorrectFormat() throws Exception {
        Resolution resolution = new Resolution(1920, 1080);

        String json = objectMapper.writeValueAsString(resolution);

        assertTrue(json.contains("\"width\":1920"));
        assertTrue(json.contains("\"height\":1080"));
    }

    @Test
    @DisplayName("Deserializes from JSON correctly")
    void deserialize_FromJson_CreatesCorrectObject() throws Exception {
        String json = "{\"width\":1920,\"height\":1080}";

        Resolution resolution = objectMapper.readValue(json, Resolution.class);

        assertEquals(1920, resolution.getWidth());
        assertEquals(1080, resolution.getHeight());
    }

    @Test
    @DisplayName("Round-trip serialization preserves data")
    void roundTrip_SerializationDeserialization_PreservesData() throws Exception {
        Resolution original = new Resolution(2560, 1440);

        String json = objectMapper.writeValueAsString(original);
        Resolution deserialized = objectMapper.readValue(json, Resolution.class);

        assertEquals(original, deserialized);
    }

    @Test
    @DisplayName("Deserializes ignoring unknown properties")
    void deserialize_WithUnknownProperties_IgnoresThem() throws Exception {
        String json = "{\"width\":1920,\"height\":1080,\"unknownField\":\"value\"}";

        Resolution resolution = objectMapper.readValue(json, Resolution.class);

        assertEquals(1920, resolution.getWidth());
        assertEquals(1080, resolution.getHeight());
    }

    // ===========================
    // equals/hashCode/toString Tests
    // ===========================

    @Test
    @DisplayName("equals returns true for same dimensions")
    void equals_WithSameDimensions_ReturnsTrue() {
        Resolution resolution1 = new Resolution(1920, 1080);
        Resolution resolution2 = new Resolution(1920, 1080);

        assertEquals(resolution1, resolution2);
    }

    @Test
    @DisplayName("equals returns false for different dimensions")
    void equals_WithDifferentDimensions_ReturnsFalse() {
        Resolution resolution1 = new Resolution(1920, 1080);
        Resolution resolution2 = new Resolution(1280, 720);

        assertNotEquals(resolution1, resolution2);
    }

    @Test
    @DisplayName("equals is reflexive")
    void equals_IsReflexive() {
        Resolution resolution = new Resolution(1920, 1080);

        assertEquals(resolution, resolution);
    }

    @Test
    @DisplayName("equals returns false for null")
    void equals_WithNull_ReturnsFalse() {
        Resolution resolution = new Resolution(1920, 1080);

        assertNotEquals(null, resolution);
    }

    @Test
    @DisplayName("equals returns false for different class")
    void equals_WithDifferentClass_ReturnsFalse() {
        Resolution resolution = new Resolution(1920, 1080);

        assertNotEquals(resolution, "1920x1080");
    }

    @Test
    @DisplayName("hashCode is consistent with equals")
    void hashCode_IsConsistentWithEquals() {
        Resolution resolution1 = new Resolution(1920, 1080);
        Resolution resolution2 = new Resolution(1920, 1080);

        assertEquals(resolution1.hashCode(), resolution2.hashCode());
    }

    @Test
    @DisplayName("toString returns WIDTHxHEIGHT format")
    void toString_ReturnsCorrectFormat() {
        Resolution resolution = new Resolution(1920, 1080);

        String result = resolution.toString();

        assertEquals("1920x1080", result);
    }

    @Test
    @DisplayName("toString output can be parsed back")
    void toString_OutputCanBeParsedBack() {
        Resolution original = new Resolution(2560, 1440);

        String str = original.toString();
        Resolution parsed = Resolution.parse(str);

        assertEquals(original, parsed);
    }
}
