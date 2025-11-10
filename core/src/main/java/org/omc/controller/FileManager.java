// filepath: src/main/java/org/omc/controller/FileManager.java

package org.omc.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.omc.core.ValidationEngine;
import org.omc.exception.ErrorCode;
import org.omc.exception.FileOperationException;
import org.omc.model.ConversionFile;
import org.omc.model.FileFormat;
import org.omc.model.ValidationResult;
import org.omc.service.FileHandler;
import org.omc.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for managing the list of files to be converted.
 * Provides observable file list with add/remove operations, duplicate
 * detection,
 * and event notifications.
 * 
 * Thread-safe using CopyOnWriteArrayList for the file list.
 * 
 * Requirements: REQ-002.1, REQ-002.2
 */
public class FileManager {
    private static final Logger logger = LoggerFactory.getLogger(FileManager.class);

    private final FileHandler fileHandler;
    private final ValidationEngine validationEngine;

    // Thread-safe observable file list
    private final CopyOnWriteArrayList<ConversionFile> files;

    // Track file hashes for duplicate detection
    private final Map<String, String> fileHashMap; // path -> hash
    private final Set<String> fileHashes; // set of hashes for fast lookup

    // Event listeners
    private final CopyOnWriteArrayList<Consumer<FileEvent>> eventListeners;

    /**
     * Event types for file operations.
     */
    public enum EventType {
        FILE_ADDED,
        FILE_REMOVED,
        FILES_CLEARED,
        STATUS_CHANGED
    }

    /**
     * Event data for file operations.
     */
    public static class FileEvent {
        private final EventType type;
        private final ConversionFile file;
        private final List<ConversionFile> files;

        public FileEvent(EventType type, ConversionFile file) {
            this.type = type;
            this.file = file;
            this.files = file != null ? List.of(file) : Collections.emptyList();
        }

        public FileEvent(EventType type, List<ConversionFile> files) {
            this.type = type;
            this.file = null;
            this.files = files != null ? List.copyOf(files) : Collections.emptyList();
        }

        public EventType getType() {
            return type;
        }

        public ConversionFile getFile() {
            return file;
        }

        public List<ConversionFile> getFiles() {
            return files;
        }
    }

    /**
     * Creates a new FileManager.
     *
     * @param fileHandler      File system handler
     * @param validationEngine Validation engine
     */
    public FileManager(FileHandler fileHandler, ValidationEngine validationEngine) {
        this.fileHandler = Objects.requireNonNull(fileHandler, "FileHandler cannot be null");
        this.validationEngine = Objects.requireNonNull(validationEngine, "ValidationEngine cannot be null");
        this.files = new CopyOnWriteArrayList<>();
        this.fileHashMap = new ConcurrentHashMap<>();
        this.fileHashes = ConcurrentHashMap.newKeySet();
        this.eventListeners = new CopyOnWriteArrayList<>();
        logger.info("FileManager initialized");
    }

    /**
     * Adds files to the conversion list with validation.
     * Requirement REQ-002.1: Add files with validation
     *
     * @param paths File paths to add
     * @return List of successfully added files
     * @throws IllegalArgumentException if paths is null
     */
    public List<ConversionFile> addFiles(List<Path> paths) {
        if (paths == null) {
            throw new IllegalArgumentException("Paths cannot be null");
        }

        logger.info("Adding {} files to conversion list", paths.size());
        List<ConversionFile> addedFiles = new ArrayList<>();

        for (Path path : paths) {
            try {
                // Validate file
                ValidationResult validation = validationEngine.validateFile(path);
                if (validation.isFailure()) {
                    logger.warn("File validation failed for {}: {}", path, validation.getFirstError());
                    continue;
                }

                // Check for duplicates
                if (isDuplicate(path)) {
                    logger.debug("Skipping duplicate file: {}", path);
                    continue;
                }

                // Detect format
                FileFormat format = fileHandler.detectFormat(path);
                long size = fileHandler.getFileSize(path);

                // Create conversion file
                ConversionFile file = ConversionFile.create(path, format, size);

                // Add to list
                files.add(file);
                addedFiles.add(file);

                // Calculate and store hash for duplicate detection
                String hash = calculateFileHash(path);
                if (hash != null) {
                    fileHashMap.put(path.toString(), hash);
                    fileHashes.add(hash);
                }

                // Notify listeners
                notifyListeners(new FileEvent(EventType.FILE_ADDED, file));

                logger.debug("Added file: {} (format: {}, size: {})", path, format, FileUtils.formatFileSize(size));

            } catch (Exception e) {
                logger.error("Failed to add file: {}", path, e);
            }
        }

        logger.info("Successfully added {} of {} files", addedFiles.size(), paths.size());
        return addedFiles;
    }

