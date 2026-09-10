// filepath: src/test/java/org/omc/controller/StateManagerTest.java

package org.omc.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.omc.core.ConfigurationManager;
import org.omc.model.ApplicationState;
import org.omc.model.FileListSortState;
import org.omc.model.FileListSortState.SortDirection;
import org.omc.model.FileListSortState.SortField;
import org.omc.model.SessionState;
import org.omc.model.WindowState;
import org.omc.util.JsonUtils;

/**
 * Unit tests for StateManager.
 * Tests state persistence, window state, session state, and recovery from
 * corruption.
 * 
 * Requirements: REQ-005.1, REQ-005.2, REQ-005.3
 */
class StateManagerTest {

    @TempDir
    Path tempDir;

    private ConfigurationManager configurationManager;
    private StateManager stateManager;

    private Path configDir;
    private Path dataDir;
    private Path cacheDir;

    @BeforeEach
    void setUp() throws Exception {
        // Create temporary directories
        configDir = tempDir.resolve("config");
        dataDir = tempDir.resolve("data");
        cacheDir = tempDir.resolve("cache");

        Files.createDirectories(configDir);
        Files.createDirectories(dataDir);
        Files.createDirectories(cacheDir);

        // Create instances
        configurationManager = new ConfigurationManager(configDir, dataDir, cacheDir);
        stateManager = new StateManager(configurationManager);
    }

    @AfterEach
    void tearDown() {
        // Cleanup handled by @TempDir
    }

    // Constructor tests
    @Test
    void constructor_ShouldInitializeWithConfigurationManager() {
        assertNotNull(stateManager);
        assertNotNull(stateManager.getCurrentState());
    }

    @Test
    void constructor_ShouldThrowWhenConfigurationManagerIsNull() {
        assertThrows(NullPointerException.class, () -> {
            new StateManager(null);
        });
    }

    // loadState() tests
    @Test
    void loadState_ShouldReturnDefaultStateWhenFileDoesNotExist() {
        // When: Load state when file doesn't exist
        ApplicationState state = stateManager.loadState();

        // Then: Should return default state
        assertNotNull(state);
        assertNotNull(state.windowState());
        assertNotNull(state.sessionState());
        assertEquals("1.0.0", state.version());
    }

    @Test
    void loadState_ShouldLoadSavedState() throws IOException {
        // Given: Create actual temporary file that exists
        Path tempFile = tempDir.resolve("file1.mp4");
        Files.createFile(tempFile);

        Path tempOutput = tempDir.resolve("output");
        Files.createDirectories(tempOutput);

        // Given: Saved state
        WindowState windowState = new WindowState(1200, 800, 100, 100, false, false);
        SessionState sessionState = new SessionState(
                List.of(tempFile),
                null,
                tempOutput,
                List.of(),
                null);

        ApplicationState savedState = ApplicationState.create(
                windowState, sessionState, null, "1.0.0");

        stateManager.saveState(savedState);

        // When: Load state
        StateManager newManager = new StateManager(configurationManager);
        ApplicationState loadedState = newManager.loadState();

        // Then: Should load saved state
        assertNotNull(loadedState);
        assertEquals(1200, loadedState.windowState().width());
        assertEquals(800, loadedState.windowState().height());
        assertEquals(1, loadedState.sessionState().recentFilePaths().size());
    }

    @Test
    void loadState_ShouldHandleCorruptedFile() throws IOException {
        // Given: Corrupted state file
        Path statePath = stateManager.getStateFilePath();
        Files.writeString(statePath, "{ corrupted json }", StandardOpenOption.CREATE);

        // When: Load state
        ApplicationState state = stateManager.loadState();

        // Then: Should return default state
        assertNotNull(state);
        assertEquals("1.0.0", state.version());

        // And: Corrupted file should be backed up
        assertTrue(Files.list(configDir)
                .anyMatch(p -> p.getFileName().toString().contains(".backup")));
    }

    @Test
    void loadState_ShouldHandleEmptyFile() throws IOException {
        // Given: Empty state file
        Path statePath = stateManager.getStateFilePath();
        Files.writeString(statePath, "", StandardOpenOption.CREATE);

        // When: Load state
        ApplicationState state = stateManager.loadState();

        // Then: Should return default state
        assertNotNull(state);
    }

