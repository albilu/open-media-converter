// filepath: src/test/java/org/omc/model/ConversionProgressTest.java

package org.omc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Comprehensive tests for ConversionProgress model.
 * Tests constructor, factory methods, update logic, formatting, and
 * serialization.
 * 
 * Requirement: REQ-004.3 - Progress tracking and reporting
 */
@DisplayName("ConversionProgress Tests")
class ConversionProgressTest {

    private static final String TEST_FILE_ID = "file-123";
    private static final long TEST_TOTAL_BYTES = 1000000L;
    private static final Instant TEST_START_TIME = Instant.parse("2025-11-04T10:00:00Z");

    // ==================== Constructor Tests ====================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Constructor creates progress with all fields")
        void constructor_WithAllFields_CreatesProgress() {
            long processedBytes = 500000L;
            int percentage = 50;
            Duration elapsed = Duration.ofSeconds(30);
            Duration eta = Duration.ofSeconds(30);
            double speed = 16666.67;

            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID,
                    TEST_TOTAL_BYTES,
                    processedBytes,
                    percentage,
                    TEST_START_TIME,
                    elapsed,
                    eta,
                    speed);

            assertNotNull(progress);
            assertEquals(TEST_FILE_ID, progress.fileId());
            assertEquals(TEST_TOTAL_BYTES, progress.totalBytes());
            assertEquals(processedBytes, progress.processedBytes());
            assertEquals(percentage, progress.percentage());
            assertEquals(TEST_START_TIME, progress.startTime());
            assertEquals(elapsed, progress.elapsedTime());
            assertEquals(eta, progress.estimatedTimeRemaining());
            assertEquals(speed, progress.bytesPerSecond(), 0.01);
        }

        @Test
        @DisplayName("Constructor accepts zero values")
        void constructor_WithZeroValues_CreatesProgress() {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID,
                    0L,
                    0L,
                    0,
                    TEST_START_TIME,
                    Duration.ZERO,
                    Duration.ZERO,
                    0.0);

            assertNotNull(progress);
            assertEquals(0L, progress.totalBytes());
            assertEquals(0L, progress.processedBytes());
            assertEquals(0, progress.percentage());
            assertEquals(0.0, progress.bytesPerSecond());
        }

        @Test
        @DisplayName("Constructor accepts null fileId")
        void constructor_WithNullFileId_CreatesProgress() {
            ConversionProgress progress = new ConversionProgress(
                    null,
                    TEST_TOTAL_BYTES,
                    0L,
                    0,
                    TEST_START_TIME,
                    Duration.ZERO,
                    Duration.ZERO,
                    0.0);

            assertNotNull(progress);
            assertNull(progress.fileId());
        }

        @Test
        @DisplayName("Constructor accepts 100% completion")
        void constructor_WithFullCompletion_CreatesProgress() {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID,
                    TEST_TOTAL_BYTES,
                    TEST_TOTAL_BYTES,
                    100,
                    TEST_START_TIME,
                    Duration.ofMinutes(5),
                    Duration.ZERO,
                    3333.33);

            assertNotNull(progress);
            assertEquals(100, progress.percentage());
            assertEquals(TEST_TOTAL_BYTES, progress.processedBytes());
        }

        @Test
        @DisplayName("Constructor accepts negative duration for ETA")
        void constructor_WithNegativeDuration_CreatesProgress() {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID,
                    TEST_TOTAL_BYTES,
                    500000L,
                    50,
                    TEST_START_TIME,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(-10),
                    16666.67);

            assertNotNull(progress);
            assertEquals(Duration.ofSeconds(-10), progress.estimatedTimeRemaining());
        }
    }

    // ==================== Factory Method Tests ====================

    @Nested
    @DisplayName("initial() Factory Method Tests")
    class InitialFactoryTests {

        @Test
        @DisplayName("initial() creates progress with zero processed bytes")
        void initial_WithValidParams_CreatesInitialProgress() {
            ConversionProgress progress = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);

            assertNotNull(progress);
            assertEquals(TEST_FILE_ID, progress.fileId());
            assertEquals(TEST_TOTAL_BYTES, progress.totalBytes());
            assertEquals(0L, progress.processedBytes());
            assertEquals(0, progress.percentage());
            assertNotNull(progress.startTime());
            assertEquals(Duration.ZERO, progress.elapsedTime());
            assertEquals(Duration.ZERO, progress.estimatedTimeRemaining());
            assertEquals(0.0, progress.bytesPerSecond());
        }

        @Test
        @DisplayName("initial() creates progress with current timestamp")
        void initial_CreatesProgressWithCurrentTime() {
            Instant before = Instant.now();
            ConversionProgress progress = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Instant after = Instant.now();

            assertNotNull(progress.startTime());
            assertFalse(progress.startTime().isBefore(before));
            assertFalse(progress.startTime().isAfter(after));
        }

        @Test
        @DisplayName("initial() accepts zero total bytes")
        void initial_WithZeroTotalBytes_CreatesProgress() {
            ConversionProgress progress = ConversionProgress.initial(TEST_FILE_ID, 0L);

            assertNotNull(progress);
            assertEquals(0L, progress.totalBytes());
            assertEquals(0, progress.percentage());
        }

        @Test
        @DisplayName("initial() accepts null fileId")
        void initial_WithNullFileId_CreatesProgress() {
            ConversionProgress progress = ConversionProgress.initial(null, TEST_TOTAL_BYTES);

            assertNotNull(progress);
            assertNull(progress.fileId());
        }
    }

    // ==================== update() Method Tests ====================

    @Nested
    @DisplayName("update() Method Tests")
    class UpdateTests {

        @Test
        @DisplayName("update() calculates percentage correctly")
        void update_WithProcessedBytes_CalculatesPercentage() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(10); // Small delay to ensure elapsed time > 0

            ConversionProgress updated = initial.update(500000L);

            assertEquals(50, updated.percentage());
            assertEquals(500000L, updated.processedBytes());
        }

        @Test
        @DisplayName("update() calculates speed based on elapsed time")
        void update_AfterDelay_CalculatesSpeed() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(100); // 100ms delay

            ConversionProgress updated = initial.update(100000L);

            assertTrue(updated.bytesPerSecond() > 0);
            assertTrue(updated.elapsedTime().toMillis() >= 100);
        }

        @Test
        @DisplayName("update() clamps percentage to 0-100 range")
        void update_WithExcessiveBytes_ClampsPercentage() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(10);

            ConversionProgress updated = initial.update(TEST_TOTAL_BYTES + 500000L);

            assertEquals(100, updated.percentage());
        }

        @Test
        @DisplayName("update() handles zero processed bytes")
        void update_WithZeroBytes_CreatesValidProgress() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(10);

            ConversionProgress updated = initial.update(0L);

            assertEquals(0, updated.percentage());
            assertEquals(0L, updated.processedBytes());
        }

        @Test
        @DisplayName("update() calculates ETA correctly")
        void update_WithProgress_CalculatesETA() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(200); // Longer sleep to ensure meaningful ETA calculation

            ConversionProgress updated = initial.update(250000L); // 25% done

            assertNotNull(updated.estimatedTimeRemaining());
            // ETA should be non-negative when progress > 0
            assertTrue(updated.estimatedTimeRemaining().toMillis() >= 0);
            // Speed should be positive when bytes processed > 0 and time elapsed > 0
            assertTrue(updated.bytesPerSecond() > 0, "Speed should be positive when progress is made");
        }

        @Test
        @DisplayName("update() preserves fileId and totalBytes")
        void update_PreservesOriginalFields() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(10);

            ConversionProgress updated = initial.update(500000L);

            assertEquals(TEST_FILE_ID, updated.fileId());
            assertEquals(TEST_TOTAL_BYTES, updated.totalBytes());
            assertEquals(initial.startTime(), updated.startTime());
        }

        @Test
        @DisplayName("update() handles completion")
        void update_WithFullBytes_ShowsCompletion() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(10);

            ConversionProgress updated = initial.update(TEST_TOTAL_BYTES);

            assertEquals(100, updated.percentage());
            assertEquals(TEST_TOTAL_BYTES, updated.processedBytes());
        }

        @Test
        @DisplayName("update() handles zero total bytes")
        void update_WithZeroTotalBytes_ReturnsZeroPercentage() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, 0L);
            Thread.sleep(10);

            ConversionProgress updated = initial.update(0L);

            assertEquals(0, updated.percentage());
        }
    }

    // ==================== updateWithPercentage() Method Tests ====================

    @Nested
    @DisplayName("updateWithPercentage() Method Tests")
    class UpdateWithPercentageTests {

        @Test
        @DisplayName("updateWithPercentage() sets percentage directly")
        void updateWithPercentage_WithValidPercentage_SetsValue() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(10);

            ConversionProgress updated = initial.updateWithPercentage(75.0);

            assertEquals(75, updated.percentage());
        }

        @Test
        @DisplayName("updateWithPercentage() calculates processed bytes from percentage")
        void updateWithPercentage_CalculatesProcessedBytes() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(10);

            ConversionProgress updated = initial.updateWithPercentage(50.0);

            assertEquals(500000L, updated.processedBytes());
        }

        @Test
        @DisplayName("updateWithPercentage() clamps percentage above 100")
        void updateWithPercentage_WithExcessivePercentage_ClampsTo100() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(10);

            ConversionProgress updated = initial.updateWithPercentage(150.0);

            assertEquals(100, updated.percentage());
        }

        @Test
        @DisplayName("updateWithPercentage() clamps negative percentage to 0")
        void updateWithPercentage_WithNegativePercentage_ClampsToZero() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(10);

            ConversionProgress updated = initial.updateWithPercentage(-10.0);

            assertEquals(0, updated.percentage());
        }

        @Test
        @DisplayName("updateWithPercentage() calculates speed and ETA")
        void updateWithPercentage_CalculatesSpeedAndETA() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(100);

            ConversionProgress updated = initial.updateWithPercentage(25.0);

            assertTrue(updated.bytesPerSecond() > 0);
            assertTrue(updated.estimatedTimeRemaining().toMillis() >= 0);
        }

        @Test
        @DisplayName("updateWithPercentage() handles fractional percentages")
        void updateWithPercentage_WithFractionalPercentage_RoundsDown() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(10);

            ConversionProgress updated = initial.updateWithPercentage(33.7);

            assertEquals(33, updated.percentage());
        }

        @Test
        @DisplayName("updateWithPercentage() preserves fileId and totalBytes")
        void updateWithPercentage_PreservesOriginalFields() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(10);

            ConversionProgress updated = initial.updateWithPercentage(60.0);

            assertEquals(TEST_FILE_ID, updated.fileId());
            assertEquals(TEST_TOTAL_BYTES, updated.totalBytes());
            assertEquals(initial.startTime(), updated.startTime());
        }
    }

    // ==================== isComplete() Method Tests ====================

    @Nested
    @DisplayName("isComplete() Method Tests")
    class IsCompleteTests {

        @Test
        @DisplayName("isComplete() returns false for zero progress")
        void isComplete_WithZeroProgress_ReturnsFalse() {
            ConversionProgress progress = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);

            assertFalse(progress.isComplete());
        }

        @Test
        @DisplayName("isComplete() returns false for partial progress")
        void isComplete_WithPartialProgress_ReturnsFalse() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(10);
            ConversionProgress progress = initial.update(500000L);

            assertFalse(progress.isComplete());
        }

        @Test
        @DisplayName("isComplete() returns true when processed equals total")
        void isComplete_WithFullProgress_ReturnsTrue() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(10);
            ConversionProgress progress = initial.update(TEST_TOTAL_BYTES);

            assertTrue(progress.isComplete());
        }

        @Test
        @DisplayName("isComplete() returns true when processed exceeds total")
        void isComplete_WithExcessProgress_ReturnsTrue() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(10);
            ConversionProgress progress = initial.update(TEST_TOTAL_BYTES + 100000L);

            assertTrue(progress.isComplete());
        }

        @Test
        @DisplayName("isComplete() returns false when totalBytes is zero")
        void isComplete_WithZeroTotalBytes_ReturnsFalse() {
            ConversionProgress progress = ConversionProgress.initial(TEST_FILE_ID, 0L);

            assertFalse(progress.isComplete());
        }
    }

    // ==================== formatSpeed() Method Tests ====================

    @Nested
    @DisplayName("formatSpeed() Method Tests")
    class FormatSpeedTests {

        @Test
        @DisplayName("formatSpeed() formats bytes per second")
        void formatSpeed_WithBytesPerSecond_FormatsCorrectly() {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 0L, 0,
                    TEST_START_TIME, Duration.ZERO, Duration.ZERO, 512.5);

            assertEquals("512.5 B/s", progress.formatSpeed());
        }

        @Test
        @DisplayName("formatSpeed() formats kilobytes per second")
        void formatSpeed_WithKilobytesPerSecond_FormatsCorrectly() {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 0L, 0,
                    TEST_START_TIME, Duration.ZERO, Duration.ZERO, 51200.0);

            assertEquals("50.0 KB/s", progress.formatSpeed());
        }

        @Test
        @DisplayName("formatSpeed() formats megabytes per second")
        void formatSpeed_WithMegabytesPerSecond_FormatsCorrectly() {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 0L, 0,
                    TEST_START_TIME, Duration.ZERO, Duration.ZERO, 5242880.0);

            assertEquals("5.0 MB/s", progress.formatSpeed());
        }

        @Test
        @DisplayName("formatSpeed() formats gigabytes per second")
        void formatSpeed_WithGigabytesPerSecond_FormatsCorrectly() {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 0L, 0,
                    TEST_START_TIME, Duration.ZERO, Duration.ZERO, 5368709120.0);

            assertEquals("5.0 GB/s", progress.formatSpeed());
        }

        @ParameterizedTest
        @CsvSource({
                "0.0, 0.0 B/s",
                "1023.0, 1023.0 B/s",
                "1024.0, 1.0 KB/s",
                "1536.0, 1.5 KB/s",
                "1048576.0, 1.0 MB/s",
                "1572864.0, 1.5 MB/s",
                "1073741824.0, 1.0 GB/s"
        })
        @DisplayName("formatSpeed() formats various speeds correctly")
        void formatSpeed_WithVariousSpeeds_FormatsCorrectly(double speed, String expected) {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 0L, 0,
                    TEST_START_TIME, Duration.ZERO, Duration.ZERO, speed);

            assertEquals(expected, progress.formatSpeed());
        }
    }

    // ==================== formatEta() Method Tests ====================

    @Nested
    @DisplayName("formatEta() Method Tests")
    class FormatEtaTests {

        @Test
        @DisplayName("formatEta() returns 'Unknown' for zero duration")
        void formatEta_WithZeroDuration_ReturnsUnknown() {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 0L, 0,
                    TEST_START_TIME, Duration.ZERO, Duration.ZERO, 0.0);

            assertEquals("Unknown", progress.formatEta());
        }

        @Test
        @DisplayName("formatEta() returns 'Unknown' for negative duration")
        void formatEta_WithNegativeDuration_ReturnsUnknown() {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 0L, 0,
                    TEST_START_TIME, Duration.ZERO, Duration.ofSeconds(-30), 0.0);

            assertEquals("Unknown", progress.formatEta());
        }

        @Test
        @DisplayName("formatEta() formats seconds only")
        void formatEta_WithSecondsOnly_FormatsCorrectly() {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 0L, 0,
                    TEST_START_TIME, Duration.ZERO, Duration.ofSeconds(45), 0.0);

            assertEquals("45s", progress.formatEta());
        }

        @Test
        @DisplayName("formatEta() formats minutes and seconds")
        void formatEta_WithMinutesAndSeconds_FormatsCorrectly() {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 0L, 0,
                    TEST_START_TIME, Duration.ZERO, Duration.ofSeconds(125), 0.0);

            assertEquals("2m 5s", progress.formatEta());
        }

        @Test
        @DisplayName("formatEta() formats hours, minutes, and seconds")
        void formatEta_WithHoursMinutesSeconds_FormatsCorrectly() {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 0L, 0,
                    TEST_START_TIME, Duration.ZERO, Duration.ofSeconds(7325), 0.0);

            assertEquals("2h 2m 5s", progress.formatEta());
        }

        @ParameterizedTest
        @CsvSource({
                "1, 1s",
                "59, 59s",
                "60, 1m 0s",
                "61, 1m 1s",
                "3600, 1h 0m 0s",
                "3661, 1h 1m 1s",
                "86400, 24h 0m 0s"
        })
        @DisplayName("formatEta() formats various durations correctly")
        void formatEta_WithVariousDurations_FormatsCorrectly(long seconds, String expected) {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 0L, 0,
                    TEST_START_TIME, Duration.ZERO, Duration.ofSeconds(seconds), 0.0);

            assertEquals(expected, progress.formatEta());
        }
    }

    // ==================== equals() and hashCode() Tests ====================

    @Nested
    @DisplayName("equals() and hashCode() Tests")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("equals() returns true for same instance")
        void equals_WithSameInstance_ReturnsTrue() {
            ConversionProgress progress = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);

            assertEquals(progress, progress);
        }

        @Test
        @DisplayName("equals() returns true for identical progress objects")
        void equals_WithIdenticalObjects_ReturnsTrue() {
            ConversionProgress progress1 = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 500000L, 50,
                    TEST_START_TIME, Duration.ofSeconds(30), Duration.ofSeconds(30), 16666.67);
            ConversionProgress progress2 = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 500000L, 50,
                    TEST_START_TIME, Duration.ofSeconds(30), Duration.ofSeconds(30), 16666.67);

            assertEquals(progress1, progress2);
            assertEquals(progress1.hashCode(), progress2.hashCode());
        }

        @Test
        @DisplayName("equals() returns false for different fileId")
        void equals_WithDifferentFileId_ReturnsFalse() {
            ConversionProgress progress1 = new ConversionProgress(
                    "file-1", TEST_TOTAL_BYTES, 500000L, 50,
                    TEST_START_TIME, Duration.ofSeconds(30), Duration.ofSeconds(30), 16666.67);
            ConversionProgress progress2 = new ConversionProgress(
                    "file-2", TEST_TOTAL_BYTES, 500000L, 50,
                    TEST_START_TIME, Duration.ofSeconds(30), Duration.ofSeconds(30), 16666.67);

            assertNotEquals(progress1, progress2);
        }

        @Test
        @DisplayName("equals() returns false for different processedBytes")
        void equals_WithDifferentProcessedBytes_ReturnsFalse() {
            ConversionProgress progress1 = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 500000L, 50,
                    TEST_START_TIME, Duration.ofSeconds(30), Duration.ofSeconds(30), 16666.67);
            ConversionProgress progress2 = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 600000L, 60,
                    TEST_START_TIME, Duration.ofSeconds(30), Duration.ofSeconds(30), 16666.67);

            assertNotEquals(progress1, progress2);
        }

        @Test
        @DisplayName("equals() returns false for null")
        void equals_WithNull_ReturnsFalse() {
            ConversionProgress progress = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);

            assertNotEquals(progress, null);
        }

        @Test
        @DisplayName("equals() returns false for different class")
        void equals_WithDifferentClass_ReturnsFalse() {
            ConversionProgress progress = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);

            assertNotEquals(progress, "not a progress object");
        }

        @Test
        @DisplayName("hashCode() is consistent")
        void hashCode_IsConsistent() {
            ConversionProgress progress = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);

            int hash1 = progress.hashCode();
            int hash2 = progress.hashCode();

            assertEquals(hash1, hash2);
        }
    }

    // ==================== toString() Tests ====================

    @Nested
    @DisplayName("toString() Tests")
    class ToStringTests {

        @Test
        @DisplayName("toString() includes fileId")
        void toString_IncludesFileId() {
            ConversionProgress progress = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);

            String result = progress.toString();

            assertTrue(result.contains(TEST_FILE_ID));
        }

        @Test
        @DisplayName("toString() includes percentage")
        void toString_IncludesPercentage() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(10);
            ConversionProgress progress = initial.update(500000L);

            String result = progress.toString();

            assertTrue(result.contains("50"));
        }

        @Test
        @DisplayName("toString() includes formatted speed")
        void toString_IncludesSpeed() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(100);
            ConversionProgress progress = initial.update(100000L);

            String result = progress.toString();

            assertTrue(result.contains("/s"));
        }

        @Test
        @DisplayName("toString() includes formatted ETA")
        void toString_IncludesETA() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(100);
            ConversionProgress progress = initial.update(250000L);

            String result = progress.toString();

            assertTrue(result.contains("eta="));
        }

        @Test
        @DisplayName("toString() returns valid format")
        void toString_ReturnsValidFormat() {
            ConversionProgress progress = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);

            String result = progress.toString();

            assertTrue(result.startsWith("ConversionProgress{"));
            assertTrue(result.endsWith("}"));
        }
    }

    // ==================== JSON Serialization Tests ====================

    @Nested
    @DisplayName("JSON Serialization Tests")
    class JsonSerializationTests {

        private final ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());

        @Test
        @DisplayName("Progress serializes to JSON")
        void progress_SerializesToJson() throws Exception {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 500000L, 50,
                    TEST_START_TIME, Duration.ofSeconds(30), Duration.ofSeconds(30), 16666.67);

            String json = mapper.writeValueAsString(progress);

            assertNotNull(json);
            assertTrue(json.contains("\"fileId\""));
            assertTrue(json.contains("\"totalBytes\""));
            assertTrue(json.contains("\"processedBytes\""));
            assertTrue(json.contains("\"percentage\""));
        }

        @Test
        @DisplayName("Progress deserializes from JSON")
        void progress_DeserializesFromJson() throws Exception {
            String json = """
                    {
                        "fileId": "file-123",
                        "totalBytes": 1000000,
                        "processedBytes": 500000,
                        "percentage": 50,
                        "startTime": "2025-11-04T10:00:00Z",
                        "elapsedTime": 30.0,
                        "estimatedTimeRemaining": 30.0,
                        "bytesPerSecond": 16666.67
                    }
                    """;

            ConversionProgress progress = mapper.readValue(json, ConversionProgress.class);

            assertNotNull(progress);
            assertEquals("file-123", progress.fileId());
            assertEquals(1000000L, progress.totalBytes());
            assertEquals(500000L, progress.processedBytes());
            assertEquals(50, progress.percentage());
        }

        @Test
        @DisplayName("Serialization roundtrip preserves data")
        void progress_RoundtripPreservesData() throws Exception {
            ConversionProgress original = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 750000L, 75,
                    TEST_START_TIME, Duration.ofMinutes(2), Duration.ofSeconds(40), 6250.0);

            String json = mapper.writeValueAsString(original);
            ConversionProgress deserialized = mapper.readValue(json, ConversionProgress.class);

            assertEquals(original, deserialized);
        }
    }

    // ==================== Edge Case Tests ====================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Progress with very large byte values")
        void progress_WithLargeValues_HandlesCorrectly() {
            long largeValue = Long.MAX_VALUE / 2;
            ConversionProgress progress = ConversionProgress.initial(TEST_FILE_ID, largeValue);

            assertNotNull(progress);
            assertEquals(largeValue, progress.totalBytes());
        }

        @Test
        @DisplayName("Progress with very high speed")
        void progress_WithHighSpeed_FormatsCorrectly() {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 0L, 0,
                    TEST_START_TIME, Duration.ZERO, Duration.ZERO, 10_000_000_000.0);

            String formatted = progress.formatSpeed();
            assertTrue(formatted.contains("GB/s"));
        }

        @Test
        @DisplayName("Progress with very long ETA")
        void progress_WithLongETA_FormatsCorrectly() {
            ConversionProgress progress = new ConversionProgress(
                    TEST_FILE_ID, TEST_TOTAL_BYTES, 0L, 0,
                    TEST_START_TIME, Duration.ZERO, Duration.ofHours(100), 0.0);

            String formatted = progress.formatEta();
            assertTrue(formatted.contains("h"));
        }

        @Test
        @DisplayName("Multiple updates preserve startTime")
        void multipleUpdates_PreserveStartTime() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(50);
            ConversionProgress update1 = initial.update(250000L);
            Thread.sleep(50);
            ConversionProgress update2 = update1.update(500000L);
            Thread.sleep(50);
            ConversionProgress update3 = update2.update(750000L);

            assertEquals(initial.startTime(), update1.startTime());
            assertEquals(initial.startTime(), update2.startTime());
            assertEquals(initial.startTime(), update3.startTime());
        }

        @Test
        @DisplayName("Progress with empty fileId")
        void progress_WithEmptyFileId_CreatesValidObject() {
            ConversionProgress progress = ConversionProgress.initial("", TEST_TOTAL_BYTES);

            assertNotNull(progress);
            assertEquals("", progress.fileId());
        }

        @Test
        @DisplayName("Update immediately after initial has minimal elapsed time")
        void update_ImmediatelyAfterInitial_HasMinimalElapsedTime() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(1); // Minimal delay
            ConversionProgress updated = initial.update(10000L);

            assertTrue(updated.elapsedTime().toMillis() >= 0);
            assertTrue(updated.elapsedTime().toMillis() < 100);
        }
    }

    // ==================== Integration Tests ====================

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Simulate complete conversion workflow")
        void simulateCompleteConversionWorkflow() throws InterruptedException {
            // Start conversion
            ConversionProgress progress = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            assertFalse(progress.isComplete());
            assertEquals(0, progress.percentage());

            // 25% complete
            Thread.sleep(50);
            progress = progress.update(250000L);
            assertEquals(25, progress.percentage());
            assertFalse(progress.isComplete());
            assertTrue(progress.bytesPerSecond() > 0);

            // 50% complete
            Thread.sleep(50);
            progress = progress.update(500000L);
            assertEquals(50, progress.percentage());
            assertFalse(progress.isComplete());

            // 75% complete
            Thread.sleep(50);
            progress = progress.update(750000L);
            assertEquals(75, progress.percentage());
            assertFalse(progress.isComplete());

            // 100% complete
            Thread.sleep(50);
            progress = progress.update(TEST_TOTAL_BYTES);
            assertEquals(100, progress.percentage());
            assertTrue(progress.isComplete());
        }

        @Test
        @DisplayName("Compare update() vs updateWithPercentage() behavior")
        void compareUpdateMethods() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(100);

            ConversionProgress byBytes = initial.update(500000L);
            ConversionProgress byPercentage = initial.updateWithPercentage(50.0);

            assertEquals(byBytes.percentage(), byPercentage.percentage());
            assertEquals(byBytes.processedBytes(), byPercentage.processedBytes());
        }

        @Test
        @DisplayName("Format methods produce user-friendly output")
        void formatMethods_ProduceUserFriendlyOutput() throws InterruptedException {
            ConversionProgress initial = ConversionProgress.initial(TEST_FILE_ID, TEST_TOTAL_BYTES);
            Thread.sleep(100);
            ConversionProgress progress = initial.update(250000L);

            String speed = progress.formatSpeed();
            String eta = progress.formatEta();

            assertNotNull(speed);
            assertNotNull(eta);
            assertFalse(speed.isEmpty());
            assertFalse(eta.isEmpty());
            assertTrue(speed.matches(".*\\d+\\.\\d+\\s+[KMGT]?B/s"));
        }
    }
}
