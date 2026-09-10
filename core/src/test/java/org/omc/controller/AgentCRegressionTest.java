package org.omc.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.omc.core.ConfigurationManager;
import org.omc.core.ValidationEngine;
import org.omc.model.ApplicationState;
import org.omc.model.FileFormat;
import org.omc.model.SessionState;
import org.omc.model.WindowState;
import org.omc.service.FileHandler;
import org.omc.util.JsonUtils;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the persistence audit fixes (agent C).
 *
 * <p>Covers four audit items:</p>
 * <ol>
 * <li>State schema migration: major+minor comparison, a real step-based
 * migration framework, and non-destructive handling of newer schemas</li>
 * <li>{@code ATOMIC_MOVE} fallback when the filesystem does not support atomic
 * moves (e.g. some network or FAT mounts)</li>
 * <li>Corrupt-settings salvage: valid section subtrees survive a corrupt
 * settings.json instead of a full reset to defaults</li>
 * <li>Unified sub-second backup filename timestamps</li>
 * </ol>
 */
class AgentCRegressionTest {

    /**
     * Backup filenames must carry sub-second precision so that rapid
     * successive backups cannot collide within the same second.
     */
    private static final Pattern SUBSECOND_BACKUP_SUFFIX =
            Pattern.compile("\\.backup_\\d{8}_\\d{6}_\\d{6}$");

    @TempDir
    Path tempDir;

    private ConfigurationManager configurationManager;
    private StateManager stateManager;
    private SettingsManager settingsManager;

    private Path configDir;
    private Path dataDir;
    private Path cacheDir;

    @BeforeEach
    void setUp() throws IOException, org.omc.exception.StateIOException {
        configDir = tempDir.resolve("config");
        dataDir = tempDir.resolve("data");
        cacheDir = tempDir.resolve("cache");

        Files.createDirectories(configDir);
        Files.createDirectories(dataDir);
        Files.createDirectories(cacheDir);

        configurationManager = new ConfigurationManager(configDir, dataDir, cacheDir);
        stateManager = new StateManager(configurationManager);
        settingsManager = new SettingsManager(configurationManager,
                new ValidationEngine(new FileHandler(configurationManager)));
    }

    // ========== Item 1: state schema migration framework ==========

    @Test
    void loadState_SameVersionAsCurrent_DoesNotMigrateAndKeepsData() throws IOException {
        // Given: State already at the current schema version with distinctive data
        WindowState window = new WindowState(1234, 700, 10, 10, false, false);
        ApplicationState state = ApplicationState.create(
                window, SessionState.empty(), null, ApplicationState.CURRENT_STATE_VERSION);
        JsonUtils.writeJsonFile(state, stateManager.getStateFilePath().toFile());

        // When
        ApplicationState loaded = new StateManager(configurationManager).loadState();

        // Then: no migration, data preserved
        assertEquals(ApplicationState.CURRENT_STATE_VERSION, loaded.version());
        assertEquals(1234, loaded.windowState().width());
    }

    @Test
    void loadState_OlderMajorVersion_RunsMigrationAndBumpsStoredVersion() throws IOException {
        // Given: State written by an older schema (0.9)
        WindowState window = new WindowState(1234, 700, 10, 10, false, false);
        ApplicationState oldState = ApplicationState.create(
                window, SessionState.empty(), null, "0.9.0");
        JsonUtils.writeJsonFile(oldState, stateManager.getStateFilePath().toFile());

        // When
        ApplicationState loaded = new StateManager(configurationManager).loadState();

        // Then: migration ran, stored version is bumped, data is preserved
        assertEquals(ApplicationState.CURRENT_STATE_VERSION, loaded.version());
        assertEquals(1234, loaded.windowState().width());
    }