    /**
     * Adds all files from a folder with optional recursive scanning.
     * Requirement REQ-002.1: Add files from folder with format filtering
     *
     * @param folderPath Folder path
     * @param recursive  Whether to scan recursively
     * @return List of successfully added files
     * @throws IllegalArgumentException if folderPath is null
     * @throws FileOperationException   if folder doesn't exist or can't be read
     */
    public List<ConversionFile> addFilesFromFolder(Path folderPath, boolean recursive)
            throws FileOperationException {
        if (folderPath == null) {
            throw new IllegalArgumentException("Folder path cannot be null");
        }

        if (!fileHandler.exists(folderPath)) {
            throw new FileOperationException(
                    "Folder does not exist",
                    ErrorCode.FILE_NOT_FOUND,
                    folderPath.toString());
        }

        if (!Files.isDirectory(folderPath)) {
            throw new FileOperationException(
                    "Path is not a directory",
                    ErrorCode.FILE_NOT_READABLE,
                    folderPath.toString());
        }

        logger.info("Scanning folder for media files: {} (recursive: {})", folderPath, recursive);

        List<Path> filePaths = new ArrayList<>();

        try (Stream<Path> stream = recursive ? Files.walk(folderPath) : Files.list(folderPath)) {

            filePaths = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        FileFormat format = fileHandler.detectFormat(path);
                        return format != FileFormat.UNKNOWN;
                    })
                    .collect(Collectors.toList());

        } catch (IOException e) {
            logger.error("Failed to scan folder: {}", folderPath, e);
            throw new FileOperationException(
                    "Failed to scan folder",
                    ErrorCode.FILE_IO_ERROR,
                    folderPath.toString(),
                    e);
        }

        logger.info("Found {} media files in folder", filePaths.size());
        return addFiles(filePaths);
    }

    /**
     * Removes files from the conversion list by their IDs.
     * Requirement REQ-002.2: Remove files from list
     *
     * @param fileIds File IDs to remove
     * @return Number of files removed
     * @throws IllegalArgumentException if fileIds is null
     */
    public int removeFiles(List<String> fileIds) {
        if (fileIds == null) {
            throw new IllegalArgumentException("File IDs cannot be null");
        }

        logger.info("Removing {} files from conversion list", fileIds.size());
        int removedCount = 0;

        Set<String> idsToRemove = new HashSet<>(fileIds);
        List<ConversionFile> removedFiles = new ArrayList<>();

        Iterator<ConversionFile> iterator = files.iterator();
        while (iterator.hasNext()) {
            ConversionFile file = iterator.next();
            if (idsToRemove.contains(file.id())) {
                files.remove(file);
                removedFiles.add(file);
                String hash = fileHashMap.remove(file.path().toString());
                if (hash != null) {
                    fileHashes.remove(hash);
                }
                removedCount++;

                // Notify listeners
                notifyListeners(new FileEvent(EventType.FILE_REMOVED, file));

                logger.debug("Removed file: {}", file.path());
            }
        }

        logger.info("Removed {} files", removedCount);
        return removedCount;
    }

    /**
     * Clears all files from the conversion list.
     * Requirement REQ-002.2: Clear file list
     */
    public void clearFiles() {
        logger.info("Clearing all files from conversion list");

        List<ConversionFile> clearedFiles = new ArrayList<>(files);
        files.clear();
        fileHashMap.clear();
        fileHashes.clear();

        // Notify listeners
        notifyListeners(new FileEvent(EventType.FILES_CLEARED, clearedFiles));

        logger.info("Cleared {} files", clearedFiles.size());
    }

    /**
     * Gets all files in the conversion list.
     * Requirement REQ-002.2: Query file list
     *
     * @return Unmodifiable list of conversion files
     */
    public List<ConversionFile> getFiles() {
        return Collections.unmodifiableList(new ArrayList<>(files));
    }

    /**
     * Gets a file by its ID.
     *
     * @param fileId File ID
     * @return The conversion file, or empty if not found
     */
    public Optional<ConversionFile> getFile(String fileId) {
        if (fileId == null) {
            return Optional.empty();
        }

        return files.stream()
                .filter(f -> fileId.equals(f.id()))
                .findFirst();
    }

    /**
     * Updates a file in the list.
     * Used to update status, progress, metadata, etc.
     *
     * @param updatedFile The updated file
     * @throws IllegalArgumentException if file is null or not in list
     */
    public void updateFile(ConversionFile updatedFile) {
        if (updatedFile == null) {
            throw new IllegalArgumentException("Updated file cannot be null");
        }

        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).id().equals(updatedFile.id())) {
                files.set(i, updatedFile);

                // Notify listeners
                notifyListeners(new FileEvent(EventType.STATUS_CHANGED, updatedFile));

                logger.debug("Updated file: {} (status: {})", updatedFile.path(), updatedFile.status());
                return;
            }
        }

        throw new IllegalArgumentException("File not found in list: " + updatedFile.id());
    }

    /**
     * Gets the number of files in the list.
     *
     * @return File count
     */
    public int getFileCount() {
        return files.size();
    }

    /**
     * Gets the file handler for file system operations.
     * Exposed for UI operations that need direct file system access,
     * such as opening files in the system file manager.
     *
     * Requirement REQ-FL-3.2: Enable file manager integration
     *
     * @return the file handler instance
     */
    public FileHandler getFileHandler() {
        return fileHandler;
    }

    /**
     * Checks if a file is already in the list by path or hash.
     * Requirement REQ-002.1: Duplicate detection
     *
     * @param path File path
     * @return true if file is a duplicate
     */
    private boolean isDuplicate(Path path) {
        // Check by path
        if (files.stream().anyMatch(f -> f.path().equals(path))) {
            return true;
        }

        // Check by hash
        String hash = calculateFileHash(path);
        if (hash != null && fileHashes.contains(hash)) {
            return true;
        }

        return false;
    }

    /**
     * Calculates SHA-256 hash of a file for duplicate detection.
     *
     * @param path File path
     * @return Hex string of file hash, or null if failed
     */
    private String calculateFileHash(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(Files.newInputStream(path), digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // DigestInputStream updates digest automatically
                }
            }
            byte[] hashBytes = digest.digest();

            // Convert to hex string
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (NoSuchAlgorithmException | IOException e) {
            logger.debug("Failed to calculate file hash: {}", path, e);
            return null;
        }
    }

    /**
     * Adds an event listener for file operations.
     * Requirement REQ-002.2: Event notifications
     *
     * @param listener Event listener
     * @throws IllegalArgumentException if listener is null
     */
    public void addEventListener(Consumer<FileEvent> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        eventListeners.add(listener);
        logger.debug("Added event listener");
    }

    /**
     * Removes an event listener.
     *
     * @param listener Event listener to remove
     * @return true if listener was removed
     */
    public boolean removeEventListener(Consumer<FileEvent> listener) {
        boolean removed = eventListeners.remove(listener);
        if (removed) {
            logger.debug("Removed event listener");
        }
        return removed;
    }

    /**
     * Notifies all event listeners of a file event.
     *
     * @param event The file event
     */
    private void notifyListeners(FileEvent event) {
        for (Consumer<FileEvent> listener : eventListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                logger.error("Error in event listener", e);
            }
        }
    }
}
