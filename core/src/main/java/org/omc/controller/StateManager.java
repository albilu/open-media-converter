// filepath: src/main/java/org/omc/controller/StateManager.java

package org.omc.controller;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import org.omc.core.ConfigurationManager;
import org.omc.model.ApplicationState;
import org.omc.model.SessionState;
import org.omc.model.WindowState;
import org.omc.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages application state persistence and retrieval.
 * Handles window state, session state, and overall application state.
 *
 * Implements atomic writes to prevent corruption and provides automatic
 * backup and recovery for corrupted state files.
 *
 * Also hosts small persistence helpers shared with the other controllers in
 * this package: an {@link #moveWithAtomicFallback(Path, Path) atomic-move
 * fallback} for filesystems without atomic move support, quiet temp-file
 * cleanup, and the {@link #BACKUP_TIMESTAMP_FORMATTER shared backup filename
 * timestamp pattern}.
 *
 * Requirements: REQ-005.1, REQ-005.2, REQ-005.3
 */
public class StateManager {
    private static final Logger logger = LoggerFactory.getLogger(StateManager.class);

    private static final String STATE_FILE = "state.json";
    private static final String BACKUP_SUFFIX = ".backup";
    private static final String TEMP_SUFFIX = ".tmp";

    /**
     * Backup filename timestamp pattern shared by all persistence managers in
     * this package.
     *
     * <p>
     * Sub-second precision (microseconds) prevents distinct backups taken
     * within the same second from overwriting each other.
     * </p>
     */
    static final String BACKUP_TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss_SSSSSS";

    /** Thread-safe formatter for {@link #BACKUP_TIMESTAMP_PATTERN}. */
    static final DateTimeFormatter BACKUP_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern(BACKUP_TIMESTAMP_PATTERN);

    private final ConfigurationManager configurationManager;
    private final AtomicReference<ApplicationState> currentState;

    /**
     * Creates a new StateManager.
     *
     * @param configurationManager Configuration manager for paths
     */
    public StateManager(ConfigurationManager configurationManager) {
        this.configurationManager = Objects.requireNonNull(configurationManager, "configurationManager cannot be null");
        this.currentState = new AtomicReference<>(ApplicationState.defaultState());
        logger.debug("StateManager initialized");
    }

    /**
     * Loads application state from disk.
     * If the state file doesn't exist or is corrupted, returns default state.
     * Corrupted files are backed up with timestamp.
     *
     * States written by an older schema (major or minor) are migrated through
     * {@link #migrateState(ApplicationState)}. States written by a newer
     * schema are kept as-is (no destructive action on downgrade).
     *
     * Requirement REQ-005.3: State persistence
     *
     * @return Loaded or default state
     */
    public ApplicationState loadState() {
        Path statePath = getStatePath();
        logger.info("Loading application state from: {}", statePath);

        // If state file doesn't exist, use defaults
        if (!Files.exists(statePath)) {
            logger.info("State file does not exist, using defaults");
            ApplicationState defaults = ApplicationState.defaultState();
            currentState.set(defaults);
            return defaults;
        }

        try {
            // Read state from file
            ApplicationState state = JsonUtils.readJsonFile(
                    statePath.toFile(),
                    ApplicationState.class);

            // Validate loaded state
            if (state == null) {
                logger.warn("State file is empty or null, using defaults");
                backupCorruptedState(statePath);
                ApplicationState defaults = ApplicationState.defaultState();
                currentState.set(defaults);
                return defaults;
            }

            // Validate and clean state
            ApplicationState validatedState = state.validated();

            // Check if state needs migration (major and minor are compared; patch ignored)
            if (state.needsMigration(ApplicationState.CURRENT_STATE_VERSION)) {
                logger.info("State requires migration from version {} to {}",
                        state.version(), ApplicationState.CURRENT_STATE_VERSION);
                validatedState = migrateState(validatedState);
            } else if (!ApplicationState.CURRENT_STATE_VERSION.equals(state.version())) {
                logger.info("State schema {} is same-generation or newer than {}; keeping state as-is",
                        state.version(), ApplicationState.CURRENT_STATE_VERSION);
            }

            currentState.set(validatedState);
            logger.info("Application state loaded successfully");
            return validatedState;

        } catch (IOException e) {
            logger.error("Error reading state file: {}", statePath, e);
            backupCorruptedState(statePath);
            ApplicationState defaults = ApplicationState.defaultState();
            currentState.set(defaults);
            return defaults;
        }
    }

