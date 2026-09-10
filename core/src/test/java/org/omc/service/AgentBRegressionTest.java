package org.omc.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.omc.core.ConfigurationManager;
import org.omc.exception.ErrorCode;
import org.omc.exception.FileOperationException;
import org.omc.model.ConversionFile;
import org.omc.model.FileFormat;
import org.omc.util.FileUtils;

/**
 * Regression tests for the P2 audit fixes owned by this agent:
 *
 * <ol>
 * <li>ZIP-content-based detection of OOXML/ODF/EPUB formats in
 * {@link FileHandler} (mimetype entry and [Content_Types].xml inspection with
 * entry/byte caps, extension as final fallback).</li>
 * <li>Streamed {@code copyFile(source, destination, overwrite, callback)} that
 * reports real cumulative-byte progress per chunk instead of one fake 100%
 * callback after an atomic copy.</li>
 * <li>Deduplication of identical copy/move/getFileSize logic between
 * {@link FileUtils} and {@link FileHandler} (Path-based FileUtils overloads
 * are the single source of truth).</li>
 * <li>{@link ConversionFile} memory-visibility contract: the mutable runtime
 * fields (status, progress, errorMessage, progressInfo) must be volatile so
 * UI threads observe engine updates.</li>
 * </ol>
 */
class AgentBRegressionTest {

    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    private FileHandler fileHandler;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // None of these tests touch the temp-file API, so no ConfigurationManager
        // behavior is needed; a null dependency keeps this suite Mockito-free.
        fileHandler = new FileHandler(null);
        tempDir = Files.createTempDirectory("agentBRegression");
    }

    @AfterEach
    void tearDown() {
        // Best-effort recursive cleanup
        try (var paths = Files.walk(tempDir)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    // Ignore cleanup errors
                }
            });
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    // =====================================================================
    // Item 1: ZIP-content-based format detection
    // =====================================================================

    /** Builds a real ZIP file with the given entries, preserving order. */
    private Path createZip(String fileName, Map<String, byte[]> entries) throws IOException {
        Path file = tempDir.resolve(fileName);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return file;
    }

    private Map<String, byte[]> orderedMap() {
        return new LinkedHashMap<>();
    }

    @Test
    void detectFormat_zipWithWordprocessingmlContentTypes_andDocxExtension_isDocx() throws IOException {
        Map<String, byte[]> entries = orderedMap();
        entries.put("[Content_Types].xml",
                "<?xml version=\"1.0\"?><Types xmlns=\"...\">wordprocessingml.document</Types>"
                        .getBytes(StandardCharsets.UTF_8));
        Path docx = createZip("real.docx", entries);

        assertEquals(FileFormat.DOCX, fileHandler.detectFormat(docx));
    }

    @Test
    void detectFormat_contentTypesSpreadsheetml_winsOverDocxExtension() throws IOException {
        // A .docx-named file whose ZIP actually carries spreadsheet parts must
        // be detected as XLSX, not blindly routed by its extension.
        Map<String, byte[]> entries = orderedMap();
        entries.put("[Content_Types].xml",
                "<?xml version=\"1.0\"?><Types xmlns=\"...\">spreadsheetml.sheet</Types>"
                        .getBytes(StandardCharsets.UTF_8));
        Path renamed = createZip("actually-xlsx.docx", entries);

        assertEquals(FileFormat.XLSX, fileHandler.detectFormat(renamed));
    }

    @Test
    void detectFormat_contentTypesPresentationml_withZipExtension_isPptx() throws IOException {
        Map<String, byte[]> entries = orderedMap();
        entries.put("[Content_Types].xml",
                "<?xml version=\"1.0\"?><Types xmlns=\"...\">presentationml.presentation</Types>"
                        .getBytes(StandardCharsets.UTF_8));
        Path pptx = createZip("slides.zip", entries);

        assertEquals(FileFormat.PPTX, fileHandler.detectFormat(pptx));
    }

    @Test
    void detectFormat_mimetypeOdtEntry_winsOverDocxExtension() throws IOException {
        Map<String, byte[]> entries = orderedMap();
        entries.put("mimetype", "application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.UTF_8));
        entries.put("content.xml", "<office:document-content/>".getBytes(StandardCharsets.UTF_8));
        Path renamed = createZip("actually-odt.docx", entries);

        assertEquals(FileFormat.ODT, fileHandler.detectFormat(renamed));
    }

    @Test
    void detectFormat_mimetypeOdsEntry_isDetected() throws IOException {
        Map<String, byte[]> entries = orderedMap();
        entries.put("mimetype", "application/vnd.oasis.opendocument.spreadsheet".getBytes(StandardCharsets.UTF_8));
        Path ods = createZip("sheet.ods", entries);

        assertEquals(FileFormat.ODS, fileHandler.detectFormat(ods));
    }

    @Test
    void detectFormat_bareEpubZipWithMimetypeEntry_isEpub() throws IOException {
        // Task-specified contract: bare zip with mimetype entry + .epub name.
        Map<String, byte[]> entries = orderedMap();
        entries.put("mimetype", "application/epub+zip".getBytes(StandardCharsets.UTF_8));
        Path epub = createZip("book.epub", entries);

        assertEquals(FileFormat.EPUB, fileHandler.detectFormat(epub));
    }

    @Test
    void detectFormat_renamedGenericJarZipWithDocxExtension_fallsBackToExtension() throws IOException {
        // Task-specified fallback contract: a renamed generic ZIP (jar bytes)
        // with a .docx extension but no mimetype/[Content_Types].xml entries
        // cannot be identified by content, so the extension decides (DOCX).
        // This documents that extension is the FINAL fallback, not that the
        // file is trusted: content detection simply found nothing.
        Map<String, byte[]> entries = orderedMap();
        entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
        entries.put("com/example/Main.class", new byte[] { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE });
        Path renamedJar = createZip("renamed.docx", entries);

        assertEquals(FileFormat.DOCX, fileHandler.detectFormat(renamedJar));
    }

    @Test
    void detectFormat_mimetypeWithinFirstThousandEntries_isDetected() throws IOException {
        // The scan must cover the full first 1000 entries, not just the head.
        Map<String, byte[]> entries = orderedMap();
        for (int i = 0; i < 998; i++) {
            entries.put("filler/" + i, new byte[0]);
        }
        entries.put("mimetype", "application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.UTF_8));
        Path deepOdt = createZip("deep-mimetype.docx", entries);

        assertEquals(FileFormat.ODT, fileHandler.detectFormat(deepOdt));
    }

    @Test
    void detectFormat_mimetypeBeyondThousandEntries_fallsBackToExtension() throws IOException {
        // Guard rail: entries past the first 1000 are not scanned (zip-bomb
        // protection), so detection falls back to the extension (.docx).
        Map<String, byte[]> entries = orderedMap();
        for (int i = 0; i < 1000; i++) {
            entries.put("filler/" + i, new byte[0]);
        }
        entries.put("mimetype", "application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.UTF_8));
        Path beyondCap = createZip("beyond-cap.docx", entries);

        assertEquals(FileFormat.DOCX, fileHandler.detectFormat(beyondCap));
    }

    @Test
    void detectFormat_malformedZipWithZipMagic_fallsBackToExtension() throws IOException {
        // Only the 4 ZIP magic bytes: not a parseable zip. Content inspection
        // must swallow the parse failure and keep the extension-based result.
        Path truncated = tempDir.resolve("truncated.docx");
        Files.write(truncated, new byte[] { 0x50, 0x4B, 0x03, 0x04 });

        assertEquals(FileFormat.DOCX, fileHandler.detectFormat(truncated));
    }

    // =====================================================================
    // Item 2: streamed copyFile with real progress
    // =====================================================================

    @Test
    void copyFileWithProgress_largeFile_reportsChunkedIncreasingProgress() throws IOException, FileOperationException {
        // ~3 x 64KB + 100 bytes: must produce several callbacks, strictly
        // increasing, ending at the total size (not one fake 100% ping).
        int total = 3 * COPY_BUFFER_SIZE + 100;
        Path source = tempDir.resolve("large-source.bin");
        byte[] data = new byte[total];
        for (int i = 0; i < total; i++) {
            data[i] = (byte) (i % 251);
        }
        Files.write(source, data);
        Path destination = tempDir.resolve("large-dest.bin");

        List<Long> callbacks = new CopyOnWriteArrayList<>();
        fileHandler.copyFile(source, destination, false, (Consumer<Long>) callbacks::add);

        assertTrue(callbacks.size() >= 3,
                "expected chunked progress callbacks but got " + callbacks.size());
        long previous = -1;
        for (Long value : callbacks) {
            assertTrue(value > previous, "callbacks must be strictly increasing: " + callbacks);
            previous = value;
        }
        assertEquals(total, callbacks.get(callbacks.size() - 1).longValue(),
                "last callback must equal total bytes copied");
        assertEquals(total, Files.size(destination));
        assertArrayEquals(data, Files.readAllBytes(destination));
    }

    @Test
    void copyFileWithProgress_overwriteTrue_preservesLastModifiedTime() throws IOException, FileOperationException {
        Path source = tempDir.resolve("mtime-source.bin");
        Files.writeString(source, "mtime content");
        FileTime original = FileTime.from(Instant.parse("2024-06-01T12:00:00Z"));
        Files.setLastModifiedTime(source, original);
        Path destination = tempDir.resolve("mtime-dest.bin");
        Files.writeString(destination, "stale content"); // exists, must be replaced

        fileHandler.copyFile(source, destination, true, bytes -> {
        });

        assertEquals(original.toMillis(), Files.getLastModifiedTime(destination).toMillis(),
                "streamed copy should preserve last-modified time (best effort)");
        assertEquals("mtime content", Files.readString(destination));
    }

    @Test
    void copyFileWithProgress_overwriteFalse_existingDestination_throwsAlreadyExists() throws IOException {
        Path source = tempDir.resolve("src.bin");
        Files.writeString(source, "data");
        Path destination = tempDir.resolve("dest.bin");
        Files.writeString(destination, "existing");

        FileOperationException exception = assertThrows(FileOperationException.class,
                () -> fileHandler.copyFile(source, destination, false, bytes -> {
                }));
        assertEquals(ErrorCode.FILE_ALREADY_EXISTS, exception.getErrorCode());
        assertEquals("existing", Files.readString(destination), "existing file must be untouched");
    }

    @Test
    void copyFileWithProgress_missingSource_throwsFileNotFound() {
        Path missing = tempDir.resolve("missing.bin");

        FileOperationException exception = assertThrows(FileOperationException.class,
                () -> fileHandler.copyFile(missing, tempDir.resolve("out.bin"), false, bytes -> {
                }));
        assertEquals(ErrorCode.FILE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void copyFileWithProgress_emptyFile_copiesWithNoProgressAndEmptyOutput() throws IOException, FileOperationException {
        Path source = tempDir.resolve("empty.bin");
        Files.createFile(source);
        Path destination = tempDir.resolve("empty-out.bin");

        List<Long> callbacks = new CopyOnWriteArrayList<>();
        fileHandler.copyFile(source, destination, false, (Consumer<Long>) callbacks::add);

        assertTrue(Files.exists(destination));
        assertEquals(0, Files.size(destination));
        // The natural contract: no bytes streamed -> no progress callbacks.
        assertTrue(callbacks.isEmpty(), "empty file should not report progress, got " + callbacks);
    }

    // =====================================================================
    // Item 3: FileUtils Path-based overloads (single source of truth)
    // =====================================================================




    @Test
    void fileUtilsCopyFile_pathOverload_copiesAndKeepsErrorCodes() throws IOException, FileOperationException {
        Path source = tempDir.resolve("fu-copy-src.txt");
        Files.writeString(source, "file utils copy");
        Path destination = tempDir.resolve("fu-copy-dest.txt");

        FileUtils.copyFile(source, destination, false);

        assertEquals("file utils copy", Files.readString(destination));

        FileOperationException exists = assertThrows(FileOperationException.class,
                () -> FileUtils.copyFile(source, destination, false));
        assertEquals(ErrorCode.FILE_ALREADY_EXISTS, exists.getErrorCode());

        FileOperationException missing = assertThrows(FileOperationException.class,
                () -> FileUtils.copyFile(tempDir.resolve("nope.txt"), destination, false));
        assertEquals(ErrorCode.FILE_NOT_FOUND, missing.getErrorCode());

        // overwrite replaces content
        Files.writeString(source, "updated");
        FileUtils.copyFile(source, destination, true);
        assertEquals("updated", Files.readString(destination));
    }

    @Test
    void fileUtilsMoveFile_pathOverload_movesAndKeepsErrorCodes() throws IOException, FileOperationException {
        Path source = tempDir.resolve("fu-move-src.txt");
        Files.writeString(source, "file utils move");
        Path destination = tempDir.resolve("fu-move-dest.txt");

        FileUtils.moveFile(source, destination, false);

        assertFalse(Files.exists(source));
        assertEquals("file utils move", Files.readString(destination));

        Path second = tempDir.resolve("fu-move-src2.txt");
        Files.writeString(second, "second");
        FileOperationException exists = assertThrows(FileOperationException.class,
                () -> FileUtils.moveFile(second, destination, false));
        assertEquals(ErrorCode.FILE_ALREADY_EXISTS, exists.getErrorCode());
    }

    @Test
    void fileUtilsGetFileSize_pathOverload_returnsSizeAndKeepsErrorCode() throws IOException, FileOperationException {
        Path source = tempDir.resolve("fu-size.txt");
        Files.writeString(source, "12345");

        assertEquals(5, FileUtils.getFileSize(source));

        FileOperationException missing = assertThrows(FileOperationException.class,
                () -> FileUtils.getFileSize(tempDir.resolve("nope.txt")));
        assertEquals(ErrorCode.FILE_NOT_FOUND, missing.getErrorCode());
    }
    @Test
    void fileUtilsStringOverloads_delegateToPathOverloads_sameBehavior() throws IOException, FileOperationException {
        // The String-based public API must behave identically to the
        // Path-based source of truth.
        Path source = tempDir.resolve("fu-string-src.txt");
        Files.writeString(source, "string api");

        FileUtils.copyFile(source.toString(), tempDir.resolve("fu-string-dest.txt").toString(), false);
        assertEquals("string api", Files.readString(tempDir.resolve("fu-string-dest.txt")));
        assertEquals("string api".length(),
                FileUtils.getFileSize(tempDir.resolve("fu-string-dest.txt").toString()));
    }

    // =====================================================================
    // Item 4: ConversionFile memory-visibility contract
    // =====================================================================

    @Test
    void conversionFile_mutableRuntimeFields_areVolatile() throws Exception {
        // The engine mutates these while the UI reads them; without volatile
        // the UI may see stale values. (Reflection is the practical way to
        // pin a memory-visibility contract.)
        for (String fieldName : List.of("status", "progress", "errorMessage", "progressInfo")) {
            Field field = ConversionFile.class.getDeclaredField(fieldName);
            assertTrue(Modifier.isVolatile(field.getModifiers()),
                    "ConversionFile." + fieldName + " must be volatile");
            assertFalse(Modifier.isFinal(field.getModifiers()),
                    "ConversionFile." + fieldName + " is intentionally mutable (copy methods write it)");
        }
    }

    @Test
    void conversionFile_identityFields_remainImmutable() throws Exception {
        for (String fieldName : List.of("id", "path", "format", "size", "metadata", "settingsOverride",
                "outputPath")) {
            Field field = ConversionFile.class.getDeclaredField(fieldName);
            assertTrue(Modifier.isFinal(field.getModifiers()),
                    "ConversionFile." + fieldName + " must stay final");
        }
    }

    @Test
    void conversionFile_withMethods_stillReturnFreshCopies() {
        // Pinned behavior from ConversionFileTest, kept here as a guard for
        // the volatile change: with* methods must not mutate the original.
        ConversionFile original = ConversionFile.create(tempDir, FileFormat.MP4, 42L);
        ConversionFile updated = original.withProgress(77);

        assertNotEquals(updated.progress(), original.progress());
        assertEquals(0, original.progress());
        assertEquals(77, updated.progress());
        assertNotNull(original.id());
    }
}
