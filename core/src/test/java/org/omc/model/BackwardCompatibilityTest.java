// filepath: src/test/java/org/omc/model/BackwardCompatibilityTest.java

package org.omc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests backward compatibility for state file deserialization.
 * Ensures old state files with deprecated fields can still be loaded.
 */
@DisplayName("Backward Compatibility Tests")
class BackwardCompatibilityTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Should load ConversionFile with deprecated inProgress and terminal fields")
    void testConversionFileWithDeprecatedFields() throws Exception {
        // Old state format with deprecated fields
        String json = """
                {
                    "id": "test-id-123",
                    "path": "/home/user/test.mp4",
                    "format": "MP4",
                    "size": 1024000,
                    "status": "COMPLETED",
                    "progress": 100,
                    "errorMessage": null,
                    "inProgress": false,
                    "terminal": true
                }
                """;

        // Should deserialize without error, ignoring unknown fields
        ConversionFile file = mapper.readValue(json, ConversionFile.class);

        assertNotNull(file);
        assertEquals("test-id-123", file.id());
        assertEquals(Path.of("/home/user/test.mp4"), file.path());
        assertEquals(FileFormat.MP4, file.format());
        assertEquals(1024000, file.size());
        assertEquals(ConversionStatus.COMPLETED, file.status());
        assertEquals(100, file.progress());
        assertNull(file.errorMessage());

        // Verify computed methods work correctly
        assertFalse(file.isInProgress());
        assertTrue(file.isTerminal());
    }

    @Test
    @DisplayName("Should load ApplicationState with unknown fields")
    void testApplicationStateWithUnknownFields() throws Exception {
        String json = """
                {
                    "windowState": {
                        "width": 1000,
                        "height": 700,
                        "x": 100,
                        "y": 100,
                        "maximized": false,
                        "fullscreen": false,
                        "unknownField": "ignored"
                    },
                    "sessionState": {
                        "recentFilePaths": [],
                        "lastInputDirectory": null,
                        "lastOutputDirectory": null,
                        "pendingFiles": [],
                        "lastUsedPreset": null,
                        "deprecatedField": "ignored"
                    },
                    "conversionSettings": null,
                    "version": "1.0.0",
                    "lastSaved": 1234567890,
                    "unknownTopLevel": "ignored"
                }
                """;

        ApplicationState state = mapper.readValue(json, ApplicationState.class);

        assertNotNull(state);
        assertNotNull(state.windowState());
        assertNotNull(state.sessionState());
        assertEquals("1.0.0", state.version());
        assertEquals(1234567890, state.lastSaved());
    }

    @Test
    @DisplayName("Should load SessionState with ConversionFile containing deprecated fields")
    void testSessionStateWithOldPendingFiles() throws Exception {
        String json = """
                {
                    "recentFilePaths": [],
                    "lastInputDirectory": null,
                    "lastOutputDirectory": null,
                    "pendingFiles": [
                        {
                            "id": "file-1",
                            "path": "/tmp/test1.mp4",
                            "format": "MP4",
                            "size": 1000,
                            "status": "PENDING",
                            "progress": 0,
                            "errorMessage": null,
                            "inProgress": false,
                            "terminal": false
                        },
                        {
                            "id": "file-2",
                            "path": "/tmp/test2.mp4",
                            "format": "MP4",
                            "size": 2000,
                            "status": "COMPLETED",
                            "progress": 100,
                            "errorMessage": null,
                            "inProgress": false,
                            "terminal": true
                        }
                    ],
                    "lastUsedPreset": null
                }
                """;

        SessionState state = mapper.readValue(json, SessionState.class);

        assertNotNull(state);
        assertEquals(2, state.pendingFiles().size());

        ConversionFile file1 = state.pendingFiles().get(0);
        assertEquals("file-1", file1.id());
        assertEquals(ConversionStatus.PENDING, file1.status());
        assertFalse(file1.isTerminal());

        ConversionFile file2 = state.pendingFiles().get(1);
        assertEquals("file-2", file2.id());
        assertEquals(ConversionStatus.COMPLETED, file2.status());
        assertTrue(file2.isTerminal());
    }

    @Test
    @DisplayName("Should handle completely minimal ConversionFile")
    void testMinimalConversionFile() throws Exception {
        String json = """
                {
                    "id": "minimal-id",
                    "path": "/tmp/minimal.mp4",
                    "format": "MP4",
                    "size": 0,
                    "status": "PENDING",
                    "progress": 0
                }
                """;

        ConversionFile file = mapper.readValue(json, ConversionFile.class);

        assertNotNull(file);
        assertEquals("minimal-id", file.id());
        assertEquals(ConversionStatus.PENDING, file.status());
        assertNull(file.errorMessage());
        assertNull(file.metadata());
    }

    @Test
    @DisplayName("Should handle malformed ConversionFile with null path in SessionState validation")
    void testSessionStateValidationWithNullPathFile() throws Exception {
        // This is the actual malformed entry from the production state file
        String json = """
                {
                    "recentFilePaths": [],
                    "lastInputDirectory": null,
                    "lastOutputDirectory": null,
                    "pendingFiles": [
                        {
                            "inProgress": false,
                            "terminal": true
                        }
                    ],
                    "lastUsedPreset": null
                }
                """;

        SessionState state = mapper.readValue(json, SessionState.class);

        assertNotNull(state);
        // The malformed file should be present but have null fields
        assertEquals(1, state.pendingFiles().size());
        ConversionFile malformed = state.pendingFiles().get(0);
        assertNull(malformed.path());

        // Now validate - this should filter out the malformed entry
        SessionState validated = state.validated();

        assertNotNull(validated);
        // After validation, the malformed entry should be filtered out
        assertEquals(0, validated.pendingFiles().size());
    }

    // Requirement FR-FL-8: Backward compatibility with old state files
    @Test
    @DisplayName("Should load ApplicationState without fileListSortState field")
    void testApplicationStateWithoutSortState() throws Exception {
        // Old state format missing fileListSortState (added in file-list-enhancements)
        String json = """
                {
                    "windowState": {
                        "width": 1024,
                        "height": 768,
                        "x": 100,
                        "y": 100,
                        "maximized": false,
                        "fullscreen": false
                    },
                    "sessionState": {
                        "recentFilePaths": ["/home/user/test.mp4"],
                        "lastInputDirectory": "/home/user",
                        "lastOutputDirectory": "/home/user/output",
                        "pendingFiles": [],
                        "lastUsedPreset": "High Quality"
                    },
                    "conversionSettings": null,
                    "version": "1.0.0",
                    "lastSaved": 1234567890
                }
                """;

        // Should deserialize without error, using default FileListSortState.unsorted()
        ApplicationState state = mapper.readValue(json, ApplicationState.class);

        assertNotNull(state);
        assertNotNull(state.windowState());
        assertNotNull(state.sessionState());
        assertEquals("1.0.0", state.version());

        // Verify fileListSortState defaults to unsorted when missing
        assertNotNull(state.fileListSortState());
        assertNull(state.fileListSortState().sortField());
        assertNotNull(state.fileListSortState().sortDir());
        assertFalse(state.fileListSortState().isSorted());
    }

    // Requirement FR-FL-8: Backward compatibility with old ConversionFile
    @Test
    @DisplayName("Should load ConversionFile without outputPath field")
    void testConversionFileWithoutOutputPath() throws Exception {
        // Old ConversionFile format missing outputPath (added in
        // file-list-enhancements)
        String json = """
                {
                    "id": "test-id-456",
                    "path": "/home/user/input.mp4",
                    "format": "MP4",
                    "size": 2048000,
                    "status": "COMPLETED",
                    "progress": 100,
                    "errorMessage": null,
                    "metadata": {
                        "duration": 120.5,
                        "width": 1920,
                        "height": 1080,
                        "bitrate": 5000000
                    }
                }
                """;

        // Should deserialize without error, outputPath should be Optional.empty()
        ConversionFile file = mapper.readValue(json, ConversionFile.class);

        assertNotNull(file);
        assertEquals("test-id-456", file.id());
        assertEquals(Path.of("/home/user/input.mp4"), file.path());
        assertEquals(FileFormat.MP4, file.format());
        assertEquals(2048000, file.size());
        assertEquals(ConversionStatus.COMPLETED, file.status());
        assertEquals(100, file.progress());
        assertNull(file.errorMessage());
        assertNotNull(file.metadata());

        // Verify outputPath() returns Optional.empty() when field is missing
        assertNotNull(file.outputPath());
        assertTrue(file.outputPath().isEmpty());
    }
}
