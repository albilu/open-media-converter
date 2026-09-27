package org.omc.core;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import org.omc.model.ConversionProgress;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionTool;

import static org.junit.jupiter.api.Assertions.*;

class IndeterminateProgressTest {
    @Test
    void mixedBatchDoesNotInventBytesOrAnEtaForUnknownWork() {
        ProgressEngine engine = new ProgressEngine();
        engine.startBatch(List.of("video", "document"), Map.of("video", 1000L, "document", 9000L));
        engine.startTracking("video", 1000);
        engine.startTracking("document", 9000);
        engine.updateProgressWithPercentage("video", 40);
        engine.updateIndeterminateProgress("document");
        var document = engine.getProgress("document").orElseThrow();
        assertTrue(document.indeterminate());
        assertEquals(0, document.processedBytes());
        assertEquals("Unknown", document.formatSpeed());
        assertEquals("Unknown", document.formatEta());
        var batch = engine.getBatchProgress();
        assertTrue(batch.indeterminate());
        assertEquals(400, batch.processedBytes(), "Only measurable work contributes processed bytes");
        assertEquals("Unknown", batch.formatEta());
        assertEquals("Unknown", batch.formatSpeed());
        assertFalse(batch.formatStatusMessage().contains("%"));

        engine.completeTracking("document", ConversionResult.success("document", java.nio.file.Path.of("output"), "",
                Duration.ofSeconds(1), 9000, 1000, ConversionTool.PANDOC));
        assertFalse(engine.getBatchProgress().indeterminate());
        assertFalse(engine.getProgress("document").orElseThrow().indeterminate());
        assertEquals(94, engine.getBatchProgress().overallPercentage());
    }

    @Test
    void failureAndCancellationStopActivityWithoutClaimingSuccess() {
        for (boolean cancel : new boolean[] {false, true}) {
            ProgressEngine engine = new ProgressEngine();
            engine.startBatch(List.of("document"), Map.of("document", 1000L));
            engine.startTracking("document", 1000);
            engine.updateIndeterminateProgress("document");
            if (cancel) engine.cancelTracking("document");
            else engine.completeTracking("document", ConversionResult.failure("document", "invalid document", "",
                    Duration.ofSeconds(2), 1000, ConversionTool.PANDOC));
            var progress = engine.getProgress("document").orElseThrow();
            assertFalse(progress.indeterminate());
            assertEquals(0, progress.percentage());
            assertFalse(engine.getBatchProgress().indeterminate());
            assertTrue(engine.getBatchProgress().isComplete());
            engine.updateIndeterminateProgress("document");
            assertFalse(engine.getProgress("document").orElseThrow().indeterminate(), "Late activity stays terminal");
        }
    }

    @Test
    void savedProgressRoundTripsAndOldSessionsDefaultToDeterminate() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var unknown = ConversionProgress.initial("document", 1000).updateIndeterminate();
        String json = mapper.writeValueAsString(unknown);
        assertEquals(unknown, mapper.readValue(json, ConversionProgress.class));
        ObjectNode legacy = mapper.valueToTree(ConversionProgress.initial("video", 1000).updateWithPercentage(40));
        legacy.remove("indeterminate");
        var restored = mapper.treeToValue(legacy, ConversionProgress.class);
        assertFalse(restored.indeterminate());
        assertEquals(40, restored.percentage());
    }
}
