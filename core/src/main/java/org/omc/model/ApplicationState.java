// filepath: src/main/java/org/omc/model/ApplicationState.java

package org.omc.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Aggregates all application state including window, session, and settings.
 * Requirement REQ-005.3: Complete application state persistence.
 * Requirement REQ-FL-4.5: File list sort state persistence.
 * 
 * Note: @JsonIgnoreProperties ensures backward compatibility when loading
 * state files from older versions that may have different field sets.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ApplicationState {

    /**
     * Current application state schema version.
     *
     * <p>
     * Single source of truth for the {@code version} stamped into saved state
     * files and for migration decisions (see {@link #needsMigration(String)}).
     * All other code must reference this constant instead of duplicating the
     * literal.
     * </p>
     */
    public static final String CURRENT_STATE_VERSION = "1.0.0";

    private final WindowState windowState;
    private final SessionState sessionState;
    private final ConversionSettings conversionSettings;
    private final FileListSortState fileListSortState;
    private final String version; // For migration support
    private final long lastSaved; // Unix timestamp

    @JsonCreator
    public ApplicationState(
            @JsonProperty("windowState") WindowState windowState,
            @JsonProperty("sessionState") SessionState sessionState,
            @JsonProperty("conversionSettings") ConversionSettings conversionSettings,
            @JsonProperty("fileListSortState") FileListSortState fileListSortState,
            @JsonProperty("version") String version,
            @JsonProperty("lastSaved") long lastSaved) {
        this.windowState = windowState;
        this.sessionState = sessionState;
        this.conversionSettings = conversionSettings;
        this.fileListSortState = fileListSortState != null ? fileListSortState : FileListSortState.unsorted();
        this.version = version;
        this.lastSaved = lastSaved;
    }

    /**
     * Creates a default application state.
     * Requirement REQ-FL-4.5: Initialize with unsorted state.
     */
    public static ApplicationState defaultState() {
        return new ApplicationState(
                WindowState.defaultState(),
                SessionState.empty(),
                null, // Settings managed separately
                FileListSortState.unsorted(),
                CURRENT_STATE_VERSION,
                System.currentTimeMillis());
    }

    /**
     * Creates an application state with current timestamp.
     */
    public static ApplicationState create(WindowState windowState, SessionState sessionState,
            ConversionSettings conversionSettings, String version) {
        return new ApplicationState(windowState, sessionState, conversionSettings,
                FileListSortState.unsorted(), version, System.currentTimeMillis());
    }

    @JsonProperty("windowState")
    public WindowState windowState() {
        return windowState;
    }

    @JsonProperty("sessionState")
    public SessionState sessionState() {
        return sessionState;
    }

    @JsonProperty("conversionSettings")
    public ConversionSettings conversionSettings() {
        return conversionSettings;
    }

    /**
     * Returns the file list sort state.
     * Requirement REQ-FL-4.5: Sort state persistence.
     * 
     * @return The file list sort state (never null, defaults to unsorted)
     */
    @JsonProperty("fileListSortState")
    public FileListSortState fileListSortState() {
        return fileListSortState;
    }

    @JsonProperty("version")
    public String version() {
        return version;
    }

    @JsonProperty("lastSaved")
    public long lastSaved() {
        return lastSaved;
    }

    /**
     * Creates a copy with updated window state.
     */
    public ApplicationState withWindowState(WindowState windowState) {
        return new ApplicationState(windowState, sessionState, conversionSettings,
                fileListSortState, version, System.currentTimeMillis());
    }

    /**
     * Creates a copy with updated session state.
     */
    public ApplicationState withSessionState(SessionState sessionState) {
        return new ApplicationState(windowState, sessionState, conversionSettings,
                fileListSortState, version, System.currentTimeMillis());
    }

    /**
     * Creates a copy with updated conversion settings.
     */
    public ApplicationState withConversionSettings(ConversionSettings conversionSettings) {
        return new ApplicationState(windowState, sessionState, conversionSettings,
                fileListSortState, version, System.currentTimeMillis());
    }

    /**
     * Creates a copy with updated file list sort state.
     * Requirement REQ-FL-4.5: Sort state persistence.
     * 
     * @param sortState The new sort state (null will be converted to unsorted)
     * @return A new ApplicationState with updated sort state and current timestamp
     */
    public ApplicationState withFileListSortState(FileListSortState sortState) {
        return new ApplicationState(windowState, sessionState, conversionSettings,
                sortState, version, System.currentTimeMillis());
    }

    /**
     * Creates a copy with updated version.
     */
    public ApplicationState withVersion(String version) {
        return new ApplicationState(windowState, sessionState, conversionSettings,
                fileListSortState, version, System.currentTimeMillis());
    }

    /**
     * Validates and cleans the application state.
     * Returns a state with validated window and session data.
     */
    public ApplicationState validated() {
        WindowState validWindow = windowState != null && windowState.isValid()
                ? windowState
                : WindowState.defaultState();

        SessionState validSession = sessionState != null
                ? sessionState.validated()
                : SessionState.empty();

        FileListSortState validSortState = fileListSortState != null
                ? fileListSortState
                : FileListSortState.unsorted();

        return new ApplicationState(validWindow, validSession, conversionSettings,
                validSortState, version, System.currentTimeMillis());
    }

    /**
     * Checks if this state needs migration based on version.
     *
     * <p>
     * Compares the saved version against {@code currentVersion} using the
     * major and minor components only (patch differences never require
     * migration, since patch releases must stay schema-compatible). Missing
     * minor components are treated as {@code 0}. Unparseable versions never
     * require migration.
     * </p>
     *
     * @param currentVersion the schema version to compare against
     * @return true only if the saved schema is strictly older
     */
    public boolean needsMigration(String currentVersion) {
        SchemaVersion saved = SchemaVersion.parse(version);
        SchemaVersion current = SchemaVersion.parse(currentVersion);

        return saved != null && current != null && saved.compareTo(current) < 0;
    }

    /**
     * Parsed {@code major.minor} schema version used for migration decisions.
     *
     * <p>
     * Patch components are intentionally excluded: they never influence
     * migration. Instances are comparable so callers can order versions
     * numerically (e.g. {@code 1.10 > 1.9}).
     * </p>
     */
    public record SchemaVersion(int major, int minor) implements Comparable<SchemaVersion> {

        /**
         * Parses a version string of the form {@code major[.minor[.patch]]}.
         * Missing minor or patch components default to {@code 0}; extra
         * components beyond the patch are ignored.
         *
         * @param version the version string, may be null
         * @return the parsed schema version, or null if unparseable
         */
        public static SchemaVersion parse(String version) {
            if (version == null) {
                return null;
            }

            String[] parts = version.split("\\.");
            if (parts.length < 1 || parts[0].isBlank()) {
                return null;
            }

            try {
                int major = Integer.parseInt(parts[0]);
                int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                return new SchemaVersion(major, minor);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Override
        public int compareTo(SchemaVersion other) {
            int majorComparison = Integer.compare(major, other.major);
            return majorComparison != 0 ? majorComparison : Integer.compare(minor, other.minor);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ApplicationState that = (ApplicationState) o;
        return lastSaved == that.lastSaved &&
                Objects.equals(windowState, that.windowState) &&
                Objects.equals(sessionState, that.sessionState) &&
                Objects.equals(conversionSettings, that.conversionSettings) &&
                Objects.equals(fileListSortState, that.fileListSortState) &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(windowState, sessionState, conversionSettings,
                fileListSortState, version, lastSaved);
    }

    @Override
    public String toString() {
        return "ApplicationState{" +
                "windowState=" + windowState +
                ", sessionState=" + sessionState +
                ", conversionSettings=" + (conversionSettings != null ? "present" : "null") +
                ", fileListSortState=" + fileListSortState +
                ", version='" + version + '\'' +
                ", lastSaved=" + lastSaved +
                '}';
    }
}
