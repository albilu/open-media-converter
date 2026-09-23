package org.omc.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class OutputBundlePublicationTest {
    @TempDir Path root;

    private Path document() throws IOException {
        Path job = Files.createDirectory(root.resolve("job"));
        Path resources = Files.createDirectory(job.resolve("omc-resources-test"));
        Files.writeString(resources.resolve("picture.png"), "image bytes");
        return Files.writeString(job.resolve("document.md"), "![image](omc-resources-test/picture.png)");
    }

    @Test
    void resourcesAreAvailableWhenTheDocumentIsPublished() throws IOException {
        Path source = document();
        Path directory = Files.createDirectory(root.resolve("output"));
        OutputPublisher.publishBundle(source, directory.resolve("document.md"), false);
        assertTrue(Files.isRegularFile(directory.resolve("document.md")));
        assertEquals("image bytes", Files.readString(directory.resolve("omc-resources-test/picture.png")));
    }

    @Test
    void primaryConflictRollsBackOnlyNewResources() throws IOException {
        Path source = document();
        Path directory = Files.createDirectory(root.resolve("output"));
        Path destination = Files.writeString(directory.resolve("document.md"), "existing document");
        assertThrows(IOException.class, () -> OutputPublisher.publishBundle(source, destination, false));
        assertEquals("existing document", Files.readString(destination));
        assertFalse(Files.exists(directory.resolve("omc-resources-test")));
        assertTrue(Files.isRegularFile(source));
    }

    @Test
    void resourceCollisionPreservesTheExistingDirectory() throws IOException {
        Path source = document();
        Path directory = Files.createDirectory(root.resolve("output"));
        Path protectedResources = Files.createDirectory(directory.resolve("omc-resources-test"));
        Files.writeString(protectedResources.resolve("keep.txt"), "untouched");
        assertThrows(IOException.class, () -> OutputPublisher.publishBundle(source, directory.resolve("document.md"), true));
        assertEquals("untouched", Files.readString(protectedResources.resolve("keep.txt")));
        assertFalse(Files.exists(directory.resolve("document.md")));
    }
}
