// filepath: src/test/java/org/omc/model/SessionStateTest.java

package org.omc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for SessionState model.
 * Tests session state creation, validation, and immutability.
 *
 * Requirement REQ-005.2: Session restoration with file list and recent paths.
 */
class SessionStateTest {

    @TempDir
    Path tempDir;

    @Test
    void empty_ShouldCreateEmptySessionState() {
        // When: Create empty state
        SessionState state = SessionState.empty();

        // Then: Should have empty/default values
        assertNotNull(state);
        assertTrue(state.recentFilePaths().isEmpty());
        assertNull(state.lastInputDirectory());
        assertNull(state.lastOutputDirectory());
        assertTrue(state.pendingFiles().isEmpty());
        assertNull(state.lastUsedPreset());
    }

    @Test
    void constructor_ShouldCreateStateWithGivenParameters() {
        // Given: Valid parameters
        List<Path> recentFiles = List.of(Paths.get("/tmp/file1.mp4"), Paths.get("/tmp/file2.mp3"));
        Path inputDir = Paths.get("/tmp/input");
        Path outputDir = Paths.get("/tmp/output");
        List<ConversionFile> pendingFiles = List.of();
        String preset = "High Quality";

        // When: Create state
        SessionState state = new SessionState(recentFiles, inputDir, outputDir, pendingFiles, preset);

        // Then: Should have given values
        assertNotNull(state);
        assertEquals(recentFiles, state.recentFilePaths());
        assertEquals(inputDir, state.lastInputDirectory());
        assertEquals(outputDir, state.lastOutputDirectory());
        assertEquals(pendingFiles, state.pendingFiles());
        assertEquals(preset, state.lastUsedPreset());
    }

    @Test
    void constructor_ShouldHandleNullLists() {
        // When: Create state with null lists
        SessionState state = new SessionState(null, null, null, null, null);

        // Then: Should have empty lists
        assertNotNull(state);
        assertTrue(state.recentFilePaths().isEmpty());
        assertNull(state.lastInputDirectory());
        assertNull(state.lastOutputDirectory());
        assertTrue(state.pendingFiles().isEmpty());
        assertNull(state.lastUsedPreset());
    }

    @Test
    void withRecentFile_ShouldAddFileToRecentList() throws IOException {
        // Given: Existing state and new file
        Path existingFile = tempDir.resolve("existing.mp4");
        Files.createFile(existingFile);
        SessionState original = new SessionState(List.of(existingFile), null, null, List.of(), null);

        Path newFile = tempDir.resolve("new.mp3");
        Files.createFile(newFile);

        // When: Add recent file
        SessionState updated = original.withRecentFile(newFile);

        // Then: Should have new file at beginning, existing file maintained
        assertNotNull(updated);
        assertNotSame(original, updated);
        assertEquals(2, updated.recentFilePaths().size());
        assertEquals(newFile, updated.recentFilePaths().get(0));
        assertEquals(existingFile, updated.recentFilePaths().get(1));
    }

    @Test
    void withRecentFile_ShouldLimitTo10RecentFiles() throws IOException {
        // Given: State with 10 files
        List<Path> tenFiles = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Path file = tempDir.resolve("file" + i + ".mp4");
            Files.createFile(file);
            tenFiles.add(file);
        }
        SessionState original = new SessionState(tenFiles, null, null, List.of(), null);

        Path newFile = tempDir.resolve("new.mp4");
        Files.createFile(newFile);

        // When: Add 11th file
        SessionState updated = original.withRecentFile(newFile);

