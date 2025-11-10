// filepath: src/main/java/org/omc/model/SessionState.java

package org.omc.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the session state including recent files and directories.
 * Requirement REQ-005.2: Session restoration with file list and recent paths.
 * 
 * Note: @JsonIgnoreProperties ensures backward compatibility when loading
 * state files from older versions that may have different field sets.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SessionState {

    private final List<Path> recentFilePaths;
    private final Path lastInputDirectory;
    private final Path lastOutputDirectory;
    private final List<ConversionFile> pendingFiles;
    private final String lastUsedPreset;

    @JsonCreator
    public SessionState(
            @JsonProperty("recentFilePaths") List<Path> recentFilePaths,
            @JsonProperty("lastInputDirectory") Path lastInputDirectory,
            @JsonProperty("lastOutputDirectory") Path lastOutputDirectory,
            @JsonProperty("pendingFiles") List<ConversionFile> pendingFiles,
            @JsonProperty("lastUsedPreset") String lastUsedPreset) {
        this.recentFilePaths = new ArrayList<>(recentFilePaths != null ? recentFilePaths : List.of());
        this.lastInputDirectory = lastInputDirectory;
        this.lastOutputDirectory = lastOutputDirectory;
        this.pendingFiles = new ArrayList<>(pendingFiles != null ? pendingFiles : List.of());
        this.lastUsedPreset = lastUsedPreset;
    }

    /**
     * Creates an empty session state.
     */
    public static SessionState empty() {
        return new SessionState(List.of(), null, null, List.of(), null);
    }

    @JsonProperty("recentFilePaths")
    public List<Path> recentFilePaths() {
        return Collections.unmodifiableList(recentFilePaths);
    }

    @JsonProperty("lastInputDirectory")
    public Path lastInputDirectory() {
        return lastInputDirectory;
    }

    @JsonProperty("lastOutputDirectory")
    public Path lastOutputDirectory() {
        return lastOutputDirectory;
    }

    @JsonProperty("pendingFiles")
    public List<ConversionFile> pendingFiles() {
        return Collections.unmodifiableList(pendingFiles);
    }

    @JsonProperty("lastUsedPreset")
    public String lastUsedPreset() {
        return lastUsedPreset;
    }

    /**
     * Creates a copy with an added recent file path.
     * Maintains a maximum of 10 recent paths.
     */
    public SessionState withRecentFile(Path filePath) {
        List<Path> updated = new ArrayList<>();
        updated.add(filePath);

        // Add existing paths, avoiding duplicates
        for (Path path : recentFilePaths) {
            if (!path.equals(filePath) && updated.size() < 10) {
                updated.add(path);
            }
        }

        return new SessionState(updated, lastInputDirectory, lastOutputDirectory,
                pendingFiles, lastUsedPreset);
    }

    /**
     * Creates a copy with updated last input directory.
     */
    public SessionState withLastInputDirectory(Path directory) {
        return new SessionState(recentFilePaths, directory, lastOutputDirectory,
                pendingFiles, lastUsedPreset);
    }

    /**
     * Creates a copy with updated last output directory.
     */
    public SessionState withLastOutputDirectory(Path directory) {
        return new SessionState(recentFilePaths, lastInputDirectory, directory,
                pendingFiles, lastUsedPreset);
    }

    /**
     * Creates a copy with updated pending files.
     */
    public SessionState withPendingFiles(List<ConversionFile> files) {
        return new SessionState(recentFilePaths, lastInputDirectory, lastOutputDirectory,
                files, lastUsedPreset);
    }

    /**
     * Creates a copy with updated last used preset.
     */
    public SessionState withLastUsedPreset(String presetName) {
        return new SessionState(recentFilePaths, lastInputDirectory, lastOutputDirectory,
                pendingFiles, presetName);
    }

    /**
     * Validates that referenced paths still exist.
     * Returns a cleaned session state with only valid paths.
     * Filters out null or invalid entries to ensure data integrity.
     */
    public SessionState validated() {
        List<Path> validRecent = recentFilePaths.stream()
                .filter(p -> p != null && p.toFile().exists())
                .toList();

        Path validInputDir = (lastInputDirectory != null && lastInputDirectory.toFile().exists())
                ? lastInputDirectory
                : null;

        Path validOutputDir = (lastOutputDirectory != null && lastOutputDirectory.toFile().exists())
                ? lastOutputDirectory
                : null;

        List<ConversionFile> validFiles = pendingFiles.stream()
                .filter(f -> f != null && f.path() != null && f.path().toFile().exists())
                .toList();

        return new SessionState(validRecent, validInputDir, validOutputDir, validFiles, lastUsedPreset);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SessionState that = (SessionState) o;
        return Objects.equals(recentFilePaths, that.recentFilePaths) &&
                Objects.equals(lastInputDirectory, that.lastInputDirectory) &&
                Objects.equals(lastOutputDirectory, that.lastOutputDirectory) &&
                Objects.equals(pendingFiles, that.pendingFiles) &&
                Objects.equals(lastUsedPreset, that.lastUsedPreset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recentFilePaths, lastInputDirectory, lastOutputDirectory,
                pendingFiles, lastUsedPreset);
    }

    @Override
    public String toString() {
        return "SessionState{" +
                "recentFileCount=" + recentFilePaths.size() +
                ", lastInputDirectory=" + lastInputDirectory +
                ", lastOutputDirectory=" + lastOutputDirectory +
                ", pendingFileCount=" + pendingFiles.size() +
                ", lastUsedPreset='" + lastUsedPreset + '\'' +
                '}';
    }
}
