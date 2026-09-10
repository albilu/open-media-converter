package org.omc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for the JsonUtils shared-mapper contract (P2 audit item).
 *
 * <p>
 * Pins two deliberate decisions:
 * <ul>
 * <li>{@link JsonUtils#getObjectMapper()} exposes one shared singleton —
 * reconfiguring it leaks into every consumer.</li>
 * <li>The shared mapper is lenient on unknown properties
 * (FAIL_ON_UNKNOWN_PROPERTIES disabled): persisted files may carry fields
 * written by newer application versions and must not fail to load. Legacy
 * preset files are detected by shape inspection in
 * {@code SettingsManager.loadPresetsBySection()} (presence of the legacy
 * {@code presets} key or a bare top-level array), <em>not</em> by
 * strict-parse failure — see the SettingsManager tests asserting legacy
 * files still trigger backup+migration under leniency.</li>
 * </ul>
 * </p>
 */
class JsonUtilsTest {

    /**
     * Deliberately has no @JsonIgnoreProperties annotation: leniency must come
     * from the shared mapper configuration, not from per-model opt-in.
     */
    record StrictShape(int x, int y) {
    }

    @Test
    void getObjectMapper_returnsSharedSingleton() {
        ObjectMapper first = JsonUtils.getObjectMapper();
        ObjectMapper second = JsonUtils.getObjectMapper();

        assertSame(first, second, "getObjectMapper must expose the shared mapper, not copies");
    }

    @Test
    void sharedMapper_ignoresUnknownProperties() throws Exception {
        // Leniency contract: unknown fields (e.g. written by a newer version
        // of the application) are dropped, known fields are kept.
        StrictShape shape = JsonUtils.fromJson("{\"x\":1,\"y\":2,\"legacyField\":3}", StrictShape.class);

        assertNotNull(shape);
        assertEquals(1, shape.x());
        assertEquals(2, shape.y());
    }

    @Test
    void sharedMapper_remainsLenientAfterOtherOperations(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("lenient.json");
        JsonUtils.toJson(new StrictShape(1, 2));
        JsonUtils.isValidJson("{\"a\":1}");
        JsonUtils.writeJsonFile(new StrictShape(3, 4), file.toFile());
        // Re-introduce the unknown field after the round-trip operations
        Files.writeString(file, "{\"x\":1,\"y\":2,\"extra\":true}");

        StrictShape shape = JsonUtils.readJsonFile(file.toFile(), StrictShape.class);

        assertNotNull(shape);
        assertEquals(1, shape.x());
        assertEquals(2, shape.y());
    }
}