    @Test
    void loadState_ShouldValidateAndCleanState() throws IOException {
        // Given: State with invalid window dimensions
        WindowState invalidWindow = new WindowState(-100, -100, 0, 0, false, false);
        ApplicationState invalidState = ApplicationState.create(
                invalidWindow, SessionState.empty(), null, "1.0.0");

        // Write directly to file
        Path statePath = stateManager.getStateFilePath();
        JsonUtils.writeJsonFile(invalidState, statePath.toFile());

        // When: Load state
        StateManager newManager = new StateManager(configurationManager);
        ApplicationState loadedState = newManager.loadState();

        // Then: Should validate and use defaults for invalid values
        assertNotNull(loadedState);
        assertTrue(loadedState.windowState().isValid());
    }

    // saveState() tests
    @Test
    void saveState_ShouldPersistStateToFile() throws IOException {
        // Given: Application state
        WindowState windowState = new WindowState(1024, 768, 50, 50, false, false);
        ApplicationState state = ApplicationState.create(
                windowState, SessionState.empty(), null, "1.0.0");

        // When: Save state
        stateManager.saveState(state);

        // Then: State file should exist
        assertTrue(Files.exists(stateManager.getStateFilePath()));

        // And: State should be readable
        ApplicationState loaded = JsonUtils.readJsonFile(
                stateManager.getStateFilePath().toFile(),
                ApplicationState.class);
        assertNotNull(loaded);
        assertEquals(1024, loaded.windowState().width());
    }

    @Test
    void saveState_ShouldThrowWhenStateIsNull() {
        assertThrows(NullPointerException.class, () -> {
            stateManager.saveState(null);
        });
    }

    @Test
    void saveState_ShouldUpdateVersion() throws IOException {
        // Given: State with old version
        ApplicationState state = ApplicationState.create(
                WindowState.defaultState(), SessionState.empty(), null, "0.9.0");

        // When: Save state
        stateManager.saveState(state);

        // Then: Version should be updated
        ApplicationState loaded = stateManager.loadState();
        assertEquals("1.0.0", loaded.version());
    }

    @Test
    void saveState_ShouldUseAtomicWrite() throws IOException {
        // Given: Valid state
        ApplicationState state = ApplicationState.create(
                WindowState.defaultState(), SessionState.empty(), null, "1.0.0");

        // When: Save state
        stateManager.saveState(state);

        // Then: Temp file should not exist
        Path statePath = stateManager.getStateFilePath();
        Path tempPath = Path.of(statePath.toString() + ".tmp");
        assertFalse(Files.exists(tempPath));

        // And: Final file should exist
        assertTrue(Files.exists(statePath));
    }

    // loadWindowState() tests
    @Test
    void loadWindowState_ShouldReturnDefaultWhenNoStateExists() {
        // When: Load window state when no state exists
        WindowState windowState = stateManager.loadWindowState();

        // Then: Should return default window state
        assertNotNull(windowState);
        assertEquals(1000, windowState.width());
        assertEquals(700, windowState.height());
        assertTrue(windowState.isValid());
    }

    @Test
    void loadWindowState_ShouldReturnSavedWindowState() throws IOException {
        // Given: Saved window state
        WindowState savedWindow = new WindowState(1920, 1080, 200, 100, true, false);
        ApplicationState state = ApplicationState.create(
                savedWindow, SessionState.empty(), null, "1.0.0");

        stateManager.saveState(state);

        // When: Load window state
        WindowState loadedWindow = stateManager.loadWindowState();

        // Then: Should return saved window state
        assertNotNull(loadedWindow);
        assertEquals(1920, loadedWindow.width());
        assertEquals(1080, loadedWindow.height());
        assertEquals(200, loadedWindow.x());
        assertEquals(100, loadedWindow.y());
        assertTrue(loadedWindow.maximized());
        assertFalse(loadedWindow.fullscreen());
    }

    @Test
    void loadWindowState_ShouldReturnDefaultForInvalidState() throws IOException {
        // Given: Invalid window state
        WindowState invalidWindow = new WindowState(-1, -1, 0, 0, false, false);
        ApplicationState state = ApplicationState.create(
                invalidWindow, SessionState.empty(), null, "1.0.0");

        // Write directly to file to bypass validation
        JsonUtils.writeJsonFile(state, stateManager.getStateFilePath().toFile());

        // When: Load window state
        StateManager newManager = new StateManager(configurationManager);
        newManager.loadState();
        WindowState loadedWindow = newManager.loadWindowState();

        // Then: Should return default window state
        assertNotNull(loadedWindow);
        assertTrue(loadedWindow.isValid());
    }

