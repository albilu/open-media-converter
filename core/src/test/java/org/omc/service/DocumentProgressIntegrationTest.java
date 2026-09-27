package org.omc.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.omc.core.ProgressCallback;
import org.omc.model.ConversionResult;
import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real converters behind latency-only wrappers make intermediate activity observable. */
@Timeout(30)
class DocumentProgressIntegrationTest {
    @TempDir Path root;

    private Path delayedTool(String name) throws Exception {
        Path executable = Path.of("/usr/bin", name);
        assumeTrue(Files.isExecutable(executable));
        Path wrapper = Files.writeString(root.resolve(name), "#!/bin/sh\nsleep 1.1\nexec " + executable + " \"$@\"\n");
        assertTrue(wrapper.toFile().setExecutable(true));
        return wrapper;
    }

    private ProgressCallback capture(List<Double> percentages) {
        return (percentage, bytes, speed) -> {
            percentages.add(percentage);
            if (percentage == ProgressCallback.INDETERMINATE) {
                assertEquals(0, bytes);
                assertEquals(0, speed);
            }
        };
    }

    private void assertUnknownUntilSuccess(List<Double> percentages) {
        assertTrue(percentages.size() >= 3, "Observe real intermediate activity");
        assertEquals(100.0, percentages.getLast());
        assertTrue(percentages.subList(0, percentages.size() - 1).stream()
                .allMatch(p -> p == ProgressCallback.INDETERMINATE), percentages.toString());
    }

    @Test
    void pandocAndOfficeUseUnknownProgressUntilValidatedOutput() throws Exception {
        Path markdown = Files.writeString(root.resolve("input.md"), "# Progress fixture\n\nPreserve this content.\n");
        Path docx = root.resolve("result.docx");
        List<Double> pandocEvents = new ArrayList<>();
        var pandoc = new PandocService(delayedTool("pandoc"));
        var result = pandoc.convertDocument(markdown, docx,
                DocumentSettings.builder().outputFormat(FileFormat.DOCX).build(), capture(pandocEvents));
        assertTrue(result.success(), result.toString());
        assertTrue(Files.size(docx) > 0);
        assertUnknownUntilSuccess(pandocEvents);

        List<Double> officeEvents = new ArrayList<>();
        Path pdf = root.resolve("result.pdf");
        result = new LibreOfficeService(delayedTool("libreoffice")).convertDocument(docx, pdf,
                DocumentSettings.builder().outputFormat(FileFormat.PDF).build(), capture(officeEvents));
        assertTrue(result.success(), result.toString());
        assertEquals("%PDF-", new String(Files.readAllBytes(pdf), 0, 5, java.nio.charset.StandardCharsets.US_ASCII));
        assertUnknownUntilSuccess(officeEvents);
    }

    @Test
    void textToPdfDoesNotPublishFictitiousHalfwayOrEarlyCompletionEvents() throws Exception {
        Path input = Files.writeString(root.resolve("input.md"), "# Two stages\n\nProgress stays unknown.\n");
        List<Double> events = new ArrayList<>();
        var service = new PandocService(delayedTool("pandoc"), new LibreOfficeService(delayedTool("libreoffice")));
        var result = service.convertDocument(input, root.resolve("result.pdf"),
                DocumentSettings.builder().outputFormat(FileFormat.PDF).build(), capture(events));
        assertTrue(result.success(), result.toString());
        assertUnknownUntilSuccess(events);
    }

    @Test
    void missingDocumentsNeverReportOneHundredPercent() throws Exception {
        // Plain text renamed .docx is still a valid LibreOffice input. A missing
        // source reliably exercises both a nonzero exit and LO's no-output case.
        Path invalid = root.resolve("missing.docx");
        for (String tool : List.of("pandoc", "libreoffice")) {
            List<Double> events = new ArrayList<>();
            Path output = root.resolve(tool + ".html");
            var settings = DocumentSettings.builder().outputFormat(FileFormat.HTML).build();
            ConversionResult result = tool.equals("pandoc")
                    ? new PandocService(delayedTool(tool)).convertDocument(invalid, output, settings, capture(events))
                    : new LibreOfficeService(delayedTool(tool)).convertDocument(invalid, output, settings, capture(events));
            assertFalse(result.success(), tool);
            assertFalse(events.isEmpty(), tool);
            assertTrue(events.stream().allMatch(p -> p == ProgressCallback.INDETERMINATE), events.toString());
            assertFalse(Files.exists(output), tool);
        }
    }
}
