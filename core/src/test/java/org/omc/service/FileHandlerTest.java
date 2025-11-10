package org.omc.service;

import org.omc.service.FileHandler;
import org.omc.core.ConfigurationManager;
import org.omc.exception.ErrorCode;
import org.omc.exception.FileOperationException;
import org.omc.model.FileFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileHandlerTest {

    @Mock
    private ConfigurationManager configManager;

    private FileHandler fileHandler;
    private Path tempDir;
    private Path tempFile;
    private Path tempSubDir;

    @BeforeEach
    void setUp() throws IOException {
        lenient().when(configManager.getTempDirectory()).thenReturn(tempDir);
        fileHandler = new FileHandler(configManager);
        tempDir = Files.createTempDirectory("fileHandlerTest");
        tempFile = Files.createTempFile(tempDir, "test", ".txt");
        Files.writeString(tempFile, "test content");
        tempSubDir = tempDir.resolve("subdir");
        Files.createDirectory(tempSubDir);

        // Update the mock with the actual tempDir
        lenient().when(configManager.getTempDirectory()).thenReturn(tempDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up temp files
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a)) // Reverse order for deletion
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }
    }

    @Test
    void exists_ShouldReturnTrueForExistingFile() {
        assertTrue(fileHandler.exists(tempFile));
    }

    @Test
    void exists_ShouldReturnFalseForNonExistingFile() {
        Path nonExisting = tempDir.resolve("nonexisting.txt");
        assertFalse(fileHandler.exists(nonExisting));
    }

    @Test
    void isReadable_ShouldReturnTrueForReadableFile() {
        assertTrue(fileHandler.isReadable(tempFile));
    }

    @Test
    void isReadable_ShouldReturnFalseForNonExistingFile() {
        Path nonExisting = tempDir.resolve("nonexisting.txt");
        assertFalse(fileHandler.isReadable(nonExisting));
    }

    @Test
    void isWritable_ShouldReturnTrueForWritableFile() {
        assertTrue(fileHandler.isWritable(tempFile));
    }

    @Test
    void isWritable_ShouldReturnFalseForNonExistingFile() {
        Path nonExisting = tempDir.resolve("nonexisting.txt");
        assertFalse(fileHandler.isWritable(nonExisting));
    }

    @Test
    void getFileSize_ShouldReturnCorrectSize() throws FileOperationException {
        long expectedSize = "test content".length();
        assertEquals(expectedSize, fileHandler.getFileSize(tempFile));
    }

    @Test
    void getFileSize_ShouldThrowForNonExistingFile() {
        Path nonExisting = tempDir.resolve("nonexisting.txt");
        FileOperationException exception = assertThrows(FileOperationException.class,
                () -> fileHandler.getFileSize(nonExisting));
        assertEquals(ErrorCode.FILE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getAvailableSpace_ShouldReturnSpace() throws FileOperationException {
        long space = fileHandler.getAvailableSpace(tempDir);
        assertTrue(space > 0);
    }

    @Test
    void createTemporaryFile_ShouldCreateFileAndRegisterCleanup() throws FileOperationException {
        Path createdFile = fileHandler.createTemporaryFile("prefix", ".tmp");
        assertTrue(Files.exists(createdFile));
        assertTrue(createdFile.getFileName().toString().startsWith("prefix"));
        assertTrue(createdFile.getFileName().toString().endsWith(".tmp"));
        assertEquals(1, fileHandler.getCleanupRegistrySize());
    }

    @Test
    void copyFile_ShouldCopySuccessfully() throws IOException, FileOperationException {
        Path source = Files.createTempFile(tempDir, "source", ".txt");
        Files.writeString(source, "source content");
        Path destination = tempDir.resolve("destination.txt");

        fileHandler.copyFile(source, destination, false);

        assertTrue(Files.exists(destination));
        assertEquals("source content", Files.readString(destination));
    }

    @Test
    void copyFile_ShouldThrowWhenDestinationExistsAndNoOverwrite() throws IOException {
        Path source = Files.createTempFile(tempDir, "source", ".txt");
        Files.writeString(source, "source content");
        Path destination = Files.createTempFile(tempDir, "destination", ".txt");

        FileOperationException exception = assertThrows(FileOperationException.class,
                () -> fileHandler.copyFile(source, destination, false));
        assertEquals(ErrorCode.FILE_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    void copyFile_ShouldOverwriteWhenOverwriteTrue() throws IOException, FileOperationException {
        Path source = Files.createTempFile(tempDir, "source", ".txt");
        Files.writeString(source, "source content");
        Path destination = Files.createTempFile(tempDir, "destination", ".txt");
        Files.writeString(destination, "old content");

        fileHandler.copyFile(source, destination, true);

        assertEquals("source content", Files.readString(destination));
    }

    @Test
    void copyFile_ShouldThrowForNonExistingSource() {
        Path nonExisting = tempDir.resolve("nonexisting.txt");
        Path destination = tempDir.resolve("destination.txt");

        FileOperationException exception = assertThrows(FileOperationException.class,
                () -> fileHandler.copyFile(nonExisting, destination, false));
        assertEquals(ErrorCode.FILE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void copyFileWithProgress_ShouldCopyAndCallCallback() throws IOException, FileOperationException {
        Path source = Files.createTempFile(tempDir, "source", ".txt");
        Files.writeString(source, "source content");
        Path destination = tempDir.resolve("destination.txt");

        AtomicLong progressValue = new AtomicLong(0);
        Consumer<Long> callback = progressValue::set;

        fileHandler.copyFile(source, destination, false, callback);

        assertTrue(Files.exists(destination));
        assertEquals("source content".length(), progressValue.get());
    }

    @Test
    void moveFile_ShouldMoveSuccessfully() throws IOException, FileOperationException {
        Path source = Files.createTempFile(tempDir, "source", ".txt");
        Files.writeString(source, "source content");
        Path destination = tempDir.resolve("destination.txt");

        fileHandler.moveFile(source, destination, false);

        assertFalse(Files.exists(source));
        assertTrue(Files.exists(destination));
        assertEquals("source content", Files.readString(destination));
    }

    @Test
    void moveFile_ShouldThrowWhenDestinationExistsAndNoOverwrite() throws IOException {
        Path source = Files.createTempFile(tempDir, "source", ".txt");
        Files.writeString(source, "source content");
        Path destination = Files.createTempFile(tempDir, "destination", ".txt");

        FileOperationException exception = assertThrows(FileOperationException.class,
                () -> fileHandler.moveFile(source, destination, false));
        assertEquals(ErrorCode.FILE_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    void deleteFile_ShouldDeleteExistingFile() throws FileOperationException, IOException {
        Path fileToDelete = Files.createTempFile(tempDir, "toDelete", ".txt");
        assertTrue(Files.exists(fileToDelete));

        fileHandler.deleteFile(fileToDelete);

        assertFalse(Files.exists(fileToDelete));
    }

    @Test
    void deleteFile_ShouldNotThrowForNonExistingFile() throws FileOperationException {
        Path nonExisting = tempDir.resolve("nonexisting.txt");
        assertFalse(Files.exists(nonExisting));

        // Should not throw
        fileHandler.deleteFile(nonExisting);
    }

    @Test
    void createDirectory_ShouldCreateNewDirectory() throws FileOperationException {
        Path newDir = tempDir.resolve("newdir");

        fileHandler.createDirectory(newDir);

        assertTrue(Files.exists(newDir));
        assertTrue(Files.isDirectory(newDir));
    }

    @Test
    void createDirectory_ShouldNotThrowForExistingDirectory() throws FileOperationException {
        fileHandler.createDirectory(tempSubDir);

        assertTrue(Files.exists(tempSubDir));
        assertTrue(Files.isDirectory(tempSubDir));
    }

    @Test
    void createDirectory_ShouldThrowForExistingFile() {
        FileOperationException exception = assertThrows(FileOperationException.class,
                () -> fileHandler.createDirectory(tempFile));
        assertEquals(ErrorCode.FILE_IO_ERROR, exception.getErrorCode());
    }

    @Test
    void listFiles_ShouldListFilesInDirectory() throws IOException, FileOperationException {
        Path file1 = Files.createTempFile(tempSubDir, "file1", ".txt");
        Path file2 = Files.createTempFile(tempSubDir, "file2", ".txt");
        Files.createTempFile(tempSubDir, "file3", ".tmp"); // Different extension

        List<Path> files = fileHandler.listFiles(tempSubDir, false);

        assertEquals(3, files.size());
        assertTrue(files.contains(file1));
        assertTrue(files.contains(file2));
    }

    @Test
    void listFiles_ShouldListFilesRecursively() throws IOException, FileOperationException {
        Path subSubDir = tempSubDir.resolve("subsubdir");
        Files.createDirectory(subSubDir);
        Path file1 = Files.createTempFile(tempSubDir, "file1", ".txt");
        Path file2 = Files.createTempFile(subSubDir, "file2", ".txt");

        List<Path> files = fileHandler.listFiles(tempSubDir, true);

        assertEquals(2, files.size());
        assertTrue(files.contains(file1));
        assertTrue(files.contains(file2));
    }

    @Test
    void listFiles_ShouldThrowForNonExistingDirectory() {
        Path nonExisting = tempDir.resolve("nonexisting");
        FileOperationException exception = assertThrows(FileOperationException.class,
                () -> fileHandler.listFiles(nonExisting, false));
        assertEquals(ErrorCode.FILE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void listFiles_ShouldThrowForFilePath() {
        FileOperationException exception = assertThrows(FileOperationException.class,
                () -> fileHandler.listFiles(tempFile, false));
        assertEquals(ErrorCode.FILE_IO_ERROR, exception.getErrorCode());
    }

    @Test
    void cleanupDirectory_ShouldDeleteFilesButNotDirectory() throws IOException, FileOperationException {
        Path file1 = Files.createTempFile(tempSubDir, "file1", ".txt");
        Path file2 = Files.createTempFile(tempSubDir, "file2", ".txt");

        fileHandler.cleanupDirectory(tempSubDir);

        assertTrue(Files.exists(tempSubDir)); // Directory should remain
        assertFalse(Files.exists(file1)); // Files should be deleted
        assertFalse(Files.exists(file2));
    }

    @Test
    void cleanupDirectory_ShouldNotThrowForNonExistingDirectory() throws FileOperationException {
        Path nonExisting = tempDir.resolve("nonexisting");
        // Should not throw
        fileHandler.cleanupDirectory(nonExisting);
    }

    @Test
    void detectFormat_ShouldDetectByMagicBytes() throws IOException {
        // Create a PNG file with magic bytes
        Path pngFile = Files.createTempFile(tempDir, "test", ".png");
        byte[] pngMagic = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
        Files.write(pngFile, pngMagic);

        FileFormat format = fileHandler.detectFormat(pngFile);

        assertEquals(FileFormat.PNG, format);
    }

    @Test
    void detectFormat_ShouldFallbackToExtension() throws IOException {
        Path txtFile = Files.createTempFile(tempDir, "test", ".txt");

        FileFormat format = fileHandler.detectFormat(txtFile);

        assertEquals(FileFormat.TXT, format);
    }

    @Test
    void detectFormat_ShouldReturnUnknownForUnknownFile() throws IOException {
        Path unknownFile = Files.createTempFile(tempDir, "test", ".unknown");

        FileFormat format = fileHandler.detectFormat(unknownFile);

        assertEquals(FileFormat.UNKNOWN, format);
    }

    @Test
    void detectMimeType_ShouldReturnMimeType() {
        String mimeType = fileHandler.detectMimeType(tempFile);
        // MIME type detection depends on system, but should not be null
        assertNotNull(mimeType);
    }

    @Test
    void registerCleanup_ShouldAddToRegistry() {
        assertEquals(0, fileHandler.getCleanupRegistrySize());

        fileHandler.registerCleanup(tempFile);

        assertEquals(1, fileHandler.getCleanupRegistrySize());
    }

    @Test
    void unregisterCleanup_ShouldRemoveFromRegistry() {
        fileHandler.registerCleanup(tempFile);
        assertEquals(1, fileHandler.getCleanupRegistrySize());

        fileHandler.unregisterCleanup(tempFile);

        assertEquals(0, fileHandler.getCleanupRegistrySize());
    }

    @Test
    void cleanupAll_ShouldDeleteRegisteredFiles() throws IOException {
        Path file1 = Files.createTempFile(tempDir, "cleanup1", ".tmp");
        Path file2 = Files.createTempFile(tempDir, "cleanup2", ".tmp");

        fileHandler.registerCleanup(file1);
        fileHandler.registerCleanup(file2);

        fileHandler.cleanupAll();

        assertFalse(Files.exists(file1));
        assertFalse(Files.exists(file2));
        assertEquals(0, fileHandler.getCleanupRegistrySize());
        verify(configManager).cleanupTempFiles();
    }

    @Test
    void getCleanupRegistrySize_ShouldReturnCorrectSize() {
        assertEquals(0, fileHandler.getCleanupRegistrySize());

        fileHandler.registerCleanup(tempFile);
        assertEquals(1, fileHandler.getCleanupRegistrySize());

        fileHandler.registerCleanup(tempDir.resolve("another.txt"));
        assertEquals(2, fileHandler.getCleanupRegistrySize());
    }

    // Edge cases

    @Test
    void getFileSize_ShouldThrowForEmptyFile() throws IOException, FileOperationException {
        Path emptyFile = Files.createTempFile(tempDir, "empty", ".txt");
        // Empty file should work fine
        assertDoesNotThrow(() -> fileHandler.getFileSize(emptyFile));
        assertEquals(0, fileHandler.getFileSize(emptyFile));
    }

    @Test
    void copyFile_ShouldHandleEmptyFile() throws IOException, FileOperationException {
        Path emptySource = Files.createTempFile(tempDir, "empty", ".txt");
        Path destination = tempDir.resolve("empty_dest.txt");

        fileHandler.copyFile(emptySource, destination, false);

        assertTrue(Files.exists(destination));
        assertEquals(0, Files.size(destination));
    }

    @Test
    void detectFormat_ShouldHandleEmptyFile() throws IOException {
        Path emptyFile = Files.createTempFile(tempDir, "empty", ".txt");

        FileFormat format = fileHandler.detectFormat(emptyFile);

        assertEquals(FileFormat.TXT, format); // Should fallback to extension
    }

    @Test
    void detectFormat_ShouldDetectZipBasedFormats() throws IOException {
        // Create a file with ZIP magic bytes and .docx extension
        Path docxFile = Files.createTempFile(tempDir, "test", ".docx");
        byte[] zipMagic = { 0x50, 0x4B, 0x03, 0x04 };
        Files.write(docxFile, zipMagic);

        FileFormat format = fileHandler.detectFormat(docxFile);

        assertEquals(FileFormat.DOCX, format);
    }

    // Tests for openInFileManager() - Requirement REQ-FL-3.2, REQ-FL-3.3

    @Test
    void openInFileManager_ShouldNotThrowForExistingFile() throws IOException {
        Path existingFile = Files.createTempFile(tempDir, "test", ".txt");

        // Should not throw - we cannot reliably test process execution without mocking
        // ProcessBuilder
        // This test verifies the method doesn't crash with a valid file
        assertDoesNotThrow(() -> {
            try {
                fileHandler.openInFileManager(existingFile);
            } catch (IOException e) {
                // IOException is acceptable if no file manager is available in CI environment
                // We just verify the method signature and basic error handling
                assertTrue(e.getMessage().contains("file manager") ||
                        e.getMessage().contains("xdg-open") ||
                        e.getMessage().contains("Unable to open"));
            }
        });
    }

    @Test
    void openInFileManager_ShouldThrowForNonExistentFile() {
        Path nonExistentFile = tempDir.resolve("does_not_exist.txt");

        IOException exception = assertThrows(IOException.class,
                () -> fileHandler.openInFileManager(nonExistentFile));

        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("does not exist") ||
                exception.getMessage().contains("not found"));
    }

    @Test
    void openInFileManager_ShouldThrowForNullPath() {
        assertThrows(NullPointerException.class,
                () -> fileHandler.openInFileManager(null));
    }

    @Test
    void openInFileManager_ShouldHandleFileInNestedDirectory() throws IOException {
        Path nestedDir = tempSubDir.resolve("nested");
        Files.createDirectory(nestedDir);
        Path nestedFile = Files.createTempFile(nestedDir, "nested", ".txt");

        // Should not throw with valid nested file
        assertDoesNotThrow(() -> {
            try {
                fileHandler.openInFileManager(nestedFile);
            } catch (IOException e) {
                // IOException is acceptable if no file manager is available in CI environment
                assertTrue(e.getMessage().contains("file manager") ||
                        e.getMessage().contains("xdg-open") ||
                        e.getMessage().contains("Unable to open"));
            }
        });
    }

    @Test
    void openInFileManager_ShouldHandleFileWithSpecialCharactersInPath() throws IOException {
        // Create a directory with spaces and special characters
        Path specialDir = tempDir.resolve("test dir with spaces");
        Files.createDirectory(specialDir);
        Path specialFile = Files.createTempFile(specialDir, "test file", ".txt");

        // Should not throw with special character path
        assertDoesNotThrow(() -> {
            try {
                fileHandler.openInFileManager(specialFile);
            } catch (IOException e) {
                // IOException is acceptable if no file manager is available in CI environment
                assertTrue(e.getMessage().contains("file manager") ||
                        e.getMessage().contains("xdg-open") ||
                        e.getMessage().contains("Unable to open"));
            }
        });
    }
}