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
                "1.0.0",
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
     */
    public boolean needsMigration(String currentVersion) {
        if (version == null || currentVersion == null) {
            return false;
        }

        // Simple version comparison (major.minor.patch)
        String[] savedParts = version.split("\\.");
        String[] currentParts = currentVersion.split("\\.");

        if (savedParts.length < 1 || currentParts.length < 1) {
            return false;
        }

        try {
            int savedMajor = Integer.parseInt(savedParts[0]);
            int currentMajor = Integer.parseInt(currentParts[0]);

            return savedMajor < currentMajor;
        } catch (NumberFormatException e) {
            return false;
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
