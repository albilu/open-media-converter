package org.omc.service;

import org.omc.core.ConfigurationManager;
import org.omc.exception.ErrorCode;
import org.omc.exception.FileOperationException;
import org.omc.model.FileFormat;
import org.omc.model.FormatDetectionResult;
import org.omc.util.FileUtils;
import org.omc.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Service for file system operations including file queries, operations, format
 * detection,
 * and temporary file management.
 * 
 * Provides comprehensive file handling with cleanup registration, progress
 * callbacks,
 * and magic bytes-based format detection.
 * 
 * Requirements: REQ-002.1, REQ-002.3
 */
public class FileHandler {
    private static final Logger logger = LoggerFactory.getLogger(FileHandler.class);

    // Magic bytes for format detection (first 16 bytes of common formats)
    private static final Map<String, byte[][]> MAGIC_BYTES = new HashMap<>();

    // Registered files for cleanup on shutdown
    private final Set<Path> cleanupRegistry = ConcurrentHashMap.newKeySet();

    private final ConfigurationManager configManager;

    static {
        // Video formats
        MAGIC_BYTES.put("MP4", new byte[][] {
                { 0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70 }, // ftyp at offset 4
                { 0x00, 0x00, 0x00, 0x1c, 0x66, 0x74, 0x79, 0x70 }
        });
        MAGIC_BYTES.put("AVI", new byte[][] {
                { 0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x41, 0x56, 0x49, 0x20 } // RIFF....AVI
        });
        MAGIC_BYTES.put("MKV", new byte[][] {
                { 0x1A, 0x45, (byte) 0xDF, (byte) 0xA3 } // EBML header
        });
        MAGIC_BYTES.put("WEBM", new byte[][] {
                { 0x1A, 0x45, (byte) 0xDF, (byte) 0xA3 } // EBML header (same as MKV)
        });
        MAGIC_BYTES.put("MOV", new byte[][] {
                { 0x00, 0x00, 0x00, 0x14, 0x66, 0x74, 0x79, 0x70, 0x71, 0x74, 0x20, 0x20 } // ftyp qt
        });

        // Audio formats
        MAGIC_BYTES.put("MP3", new byte[][] {
                { (byte) 0xFF, (byte) 0xFB }, // MPEG-1 Layer 3
                { (byte) 0xFF, (byte) 0xF3 }, // MPEG-1 Layer 3
                { (byte) 0xFF, (byte) 0xF2 }, // MPEG-2 Layer 3
                { 0x49, 0x44, 0x33 } // ID3
        });
        MAGIC_BYTES.put("WAV", new byte[][] {
                { 0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x41, 0x56, 0x45 } // RIFF....WAVE
        });
        MAGIC_BYTES.put("FLAC", new byte[][] {
                { 0x66, 0x4C, 0x61, 0x43 } // fLaC
        });
        MAGIC_BYTES.put("OGG", new byte[][] {
                { 0x4F, 0x67, 0x67, 0x53 } // OggS
        });
        MAGIC_BYTES.put("M4A", new byte[][] {
                { 0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x4D, 0x34, 0x41 } // ftyp M4A
        });

        // Image formats
        MAGIC_BYTES.put("PNG", new byte[][] {
                { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A }
        });
        MAGIC_BYTES.put("JPG", new byte[][] {
                { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 },
                { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1 },
                { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE2 }
        });
        MAGIC_BYTES.put("GIF", new byte[][] {
                { 0x47, 0x49, 0x46, 0x38, 0x37, 0x61 }, // GIF87a
                { 0x47, 0x49, 0x46, 0x38, 0x39, 0x61 } // GIF89a
        });
        MAGIC_BYTES.put("WEBP", new byte[][] {
                { 0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50 } // RIFF....WEBP
        });
        MAGIC_BYTES.put("BMP", new byte[][] {
                { 0x42, 0x4D } // BM
        });
        MAGIC_BYTES.put("TIFF", new byte[][] {
                { 0x49, 0x49, 0x2A, 0x00 }, // Little-endian
                { 0x4D, 0x4D, 0x00, 0x2A } // Big-endian
        });

        // Document formats
        MAGIC_BYTES.put("PDF", new byte[][] {
                { 0x25, 0x50, 0x44, 0x46 } // %PDF
        });
        MAGIC_BYTES.put("ZIP", new byte[][] {
                { 0x50, 0x4B, 0x03, 0x04 }, // ZIP (used by DOCX, XLSX, ODT, etc.)
                { 0x50, 0x4B, 0x05, 0x06 },
                { 0x50, 0x4B, 0x07, 0x08 }
        });
        MAGIC_BYTES.put("RTF", new byte[][] {
                { 0x7B, 0x5C, 0x72, 0x74, 0x66 } // {\rtf
        });
    }

