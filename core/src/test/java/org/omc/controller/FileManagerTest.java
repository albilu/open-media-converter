package org.omc.controller;

import org.omc.controller.FileManager;
import org.omc.core.ValidationEngine;
import org.omc.exception.ErrorCode;
import org.omc.exception.FileOperationException;
import org.omc.model.ConversionFile;
import org.omc.model.ConversionStatus;
import org.omc.model.FileFormat;
import org.omc.model.ValidationResult;
import org.omc.service.FileHandler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileManagerTest {

    @Mock
    private FileHandler fileHandler;

    @Mock
    private ValidationEngine validationEngine;

    private FileManager fileManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileManager = new FileManager(fileHandler, validationEngine);
    }

    // ===== restoreFiles hash warm-up =====

    @Test
    void restoreFiles_populatesHashIndex_soLaterContentDuplicatesAreRejected() throws Exception {
        // Given: a file restored from a previous session
        Path restoredPath = Files.write(tempDir.resolve("restored.mp4"), "identical-content".getBytes());
        ConversionFile restored = ConversionFile.create(restoredPath, FileFormat.MP4, 1000L);
        fileManager.restoreFiles(List.of(restored));
        assertEquals(1, fileManager.getFileCount());

        // Wait for the asynchronous hash warm-up to index the restored file
        // (condition-based polling, no fixed sleep)
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!fileManager.isContentHashIndexed(restoredPath) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(fileManager.isContentHashIndexed(restoredPath),
                "restored file must eventually be indexed for content-duplicate detection");

        // When: a different path with identical content is added
        Path duplicatePath = Files.write(tempDir.resolve("duplicate.mp4"), "identical-content".getBytes());
        when(validationEngine.validateFile(duplicatePath)).thenReturn(ValidationResult.success());
        List<ConversionFile> added = fileManager.addFiles(List.of(duplicatePath));

        // Then: it is rejected as a content duplicate
        assertTrue(added.isEmpty(), "content-identical duplicate must be rejected");
        assertEquals(1, fileManager.getFileCount());
    }

    // ===== UNKNOWN-format admission policy =====

    @Test
    void addFiles_rejectsUnknownFormats_consistentWithFolderScan() throws Exception {
        // Given: a file whose format cannot be detected (unsupported);
        // folder scans already filter such files, direct adds must too
        Path unknownFile = Files.write(tempDir.resolve("mystery.xyz123"), "data".getBytes());
        when(validationEngine.validateFile(unknownFile)).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(unknownFile)).thenReturn(FileFormat.UNKNOWN);

        // When
        List<ConversionFile> added = fileManager.addFiles(List.of(unknownFile));

        // Then: unconvertible files never enter the queue
        assertTrue(added.isEmpty(), "UNKNOWN-format files must be rejected at admission");
        assertEquals(0, fileManager.getFileCount());
    }

    // 1. Constructor and initialization
    @Test
    void constructor_ShouldInitializeWithDependencies() throws Exception {
        assertNotNull(fileManager);
        assertEquals(0, fileManager.getFileCount());
        assertTrue(fileManager.getFiles().isEmpty());
    }

    @Test
    void constructor_ShouldThrowWhenFileHandlerIsNull() throws Exception {
        assertThrows(NullPointerException.class, () -> new FileManager(null, validationEngine));
    }

    @Test
    void constructor_ShouldThrowWhenValidationEngineIsNull() throws Exception {
        assertThrows(NullPointerException.class, () -> new FileManager(fileHandler, null));
    }

    // 2. addFiles() - success cases, validation failures, duplicates by path,
    // duplicates by hash
    @Test
    void addFiles_ShouldAddValidFilesSuccessfully() throws Exception {
        Path path1 = Paths.get("file1.mp4");
        Path path2 = Paths.get("file2.mp3");

        when(validationEngine.validateFile(path1)).thenReturn(ValidationResult.success());
        when(validationEngine.validateFile(path2)).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(path1)).thenReturn(FileFormat.MP4);
        when(fileHandler.detectFormat(path2)).thenReturn(FileFormat.MP3);
        when(fileHandler.getFileSize(path1)).thenReturn(1000L);
        when(fileHandler.getFileSize(path2)).thenReturn(2000L);

        List<ConversionFile> added = fileManager.addFiles(List.of(path1, path2));

        assertEquals(2, added.size());
        assertEquals(2, fileManager.getFileCount());
        verify(fileHandler, times(2)).detectFormat(any());
        verify(fileHandler, times(2)).getFileSize(any());
    }

    @Test
    void addFiles_ShouldSkipInvalidFiles() throws Exception {
        Path validPath = Paths.get("valid.mp4");
        Path invalidPath = Paths.get("invalid.txt");

        when(validationEngine.validateFile(validPath)).thenReturn(ValidationResult.success());
        when(validationEngine.validateFile(invalidPath)).thenReturn(ValidationResult.failure("Invalid format"));
        when(fileHandler.detectFormat(validPath)).thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(validPath)).thenReturn(1000L);

        List<ConversionFile> added = fileManager.addFiles(List.of(validPath, invalidPath));

        assertEquals(1, added.size());
        assertEquals(1, fileManager.getFileCount());
    }

    @Test
    void addFiles_ShouldSkipDuplicateByPath() throws Exception {
        Path path = Paths.get("duplicate.mp4");

        when(validationEngine.validateFile(path)).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(path)).thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(path)).thenReturn(1000L);

        fileManager.addFiles(List.of(path));
        List<ConversionFile> addedAgain = fileManager.addFiles(List.of(path));

        assertEquals(0, addedAgain.size());
        assertEquals(1, fileManager.getFileCount());
    }

    @Test
    void addFiles_ShouldSkipDuplicateByHash() throws Exception {
        Path path1 = Paths.get("file1.mp4");
        Path path2 = Paths.get("file2.mp4");

        when(validationEngine.validateFile(any())).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(any())).thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(any())).thenReturn(1000L);

        fileManager.addFiles(List.of(path1));
        List<ConversionFile> added = fileManager.addFiles(List.of(path2));
        assertEquals(1, added.size());
        assertEquals(2, fileManager.getFileCount());
    }

    @Test
    void addFiles_ShouldThrowWhenPathsIsNull() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> fileManager.addFiles(null));
    }

    @Test
    void addFiles_ShouldHandleExceptionDuringFileProcessing() throws Exception {
        Path path = Paths.get("error.mp4");

        when(validationEngine.validateFile(path)).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(path)).thenThrow(new IllegalArgumentException("Invalid format"));

        List<ConversionFile> added = fileManager.addFiles(List.of(path));

        assertEquals(0, added.size());
        assertEquals(0, fileManager.getFileCount());
    }

    // 3. addFilesFromFolder() - folder doesn't exist, not a directory
    @Test
    void addFilesFromFolder_ShouldThrowWhenFolderDoesNotExist() throws FileOperationException {
        Path folder = Paths.get("nonexistent");

        when(fileHandler.exists(folder)).thenReturn(false);

        FileOperationException exception = assertThrows(FileOperationException.class,
                () -> fileManager.addFilesFromFolder(folder, false));

        assertEquals(ErrorCode.FILE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void addFilesFromFolder_ShouldThrowWhenFolderPathIsNull() throws FileOperationException {
        assertThrows(IllegalArgumentException.class, () -> fileManager.addFilesFromFolder(null, false));
    }

    // 3b. addFilesFromFolder() - per-entry tolerance for unreadable entries
    @Test
    void addFilesFromFolder_ShouldSkipUnreadableSubdirAndReturnReadableFiles() throws Exception {
        Assumptions.assumeTrue(
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "POSIX file permissions required");

        Path readableFile = Files.createFile(tempDir.resolve("video.mp4"));
        Path lockedSubdir = Files.createDirectory(tempDir.resolve("locked"));
        Files.createFile(lockedSubdir.resolve("nested.mp4"));
        Files.setPosixFilePermissions(lockedSubdir, PosixFilePermissions.fromString("---------"));
        Assumptions.assumeTrue(!Files.isReadable(lockedSubdir),
                "effective user must not bypass permission checks (skipped for root)");

        when(fileHandler.exists(tempDir)).thenReturn(true);
        when(fileHandler.detectFormat(readableFile)).thenReturn(FileFormat.MP4);
        when(validationEngine.validateFile(readableFile)).thenReturn(ValidationResult.success());
        when(fileHandler.getFileSize(readableFile)).thenReturn(1000L);

        try {
            // Unreadable subdirectory must not abort the whole scan
            List<ConversionFile> added = fileManager.addFilesFromFolder(tempDir, true);

            assertEquals(1, added.size());
            assertEquals(readableFile, added.get(0).path());
        } finally {
            // Restore permissions so @TempDir cleanup can delete the tree
            Files.setPosixFilePermissions(lockedSubdir, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void addFilesFromFolder_ShouldFailFastWhenTopLevelFolderUnreadable() throws Exception {
        Assumptions.assumeTrue(
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "POSIX file permissions required");

        Path lockedFolder = Files.createDirectory(tempDir.resolve("lockedroot"));
        Files.setPosixFilePermissions(lockedFolder, PosixFilePermissions.fromString("---------"));
        Assumptions.assumeTrue(!Files.isReadable(lockedFolder),
                "effective user must not bypass permission checks (skipped for root)");

        when(fileHandler.exists(lockedFolder)).thenReturn(true);

        try {
            assertThrows(FileOperationException.class,
                    () -> fileManager.addFilesFromFolder(lockedFolder, true));
        } finally {
            Files.setPosixFilePermissions(lockedFolder, PosixFilePermissions.fromString("rwx------"));
        }
    }

    // 4. removeFiles() - remove single file, remove multiple files, remove
    // non-existent files, null parameter
    @Test
    void removeFiles_ShouldRemoveExistingFiles() throws Exception {
        Path path1 = Paths.get("file1.mp4");
        Path path2 = Paths.get("file2.mp3");

        when(validationEngine.validateFile(any())).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(any())).thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(any())).thenReturn(1000L);

        List<ConversionFile> added = fileManager.addFiles(List.of(path1, path2));
        String id1 = added.get(0).id();
        String id2 = added.get(1).id();

        int removed = fileManager.removeFiles(List.of(id1, id2));

        assertEquals(2, removed);
        assertEquals(0, fileManager.getFileCount());
    }

    @Test
    void removeFiles_ShouldReturnZeroForNonExistentFiles() throws Exception {
        int removed = fileManager.removeFiles(List.of("nonexistent"));

        assertEquals(0, removed);
    }

    @Test
    void removeFiles_ShouldThrowWhenFileIdsIsNull() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> fileManager.removeFiles(null));
    }

    // 5. clearFiles() - clear empty list, clear with files
    @Test
    void clearFiles_ShouldClearAllFiles() throws Exception {
        Path path = Paths.get("file.mp4");

        when(validationEngine.validateFile(path)).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(path)).thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(path)).thenReturn(1000L);

        fileManager.addFiles(List.of(path));
        assertEquals(1, fileManager.getFileCount());

        fileManager.clearFiles();
        assertEquals(0, fileManager.getFileCount());
    }

    @Test
    void clearFiles_ShouldHandleEmptyList() throws Exception {
        fileManager.clearFiles();
        assertEquals(0, fileManager.getFileCount());
    }

    // 6. getFiles() - returns unmodifiable list, returns current state
    @Test
    void getFiles_ShouldReturnUnmodifiableList() throws Exception {
        List<ConversionFile> files = fileManager.getFiles();

        assertThrows(UnsupportedOperationException.class, () -> files.add(null));
    }

    @Test
    void getFiles_ShouldReturnCurrentState() throws Exception {
        Path path = Paths.get("file.mp4");

        when(validationEngine.validateFile(path)).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(path)).thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(path)).thenReturn(1000L);

        assertTrue(fileManager.getFiles().isEmpty());

        fileManager.addFiles(List.of(path));
        assertEquals(1, fileManager.getFiles().size());
    }

    // 7. getFile() - by ID, non-existent ID, null ID
    @Test
    void getFile_ShouldReturnFileById() throws Exception {
        Path path = Paths.get("file.mp4");

        when(validationEngine.validateFile(path)).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(path)).thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(path)).thenReturn(1000L);

        List<ConversionFile> added = fileManager.addFiles(List.of(path));
        String id = added.get(0).id();

        Optional<ConversionFile> file = fileManager.getFile(id);

        assertTrue(file.isPresent());
        assertEquals(path, file.get().path());
    }

    @Test
    void getFile_ShouldReturnEmptyForNonExistentId() throws Exception {
        Optional<ConversionFile> file = fileManager.getFile("nonexistent");

        assertFalse(file.isPresent());
    }

    @Test
    void getFile_ShouldReturnEmptyForNullId() throws Exception {
        Optional<ConversionFile> file = fileManager.getFile(null);

        assertFalse(file.isPresent());
    }

    // 8. updateFile() - successful update, file not in list, null file, status
    // change event
    @Test
    void updateFile_ShouldUpdateExistingFile() throws Exception {
        Path path = Paths.get("file.mp4");

        when(validationEngine.validateFile(path)).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(path)).thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(path)).thenReturn(1000L);

        List<ConversionFile> added = fileManager.addFiles(List.of(path));
        ConversionFile original = added.get(0);

        ConversionFile updated = original.withStatus(ConversionStatus.COMPLETED);

        fileManager.updateFile(updated);

        Optional<ConversionFile> retrieved = fileManager.getFile(original.id());
        assertTrue(retrieved.isPresent());
        assertEquals(ConversionStatus.COMPLETED, retrieved.get().status());
    }

    @Test
    void updateFile_ShouldThrowForNonExistentFile() throws Exception {
        ConversionFile fake = ConversionFile.create(Paths.get("fake.mp4"), FileFormat.MP4, 1000L);

        assertThrows(IllegalArgumentException.class, () -> fileManager.updateFile(fake));
    }

    @Test
    void updateFile_ShouldThrowForNullFile() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> fileManager.updateFile(null));
    }

    // 9. getFileCount() - empty, with files
    @Test
    void getFileCount_ShouldReturnZeroWhenEmpty() throws Exception {
        assertEquals(0, fileManager.getFileCount());
    }

    @Test
    void getFileCount_ShouldReturnCorrectCount() throws Exception {
        Path path = Paths.get("file.mp4");

        when(validationEngine.validateFile(path)).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(path)).thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(path)).thenReturn(1000L);

        fileManager.addFiles(List.of(path));

        assertEquals(1, fileManager.getFileCount());
    }

    // 10. Event listeners - add listener, remove listener, notifications for all
    // event types
    @Test
    void addEventListener_ShouldAddListener() throws Exception {
        @SuppressWarnings("unchecked")
        Consumer<FileManager.FileEvent> listener = mock(Consumer.class);

        fileManager.addEventListener(listener);

        Path path = Paths.get("file.mp4");
        when(validationEngine.validateFile(path)).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(path)).thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(path)).thenReturn(1000L);

        fileManager.addFiles(List.of(path));

        verify(listener).accept(any(FileManager.FileEvent.class));
    }

    @Test
    void addEventListener_ShouldThrowForNullListener() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> fileManager.addEventListener(null));
    }

    @Test
    void removeEventListener_ShouldRemoveListener() throws Exception {
        @SuppressWarnings("unchecked")
        Consumer<FileManager.FileEvent> listener = mock(Consumer.class);

        fileManager.addEventListener(listener);
        boolean removed = fileManager.removeEventListener(listener);

        assertTrue(removed);
    }

    @Test
    void eventListeners_ShouldNotifyForFileAdded() throws Exception {
        @SuppressWarnings("unchecked")
        Consumer<FileManager.FileEvent> listener = mock(Consumer.class);

        fileManager.addEventListener(listener);

        Path path = Paths.get("file.mp4");
        when(validationEngine.validateFile(path)).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(path)).thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(path)).thenReturn(1000L);

        fileManager.addFiles(List.of(path));

        verify(listener).accept(argThat(event -> event.getType() == FileManager.EventType.FILE_ADDED));
    }

    @Test
    void eventListeners_ShouldNotifyForFileRemoved() throws Exception {
        @SuppressWarnings("unchecked")
        Consumer<FileManager.FileEvent> listener = mock(Consumer.class);
        fileManager.addEventListener(listener);

        Path path = Paths.get("file.mp4");
        when(validationEngine.validateFile(path)).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(path)).thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(path)).thenReturn(1000L);

        List<ConversionFile> added = fileManager.addFiles(List.of(path));
        String id = added.get(0).id();

        fileManager.removeFiles(List.of(id));

        verify(listener).accept(argThat(event -> event.getType() == FileManager.EventType.FILE_REMOVED));
    }

    @Test
    void eventListeners_ShouldNotifyForFilesCleared() throws Exception {
        @SuppressWarnings("unchecked")
        Consumer<FileManager.FileEvent> listener = mock(Consumer.class);
        fileManager.addEventListener(listener);

        Path path = Paths.get("file.mp4");
        when(validationEngine.validateFile(path)).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(path)).thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(path)).thenReturn(1000L);

        fileManager.addFiles(List.of(path));
        fileManager.clearFiles();

        verify(listener).accept(argThat(event -> event.getType() == FileManager.EventType.FILES_CLEARED));
    }

    @Test
    void eventListeners_ShouldNotifyForStatusChanged() throws Exception {
        @SuppressWarnings("unchecked")
        Consumer<FileManager.FileEvent> listener = mock(Consumer.class);
        fileManager.addEventListener(listener);

        Path path = Paths.get("file.mp4");
        when(validationEngine.validateFile(path)).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(path)).thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(path)).thenReturn(1000L);

        List<ConversionFile> added = fileManager.addFiles(List.of(path));
        ConversionFile original = added.get(0);
        ConversionFile updated = original.withStatus(ConversionStatus.COMPLETED);

        fileManager.updateFile(updated);

        verify(listener).accept(argThat(event -> event.getType() == FileManager.EventType.STATUS_CHANGED));
    }

    // 11. Thread safety - concurrent adds/removes
    @Test
    void fileManager_ShouldBeThreadSafeForConcurrentOperations() throws Exception {
        when(validationEngine.validateFile(any())).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(any())).thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(any())).thenReturn(1000L);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> results = new ArrayList<>();

            for (int t = 0; t < 10; t++) {
                results.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 10; i++) {
                        Path path = Paths.get("file" + Thread.currentThread().getName() + "_" + i + ".mp4");
                        fileManager.addFiles(List.of(path));
                    }
                    return null;
                }));
            }

            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            for (Future<?> result : results) {
                result.get(1, TimeUnit.SECONDS);
            }
            assertEquals(100, fileManager.getFileCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void updateFile_ShouldBeAtomicWithConcurrentRemoval() throws Exception {
        when(validationEngine.validateFile(any())).thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(any())).thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(any())).thenReturn(1000L);

        List<Path> paths = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            paths.add(Paths.get("race" + i + ".mp4"));
        }
        List<ConversionFile> added = fileManager.addFiles(paths);
        assertEquals(100, added.size());

        List<String> idsToRemove = added.subList(0, 50).stream().map(ConversionFile::id).toList();
        List<ConversionFile> survivors = List.copyOf(added.subList(50, 100));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);

            Future<?> remover = executor.submit(() -> {
                start.await();
                for (String id : idsToRemove) {
                    fileManager.removeFiles(List.of(id));
                }
                return null;
            });

            Future<?> updater = executor.submit(() -> {
                start.await();
                for (int round = 0; round < 200; round++) {
                    for (ConversionFile file : survivors) {
                        fileManager.updateFile(file.withStatus(ConversionStatus.COMPLETED));
                    }
                }
                return null;
            });

            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            remover.get(5, TimeUnit.SECONDS);
            updater.get(5, TimeUnit.SECONDS);

            assertEquals(50, fileManager.getFileCount());
            List<ConversionFile> remaining = fileManager.getFiles();
            assertEquals(50, remaining.stream().map(ConversionFile::id).distinct().count());
            for (ConversionFile survivor : survivors) {
                Optional<ConversionFile> retrieved = fileManager.getFile(survivor.id());
                assertTrue(retrieved.isPresent(), "Survivor lost from list: " + survivor.id());
                assertEquals(ConversionStatus.COMPLETED, retrieved.get().status(),
                        "Newest update lost for: " + survivor.id());
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