    @Test
    void loadState_UnknownOlderVersion_StillStampsCurrentVersionNonDestructively() throws IOException {
        // Given: State with a version for which no explicit step is registered
        WindowState window = new WindowState(1500, 700, 0, 0, false, false);
        ApplicationState oldState = ApplicationState.create(
                window, SessionState.empty(), null, "0.1.0");
        JsonUtils.writeJsonFile(oldState, stateManager.getStateFilePath().toFile());

        // When
        ApplicationState loaded = new StateManager(configurationManager).loadState();

        // Then: version stamped to current, no data destroyed
        assertEquals(ApplicationState.CURRENT_STATE_VERSION, loaded.version());
        assertEquals(1500, loaded.windowState().width());
    }

    @Test
    void loadState_NewerMajorVersionThanCurrent_KeepsStateAsIs() throws IOException {
        // Given: State written by a newer schema (downgrade scenario)
        WindowState window = new WindowState(1800, 700, 0, 0, false, false);
        ApplicationState newerState = ApplicationState.create(
                window, SessionState.empty(), null, "2.0.0");
        JsonUtils.writeJsonFile(newerState, stateManager.getStateFilePath().toFile());

        // When
        ApplicationState loaded = new StateManager(configurationManager).loadState();

        // Then: no destructive migration - version and data kept as-is
        assertEquals("2.0.0", loaded.version());
        assertEquals(1800, loaded.windowState().width());
    }

    @Test
    void loadState_NewerMinorVersionThanCurrent_KeepsStateAsIs() throws IOException {
        // Given: State written by a newer minor schema
        WindowState window = new WindowState(1600, 700, 0, 0, false, false);
        ApplicationState newerState = ApplicationState.create(
                window, SessionState.empty(), null, "1.1.0");
        JsonUtils.writeJsonFile(newerState, stateManager.getStateFilePath().toFile());

        // When
        ApplicationState loaded = new StateManager(configurationManager).loadState();

        // Then: kept as-is
        assertEquals("1.1.0", loaded.version());
        assertEquals(1600, loaded.windowState().width());
    }

    @Test
    void migrationStep_ShouldDispatchKnownSchemaPathsOnly() {
        // The dispatch registry maps "major.minor" source schemas to steps
        Optional<StateManager.MigrationStep> placeholder = StateManager.migrationStep("0.9");
        assertTrue(placeholder.isPresent(), "registered placeholder path must be dispatched");
        assertEquals("1.0", placeholder.orElseThrow().toSchema());

        // Current and unknown schemas have no outgoing step
        assertTrue(StateManager.migrationStep("1.0").isEmpty());
        assertTrue(StateManager.migrationStep("2.0").isEmpty());
        assertTrue(StateManager.migrationStep("0.1").isEmpty());
    }

    @Test
    void migrationStep_ToSchema_MustBeForwardProgressOnly() {
        // Every registered step must move strictly toward the current schema
        List<StateManager.MigrationStep> steps = StateManager.migrationSteps();
        ApplicationState.SchemaVersion current =
                ApplicationState.SchemaVersion.parse(ApplicationState.CURRENT_STATE_VERSION);
        assertNotNull(current);
        for (StateManager.MigrationStep step : steps) {
            ApplicationState.SchemaVersion from = ApplicationState.SchemaVersion.parse(step.fromSchema());
            ApplicationState.SchemaVersion to = ApplicationState.SchemaVersion.parse(step.toSchema());
            assertNotNull(from, "fromSchema must parse: " + step.fromSchema());
            assertNotNull(to, "toSchema must parse: " + step.toSchema());
            assertTrue(from.compareTo(to) < 0,
                    "step " + step.fromSchema() + "->" + step.toSchema() + " must advance");
            assertTrue(to.compareTo(current) <= 0,
                    "step " + step.fromSchema() + "->" + step.toSchema()
                            + " must not overshoot the current schema");
        }
    }

    // ========== Item 2: ATOMIC_MOVE fallback ==========

    @Test
    void saveState_FallsBackToNonAtomicMove_WhenAtomicMoveNotSupported() throws IOException {
        Path statePath = stateManager.getStateFilePath();
        Path tempPath = Path.of(statePath.toString() + ".tmp");

        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            stubMoveSimulatingFilesystemWithoutAtomicSupport(mockedFiles);

            // When: saving on a filesystem that rejects ATOMIC_MOVE
            ApplicationState state = ApplicationState.create(
                    WindowState.defaultState(), SessionState.empty(), null,
                    ApplicationState.CURRENT_STATE_VERSION);

            // Then: save must succeed via the non-atomic fallback, not throw
            assertDoesNotThrow(() -> stateManager.saveState(state));
        }