    /**
     * Creates a FileHandler with the given configuration manager.
     *
     * @param configManager The configuration manager
     */
    public FileHandler(ConfigurationManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Checks if a file exists.
     *
     * @param filePath The file path
     * @return true if file exists
     */
    public boolean exists(Path filePath) {
        return Files.exists(filePath);
    }

    /**
     * Checks if a file is readable.
     *
     * @param filePath The file path
     * @return true if file is readable
     */
    public boolean isReadable(Path filePath) {
        return Files.isReadable(filePath);
    }

    /**
     * Checks if a file is writable.
     *
     * @param filePath The file path
     * @return true if file is writable
     */
    public boolean isWritable(Path filePath) {
        return Files.isWritable(filePath);
    }

    /**
     * Gets the size of a file in bytes.
     *
     * @param filePath The file path
     * @return The file size in bytes
     * @throws FileOperationException if file doesn't exist or size query fails
     */
    public long getFileSize(Path filePath) throws FileOperationException {
        if (!exists(filePath)) {
            throw new FileOperationException(
                    "File does not exist",
                    ErrorCode.FILE_NOT_FOUND,
                    filePath.toString());
        }

        try {
            return Files.size(filePath);
        } catch (IOException e) {
            throw new FileOperationException(
                    "Failed to get file size",
                    ErrorCode.FILE_IO_ERROR,
                    filePath.toString(),
                    e);
        }
    }

    /**
     * Gets the available disk space at a given path in bytes.
     *
     * @param directory The directory path to check
     * @return The available space in bytes
     * @throws FileOperationException if query fails
     */
    public long getAvailableSpace(Path directory) throws FileOperationException {
        try {
            // If directory doesn't exist, check parent
            Path checkPath = directory;
            while (!Files.exists(checkPath)) {
                checkPath = checkPath.getParent();
                if (checkPath == null) {
                    throw new FileOperationException(
                            "Cannot determine disk space: no existing parent directory",
                            ErrorCode.FILE_NOT_FOUND,
                            directory.toString());
                }
            }

            FileStore store = Files.getFileStore(checkPath);
            return store.getUsableSpace();
        } catch (IOException e) {
            throw new FileOperationException(
                    "Failed to get available disk space",
                    ErrorCode.FILE_IO_ERROR,
                    directory.toString(),
                    e);
        }
    }

    /**
     * Creates a temporary file in the application's temp directory.
     *
     * @param prefix The file name prefix
     * @param suffix The file name suffix (extension)
     * @return The path to the created temporary file
     * @throws FileOperationException if creation fails
     */
    public Path createTemporaryFile(String prefix, String suffix) throws FileOperationException {
        try {
            Path tempDir = configManager.getTempDirectory();
            Path tempFile = Files.createTempFile(tempDir, prefix, suffix);
            registerCleanup(tempFile);
            logger.debug("Created temporary file: {}", tempFile);
            return tempFile;
        } catch (IOException e) {
            throw new FileOperationException(
                    "Failed to create temporary file",
                    ErrorCode.FILE_IO_ERROR,
                    configManager.getTempDirectory().toString(),
                    e);
        }
    }

    /**
     * Copies a file from source to destination.
     *
     * @param source      The source file path
     * @param destination The destination file path
     * @param overwrite   Whether to overwrite if destination exists
     * @throws FileOperationException if copy fails
     */
    public void copyFile(Path source, Path destination, boolean overwrite)
            throws FileOperationException {
        if (!exists(source)) {
            throw new FileOperationException(
                    "Source file does not exist",
                    ErrorCode.FILE_NOT_FOUND,
                    source.toString());
        }

        if (!isReadable(source)) {
            throw new FileOperationException(
                    "Source file is not readable",
                    ErrorCode.FILE_NOT_READABLE,
                    source.toString());
        }

        try {
            if (overwrite) {
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(source, destination);
            }
            logger.debug("Copied file from {} to {}", source, destination);
        } catch (FileAlreadyExistsException e) {
            throw new FileOperationException(
                    "Destination file already exists",
                    ErrorCode.FILE_ALREADY_EXISTS,
                    destination.toString(),
                    e);
        } catch (IOException e) {
            throw new FileOperationException(
                    "Failed to copy file",
                    ErrorCode.FILE_IO_ERROR,
                    source.toString(),
                    e);
        }
    }

    /**
     * Copies a file with progress callback.
     *
     * @param source           The source file path
     * @param destination      The destination file path
     * @param overwrite        Whether to overwrite if destination exists
     * @param progressCallback Callback invoked with bytes copied
     * @throws FileOperationException if copy fails
     */
    public void copyFile(Path source, Path destination, boolean overwrite,
            Consumer<Long> progressCallback) throws FileOperationException {
        if (!exists(source)) {
            throw new FileOperationException(
                    "Source file does not exist",
                    ErrorCode.FILE_NOT_FOUND,
                    source.toString());
        }

        try {
            long fileSize = getFileSize(source);
            long bytesCopied = 0;

            if (!overwrite && exists(destination)) {
                throw new FileOperationException(
                        "Destination file already exists",
                        ErrorCode.FILE_ALREADY_EXISTS,
                        destination.toString());
            }

            Files.copy(source, destination,
                    overwrite ? StandardCopyOption.REPLACE_EXISTING : StandardCopyOption.COPY_ATTRIBUTES);

            // Report progress after completion (for large files, streaming would be better)
            if (progressCallback != null) {
                progressCallback.accept(fileSize);
            }

            logger.debug("Copied file from {} to {} ({} bytes)", source, destination, fileSize);
        } catch (FileAlreadyExistsException e) {
            throw new FileOperationException(
                    "Destination file already exists",
                    ErrorCode.FILE_ALREADY_EXISTS,
                    destination.toString(),
                    e);
        } catch (IOException e) {
            throw new FileOperationException(
                    "Failed to copy file",
                    ErrorCode.FILE_IO_ERROR,
                    source.toString(),
                    e);
        }
    }

    /**
     * Moves a file from source to destination.
     *
     * @param source      The source file path
     * @param destination The destination file path
     * @param overwrite   Whether to overwrite if destination exists
     * @throws FileOperationException if move fails
     */
    public void moveFile(Path source, Path destination, boolean overwrite)
            throws FileOperationException {
        if (!exists(source)) {
            throw new FileOperationException(
                    "Source file does not exist",
                    ErrorCode.FILE_NOT_FOUND,
                    source.toString());
        }

        try {
            if (overwrite) {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, destination);
            }
            logger.debug("Moved file from {} to {}", source, destination);
        } catch (FileAlreadyExistsException e) {
            throw new FileOperationException(
                    "Destination file already exists",
                    ErrorCode.FILE_ALREADY_EXISTS,
                    destination.toString(),
                    e);
        } catch (IOException e) {
            throw new FileOperationException(
                    "Failed to move file",
                    ErrorCode.FILE_IO_ERROR,
                    source.toString(),
                    e);
        }
    }

