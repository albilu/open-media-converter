package org.omc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;

class DocumentOutputOptionsTest {

    @TempDir
    Path tempDir;

    private static final String DOCX_DOCUMENT_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body><w:sectPr><w:pgMar w:top="720" w:bottom="720" w:left="720" w:right="720"/></w:sectPr></w:body>
            </w:document>
            """;

    private static final String DOCX_DOCUMENT_XML_NO_MARGINS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body><w:sectPr/></w:body>
            </w:document>
            """;

    private Path writeZip(String name, String[][] entries) throws IOException {
        Path zip = tempDir.resolve(name);
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String[] entry : entries) {
                out.putNextEntry(new ZipEntry(entry[0]));
                out.write(entry[1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zip;
    }

    private String readEntry(Path zip, String entryName) throws IOException {
        try (var in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    private DocumentSettings margins() {
        return DocumentSettings.builder()
                .marginTop(25).marginBottom(25).marginLeft(25).marginRight(25)
                .build();
    }

    @Test
    void apply_withNonDocumentFormat_leavesFileUntouched() throws Exception {
        Path zip = writeZip("plain.zip", new String[][] { { "a.txt", "hello" } });
        byte[] before = Files.readAllBytes(zip);

        DocumentOutputOptions.apply(zip, FileFormat.PDF, margins());

        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(zip)));
    }

    @Test
    void apply_docx_rewritesMarginsInTwips() throws Exception {
        Path docx = writeZip("in.docx", new String[][] { { "word/document.xml", DOCX_DOCUMENT_XML } });

        DocumentOutputOptions.apply(docx, FileFormat.DOCX, margins());

        String xml = readEntry(docx, "word/document.xml");
        // 25mm -> round(25 * 1440 / 25.4) = 1417 twips
        assertTrue(xml.contains("w:top=\"1417\""), xml);
        assertTrue(xml.contains("w:bottom=\"1417\""), xml);
        assertTrue(xml.contains("w:left=\"1417\""), xml);
        assertTrue(xml.contains("w:right=\"1417\""), xml);
    }

    @Test
    void apply_docx_addsMissingPgMarElement() throws Exception {
        Path docx = writeZip("in.docx", new String[][] { { "word/document.xml", DOCX_DOCUMENT_XML_NO_MARGINS } });

        DocumentOutputOptions.apply(docx, FileFormat.DOCX, margins());

        String xml = readEntry(docx, "word/document.xml");
        assertTrue(xml.contains("pgMar"), "a pgMar element must be created when absent: " + xml);
        assertTrue(xml.contains("w:top=\"1417\""), xml);
    }

    @Test
    void apply_odt_rewritesMarginsInMillimeters() throws Exception {
        String stylesXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document-styles
                    xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                    xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
                    xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0">
                  <office:automatic-styles>
                    <style:page-layout style:name="pm1">
                      <style:page-layout-properties fo:margin-top="10mm" fo:margin-bottom="10mm"
                          fo:margin-left="10mm" fo:margin-right="10mm"/>
                    </style:page-layout>
                  </office:automatic-styles>
                </office:document-styles>
                """;
        Path odt = writeZip("in.odt", new String[][] { { "styles.xml", stylesXml } });

        DocumentOutputOptions.apply(odt, FileFormat.ODT, margins());

        String xml = readEntry(odt, "styles.xml");
        assertTrue(xml.contains("fo:margin-top=\"25mm\""), xml);
        assertTrue(xml.contains("fo:margin-right=\"25mm\""), xml);
    }

    @Test
    void apply_withZipSlipEntry_throwsAndKeepsOriginal() throws Exception {
        Path docx = writeZip("evil.docx", new String[][] {
                { "../evil.xml", "<x/>" },
                { "word/document.xml", DOCX_DOCUMENT_XML }
        });
        byte[] before = Files.readAllBytes(docx);

        assertThrows(IOException.class, () -> DocumentOutputOptions.apply(docx, FileFormat.DOCX, margins()));
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(docx)),
                "the original document must not be modified on rejection");
    }

    @Test
    void apply_withXxeDoctype_throws() throws Exception {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE w:document [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body><w:sectPr><w:pgMar/></w:sectPr></w:body>
                </w:document>
                """;
        Path docx = writeZip("xxe.docx", new String[][] { { "word/document.xml", xxe } });

        IOException e = assertThrows(IOException.class,
                () -> DocumentOutputOptions.apply(docx, FileFormat.DOCX, margins()));
        assertFalse(e.getMessage().contains("passwd"), "no external entity content must leak");
    }

    @Test
    void apply_preservesStoredMethodForUntouchedEntries() throws Exception {
        // ODT requires the mimetype entry STORED (uncompressed) per spec;
        // the rewriter must keep STORED for entries it does not rewrite
        Path odt = tempDir.resolve("stored.odt");
        byte[] mimetype = "application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.UTF_8);
        try (var out = new ZipOutputStream(Files.newOutputStream(odt))) {
            ZipEntry stored = new ZipEntry("mimetype");
            stored.setMethod(ZipEntry.STORED);
            stored.setSize(mimetype.length);
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(mimetype);
            stored.setCrc(crc.getValue());
            out.putNextEntry(stored);
            out.write(mimetype);
            out.closeEntry();
            out.putNextEntry(new ZipEntry("styles.xml"));
            out.write("""
                    <?xml version="1.0"?>
                    <office:document-styles
                        xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                        xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
                        xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0">
                      <office:automatic-styles>
                        <style:page-layout style:name="pm1">
                          <style:page-layout-properties fo:margin-top="10mm" fo:margin-bottom="10mm"
                              fo:margin-left="10mm" fo:margin-right="10mm"/>
                        </style:page-layout>
                      </office:automatic-styles>
                    </office:document-styles>
                    """.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        DocumentOutputOptions.apply(odt, FileFormat.ODT, margins());

        try (var in = new ZipInputStream(Files.newInputStream(odt))) {
            ZipEntry first = in.getNextEntry();
            assertEquals("mimetype", first.getName());
            assertEquals(ZipEntry.STORED, first.getMethod(), "untouched STORED entry must stay STORED");
            assertTrue(java.util.Arrays.equals(mimetype, in.readAllBytes()), "mimetype content unchanged");
        }
    }
}
