package org.omc.core;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Publishes completed output without exposing partial conversions or overwriting protected files. */
final class OutputPublisher {
    private OutputPublisher() {
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
                    Files.delete(staged);
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
            }
            if (copied) {
                Files.deleteIfExists(temporary);
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
        Files.delete(staged);
    }
}