    /**
     * Deletes a file.
     *
     * @param filePath The file path to delete
     * @throws FileOperationException if delete fails
     */
    public void deleteFile(Path filePath) throws FileOperationException {
        if (!exists(filePath)) {
            // File doesn't exist - nothing to delete (idempotent)
            logger.debug("File doesn't exist, nothing to delete: {}", filePath);
            return;
        }

        try {
            Files.delete(filePath);
            cleanupRegistry.remove(filePath);
            logger.debug("Deleted file: {}", filePath);
        } catch (IOException e) {
            throw new FileOperationException(
                    "Failed to delete file",
                    ErrorCode.FILE_IO_ERROR,
                    filePath.toString(),
                    e);
        }
    }

    /**
     * Creates a directory including any necessary parent directories.
     *
     * @param directory The directory path to create
     * @throws FileOperationException if creation fails
     */
    public void createDirectory(Path directory) throws FileOperationException {
        if (exists(directory)) {
            if (!Files.isDirectory(directory)) {
                throw new FileOperationException(
                        "Path exists but is not a directory",
                        ErrorCode.FILE_IO_ERROR,
                        directory.toString());
            }
            return;
        }

        try {
            Files.createDirectories(directory);
            logger.debug("Created directory: {}", directory);
        } catch (IOException e) {
            throw new FileOperationException(
                    "Failed to create directory",
                    ErrorCode.FILE_IO_ERROR,
                    directory.toString(),
                    e);
        }
    }