        assertTrue(Files.exists(statePath), "final state file must exist after fallback move");
        ApplicationState persisted = JsonUtils.readJsonFile(statePath.toFile(), ApplicationState.class);
        assertNotNull(persisted);
        assertFalse(Files.exists(tempPath), "temp file must not be left behind");
    }

    @Test
    void saveSettings_FallsBackToNonAtomicMove_WhenAtomicMoveNotSupported() throws IOException {
        Path settingsPath = settingsManager.getSettingsFilePath();

        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            stubMoveSimulatingFilesystemWithoutAtomicSupport(mockedFiles);

            // When
            assertDoesNotThrow(() -> settingsManager.saveSettings(
                    SettingsManager.createDefaultSettings()));
        }

        assertTrue(Files.exists(settingsPath), "settings file must exist after fallback move");
        assertFalse(Files.exists(Path.of(settingsPath.toString() + ".tmp")),
                "temp file must not be left behind");
    }

    @Test
    void saveState_CleansUpTempFile_WhenMoveStillFails() throws IOException {
        Path statePath = stateManager.getStateFilePath();
        Path tempPath = Path.of(statePath.toString() + ".tmp");

        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            stubMoveAlwaysFailing(mockedFiles);

            ApplicationState state = ApplicationState.create(
                    WindowState.defaultState(), SessionState.empty(), null,
                    ApplicationState.CURRENT_STATE_VERSION);

            // Then: the original failure still propagates
            assertThrows(java.io.IOException.class, () -> stateManager.saveState(state));
        }

        // And: the temp file is cleaned up even though the move failed
        assertFalse(Files.exists(tempPath), "temp file must be cleaned up on failure");
    }

    // ========== Item 3: corrupt-settings salvage ==========

    @Test
    void loadSettings_CorruptSection_SalvagesValidSections() throws IOException {
        // Given: settings.json with a corrupt video section but valid audio + global fields
        // (sections carry the full field set the app itself writes)
        Path outputDir = tempDir.resolve("out");
        Files.createDirectories(outputDir);
        writeSettingsJson("""
                {
                  "outputDirectory": "%s",
                  "overwriteExisting": true,
                  "createSubdirectory": false,
                  "parallelConversions": 7,
                  "videoSettings": {
                    "codec": "libx264",
                    "outputFormat": "TOTALLY_BOGUS_FORMAT"
                  },
                  "audioSettings": {
                    "codec": "libmp3lame",
                    "bitrate": 320,
                    "sampleRate": -1,
                    "channels": -1,
                    "quality": 5,
                    "outputFormat": "MP3"
                  }
                }
                """.formatted(outputDir));

        // When
        var settings = settingsManager.loadSettings();

        // Then: the valid audio section and global fields survive
        assertEquals(FileFormat.MP3, settings.audioSettings().outputFormat());
        assertEquals(320, settings.audioSettings().bitrate());
        assertEquals(7, settings.parallelConversions());
        assertTrue(settings.overwriteExisting());
        assertEquals(outputDir, settings.outputDirectory());

        // And: the corrupt video section is reset to valid defaults
        assertNotNull(settings.videoSettings());
        assertTrue(settings.videoSettings().isValid());

        // And: the corrupt original is still backed up
        assertTrue(listBackupFiles("settings.json").size() >= 1,
                "corrupt settings file should be backed up");
    }

    @Test
    void loadSettings_CorruptGlobalField_SalvagesSections() throws IOException {
        // Given: a non-integer parallelConversions corrupts the global section,
        // but the per-section subtrees are fine
        Path outputDir = tempDir.resolve("out");
        Files.createDirectories(outputDir);
        writeSettingsJson("""
                {
                  "outputDirectory": "%s",
                  "parallelConversions": "not-a-number",
                  "videoSettings": {
                    "codec": "libx264",
                    "bitrate": 8000,
                    "frameRate": -1,
                    "aspectRatio": "KEEP_ORIGINAL",
                    "outputFormat": "MP4"
                  },
                  "audioSettings": {
                    "codec": "libmp3lame",
                    "bitrate": 256,
                    "sampleRate": -1,
                    "channels": -1,
                    "quality": 5,
                    "outputFormat": "MP3"
                  }
                }
                """.formatted(outputDir));

        // When
        var settings = settingsManager.loadSettings();

        // Then: sections survive, invalid global field resets to default
        assertEquals(8000, settings.videoSettings().bitrate());
        assertEquals(256, settings.audioSettings().bitrate());
        assertEquals(4, settings.parallelConversions(), "invalid global field must reset to default");
    }

    @Test
    void loadSettings_SectionWithInvalidValues_ResetsOnlyThatSection() throws IOException {
        // Given: parseable JSON, but the audio section carries out-of-range values
        Path outputDir = tempDir.resolve("out");
        Files.createDirectories(outputDir);
        writeSettingsJson("""
                {
                  "outputDirectory": "%s",
                  "parallelConversions": 5,
                  "videoSettings": {
                    "codec": "libx264",
                    "bitrate": 8000,
                    "frameRate": -1,
                    "aspectRatio": "KEEP_ORIGINAL",
                    "outputFormat": "MP4"
                  },
                  "audioSettings": {
                    "codec": "libmp3lame",
                    "bitrate": 99999,
                    "sampleRate": -1,
                    "channels": -1,
                    "quality": 5,
                    "outputFormat": "MP3"
                  }
                }
                """.formatted(outputDir));

        // When
        var settings = settingsManager.loadSettings();

        // Then: the valid video section survives; audio resets to defaults
        assertEquals(8000, settings.videoSettings().bitrate());
        assertEquals(192, settings.audioSettings().bitrate(), "invalid section must reset to default");
        assertEquals(5, settings.parallelConversions());
    }

    @Test
    void loadSettings_UnparseableJson_KeepsFullResetBehaviorWithBackup() throws IOException {
        // Given: a file that is not even parseable JSON
        Files.writeString(settingsManager.getSettingsFilePath(), "{ not json at all",
                StandardOpenOption.CREATE);

        // When
        var settings = settingsManager.loadSettings();

        // Then: full reset to defaults and a backup is kept
        assertNotNull(settings);
        assertEquals(4, settings.parallelConversions());
        assertFalse(settings.overwriteExisting());
        assertTrue(listBackupFiles("settings.json").size() >= 1);
    }

    // ========== Item 4: unified sub-second backup timestamps ==========

    @Test
    void stateBackup_Filename_UsesSubSecondTimestampPattern() throws IOException {
        // Given: corrupted state file
        Files.writeString(stateManager.getStateFilePath(), "{ corrupted",
                StandardOpenOption.CREATE);

        // When: load triggers the backup
        stateManager.loadState();

        // Then: the backup filename has sub-second precision
        List<Path> backups = listBackupFiles("state.json");
        assertEquals(1, backups.size());
        assertTrue(SUBSECOND_BACKUP_SUFFIX.matcher(backups.get(0).getFileName().toString()).find(),
                "backup filename must match sub-second pattern: " + backups.get(0).getFileName());
    }

    @Test
    void settingsBackup_Filename_UsesSharedSubSecondTimestampPattern() throws IOException {
        // Given: unparseable settings file (full-reset path also backs up)
        Files.writeString(settingsManager.getSettingsFilePath(), "{ corrupted",
                StandardOpenOption.CREATE);

        // When
        settingsManager.loadSettings();

        // Then: the backup filename matches the same shared pattern as state backups
        List<Path> backups = listBackupFiles("settings.json");
        assertEquals(1, backups.size());
        assertTrue(SUBSECOND_BACKUP_SUFFIX.matcher(backups.get(0).getFileName().toString()).find(),
                "backup filename must match sub-second pattern: " + backups.get(0).getFileName());
    }

    @Test
    void stateBackups_RapidSuccession_DoNotCollideWithinTheSameSecond() throws IOException {
        // Given: repeated corruption recovery within the same second
        Path statePath = stateManager.getStateFilePath();
        Files.writeString(statePath, "{ corrupted once", StandardOpenOption.CREATE);

        // When: two backup+recover cycles run back to back
        stateManager.loadState();
        Files.writeString(statePath, "{ corrupted twice", StandardOpenOption.CREATE);
        stateManager.loadState();

        // Then: both backups survive with distinct sub-second names
        List<Path> backups = listBackupFiles("state.json");
        assertEquals(2, backups.size(),
                () -> "expected 2 distinct backups, got: " + backups);
    }

    // ========== helpers ==========

    /**
     * Stubs {@link Files#move} to behave like a filesystem without atomic move
     * support: calls carrying {@link StandardCopyOption#ATOMIC_MOVE} fail with
     * {@link AtomicMoveNotSupportedException}, plain moves succeed (simulated
     * with a real copy+delete).
     *
     * <p>Varargs calls of every arity are registered individually because
     * Mockito matches varargs stubs per argument count.</p>
     */
    private static void stubMoveSimulatingFilesystemWithoutAtomicSupport(MockedStatic<Files> mockedFiles) {
        org.mockito.stubbing.Answer<Path> simulate = invocation -> {
            Object[] args = invocation.getArguments();
            Path source = (Path) args[0];
            Path target = (Path) args[1];
            boolean atomic = java.util.Arrays.stream(args, 2, args.length)
                    .flatMap(a -> a instanceof CopyOption[] arr
                            ? java.util.Arrays.stream(arr)
                            : java.util.stream.Stream.of(a))
                    .anyMatch(StandardCopyOption.ATOMIC_MOVE::equals);
            if (atomic) {
                throw new AtomicMoveNotSupportedException(
                        source.toString(), target.toString(),
                        "atomic move not supported by test filesystem");
            }
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            Files.delete(source);
            return target;
        };
        mockedFiles.when(() -> Files.move(
                Mockito.any(Path.class), Mockito.any(Path.class))).thenAnswer(simulate);
        mockedFiles.when(() -> Files.move(Mockito.any(Path.class), Mockito.any(Path.class),
                Mockito.any())).thenAnswer(simulate);
        mockedFiles.when(() -> Files.move(Mockito.any(Path.class), Mockito.any(Path.class),
                Mockito.any(), Mockito.any())).thenAnswer(simulate);
        mockedFiles.when(() -> Files.move(Mockito.any(Path.class), Mockito.any(Path.class),
                Mockito.any(), Mockito.any(), Mockito.any())).thenAnswer(simulate);
    }

    /** Stubs every {@link Files#move} arity to fail with an {@link IOException}. */
    private static void stubMoveAlwaysFailing(MockedStatic<Files> mockedFiles) {
        java.io.IOException failure = new java.io.IOException("move failed");
        mockedFiles.when(() -> Files.move(
                Mockito.any(Path.class), Mockito.any(Path.class))).thenThrow(failure);
        mockedFiles.when(() -> Files.move(Mockito.any(Path.class), Mockito.any(Path.class),
                Mockito.any())).thenThrow(failure);
        mockedFiles.when(() -> Files.move(Mockito.any(Path.class), Mockito.any(Path.class),
                Mockito.any(), Mockito.any())).thenThrow(failure);
        mockedFiles.when(() -> Files.move(Mockito.any(Path.class), Mockito.any(Path.class),
                Mockito.any(), Mockito.any(), Mockito.any())).thenThrow(failure);
    }

    private void writeSettingsJson(String json) throws IOException {
        Files.writeString(settingsManager.getSettingsFilePath(), json, StandardOpenOption.CREATE);
    }

    private List<Path> listBackupFiles(String baseName) throws IOException {
        try (var files = Files.list(configDir)) {
            return files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(baseName + ".backup");
                    })
                    .toList();
        }
    }
}
