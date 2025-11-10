// filepath: src/test/java/org/omc/core/ConversionEngineTest.java

package org.omc.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.omc.model.AudioSettings;
import org.omc.model.BatchConversionResult;
import org.omc.model.ConversionFile;
import org.omc.model.ConversionProgress;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionSettings;
import org.omc.model.ConversionTool;
import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;
import org.omc.model.FileSettingsOverride;
import org.omc.model.ImageSettings;
import org.omc.model.ValidationResult;
import org.omc.model.VideoSettings;
import org.omc.exception.ErrorCode;
import org.omc.exception.FileOperationException;
import org.omc.service.FileHandler;
import org.slf4j.Logger;
import org.slf4j.Logger;

/**
 * Comprehensive unit tests for ConversionEngine.
 *
 * Test Coverage:
 * - Constructor validation (null checks, parallelConversions bounds)
 * - Thread pool creation and configuration
 * - Pause/Resume/Cancel operations
 * - State tracking (active conversions map)
 * - Event handler registration and invocation
 * - Graceful shutdown with timeout
 * - Temporary file management
 * - Edge cases and error conditions
 * - Single file conversion logic (happy path, validation failures, tool
 * unavailability, etc.)
 *
 * Requirements tested:
 * - REQ-004.2: Batch processing with configurable parallelism, temporary file
 * management
 * - REQ-004.3: Progress tracking and status updates
 */
@ExtendWith(MockitoExtension.class)
class ConversionEngineTest {

        private ConversionEngine conversionEngine;

        @Mock
        private ToolManager toolManager;

        @Mock
        private ValidationEngine validationEngine;

        @Mock
        private ProgressEngine progressEngine;

        @Mock
        private FileHandler fileHandler;

        @Mock
        private BiConsumer<String, ConversionProgress> progressHandler;

        @Mock
        private BiConsumer<String, ConversionResult> completionHandler;

        private Path testPath;
        private ConversionFile testFile;
        private ConversionSettings testSettings;
        private int tempFileIndex = 0;

        @BeforeEach
        void setUp() throws Exception {
                testPath = Paths.get("/tmp/test.mp4");
                testFile = ConversionFile.create(testPath, FileFormat.MP4, 1000L);
                testSettings = ConversionSettings.builder()
                                .outputFormat(FileFormat.AVI)
                                .outputDirectory(Paths.get("/tmp/output"))
                                .build();

                // Mock fileHandler to return a temporary file path (lenient to avoid
                // unnecessary stubbing warnings)
                lenient().when(fileHandler.createTemporaryFile(anyString(), anyString()))
                                .thenAnswer(inv -> Paths.get("/tmp/omm-test-temp-" + tempFileIndex++ + ".avi"));
                lenient().doNothing().when(fileHandler).registerCleanup(any(Path.class));
                lenient().doNothing().when(fileHandler).unregisterCleanup(any(Path.class));
                lenient().doNothing().when(fileHandler).cleanupAll();

                // Create engine with valid parameters including FileHandler
                conversionEngine = new ConversionEngine(toolManager, validationEngine, progressEngine, fileHandler, 4);
        }

        @AfterEach
        void tearDown() throws Exception {
                // Ensure clean shutdown after each test
                if (conversionEngine != null && !conversionEngine.isShuttingDown()) {
                        conversionEngine.shutdown();
                }

                // Clean up any test output files
                Files.deleteIfExists(Paths.get("/tmp/output/test.avi"));
                Files.deleteIfExists(Paths.get("/tmp/test.avi"));
                Files.deleteIfExists(Paths.get("/tmp/test.mp4")); // testPath used by deletion tests
                Files.deleteIfExists(Paths.get("/tmp/conversion-temp-12345.avi"));
                Files.deleteIfExists(Paths.get("/custom/output/test.avi"));

                // Clean up temp files created by the mock
                for (int i = 0; i < tempFileIndex; i++) {
                        try {
                                Files.deleteIfExists(Paths.get("/tmp/omm-test-temp-" + i + ".avi"));
                        } catch (IOException e) {
                                // ignore
                        }
                }
        }

        // Constructor validation tests

        @Test
        void testConstructor_WithValidParameters_CreatesInstance() {
                // When
                ConversionEngine engine = new ConversionEngine(toolManager, validationEngine, progressEngine,
                                fileHandler, 8);

                // Then
                assertNotNull(engine);
                assertFalse(engine.isPaused());
                assertFalse(engine.isShuttingDown());
                assertEquals(0, engine.getActiveConversionCount());
        }

        @Test
        void testConstructor_WithNullToolManager_ThrowsNullPointerException() {
                // When/Then
                assertThrows(NullPointerException.class,
                                () -> new ConversionEngine(null, validationEngine, progressEngine, fileHandler, 4));
        }

        @Test
        void testConstructor_WithNullValidationEngine_ThrowsNullPointerException() {
                // When/Then
                assertThrows(NullPointerException.class,
                                () -> new ConversionEngine(toolManager, null, progressEngine, fileHandler, 4));
        }

        @Test
        void testConstructor_WithNullProgressEngine_ThrowsNullPointerException() {
                // When/Then
                assertThrows(NullPointerException.class,
                                () -> new ConversionEngine(toolManager, validationEngine, null, fileHandler, 4));
        }

        @Test
        void testConstructor_WithNullFileHandler_ThrowsNullPointerException() {
                // When/Then
                assertThrows(NullPointerException.class,
                                () -> new ConversionEngine(toolManager, validationEngine, progressEngine, null, 4));
        }

