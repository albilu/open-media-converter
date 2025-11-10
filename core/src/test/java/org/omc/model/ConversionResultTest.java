// filepath: src/test/java/org/omc/model/ConversionResultTest.java

package org.omc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Comprehensive tests for ConversionResult model.
 * Tests constructor, factory methods, calculations, formatting, and
 * serialization.
 * 
 * Requirement: REQ-004.2 - Conversion execution with result tracking
 */
@DisplayName("ConversionResult Tests")
class ConversionResultTest {

    private static final String TEST_FILE_ID = "file-123";
    private static final Path TEST_OUTPUT_PATH = Paths.get("/output/file.mp4");
    private static final String TEST_TOOL_OUTPUT = "FFmpeg output line 1\nFFmpeg output line 2\nConversion complete";
    private static final Duration TEST_DURATION = Duration.ofMinutes(2);
    private static final long TEST_INPUT_SIZE = 10_000_000L; // 10MB
    private static final long TEST_OUTPUT_SIZE = 5_000_000L; // 5MB
    private static final ConversionTool TEST_TOOL = ConversionTool.FFMPEG;

    // ==================== Constructor Tests ====================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Constructor creates result with all fields")
        void constructor_WithAllFields_CreatesResult() {
            ConversionResult result = new ConversionResult(
                    TEST_FILE_ID,
                    true,
                    TEST_OUTPUT_PATH,
                    null,
                    TEST_TOOL_OUTPUT,
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertNotNull(result);
            assertEquals(TEST_FILE_ID, result.fileId());
            assertTrue(result.success());
            assertEquals(Optional.of(TEST_OUTPUT_PATH), result.outputPath());
            assertEquals(Optional.empty(), result.errorMessage());
            assertEquals(Optional.of(TEST_TOOL_OUTPUT), result.toolOutput());
            assertEquals(TEST_DURATION, result.conversionTime());
            assertEquals(TEST_INPUT_SIZE, result.inputSize());
            assertEquals(TEST_OUTPUT_SIZE, result.outputSize());
            assertEquals(TEST_TOOL, result.toolUsed());
        }

        @Test
        @DisplayName("Constructor accepts null outputPath for failures")
        void constructor_WithNullOutputPath_CreatesResult() {
            ConversionResult result = new ConversionResult(
                    TEST_FILE_ID,
                    false,
                    null,
                    "Conversion failed",
                    TEST_TOOL_OUTPUT,
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    0L,
                    TEST_TOOL);

            assertNotNull(result);
            assertFalse(result.success());
            assertEquals(Optional.empty(), result.outputPath());
            assertEquals(Optional.of("Conversion failed"), result.errorMessage());
            assertEquals(Optional.of(TEST_TOOL_OUTPUT), result.toolOutput());
        }

        @Test
        @DisplayName("Constructor accepts zero sizes")
        void constructor_WithZeroSizes_CreatesResult() {
            ConversionResult result = new ConversionResult(
                    TEST_FILE_ID,
                    true,
                    TEST_OUTPUT_PATH,
                    null,
                    null,
                    TEST_DURATION,
                    0L,
                    0L,
                    TEST_TOOL);

            assertNotNull(result);
            assertEquals(0L, result.inputSize());
            assertEquals(0L, result.outputSize());
        }

        @Test
        @DisplayName("Constructor accepts zero duration")
        void constructor_WithZeroDuration_CreatesResult() {
            ConversionResult result = new ConversionResult(
                    TEST_FILE_ID,
                    true,
                    TEST_OUTPUT_PATH,
                    null,
                    null,
                    Duration.ZERO,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertNotNull(result);
            assertEquals(Duration.ZERO, result.conversionTime());
        }
    }

    // ==================== Success Factory Tests ====================

    @Nested
    @DisplayName("Success Factory Method Tests")
    class SuccessFactoryTests {

        @Test
        @DisplayName("success() creates successful result")
        void success_CreatesSuccessfulResult() {
            ConversionResult result = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    TEST_TOOL_OUTPUT,
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertNotNull(result);
            assertTrue(result.success());
            assertEquals(TEST_FILE_ID, result.fileId());
            assertEquals(Optional.of(TEST_OUTPUT_PATH), result.outputPath());
            assertEquals(Optional.empty(), result.errorMessage());
            assertEquals(Optional.of(TEST_TOOL_OUTPUT), result.toolOutput());
            assertEquals(TEST_DURATION, result.conversionTime());
            assertEquals(TEST_INPUT_SIZE, result.inputSize());
            assertEquals(TEST_OUTPUT_SIZE, result.outputSize());
            assertEquals(TEST_TOOL, result.toolUsed());
            assertFalse(result.isCancelled());
        }

        @Test
        @DisplayName("success() with compression saves space")
        void success_WithCompression_CalculatesSpaceSaved() {
            long inputSize = 10_000_000L;
            long outputSize = 5_000_000L;

            ConversionResult result = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    TEST_TOOL_OUTPUT,
                    TEST_DURATION,
                    inputSize,
                    outputSize,
                    TEST_TOOL);

            assertTrue(result.success());
            assertEquals(5_000_000L, result.spaceSaved());
            assertEquals(50.0, result.compressionRatio(), 0.01);
        }

        @Test
        @DisplayName("success() with expansion shows negative space saved")
        void success_WithExpansion_CalculatesNegativeSpaceSaved() {
            long inputSize = 5_000_000L;
            long outputSize = 10_000_000L;

            ConversionResult result = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    null,
                    TEST_DURATION,
                    inputSize,
                    outputSize,
                    TEST_TOOL);

            assertTrue(result.success());
            assertEquals(-5_000_000L, result.spaceSaved());
            assertEquals(-100.0, result.compressionRatio(), 0.01);
        }
    }

    // ==================== Failure Factory Tests ====================

    @Nested
    @DisplayName("Failure Factory Method Tests")
    class FailureFactoryTests {

        @Test
        @DisplayName("failure() creates failed result with error message")
        void failure_CreatesFailedResult() {
            String errorMsg = "FFmpeg encoding error";

            ConversionResult result = ConversionResult.failure(TEST_FILE_ID, errorMsg, null, TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_TOOL);

            assertNotNull(result);
            assertFalse(result.success());
            assertEquals(TEST_FILE_ID, result.fileId());
            assertEquals(Optional.empty(), result.outputPath());
            assertEquals(Optional.of(errorMsg), result.errorMessage());
            assertEquals(TEST_DURATION, result.conversionTime());
            assertEquals(TEST_INPUT_SIZE, result.inputSize());
            assertEquals(0L, result.outputSize());
            assertEquals(TEST_TOOL, result.toolUsed());
            assertFalse(result.isCancelled());
        }

        @Test
        @DisplayName("failure() sets outputSize to zero")
        void failure_SetsOutputSizeToZero() {
            ConversionResult result = ConversionResult.failure(TEST_FILE_ID, "Error", null, TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_TOOL);

            assertEquals(0L, result.outputSize());
            assertEquals(TEST_INPUT_SIZE, result.spaceSaved());
        }
    }

    // ==================== Cancelled Factory Tests ====================

    @Nested
    @DisplayName("Cancelled Factory Method Tests")
    class CancelledFactoryTests {

        @Test
        @DisplayName("cancelled() creates cancelled result")
        void cancelled_CreatesCancelledResult() {
            ConversionResult result = ConversionResult.cancelled(TEST_FILE_ID, null, TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_TOOL);

            assertNotNull(result);
            assertFalse(result.success());
            assertEquals(TEST_FILE_ID, result.fileId());
            assertEquals(Optional.empty(), result.outputPath());
            assertTrue(result.errorMessage().isPresent());
            assertTrue(result.errorMessage().get().contains("cancelled"));
            assertEquals(TEST_DURATION, result.conversionTime());
            assertEquals(TEST_INPUT_SIZE, result.inputSize());
            assertEquals(0L, result.outputSize());
            assertEquals(TEST_TOOL, result.toolUsed());
            assertTrue(result.isCancelled());
        }

        @Test
        @DisplayName("cancelled() produces standard error message")
        void cancelled_HasStandardErrorMessage() {
            ConversionResult result = ConversionResult.cancelled(TEST_FILE_ID, null, TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_TOOL);

            assertEquals("Conversion cancelled by user", result.errorMessage().get());
        }
    }

    // ==================== isCancelled Tests ====================

    @Nested
    @DisplayName("isCancelled Method Tests")
    class IsCancelledTests {

        @Test
        @DisplayName("isCancelled returns true for cancelled results")
        void isCancelled_WithCancelledResult_ReturnsTrue() {
            ConversionResult result = ConversionResult.cancelled(TEST_FILE_ID, null, TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_TOOL);

            assertTrue(result.isCancelled());
        }

        @Test
        @DisplayName("isCancelled returns false for successful results")
        void isCancelled_WithSuccessfulResult_ReturnsFalse() {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertFalse(result.isCancelled());
        }

        @Test
        @DisplayName("isCancelled returns false for failed results")
        void isCancelled_WithFailedResult_ReturnsFalse() {
            ConversionResult result = ConversionResult.failure(TEST_FILE_ID, "FFmpeg error", null, TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_TOOL);

            assertFalse(result.isCancelled());
        }

        @Test
        @DisplayName("isCancelled detects 'cancelled' in error message")
        void isCancelled_WithCancelledInMessage_ReturnsTrue() {
            ConversionResult result = new ConversionResult(
                    TEST_FILE_ID,
                    false,
                    null,
                    "Operation was cancelled due to timeout",
                    null,
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    0L,
                    TEST_TOOL);

            assertTrue(result.isCancelled());
        }

        @Test
        @DisplayName("isCancelled returns false with no error message")
        void isCancelled_WithNoErrorMessage_ReturnsFalse() {
            ConversionResult result = new ConversionResult(
                    TEST_FILE_ID,
                    false,
                    null,
                    null,
                    null,
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    0L,
                    TEST_TOOL);

            assertFalse(result.isCancelled());
        }
    }

    // ==================== Space Calculation Tests ====================

    @Nested
    @DisplayName("Space Calculation Tests")
    class SpaceCalculationTests {

        @Test
        @DisplayName("spaceSaved() returns positive for compression")
        void spaceSaved_WithCompression_ReturnsPositive() {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    10_000_000L,
                    6_000_000L,
                    TEST_TOOL);

            assertEquals(4_000_000L, result.spaceSaved());
        }

        @Test
        @DisplayName("spaceSaved() returns negative for expansion")
        void spaceSaved_WithExpansion_ReturnsNegative() {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    5_000_000L,
                    8_000_000L,
                    TEST_TOOL);

            assertEquals(-3_000_000L, result.spaceSaved());
        }

        @Test
        @DisplayName("spaceSaved() returns zero for same size")
        void spaceSaved_WithSameSize_ReturnsZero() {
            long size = 7_000_000L;
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    size,
                    size,
                    TEST_TOOL);

            assertEquals(0L, result.spaceSaved());
        }

        @Test
        @DisplayName("spaceSaved() handles zero output size")
        void spaceSaved_WithZeroOutputSize_ReturnsInputSize() {
            ConversionResult result = ConversionResult.failure(TEST_FILE_ID, "Error", null, TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_TOOL);

            assertEquals(TEST_INPUT_SIZE, result.spaceSaved());
        }
    }

    // ==================== Compression Ratio Tests ====================

    @Nested
    @DisplayName("Compression Ratio Tests")
    class CompressionRatioTests {

        @ParameterizedTest
        @CsvSource({
                "10000000, 5000000, 50.0", // 50% compression
                "10000000, 2000000, 80.0", // 80% compression
                "10000000, 9000000, 10.0", // 10% compression
                "10000000, 10000000, 0.0", // No compression
                "5000000, 10000000, -100.0", // 100% expansion
                "1000000, 2000000, -100.0" // 100% expansion
        })
        @DisplayName("compressionRatio() calculates correctly for various sizes")
        void compressionRatio_CalculatesCorrectly(long inputSize, long outputSize, double expectedRatio) {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    inputSize,
                    outputSize,
                    TEST_TOOL);

            assertEquals(expectedRatio, result.compressionRatio(), 0.01);
        }

        @Test
        @DisplayName("compressionRatio() returns zero for zero input size")
        void compressionRatio_WithZeroInputSize_ReturnsZero() {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    0L,
                    1000L,
                    TEST_TOOL);

            assertEquals(0.0, result.compressionRatio(), 0.01);
        }

        @Test
        @DisplayName("compressionRatio() handles large values")
        void compressionRatio_WithLargeValues_CalculatesCorrectly() {
            long inputSize = 10_000_000_000L; // 10GB
            long outputSize = 5_000_000_000L; // 5GB

            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    inputSize,
                    outputSize,
                    TEST_TOOL);

            assertEquals(50.0, result.compressionRatio(), 0.01);
        }
    }

    // ==================== Format Conversion Time Tests ====================

    @Nested
    @DisplayName("Format Conversion Time Tests")
    class FormatConversionTimeTests {

        @Test
        @DisplayName("formatConversionTime() formats seconds only")
        void formatConversionTime_WithSecondsOnly_FormatsCorrectly() {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null,
                    Duration.ofSeconds(45),
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertEquals("45s", result.formatConversionTime());
        }

        @Test
        @DisplayName("formatConversionTime() formats minutes and seconds")
        void formatConversionTime_WithMinutesAndSeconds_FormatsCorrectly() {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null,
                    Duration.ofSeconds(125), // 2m 5s
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertEquals("2m 5s", result.formatConversionTime());
        }

        @Test
        @DisplayName("formatConversionTime() formats exact minutes")
        void formatConversionTime_WithExactMinutes_FormatsCorrectly() {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null,
                    Duration.ofMinutes(5),
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertEquals("5m 0s", result.formatConversionTime());
        }

        @Test
        @DisplayName("formatConversionTime() formats zero duration")
        void formatConversionTime_WithZeroDuration_FormatsCorrectly() {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, Duration.ZERO,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertEquals("0s", result.formatConversionTime());
        }

        @Test
        @DisplayName("formatConversionTime() formats long duration")
        void formatConversionTime_WithLongDuration_FormatsCorrectly() {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null,
                    Duration.ofSeconds(3725), // 62m 5s
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertEquals("62m 5s", result.formatConversionTime());
        }
    }

    // ==================== Equals and HashCode Tests ====================

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("equals() returns true for identical results")
        void equals_WithIdenticalResults_ReturnsTrue() {
            ConversionResult result1 = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    TEST_INPUT_SIZE, TEST_OUTPUT_SIZE, TEST_TOOL);
            ConversionResult result2 = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    TEST_INPUT_SIZE, TEST_OUTPUT_SIZE, TEST_TOOL);

            assertEquals(result1, result2);
            assertEquals(result1.hashCode(), result2.hashCode());
        }

        @Test
        @DisplayName("equals() returns false for different fileIds")
        void equals_WithDifferentFileIds_ReturnsFalse() {
            ConversionResult result1 = ConversionResult.success("file-1", TEST_OUTPUT_PATH, null, TEST_DURATION,
                    TEST_INPUT_SIZE, TEST_OUTPUT_SIZE, TEST_TOOL);
            ConversionResult result2 = ConversionResult.success("file-2", TEST_OUTPUT_PATH, null, TEST_DURATION,
                    TEST_INPUT_SIZE, TEST_OUTPUT_SIZE, TEST_TOOL);

            assertNotEquals(result1, result2);
        }

        @Test
        @DisplayName("equals() returns false for different success states")
        void equals_WithDifferentSuccessStates_ReturnsFalse() {
            ConversionResult result1 = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    TEST_INPUT_SIZE, TEST_OUTPUT_SIZE, TEST_TOOL);
            ConversionResult result2 = ConversionResult.failure(TEST_FILE_ID, "Error", null, TEST_DURATION,
                    TEST_INPUT_SIZE, TEST_TOOL);

            assertNotEquals(result1, result2);
        }

        @Test
        @DisplayName("equals() handles null comparison")
        void equals_WithNull_ReturnsFalse() {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    TEST_INPUT_SIZE, TEST_OUTPUT_SIZE, TEST_TOOL);

            assertNotEquals(result, null);
        }

        @Test
        @DisplayName("equals() handles self comparison")
        void equals_WithSelf_ReturnsTrue() {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    TEST_INPUT_SIZE, TEST_OUTPUT_SIZE, TEST_TOOL);

            assertEquals(result, result);
        }

        @Test
        @DisplayName("equals() handles different class comparison")
        void equals_WithDifferentClass_ReturnsFalse() {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    TEST_INPUT_SIZE, TEST_OUTPUT_SIZE, TEST_TOOL);

            assertNotEquals(result, "not a ConversionResult");
        }
    }

    // ==================== toString Tests ====================

    @Nested
    @DisplayName("toString Tests")
    class ToStringTests {

        @Test
        @DisplayName("toString() includes all key information")
        void toString_IncludesKeyInformation() {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            String str = result.toString();

            assertNotNull(str);
            assertTrue(str.contains("fileId"));
            assertTrue(str.contains(TEST_FILE_ID));
            assertTrue(str.contains("success=true"));
            assertTrue(str.contains("compressionRatio"));
            assertTrue(str.contains("toolUsed"));
        }

        @Test
        @DisplayName("toString() includes formatted conversion time")
        void toString_IncludesFormattedConversionTime() {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null,
                    Duration.ofSeconds(125),
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            String str = result.toString();

            assertTrue(str.contains("conversionTime"));
            assertTrue(str.contains("2m 5s"));
        }

        @Test
        @DisplayName("toString() includes formatted compression ratio")
        void toString_IncludesFormattedCompressionRatio() {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    10_000_000L,
                    5_000_000L,
                    TEST_TOOL);

            String str = result.toString();

            assertTrue(str.contains("compressionRatio"));
            assertTrue(str.contains("50.0%"));
        }

        @Test
        @DisplayName("toString() for failed result includes error message")
        void toString_ForFailedResult_IncludesErrorMessage() {
            ConversionResult result = ConversionResult.failure(TEST_FILE_ID, "FFmpeg encoding error", null,
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_TOOL);

            String str = result.toString();

            assertTrue(str.contains("success=false"));
            assertTrue(str.contains("errorMessage"));
        }
    }

    // ==================== JSON Serialization Tests ====================

    @Nested
    @DisplayName("JSON Serialization Tests")
    class JsonSerializationTests {

        private final ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());

        @Test
        @DisplayName("Result serializes to JSON")
        void result_SerializesToJson() throws Exception {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            String json = mapper.writeValueAsString(result);

            assertNotNull(json);
            assertTrue(json.contains("\"fileId\""));
            assertTrue(json.contains("\"success\""));
            assertTrue(json.contains("\"outputPath\""));
            assertTrue(json.contains("\"conversionTime\""));
        }

        @Test
        @DisplayName("Result deserializes from JSON")
        void result_DeserializesFromJson() throws Exception {
            String json = """
                    {
                        "fileId": "file-123",
                        "success": true,
                        "outputPath": "/output/file.mp4",
                        "errorMessage": null,
                        "conversionTime": 120.0,
                        "inputSize": 10000000,
                        "outputSize": 5000000,
                        "toolUsed": "FFMPEG"
                    }
                    """;

            ConversionResult result = mapper.readValue(json, ConversionResult.class);

            assertNotNull(result);
            assertEquals("file-123", result.fileId());
            assertTrue(result.success());
            assertEquals(Optional.of(Paths.get("/output/file.mp4")), result.outputPath());
            assertEquals(ConversionTool.FFMPEG, result.toolUsed());
        }

        @Test
        @DisplayName("Serialization roundtrip preserves data")
        void result_RoundtripPreservesData() throws Exception {
            ConversionResult original = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            String json = mapper.writeValueAsString(original);
            ConversionResult deserialized = mapper.readValue(json, ConversionResult.class);

            assertEquals(original, deserialized);
        }
    }

    // ==================== Edge Case Tests ====================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Result handles extremely large file sizes")
        void result_WithExtremelyLargeFileSizes_CalculatesCorrectly() {
            long inputSize = Long.MAX_VALUE / 2;
            long outputSize = Long.MAX_VALUE / 4;

            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    inputSize,
                    outputSize,
                    TEST_TOOL);

            assertTrue(result.spaceSaved() > 0);
            assertTrue(result.compressionRatio() > 0);
        }

        @Test
        @DisplayName("Result handles all conversion tools")
        void result_WithAllConversionTools_WorksCorrectly() {
            for (ConversionTool tool : ConversionTool.values()) {
                ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                        TEST_INPUT_SIZE,
                        TEST_OUTPUT_SIZE,
                        tool);

                assertEquals(tool, result.toolUsed());
            }
        }

        @Test
        @DisplayName("Result handles empty error message")
        void result_WithEmptyErrorMessage_WorksCorrectly() {
            ConversionResult result = ConversionResult.failure(TEST_FILE_ID, "", null, TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_TOOL);

            assertEquals(Optional.of(""), result.errorMessage());
            assertFalse(result.isCancelled());
        }

        @Test
        @DisplayName("Result handles very long conversion time")
        void result_WithVeryLongConversionTime_FormatsCorrectly() {
            Duration longDuration = Duration.ofHours(5).plusMinutes(30).plusSeconds(45);

            ConversionResult result = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    null,
                    longDuration,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            String formatted = result.formatConversionTime();
            assertTrue(formatted.contains("330m")); // 5 hours = 300 minutes + 30 minutes
            assertTrue(formatted.contains("45s"));
        }
    }

    // ==================== Integration Tests ====================

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Successful conversion has consistent state")
        void successfulConversion_HasConsistentState() {
            ConversionResult result = ConversionResult.success(TEST_FILE_ID, TEST_OUTPUT_PATH, null, TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertTrue(result.success());
            assertFalse(result.isCancelled());
            assertTrue(result.outputPath().isPresent());
            assertFalse(result.errorMessage().isPresent());
            assertTrue(result.outputSize() > 0);
        }

        @Test
        @DisplayName("Failed conversion has consistent state")
        void failedConversion_HasConsistentState() {
            ConversionResult result = ConversionResult.failure(TEST_FILE_ID, "Error occurred", null, TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_TOOL);

            assertFalse(result.success());
            assertFalse(result.isCancelled());
            assertFalse(result.outputPath().isPresent());
            assertTrue(result.errorMessage().isPresent());
            assertEquals(0L, result.outputSize());
        }

        @Test
        @DisplayName("Cancelled conversion has consistent state")
        void cancelledConversion_HasConsistentState() {
            ConversionResult result = ConversionResult.cancelled(TEST_FILE_ID, null, TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_TOOL);

            assertFalse(result.success());
            assertTrue(result.isCancelled());
            assertFalse(result.outputPath().isPresent());
            assertTrue(result.errorMessage().isPresent());
            assertEquals(0L, result.outputSize());
        }
    }

    // ==================== Tool Output Tests ====================

    @Nested
    @DisplayName("Tool Output Tests")
    class ToolOutputTests {

        @Test
        @DisplayName("Success with null toolOutput returns empty Optional")
        void success_WithNullToolOutput_ReturnsEmptyOptional() {
            ConversionResult result = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    null, // null toolOutput
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertTrue(result.success());
            assertEquals(Optional.empty(), result.toolOutput());
            assertFalse(result.toolOutput().isPresent());
        }

        @Test
        @DisplayName("Success with non-null toolOutput returns populated Optional")
        void success_WithNonNullToolOutput_ReturnsPopulatedOptional() {
            String output = "FFmpeg version 4.4\nConverting file...\nConversion complete";

            ConversionResult result = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    output,
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertTrue(result.success());
            assertEquals(Optional.of(output), result.toolOutput());
            assertTrue(result.toolOutput().isPresent());
            assertEquals(output, result.toolOutput().get());
        }

        @Test
        @DisplayName("Failure with null toolOutput returns empty Optional")
        void failure_WithNullToolOutput_ReturnsEmptyOptional() {
            ConversionResult result = ConversionResult.failure(
                    TEST_FILE_ID,
                    "Conversion failed",
                    null, // null toolOutput
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_TOOL);

            assertFalse(result.success());
            assertEquals(Optional.empty(), result.toolOutput());
        }

        @Test
        @DisplayName("Failure with non-null toolOutput returns populated Optional")
        void failure_WithNonNullToolOutput_ReturnsPopulatedOptional() {
            String output = "FFmpeg version 4.4\nError: Invalid codec\nConversion failed";

            ConversionResult result = ConversionResult.failure(
                    TEST_FILE_ID,
                    "Conversion failed",
                    output,
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_TOOL);

            assertFalse(result.success());
            assertEquals(Optional.of(output), result.toolOutput());
            assertEquals(output, result.toolOutput().get());
        }

        @Test
        @DisplayName("Cancelled with null toolOutput returns empty Optional")
        void cancelled_WithNullToolOutput_ReturnsEmptyOptional() {
            ConversionResult result = ConversionResult.cancelled(
                    TEST_FILE_ID,
                    null, // null toolOutput
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_TOOL);

            assertTrue(result.isCancelled());
            assertEquals(Optional.empty(), result.toolOutput());
        }

        @Test
        @DisplayName("Cancelled with partial toolOutput returns populated Optional")
        void cancelled_WithPartialToolOutput_ReturnsPopulatedOptional() {
            String partialOutput = "FFmpeg version 4.4\nConverting file...\n[Cancelled by user]";

            ConversionResult result = ConversionResult.cancelled(
                    TEST_FILE_ID,
                    partialOutput,
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_TOOL);

            assertTrue(result.isCancelled());
            assertEquals(Optional.of(partialOutput), result.toolOutput());
            assertEquals(partialOutput, result.toolOutput().get());
        }

        @Test
        @DisplayName("ToolOutput with empty string is preserved")
        void toolOutput_WithEmptyString_IsPreserved() {
            String emptyOutput = "";

            ConversionResult result = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    emptyOutput,
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertEquals(Optional.of(emptyOutput), result.toolOutput());
            assertTrue(result.toolOutput().isPresent());
            assertEquals("", result.toolOutput().get());
        }

        @Test
        @DisplayName("ToolOutput with multiline content is preserved")
        void toolOutput_WithMultilineContent_IsPreserved() {
            String multilineOutput = "Line 1\nLine 2\nLine 3\n\nLine 5";

            ConversionResult result = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    multilineOutput,
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertEquals(Optional.of(multilineOutput), result.toolOutput());
            assertEquals(multilineOutput, result.toolOutput().get());
            assertTrue(result.toolOutput().get().contains("\n"));
        }

        @Test
        @DisplayName("ToolOutput serializes and deserializes correctly with Jackson")
        void toolOutput_SerializesAndDeserializesCorrectly() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());

            String toolOutput = "FFmpeg output\nMultiple lines\nFinal line";

            ConversionResult original = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    toolOutput,
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            String json = mapper.writeValueAsString(original);
            ConversionResult deserialized = mapper.readValue(json, ConversionResult.class);

            assertEquals(original.toolOutput(), deserialized.toolOutput());
            assertEquals(toolOutput, deserialized.toolOutput().get());
        }

        @Test
        @DisplayName("ToolOutput null serializes and deserializes correctly with Jackson")
        void toolOutput_NullSerializesAndDeserializesCorrectly() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());

            ConversionResult original = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    null, // null toolOutput
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            String json = mapper.writeValueAsString(original);
            ConversionResult deserialized = mapper.readValue(json, ConversionResult.class);

            assertEquals(Optional.empty(), deserialized.toolOutput());
            assertFalse(deserialized.toolOutput().isPresent());
        }

        @Test
        @DisplayName("Backward compatibility: Old JSON without toolOutput field deserializes")
        void backwardCompatibility_OldJsonWithoutToolOutput_Deserializes() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());

            // Simulate old JSON format without toolOutput field
            String oldJson = """
                    {
                        "fileId": "file-123",
                        "success": true,
                        "outputPath": "/output/file.mp4",
                        "errorMessage": null,
                        "conversionTime": 120000000000,
                        "inputSize": 10000000,
                        "outputSize": 5000000,
                        "toolUsed": "FFMPEG"
                    }
                    """;

            ConversionResult result = mapper.readValue(oldJson, ConversionResult.class);

            assertNotNull(result);
            assertTrue(result.success());
            assertEquals(TEST_FILE_ID, result.fileId());
            assertEquals(Optional.empty(), result.toolOutput()); // Should default to empty
            assertFalse(result.toolOutput().isPresent());
        }

        @Test
        @DisplayName("ToolOutput is included in equals comparison")
        void toolOutput_IsIncludedInEqualsComparison() {
            ConversionResult result1 = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    "Output A",
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            ConversionResult result2 = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    "Output B",
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            ConversionResult result3 = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    "Output A",
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertNotEquals(result1, result2); // Different toolOutput
            assertEquals(result1, result3); // Same toolOutput
        }

        @Test
        @DisplayName("ToolOutput is included in hashCode")
        void toolOutput_IsIncludedInHashCode() {
            ConversionResult result1 = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    "Output A",
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            ConversionResult result2 = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    "Output B",
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            ConversionResult result3 = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    "Output A",
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertNotEquals(result1.hashCode(), result2.hashCode()); // Different toolOutput
            assertEquals(result1.hashCode(), result3.hashCode()); // Same toolOutput
        }

        @Test
        @DisplayName("ToolOutput is included in toString")
        void toolOutput_IsIncludedInToString() {
            String toolOutput = "FFmpeg conversion output";

            ConversionResult result = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    toolOutput,
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            String toString = result.toString();
            assertTrue(toString.contains("toolOutput")); // Field name should appear
        }

        @Test
        @DisplayName("ToolOutput with very large content is handled correctly")
        void toolOutput_WithVeryLargeContent_IsHandledCorrectly() {
            // Simulate a 100KB output
            StringBuilder largeOutput = new StringBuilder(100_000);
            for (int i = 0; i < 10_000; i++) {
                largeOutput.append("Line ").append(i).append("\n");
            }
            String largeOutputString = largeOutput.toString();

            ConversionResult result = ConversionResult.success(
                    TEST_FILE_ID,
                    TEST_OUTPUT_PATH,
                    largeOutputString,
                    TEST_DURATION,
                    TEST_INPUT_SIZE,
                    TEST_OUTPUT_SIZE,
                    TEST_TOOL);

            assertEquals(Optional.of(largeOutputString), result.toolOutput());
            assertTrue(result.toolOutput().get().length() > 50_000); // Should be preserved
        }
    }
}