    /**
     * Saves application state to persistent storage (state.json).
     * Writes to temporary file first, then renames to prevent corruption.
     * Method is synchronized to prevent concurrent writes.
     *
     * Requirement REQ-005.3: State persistence with atomic writes
     *
     * @param state Application state to save
     * @throws IOException if save operation fails
     */
    public synchronized void saveState(ApplicationState state) throws IOException {
        Objects.requireNonNull(state, "state cannot be null");
        logger.debug("Saving application state");

        Path statePath = getStatePath();
        Path tempPath = Path.of(statePath.toString() + TEMP_SUFFIX);

        try {
            // Ensure config directory exists
            Files.createDirectories(statePath.getParent());

            // Update version
            ApplicationState stateWithVersion = state.withVersion(ApplicationState.CURRENT_STATE_VERSION);

            // Write to temporary file first
            JsonUtils.writeJsonFile(stateWithVersion, tempPath.toFile());

            // Atomic rename to target file (falls back to a plain move where
            // the filesystem does not support atomic moves)
            moveWithAtomicFallback(tempPath, statePath);

            // Update current state
            currentState.set(stateWithVersion);

            // log state fileListSortState object for debugging
            logger.debug("State fileListSortState object: {}", stateWithVersion.toString());

            logger.info("Application state saved successfully to: {}", statePath);

        } catch (IOException e) {
            logger.error("Failed to save application state to: {}", statePath, e);
            throw e;
        } finally {
            // Clean up the temp file whether the move failed or never happened;
            // after a successful move this is a no-op because the file is gone.
            deleteQuietly(tempPath);
        }
    }

    /**
     * Atomically updates the current application state.
     *
     * <p>
     * Applies {@code update} to the current state and persists the result,
     * both under the same monitor as {@link #saveState(ApplicationState)}, so
     * that concurrent read-modify-write cycles can never overwrite each
     * other's changes (no lost updates). Validation, version re-stamping,
     * atomic file writes and error handling are identical to
     * {@code saveState} because the persistence step delegates to it.
     * </p>
     *
     * <p>
     * The operator runs while the manager monitor is held: it must be
     * side-effect free, must not call back into this manager, and must not
     * return null. If the operator throws, no save happens and the current
     * state is left untouched.
     * </p>
     *
     * @param update transform applied to the current state
     * @return the persisted state (with the version re-stamped), also
     *         visible via {@link #getCurrentState()}
     * @throws IOException     if the save operation fails
     * @throws NullPointerException if {@code update} is null or returns null
     */
    public synchronized ApplicationState updateState(UnaryOperator<ApplicationState> update)
            throws IOException {
        Objects.requireNonNull(update, "update cannot be null");

        ApplicationState current = currentState.get();
        ApplicationState updated = update.apply(current);

        // Reuses saveState under this (already held, re-entrant) monitor so
        // null-check, version re-stamping, atomic write and IOException
        // behavior stay identical to a direct save.
        saveState(updated);

        return currentState.get();
    }

    /**
     * Loads window state from current application state.
     *
     * Requirement REQ-005.1: Window state restoration
     *
     * @return Current window state
     */
    public WindowState loadWindowState() {
        ApplicationState state = currentState.get();
        if (state == null || state.windowState() == null) {
            logger.debug("No window state available, using default");
            return WindowState.defaultState();
        }

        WindowState windowState = state.windowState();
        if (!windowState.isValid()) {
            logger.warn("Window state is invalid, using default");
            return WindowState.defaultState();
        }

        logger.debug("Loaded window state: {}x{} at ({}, {})",
                windowState.width(), windowState.height(), windowState.x(), windowState.y());
        return windowState;
    }

    /**
     * Saves window state.
     * Updates current state and persists to disk.
     * 
     * Requirement REQ-005.1: Window state persistence
     *
     * @param windowState Window state to save
     * @throws IOException if save operation fails
     */
    public void saveWindowState(WindowState windowState) throws IOException {
        Objects.requireNonNull(windowState, "windowState cannot be null");
        logger.debug("Saving window state");

        if (!windowState.isValid()) {
            logger.warn("Window state is invalid, not saving");
            throw new IllegalArgumentException("Invalid window state");
        }

        // Read-modify-write must be atomic so concurrent state updates
        // (session, sort state) are not overwritten by this save
        updateState(state -> state.withWindowState(windowState));

        logger.info("Window state saved successfully");
    }