        // Then: Should maintain only 10 files
        assertEquals(10, updated.recentFilePaths().size());
        assertEquals(newFile, updated.recentFilePaths().get(0));
    }

    @Test
    void withRecentFile_ShouldAvoidDuplicates() throws IOException {
        // Given: State with existing file
        Path file = tempDir.resolve("file.mp4");
        Files.createFile(file);
        SessionState original = new SessionState(List.of(file), null, null, List.of(), null);

        // When: Add same file again
        SessionState updated = original.withRecentFile(file);

        // Then: Should not duplicate
        assertEquals(1, updated.recentFilePaths().size());
        assertEquals(file, updated.recentFilePaths().get(0));
    }

    @Test
    void withLastInputDirectory_ShouldCreateNewStateWithUpdatedInputDirectory() {
        // Given: Original state
        SessionState original = SessionState.empty();
        Path newInputDir = Paths.get("/new/input");

        // When: Update input directory
        SessionState updated = original.withLastInputDirectory(newInputDir);

        // Then: Should have new input directory, others unchanged
        assertNotNull(updated);
        assertNotSame(original, updated);
        assertEquals(newInputDir, updated.lastInputDirectory());
        assertEquals(original.lastOutputDirectory(), updated.lastOutputDirectory());
        assertEquals(original.recentFilePaths(), updated.recentFilePaths());
    }

    @Test
    void withLastOutputDirectory_ShouldCreateNewStateWithUpdatedOutputDirectory() {
        // Given: Original state
        SessionState original = SessionState.empty();
        Path newOutputDir = Paths.get("/new/output");

        // When: Update output directory
        SessionState updated = original.withLastOutputDirectory(newOutputDir);

        // Then: Should have new output directory, others unchanged
        assertNotNull(updated);
        assertNotSame(original, updated);
        assertEquals(newOutputDir, updated.lastOutputDirectory());
        assertEquals(original.lastInputDirectory(), updated.lastInputDirectory());
        assertEquals(original.recentFilePaths(), updated.recentFilePaths());
    }

    @Test
    void withPendingFiles_ShouldCreateNewStateWithUpdatedPendingFiles() throws IOException {
        // Given: Original state and new pending files
        SessionState original = SessionState.empty();
        Path filePath = tempDir.resolve("test.mp4");
        Files.createFile(filePath);
        List<ConversionFile> newPendingFiles = List.of(
                ConversionFile.create(filePath, FileFormat.MP4, 1024));

        // When: Update pending files
        SessionState updated = original.withPendingFiles(newPendingFiles);

        // Then: Should have new pending files, others unchanged
        assertNotNull(updated);
        assertNotSame(original, updated);
        assertEquals(newPendingFiles, updated.pendingFiles());
        assertEquals(original.recentFilePaths(), updated.recentFilePaths());
    }

    @Test
    void withLastUsedPreset_ShouldCreateNewStateWithUpdatedPreset() {
        // Given: Original state
        SessionState original = SessionState.empty();
        String newPreset = "High Quality";

        // When: Update last used preset
        SessionState updated = original.withLastUsedPreset(newPreset);

        // Then: Should have new preset, others unchanged
        assertNotNull(updated);
        assertNotSame(original, updated);
        assertEquals(newPreset, updated.lastUsedPreset());
        assertEquals(original.recentFilePaths(), updated.recentFilePaths());
    }

    @Test
    void validated_ShouldRemoveNonExistentPaths() throws IOException {
        // Given: State with mix of existing and non-existing paths
        Path existingFile = tempDir.resolve("existing.mp4");
        Files.createFile(existingFile);
        Path existingDir = tempDir.resolve("existingDir");
        Files.createDirectories(existingDir);

        SessionState state = new SessionState(
                List.of(existingFile, Paths.get("/nonexistent/file.mp4")),
                existingDir,
                Paths.get("/nonexistent/output"),
                List.of(
                        ConversionFile.create(existingFile, FileFormat.MP4, 1024),
                        ConversionFile.create(Paths.get("/nonexistent/pending.mp4"), FileFormat.MP4, 2048)),
                "preset");

        // When: Validate
        SessionState validated = state.validated();

        // Then: Should only keep existing paths
        assertNotNull(validated);
        assertEquals(1, validated.recentFilePaths().size());
        assertEquals(existingFile, validated.recentFilePaths().get(0));
        assertEquals(existingDir, validated.lastInputDirectory());
        assertNull(validated.lastOutputDirectory());
        assertEquals(1, validated.pendingFiles().size());
        assertEquals(existingFile, validated.pendingFiles().get(0).path());
        assertEquals("preset", validated.lastUsedPreset());
    }

    @Test
    void equals_ShouldReturnTrueForIdenticalStates() {
        // Given: Two identical states
        List<Path> recentFiles = List.of(Paths.get("/tmp/file.mp4"));
        Path inputDir = Paths.get("/tmp/input");
        Path outputDir = Paths.get("/tmp/output");
        List<ConversionFile> pendingFiles = List.of();
        String preset = "preset";

        SessionState state1 = new SessionState(recentFiles, inputDir, outputDir, pendingFiles, preset);
        SessionState state2 = new SessionState(recentFiles, inputDir, outputDir, pendingFiles, preset);

        // When/Then: Should be equal
        assertEquals(state1, state2);
        assertEquals(state1.hashCode(), state2.hashCode());
    }

    @Test
    void equals_ShouldReturnFalseForDifferentStates() {
        // Given: Two different states
        SessionState state1 = SessionState.empty();
        SessionState state2 = new SessionState(
                List.of(Paths.get("/tmp/file.mp4")), null, null, List.of(), null);

        // When/Then: Should not be equal
        assertNotEquals(state1, state2);
    }

    @Test
    void equals_ShouldHandleSameInstance() {
        // Given: Single state
        SessionState state = SessionState.empty();

        // When/Then: Should equal itself
        assertEquals(state, state);
    }

    @Test
    void equals_ShouldHandleNull() {
        // Given: State
        SessionState state = SessionState.empty();

        // When/Then: Should not equal null
        assertNotEquals(state, null);
    }

    @Test
    void equals_ShouldHandleDifferentClass() {
        // Given: State and different object
        SessionState state = SessionState.empty();

        // When/Then: Should not equal different class
        assertNotEquals(state, "String object");
    }

    @Test
    void toString_ShouldIncludeAllFields() {
        // Given: State
        SessionState state = SessionState.empty();

        // When: Convert to string
        String str = state.toString();

        // Then: Should contain all key information
        assertNotNull(str);
        assertTrue(str.contains("recentFileCount"));
        assertTrue(str.contains("lastInputDirectory"));
        assertTrue(str.contains("lastOutputDirectory"));
        assertTrue(str.contains("pendingFileCount"));
        assertTrue(str.contains("lastUsedPreset"));
    }
}