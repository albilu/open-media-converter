// filepath: src/test/java/org/omc/model/BatchConversionResultTest.java

package org.omc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for BatchConversionResult model.
 * Requirement REQ-004.2: Batch conversion with aggregated results.
 */
class BatchConversionResultTest {

    @Test
    void constructor_WithValidParameters_CreatesInstance() {
        // Arrange
        List<ConversionResult> results = createSampleResults();
        Duration totalTime = Duration.ofMinutes(5);

        // Act
        BatchConversionResult batch = new BatchConversionResult(
                results, totalTime, 2, 1, 3000L, 1500L);

        // Assert
        assertNotNull(batch);
        assertEquals(3, batch.totalCount());
        assertEquals(2, batch.successCount());
        assertEquals(1, batch.failureCount());
        assertEquals(totalTime, batch.totalTime());
        assertEquals(3000L, batch.totalInputSize());
        assertEquals(1500L, batch.totalOutputSize());
    }

    @Test
    void from_WithSuccessfulResults_CalculatesCorrectly() {
        // Arrange
        List<ConversionResult> results = List.of(
                ConversionResult.success("file1", Paths.get("/output1.mp4"), null, Duration.ofSeconds(30), 1000L, 500L,
                        ConversionTool.FFMPEG),
                ConversionResult.success("file2", Paths.get("/output2.mp4"), null, Duration.ofSeconds(40), 2000L, 1000L,
                        ConversionTool.FFMPEG));
        Duration totalTime = Duration.ofMinutes(2);

        // Act
        BatchConversionResult batch = BatchConversionResult.from(results, totalTime);

        // Assert
        assertEquals(2, batch.successCount());
        assertEquals(0, batch.failureCount());
        assertEquals(3000L, batch.totalInputSize());
        assertEquals(1500L, batch.totalOutputSize());
    }

    @Test
    void from_WithMixedResults_CalculatesCorrectly() {
        // Arrange
        List<ConversionResult> results = List.of(
                ConversionResult.success("file1", Paths.get("/output1.mp4"), null, Duration.ofSeconds(30), 1000L, 500L,
                        ConversionTool.FFMPEG),
                ConversionResult.failure("file2", "Conversion failed", null, Duration.ofSeconds(10), 2000L,
                        ConversionTool.FFMPEG));
        Duration totalTime = Duration.ofMinutes(1);

        // Act
        BatchConversionResult batch = BatchConversionResult.from(results, totalTime);

        // Assert
        assertEquals(1, batch.successCount());
        assertEquals(1, batch.failureCount());
        assertEquals(3000L, batch.totalInputSize());
        assertEquals(500L, batch.totalOutputSize());
    }

    @Test
    void results_ReturnsUnmodifiableList() {
        // Arrange
        List<ConversionResult> results = createSampleResults();
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofMinutes(5));

        // Act
        List<ConversionResult> returnedResults = batch.results();