    /**
     * Loads session state from current application state.
     * 
     * Requirement REQ-005.2: Session state restoration
     *
     * @return Current session state
     */
    public SessionState loadSessionState() {
        ApplicationState state = currentState.get();
        if (state == null || state.sessionState() == null) {
            logger.debug("No session state available, using empty");
            return SessionState.empty();
        }

        SessionState sessionState = state.sessionState().validated();
        logger.debug("Loaded session state with {} recent files and {} pending files",
                sessionState.recentFilePaths().size(), sessionState.pendingFiles().size());
        return sessionState;
    }

    /**
     * Saves session state.
     * Updates current state and persists to disk.
     * 
     * Requirement REQ-005.2: Session state persistence
     *
     * @param sessionState Session state to save
     * @throws IOException if save operation fails
     */
    public void saveSessionState(SessionState sessionState) throws IOException {
        Objects.requireNonNull(sessionState, "sessionState cannot be null");
        logger.debug("Saving session state");

        // Read-modify-write must be atomic so concurrent state updates
        // (window, sort state) are not overwritten by this save
        updateState(state -> state.withSessionState(sessionState));

        logger.info("Session state saved successfully");
    }

    /**
     * Gets the current application state.
     *
     * @return Current application state
     */
    public ApplicationState getCurrentState() {
        return currentState.get();
    }

    /**
     * Resets state to defaults.
     * Creates default state and saves it to disk.
     *
     * @throws IOException if save operation fails
     */
    public void resetToDefaults() throws IOException {
        logger.info("Resetting state to defaults");

        ApplicationState defaults = ApplicationState.defaultState();
        saveState(defaults);

        logger.info("State reset to defaults successfully");
    }

    // ========== State schema migration framework ==========
    // Requirement REQ-005.3: State version migration support

    /**
     * One schema migration step, upgrading a state file from one schema
     * version to the next.
     *
     * @param fromSchema source schema version as {@code "major.minor"}
     * @param toSchema   target schema version as {@code "major.minor"}
     * @param migrator   transform applied to the state; must preserve all
     *                   fields it does not explicitly convert
     */
    record MigrationStep(String fromSchema, String toSchema, UnaryOperator<ApplicationState> migrator) {
    }

    /**
     * Registered migration steps, forming a chain that ends at
     * {@link ApplicationState#CURRENT_STATE_VERSION}.
     *
     * <p>
     * <b>How to add a migration step when the schema changes:</b>
     * </p>
     * <ol>
     * <li>Bump {@link ApplicationState#CURRENT_STATE_VERSION} to the new
     * {@code "major.minor.patch"} value.</li>
     * <li>Add a {@link MigrationStep} below mapping the previous
     * {@code "major.minor"} to the new one, with a method that transforms the
     * old state shape into the new one (rename/move fields, inject defaults,
     * drop obsolete data).</li>
     * <li>Steps must advance strictly toward the current schema and never
     * overshoot it (enforced by tests).</li>
     * </ol>
     *
     * <p>
     * The {@code 0.9 -> 1.0} entry below is a no-op placeholder: no released
     * build ever wrote a 0.9 schema, so the step documents the mechanism.
     * States with unknown old versions are treated as structurally compatible
     * and simply re-stamped (see {@link #migrateState(ApplicationState)}).
     * </p>
     */
    private static final List<MigrationStep> MIGRATION_STEPS = List.of(
            new MigrationStep("0.9", "1.0", StateManager::migrateNoOp));

    /**
     * Returns the registered step that starts at the given schema version.
     *
     * @param fromSchema source schema version as {@code "major.minor"}
     * @return the outgoing step, or empty when no step is registered
     */
    static Optional<MigrationStep> migrationStep(String fromSchema) {
        return MIGRATION_STEPS.stream()
                .filter(step -> step.fromSchema().equals(fromSchema))
                .findFirst();
    }

    /**
     * Returns all registered migration steps (in registration order).
     *
     * @return immutable list of steps
     */
    static List<MigrationStep> migrationSteps() {
        return MIGRATION_STEPS;
    }

    /**
     * Placeholder transform for schema transitions that need no structural
     * change (the state already deserializes into the new shape thanks to
     * tolerant binding).
     */
    private static ApplicationState migrateNoOp(ApplicationState state) {
        return state;
    }

