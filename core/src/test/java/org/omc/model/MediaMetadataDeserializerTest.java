package org.omc.model;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.omc.util.JsonUtils;

/**
 * Tests the lenient metadata handling on {@link ConversionFile}: typed
 * metadata round-trips polymorphically; legacy flat metadata and
 * unknown-subtype metadata degrade to null; malformed typed metadata
 * propagates as corruption.
 */
class MediaMetadataDeserializerTest {

    private static ConversionFile readFile(String metadataJson) throws IOException {
        String json = """
                {"path": "/tmp/a.png", "format": "PNG", "size": 1000, "metadata": %s}
                """.formatted(metadataJson);
        return JsonUtils.getObjectMapper().readValue(json, ConversionFile.class);
    }

    @Test
    void typedMetadata_roundTripsToSubtype() throws Exception {
        String metadata = """
                {"type": "image", "width": 800, "height": 600, "colorSpace": "sRGB", "bitDepth": 8, "hasAlpha": true}
                """;

        ConversionFile file = readFile(metadata);

        ImageMetadata image = assertInstanceOf(ImageMetadata.class, file.metadata());
        assertNotNull(image);
        assertTrue(image.getSummary() != null);
    }

    @Test
    void legacyFlatMetadata_degradesToNull() throws Exception {
        // Session files written by prior versions carry flat metadata without
        // the "type" discriminator; the file must still load
        String metadata = """
                {"width": 800, "height": 600}
                """;

        ConversionFile file = readFile(metadata);

        assertNotNull(file);
        assertNull(file.metadata());
    }

    @Test
    void unknownSubtypeMetadata_degradesToNull() throws Exception {
        // Metadata written by a newer application version
        String metadata = """
                {"type": "holographic", "depth": 42}
                """;

        ConversionFile file = readFile(metadata);

        assertNull(file.metadata());
    }

    @Test
    void nullMetadata_isAccepted() throws Exception {
        ConversionFile file = readFile("null");

        assertNull(file.metadata());
    }

    @Test
    void malformedTypedMetadata_propagates() {
        // A present, valid type with a corrupt body is real corruption, not
        // version skew, and must surface
        String metadata = """
                {"type": "image", "width": "not-a-number", "height": 600}
                """;

        assertThrows(IOException.class, () -> readFile(metadata));
    }
}
