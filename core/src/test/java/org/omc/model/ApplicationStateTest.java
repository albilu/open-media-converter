// filepath: src/test/java/org/omc/model/ApplicationStateTest.java

package org.omc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for ApplicationState model.
 * Tests state creation, validation, migration, and immutability.
 *
 * Requirement REQ-005.3: Complete application state persistence.
 */
class ApplicationStateTest {

    @Test
    void defaultState_ShouldCreateValidDefaultState() {
        // When: Create default state
        ApplicationState state = ApplicationState.defaultState();

        // Then: Should have default values
        assertNotNull(state);
        assertNotNull(state.windowState());
        assertNotNull(state.sessionState());
        assertNotNull(state.fileListSortState());
        assertNull(state.conversionSettings());
        assertEquals("1.0.0", state.version());
        assertTrue(state.lastSaved() > 0);
        assertTrue(state.windowState().isValid());
        assertTrue(state.sessionState().recentFilePaths().isEmpty());
        assertFalse(state.fileListSortState().isSorted()); // Should be unsorted by default
    }

    @Test
    void create_ShouldCreateStateWithGivenParameters() {
        // Given: Valid components
        WindowState windowState = new WindowState(1200, 800, 100, 100, false, false);
        SessionState sessionState = SessionState.empty();
        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP4)
                .outputDirectory(Paths.get("/tmp"))
                .parallelConversions(4)
                .build();
        String version = "2.0.0";

        // When: Create state
        ApplicationState state = ApplicationState.create(windowState, sessionState, settings, version);