        // Assert
        assertNotNull(returnedResults);
        assertEquals(3, returnedResults.size());
        assertThrows(UnsupportedOperationException.class, () -> returnedResults.clear());
    }

    @Test
    void totalCount_ReturnsCorrectCount() {
        // Arrange
        List<ConversionResult> results = createSampleResults();
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofMinutes(5));

        // Act & Assert
        assertEquals(3, batch.totalCount());
    }

    @Test
    void totalSpaceSaved_WithCompression_ReturnsPositiveValue() {
        // Arrange
        List<ConversionResult> results = List.of(
                ConversionResult.success("file1", Paths.get("/output1.mp4"), null, Duration.ofSeconds(30), 1000L, 500L,
                        ConversionTool.FFMPEG));
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofMinutes(1));

        // Act
        long spaceSaved = batch.totalSpaceSaved();

        // Assert
        assertEquals(500L, spaceSaved);
    }

    @Test
    void totalSpaceSaved_WithExpansion_ReturnsNegativeValue() {
        // Arrange
        List<ConversionResult> results = List.of(
                ConversionResult.success("file1", Paths.get("/output1.wav"), null, Duration.ofSeconds(30), 500L, 1000L,
                        ConversionTool.FFMPEG));
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofMinutes(1));

        // Act
        long spaceSaved = batch.totalSpaceSaved();

        // Assert
        assertEquals(-500L, spaceSaved);
    }

    @Test
    void overallCompressionRatio_WithCompression_ReturnsPositivePercentage() {
        // Arrange
        List<ConversionResult> results = List.of(
                ConversionResult.success("file1", Paths.get("/output1.mp4"), null, Duration.ofSeconds(30), 1000L, 500L,
                        ConversionTool.FFMPEG));
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofMinutes(1));

        // Act
        double ratio = batch.overallCompressionRatio();

        // Assert
        assertEquals(50.0, ratio, 0.01);
    }

    @Test
    void overallCompressionRatio_WithZeroInput_ReturnsZero() {
        // Arrange
        BatchConversionResult batch = new BatchConversionResult(
                List.of(), Duration.ofMinutes(1), 0, 0, 0L, 0L);

        // Act
        double ratio = batch.overallCompressionRatio();

        // Assert
        assertEquals(0.0, ratio);
    }

    @Test
    void averageConversionSpeed_WithNormalConversion_ReturnsCorrectSpeed() {
        // Arrange
        List<ConversionResult> results = List.of(
                ConversionResult.success("file1", Paths.get("/output1.mp4"), null, Duration.ofSeconds(10), 1000L, 500L,
                        ConversionTool.FFMPEG));
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofSeconds(10));

        // Act
        double speed = batch.averageConversionSpeed();

        // Assert
        assertEquals(100.0, speed, 0.01); // 1000 bytes / 10 seconds = 100 bytes/sec
    }

    @Test
    void averageConversionSpeed_WithZeroTime_ReturnsZero() {
        // Arrange
        List<ConversionResult> results = List.of(
                ConversionResult.success("file1", Paths.get("/output1.mp4"), null, Duration.ZERO, 1000L, 500L,
                        ConversionTool.FFMPEG));
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ZERO);

        // Act
        double speed = batch.averageConversionSpeed();

        // Assert
        assertEquals(0.0, speed);
    }

    @Test
    void formatAverageSpeed_WithBytes_ReturnsCorrectFormat() {
        // Arrange
        List<ConversionResult> results = List.of(
                ConversionResult.success("file1", Paths.get("/output1.mp4"), null, Duration.ofSeconds(10), 500L, 250L,
                        ConversionTool.FFMPEG));
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofSeconds(10));

        // Act
        String formatted = batch.formatAverageSpeed();

        // Assert
        assertTrue(formatted.endsWith("B/s"));
    }

    @Test
    void formatAverageSpeed_WithKilobytes_ReturnsCorrectFormat() {
        // Arrange
        List<ConversionResult> results = List.of(
                ConversionResult.success("file1", Paths.get("/output1.mp4"), null, Duration.ofSeconds(1), 10240L, 5120L,
                        ConversionTool.FFMPEG));
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofSeconds(1));

        // Act
        String formatted = batch.formatAverageSpeed();

        // Assert
        assertTrue(formatted.endsWith("KB/s"));
    }

    @Test
    void formatAverageSpeed_WithMegabytes_ReturnsCorrectFormat() {
        // Arrange
        List<ConversionResult> results = List.of(
                ConversionResult.success("file1", Paths.get("/output1.mp4"), null, Duration.ofSeconds(1), 10485760L,
                        5242880L, ConversionTool.FFMPEG));
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofSeconds(1));

        // Act
        String formatted = batch.formatAverageSpeed();

        // Assert
        assertTrue(formatted.endsWith("MB/s"));
    }

    @Test
    void formatAverageSpeed_WithGigabytes_ReturnsCorrectFormat() {
        // Arrange
        List<ConversionResult> results = List.of(
                ConversionResult.success("file1", Paths.get("/output1.mp4"), null, Duration.ofSeconds(1), 10737418240L,
                        5368709120L, ConversionTool.FFMPEG));
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofSeconds(1));

        // Act
        String formatted = batch.formatAverageSpeed();

        // Assert
        assertTrue(formatted.endsWith("GB/s"));
    }

    @Test
    void failures_ReturnsOnlyFailedResults() {
        // Arrange
        List<ConversionResult> results = createSampleResults();
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofMinutes(5));

        // Act
        List<ConversionResult> failures = batch.failures();

        // Assert
        assertEquals(1, failures.size());
        assertFalse(failures.get(0).success());
    }

    @Test
    void successes_ReturnsOnlySuccessfulResults() {
        // Arrange
        List<ConversionResult> results = createSampleResults();
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofMinutes(5));

        // Act
        List<ConversionResult> successes = batch.successes();

        // Assert
        assertEquals(2, successes.size());
        assertTrue(successes.stream().allMatch(ConversionResult::success));
    }

    @Test
    void allSucceeded_WithAllSuccesses_ReturnsTrue() {
        // Arrange
        List<ConversionResult> results = List.of(
                ConversionResult.success("file1", Paths.get("/output1.mp4"), null, Duration.ofSeconds(30), 1000L, 500L,
                        ConversionTool.FFMPEG),
                ConversionResult.success("file2", Paths.get("/output2.mp4"), null, Duration.ofSeconds(40), 2000L, 1000L,
                        ConversionTool.FFMPEG));
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofMinutes(2));

        // Act & Assert
        assertTrue(batch.allSucceeded());
    }

    @Test
    void allSucceeded_WithFailures_ReturnsFalse() {
        // Arrange
        List<ConversionResult> results = createSampleResults();
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofMinutes(5));

        // Act & Assert
        assertFalse(batch.allSucceeded());
    }

    @Test
    void anySucceeded_WithAtLeastOneSuccess_ReturnsTrue() {
        // Arrange
        List<ConversionResult> results = createSampleResults();
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofMinutes(5));

        // Act & Assert
        assertTrue(batch.anySucceeded());
    }

    @Test
    void anySucceeded_WithNoSuccesses_ReturnsFalse() {
        // Arrange
        List<ConversionResult> results = List.of(
                ConversionResult.failure("file1", "Error 1", null, Duration.ofSeconds(10), 1000L,
                        ConversionTool.FFMPEG),
                ConversionResult.failure("file2", "Error 2", null, Duration.ofSeconds(10), 2000L,
                        ConversionTool.FFMPEG));
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofMinutes(1));

        // Act & Assert
        assertFalse(batch.anySucceeded());
    }

    @Test
    void formatTotalTime_WithHours_ReturnsCorrectFormat() {
        // Arrange
        List<ConversionResult> results = createSampleResults();
        BatchConversionResult batch = BatchConversionResult.from(results,
                Duration.ofHours(2).plusMinutes(30).plusSeconds(45));

        // Act
        String formatted = batch.formatTotalTime();

        // Assert
        assertEquals("2h 30m 45s", formatted);
    }

    @Test
    void formatTotalTime_WithMinutes_ReturnsCorrectFormat() {
        // Arrange
        List<ConversionResult> results = createSampleResults();
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofMinutes(5).plusSeconds(30));

        // Act
        String formatted = batch.formatTotalTime();

        // Assert
        assertEquals("5m 30s", formatted);
    }

    @Test
    void formatTotalTime_WithOnlySeconds_ReturnsCorrectFormat() {
        // Arrange
        List<ConversionResult> results = createSampleResults();
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofSeconds(45));

        // Act
        String formatted = batch.formatTotalTime();

        // Assert
        assertEquals("45s", formatted);
    }

    @Test
    void formatSpaceSaved_WithSavings_ReturnsCorrectFormat() {
        // Arrange
        List<ConversionResult> results = List.of(
                ConversionResult.success("file1", Paths.get("/output1.mp4"), null, Duration.ofSeconds(30), 1048576L,
                        524288L, ConversionTool.FFMPEG));
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofMinutes(1));

        // Act
        String formatted = batch.formatSpaceSaved();

        // Assert
        assertTrue(formatted.contains("saved"));
        assertTrue(formatted.contains("KB"));
    }

    @Test
    void formatSpaceSaved_WithIncrease_ReturnsCorrectFormat() {
        // Arrange
        List<ConversionResult> results = List.of(
                ConversionResult.success("file1", Paths.get("/output1.wav"), null, Duration.ofSeconds(30), 524288L,
                        1048576L, ConversionTool.FFMPEG));
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofMinutes(1));

        // Act
        String formatted = batch.formatSpaceSaved();

        // Assert
        assertTrue(formatted.contains("increase"));
    }

    @Test
    void equals_WithSameValues_ReturnsTrue() {
        // Arrange
        List<ConversionResult> results = createSampleResults();
        BatchConversionResult batch1 = new BatchConversionResult(
                results, Duration.ofMinutes(5), 2, 1, 3000L, 1500L);
        BatchConversionResult batch2 = new BatchConversionResult(
                new ArrayList<>(results), Duration.ofMinutes(5), 2, 1, 3000L, 1500L);

        // Act & Assert
        assertEquals(batch1, batch2);
        assertEquals(batch1.hashCode(), batch2.hashCode());
    }

    @Test
    void equals_WithDifferentValues_ReturnsFalse() {
        // Arrange
        List<ConversionResult> results = createSampleResults();
        BatchConversionResult batch1 = new BatchConversionResult(
                results, Duration.ofMinutes(5), 2, 1, 3000L, 1500L);
        BatchConversionResult batch2 = new BatchConversionResult(
                results, Duration.ofMinutes(6), 2, 1, 3000L, 1500L);

        // Act & Assert
        assertNotEquals(batch1, batch2);
    }

    @Test
    void toString_ContainsKeyInformation() {
        // Arrange
        List<ConversionResult> results = createSampleResults();
        BatchConversionResult batch = BatchConversionResult.from(results, Duration.ofMinutes(5));

        // Act
        String str = batch.toString();

        // Assert
        assertTrue(str.contains("BatchConversionResult"));
        assertTrue(str.contains("totalCount="));
        assertTrue(str.contains("successCount="));
        assertTrue(str.contains("failureCount="));
    }

    // Helper methods

    private List<ConversionResult> createSampleResults() {
        return List.of(
                ConversionResult.success("file1", Paths.get("/output1.mp4"), null, Duration.ofSeconds(30), 1000L, 500L,
                        ConversionTool.FFMPEG),
                ConversionResult.success("file2", Paths.get("/output2.mp4"), null, Duration.ofSeconds(40), 2000L, 1000L,
                        ConversionTool.FFMPEG),
                ConversionResult.failure("file3", "Conversion failed", null, Duration.ofSeconds(10), 0L,
                        ConversionTool.FFMPEG));
    }
}
