package org.omc.core;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Publishes completed output without exposing partial conversions or overwriting protected files. */
final class OutputPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OutputPublisher.class);

    private OutputPublisher() {
    }

    /** Publishes a document's resources first and its primary file last, rolling resources back on failure. */
    static void publishBundle(Path primary, Path destination, boolean overwrite) throws IOException {
        List<Path> publishedResources = new ArrayList<>();
        try {
            try (var children = Files.list(primary.getParent())) {
                for (Path resource : children.filter(path -> !path.equals(primary)).toList()) {
                    if (!Files.isDirectory(resource, LinkOption.NOFOLLOW_LINKS)
                            || !resource.getFileName().toString().startsWith("omc-resources-")) {
                        throw new IOException("Converter produced unexpected additional output: " + resource.getFileName());
                    }
                    Path target = destination.getParent().resolve(resource.getFileName());
                    // Each job uses a fresh resource name. Never overwrite an unrelated directory.
                    Files.createDirectory(target);
                    publishedResources.add(target);
                    try (var paths = Files.walk(resource)) {
                        for (Path source : paths.filter(path -> !path.equals(resource)).toList()) {
                            Path output = target.resolve(resource.relativize(source));
                            if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(output);
                            else if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) Files.copy(source, output);
                            else throw new IOException("Unsupported document resource: " + source);
                        }
                    }
                }
            }
            publish(primary, destination, overwrite);
        } catch (IOException | RuntimeException e) {
            for (Path resource : publishedResources) {
                try { deleteTree(resource); }
                catch (IOException cleanup) { e.addSuppressed(cleanup); }
            }
            throw e;
        }
    }

    /** Removes only the supplied job directory without following symbolic links. */
    static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    static void publish(Path temporary, Path destination, boolean overwrite) throws IOException {
        Path staged = temporary;
        boolean copied = false;
        try {
            if (!Files.getFileStore(temporary).equals(Files.getFileStore(destination.getParent()))) {
                staged = Files.createTempFile(destination.getParent(), ".omc-", ".part");
                copied = true;
                Files.copy(temporary, staged, StandardCopyOption.REPLACE_EXISTING);
            }
            if (overwrite) {
                try {
                    Files.move(staged, destination, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(staged, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                // Creating a hard link publishes the complete file atomically and fails
                // if another process has created the destination in the meantime.
                try {
                    Files.createLink(destination, staged);
                } catch (UnsupportedOperationException e) {
                    copyWithoutReplacing(staged, destination);
                } catch (java.nio.file.FileSystemException e) {
                    if (Files.exists(destination)) {
                        throw e;
                    }
                    // FAT has no hard links. CREATE_NEW also protects against a target
                    // appearing between the existence check and the filesystem call.
                    copyWithoutReplacing(staged, destination);
                }
                // The destination is complete once the link/copy above returned; a
                // staged-cleanup failure afterwards must not fail the conversion (a
                // no-overwrite retry would report "already exists"), so it is logged
                // loudly instead of thrown.
                deleteStagedQuietly(staged);
            }
            if (copied) {
                // Same contract as the staged cleanup: the publish is complete, so
                // a leftover temporary file is a warning, not a conversion failure.
                deleteStagedQuietly(temporary);
            }
        } finally {
            if (copied) {
                Files.deleteIfExists(staged);
            }
        }
    }

    private static void copyWithoutReplacing(Path staged, Path destination) throws IOException {
        boolean created = false;
        try (var input = Files.newInputStream(staged)) {
            try (var output = Files.newOutputStream(destination, java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE)) {
                created = true;
                input.transferTo(output);
            } catch (IOException e) {
                if (created) Files.deleteIfExists(destination);
                throw e;
            }
        }
    }

    /**
     * Best-effort removal of a leftover staged/temporary file after the
     * destination was published successfully. The publish is already
     * complete, so a cleanup failure is logged loudly but never fails the
     * conversion.
     */
    private static void deleteStagedQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.warn("Output was published but cleaning up {} failed: {}", path, e.toString());
        }
    }
}
