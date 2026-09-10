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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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

    // Magic bytes for format detection (first 16 bytes of common formats).
    // A LinkedHashMap with most-specific signatures FIRST: iteration order is
    // the tie-break, so wildcard entries (MP4's 0x00 box-size bytes, EBML)
    // must never shadow longer specific ones (M4A/MOV). A HashMap here made
    // MKV-vs-WEBM and MP4-vs-MOV/M4A detection order-random.
    // NOTE: 0x00 in a signature means "any byte" (box/chunk sizes vary).
    private static final Map<String, byte[][]> MAGIC_BYTES = new LinkedHashMap<>();

    // Guard rails for ZIP content inspection (zip-bomb / hostile archive
    // protection): never scan more than 1000 entries nor read more than 1MB
    // from a single entry.
    private static final int MAX_ZIP_ENTRIES_TO_SCAN = 1000;
    private static final int MAX_ZIP_ENTRY_READ_BYTES = 1024 * 1024;

    // MIME strings carried in the "mimetype" entry of ODF and EPUB archives.
    private static final Map<String, FileFormat> ZIP_MIME_FORMATS = Map.of(
            "application/vnd.oasis.opendocument.text", FileFormat.ODT,
            "application/vnd.oasis.opendocument.spreadsheet", FileFormat.ODS,
            "application/vnd.oasis.opendocument.presentation", FileFormat.ODP,
            "application/epub+zip", FileFormat.EPUB);

    // Well-known BMP DIB header sizes (BITMAPCOREHEADER through
    // BITMAPV5HEADER). "BM" alone matches almost any text starting with
    // those two bytes, so BMP detection additionally requires the DIB
    // header size (little-endian int at offset 14) to be one of these.
    // Exotic/ancient BMP variants failing this check fall back to
    // extension detection, which still routes real .bmp files correctly.
    private static final Set<Integer> BMP_DIB_HEADER_SIZES = Set.of(12, 40, 52, 56, 64, 108, 124);

    // Streamed copy chunk size.
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    // Minimum interval between copy progress callbacks (~100ms): reporting
    // every 64KB chunk means ~16k calls/GB, which floods listeners for
    // large copies. The first chunk reports immediately; a final callback
    // at completion always carries the total.
    private static final long COPY_PROGRESS_THROTTLE_MILLIS = 100;

    // Registered files for cleanup on shutdown
    private final Set<Path> cleanupRegistry = ConcurrentHashMap.newKeySet();

    private final ConfigurationManager configManager;

    static {
        // Audio first: M4A's specific ftypM4A must precede MP4's wildcard ftyp.
        MAGIC_BYTES.put("M4A", new byte[][] {
                { 0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x4D, 0x34, 0x41 } // ftyp M4A
        });
        // Video formats: MOV's ftypqt precedes MP4's wildcard ftyp for the
        // same reason; MKV precedes WEBM (identical EBML headers - true
        // separation needs DocType parsing, so MKV wins deterministically and
        // both stay VIDEO for routing).
        MAGIC_BYTES.put("MOV", new byte[][] {
                { 0x00, 0x00, 0x00, 0x14, 0x66, 0x74, 0x79, 0x70, 0x71, 0x74, 0x20, 0x20 } // ftyp qt
        });
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
     * Delegates to {@link FileUtils#getFileSize(Path)}: the validation and
     * error mapping is shared with the static utility so it exists in
     * exactly one place.
     *
     * @param filePath The file path
     * @return The file size in bytes
     * @throws FileOperationException if file doesn't exist or size query fails
     */
    public long getFileSize(Path filePath) throws FileOperationException {
        return FileUtils.getFileSize(filePath);
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
     * Delegates to {@link FileUtils#copyFile(Path, Path, boolean)}: the
     * validation and error mapping is shared with the static utility so it
     * exists in exactly one place. Use
     * {@link #copyFile(Path, Path, boolean, Consumer)} when progress
     * reporting is needed.
     *
     * @param source      The source file path
     * @param destination The destination file path
     * @param overwrite   Whether to overwrite if destination exists
     * @throws FileOperationException if copy fails
     */
    public void copyFile(Path source, Path destination, boolean overwrite)
            throws FileOperationException {
        FileUtils.copyFile(source, destination, overwrite);
        logger.debug("Copied file from {} to {}", source, destination);
    }

    /**
     * Copies a file with real streaming progress.
     *
     * <p>
     * The copy streams through a 64KB buffer and invokes the callback with
     * the cumulative number of bytes copied. Callbacks are time-throttled
     * to at most one every ~100ms (the first chunk reports immediately and
     * a final callback at completion always carries the total), so callers
     * see progress grow during the copy instead of a single fake 100%
     * report after an atomic copy, without being flooded per chunk. An
     * empty file produces no callbacks.
     * </p>
     *
     * <p>
     * Semantics preserved from the previous atomic implementation: the
     * overwrite flag behavior (no silent replacement when false) and the
     * thrown {@link FileOperationException} error codes. Last-modified time
     * is carried over from the source as a best-effort replacement for the
     * COPY_ATTRIBUTES option.
     * </p>
     *
     * @param source           The source file path
     * @param destination      The destination file path
     * @param overwrite        Whether to overwrite if destination exists
     * @param progressCallback Callback invoked with cumulative bytes copied
     *                         (may be null)
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

        // Fail fast when overwriting is not allowed (the CREATE_NEW open
        // option below closes the remaining race window).
        if (!overwrite && exists(destination)) {
            throw new FileOperationException(
                    "Destination file already exists",
                    ErrorCode.FILE_ALREADY_EXISTS,
                    destination.toString());
        }

        long bytesCopied = 0;
        long lastCallbackMillis = -1; // -1 => first chunk reports immediately
        long reportedBytes = 0;
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        try (InputStream input = Files.newInputStream(source);
                OutputStream output = overwrite
                        ? Files.newOutputStream(destination, StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
                        : Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.WRITE)) {
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                bytesCopied += bytesRead;
                if (progressCallback != null && (lastCallbackMillis < 0
                        || System.currentTimeMillis() - lastCallbackMillis >= COPY_PROGRESS_THROTTLE_MILLIS)) {
                    progressCallback.accept(bytesCopied);
                    reportedBytes = bytesCopied;
                    lastCallbackMillis = System.currentTimeMillis();
                }
            }
            // Final callback so the total is always reported. Skipped when
            // nothing was streamed (empty files produce no callbacks, per
            // the existing contract) and when the last throttled report
            // already carried the total.
            if (progressCallback != null && bytesCopied > 0 && reportedBytes != bytesCopied) {
                progressCallback.accept(bytesCopied);
            }
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

        // Best-effort last-modified preservation (COPY_ATTRIBUTES
        // replacement for the streamed copy; failure is non-fatal).
        try {
            Files.setLastModifiedTime(destination, Files.getLastModifiedTime(source));
        } catch (IOException e) {
            logger.debug("Could not preserve last-modified time for {}", destination, e);
        }

        logger.debug("Copied file from {} to {} ({} bytes)", source, destination, bytesCopied);
    }

    /**
     * Moves a file from source to destination.
     * Delegates to {@link FileUtils#moveFile(Path, Path, boolean)}: the
     * validation and error mapping is shared with the static utility so it
     * exists in exactly one place.
     *
     * @param source      The source file path
     * @param destination The destination file path
     * @param overwrite   Whether to overwrite if destination exists
     * @throws FileOperationException if move fails
     */
    public void moveFile(Path source, Path destination, boolean overwrite)
            throws FileOperationException {
        FileUtils.moveFile(source, destination, overwrite);
        logger.debug("Moved file from {} to {}", source, destination);
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
            // 18 bytes: the longest generic signature is 12 bytes, but the
            // BMP validation reads the DIB header size from bytes 14-17.
            byte[] header = readFileHeader(filePath, 18);
            if (header.length == 0) {
                return null;
            }

            // Check against known magic bytes
            for (Map.Entry<String, byte[][]> entry : MAGIC_BYTES.entrySet()) {
                String formatName = entry.getKey();
                // "ZIP" is a container signature, not a terminal format: it
                // must fall through to detectZipBasedFormat() below. Without
                // this skip the loop would match the ZIP entry first and
                // FileFormat.valueOf("ZIP") / mapFormatName("ZIP") would
                // return UNKNOWN, making the ZIP content inspection dead code
                // and reducing every zip-based file to extension detection.
                if ("ZIP".equals(formatName)) {
                    continue;
                }
                // "BMP" is a 2-byte signature that false-positives on any
                // "BM..." file (e.g. a text starting "BMW is a car"): require
                // a plausible DIB header before the magic can win.
                if ("BMP".equals(formatName) && !looksLikeBmp(header)) {
                    continue;
                }
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
     * Cheap structural validation for BMP: after the "BM" signature, bytes
     * 14-17 hold the DIB header size as a little-endian int, which for every
     * real BMP variant is one of {@link #BMP_DIB_HEADER_SIZES}.
     *
     * @param header The file header bytes (at least 18 bytes long)
     * @return true if the header carries a known DIB header size
     */
    private static boolean looksLikeBmp(byte[] header) {
        if (header.length < 18) {
            return false;
        }
        int dibHeaderSize = (header[14] & 0xFF)
                | (header[15] & 0xFF) << 8
                | (header[16] & 0xFF) << 16
                | (header[17] & 0xFF) << 24;
        return BMP_DIB_HEADER_SIZES.contains(dibHeaderSize);
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
     * Detects ZIP-based document formats by examining the ZIP container's
     * content, falling back to the file extension.
     *
     * <p>
     * Trusting the extension alone misroutes renamed archives (e.g. a .jar
     * renamed to .docx), so the internal structure is inspected first:
     * </p>
     * <ul>
     * <li>ODT/ODS/ODP/EPUB: the {@code mimetype} entry (always the first
     * entry in conforming ODF/EPUB files) is mapped by its exact MIME
     * string.</li>
     * <li>DOCX/XLSX/PPTX: presence of a {@code [Content_Types].xml} entry
     * whose content references wordprocessingml / spreadsheetml /
     * presentationml respectively.</li>
     * </ul>
     *
     * <p>
     * Guard rails against hostile archives: at most
     * {@value #MAX_ZIP_ENTRIES_TO_SCAN} entries are scanned, at most
     * {@value #MAX_ZIP_ENTRY_READ_BYTES} bytes are read from any single
     * entry, and every parse failure (truncated zip, unsupported zip
     * variants, unexpected runtime errors) falls back to the extension-based
     * result. The extension is always the final fallback.
     * </p>
     *
     * @param filePath The file path (already known to start with ZIP magic
     *                 bytes)
     * @return The detected format, or the extension-based result when the
     *         content is not recognizable
     */
    private FileFormat detectZipBasedFormat(Path filePath) {
        FileFormat byExtension = detectZipBasedFormatByExtension(filePath);
        try (ZipFile zipFile = new ZipFile(filePath.toFile())) {
            ZipEntry mimetypeEntry = null;
            ZipEntry contentTypesEntry = null;
            int scanned = 0;
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements() && scanned < MAX_ZIP_ENTRIES_TO_SCAN) {
                ZipEntry entry = entries.nextElement();
                scanned++;
                String name = entry.getName();
                if ("mimetype".equals(name)) {
                    mimetypeEntry = entry;
                } else if ("[Content_Types].xml".equals(name)) {
                    contentTypesEntry = entry;
                }
                if (mimetypeEntry != null && contentTypesEntry != null) {
                    break;
                }
            }

            // ODF and EPUB archives identify themselves via the mimetype entry
            if (mimetypeEntry != null) {
                String mime = readZipEntryAsString(zipFile, mimetypeEntry).trim();
                FileFormat format = ZIP_MIME_FORMATS.get(mime);
                if (format != null) {
                    logger.debug("Detected {} by ZIP mimetype entry '{}': {}", format, mime, filePath);
                    return format;
                }
            }

            // OOXML archives carry [Content_Types].xml naming the part types
            if (contentTypesEntry != null) {
                String contentTypes = readZipEntryAsString(zipFile, contentTypesEntry);
                if (contentTypes.contains("wordprocessingml")) {
                    return logZipContentDetection(filePath, FileFormat.DOCX);
                }
                if (contentTypes.contains("spreadsheetml")) {
                    return logZipContentDetection(filePath, FileFormat.XLSX);
                }
                if (contentTypes.contains("presentationml")) {
                    return logZipContentDetection(filePath, FileFormat.PPTX);
                }
            }

            return byExtension;
        } catch (Exception e) {
            // Unparseable/truncated zip or unexpected failure: the extension
            // stays authoritative rather than guessing UNKNOWN.
            logger.debug("ZIP content inspection failed for {}, using extension fallback", filePath, e);
            return byExtension;
        }
    }

    private FileFormat logZipContentDetection(Path filePath, FileFormat format) {
        logger.debug("Detected {} by [Content_Types].xml content: {}", format, filePath);
        return format;
    }

    /**
     * Reads a ZIP entry as a string, capped at
     * {@value #MAX_ZIP_ENTRY_READ_BYTES} bytes to bound memory on hostile
     * archives.
     */
    private String readZipEntryAsString(ZipFile zipFile, ZipEntry entry) throws IOException {
        try (InputStream input = zipFile.getInputStream(entry)) {
            byte[] bytes = input.readNBytes(MAX_ZIP_ENTRY_READ_BYTES);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * Maps a ZIP file's extension to a ZIP-based document format. This is the
     * final fallback used when the ZIP content is missing or unrecognized.
     */
    private FileFormat detectZipBasedFormatByExtension(Path filePath) {
        String extension = PathUtils.getExtension(filePath.toString()).toUpperCase();

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
            // DISCARD so a chatty child never blocks on a full stdout pipe
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            // xdg-open normally exits right after handing off to the desktop
            // environment; reaping it avoids a zombie and detects failure.
            boolean exited = process.waitFor(10, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                logger.debug("xdg-open did not exit for: {}", directory);
                // Hand-off likely already happened; treat as success
                return true;
            }
            if (process.exitValue() != 0) {
                logger.debug("xdg-open exited with {}: {}", process.exitValue(), directory);
                return false;
            }
            logger.debug("Successfully launched xdg-open for: {}", directory);
            return true;
        } catch (IOException e) {
            logger.debug("xdg-open failed: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("xdg-open wait interrupted for: {}", directory);
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
                // GUI managers keep running; do NOT waitFor, but DISCARD so
                // they never block writing to an unread pipe.
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                pb.start();
                logger.debug("Successfully launched {} for: {}", fileManager, filePath);
                return true;
            } catch (IOException e) {
                logger.debug("{} not available or failed: {}", fileManager, e.getMessage());
            }
        }

        return false;
    }
}
