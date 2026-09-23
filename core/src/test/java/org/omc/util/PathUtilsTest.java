package org.omc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for PathUtils degenerate-input handling and symlink-aware containment
 * checks (P2 audit fixes).
 */
class PathUtilsTest {

    @TempDir
    Path tempDir;

    // ========== getExtension / getFilenameWithoutExtension ==========

    @Test
    void getExtension_withDegeneratePath_returnsEmptyString() {
        // Embedded NUL byte makes Paths.get throw InvalidPathException
        assertEquals("", PathUtils.getExtension("a\u0000b.mp4"));
    }

    @Test
    void getFilenameWithoutExtension_withDegeneratePath_returnsEmptyString() {
        assertEquals("", PathUtils.getFilenameWithoutExtension("a\u0000b.mp4"));
    }

    @Test
    void getExtension_withRootPath_returnsEmptyString() {
        // Path.getFileName() is null for the root component
        assertEquals("", PathUtils.getExtension("/"));
    }

    @Test
    void getFilenameWithoutExtension_withRootPath_returnsEmptyString() {
        assertEquals("", PathUtils.getFilenameWithoutExtension("/"));
    }

    @Test
    void getExtension_withRegularFile_returnsExtension() {
        assertEquals("mp4", PathUtils.getExtension("/tmp/video.mp4"));
    }

    @Test
    void getExtension_withNoExtension_returnsEmptyString() {
        assertEquals("", PathUtils.getExtension("/tmp/README"));
    }

    @Test
    void getExtension_withDotfile_returnsEmptyString() {
        assertEquals("", PathUtils.getExtension("/home/user/.hidden"));
    }

    @Test
    void getFilenameWithoutExtension_withRegularFile_returnsBasename() {
        assertEquals("video", PathUtils.getFilenameWithoutExtension("/tmp/video.mp4"));
    }

    // ========== isWithinDirectory ==========

    @Test
    void isWithinDirectory_withFileInside_returnsTrue() throws Exception {
        Path base = Files.createDirectory(tempDir.resolve("base"));
        Path target = Files.createFile(base.resolve("file.txt"));

        assertTrue(PathUtils.isWithinDirectory(base.toString(), target.toString()));
    }

    @Test
    void isWithinDirectory_withPathOutside_returnsFalse() throws Exception {
        Path base = Files.createDirectory(tempDir.resolve("base"));
        Path outside = Files.createFile(tempDir.resolve("outside.txt"));

        assertFalse(PathUtils.isWithinDirectory(base.toString(), outside.toString()));
    }

    @Test
    void isWithinDirectory_withTraversalAttempt_returnsFalse() throws Exception {
        Path base = Files.createDirectory(tempDir.resolve("base"));
        String traversal = base.resolve("../../etc/passwd").toString();

        assertFalse(PathUtils.isWithinDirectory(base.toString(), traversal));
    }

    @Test
    void isWithinDirectory_withSymlinkInsidePointingOutside_returnsFalse() throws Exception {
        Path base = Files.createDirectory(tempDir.resolve("base"));
        Path outsideDir = Files.createDirectory(tempDir.resolve("outside"));
        Path outsideFile = Files.createFile(outsideDir.resolve("secret.txt"));

        Path link;
        try {
            link = Files.createSymbolicLink(base.resolve("link"), outsideFile);
        } catch (Exception e) {
            // Symlink creation may be unavailable (permissions); skip rather than fail
            Assumptions.assumeTrue(false, "symbolic links not supported: " + e.getMessage());
            return;
        }

        // The symlink lives inside 'base' but resolves outside it: the
        // lexical startsWith check wrongly accepts it, toRealPath must not.
        assertFalse(PathUtils.isWithinDirectory(base.toString(), link.toString()),
                "symlink escaping the base directory must not be considered within it");
    }

    @Test
    void isWithinDirectory_withNonExistentTarget_fallsBackToLexicalCheck() {
        Path base = tempDir.resolve("base");
        Path futureTarget = base.resolve("future.txt");

        // Neither path exists: toRealPath fails and the check falls back to
        // absolute+normalize comparison instead of throwing.
        assertTrue(PathUtils.isWithinDirectory(base.toString(), futureTarget.toString()));
    }

    @Test
    void isWithinDirectory_withDegeneratePath_returnsFalse() {
        assertFalse(PathUtils.isWithinDirectory("/tmp", "a\u0000b"));
        assertFalse(PathUtils.isWithinDirectory("a\u0000b", "/tmp"));
    }

    // ========== expandHome ==========

    @Test
    void expandHome_withRegexMetacharactersInHomeDir_replacesTildeLiterally() {
        // "$" and "\" in the replacement string of replaceFirst have regex
        // replacement semantics; expansion must be a literal concatenation.
        assertEquals("/home/we$ird/user/media", PathUtils.expandHome("~/media", "/home/we$ird/user"));
    }

    @Test
    void expandHome_withBackslashInHomeDir_replacesTildeLiterally() {
        assertEquals("/home/we\\ird/user/media", PathUtils.expandHome("~/media", "/home/we\\ird/user"));
    }

    @Test
    void expandHome_bareTilde_expandsToHomeDirectory() {
        assertEquals("/home/we$ird/user", PathUtils.expandHome("~", "/home/we$ird/user"));
    }

    @Test
    void expandHome_publicApi_delegatesToUserHomeProperty() {
        String home = System.getProperty("user.home");
        assertEquals(home + "/media", PathUtils.expandHome("~/media"));
    }

    @Test
    void expandHome_withoutTildePrefix_returnsPathUnchanged() {
        assertEquals("/opt/data", PathUtils.expandHome("/opt/data"));
        assertEquals("~other/file", PathUtils.expandHome("~other/file"));
    }

    @Test
    void expandHome_nullOrBlank_returnsInputUnchanged() {
        assertNull(PathUtils.expandHome(null));
        assertEquals("", PathUtils.expandHome(""));
        assertEquals("   ", PathUtils.expandHome("   "));
    }

    @Test
    void xdgDir_withAbsoluteEnvValue_usesEnvValue() {
        assertEquals(java.nio.file.Paths.get("/run/user/1000/app"),
                PathUtils.xdgDir("/run/user/1000/app", ".config"));
    }

    @Test
    void xdgDir_withRelativeEnvValue_fallsBackToUserHome() {
        String home = System.getProperty("user.home");
        assertEquals(java.nio.file.Paths.get(home, ".config"),
                PathUtils.xdgDir("relative/path", ".config"));
    }

    @Test
    void xdgDir_withNullOrBlankEnvValue_fallsBackToUserHome() {
        String home = System.getProperty("user.home");
        assertEquals(java.nio.file.Paths.get(home, ".cache"), PathUtils.xdgDir(null, ".cache"));
        assertEquals(java.nio.file.Paths.get(home, ".cache"), PathUtils.xdgDir("   ", ".cache"));
    }
}