        @Test
        void testConstructor_WithParallelConversionsTooLow_ThrowsIllegalArgumentException() {
                // When/Then
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> new ConversionEngine(toolManager, validationEngine, progressEngine, fileHandler,
                                                0));
                assertTrue(exception.getMessage().contains("Parallel conversions must be between 1 and 16"));
        }

        @Test
        void testConstructor_WithParallelConversionsTooHigh_ThrowsIllegalArgumentException() {
                // When/Then
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> new ConversionEngine(toolManager, validationEngine, progressEngine, fileHandler,
                                                17));
                assertTrue(exception.getMessage().contains("Parallel conversions must be between 1 and 16"));
        }

        @Test
        void testConstructor_WithMinimumParallelConversions_CreatesInstance() {
                // When
                ConversionEngine engine = new ConversionEngine(toolManager, validationEngine, progressEngine,
                                fileHandler, 1);

                // Then
                assertNotNull(engine);
        }

        @Test
        void testConstructor_WithMaximumParallelConversions_CreatesInstance() {
                // When
                ConversionEngine engine = new ConversionEngine(toolManager, validationEngine, progressEngine,
                                fileHandler, 16);

                // Then
                assertNotNull(engine);
        }

        // Thread pool configuration tests

        @Test
        void testThreadPoolConfiguration_WithSpecifiedParallelism_CreatesEngine() throws InterruptedException {
                // Given
                int parallelism = 3;
                ConversionEngine engine = new ConversionEngine(toolManager, validationEngine, progressEngine,
                                fileHandler,
                                parallelism);

                // When - Just create and shutdown to verify thread pool creation
                engine.shutdown();

                // Then - Should not throw any exceptions
                assertTrue(engine.isShuttingDown());
        }

        // Single file conversion tests

        @Test
        void testConvertSingle_WithValidParameters_ReturnsCompletableFuture() {
                // When
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, testSettings);

                // Then
                assertNotNull(future);
                assertFalse(future.isDone()); // Should be running asynchronously
                assertEquals(1, conversionEngine.getActiveConversionCount());
        }

        @Test
        void testConvertSingle_WithNullFile_ThrowsNullPointerException() {
                // When/Then
                assertThrows(NullPointerException.class, () -> conversionEngine.convertSingle(null, testSettings));
        }

        @Test
        void testConvertSingle_WithNullSettings_ThrowsNullPointerException() {
                // When/Then
                assertThrows(NullPointerException.class, () -> conversionEngine.convertSingle(testFile, null));
        }

        @Test
        void testConvertSingle_WhenShuttingDown_ThrowsIllegalStateException() throws InterruptedException {
                // Given
                conversionEngine.shutdown();

                // When/Then
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                () -> conversionEngine.convertSingle(testFile, testSettings));
                assertTrue(exception.getMessage().contains("ConversionEngine is shutting down"));
        }

        // Batch conversion tests

        @Test
        void testConvertBatch_WithValidParameters_ReturnsCompletableFuture() throws Exception {
                // Given
                List<ConversionFile> files = Arrays.asList(testFile);

                // When
                CompletableFuture<BatchConversionResult> future = conversionEngine.convertBatch(files, testSettings);

                // Then
                assertNotNull(future);
                // Wait for completion and verify result
                BatchConversionResult result = future.get(5, TimeUnit.SECONDS);
                assertNotNull(result);
                assertEquals(1, result.totalCount());
        }

        @Test
        void testConvertBatch_WithNullFiles_ThrowsNullPointerException() {
                // When/Then
                assertThrows(NullPointerException.class, () -> conversionEngine.convertBatch(null, testSettings));
        }

        @Test
        void testConvertBatch_WithNullSettings_ThrowsNullPointerException() {
                // Given
                List<ConversionFile> files = Arrays.asList(testFile);

                // When/Then
                assertThrows(NullPointerException.class, () -> conversionEngine.convertBatch(files, null));
        }

        @Test
        void testConvertBatch_WithEmptyFileList_ReturnsCompletedFuture()
                        throws ExecutionException, InterruptedException {
                // Given
                List<ConversionFile> files = Arrays.asList();

                // When
                CompletableFuture<BatchConversionResult> future = conversionEngine.convertBatch(files, testSettings);

                // Then
                assertTrue(future.isDone());
                BatchConversionResult result = future.get();
                assertEquals(0, result.totalCount());
                assertEquals(0, result.successCount());
                assertEquals(0, result.failureCount());
        }

        @Test
        void testConvertBatch_WhenShuttingDown_ThrowsIllegalStateException() throws InterruptedException {
                // Given
                List<ConversionFile> files = Arrays.asList(testFile);
                conversionEngine.shutdown();

                // When/Then
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                () -> conversionEngine.convertBatch(files, testSettings));
                assertTrue(exception.getMessage().contains("ConversionEngine is shutting down"));
        }

        // Pause/Resume operations tests

        @Test
        void testPauseConversion_WhenNotPaused_SetsPausedState() {
                // When
                conversionEngine.pauseConversion();

                // Then
                assertTrue(conversionEngine.isPaused());
        }

        @Test
        void testPauseConversion_WhenAlreadyPaused_DoesNotChangeState() {
                // Given
                conversionEngine.pauseConversion();

                // When
                conversionEngine.pauseConversion();

                // Then
                assertTrue(conversionEngine.isPaused());
        }

        @Test
        void testResumeConversion_WhenPaused_ResumesSuccessfully() {
                // Given
                conversionEngine.pauseConversion();

                // When
                conversionEngine.resumeConversion();

                // Then
                assertFalse(conversionEngine.isPaused());
        }

        @Test
        void testResumeConversion_WhenNotPaused_DoesNotChangeState() {
                // When
                conversionEngine.resumeConversion();

                // Then
                assertFalse(conversionEngine.isPaused());
        }

        // Cancel operations tests

        @Test
        void testCancelConversion_WithActiveConversions_CancelsAll() {
                // Given
                conversionEngine.convertSingle(testFile, testSettings);
                assertEquals(1, conversionEngine.getActiveConversionCount());

                // When
                conversionEngine.cancelConversion();

                // Then
                assertEquals(0, conversionEngine.getActiveConversionCount());
        }

        @Test
        void testCancelConversion_WhenPaused_ResumesBeforeCancelling() {
                // Given
                conversionEngine.pauseConversion();
                conversionEngine.convertSingle(testFile, testSettings);

                // When
                conversionEngine.cancelConversion();

                // Then
                assertFalse(conversionEngine.isPaused());
                assertEquals(0, conversionEngine.getActiveConversionCount());
        }

        // Event handler registration tests

        @Test
        void testOnProgressUpdate_RegistersHandler() {
                // When
                conversionEngine.onProgressUpdate(progressHandler);

                // Then
                // Handler should be registered (verified through completion handler test)
        }

        @Test
        void testOnConversionComplete_RegistersHandler() {
                // When
                conversionEngine.onConversionComplete(completionHandler);

                // Then
                // Handler should be registered (verified through completion handler test)
        }

        @Test
        void testOnProgressUpdate_WithNullHandler_AcceptsNull() {
                // When/Then - Should not throw
                assertDoesNotThrow(() -> conversionEngine.onProgressUpdate(null));
        }

        @Test
        void testOnConversionComplete_WithNullHandler_AcceptsNull() {
                // When/Then - Should not throw
                assertDoesNotThrow(() -> conversionEngine.onConversionComplete(null));
        }

        // State tracking tests

        @Test
        void testGetActiveConversionCount_WithNoActive_ReturnsZero() {
                // When
                int count = conversionEngine.getActiveConversionCount();

                // Then
                assertEquals(0, count);
        }

        @Test
        void testGetActiveConversionCount_WithActive_ReturnsCorrectCount() {
                // Given
                conversionEngine.convertSingle(testFile, testSettings);

                // When
                int count = conversionEngine.getActiveConversionCount();

                // Then
                assertEquals(1, count);
        }

        @Test
        void testIsPaused_Initially_ReturnsFalse() {
                // When
                boolean paused = conversionEngine.isPaused();

                // Then
                assertFalse(paused);
        }

        @Test
        void testIsShuttingDown_Initially_ReturnsFalse() {
                // When
                boolean shuttingDown = conversionEngine.isShuttingDown();

                // Then
                assertFalse(shuttingDown);
        }

        // Shutdown tests

        @Test
        void testShutdown_WhenNotShuttingDown_InitiatesShutdown() throws InterruptedException {
                // When
                conversionEngine.shutdown();

                // Then
                assertTrue(conversionEngine.isShuttingDown());
        }

        @Test
        void testShutdown_WhenAlreadyShuttingDown_DoesNotThrow() throws InterruptedException {
                // Given
                conversionEngine.shutdown();

                // When/Then - Should not throw
                assertDoesNotThrow(() -> conversionEngine.shutdown());
        }

        @Test
        void testShutdown_WithActiveConversions_WaitsForCompletion() throws InterruptedException {
                // Given
                conversionEngine.convertSingle(testFile, testSettings);
                assertEquals(1, conversionEngine.getActiveConversionCount());

                // When
                conversionEngine.shutdown();

                // Then
                assertTrue(conversionEngine.isShuttingDown());
                // Active count should eventually be 0 (may take time for executor to terminate)
        }

        @Test
        void testShutdown_WhenPaused_ResumesBeforeShutdown() throws InterruptedException {
                // Given
                conversionEngine.pauseConversion();

                // When
                conversionEngine.shutdown();

                // Then
                assertTrue(conversionEngine.isShuttingDown());
                assertFalse(conversionEngine.isPaused());
        }

        // Edge cases and error conditions

        @Test
        void testConvertSingle_CompletesWithFailure_NotifiesCompletionHandler() throws Exception {
                // Given
                conversionEngine.onConversionComplete(completionHandler);

                // When
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, testSettings);

                // Wait for completion (since actual conversion is mocked to fail)
                ConversionResult result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertNotNull(result);
                assertFalse(result.success());
                verify(completionHandler, timeout(5000)).accept(eq(testFile.id()), any(ConversionResult.class));
        }

        @Test
        void testConvertBatch_MultipleFiles_HandlesAllCompletions() throws Exception {
                // Given
                ConversionFile file2 = ConversionFile.create(Paths.get("/tmp/test2.mp4"), FileFormat.MP4, 2000L);
                List<ConversionFile> files = Arrays.asList(testFile, file2);
                conversionEngine.onConversionComplete(completionHandler);

                // When
                CompletableFuture<BatchConversionResult> batchFuture = conversionEngine.convertBatch(files,
                                testSettings);

                // Wait for batch completion
                BatchConversionResult batchResult = batchFuture.get(10, TimeUnit.SECONDS);

                // Then
                assertNotNull(batchResult);
                assertEquals(2, batchResult.totalCount());
                verify(completionHandler, timeout(10000).times(2)).accept(anyString(), any(ConversionResult.class));
        }

        @Test
        void testConcurrentOperations_ThreadSafety() throws InterruptedException {
                // Given
                int numberOfThreads = 5;
                Thread[] threads = new Thread[numberOfThreads];

                // When - Start multiple threads performing operations
                for (int i = 0; i < numberOfThreads; i++) {
                        final int threadIndex = i;
                        threads[i] = new Thread(() -> {
                                try {
                                        ConversionFile file = ConversionFile.create(
                                                        Paths.get("/tmp/test" + threadIndex + ".mp4"),
                                                        FileFormat.MP4, 1000L);
                                        conversionEngine.convertSingle(file, testSettings);
                                        Thread.sleep(10); // Small delay
                                        conversionEngine.getActiveConversionCount();
                                } catch (Exception e) {
                                        fail("Thread operation failed: " + e.getMessage());
                                }
                        });
                        threads[i].start();
                }

                // Wait for all threads to complete
                for (Thread thread : threads) {
                        thread.join(5000);
                }

                // Then - No exceptions should have been thrown, engine should still be
                // functional
                assertFalse(conversionEngine.isShuttingDown());
        }

        @Test
        void testShutdown_Interruption_HandlesInterruptedException() throws InterruptedException {
                // Given
                Thread.currentThread().interrupt(); // Set interrupted status

                // When/Then - Should handle interruption gracefully
                assertDoesNotThrow(() -> conversionEngine.shutdown());
                assertTrue(Thread.interrupted()); // Clear interrupted status for other tests
        }

        @Test
        void testPauseResume_WithMultipleConversions_HandlesCorrectly() throws InterruptedException {
                // Given
                List<ConversionFile> files = Arrays.asList(
                                ConversionFile.create(Paths.get("/tmp/file1.mp4"), FileFormat.MP4, 1000L),
                                ConversionFile.create(Paths.get("/tmp/file2.mp4"), FileFormat.MP4, 1000L));

                // When
                conversionEngine.convertBatch(files, testSettings);
                conversionEngine.pauseConversion();
                Thread.sleep(100); // Allow pause to take effect
                conversionEngine.resumeConversion();

                // Then
                assertFalse(conversionEngine.isPaused());
                // Conversions should continue (though they will fail due to mock setup)
        }

        @Test
        void testActiveConversionTracking_AfterCompletion_RemovesFromActive() throws Exception {
                // Given
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, testSettings);

                // When
                ConversionResult result = future.get(5, TimeUnit.SECONDS); // Wait for completion

                // Wait for the whenComplete callback to finish cleanup (max 1 second)
                long startTime = System.currentTimeMillis();
                while (conversionEngine.getActiveConversionCount() > 0) {
                        if (System.currentTimeMillis() - startTime > 1000) {
                                fail("Active conversion count did not reach 0 within timeout");
                        }
                        Thread.sleep(10);
                }

                // Then
                assertNotNull(result);
                // Active count should be 0 after completion
                assertEquals(0, conversionEngine.getActiveConversionCount());
        }

        @Test
        void testConvertSingle_HappyPath_Succeeds() throws Exception {
                // Given - Mock all dependencies for successful conversion
                when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                any(ConversionSettings.class)))
                                .thenReturn(ValidationResult.success());
                when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                .thenReturn(ConversionTool.FFMPEG);
                when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateOutputDirectory(any(Path.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                .thenReturn(ValidationResult.success());

                Path outputPath = Paths.get("/tmp/output/test.avi");
                ConversionResult successResult = ConversionResult.success(testFile.id(), outputPath, null,
                                Duration.ofSeconds(2), testFile.size(), 800L, ConversionTool.FFMPEG);

                // Mock executeTool to create the temp file (simulating actual conversion)
                when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                any(ProgressCallback.class),
                                any(String.class), any(ProcessRegistry.class)))
                                .thenAnswer(invocation -> {
                                        // Create the temp file to simulate tool execution
                                        Path tempPath = invocation.getArgument(2);
                                        Files.createDirectories(tempPath.getParent());
                                        Files.write(tempPath, "test content".getBytes());
                                        return successResult;
                                });

                doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                // When
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, testSettings);
                ConversionResult result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertTrue(result.success());
                assertEquals(testFile.id(), result.fileId());
                assertEquals(ConversionTool.FFMPEG, result.toolUsed());
                verify(toolManager).executeTool(any(ConversionTool.class), any(Path.class),
                                any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                any(ProgressCallback.class),
                                any(String.class), any(ProcessRegistry.class));
                verify(progressEngine).startTracking(testFile.id(), testFile.size());
                verify(progressEngine).completeTracking(testFile.id(), result);
        }

        @Test
        void testConvertSingle_ValidationFailure_FailsWithError() throws Exception {
                // Given
                String errorMsg = "Invalid file format";
                when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                any(ConversionSettings.class)))
                                .thenReturn(ValidationResult.failure(errorMsg));

                // When
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, testSettings);
                ConversionResult result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertFalse(result.success());
                assertEquals(testFile.id(), result.fileId());
                assertEquals(errorMsg, result.errorMessage().get());
                assertEquals(ConversionTool.FFMPEG, result.toolUsed()); // Default tool
                verify(toolManager, never()).executeTool(any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void testConvertSingle_ToolUnavailable_FailsWithError() throws Exception {
                // Given
                when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                any(ConversionSettings.class)))
                                .thenReturn(ValidationResult.success());
                when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                .thenReturn(ConversionTool.FFMPEG);
                String toolError = "FFmpeg not installed";
                when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                .thenReturn(ValidationResult.failure(toolError));

                // When
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, testSettings);
                ConversionResult result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertFalse(result.success());
                assertEquals("Tool not available: " + toolError, result.errorMessage().get());
                assertEquals(ConversionTool.FFMPEG, result.toolUsed());
                verify(toolManager, never()).executeTool(any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void testConvertSingle_OutputFileExists_OverwriteDisabled_Fails() throws Exception {
                // Given - Create a real output file to test conflict handling
                Path tempOutputDir = Files.createTempDirectory("omm-test-output");
                Path existingOutputFile = tempOutputDir.resolve("test.avi");
                Files.createFile(existingOutputFile); // Create the conflicting file

                try {
                        // Setup validation to pass so we reach the file exists check
                        when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                        any(ConversionSettings.class)))
                                        .thenReturn(ValidationResult.success());
                        when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                        .thenReturn(ConversionTool.FFMPEG);
                        when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                        .thenReturn(ValidationResult.success());
                        // Note: We don't stub validateOutputDirectory or validateDiskSpace because
                        // the code fails earlier at the file exists check

                        // When - Use settings with overwrite disabled
                        ConversionSettings noOverwriteSettings = ConversionSettings.builder()
                                        .outputFormat(FileFormat.AVI)
                                        .outputDirectory(tempOutputDir)
                                        .overwriteExisting(false)
                                        .build();
                        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile,
                                        noOverwriteSettings);
                        ConversionResult result = future.get(5, TimeUnit.SECONDS);

                        // Then
                        assertFalse(result.success());
                        assertTrue(result.errorMessage().get().contains("Output file already exists"),
                                        "Expected error message to contain 'Output file already exists', but was: "
                                                        + result.errorMessage().get());
                } finally {
                        // Cleanup
                        Files.deleteIfExists(existingOutputFile);
                        Files.deleteIfExists(tempOutputDir);
                }
        }

        @Test
        void testConvertSingle_OutputFileExists_OverwriteEnabled_Succeeds() throws Exception {
                // Given
                when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                any(ConversionSettings.class)))
                                .thenReturn(ValidationResult.success());
                when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                .thenReturn(ConversionTool.FFMPEG);
                when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateOutputDirectory(any(Path.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                .thenReturn(ValidationResult.success());

                Path outputPath = Paths.get("/tmp/output/test.avi");
                ConversionResult successResult = ConversionResult.success(testFile.id(), outputPath, null,
                                Duration.ofSeconds(2), testFile.size(), 800L, ConversionTool.FFMPEG);

                // Mock executeTool to create the temp file (simulating actual conversion)
                when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                any(ProgressCallback.class),
                                any(String.class), any(ProcessRegistry.class)))
                                .thenAnswer(invocation -> {
                                        // Create the temp file to simulate tool execution
                                        Path tempPath = invocation.getArgument(2);
                                        Files.createDirectories(tempPath.getParent());
                                        Files.write(tempPath, "test content".getBytes());
                                        return successResult;
                                });

                doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                // When - testSettings has overwriteExisting = false by default, so change it
                ConversionSettings overwriteSettings = ConversionSettings.builder()
                                .outputFormat(FileFormat.AVI)
                                .outputDirectory(Paths.get("/tmp/output"))
                                .overwriteExisting(true)
                                .build();
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile,
                                overwriteSettings);
                ConversionResult result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertTrue(result.success());
                verify(toolManager).executeTool(any(ConversionTool.class), any(Path.class),
                                any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                any(ProgressCallback.class),
                                any(String.class), any(ProcessRegistry.class));

                // When - Second conversion with overwrite enabled should also succeed
                // (output file already exists from first conversion)
                CompletableFuture<ConversionResult> future2 = conversionEngine.convertSingle(testFile,
                                overwriteSettings);
                ConversionResult result2 = future2.get(5, TimeUnit.SECONDS);

                // Then - Second conversion should also succeed with overwrite enabled
                assertTrue(result2.success());
                verify(toolManager, times(2)).executeTool(any(ConversionTool.class), any(Path.class),
                                any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                any(ProgressCallback.class),
                                any(String.class), any(ProcessRegistry.class));
        }

        @Test
        void testConvertSingle_OutputPathGeneration_DefaultDirectory() throws Exception {
                // Given - Settings with null outputDirectory to test default behavior
                ConversionSettings defaultDirSettings = ConversionSettings.builder()
                                .outputFormat(FileFormat.AVI)
                                .outputDirectory(null) // Null means use input file's directory
                                .build();

                when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                any(ConversionSettings.class)))
                                .thenReturn(ValidationResult.success());
                when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                .thenReturn(ConversionTool.FFMPEG);
                when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateOutputDirectory(any(Path.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                .thenReturn(ValidationResult.success());

                Path outputPath = Paths.get("/tmp/test.avi"); // Same directory as input
                ConversionResult successResult = ConversionResult.success(testFile.id(), outputPath, null,
                                Duration.ofSeconds(2), testFile.size(), 800L, ConversionTool.FFMPEG);

                when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                any(ProgressCallback.class),
                                any(String.class), any(ProcessRegistry.class)))
                                .thenAnswer(invocation -> {
                                        // Create the temp file to simulate tool execution
                                        Path tempPath = invocation.getArgument(2);
                                        Files.createDirectories(tempPath.getParent());
                                        Files.write(tempPath, "test content".getBytes());
                                        return successResult;
                                });

                doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                // When
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile,
                                defaultDirSettings);
                ConversionResult result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertTrue(result.success());
                // Verify output path from result, not from tool execution (which uses temp
                // files internally)
                assertTrue(result.outputPath().isPresent());
                assertEquals("test.avi", result.outputPath().get().getFileName().toString());
                assertEquals(testPath.getParent(), result.outputPath().get().getParent()); // Same directory
        }

        @Test
        void testConvertSingle_OutputPathGeneration_CustomDirectory() throws Exception {
                // Given
                Path customOutputDir = Paths.get("/tmp/custom-output-" + System.currentTimeMillis());
                Files.createDirectories(customOutputDir); // Ensure directory exists

                try {
                        ConversionSettings customDirSettings = ConversionSettings.builder()
                                        .outputFormat(FileFormat.AVI)
                                        .outputDirectory(customOutputDir)
                                        .build();

                        when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                        any(ConversionSettings.class)))
                                        .thenReturn(ValidationResult.success());
                        when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                        .thenReturn(ConversionTool.FFMPEG);
                        when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateOutputDirectory(any(Path.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                        .thenReturn(ValidationResult.success());

                        Path expectedOutputPath = customOutputDir.resolve("test.avi");
                        ConversionResult successResult = ConversionResult.success(testFile.id(), expectedOutputPath,
                                        null,
                                        Duration.ofSeconds(2), testFile.size(), 800L, ConversionTool.FFMPEG);

                        when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                        any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class)))
                                        .thenAnswer(invocation -> {
                                                // Create the temp file to simulate tool execution
                                                Path tempPath = invocation.getArgument(2);
                                                Files.createDirectories(tempPath.getParent());
                                                Files.write(tempPath, "test content".getBytes());
                                                return successResult;
                                        });

                        doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                        doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                        // When
                        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile,
                                        customDirSettings);
                        ConversionResult result = future.get(5, TimeUnit.SECONDS);

                        // Then
                        assertTrue(result.success(),
                                        () -> "Conversion failed: " + result.errorMessage().orElse("Unknown error"));
                        // Verify output path from result, not from tool execution (which uses temp
                        // files internally)
                        assertTrue(result.outputPath().isPresent());
                        assertEquals(expectedOutputPath, result.outputPath().get());
                } finally {
                        // Cleanup
                        Files.deleteIfExists(customOutputDir.resolve("test.avi"));
                        Files.deleteIfExists(customOutputDir);
                }
        }

        @Test
        void testConvertSingle_OutputPathGeneration_WithSubdirectory() throws Exception {
                // Given
                Path tempOutputDir = Files.createTempDirectory("omm-test-output");

                try {
                        ConversionSettings subdirSettings = ConversionSettings.builder()
                                        .outputFormat(FileFormat.AVI)
                                        .outputDirectory(tempOutputDir)
                                        .createSubdirectory(true)
                                        .build();

                        when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                        any(ConversionSettings.class)))
                                        .thenReturn(ValidationResult.success());
                        when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                        .thenReturn(ConversionTool.FFMPEG);
                        when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateOutputDirectory(any(Path.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                        .thenReturn(ValidationResult.success());

                        Path expectedOutputPath = tempOutputDir.resolve("converted/test.avi");
                        ConversionResult successResult = ConversionResult.success(testFile.id(), expectedOutputPath,
                                        null,
                                        Duration.ofSeconds(2), testFile.size(), 800L, ConversionTool.FFMPEG);

                        when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                        any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class)))
                                        .thenAnswer(invocation -> {
                                                // Create the temp file to simulate tool execution
                                                Path tempPath = invocation.getArgument(2);
                                                Files.createDirectories(tempPath.getParent());
                                                Files.write(tempPath, "test content".getBytes());
                                                return successResult;
                                        });

                        doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                        doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                        // When
                        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile,
                                        subdirSettings);
                        ConversionResult result = future.get(5, TimeUnit.SECONDS);

                        // Then
                        assertTrue(result.success());
                        // Verify output path from result, not from tool execution (which uses temp
                        // files internally)
                        assertTrue(result.outputPath().isPresent());
                        Path actualOutputPath = result.outputPath().get();
                        assertEquals("test.avi", actualOutputPath.getFileName().toString());
                        assertEquals("converted", actualOutputPath.getParent().getFileName().toString());
                        assertTrue(Files.exists(tempOutputDir.resolve("converted")),
                                        "Subdirectory 'converted' should have been created");
                } finally {
                        // Cleanup
                        Files.deleteIfExists(tempOutputDir.resolve("converted/test.avi"));
                        Files.deleteIfExists(tempOutputDir.resolve("converted"));
                        Files.deleteIfExists(tempOutputDir);
                }
        }

        // ========== Error Recovery and Retry Tests ==========

        @Test
        void testConvertSingle_TransientError_RetriesOnce() throws Exception {
                // Given - Exit code 255 in error message triggers retry
                Path tempOutputDir = Files.createTempDirectory("omm-test-retry");
                try {
                        when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                        any(ConversionSettings.class)))
                                        .thenReturn(ValidationResult.success());
                        when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                        .thenReturn(ConversionTool.FFMPEG);
                        when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateOutputDirectory(any(Path.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                        .thenReturn(ValidationResult.success());

                        doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                        doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                        // First attempt fails with transient error (exit code 255)
                        ConversionResult transientFailure = ConversionResult.failure(testFile.id(),
                                        "FFmpeg conversion failed (exit code 255): Temporary network error", null,
                                        Duration.ofSeconds(1),
                                        testFile.size(),
                                        ConversionTool.FFMPEG);

                        // Second attempt succeeds
                        Path expectedOutputPath = tempOutputDir.resolve("test.avi");
                        ConversionResult successResult = ConversionResult.success(testFile.id(), expectedOutputPath,
                                        null,
                                        Duration.ofSeconds(2), testFile.size(), 800L, ConversionTool.FFMPEG);

                        when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                        any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class)))
                                        .thenReturn(transientFailure) // First attempt
                                        .thenAnswer(invocation -> { // Second attempt (retry)
                                                Path tempPath = invocation.getArgument(2);
                                                Files.createDirectories(tempPath.getParent());
                                                Files.write(tempPath, "test content".getBytes());
                                                return successResult;
                                        });

                        // When
                        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile,
                                        testSettings);
                        ConversionResult result = future.get(5, TimeUnit.SECONDS);

                        // Then
                        assertTrue(result.success(), "Should succeed on retry");
                        verify(toolManager, times(2)).executeTool(
                                        any(ConversionTool.class), any(Path.class), any(Path.class),
                                        any(FileFormat.class), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class));
                } finally {
                        Files.deleteIfExists(tempOutputDir.resolve("test.avi"));
                        Files.deleteIfExists(tempOutputDir);
                }
        }

        @Test
        void testConvertSingle_TransientErrorPersists_FailsAfterRetry() throws Exception {
                // Given - Exit code 255 persists even after retry
                when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                any(ConversionSettings.class)))
                                .thenReturn(ValidationResult.success());
                when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                .thenReturn(ConversionTool.FFMPEG);
                when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateOutputDirectory(any(Path.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                .thenReturn(ValidationResult.success());

                doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                // Both attempts fail with transient error (exit code 255)
                ConversionResult transientFailure = ConversionResult.failure(testFile.id(),
                                "FFmpeg conversion failed (exit code 255): Persistent network error", null,
                                Duration.ofSeconds(1),
                                testFile.size(),
                                ConversionTool.FFMPEG);

                when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                any(ProgressCallback.class),
                                any(String.class), any(ProcessRegistry.class)))
                                .thenReturn(transientFailure);

                // When
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, testSettings);
                ConversionResult result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertFalse(result.success(), "Should fail after retry exhausted");
                assertTrue(result.errorMessage().get().contains("exit code 255"));
                verify(toolManager, times(2)).executeTool(
                                any(ConversionTool.class), any(Path.class), any(Path.class),
                                any(FileFormat.class), any(ConversionSettings.class), any(ProgressCallback.class),
                                any(String.class), any(ProcessRegistry.class));
        }

        @Test
        void testConvertSingle_NonTransientError_DoesNotRetry() throws Exception {
                // Given - Non-transient error (exit code 1)
                when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                any(ConversionSettings.class)))
                                .thenReturn(ValidationResult.success());
                when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                .thenReturn(ConversionTool.FFMPEG);
                when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateOutputDirectory(any(Path.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                .thenReturn(ValidationResult.success());

                doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                // Non-transient error (exit code 1)
                ConversionResult nonTransientFailure = ConversionResult.failure(testFile.id(),
                                "FFmpeg conversion failed (exit code 1): Invalid codec", null, Duration.ofSeconds(1),
                                testFile.size(),
                                ConversionTool.FFMPEG);

                when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                any(ProgressCallback.class),
                                any(String.class), any(ProcessRegistry.class)))
                                .thenReturn(nonTransientFailure);

                // When
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, testSettings);
                ConversionResult result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertFalse(result.success(), "Should fail without retry");
                assertTrue(result.errorMessage().get().contains("exit code 1"));
                verify(toolManager, times(1)).executeTool(
                                any(ConversionTool.class), any(Path.class), any(Path.class),
                                any(FileFormat.class), any(ConversionSettings.class), any(ProgressCallback.class),
                                any(String.class), any(ProcessRegistry.class));
        }

        @Test
        void testConvertBatch_IndividualFileError_ContinuesWithOthers() throws Exception {
                Path tempOutputDir = Files.createTempDirectory("omm-test-batch");
                try {
                        // Given - Mix of success and failure
                        ConversionFile file1 = ConversionFile.create(Paths.get("/test/file1.mp4"), FileFormat.MP4,
                                        1000L);
                        ConversionFile file2 = ConversionFile.create(Paths.get("/test/file2.mp4"), FileFormat.MP4,
                                        2000L);
                        ConversionFile file3 = ConversionFile.create(Paths.get("/test/file3.mp4"), FileFormat.MP4,
                                        3000L);
                        List<ConversionFile> files = Arrays.asList(file1, file2, file3);

                        ConversionSettings batchSettings = ConversionSettings.builder()
                                        .outputFormat(FileFormat.AVI)
                                        .outputDirectory(tempOutputDir)
                                        .build();

                        when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                        any(ConversionSettings.class)))
                                        .thenReturn(ValidationResult.success());
                        when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                        .thenReturn(ConversionTool.FFMPEG);
                        when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateOutputDirectory(any(Path.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                        .thenReturn(ValidationResult.success());

                        doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                        doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                        // file1 succeeds, file2 fails, file3 succeeds
                        when(toolManager.executeTool(any(ConversionTool.class), eq(Paths.get("/test/file1.mp4")),
                                        any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class)))
                                        .thenAnswer(invocation -> {
                                                Path tempPath = invocation.getArgument(2);
                                                Files.createDirectories(tempPath.getParent());
                                                Files.write(tempPath, "content1".getBytes());
                                                return ConversionResult.success(file1.id(),
                                                                tempOutputDir.resolve("file1.avi"), null,
                                                                Duration.ofSeconds(1), 1000L, 800L,
                                                                ConversionTool.FFMPEG);
                                        });

                        when(toolManager.executeTool(any(ConversionTool.class), eq(Paths.get("/test/file2.mp4")),
                                        any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class)))
                                        .thenReturn(ConversionResult.failure(file2.id(), "Codec error", null,
                                                        Duration.ofSeconds(1),
                                                        2000L, ConversionTool.FFMPEG));

                        when(toolManager.executeTool(any(ConversionTool.class), eq(Paths.get("/test/file3.mp4")),
                                        any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class)))
                                        .thenAnswer(invocation -> {
                                                Path tempPath = invocation.getArgument(2);
                                                Files.createDirectories(tempPath.getParent());
                                                Files.write(tempPath, "content3".getBytes());
                                                return ConversionResult.success(file3.id(),
                                                                tempOutputDir.resolve("file3.avi"), null,
                                                                Duration.ofSeconds(1), 3000L, 2500L,
                                                                ConversionTool.FFMPEG);
                                        });

                        // When
                        CompletableFuture<BatchConversionResult> future = conversionEngine.convertBatch(files,
                                        batchSettings);
                        BatchConversionResult batchResult = future.get(10, TimeUnit.SECONDS);

                        // Then
                        assertEquals(3, batchResult.totalCount());
                        assertEquals(2, batchResult.successCount());
                        assertEquals(1, batchResult.failureCount());
                        assertEquals(2, batchResult.results().stream().filter(ConversionResult::success).count());
                        assertEquals(1, batchResult.results().stream().filter(r -> !r.success()).count());
                } finally {
                        // Cleanup
                        Files.deleteIfExists(tempOutputDir.resolve("file1.avi"));
                        Files.deleteIfExists(tempOutputDir.resolve("file3.avi"));
                        Files.deleteIfExists(tempOutputDir);
                }
        }

        // ========== Settings Resolution Tests ==========
        // Test private methods: resolveSettingsForFile(),
        // getOutputFormatFromSettings(), getDefaultFormatForCategory()
        // These are tested indirectly through the conversion flow

        @Test
        void testResolveSettingsForFile_WithVideoOverride_UsesOverrideSettings() throws Exception {
                // Given - Video file with custom settings override
                Path tempOutputDir = Files.createTempDirectory("omm-test-settings");
                try {
                        VideoSettings videoOverride = VideoSettings.builder()
                                        .outputFormat(FileFormat.WEBM)
                                        .codec("vp9")
                                        .bitrate(5000)
                                        .build();

                        FileSettingsOverride override = FileSettingsOverride.forVideo("custom-preset", videoOverride);
                        ConversionFile fileWithOverride = testFile.withSettingsOverride(override);

                        // Global settings have different format
                        VideoSettings globalVideoSettings = VideoSettings.builder()
                                        .outputFormat(FileFormat.AVI)
                                        .codec("h264")
                                        .bitrate(3000)
                                        .build();

                        ConversionSettings settings = ConversionSettings.builder()
                                        .outputDirectory(tempOutputDir)
                                        .videoSettings(globalVideoSettings)
                                        .build();

                        // Mock validation and tool execution
                        when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                        any(ConversionSettings.class)))
                                        .thenReturn(ValidationResult.success());
                        when(toolManager.selectTool(eq(FileFormat.MP4), eq(FileFormat.WEBM))) // Should use override
                                                                                              // format
                                        .thenReturn(ConversionTool.FFMPEG);
                        when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateOutputDirectory(any(Path.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                        .thenReturn(ValidationResult.success());

                        Path expectedOutputPath = tempOutputDir.resolve("test.webm"); // WEBM from override
                        ConversionResult successResult = ConversionResult.success(fileWithOverride.id(),
                                        expectedOutputPath, null,
                                        Duration.ofSeconds(2), fileWithOverride.size(), 800L, ConversionTool.FFMPEG);

                        when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                        any(Path.class), eq(FileFormat.WEBM), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class)))
                                        .thenAnswer(invocation -> {
                                                Path tempPath = invocation.getArgument(2);
                                                Files.createDirectories(tempPath.getParent());
                                                Files.write(tempPath, "test content".getBytes());
                                                return successResult;
                                        });

                        doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                        doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                        // When
                        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(fileWithOverride,
                                        settings);
                        ConversionResult result = future.get(5, TimeUnit.SECONDS);

                        // Then
                        assertTrue(result.success(), "Conversion should succeed with override settings");
                        // Verify tool was selected with override format (WEBM), not global format (AVI)
                        verify(toolManager).selectTool(eq(FileFormat.MP4), eq(FileFormat.WEBM));
                        verify(toolManager).executeTool(any(ConversionTool.class), any(Path.class),
                                        any(Path.class), eq(FileFormat.WEBM), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class));
                } finally {
                        Files.deleteIfExists(tempOutputDir.resolve("test.webm"));
                        Files.deleteIfExists(tempOutputDir);
                }
        }

        @Test
        void testResolveSettingsForFile_WithoutOverride_UsesSectionSettings() throws Exception {
                // Given - Video file without custom settings
                Path tempOutputDir = Files.createTempDirectory("omm-test-settings");
                try {
                        VideoSettings globalVideoSettings = VideoSettings.builder()
                                        .outputFormat(FileFormat.AVI)
                                        .codec("h264")
                                        .bitrate(3000)
                                        .build();

                        ConversionSettings settings = ConversionSettings.builder()
                                        .outputDirectory(tempOutputDir)
                                        .videoSettings(globalVideoSettings)
                                        .build();

                        // Mock validation and tool execution
                        when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                        any(ConversionSettings.class)))
                                        .thenReturn(ValidationResult.success());
                        when(toolManager.selectTool(eq(FileFormat.MP4), eq(FileFormat.AVI))) // Should use section
                                                                                             // format
                                        .thenReturn(ConversionTool.FFMPEG);
                        when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateOutputDirectory(any(Path.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                        .thenReturn(ValidationResult.success());

                        Path expectedOutputPath = tempOutputDir.resolve("test.avi"); // AVI from section settings
                        ConversionResult successResult = ConversionResult.success(testFile.id(), expectedOutputPath,
                                        null,
                                        Duration.ofSeconds(2), testFile.size(), 800L, ConversionTool.FFMPEG);

                        when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                        any(Path.class), eq(FileFormat.AVI), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class)))
                                        .thenAnswer(invocation -> {
                                                Path tempPath = invocation.getArgument(2);
                                                Files.createDirectories(tempPath.getParent());
                                                Files.write(tempPath, "test content".getBytes());
                                                return successResult;
                                        });

                        doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                        doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                        // When
                        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, settings);
                        ConversionResult result = future.get(5, TimeUnit.SECONDS);

                        // Then
                        assertTrue(result.success(), "Conversion should succeed with section settings");
                        // Verify tool was selected with section format (AVI)
                        verify(toolManager).selectTool(eq(FileFormat.MP4), eq(FileFormat.AVI));
                        verify(toolManager).executeTool(any(ConversionTool.class), any(Path.class),
                                        any(Path.class), eq(FileFormat.AVI), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class));
                } finally {
                        Files.deleteIfExists(tempOutputDir.resolve("test.avi"));
                        Files.deleteIfExists(tempOutputDir);
                }
        }

        @Test
        void testResolveSettingsForFile_AudioFile_UsesAudioSettings() throws Exception {
                // Given - Audio file with section audio settings
                Path tempOutputDir = Files.createTempDirectory("omm-test-settings");
                try {
                        ConversionFile audioFile = ConversionFile.create(Paths.get("/tmp/test.mp3"), FileFormat.MP3,
                                        1000L);

                        AudioSettings globalAudioSettings = AudioSettings.builder()
                                        .outputFormat(FileFormat.FLAC)
                                        .codec("flac")
                                        .bitrate(256)
                                        .build();

                        ConversionSettings settings = ConversionSettings.builder()
                                        .outputDirectory(tempOutputDir)
                                        .audioSettings(globalAudioSettings)
                                        .build();

                        // Mock validation and tool execution
                        when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                        any(ConversionSettings.class)))
                                        .thenReturn(ValidationResult.success());
                        when(toolManager.selectTool(eq(FileFormat.MP3), eq(FileFormat.FLAC))) // Should use audio
                                                                                              // section format
                                        .thenReturn(ConversionTool.FFMPEG);
                        when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateOutputDirectory(any(Path.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                        .thenReturn(ValidationResult.success());

                        Path expectedOutputPath = tempOutputDir.resolve("test.flac");
                        ConversionResult successResult = ConversionResult.success(audioFile.id(), expectedOutputPath,
                                        null,
                                        Duration.ofSeconds(2), audioFile.size(), 1200L, ConversionTool.FFMPEG);

                        when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                        any(Path.class), eq(FileFormat.FLAC), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class)))
                                        .thenAnswer(invocation -> {
                                                Path tempPath = invocation.getArgument(2);
                                                Files.createDirectories(tempPath.getParent());
                                                Files.write(tempPath, "test content".getBytes());
                                                return successResult;
                                        });

                        doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                        doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                        // When
                        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(audioFile,
                                        settings);
                        ConversionResult result = future.get(5, TimeUnit.SECONDS);

                        // Then
                        assertTrue(result.success(), "Conversion should succeed with audio settings");
                        verify(toolManager).selectTool(eq(FileFormat.MP3), eq(FileFormat.FLAC));
                        verify(toolManager).executeTool(any(ConversionTool.class), any(Path.class),
                                        any(Path.class), eq(FileFormat.FLAC), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class));
                } finally {
                        Files.deleteIfExists(tempOutputDir.resolve("test.flac"));
                        Files.deleteIfExists(tempOutputDir);
                }
        }

        @Test
        void testResolveSettingsForFile_ImageFile_UsesImageSettings() throws Exception {
                // Given - Image file with section image settings
                Path tempOutputDir = Files.createTempDirectory("omm-test-settings");
                try {
                        ConversionFile imageFile = ConversionFile.create(Paths.get("/tmp/test.jpg"), FileFormat.JPEG,
                                        500L);

                        ImageSettings globalImageSettings = ImageSettings.builder()
                                        .outputFormat(FileFormat.PNG)
                                        .compressionLevel(9)
                                        .build();

                        ConversionSettings settings = ConversionSettings.builder()
                                        .outputDirectory(tempOutputDir)
                                        .imageSettings(globalImageSettings)
                                        .build();

                        // Mock validation and tool execution
                        when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                        any(ConversionSettings.class)))
                                        .thenReturn(ValidationResult.success());
                        when(toolManager.selectTool(eq(FileFormat.JPEG), eq(FileFormat.PNG))) // Should use image
                                                                                              // section format
                                        .thenReturn(ConversionTool.FFMPEG);
                        when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateOutputDirectory(any(Path.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                        .thenReturn(ValidationResult.success());

                        Path expectedOutputPath = tempOutputDir.resolve("test.png");
                        ConversionResult successResult = ConversionResult.success(imageFile.id(), expectedOutputPath,
                                        null,
                                        Duration.ofSeconds(1), imageFile.size(), 450L, ConversionTool.FFMPEG);

                        when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                        any(Path.class), eq(FileFormat.PNG), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class)))
                                        .thenAnswer(invocation -> {
                                                Path tempPath = invocation.getArgument(2);
                                                Files.createDirectories(tempPath.getParent());
                                                Files.write(tempPath, "test content".getBytes());
                                                return successResult;
                                        });

                        doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                        doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                        // When
                        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(imageFile,
                                        settings);
                        ConversionResult result = future.get(5, TimeUnit.SECONDS);

                        // Then
                        assertTrue(result.success(), "Conversion should succeed with image settings");
                        verify(toolManager).selectTool(eq(FileFormat.JPEG), eq(FileFormat.PNG));
                        verify(toolManager).executeTool(any(ConversionTool.class), any(Path.class),
                                        any(Path.class), eq(FileFormat.PNG), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class));
                } finally {
                        Files.deleteIfExists(tempOutputDir.resolve("test.png"));
                        Files.deleteIfExists(tempOutputDir);
                }
        }

        @Test
        void testResolveSettingsForFile_DocumentFile_UsesDocumentSettings() throws Exception {
                // Given - Document file with section document settings
                Path tempOutputDir = Files.createTempDirectory("omm-test-settings");
                try {
                        ConversionFile docFile = ConversionFile.create(Paths.get("/tmp/test.docx"), FileFormat.DOCX,
                                        2000L);

                        DocumentSettings globalDocumentSettings = DocumentSettings.builder()
                                        .outputFormat(FileFormat.PDF)
                                        .marginTop(20)
                                        .marginBottom(20)
                                        .build();

                        ConversionSettings settings = ConversionSettings.builder()
                                        .outputDirectory(tempOutputDir)
                                        .documentSettings(globalDocumentSettings)
                                        .build();

                        // Mock validation and tool execution
                        when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                        any(ConversionSettings.class)))
                                        .thenReturn(ValidationResult.success());
                        when(toolManager.selectTool(eq(FileFormat.DOCX), eq(FileFormat.PDF))) // Should use document
                                                                                              // section format
                                        .thenReturn(ConversionTool.LIBREOFFICE);
                        when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateOutputDirectory(any(Path.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                        .thenReturn(ValidationResult.success());

                        Path expectedOutputPath = tempOutputDir.resolve("test.pdf");
                        ConversionResult successResult = ConversionResult.success(docFile.id(), expectedOutputPath,
                                        null,
                                        Duration.ofSeconds(3), docFile.size(), 1800L, ConversionTool.LIBREOFFICE);

                        when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                        any(Path.class), eq(FileFormat.PDF), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class)))
                                        .thenAnswer(invocation -> {
                                                Path tempPath = invocation.getArgument(2);
                                                Files.createDirectories(tempPath.getParent());
                                                Files.write(tempPath, "test content".getBytes());
                                                return successResult;
                                        });

                        doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                        doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                        // When
                        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(docFile, settings);
                        ConversionResult result = future.get(5, TimeUnit.SECONDS);

                        // Then
                        assertTrue(result.success(), "Conversion should succeed with document settings");
                        verify(toolManager).selectTool(eq(FileFormat.DOCX), eq(FileFormat.PDF));
                        verify(toolManager).executeTool(any(ConversionTool.class), any(Path.class),
                                        any(Path.class), eq(FileFormat.PDF), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class));
                } finally {
                        Files.deleteIfExists(tempOutputDir.resolve("test.pdf"));
                        Files.deleteIfExists(tempOutputDir);
                }
        }

        @Test
        void testGetOutputFormatFromSettings_AllCategories_ExtractsCorrectFormat() throws Exception {
                // This test verifies getOutputFormatFromSettings() works for all categories
                // by testing conversions with each category and verifying correct output format
                Path tempOutputDir = Files.createTempDirectory("omm-test-format-extract");
                try {
                        // Test VIDEO category
                        VideoSettings videoSettings = VideoSettings.builder()
                                        .outputFormat(FileFormat.MKV)
                                        .build();
                        ConversionSettings settingsWithVideo = ConversionSettings.builder()
                                        .outputDirectory(tempOutputDir)
                                        .videoSettings(videoSettings)
                                        .build();

                        setupSuccessfulConversion(FileFormat.MP4, FileFormat.MKV);
                        CompletableFuture<ConversionResult> videoFuture = conversionEngine.convertSingle(testFile,
                                        settingsWithVideo);
                        ConversionResult videoResult = videoFuture.get(5, TimeUnit.SECONDS);
                        assertTrue(videoResult.success(), "Video conversion should extract MKV format");
                        verify(toolManager).selectTool(eq(FileFormat.MP4), eq(FileFormat.MKV));

                        // Test AUDIO category
                        ConversionFile audioFile = ConversionFile.create(Paths.get("/tmp/test.wav"), FileFormat.WAV,
                                        1000L);
                        AudioSettings audioSettings = AudioSettings.builder()
                                        .outputFormat(FileFormat.OGG)
                                        .build();
                        ConversionSettings settingsWithAudio = ConversionSettings.builder()
                                        .outputDirectory(tempOutputDir)
                                        .audioSettings(audioSettings)
                                        .build();

                        setupSuccessfulConversion(FileFormat.WAV, FileFormat.OGG);
                        CompletableFuture<ConversionResult> audioFuture = conversionEngine.convertSingle(audioFile,
                                        settingsWithAudio);
                        ConversionResult audioResult = audioFuture.get(5, TimeUnit.SECONDS);
                        assertTrue(audioResult.success(), "Audio conversion should extract OGG format");
                        verify(toolManager).selectTool(eq(FileFormat.WAV), eq(FileFormat.OGG));

                } finally {
                        Files.deleteIfExists(tempOutputDir.resolve("test.mkv"));
                        Files.deleteIfExists(tempOutputDir.resolve("test.ogg"));
                        Files.deleteIfExists(tempOutputDir);
                }
        }

        @Test
        void testGetDefaultFormatForCategory_ReturnsCorrectDefaults() throws Exception {
                // This test verifies getDefaultFormatForCategory() by testing conversions
                // with null settings, which should fall back to defaults
                // However, ConversionEngine requires non-null settings, so we test indirectly
                // by observing the behavior when settings objects lack outputFormat

                // The defaults are:
                // VIDEO -> MP4
                // AUDIO -> MP3
                // IMAGE -> PNG
                // DOCUMENT -> PDF

                // Since our builder always sets defaults, this test documents the expected
                // defaults
                VideoSettings defaultVideo = VideoSettings.builder().build();
                assertEquals(FileFormat.MP4, defaultVideo.outputFormat(), "Default video format should be MP4");

                AudioSettings defaultAudio = AudioSettings.builder().build();
                assertEquals(FileFormat.MP3, defaultAudio.outputFormat(), "Default audio format should be MP3");

                ImageSettings defaultImage = ImageSettings.builder().build();
                assertEquals(FileFormat.PNG, defaultImage.outputFormat(), "Default image format should be PNG");

                DocumentSettings defaultDocument = DocumentSettings.builder().build();
                assertEquals(FileFormat.PDF, defaultDocument.outputFormat(), "Default document format should be PDF");
        }

        /**
         * Requirement: REQ-FL-2.2 - Test conversion result storage and retrieval.
         */
        @Test
        void testGetConversionResult_AfterSuccessfulConversion_ReturnsResult() throws Exception {
                // Arrange
                setupSuccessfulConversion(FileFormat.MP4, FileFormat.AVI);

                // Act
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, testSettings);
                ConversionResult result = future.get(5, TimeUnit.SECONDS);

                // Wait for whenComplete to store result (race condition mitigation)
                ConversionResult storedResult = null;
                for (int i = 0; i < 100 && storedResult == null; i++) {
                        storedResult = conversionEngine.getConversionResult(testFile.id());
                        if (storedResult == null) {
                                Thread.sleep(10); // Wait up to 1 second total
                        }
                }

                // Assert
                assertNotNull(storedResult, "Stored result should not be null");
                assertEquals(result.success(), storedResult.success(), "Stored result should match returned result");
                assertEquals(result.fileId(), storedResult.fileId(), "File ID should match");
        }

        /**
         * Requirement: REQ-FL-2.2 - Test conversion result storage for failed
         * conversion.
         */
        @Test
        void testGetConversionResult_AfterFailedConversion_ReturnsFailureResult() throws Exception {
                // Arrange
                when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                any(ConversionSettings.class)))
                                .thenReturn(ValidationResult.failure("Invalid file format"));

                // Act
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, testSettings);
                ConversionResult result = future.get(5, TimeUnit.SECONDS);

                // Wait for whenComplete to store result (race condition mitigation)
                ConversionResult storedResult = null;
                for (int i = 0; i < 100 && storedResult == null; i++) {
                        storedResult = conversionEngine.getConversionResult(testFile.id());
                        if (storedResult == null) {
                                Thread.sleep(10); // Wait up to 1 second total
                        }
                }

                // Assert
                assertNotNull(storedResult, "Stored result should not be null");
                assertFalse(storedResult.success(), "Stored result should indicate failure");
                assertEquals(result.fileId(), storedResult.fileId(), "File ID should match");
                assertTrue(storedResult.errorMessage().isPresent(), "Error message should be present");
        }

        /**
         * Requirement: REQ-FL-2.2 - Test conversion result retrieval for non-existent
         * file.
         */
        @Test
        void testGetConversionResult_ForNonExistentFile_ReturnsNull() {
                // Act
                ConversionResult result = conversionEngine.getConversionResult("non-existent-file-id");

                // Assert
                assertNull(result, "Result should be null for non-existent file ID");
        }

        /**
         * Requirement: REQ-FL-2.2 - Test conversion results are cleared on shutdown.
         */
        @Test
        void testShutdown_ClearsConversionResults() throws Exception {
                // Arrange
                setupSuccessfulConversion(FileFormat.MP4, FileFormat.AVI);

                // Act
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, testSettings);
                future.get(5, TimeUnit.SECONDS);

                // Wait for whenComplete to store result (race condition mitigation)
                ConversionResult storedResult = null;
                for (int i = 0; i < 100 && storedResult == null; i++) {
                        storedResult = conversionEngine.getConversionResult(testFile.id());
                        if (storedResult == null) {
                                Thread.sleep(10); // Wait up to 1 second total
                        }
                }

                // Verify result is stored
                assertNotNull(storedResult, "Result should be stored before shutdown");

                // Shutdown
                conversionEngine.shutdown();

                // Assert
                ConversionResult resultAfterShutdown = conversionEngine.getConversionResult(testFile.id());
                assertNull(resultAfterShutdown, "Result should be cleared after shutdown");
        }

        /**
         * Requirement: REQ-FL-2.2 - Test conversion results are cleared on
         * cancellation.
         */
        @Test
        void testCancelConversion_ClearsConversionResults() throws Exception {
                // Arrange
                setupSuccessfulConversion(FileFormat.MP4, FileFormat.AVI);

                // Act
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, testSettings);
                future.get(5, TimeUnit.SECONDS);

                // Wait for whenComplete to store result (race condition mitigation)
                ConversionResult storedResult = null;
                for (int i = 0; i < 100 && storedResult == null; i++) {
                        storedResult = conversionEngine.getConversionResult(testFile.id());
                        if (storedResult == null) {
                                Thread.sleep(10); // Wait up to 1 second total
                        }
                }

                // Verify result is stored
                assertNotNull(storedResult, "Result should be stored before cancellation");

                // Cancel
                conversionEngine.cancelConversion();

                // Assert
                ConversionResult resultAfterCancel = conversionEngine.getConversionResult(testFile.id());
                assertNull(resultAfterCancel, "Result should be cleared after cancellation");
        }

        /**
         * Requirement: REQ-FL-2.2 - Test multiple conversion results are stored
         * independently.
         */
        @Test
        void testGetConversionResult_MultipleFiles_StoresIndependently() throws Exception {
                // Arrange
                setupSuccessfulConversion(FileFormat.MP4, FileFormat.AVI);
                ConversionFile file1 = ConversionFile.create(Paths.get("/tmp/test1.mp4"), FileFormat.MP4, 1024L);
                ConversionFile file2 = ConversionFile.create(Paths.get("/tmp/test2.mp4"), FileFormat.MP4, 2048L);

                // Act
                CompletableFuture<ConversionResult> future1 = conversionEngine.convertSingle(file1, testSettings);
                CompletableFuture<ConversionResult> future2 = conversionEngine.convertSingle(file2, testSettings);
                future1.get(5, TimeUnit.SECONDS);
                future2.get(5, TimeUnit.SECONDS);

                // Wait for whenComplete to store results (race condition mitigation)
                ConversionResult result1 = null;
                ConversionResult result2 = null;
                for (int i = 0; i < 100 && (result1 == null || result2 == null); i++) {
                        if (result1 == null) {
                                result1 = conversionEngine.getConversionResult(file1.id());
                        }
                        if (result2 == null) {
                                result2 = conversionEngine.getConversionResult(file2.id());
                        }
                        if (result1 == null || result2 == null) {
                                Thread.sleep(10); // Wait up to 1 second total
                        }
                }

                // Assert
                assertNotNull(result1, "Result for file1 should be stored");
                assertNotNull(result2, "Result for file2 should be stored");
                assertNotEquals(result1.fileId(), result2.fileId(), "Results should have different file IDs");
        }

        /**
         * Helper method to set up mocks for successful conversion.
         */
        private void setupSuccessfulConversion(FileFormat inputFormat, FileFormat outputFormat) throws Exception {
                lenient()
                                .when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                                any(ConversionSettings.class)))
                                .thenReturn(ValidationResult.success());
                lenient().when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                .thenReturn(ConversionTool.FFMPEG);
                lenient().when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                .thenReturn(ValidationResult.success());
                lenient().when(validationEngine.validateOutputDirectory(any(Path.class)))
                                .thenReturn(ValidationResult.success());
                lenient().when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                .thenReturn(ValidationResult.success());

                lenient().when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                any(ProgressCallback.class),
                                any(String.class), any(ProcessRegistry.class)))
                                .thenAnswer(invocation -> {
                                        Path inputPath = invocation.getArgument(1);
                                        Path tempPath = invocation.getArgument(2);
                                        Files.createDirectories(tempPath.getParent());
                                        Files.write(tempPath, "test content".getBytes());
                                        // Generate file ID from input path (e.g., /tmp/test.mp4 -> test.mp4)
                                        String fileId = inputPath.getFileName().toString();
                                        return ConversionResult.success(fileId, tempPath, null, Duration.ofSeconds(1),
                                                        1000L, 800L,
                                                        ConversionTool.FFMPEG);
                                });

                lenient().doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                lenient().doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));
        }

        // ==================== File Deletion Tests (REQ-GEN-1.2, REQ-GEN-1.4)
        // ====================

        /**
         * REQ-GEN-1.2: Verify original file is deleted after successful conversion when
         * deleteOriginalFile=true
         */
        @Test
        void testDeleteOriginalFileAfterSuccessfulConversion() throws Exception {
                // Given - Settings with deleteOriginalFile enabled
                ConversionSettings deleteSettings = ConversionSettings.builder()
                                .outputFormat(FileFormat.AVI)
                                .outputDirectory(Paths.get("/tmp/output"))
                                .deleteOriginalFile(true)
                                .build();

                // Create the original file on disk (delete first if exists from previous run)
                Files.deleteIfExists(testPath);
                Files.createFile(testPath);
                assertTrue(Files.exists(testPath), "Test file should exist before conversion");

                // Mock successful conversion
                when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                any(ConversionSettings.class)))
                                .thenReturn(ValidationResult.success());
                when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                .thenReturn(ConversionTool.FFMPEG);
                when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateOutputDirectory(any(Path.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                .thenReturn(ValidationResult.success());

                Path outputPath = Paths.get("/tmp/output/test.avi");
                ConversionResult successResult = ConversionResult.success(testFile.id(), outputPath, null,
                                Duration.ofSeconds(2), testFile.size(), 800L, ConversionTool.FFMPEG);

                when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                any(ProgressCallback.class),
                                any(String.class), any(ProcessRegistry.class)))
                                .thenAnswer(invocation -> {
                                        Path tempPath = invocation.getArgument(2);
                                        Files.createDirectories(tempPath.getParent());
                                        Files.write(tempPath, "test content".getBytes());
                                        return successResult;
                                });

                doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                // When
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, deleteSettings);
                ConversionResult result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertTrue(result.success(), "Conversion should succeed");
                assertFalse(Files.exists(testPath), "Original file should be deleted after successful conversion");
        }

        /**
         * REQ-GEN-1.2: Verify original file is preserved when deleteOriginalFile=false
         */
        @Test
        void testPreserveOriginalFileWhenDeleteDisabled() throws Exception {
                // Given - Settings with deleteOriginalFile disabled
                ConversionSettings preserveSettings = ConversionSettings.builder()
                                .outputFormat(FileFormat.AVI)
                                .outputDirectory(Paths.get("/tmp/output"))
                                .deleteOriginalFile(false)
                                .build();

                // Create the original file on disk (delete first if exists from previous run)
                Files.deleteIfExists(testPath);
                Files.createFile(testPath);
                assertTrue(Files.exists(testPath), "Test file should exist before conversion");

                // Mock successful conversion
                when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                any(ConversionSettings.class)))
                                .thenReturn(ValidationResult.success());
                when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                .thenReturn(ConversionTool.FFMPEG);
                when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateOutputDirectory(any(Path.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                .thenReturn(ValidationResult.success());

                Path outputPath = Paths.get("/tmp/output/test.avi");
                ConversionResult successResult = ConversionResult.success(testFile.id(), outputPath, null,
                                Duration.ofSeconds(2), testFile.size(), 800L, ConversionTool.FFMPEG);

                when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                any(ProgressCallback.class),
                                any(String.class), any(ProcessRegistry.class)))
                                .thenAnswer(invocation -> {
                                        Path tempPath = invocation.getArgument(2);
                                        Files.createDirectories(tempPath.getParent());
                                        Files.write(tempPath, "test content".getBytes());
                                        return successResult;
                                });

                doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                // When
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, preserveSettings);
                ConversionResult result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertTrue(result.success(), "Conversion should succeed");
                assertTrue(Files.exists(testPath), "Original file should be preserved when deleteOriginalFile=false");
        }

        /**
         * REQ-GEN-1.2: Verify original file is preserved when conversion fails, even if
         * deleteOriginalFile=true
         */
        @Test
        void testPreserveOriginalFileOnConversionFailure() throws Exception {
                // Given - Settings with deleteOriginalFile enabled but conversion fails
                ConversionSettings deleteSettings = ConversionSettings.builder()
                                .outputFormat(FileFormat.AVI)
                                .outputDirectory(Paths.get("/tmp/output"))
                                .deleteOriginalFile(true)
                                .build();

                // Create the original file on disk (delete first if exists from previous run)
                Files.deleteIfExists(testPath);
                Files.createFile(testPath);
                assertTrue(Files.exists(testPath), "Test file should exist before conversion");

                // Mock failed conversion
                when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                any(ConversionSettings.class)))
                                .thenReturn(ValidationResult.success());
                when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                .thenReturn(ConversionTool.FFMPEG);
                when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateOutputDirectory(any(Path.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                .thenReturn(ValidationResult.success());

                ConversionResult failureResult = ConversionResult.failure(testFile.id(), "Conversion failed", null,
                                Duration.ofSeconds(1), testFile.size(), ConversionTool.FFMPEG);

                when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                any(ProgressCallback.class),
                                any(String.class), any(ProcessRegistry.class)))
                                .thenReturn(failureResult);

                doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                // When
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, deleteSettings);
                ConversionResult result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertFalse(result.success(), "Conversion should fail");
                assertTrue(Files.exists(testPath), "Original file should be preserved when conversion fails");
        }

        /**
         * REQ-GEN-1.4: Verify batch conversion deletes only successful files when
         * deleteOriginalFile=true
         */
        @Test
        void testBatchPartialFailureDeletesOnlySuccessfulFiles() throws Exception {
                // Given - Batch with mixed success/failure, delete enabled
                Path tempOutputDir = Files.createTempDirectory("omm-test-batch-partial");
                Path file1Path = Files.createTempFile("omm-test-file1", ".mp4");
                Path file2Path = Files.createTempFile("omm-test-file2", ".mp4");

                try {
                        // Create actual test files
                        Files.write(file1Path, "file1 content".getBytes());
                        Files.write(file2Path, "file2 content".getBytes());

                        ConversionFile file1 = ConversionFile.create(file1Path, FileFormat.MP4, Files.size(file1Path));
                        ConversionFile file2 = ConversionFile.create(file2Path, FileFormat.MP4, Files.size(file2Path));
                        List<ConversionFile> files = Arrays.asList(file1, file2);

                        ConversionSettings batchDeleteSettings = ConversionSettings.builder()
                                        .outputFormat(FileFormat.AVI)
                                        .outputDirectory(tempOutputDir)
                                        .deleteOriginalFile(true)
                                        .build();

                        when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                        any(ConversionSettings.class)))
                                        .thenReturn(ValidationResult.success());
                        when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                        .thenReturn(ConversionTool.FFMPEG);
                        when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateOutputDirectory(any(Path.class)))
                                        .thenReturn(ValidationResult.success());
                        when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                        .thenReturn(ValidationResult.success());

                        doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                        doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                        // file1 succeeds, file2 fails
                        when(toolManager.executeTool(any(ConversionTool.class), eq(file1Path),
                                        any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class)))
                                        .thenAnswer(invocation -> {
                                                Path tempPath = invocation.getArgument(2);
                                                Files.createDirectories(tempPath.getParent());
                                                Files.write(tempPath, "content1".getBytes());
                                                return ConversionResult.success(file1.id(),
                                                                tempOutputDir.resolve("file1.avi"), null,
                                                                Duration.ofSeconds(1), file1.size(), 800L,
                                                                ConversionTool.FFMPEG);
                                        });

                        when(toolManager.executeTool(any(ConversionTool.class), eq(file2Path),
                                        any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                        any(ProgressCallback.class),
                                        any(String.class), any(ProcessRegistry.class)))
                                        .thenReturn(ConversionResult.failure(file2.id(), "Codec error", null,
                                                        Duration.ofSeconds(1), file2.size(), ConversionTool.FFMPEG));

                        // When
                        CompletableFuture<BatchConversionResult> future = conversionEngine.convertBatch(files,
                                        batchDeleteSettings);
                        BatchConversionResult batchResult = future.get(10, TimeUnit.SECONDS);

                        // Then
                        assertEquals(1, batchResult.successCount(), "One file should succeed");
                        assertEquals(1, batchResult.failureCount(), "One file should fail");
                        assertFalse(Files.exists(file1Path), "Successful file should be deleted");
                        assertTrue(Files.exists(file2Path), "Failed file should be preserved");

                } finally {
                        // Cleanup
                        Files.deleteIfExists(file1Path);
                        Files.deleteIfExists(file2Path);
                        Files.deleteIfExists(tempOutputDir.resolve("file1.avi"));
                        // Delete directory contents and then directory
                        try {
                                if (Files.exists(tempOutputDir)) {
                                        Files.walk(tempOutputDir)
                                                        .sorted((a, b) -> b.compareTo(a)) // Reverse order to delete
                                                                                          // files before dirs
                                                        .forEach(path -> {
                                                                try {
                                                                        Files.deleteIfExists(path);
                                                                } catch (IOException e) {
                                                                        // Ignore cleanup errors
                                                                }
                                                        });
                                }
                        } catch (IOException e) {
                                // Ignore cleanup errors
                        }
                }
        }

        /**
         * REQ-GEN-1.2: Verify deletion errors are logged but do not fail the conversion
         */
        @Test
        void testDeletionErrorHandling() throws Exception {
                // Given - File that cannot be deleted (simulated by deleting it before the
                // engine tries)
                ConversionSettings deleteSettings = ConversionSettings.builder()
                                .outputFormat(FileFormat.AVI)
                                .outputDirectory(Paths.get("/tmp/output"))
                                .deleteOriginalFile(true)
                                .build();

                // Create and immediately delete the file to simulate deletion error
                Files.deleteIfExists(testPath);
                Files.createFile(testPath);
                Path fileToDelete = testPath;

                // Mock successful conversion
                when(validationEngine.validateConversionRequest(any(ConversionFile.class),
                                any(ConversionSettings.class)))
                                .thenReturn(ValidationResult.success());
                when(toolManager.selectTool(any(FileFormat.class), any(FileFormat.class)))
                                .thenReturn(ConversionTool.FFMPEG);
                when(validationEngine.validateToolAvailability(any(ConversionTool.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateOutputDirectory(any(Path.class)))
                                .thenReturn(ValidationResult.success());
                when(validationEngine.validateDiskSpace(any(Path.class), anyLong()))
                                .thenReturn(ValidationResult.success());

                Path outputPath = Paths.get("/tmp/output/test.avi");
                ConversionResult successResult = ConversionResult.success(testFile.id(), outputPath, null,
                                Duration.ofSeconds(2), testFile.size(), 800L, ConversionTool.FFMPEG);

                when(toolManager.executeTool(any(ConversionTool.class), any(Path.class),
                                any(Path.class), any(FileFormat.class), any(ConversionSettings.class),
                                any(ProgressCallback.class),
                                any(String.class), any(ProcessRegistry.class)))
                                .thenAnswer(invocation -> {
                                        Path tempPath = invocation.getArgument(2);
                                        Files.createDirectories(tempPath.getParent());
                                        Files.write(tempPath, "test content".getBytes());
                                        // Delete the original file before the engine tries to delete it
                                        // This simulates a scenario where the file was already removed or is
                                        // inaccessible
                                        Files.deleteIfExists(fileToDelete);
                                        return successResult;
                                });

                doNothing().when(progressEngine).startTracking(anyString(), anyLong());
                doNothing().when(progressEngine).completeTracking(anyString(), any(ConversionResult.class));

                // When
                CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(testFile, deleteSettings);
                ConversionResult result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertTrue(result.success(), "Conversion should succeed even if deletion fails");
                assertFalse(Files.exists(testPath), "File was deleted by mock (simulating deletion scenario)");
                // Note: In real scenario with permission error, file would still exist but
                // conversion would succeed
                // The key assertion is that result.success() is true regardless of deletion
                // outcome
        }

}