        // Then: Should have given values
        assertNotNull(state);
        assertEquals(windowState, state.windowState());
        assertEquals(sessionState, state.sessionState());
        assertEquals(settings, state.conversionSettings());
        assertEquals(version, state.version());
        assertTrue(state.lastSaved() > 0);
    }

    @Test
    void validated_ShouldReturnValidatedStateWithInvalidWindowDefaults() {
        // Given: State with invalid window
        WindowState invalidWindow = new WindowState(-100, -100, 0, 0, false, false);
        ApplicationState state = ApplicationState.create(invalidWindow, SessionState.empty(), null, "1.0.0");

        // When: Validate
        ApplicationState validated = state.validated();

        // Then: Should have default window state
        assertNotNull(validated);
        assertTrue(validated.windowState().isValid());
        assertEquals(WindowState.defaultState().width(), validated.windowState().width());
        assertEquals(SessionState.empty(), validated.sessionState());
    }

    @Test
    void validated_ShouldReturnValidatedStateWithInvalidSessionDefaults() {
        // Given: State with null session
        ApplicationState state = new ApplicationState(
                WindowState.defaultState(), null, null, null, "1.0.0", System.currentTimeMillis());

        // When: Validate
        ApplicationState validated = state.validated();

        // Then: Should have empty session state and unsorted file list state
        assertNotNull(validated);
        assertEquals(SessionState.empty(), validated.sessionState());
        assertNotNull(validated.fileListSortState());
        assertFalse(validated.fileListSortState().isSorted());
    }

    @Test
    void needsMigration_ShouldReturnTrueForOlderMajorVersion() {
        // Given: State with older major version
        ApplicationState state = ApplicationState.create(
                WindowState.defaultState(), SessionState.empty(), null, "0.9.0");

        // When/Then: Should need migration
        assertTrue(state.needsMigration("1.0.0"));
        assertTrue(state.needsMigration("2.0.0"));
    }

    @Test
    void needsMigration_ShouldReturnFalseForSameOrNewerVersion() {
        // Given: State with same or newer version
        ApplicationState state1 = ApplicationState.create(
                WindowState.defaultState(), SessionState.empty(), null, "1.0.0");
        ApplicationState state2 = ApplicationState.create(
                WindowState.defaultState(), SessionState.empty(), null, "1.1.0");

        // When/Then: Should not need migration
        assertFalse(state1.needsMigration("1.0.0"));
        assertFalse(state2.needsMigration("1.0.0"));
    }

    @Test
    void needsMigration_ShouldHandleNullVersions() {
        // Given: State with null version
        ApplicationState state = new ApplicationState(
                WindowState.defaultState(), SessionState.empty(), null, null, null, System.currentTimeMillis());

        // When/Then: Should not need migration
        assertFalse(state.needsMigration("1.0.0"));
        assertFalse(state.needsMigration(null));
    }

    @Test
    void needsMigration_ShouldHandleInvalidVersionFormat() {
        // Given: State with invalid version format
        ApplicationState state = ApplicationState.create(
                WindowState.defaultState(), SessionState.empty(), null, "invalid");

        // When/Then: Should not need migration
        assertFalse(state.needsMigration("1.0.0"));
    }

    @Test
    void withWindowState_ShouldCreateNewStateWithUpdatedWindowState() {
        // Given: Original state
        ApplicationState original = ApplicationState.defaultState();
        WindowState newWindow = new WindowState(1920, 1080, 200, 100, true, false);

        // When: Update window state
        ApplicationState updated = original.withWindowState(newWindow);

        // Then: Should have new window state, others unchanged
        assertNotNull(updated);
        assertNotSame(original, updated);
        assertEquals(newWindow, updated.windowState());
        assertEquals(original.sessionState(), updated.sessionState());
        assertEquals(original.conversionSettings(), updated.conversionSettings());
        assertEquals(original.fileListSortState(), updated.fileListSortState());
        assertEquals(original.version(), updated.version());
        assertTrue(updated.lastSaved() >= original.lastSaved());
    }

    @Test
    void withSessionState_ShouldCreateNewStateWithUpdatedSessionState() {
        // Given: Original state
        ApplicationState original = ApplicationState.defaultState();
        SessionState newSession = new SessionState(
                java.util.List.of(Paths.get("/tmp/file.mp4")),
                Paths.get("/tmp"),
                Paths.get("/tmp/output"),
                java.util.List.of(),
                "HD 1080p");

        // When: Update session state
        ApplicationState updated = original.withSessionState(newSession);

        // Then: Should have new session state, others unchanged
        assertNotNull(updated);
        assertNotSame(original, updated);
        assertEquals(original.windowState(), updated.windowState());
        assertEquals(newSession, updated.sessionState());
        assertEquals(original.conversionSettings(), updated.conversionSettings());
        assertEquals(original.fileListSortState(), updated.fileListSortState());
        assertEquals(original.version(), updated.version());
    }

    @Test
    void withConversionSettings_ShouldCreateNewStateWithUpdatedSettings() {
        // Given: Original state
        ApplicationState original = ApplicationState.defaultState();
        ConversionSettings newSettings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP4)
                .outputDirectory(Paths.get("/tmp"))
                .parallelConversions(4)
                .build();

        // When: Update conversion settings
        ApplicationState updated = original.withConversionSettings(newSettings);

        // Then: Should have new settings, others unchanged
        assertNotNull(updated);
        assertNotSame(original, updated);
        assertEquals(original.windowState(), updated.windowState());
        assertEquals(original.sessionState(), updated.sessionState());
        assertEquals(newSettings, updated.conversionSettings());
        assertEquals(original.fileListSortState(), updated.fileListSortState());
        assertEquals(original.version(), updated.version());
    }

    @Test
    void withVersion_ShouldCreateNewStateWithUpdatedVersion() {
        // Given: Original state
        ApplicationState original = ApplicationState.defaultState();

        // When: Update version
        ApplicationState updated = original.withVersion("2.0.0");

        // Then: Should have new version, others unchanged
        assertNotNull(updated);
        assertNotSame(original, updated);
        assertEquals(original.windowState(), updated.windowState());
        assertEquals(original.sessionState(), updated.sessionState());
        assertEquals(original.conversionSettings(), updated.conversionSettings());
        assertEquals(original.fileListSortState(), updated.fileListSortState());
        assertEquals("2.0.0", updated.version());
    }

    @Test
    void withFileListSortState_ShouldCreateNewStateWithUpdatedSortState() {
        // Given: Original state
        ApplicationState original = ApplicationState.defaultState();
        FileListSortState newSortState = FileListSortState.byName(FileListSortState.SortDirection.DESCENDING);

        // When: Update file list sort state
        ApplicationState updated = original.withFileListSortState(newSortState);

        // Then: Should have new sort state, others unchanged
        assertNotNull(updated);
        assertNotSame(original, updated);
        assertEquals(original.windowState(), updated.windowState());
        assertEquals(original.sessionState(), updated.sessionState());
        assertEquals(original.conversionSettings(), updated.conversionSettings());
        assertEquals(newSortState, updated.fileListSortState());
        assertEquals(original.version(), updated.version());
        assertTrue(updated.lastSaved() >= original.lastSaved());
    }

    @Test
    void withFileListSortState_ShouldHandleNullSortState() {
        // Given: Original state with custom sort
        ApplicationState original = ApplicationState.defaultState()
                .withFileListSortState(FileListSortState.bySize(FileListSortState.SortDirection.ASCENDING));

        // When: Update with null sort state
        ApplicationState updated = original.withFileListSortState(null);

        // Then: Should convert null to unsorted state
        assertNotNull(updated);
        assertNotNull(updated.fileListSortState());
        assertFalse(updated.fileListSortState().isSorted());
    }

    @Test
    void equals_ShouldReturnTrueForIdenticalStates() {
        // Given: Two identical states
        WindowState window = WindowState.defaultState();
        SessionState session = SessionState.empty();
        FileListSortState sortState = FileListSortState.unsorted();
        long timestamp = System.currentTimeMillis();

        ApplicationState state1 = new ApplicationState(window, session, null, sortState, "1.0.0", timestamp);
        ApplicationState state2 = new ApplicationState(window, session, null, sortState, "1.0.0", timestamp);

        // When/Then: Should be equal
        assertEquals(state1, state2);
        assertEquals(state1.hashCode(), state2.hashCode());
    }

    @Test
    void equals_ShouldReturnFalseForDifferentStates() {
        // Given: Two different states
        ApplicationState state1 = ApplicationState.defaultState();
        ApplicationState state2 = ApplicationState.create(
                new WindowState(1200, 800, 0, 0, false, false), SessionState.empty(), null, "1.0.0");

        // When/Then: Should not be equal
        assertNotEquals(state1, state2);
    }

    @Test
    void equals_ShouldHandleSameInstance() {
        // Given: Single state
        ApplicationState state = ApplicationState.defaultState();

        // When/Then: Should equal itself
        assertEquals(state, state);
    }

    @Test
    void equals_ShouldHandleNull() {
        // Given: State
        ApplicationState state = ApplicationState.defaultState();

        // When/Then: Should not equal null
        assertNotEquals(state, null);
    }

    @Test
    void equals_ShouldHandleDifferentClass() {
        // Given: State and different object
        ApplicationState state = ApplicationState.defaultState();

        // When/Then: Should not equal different class
        assertNotEquals(state, "String object");
    }

    @Test
    void toString_ShouldIncludeAllFields() {
        // Given: State
        ApplicationState state = ApplicationState.defaultState();

        // When: Convert to string
        String str = state.toString();

        // Then: Should contain all key information
        assertNotNull(str);
        assertTrue(str.contains("windowState"));
        assertTrue(str.contains("sessionState"));
        assertTrue(str.contains("fileListSortState"));
        assertTrue(str.contains("version"));
        assertTrue(str.contains("lastSaved"));
    }

    // --- Tests for REQ-FL-4.5: File list sort state persistence ---

    @Test
    void fileListSortState_ShouldBeIncludedInDefaultState() {
        // When: Create default state
        ApplicationState state = ApplicationState.defaultState();

        // Then: Should have unsorted file list state
        assertNotNull(state.fileListSortState());
        assertFalse(state.fileListSortState().isSorted());
    }

    @Test
    void fileListSortState_ShouldBeIncludedInCreateMethod() {
        // Given: Components without explicit sort state
        WindowState window = WindowState.defaultState();
        SessionState session = SessionState.empty();

        // When: Create state
        ApplicationState state = ApplicationState.create(window, session, null, "1.0.0");

        // Then: Should have unsorted file list state
        assertNotNull(state.fileListSortState());
        assertFalse(state.fileListSortState().isSorted());
    }

    @Test
    void fileListSortState_ShouldSupportBackwardCompatibility() {
        // Given: Old state JSON without fileListSortState field (simulated with null)
        ApplicationState oldState = new ApplicationState(
                WindowState.defaultState(),
                SessionState.empty(),
                null,
                null, // Old state files won't have this field
                "1.0.0",
                System.currentTimeMillis());

        // When: Access file list sort state
        FileListSortState sortState = oldState.fileListSortState();

        // Then: Should have default unsorted state (not null)
        assertNotNull(sortState);
        assertFalse(sortState.isSorted());
    }

    @Test
    void fileListSortState_ShouldPersistSortedState() {
        // Given: State with sorted file list
        FileListSortState sortedByName = FileListSortState.byName(FileListSortState.SortDirection.ASCENDING);
        ApplicationState state = ApplicationState.defaultState()
                .withFileListSortState(sortedByName);

        // When: Get sort state
        FileListSortState retrieved = state.fileListSortState();

        // Then: Should have same sort state
        assertNotNull(retrieved);
        assertTrue(retrieved.isSorted());
        assertEquals(FileListSortState.SortField.NAME, retrieved.sortField());
        assertEquals(FileListSortState.SortDirection.ASCENDING, retrieved.sortDir());
    }

    @Test
    void validated_ShouldEnsureNonNullFileListSortState() {
        // Given: State with null sort state
        ApplicationState state = new ApplicationState(
                WindowState.defaultState(),
                SessionState.empty(),
                null,
                null, // Null sort state
                "1.0.0",
                System.currentTimeMillis());

        // When: Validate
        ApplicationState validated = state.validated();

        // Then: Should have non-null unsorted state
        assertNotNull(validated.fileListSortState());
        assertFalse(validated.fileListSortState().isSorted());
    }
}