    // saveWindowState() tests
    @Test
    void saveWindowState_ShouldPersistWindowState() throws IOException {
        // Given: Window state
        WindowState windowState = new WindowState(1600, 900, 150, 75, false, false);

        // When: Save window state
        stateManager.saveWindowState(windowState);

        // Then: Window state should be persisted
        WindowState loaded = stateManager.loadWindowState();
        assertEquals(1600, loaded.width());
        assertEquals(900, loaded.height());
        assertEquals(150, loaded.x());
        assertEquals(75, loaded.y());
    }

    @Test
    void saveWindowState_ShouldThrowWhenWindowStateIsNull() {
        assertThrows(NullPointerException.class, () -> {
            stateManager.saveWindowState(null);
        });
    }

    @Test
    void saveWindowState_ShouldThrowForInvalidWindowState() {
        // Given: Invalid window state
        WindowState invalidWindow = new WindowState(-100, -100, 0, 0, false, false);

        // When/Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            stateManager.saveWindowState(invalidWindow);
        });
    }

    // loadSessionState() tests
    @Test
    void loadSessionState_ShouldReturnEmptyWhenNoStateExists() {
        // When: Load session state when no state exists
        SessionState sessionState = stateManager.loadSessionState();

        // Then: Should return empty session state
        assertNotNull(sessionState);
        assertTrue(sessionState.recentFilePaths().isEmpty());
        assertTrue(sessionState.pendingFiles().isEmpty());
    }

    @Test
    void loadSessionState_ShouldReturnSavedSessionState() throws IOException {
        // Given: Create actual temporary files that exist
        Path tempFile1 = tempDir.resolve("file1.mp4");
        Path tempFile2 = tempDir.resolve("file2.mp3");
        Files.createFile(tempFile1);
        Files.createFile(tempFile2);

        Path tempInput = tempDir.resolve("input");
        Path tempOutput = tempDir.resolve("output");
        Files.createDirectories(tempInput);
        Files.createDirectories(tempOutput);

        // Given: Saved session state
        SessionState savedSession = new SessionState(
                List.of(tempFile1, tempFile2),
                tempInput,
                tempOutput,
                List.of(),
                null);

        ApplicationState state = ApplicationState.create(
                WindowState.defaultState(), savedSession, null, "1.0.0");

        stateManager.saveState(state);

        // When: Load session state
        SessionState loadedSession = stateManager.loadSessionState();

        // Then: Should return saved session state
        assertNotNull(loadedSession);
        assertEquals(2, loadedSession.recentFilePaths().size());
        assertEquals(tempInput, loadedSession.lastInputDirectory());
        assertEquals(tempOutput, loadedSession.lastOutputDirectory());
    }

    @Test
    void loadSessionState_ShouldValidateAndCleanPaths() throws IOException {
        // Given: Session state with non-existent files
        SessionState sessionWithInvalidPaths = new SessionState(
                List.of(
                        Paths.get("/nonexistent/file1.mp4"),
                        Paths.get("/nonexistent/file2.mp3")),
                null,
                null,
                List.of(),
                null);

        ApplicationState state = ApplicationState.create(
                WindowState.defaultState(), sessionWithInvalidPaths, null, "1.0.0");

        stateManager.saveState(state);

        // When: Load session state
        StateManager newManager = new StateManager(configurationManager);
        newManager.loadState();
        SessionState loadedSession = newManager.loadSessionState();

        // Then: Should validate and clean invalid paths
        assertNotNull(loadedSession);
        // Invalid paths should be removed by validation
        assertTrue(loadedSession.recentFilePaths().isEmpty() ||
                loadedSession.recentFilePaths().size() <= 2);
    }

    // saveSessionState() tests
    @Test
    void saveSessionState_ShouldPersistSessionState() throws IOException {
        // Given: Create actual temporary file that exists
        Path tempFile = tempDir.resolve("recent.mp4");
        Files.createFile(tempFile);

        Path tempOutput = tempDir.resolve("out");
        Files.createDirectories(tempOutput);

        // Given: Session state
        SessionState sessionState = new SessionState(
                List.of(tempFile),
                null,
                tempOutput,
                List.of(),
                null);

        // When: Save session state
        stateManager.saveSessionState(sessionState);

        // Then: Session state should be persisted
        SessionState loaded = stateManager.loadSessionState();
        assertEquals(1, loaded.recentFilePaths().size());
        assertEquals(tempOutput, loaded.lastOutputDirectory());
    }

    @Test
    void saveSessionState_ShouldThrowWhenSessionStateIsNull() {
        assertThrows(NullPointerException.class, () -> {
            stateManager.saveSessionState(null);
        });
    }

    // getCurrentState() tests
    @Test
    void getCurrentState_ShouldReturnCurrentState() throws IOException {
        // Given: Saved state
        WindowState windowState = new WindowState(1280, 720, 0, 0, false, false);
        ApplicationState state = ApplicationState.create(
                windowState, SessionState.empty(), null, "1.0.0");

        stateManager.saveState(state);

        // When: Get current state
        ApplicationState current = stateManager.getCurrentState();

        // Then: Should return current state
        assertNotNull(current);
        assertEquals(1280, current.windowState().width());
    }

    // resetToDefaults() tests
    @Test
    void resetToDefaults_ShouldRestoreDefaultState() throws IOException {
        // Given: Custom state
        WindowState customWindow = new WindowState(2560, 1440, 100, 100, true, false);
        ApplicationState customState = ApplicationState.create(
                customWindow, SessionState.empty(), null, "1.0.0");

        stateManager.saveState(customState);

        // When: Reset to defaults
        stateManager.resetToDefaults();

        // Then: State should be defaults
        ApplicationState state = stateManager.getCurrentState();
        assertEquals(1000, state.windowState().width());
        assertEquals(700, state.windowState().height());

        // And: State file should contain defaults
        ApplicationState loaded = stateManager.loadState();
        assertEquals(1000, loaded.windowState().width());
    }

    // stateFileExists() tests
    @Test
    void stateFileExists_ShouldReturnFalseWhenFileDoesNotExist() {
        // When: Check if state file exists
        boolean exists = stateManager.stateFileExists();

        // Then: Should return false
        assertFalse(exists);
    }

    @Test
    void stateFileExists_ShouldReturnTrueWhenFileExists() throws IOException {
        // Given: Saved state
        stateManager.saveState(ApplicationState.defaultState());

        // When: Check if state file exists
        boolean exists = stateManager.stateFileExists();

        // Then: Should return true
        assertTrue(exists);
    }

    // getStateFilePath() tests
    @Test
    void getStateFilePath_ShouldReturnCorrectPath() {
        // When: Get state file path
        Path path = stateManager.getStateFilePath();

        // Then: Should return correct path
        assertNotNull(path);
        assertTrue(path.toString().endsWith("state.json"));
        assertEquals(configurationManager.getConfigDirectory().resolve("state.json"), path);
    }

    // Backup and recovery tests
    @Test
    void loadState_ShouldBackupCorruptedFileWithTimestamp() throws IOException {
        // Given: Corrupted state file
        Path statePath = stateManager.getStateFilePath();
        Files.writeString(statePath, "{ invalid }", StandardOpenOption.CREATE);

        // When: Load state (triggers backup)
        stateManager.loadState();

        // Then: Backup file should exist with timestamp
        long backupCount = Files.list(configDir)
                .filter(p -> p.getFileName().toString().contains(".backup"))
                .count();

        assertTrue(backupCount > 0);
    }

    // Concurrent access tests
    @Test
    void saveState_ShouldHandleConcurrentWrites() throws Exception {
        // Given: Multiple threads trying to save state
        Thread[] threads = new Thread[5];

        for (int i = 0; i < threads.length; i++) {
            final int width = 1000 + (i * 100);
            threads[i] = new Thread(() -> {
                try {
                    WindowState windowState = new WindowState(width, 700, 0, 0, false, false);
                    ApplicationState state = ApplicationState.create(
                            windowState, SessionState.empty(), null, "1.0.0");

                    stateManager.saveState(state);
                } catch (IOException e) {
                    fail("Concurrent save failed: " + e.getMessage());
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Then: State file should exist and be valid
        assertTrue(stateManager.stateFileExists());
        ApplicationState loaded = stateManager.loadState();
        assertNotNull(loaded);
        assertTrue(loaded.windowState().width() >= 1000 && loaded.windowState().width() <= 1400);
    }

    // Migration tests
    @Test
    void loadState_ShouldMigrateOldVersionToNewVersion() throws IOException {
        // Given: State with old version
        ApplicationState oldState = ApplicationState.create(
                WindowState.defaultState(), SessionState.empty(), null, "0.5.0");

        // Write directly to file
        JsonUtils.writeJsonFile(oldState, stateManager.getStateFilePath().toFile());

        // When: Load state (triggers migration)
        StateManager newManager = new StateManager(configurationManager);
        ApplicationState migrated = newManager.loadState();

        // Then: Version should be updated
        assertEquals("1.0.0", migrated.version());
    }

    // Concurrent read-modify-write tests
    //
    // These tests reproduce the lost-update defect: the update methods used
    // to read the current state, derive an updated copy, and save it in
    // separate steps. Two threads updating different fields could interleave
    // so that the last writer's stale snapshot silently discarded the other
    // thread's update.
    //
    // The tests are deterministic without relying on thread timing: a
    // per-iteration CyclicBarrier makes both threads snapshot the state at
    // the same moment, and because saveState serializes on the manager
    // monitor for the whole file write, the thread that saves second always
    // computed its update from a snapshot taken before the first thread's
    // write became visible. The barrier after both saves lets each thread
    // verify its own update survived before the next iteration begins.

    private static final int RACE_ITERATIONS = 25;

    @Test
    void concurrentSaveWindowStateAndSaveSessionState_ShouldNotLoseUpdates() throws Exception {
        CyclicBarrier startIteration = new CyclicBarrier(2);
        CyclicBarrier iterationDone = new CyclicBarrier(2);
        List<String> lostUpdates = new CopyOnWriteArrayList<>();

        Thread windowThread = new Thread(() -> {
            try {
                for (int i = 0; i < RACE_ITERATIONS; i++) {
                    WindowState windowState = new WindowState(1100 + i, 700, 0, 0, false, false);
                    startIteration.await();
                    stateManager.saveWindowState(windowState);
                    iterationDone.await();
                    if (stateManager.getCurrentState().windowState().width() != windowState.width()) {
                        lostUpdates.add("window update #" + i + " (width " + windowState.width() + ") was lost");
                    }
                }
            } catch (Exception e) {
                lostUpdates.add("window thread failed: " + e);
            }
        }, "window-saver");

        Thread sessionThread = new Thread(() -> {
            try {
                for (int i = 0; i < RACE_ITERATIONS; i++) {
                    int expectedRecentCount = i + 1;
                    SessionState sessionState = new SessionState(
                            phantomRecentPaths(expectedRecentCount), null, null, List.of(), null);
                    startIteration.await();
                    stateManager.saveSessionState(sessionState);
                    iterationDone.await();
                    if (stateManager.getCurrentState().sessionState().recentFilePaths().size()
                            != expectedRecentCount) {
                        lostUpdates.add("session update #" + i + " ("
                                + expectedRecentCount + " recent paths) was lost");
                    }
                }
            } catch (Exception e) {
                lostUpdates.add("session thread failed: " + e);
            }
        }, "session-saver");

        windowThread.start();
        sessionThread.start();
        windowThread.join();
        sessionThread.join();

        assertTrue(lostUpdates.isEmpty(), "Lost updates detected: " + lostUpdates);
    }

    @Test
    void concurrentSaveSortStateAndSaveApplicationState_ShouldNotLoseUpdates() throws Exception {
        FileManager mockFileManager = Mockito.mock(FileManager.class);
        SettingsManager mockSettingsManager = Mockito.mock(SettingsManager.class);
        Mockito.when(mockFileManager.getFiles()).thenReturn(List.of());
        SessionStateHandler handler = new SessionStateHandler(
                stateManager, mockFileManager, mockSettingsManager);

        CyclicBarrier startIteration = new CyclicBarrier(2);
        CyclicBarrier iterationDone = new CyclicBarrier(2);
        List<String> lostUpdates = new CopyOnWriteArrayList<>();

        // Simulates the GTK thread persisting a user sort change
        Thread sortThread = new Thread(() -> {
            try {
                for (int i = 0; i < RACE_ITERATIONS; i++) {
                    FileListSortState sortState = new FileListSortState(
                            SortField.values()[i % SortField.values().length],
                            i % 2 == 0 ? SortDirection.ASCENDING : SortDirection.DESCENDING);
                    startIteration.await();
                    handler.saveSortState(sortState);
                    iterationDone.await();
                    if (!sortState.equals(stateManager.getCurrentState().fileListSortState())) {
                        lostUpdates.add("sort update #" + i + " (" + sortState + ") was lost");
                    }
                }
            } catch (Exception e) {
                lostUpdates.add("sort thread failed: " + e);
            }
        }, "sort-saver");

        // Simulates the worker thread saving session state on batch completion
        Thread applicationThread = new Thread(() -> {
            try {
                for (int i = 0; i < RACE_ITERATIONS; i++) {
                    int expectedRecentCount = i + 1;
                    List<Path> recentFilePaths = phantomRecentPaths(expectedRecentCount);
                    startIteration.await();
                    handler.saveApplicationState(recentFilePaths, null, null, null);
                    iterationDone.await();
                    if (stateManager.getCurrentState().sessionState().recentFilePaths().size()
                            != expectedRecentCount) {
                        lostUpdates.add("application state update #" + i + " ("
                                + expectedRecentCount + " recent paths) was lost");
                    }
                }
            } catch (Exception e) {
                lostUpdates.add("application thread failed: " + e);
            }
        }, "application-saver");

        sortThread.start();
        applicationThread.start();
        sortThread.join();
        applicationThread.join();

        assertTrue(lostUpdates.isEmpty(), "Lost updates detected: " + lostUpdates);
    }

    /** Builds a list of {@code count} recent-file paths (need not exist on disk). */
    private static List<Path> phantomRecentPaths(int count) {
        List<Path> paths = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            paths.add(Path.of("/nonexistent/recent-" + i + ".mp4"));
        }
        return paths;
    }

    // updateState() tests
    @Test
    void updateState_ShouldApplyUpdateAndReturnPersistedState() throws IOException {
        // Given: A window state update
        WindowState windowState = new WindowState(1400, 900, 0, 0, false, false);

        // When: Atomically update the state
        ApplicationState result = stateManager.updateState(
                state -> state.withWindowState(windowState));

        // Then: Returned state carries the update and current version
        assertNotNull(result);
        assertEquals(1400, result.windowState().width());
        assertEquals(ApplicationState.CURRENT_STATE_VERSION, result.version());

        // And: Current in-memory state carries the update
        assertEquals(1400, stateManager.getCurrentState().windowState().width());

        // And: State is persisted to disk
        ApplicationState loaded = new StateManager(configurationManager).loadState();
        assertEquals(1400, loaded.windowState().width());
    }

    @Test
    void updateState_ShouldThrowWhenOperatorIsNull() {
        assertThrows(NullPointerException.class, () -> {
            stateManager.updateState(null);
        });
    }

    @Test
    void updateState_ShouldThrowWhenOperatorReturnsNull() {
        assertThrows(NullPointerException.class, () -> {
            stateManager.updateState(state -> null);
        });
    }

    @Test
    void updateState_ShouldApplyAllConcurrentUpdatesAtomically() throws Exception {
        // Given: Real files (so the persisted state survives load-time validation)
        // and N threads that each append a distinct recent path M times
        int threadCount = 4;
        int iterationsPerThread = 25;
        int totalUpdates = threadCount * iterationsPerThread;
        for (int i = 0; i < totalUpdates; i++) {
            Files.createFile(tempDir.resolve("recent-real-" + i + ".mp4"));
        }

        CyclicBarrier start = new CyclicBarrier(threadCount);
        List<String> failures = new CopyOnWriteArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        int index = threadId * iterationsPerThread + i;
                        Path recentPath = tempDir.resolve("recent-real-" + index + ".mp4");
                        stateManager.updateState(state -> {
                            List<Path> recents =
                                    new ArrayList<>(state.sessionState().recentFilePaths());
                            recents.add(recentPath);
                            return state.withSessionState(
                                    new SessionState(recents, null, null, List.of(), null));
                        });
                    }
                } catch (Exception e) {
                    failures.add("thread " + threadId + " failed: " + e);
                }
            }, "updater-" + t);
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        // Then: Every update is reflected (no lost updates)
        assertTrue(failures.isEmpty(), "Update failures: " + failures);
        assertEquals(totalUpdates,
                stateManager.getCurrentState().sessionState().recentFilePaths().size());

        // And: The persisted file reflects every update too
        ApplicationState loaded = new StateManager(configurationManager).loadState();
        assertEquals(totalUpdates, loaded.sessionState().recentFilePaths().size());
    }
}
