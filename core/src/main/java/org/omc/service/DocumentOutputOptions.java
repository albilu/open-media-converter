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

    static void apply(Path output, FileFormat format, DocumentSettings settings) throws IOException {
        if (format != FileFormat.DOCX && format != FileFormat.ODT) return;
        Path staged = Files.createTempFile(output.toAbsolutePath().getParent(), "omc-layout-", ".zip");
        try {
            try (var input = new ZipInputStream(Files.newInputStream(output));
                    var result = new ZipOutputStream(Files.newOutputStream(staged))) {
                ZipEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    boolean layout = format == FileFormat.DOCX && entry.getName().equals("word/document.xml")
                            || format == FileFormat.ODT && entry.getName().equals("styles.xml");
                    // ODT requires its mimetype entry to remain uncompressed.
                    ZipEntry replacement = new ZipEntry(entry.getName());
                    if (!layout && entry.getMethod() == ZipEntry.STORED) {
                        replacement.setMethod(ZipEntry.STORED);
                        replacement.setSize(entry.getSize());
                        replacement.setCrc(entry.getCrc());
                    }
                    result.putNextEntry(replacement);
                    if (layout) result.write(withMargins(input.readAllBytes(), format, settings));
                    else input.transferTo(result);
                    result.closeEntry();
                }
            }
            Files.move(staged, output, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staged);
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
