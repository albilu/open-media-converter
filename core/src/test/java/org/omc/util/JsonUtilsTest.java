package org.omc.util;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Tests for the JsonUtils shared-mapper contract (P2 audit item).
 *
 * <p>
 * Pins two deliberate decisions:
 * <ul>
 * <li>{@link JsonUtils#getObjectMapper()} exposes one shared singleton —
 * reconfiguring it leaks into every consumer.</li>
 * <li>The shared mapper keeps FAIL_ON_UNKNOWN_PROPERTIES enabled: strict-parse
 * failure is the legacy-preset detection signal in
 * {@code SettingsManager.loadPresetsBySection()} (an unknown field means "old
 * file shape" and must trigger backup+migration, not silent data dropping).
 * Forward compatibility is opt-in per model via
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}.</li>
 * </ul>
 */
class JsonUtilsTest {

    /**
     * Deliberately has no @JsonIgnoreProperties annotation: it stands in for
     * current-format models whose strict parse failure signals "this file is
     * in an older shape".
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
    void sharedMapper_staysStrictOnUnknownProperties() {
        // Load-bearing strictness: legacy detection relies on this throwing.
        // If this test fails after a mapper configuration change, preset
        // migration in SettingsManager has been silently broken.
        assertThrows(UnrecognizedPropertyException.class,
                () -> JsonUtils.fromJson("{\"x\":1,\"y\":2,\"legacyField\":3}", StrictShape.class));
    }

    @Test
    void sharedMapper_remainsStrictAfterOtherOperations(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("strict.json");
        JsonUtils.toJson(new StrictShape(1, 2));
        JsonUtils.isValidJson("{\"a\":1}");
        JsonUtils.writeJsonFile(new StrictShape(3, 4), file.toFile());
        // Re-introduce the unknown field after the round-trip operations
        Files.writeString(file, "{\"x\":1,\"y\":2,\"extra\":true}");

        assertThrows(UnrecognizedPropertyException.class,
                () -> JsonUtils.readJsonFile(file.toFile(), StrictShape.class));
    }
}
