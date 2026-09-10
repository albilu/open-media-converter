package org.omc.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for path operations including validation, normalization, and
 * relative path calculations.
 * All methods are static and thread-safe.
 * 
 * Requirements: All
 */
public final class PathUtils {

    // Private constructor to prevent instantiation
    private PathUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Validates that a path string is valid.
     *
     * @param pathString The path string to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidPath(String pathString) {
        if (pathString == null || pathString.isBlank()) {
            return false;
        }

        try {
            Paths.get(pathString);
            return true;
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /**
     * Normalizes a path by resolving relative components and removing redundancies.
     *
     * @param pathString The path string to normalize
     * @return The normalized path string
     * @throws IllegalArgumentException if path is invalid
     */
    public static String normalize(String pathString) {
        if (!isValidPath(pathString)) {
            throw new IllegalArgumentException("Invalid path: " + pathString);
        }

        return Paths.get(pathString).normalize().toString();
    }

    /**
     * Converts a path to an absolute path.
     *
     * @param pathString The path string
     * @return The absolute path string
     * @throws IllegalArgumentException if path is invalid
     */
    public static String toAbsolute(String pathString) {
        if (!isValidPath(pathString)) {
            throw new IllegalArgumentException("Invalid path: " + pathString);
        }

        return Paths.get(pathString).toAbsolutePath().normalize().toString();
    }

    /**
     * Computes a relative path from one path to another.
     *
     * @param fromPath The starting path
     * @param toPath   The destination path
     * @return The relative path from 'from' to 'to'
     * @throws IllegalArgumentException if either path is invalid
     */
    public static String relativize(String fromPath, String toPath) {
        if (!isValidPath(fromPath) || !isValidPath(toPath)) {
            throw new IllegalArgumentException("Invalid paths: from=" + fromPath + ", to=" + toPath);
        }

        Path from = Paths.get(fromPath).toAbsolutePath().normalize();
        Path to = Paths.get(toPath).toAbsolutePath().normalize();

        return from.relativize(to).toString();
    }

    /**
     * Gets the file extension from a path (without the dot).
     *
     * @param pathString The path string
     * @return The file extension, or empty string if no extension or the
     *         path is degenerate (e.g. contains NUL bytes or is the root)
     */
    public static String getExtension(String pathString) {
        if (pathString == null || pathString.isBlank()) {
            return "";
        }

        String filename = getFileNameSafely(pathString);
        if (filename == null) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');

        if (lastDot == -1 || lastDot == 0 || lastDot == filename.length() - 1) {
            return "";
        }

        return filename.substring(lastDot + 1);
    }

    /**
     * Gets the filename without extension.
     *
     * @param pathString The path string
     * @return The filename without extension, or empty string if the path is
     *         degenerate (e.g. contains NUL bytes or is the root)
     */
    public static String getFilenameWithoutExtension(String pathString) {
        if (pathString == null || pathString.isBlank()) {
            return "";
        }

        String filename = getFileNameSafely(pathString);
        if (filename == null) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');

        if (lastDot == -1 || lastDot == 0) {
            return filename;
        }

        return filename.substring(0, lastDot);
    }

    /**
     * Extracts the file name of a path string, returning null instead of
     * throwing for degenerate input.
     *
     * @param pathString The path string
     * @return The file name, or null when the path is invalid or has no file
     *         name component (root)
     */
    private static String getFileNameSafely(String pathString) {
        try {
            Path fileName = Paths.get(pathString).getFileName();
            return fileName == null ? null : fileName.toString();
        } catch (InvalidPathException e) {
            // Degenerate input (e.g. embedded NUL bytes) has no usable
            // filename to extract
            return null;
        }
    }

    /**
     * Changes the file extension of a path.
     *
     * @param pathString   The original path
     * @param newExtension The new extension (with or without dot)
     * @return The path with the new extension
     */
    public static String changeExtension(String pathString, String newExtension) {
        if (!isValidPath(pathString)) {
            throw new IllegalArgumentException("Invalid path: " + pathString);
        }

        Path path = Paths.get(pathString);
        Path parent = path.getParent();
        String filenameWithoutExt = getFilenameWithoutExtension(pathString);

        // Normalize extension (ensure it starts with a dot)
        String ext = newExtension;
        if (!ext.startsWith(".")) {
            ext = "." + ext;
        }

        String newFilename = filenameWithoutExt + ext;

        if (parent != null) {
            return parent.resolve(newFilename).toString();
        } else {
            return newFilename;
        }
    }

    /**
     * Checks if a path is within a given directory (prevents directory traversal).
     * Symlinks are resolved, so a link inside the directory that points outside
     * is correctly rejected.
     *
     * @param basePath   The base directory path
     * @param targetPath The target path to check
     * @return true if target is within base directory
     */
    public static boolean isWithinDirectory(String basePath, String targetPath) {
        if (!isValidPath(basePath) || !isValidPath(targetPath)) {
            return false;
        }

        try {
            Path base = resolveRealPath(Paths.get(basePath));
            Path target = resolveRealPath(Paths.get(targetPath));

            return target.startsWith(base);
        } catch (IOException e) {
            // Path resolution failures mean containment cannot be established
            return false;
        }
    }

    /**
     * Resolves a path to its real (symlink-free, absolute) form, falling back
     * to absolute+normalized when the path does not exist (toRealPath throws).
     *
     * @param path The path to resolve
     * @return the real path when it exists, otherwise the normalized absolute path
     */
    private static Path resolveRealPath(Path path) throws IOException {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            // Non-existent path: lexical comparison is the best available check
            return path.toAbsolutePath().normalize();
        }
    }

    /**
     * Ensures that a directory exists, creating it if necessary.
     *
     * @param dirPath The directory path
     * @return true if directory exists or was created successfully
     */
    public static boolean ensureDirectoryExists(String dirPath) {
        if (!isValidPath(dirPath)) {
            return false;
        }

        try {
            Path path = Paths.get(dirPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            return Files.isDirectory(path);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Expands home directory symbol (~) in path.
     *
     * @param pathString The path string potentially containing ~
     * @return The expanded path
     */
    public static String expandHome(String pathString) {
        if (pathString == null || pathString.isBlank()) {
            return pathString;
        }

        if (pathString.startsWith("~/") || pathString.equals("~")) {
            String home = System.getProperty("user.home");
            return pathString.replaceFirst("^~", home);
        }

        return pathString;
    }
}