    /**
     * Lists all files in a directory.
     *
     * @param directory The directory path
     * @param recursive Whether to scan subdirectories recursively
     * @return List of file paths (not directories)
     * @throws FileOperationException if listing fails
     */
    public List<Path> listFiles(Path directory, boolean recursive) throws FileOperationException {
        if (!exists(directory)) {
            throw new FileOperationException(
                    "Directory does not exist",
                    ErrorCode.FILE_NOT_FOUND,
                    directory.toString());
        }

        if (!Files.isDirectory(directory)) {
            throw new FileOperationException(
                    "Path is not a directory",
                    ErrorCode.FILE_IO_ERROR,
                    directory.toString());
        }

        List<Path> files = new ArrayList<>();

        try {
            if (recursive) {
                try (Stream<Path> stream = Files.walk(directory)) {
                    stream.filter(Files::isRegularFile)
                            .forEach(files::add);
                }
            } else {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                    for (Path entry : stream) {
                        if (Files.isRegularFile(entry)) {
                            files.add(entry);
                        }
                    }
                }
            }

            logger.debug("Listed {} files in {}", files.size(), directory);
            return files;
        } catch (IOException e) {
            throw new FileOperationException(
                    "Failed to list files in directory",
                    ErrorCode.FILE_IO_ERROR,
                    directory.toString(),
                    e);
        }
    }

    /**
     * Cleans up all files in a directory.
     *
     * @param directory The directory path to clean
     * @throws FileOperationException if cleanup fails
     */
    public void cleanupDirectory(Path directory) throws FileOperationException {
        if (!exists(directory)) {
            logger.debug("Directory doesn't exist, nothing to clean: {}", directory);
            return;
        }

        if (!Files.isDirectory(directory)) {
            throw new FileOperationException(
                    "Path is not a directory",
                    ErrorCode.FILE_IO_ERROR,
                    directory.toString());
        }

        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        Files.delete(file);
                        logger.debug("Deleted file during cleanup: {}", file);
                    } catch (IOException e) {
                        logger.warn("Failed to delete file during cleanup: {}", file, e);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    // Don't delete the directory itself
                    return FileVisitResult.CONTINUE;
                }
            });

            logger.debug("Cleaned up directory: {}", directory);
        } catch (IOException e) {
            throw new FileOperationException(
                    "Failed to clean up directory",
                    ErrorCode.FILE_IO_ERROR,
                    directory.toString(),
                    e);
        }
    }

    /**
     * Detects the file format based on magic bytes and extension.
     * First tries magic bytes detection, then falls back to extension.
     *
     * @param filePath The file path
     * @return The detected file format, or UNKNOWN if not recognized
     */
    public FileFormat detectFormat(Path filePath) {
        return detectFormatWithConfidence(filePath).getFormat();
    }

    /**
     * Detects the file format with confidence score based on magic bytes and
     * extension.
     * First tries magic bytes detection, then falls back to extension.
     * 
     * Requirement 2.3: Format detection uses file extension and magic bytes with
     * confidence scoring.
     *
     * @param filePath The file path
     * @return Detection result with format and confidence score
     */
    public FormatDetectionResult detectFormatWithConfidence(Path filePath) {
        String extension = PathUtils.getExtension(filePath.toString());
        FileFormat extensionFormat = FileFormat.fromExtension(extension);

        // Try magic bytes first (more reliable)
        try {
            FileFormat magicFormat = detectFormatByMagicBytes(filePath);
            if (magicFormat != null && magicFormat != FileFormat.UNKNOWN) {
                // Check if magic bytes and extension agree
                if (magicFormat == extensionFormat) {
                    logger.debug("Detected format {} by both magic bytes and extension: {}", magicFormat, filePath);
                    return FormatDetectionResult.fromBoth(magicFormat);
                } else {
                    logger.debug("Detected format {} by magic bytes (extension suggests {}): {}",
                            magicFormat, extensionFormat, filePath);
                    return FormatDetectionResult.fromMagicBytes(magicFormat);
                }
            }
        } catch (Exception e) {
            logger.warn("Magic bytes detection failed for {}, falling back to extension", filePath, e);
        }

        // Fall back to extension-based detection
        if (extensionFormat != FileFormat.UNKNOWN) {
            logger.debug("Detected format {} by extension '{}': {}", extensionFormat, extension, filePath);
            return FormatDetectionResult.fromExtension(extensionFormat);
        }

        logger.debug("Could not detect format for: {}", filePath);
        return FormatDetectionResult.unknown();
    }

    /**
     * Detects format by reading magic bytes from file.
     *
     * @param filePath The file path
     * @return The detected format or null if not detected
     */
    private FileFormat detectFormatByMagicBytes(Path filePath) {
        if (!exists(filePath) || !isReadable(filePath)) {
            return null;
        }

        try {
            byte[] header = readFileHeader(filePath, 16);
            if (header.length == 0) {
                return null;
            }

            // Check against known magic bytes
            for (Map.Entry<String, byte[][]> entry : MAGIC_BYTES.entrySet()) {
                String formatName = entry.getKey();
                for (byte[] signature : entry.getValue()) {
                    if (matchesMagicBytes(header, signature)) {
                        // Convert format name to FileFormat enum
                        try {
                            return FileFormat.valueOf(formatName);
                        } catch (IllegalArgumentException e) {
                            // Format name doesn't match enum, try alternative names
                            return mapFormatName(formatName);
                        }
                    }
                }
            }

            // Special handling for ZIP-based formats (DOCX, XLSX, ODT, etc.)
            if (matchesMagicBytes(header, MAGIC_BYTES.get("ZIP")[0])) {
                return detectZipBasedFormat(filePath);
            }

            return FileFormat.UNKNOWN;
        } catch (Exception e) {
            logger.debug("Failed to detect format by magic bytes: {}", filePath, e);
            return null;
        }
    }

    /**
     * Reads the first N bytes of a file.
     *
     * @param filePath The file path
     * @param numBytes Number of bytes to read
     * @return Byte array containing the header
     */
    private byte[] readFileHeader(Path filePath, int numBytes) {
        try {
            long fileSize = getFileSize(filePath);
            int bytesToRead = (int) Math.min(numBytes, fileSize);
            try (var inputStream = Files.newInputStream(filePath)) {
                return inputStream.readNBytes(bytesToRead);
            }
        } catch (Exception e) {
            logger.debug("Failed to read file header: {}", filePath, e);
            return new byte[0];
        }
    }

    /**
     * Checks if file header matches magic bytes signature.
     *
     * @param header    The file header bytes
     * @param signature The magic bytes signature
     * @return true if matches
     */
    private boolean matchesMagicBytes(byte[] header, byte[] signature) {
        if (header.length < signature.length) {
            return false;
        }

        for (int i = 0; i < signature.length; i++) {
            // 0x00 in signature means "any byte"
            if (signature[i] != 0x00 && header[i] != signature[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Maps magic bytes format name to FileFormat enum.
     *
     * @param formatName The format name from magic bytes
     * @return The corresponding FileFormat
     */
    private FileFormat mapFormatName(String formatName) {
        return switch (formatName) {
            case "JPG" -> FileFormat.JPEG;
            case "TIFF" -> FileFormat.TIFF;
            default -> FileFormat.UNKNOWN;
        };
    }

    /**
     * Detects ZIP-based document formats by examining internal structure.
     *
     * @param filePath The file path
     * @return The detected format
     */
    private FileFormat detectZipBasedFormat(Path filePath) {
        String extension = PathUtils.getExtension(filePath.toString()).toUpperCase();

        // Use extension as hint for ZIP-based formats
        return switch (extension) {
            case "DOCX" -> FileFormat.DOCX;
            case "XLSX" -> FileFormat.XLSX;
            case "PPTX" -> FileFormat.PPTX;
            case "ODT" -> FileFormat.ODT;
            case "ODS" -> FileFormat.ODS;
            case "ODP" -> FileFormat.ODP;
            case "EPUB" -> FileFormat.EPUB;
            default -> FileFormat.UNKNOWN;
        };
    }

    /**
     * Detects MIME type of a file.
     *
     * @param filePath The file path
     * @return The MIME type string, or "application/octet-stream" if unknown
     */
    public String detectMimeType(Path filePath) {
        try {
            String mimeType = Files.probeContentType(filePath);
            if (mimeType != null) {
                return mimeType;
            }
        } catch (IOException e) {
            logger.debug("Failed to probe MIME type: {}", filePath, e);
        }

        // Fallback: map FileFormat to MIME type
        FileFormat format = detectFormat(filePath);
        return format.getMimeType();
    }

    /**
     * Registers a file for cleanup on shutdown.
     *
     * @param filePath The file path to register
     */
    public void registerCleanup(Path filePath) {
        cleanupRegistry.add(filePath);
        logger.debug("Registered file for cleanup: {}", filePath);
    }

    /**
     * Unregisters a file from cleanup.
     *
     * @param filePath The file path to unregister
     */
    public void unregisterCleanup(Path filePath) {
        cleanupRegistry.remove(filePath);
        logger.debug("Unregistered file from cleanup: {}", filePath);
    }

    /**
     * Cleans up all registered temporary files.
     * This should be called on application shutdown.
     */
    public void cleanupAll() {
        logger.info("Cleaning up {} registered temporary files", cleanupRegistry.size());

        for (Path filePath : cleanupRegistry) {
            try {
                if (exists(filePath)) {
                    Files.delete(filePath);
                    logger.debug("Deleted registered file: {}", filePath);
                }
            } catch (IOException e) {
                logger.warn("Failed to delete registered file: {}", filePath, e);
            }
        }

        cleanupRegistry.clear();

        // Also clean up temp directory
        configManager.cleanupTempFiles();
    }

    /**
     * Gets the number of files registered for cleanup.
     *
     * @return The count of registered files
     */
    public int getCleanupRegistrySize() {
        return cleanupRegistry.size();
    }

    /**
     * Opens the file's parent directory in the system file manager.
     * 
     * Task 63: Add openInFileManager() method to FileHandler
     * Requirements: REQ-FL-3.2, REQ-FL-3.3
     * 
     * Tries xdg-open first (standard on Linux), then falls back to common file
     * managers
     * (nautilus, dolphin, thunar, nemo, pcmanfm) if xdg-open is not available.
     * 
     * @param filePath The file path to open in file manager
     * @throws IOException if file doesn't exist or no file manager is available
     */
    public void openInFileManager(Path filePath) throws IOException {
        // Check file existence first
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist: " + filePath);
        }

        // Get the parent directory to open
        Path directory = filePath.getParent();
        if (directory == null) {
            throw new IOException("Cannot determine parent directory for: " + filePath);
        }

        // Task 64: Try xdg-open first (standard on most Linux distros)
        if (tryXdgOpen(directory)) {
            logger.debug("Opened file location using xdg-open: {}", directory);
            return;
        }

        // Task 65: Try fallback file managers
        if (tryFallbackFileManagers(filePath)) {
            logger.debug("Opened file location using fallback file manager: {}", filePath);
            return;
        }

        // All attempts failed
        throw new IOException(
                "Could not open file manager. Please ensure xdg-open or a file manager " +
                        "(nautilus, dolphin, thunar, nemo, pcmanfm) is installed.");
    }

    /**
     * Tries to open a directory using xdg-open.
     * 
     * Task 64: Implement xdg-open command execution
     * 
     * @param directory The directory to open
     * @return true if successful, false otherwise
     */
    private boolean tryXdgOpen(Path directory) {
        try {
            ProcessBuilder pb = new ProcessBuilder("xdg-open", directory.toString());
            pb.start(); // Don't wait for process to complete
            logger.debug("Successfully launched xdg-open for: {}", directory);
            return true;
        } catch (IOException e) {
            logger.debug("xdg-open failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Tries to open a file using common Linux file managers.
     * 
     * Task 65: Implement fallback file manager execution
     * 
     * Tries each file manager in order: nautilus, dolphin, thunar, nemo, pcmanfm.
     * Most file managers accept a file path and will show it selected in the parent
     * directory.
     * 
     * @param filePath The file path to open (file managers will show it in parent
     *                 directory)
     * @return true if successful, false otherwise
     */
    private boolean tryFallbackFileManagers(Path filePath) {
        String[] fileManagers = { "nautilus", "dolphin", "thunar", "nemo", "pcmanfm" };

        for (String fileManager : fileManagers) {
            try {
                ProcessBuilder pb = new ProcessBuilder(fileManager, filePath.toString());
                pb.start(); // Don't wait for process to complete
                logger.debug("Successfully launched {} for: {}", fileManager, filePath);
                return true;
            } catch (IOException e) {
                logger.debug("{} not available or failed: {}", fileManager, e.getMessage());
            }
        }

        return false;
    }
}
