package org.omc.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/** Applies page margins to generated editable documents. */
final class DocumentOutputOptions {
    private DocumentOutputOptions() { }

    /** Max entries scanned per archive (zip-bomb guard). */
    private static final int MAX_ZIP_ENTRIES = 10_000;
    /** Max bytes read from a single zip entry (zip-bomb guard). */
    private static final long MAX_ENTRY_BYTES = 50L * 1024 * 1024;
    /** Max total bytes across all entries (zip-bomb guard). */
    private static final long MAX_TOTAL_BYTES = 200L * 1024 * 1024;

    static void apply(Path output, FileFormat format, DocumentSettings settings) throws IOException {
        if (format != FileFormat.DOCX && format != FileFormat.ODT) return;
        // System temp dir (never the user output parent).
        Path staged = Files.createTempFile("omc-layout-", ".zip");
        try {
            try (var input = new ZipInputStream(Files.newInputStream(output));
                    var result = new ZipOutputStream(Files.newOutputStream(staged))) {
                ZipEntry entry;
                int entryCount = 0;
                long totalBytes = 0;
                byte[] buffer = new byte[8192];
                while ((entry = input.getNextEntry()) != null) {
                    if (++entryCount > MAX_ZIP_ENTRIES) {
                        throw new IOException("Document has too many archive entries");
                    }
                    String name = entry.getName();
                    // Reject path traversal in entry names.
                    if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                        throw new IOException("Document has unsafe archive entry: " + name);
                    }
                    boolean layout = format == FileFormat.DOCX && name.equals("word/document.xml")
                            || format == FileFormat.ODT && name.equals("styles.xml");
                    // Rewritten layout bytes differ from the original, so a
                    // STORED entry's stale size/crc would corrupt the zip:
                    // always DEFLATE rewritten entries. Preserve STORED only
                    // for untouched entries (e.g. ODT mimetype).
                    ZipEntry replacement = new ZipEntry(name);
                    if (!layout && entry.getMethod() == ZipEntry.STORED) {
                        replacement.setMethod(ZipEntry.STORED);
                        replacement.setSize(entry.getSize());
                        replacement.setCrc(entry.getCrc());
                    }
                    result.putNextEntry(replacement);
                    if (layout) {
                        byte[] xml = readEntryCapped(input, MAX_ENTRY_BYTES);
                        totalBytes += xml.length;
                        if (totalBytes > MAX_TOTAL_BYTES) {
                            throw new IOException("Document archive is too large");
                        }
                        result.write(withMargins(xml, format, settings));
                    } else {
                        long copied = 0;
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            copied += read;
                            totalBytes += read;
                            if (copied > MAX_ENTRY_BYTES || totalBytes > MAX_TOTAL_BYTES) {
                                throw new IOException("Document archive is too large");
                            }
                            result.write(buffer, 0, read);
                        }
                    }
                    result.closeEntry();
                }
            }
            Files.move(staged, output, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    /** Reads one zip entry with a byte cap (zip-bomb guard). */
    private static byte[] readEntryCapped(ZipInputStream input, long maxBytes) throws IOException {
        try (var bytes = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Document entry is too large");
                }
                bytes.write(buffer, 0, read);
            }
            return bytes.toByteArray();
        }
    }

    private static byte[] withMargins(byte[] xml, FileFormat format, DocumentSettings settings) throws IOException {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            String namespace = format == FileFormat.DOCX
                    ? "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                    : "urn:oasis:names:tc:opendocument:xmlns:style:1.0";
            if (format == FileFormat.DOCX) {
                var sections = document.getElementsByTagNameNS(namespace, "sectPr");
                for (int i = 0; i < sections.getLength(); i++) {
                    Element section = (Element) sections.item(i);
                    if (section.getElementsByTagNameNS(namespace, "pgMar").getLength() == 0) {
                        section.appendChild(document.createElementNS(namespace, "w:pgMar"));
                    }
                }
            }
            var margins = document.getElementsByTagNameNS(namespace,
                    format == FileFormat.DOCX ? "pgMar" : "page-layout-properties");
            String[] names = { "top", "bottom", "left", "right" };
            int[] values = { settings.marginTop(), settings.marginBottom(), settings.marginLeft(), settings.marginRight() };
            for (int i = 0; i < margins.getLength(); i++) {
                Element element = (Element) margins.item(i);
                for (int side = 0; side < names.length; side++) {
                    if (format == FileFormat.DOCX) {
                        element.setAttributeNS(namespace, "w:" + names[side], Long.toString(Math.round(values[side] * 1440 / 25.4)));
                    } else {
                        element.setAttributeNS("urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0",
                                "fo:margin-" + names[side], values[side] + "mm");
                    }
                }
            }
            var transformers = TransformerFactory.newInstance();
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var bytes = new ByteArrayOutputStream();
            transformers.newTransformer().transform(new DOMSource(document), new StreamResult(bytes));
            return bytes.toByteArray();
        } catch (ParserConfigurationException | SAXException | TransformerException e) {
            throw new IOException("Could not apply document margins", e);
        }
    }
}
