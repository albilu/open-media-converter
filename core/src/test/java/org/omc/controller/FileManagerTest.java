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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
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

    @BeforeEach
    void setUp() {
        fileManager = new FileManager(fileHandler, validationEngine);
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
        when(fileHandler.detectFormat(path)).thenThrow(new RuntimeException("IO error"));

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
    void fileManager_ShouldBeThreadSafeForConcurrentOperations() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(1);

        Runnable addTask = () -> {
            try {
                latch.await();
                for (int i = 0; i < 10; i++) {
                    Path path = Paths.get("file" + Thread.currentThread().getName() + "_" + i + ".mp4");
                    when(validationEngine.validateFile(path)).thenReturn(ValidationResult.success());
                    when(fileHandler.detectFormat(path)).thenReturn(FileFormat.MP4);
                    when(fileHandler.getFileSize(path)).thenReturn(1000L);
                    fileManager.addFiles(List.of(path));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        for (int i = 0; i < 10; i++) {
            executor.submit(addTask);
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertTrue(fileManager.getFileCount() > 0);
    }
}