package org.omc.util;

import org.omc.exception.ErrorCode;
import org.omc.exception.FileOperationException;

import java.io.IOException;
import java.nio.file.*;
import java.text.DecimalFormat;

/**
 * Utility class for file operations including copy, move, delete, and size
 * formatting.
 * All methods are static and provide comprehensive error handling.
 * 
 * Requirements: REQ-002.1, REQ-002.3
 */
public final class FileUtils {

    private static final long KB = 1024;
    private static final long MB = KB * 1024;
    private static final long GB = MB * 1024;
    private static final long TB = GB * 1024;

    // Private constructor to prevent instantiation
    private FileUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Copies a file from source to destination.
     *
     * @param sourcePath The source file path
     * @param destPath   The destination file path
     * @param overwrite  Whether to overwrite if destination exists
     * @throws FileOperationException if copy fails
     */
    public static void copyFile(String sourcePath, String destPath, boolean overwrite)
            throws FileOperationException {
        Path source = Paths.get(sourcePath);
        Path dest = Paths.get(destPath);

        if (!Files.exists(source)) {
            throw new FileOperationException(
                    "Source file does not exist",
                    ErrorCode.FILE_NOT_FOUND,
                    sourcePath);
        }

        if (!Files.isReadable(source)) {
            throw new FileOperationException(
                    "Source file is not readable",
                    ErrorCode.FILE_NOT_READABLE,
                    sourcePath);
        }

        try {
            if (overwrite) {
                Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(source, dest);
            }
        } catch (FileAlreadyExistsException e) {
            throw new FileOperationException(
                    "Destination file already exists",
                    ErrorCode.FILE_ALREADY_EXISTS,
                    destPath,
                    e);
        } catch (IOException e) {
            throw new FileOperationException(
                    "Failed to copy file",
                    ErrorCode.FILE_IO_ERROR,
                    sourcePath,
                    e);
        }
    }

    /**
     * Moves a file from source to destination.
     *
     * @param sourcePath The source file path
     * @param destPath   The destination file path
     * @param overwrite  Whether to overwrite if destination exists
     * @throws FileOperationException if move fails
     */
    public static void moveFile(String sourcePath, String destPath, boolean overwrite)
            throws FileOperationException {
        Path source = Paths.get(sourcePath);
        Path dest = Paths.get(destPath);

        if (!Files.exists(source)) {
            throw new FileOperationException(
                    "Source file does not exist",
                    ErrorCode.FILE_NOT_FOUND,
                    sourcePath);
        }

        try {
            if (overwrite) {
                Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, dest);
            }
        } catch (FileAlreadyExistsException e) {
            throw new FileOperationException(
                    "Destination file already exists",
                    ErrorCode.FILE_ALREADY_EXISTS,
                    destPath,
                    e);
        } catch (IOException e) {
            throw new FileOperationException(
                    "Failed to move file",
                    ErrorCode.FILE_IO_ERROR,
                    sourcePath,
                    e);
        }
    }

    /**
     * Deletes a file.
     *
     * @param filePath The file path to delete
     * @throws FileOperationException if delete fails
     */
    public static void deleteFile(String filePath) throws FileOperationException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            // File doesn't exist - nothing to delete (idempotent)
            return;
        }

        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new FileOperationException(
                    "Failed to delete file",
                    ErrorCode.FILE_IO_ERROR,
                    filePath,
                    e);
        }
    }

    /**
     * Deletes a file silently (no exception if file doesn't exist).
     *
     * @param filePath The file path to delete
     * @return true if file was deleted, false if it didn't exist or delete failed
     */
    public static boolean deleteFileSilently(String filePath) {
        try {
            deleteFile(filePath);
            return true;
        } catch (FileOperationException e) {
            return false;
        }
    }

    /**
     * Gets the size of a file in bytes.
     *
     * @param filePath The file path
     * @return The file size in bytes
     * @throws FileOperationException if size query fails
     */
    public static long getFileSize(String filePath) throws FileOperationException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new FileOperationException(
                    "File does not exist",
                    ErrorCode.FILE_NOT_FOUND,
                    filePath);
        }

        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new FileOperationException(
                    "Failed to get file size",
                    ErrorCode.FILE_IO_ERROR,
                    filePath,
                    e);
        }
    }

    /**
     * Formats a file size in bytes to human-readable string (e.g., "1.5 GB").
     *
     * @param bytes The size in bytes
     * @return The formatted size string
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 0) {
            return "0 B";
        }

        DecimalFormat df = new DecimalFormat("#,##0.##");

        if (bytes < KB) {
            return bytes + " B";
        } else if (bytes < MB) {
            return df.format(bytes / (double) KB) + " KB";
        } else if (bytes < GB) {
            return df.format(bytes / (double) MB) + " MB";
        } else if (bytes < TB) {
            return df.format(bytes / (double) GB) + " GB";
        } else {
            return df.format(bytes / (double) TB) + " TB";
        }
    }

    /**
     * Gets the available disk space at a given path in bytes.
     *
     * @param pathString The path to check
     * @return The available space in bytes
     * @throws FileOperationException if query fails
     */
    public static long getAvailableDiskSpace(String pathString) throws FileOperationException {
        Path path = Paths.get(pathString);

        try {
            FileStore store = Files.getFileStore(path);
            return store.getUsableSpace();
        } catch (IOException e) {
            throw new FileOperationException(
                    "Failed to get available disk space",
                    ErrorCode.FILE_IO_ERROR,
                    pathString,
                    e);
        }
    }

    /**
     * Checks if there is sufficient disk space for a file operation.
     *
     * @param pathString    The path where the operation will occur
     * @param requiredBytes The required space in bytes
     * @return true if sufficient space is available
     */
    public static boolean hasSufficientDiskSpace(String pathString, long requiredBytes) {
        try {
            long available = getAvailableDiskSpace(pathString);
            // Add 10% buffer for safety
            long required = (long) (requiredBytes * 1.1);
            return available >= required;
        } catch (FileOperationException e) {
            return false;
        }
    }

    /**
     * Checks if a file exists and is a regular file.
     *
     * @param filePath The file path
     * @return true if file exists and is a regular file
     */
    public static boolean isRegularFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            return Files.exists(path) && Files.isRegularFile(path);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if a file is readable.
     *
     * @param filePath The file path
     * @return true if file is readable
     */
    public static boolean isReadable(String filePath) {
        try {
            Path path = Paths.get(filePath);
            return Files.isReadable(path);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if a file is writable.
     *
     * @param filePath The file path
     * @return true if file is writable
     */
    public static boolean isWritable(String filePath) {
        try {
            Path path = Paths.get(filePath);
            return Files.isWritable(path);
        } catch (Exception e) {
            return false;
        }
    }
}