    /**
     * Migrates state from an older schema version to
     * {@link ApplicationState#CURRENT_STATE_VERSION}.
     *
     * <p>
     * The migration walks the registered step chain one hop at a time until
     * the current schema is reached, then stamps the current version. States
     * already at or newer than the current schema are returned untouched
     * (downgrades must never destroy newer data).
     * </p>
     *
     * <p>
     * When a state carries an old version with no registered step, the chain
     * cannot continue; because every released schema is structurally
     * compatible, the state is kept as-is and only the version is stamped.
     * </p>
     *
     * @param state State to migrate
     * @return Migrated state
     */
    private ApplicationState migrateState(ApplicationState state) {
        ApplicationState.SchemaVersion target =
                ApplicationState.SchemaVersion.parse(ApplicationState.CURRENT_STATE_VERSION);
        ApplicationState.SchemaVersion current =
                ApplicationState.SchemaVersion.parse(state.version());

        // Defensive guard: never act on a state that is already current or
        // newer (callers filter with needsMigration, but stay fail-safe).
        if (target == null || current == null || current.compareTo(target) >= 0) {
            logger.info("State schema {} requires no migration to {}; keeping state as-is",
                    state.version(), ApplicationState.CURRENT_STATE_VERSION);
            return state;
        }

        ApplicationState migrated = state;
        int appliedSteps = 0;
        while (true) {
            ApplicationState.SchemaVersion version =
                    ApplicationState.SchemaVersion.parse(migrated.version());
            if (version == null || version.compareTo(target) >= 0) {
                break;
            }

            String schema = version.major() + "." + version.minor();
            Optional<MigrationStep> step = migrationStep(schema);
            if (step.isEmpty()) {
                logger.warn("No migration step registered from schema {}; assuming structural "
                        + "compatibility and stamping version {}", schema,
                        ApplicationState.CURRENT_STATE_VERSION);
                break;
            }

            logger.info("Applying state migration step {} -> {}", schema, step.get().toSchema());
            migrated = step.get().migrator().apply(migrated);
            // Patch component resets to 0 when the schema version changes
            migrated = migrated.withVersion(step.get().toSchema() + ".0");

            if (++appliedSteps > MIGRATION_STEPS.size()) {
                // Defensive: a broken step chain must never loop forever
                logger.error("Migration chain from {} did not converge; stopping after {} steps",
                        state.version(), appliedSteps);
                break;
            }
        }

        logger.info("Migrating state to version {}", ApplicationState.CURRENT_STATE_VERSION);
        return migrated.withVersion(ApplicationState.CURRENT_STATE_VERSION);
    }

    /**
     * Backs up a corrupted state file.
     * Renames the file with .backup suffix and timestamp.
     *
     * Requirement REQ-005.3: Corrupted file handling
     *
     * @param statePath Path to corrupted state file
     */
    private void backupCorruptedState(Path statePath) {
        try {
            if (Files.exists(statePath)) {
                String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMATTER);
                Path backupPath = Path.of(statePath.toString() + BACKUP_SUFFIX + "_" + timestamp);

                Files.move(statePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                logger.warn("Corrupted state file backed up to: {}", backupPath);
            }
        } catch (IOException e) {
            logger.error("Failed to backup corrupted state file: {}", statePath, e);
        }
    }

    /**
     * Gets the state file path.
     *
     * @return State file path
     */
    private Path getStatePath() {
        return configurationManager.getConfigDirectory().resolve(STATE_FILE);
    }

    /**
     * Checks if state file exists.
     *
     * @return true if state file exists
     */
    public boolean stateFileExists() {
        return Files.exists(getStatePath());
    }

    /**
     * Gets the state file path for external use.
     *
     * @return State file path
     */
    public Path getStateFilePath() {
        return getStatePath();
    }

    // ========== Shared persistence helpers (package-visible) ==========

    /**
     * Moves {@code source} to {@code target}, preferring an atomic move.
     *
     * <p>
     * Some filesystems (certain network mounts, FAT/exFAT) do not support
     * atomic moves; {@link Files#move} then throws
     * {@link AtomicMoveNotSupportedException}, which is unchecked and would
     * escape {@code catch (IOException)} handlers. This helper catches it and
     * retries with a plain {@link StandardCopyOption#REPLACE_EXISTING} move,
     * logging a warning, so callers keep working on such filesystems.
     * </p>
     *
     * @param source the file to move (typically the temp file)
     * @param target the destination file
     * @throws IOException if both the atomic and the fallback move fail
     */
    static void moveWithAtomicFallback(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            logger.warn("Atomic move not supported for {} ({}); falling back to a non-atomic move",
                    target, e.getMessage());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Deletes a file if it exists, swallowing IOExceptions (best effort).
     * Used for temp-file cleanup in {@code finally} blocks where the original
     * outcome must not be masked.
     *
     * @param file the file to delete, may no longer exist
     */
    static void deleteQuietly(Path file) {
        try {
            if (Files.exists(file)) {
                Files.delete(file);
            }
        } catch (IOException e) {
            logger.warn("Failed to delete temp file: {}", file, e);
        }
    }
}
